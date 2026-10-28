#!/usr/bin/env bash
set -euo pipefail
collect_diagnostics() {
  mkdir -p runtime-diagnostics
  adb pull /sdcard/Android/data/yos.music.player.artworkfix3/files/interaction-diagnostics runtime-diagnostics/ || true
  adb logcat -d > runtime-diagnostics/logcat.txt || true
  adb exec-out screencap -p > runtime-diagnostics/screen.png || true
}
trap collect_diagnostics EXIT
bash gradlew :app:connectedDebugAndroidTest -PmusicSmokeAbi=x86_64 \
  -Pandroid.testInstrumentationRunnerArguments.class=yos.music.player.ui.PlayerInteractionTest --stacktrace
