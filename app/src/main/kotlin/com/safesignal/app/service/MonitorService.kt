package com.safesignal.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.safesignal.app.SafeSignalApplication
import com.safesignal.app.domain.AlertController
import com.safesignal.app.source.SensorSource
import com.safesignal.detection.AccelSample
import com.safesignal.detection.FallDetector
import com.safesignal.detection.ManDownDetector
import com.safesignal.protocol.AlertStateMachine
import com.safesignal.protocol.AlertType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Foreground service that holds partial wake lock and continuously monitors sensor events
 * for fall and man-down conditions across lone worker shifts.
 */
class MonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val alertController: AlertController by lazy {
        SafeSignalApplication.instance.alertController
    }

    private val sensorSource: SensorSource by lazy {
        SafeSignalApplication.instance.sensorSource
    }

    private val fallDetector = FallDetector()
    private val manDownDetector = ManDownDetector()

    private var wakeLock: PowerManager.WakeLock? = null
    private var sensorJob: Job? = null
    private var stateObserverJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SafeSignal:MonitorServiceWakeLock").apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                startForegroundServiceInternal()
                startMonitoring()
            }

            ACTION_STOP -> {
                stopMonitoring()
                stopForegroundInternal()
                stopSelf()
            }

            ACTION_INJECT_FALL -> {
                // Ensure service is running in foreground first
                if (!isForeground) {
                    startForegroundServiceInternal()
                    startMonitoring()
                }

                // If currently in an alert state, reset back to Monitoring
                if (alertController.state.value !is AlertStateMachine.State.Monitoring) {
                    alertController.processEvent(AlertStateMachine.Event.Reset)
                }

                val overrideCountdown = intent?.getIntExtra(EXTRA_COUNTDOWN_SECONDS, -1) ?: -1
                if (overrideCountdown > 0) {
                    alertController.countdownSeconds = overrideCountdown
                }

                injectSyntheticFall()
            }

            ACTION_SOS -> {
                val alertId = intent?.getStringExtra(EXTRA_ALERT_ID) ?: UUID.randomUUID().toString()
                alertController.processEvent(AlertStateMachine.Event.SosPressed(alertId))
            }

            ACTION_CANCEL -> {
                alertController.processEvent(AlertStateMachine.Event.CancelPressed)
            }

            ACTION_RESET -> {
                fallDetector.reset()
                manDownDetector.reset()
                alertController.processEvent(AlertStateMachine.Event.Reset)
            }
        }

        return START_STICKY
    }

    private fun startForegroundServiceInternal() {
        wakeLock?.acquire(12 * 60 * 60 * 1000L) // 12-hour shift safety timeout
        val notification = buildNotification("Monitoring active")

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
            isForeground = true
            Log.i(TAG, "service isForeground=true")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}", e)
        }
    }

    private fun stopForegroundInternal() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            isForeground = false
            Log.i(TAG, "service isForeground=false")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop foreground service: ${e.message}", e)
        }
    }

    private fun startMonitoring() {
        alertController.processEvent(AlertStateMachine.Event.StartShift)
        sensorSource.start()
        try {
            SafeSignalApplication.instance.locationSource.start()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start locationSource: ${e.message}")
        }

        sensorJob?.cancel()
        sensorJob = serviceScope.launch {
            sensorSource.samples.collect { sample ->
                processSample(sample)
            }
        }

        stateObserverJob?.cancel()
        stateObserverJob = serviceScope.launch {
            alertController.state.collect { state ->
                updateNotificationForState(state)
            }
        }
    }

    private fun stopMonitoring() {
        sensorJob?.cancel()
        sensorJob = null
        sensorSource.stop()
        try {
            SafeSignalApplication.instance.locationSource.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop locationSource: ${e.message}")
        }
        alertController.processEvent(AlertStateMachine.Event.StopShift)
    }

    private fun processSample(sample: AccelSample) {
        val fallEvent = fallDetector.onSample(sample)
        if (fallEvent.isPresent) {
            Log.w(TAG, "Fall detected by kinematics engine!")
            alertController.processEvent(AlertStateMachine.Event.Detected(AlertType.FALL))
            return
        }

        val manDownEvent = manDownDetector.onSample(sample)
        if (manDownEvent.isPresent) {
            Log.w(TAG, "Man-down immobility detected by engine!")
            alertController.processEvent(AlertStateMachine.Event.Detected(AlertType.MAN_DOWN))
        }
    }

    /**
     * Injects synthetic kinematic fall samples matching FallDetector criteria:
     * 1. Walking/standing -> 2. Free-fall weightlessness -> 3. Hard ground impact -> 4. Post-fall stillness (>= 2s)
     */
    fun injectSyntheticFall() {
        fallDetector.reset()
        serviceScope.launch {
            var t = System.currentTimeMillis()

            // 1. Upright pre-fall
            for (i in 0 until 5) {
                t += 20L
                processSample(AccelSample(t, 0.1, 9.8, 0.2))
            }

            // 2. Free fall: weightlessness (magnitude < 3.0 m/s^2) for ~160 ms
            for (i in 0 until 8) {
                t += 20L
                processSample(AccelSample(t, 0.3, 0.8, 0.4))
            }

            // 3. Ground impact shock spike (magnitude > 28 m/s^2)
            for (i in 0 until 4) {
                t += 20L
                processSample(AccelSample(t, 12.0, 26.0, 8.0))
            }

            // 4. Rebound settling
            for (i in 0 until 6) {
                t += 20L
                processSample(AccelSample(t, 1.5, 9.5, 1.0))
            }

            // 5. Post-fall stillness (> 2000 ms, variance low)
            for (i in 0 until 110) {
                t += 20L
                processSample(AccelSample(t, 0.05, 9.8, 0.05))
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SafeSignal Monitoring Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains lone worker fall detection and emergency beacon"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafeSignal Worker Beacon")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotificationForState(state: AlertStateMachine.State) {
        val text = when (state) {
            is AlertStateMachine.State.Idle -> "Monitoring inactive"
            is AlertStateMachine.State.Monitoring -> "Monitoring active"
            is AlertStateMachine.State.Countdown -> "EMERGENCY COUNTDOWN (${state.secondsLeft}s) - Press Cancel if safe"
            is AlertStateMachine.State.Sending -> "Sending emergency alert (${state.type.name})..."
            is AlertStateMachine.State.Sent -> "Alert delivered. Waiting for dispatcher..."
            is AlertStateMachine.State.Acknowledged -> "Alert ACKNOWLEDGED by ${state.acknowledgedBy ?: "Dispatcher"}"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        stopMonitoring()
        stopForegroundInternal()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val TAG = "SafeSignal"
        const val CHANNEL_ID = "safesignal_monitor_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.safesignal.app.START_MONITORING"
        const val ACTION_STOP = "com.safesignal.app.STOP_MONITORING"
        const val ACTION_INJECT_FALL = "com.safesignal.app.INJECT_FALL"
        const val ACTION_SOS = "com.safesignal.app.SOS"
        const val ACTION_CANCEL = "com.safesignal.app.CANCEL"
        const val ACTION_RESET = "com.safesignal.app.RESET"

        const val EXTRA_COUNTDOWN_SECONDS = "countdown_seconds"
        const val EXTRA_ALERT_ID = "alert_id"

        @Volatile
        var isForeground: Boolean = false
            private set
    }
}
