package com.safesignal.protocol

import kotlinx.serialization.Serializable

@Serializable
enum class AlertType {
    FALL,
    MAN_DOWN,
    SOS
}

@Serializable
data class LocationPayload(
    val lat: Double,
    val lon: Double,
    val accuracyM: Double? = null,
    val fixAtEpochMs: Long? = null
)

@Serializable
data class AlertMessage(
    val protocolVersion: Int = 1,
    val alertId: String,
    val workerId: String,
    val deviceId: String,
    val type: AlertType,
    val raisedAtEpochMs: Long,
    val location: LocationPayload? = null,
    val batteryPercent: Int,
    val attempt: Int = 1
)

@Serializable
enum class RecordStatus {
    RECEIVED,
    ACKNOWLEDGED
}

@Serializable
data class AlertRecord(
    val alert: AlertMessage,
    val status: RecordStatus,
    val receivedAtEpochMs: Long,
    val acknowledgedAtEpochMs: Long? = null,
    val acknowledgedBy: String? = null
)

@Serializable
data class AckRequest(
    val dispatcher: String
)
