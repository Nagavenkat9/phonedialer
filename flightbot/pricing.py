"""Pluggable flight-price providers, tried as a fallback chain.

Each provider answers the same question — "cheapest fare for this route on this
date" — and may optionally use the carrier + flight number to prefer an exact
match. The first provider that returns a price wins; failures fall through to
the next. This is what lets the bot survive one source going down or changing.

Providers:
  - travelpayouts : free Aviasales cached-fare API (needs TRAVELPAYOUTS_TOKEN)
  - google        : best-effort Google Flights scrape (Playwright, no key)
  - amadeus       : optional, only if enterprise credentials are present
"""

from __future__ import annotations

import re
from dataclasses import dataclass

import requests

from .config import Config

TIMEOUT = 30


@dataclass
class FareResult:
    price: float
    currency: str
    source: str  # which provider produced it
    note: str | None = None  # e.g. "exact 6E732" or "route cheapest"


class PriceProvider:
    """Interface: return the cheapest fare, or None if unavailable."""

    name = "base"

    def cheapest(
        self,
        origin: str,
        destination: str,
        date: str,
        currency: str,
        carrier: str | None = None,
        number: str | None = None,
    ) -> FareResult | None:
        raise NotImplementedError


# --------------------------------------------------------------------------- #
# Travelpayouts (Aviasales) — free cached fares
# --------------------------------------------------------------------------- #
class TravelpayoutsProvider(PriceProvider):
    name = "travelpayouts"
    URL = "https://api.travelpayouts.com/aviasales/v3/prices_for_dates"

    def __init__(self, token: str) -> None:
        self._token = token

    def cheapest(self, origin, destination, date, currency, carrier=None, number=None):
        resp = requests.get(
            self.URL,
            params={
                "origin": origin,
                "destination": destination,
                "departure_at": date,
                "currency": currency.lower(),
                "one_way": "true",
                "sorting": "price",
                "direct": "true",  # a numbered flight is a direct leg
                "limit": 30,
                "token": self._token,
            },
            timeout=TIMEOUT,
        )
        if resp.status_code != 200:
            raise ProviderError(f"travelpayouts HTTP {resp.status_code}: {resp.text[:150]}")
        payload = resp.json()
        rows = payload.get("data") or []
        if not rows:
            return None

        # Prefer an exact flight-number match when the cached data carries it.
        if carrier and number:
            for row in rows:
                if str(row.get("airline", "")).upper() == carrier.upper() and str(
                    row.get("flight_number", "")
                ) == str(number):
                    return FareResult(
                        price=float(row["price"]),
                        currency=payload.get("currency", currency).upper(),
                        source=self.name,
                        note=f"exact {carrier}{number}",
                    )

        cheapest = min(rows, key=lambda r: float(r["price"]))
        return FareResult(
            price=float(cheapest["price"]),
            currency=payload.get("currency", currency).upper(),
            source=self.name,
            note="route cheapest",
        )


# --------------------------------------------------------------------------- #
# Google Flights — best-effort scrape (no key, fragile)
# --------------------------------------------------------------------------- #
class GoogleFlightsProvider(PriceProvider):
    name = "google"

    # Match "₹4,532", "Rs 4532", "INR 4,532", "$212" etc.
    _PRICE_RE = re.compile(r"(?:₹|Rs\.?|INR|\$)\s?([0-9][0-9,]{2,})")

    def cheapest(self, origin, destination, date, currency, carrier=None, number=None):
        try:
            from playwright.sync_api import sync_playwright  # lazy: optional dep
        except ImportError:
            raise ProviderError("playwright not installed; google provider unavailable")

        url = (
            "https://www.google.com/travel/flights?curr="
            f"{currency.upper()}&q="
            f"Flights%20from%20{origin}%20to%20{destination}%20on%20{date}%20nonstop"
        )
        text = ""
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            try:
                page = browser.new_page(locale="en-IN")
                page.goto(url, wait_until="networkidle", timeout=60000)
                page.wait_for_timeout(4000)  # let fare tiles render
                text = page.inner_text("body")
            finally:
                browser.close()

        prices = [
            float(m.replace(",", ""))
            for m in self._PRICE_RE.findall(text)
            if float(m.replace(",", "")) >= 500  # drop stray small numbers
        ]
        if not prices:
            return None
        return FareResult(
            price=min(prices),
            currency=currency.upper(),
            source=self.name,
            note="scraped route cheapest",
        )


