package com.aistudio.dhanam.expense.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LocalInferenceState(
    val eligibility: DeviceEligibility? = null,
    val isLoading: Boolean = false,
    val isReady: Boolean = false,
    val isGenerating: Boolean = false,
    val reply: String = "",
    val status: String = "Local inference is not loaded.",
    val error: String? = null
)

/**
 * Owns LiteRT-LM on one dedicated thread. Prompts and replies remain in memory only;
 * this ViewModel does not read financial records, persist prompts, or use network APIs.
 */
class LocalInferenceViewModel(application: Application) : AndroidViewModel(application) {
    private val model = ModelCatalog.models.first()
    private val preferences = application.getSharedPreferences("model_manager", 0)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Dhanam-LiteRT-LM").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    private val _state = MutableStateFlow(LocalInferenceState())
    val state: StateFlow<LocalInferenceState> = _state.asStateFlow()

    init { refreshEligibility() }

    fun refreshEligibility() {
        scope.launch {
            val eligibility = withContext(Dispatchers.IO) {
                ModelCatalog.runtimeEligibility(getApplication(), model)
            }
            _state.update { it.copy(eligibility = eligibility) }
        }
    }

    fun loadModel() {
        if (_state.value.isLoading || _state.value.isReady) return
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null, status = "Checking model integrity and device resources…") }
            try {
                check(preferences.getString("preferred_model_id", null) == model.id) {
                    "Set this verified model as preferred before loading it."
                }
                val file = ModelCatalog.modelFile(getApplication(), model)
                val eligibility = withContext(Dispatchers.IO) {
                    ModelCatalog.runtimeEligibility(getApplication(), model)
                }
                _state.update { it.copy(eligibility = eligibility) }
                check(eligibility.allowed) { eligibility.reasons.joinToString(" ") }
                check(withContext(Dispatchers.IO) {
                    ModelFileVerifier.isValid(file, model.expectedBytes, model.sha256)
                }) { "Model file failed size or SHA-256 verification. Remove it and download again." }

                _state.update { it.copy(status = "Loading model on CPU. The first load can take several seconds…") }
                val candidate = Engine(
                    EngineConfig(
                        modelPath = file.absolutePath,
                        backend = Backend.CPU(),
                        cacheDir = getApplication<Application>().cacheDir.absolutePath
                    )
                )
                // LiteRT-LM forbids closing an Engine whose initialize() call throws.
                candidate.initialize()
                engine = candidate
                try {
                    conversation = candidate.createConversation(createConversationConfig())
                } catch (error: Exception) {
                    candidate.close()
                    engine = null
                    throw error
                }
                _state.update {
                    it.copy(
                        isLoading = false,
                        isReady = true,
                        status = "Model loaded locally on CPU. Prompts are not saved by Dhanam.",
                        error = null
                    )
                }
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(isLoading = false, status = "Model loading cancelled.") }
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isReady = false,
                        status = "Model could not be loaded.",
                        error = error.message ?: error.javaClass.simpleName
                    )
                }
            }
        }
    }

    fun sendMessage(rawPrompt: String) {
        val prompt = try {
            LocalPromptValidator.validate(rawPrompt)
        } catch (error: IllegalArgumentException) {
            _state.update { it.copy(error = error.message) }
            return
        }
        if (!_state.value.isReady || _state.value.isLoading || _state.value.isGenerating) return
        scope.launch {
            if (!_state.value.isReady || _state.value.isGenerating) return@launch
            _state.update { it.copy(isGenerating = true, reply = "", error = null, status = "Generating on-device…") }
            try {
                checkNotNull(conversation) { "The local conversation is not available. Reload the model." }
                    .sendMessageAsync(prompt)
                    .collect { chunk ->
                        _state.update { it.copy(reply = it.reply + chunk.toString()) }
                    }
                _state.update { it.copy(status = "Response complete. Generated locally; no cloud API was used.") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(status = "Local inference failed.", error = error.message ?: error.javaClass.simpleName) }
            } finally {
                _state.update { it.copy(isGenerating = false) }
            }
        }
    }

    fun stopGeneration() {
        scope.launch {
            if (_state.value.isGenerating) {
                try {
                    conversation?.cancelProcess()
                } catch (error: Exception) {
                    _state.update { it.copy(error = error.message ?: "Could not stop generation.") }
                }
            }
        }
    }

    fun resetConversation() {
        if (!_state.value.isReady || _state.value.isGenerating) return
        scope.launch {
            try {
                conversation?.close()
                conversation = checkNotNull(engine).createConversation(createConversationConfig())
                _state.update { it.copy(reply = "", error = null, status = "New local conversation started.") }
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message ?: "Could not reset the conversation.") }
            }
        }
    }

    fun unloadModel() {
        scope.launch { releaseLoadedModel() }
    }

    private suspend fun releaseLoadedModel() {
        if (_state.value.isGenerating) {
            _state.update { it.copy(status = "Stopping generation before releasing model…") }
            while (_state.value.isGenerating) {
                try { conversation?.cancelProcess() } catch (_: Exception) { }
                delay(100)
            }
        }
        try {
            conversation?.close()
        } catch (error: Exception) {
            _state.update { it.copy(error = error.message ?: "Conversation cleanup failed.") }
        } finally {
            conversation = null
        }
        try {
            engine?.close()
        } catch (error: Exception) {
            _state.update { it.copy(error = error.message ?: "Model cleanup failed.") }
        } finally {
            engine = null
        }
        _state.update {
            it.copy(
                isLoading = false,
                isReady = false,
                isGenerating = false,
                reply = "",
                status = "Model unloaded and runtime resources released."
            )
        }
    }

    override fun onCleared() {
        scope.launch {
            releaseLoadedModel()
            dispatcher.close()
        }.invokeOnCompletion { scope.cancel() }
        super.onCleared()
    }

    private fun createConversationConfig() = ConversationConfig(
        systemInstruction = Contents.of(
            "You are Dhanam AI, a concise, helpful assistant running entirely on this phone. " +
                "You do not have access to the user's financial records unless they are explicitly included in the current prompt. " +
                "Never claim to have read, changed, or saved financial data. Do not invent balances or transactions."
        )
    )
}
