package com.safesignal.app.domain

import android.util.Log
import com.safesignal.app.data.AlertEntity
import com.safesignal.app.data.AlertRepository
import com.safesignal.app.data.AlertSyncer
import com.safesignal.app.data.KpiLogger
import com.safesignal.app.source.BatterySource
import com.safesignal.app.source.LocationSource
import com.safesignal.protocol.AlertStateMachine
import com.safesignal.protocol.AlertType
import com.safesignal.protocol.RetryPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Singleton orchestrator running AlertStateMachine pure transitions and executing side effects.
 */
class AlertController(
    private val alertRepository: AlertRepository,
    private val alertSyncer: AlertSyncer,
    private val locationSource: LocationSource,
    private val batterySource: BatterySource,
    private val kpiLogger: KpiLogger,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    var countdownSeconds: Int = AlertStateMachine.DEFAULT_COUNTDOWN_SECONDS,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val logger: (String, String) -> Unit = { tag, msg ->
        try {
            Log.i(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }
) {

    private val _state = MutableStateFlow<AlertStateMachine.State>(AlertStateMachine.State.Idle)
    val state: StateFlow<AlertStateMachine.State> = _state.asStateFlow()

    private var countdownJob: Job? = null
    private var sendJob: Job? = null
    private var ackJob: Job? = null

    @Synchronized
    fun processEvent(event: AlertStateMachine.Event) {
        val oldState = _state.value
        val transition = AlertStateMachine.transition(
            currentState = oldState,
            event = event,
            retryPolicy = retryPolicy,
            countdownSeconds = countdownSeconds
        )

        _state.value = transition.state
        logTransition(oldState, transition.state)

        if (oldState is AlertStateMachine.State.Countdown && event is AlertStateMachine.Event.CancelPressed) {
            scope.launch {
                val battery = batterySource.getBatteryPercent()
                alertRepository.markCancelled(
                    alertId = UUID.randomUUID().toString(),
                    type = oldState.type,
                    batteryPercent = battery
                )
            }
        }

        for (effect in transition.effects) {
            executeEffect(effect)
        }
    }

    private fun logTransition(old: AlertStateMachine.State, new: AlertStateMachine.State) {
        if (old == new) return

        val logTag = "SafeSignal"
        logger(logTag, "State transition: $old -> $new")

        // Specialized logcat outputs for test & acceptance parsing
        if (old is AlertStateMachine.State.Monitoring && new is AlertStateMachine.State.Countdown) {
            logger(logTag, "Monitoring -> Countdown(${new.type.name})")
        } else if (old is AlertStateMachine.State.Countdown && new is AlertStateMachine.State.Sending) {
            logger(logTag, "Countdown(${old.type.name}) -> Sending")
            logger(logTag, "-> Sending")
        }
    }

    private fun executeEffect(effect: AlertStateMachine.Effect) {
        when (effect) {
            is AlertStateMachine.Effect.StartCountdown -> {
                countdownJob?.cancel()
                countdownJob = scope.launch {
                    val duration = effect.durationSeconds
                    for (sec in (duration - 1) downTo 0) {
                        delay(1000L)
                        val current = _state.value
                        if (current is AlertStateMachine.State.Countdown) {
                            if (sec > 0) {
                                processEvent(AlertStateMachine.Event.CountdownTick(sec))
                            } else {
                                val alertId = UUID.randomUUID().toString()
                                processEvent(AlertStateMachine.Event.CountdownExpired(alertId))
                            }
                        } else {
                            break
                        }
                    }
                }
            }

            is AlertStateMachine.Effect.StopCountdown -> {
                countdownJob?.cancel()
                countdownJob = null
            }

            is AlertStateMachine.Effect.PersistAlert -> {
                scope.launch {
                    when (effect.status) {
                        AlertEntity.STATUS_PENDING -> {
                            val loc = locationSource.getLastKnownLocation()
                            val battery = batterySource.getBatteryPercent()
                            alertRepository.recordAlert(
                                alertId = effect.alertId,
                                type = effect.type ?: AlertType.SOS,
                                status = AlertEntity.STATUS_PENDING,
                                location = loc,
                                batteryPercent = battery
                            )
                        }
                        AlertEntity.STATUS_SENT -> {
                            alertRepository.markSent(effect.alertId, attempts = 1)
                        }
                        AlertEntity.STATUS_ACKNOWLEDGED -> {
                            alertRepository.markAcknowledged(
                                alertId = effect.alertId,
                                acknowledgedBy = effect.acknowledgedBy ?: "DISPATCHER"
                            )
                        }
                        AlertEntity.STATUS_CANCELLED -> {
                            alertRepository.markCancelled(
                                alertId = effect.alertId,
                                type = effect.type ?: AlertType.FALL
                            )
                        }
                    }
                }
            }

            is AlertStateMachine.Effect.SendAlert -> {
                sendJob?.cancel()
                sendJob = scope.launch {
                    val result = alertSyncer.sendPendingAlert(effect.alertId)
                    if (result.isSuccess) {
                        processEvent(AlertStateMachine.Event.DeliverySucceeded(effect.alertId))
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Delivery failure"
                        processEvent(AlertStateMachine.Event.DeliveryFailed(effect.alertId, error))
                    }
                }
            }

            is AlertStateMachine.Effect.ScheduleRetry -> {
                // AlertSyncer handles exponential delay internally during sendPendingAlert
            }

            is AlertStateMachine.Effect.StartAckPolling -> {
                ackJob?.cancel()
                ackJob = scope.launch {
                    val result = alertSyncer.pollForAck(effect.alertId)
                    if (result.isSuccess) {
                        val record = result.getOrThrow()
                        val dispatcher = record.acknowledgedBy ?: "DISPATCHER"
                        processEvent(AlertStateMachine.Event.AckReceived(effect.alertId, dispatcher))
                    }
                }
            }

            is AlertStateMachine.Effect.StopAckPolling -> {
                ackJob?.cancel()
                ackJob = null
            }

            is AlertStateMachine.Effect.LogKpi -> {
                kpiLogger.logEvent(alertId = "", event = effect.eventName, detail = effect.detail)
            }
        }
    }

    fun reset() {
        countdownJob?.cancel()
        sendJob?.cancel()
        ackJob?.cancel()
        _state.value = AlertStateMachine.State.Idle
    }
}
