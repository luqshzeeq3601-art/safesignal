package com.safesignal.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local alert persistence and retry queue operations.
 */
@Dao
interface AlertDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEntity)

    @Update
    suspend fun updateAlert(alert: AlertEntity)

    @Query("SELECT * FROM alerts WHERE alertId = :alertId")
    suspend fun getAlert(alertId: String): AlertEntity?

    @Query("SELECT * FROM alerts WHERE alertId = :alertId")
    fun observeAlert(alertId: String): Flow<AlertEntity?>

    @Query("SELECT * FROM alerts ORDER BY raisedAtEpochMs DESC")
    fun getAllAlerts(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE status = 'PENDING' ORDER BY raisedAtEpochMs ASC")
    suspend fun getPendingAlerts(): List<AlertEntity>

    @Query("UPDATE alerts SET status = :status, attempts = :attempts, sentAtEpochMs = :sentAtEpochMs, acknowledgedAtEpochMs = :ackAtEpochMs, acknowledgedBy = :ackBy WHERE alertId = :alertId")
    suspend fun updateAlertStatus(
        alertId: String,
        status: String,
        attempts: Int,
        sentAtEpochMs: Long?,
        ackAtEpochMs: Long?,
        ackBy: String?
    )

    @Query("DELETE FROM alerts WHERE alertId = :alertId")
    suspend fun deleteAlert(alertId: String)

    @Query("DELETE FROM alerts")
    suspend fun clearAll()
}
