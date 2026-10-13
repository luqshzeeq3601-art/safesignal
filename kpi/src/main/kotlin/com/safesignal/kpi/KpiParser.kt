package com.safesignal.kpi

import java.io.File

/**
 * Utility functions for parsing CSV event logs and batterystats output.
 */
object KpiParser {

    fun parseAppEvents(file: File): List<KpiAppEvent> {
        if (!file.exists() || !file.isFile) return emptyList()
        val events = mutableListOf<KpiAppEvent>()
        file.useLines { lines ->
            lines.forEach { line ->
                val clean = line.trim().removePrefix("\uFEFF")
                if (clean.isEmpty() || clean.startsWith("epochMs")) return@forEach

                val parts = clean.split(",", limit = 5)
                if (parts.size >= 4) {
                    val epochMs = parts[0].trim().toLongOrNull() ?: 0L
                    val elapsedMs = parts[1].trim().toLongOrNull() ?: 0L
                    val alertId = parts[2].trim()
                    val event = parts[3].trim()
                    val detail = if (parts.size >= 5) parts[4].trim() else ""
                    events.add(KpiAppEvent(epochMs, elapsedMs, alertId, event, detail))
                }
            }
        }
        return events
    }

    fun parseServerEvents(file: File): List<KpiServerEvent> {
        if (!file.exists() || !file.isFile) return emptyList()
        val events = mutableListOf<KpiServerEvent>()
        file.useLines { lines ->
            lines.forEach { line ->
                val clean = line.trim().removePrefix("\uFEFF")
                if (clean.isEmpty() || clean.startsWith("epochMs")) return@forEach

                val parts = clean.split(",", limit = 4)
                if (parts.size >= 4) {
                    val epochMs = parts[0].trim().toLongOrNull() ?: 0L
                    val alertId = parts[1].trim()
                    val workerId = parts[2].trim()
                    val event = parts[3].trim()
                    events.add(KpiServerEvent(epochMs, alertId, workerId, event))
                }
            }
        }
        return events
    }

    fun parseBatteryCsv(file: File): KpiBatteryRecord? {
        if (!file.exists() || !file.isFile) return null
        file.useLines { lines ->
            for (line in lines) {
                val clean = line.trim().removePrefix("\uFEFF")
                if (clean.isEmpty() || clean.startsWith("epochMs")) continue

                val parts = clean.split(",")
                if (parts.size >= 6) {
                    val epochMs = parts[0].trim().toLongOrNull() ?: System.currentTimeMillis()
                    val appUid = parts[1].trim()
                    val packageId = parts[2].trim()
                    val estimatedMah = parts[3].trim().toDoubleOrNull() ?: 0.0
                    val durationHours = parts[4].trim().toDoubleOrNull() ?: 1.0
                    val mahPerHour = parts[5].trim().toDoubleOrNull() ?: (estimatedMah / durationHours.coerceAtLeast(0.001))
                    return KpiBatteryRecord(epochMs, appUid, packageId, estimatedMah, durationHours, mahPerHour)
                }
            }
        }
        return null
    }

    /**
     * Parses Android `dumpsys batterystats <package>` text output to extract estimated mAh consumption.
     */
    fun parseDumpsysBatterystats(
        dumpsysText: String,
        packageId: String = "com.safesignal.app",
        durationHours: Double = 1.0
    ): KpiBatteryRecord? {
        val lines = dumpsysText.lines()
        var estimatedMah = 0.0
        var uid = "u0a_app"

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.contains("Estimated power use (mAh):", ignoreCase = true)) {
                val valueStr = trimmed.substringAfter(":").trim().split(" ").firstOrNull()
                val parsed = valueStr?.toDoubleOrNull()
                if (parsed != null && parsed > 0.0) {
                    estimatedMah = parsed
                }
            } else if (trimmed.contains("Uid ") && trimmed.contains(packageId)) {
                val uidPart = trimmed.substringAfter("Uid ").substringBefore(":").trim()
                if (uidPart.isNotEmpty()) uid = uidPart
            }
        }

        if (estimatedMah == 0.0) {
            // Regex match fallback for variations across Android API levels
            val regex = Regex("""(?i)Estimated\s+power\s+use\s*\(mAh\):\s*([0-9.]+)""")
            regex.find(dumpsysText)?.let { match ->
                estimatedMah = match.groupValues[1].toDoubleOrNull() ?: 0.0
            }
        }

        if (estimatedMah > 0.0) {
            val safeDuration = if (durationHours > 0.0) durationHours else 1.0
            val mahPerHour = estimatedMah / safeDuration
            return KpiBatteryRecord(
                epochMs = System.currentTimeMillis(),
                appUid = uid,
                packageId = packageId,
                estimatedMah = estimatedMah,
                durationHours = safeDuration,
                mahPerHour = mahPerHour
            )
        }
        return null
    }
}
