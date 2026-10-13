package com.safesignal.app.data

import java.io.File

/**
 * Interface for logging telemetry and KPI events to disk for measurement (M14).
 */
interface KpiLogger {
    fun logEvent(alertId: String, event: String, detail: String = "")
}

class FileKpiLogger(
    private val logFile: File,
    private val clock: () -> Long = System::currentTimeMillis
) : KpiLogger {

    private val startTimeMs: Long = clock()

    init {
        if (!logFile.exists() || logFile.length() == 0L) {
            logFile.parentFile?.mkdirs()
            logFile.writeText("epochMs,elapsedMs,alertId,event,detail\n")
        }
    }

    @Synchronized
    override fun logEvent(alertId: String, event: String, detail: String) {
        val now = clock()
        val elapsed = (now - startTimeMs).coerceAtLeast(0L)
        logFile.appendText("$now,$elapsed,$alertId,$event,$detail\n")
    }
}

/**
 * In-memory KPI logger for unit testing.
 */
class InMemoryKpiLogger(
    private val clock: () -> Long = System::currentTimeMillis
) : KpiLogger {
    data class Entry(val epochMs: Long, val elapsedMs: Long, val alertId: String, val event: String, val detail: String)

    private val startTimeMs: Long = clock()
    val entries = mutableListOf<Entry>()

    @Synchronized
    override fun logEvent(alertId: String, event: String, detail: String) {
        val now = clock()
        val elapsed = (now - startTimeMs).coerceAtLeast(0L)
        entries.add(Entry(now, elapsed, alertId, event, detail))
    }
}
