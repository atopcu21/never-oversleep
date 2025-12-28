package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.UserActivityInfo
import androidx.health.services.client.data.UserActivityState

class SleepMonitorService : PassiveListenerService() {

    companion object {
        const val ONE_HOUR_MS = 60 * 60 * 1000L
        const val EIGHT_HOURS_MS = 8 * 60 * 60 * 1000L
        const val ONE_MINUTE_MS = 60 * 1000L  // For test mode
    }

    override fun onUserActivityInfoReceived(info: UserActivityInfo) {
        val prefs = applicationContext.getSharedPreferences("SleepData", Context.MODE_PRIVATE)
        val testMode = prefs.getBoolean("TEST_MODE", false)
        
        val stateChangeTime = info.stateChangeTime
        val stateChangeMs = stateChangeTime.toEpochMilli()
        
        // Store the current activity state for UI display
        val stateName = when (info.userActivityState) {
            UserActivityState.USER_ACTIVITY_ASLEEP -> "ASLEEP"
            UserActivityState.USER_ACTIVITY_PASSIVE -> "PASSIVE"
            UserActivityState.USER_ACTIVITY_EXERCISE -> "EXERCISE"
            UserActivityState.USER_ACTIVITY_UNKNOWN -> "UNKNOWN"
            else -> "OTHER"
        }
        prefs.edit().putString("ACTIVITY_STATE", stateName).apply()
        
        Log.d("SleepAlarm", "Activity state: $stateName, testMode: $testMode")

        // TEST MODE: When EXERCISE detected, schedule alarm for 1 minute
        if (testMode && info.userActivityState == UserActivityState.USER_ACTIVITY_EXERCISE) {
            handleTestExerciseMode(prefs, stateChangeMs)
            return
        }

        when (info.userActivityState) {
            UserActivityState.USER_ACTIVITY_ASLEEP -> {
                handleSleepState(prefs, stateChangeMs)
            }
            UserActivityState.USER_ACTIVITY_PASSIVE,
            UserActivityState.USER_ACTIVITY_EXERCISE -> {
                handleAwakeState(prefs, stateChangeMs)
            }
            else -> {
                Log.d("SleepAlarm", "Unknown state: ${info.userActivityState}")
            }
        }
    }

    private fun handleTestExerciseMode(prefs: android.content.SharedPreferences, stateChangeMs: Long) {
        val scheduledAlarmTime = prefs.getLong("SCHEDULED_ALARM_TIME", 0L)
        
        if (scheduledAlarmTime == 0L) {
            // No alarm scheduled yet - schedule one for 1 minute from now
            val alarmTime = System.currentTimeMillis() + ONE_MINUTE_MS
            prefs.edit()
                .putLong("TEST_START_TIME", stateChangeMs)
                .putLong("SCHEDULED_ALARM_TIME", alarmTime)
                .apply()
            
            Log.d("SleepAlarm", "TEST MODE: Scheduling alarm for 1 minute from now")
            AlarmReceiver.scheduleAlarm(applicationContext, alarmTime)
        } else {
            Log.d("SleepAlarm", "TEST MODE: Alarm already scheduled for $scheduledAlarmTime")
        }
    }

    private fun handleSleepState(prefs: android.content.SharedPreferences, stateChangeMs: Long) {
        val startTime = prefs.getLong("START_TIME", 0L)
        val wakeTime = prefs.getLong("WAKE_TIME", 0L)
        val scheduledAlarmTime = prefs.getLong("SCHEDULED_ALARM_TIME", 0L)

        if (startTime == 0L) {
            if (wakeTime > 0L) {
                // User was awake, check if within 1-hour grace period
                val awakeDuration = stateChangeMs - wakeTime
                val originalStartTime = prefs.getLong("ORIGINAL_START_TIME", 0L)
                
                if (awakeDuration < ONE_HOUR_MS && originalStartTime > 0L) {
                    // Within grace period - restore original start time and reschedule alarm
                    Log.d("SleepAlarm", "Within 1-hour grace period, restoring timer")
                    val remainingSleepTime = EIGHT_HOURS_MS - (stateChangeMs - originalStartTime)
                    val alarmTime = System.currentTimeMillis() + remainingSleepTime
                    
                    prefs.edit()
                        .putLong("START_TIME", originalStartTime)
                        .putLong("WAKE_TIME", 0L)
                        .putLong("SCHEDULED_ALARM_TIME", alarmTime)
                        .apply()
                    
                    if (remainingSleepTime > 0) {
                        AlarmReceiver.scheduleAlarm(applicationContext, alarmTime)
                    } else {
                        // Already past 8 hours - trigger now
                        AlarmReceiver.scheduleAlarm(applicationContext, System.currentTimeMillis() + 1000)
                    }
                } else {
                    // Grace period expired - start new session
                    startNewSleepSession(prefs, stateChangeMs)
                }
            } else {
                // Fresh start - new sleep session
                startNewSleepSession(prefs, stateChangeMs)
            }
        } else {
            // Already sleeping - alarm is already scheduled, nothing to do
            Log.d("SleepAlarm", "Already sleeping, alarm scheduled for: $scheduledAlarmTime")
        }
    }

    private fun startNewSleepSession(prefs: android.content.SharedPreferences, stateChangeMs: Long) {
        val sleepCount = prefs.getInt("SLEEP_COUNT", 0)
        val alarmTime = System.currentTimeMillis() + EIGHT_HOURS_MS
        
        Log.d("SleepAlarm", "New sleep session #${sleepCount + 1}, scheduling 8-hour alarm")
        
        prefs.edit()
            .putLong("START_TIME", stateChangeMs)
            .putLong("ORIGINAL_START_TIME", stateChangeMs)
            .putLong("WAKE_TIME", 0L)
            .putLong("SCHEDULED_ALARM_TIME", alarmTime)
            .putInt("SLEEP_COUNT", sleepCount + 1)
            .apply()
        
        AlarmReceiver.scheduleAlarm(applicationContext, alarmTime)
    }

    private fun handleAwakeState(prefs: android.content.SharedPreferences, stateChangeMs: Long) {
        val startTime = prefs.getLong("START_TIME", 0L)
        
        if (startTime > 0L) {
            // User was sleeping, now awake - cancel alarm, start grace period
            Log.d("SleepAlarm", "User awoke, cancelling alarm, starting 1-hour grace period")
            AlarmReceiver.cancelAlarm(applicationContext)
            
            prefs.edit()
                .putLong("START_TIME", 0L)
                .putLong("WAKE_TIME", stateChangeMs)
                .putLong("SCHEDULED_ALARM_TIME", 0L)
                .apply()
            // ORIGINAL_START_TIME is preserved for grace period restoration
        } else {
            // User was already awake
            val wakeTime = prefs.getLong("WAKE_TIME", 0L)
            if (wakeTime > 0L) {
                val awakeDuration = stateChangeMs - wakeTime
                if (awakeDuration >= ONE_HOUR_MS) {
                    // Grace period expired - fully reset
                    Log.d("SleepAlarm", "Awake for over 1 hour, resetting timer")
                    prefs.edit()
                        .putLong("ORIGINAL_START_TIME", 0L)
                        .putLong("WAKE_TIME", 0L)
                        .apply()
                }
            }
        }
    }
}
