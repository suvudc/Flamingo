#!/usr/bin/env bash
set -euo pipefail
collect_diagnostics() {
  mkdir -p runtime-diagnostics
  adb pull /sdcard/Android/data/yos.music.player.artworkfix3/files/interaction-diagnostics runtime-diagnostics/ || true
  adb logcat -d > runtime-diagnostics/logcat.txt || true
  adb exec-out screencap -p > runtime-diagnostics/screen.png || true
}
trap collect_diagnostics EXIT
# Run the already-built packages directly so Gradle does not uninstall the app
# and delete its screenshots before the diagnostics trap can retrieve them.
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb install -r -g app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
checks='yos.music.player.ui.PlayerInteractionTest#fullScreenLyricsKeepTheLowerViewportScrollableAfterControlsHide,yos.music.player.ui.PlayerInteractionTest#fullScreenLyricsScrollWithControlsVisibleAndTheHandleEnabled,yos.music.player.ui.PlayerInteractionTest#handleDragStillWorksInsideItsOwnBounds'
timeout 240s adb shell am instrument -w -r -e class "$checks" \
  yos.music.player.artworkfix3.test/androidx.test.runner.AndroidJUnitRunner | tee runtime-test-results.txt
grep -q 'OK (3 tests)' runtime-test-results.txt
