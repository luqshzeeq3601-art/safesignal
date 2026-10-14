package com.safesignal.bot

import com.safesignal.protocol.AlertApi
import com.safesignal.protocol.AlertMessage
import com.safesignal.protocol.AlertStateMachine
import com.safesignal.protocol.AlertType
import com.safesignal.protocol.RecordStatus
import com.safesignal.protocol.RetryPolicy
import kotlinx.coroutines.delay
import java.util.UUID

data class BotKpiEvent(
    val epochMs: Long,
    val elapsedMs: Long,
    val alertId: String,
    val event: String,
    val detail: String = ""
)

data class AlertResult(
    val alertId: String,
    val type: AlertType,
    val delivered: Boolean,
    val acknowledged: Boolean,
    val attempts: Int,
    val raisedAtMs: Long = 0L,
    val deliveredAtMs: Long = 0L,
    val ackedAtMs: Long = 0L
)

data class BotReport(
    val totalAlerts: Int,
    val deliveredCount: Int,
    val acknowledgedCount: Int,
    val retriesCount: Int,
    val results: List<AlertResult>,
    val kpiEvents: List<BotKpiEvent> = emptyList()
) {
    fun summaryString(): String =
        "$deliveredCount/$totalAlerts delivered, $acknowledgedCount/$totalAlerts acknowledged, retries=$retriesCount"
}

/**
 * Fake worker bot simulating lone worker emergency alerts, retry loops, and ack polling.
 */
