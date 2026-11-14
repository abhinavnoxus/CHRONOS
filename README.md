# CHRONOS V3 — Personal Time Operating System (Android)

CHRONOS is being upgraded into a premium, offline-first Android time dashboard built with Kotlin and Jetpack Compose. This branch focuses first on the reusable Luxury Adaptive Glass design system and reliable alarm lifecycle. It is a V3 foundation, not a claim that every module in the full product specification is complete.

## What's in this branch

- Five primary destinations: Home, Plan, Alarms, Focus, and Insights; Settings is available from Home.
- Shared semantic theme tokens, persisted theme/accent/glass/motion preferences, clock format controls, reusable glass surfaces, and reduced-effects styling.
- Home clock with digital/analog/hybrid faces, local date/time, next-alarm information, and working navigation shortcuts.
- Room-backed alarm list with create, edit, repeat weekdays, enable/disable, delete, system alarm tone, vibration, and snooze-duration controls.
- Alarm runtime state separated from the screen lifecycle. Notification content taps and Stop actions use the same explicit, instance-checked dismissal receiver; snooze uses its own AlarmManager PendingIntent so it does not replace the recurring alarm.
- Reboot/time/timezone/permission reconciliation and a non-destructive Room 1→2 migration that adds per-firing alarm-instance state.
- Read-only device calendar screen with a runtime permission explanation, real provider data, loading/error/empty states, and conflict counts.
- Focus/break timer whose deadline is persisted locally and recalculated after backgrounding or process recreation; recorded sessions feed Insights.
- Insights use recorded focus sessions and alarm history only. Calendar time is not reported as actual time spent.
- Android 8.0+ install target (`minSdk 26`), including Android 10/11 devices such as the Realme Narzo 20 Pro.

See [the initial audit](docs/CHRONOS_V3_AUDIT.md) for existing-project findings and constraints.

## Download and install on a Narzo 20 Pro

The lowest-complexity path is the APK artifact produced by GitHub Actions; Android Studio is not required on the phone.

1. Open the repository on GitHub and select **Actions**.
2. Open the newest successful **Build CHRONOS V3 APK** run on this branch.
3. Download the **CHRONOS-V3-APK** artifact and unzip it.
4. On the phone, open the APK using Files/Chrome and approve **Install unknown apps** for that source when Android asks.
5. Launch **CHRONOS**. Allow notifications and exact-alarm access when prompted/needed. On Realme UI, if alarms are delayed, review CHRONOS's auto-launch/background activity and battery settings; those controls differ across Realme UI versions.

The workflow is configured to produce a debug-signed APK with the package suffix `.debug`, so it can be installed beside another CHRONOS package. It is a separate app: it does **not** read or migrate data from an existing unsuffixed CHRONOS installation. Keep the existing app installed if its alarms or settings matter. This is a sideload/test build, not a Play Store release; a successful workflow run is still required before an APK is available.

## Build locally

Requirements: JDK 17, Android SDK Platform 34/build tools 34, and internet access for Gradle dependencies.

```bash
# Debug APK (installable, debug application ID)
./gradlew :androidApp:assembleDebug

# Unit tests
./gradlew :androidApp:testDebugUnitTest

# Install to a connected Android device with USB debugging enabled
./gradlew :androidApp:installDebug
```

The APK output is `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

## Android permissions

| Permission | Purpose |
| --- | --- |
| `SCHEDULE_EXACT_ALARM` | Exact alarm scheduling on supported Android versions; users can review access in Alarm Guard. |
| `POST_NOTIFICATIONS` | Visible alarm notification and notification controls on Android 13+. |
| `USE_FULL_SCREEN_INTENT` | Full-screen ringing interface when Android allows it. A notification remains the fallback. |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Active alarm audio playback. |
| `RECEIVE_BOOT_COMPLETED` | Restore stored alarm schedules after a device restart. |
| `WAKE_LOCK` / `VIBRATE` | Alarm playback and vibration. |
| `READ_CALENDAR` | Optional, read-only device calendar access from Plan. |

Alarm delivery is still subject to Android permission, notification-channel, and manufacturer battery policies. CHRONOS does not claim an operating-system guarantee when access is denied or restricted.

## Current implementation status

### Implemented in this branch (not device-verified here)

- Shared design tokens and a persistent theme/preferences foundation.
- Real Room-backed alarm CRUD and scheduler paths.
- Persistent alarm-instance identity, idempotent notification dismissal, separate snooze scheduling, and reboot/system-change recovery paths.
- Read-only calendar permission/data states.
- Persistent focus timer state and recorded focus-session summaries.
- Real-data Insights summaries and five-section navigation.
- Room migration and CI APK artifact workflow.

### Not completed in this branch

- Automated end-to-end alarm tests (including foreground/background/locked-screen/reboot/OEM cases), notification audio/vibration device verification, and hardware Alarm Guard diagnostics beyond the exact-alarm/notification status shown in UI.
- Reminders and their notification scheduler; sleep/routines; world clock; multi-timer; stopwatch; widgets; dashboard rearrangement; profile management; AI assistant; custom audio import; calendar recurrence-instance expansion; calendar day/week/month/agenda editing; backup/export; accessibility/performance profiling and release certification.
- The full analytics suite and extensive UI/instrumentation tests from the V3 specification.

The Focus timer preserves its deadline and session state, but this branch does not yet issue background phase-completion notifications or suppress other apps' notifications.

## Tests and verification

JUnit regression tests for the pure alarm-time calculator are included. The Android build and tests have **not** been executed in this workspace because JDK/Java and Android SDK are not installed here. The automated GitHub workflow is configured to build an APK on pushes to `main` and `arena/**`; only a successful Actions run confirms the CI build. No on-device performance, notification, audio, vibration, reboot, or OEM test result is claimed.

## Data and repository history

The existing Room database name and entities are retained. Schema upgrades use an explicit migration rather than destructive fallback. Git history has not been rewritten; this V3 work remains on the active Arena branch and does not replace the repository's default branch directly.
