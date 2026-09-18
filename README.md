# phonedialer — Flight price watcher

A tiny Telegram bot that watches a flight **route + date** (e.g. DEL→TIR on
2026-09-24, labelled by flight number like 6E732) and messages you when the
fare drops.

It runs entirely on **GitHub Actions** (a cron job every 3 hours) — no server
needed. Each run reads the current cheapest fare, compares it to the last price
seen, and sends a Telegram alert on a drop. State is committed back to
`data/state.json` so it persists between runs.

> Why GitHub Actions and not a hosted agent? Alerts require an outbound call to
> Telegram, and the runner is the piece with open internet access. The schedule
> lives in `.github/workflows/flight-price-watch.yml`.

## Price providers (fallback chain)

Amadeus **discontinued its free self-service tier in July 2026**, so the bot
uses a chain of sources and takes the first that returns a price:

1. **`travelpayouts`** — free [Travelpayouts / Aviasales](https://www.travelpayouts.com/)
   cached-fare API (needs a free token). If the cached data includes your exact
   flight number, it's used; otherwise the cheapest fare for the route/date.
2. **`google`** — best-effort Google Flights scrape via Playwright (no key,
   fragile, used only if Travelpayouts has nothing).
3. **`amadeus`** — optional, only if you have **enterprise** Amadeus credentials.

Each alert shows which source and match type produced the number (e.g.
*via travelpayouts · route cheapest*), so a route-level price is never
mistaken for the exact flight.

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

### 2. Get a free Travelpayouts token
- Sign up at <https://www.travelpayouts.com/> → **Developers / API** → copy your
  **API token**. (Free; the flight-prices data endpoint needs no affiliate
  approval.)
- If you skip this, the bot falls back to the Google Flights scraper only.

### 3. Add repository secrets
In GitHub: **Settings → Secrets and variables → Actions → New repository secret**

| Secret | Value |
| --- | --- |
| `TELEGRAM_BOT_TOKEN` | token from BotFather |
| `TRAVELPAYOUTS_TOKEN` | Travelpayouts API token (recommended) |
| `TELEGRAM_CHAT_ID` | *(optional)* your numeric chat id, to lock alerts to you |
| `AMADEUS_API_KEY` / `AMADEUS_API_SECRET` | *(optional)* enterprise Amadeus only |

Optional **repository variables** (same page, "Variables" tab):

| Variable | Default | Purpose |
| --- | --- | --- |
| `FLIGHTBOT_PROVIDERS` | `travelpayouts,google` | provider order to try |
| `FLIGHTBOT_CURRENCY` | `INR` | quote currency |
| `AMADEUS_ENV` | `production` | `test` or `production` (enterprise only) |

### 4. Run it
- Trigger the first run manually: **Actions → Flight price watch → Run
  workflow**. After that it runs every 3 hours automatically.
- The repo ships with one flight pre-seeded in `data/state.json`
  (**6E732 DEL→TIR on 2026-09-24**). Add or remove flights any time via the
  Telegram commands above.

## Running locally (optional)

```bash
pip install -r requirements.txt
python -m playwright install chromium   # only for the google fallback
export TELEGRAM_BOT_TOKEN=... TRAVELPAYOUTS_TOKEN=...
python -m flightbot        # one tick: process commands + check fares
```

## Layout

```
flightbot/
  config.py    # settings from environment variables
  storage.py   # JSON state: watches, prices, history, update offset
  telegram.py  # Telegram Bot API client
  pricing.py   # provider chain: travelpayouts / google / amadeus
  amadeus.py   # optional Amadeus flight-offers lookup
  core.py      # command handling + price-drop logic
  __main__.py  # `python -m flightbot` entrypoint
.github/workflows/flight-price-watch.yml   # 3-hourly cron
data/state.json                            # persisted state (auto-updated)
```

## Notes & limits
- Free sources track the **route + date**, not a guaranteed seat on the exact
  flight number — good for "when is it cheapest to fly this route that day."
  Travelpayouts data is cached, so it can lag the live fare a little.
- Small airports (e.g. Tirupati/TIR) may have sparse cached data; the scraper
  fallback helps, but scraping can break or get blocked at any time.
- Keep your bot token secret. If it leaks, revoke it in BotFather
  (`/mybots → your bot → API Token → Revoke`).
