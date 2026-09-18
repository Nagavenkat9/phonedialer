"""Main run loop: drain Telegram commands, then check fares and alert on drops.

Designed to be invoked once per cron tick (GitHub Actions). Each run:
  1. reads pending Telegram messages and applies commands (/track, /list, ...),
     capturing the sender's chat id so alerts have somewhere to go;
  2. queries the current fare for every tracked flight via Amadeus;
  3. sends a Telegram alert when a fare drops below the last-seen value
     (or meets a target), then persists the new state.
"""

from __future__ import annotations

import datetime as dt
import re

from .config import Config
from .pricing import build_providers, cheapest_across
from .storage import State, Watch
from .telegram import Telegram

HELP_TEXT = (
    "<b>Flight price watcher</b>\n"
    "Commands:\n"
    "<code>/track FLIGHT ORIGIN DEST YYYY-MM-DD [target]</code> — watch a flight\n"
    "   e.g. <code>/track 6E732 DEL TIR 2026-09-24 4500</code>\n"
    "<code>/list</code> — show tracked flights\n"
    "<code>/remove ID|number</code> — stop watching one\n"
    "<code>/check</code> — force a price check now\n"
    "<code>/help</code> — this message"
)

_FLIGHT_RE = re.compile(r"^([A-Za-z0-9]{2})\s*([0-9]{1,4})$")
_DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")


def _split_flight(token: str) -> tuple[str, str] | None:
    """"6E732" -> ("6E", "732"). Carrier is the first two chars (IATA code)."""
    m = _FLIGHT_RE.match(token.strip())
    if not m:
        return None
    return m.group(1).upper(), m.group(2)


def _parse_target(token: str) -> float | None:
    try:
        return float(token.replace(",", ""))
    except ValueError:
        return None


def handle_command(text: str, chat_id: int, state: State, cfg: Config) -> str | None:
    """Apply a single command, mutating state. Returns a reply string, or None."""
    parts = text.strip().split()
    if not parts:
        return None
    cmd = parts[0].lower().split("@")[0]  # strip @BotName suffix in groups
    args = parts[1:]

    if cmd in ("/start", "/help"):
        return HELP_TEXT

    if cmd == "/track":
        if len(args) < 4:
            return "Usage: /track FLIGHT ORIGIN DEST YYYY-MM-DD [target]"
        flight = _split_flight(args[0])
        if not flight:
            return f"Couldn't parse flight number '{args[0]}'. Try e.g. 6E732."
        origin, dest, date = args[1].upper(), args[2].upper(), args[3]
        if not _DATE_RE.match(date):
            return f"Date must be YYYY-MM-DD, got '{date}'."
        target = _parse_target(args[4]) if len(args) >= 5 else None
        watch = Watch(
            carrier=flight[0],
            number=flight[1],
            origin=origin,
            destination=dest,
            date=date,
            chat_id=chat_id,
            target_price=target,
            currency=cfg.currency,
        )
        state.watches[watch.id] = watch
        return f"Now tracking {watch.describe()}"

    if cmd == "/list":
        if not state.watches:
            return "No flights tracked yet. Add one with /track."
        lines = ["<b>Tracked flights:</b>"]
        for i, w in enumerate(state.watches.values(), start=1):
            lines.append(f"{i}. {w.describe()}")
        return "\n".join(lines)

    if cmd == "/remove":
        if not args:
            return "Usage: /remove ID|number (see /list)"
        key = args[0]
        ids = list(state.watches.keys())
        target_id = None
        if key in state.watches:
            target_id = key
        elif key.isdigit() and 1 <= int(key) <= len(ids):
            target_id = ids[int(key) - 1]
        if target_id is None:
            return f"No tracked flight matching '{key}'."
        removed = state.watches.pop(target_id)
        return f"Stopped tracking {removed.flight_number} ({removed.id})."

    if cmd == "/check":
        return "__FORCE_CHECK__"  # sentinel handled by run()

    return None  # ignore unknown/non-command text


