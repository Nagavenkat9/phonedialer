"""Minimal Telegram Bot API client (send messages, drain updates)."""

from __future__ import annotations

from typing import Any

import requests

API_ROOT = "https://api.telegram.org"
TIMEOUT = 30


class Telegram:
    def __init__(self, token: str) -> None:
        self._base = f"{API_ROOT}/bot{token}"

    def _call(self, method: str, **params: Any) -> dict:
        resp = requests.post(f"{self._base}/{method}", json=params, timeout=TIMEOUT)
        resp.raise_for_status()
        data = resp.json()
        if not data.get("ok"):
            raise RuntimeError(f"Telegram {method} failed: {data}")
        return data.get("result", {})

    def send_message(self, chat_id: int, text: str) -> None:
        # HTML parse mode keeps formatting simple and avoids Markdown escaping.
        self._call(
            "sendMessage",
            chat_id=chat_id,
            text=text,
            parse_mode="HTML",
            disable_web_page_preview=True,
        )

    def get_updates(self, offset: int) -> list[dict]:
        """Fetch pending updates. `offset` acknowledges everything before it."""
        return self._call(
            "getUpdates",
            offset=offset,
            timeout=0,  # short poll: cron drains what's queued and moves on
            allowed_updates=["message"],
        )
