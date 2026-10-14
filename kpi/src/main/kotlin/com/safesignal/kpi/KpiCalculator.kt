package com.safesignal.kpi

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Calculates the four system KPIs specified in PRD.md and Data.md:
 * 1. Detection-to-alert latency (p50, p95, and countdown delay)
 * 2. Delivery success rate (within 60s, plus first-attempt rate)
 * 3. False-alarm rate (field cancellation rate and lab trace false-positive rate)
 * 4. Battery use per hour (estimated mAh/hr)
 */
object KpiCalculator {

    fun calculate(
        appEvents: List<KpiAppEvent>,
        serverEvents: List<KpiServerEvent> = emptyList(),
        batteryRecord: KpiBatteryRecord? = null,
        labFalsePositives: Int = 0,
        labTotalTraces: Int = 9
    ): KpiSummary {
        // --- 1. Latency & Delivery ---
        val raisedEvents = appEvents.filter { it.event == "RAISED" }
        val deliveredEvents = appEvents.filter { it.event == "DELIVERED" }
        val detectedEvents = appEvents.filter { it.event == "DETECTED" }
        val cancelledEvents = appEvents.filter { it.event == "COUNTDOWN_CANCELLED" }

        val latenciesMs = mutableListOf<Long>()
        val countdownDelaysMs = mutableListOf<Long>()
        var deliveredWithin60sCount = 0
        var firstAttemptCount = 0

        // Map by alertId when available
        val raisedByAlertId = raisedEvents.filter { it.alertId.isNotEmpty() }.associateBy { it.alertId }
        // The app logs DELIVERED twice per alert (AlertSyncer with "attempt=N", then the state
        // machine); the earliest row is the actual delivery and carries the attempt number.
        val deliveredByAlertId = deliveredEvents.filter { it.alertId.isNotEmpty() }
            .groupBy { it.alertId }
            .mapValues { (_, rows) -> rows.minByOrNull { it.epochMs }!! }

        for ((alertId, raised) in raisedByAlertId) {
            val delivered = deliveredByAlertId[alertId]
            if (delivered != null) {
                val latency = (delivered.epochMs - raised.epochMs).coerceAtLeast(0L)
                latenciesMs.add(latency)
                if (latency <= 60_000L) {
                    deliveredWithin60sCount++
                }
                if (isFirstAttempt(delivered)) {
                    firstAttemptCount++
                }
            }
        }

        // Fallback for events logged without alertId (e.g. earlier mock or sequence matching)
        if (latenciesMs.isEmpty() && raisedEvents.isNotEmpty() && deliveredEvents.isNotEmpty()) {
            val minSize = minOf(raisedEvents.size, deliveredEvents.size)
            for (i in 0 until minSize) {
                val latency = (deliveredEvents[i].epochMs - raisedEvents[i].epochMs).coerceAtLeast(0L)
                latenciesMs.add(latency)
                if (latency <= 60_000L) deliveredWithin60sCount++
                if (isFirstAttempt(deliveredEvents[i])) {
                    firstAttemptCount++
                }
            }
        }

        // Countdown delays (RAISED - DETECTED)
        if (detectedEvents.isNotEmpty() && raisedEvents.isNotEmpty()) {
            val minPairs = minOf(detectedEvents.size, raisedEvents.size)
            for (i in 0 until minPairs) {
                val delay = (raisedEvents[i].epochMs - detectedEvents[i].epochMs).coerceAtLeast(0L)
                countdownDelaysMs.add(delay)
            }
        }

        val latencyP50 = calculatePercentile(latenciesMs, 0.50)
        val latencyP95 = calculatePercentile(latenciesMs, 0.95)
        val countdownP50 = calculatePercentile(countdownDelaysMs, 0.50)

        // --- 2. Delivery Success Rate ---
        val totalRaised = raisedEvents.size
        val deliveryRatePercent = if (totalRaised > 0) {
            (deliveredWithin60sCount.toDouble() / totalRaised) * 100.0
        } else if (deliveredEvents.isNotEmpty()) {
            100.0
        } else {
            0.0
        }

        val firstAttemptRatePercent = if (totalRaised > 0) {
            (firstAttemptCount.toDouble() / totalRaised) * 100.0
        } else {
            0.0
        }

        // --- 3. False-Alarm Rate ---
        val detectedCount = detectedEvents.size
        val cancelledCount = cancelledEvents.size
        val fieldFalseAlarmRate = if (detectedCount > 0) {
            (cancelledCount.toDouble() / detectedCount) * 100.0
        } else {
            0.0
        }

        // --- 4. Battery Consumption ---
        val batteryMahPerHour = batteryRecord?.mahPerHour ?: 0.0
        val batteryTotalMah = batteryRecord?.estimatedMah ?: 0.0
        val batteryDurationHours = batteryRecord?.durationHours ?: 0.0
        val isEstimated = batteryRecord == null || batteryRecord.appUid in listOf("estimated", "no_device")

        return KpiSummary(
            latencyP50Ms = latencyP50,
            latencyP95Ms = latencyP95,
            countdownP50Ms = countdownP50,
            deliveryRatePercent = deliveryRatePercent,
            totalRaised = totalRaised,
            deliveredWithin60s = deliveredWithin60sCount,
            firstAttemptRatePercent = firstAttemptRatePercent,
            firstAttemptDelivered = firstAttemptCount,
            falseAlarmFieldRatePercent = fieldFalseAlarmRate,
            cancelledCount = cancelledCount,
            detectedCount = detectedCount,
            labFalsePositives = labFalsePositives,
            labTotalTraces = labTotalTraces,
            batteryMahPerHour = batteryMahPerHour,
            batteryTotalMah = batteryTotalMah,
            batteryDurationHours = batteryDurationHours,
            batteryIsEstimated = isEstimated
        )
    }

