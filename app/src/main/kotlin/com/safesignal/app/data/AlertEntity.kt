package com.safesignal.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.safesignal.protocol.AlertMessage
import com.safesignal.protocol.AlertType
import com.safesignal.protocol.LocationPayload

/**
 * Room entity representing an alert in the local database.
 * Serves as both local audit history and reliable retry outbox.
 */
@Entity(
    tableName = "alerts",
    indices = [
        Index(value = ["status"]),
        Index(value = ["raisedAtEpochMs"])
    ]
)
data class AlertEntity(
    @PrimaryKey
    val alertId: String,
    val type: String,
    val raisedAtEpochMs: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyM: Double? = null,
    val batteryPercent: Int,
    val status: String, // CANCELLED | PENDING | SENT | ACKNOWLEDGED
    val attempts: Int = 1,
    val sentAtEpochMs: Long? = null,
    val acknowledgedAtEpochMs: Long? = null,
    val acknowledgedBy: String? = null
) {
    fun toAlertMessage(workerId: String, deviceId: String): AlertMessage {
        val loc = if (lat != null && lon != null) {
            LocationPayload(
                lat = lat,
                lon = lon,
                accuracyM = accuracyM,
                fixAtEpochMs = raisedAtEpochMs
            )
        } else null

        return AlertMessage(
            protocolVersion = 1,
            alertId = alertId,
            workerId = workerId,
            deviceId = deviceId,
            type = AlertType.valueOf(type),
            raisedAtEpochMs = raisedAtEpochMs,
            location = loc,
            batteryPercent = batteryPercent,
            attempt = attempts
        )
    }

    companion object {
        const val STATUS_CANCELLED = "CANCELLED"
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SENT = "SENT"
        const val STATUS_ACKNOWLEDGED = "ACKNOWLEDGED"

        fun fromAlertMessage(
            message: AlertMessage,
            status: String = STATUS_PENDING
        ): AlertEntity {
            return AlertEntity(
                alertId = message.alertId,
                type = message.type.name,
                raisedAtEpochMs = message.raisedAtEpochMs,
                lat = message.location?.lat,
                lon = message.location?.lon,
                accuracyM = message.location?.accuracyM,
                batteryPercent = message.batteryPercent,
                status = status,
                attempts = message.attempt
            )
        }
    }
}
