package com.safesignal.app.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.safesignal.app.MainActivity
import com.safesignal.app.SafeSignalApplication
import com.safesignal.protocol.AlertStateMachine
import com.safesignal.protocol.AlertType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafeSignalUiTest {

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS
    )

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        runBlocking {
            SafeSignalApplication.instance.database.alertDao().clearAll()
        }
        SafeSignalApplication.instance.alertController.reset()
        composeTestRule.waitForIdle()
    }

    @After
    fun tearDown() {
        SafeSignalApplication.instance.alertController.reset()
    }

    @Test
    fun initialState_showsWorkerIdAndOffDuty() {
        val workerId = SafeSignalApplication.instance.settingsRepository.workerId

        composeTestRule.onNodeWithText("Worker: $workerId").assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_status_title").assertIsDisplayed()
        composeTestRule.onNodeWithText("OFF DUTY").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_start_shift").assertIsDisplayed()
    }

    @Test
    fun startAndStopShift_transitionsUiState() {
        // Start Shift
        composeTestRule.onNodeWithTag("btn_start_shift").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("MONITORING ACTIVE").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_sos").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_stop_shift").assertIsDisplayed()

        // Stop Shift
        composeTestRule.onNodeWithTag("btn_stop_shift").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("OFF DUTY").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_start_shift").assertIsDisplayed()
    }

    @Test
    fun fallCountdown_showsCancelButton_andCancellingUpdatesHistory() {
        // Start Shift first
        composeTestRule.onNodeWithTag("btn_start_shift").performClick()
        composeTestRule.waitForIdle()

        // Inject Fall detection event
        SafeSignalApplication.instance.alertController.processEvent(
            AlertStateMachine.Event.Detected(AlertType.FALL)
        )
        composeTestRule.waitForIdle()

        // Verify Countdown UI
        composeTestRule.onNodeWithText("EMERGENCY COUNTDOWN").assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_countdown_seconds").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_cancel").assertIsDisplayed()

        // Cancel the alert
        composeTestRule.onNodeWithTag("btn_cancel").performClick()
        composeTestRule.waitForIdle()

        // Status should return to Monitoring Active
        composeTestRule.onNodeWithText("MONITORING ACTIVE").assertIsDisplayed()

        // Switch to History Tab
        composeTestRule.onNodeWithTag("tab_history").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("FALL").fetchSemanticsNodes().isNotEmpty()
        }

        // History should display the cancelled FALL event
        composeTestRule.onNodeWithText("FALL").assertIsDisplayed()
        composeTestRule.onNodeWithText("CANCELLED").assertIsDisplayed()
    }

    @Test
    fun emergencySos_createsAlertAndDisplaysInHistory() {
        // Start Shift
        composeTestRule.onNodeWithTag("btn_start_shift").performClick()
        composeTestRule.waitForIdle()

        // Press SOS
        composeTestRule.onNodeWithTag("btn_sos").performClick()
        composeTestRule.waitForIdle()

        // Switch to History Tab
        composeTestRule.onNodeWithTag("tab_history").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("SOS").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify SOS alert appears in History
        composeTestRule.onNodeWithText("SOS").assertIsDisplayed()
    }

    @Test
    fun historyTab_showsEmptyStateWhenNoAlerts() {
        composeTestRule.onNodeWithTag("tab_history").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("text_empty_history").assertIsDisplayed()
        composeTestRule.onNodeWithText("No alerts recorded yet.").assertIsDisplayed()
    }
}
