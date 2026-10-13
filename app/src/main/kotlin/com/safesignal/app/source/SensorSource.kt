package com.safesignal.app.source

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.safesignal.detection.AccelSample
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Source of continuous accelerometer samples.
 */
interface SensorSource {
    val samples: Flow<AccelSample>
    fun start()
    fun stop()
}

/**
 * Reads real accelerometer data from Android SensorManager.
 */
class AndroidSensorSource(
    private val context: Context
) : SensorSource, SensorEventListener {

    private val sensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private val _samples = MutableSharedFlow<AccelSample>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val samples: Flow<AccelSample> = _samples.asSharedFlow()

    private var isListening = false

    override fun start() {
        if (isListening) return
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer != null) {
            // SENSOR_DELAY_GAME delivers ~50 Hz (20ms interval)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            isListening = true
        }
    }

    override fun stop() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        isListening = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val sample = AccelSample(
                System.currentTimeMillis(),
                event.values[0].toDouble(),
                event.values[1].toDouble(),
                event.values[2].toDouble()
            )
            _samples.tryEmit(sample)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}

/**
 * Replays or injects AccelSamples for deterministic testing or simulated sensor feeds.
 */
class ReplaySensorSource : SensorSource {

    private val _samples = MutableSharedFlow<AccelSample>(
        replay = 1,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val samples: Flow<AccelSample> = _samples.asSharedFlow()

    private var running = false

    override fun start() {
        running = true
    }

    override fun stop() {
        running = false
    }

    fun emitSample(sample: AccelSample) {
        if (running) {
            _samples.tryEmit(sample)
        }
    }

    fun emitSamples(samplesList: List<AccelSample>) {
        if (running) {
            for (s in samplesList) {
                _samples.tryEmit(s)
            }
        }
    }
}
