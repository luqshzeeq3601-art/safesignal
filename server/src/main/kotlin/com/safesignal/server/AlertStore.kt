package com.safesignal.server

import com.safesignal.protocol.AlertMessage
import com.safesignal.protocol.AlertRecord
import com.safesignal.protocol.RecordStatus
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe alert store with optional append-only JSONL persistence and KPI event logging.
 */
class AlertStore(
    private val jsonlFile: File? = null,
    private val kpiCsvFile: File? = null,
    private val json: Json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
) {
    private val records = ConcurrentHashMap<String, AlertRecord>()
    private val writeLock = Any()

    sealed interface PutResult {
        val record: AlertRecord

        data class Created(override val record: AlertRecord) : PutResult
        data class Duplicate(override val record: AlertRecord) : PutResult
    }

    init {
        initKpiCsv()
        loadExistingJsonl()
    }

    private fun initKpiCsv() {
        if (kpiCsvFile != null && (!kpiCsvFile.exists() || kpiCsvFile.length() == 0L)) {
            kpiCsvFile.parentFile?.mkdirs()
            kpiCsvFile.writeText("epochMs,alertId,workerId,event\n")
        }
    }

    private fun loadExistingJsonl() {
        if (jsonlFile != null && jsonlFile.exists()) {
            jsonlFile.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    runCatching {
                        val record = json.decodeFromString<AlertRecord>(trimmed)
                        records[record.alert.alertId] = record
                    }
                }
            }
        }
    }

    fun put(
        alert: AlertMessage,
        receivedAtEpochMs: Long = System.currentTimeMillis()
    ): PutResult = synchronized(writeLock) {
        val existing = records[alert.alertId]
        if (existing != null) {
            logKpi(receivedAtEpochMs, alert.alertId, alert.workerId, "DUPLICATE")
            return PutResult.Duplicate(existing)
        }

        val newRecord = AlertRecord(
            alert = alert,
            status = RecordStatus.RECEIVED,
            receivedAtEpochMs = receivedAtEpochMs
        )
        records[alert.alertId] = newRecord
        appendJsonl(newRecord)
        logKpi(receivedAtEpochMs, alert.alertId, alert.workerId, "RECEIVED")
        return PutResult.Created(newRecord)
    }

    fun get(alertId: String): AlertRecord? {
        return records[alertId]
    }

    fun getAll(): List<AlertRecord> {
        return records.values.sortedByDescending { it.receivedAtEpochMs }
    }

    fun acknowledge(
        alertId: String,
        dispatcher: String,
        acknowledgedAtEpochMs: Long = System.currentTimeMillis()
    ): AlertRecord? = synchronized(writeLock) {
        val existing = records[alertId] ?: return null
        val updated = existing.copy(
            status = RecordStatus.ACKNOWLEDGED,
            acknowledgedAtEpochMs = acknowledgedAtEpochMs,
            acknowledgedBy = dispatcher
        )
        records[alertId] = updated
        appendJsonl(updated)
        logKpi(acknowledgedAtEpochMs, alertId, existing.alert.workerId, "ACKED")
        return updated
    }

    fun clear() = synchronized(writeLock) {
        records.clear()
        if (jsonlFile != null && jsonlFile.exists()) {
            jsonlFile.delete()
        }
        if (kpiCsvFile != null && kpiCsvFile.exists()) {
            kpiCsvFile.delete()
            initKpiCsv()
        }
    }

    private fun appendJsonl(record: AlertRecord) {
        if (jsonlFile != null) {
            jsonlFile.parentFile?.mkdirs()
            jsonlFile.appendText(json.encodeToString(AlertRecord.serializer(), record) + "\n")
        }
    }

    private fun logKpi(epochMs: Long, alertId: String, workerId: String, event: String) {
        if (kpiCsvFile != null) {
            kpiCsvFile.parentFile?.mkdirs()
            kpiCsvFile.appendText("$epochMs,$alertId,$workerId,$event\n")
        }
    }
}
