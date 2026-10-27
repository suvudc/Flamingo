#!/usr/bin/env bash
set -euo pipefail
mkdir -p runtime-diagnostics
capture() {
  adb logcat -d -v threadtime > runtime-diagnostics/logcat.txt || true
  adb shell dumpsys activity activities > runtime-diagnostics/activities.txt || true
  adb exec-out screencap -p > runtime-diagnostics/screen.png || true
}
trap capture EXIT
apk=$(find app/build/outputs/apk/optimized -name '*.apk' -print -quit)
adb install -r -g "$apk"
adb shell cmd uimode night yes
adb logcat -c
adb shell am force-stop yos.music.player.music
adb shell am start -W -n yos.music.player.music/yos.music.player.MainActivity
sleep 6
for attempt in 1 2 3; do
  adb shell pidof yos.music.player.music >/dev/null || { echo 'Music main process exited after launch'; exit 1; }
  sleep 2
done
dump_ui() {
  adb shell uiautomator dump /sdcard/music-window.xml
  adb pull /sdcard/music-window.xml runtime-diagnostics/window.xml
  # The emulator launcher can ANR while Android performs its first-boot indexing.
  # Dismiss only that named app's system dialog; never dismiss a Music ANR.
  if python - <<'PY_LAUNCHER'
import xml.etree.ElementTree as ET
root = ET.parse('runtime-diagnostics/window.xml').getroot()
raise SystemExit(0 if any(n.get('text') == "Pixel Launcher isn't responding" for n in root.iter('node')) else 1)
PY_LAUNCHER
  then
    python scripts/android-runtime-tap.py 'Close app'
    sleep 2
    adb shell uiautomator dump /sdcard/music-window.xml
    adb pull /sdcard/music-window.xml runtime-diagnostics/window.xml
  fi
}
# First launch can still be animating the setup sheet on slow virtual GPUs.
for attempt in 1 2 3; do
  dump_ui
  if ! python scripts/android-runtime-tap.py 'Save Changes'; then break; fi
  sleep 4
done
for label in New Library Home; do
  adb shell pidof yos.music.player.music >/dev/null || { echo 'Music exited during navigation'; exit 1; }
  adb shell uiautomator dump /sdcard/music-window.xml
  adb pull /sdcard/music-window.xml runtime-diagnostics/window.xml
  python scripts/android-runtime-tap.py "$label"
  sleep 2
  adb exec-out screencap -p > "runtime-diagnostics/screen-$label.png"
done
dump_ui
python scripts/android-runtime-tap.py 'Music'
sleep 3
dump_ui
python scripts/android-runtime-tap.py 'More song options'
sleep 3
dump_ui
python - <<'PY_CHECK'
import xml.etree.ElementTree as ET
root = ET.parse('runtime-diagnostics/window.xml').getroot()
assert any(n.get('text') == 'Sleep Timer' for n in root.iter('node')), 'Player overflow did not open'
print('Player overflow menu opened from its visible button')
PY_CHECK
adb exec-out screencap -p > runtime-diagnostics/screen-overflow.png
adb shell input keyevent 4
sleep 1
dump_ui
python scripts/android-runtime-tap.py 'More song options'
sleep 1
dump_ui
python scripts/android-runtime-tap.py 'Lyrics provider'
sleep 1
dump_ui
python scripts/android-runtime-tap.py 'Automatic'
sleep 2
# Swipe from the middle of the artwork, not the top handle.
adb shell input swipe 500 1000 500 2050 350
sleep 2
dump_ui
python - <<'PY_DISMISS'
import xml.etree.ElementTree as ET
root = ET.parse('runtime-diagnostics/window.xml').getroot()
texts = [n.get('text') for n in root.iter('node')]
assert 'Home' in texts and 'Library' in texts, 'Mid-player swipe did not minimize'
print('Mid-player swipe minimized; provider picker opened')
PY_DISMISS
adb exec-out screencap -p > runtime-diagnostics/screen-minimized.png
adb shell dumpsys activity activities | grep -Eq '(mResumedActivity|topResumedActivity|ResumedActivity).*yos[.]music[.]player[.]music/.*MainActivity'
echo 'Music stayed open and navigated Home, New and Library on Android 15.'
