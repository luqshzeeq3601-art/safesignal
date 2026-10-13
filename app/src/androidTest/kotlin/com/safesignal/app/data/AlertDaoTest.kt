package com.safesignal.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertDaoTest {

    private lateinit var database: SafeSignalDatabase
    private lateinit var dao: AlertDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SafeSignalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.alertDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertAndGetAlertById() = runBlocking {
        val entity = AlertEntity(
            alertId = "e305e94b-4b14-4340-9883-228795730302",
            type = "FALL",
            raisedAtEpochMs = 1791878400000L,
            lat = 3.1390,
            lon = 101.6869,
            accuracyM = 5.0,
            batteryPercent = 87,
            status = AlertEntity.STATUS_PENDING,
            attempts = 1
        )

        dao.insertAlert(entity)

        val loaded = checkNotNull(dao.getAlert("e305e94b-4b14-4340-9883-228795730302"))
        assertEquals("FALL", loaded.type)
        assertEquals(1791878400000L, loaded.raisedAtEpochMs)
        assertEquals(3.1390, loaded.lat!!, 0.0001)
        assertEquals(101.6869, loaded.lon!!, 0.0001)
        assertEquals(87, loaded.batteryPercent)
        assertEquals(AlertEntity.STATUS_PENDING, loaded.status)
        assertEquals(1, loaded.attempts)
    }

    @Test
    fun getPendingAlertsReturnsOnlyOutboxRowsOrderedByTimestamp() = runBlocking {
        val pending1 = AlertEntity(
            alertId = "id-1",
            type = "FALL",
            raisedAtEpochMs = 1000L,
            batteryPercent = 90,
            status = AlertEntity.STATUS_PENDING
        )
        val sentAlert = AlertEntity(
            alertId = "id-2",
            type = "SOS",
            raisedAtEpochMs = 1500L,
            batteryPercent = 89,
            status = AlertEntity.STATUS_SENT,
            sentAtEpochMs = 1550L
        )
        val pending2 = AlertEntity(
            alertId = "id-3",
            type = "MAN_DOWN",
            raisedAtEpochMs = 2000L,
            batteryPercent = 88,
            status = AlertEntity.STATUS_PENDING
        )

        dao.insertAlert(pending1)
        dao.insertAlert(sentAlert)
        dao.insertAlert(pending2)

        val pendingList = dao.getPendingAlerts()
        assertEquals(2, pendingList.size)
        assertEquals("id-1", pendingList[0].alertId)
        assertEquals("id-3", pendingList[1].alertId)
    }

    @Test
    fun updateAlertStatusTransitionsOutboxRecord() = runBlocking {
        val entity = AlertEntity(
            alertId = "test-update-id",
            type = "SOS",
            raisedAtEpochMs = 5000L,
            batteryPercent = 75,
            status = AlertEntity.STATUS_PENDING,
            attempts = 1
        )
        dao.insertAlert(entity)

        dao.updateAlertStatus(
            alertId = "test-update-id",
            status = AlertEntity.STATUS_SENT,
            attempts = 2,
            sentAtEpochMs = 6000L,
            ackAtEpochMs = null,
            ackBy = null
        )

        var loaded = dao.getAlert("test-update-id")
        assertEquals(AlertEntity.STATUS_SENT, loaded?.status)
        assertEquals(2, loaded?.attempts)
        assertEquals(6000L, loaded?.sentAtEpochMs)

        // Then acknowledge
        dao.updateAlertStatus(
            alertId = "test-update-id",
            status = AlertEntity.STATUS_ACKNOWLEDGED,
            attempts = 2,
            sentAtEpochMs = 6000L,
            ackAtEpochMs = 9000L,
            ackBy = "Dispatcher-Unit-4"
        )

        loaded = dao.getAlert("test-update-id")
        assertEquals(AlertEntity.STATUS_ACKNOWLEDGED, loaded?.status)
        assertEquals("Dispatcher-Unit-4", loaded?.acknowledgedBy)
        assertEquals(9000L, loaded?.acknowledgedAtEpochMs)
    }

    @Test
    fun getAllAlertsEmitsNewestFirst() = runBlocking {
        val alert1 = AlertEntity(
            alertId = "old",
            type = "FALL",
            raisedAtEpochMs = 1000L,
            batteryPercent = 80,
            status = AlertEntity.STATUS_SENT
        )
        val alert2 = AlertEntity(
            alertId = "new",
            type = "SOS",
            raisedAtEpochMs = 5000L,
            batteryPercent = 79,
            status = AlertEntity.STATUS_PENDING
        )

        dao.insertAlert(alert1)
        dao.insertAlert(alert2)

        val all = dao.getAllAlerts().first()
        assertEquals(2, all.size)
        assertEquals("new", all[0].alertId)
        assertEquals("old", all[1].alertId)
    }

    @Test
    fun deleteAlertAndClearAll() = runBlocking {
        val alert = AlertEntity(
            alertId = "to-delete",
            type = "FALL",
            raisedAtEpochMs = 1000L,
            batteryPercent = 80,
            status = AlertEntity.STATUS_SENT
        )
        dao.insertAlert(alert)
        assertNotNull(dao.getAlert("to-delete"))

        dao.deleteAlert("to-delete")
        assertNull(dao.getAlert("to-delete"))

        dao.insertAlert(alert.copy(alertId = "another"))
        dao.clearAll()
        assertTrue(dao.getAllAlerts().first().isEmpty())
    }
}
