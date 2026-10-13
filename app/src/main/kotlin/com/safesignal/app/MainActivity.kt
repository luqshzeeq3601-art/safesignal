package com.safesignal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safesignal.app.ui.HistoryScreen
import com.safesignal.app.ui.HistoryViewModel
import com.safesignal.app.ui.StatusScreen
import com.safesignal.app.ui.StatusViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SafeSignalApp()
                }
            }
        }
    }
}

@Composable
fun SafeSignalApp(
    statusViewModel: StatusViewModel = viewModel(),
    historyViewModel: HistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val statusUiState by statusViewModel.uiState.collectAsState()
    val historyUiState by historyViewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Text("🛡️")
                    },
                    label = { Text("Status") },
                    modifier = Modifier.testTag("tab_status")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Text("📜")
                    },
                    label = { Text("History") },
                    modifier = Modifier.testTag("tab_history")
                )
            }
        }
    ) { innerPadding ->
        if (selectedTab == 0) {
            StatusScreen(
                state = statusUiState,
                onStartShift = { statusViewModel.startShift(context) },
                onStopShift = { statusViewModel.stopShift(context) },
                onCancelCountdown = { statusViewModel.cancelCountdown() },
                onTriggerSos = { statusViewModel.triggerSos() },
                onReset = { statusViewModel.reset() },
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            HistoryScreen(
                state = historyUiState,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
