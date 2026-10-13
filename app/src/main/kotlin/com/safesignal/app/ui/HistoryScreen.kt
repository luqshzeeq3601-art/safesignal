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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safesignal.app.data.AlertEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Alert History",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.alerts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("text_empty_history"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No alerts recorded yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("list_history"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.alerts, key = { it.alertId }) { alert ->
                    AlertHistoryCard(alert = alert)
                }
            }
        }
    }
}

@Composable
private fun AlertHistoryCard(alert: AlertEntity) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val formattedTime = dateFormat.format(Date(alert.raisedAtEpochMs))

    val statusBgColor = when (alert.status) {
        AlertEntity.STATUS_ACKNOWLEDGED -> Color(0xFF2E7D32)
        AlertEntity.STATUS_SENT -> Color(0xFF1565C0)
        AlertEntity.STATUS_PENDING -> Color(0xFFE65100)
        AlertEntity.STATUS_CANCELLED -> Color(0xFF616161)
        else -> Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("item_alert_${alert.alertId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = alert.type,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = if (alert.type == "SOS") Color(0xFFC62828) else MaterialTheme.colorScheme.onSurface
                )

                Box(
                    modifier = Modifier
                        .background(statusBgColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = alert.status,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Time: $formattedTime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val locationText = if (alert.lat != null && alert.lon != null) {
                "GPS: ${"%.4f".format(Locale.US, alert.lat)}, ${"%.4f".format(Locale.US, alert.lon)}"
            } else {
                "GPS: No fix"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = locationText,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Battery: ${alert.batteryPercent}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (alert.acknowledgedBy != null) {
                Text(
                    text = "Ack by: ${alert.acknowledgedBy}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
