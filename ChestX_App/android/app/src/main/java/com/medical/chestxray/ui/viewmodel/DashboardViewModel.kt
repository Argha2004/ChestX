package com.medical.chestxray.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medical.chestxray.data.local.AppDatabase
import com.medical.chestxray.data.model.HistoryItem
import com.medical.chestxray.data.model.ScanFrequency
import com.medical.chestxray.data.model.StatisticsResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _statistics = MutableStateFlow<StatisticsResponse?>(null)
    val statistics: StateFlow<StatisticsResponse?> = _statistics.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _recentHistory = mutableStateListOf<HistoryItem>()
    val recentHistory: List<HistoryItem> = _recentHistory
    
    private val scanDao = AppDatabase.getDatabase(application).scanDao()
    
    fun loadStatistics() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                // Fetch scans from local DB
                val scans = scanDao.getAllScans().first()
                
                if (scans.isEmpty()) {
                    _statistics.value = StatisticsResponse(
                        total_scans = 0,
                        average_confidence = 0f,
                        most_common_disease = "None",
                        scan_frequency = emptyList()
                    )
                    _recentHistory.clear()
                    return@launch
                }
                
                // Calculate stats
                val totalScans = scans.size
                val avgConfidence = scans.map { it.confidence }.average().toFloat()
                
                val mostCommonDisease = scans.groupBy { it.topPrediction }
                    .maxByOrNull { it.value.size }?.key ?: "None"
                
                // Group scans by date (last 30 days)
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val frequencies = mutableListOf<ScanFrequency>()
                
                // Create placeholders for the last 30 days
                val dateCounts = mutableMapOf<String, Int>()
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -29)
                for (i in 0 until 30) {
                    val dateString = dateFormat.format(cal.time)
                    dateCounts[dateString] = 0
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                
                // Aggregate real scans
                scans.forEach { scan ->
                    try {
                        val scanDateStr = scan.timestamp.take(10) // "YYYY-MM-DD"
                        if (dateCounts.containsKey(scanDateStr)) {
                            dateCounts[scanDateStr] = (dateCounts[scanDateStr] ?: 0) + 1
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                dateCounts.forEach { (date, count) ->
                    frequencies.add(ScanFrequency(date = date, count = count))
                }
                
                val stats = StatisticsResponse(
                    total_scans = totalScans,
                    average_confidence = avgConfidence,
                    most_common_disease = mostCommonDisease,
                    scan_frequency = frequencies
                )
                _statistics.value = stats
                
                // Update recent history
                val history = scans.take(5).map { scan ->
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
                _recentHistory.clear()
                _recentHistory.addAll(history)
                
            } catch (e: Exception) {
                e.printStackTrace()
                _statistics.value = null
                _recentHistory.clear()
                _error.value = e.localizedMessage ?: "Failed to calculate stats"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
