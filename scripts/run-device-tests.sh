#!/usr/bin/env bash
set -o pipefail
./gradlew --no-daemon :androidApp:connectedDebugAndroidTest --stacktrace 2>&1 | tee "$RUNNER_TEMP/device-gradle.log"
result=${PIPESTATUS[0]}
if [[ "$result" != 0 ]]; then
  adb logcat -d -t 2000 > "$RUNNER_TEMP/device-logcat.txt" || true
fi
exit "$result"
