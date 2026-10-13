package com.safesignal.app.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Manages device owner kiosk provisioning, lock task packages, and lock task mode.
 */
class KioskManager(private val context: Context) {
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = SafeSignalAdminReceiver.getComponentName(context)

    val isDeviceOwner: Boolean
        get() = dpm.isDeviceOwnerApp(context.packageName)

    val isLockTaskPermitted: Boolean
        get() = dpm.isLockTaskPermitted(context.packageName)

    fun setupKioskMode() {
        if (!isDeviceOwner) {
            Log.w("SafeSignal", "KioskManager: App is not device owner; cannot configure lock task packages")
            return
        }

        try {
            dpm.setLockTaskPackages(adminComponent, arrayOf(context.packageName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(
                    adminComponent,
                    DevicePolicyManager.LOCK_TASK_FEATURE_NONE
                )
            }
            Log.i("SafeSignal", "KioskManager: Lock task packages configured successfully")
        } catch (e: Exception) {
            Log.e("SafeSignal", "KioskManager: Error configuring lock task packages: ${e.message}", e)
        }
    }

    fun startKiosk(activity: Activity) {
        setupKioskMode()
        try {
            activity.startLockTask()
            Log.i("SafeSignal", "KioskManager: startLockTask() invoked")
        } catch (e: Exception) {
            Log.e("SafeSignal", "KioskManager: Error starting lock task: ${e.message}", e)
        }
    }

    fun stopKiosk(activity: Activity) {
        try {
            activity.stopLockTask()
            Log.i("SafeSignal", "KioskManager: stopLockTask() invoked")
        } catch (e: Exception) {
            Log.e("SafeSignal", "KioskManager: Error stopping lock task: ${e.message}", e)
        }
    }
}
