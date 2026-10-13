package com.safesignal.app.data

import com.safesignal.protocol.AlertApi
import com.safesignal.protocol.AlertRecord
import com.safesignal.protocol.RecordStatus
import com.safesignal.protocol.RetryPolicy
import kotlinx.coroutines.delay

/**
 * Handles outbox synchronization, exponential backoff retries, and dispatcher acknowledgement polling.
 * Operates deterministically with injectable delay and clock providers.
 */
class AlertSyncer(
    private val repository: AlertRepository,
    private val alertApi: AlertApi,
    private val kpiLogger: KpiLogger,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    val workerId: String = "W-1042",
    val deviceId: String = "EMULATOR34X1X2X0",
    private val delayProvider: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) }
) {

    /**
     * Attempts delivery of a single pending alert from the outbox with exponential backoff.
     */
    suspend fun sendPendingAlert(alertId: String): Result<AlertRecord> {
        var alert = repository.getAlert(alertId)
        if (alert == null) {
            repeat(5) {
                delayProvider(100L)
                alert = repository.getAlert(alertId)
                if (alert != null) return@repeat
            }
        }
        if (alert == null) {
            return Result.failure(IllegalArgumentException("Alert $alertId not found"))
        }

        if (alert.status != AlertEntity.STATUS_PENDING) {
            return Result.failure(IllegalStateException("Alert $alertId is not PENDING (current status: ${alert.status})"))
        }

        var currentAttempt = alert.attempts.coerceAtLeast(1)

        while (true) {
            val message = alert.toAlertMessage(workerId, deviceId).copy(attempt = currentAttempt)
            kpiLogger.logEvent(alertId, "SEND_ATTEMPT", "attempt=$currentAttempt")

            val result = alertApi.postAlert(message)
            if (result.isSuccess) {
                val record = result.getOrThrow()
                repository.markSent(alertId, currentAttempt)
                kpiLogger.logEvent(alertId, "DELIVERED", "attempt=$currentAttempt")
                return Result.success(record)
            }

            val exception = result.exceptionOrNull() ?: Exception("Unknown delivery error")
            kpiLogger.logEvent(
                alertId,
                "SEND_ATTEMPT_FAILED",
                "attempt=$currentAttempt, error=${exception.message}"
            )

            val nextAttempt = currentAttempt + 1
            if (retryPolicy.shouldRetry(currentAttempt)) {
                repository.updateAttempts(alertId, nextAttempt)
                val backoffDelay = retryPolicy.delayForAttempt(nextAttempt)
                currentAttempt = nextAttempt
                delayProvider(backoffDelay)
            } else {
                kpiLogger.logEvent(alertId, "RETRY_EXHAUSTED", alertId)
                return Result.failure(exception)
            }
        }
    }

    /**
     * Polls the dispatcher server for acknowledgement of a sent alert.
     */
    suspend fun pollForAck(
        alertId: String,
        pollIntervalMs: Long = 3000L,
        maxPollAttempts: Int = 20
    ): Result<AlertRecord> {
        repeat(maxPollAttempts) { attempt ->
            val result = alertApi.getAlert(alertId)
            if (result.isSuccess) {
                val record = result.getOrThrow()
                if (record.status == RecordStatus.ACKNOWLEDGED) {
                    val ackBy = record.acknowledgedBy ?: "DISPATCHER"
                    val ackTime = record.acknowledgedAtEpochMs ?: System.currentTimeMillis()
                    repository.markAcknowledged(alertId, ackBy, ackTime)
                    kpiLogger.logEvent(alertId, "ACK_SEEN", alertId)
                    return Result.success(record)
                }
            }
            if (attempt < maxPollAttempts - 1) {
                delayProvider(pollIntervalMs)
            }
        }
        return Result.failure(IllegalStateException("Ack polling timed out for alert $alertId"))
    }

    /**
     * Drains all pending alerts in the Room outbox.
     */
    suspend fun syncAllPending(): List<Result<AlertRecord>> {
        val pending = repository.getPendingAlerts()
        return pending.map { sendPendingAlert(it.alertId) }
    }
}
