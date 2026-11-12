#!/usr/bin/env bash
# Runs inside the emulator: install app, load sample media, grant permissions, drive the UI.
set -x
PKG=app.memories.gallery
APK=$(ls apk/*.apk | head -1)
adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb install -r -g "$APK"

adb shell mkdir -p /sdcard/DCIM/Camera
for f in sample_media/*; do adb push "$f" /sdcard/DCIM/Camera/ >/dev/null; done
# Ask MediaStore to index the new files.
adb shell content call --uri content://media/ --method scan_volume --arg external_primary || true
for f in sample_media/*; do
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/DCIM/Camera/$(basename "$f")" >/dev/null || true
done
sleep 8
echo "images indexed: $(adb shell content query --uri content://media/external/images/media --projection _id | grep -c Row)"
echo "videos indexed: $(adb shell content query --uri content://media/external/video/media --projection _id | grep -c Row)"

for p in READ_MEDIA_IMAGES READ_MEDIA_VIDEO READ_MEDIA_AUDIO ACCESS_MEDIA_LOCATION POST_NOTIFICATIONS; do
  adb shell pm grant $PKG android.permission.$p || true
done

python3 .github/scripts/ui_check.py screenshots
STATUS=$?
adb logcat -d > screenshots/logcat.txt || true
exit $STATUS
