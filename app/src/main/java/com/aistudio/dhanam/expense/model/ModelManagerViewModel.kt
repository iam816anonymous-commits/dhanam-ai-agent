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
    val eligibilityByModel: Map<String, DeviceEligibility> = emptyMap(),
    val runtimeEligibilityByModel: Map<String, DeviceEligibility> = emptyMap(),
    val installedModelIds: Set<String> = emptySet(),
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isVerified: Boolean = false,
    val preferredModelId: String? = null,
    val selectedModelId: String = ModelCatalog.models.first().id,
    val message: String? = null
)

class ModelManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val downloader = ModelDownloader(application)
    private val preferences = application.getSharedPreferences("model_manager", 0)
    private val initialModelId = preferences.getString(KEY_PREFERRED, null)
        ?.takeIf { id -> ModelCatalog.models.any { it.id == id } } ?: ModelCatalog.models.first().id
    private val _state = MutableStateFlow(
        ModelManagerState(preferredModelId = preferences.getString(KEY_PREFERRED, null), selectedModelId = initialModelId)
    )
    private val model: ModelSpec
        get() = ModelCatalog.models.first { it.id == _state.value.selectedModelId }
    val state: StateFlow<ModelManagerState> = _state.asStateFlow()
    private var downloadJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) { ModelCatalog.removeLegacyArtifact(getApplication()) }
        refresh()
    }

    fun selectModel(modelId: String) {
        if (downloadJob?.isActive == true) return
        if (ModelCatalog.models.none { it.id == modelId }) return
        val selectedEligibility = _state.value.eligibilityByModel[modelId]
        _state.value = _state.value.copy(
            selectedModelId = modelId,
            eligibility = selectedEligibility,
            isVerified = false,
            progress = 0f,
            downloadedBytes = 0L,
            totalBytes = 0L,
            message = null
        )
        refresh()
    }

    /** Refresh every catalogue entry so users can see why each model is or is not runnable. */
    fun refresh() {
        val selectedId = _state.value.selectedModelId
        val selectedModel = ModelCatalog.models.first { it.id == selectedId }
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                val downloadChecks = ModelCatalog.models.associate { candidate ->
                    candidate.id to ModelCatalog.eligibility(getApplication(), candidate)
                }
                val runtimeChecks = ModelCatalog.models.associate { candidate ->
                    candidate.id to ModelCatalog.runtimeEligibility(getApplication(), candidate)
                }
                val installed = ModelCatalog.models.filter { candidate ->
                    val file = ModelCatalog.modelFile(getApplication(), candidate)
                    file.isFile && file.length() == candidate.expectedBytes
                }.map { it.id }.toSet()
                val selected = ModelCatalog.models.first { it.id == selectedId }
                val selectedFile = ModelCatalog.modelFile(getApplication(), selected)
                val selectedVerified = ModelFileVerifier.isValid(
                    selectedFile, selected.expectedBytes, selected.sha256
                )
                val partialBytes = ModelCatalog.partialFile(getApplication(), selected)
                    .takeIf { it.isFile }?.length()?.coerceAtMost(selected.expectedBytes) ?: 0L
                CatalogSnapshot(downloadChecks, runtimeChecks, installed, selectedVerified, partialBytes)
            }
            if (_state.value.selectedModelId != selectedId) return@launch
            val preferred = preferences.getString(KEY_PREFERRED, null)
            if (!snapshot.selectedVerified && preferred == selectedId) {
                preferences.edit().remove(KEY_PREFERRED).apply()
            }
            _state.value = _state.value.copy(
                eligibility = snapshot.downloadEligibility[selectedId],
                eligibilityByModel = snapshot.downloadEligibility,
                runtimeEligibilityByModel = snapshot.runtimeEligibility,
                installedModelIds = snapshot.installedModelIds,
                isVerified = snapshot.selectedVerified,
                preferredModelId = if (snapshot.selectedVerified) preferred else null,
                downloadedBytes = if (snapshot.selectedVerified) selectedModel.expectedBytes else snapshot.partialBytes,
                totalBytes = selectedModel.expectedBytes,
                progress = if (snapshot.selectedVerified) 1f else
                    (snapshot.partialBytes.toDouble() / selectedModel.expectedBytes.coerceAtLeast(1L)).toFloat().coerceIn(0f, 1f),
                message = when {
                    snapshot.selectedVerified -> "Selected model is installed and SHA-256 verified."
                    snapshot.partialBytes > 0L -> "Paused download found. ${snapshot.partialBytes} bytes are saved; tap Resume download to continue."
                    else -> _state.value.message
                }
            )
        }
    }

    fun download() {
        if (downloadJob?.isActive == true) return
        val selected = model
        downloadJob = viewModelScope.launch {
            try {
                val freshEligibility = withContext(Dispatchers.IO) {
                    ModelCatalog.eligibility(getApplication(), selected)
                }
                _state.value = _state.value.copy(
                    eligibility = freshEligibility,
                    eligibilityByModel = _state.value.eligibilityByModel + (selected.id to freshEligibility)
                )
                check(freshEligibility.allowed) {
                    freshEligibility.reasons.joinToString(" ").ifBlank {
                        "Download blocked because this device does not meet the resource checks."
                    }
                }
                val partialBytes = withContext(Dispatchers.IO) {
                    ModelCatalog.partialFile(getApplication(), selected)
                        .takeIf { it.isFile }?.length()?.coerceAtMost(selected.expectedBytes) ?: 0L
                }
                _state.value = _state.value.copy(
                    isDownloading = true,
                    progress = (partialBytes.toDouble() / selected.expectedBytes.coerceAtLeast(1L)).toFloat().coerceIn(0f, 1f),
                    downloadedBytes = partialBytes,
                    totalBytes = selected.expectedBytes,
                    message = if (partialBytes > 0L) "Resuming the saved download…" else "Downloading from the pinned HTTPS source…"
                )
                val file = downloader.download(selected) { received, total ->
                    _state.value = _state.value.copy(
                        downloadedBytes = received,
                        totalBytes = total,
                        progress = (received.toDouble() / total.coerceAtLeast(1L)).toFloat().coerceIn(0f, 1f)
                    )
                }
                val verified = withContext(Dispatchers.IO) {
                    ModelFileVerifier.isValid(file, selected.expectedBytes, selected.sha256)
                }
                check(verified) { "Final model verification failed." }
                _state.value = _state.value.copy(
                    isDownloading = false, progress = 1f, isVerified = true,
                    installedModelIds = _state.value.installedModelIds + selected.id,
                    message = "Download complete. File size and SHA-256 verified. Set it as preferred, then load it for local inference."
                )
                refresh()
            } catch (cancelled: CancellationException) {
                val savedBytes = _state.value.downloadedBytes
                _state.value = _state.value.copy(
                    isDownloading = false,
                    totalBytes = selected.expectedBytes,
                    progress = (savedBytes.toDouble() / selected.expectedBytes.coerceAtLeast(1L)).toFloat().coerceIn(0f, 1f),
                    message = if (savedBytes > 0L) "Download paused. Your progress is saved and will resume next time." else "Download paused."
                )
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
            val selected = model
            val file = ModelCatalog.modelFile(getApplication(), selected)
            val verified = withContext(Dispatchers.IO) {
                ModelFileVerifier.isValid(file, selected.expectedBytes, selected.sha256)
            }
            if (!verified) {
                _state.value = _state.value.copy(isVerified = false, message = "Model verification failed; it cannot be selected.")
                return@launch
            }
            preferences.edit().putString(KEY_PREFERRED, selected.id).apply()
            _state.value = _state.value.copy(
                preferredModelId = selected.id,
                isVerified = true,
                message = "Preferred model saved. Load it below to run a local inference test."
            )
        }
    }

    fun deleteModel() {
        if (downloadJob?.isActive == true) return
        viewModelScope.launch {
            val selected = model
            downloader.delete(selected)
            if (preferences.getString(KEY_PREFERRED, null) == selected.id) preferences.edit().remove(KEY_PREFERRED).apply()
            _state.value = _state.value.copy(
                isVerified = false,
                installedModelIds = _state.value.installedModelIds - selected.id,
                preferredModelId = preferences.getString(KEY_PREFERRED, null), progress = 0f,
                downloadedBytes = 0L, totalBytes = 0L, message = "Model file removed from app storage."
            )
            refresh()
        }
    }

    override fun onCleared() {
        downloadJob?.cancel()
        super.onCleared()
    }

    private data class CatalogSnapshot(
        val downloadEligibility: Map<String, DeviceEligibility>,
        val runtimeEligibility: Map<String, DeviceEligibility>,
        val installedModelIds: Set<String>,
        val selectedVerified: Boolean,
        val partialBytes: Long
    )

    private companion object { const val KEY_PREFERRED = "preferred_model_id" }
}
