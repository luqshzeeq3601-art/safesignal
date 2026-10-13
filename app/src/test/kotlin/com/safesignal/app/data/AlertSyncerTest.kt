package com.safesignal.app.data

import com.safesignal.protocol.AlertApi
import com.safesignal.protocol.AlertMessage
import com.safesignal.protocol.AlertRecord
import com.safesignal.protocol.AlertType
import com.safesignal.protocol.RecordStatus
import com.safesignal.protocol.RetryPolicy
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AlertSyncerTest {

    private lateinit var dao: FakeAlertDao
    private lateinit var repository: AlertRepository
    private lateinit var kpiLogger: InMemoryKpiLogger
    private var simulatedTimeMs = 1700000000000L
    private val recordedDelays = mutableListOf<Long>()

    private val json = AlertApi.defaultJson

    @Before
    fun setUp() {
        dao = FakeAlertDao()
        repository = AlertRepository(alertDao = dao, clock = { simulatedTimeMs })
        kpiLogger = InMemoryKpiLogger(clock = { simulatedTimeMs })
        recordedDelays.clear()
    }

    private fun createSyncer(
        mockEngine: MockEngine,
        retryPolicy: RetryPolicy = RetryPolicy(initialDelayMs = 1000L, maxAttempts = 3)
    ): AlertSyncer {
        val alertApi = AlertApi.create(mockEngine, baseUrl = "http://testserver", json = json)
        return AlertSyncer(
            repository = repository,
            alertApi = alertApi,
            kpiLogger = kpiLogger,
            retryPolicy = retryPolicy,
            workerId = "W-1042",
            deviceId = "EMULATOR34X1X2X0",
            delayProvider = { delayMs -> recordedDelays.add(delayMs) }
        )
    }

    @Test
    fun `sendPendingAlert succeeds on first attempt and updates Room to SENT`() = runTest {
        repository.recordAlert("alert-1", AlertType.FALL)

        val mockEngine = MockEngine { request ->
            val record = AlertRecord(
                alert = AlertMessage(
                    alertId = "alert-1",
                    workerId = "W-1042",
                    deviceId = "EMULATOR34X1X2X0",
                    type = AlertType.FALL,
                    raisedAtEpochMs = simulatedTimeMs,
                    batteryPercent = 100,
                    attempt = 1
                ),
                status = RecordStatus.RECEIVED,
                receivedAtEpochMs = simulatedTimeMs
            )
            respond(
                content = json.encodeToString(record),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val syncer = createSyncer(mockEngine)
        val result = syncer.sendPendingAlert("alert-1")

        assertTrue("Expected delivery to succeed", result.isSuccess)
        val entity = repository.getAlert("alert-1")
        assertEquals(AlertEntity.STATUS_SENT, entity?.status)
        assertEquals(1, entity?.attempts)
        assertTrue(recordedDelays.isEmpty())

        val events = kpiLogger.entries.map { it.event }
        assertTrue(events.contains("SEND_ATTEMPT"))
        assertTrue(events.contains("DELIVERED"))
    }

    @Test
    fun `sendPendingAlert retries on transient network failure with backoff delay`() = runTest {
        repository.recordAlert("alert-retry", AlertType.SOS)

        val requestCounter = AtomicInteger(0)
        val mockEngine = MockEngine { request ->
            val count = requestCounter.incrementAndGet()
            if (count == 1) {
                // Simulate HTTP 503 Service Unavailable
                respondError(HttpStatusCode.ServiceUnavailable, "Temporary network outage")
            } else {
                val record = AlertRecord(
                    alert = AlertMessage(
                        alertId = "alert-retry",
                        workerId = "W-1042",
                        deviceId = "EMULATOR34X1X2X0",
                        type = AlertType.SOS,
                        raisedAtEpochMs = simulatedTimeMs,
                        batteryPercent = 100,
                        attempt = 2
                    ),
                    status = RecordStatus.RECEIVED,
                    receivedAtEpochMs = simulatedTimeMs
                )
                respond(
                    content = json.encodeToString(record),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val syncer = createSyncer(mockEngine)
        val result = syncer.sendPendingAlert("alert-retry")

        assertTrue("Expected successful recovery on attempt 2", result.isSuccess)
        assertEquals(2, requestCounter.get())
        assertEquals(listOf(1000L), recordedDelays)

        val entity = repository.getAlert("alert-retry")
        assertEquals(AlertEntity.STATUS_SENT, entity?.status)
        assertEquals(2, entity?.attempts)

        val events = kpiLogger.entries.map { it.event }
        assertEquals(listOf("SEND_ATTEMPT", "SEND_ATTEMPT_FAILED", "SEND_ATTEMPT", "DELIVERED"), events)
    }

    @Test
    fun `sendPendingAlert logs RETRY_EXHAUSTED when all retry attempts fail`() = runTest {
        repository.recordAlert("alert-fail", AlertType.MAN_DOWN)

        val requestCounter = AtomicInteger(0)
        val mockEngine = MockEngine { _ ->
            requestCounter.incrementAndGet()
            respondError(HttpStatusCode.InternalServerError, "Server down")
        }

        val syncer = createSyncer(mockEngine, retryPolicy = RetryPolicy(initialDelayMs = 1000L, maxAttempts = 3))
        val result = syncer.sendPendingAlert("alert-fail")

        assertFalse("Expected failure after exhausting retries", result.isSuccess)
        assertEquals(3, requestCounter.get())
        // Backoff delays for attempt 2 (1000 ms) and attempt 3 (2000 ms)
        assertEquals(listOf(1000L, 2000L), recordedDelays)

        val entity = repository.getAlert("alert-fail")
        assertEquals(AlertEntity.STATUS_PENDING, entity?.status)
        assertEquals(3, entity?.attempts)

        val events = kpiLogger.entries.map { it.event }
        assertTrue(events.contains("RETRY_EXHAUSTED"))
    }

    @Test
    fun `pollForAck waits and updates Room when dispatcher acknowledges`() = runTest {
        repository.recordAlert("alert-ack", AlertType.FALL)
        repository.markSent("alert-ack", attempts = 1)

        val pollCounter = AtomicInteger(0)
        val mockEngine = MockEngine { _ ->
            val count = pollCounter.incrementAndGet()
            val status = if (count == 1) RecordStatus.RECEIVED else RecordStatus.ACKNOWLEDGED
            val ackBy = if (count > 1) "Dispatcher-Carol" else null
            val ackTime = if (count > 1) simulatedTimeMs + 5000L else null

            val record = AlertRecord(
                alert = AlertMessage(
                    alertId = "alert-ack",
                    workerId = "W-1042",
                    deviceId = "EMULATOR34X1X2X0",
                    type = AlertType.FALL,
                    raisedAtEpochMs = simulatedTimeMs,
                    batteryPercent = 100,
                    attempt = 1
                ),
                status = status,
                receivedAtEpochMs = simulatedTimeMs,
                acknowledgedAtEpochMs = ackTime,
                acknowledgedBy = ackBy
            )
            respond(
                content = json.encodeToString(record),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val syncer = createSyncer(mockEngine)
        val result = syncer.pollForAck("alert-ack", pollIntervalMs = 3000L)

        assertTrue(result.isSuccess)
        assertEquals(2, pollCounter.get())
        assertEquals(listOf(3000L), recordedDelays)

        val entity = repository.getAlert("alert-ack")
        assertEquals(AlertEntity.STATUS_ACKNOWLEDGED, entity?.status)
        assertEquals("Dispatcher-Carol", entity?.acknowledgedBy)

        val events = kpiLogger.entries.map { it.event }
        assertTrue(events.contains("ACK_SEEN"))
    }

    @Test
    fun `syncAllPending drains outbox of multiple pending alerts`() = runTest {
        repository.recordAlert("pending-1", AlertType.FALL)
        repository.recordAlert("pending-2", AlertType.SOS)

        val mockEngine = MockEngine { request ->
            val alertId = if (request.url.encodedPath.contains("pending-1")) "pending-1" else "pending-2"
            val record = AlertRecord(
                alert = AlertMessage(
                    alertId = alertId,
                    workerId = "W-1042",
                    deviceId = "EMULATOR34X1X2X0",
                    type = AlertType.FALL,
                    raisedAtEpochMs = simulatedTimeMs,
                    batteryPercent = 100,
                    attempt = 1
                ),
                status = RecordStatus.RECEIVED,
                receivedAtEpochMs = simulatedTimeMs
            )
            respond(
                content = json.encodeToString(record),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val syncer = createSyncer(mockEngine)
        val results = syncer.syncAllPending()

        assertEquals(2, results.size)
        assertTrue(results.all { it.isSuccess })
        assertEquals(AlertEntity.STATUS_SENT, repository.getAlert("pending-1")?.status)
        assertEquals(AlertEntity.STATUS_SENT, repository.getAlert("pending-2")?.status)
        assertTrue(repository.getPendingAlerts().isEmpty())
    }
}
