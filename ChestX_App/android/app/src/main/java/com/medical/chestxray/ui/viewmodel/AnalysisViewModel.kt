package com.medical.chestxray.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medical.chestxray.data.model.AnalysisResponse
import com.medical.chestxray.data.model.PatientInfo
import com.medical.chestxray.data.local.AppDatabase
import com.medical.chestxray.data.local.ScanEntity
import com.medical.chestxray.util.ModelRepository
import com.medical.chestxray.util.OnnxModelRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AnalysisUiState(
    val isLoading: Boolean = false,
    val analysisResult: AnalysisResponse? = null,
    val error: String? = null
)

class AnalysisViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()
    
    private var modelRunner: OnnxModelRunner? = null

    // Identifies the scan currently loaded/analyzed, so re-entering the screen
    // (e.g. returning from the Heatmap) does not re-run inference and save a duplicate.
    private var currentKey: String? = null

    private fun keyFor(imageUri: Uri, patientInfo: PatientInfo?): String =
        imageUri.toString() + "|" + (patientInfo?.name ?: "") +
                "|" + (patientInfo?.age ?: "") + "|" + (patientInfo?.sex ?: "")

    /** Shows an already-computed result (from History) without re-running inference or saving. */
    fun loadStoredResult(result: AnalysisResponse, imageUri: Uri, patientInfo: PatientInfo?) {
        currentKey = keyFor(imageUri, patientInfo)
        _uiState.value = AnalysisUiState(isLoading = false, analysisResult = result)
    }

    fun analyzeImage(imageUri: Uri, patientInfo: PatientInfo, context: Context) {
        // Skip if this exact scan is already being analyzed or already has a result.
        val key = keyFor(imageUri, patientInfo)
        val state = _uiState.value
        if (key == currentKey && (state.isLoading || state.analysisResult != null)) {
            return
        }
        currentKey = key

        viewModelScope.launch {
            _uiState.value = AnalysisUiState(isLoading = true)
            
            try {
                val result = withContext(Dispatchers.Default) {
                    // Rebuild the runner if no model is loaded yet, or the active model changed.
                    val currentSignature = ModelRepository.signature(context.applicationContext)
                    if (modelRunner == null || modelRunner!!.loadedSignature != currentSignature) {
                        modelRunner = OnnxModelRunner(context.applicationContext)
                    }
                    modelRunner!!.runInference(imageUri)
                }
                
                // Save to Room DB
                val scanDao = AppDatabase.getDatabase(context.applicationContext).scanDao()
                val scanEntity = ScanEntity(
                    id = result.id,
                    timestamp = result.timestamp,
                    patientName = patientInfo.name,
                    patientAge = patientInfo.age,
                    patientSex = patientInfo.sex,
                    topPrediction = result.top_prediction,
                    confidence = result.confidence,
                    predictions = result.predictions,
                    heatmapData = result.heatmap_data,
                    imageUri = imageUri.toString()
                )
                scanDao.insertScan(scanEntity)
                
                _uiState.value = AnalysisUiState(isLoading = false, analysisResult = result)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = AnalysisUiState(
                    isLoading = false,
                    error = e.localizedMessage ?: "Inference failed"
                )
            }
        }
    }
}
