package com.opencompanion.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencompanion.app.data.CharacterEntity
import com.opencompanion.app.data.CharacterRepository
import com.opencompanion.app.data.ChatMessageEntity
import com.opencompanion.app.data.EngineBackend
import com.opencompanion.app.data.EngineSettings
import com.opencompanion.app.data.MessageRole
import com.opencompanion.app.data.SettingsRepository
import com.opencompanion.app.data.resolveCharacterPlaceholders
import com.opencompanion.app.engine.GenerationEvent
import com.opencompanion.app.engine.GenerationParams
import com.opencompanion.app.engine.InferenceEngine
import com.opencompanion.app.engine.NanoBridge
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

/**
 * Découpe une réponse en plusieurs messages distincts sur les sauts de ligne vides, pour simuler
 * une vraie rafale de textos plutôt qu'un unique pavé de texte (voir
 * PromptBuilder.CONCISENESS_DIRECTIVE, qui enseigne cette convention au modèle). Plafonné pour
 * éviter qu'une dérive du modèle ne fragmente une réponse en dizaines de bulles ; au-delà, on
 * regroupe le surplus dans le dernier message plutôt que de le perdre.
 */
private const val MAX_SPLIT_MESSAGES = 4

internal fun splitIntoBubbles(text: String): List<String> {
    val parts = text.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size <= MAX_SPLIT_MESSAGES) return parts.ifEmpty { listOf(text.trim()) }
    val head = parts.take(MAX_SPLIT_MESSAGES - 1)
    val tail = parts.drop(MAX_SPLIT_MESSAGES - 1).joinToString("\n\n")
    return head + tail
}

data class ChatUiState(
    val character: CharacterEntity? = null,
    val messages: List<ChatMessageEntity> = emptyList(),
    val streamingText: String = "",
    val status: EngineStatus = EngineStatus.IDLE,
    val statusMessage: String? = null,
    val usingGpu: Boolean = false,
    /** true si la réponse en cours (ou la dernière) a été générée par Gemini Nano (AICore)
     *  plutôt que par le moteur llama.cpp embarqué — voir [ChatViewModel.resolveActiveBackend]. */
    val usingNano: Boolean = false,
    val selectedModelName: String? = null,
)

