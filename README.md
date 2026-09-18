# phonedialer — Flight price watcher

A tiny Telegram bot that watches a **specific flight** (by flight number +
route + date) and messages you when the fare drops.

It runs entirely on **GitHub Actions** (a cron job every 3 hours) — no server
needed. Each run reads the current fare from the [Amadeus flight-offers
API](https://developers.amadeus.com/), compares it to the last price seen, and
sends a Telegram alert on a drop. State is committed back to `data/state.json`
so it persists between runs.

> Why GitHub Actions and not a hosted agent? Alerts require an outbound call to
> Telegram, and the runner is the piece with open internet access. The schedule
> lives in `.github/workflows/flight-price-watch.yml`.

## Commands (via Telegram)

| Command | What it does |
| --- | --- |
| `/track FLIGHT ORIGIN DEST YYYY-MM-DD [target]` | Watch a flight, e.g. `/track 6E732 DEL TIR 2026-09-24 4500` |
| `/list` | Show tracked flights and last-seen prices |
| `/remove ID\|number` | Stop watching one (id from `/list`, or its list number) |
| `/check` | Note a manual check (a check runs every tick regardless) |
| `/help` | Usage |

Commands are processed on each cron tick, so replies arrive within the polling
interval, not instantly.

## One-time setup

### 1. Create the Telegram bot
- In Telegram, message [@BotFather](https://t.me/BotFather) → `/newbot`, pick a
  name and username. It gives you a **bot token**.
- Open your new bot and send it any message (e.g. `hi`). The first run captures
  your chat id from that message so it knows where to send alerts.

### 2. Get free Amadeus API credentials
- Sign up at <https://developers.amadeus.com/> → create an app → copy the
  **API Key** and **API Secret**.
- New apps start in the **test** environment (limited, cached data). For real
  live fares, move the app to **production** in the Amadeus dashboard and set
  the `AMADEUS_ENV` repository variable to `production` (see below).

### 3. Add repository secrets
In GitHub: **Settings → Secrets and variables → Actions → New repository secret**

| Secret | Value |
| --- | --- |
| `TELEGRAM_BOT_TOKEN` | token from BotFather |
| `AMADEUS_API_KEY` | Amadeus API key |
| `AMADEUS_API_SECRET` | Amadeus API secret |
| `TELEGRAM_CHAT_ID` | *(optional)* your numeric chat id, to lock alerts to you |

Optional **repository variables** (same page, "Variables" tab):

| Variable | Default | Purpose |
| --- | --- | --- |
| `AMADEUS_ENV` | `test` | set to `production` for live fares |
| `FLIGHTBOT_CURRENCY` | `INR` | quote currency |

### 4. Run it
- Trigger the first run manually: **Actions → Flight price watch → Run
  workflow**. After that it runs every 3 hours automatically.
- The repo ships with one flight pre-seeded in `data/state.json`
  (**6E732 DEL→TIR on 2026-09-24**). Add or remove flights any time via the
  Telegram commands above.

## Running locally (optional)

```bash
pip install -r requirements.txt
export TELEGRAM_BOT_TOKEN=... AMADEUS_API_KEY=... AMADEUS_API_SECRET=...
python -m flightbot        # one tick: process commands + check fares
```

## Layout

```
flightbot/
  config.py    # settings from environment variables
  storage.py   # JSON state: watches, prices, history, update offset
  telegram.py  # Telegram Bot API client
  amadeus.py   # Amadeus flight-offers lookup (fare for a specific flight)
  core.py      # command handling + price-drop logic
  __main__.py  # `python -m flightbot` entrypoint
.github/workflows/flight-price-watch.yml   # 3-hourly cron
data/state.json                            # persisted state (auto-updated)
```

## Notes & limits
- The Amadeus **test** environment often lacks real fares for domestic Indian
  low-cost carriers; use **production** for dependable results.
- A "specific flight" is matched by finding non-stop offers whose segment
  carrier + number equals yours (e.g. `6E` + `732`), taking the cheapest.
- Keep your bot token secret. If it leaks, revoke it in BotFather
  (`/mybots → your bot → API Token → Revoke`).
