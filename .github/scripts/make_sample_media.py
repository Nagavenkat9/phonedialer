"""Generate sample photos/videos with realistic dates (and some GPS) for the UI check."""
import datetime as dt
import os
import random
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFont
import piexif

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
today = dt.date.today()
random.seed(7)

PALETTES = [
    ((255, 183, 77), (229, 57, 53)), ((129, 212, 250), (21, 101, 192)), ((165, 214, 167), (46, 125, 50)),
    ((206, 147, 216), (106, 27, 154)), ((255, 241, 118), (245, 124, 0)), ((128, 222, 234), (0, 131, 143)),
    ((248, 187, 208), (194, 24, 91)), ((188, 170, 164), (78, 52, 46)),
]
try:
    BIG = ImageFont.load_default(size=64)
except TypeError:  # Pillow < 10.1
    BIG = ImageFont.load_default()


def deg(v):
    v = abs(v)
    d = int(v); m = int((v - d) * 60); s = round(((v - d) * 60 - m) * 60 * 100)
    return ((d, 1), (m, 1), (s, 100))


def photo(name, when, label, gps=None, portrait=False):
    w, h = (1080, 1440) if portrait else (1440, 1080)
    a, b = random.choice(PALETTES)
    img = Image.new("RGB", (w, h))
    px = ImageDraw.Draw(img)
    for y in range(h):
        t = y / h
        px.line([(0, y), (w, y)], fill=tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3)))
    for _ in range(6):
        r = random.randint(60, 260); x = random.randint(0, w); y = random.randint(0, h)
        px.ellipse([x - r, y - r, x + r, y + r], fill=tuple(min(255, c + 40) for c in a))
    px.rectangle([40, h - 200, w - 40, h - 60], fill=(0, 0, 0))
    px.text((70, h - 170), label, fill=(255, 255, 255), font=BIG)
    stamp = when.strftime("%Y:%m:%d %H:%M:%S")
    exif = {"0th": {}, "Exif": {piexif.ExifIFD.DateTimeOriginal: stamp, piexif.ExifIFD.DateTimeDigitized: stamp}, "GPS": {}}
    if gps:
        lat, lon = gps
        exif["GPS"] = {
            piexif.GPSIFD.GPSLatitudeRef: "N" if lat >= 0 else "S", piexif.GPSIFD.GPSLatitude: deg(lat),
            piexif.GPSIFD.GPSLongitudeRef: "E" if lon >= 0 else "W", piexif.GPSIFD.GPSLongitude: deg(lon),
        }
    path = os.path.join(out, name)
    img.save(path, "JPEG", quality=85, exif=piexif.dump(exif))
    ts = when.timestamp(); os.utime(path, (ts, ts))


def video(name, when, seconds=24):
    path = os.path.join(out, name)
    subprocess.run([
        "ffmpeg", "-y", "-loglevel", "error",
        "-f", "lavfi", "-i", f"testsrc2=duration={seconds}:size=1280x720:rate=30",
        "-f", "lavfi", "-i", f"sine=frequency=440:duration={seconds}",
        "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest",
        "-metadata", f"creation_time={when.strftime('%Y-%m-%dT%H:%M:%S')}Z", path,
    ], check=True)
    ts = when.timestamp(); os.utime(path, (ts, ts))


HOME = (17.385, 78.486)   # Hyderabad
TRIP = (15.299, 74.124)   # Goa (~450 km away)
n = 0
def at(day, hour=12):
    return dt.datetime.combine(day, dt.time(hour, random.randint(0, 59)))

# On this day: 1, 2, 3 years ago
for years in (1, 2, 3):
    d = today.replace(year=today.year - years) if not (today.month == 2 and today.day == 29) else today - dt.timedelta(days=365 * years)
    for i in range(5):
        n += 1; photo(f"IMG_otd{years}_{i}.jpg", at(d, 9 + i), f"{years} year(s) ago #{i+1}", gps=HOME, portrait=i % 2 == 0)
    video(f"VID_otd{years}.mp4", at(d, 18))
# A trip 2 years ago (5 days, far from home)
start = today.replace(year=today.year - 2) - dt.timedelta(days=60)
for day in range(5):
    for i in range(4):
        n += 1; photo(f"IMG_trip_{day}_{i}.jpg", at(start + dt.timedelta(days=day), 10 + i), f"Goa day {day+1}", gps=(TRIP[0] + random.uniform(-.05, .05), TRIP[1] + random.uniform(-.05, .05)))
# A busy day 4 years ago (day to remember)
big = today.replace(year=today.year - 4) - dt.timedelta(days=100)
for i in range(10):
    n += 1; photo(f"IMG_party_{i}.jpg", at(big, 10 + i % 10), f"Birthday #{i+1}", gps=HOME)
# Recent days
for back in range(0, 14):
    d = today - dt.timedelta(days=back)
    for i in range(random.randint(1, 3)):
        n += 1; photo(f"IMG_recent_{back}_{i}.jpg", at(d, 8 + i * 3), f"{d.isoformat()}", gps=HOME)
video("VID_recent.mp4", at(today - dt.timedelta(days=1), 20), seconds=40)
print(f"generated {n} photos + 4 videos in {out}")