class WorkerBot(
    val api: AlertApi,
    val workerId: String = "W-BOT-1",
    val deviceId: String = "JVM-BOT-1",
    val retryPolicy: RetryPolicy = RetryPolicy(initialDelayMs = 400, maxDelayMs = 2000, backoffFactor = 2.0, maxAttempts = 10),
    val ackPollIntervalMs: Long = 250,
    val ackTimeoutMs: Long = 10000
) {

    suspend fun sendAlert(
        type: AlertType,
        alertId: String = UUID.randomUUID().toString(),
        simulateInitialFailures: Int = 0,
        eventsCollector: MutableList<BotKpiEvent>? = null,
        shiftStartMs: Long = System.currentTimeMillis()
    ): AlertResult {
        var state: AlertStateMachine.State = AlertStateMachine.State.Monitoring

        val raiseTransition = if (type == AlertType.SOS) {
            AlertStateMachine.transition(state, AlertStateMachine.Event.SosPressed(alertId), retryPolicy)
        } else {
            val countdown = AlertStateMachine.transition(state, AlertStateMachine.Event.Detected(type), retryPolicy)
            AlertStateMachine.transition(countdown.state, AlertStateMachine.Event.CountdownExpired(alertId), retryPolicy)
        }
        state = raiseTransition.state

        val raisedAtMs = System.currentTimeMillis()
        var deliveredAtMs = 0L
        var ackedAtMs = 0L

        val detectedAtMs = if (type == AlertType.SOS) raisedAtMs else (raisedAtMs - 3000L)
        eventsCollector?.add(
            BotKpiEvent(
                epochMs = detectedAtMs,
                elapsedMs = (detectedAtMs - shiftStartMs).coerceAtLeast(0L),
                alertId = alertId,
                event = "DETECTED",
                detail = type.name
            )
        )
        eventsCollector?.add(
            BotKpiEvent(
                epochMs = raisedAtMs,
                elapsedMs = (raisedAtMs - shiftStartMs).coerceAtLeast(0L),
                alertId = alertId,
                event = "RAISED",
                detail = type.name
            )
        )

        var attempt = 1
        var delivered = false
        var acknowledged = false
        var simulatedFailsLeft = simulateInitialFailures

        while (attempt <= retryPolicy.maxAttempts) {
            if (simulatedFailsLeft > 0) {
                simulatedFailsLeft--
                val failTrans = AlertStateMachine.transition(
                    state,
                    AlertStateMachine.Event.DeliveryFailed(alertId, "Simulated network failure"),
                    retryPolicy
                )
                state = failTrans.state
                val delayMs = retryPolicy.delayForAttempt(attempt + 1)
                println("[Bot $workerId] Attempt $attempt failed (simulated network outage). Retrying in ${delayMs}ms...")
                eventsCollector?.add(
                    BotKpiEvent(
                        epochMs = System.currentTimeMillis(),
                        elapsedMs = (System.currentTimeMillis() - shiftStartMs).coerceAtLeast(0L),
                        alertId = alertId,
                        event = "SEND_ATTEMPT",
                        detail = "attempt=$attempt"
                    )
                )
                delay(delayMs)
                attempt++
                continue
            }

            val message = AlertMessage(
                protocolVersion = 1,
                alertId = alertId,
                workerId = workerId,
                deviceId = deviceId,
                type = type,
                raisedAtEpochMs = System.currentTimeMillis(),
                batteryPercent = 90,
                attempt = attempt
            )

            println("[Bot $workerId] Dispatching alert $alertId (type=$type, attempt=$attempt)...")
            eventsCollector?.add(
                BotKpiEvent(
                    epochMs = System.currentTimeMillis(),
                    elapsedMs = (System.currentTimeMillis() - shiftStartMs).coerceAtLeast(0L),
                    alertId = alertId,
                    event = "SEND_ATTEMPT",
                    detail = "attempt=$attempt"
                )
            )
            val postResult = api.postAlert(message)

            if (postResult.isSuccess) {
                delivered = true
                deliveredAtMs = System.currentTimeMillis()
                eventsCollector?.add(
                    BotKpiEvent(
                        epochMs = deliveredAtMs,
                        elapsedMs = (deliveredAtMs - shiftStartMs).coerceAtLeast(0L),
                        alertId = alertId,
                        event = "DELIVERED",
                        detail = "attempt=$attempt"
                    )
                )
                val succTrans = AlertStateMachine.transition(
                    state,
                    AlertStateMachine.Event.DeliverySucceeded(alertId),
                    retryPolicy
                )
                state = succTrans.state
                println("[Bot $workerId] Alert $alertId delivered successfully on attempt $attempt.")
                break
            } else {
                val error = postResult.exceptionOrNull()?.message ?: "Network error"
                val failTrans = AlertStateMachine.transition(
                    state,
                    AlertStateMachine.Event.DeliveryFailed(alertId, error),
                    retryPolicy
                )
                state = failTrans.state
                val nextAttempt = attempt + 1
                if (retryPolicy.shouldRetry(attempt)) {
                    val delayMs = retryPolicy.delayForAttempt(nextAttempt)
                    println("[Bot $workerId] Attempt $attempt failed ($error). Retrying in ${delayMs}ms...")
                    delay(delayMs)
                }
                attempt++
            }
        }

        if (!delivered) {
            println("[Bot $workerId] Alert $alertId exhausted retries.")
            return AlertResult(
                alertId = alertId,
                type = type,
                delivered = false,
                acknowledged = false,
                attempts = attempt - 1,
                raisedAtMs = raisedAtMs,
                deliveredAtMs = deliveredAtMs,
                ackedAtMs = ackedAtMs
            )
        }

        val pollStart = System.currentTimeMillis()
        println("[Bot $workerId] Polling for acknowledgement on alert $alertId...")
        while (System.currentTimeMillis() - pollStart < ackTimeoutMs) {
            val getResult = api.getAlert(alertId)
            if (getResult.isSuccess) {
                val record = getResult.getOrThrow()
                if (record.status == RecordStatus.ACKNOWLEDGED) {
                    acknowledged = true
                    ackedAtMs = System.currentTimeMillis()
                    eventsCollector?.add(
                        BotKpiEvent(
                            epochMs = ackedAtMs,
                            elapsedMs = (ackedAtMs - shiftStartMs).coerceAtLeast(0L),
                            alertId = alertId,
                            event = "ACK_SEEN",
                            detail = alertId
                        )
                    )
                    val ackTrans = AlertStateMachine.transition(
                        state,
                        AlertStateMachine.Event.AckReceived(alertId, record.acknowledgedBy ?: "Dispatcher"),
                        retryPolicy
                    )
                    state = ackTrans.state
                    println("[Bot $workerId] Alert $alertId ACKNOWLEDGED by ${record.acknowledgedBy}!")
                    break
                }
            }
            delay(ackPollIntervalMs)
        }

        return AlertResult(
            alertId = alertId,
            type = type,
            delivered = delivered,
            acknowledged = acknowledged,
            attempts = attempt,
            raisedAtMs = raisedAtMs,
            deliveredAtMs = deliveredAtMs,
            ackedAtMs = ackedAtMs
        )
    }

    suspend fun runShift(
        alertCount: Int = 5,
        simulateInitialFailures: Int = 1,
        eventsCollector: MutableList<BotKpiEvent>? = null
    ): BotReport {
        val types = listOf(AlertType.FALL, AlertType.MAN_DOWN, AlertType.SOS, AlertType.FALL, AlertType.SOS)
        val results = mutableListOf<AlertResult>()
        val events = eventsCollector ?: mutableListOf()
        val shiftStart = System.currentTimeMillis()
        var totalRetries = 0

        for (i in 0 until alertCount) {
            val type = types[i % types.size]
            val failuresForThisAlert = if (i == 0) simulateInitialFailures else 0
            val res = sendAlert(
                type = type,
                simulateInitialFailures = failuresForThisAlert,
                eventsCollector = events,
                shiftStartMs = shiftStart
            )
            results.add(res)
            val retriesForAlert = (res.attempts - 1).coerceAtLeast(0)
            totalRetries += retriesForAlert
            delay(200)
        }

        return BotReport(
            totalAlerts = alertCount,
            deliveredCount = results.count { it.delivered },
            acknowledgedCount = results.count { it.acknowledged },
            retriesCount = totalRetries,
            results = results,
            kpiEvents = events
        )
    }
}
