# CHRONOS V4 release candidate

**Do not distribute as stable yet.** Version code 4 / version name 4.0.0-rc.1. This branch is a stabilization candidate; alarm behavior on actual hardware is a release blocker.

## Build and signing

The `CHRONOS V4 validation` workflow runs clean unit tests, lint, debug APK, release APK and AAB. Download the artifacts from the successful workflow run for the exact commit under test. The debug APK is signed with the CI debug key and can be installed for testing; it is not a Play Store release. Release outputs are **unsigned** unless the following repository secrets have been configured by an authorized maintainer: `CHRONOS_KEYSTORE_BASE64`, `CHRONOS_KEYSTORE_PASSWORD`, `CHRONOS_KEY_ALIAS`, `CHRONOS_KEY_PASSWORD`. The workflow writes the keystore only under the runner's temporary directory. Use an existing protected release key; do not commit keystores, passwords, or signing properties. Do not upload an unsigned AAB as a release. Verify signer and checksums independently before distribution.

## Release gates outstanding

- Run device/emulator tests on supported Android versions (including API 26 and 34) and physical OEM devices: lock-screen and notification-tap dismissal of the exact alarm, simultaneous alarms, snooze, process death, reboot, time-zone/DST changes, exact-alarm permissions, unavailable ringtones, audio/vibration and notification permissions. Unit tests cannot certify these OS behaviors.
- The manually dispatched instrumentation workflow exists on this branch, but GitHub only registers workflow dispatch for workflows on the default branch. It could not be dispatched without changing the default branch; run it after an approved integration or from an authorized Android environment. Its tests have not been executed.
- Migration test reconstructs the version-2 table set from current unchanged entity definitions and removes the added table before migration; it is not a test against an exported historical v2 schema/database fixture. Verify upgrade with a genuine V3 install and backup first.
- Focus phase catch-up happens when the screen is reopened, not as a guaranteed background phase notification. Countdown recovery after an elapsed deadline/reboot, widget launcher refresh, and all OEM power-management behaviors need device verification.
- No full accessibility, localization, large-font, reduced-motion, audio-focus or battery-management audit has been performed. Full-screen intent restrictions and notification permissions can prevent prominent presentation on recent Android versions.
- Re-run all checks on the exact proposed release commit. CI logs and artifacts are retained temporarily by GitHub; record hashes in the release notes. Do not merge into the default branch without approval.

## Delivery scope and known gaps

The optional advisory on Insights uses cached event and recorded session data; it never changes user data. The stopwatch restores a snapshot paused after process recreation; it is not a continuously running foreground stopwatch across process death. World clocks use system zone data. There is one resizable widget, not separate widget products. Routine notifications are schedules, not tracked routine-completion analytics. Alarm challenges are legacy model fields, not implemented as a dismissal requirement. Ringtone fallback playback has not been exercised on hardware; no audio-focus behavior is claimed. Release readiness requires testing every critical path, not just successful compilation.

Lint currently reports that several Compose custom lint checks are skipped due to an incompatible lint API, even though `lintDebug` completes. Resolve the toolchain compatibility before treating lint as comprehensive.
