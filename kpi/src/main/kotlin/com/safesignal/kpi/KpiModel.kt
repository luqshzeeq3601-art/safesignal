package com.safesignal.kpi

/**
 * Data structures representing telemetry events and KPI summary metrics.
 */

data class KpiAppEvent(
    val epochMs: Long,
    val elapsedMs: Long,
    val alertId: String,
    val event: String,
    val detail: String = ""
)

data class KpiServerEvent(
    val epochMs: Long,
    val alertId: String,
    val workerId: String,
    val event: String
)

data class KpiBatteryRecord(
    val epochMs: Long,
    val appUid: String,
    val packageId: String,
    val estimatedMah: Double,
    val durationHours: Double,
    val mahPerHour: Double
)

data class KpiSummary(
    val latencyP50Ms: Long,
    val latencyP95Ms: Long,
    val countdownP50Ms: Long,
    val deliveryRatePercent: Double,
    val totalRaised: Int,
    val deliveredWithin60s: Int,
    val firstAttemptRatePercent: Double,
    val firstAttemptDelivered: Int,
    val falseAlarmFieldRatePercent: Double,
    val cancelledCount: Int,
    val detectedCount: Int,
    val labFalsePositives: Int,
    val labTotalTraces: Int,
    val batteryMahPerHour: Double,
    val batteryTotalMah: Double,
    val batteryDurationHours: Double
)
