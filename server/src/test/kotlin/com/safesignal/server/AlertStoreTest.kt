package com.safesignal.server

import com.safesignal.protocol.AlertMessage
import com.safesignal.protocol.AlertType
import com.safesignal.protocol.RecordStatus
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AlertStoreTest {

    private lateinit var tempDir: File
    private lateinit var jsonlFile: File
    private lateinit var kpiCsvFile: File
    private lateinit var store: AlertStore

    private val sampleAlert = AlertMessage(
        alertId = "store-test-1",
        workerId = "W-200",
        deviceId = "DEV-200",
        type = AlertType.MAN_DOWN,
        raisedAtEpochMs = 1791878400000L,
        batteryPercent = 75
    )

    @Before
    fun setUp() {
        tempDir = File.createTempFile("safesignal-test", "").apply {
            delete()
            mkdirs()
        }
        jsonlFile = File(tempDir, "alerts.jsonl")
        kpiCsvFile = File(tempDir, "server_events.csv")
        store = AlertStore(jsonlFile = jsonlFile, kpiCsvFile = kpiCsvFile)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun putReturnsCreatedAndLogsKpi() {
        val res = store.put(sampleAlert, receivedAtEpochMs = 1000L)
        assertTrue(res is AlertStore.PutResult.Created)
        assertEquals(RecordStatus.RECEIVED, res.record.status)

        // Duplicate test
        val dup = store.put(sampleAlert, receivedAtEpochMs = 2000L)
        assertTrue(dup is AlertStore.PutResult.Duplicate)

        // Verify KPI CSV events
        val csvLines = kpiCsvFile.readLines()
        assertEquals(3, csvLines.size) // header + RECEIVED + DUPLICATE
        assertEquals("epochMs,alertId,workerId,event", csvLines[0])
        assertTrue(csvLines[1].contains("store-test-1,W-200,RECEIVED"))
        assertTrue(csvLines[2].contains("store-test-1,W-200,DUPLICATE"))
    }

    @Test
    fun acknowledgeUpdatesRecordAndLogsKpi() {
        store.put(sampleAlert, receivedAtEpochMs = 1000L)
        val acked = store.acknowledge("store-test-1", dispatcher = "Dispatch-Bob", acknowledgedAtEpochMs = 1500L)

        assertNotNull(acked)
        assertEquals(RecordStatus.ACKNOWLEDGED, acked.status)
        assertEquals("Dispatch-Bob", acked.acknowledgedBy)
        assertEquals(1500L, acked.acknowledgedAtEpochMs)

        val csvLines = kpiCsvFile.readLines()
        assertTrue(csvLines.last().contains("store-test-1,W-200,ACKED"))
    }

    @Test
    fun persistencePreservedAcrossStoreInstances() {
        store.put(sampleAlert, receivedAtEpochMs = 1000L)
        store.acknowledge("store-test-1", dispatcher = "Dispatch-Alice", acknowledgedAtEpochMs = 1200L)

        // Instantiate new store pointing to same files
        val newStore = AlertStore(jsonlFile = jsonlFile, kpiCsvFile = kpiCsvFile)
        val loaded = newStore.get("store-test-1")

        assertNotNull(loaded)
        assertEquals(RecordStatus.ACKNOWLEDGED, loaded.status)
        assertEquals("Dispatch-Alice", loaded.acknowledgedBy)
    }
}
