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
python scripts/android-runtime-fixture.py
adb shell mkdir -p /sdcard/Music
adb push runtime-diagnostics/MusicRuntime.mp3 /sdcard/Music/MusicRuntime.mp3
adb push runtime-diagnostics/MusicRuntime.ttml /sdcard/Music/MusicRuntime.ttml
adb shell appops set yos.music.player.music MANAGE_EXTERNAL_STORAGE allow
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Music/MusicRuntime.mp3
# Wait for indexing and mark this synthetic file as music before Music's first scan.
sleep 4
adb shell content query --uri content://media/external/audio/media --projection _id:title > runtime-diagnostics/media-before.txt
fixture_id=$(python - <<'PY_ID'
import re
from pathlib import Path
s = Path('runtime-diagnostics/media-before.txt').read_text()
for line in s.splitlines():
    if 'MusicRuntime' in line:
        match = re.search(r'_id=(\d+)', line)
        if match:
            print(match.group(1))
            break
PY_ID
)
if [ -n "$fixture_id" ]; then
  # A numerical item URI avoids the shell stripping SQL string quotes from --where.
  adb shell content update --uri "content://media/external/audio/media/$fixture_id" --bind is_music:i:1 --bind duration:l:64000 --bind mime_type:s:audio/mpeg
fi
adb shell content query --uri content://media/external/audio/media --projection _id:title:is_music:duration:mime_type > runtime-diagnostics/media-store.txt
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
python scripts/android-runtime-tap.py 'New'
sleep 2
dump_ui
python scripts/android-runtime-tap.py 'MusicRuntime'
sleep 3
dump_ui
python scripts/android-runtime-tap.py 'MusicRuntime'
sleep 2
dump_ui
adb exec-out screencap -p > runtime-diagnostics/screen-player.png
adb shell svc wifi disable
adb shell svc data disable
python scripts/android-runtime-tap.py 'Open lyrics'
sleep 3
dump_ui
python - <<'PY_WORDS'
import xml.etree.ElementTree as ET
root = ET.parse('runtime-diagnostics/window.xml').getroot()
assert any('Offline lyrics test' in (n.get('content-desc', '') + n.get('text', '')) for n in root.iter('node')), 'Timed fixture lyrics did not render'
print('Local timed lyrics rendered in the full lyrics view')
PY_WORDS
adb exec-out screencap -p > runtime-diagnostics/screen-lyrics.png
# Lyrics must stop above the slider/control region rather than drawing behind it.
# Keep XML and a short recording for visual inspection at normal animation speed.
adb shell screenrecord --time-limit 4 /sdcard/music-lyrics.mp4
adb pull /sdcard/music-lyrics.mp4 runtime-diagnostics/lyrics-animation.mp4
adb shell dumpsys activity activities | grep -Eq '(mResumedActivity|topResumedActivity|ResumedActivity).*yos[.]music[.]player[.]music/.*MainActivity'
echo 'Music stayed open and navigated Home, New and Library on Android 15.'
