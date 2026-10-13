package com.safesignal.app.domain

import com.safesignal.app.data.AlertEntity
import com.safesignal.app.data.AlertRepository
import com.safesignal.app.data.AlertSyncer
import com.safesignal.app.data.FakeAlertDao
import com.safesignal.app.data.InMemoryKpiLogger
import com.safesignal.app.source.FakeBatterySource
import com.safesignal.app.source.FakeLocationSource
import com.safesignal.protocol.AlertApi
import com.safesignal.protocol.AlertMessage
import com.safesignal.protocol.AlertRecord
import com.safesignal.protocol.AlertStateMachine
import com.safesignal.protocol.AlertType
import com.safesignal.protocol.RecordStatus
import com.safesignal.protocol.RetryPolicy
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlertControllerTest {

    private lateinit var dao: FakeAlertDao
    private lateinit var repository: AlertRepository
    private lateinit var locationSource: FakeLocationSource
    private lateinit var batterySource: FakeBatterySource
    private lateinit var kpiLogger: InMemoryKpiLogger
    private lateinit var syncer: AlertSyncer
    private lateinit var controller: AlertController

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private var simulatedTime = 1700000000000L

    @Before
    fun setUp() {
        dao = FakeAlertDao()
        repository = AlertRepository(dao, clock = { simulatedTime })
        locationSource = FakeLocationSource()
        batterySource = FakeBatterySource(85)
        kpiLogger = InMemoryKpiLogger(clock = { simulatedTime })

        val mockEngine = MockEngine { request ->
            val record = AlertRecord(
                alert = AlertMessage(
                    alertId = "test-alert",
                    workerId = "W-1042",
                    deviceId = "EMULATOR34X1X2X0",
                    type = AlertType.FALL,
                    raisedAtEpochMs = simulatedTime,
                    batteryPercent = 85
                ),
                status = RecordStatus.RECEIVED,
                receivedAtEpochMs = simulatedTime
            )
            respond(
                content = AlertApi.defaultJson.encodeToString(record),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val alertApi = AlertApi.create(mockEngine, baseUrl = "http://test")
        syncer = AlertSyncer(
            repository = repository,
            alertApi = alertApi,
            kpiLogger = kpiLogger,
            retryPolicy = RetryPolicy(maxAttempts = 3),
            delayProvider = { /* immediate in unit test */ }
        )

        controller = AlertController(
            alertRepository = repository,
            alertSyncer = syncer,
            locationSource = locationSource,
            batterySource = batterySource,
            kpiLogger = kpiLogger,
            countdownSeconds = 5,
            scope = testScope
        )
    }

    @Test
    fun `StartShift transitions state from Idle to Monitoring`() = testScope.runTest {
        assertEquals(AlertStateMachine.State.Idle, controller.state.value)

        controller.processEvent(AlertStateMachine.Event.StartShift)
        assertEquals(AlertStateMachine.State.Monitoring, controller.state.value)
    }

    @Test
    fun `Detected fall starts countdown and expires into Sending`() = testScope.runTest {
        controller.processEvent(AlertStateMachine.Event.StartShift)

        // Trigger fall
        controller.processEvent(AlertStateMachine.Event.Detected(AlertType.FALL))
        assertTrue(controller.state.value is AlertStateMachine.State.Countdown)
        val countdown = controller.state.value as AlertStateMachine.State.Countdown
        assertEquals(AlertType.FALL, countdown.type)
        assertEquals(5, countdown.secondsLeft)

        // Advance through ticks
        testScheduler.advanceTimeBy(1000L)
        testScheduler.runCurrent()
        assertEquals(4, (controller.state.value as? AlertStateMachine.State.Countdown)?.secondsLeft)

        testScheduler.advanceTimeBy(4100L)
        testScheduler.runCurrent()

        // Countdown expired -> Sending
        assertTrue(controller.state.value is AlertStateMachine.State.Sending)
        val sending = controller.state.value as AlertStateMachine.State.Sending
        assertEquals(AlertType.FALL, sending.type)

        // Verify outbox persisted in Room
        val pending = repository.getPendingAlerts()
        assertEquals(1, pending.size)
        assertEquals(AlertEntity.STATUS_PENDING, pending[0].status)
        assertEquals(85, pending[0].batteryPercent)
    }

    @Test
    fun `CancelPressed stops countdown and returns to Monitoring`() = testScope.runTest {
        controller.processEvent(AlertStateMachine.Event.StartShift)
        controller.processEvent(AlertStateMachine.Event.Detected(AlertType.FALL))

        testScheduler.advanceTimeBy(2000L)
        testScheduler.runCurrent()
        assertTrue(controller.state.value is AlertStateMachine.State.Countdown)

        controller.processEvent(AlertStateMachine.Event.CancelPressed)
        assertEquals(AlertStateMachine.State.Monitoring, controller.state.value)

        // Advance time and verify no alert is raised
        testScheduler.advanceTimeBy(10000L)
        testScheduler.runCurrent()
        assertEquals(AlertStateMachine.State.Monitoring, controller.state.value)
        assertTrue(repository.getPendingAlerts().isEmpty())
    }

    @Test
    fun `SosPressed immediately enters Sending and bypasses countdown`() = testScope.runTest {
        controller.processEvent(AlertStateMachine.Event.StartShift)
        controller.processEvent(AlertStateMachine.Event.SosPressed("sos-immediate-1"))

        assertTrue(controller.state.value is AlertStateMachine.State.Sending)
        val sending = controller.state.value as AlertStateMachine.State.Sending
        assertEquals("sos-immediate-1", sending.alertId)
        assertEquals(AlertType.SOS, sending.type)

        testScheduler.runCurrent()
        val alert = repository.getAlert("sos-immediate-1")
        assertEquals(AlertEntity.STATUS_PENDING, alert?.status)
    }
}
