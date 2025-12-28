package com.example.myapplication

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_TRIGGER_ALARM = "com.example.myapplication.TRIGGER_ALARM"
        
        fun scheduleAlarm(context: Context, triggerAtMillis: Long) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_TRIGGER_ALARM
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Use setAlarmClock for precise timing that works even in Doze mode
            val alarmInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent)
            alarmManager.setAlarmClock(alarmInfo, pendingIntent)
            
            Log.d("SleepAlarm", "Alarm scheduled for: $triggerAtMillis (in ${(triggerAtMillis - System.currentTimeMillis()) / 1000}s)")
        }
        
        fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_TRIGGER_ALARM
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            Log.d("SleepAlarm", "Alarm cancelled")
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TRIGGER_ALARM) {
            Log.d("SleepAlarm", "Alarm received! Triggering alarm service...")
            
            // Reset timer data
            val prefs = context.getSharedPreferences("SleepData", Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("START_TIME", 0L)
                .putLong("ORIGINAL_START_TIME", 0L)
                .putLong("WAKE_TIME", 0L)
                .putLong("TEST_START_TIME", 0L)
                .putLong("SCHEDULED_ALARM_TIME", 0L)
                .apply()
            
            // Start the alarm service
            val serviceIntent = Intent(context, AlarmService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
