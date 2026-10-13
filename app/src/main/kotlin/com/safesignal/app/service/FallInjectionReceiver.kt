package com.safesignal.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver enabling test automation and scripts to inject fall scenarios via adb broadcast.
 */
class FallInjectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.i("SafeSignal", "FallInjectionReceiver: Received fall injection request")
        val serviceIntent = Intent(context, MonitorService::class.java).apply {
            action = MonitorService.ACTION_INJECT_FALL
            val countdown = intent?.getIntExtra(MonitorService.EXTRA_COUNTDOWN_SECONDS, -1) ?: -1
            if (countdown > 0) {
                putExtra(MonitorService.EXTRA_COUNTDOWN_SECONDS, countdown)
            }
        }
        context.startForegroundService(serviceIntent)
    }

    companion object {
        const val ACTION_INJECT_FALL = "com.safesignal.app.INJECT_FALL"
    }
}