class ChatViewModel(
    private val characterId: Long,
    private val repository: CharacterRepository,
    private val engine: InferenceEngine,
    private val nanoBridge: NanoBridge,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _streamingText = MutableStateFlow("")
    private val _status = MutableStateFlow(EngineStatus.IDLE)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _usingNano = MutableStateFlow(false)
    private var generationJob: Job? = null
    private var gpuRetryUsed = false

    init {
        viewModelScope.launch {
            val character = repository.getCharacter(characterId)
            if (character != null && character.firstMessage.isNotBlank() &&
                repository.getMessages(characterId).isEmpty()
            ) {
                val userName = settingsRepository.userProfile.first().displayName
                val firstMessage = resolveCharacterPlaceholders(character.firstMessage, character, userName)
                repository.appendMessage(characterId, MessageRole.ASSISTANT, firstMessage)
            }

            // Préchargement proactif du modèle local s'il est configuré pour que le nom et l'état
            // s'affichent immédiatement à l'ouverture du chat.
            val settings = settingsRepository.settings.first()
            val backend = resolveActiveBackend(settings.enginePreference)
            if (backend == EngineBackend.LLAMA_CPP && settings.selectedModelPath != null) {
                _usingNano.value = false
                loadModelIfNeeded(settings)
            } else if (backend == EngineBackend.AICORE) {
                _usingNano.value = true
            }
        }
    }

    private val _engineState = combine(_streamingText, _status, _usingNano) { streaming, status, usingNano ->
        Triple(streaming, status, usingNano)
    }

    val uiState: StateFlow<ChatUiState> = combine(
        repository.observeCharacter(characterId),
        repository.observeMessages(characterId),
        _engineState,
        settingsRepository.settings,
    ) { character, messages, (streaming, status, usingNano), settings ->
        val modelName = settings.selectedModelPath?.let { path ->
            java.io.File(path).name.removeSuffix(".gguf")
        }
        ChatUiState(
            character = character,
            messages = messages,
            streamingText = streaming,
            status = status,
            statusMessage = _statusMessage.value,
            usingGpu = engine.isUsingGpu,
            usingNano = usingNano,
            selectedModelName = modelName,
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
            val backend = resolveActiveBackend(settings.enginePreference)

            if (backend == EngineBackend.AICORE) {
                // Gemini Nano ne nécessite aucun fichier .gguf : contrairement au backend
                // llama.cpp ci-dessous, rien à charger, le service système fait le travail.
                _usingNano.value = true
                runNanoGeneration(
                    character = character,
                    settings = settings,
                    allowFallbackToLlama = settings.enginePreference == EngineBackend.AUTO,
                )
                return@launch
            }

            _usingNano.value = false
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

    /**
     * Détermine le moteur à utiliser pour ce message. En [EngineBackend.AUTO] (réglage par
     * défaut), on essaie Gemini Nano si AICore le rapporte disponible *maintenant* — pas
     * "téléchargeable" ni "en cours de téléchargement", ces deux états déclenchent le repli
     * immédiat sur llama.cpp plutôt que d'attendre. Voir docs/MODELES_ET_AICORE.md.
     */
    private suspend fun resolveActiveBackend(preference: EngineBackend): EngineBackend = when (preference) {
        EngineBackend.LLAMA_CPP -> EngineBackend.LLAMA_CPP
        EngineBackend.AICORE -> EngineBackend.AICORE
        EngineBackend.AUTO -> {
            if (nanoBridge.checkAvailability() == NanoBridge.NanoAvailability.AVAILABLE) {
                EngineBackend.AICORE
            } else {
                EngineBackend.LLAMA_CPP
            }
        }
    }

    /**
     * Génère une réponse via Gemini Nano (AICore). Si [allowFallbackToLlama] est vrai (mode
     * AUTO uniquement — un choix explicite de "Gemini Nano" par l'utilisateur ne bascule
     * jamais tout seul) et qu'un modèle llama.cpp est configuré, un échec ici relance
     * automatiquement la génération sur ce modèle local, exactement comme le repli GPU→CPU
     * de [runGeneration].
     */
    private suspend fun runNanoGeneration(
        character: CharacterEntity,
        settings: EngineSettings,
        allowFallbackToLlama: Boolean,
    ) {
        _status.value = EngineStatus.GENERATING
        _streamingText.value = ""

        val fullHistory = repository.getMessages(characterId)
        val lastUserMessage = fullHistory.lastOrNull { it.role == MessageRole.USER }?.content.orEmpty()
        val prompt = PromptBuilder.buildNanoPrompt(
            character = character,
            history = fullHistory.dropLast(1),
            newUserMessage = lastUserMessage,
            maxOutputTokens = settings.maxResponseTokens,
            userProfile = settingsRepository.userProfile.first(),
        )

        var nanoFailed = false
        var nanoErrorMessage: String? = null
        nanoBridge.generate(prompt).collectLatest { event ->
            when (event) {
                is GenerationEvent.Token -> _streamingText.value += event.text
                is GenerationEvent.Done -> {
                    val text = _streamingText.value
                    _streamingText.value = ""
                    _status.value = EngineStatus.IDLE
                    if (text.isNotBlank()) {
                        splitIntoBubbles(text).forEach {
                            repository.appendMessage(characterId, MessageRole.ASSISTANT, it)
                        }
                    }
                }
                is GenerationEvent.Error -> {
                    nanoFailed = true
                    nanoErrorMessage = event.message
                }
                is GenerationEvent.GpuFailure -> Unit // ne peut pas arriver pour ce backend
            }
        }

        if (nanoFailed) {
            _streamingText.value = ""
            if (allowFallbackToLlama && settings.selectedModelPath != null) {
                _usingNano.value = false
                _statusMessage.value = "Gemini Nano indisponible ($nanoErrorMessage) : bascule sur le modèle local."
                if (loadModelIfNeeded(settings)) {
                    runGeneration(character, settings, allowGpuRetry = true)
                }
            } else {
                _status.value = EngineStatus.LOAD_ERROR
                _statusMessage.value = nanoErrorMessage
                    ?: "Gemini Nano (AICore) est indisponible sur cet appareil."
            }
        }
    }

    private suspend fun loadModelIfNeeded(settings: EngineSettings): Boolean {
        _status.value = EngineStatus.LOADING_MODEL
        val threads = if (settings.threads > 0) settings.threads else engine.recommendedThreadCount()
        val result = engine.ensureModelLoaded(
            modelPath = settings.selectedModelPath!!,
            contextSize = settings.contextSize,
            useGpu = settings.useGpu,
            gpuLayers = settings.gpuLayers,
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
            userProfile = settingsRepository.userProfile.first(),
            allowNsfw = settings.allowNsfwMode,
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
                    if (text.isNotBlank()) {
                        splitIntoBubbles(text).forEach {
                            repository.appendMessage(characterId, MessageRole.ASSISTANT, it)
                        }
                    } else {
                        // Peut arriver si le modèle a épuisé tout son budget de tokens dans un
                        // bloc <think>...</think> (voir ThinkBlockFilter) sans jamais produire de
                        // réponse visible — sans ce message, l'appli semblait n'avoir rien fait.
                        _statusMessage.value = "Réponse vide : le modèle a peut-être épuisé son " +
                            "budget de tokens en réflexion interne. Réessaie, ou augmente la " +
                            "limite de tokens de réponse dans les réglages."
                    }
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
