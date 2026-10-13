package com.safesignal.app

import android.app.Application
import android.util.Log
import com.safesignal.app.data.AlertRepository
import com.safesignal.app.data.AlertSyncer
import com.safesignal.app.data.FileKpiLogger
import com.safesignal.app.data.KpiLogger
import com.safesignal.app.data.SafeSignalDatabase
import com.safesignal.app.data.SettingsRepository
import com.safesignal.app.domain.AlertController
import com.safesignal.app.source.AndroidBatterySource
import com.safesignal.app.source.AndroidLocationSource
import com.safesignal.app.source.AndroidSensorSource
import com.safesignal.app.source.BatterySource
import com.safesignal.app.source.LocationSource
import com.safesignal.app.source.SensorSource
import com.safesignal.protocol.AlertApi
import io.ktor.client.engine.cio.CIO
import java.io.File

/**
 * Main Application class providing application-scoped container and dependencies.
 */
class SafeSignalApplication : Application() {

    lateinit var database: SafeSignalDatabase
        private set
    lateinit var alertRepository: AlertRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var kpiLogger: KpiLogger
        private set
    lateinit var sensorSource: SensorSource
        private set
    lateinit var locationSource: LocationSource
        private set
    lateinit var batterySource: BatterySource
        private set
    lateinit var alertApi: AlertApi
        private set
    lateinit var alertSyncer: AlertSyncer
        private set
    lateinit var alertController: AlertController
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = SafeSignalDatabase.create(this)
        settingsRepository = SettingsRepository.fromContext(this)
        alertRepository = AlertRepository(database.alertDao())

        val kpiFile = File(filesDir, "kpi_events.csv")
        kpiLogger = FileKpiLogger(kpiFile)

        sensorSource = AndroidSensorSource(this)
        locationSource = AndroidLocationSource(this)
        batterySource = AndroidBatterySource(this)

        alertApi = AlertApi.create(
            engine = CIO.create(),
            baseUrl = settingsRepository.serverUrl
        )

        alertSyncer = AlertSyncer(
            repository = alertRepository,
            alertApi = alertApi,
            kpiLogger = kpiLogger,
            workerId = settingsRepository.workerId,
            deviceId = settingsRepository.deviceId
        )

        alertController = AlertController(
            alertRepository = alertRepository,
            alertSyncer = alertSyncer,
            locationSource = locationSource,
            batterySource = batterySource,
            kpiLogger = kpiLogger,
            countdownSeconds = settingsRepository.countdownSeconds
        )

        Log.i("SafeSignal", "SafeSignalApplication initialized. Worker: ${settingsRepository.workerId}, Device: ${settingsRepository.deviceId}")
    }

    companion object {
        lateinit var instance: SafeSignalApplication
            private set
    }
}
