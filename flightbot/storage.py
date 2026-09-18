"""Persistent state: tracked flights, last-seen prices, and the Telegram
update offset. Serialized to a single JSON file so the GitHub Action can
commit it back to the repo between runs."""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass, field
from pathlib import Path


@dataclass
class Watch:
    """A single tracked flight."""

    carrier: str  # e.g. "6E"
    number: str  # e.g. "732"
    origin: str  # IATA, e.g. "DEL"
    destination: str  # IATA, e.g. "BOM"
    date: str  # departure date, "YYYY-MM-DD"
    chat_id: int  # Telegram chat to notify
    target_price: float | None = None  # alert if fare <= this
    last_price: float | None = None  # most recent fare seen
    currency: str = "INR"
    history: list[list] = field(default_factory=list)  # [[iso_ts, price], ...]

    @property
    def flight_number(self) -> str:
        return f"{self.carrier}{self.number}"

    @property
    def id(self) -> str:
        return f"{self.flight_number}-{self.origin}-{self.destination}-{self.date}"

    def describe(self) -> str:
        line = f"{self.flight_number} {self.origin}→{self.destination} on {self.date}"
        if self.last_price is not None:
            line += f" — last {self.currency} {self.last_price:,.0f}"
        if self.target_price is not None:
            line += f" (target {self.currency} {self.target_price:,.0f})"
        return line


@dataclass
class State:
    watches: dict[str, Watch] = field(default_factory=dict)
    update_offset: int = 0  # Telegram getUpdates offset

    def to_dict(self) -> dict:
        return {
            "update_offset": self.update_offset,
            "watches": {wid: asdict(w) for wid, w in self.watches.items()},
        }

    @classmethod
    def from_dict(cls, data: dict) -> "State":
        watches = {
            wid: Watch(**wdata) for wid, wdata in data.get("watches", {}).items()
        }
        return cls(watches=watches, update_offset=int(data.get("update_offset", 0)))


def load_state(path: Path) -> State:
    if not path.exists():
        return State()
    try:
        return State.from_dict(json.loads(path.read_text()))
    except (json.JSONDecodeError, TypeError, ValueError):
        # Corrupt or partial file: start fresh rather than crash the run.
        return State()


def save_state(path: Path, state: State) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(state.to_dict(), indent=2, sort_keys=True) + "\n")
