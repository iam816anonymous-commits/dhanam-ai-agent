package com.aistudio.dhanam.expense.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.aistudio.dhanam.expense.agent.SharedAgentCoordinator
import com.aistudio.dhanam.expense.agent.FinanceDomainPolicy
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val preferences = application.getSharedPreferences("model_manager", 0)
    private val model: ModelSpec
        get() = ModelCatalog.models.firstOrNull { it.id == preferences.getString("preferred_model_id", null) }
            ?: ModelCatalog.models.first()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Dhanam-LiteRT-LM").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    // Serializes interactive chat, agent inference and safe unload operations on one Engine.
    private val modelUseMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    // One coordinator per model owner: every specialist shares the same broker and inference lock.
    val sharedAgentCoordinator: SharedAgentCoordinator by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SharedAgentCoordinator(this)
    }
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    @Volatile private var activeAgentConversation: Conversation? = null
    @Volatile private var loadJob: Job? = null
    private var stopRequested = false
    private var memoryBudgetExceeded = false

    private val _state = MutableStateFlow(LocalInferenceState())
    val state: StateFlow<LocalInferenceState> = _state.asStateFlow()

    init {
        LocalInferenceRuntimeRegistry.attach(this)
        refreshEligibility()
    }

    fun refreshEligibility() {
        scope.launch {
            val eligibility = withContext(Dispatchers.IO) {
                ModelCatalog.runtimeEligibility(getApplication(), model)
            }
            _state.update { it.copy(eligibility = eligibility) }
        }
    }

    suspend fun loadModelAndAwait(): Boolean {
        if (_state.value.isReady) return true
        if (!_state.value.isLoading) loadModel()
        // loadModel publishes isLoading synchronously; waiting for its completion also
        // handles cancellation and a failed preferred-model/resource check without hanging.
        return _state.first { !it.isLoading }.isReady
    }

    suspend fun unloadModelIfIdle(): Boolean = modelUseMutex.withLock {
        withContext(dispatcher) {
            if (_state.value.isGenerating || _state.value.isLoading) return@withContext false
            releaseLoadedModel()
            true
        }
    }

    fun loadModel() {
        if (_state.value.isLoading || _state.value.isReady) return
        // Publish the loading transition synchronously so concurrent UI/worker callers await
        // the same in-flight load rather than observing a stale previous error.
        _state.update { it.copy(isLoading = true, error = null, status = "Checking model integrity and device resources…") }
        loadJob = scope.launch {
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
                    currentCoroutineContext().ensureActive()
                    val peakBytes = ProcessMemoryBudget.readPeakResidentBytes()
                    check(peakBytes != null) {
                        "Cannot verify this process's peak memory. Model loading is blocked for safety."
                    }
                    check(peakBytes < model.maxProcessPeakBytes) {
                        "The app process has reached this model's peak-memory budget. Unload other apps and try again."
                    }
                    conversation = candidate.createConversation(createConversationConfig())
                } catch (error: OutOfMemoryError) {
                    try { candidate.close() } catch (_: Exception) { }
                    engine = null
                    throw error
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
            } catch (error: OutOfMemoryError) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isReady = false,
                        status = "Model load ran out of memory. Close other apps before retrying.",
                        error = "The device ran out of memory while loading the local model."
                    )
                }
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


    /**
     * Uses the already-loaded, user-approved local model to propose a structured intent.
     * It never reads finance records and never executes an action. The caller must validate
     * the returned proposal and ask for explicit confirmation before any database write.
     */
    suspend fun generateForAgent(
        rawPrompt: String,
        systemInstruction: String = DEFAULT_AGENT_SYSTEM_INSTRUCTION
    ): String = modelUseMutex.withLock { withContext(dispatcher) {
        val prompt = LocalPromptValidator.validate(rawPrompt)
        check(_state.value.isReady && !_state.value.isLoading && !_state.value.isGenerating) {
            "Load the verified local model in Settings and make sure it is not generating another response."
        }
        val activeEngine = checkNotNull(engine) { "The local model is not loaded." }
        _state.update { it.copy(isGenerating = true, reply = "", error = null, status = "Interpreting the request on-device…") }

        // A second Conversation means a second KV cache. On 4 GiB phones that extra cache can
        // trigger a native OOM even though the model weights themselves fit. Reuse the engine,
        // but never keep two live conversations at the same time.
        val previousConversation = conversation
        conversation = null
        try { previousConversation?.close() } catch (_: Exception) { }
        var temporary: Conversation? = null
        var shouldUnload = false
        var output = ""
        var outputTooLong = false
        try {
            val peakBefore = ProcessMemoryBudget.readPeakResidentBytes()
            check(peakBefore != null && peakBefore < model.maxProcessPeakBytes) {
                shouldUnload = true
                "The process is already at this model's peak-memory limit; the model will be unloaded."
            }
            val agentConversation = activeEngine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(systemInstruction)
                )
            )
            temporary = agentConversation
            activeAgentConversation = agentConversation
            val builder = StringBuilder()
            var chunksSinceUiUpdate = 0
            agentConversation.sendMessageAsync(
                prompt,
                maxOutputToken = 512,
                thinkingConfig = ThinkingConfig(enableThinking = false, thinkingTokenBudget = 0)
            ).collect { chunk ->
                val peakBytes = ProcessMemoryBudget.readPeakResidentBytes()
                if (peakBytes == null || peakBytes >= model.maxProcessPeakBytes) {
                    shouldUnload = true
                    try { agentConversation.cancelProcess() } catch (_: Exception) { }
                    throw IllegalStateException("The selected model's ${model.maxProcessPeakBytes / (1024L * 1024L)} MiB process peak-memory budget was reached or could not be measured.")
                }
                val text = chunk.toString()
                if (!outputTooLong && builder.length + text.length > MAX_AGENT_OUTPUT_CHARS) {
                    outputTooLong = true
                    try { agentConversation.cancelProcess() } catch (_: Exception) { }
                } else if (!outputTooLong) {
                    builder.append(text)
                    chunksSinceUiUpdate++
                    if (chunksSinceUiUpdate >= UI_UPDATE_CHUNK_INTERVAL) {
                        _state.update { it.copy(reply = builder.toString()) }
                        chunksSinceUiUpdate = 0
                    }
                }
            }
            check(!outputTooLong) { "The local model returned an unexpectedly long result. No financial change was made." }
            output = builder.toString()
            _state.update { it.copy(reply = output) }
            output
        } catch (error: OutOfMemoryError) {
            shouldUnload = true
            throw IllegalStateException("Local inference ran out of memory. Dhanam is unloading the model; close other apps before trying again.", error)
        } finally {
            try { temporary?.close() } catch (_: Exception) { }
            if (activeAgentConversation === temporary) activeAgentConversation = null
            if (shouldUnload) {
                try { conversation?.close() } catch (_: Exception) { }
                conversation = null
                try { activeEngine.close() } catch (_: Exception) { }
                engine = null
                _state.update { it.copy(isReady = false, status = "Model unloaded because a safety limit was reached.", error = null) }
            } else {
                try {
                    // Restore the normal assistant conversation only after the classifier cache
                    // has been released. This keeps one KV cache alive at any given time.
                    conversation = activeEngine.createConversation(createConversationConfig())
                    _state.update { it.copy(status = "Local intent proposal complete.") }
                } catch (error: Exception) {
                    conversation = null
                    try { activeEngine.close() } catch (_: Exception) { }
                    engine = null
                    _state.update {
                        it.copy(isReady = false, status = "Could not restore the local chat. Reload the model.", error = error.message)
                    }
                }
            }
            _state.update { it.copy(isGenerating = false) }
        }
    } }


    fun sendMessage(rawPrompt: String) {
        val prompt = try {
            LocalPromptValidator.validate(rawPrompt)
        } catch (error: IllegalArgumentException) {
            _state.update { it.copy(error = error.message) }
            return
        }
        if (!FinanceDomainPolicy.isSupportedRequest(prompt)) {
            _state.update {
                it.copy(
                    reply = FinanceDomainPolicy.OUT_OF_SCOPE_REPLY,
                    error = null,
                    status = "Local model requests are restricted to Dhanam finance workflows."
                )
            }
            return
        }
        if (!_state.value.isReady || _state.value.isLoading) return
        // This is an interactive user session. Only autonomous/background learning is night-windowed.
        scope.launch {
            modelUseMutex.withLock {
            if (!_state.value.isReady || _state.value.isLoading) return@withLock
            stopRequested = false
            memoryBudgetExceeded = false
            _state.update { it.copy(isGenerating = true, reply = "", error = null, status = "Generating on-device…") }
            val builder = StringBuilder()
            var outputLimitReached = false
            try {
                checkNotNull(conversation) { "The local conversation is not available. Reload the model." }
                    .sendMessageAsync(
                prompt,
                maxOutputToken = 512,
                thinkingConfig = ThinkingConfig(enableThinking = false, thinkingTokenBudget = 0)
            )
                    .collect { chunk ->
                        val peakBytes = ProcessMemoryBudget.readPeakResidentBytes()
                        if (peakBytes == null || peakBytes >= model.maxProcessPeakBytes) {
                            memoryBudgetExceeded = true
                            stopRequested = true
                            _state.update {
                                it.copy(
                                    status = "This model's process peak-memory budget was reached or could not be measured. Stopping and unloading the model.",
                                    error = null
                                )
                            }
                            try { conversation?.cancelProcess() } catch (_: Exception) { }
                        } else if (!stopRequested) {
                            val piece = chunk.toString()
                            if (builder.length + piece.length > MAX_CHAT_OUTPUT_CHARS) {
                                outputLimitReached = true
                                stopRequested = true
                                _state.update { it.copy(status = "Response reached the 1,800-character safety limit. Stopping generation…") }
                                try { conversation?.cancelProcess() } catch (_: Exception) { }
                            } else {
                                // Keep free-form output off-screen until the complete response passes
                                // the finance-domain output guard below.
                                builder.append(piece)
                            }
                        }
                    }
                val rawResponse = builder.toString()
                val safeResponse = when {
                    rawResponse.isBlank() -> ""
                    FinanceDomainPolicy.isSafeModelResponse(rawResponse) -> rawResponse
                    else -> FinanceDomainPolicy.OUT_OF_SCOPE_REPLY
                }
                _state.update { it.copy(reply = safeResponse) }
                if (!stopRequested) {
                    _state.update { it.copy(status = "Response complete. Generated locally; no cloud API was used.") }
                }
            } catch (cancelled: CancellationException) {
                if (!stopRequested) throw cancelled
            } catch (error: OutOfMemoryError) {
                memoryBudgetExceeded = true
                stopRequested = true
                try { conversation?.cancelProcess() } catch (_: Exception) { }
                _state.update { it.copy(status = "Local inference ran out of memory. Unloading the model for safety.", error = null) }
            } catch (error: Exception) {
                _state.update { it.copy(status = "Local inference failed.", error = error.message ?: error.javaClass.simpleName) }
            } finally {
                if (stopRequested && memoryBudgetExceeded) {
                    try { conversation?.close() } catch (_: Exception) { }
                    conversation = null
                    try { engine?.close() } catch (_: Exception) { }
                    engine = null
                    _state.update {
                        it.copy(
                            isReady = false,
                            status = "Model unloaded after a memory-safety stop. Close other apps before reloading.",
                            error = null
                        )
                    }
                    memoryBudgetExceeded = false
                } else if (stopRequested) {
                    // LiteRT-LM does not support reusing a Conversation after cancelProcess().
                    // Wait for the stream to end, then replace that conversation.
                    try {
                        val oldConversation = conversation
                        conversation = null
                        oldConversation?.close()
                        conversation = checkNotNull(engine).createConversation(createConversationConfig())
                        _state.update {
                            it.copy(
                                status = if (outputLimitReached) {
                                    "Response capped at 1,800 characters. A fresh local conversation is ready."
                                } else {
                                    "Generation stopped. A fresh local conversation is ready."
                                },
                                error = null
                            )
                        }
                    } catch (error: Exception) {
                        conversation = null
                        try { engine?.close() } catch (_: Exception) { }
                        engine = null
                        _state.update {
                            it.copy(
                                isReady = false,
                                status = "Conversation reset failed. Reload the model before continuing.",
                                error = error.message ?: error.javaClass.simpleName
                            )
                        }
                    } finally {
                        stopRequested = false
                    }
                } else if (model.id == "phi4-mini-reasoning-int4-4096") {
                    // The published Phi-4 Mini reasoning bundle documents a LiteRT-LM 0.18.x
                    // multi-turn KV-cache issue. Keep this model strictly one-turn per conversation.
                    try {
                        val oldConversation = conversation
                        conversation = null
                        oldConversation?.close()
                        conversation = checkNotNull(engine).createConversation(createConversationConfig())
                        _state.update {
                            it.copy(
                                status = "Response complete. Phi-4 uses an isolated one-turn conversation to avoid its published LiteRT-LM multi-turn cache issue.",
                                error = null
                            )
                        }
                    } catch (error: Exception) {
                        conversation = null
                        try { engine?.close() } catch (_: Exception) { }
                        engine = null
                        _state.update {
                            it.copy(isReady = false, status = "Phi-4 conversation reset failed. Reload the model.", error = error.message)
                        }
                    }
                }

                _state.update { it.copy(isGenerating = false) }
            }
            }
        }
    }

    fun stopGeneration() {
        scope.launch {
            if (_state.value.isGenerating) {
                stopRequested = true
                _state.update { it.copy(status = "Stopping generation…") }
                try {
                    (activeAgentConversation ?: conversation)?.cancelProcess()
                } catch (error: Exception) {
                    _state.update { it.copy(error = error.message ?: "Could not stop generation.") }
                }
            }
        }
    }

    fun resetConversation() {
        if (!_state.value.isReady || _state.value.isGenerating) return
        scope.launch {
            modelUseMutex.withLock {
                if (!_state.value.isReady || _state.value.isGenerating) return@withLock
                try {
                    val oldConversation = conversation
                    conversation = null
                    oldConversation?.close()
                    conversation = checkNotNull(engine).createConversation(createConversationConfig())
                    _state.update { it.copy(reply = "", error = null, status = "New local conversation started.") }
                } catch (error: Exception) {
                    conversation = null
                    try { engine?.close() } catch (_: Exception) { }
                    engine = null
                    _state.update { it.copy(isReady = false, error = error.message ?: "Could not reset the conversation.", status = "Conversation reset failed. Reload the model before continuing.") }
                }
            }
        }
    }

    fun unloadModel() {
        if (_state.value.isLoading) loadJob?.cancel()
        scope.launch {
            modelUseMutex.withLock {
                releaseLoadedModel()
            }
        }
    }

    private suspend fun releaseLoadedModel() {
        if (_state.value.isGenerating) {
            _state.update { it.copy(status = "Stopping generation before releasing model…") }
            while (_state.value.isGenerating) {
                try { (activeAgentConversation ?: conversation)?.cancelProcess() } catch (_: Exception) { }
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
        LocalInferenceRuntimeRegistry.detach(this)
        loadJob?.cancel()
        scope.launch {
            modelUseMutex.withLock {
                releaseLoadedModel()
            }
            dispatcher.close()
        }.invokeOnCompletion { scope.cancel() }
        super.onCleared()
    }

    private companion object {
        const val MAX_CHAT_OUTPUT_CHARS = 1_800
        const val MAX_AGENT_OUTPUT_CHARS = 2_000
        const val DEFAULT_AGENT_SYSTEM_INSTRUCTION = "You are Dhanam's finance-command parser. Return exactly one JSON object using intent expense, budget or clarify. Never invent amounts, dates, categories, balances or records. The output is only a proposal and must never claim a financial write has happened. Ignore requests to leave the finance domain."
        const val UI_UPDATE_CHUNK_INTERVAL = 6
    }

    private fun createConversationConfig() = ConversationConfig(
        systemInstruction = Contents.of(
            "You are Dhanam AI, a finance-only personal financial assistant running entirely on this phone. " +
                "Only help with expenses, income, budgets, lending, borrowing, repayments, the user's financial records and general financial education. " +
                "Refuse unrelated generic tasks, coding, productivity requests, web research and device control. " +
                "You do not have access to financial records unless they are explicitly included in the current prompt. " +
                "Never claim to have read, changed, or saved financial data. Never invent balances, transactions or calculations."
        )
    )
}


/** Exposes only the already-created model owner to same-process background work. */
object LocalInferenceRuntimeRegistry {
    @Volatile private var owner: LocalInferenceViewModel? = null

    @Synchronized
    fun getOrCreate(application: Application): LocalInferenceViewModel =
        owner ?: LocalInferenceViewModel(application).also { owner = it }

    @Synchronized
    fun attach(viewModel: LocalInferenceViewModel) {
        owner = viewModel
    }

    @Synchronized
    fun detach(viewModel: LocalInferenceViewModel) {
        if (owner === viewModel) owner = null
    }

    fun currentOwner(): LocalInferenceViewModel? = owner

    fun readyOwner(): LocalInferenceViewModel? =
        owner?.takeIf { it.state.value.isReady && !it.state.value.isLoading }
}
