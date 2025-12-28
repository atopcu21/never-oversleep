package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator

class AlarmService : Service() {

    private lateinit var vibrator: Vibrator

    override fun onCreate() {
        super.onCreate()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_ALARM") {
            stopSelf()
            return START_NOT_STICKY
        }

        // 1. Show notification (Required for Foreground Service)
        startForeground(1, createNotification())

        // 2. Start Vibration
        vibrateForDeepSleep()

        // 3. Launch Full Screen Activity (The "Stop" button UI)
        launchAlarmUI()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        vibrator.cancel()
    }

    private fun vibrateForDeepSleep() {
        // CRITICAL: USAGE_ALARM allows this to bypass DND/Bedtime Mode
        val audioAttributes = AudioAttributes.Builder()
           .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
           .setUsage(AudioAttributes.USAGE_ALARM)
           .build()

        // "SOS" pattern: Short, Short, Short, Long, Long, Long
        val pattern = longArrayOf(0, 200, 100, 200, 100, 200, 500, 500, 500, 500, 500)

        // 0 means "repeat from index 0" (Infinite Loop) until stopped
        val effect = VibrationEffect.createWaveform(pattern, 0)

        vibrator.vibrate(effect, audioAttributes)
    }

    private fun launchAlarmUI() {
        val activityIntent = Intent(this, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        // Requires USE_FULL_SCREEN_INTENT permission
        startActivity(activityIntent)
    }

    private fun createNotification(): Notification {
        val channelId = "alarm_channel"
        val channelName = "Alarm Channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationChannel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
        notificationManager.createNotificationChannel(notificationChannel)

        val notificationIntent = Intent(this, AlarmActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return Notification.Builder(this, channelId)
            .setContentTitle("Alarm")
            .setContentText("Time to wake up!")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .build()
    }


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}