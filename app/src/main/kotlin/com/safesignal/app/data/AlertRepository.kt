package com.safesignal.app.data

import com.safesignal.protocol.AlertType
import com.safesignal.protocol.LocationPayload
import kotlinx.coroutines.flow.Flow

/**
 * Repository coordinating local persistence of alerts in Room.
 * Maintains the local audit log and manages outbox states.
 */
class AlertRepository(
    private val alertDao: AlertDao,
    private val clock: () -> Long = System::currentTimeMillis
) {
    fun getAllAlerts(): Flow<List<AlertEntity>> = alertDao.getAllAlerts()

    suspend fun getAlert(alertId: String): AlertEntity? = alertDao.getAlert(alertId)

    fun observeAlert(alertId: String): Flow<AlertEntity?> = alertDao.observeAlert(alertId)

    suspend fun getPendingAlerts(): List<AlertEntity> = alertDao.getPendingAlerts()

    suspend fun recordAlert(
        alertId: String,
        type: AlertType,
        status: String = AlertEntity.STATUS_PENDING,
        location: LocationPayload? = null,
        batteryPercent: Int = 100,
        attempts: Int = 1
    ): AlertEntity {
        val entity = AlertEntity(
            alertId = alertId,
            type = type.name,
            raisedAtEpochMs = clock(),
            lat = location?.lat,
            lon = location?.lon,
            accuracyM = location?.accuracyM,
            batteryPercent = batteryPercent,
            status = status,
            attempts = attempts
        )
        alertDao.insertAlert(entity)
        return entity
    }

    suspend fun markSent(
        alertId: String,
        attempts: Int,
        sentAtEpochMs: Long = clock()
    ) {
        val current = alertDao.getAlert(alertId)
        if (current != null) {
            alertDao.updateAlert(
                current.copy(
                    status = AlertEntity.STATUS_SENT,
                    attempts = attempts,
                    sentAtEpochMs = sentAtEpochMs
                )
            )
        }
    }

    suspend fun markAcknowledged(
        alertId: String,
        acknowledgedBy: String,
        ackAtEpochMs: Long = clock()
    ) {
        val current = alertDao.getAlert(alertId)
        if (current != null) {
            alertDao.updateAlert(
                current.copy(
                    status = AlertEntity.STATUS_ACKNOWLEDGED,
                    acknowledgedAtEpochMs = ackAtEpochMs,
                    acknowledgedBy = acknowledgedBy
                )
            )
        }
    }

    suspend fun markCancelled(
        alertId: String,
        type: AlertType,
        batteryPercent: Int = 100
    ) {
        val entity = AlertEntity(
            alertId = alertId,
            type = type.name,
            raisedAtEpochMs = clock(),
            batteryPercent = batteryPercent,
            status = AlertEntity.STATUS_CANCELLED,
            attempts = 0
        )
        alertDao.insertAlert(entity)
    }

    suspend fun updateAttempts(alertId: String, attempts: Int) {
        val current = alertDao.getAlert(alertId)
        if (current != null) {
            alertDao.updateAlert(current.copy(attempts = attempts))
        }
    }

    suspend fun clearAll() {
        alertDao.clearAll()
    }
}
