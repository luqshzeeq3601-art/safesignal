package com.safesignal.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KpiLoggerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `FileKpiLogger writes header and formatted CSV rows`() {
        val file = tempFolder.newFile("kpi_events.csv")
        var currentTime = 1000L

        val logger = FileKpiLogger(file, clock = { currentTime })

        currentTime = 2500L
        logger.logEvent("alert-123", "DETECTED", "FALL")

        currentTime = 3000L
        logger.logEvent("alert-123", "DELIVERED", "attempt=1")

        val lines = file.readLines()
        assertEquals("epochMs,elapsedMs,alertId,event,detail", lines[0])
        assertEquals("2500,1500,alert-123,DETECTED,FALL", lines[1])
        assertEquals("3000,2000,alert-123,DELIVERED,attempt=1", lines[2])
    }

    @Test
    fun `InMemoryKpiLogger records entries in memory`() {
        var currentTime = 10000L
        val logger = InMemoryKpiLogger(clock = { currentTime })

        currentTime = 12000L
        logger.logEvent("alert-99", "RAISED", "SOS")

        assertEquals(1, logger.entries.size)
        val entry = logger.entries[0]
        assertEquals(12000L, entry.epochMs)
        assertEquals(2000L, entry.elapsedMs)
        assertEquals("alert-99", entry.alertId)
        assertEquals("RAISED", entry.event)
        assertEquals("SOS", entry.detail)
    }
}
