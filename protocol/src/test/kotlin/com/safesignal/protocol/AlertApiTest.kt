package com.safesignal.protocol

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlertApiTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val sampleAlert = AlertMessage(
        alertId = "alert-test-1",
        workerId = "W-1",
        deviceId = "DEV-1",
        type = AlertType.FALL,
        raisedAtEpochMs = 1791878400000L,
        batteryPercent = 90
    )

    private val sampleRecord = AlertRecord(
        alert = sampleAlert,
        status = RecordStatus.RECEIVED,
        receivedAtEpochMs = 1791878400500L
    )

    @Test
    fun postAlertSendsPayloadAndParsesRecord() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/api/alerts", request.url.encodedPath)
            respond(
                content = json.encodeToString(AlertRecord.serializer(), sampleRecord),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val api = AlertApi.create(mockEngine)
        val result = api.postAlert(sampleAlert)

        assertTrue(result.isSuccess)
        val record = result.getOrThrow()
        assertEquals("alert-test-1", record.alert.alertId)
        assertEquals(RecordStatus.RECEIVED, record.status)
    }

    @Test
    fun getAlertReturnsRecordById() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/api/alerts/alert-test-1", request.url.encodedPath)
            respond(
                content = json.encodeToString(AlertRecord.serializer(), sampleRecord),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val api = AlertApi.create(mockEngine)
        val result = api.getAlert("alert-test-1")

        assertTrue(result.isSuccess)
        assertEquals("alert-test-1", result.getOrThrow().alert.alertId)
    }

    @Test
    fun getAlertsReturnsListOfRecords() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/api/alerts", request.url.encodedPath)
            respond(
                content = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(AlertRecord.serializer()),
                    listOf(sampleRecord)
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val api = AlertApi.create(mockEngine)
        val result = api.getAlerts()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
    }

    @Test
    fun acknowledgeAlertSendsDispatcherAndParsesAck() = runTest {
        val ackedRecord = sampleRecord.copy(
            status = RecordStatus.ACKNOWLEDGED,
            acknowledgedAtEpochMs = 1791878405000L,
            acknowledgedBy = "Dispatcher-1"
        )

        val mockEngine = MockEngine { request ->
            assertEquals("/api/alerts/alert-test-1/ack", request.url.encodedPath)
            respond(
                content = json.encodeToString(AlertRecord.serializer(), ackedRecord),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val api = AlertApi.create(mockEngine)
        val result = api.acknowledgeAlert("alert-test-1", AckRequest("Dispatcher-1"))

        assertTrue(result.isSuccess)
        assertEquals(RecordStatus.ACKNOWLEDGED, result.getOrThrow().status)
        assertEquals("Dispatcher-1", result.getOrThrow().acknowledgedBy)
    }

    @Test
    fun checkHealthReturnsOk() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/health", request.url.encodedPath)
            respond(
                content = "OK",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }

        val api = AlertApi.create(mockEngine)
        val result = api.checkHealth()

        assertTrue(result.isSuccess)
        assertEquals("OK", result.getOrThrow())
    }

    @Test
    fun httpErrorReturnsFailureResult() = runTest {
        val mockEngine = MockEngine {
            respondError(HttpStatusCode.InternalServerError, "Server error")
        }

        val api = AlertApi.create(mockEngine)
        val result = api.getAlert("invalid-id")

        assertTrue(result.isFailure)
    }
}
