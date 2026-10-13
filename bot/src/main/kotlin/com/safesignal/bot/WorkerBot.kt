package com.safesignal.bot

import com.safesignal.protocol.AlertApi
import com.safesignal.protocol.AlertMessage
import com.safesignal.protocol.AlertStateMachine
import com.safesignal.protocol.AlertType
import com.safesignal.protocol.RecordStatus
import com.safesignal.protocol.RetryPolicy
import kotlinx.coroutines.delay
import java.util.UUID

data class AlertResult(
    val alertId: String,
    val type: AlertType,
    val delivered: Boolean,
    val acknowledged: Boolean,
    val attempts: Int
)

data class BotReport(
    val totalAlerts: Int,
    val deliveredCount: Int,
    val acknowledgedCount: Int,
    val retriesCount: Int,
    val results: List<AlertResult>
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
        simulateInitialFailures: Int = 0
    ): AlertResult {
        var state: AlertStateMachine.State = AlertStateMachine.State.Monitoring

        val raiseTransition = if (type == AlertType.SOS) {
            AlertStateMachine.transition(state, AlertStateMachine.Event.SosPressed(alertId), retryPolicy)
        } else {
            val countdown = AlertStateMachine.transition(state, AlertStateMachine.Event.Detected(type), retryPolicy)
            AlertStateMachine.transition(countdown.state, AlertStateMachine.Event.CountdownExpired(alertId), retryPolicy)
        }
        state = raiseTransition.state

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
            val postResult = api.postAlert(message)

            if (postResult.isSuccess) {
                delivered = true
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
            return AlertResult(alertId, type, delivered = false, acknowledged = false, attempts = attempt - 1)
        }

        val pollStart = System.currentTimeMillis()
        println("[Bot $workerId] Polling for acknowledgement on alert $alertId...")
        while (System.currentTimeMillis() - pollStart < ackTimeoutMs) {
            val getResult = api.getAlert(alertId)
            if (getResult.isSuccess) {
                val record = getResult.getOrThrow()
                if (record.status == RecordStatus.ACKNOWLEDGED) {
                    acknowledged = true
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
            attempts = attempt
        )
    }

    suspend fun runShift(
        alertCount: Int = 5,
        simulateInitialFailures: Int = 1
    ): BotReport {
        val types = listOf(AlertType.FALL, AlertType.MAN_DOWN, AlertType.SOS, AlertType.FALL, AlertType.SOS)
        val results = mutableListOf<AlertResult>()
        var totalRetries = 0

        for (i in 0 until alertCount) {
            val type = types[i % types.size]
            val failuresForThisAlert = if (i == 0) simulateInitialFailures else 0
            val res = sendAlert(type, simulateInitialFailures = failuresForThisAlert)
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
            results = results
        )
    }
}