    // Exact match: "attempt=11" must not count as a first-attempt delivery.
    private fun isFirstAttempt(delivered: KpiAppEvent): Boolean =
        delivered.detail == "attempt=1" || delivered.detail.isEmpty()

    private fun calculatePercentile(values: List<Long>, percentile: Double): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        val index = ((sorted.size - 1) * percentile).roundToInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    fun formatMarkdown(summary: KpiSummary): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val timestamp = dateFormat.format(Date())

        val latencyStatus = if (summary.latencyP50Ms <= 5000 && summary.latencyP95Ms <= 15000) "PASS" else "WARN"
        val deliveryStatus = if (summary.deliveryRatePercent >= 95.0) "PASS" else "FAIL"
        val falseAlarmStatus = if (summary.labFalsePositives == 0 && summary.falseAlarmFieldRatePercent < 15.0) "PASS" else "WARN"
        val batteryStatus = if (summary.batteryIsEstimated) "N/A" else if (summary.batteryMahPerHour <= 5.0) "PASS" else "WARN"

        val othersPass = latencyStatus == "PASS" && deliveryStatus == "PASS" && falseAlarmStatus == "PASS"
        val overallStatus = when {
            othersPass && batteryStatus == "PASS" -> "PASS"
            othersPass && batteryStatus == "N/A" -> "PARTIAL (battery not measured)"
            else -> "ATTENTION NEEDED"
        }
        val labRatePercent = if (summary.labTotalTraces > 0) {
            summary.labFalsePositives.toDouble() / summary.labTotalTraces * 100.0
        } else {
            0.0
        }

