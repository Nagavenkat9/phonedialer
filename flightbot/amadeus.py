"""Amadeus flight-offers client.

Looks up the cheapest fare for a *specific* flight by searching the route+date
and filtering offers down to the segment matching the carrier + flight number.
"""

from __future__ import annotations

from dataclasses import dataclass

import requests

TIMEOUT = 30


class AmadeusError(RuntimeError):
    pass


@dataclass
class FareResult:
    price: float
    currency: str


class Amadeus:
    def __init__(self, key: str, secret: str, base_url: str) -> None:
        self._key = key
        self._secret = secret
        self._base = base_url.rstrip("/")
        self._token: str | None = None

    def _authenticate(self) -> str:
        resp = requests.post(
            f"{self._base}/v1/security/oauth2/token",
            data={
                "grant_type": "client_credentials",
                "client_id": self._key,
                "client_secret": self._secret,
            },
            timeout=TIMEOUT,
        )
        if resp.status_code != 200:
            raise AmadeusError(
                f"Amadeus auth failed ({resp.status_code}): {resp.text[:200]}"
            )
        self._token = resp.json()["access_token"]
        return self._token

    def _search_offers(
        self, origin: str, destination: str, date: str, currency: str
    ) -> list[dict]:
        if self._token is None:
            self._authenticate()
        resp = requests.get(
            f"{self._base}/v2/shopping/flight-offers",
            headers={"Authorization": f"Bearer {self._token}"},
            params={
                "originLocationCode": origin,
                "destinationLocationCode": destination,
                "departureDate": date,
                "adults": 1,
                "currencyCode": currency,
                "nonStop": "true",  # a specific numbered flight is a single segment
                "max": 50,
            },
            timeout=TIMEOUT,
        )
        if resp.status_code == 401:
            # Token expired mid-run: re-auth once and retry.
            self._authenticate()
            return self._search_offers(origin, destination, date, currency)
        if resp.status_code != 200:
            raise AmadeusError(
                f"Amadeus search failed ({resp.status_code}): {resp.text[:200]}"
            )
        return resp.json().get("data", [])

    def cheapest_fare_for_flight(
        self,
        carrier: str,
        number: str,
        origin: str,
        destination: str,
        date: str,
        currency: str,
    ) -> FareResult | None:
        """Return the lowest fare whose itinerary contains exactly this flight,
        or None if the flight isn't offered on that date."""
        offers = self._search_offers(origin, destination, date, currency)
        best: FareResult | None = None
        for offer in offers:
            if not _offer_matches_flight(offer, carrier, number):
                continue
            price_info = offer.get("price", {})
            raw = price_info.get("grandTotal") or price_info.get("total")
            if raw is None:
                continue
            price = float(raw)
            cur = price_info.get("currency", currency)
            if best is None or price < best.price:
                best = FareResult(price=price, currency=cur)
        return best


def _offer_matches_flight(offer: dict, carrier: str, number: str) -> bool:
    """True if any itinerary segment is the given carrier + flight number.

    `nonStop=true` keeps offers to a single segment, so a match means the offer
    *is* the flight we care about rather than merely including it in a connection.
    """
    for itinerary in offer.get("itineraries", []):
        for segment in itinerary.get("segments", []):
            seg_carrier = str(segment.get("carrierCode", "")).upper()
            seg_number = str(segment.get("number", ""))
            if seg_carrier == carrier.upper() and seg_number == number:
                return True
    return False
