# CHRONOS — Premium Time Management for Android

> **Your time. Your schedule. Your control.**

CHRONOS is a luxury time-management Android application combining a premium clock, smart calendar integration, intelligent alarms, and a personal command centre — all wrapped in a cinematic "Luxury Adaptive Glass" design language.

---

## 🏗 Architecture

```
CHRONOS/
├── androidApp/           # Android application module
│   └── src/main/
│       ├── kotlin/com/chronos/app/
│       │   ├── alarm/          # Alarm engine (scheduler, receivers, service, UI)
│       │   ├── calendar/       # Google Calendar integration
│       │   ├── clock/          # Clock, timer, stopwatch logic
│       │   ├── common/         # Shared utilities
│       │   ├── data/           # Room database, DAOs, models
│       │   ├── di/             # Hilt dependency injection
│       │   ├── notifications/  # Notification management
│       │   ├── settings/       # DataStore preferences
│       │   ├── theme/          # Luxury Glass design system
│       │   └── ui/             # Compose UI screens & components
│       ├── res/                # Android resources
│       └── AndroidManifest.xml
├── shared/               # Kotlin Multiplatform module
│   └── src/
│       ├── commonMain/   # Shared models & interfaces
│       ├── androidMain/  # Android-specific implementations
│       └── iosMain/      # iOS-specific implementations (future)
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## ⚙️ Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Kotlin 1.9 |
| **Platform** | Kotlin Multiplatform (Android + iOS-ready) |
| **UI** | Jetpack Compose + Material 3 |
| **Architecture** | MVVM + Clean Architecture |
| **DI** | Hilt (Dagger) |
| **Database** | Room |
| **Preferences** | DataStore |
| **Scheduling** | AlarmManager (exact) + WorkManager (backup) |
| **Navigation** | Jetpack Navigation Compose |
| **Min SDK** | 31 (Android 12) |
| **Target SDK** | 34 |

---

## 🔧 Key Features

### 1. Premium Alarm Engine ⭐
- **Exact alarm scheduling** using `AlarmManager.setAlarmClock()`
- **Reliable notification dismissal** — the critical V2 fix
- Foreground service for guaranteed audio playback
- Gradual volume increase (30-second ramp)
- Lock-screen full-screen alarm UI with swipe-to-dismiss
- Snooze with configurable duration
- Recurring alarms (daily, weekdays, weekends, custom)
- Boot recovery — alarms reschedule after device restart
- Timezone and DST transition handling
- Alarm history (dismissed, snoozed, missed)

### 2. Smart Calendar Integration
- Google Calendar sync (read-only)
- Today's timeline view
- Gap detection between events
- Conflict / overlap detection
- Smart alarm suggestions (requires user approval)
- Upcoming event previews

### 3. Clock Module
- Digital clock with seconds
- Analog clock face (canvas-rendered)
- World clocks
- Multiple concurrent timers
- Stopwatch with lap tracking
- 12h/24h format toggle

### 4. Reminders
- One-time and recurring reminders
- Priority categories
- Completion tracking
- Snooze and reschedule

### 5. Focus Sessions (Pomodoro)
- Configurable focus/break durations
- Session history and statistics
- Long break after N sessions

### 6. Luxury Adaptive Glass UI
- **Three glass styles:** Luxury, Smoked, Frosted
- Champagne gold accent system
- Dark / Light / Adaptive themes
- Wallpaper-based colour extraction
- Cinematic animations (respects reduced-motion)
- Consistent spacing, typography, and hierarchy

---

## 🚨 Critical Bug Fix: Alarm Notification Dismissal

### Problem (V1)
Tapping the alarm notification did NOT stop the alarm. Audio continued playing in the background while the UI appeared dismissed.

### Root Cause
The notification's `contentIntent` opened the main activity instead of routing through the alarm service. There was no central authority for the "ringing" state.

### Solution (V2)
- **Single source of truth:** `AlarmPlaybackService` is the only component that controls alarm state
- **All dismiss paths converge:** Notification tap → `AlarmRingingActivity` → `service.dismissAlarm()`
- **Direct action buttons:** "Stop" and "Snooze" notification actions go through `AlarmDismissReceiver` / `AlarmSnoozeReceiver` → service
- **`setDeleteIntent`:** Even swiping the notification away triggers dismissal
- **Atomic cleanup:** Audio stop → vibration stop → notification remove → DB update → service stop (all in sequence)
- **Idempotent:** Calling dismiss multiple times is safe

---

## 📦 Building

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17+
- Android SDK 34

### Build Commands
```bash
# Debug build
./gradlew :androidApp:assembleDebug

# Release build
./gradlew :androidApp:assembleRelease

# Run tests
./gradlew test
./gradlew connectedAndroidTest

# Install on device
./gradlew :androidApp:installDebug
```

---

## 📱 Permissions

| Permission | Purpose |
|-----------|---------|
| `SCHEDULE_EXACT_ALARM` | Precise alarm scheduling |
| `USE_EXACT_ALARM` | Alternative exact alarm permission |
| `WAKE_LOCK` | Keep CPU awake during alarm |
| `VIBRATE` | Haptic alarm feedback |
| `RECEIVE_BOOT_COMPLETED` | Reschedule alarms after reboot |
| `POST_NOTIFICATIONS` | Show alarm notifications |
| `USE_FULL_SCREEN_INTENT` | Lock-screen alarm UI |
| `FOREGROUND_SERVICE` | Alarm playback service |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Audio playback service type |
| `READ_CALENDAR` | Google Calendar integration |

---

## 🧪 Testing

The project includes test coverage for:
- Alarm creation, editing, deletion
- Alarm notification dismissal (the critical fix)
- Snooze functionality
- Recurring alarm scheduling
- Boot recovery
- Timezone change handling
- Database persistence
- UI navigation
- Theme persistence

---

## 📋 Roadmap

- [x] V2 Architecture & Alarm Engine
- [x] Luxury Glass UI System
- [x] Navigation & Core Screens
- [ ] Google Calendar integration (implementation)
- [ ] Reminder engine implementation
- [ ] Focus session implementation
- [ ] Widget support
- [ ] iOS target (via KMP shared module)
- [ ] Backup & restore
- [ ] Accessibility audit

---

## 📄 Licence

Proprietary — CHRONOS © 2026
