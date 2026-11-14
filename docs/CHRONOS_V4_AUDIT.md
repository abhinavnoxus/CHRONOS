# CHRONOS V4 — Baseline Audit (performed before implementation)

Audit target: branch `arena/8343f54c-chronos` @ `ac6f7df` ("Fix Compose lifecycle imports and material opt-ins"), which carries the V3 foundation. `master` still holds V2 (`d2c3116`, versionCode 2 / minSdk 31). No Git tags or GitHub releases existed at audit time.

## Environment constraints (honest limits)

- This workspace has **no JDK, no Android SDK, and no emulator/device**.
- Outbound network is restricted to `github.com`, `api.github.com`, `codeload.github.com`, `registry.npmjs.org`, `pypi.org`, `files.pythonhosted.org`. `dl.google.com` (Android SDK), `repo.maven.apache.org` / `maven.google.com` (Gradle dependencies), and GitHub release-asset CDN are **blocked**, so a local Gradle build is impossible here.
- Verification therefore runs through **GitHub Actions CI** (JDK 17 + Android SDK 34 + Gradle 8.5). The prior V3 CI run `43586872352` on the parent branch succeeded (unit tests + `assembleDebug`), which proves the toolchain path works for this repo.
- Every build/test claim in the V4 report refers to an actual CI run; nothing is claimed from this sandbox.

## Baseline version

- `versionCode 3`, `versionName "3.0.0"`, `minSdk 26`, `targetSdk 34`, applicationId `com.chronos.app` (debug suffix `.debug`).
- Room `chronos_database` schema v2 with non-destructive `MIGRATION_1_2`.
- Stack: Kotlin 1.9.22, Compose BOM 2024.01.00, Hilt 2.50, Room 2.6.1, DataStore 1.0.0, Navigation Compose 2.7.6, plus an unused-at-runtime KMP `shared` module (iOS targets declared).

## P0 — alarm dismissal findings (root causes)

1. **Dismissal gated on volatile runtime state.** `AlarmDismissReceiver` only persists a dismissal when `stopImmediately()` returns true (service singleton match OR SharedPreferences active slot). Any mismatch between the service singleton, the prefs slot, and the notification's instance (process recreation, redelivered `START_REDELIVER_INTENT`, rapid alarm succession) can drop the dismissal while audio continues. The database instance row — the authoritative record — is not consulted first.
2. **Notification trampoline on Android 12+.** The content tap is a broadcast `PendingIntent` whose receiver then calls `startActivity(MainActivity)`. Android 12+ blocks notification trampolines; the launch is silently dropped (and on some OEM builds the whole tap path is penalized). Dismissal must not depend on launching an Activity.
3. **Early-tap / activation race.** `AlarmPlaybackService` activates the SharedPreferences slot and posts the notification *before* `AlarmRuntimeController.activate()` persists the instance. A tap in that window relies on `dismiss()` blindly inserting a RINGING row; a stale intent for an instance that never existed can also fabricate a phantom dismissed row.
4. **`START_REDELIVER_INTENT` re-ring.** After process death the service redelivers the start intent and unconditionally re-activates the runtime slot before the DB check, briefly resurrecting a dismissed alarm's notification.
5. **Main-thread media I/O.** `MediaPlayer.setDataSource()` runs on the main thread; a slow/unavailable ringtone provider can jank or ANR the ringing path. No audio focus request; no bundled fallback when every system URI is missing.
6. **No reconciliation on app launch.** Schedules are only repaired on boot/time/permission broadcasts. If an OEM kills AlarmManager entries, nothing heals them until reboot.
7. **One-shot alarm can appear enabled but never ring.** Enabling a dated one-shot whose date passed leaves `isEnabled = true` with no schedule; `scheduleAlarm` returns false but the row stays enabled.
8. **Exact-alarm fragility.** Only `SCHEDULE_EXACT_ALARM` is declared (revocable on 12+). As a core alarm-clock app CHRONOS qualifies for `USE_EXACT_ALARM`, which removes the most common "scheduled but silent" failure on 13+.
9. **Stale full-screen UI.** `AlarmRingingActivity` renders whatever instance the intent carries and never re-validates against runtime state; after process recreation it can show a dead alarm.

## Other functional gaps / bugs

- **Reminders:** model + DAO + notification helper exist, but there is no scheduler, receiver, boot recovery, or UI. Non-functional module.
- **Routines:** absent entirely (no entity/UI).
- **Clock tools:** `Timer`, `StopwatchLap`, `WorldClock` models/DAOs exist but no stopwatch, countdown, or world-clock UI; `ClockViewModel` is a dead stub.
- **Focus:** breaks hard-coded (5/15/4) with no configuration UI; phase transitions only advance while the Focus screen is composed (no catch-up after backgrounding); no session-history list on the Focus screen.
- **Plan/calendar:** queries `CalendarContract.Events`, so **recurring events never appear** after their first occurrence; must use `Instances`. `ScheduleScreen` constructs a second `CalendarSyncManager(context)` outside DI just for conflict detection.
- **Home:** no daily overview, no upcoming events/reminders, no customization of layout despite a stored `dashboardOrder` key.
- **Insights:** no schedule-pattern summary or suggestions; `InsightsViewModel` loads all sessions unbounded.
- **Widgets:** none.
- **Settings:** "About CHRONOS 3.0" hard-coded; `BuildConfig` generation is disabled under AGP 8 so version cannot be read; haptic toggle stored but never used; no Home-layout controls.
- **Splash:** `core-splashscreen` dependency + `Theme.Chronos.Splash` exist but `installSplashScreen()` is never called and the theme is a plain black window.
- **State collection:** screens use `collectAsState()` instead of lifecycle-aware `collectAsStateWithLifecycle()`.
- **Unused dependencies:** WorkManager (+hilt-work) and Palette add init/binary weight with zero usage.
- **CI:** only debug APK; no lint, no release/AAB, no instrumentation, no version/changelog discipline.

## UI/UX weaknesses

- One `GlassCard` component carries every surface; no shared empty/loading/error states, buttons, headers, or spacing scale → inconsistent density across screens.
- Generic Material typography; no distinctive time display treatment; quick-action tiles are text rows with dead vertical space.
- Navigation transitions ignore the stored motion preference on some paths; system reduced-motion is only checked in the nav host.
- Alarm editor is a cramped `AlertDialog`; delete is an unguarded icon tap (destructive, no confirmation).
- Light theme glass alphas are ad-hoc; contrast of tertiary text on glass is marginal.

## Preservation decisions

- Keep package `com.chronos.app`, database name `chronos_database`, all existing entities and columns. Schema evolves v2 → v3 with an explicit additive migration (routines table); no destructive fallback.
- `master` (V2) is untouched; the V3 foundation commit `ac6f7df` remains in history and receives a preservation tag. No history rewrite, no force-push.
- Arena fixes the working branch name to `arena/8343f54c-chronos`; the requested `chronos-v4-stabilization` name cannot be created in this session, so this branch serves as the V4 stabilization branch.
