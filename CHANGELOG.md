# Changelog

## 4.0.0-rc.1 (unreleased)

This is a release candidate, not a production-stable release. Keep the V3 foundation tag for rollback.

- Reworked alarm notification and dismissal around persisted occurrence identities, with independent simultaneous playback sessions, stale-action checks, snooze, boot recovery and a bundled fallback sound.
- Added Plan tabs for calendar instances, reminders and routines; recurring calendar events are expanded by Android's calendar provider.
- Added countdown timers, a stopwatch with laps, and configurable world clocks; added focus defaults, catch-up and session history.
- Added a configurable home overview, honest activity-based insights, shared glass UI primitives, a compact/resizable home widget and a system splash.
- Added Room schema version 3 with an additive routines migration; retained the existing database and migration path, without destructive fallback.
- Added regression tests and CI tasks for tests, lint, debug APK, release APK and AAB. Device-only behavior remains to be verified.

See `docs/CHRONOS_V4_AUDIT.md` and `docs/CHRONOS_V4_RELEASE.md` for constraints and release verification.
