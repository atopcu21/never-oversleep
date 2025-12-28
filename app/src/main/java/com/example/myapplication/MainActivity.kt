package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.PassiveListenerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val isMonitoring = mutableStateOf(false)
    private val statusMessage = mutableStateOf("Not monitoring")
    private val testMode = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSleepMonitoring()
        } else {
            statusMessage.value = "Permission denied"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("SleepData", MODE_PRIVATE)
        isMonitoring.value = prefs.getBoolean("IS_MONITORING", false)
        testMode.value = prefs.getBoolean("TEST_MODE", false)
        if (isMonitoring.value) {
            statusMessage.value = "Monitoring sleep\n(8-hour alarm active)"
        }

        setContent {
            MaterialTheme {
                Surface(color = Color.Black) {
                    MainScreen(
                        context = this,
                        isMonitoring = isMonitoring.value,
                        statusMessage = statusMessage.value,
                        testMode = testMode.value,
                        onStartMonitoring = { checkPermissionAndStart() },
                        onStopMonitoring = { stopSleepMonitoring() },
                        onTestModeChanged = { enabled -> toggleTestMode(enabled) }
                    )
                }
            }
        }
    }

    private fun toggleTestMode(enabled: Boolean) {
        testMode.value = enabled
        getSharedPreferences("SleepData", MODE_PRIVATE)
            .edit()
            .putBoolean("TEST_MODE", enabled)
            .putLong("TEST_START_TIME", 0L)
            .putLong("SCHEDULED_ALARM_TIME", 0L)
            .apply()
        
        // Cancel any existing alarm when toggling test mode
        AlarmReceiver.cancelAlarm(this)
    }

    private fun checkPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED -> {
                startSleepMonitoring()
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
    }

    private fun startSleepMonitoring() {
        val healthClient = HealthServices.getClient(this)
        val passiveMonitoringClient = healthClient.passiveMonitoringClient

        val config = PassiveListenerConfig.builder()
            .setShouldUserActivityInfoBeRequested(true)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                passiveMonitoringClient.setPassiveListenerServiceAsync(
                    SleepMonitorService::class.java,
                    config
                ).await()

                runOnUiThread {
                    isMonitoring.value = true
                    statusMessage.value = "Monitoring sleep\n(8-hour alarm active)"
                    getSharedPreferences("SleepData", MODE_PRIVATE)
                        .edit()
                        .putBoolean("IS_MONITORING", true)
                        .apply()
                }
            } catch (e: Exception) {
                Log.e("SleepAlarm", "Failed to start monitoring", e)
                runOnUiThread {
                    statusMessage.value = "Failed: ${e.message}"
                }
            }
        }
    }

    private fun stopSleepMonitoring() {
        val healthClient = HealthServices.getClient(this)
        val passiveMonitoringClient = healthClient.passiveMonitoringClient

        CoroutineScope(Dispatchers.IO).launch {
            try {
                passiveMonitoringClient.clearPassiveListenerServiceAsync().await()
                
                // Cancel any scheduled alarm
                AlarmReceiver.cancelAlarm(this@MainActivity)

                runOnUiThread {
                    isMonitoring.value = false
                    statusMessage.value = "Not monitoring"
                    getSharedPreferences("SleepData", MODE_PRIVATE)
                        .edit()
                        .putBoolean("IS_MONITORING", false)
                        .putLong("START_TIME", 0L)
                        .putLong("SCHEDULED_ALARM_TIME", 0L)
                        .putString("ACTIVITY_STATE", "")
                        .apply()
                }
            } catch (e: Exception) {
                Log.e("SleepAlarm", "Failed to stop monitoring", e)
            }
        }
    }
}

@Composable
fun MainScreen(
    context: Context,
    isMonitoring: Boolean,
    statusMessage: String,
    testMode: Boolean,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onTestModeChanged: (Boolean) -> Unit
) {
    val sleepDuration = remember { mutableStateOf("") }
    val sleepCount = remember { mutableStateOf(0) }
    val activityState = remember { mutableStateOf("") }
    val alarmCountdown = remember { mutableStateOf("") }

    LaunchedEffect(isMonitoring) {
        while (isMonitoring) {
            val prefs = context.getSharedPreferences("SleepData", Context.MODE_PRIVATE)
            val startTime = prefs.getLong("START_TIME", 0L)
            val scheduledAlarmTime = prefs.getLong("SCHEDULED_ALARM_TIME", 0L)
            sleepCount.value = prefs.getInt("SLEEP_COUNT", 0)
            activityState.value = prefs.getString("ACTIVITY_STATE", "") ?: ""
            
            // Show countdown to alarm
            if (scheduledAlarmTime > 0L) {
                val remaining = scheduledAlarmTime - System.currentTimeMillis()
                if (remaining > 0) {
                    val hours = remaining / (1000 * 60 * 60)
                    val minutes = (remaining / (1000 * 60)) % 60
                    val seconds = (remaining / 1000) % 60
                    alarmCountdown.value = "⏰ ${hours}h ${minutes}m ${seconds}s"
                } else {
                    alarmCountdown.value = "⏰ Alarm pending..."
                }
            } else {
                alarmCountdown.value = ""
            }
            
            if (startTime > 0L) {
                val duration = System.currentTimeMillis() - startTime
                val hours = duration / (1000 * 60 * 60)
                val minutes = (duration / (1000 * 60)) % 60
                val seconds = (duration / 1000) % 60
                sleepDuration.value = "😴 ${hours}h ${minutes}m ${seconds}s"
            } else {
                sleepDuration.value = "👁 Awake"
            }
            delay(1000)
        }
        sleepDuration.value = ""
        activityState.value = ""
        alarmCountdown.value = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Test mode toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Test", color = if (testMode) Color.Yellow else Color.Gray, fontSize = 9.sp)
            Switch(
                checked = testMode,
                onCheckedChange = onTestModeChanged,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = statusMessage,
            color = if (isMonitoring) Color.Green else Color.Gray,
            fontSize = 9.sp,
            textAlign = TextAlign.Center
        )

        if (activityState.value.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "State: ${activityState.value}",
                color = when (activityState.value) {
                    "ASLEEP" -> Color.Cyan
                    "PASSIVE" -> Color.Yellow
                    "EXERCISE" -> Color.Green
                    else -> Color.Gray
                },
                fontSize = 9.sp
            )
        }

        if (alarmCountdown.value.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = alarmCountdown.value, color = Color.Red, fontSize = 10.sp)
        }

        if (sleepDuration.value.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = sleepDuration.value,
                color = if (sleepDuration.value.startsWith("😴")) Color.Cyan else Color.Gray,
                fontSize = 10.sp
            )
        }

        if (isMonitoring && sleepCount.value > 0) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = "Sessions: ${sleepCount.value}", color = Color.Magenta, fontSize = 8.sp)
        }

        Spacer(modifier = Modifier.height(6.dp))

        Button(onClick = if (!isMonitoring) onStartMonitoring else onStopMonitoring) {
            Text(if (!isMonitoring) "Start" else "Stop", fontSize = 10.sp)
        }
    }
}
