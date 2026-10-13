package com.safesignal.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AlertMessageJsonTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun jsonOutputMatchesDataMdSpecification() {
        val sampleJson = """
        {
          "protocolVersion": 1,
          "alertId": "3f1c9c0e-6c1e-4f0e-9d53-0a6c5b1f2e77",
          "workerId": "W-1042",
          "deviceId": "EMULATOR34X1X2X0",
          "type": "FALL",
          "raisedAtEpochMs": 1791878400000,
          "location": { "lat": 3.1390, "lon": 101.6869, "accuracyM": 5.0, "fixAtEpochMs": 1791878398000 },
          "batteryPercent": 87,
          "attempt": 1
        }
        """.trimIndent()

        // 1. Deserialization from Data.md specification JSON
        val decoded = json.decodeFromString<AlertMessage>(sampleJson)
        assertEquals(1, decoded.protocolVersion)
        assertEquals("3f1c9c0e-6c1e-4f0e-9d53-0a6c5b1f2e77", decoded.alertId)
        assertEquals("W-1042", decoded.workerId)
        assertEquals("EMULATOR34X1X2X0", decoded.deviceId)
        assertEquals(AlertType.FALL, decoded.type)
        assertEquals(1791878400000L, decoded.raisedAtEpochMs)
        assertNotNull(decoded.location)
        assertEquals(3.1390, decoded.location.lat, 0.0001)
        assertEquals(101.6869, decoded.location.lon, 0.0001)
        assertEquals(5.0, decoded.location.accuracyM!!, 0.0001)
        assertEquals(1791878398000L, decoded.location.fixAtEpochMs)
        assertEquals(87, decoded.batteryPercent)
        assertEquals(1, decoded.attempt)

        // 2. Round-trip serialization and field-level validation
        val encoded = json.encodeToString(AlertMessage.serializer(), decoded)
        val jsonElement = json.parseToJsonElement(encoded).jsonObject

        assertEquals("1", jsonElement["protocolVersion"]?.jsonPrimitive?.content)
        assertEquals("3f1c9c0e-6c1e-4f0e-9d53-0a6c5b1f2e77", jsonElement["alertId"]?.jsonPrimitive?.content)
        assertEquals("W-1042", jsonElement["workerId"]?.jsonPrimitive?.content)
        assertEquals("EMULATOR34X1X2X0", jsonElement["deviceId"]?.jsonPrimitive?.content)
        assertEquals("FALL", jsonElement["type"]?.jsonPrimitive?.content)
        assertEquals("1791878400000", jsonElement["raisedAtEpochMs"]?.jsonPrimitive?.content)
        assertEquals("87", jsonElement["batteryPercent"]?.jsonPrimitive?.content)
        assertEquals("1", jsonElement["attempt"]?.jsonPrimitive?.content)
    }

    @Test
    fun nullLocationSerializesCorrectly() {
        val alert = AlertMessage(
            alertId = "alert-no-loc",
            workerId = "W-1",
            deviceId = "DEV-1",
            type = AlertType.SOS,
            raisedAtEpochMs = 1000L,
            location = null,
            batteryPercent = 95
        )

        val encoded = json.encodeToString(AlertMessage.serializer(), alert)
        val decoded = json.decodeFromString<AlertMessage>(encoded)

        assertNull(decoded.location)
        assertEquals(AlertType.SOS, decoded.type)
    }

    @Test
    fun alertRecordAndAckRequestSerialization() {
        val alert = AlertMessage(
            alertId = "alert-123",
            workerId = "W-100",
            deviceId = "DEV-100",
            type = AlertType.MAN_DOWN,
            raisedAtEpochMs = 2000L,
            batteryPercent = 50
        )
        val record = AlertRecord(
            alert = alert,
            status = RecordStatus.RECEIVED,
            receivedAtEpochMs = 2100L
        )

        val recordJson = json.encodeToString(AlertRecord.serializer(), record)
        val decodedRecord = json.decodeFromString<AlertRecord>(recordJson)
        assertEquals(RecordStatus.RECEIVED, decodedRecord.status)
        assertEquals("alert-123", decodedRecord.alert.alertId)

        val ack = AckRequest(dispatcher = "D-42")
        val ackJson = json.encodeToString(AckRequest.serializer(), ack)
        val decodedAck = json.decodeFromString<AckRequest>(ackJson)
        assertEquals("D-42", decodedAck.dispatcher)
    }
}
