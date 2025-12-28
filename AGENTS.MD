# AGENTS.MD - Sleep Alarm Watch App

## Project Overview

A Wear OS app for Pixel Watch 2 that monitors sleep and triggers an alarm after 8 hours of continuous sleep, even without a user-set alarm.

## Current Status: ✅ Complete & Ready to Deploy

### Features Implemented
- **Sleep Detection**: Uses Health Services `PassiveListenerService` to detect `USER_ACTIVITY_ASLEEP`
- **8-Hour Alarm**: Triggers vibration alarm after 8 hours of sleep
- **1-Hour Grace Period**: Brief awakenings (bathroom breaks < 1 hour) don't reset the timer
- **Precise Timing**: Uses `AlarmManager.setAlarmClock()` for exact alarm timing
- **Test Mode**: Toggle to test with EXERCISE state → 1-minute alarm
- **UI Displays**: Activity state, sleep duration, alarm countdown, session count

## Architecture

```
MainActivity.kt          - UI, permission handling, monitoring control
SleepMonitorService.kt   - PassiveListenerService for Health Services
AlarmReceiver.kt         - BroadcastReceiver for AlarmManager scheduling
AlarmService.kt          - Foreground service for vibration
AlarmActivity.kt         - Full-screen wake-up UI with STOP button
```

## Key Files

| File | Purpose |
|------|---------|
| `app/src/main/java/com/example/myapplication/MainActivity.kt` | Main UI with test toggle, status display |
| `app/src/main/java/com/example/myapplication/SleepMonitorService.kt` | Health Services passive monitoring |
| `app/src/main/java/com/example/myapplication/AlarmReceiver.kt` | AlarmManager scheduling |
| `app/src/main/java/com/example/myapplication/AlarmService.kt` | Vibration with SOS pattern |
| `app/src/main/java/com/example/myapplication/AlarmActivity.kt` | Wake-up screen |
| `app/src/main/AndroidManifest.xml` | Permissions and service declarations |

## Build & Deploy

```bash
# Build
./gradlew assembleDebug

# Install via ADB
adb install app/build/outputs/apk/debug/app-debug.apk

# Or deploy via Android Studio to Pixel Watch 2
```

## Permissions Required
- `ACTIVITY_RECOGNITION` - For sleep detection
- `BODY_SENSORS` - For Health Services
- `VIBRATE`, `WAKE_LOCK` - For alarm
- `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM` - For precise timing
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_HEALTH` - For background monitoring

## SharedPreferences Keys (`SleepData`)
- `IS_MONITORING` - Boolean, monitoring active
- `TEST_MODE` - Boolean, test mode enabled
- `START_TIME` - Long, current sleep session start timestamp
- `ORIGINAL_START_TIME` - Long, preserved for grace period
- `WAKE_TIME` - Long, when user woke (for grace period)
- `SCHEDULED_ALARM_TIME` - Long, when alarm will fire
- `ACTIVITY_STATE` - String, last detected state
- `SLEEP_COUNT` - Int, total sleep sessions

## Logic Flow

1. User starts monitoring
2. Health Services detects `USER_ACTIVITY_ASLEEP`
3. App records start time, schedules alarm for now + 8 hours
4. If user wakes (PASSIVE/EXERCISE):
   - Cancel alarm, start 1-hour grace period
   - If user sleeps again within 1 hour → restore timer
   - If user stays awake > 1 hour → reset
5. At 8 hours → AlarmReceiver fires → AlarmService vibrates

## Test Mode
- Enable toggle in UI
- When EXERCISE state detected → alarm in 60 seconds
- Useful for testing on physical watch
