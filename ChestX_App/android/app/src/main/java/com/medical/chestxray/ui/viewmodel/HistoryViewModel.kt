package com.medical.chestxray.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medical.chestxray.data.local.AppDatabase
import com.medical.chestxray.data.model.HistoryItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val isLoading: Boolean = false,
    val items: List<HistoryItem> = emptyList(),
    val error: String? = null
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()
    
    private val scanDao = AppDatabase.getDatabase(application).scanDao()
    private var collectJob: Job? = null
    
    init {
        loadHistory()
    }
    
    fun loadHistory() {
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            _uiState.value = HistoryUiState(isLoading = true)
            
            try {
                scanDao.getAllScans().collect { scans ->
                    val historyItems = scans.map { scan ->
                        HistoryItem(
                            id = scan.id,
                            timestamp = scan.timestamp,
                            prediction = scan.topPrediction,
                            confidence = scan.confidence,
                            thumbnail_url = scan.imageUri,
                            patientName = scan.patientName,
                            patientAge = scan.patientAge,
                            patientSex = scan.patientSex
                        )
                    }
                    _uiState.value = HistoryUiState(
                        isLoading = false,
                        items = historyItems
                    )
                }
            } catch (e: CancellationException) {
                // Expected when a newer loadHistory() call supersedes this one — not a real
                // error, so don't surface it (and let structured concurrency keep cancelling).
                throw e
            } catch (e: Exception) {
                _uiState.value = HistoryUiState(
                    isLoading = false,
                    error = e.localizedMessage ?: "Unknown Error"
                )
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                scanDao.deleteAllScans()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
