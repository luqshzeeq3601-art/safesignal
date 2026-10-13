package com.safesignal.kpi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class KpiTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `KpiParser parses app kpi_events correctly`() {
        val file = tempFolder.newFile("kpi_events.csv")
        file.writeText(
            """
            epochMs,elapsedMs,alertId,event,detail
            1000,0,alert-1,DETECTED,FALL
            4000,3000,alert-1,RAISED,FALL
            4100,3100,alert-1,SEND_ATTEMPT,attempt=1
            4800,3800,alert-1,DELIVERED,attempt=1
            5000,4000,alert-1,ACK_SEEN,alert-1
            """.trimIndent()
        )

        val events = KpiParser.parseAppEvents(file)
        assertEquals(5, events.size)
        assertEquals("alert-1", events[0].alertId)
        assertEquals("DETECTED", events[0].event)
        assertEquals("FALL", events[0].detail)
        assertEquals("DELIVERED", events[3].event)
        assertEquals("attempt=1", events[3].detail)
    }

    @Test
    fun `KpiParser parses battery CSV and dumpsys text`() {
        val csvFile = tempFolder.newFile("battery.csv")
        csvFile.writeText(
            """
            epochMs,appUid,packageId,estimatedMah,durationHours,mahPerHour
            10000,u0a123,com.safesignal.app,7.2,4.0,1.80
            """.trimIndent()
        )

        val record = KpiParser.parseBatteryCsv(csvFile)
        assertNotNull(record)
        assertEquals("u0a123", record?.appUid)
        assertEquals(7.2, record!!.estimatedMah, 0.001)
        assertEquals(4.0, record.durationHours, 0.001)
        assertEquals(1.80, record.mahPerHour, 0.001)

        val dumpsysSample = """
            Battery History:
            ...
            Estimated power use (mAh):
              Capacity: 3000, Computed drain: 14.5, actual drain: 15-18
              Uid u0_a89: 3.60 ( cpu=2.10 sensor=1.50 )
            Uid u0_a89 (com.safesignal.app):
              Estimated power use (mAh): 3.60
        """.trimIndent()

        val parsedFromDumpsys = KpiParser.parseDumpsysBatterystats(dumpsysSample, durationHours = 2.0)
        assertNotNull(parsedFromDumpsys)
        assertEquals(3.60, parsedFromDumpsys!!.estimatedMah, 0.001)
        assertEquals(1.80, parsedFromDumpsys.mahPerHour, 0.001)
    }

    @Test
    fun `KpiCalculator accurately computes latency delivery false alarms and battery`() {
        val appEvents = listOf(
            // Alert 1: fall detected, raised after 3s countdown, delivered in 800ms
            KpiAppEvent(1000, 0, "a1", "DETECTED", "FALL"),
            KpiAppEvent(4000, 3000, "a1", "RAISED", "FALL"),
            KpiAppEvent(4800, 3800, "a1", "DELIVERED", "attempt=1"),
            // Alert 2: SOS immediate, delivered in 500ms
            KpiAppEvent(6000, 5000, "a2", "RAISED", "SOS"),
            KpiAppEvent(6500, 5500, "a2", "DELIVERED", "attempt=1"),
            // Alert 3: fall detected, but user cancelled countdown
            KpiAppEvent(8000, 7000, "a3", "DETECTED", "FALL"),
            KpiAppEvent(9500, 8500, "a3", "COUNTDOWN_CANCELLED", "FALL")
        )

        val batteryRecord = KpiBatteryRecord(
            epochMs = 10000,
            appUid = "u0_a89",
            packageId = "com.safesignal.app",
            estimatedMah = 3.6,
            durationHours = 2.0,
            mahPerHour = 1.8
        )

        val summary = KpiCalculator.calculate(
            appEvents = appEvents,
            batteryRecord = batteryRecord,
            labFalsePositives = 0,
            labTotalTraces = 9
        )

        // Latencies: a1=800ms, a2=500ms. Median (p50) is 650/800ms, p95 is 800ms
        assertTrue("Latency p50 should be around 500-800ms", summary.latencyP50Ms in 500..800)
        assertEquals(800L, summary.latencyP95Ms)
        assertEquals(3000L, summary.countdownP50Ms)

        // Delivery: 2 raised, 2 delivered within 60s -> 100%
        assertEquals(2, summary.totalRaised)
        assertEquals(2, summary.deliveredWithin60s)
        assertEquals(100.0, summary.deliveryRatePercent, 0.001)
        assertEquals(100.0, summary.firstAttemptRatePercent, 0.001)

        // False alarm: 2 detected, 1 cancelled -> 50%
        assertEquals(2, summary.detectedCount)
        assertEquals(1, summary.cancelledCount)
        assertEquals(50.0, summary.falseAlarmFieldRatePercent, 0.001)
        assertEquals(0, summary.labFalsePositives)

        // Battery: 1.8 mAh/hr
        assertEquals(1.8, summary.batteryMahPerHour, 0.001)

        // Format Markdown and verify 4 KPI rows exist with numbers
        val markdown = KpiCalculator.formatMarkdown(summary)
        assertTrue(markdown.contains("| Detection-to-Alert Latency |"))
        assertTrue(markdown.contains("| Delivery Success Rate |"))
        assertTrue(markdown.contains("| False-Alarm Rate |"))
        assertTrue(markdown.contains("| Battery Consumption |"))
        assertTrue(markdown.contains("p50: ${summary.latencyP50Ms} ms"))
        assertTrue(markdown.contains("100.0% (2/2 delivered)"))
        assertTrue(markdown.contains("1.80 mAh/hr"))

        // Format CSV
        val csv = KpiCalculator.formatCsv(summary)
        assertTrue(csv.contains("Detection-to-Alert Latency"))
        assertTrue(csv.contains("Delivery Success Rate"))
        assertTrue(csv.contains("False-Alarm Rate"))
        assertTrue(csv.contains("Battery Consumption"))
    }

    @Test
    fun `KpiMain execution generates summary md and summary csv files`() {
        val outDir = tempFolder.newFolder("output")
        val appEventsFile = tempFolder.newFile("kpi_events.csv")
        appEventsFile.writeText(
            """
            epochMs,elapsedMs,alertId,event,detail
            1000,0,alert-1,DETECTED,FALL
            4000,3000,alert-1,RAISED,FALL
            4900,3900,alert-1,DELIVERED,attempt=1
            """.trimIndent()
        )

        val batteryFile = tempFolder.newFile("battery.csv")
        batteryFile.writeText(
            """
            epochMs,appUid,packageId,estimatedMah,durationHours,mahPerHour
            10000,u0a89,com.safesignal.app,1.9,1.0,1.9
            """.trimIndent()
        )

        main(
            arrayOf(
                "--app-events", appEventsFile.absolutePath,
                "--battery", batteryFile.absolutePath,
                "--output-dir", outDir.absolutePath
            )
        )

        val summaryMd = File(outDir, "summary.md")
        val summaryCsv = File(outDir, "summary.csv")

        assertTrue("summary.md must exist", summaryMd.exists())
        assertTrue("summary.csv must exist", summaryCsv.exists())

        val mdLines = summaryMd.readLines()
        val kpiTableRows = mdLines.filter { it.startsWith("| Detection-to-Alert Latency |") ||
                it.startsWith("| Delivery Success Rate |") ||
                it.startsWith("| False-Alarm Rate |") ||
                it.startsWith("| Battery Consumption |") }
        assertEquals("Must contain 4 KPI rows", 4, kpiTableRows.size)
    }
}
