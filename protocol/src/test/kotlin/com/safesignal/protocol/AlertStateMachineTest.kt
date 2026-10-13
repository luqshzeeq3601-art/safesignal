package com.safesignal.protocol

import com.safesignal.protocol.AlertStateMachine.Effect
import com.safesignal.protocol.AlertStateMachine.Event
import com.safesignal.protocol.AlertStateMachine.State
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlertStateMachineTest {

    @Test
    fun idleToMonitoringOnStartShift() {
        val result = AlertStateMachine.transition(State.Idle, Event.StartShift)
        assertEquals(State.Monitoring, result.state)
        assertTrue(result.effects.any { it is Effect.LogKpi && it.eventName == "SHIFT_STARTED" })
    }

    @Test
    fun monitoringToIdleOnStopShift() {
        val result = AlertStateMachine.transition(State.Monitoring, Event.StopShift)
        assertEquals(State.Idle, result.state)
        assertTrue(result.effects.any { it is Effect.LogKpi && it.eventName == "SHIFT_STOPPED" })
    }

    @Test
    fun detectionTransitionsToCountdown() {
        val result = AlertStateMachine.transition(
            State.Monitoring,
            Event.Detected(AlertType.FALL),
            countdownSeconds = 15
        )
        val expectedState = State.Countdown(AlertType.FALL, 15)
        assertEquals(expectedState, result.state)
        assertTrue(result.effects.contains(Effect.StartCountdown(AlertType.FALL, 15)))
        assertTrue(result.effects.any { it is Effect.LogKpi && it.eventName == "DETECTED" && it.detail == "FALL" })
    }

    @Test
    fun cancelPressedAbortsCountdown() {
        val countdown = State.Countdown(AlertType.FALL, 10)
        val result = AlertStateMachine.transition(countdown, Event.CancelPressed)

        assertEquals(State.Monitoring, result.state)
        assertTrue(result.effects.contains(Effect.StopCountdown))
        assertTrue(result.effects.any { it is Effect.LogKpi && it.eventName == "COUNTDOWN_CANCELLED" })
    }

    @Test
    fun countdownTickUpdatesSeconds() {
        val countdown = State.Countdown(AlertType.MAN_DOWN, 15)
        val result = AlertStateMachine.transition(countdown, Event.CountdownTick(14))

        assertEquals(State.Countdown(AlertType.MAN_DOWN, 14), result.state)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun countdownExpiredEscalatesToSending() {
        val countdown = State.Countdown(AlertType.FALL, 0)
        val result = AlertStateMachine.transition(countdown, Event.CountdownExpired("alert-1"))

        assertEquals(State.Sending("alert-1", 1, AlertType.FALL), result.state)
        assertTrue(result.effects.contains(Effect.StopCountdown))
        assertTrue(result.effects.contains(Effect.PersistAlert("alert-1", AlertType.FALL, "PENDING")))
        assertTrue(result.effects.contains(Effect.SendAlert("alert-1", 1)))
        assertTrue(result.effects.any { it is Effect.LogKpi && it.eventName == "RAISED" && it.detail == "FALL" })
    }

    @Test
    fun sosPressedDuringCountdownEscalatesImmediately() {
        val countdown = State.Countdown(AlertType.FALL, 8)
        val result = AlertStateMachine.transition(countdown, Event.SosPressed("sos-alert"))

        assertEquals(State.Sending("sos-alert", 1, AlertType.SOS), result.state)
        assertTrue(result.effects.contains(Effect.StopCountdown))
        assertTrue(result.effects.contains(Effect.PersistAlert("sos-alert", AlertType.SOS, "PENDING")))
        assertTrue(result.effects.contains(Effect.SendAlert("sos-alert", 1)))
        assertTrue(result.effects.any { it is Effect.LogKpi && it.eventName == "RAISED" && it.detail == "SOS" })
    }

    @Test
    fun sosPressedInMonitoringSendsImmediatelyWithoutCountdown() {
        val result = AlertStateMachine.transition(State.Monitoring, Event.SosPressed("direct-sos"))

        assertEquals(State.Sending("direct-sos", 1, AlertType.SOS), result.state)
        assertTrue(result.effects.contains(Effect.PersistAlert("direct-sos", AlertType.SOS, "PENDING")))
        assertTrue(result.effects.contains(Effect.SendAlert("direct-sos", 1)))
    }

    @Test
    fun deliverySucceededTransitionsToSentAndStartsAckPolling() {
        val sending = State.Sending("alert-1", 1, AlertType.FALL)
        val result = AlertStateMachine.transition(sending, Event.DeliverySucceeded("alert-1"))

        assertEquals(State.Sent("alert-1", AlertType.FALL), result.state)
        assertTrue(result.effects.contains(Effect.PersistAlert("alert-1", AlertType.FALL, "SENT")))
        assertTrue(result.effects.contains(Effect.StartAckPolling("alert-1")))
        assertTrue(result.effects.any { it is Effect.LogKpi && it.eventName == "DELIVERED" })
    }

    @Test
    fun deliveryFailedSchedulesRetryWithExponentialBackoff() {
        val policy = RetryPolicy(initialDelayMs = 1000L, backoffFactor = 2.0)
        val sending = State.Sending("alert-1", 1, AlertType.FALL)
        val result = AlertStateMachine.transition(sending, Event.DeliveryFailed("alert-1", "Network timeout"), policy)

        assertEquals(State.Sending("alert-1", 2, AlertType.FALL), result.state)
        assertTrue(result.effects.contains(Effect.ScheduleRetry("alert-1", 2, 1000L)))
    }

    @Test
    fun ackReceivedTransitionsToAcknowledged() {
        val sent = State.Sent("alert-1", AlertType.FALL)
        val result = AlertStateMachine.transition(sent, Event.AckReceived("alert-1", "Dispatcher-Alice"))

        assertEquals(State.Acknowledged("alert-1", "Dispatcher-Alice", AlertType.FALL), result.state)
        assertTrue(result.effects.contains(Effect.PersistAlert("alert-1", AlertType.FALL, "ACKNOWLEDGED", "Dispatcher-Alice")))
        assertTrue(result.effects.contains(Effect.StopAckPolling("alert-1")))
        assertTrue(result.effects.any { it is Effect.LogKpi && it.eventName == "ACK_SEEN" })
    }

    @Test
    fun resetReturnsToMonitoring() {
        val acknowledged = State.Acknowledged("alert-1", "D-1", AlertType.FALL)
        val result = AlertStateMachine.transition(acknowledged, Event.Reset)

        assertEquals(State.Monitoring, result.state)
        assertTrue(result.effects.any { it is Effect.LogKpi && it.eventName == "RESET" })
    }

    @Test
    fun activeAlertIgnoresStopShiftAndFurtherDetections() {
        val sending = State.Sending("alert-active", 1, AlertType.FALL)

        val ignoreStop = AlertStateMachine.transition(sending, Event.StopShift)
        assertEquals(sending, ignoreStop.state)
        assertTrue(ignoreStop.effects.isEmpty())

        val ignoreDetect = AlertStateMachine.transition(sending, Event.Detected(AlertType.MAN_DOWN))
        assertEquals(sending, ignoreDetect.state)
        assertTrue(ignoreDetect.effects.isEmpty())

        val ignoreSos = AlertStateMachine.transition(sending, Event.SosPressed("another-sos"))
        assertEquals(sending, ignoreSos.state)
        assertTrue(ignoreSos.effects.isEmpty())
    }
}
