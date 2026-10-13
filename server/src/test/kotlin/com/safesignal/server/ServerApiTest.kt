package com.safesignal.server

import com.safesignal.protocol.AckRequest
import com.safesignal.protocol.AlertMessage
import com.safesignal.protocol.AlertRecord
import com.safesignal.protocol.AlertType
import com.safesignal.protocol.RecordStatus
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServerApiTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val testAlert = AlertMessage(
        alertId = "alert-server-test-1",
        workerId = "W-1042",
        deviceId = "DEV-TEST-1",
        type = AlertType.FALL,
        raisedAtEpochMs = 1791878400000L,
        batteryPercent = 88
    )

    @Test
    fun testHealthEndpointReturnsOk() = testApplication {
        application { module(AlertStore()) }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("OK", response.bodyAsText())
    }

    @Test
    fun testConsoleEndpointReturnsHtml() = testApplication {
        application { module(AlertStore()) }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("SafeSignal Dispatcher Console"))
    }

    @Test
    fun testPostNewAlertReturns201Created() = testApplication {
        val store = AlertStore()
        application { module(store) }

        val testClient = createClient {
            install(ContentNegotiation) { json(json) }
        }

        val response = testClient.post("/api/alerts") {
            contentType(ContentType.Application.Json)
            setBody(testAlert)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val record = response.body<AlertRecord>()
        assertEquals("alert-server-test-1", record.alert.alertId)
        assertEquals(RecordStatus.RECEIVED, record.status)
    }

    @Test
    fun testPostDuplicateAlertReturns200Ok() = testApplication {
        val store = AlertStore()
        application { module(store) }

        val testClient = createClient {
            install(ContentNegotiation) { json(json) }
        }

        // First post
        val firstResponse = testClient.post("/api/alerts") {
            contentType(ContentType.Application.Json)
            setBody(testAlert)
        }
        assertEquals(HttpStatusCode.Created, firstResponse.status)

        // Duplicate post (e.g. network retry)
        val duplicateResponse = testClient.post("/api/alerts") {
            contentType(ContentType.Application.Json)
            setBody(testAlert)
        }
        assertEquals(HttpStatusCode.OK, duplicateResponse.status)
        val record = duplicateResponse.body<AlertRecord>()
        assertEquals("alert-server-test-1", record.alert.alertId)
    }

    @Test
    fun testPostInvalidAlertReturns400BadRequest() = testApplication {
        application { module(AlertStore()) }

        val response = client.post("/api/alerts") {
            contentType(ContentType.Application.Json)
            setBody("{ invalid json }")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun testGetAlertByIdAndNotFound() = testApplication {
        val store = AlertStore()
        store.put(testAlert)
        application { module(store) }

        val testClient = createClient {
            install(ContentNegotiation) { json(json) }
        }

        val found = testClient.get("/api/alerts/alert-server-test-1")
        assertEquals(HttpStatusCode.OK, found.status)
        assertEquals("alert-server-test-1", found.body<AlertRecord>().alert.alertId)

        val notFound = testClient.get("/api/alerts/nonexistent-id")
        assertEquals(HttpStatusCode.NotFound, notFound.status)
    }

    @Test
    fun testListAlertsNewestFirst() = testApplication {
        val store = AlertStore()
        val alert1 = testAlert.copy(alertId = "id-1")
        val alert2 = testAlert.copy(alertId = "id-2")
        store.put(alert1, receivedAtEpochMs = 1000L)
        store.put(alert2, receivedAtEpochMs = 2000L)
        application { module(store) }

        val testClient = createClient {
            install(ContentNegotiation) { json(json) }
        }

        val response = testClient.get("/api/alerts")
        assertEquals(HttpStatusCode.OK, response.status)
        val list = response.body<List<AlertRecord>>()
        assertEquals(2, list.size)
        assertEquals("id-2", list[0].alert.alertId) // newest first
        assertEquals("id-1", list[1].alert.alertId)
    }

    @Test
    fun testAcknowledgeAlertFlow() = testApplication {
        val store = AlertStore()
        store.put(testAlert)
        application { module(store) }

        val testClient = createClient {
            install(ContentNegotiation) { json(json) }
        }

        val ackResponse = testClient.post("/api/alerts/alert-server-test-1/ack") {
            contentType(ContentType.Application.Json)
            setBody(AckRequest(dispatcher = "D-TEST"))
        }

        assertEquals(HttpStatusCode.OK, ackResponse.status)
        val ackedRecord = ackResponse.body<AlertRecord>()
        assertEquals(RecordStatus.ACKNOWLEDGED, ackedRecord.status)
        assertEquals("D-TEST", ackedRecord.acknowledgedBy)
        assertNotNull(ackedRecord.acknowledgedAtEpochMs)

        // Verify status remains ACKNOWLEDGED on subsequent GET
        val getResponse = testClient.get("/api/alerts/alert-server-test-1")
        assertEquals(RecordStatus.ACKNOWLEDGED, getResponse.body<AlertRecord>().status)
    }
}
