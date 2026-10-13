package com.safesignal.bot

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
import kotlinx.serialization.json.Json
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkerBotTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val sampleAlert = AlertMessage(
        alertId = "test-alert-1",
        workerId = "W-1",
        deviceId = "D-1",
        type = AlertType.FALL,
        raisedAtEpochMs = 1000L,
        batteryPercent = 90
    )

    @Test
    fun testAlertDeliveryAndAckPollingSuccess() = runTest {
        val ackedRecord = AlertRecord(

            alert = sampleAlert,
            status = RecordStatus.ACKNOWLEDGED,
            receivedAtEpochMs = 1000L,
            acknowledgedAtEpochMs = 1100L,
            acknowledgedBy = "Dispatcher"
        )

        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/alerts" -> respond(
                    content = json.encodeToString(AlertRecord.serializer(), ackedRecord),
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/api/alerts/test-alert-1" -> respond(
                    content = json.encodeToString(AlertRecord.serializer(), ackedRecord),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        val api = AlertApi.create(mockEngine)
        val bot = WorkerBot(api = api, ackPollIntervalMs = 10, ackTimeoutMs = 1000)
        val result = bot.sendAlert(AlertType.FALL, alertId = "test-alert-1")

        assertTrue(result.delivered)
        assertTrue(result.acknowledged)
        assertEquals(1, result.attempts)
    }

    @Test
    fun testRetryOnInitialFailureThenSucceeds() = runTest {
        val postAttempts = AtomicInteger(0)
        val sampleAlert = AlertMessage(
            alertId = "retry-alert",
            workerId = "W-1",
            deviceId = "D-1",
            type = AlertType.SOS,
            raisedAtEpochMs = 1000L,
            batteryPercent = 85
        )
        val ackedRecord = AlertRecord(
            alert = sampleAlert,
            status = RecordStatus.ACKNOWLEDGED,
            receivedAtEpochMs = 1000L,
            acknowledgedAtEpochMs = 1200L,
            acknowledgedBy = "Dispatcher"
        )

        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/alerts" -> {
                    val attempt = postAttempts.incrementAndGet()
                    if (attempt == 1) {
                        respondError(HttpStatusCode.ServiceUnavailable, "Server starting up")
                    } else {
                        respond(
                            content = json.encodeToString(AlertRecord.serializer(), ackedRecord),
                            status = HttpStatusCode.Created,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                }
                "/api/alerts/retry-alert" -> respond(
                    content = json.encodeToString(AlertRecord.serializer(), ackedRecord),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        val api = AlertApi.create(mockEngine)
        val retryPolicy = RetryPolicy(initialDelayMs = 20, maxDelayMs = 100, backoffFactor = 2.0)
        val bot = WorkerBot(api = api, retryPolicy = retryPolicy, ackPollIntervalMs = 10, ackTimeoutMs = 1000)
        val result = bot.sendAlert(AlertType.SOS, alertId = "retry-alert")

        assertTrue(result.delivered)
        assertTrue(result.acknowledged)
        assertEquals(2, result.attempts)
    }

    @Test
    fun testRunShiftProducesAccurateReport() = runTest {
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.startsWith("/api/alerts/")) {
                val id = request.url.encodedPath.substringAfterLast("/")
                val sample = AlertMessage(
                    alertId = id,
                    workerId = "W-1",
                    deviceId = "D-1",
                    type = AlertType.FALL,
                    raisedAtEpochMs = 1000L,
                    batteryPercent = 90
                )
                respond(
                    content = json.encodeToString(
                        AlertRecord.serializer(),
                        AlertRecord(sample, RecordStatus.ACKNOWLEDGED, 1000L, 1100L, "D-1")
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                val bodyText = (request.body as? io.ktor.http.content.OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString() ?: ""
                val alert = if (bodyText.isNotEmpty()) {
                    json.decodeFromString<AlertMessage>(bodyText)
                } else {
                    sampleAlert
                }
                respond(
                    content = json.encodeToString(
                        AlertRecord.serializer(),
                        AlertRecord(alert, RecordStatus.RECEIVED, 1000L)
                    ),
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }

        }

        val api = AlertApi.create(mockEngine)
        val bot = WorkerBot(api = api, ackPollIntervalMs = 10, ackTimeoutMs = 1000)
        val report = bot.runShift(alertCount = 3, simulateInitialFailures = 1)

        assertEquals(3, report.totalAlerts)
        assertEquals(3, report.deliveredCount)
        assertEquals(3, report.acknowledgedCount)
        assertEquals(1, report.retriesCount)
    }
}
