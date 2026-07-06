package com.medical.chestxray.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medical.chestxray.util.ReleaseResult
import com.medical.chestxray.util.UpdateInfo
import com.medical.chestxray.util.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class UpdateStatus { IDLE, CHECKING, AVAILABLE, UP_TO_DATE, DOWNLOADING, READY, ERROR }

data class UpdateUiState(
    val status: UpdateStatus = UpdateStatus.IDLE,
    val info: UpdateInfo? = null,
    val progress: Int = 0,
    val downloadedFile: File? = null,
    val errorMessage: String? = null
)

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    /** Set once the user dismisses the prompt, so the automatic check won't nag again this session. */
    var dismissedThisSession = false
        private set

    private fun currentVersion(): String = try {
        val ctx = getApplication<Application>()
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0.0"
    } catch (e: Exception) {
        "1.0.0"
    }

    fun checkForUpdate() {
        val s = _uiState.value.status
        if (s == UpdateStatus.CHECKING || s == UpdateStatus.DOWNLOADING) return

        _uiState.value = UpdateUiState(status = UpdateStatus.CHECKING)
        viewModelScope.launch {
            val current = currentVersion()
            val result = withContext(Dispatchers.IO) { UpdateManager.fetchLatestRelease(current) }
            _uiState.value = when (result) {
                is ReleaseResult.Ok ->
                    if (UpdateManager.isNewer(result.info.latestVersion, current)) {
                        UpdateUiState(status = UpdateStatus.AVAILABLE, info = result.info)
                    } else {
                        UpdateUiState(status = UpdateStatus.UP_TO_DATE, info = result.info)
                    }
                ReleaseResult.NoReleases -> UpdateUiState(
                    status = UpdateStatus.ERROR,
                    errorMessage = "No published release was found. Make sure a GitHub Release exists for the configured repository (owner/repo) with ChestX.apk attached."
                )
                is ReleaseResult.Error -> UpdateUiState(
                    status = UpdateStatus.ERROR,
                    errorMessage = "Couldn't check for updates: ${result.reason}"
                )
            }
        }
    }

    fun startDownload() {
        val info = _uiState.value.info ?: return
        val url = info.apkUrl
        if (url.isNullOrBlank()) {
            // No APK attached to the release — fall back to the GitHub page.
            UpdateManager.openReleasePage(getApplication(), info.releasePageUrl)
            return
        }
        _uiState.value = _uiState.value.copy(status = UpdateStatus.DOWNLOADING, progress = 0)
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                UpdateManager.downloadApk(getApplication(), url) { p ->
                    _uiState.value = _uiState.value.copy(progress = p)
                }
            }
            _uiState.value = if (file != null) {
                _uiState.value.copy(status = UpdateStatus.READY, downloadedFile = file, progress = 100)
            } else {
                _uiState.value.copy(status = UpdateStatus.ERROR)
            }
        }
    }

    fun install() {
        val file = _uiState.value.downloadedFile ?: return
        UpdateManager.installApk(getApplication(), file)
    }

    fun dismiss() {
        dismissedThisSession = true
        _uiState.value = UpdateUiState(status = UpdateStatus.IDLE)
    }
}
