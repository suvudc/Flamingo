#!/usr/bin/env bash
set -euo pipefail
collect_diagnostics() {
  mkdir -p runtime-diagnostics
  timeout 10s adb pull /sdcard/Android/data/yos.music.player.artworkfix3/files/interaction-diagnostics runtime-diagnostics/ || true
  timeout 10s adb logcat -d > runtime-diagnostics/logcat.txt || true
  timeout 10s adb exec-out screencap -p > runtime-diagnostics/screen.png || true
}
trap collect_diagnostics EXIT
# Keep the Pixel 7's logical dimensions at half the physical resolution, which
# limits software GPU memory use without changing the layout or touch regions.
timeout 15s adb shell wm size 540x1200
timeout 15s adb shell wm density 210
timeout 15s adb shell svc wifi disable
# Run the already-built packages directly so Gradle does not uninstall the app
# and delete its screenshots before the diagnostics trap can retrieve them.
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb install -r -g app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
checks='yos.music.player.ui.PlayerInteractionTest#fullScreenLyricsKeepTheLowerViewportScrollableAfterControlsHide,yos.music.player.ui.PlayerInteractionTest#fullScreenLyricsScrollWithControlsVisibleAndTheHandleEnabled,yos.music.player.ui.PlayerInteractionTest#handleDragStillWorksInsideItsOwnBounds'
timeout 240s adb shell am instrument -w -r -e class "$checks" \
  yos.music.player.artworkfix3.test/androidx.test.runner.AndroidJUnitRunner | tee runtime-test-results.txt
grep -q 'OK (3 tests)' runtime-test-results.txt
