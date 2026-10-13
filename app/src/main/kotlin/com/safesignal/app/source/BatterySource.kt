package com.safesignal.app.source

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Data source providing current battery charge percentage.
 */
interface BatterySource {
    fun getBatteryPercent(): Int
}

/**
 * Reads battery level from Android BatteryManager via sticky intent.
 */
class AndroidBatterySource(
    private val context: Context
) : BatterySource {

    override fun getBatteryPercent(): Int {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = context.registerReceiver(null, filter)
            if (intent != null) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    ((level.toFloat() / scale.toFloat()) * 100).toInt()
                } else 100
            } else 100
        } catch (_: Exception) {
            100
        }
    }
}

/**
 * Fake battery provider for testing.
 */
class FakeBatterySource(
    var level: Int = 87
) : BatterySource {
    override fun getBatteryPercent(): Int = level
}
