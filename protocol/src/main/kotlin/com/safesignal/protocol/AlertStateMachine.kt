package com.safesignal.protocol

/**
 * Pure state machine modeling the lone worker emergency lifecycle.
 * All functions are pure with zero side-effects or I/O.
 */
object AlertStateMachine {

    const val DEFAULT_COUNTDOWN_SECONDS = 15

    sealed interface State {
        data object Idle : State
        data object Monitoring : State
        data class Countdown(val type: AlertType, val secondsLeft: Int) : State
        data class Sending(val alertId: String, val attempt: Int, val type: AlertType = AlertType.SOS) : State
        data class Sent(val alertId: String, val type: AlertType = AlertType.SOS) : State
        data class Acknowledged(val alertId: String, val acknowledgedBy: String? = null, val type: AlertType = AlertType.SOS) : State
    }

    sealed interface Event {
        data object StartShift : Event
        data object StopShift : Event
        data class Detected(val type: AlertType) : Event
        data class CountdownTick(val secondsLeft: Int) : Event
        data class CountdownExpired(val alertId: String) : Event
        data object CancelPressed : Event
        data class SosPressed(val alertId: String) : Event
        data class DeliverySucceeded(val alertId: String) : Event
        data class DeliveryFailed(val alertId: String, val error: String) : Event
        data class AckReceived(val alertId: String, val acknowledgedBy: String) : Event
        data object Reset : Event
    }

    sealed interface Effect {
        data class StartCountdown(val type: AlertType, val durationSeconds: Int) : Effect
        data object StopCountdown : Effect
        data class PersistAlert(
            val alertId: String,
            val type: AlertType? = null,
            val status: String,
            val acknowledgedBy: String? = null
        ) : Effect
        data class SendAlert(val alertId: String, val attempt: Int) : Effect
        data class ScheduleRetry(val alertId: String, val nextAttempt: Int, val delayMs: Long) : Effect
        data class StartAckPolling(val alertId: String) : Effect
        data class StopAckPolling(val alertId: String) : Effect
        data class LogKpi(val eventName: String, val detail: String = "") : Effect
    }

    data class Transition(
        val state: State,
        val effects: List<Effect> = emptyList()
    )

