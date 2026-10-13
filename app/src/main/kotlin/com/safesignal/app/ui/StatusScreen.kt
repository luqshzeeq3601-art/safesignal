package com.safesignal.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safesignal.protocol.AlertStateMachine

@Composable
fun StatusScreen(
    state: StatusUiState,
    onStartShift: () -> Unit,
    onStopShift: () -> Unit,
    onCancelCountdown: () -> Unit,
    onTriggerSos: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WorkerInfoCard(
            workerId = state.workerId,
            deviceId = state.deviceId,
            batteryPercent = state.batteryPercent
        )

        StatusBannerCard(
            alertState = state.alertState,
            countdownSeconds = state.countdownSecondsLeft,
            countdownType = state.countdownType,
            acknowledgedBy = state.acknowledgedBy
        )

        when (val alertState = state.alertState) {
            is AlertStateMachine.State.Idle -> {
                Button(
                    onClick = onStartShift,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("btn_start_shift"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1B5E20)
                    )
                ) {
                    Text("START SHIFT", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            is AlertStateMachine.State.Monitoring -> {
                Button(
                    onClick = onTriggerSos,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .testTag("btn_sos"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFC62828)
                    )
                ) {
                    Text("EMERGENCY SOS", fontSize = 20.sp, fontWeight = FontWeight.Black)
                }

                OutlinedButton(
                    onClick = onStopShift,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_stop_shift"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("STOP SHIFT", fontSize = 16.sp)
                }
            }

            is AlertStateMachine.State.Countdown -> {
                Button(
                    onClick = onCancelCountdown,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .testTag("btn_cancel"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32)
                    )
                ) {
                    Text("I'M OK — CANCEL ALARM", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onTriggerSos,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("btn_sos"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFC62828)
                    )
                ) {
                    Text("SEND SOS NOW", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            is AlertStateMachine.State.Sending -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(48.dp)
                        .padding(top = 8.dp),
                    color = Color(0xFFD84315)
                )
            }

            is AlertStateMachine.State.Sent -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(48.dp)
                        .padding(top = 8.dp),
                    color = Color(0xFF1565C0)
                )
            }

            is AlertStateMachine.State.Acknowledged -> {
                Button(
                    onClick = onReset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("btn_reset"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00695C)
                    )
                ) {
                    Text("RESET BEACON", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun WorkerInfoCard(
    workerId: String,
    deviceId: String,
    batteryPercent: Int
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Worker: $workerId",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Device: $deviceId",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = if (batteryPercent > 20) Color(0xFF2E7D32) else Color(0xFFC62828),
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "$batteryPercent%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun StatusBannerCard(
    alertState: AlertStateMachine.State,
    countdownSeconds: Int,
    countdownType: String,
    acknowledgedBy: String?
) {
    val (bgColor, title, description) = when (alertState) {
        is AlertStateMachine.State.Idle -> Triple(
            Color(0xFF455A64),
            "OFF DUTY",
            "Monitoring inactive. Press Start Shift to begin fall and man-down monitoring."
        )

        is AlertStateMachine.State.Monitoring -> Triple(
            Color(0xFF2E7D32),
            "MONITORING ACTIVE",
            "Sensors active: continuous fall and man-down detection enabled."
        )

        is AlertStateMachine.State.Countdown -> Triple(
            Color(0xFFC62828),
            "EMERGENCY COUNTDOWN",
            "$countdownType detected! Dispatching distress alert in:"
        )

        is AlertStateMachine.State.Sending -> Triple(
            Color(0xFFE65100),
            "DISPATCHING ALERT",
            "Transmitting distress signal to central dispatcher..."
        )

        is AlertStateMachine.State.Sent -> Triple(
            Color(0xFF0277BD),
            "ALERT DELIVERED",
            "Alert received by dispatcher server. Awaiting operator acknowledgement..."
        )

        is AlertStateMachine.State.Acknowledged -> Triple(
            Color(0xFF00695C),
            "ALERT ACKNOWLEDGED",
            "Acknowledged by dispatcher: ${acknowledgedBy ?: "Dispatch Operator"}. Help dispatched!"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                modifier = Modifier.testTag("text_status_title"),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            if (alertState is AlertStateMachine.State.Countdown) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "${countdownSeconds}s",
                    modifier = Modifier.testTag("text_countdown_seconds"),
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
