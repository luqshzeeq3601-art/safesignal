package com.safesignal.app.data

import com.safesignal.protocol.AlertType
import com.safesignal.protocol.LocationPayload
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AlertRepositoryTest {

    private lateinit var dao: FakeAlertDao
    private lateinit var repository: AlertRepository
    private var simulatedTimeMs = 1700000000000L

    @Before
    fun setUp() {
        dao = FakeAlertDao()
        repository = AlertRepository(
            alertDao = dao,
            clock = { simulatedTimeMs }
        )
    }

    @Test
    fun `recordAlert persists alert with pending status`() = runTest {
        val location = LocationPayload(lat = 3.1390, lon = 101.6869, accuracyM = 4.5, fixAtEpochMs = simulatedTimeMs)
        val alert = repository.recordAlert(
            alertId = "alert-1",
            type = AlertType.FALL,
            location = location,
            batteryPercent = 88
        )

        assertEquals("alert-1", alert.alertId)
        assertEquals("FALL", alert.type)
        assertEquals(AlertEntity.STATUS_PENDING, alert.status)
        assertEquals(88, alert.batteryPercent)
        assertEquals(3.1390, alert.lat!!, 0.0001)

        val retrieved = repository.getAlert("alert-1")
        assertNotNull(retrieved)
        assertEquals("FALL", retrieved?.type)
        assertEquals(AlertEntity.STATUS_PENDING, retrieved?.status)
    }

    @Test
    fun `markSent updates status to SENT with timestamp and attempts`() = runTest {
        repository.recordAlert("alert-1", AlertType.SOS)

        simulatedTimeMs += 5000L
        repository.markSent("alert-1", attempts = 2, sentAtEpochMs = simulatedTimeMs)

        val updated = repository.getAlert("alert-1")
        assertEquals(AlertEntity.STATUS_SENT, updated?.status)
        assertEquals(2, updated?.attempts)
        assertEquals(simulatedTimeMs, updated?.sentAtEpochMs)
    }

    @Test
    fun `markAcknowledged updates status to ACKNOWLEDGED with dispatcher id`() = runTest {
        repository.recordAlert("alert-1", AlertType.MAN_DOWN)
        repository.markSent("alert-1", attempts = 1)

        simulatedTimeMs += 3000L
        repository.markAcknowledged("alert-1", acknowledgedBy = "Dispatcher-Alpha", ackAtEpochMs = simulatedTimeMs)

        val updated = repository.getAlert("alert-1")
        assertEquals(AlertEntity.STATUS_ACKNOWLEDGED, updated?.status)
        assertEquals("Dispatcher-Alpha", updated?.acknowledgedBy)
        assertEquals(simulatedTimeMs, updated?.acknowledgedAtEpochMs)
    }

    @Test
    fun `markCancelled stores alert with CANCELLED status`() = runTest {
        repository.markCancelled("alert-cancel", AlertType.FALL, batteryPercent = 95)

        val cancelled = repository.getAlert("alert-cancel")
        assertEquals(AlertEntity.STATUS_CANCELLED, cancelled?.status)
        assertEquals(0, cancelled?.attempts)
    }

    @Test
    fun `getPendingAlerts only returns alerts in PENDING status`() = runTest {
        repository.recordAlert("alert-pending-1", AlertType.FALL)
        repository.recordAlert("alert-pending-2", AlertType.SOS)
        repository.recordAlert("alert-sent", AlertType.MAN_DOWN)
        repository.markSent("alert-sent", attempts = 1)

        val pending = repository.getPendingAlerts()
        assertEquals(2, pending.size)
        assertEquals(listOf("alert-pending-1", "alert-pending-2"), pending.map { it.alertId })
    }

    @Test
    fun `getAllAlerts flow emits alerts in descending order of creation`() = runTest {
        simulatedTimeMs = 1000L
        repository.recordAlert("first", AlertType.FALL)

        simulatedTimeMs = 2000L
        repository.recordAlert("second", AlertType.SOS)

        val list = repository.getAllAlerts().first()
        assertEquals(listOf("second", "first"), list.map { it.alertId })
    }
}
