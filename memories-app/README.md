# Memories — a gallery that remembers

An Android gallery app that looks and feels like Google Photos but puts **memories**
front and centre, with an **MX-Player-style** video player.

## Get the app

1. Open the **Actions** tab on GitHub → **Memories Android app** → latest green run.
2. Download the **memories-apk** artifact, unzip it, and open `app-release.apk` on your phone
   (allow "Install unknown apps" for your browser/file manager when asked).

Or open `memories-app/` in Android Studio and press **Run**.

Requires Android 8.0 (API 26) or newer.

## What's inside

### Photos (Google Photos look)
- Timeline grouped by day — *Today*, *Yesterday*, *Sat, 14 Aug 2021*…
- Memories carousel across the top.
- **Pinch the grid** to show more or fewer columns.
- Video duration and favorite badges on thumbnails.
- Full-screen viewer: swipe between items, pinch / double-tap zoom, share, favorite,
  hide-from-memories, and an info sheet (date, how long ago, size, resolution, album).

### Memories (the main part)
- **1 year ago, 2 years ago, 3 years ago…** for today's date (falls back to "this week" when
  that exact day has nothing).
- **Your favorites**, **Remember this day?** (a different favorite every day),
  **Favorites from 2021**, **A day to remember**, **Best of August 2022**,
  **Videos from 2023**, **Recent highlights**, **Throwback to 2019**.
- **Favorites show up often**: they get their own memories, are always kept when a memory is
  trimmed, are preferred as covers, and boost a memory's ranking.
- Memories rotate daily but stay stable during the day.
- **Long-press any memory** → see *the day*: weekday, full date, how many years ago,
  photo/video counts, a preview, and every day it covers.
- **Tap a memory** → story player: photos with a slow Ken-Burns zoom, videos play inline,
  tap left/right to move, press-and-hold to pause, swipe down to close, "Full video" opens
  the player.
- **Remove from memories but keep it**: the eye-off button removes a photo/video from
  memories only (with Undo). It stays in your gallery. You can also dismiss a whole memory.
  Manage/restore everything in **Library → Hidden from memories**.

### Video player (MX Player style)
- **Drag left/right** anywhere to seek, with a live `+0:15  (1:23 / 10:00)` preview.
- **Double-tap** left/right to skip **10 s** back/forward — keep tapping to add +10 s more.
- Double-tap the centre to play/pause.
- **Swipe up/down** on the left for brightness, on the right for volume.
- **Pinch to zoom**; aspect button cycles Fit / Crop / Stretch.
- **Press and hold** for temporary 2x speed; speed menu 0.25x – 3x.
- **Lock** the screen, **rotate** button, auto-rotates to the video's orientation.
- **Resumes** where you left off.

### Library
- Favorites, Videos, Hidden from memories, and all device albums.

Everything stays on the device — nothing is uploaded.

## Tech
Kotlin · Jetpack Compose (Material 3, dynamic colour, dark mode) · Media3 ExoPlayer · Coil.
Memory generation lives in `app/src/main/java/app/memories/gallery/memories/MemoryEngine.kt`.