        return buildString {
            appendLine("# SafeSignal - KPI Measurement Summary")
            appendLine()
            appendLine("Generated: `$timestamp` | Overall Status: **$overallStatus**")
            appendLine()
            appendLine("## System Key Performance Indicators")
            appendLine()
            appendLine("| KPI | Target | Measured Value | Status | Notes |")
            appendLine("|---|---|---|---|---|")
            appendLine("| Detection-to-Alert Latency | p50 < 5s, p95 < 15s | p50: ${summary.latencyP50Ms} ms, p95: ${summary.latencyP95Ms} ms | $latencyStatus | Countdown delay: ${summary.countdownP50Ms} ms |")
            appendLine("| Delivery Success Rate | >= 95.0% within 60s | ${String.format(Locale.US, "%.1f", summary.deliveryRatePercent)}% (${summary.deliveredWithin60s}/${summary.totalRaised} delivered) | $deliveryStatus | First-attempt success: ${String.format(Locale.US, "%.1f", summary.firstAttemptRatePercent)}% (${summary.firstAttemptDelivered}/${summary.totalRaised}) |")
            appendLine("| False-Alarm Rate | Field < 15.0%, Lab: 0 FP | Field: ${String.format(Locale.US, "%.1f", summary.falseAlarmFieldRatePercent)}% (${summary.cancelledCount}/${summary.detectedCount} cancelled), Lab: ${summary.labFalsePositives}/${summary.labTotalTraces} FP (${String.format(Locale.US, "%.1f", labRatePercent)}%) | $falseAlarmStatus | ${if (summary.cancelledCount > 0) "Simulated cancellation verified (${summary.cancelledCount}/${summary.detectedCount} cancelled). " else ""}${summary.labFalsePositives} false detections across ${summary.labTotalTraces} non-fall traces |")
            appendLine("| Battery Consumption | < 5.0 mAh/hr | ${String.format(Locale.US, "%.2f", summary.batteryMahPerHour)} mAh/hr | $batteryStatus | ${if (summary.batteryIsEstimated) "Not measured - no device attached (run kpi-run.ps1 without -Quick)" else "Emulator power_profile estimate: ${String.format(Locale.US, "%.2f", summary.batteryTotalMah)} mAh over ${String.format(Locale.US, "%.2f", summary.batteryDurationHours)} h run time (default: 10 min)"} |")
            appendLine()
            appendLine("## Metric Definitions and Acceptance Proof")
            appendLine()
            appendLine("1. **Detection-to-Alert Latency**: Measured from alert raise (`RAISED`) to dispatcher receipt confirmation (`DELIVERED`). The countdown delay (`RAISED - DETECTED`) is reported separately to distinguish configurable user cancel window from transmission latency.")
            appendLine("2. **Delivery Success Rate**: Percentage of raised alerts successfully ingested by the server within 60 seconds, validating outbox exponential backoff retries over flaky radio channels.")
            appendLine("3. **False-Alarm Rate**: Evaluated via dual-track verification: simulated field worker countdown cancellation (`COUNTDOWN_CANCELLED / DETECTED`) exercising the false-alarm abort flow, alongside automated lab trace evaluation over ${summary.labTotalTraces} non-fall synthetic traces (`walk_*`, `sit_*`, `drop_phone_*`). Real-world false alarm rates require longitudinal worker fleet telemetry.")
            appendLine("4. **Battery Consumption**: Evaluated from Android `dumpsys batterystats` using the AOSP platform `power_profile.xml` model over the test duration (script default 10 minutes) for foreground location service and accelerometer sensor pipeline. Physical hardware coulometer bench testing is required for production battery certification.")
        }
    }

    fun formatCsv(summary: KpiSummary): String {
        val latencyStatus = if (summary.latencyP50Ms <= 5000 && summary.latencyP95Ms <= 15000) "PASS" else "WARN"
        val deliveryStatus = if (summary.deliveryRatePercent >= 95.0) "PASS" else "FAIL"
        val falseAlarmStatus = if (summary.labFalsePositives == 0 && summary.falseAlarmFieldRatePercent < 15.0) "PASS" else "WARN"
        val batteryStatus = if (summary.batteryIsEstimated) "N/A" else if (summary.batteryMahPerHour <= 5.0) "PASS" else "WARN"

        return buildString {
            appendLine("kpi,target,measured_value,status,detail")
            appendLine("Detection-to-Alert Latency,\"p50<5000ms;p95<15000ms\",\"p50=${summary.latencyP50Ms}ms;p95=${summary.latencyP95Ms}ms\",$latencyStatus,\"countdown_p50=${summary.countdownP50Ms}ms\"")
            appendLine("Delivery Success Rate,\">=95.0%\",\"${String.format(Locale.US, "%.1f", summary.deliveryRatePercent)}%\",$deliveryStatus,\"total_raised=${summary.totalRaised};delivered_within_60s=${summary.deliveredWithin60s};first_attempt_rate=${String.format(Locale.US, "%.1f", summary.firstAttemptRatePercent)}%\"")
            appendLine("False-Alarm Rate,\"Field<15.0%;Lab=0\",\"Field=${String.format(Locale.US, "%.1f", summary.falseAlarmFieldRatePercent)}%;Lab=${summary.labFalsePositives}/${summary.labTotalTraces}\",$falseAlarmStatus,\"cancelled=${summary.cancelledCount};detected=${summary.detectedCount};lab_fp=${summary.labFalsePositives}\"")
            appendLine("Battery Consumption,\"<5.0mAh/hr\",\"${String.format(Locale.US, "%.2f", summary.batteryMahPerHour)}mAh/hr\",$batteryStatus,\"emulator_model_estimate;estimated_mah=${String.format(Locale.US, "%.2f", summary.batteryTotalMah)};duration_hours=${String.format(Locale.US, "%.2f", summary.batteryDurationHours)}\"")
        }
    }
}
