package com.safesignal.app.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safesignal.app.SafeSignalApplication
import com.safesignal.app.data.SettingsRepository
import com.safesignal.app.domain.AlertController
import com.safesignal.app.service.MonitorService
import com.safesignal.app.source.BatterySource
import com.safesignal.protocol.AlertStateMachine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

class StatusViewModel(
    private val alertController: AlertController = SafeSignalApplication.instance.alertController,
    private val settingsRepository: SettingsRepository = SafeSignalApplication.instance.settingsRepository,
    private val batterySource: BatterySource = SafeSignalApplication.instance.batterySource
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        StatusUiState(
            workerId = settingsRepository.workerId,
            deviceId = settingsRepository.deviceId,
            batteryPercent = batterySource.getBatteryPercent(),
            alertState = alertController.state.value
        )
    )
    val uiState: StateFlow<StatusUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            alertController.state.collectLatest { state ->
                _uiState.value = _uiState.value.copy(
                    alertState = state,
                    batteryPercent = batterySource.getBatteryPercent()
                )
            }
        }
    }

    fun startShift(context: Context) {
        val intent = Intent(context, MonitorService::class.java).apply {
            action = MonitorService.ACTION_START
        }
        try {
            context.startForegroundService(intent)
        } catch (_: Throwable) {
            context.startService(intent)
        }
        alertController.processEvent(AlertStateMachine.Event.StartShift)
    }

    fun stopShift(context: Context) {
        val intent = Intent(context, MonitorService::class.java).apply {
            action = MonitorService.ACTION_STOP
        }
        context.startService(intent)
        alertController.processEvent(AlertStateMachine.Event.StopShift)
    }

    fun cancelCountdown() {
        alertController.processEvent(AlertStateMachine.Event.CancelPressed)
    }

    fun triggerSos() {
        val alertId = UUID.randomUUID().toString()
        alertController.processEvent(AlertStateMachine.Event.SosPressed(alertId))
    }

    fun reset() {
        alertController.processEvent(AlertStateMachine.Event.Reset)
    }
}
