package com.safesignal.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Debug-only BroadcastReceiver letting test scripts start monitoring and inject fall scenarios
 * via adb broadcast. Guarded by android.permission.DUMP, which the adb shell holds and
 * third-party apps cannot obtain. MonitorService itself is not exported.
 */
class FallInjectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val serviceIntent = when (intent?.action) {
            ACTION_START_MONITORING -> {
                Log.i("SafeSignal", "FallInjectionReceiver: Received start monitoring request")
                Intent(context, MonitorService::class.java).apply {
                    action = MonitorService.ACTION_START
                }
            }

            ACTION_INJECT_FALL -> {
                Log.i("SafeSignal", "FallInjectionReceiver: Received fall injection request")
                Intent(context, MonitorService::class.java).apply {
                    action = MonitorService.ACTION_INJECT_FALL
                    val countdown = intent.getIntExtra(MonitorService.EXTRA_COUNTDOWN_SECONDS, -1)
                    if (countdown > 0) {
                        putExtra(MonitorService.EXTRA_COUNTDOWN_SECONDS, countdown)
                    }
                }
            }

            else -> return
        }
        context.startForegroundService(serviceIntent)
    }

    companion object {
        const val ACTION_START_MONITORING = "com.safesignal.app.START_MONITORING"
        const val ACTION_INJECT_FALL = "com.safesignal.app.INJECT_FALL"
    }
}
