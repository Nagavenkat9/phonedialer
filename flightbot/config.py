"""Configuration loaded from environment variables.

All secrets come from the environment (GitHub Action secrets locally, or a
shell/.env when running by hand). Nothing sensitive is stored in the repo.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

# Path to the persisted state file (watches + last-seen prices + update offset).
# Committed back to the repo by the GitHub Action so state survives between runs.
STATE_FILE = Path(os.environ.get("FLIGHTBOT_STATE_FILE", "data/state.json"))

# Default order in which price providers are tried; first hit wins.
DEFAULT_PROVIDERS = ("travelpayouts", "google", "amadeus")


@dataclass(frozen=True)
class Config:
    telegram_token: str
    # Restrict who the bot listens to. Optional: if set, only this chat id may
    # issue commands, and alerts go here even for flights added elsewhere.
    allowed_chat_id: int | None
    # Currency for fare quotes.
    currency: str
    # Ordered list of provider names to try (see pricing.build_providers).
    providers: tuple[str, ...]
    # Travelpayouts (Aviasales) API token — free after signup.
    travelpayouts_token: str
    # Amadeus (Enterprise) credentials — optional; only used if present.
    amadeus_key: str
    amadeus_secret: str
    amadeus_base_url: str

    @property
    def has_amadeus(self) -> bool:
        return bool(self.amadeus_key and self.amadeus_secret)

    @property
    def has_travelpayouts(self) -> bool:
        return bool(self.travelpayouts_token)


def _amadeus_base_url() -> str:
    env = os.environ.get("AMADEUS_ENV", "production").strip().lower()
    if env in ("test", "sandbox"):
        return "https://test.api.amadeus.com"
    return "https://api.amadeus.com"


def _providers() -> tuple[str, ...]:
    raw = os.environ.get("FLIGHTBOT_PROVIDERS", "").strip()
    if not raw:
        return DEFAULT_PROVIDERS
    names = tuple(p.strip().lower() for p in raw.split(",") if p.strip())
    return names or DEFAULT_PROVIDERS


def load_config() -> Config:
    """Read config from the environment, raising if a required value is missing."""
    token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
    if not token:
        raise RuntimeError(
            "TELEGRAM_BOT_TOKEN is not set. Create a bot with @BotFather and "
            "export the token (or add it as a GitHub Action secret)."
        )

    chat_raw = os.environ.get("TELEGRAM_CHAT_ID", "").strip()
    allowed_chat_id = int(chat_raw) if chat_raw else None

    return Config(
        telegram_token=token,
        allowed_chat_id=allowed_chat_id,
        currency=os.environ.get("FLIGHTBOT_CURRENCY", "INR").strip() or "INR",
        providers=_providers(),
        travelpayouts_token=os.environ.get("TRAVELPAYOUTS_TOKEN", "").strip(),
        amadeus_key=os.environ.get("AMADEUS_API_KEY", "").strip(),
        amadeus_secret=os.environ.get("AMADEUS_API_SECRET", "").strip(),
        amadeus_base_url=_amadeus_base_url(),
    )
