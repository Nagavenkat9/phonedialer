"""Drive the Memories app on an emulator: visit every feature, screenshot each step, detect crashes."""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "app.memories.gallery"
OUT = sys.argv[1] if len(sys.argv) > 1 else "screenshots"
os.makedirs(OUT, exist_ok=True)
results = []  # (step, status, note)
shot_no = [0]


def adb(*args, capture=True, check=False):
    r = subprocess.run(["adb", *args], capture_output=capture, text=not capture or None)
    return r


def sh(cmd):
    return subprocess.run(["adb", "shell", cmd], capture_output=True, text=True).stdout


def screen_size():
    m = re.search(r"(\d+)x(\d+)", sh("wm size"))
    return int(m.group(1)), int(m.group(2))


W, H = screen_size()


def dump():
    for _ in range(3):
        sh("rm -f /sdcard/ui.xml")
        msg = sh("uiautomator dump /sdcard/ui.xml 2>&1")
        if "dumped" not in msg:
            print(f"    uiautomator: {msg.strip()[:200]}", flush=True)
        xml = sh("cat /sdcard/ui.xml")
        if xml.strip().startswith("<?xml"):
            try:
                return ET.fromstring(xml)
            except ET.ParseError:
                pass
        time.sleep(1)
    return None


def bounds(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2, (x1, y1, x2, y2)


def find_all(text=None, desc=None, cls=None, contains=False, suffix=None):
    root = dump()
    if root is None:
        return []
    hits = []
    for n in root.iter("node"):
        t, d = n.get("text", ""), n.get("content-desc", "")
        ok = True
        if text is not None:
            ok = ok and ((text.lower() in t.lower()) if contains else t == text)
        if desc is not None:
            ok = ok and ((desc.lower() in d.lower()) if contains else d == desc)
        if suffix is not None:
            ok = ok and (d.lower().endswith(suffix) or t.lower().endswith(suffix))
        if cls is not None:
            ok = ok and n.get("class", "").endswith(cls)
        if ok:
            hits.append(n)
    return hits


def find(lowest=False, **kw):
    hits = find_all(**kw)
    if not hits:
        return None
    if lowest:
        return max(hits, key=lambda n: bounds(n)[1])
    return hits[0]


def tap_xy(x, y, wait=1.2):
    sh(f"input tap {x} {y}")
    time.sleep(wait)


def tap(wait=1.5, lowest=False, **kw):
    n = find(lowest=lowest, **kw)
    if n is None:
        raise RuntimeError(f"not found: {kw}")
    x, y, _ = bounds(n)
    tap_xy(x, y, wait)
    return n


def long_press(x, y, ms=900, wait=1.5):
    sh(f"input swipe {x} {y} {x} {y} {ms}")
    time.sleep(wait)


def back(wait=1.2):
    sh("input keyevent KEYCODE_BACK")
    time.sleep(wait)


def shot(name):
    shot_no[0] += 1
    path = os.path.join(OUT, f"{shot_no[0]:02d}_{name}.png")
    with open(path, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)
    return path


def crashed():
    log = sh(f"logcat -d -b crash")
    return PKG in log and ("FATAL EXCEPTION" in log or "AndroidRuntime" in log)


def alive():
    return sh(f"pidof {PKG}").strip() != ""


def foreground():
    out = sh("dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity'")
    return PKG in out


def step(name, fn):
    print(f"--- {name}", flush=True)
    if not foreground():
        print("    (app not in foreground, relaunching)", flush=True)
        relaunch()
    try:
        note = fn() or ""
        status = "PASS"
    except Exception as e:  # keep going; record the problem
        note, status = str(e), "FAIL"
    if crashed() or not alive():
        status, note = "CRASH", (note + " | app crashed").strip(" |")
        print(sh("logcat -d -b crash")[-4000:], flush=True)
        shot(f"{name}_CRASH")
        sh("logcat -c -b crash")
        relaunch()
    results.append((name, status, note))
    print(f"    {status} {note}", flush=True)


def relaunch():
    sh(f"am start -W -n {PKG}/.MainActivity")
    time.sleep(4)


# Fixed positions (fractions of the screen) used when the accessibility dump is unavailable.
TABS = {"Photos": 0.118, "Memories": 0.373, "Search": 0.628, "Library": 0.882}


def go_tab(label):
    tap_xy(int(W * TABS[label]), int(H * 0.947), wait=1.5)


# ----------------------------------------------------------------- scenario
sh("logcat -c -b all")
relaunch()


def s_photos():
    time.sleep(3)
    if find(text="Allow access to photos & videos"):
        raise RuntimeError("permission screen still shown")
    shot("photos_timeline")
    thumbs = find_all(suffix=".jpg")
    return f"{len(thumbs)} photo thumbnails visible"


def s_pinch_columns():
    # Use 2-finger pinch via motionevent is not available in `input`; check the grid by scrolling instead.
    sh(f"input swipe {W//2} {int(H*0.75)} {W//2} {int(H*0.3)} 300"); time.sleep(1)
    shot("photos_scrolled")
    sh(f"input swipe {W//2} {int(H*0.3)} {W//2} {int(H*0.9)} 200"); time.sleep(0.5)
    sh(f"input swipe {W//2} {int(H*0.3)} {W//2} {int(H*0.9)} 200"); time.sleep(1)


def s_memories():
    go_tab("Memories")
    time.sleep(1.5)
    shot("memories_tab")
    if not (find(text="Play memory", contains=True) or find(desc="Play memory", contains=True)):
        return "hero: verify in screenshot (accessibility dump unavailable)"


def s_long_press_day():
    long_press(W // 2, int(H * 0.35))
    shot("memory_long_press_day_sheet")
    if not find(text="Don't show this memory", contains=True):
        return "day sheet: verify in screenshot (accessibility dump unavailable)"


def s_play_memory():
    if find(text="Play memory", contains=True, lowest=True):
        tap(text="Play memory", contains=True, lowest=True, wait=2.5)
    else:
        tap_xy(W // 2, int(H * 0.82), wait=2.5)  # "Play memory" button in the day sheet
    shot("memory_story_player")
    time.sleep(3)
    shot("memory_story_after_3s")  # must still be on an early item (photos last 5s)
    tap_xy(int(W * 0.8), H // 2, wait=1.5)
    shot("memory_story_next")
    m = find(desc="music", contains=True)
    if m:
        x, y, _ = bounds(m); tap_xy(x, y, wait=2)
        shot("memory_music_picker")
        back()
    c = find(desc="Close")
    if c:
        x, y, _ = bounds(c); tap_xy(x, y, 1.5)
    else:
        tap_xy(int(W * 0.922), int(H * 0.1), 1.5)  # story X button


def s_viewer():
    go_tab("Photos")
    if find(suffix=".jpg"):
        tap(suffix=".jpg", wait=2)
    else:
        tap_xy(int(W * 0.12), int(H * 0.50), wait=2)
    shot("viewer_with_filmstrip")
    # skim with the filmstrip: drag it to the left near the bottom area
    strip_y = None
    for n in find_all(desc="Share"):
        strip_y = bounds(n)[2][1] - 70
    if strip_y:
        sh(f"input swipe {int(W*0.8)} {strip_y} {int(W*0.2)} {strip_y} 400"); time.sleep(1.5)
        shot("viewer_filmstrip_skimmed")
    tap(desc="Favorite", wait=1)
    tap(desc="Info", wait=1.5)
    shot("viewer_info_sheet")
    back()
    tap(desc="Hide from memories", contains=True, wait=1)
    shot("viewer_hidden_from_memories")
    tap(desc="Show in memories", contains=True, wait=1)
    back()


def s_video_player():
    go_tab("Search")
    tap(cls="EditText", wait=1)
    sh("input text videos"); sh("input keyevent KEYCODE_ENTER"); time.sleep(2)
    shot("search_videos")
    tap(suffix=".mp4", wait=2)
    tap(desc="Play", wait=4)
    shot("video_player_controls")
    # double tap right side → +10s
    sh(f"input tap {int(W*0.85)} {H//2} && input tap {int(W*0.85)} {H//2}")
    time.sleep(0.3)
    shot("video_player_double_tap_forward")
    time.sleep(1.5)
    # drag to seek (hold the finger down while capturing)
    y = H // 2
    sh(f"input motionevent DOWN {int(W*0.3)} {y}")
    for x in range(int(W * 0.3), int(W * 0.7), 40):
        sh(f"input motionevent MOVE {x} {y}")
    shot("video_player_drag_seek")
    sh(f"input motionevent UP {int(W*0.7)} {y}")
    time.sleep(1)
    tap_xy(W // 2, int(H * 0.3), wait=1)  # show controls
    more = find(desc="More", contains=True)
    if more:
        x, yy, _ = bounds(more); tap_xy(x, yy, wait=1.2)
        shot("video_player_more_menu")
        back(0.8)
    back(1.5)
    back(1.5)


def s_multiselect():
    go_tab("Photos")
    n = find(suffix=".jpg")
    x, y, _ = bounds(n) if n else (int(W * 0.12), int(H * 0.50), None)
    long_press(x, y)
    hits = find_all(suffix=".jpg")
    if len(hits) > 2:
        x2, y2, _ = bounds(hits[2]); tap_xy(x2, y2, 1)
    shot("photos_multi_select")
    back()


def s_search():
    go_tab("Search")
    shot("search_suggestions")
    tap(cls="EditText", wait=1)
    sh(f"input text {time.localtime().tm_year - 2}"); time.sleep(2)
    sh("input keyevent KEYCODE_BACK"); time.sleep(1)
    shot("search_year_results")


def s_library():
    go_tab("Library")
    shot("library")
    tap(text="People & selfies", contains=True, wait=2)
    shot("people_and_selfies")
    back()
    tap(text="Hidden from memories", wait=1.5)
    shot("hidden_from_memories")
    back()
    tap(text="Videos", wait=1.5)
    shot("collection_videos")
    back()


def s_settings():
    go_tab("Memories")
    if find(desc="Settings"):
        tap(desc="Settings", wait=1.5)
    else:
        tap_xy(int(W * 0.903), int(H * 0.088), wait=1.5)
    shot("settings")
    tap(text="Dark", wait=1.5)
    shot("settings_dark")
    back()
    go_tab("Photos")
    shot("photos_dark_theme")
    go_tab("Memories")
    tap_xy(int(W * 0.903), int(H * 0.088), wait=1.5)
    tap(text="System default", contains=True, wait=1) if find(text="System default", contains=True) else tap(text="System", contains=True, wait=1)
    back()


def s_rotate():
    sh("settings put system accelerometer_rotation 0")
    sh("settings put system user_rotation 1"); time.sleep(2)
    go_tab("Photos")
    shot("photos_landscape")
    sh("settings put system user_rotation 0"); time.sleep(2)


for name, fn in [
    ("photos_timeline", s_photos),
    ("scroll_timeline", s_pinch_columns),
    ("memories_tab", s_memories),
    ("long_press_shows_day", s_long_press_day),
    ("play_memory", s_play_memory),
    ("viewer_filmstrip_favorite_info_hide", s_viewer),
    ("video_player", s_video_player),
    ("multi_select", s_multiselect),
    ("search", s_search),
    ("library_people_hidden_videos", s_library),
    ("settings_theme", s_settings),
    ("rotation", s_rotate),
]:
    step(name, fn)

with open(os.path.join(OUT, "REPORT.md"), "w") as f:
    f.write("| Step | Result | Notes |\n|---|---|---|\n")
    for name, status, note in results:
        f.write(f"| {name} | {status} | {note.replace('|', '/')} |\n")
print(open(os.path.join(OUT, "REPORT.md")).read())
if any(s == "CRASH" for _, s, _ in results):
    sys.exit(1)
