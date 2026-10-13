package com.safesignal.app.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * DeviceAdminReceiver for SafeSignal enterprise administration and kiosk lock-task provisioning.
 */
class SafeSignalAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i("SafeSignal", "SafeSignalAdminReceiver: Device Admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i("SafeSignal", "SafeSignalAdminReceiver: Device Admin disabled")
    }

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, SafeSignalAdminReceiver::class.java)
        }
    }
}
