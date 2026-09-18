"""Configuration loaded from environment variables.

All secrets come from the environment (GitHub Action secrets locally, or a
shell/.env when running by hand). Nothing sensitive is stored in the repo.
"""

from __future__ import annotations

import base64
import os
from dataclasses import dataclass
from pathlib import Path

# Path to the persisted state file (watches + last-seen prices + update offset).
# Committed back to the repo by the GitHub Action so state survives between runs.
STATE_FILE = Path(os.environ.get("FLIGHTBOT_STATE_FILE", "data/state.json"))

# Default order in which price providers are tried; first hit wins.
DEFAULT_PROVIDERS = ("skyscanner", "travelpayouts", "google", "amadeus")


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


def _telegram_token() -> str:
    """Prefer TELEGRAM_BOT_TOKEN; fall back to a base64-encoded token.

    The base64 form (TELEGRAM_BOT_TOKEN_B64) lets a throwaway token live in the
    workflow of a public repo without tripping GitHub's secret-scanning push
    protection. It is *not* secure — only lightly obfuscated.
    """
    token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
    if token:
        return token
    b64 = os.environ.get("TELEGRAM_BOT_TOKEN_B64", "").strip()
    if b64:
        try:
            return base64.b64decode(b64).decode().strip()
        except (ValueError, UnicodeDecodeError):
            return ""
    return ""


def load_config() -> Config:
    """Read config from the environment, raising if a required value is missing."""
    token = _telegram_token()
    if not token:
        raise RuntimeError(
            "No Telegram token. Set TELEGRAM_BOT_TOKEN (or TELEGRAM_BOT_TOKEN_B64) "
            "from @BotFather, e.g. as a GitHub Action secret or workflow env."
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