# --------------------------------------------------------------------------- #
# Skyscanner — best-effort scrape (no key, aggregates OTA deals, fragile)
# --------------------------------------------------------------------------- #
class SkyscannerProvider(PriceProvider):
    name = "skyscanner"

    # Flight fares only: ignore stray small numbers (hotel "rooms from", ratings)
    # and absurdly large ones.
    _PRICE_RE = re.compile(r"₹\s?([0-9][0-9,]{3,})")
    _MIN = 1500.0
    _MAX = 300000.0

    def cheapest(self, origin, destination, date, currency, carrier=None, number=None):
        try:
            from playwright.sync_api import sync_playwright  # lazy: optional dep
        except ImportError:
            raise ProviderError("playwright not installed; skyscanner unavailable")

        # Skyscanner wants the date as YYMMDD.
        try:
            yymmdd = date[2:4] + date[5:7] + date[8:10]
        except IndexError:
            raise ProviderError(f"bad date {date!r}")
        url = (
            f"https://www.skyscanner.co.in/transport/flights/"
            f"{origin.lower()}/{destination.lower()}/{yymmdd}/"
            "?adultsv2=1&cabinclass=economy&rtn=0&preferdirects=true"
        )
        text = ""
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            try:
                page = browser.new_page(
                    locale="en-IN",
                    user_agent=(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        "AppleWebKit/537.36 (KHTML, like Gecko) "
                        "Chrome/125.0.0.0 Safari/537.36"
                    ),
                )
                page.goto(url, wait_until="domcontentloaded", timeout=60000)
                # Results stream in after an initial poll; give them time.
                page.wait_for_timeout(9000)
                text = page.inner_text("body")
            finally:
                browser.close()

        prices = [
            v
            for m in self._PRICE_RE.findall(text)
            if self._MIN <= (v := float(m.replace(",", ""))) <= self._MAX
        ]
        if not prices:
            return None
        return FareResult(
            price=min(prices),
            currency="INR",
            source=self.name,
            note="scraped route cheapest",
        )


# --------------------------------------------------------------------------- #
# Amadeus — optional, only when enterprise credentials exist
# --------------------------------------------------------------------------- #
class AmadeusProvider(PriceProvider):
    name = "amadeus"

    def __init__(self, key: str, secret: str, base_url: str) -> None:
        from .amadeus import Amadeus  # local import keeps it optional

        self._client = Amadeus(key, secret, base_url)

    def cheapest(self, origin, destination, date, currency, carrier=None, number=None):
        from .amadeus import AmadeusError

        try:
            if carrier and number:
                fare = self._client.cheapest_fare_for_flight(
                    carrier, number, origin, destination, date, currency
                )
            else:
                fare = None
        except AmadeusError as exc:
            raise ProviderError(str(exc)) from exc
        if fare is None:
            return None
        return FareResult(
            price=fare.price,
            currency=fare.currency,
            source=self.name,
            note=f"exact {carrier}{number}" if carrier and number else "route",
        )


class ProviderError(RuntimeError):
    """A provider failed (network/parse); the chain should try the next one."""


def build_providers(cfg: Config) -> list[PriceProvider]:
    """Instantiate the configured providers, in order, skipping unusable ones."""
    built: list[PriceProvider] = []
    for name in cfg.providers:
        if name == "travelpayouts" and cfg.has_travelpayouts:
            built.append(TravelpayoutsProvider(cfg.travelpayouts_token))
        elif name == "skyscanner":
            built.append(SkyscannerProvider())
        elif name == "google":
            built.append(GoogleFlightsProvider())
        elif name == "amadeus" and cfg.has_amadeus:
            built.append(
                AmadeusProvider(cfg.amadeus_key, cfg.amadeus_secret, cfg.amadeus_base_url)
            )
    return built


def cheapest_across(
    providers: list[PriceProvider],
    origin: str,
    destination: str,
    date: str,
    currency: str,
    carrier: str | None = None,
    number: str | None = None,
) -> FareResult | None:
    """Try each provider in order; return the first fare found."""
    for provider in providers:
        try:
            fare = provider.cheapest(origin, destination, date, currency, carrier, number)
        except ProviderError as exc:
            print(f"[warn] provider {provider.name} failed: {exc}")
            continue
        except Exception as exc:  # noqa: BLE001 — never let one provider kill the run
            print(f"[warn] provider {provider.name} errored: {exc!r}")
            continue
        if fare is not None:
            print(f"[info] {origin}->{destination} {date}: {fare.source} "
                  f"{fare.currency} {fare.price:,.0f} ({fare.note})")
            return fare
        print(f"[info] provider {provider.name} had no fare for {origin}->{destination}")
    return None
