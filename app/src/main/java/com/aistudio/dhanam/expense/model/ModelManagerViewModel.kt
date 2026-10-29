package com.aistudio.dhanam.expense.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ModelManagerState(
    val eligibility: DeviceEligibility? = null,
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isVerified: Boolean = false,
    val preferredModelId: String? = null,
    val message: String? = null
)

class ModelManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val model = ModelCatalog.models.first()
    private val downloader = ModelDownloader(application)
    private val preferences = application.getSharedPreferences("model_manager", 0)
    private val _state = MutableStateFlow(ModelManagerState(preferredModelId = preferences.getString(KEY_PREFERRED, null)))
    val state: StateFlow<ModelManagerState> = _state.asStateFlow()
    private var downloadJob: Job? = null

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val eligibility = withContext(Dispatchers.IO) { ModelCatalog.eligibility(getApplication(), model) }
            val file = ModelCatalog.modelFile(getApplication(), model)
            val verified = withContext(Dispatchers.IO) {
                file.isFile && file.length() == model.expectedBytes &&
                    ModelFileVerifier.isValid(file, model.expectedBytes, model.sha256)
            }
            if (!verified && _state.value.preferredModelId == model.id) {
                preferences.edit().remove(KEY_PREFERRED).apply()
            }
            _state.value = _state.value.copy(
                eligibility = eligibility,
                isVerified = verified,
                preferredModelId = if (verified) preferences.getString(KEY_PREFERRED, null) else null,
                message = if (verified) "Model file is present and SHA-256 verified." else _state.value.message
            )
        }
    }

    fun download() {
        if (downloadJob?.isActive == true) return
        val eligibility = _state.value.eligibility ?: return
        if (!eligibility.allowed) {
            _state.value = _state.value.copy(message = "Download blocked because this device does not meet the resource checks.")
            return
        }
        downloadJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                isDownloading = true, progress = 0f, downloadedBytes = 0L,
                totalBytes = model.expectedBytes, message = "Downloading from the pinned HTTPS source…"
            )
            try {
                val file = downloader.download(model) { received, total ->
                    _state.value = _state.value.copy(
                        downloadedBytes = received,
                        totalBytes = total,
                        progress = (received.toDouble() / total.coerceAtLeast(1L)).toFloat().coerceIn(0f, 1f)
                    )
                }
                val verified = withContext(Dispatchers.IO) {
                    ModelFileVerifier.isValid(file, model.expectedBytes, model.sha256)
                }
                check(verified) { "Final model verification failed." }
                _state.value = _state.value.copy(
                    isDownloading = false, progress = 1f, isVerified = true,
                    message = "Download complete. File size and SHA-256 verified; inference is not enabled yet."
                )
                refresh()
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(isDownloading = false, message = "Download cancelled. Partial data was discarded.")
                throw cancelled
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    isDownloading = false,
                    message = error.message ?: "Download failed. You can retry when the device is ready."
                )
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    fun setPreferred() {
        viewModelScope.launch {
            val file = ModelCatalog.modelFile(getApplication(), model)
            val verified = withContext(Dispatchers.IO) {
                ModelFileVerifier.isValid(file, model.expectedBytes, model.sha256)
            }
            if (!verified) {
                _state.value = _state.value.copy(isVerified = false, message = "Model verification failed; it cannot be selected.")
                return@launch
            }
            preferences.edit().putString(KEY_PREFERRED, model.id).apply()
            _state.value = _state.value.copy(
                preferredModelId = model.id,
                message = "Preferred model saved. Runtime activation awaits device validation."
            )
        }
    }

    fun deleteModel() {
        if (downloadJob?.isActive == true) return
        viewModelScope.launch {
            downloader.delete(model)
            preferences.edit().remove(KEY_PREFERRED).apply()
            _state.value = _state.value.copy(
                isVerified = false, preferredModelId = null, progress = 0f,
                downloadedBytes = 0L, totalBytes = 0L, message = "Model file removed from app storage."
            )
            refresh()
        }
    }

    override fun onCleared() {
        downloadJob?.cancel()
        super.onCleared()
    }

    private companion object { const val KEY_PREFERRED = "preferred_model_id" }
}
