"""CLI entrypoint: `python -m flightbot`.

Runs one tick (process Telegram commands + check fares) and persists state.
Intended to be called on a schedule by the GitHub Actions workflow.
"""

from __future__ import annotations

import sys

from .config import STATE_FILE, load_config
from .core import run
from .storage import load_state, save_state


def main() -> int:
    try:
        cfg = load_config()
    except RuntimeError as exc:
        print(f"[error] {exc}", file=sys.stderr)
        return 1

    state = load_state(STATE_FILE)
    state = run(cfg, state)
    save_state(STATE_FILE, state)
    print(f"[ok] state saved to {STATE_FILE} ({len(state.watches)} watch(es))")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
