package com.safesignal.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safesignal.app.SafeSignalApplication
import com.safesignal.app.data.AlertEntity
import com.safesignal.app.data.AlertRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class HistoryUiState(
    val alerts: List<AlertEntity> = emptyList(),
    val isLoading: Boolean = false
)

class HistoryViewModel(
    private val alertRepository: AlertRepository = SafeSignalApplication.instance.alertRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState(isLoading = true))
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            alertRepository.getAllAlerts().collectLatest { list ->
                _uiState.value = HistoryUiState(
                    alerts = list,
                    isLoading = false
                )
            }
        }
    }
}
