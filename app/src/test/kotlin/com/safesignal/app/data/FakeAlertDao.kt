package com.safesignal.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory FakeAlertDao for millisecond JVM unit tests without Android SQLite dependencies.
 */
class FakeAlertDao : AlertDao {
    private val alertsMap = mutableMapOf<String, AlertEntity>()
    private val alertsFlow = MutableStateFlow<List<AlertEntity>>(emptyList())

    private fun emit() {
        alertsFlow.value = alertsMap.values.sortedByDescending { it.raisedAtEpochMs }
    }

    override suspend fun insertAlert(alert: AlertEntity) {
        alertsMap[alert.alertId] = alert
        emit()
    }

    override suspend fun updateAlert(alert: AlertEntity) {
        alertsMap[alert.alertId] = alert
        emit()
    }

    override suspend fun getAlert(alertId: String): AlertEntity? {
        return alertsMap[alertId]
    }

    override fun observeAlert(alertId: String): Flow<AlertEntity?> {
        return alertsFlow.map { list -> list.find { it.alertId == alertId } }
    }

    override fun getAllAlerts(): Flow<List<AlertEntity>> {
        return alertsFlow
    }

    override suspend fun getPendingAlerts(): List<AlertEntity> {
        return alertsMap.values
            .filter { it.status == AlertEntity.STATUS_PENDING }
            .sortedBy { it.raisedAtEpochMs }
    }

    override suspend fun updateAlertStatus(
        alertId: String,
        status: String,
        attempts: Int,
        sentAtEpochMs: Long?,
        ackAtEpochMs: Long?,
        ackBy: String?
    ) {
        val current = alertsMap[alertId] ?: return
        alertsMap[alertId] = current.copy(
            status = status,
            attempts = attempts,
            sentAtEpochMs = sentAtEpochMs,
            acknowledgedAtEpochMs = ackAtEpochMs,
            acknowledgedBy = ackBy
        )
        emit()
    }

    override suspend fun deleteAlert(alertId: String) {
        alertsMap.remove(alertId)
        emit()
    }

    override suspend fun clearAll() {
        alertsMap.clear()
        emit()
    }
}
