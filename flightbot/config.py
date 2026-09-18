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


@dataclass(frozen=True)
class Config:
    telegram_token: str
    amadeus_key: str
    amadeus_secret: str
    # Restrict who the bot listens to. Optional: if set, only this chat id may
    # issue commands, and alerts go here even for flights added elsewhere.
    allowed_chat_id: int | None
    # Amadeus environment: "test" (default, free but sparse data) or "production".
    amadeus_base_url: str
    # Currency for fare quotes.
    currency: str

    @property
    def has_amadeus(self) -> bool:
        return bool(self.amadeus_key and self.amadeus_secret)


def _amadeus_base_url() -> str:
    env = os.environ.get("AMADEUS_ENV", "test").strip().lower()
    if env in ("prod", "production"):
        return "https://api.amadeus.com"
    return "https://test.api.amadeus.com"


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
        amadeus_key=os.environ.get("AMADEUS_API_KEY", "").strip(),
        amadeus_secret=os.environ.get("AMADEUS_API_SECRET", "").strip(),
        allowed_chat_id=allowed_chat_id,
        amadeus_base_url=_amadeus_base_url(),
        currency=os.environ.get("FLIGHTBOT_CURRENCY", "INR").strip() or "INR",
    )