    fun transition(
        currentState: State,
        event: Event,
        retryPolicy: RetryPolicy = RetryPolicy(),
        countdownSeconds: Int = DEFAULT_COUNTDOWN_SECONDS
    ): Transition {
        return when (currentState) {
            is State.Idle -> when (event) {
                is Event.StartShift -> Transition(
                    state = State.Monitoring,
                    effects = listOf(Effect.LogKpi("SHIFT_STARTED"))
                )
                else -> Transition(currentState)
            }

            is State.Monitoring -> when (event) {
                is Event.StopShift -> Transition(
                    state = State.Idle,
                    effects = listOf(Effect.LogKpi("SHIFT_STOPPED"))
                )

                is Event.Detected -> Transition(
                    state = State.Countdown(event.type, countdownSeconds),
                    effects = listOf(
                        Effect.StartCountdown(event.type, countdownSeconds),
                        Effect.LogKpi("DETECTED", event.type.name)
                    )
                )

                is Event.SosPressed -> Transition(
                    state = State.Sending(event.alertId, 1, AlertType.SOS),
                    effects = listOf(
                        Effect.PersistAlert(event.alertId, AlertType.SOS, "PENDING"),
                        Effect.SendAlert(event.alertId, 1),
                        Effect.LogKpi("RAISED", "SOS")
                    )
                )

                else -> Transition(currentState)
            }

            is State.Countdown -> when (event) {
                is Event.CancelPressed -> Transition(
                    state = State.Monitoring,
                    effects = listOf(
                        Effect.StopCountdown,
                        Effect.LogKpi("COUNTDOWN_CANCELLED", currentState.type.name)
                    )
                )

                is Event.CountdownTick -> Transition(
                    state = State.Countdown(currentState.type, event.secondsLeft)
                )

                is Event.CountdownExpired -> Transition(
                    state = State.Sending(event.alertId, 1, currentState.type),
                    effects = listOf(
                        Effect.StopCountdown,
                        Effect.PersistAlert(event.alertId, currentState.type, "PENDING"),
                        Effect.SendAlert(event.alertId, 1),
                        Effect.LogKpi("RAISED", currentState.type.name)
                    )
                )

                is Event.SosPressed -> Transition(
                    state = State.Sending(event.alertId, 1, AlertType.SOS),
                    effects = listOf(
                        Effect.StopCountdown,
                        Effect.PersistAlert(event.alertId, AlertType.SOS, "PENDING"),
                        Effect.SendAlert(event.alertId, 1),
                        Effect.LogKpi("RAISED", "SOS")
                    )
                )

                // Detections and StopShift are ignored during active countdown
                else -> Transition(currentState)
            }

            is State.Sending -> when (event) {
                is Event.DeliverySucceeded -> {
                    if (event.alertId == currentState.alertId) {
                        Transition(
                            state = State.Sent(currentState.alertId, currentState.type),
                            effects = listOf(
                                Effect.PersistAlert(currentState.alertId, currentState.type, "SENT"),
                                Effect.StartAckPolling(currentState.alertId),
                                Effect.LogKpi("DELIVERED", currentState.alertId)
                            )
                        )
                    } else {
                        Transition(currentState)
                    }
                }

                is Event.DeliveryFailed -> {
                    if (event.alertId == currentState.alertId) {
                        val nextAttempt = currentState.attempt + 1
                        if (retryPolicy.shouldRetry(currentState.attempt)) {
                            val delayMs = retryPolicy.delayForAttempt(nextAttempt)
                            Transition(
                                state = State.Sending(currentState.alertId, nextAttempt, currentState.type),
                                effects = listOf(
                                    Effect.ScheduleRetry(currentState.alertId, nextAttempt, delayMs),
                                    Effect.LogKpi("SEND_ATTEMPT_FAILED", "attempt=${currentState.attempt}, error=${event.error}")
                                )
                            )
                        } else {
                            Transition(
                                state = currentState,
                                effects = listOf(
                                    Effect.LogKpi("RETRY_EXHAUSTED", currentState.alertId)
                                )
                            )
                        }
                    } else {
                        Transition(currentState)
                    }
                }

                is Event.Reset -> Transition(
                    state = State.Monitoring,
                    effects = listOf(
                        Effect.StopAckPolling(currentState.alertId),
                        Effect.LogKpi("RESET")
                    )
                )

                // Only one active alert at a time: detections, SOS and StopShift ignored
                else -> Transition(currentState)
            }

            is State.Sent -> when (event) {
                is Event.AckReceived -> {
                    if (event.alertId == currentState.alertId) {
                        Transition(
                            state = State.Acknowledged(currentState.alertId, event.acknowledgedBy, currentState.type),
                            effects = listOf(
                                Effect.PersistAlert(currentState.alertId, currentState.type, "ACKNOWLEDGED", event.acknowledgedBy),
                                Effect.StopAckPolling(currentState.alertId),
                                Effect.LogKpi("ACK_SEEN", currentState.alertId)
                            )
                        )
                    } else {
                        Transition(currentState)
                    }
                }

                is Event.Reset -> Transition(
                    state = State.Monitoring,
                    effects = listOf(
                        Effect.StopAckPolling(currentState.alertId),
                        Effect.LogKpi("RESET")
                    )
                )

                // Detections, SOS and StopShift ignored while waiting for Ack
                else -> Transition(currentState)
            }

            is State.Acknowledged -> when (event) {
                is Event.Reset -> Transition(
                    state = State.Monitoring,
                    effects = listOf(Effect.LogKpi("RESET"))
                )

                else -> Transition(currentState)
            }
        }
    }
}