def process_updates(tg: Telegram, state: State, cfg: Config) -> bool:
    """Fetch and apply pending Telegram commands. Returns True if a /check was
    requested. Captures the sender chat id as the default alert destination."""
    force_check = False
    updates = tg.get_updates(state.update_offset)
    for upd in updates:
        state.update_offset = max(state.update_offset, upd["update_id"] + 1)
        message = upd.get("message") or {}
        chat = message.get("chat") or {}
        chat_id = chat.get("id")
        text = message.get("text")
        if chat_id is None or not text:
            continue
        # Optional access control: only obey the configured chat.
        if cfg.allowed_chat_id and chat_id != cfg.allowed_chat_id:
            continue
        state.default_chat_id = chat_id
        reply = handle_command(text, chat_id, state, cfg)
        if reply == "__FORCE_CHECK__":
            force_check = True
            tg.send_message(chat_id, "Checking prices now…")
        elif reply:
            tg.send_message(chat_id, reply)
    return force_check


def _resolve_chat(watch: Watch, state: State, cfg: Config) -> int | None:
    return watch.chat_id or state.default_chat_id or cfg.allowed_chat_id or None


def check_prices(tg: Telegram, providers, state: State, cfg: Config) -> None:
    """Query each watched flight and alert when its fare drops or hits target."""
    now = dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds")
    for watch in state.watches.values():
        fare = cheapest_across(
            providers,
            origin=watch.origin,
            destination=watch.destination,
            date=watch.date,
            currency=watch.currency,
            carrier=watch.carrier,
            number=watch.number,
        )

        if fare is None:
            print(f"[info] no fare found for {watch.flight_number} on {watch.date}")
            continue

        price = fare.price
        watch.history.append([now, price])
        chat_id = _resolve_chat(watch, state, cfg)
        prev = watch.last_price

        # "route cheapest" fares aren't guaranteed to be this exact flight;
        # say so in the alert so the price is never misleading.
        src = f"\n<i>via {fare.source} · {fare.note}</i>" if fare.note else ""

        alert = None
        if prev is None:
            alert = (
                f"👀 Now watching <b>{watch.flight_number}</b> "
                f"{watch.origin}→{watch.destination} on {watch.date}.\n"
                f"Current fare: <b>{fare.currency} {price:,.0f}</b>{src}"
            )
        elif price < prev:
            drop = prev - price
            alert = (
                f"📉 <b>Price drop!</b> {watch.flight_number} "
                f"{watch.origin}→{watch.destination} on {watch.date}\n"
                f"{fare.currency} {prev:,.0f} → <b>{fare.currency} {price:,.0f}</b> "
                f"(down {fare.currency} {drop:,.0f}){src}"
            )
        elif watch.target_price is not None and price <= watch.target_price:
            alert = (
                f"🎯 {watch.flight_number} is at or below your target: "
                f"<b>{fare.currency} {price:,.0f}</b> (target "
                f"{fare.currency} {watch.target_price:,.0f}){src}"
            )

        watch.last_price = price
        if alert and chat_id:
            tg.send_message(chat_id, alert)
        elif alert:
            print(f"[warn] would alert but no chat id known: {alert!r}")


def run(cfg: Config, state: State) -> State:
    """One full tick: process commands, then check prices."""
    tg = Telegram(cfg.telegram_token)

    # Commands work regardless of price providers, so process them first.
    force_check = process_updates(tg, state, cfg)

    providers = build_providers(cfg)
    if not providers:
        print("[warn] no usable price providers configured; skipping price checks. "
              "Set TRAVELPAYOUTS_TOKEN and/or install playwright for the scraper.")
        return state

    print(f"[info] price providers: {', '.join(p.name for p in providers)}")
    # A cron tick always checks; /check just makes the intent explicit in logs.
    if force_check:
        print("[info] forced check requested via /check")
    check_prices(tg, providers, state, cfg)
    return state
