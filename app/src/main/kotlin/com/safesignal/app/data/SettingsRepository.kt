package com.safesignal.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Repository for device configuration and worker identity settings.
 */
class SettingsRepository(
    private val prefs: SharedPreferences? = null
) {
    var workerId: String
        get() = prefs?.getString(KEY_WORKER_ID, DEFAULT_WORKER_ID) ?: DEFAULT_WORKER_ID
        set(value) {
            prefs?.edit()?.putString(KEY_WORKER_ID, value)?.apply()
        }

    var deviceId: String
        get() = prefs?.getString(KEY_DEVICE_ID, DEFAULT_DEVICE_ID) ?: DEFAULT_DEVICE_ID
        set(value) {
            prefs?.edit()?.putString(KEY_DEVICE_ID, value)?.apply()
        }

    var serverUrl: String
        get() = prefs?.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) {
            prefs?.edit()?.putString(KEY_SERVER_URL, value)?.apply()
        }

    var countdownSeconds: Int
        get() = prefs?.getInt(KEY_COUNTDOWN_SECONDS, DEFAULT_COUNTDOWN_SECONDS) ?: DEFAULT_COUNTDOWN_SECONDS
        set(value) {
            prefs?.edit()?.putInt(KEY_COUNTDOWN_SECONDS, value)?.apply()
        }

    companion object {
        const val PREFS_NAME = "safesignal_settings"
        const val KEY_WORKER_ID = "worker_id"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_COUNTDOWN_SECONDS = "countdown_seconds"

        const val DEFAULT_WORKER_ID = "W-1042"
        const val DEFAULT_DEVICE_ID = "EMULATOR34X1X2X0"
        const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080"
        const val DEFAULT_COUNTDOWN_SECONDS = 15

        fun fromContext(context: Context): SettingsRepository {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return SettingsRepository(prefs)
        }
    }
}
