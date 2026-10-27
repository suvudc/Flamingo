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
adb shell uiautomator dump /sdcard/music-window.xml
adb pull /sdcard/music-window.xml runtime-diagnostics/window.xml
python scripts/android-runtime-tap.py 'Save Changes' || true
sleep 2
for label in New Library Home; do
  adb shell pidof yos.music.player.music >/dev/null || { echo 'Music exited during navigation'; exit 1; }
  adb shell uiautomator dump /sdcard/music-window.xml
  adb pull /sdcard/music-window.xml runtime-diagnostics/window.xml
  python scripts/android-runtime-tap.py "$label"
  sleep 2
  adb exec-out screencap -p > "runtime-diagnostics/screen-$label.png"
done
adb shell uiautomator dump /sdcard/music-window.xml
adb pull /sdcard/music-window.xml runtime-diagnostics/window.xml
adb shell dumpsys activity activities | grep -Eq '(mResumedActivity|topResumedActivity|ResumedActivity).*yos[.]music[.]player[.]music/.*MainActivity'
echo 'Music stayed open and navigated Home, New and Library on Android 15.'
