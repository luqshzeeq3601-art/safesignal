package com.safesignal.app.ui

import com.safesignal.protocol.AlertStateMachine

data class StatusUiState(
    val workerId: String = "",
    val deviceId: String = "",
    val batteryPercent: Int = 100,
    val alertState: AlertStateMachine.State = AlertStateMachine.State.Idle
) {
    val isShiftActive: Boolean
        get() = alertState !is AlertStateMachine.State.Idle

    val isCountdown: Boolean
        get() = alertState is AlertStateMachine.State.Countdown

    val countdownSecondsLeft: Int
        get() = (alertState as? AlertStateMachine.State.Countdown)?.secondsLeft ?: 0

    val countdownType: String
        get() = (alertState as? AlertStateMachine.State.Countdown)?.type?.name ?: ""

    val acknowledgedBy: String?
        get() = (alertState as? AlertStateMachine.State.Acknowledged)?.acknowledgedBy

    val canCancel: Boolean
        get() = alertState is AlertStateMachine.State.Countdown

    val canTriggerSos: Boolean
        get() = alertState !is AlertStateMachine.State.Sending &&
                alertState !is AlertStateMachine.State.Sent &&
                alertState !is AlertStateMachine.State.Acknowledged

    val canReset: Boolean
        get() = alertState is AlertStateMachine.State.Acknowledged
}
