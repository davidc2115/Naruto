package com.opencompanion.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencompanion.app.data.CharacterEntity
import com.opencompanion.app.data.CharacterRepository
import com.opencompanion.app.data.ChatMessageEntity
import com.opencompanion.app.data.EngineSettings
import com.opencompanion.app.data.MessageRole
import com.opencompanion.app.data.SettingsRepository
import com.opencompanion.app.engine.GenerationEvent
import com.opencompanion.app.engine.GenerationParams
import com.opencompanion.app.engine.InferenceEngine
import com.opencompanion.app.prompt.PromptBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class EngineStatus { IDLE, LOADING_MODEL, GENERATING, NO_MODEL_CONFIGURED, LOAD_ERROR }

data class ChatUiState(
    val character: CharacterEntity? = null,
    val messages: List<ChatMessageEntity> = emptyList(),
    val streamingText: String = "",
    val status: EngineStatus = EngineStatus.IDLE,
    val statusMessage: String? = null,
    val usingGpu: Boolean = false,
)

class ChatViewModel(
    private val characterId: Long,
    private val repository: CharacterRepository,
    private val engine: InferenceEngine,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _streamingText = MutableStateFlow("")
    private val _status = MutableStateFlow(EngineStatus.IDLE)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private var generationJob: Job? = null
    private var gpuRetryUsed = false

    init {
        viewModelScope.launch {
            val character = repository.getCharacter(characterId)
            if (character != null && character.firstMessage.isNotBlank() &&
                repository.getMessages(characterId).isEmpty()
            ) {
                repository.appendMessage(characterId, MessageRole.ASSISTANT, character.firstMessage)
            }
        }
    }

    val uiState: StateFlow<ChatUiState> = combine(
        repository.observeCharacter(characterId),
        repository.observeMessages(characterId),
        _streamingText,
        _status,
    ) { character, messages, streaming, status ->
        ChatUiState(
            character = character,
            messages = messages,
            streamingText = streaming,
            status = status,
            statusMessage = _statusMessage.value,
            usingGpu = engine.isUsingGpu,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            val character = repository.getCharacter(characterId) ?: return@launch
            repository.appendMessage(characterId, MessageRole.USER, trimmed)

            val settings = settingsRepository.settings.first()
            if (settings.selectedModelPath == null) {
                _status.value = EngineStatus.NO_MODEL_CONFIGURED
                _statusMessage.value = "Choisis un modèle dans les réglages avant de discuter."
                return@launch
            }

            val loaded = loadModelIfNeeded(settings)
            if (!loaded) return@launch

            runGeneration(character, settings, allowGpuRetry = true)
        }
    }

    private suspend fun loadModelIfNeeded(settings: EngineSettings): Boolean {
        _status.value = EngineStatus.LOADING_MODEL
        val threads = if (settings.threads > 0) settings.threads else engine.recommendedThreadCount()
        val result = engine.ensureModelLoaded(
            modelPath = settings.selectedModelPath!!,
            contextSize = settings.contextSize,
            useGpu = settings.useGpu,
            threads = threads,
        )
        return if (result.isSuccess) {
            true
        } else {
            _status.value = EngineStatus.LOAD_ERROR
            _statusMessage.value = result.exceptionOrNull()?.message ?: "Échec du chargement du modèle"
            false
        }
    }

    private suspend fun runGeneration(character: CharacterEntity, settings: EngineSettings, allowGpuRetry: Boolean) {
        _status.value = EngineStatus.GENERATING
        _streamingText.value = ""

        // Le message utilisateur qui vient de déclencher cette génération est déjà en base
        // (voir sendMessage) : on le ressort de l'historique complet plutôt que de le passer
        // deux fois, et on fournit le reste comme contexte de conversation.
        val fullHistory = repository.getMessages(characterId)
        val lastUserMessage = fullHistory.lastOrNull { it.role == MessageRole.USER }?.content.orEmpty()
        val finalPrompt = PromptBuilder.buildPrompt(
            character = character,
            history = fullHistory.dropLast(1),
            newUserMessage = lastUserMessage,
            engine = engine,
            contextSize = settings.contextSize,
            reservedForResponse = settings.maxResponseTokens,
        )

        var gpuFailed = false
        engine.generate(
            GenerationParams(
                prompt = finalPrompt,
                maxTokens = settings.maxResponseTokens,
                temperature = settings.temperature,
                topK = settings.topK,
                topP = settings.topP,
                repeatPenalty = settings.repeatPenalty,
            )
        ).collectLatest { event ->
            when (event) {
                is GenerationEvent.Token -> _streamingText.value += event.text
                is GenerationEvent.Done -> {
                    val text = _streamingText.value
                    _streamingText.value = ""
                    _status.value = EngineStatus.IDLE
                    if (text.isNotBlank()) repository.appendMessage(characterId, MessageRole.ASSISTANT, text)
                }
                is GenerationEvent.Error -> {
                    _status.value = EngineStatus.LOAD_ERROR
                    _statusMessage.value = event.message
                    _streamingText.value = ""
                }
                is GenerationEvent.GpuFailure -> gpuFailed = true
            }
        }

        if (gpuFailed) {
            _streamingText.value = ""
            if (allowGpuRetry && !gpuRetryUsed) {
                gpuRetryUsed = true
                settingsRepository.markGpuUnstable()
                _statusMessage.value = "Le GPU (Vulkan) a rencontré un problème : nouvelle tentative en mode CPU."
                engine.unload()
                val cpuSettings = settingsRepository.settings.first()
                if (loadModelIfNeeded(cpuSettings)) {
                    runGeneration(character, cpuSettings, allowGpuRetry = false)
                }
            } else {
                _status.value = EngineStatus.LOAD_ERROR
                _statusMessage.value = "La génération a échoué même en mode CPU. Vérifie le fichier du modèle."
            }
        }
    }

    fun stopGeneration() {
        engine.requestStop()
        generationJob?.cancel()
        _status.value = EngineStatus.IDLE
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory(characterId) }
    }

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }
}
