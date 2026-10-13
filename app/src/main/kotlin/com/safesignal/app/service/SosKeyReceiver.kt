package com.safesignal.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.safesignal.app.SafeSignalApplication
import com.safesignal.protocol.AlertStateMachine
import java.util.UUID

/**
 * BroadcastReceiver triggered by the hardware SOS key or test broadcast.
 */
class SosKeyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.i("SafeSignal", "SosKeyReceiver: SOS event received")
        try {
            val alertId = intent?.getStringExtra(MonitorService.EXTRA_ALERT_ID) ?: UUID.randomUUID().toString()
            val controller = SafeSignalApplication.instance.alertController
            controller.processEvent(AlertStateMachine.Event.SosPressed(alertId))
        } catch (e: Exception) {
            Log.e("SafeSignal", "Error processing SOS broadcast: ${e.message}", e)
        }
    }

    companion object {
        const val ACTION_SOS_KEY = "com.safesignal.app.SOS_KEY"
    }
}
