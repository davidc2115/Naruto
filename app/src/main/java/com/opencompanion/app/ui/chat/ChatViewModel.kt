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
import com.opencompanion.app.data.UserGender
import com.opencompanion.app.data.UserPersonaEntity
import com.opencompanion.app.data.UserProfile
import com.opencompanion.app.data.resolveCharacterPlaceholders
import com.opencompanion.app.engine.CloudEngineBridge
import com.opencompanion.app.engine.GenerationEvent
import com.opencompanion.app.engine.GenerationParams
import com.opencompanion.app.engine.InferenceEngine
import com.opencompanion.app.engine.NanoBridge
import com.opencompanion.app.engine.DialogueMode
import com.opencompanion.app.engine.DialogueRouter
import com.opencompanion.app.engine.parseApiKeys
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

internal fun cleanSpeakerPrefix(text: String, characterName: String): String {
    var result = text.trim()
    val prefixes = listOf(
        "$characterName :",
        "$characterName:",
        "assistant :",
        "assistant:",
        "Assistant :",
        "Assistant:"
    )
    for (prefix in prefixes) {
        if (result.startsWith(prefix, ignoreCase = true)) {
            result = result.substring(prefix.length).trim()
            break
        }
    }
    return result
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
    val dialogueMode: DialogueMode = DialogueMode.AUTO_HYBRID,
    val activeEngineLabel: String? = null,
)

class ChatViewModel(
    private val characterId: Long,
    private val repository: CharacterRepository,
    private val engine: InferenceEngine,
    private val nanoBridge: NanoBridge,
    private val cloudBridge: CloudEngineBridge,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _streamingText = MutableStateFlow("")
    private val _status = MutableStateFlow(EngineStatus.IDLE)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _usingNano = MutableStateFlow(false)
    private val _dialogueMode = MutableStateFlow(DialogueMode.AUTO_HYBRID)
    private val _activeEngineLabel = MutableStateFlow<String?>(null)
    private var generationJob: Job? = null
    private var gpuRetryUsed = false

    fun setDialogueMode(mode: DialogueMode) {
        _dialogueMode.value = mode
    }

    fun cycleDialogueMode() {
        _dialogueMode.value = when (_dialogueMode.value) {
            DialogueMode.AUTO_HYBRID -> DialogueMode.FORCE_NSFW
            DialogueMode.FORCE_NSFW -> DialogueMode.FORCE_SFW
            DialogueMode.FORCE_SFW -> DialogueMode.AUTO_HYBRID
        }
    }

    init {
        viewModelScope.launch {
            val character = repository.getCharacter(characterId)
            if (character != null && character.firstMessage.isNotBlank() &&
                repository.getMessages(characterId).isEmpty()
            ) {
                val userName = resolveUserProfile(character).displayName
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

    private data class CombinedEngineState(
        val streaming: String,
        val status: EngineStatus,
        val usingNano: Boolean,
        val mode: DialogueMode,
        val label: String?,
    )

    private val _engineState = combine(
        _streamingText,
        _status,
        _usingNano,
        _dialogueMode,
        _activeEngineLabel,
    ) { streaming, status, usingNano, mode, label ->
        CombinedEngineState(streaming, status, usingNano, mode, label)
    }

    val uiState: StateFlow<ChatUiState> = combine(
        repository.observeCharacter(characterId),
        repository.observeMessages(characterId),
        _engineState,
        settingsRepository.settings,
    ) { character, messages, engineState, settings ->
        val modelName = when (settings.enginePreference) {
            EngineBackend.CLOUD_OPENROUTER, EngineBackend.CLOUD_KOBOLD_HORDE, EngineBackend.CLOUD_CUSTOM_OPENAI -> {
                "Cloud : ${settings.cloudModelName.substringAfterLast('/')}"
            }
            EngineBackend.CLOUD_GROQ -> "Groq : ${settings.groqModelName}"
            EngineBackend.CLOUD_GEMINI -> "Gemini : ${settings.geminiModelName}"
            else -> settings.selectedModelPath?.let { path ->
                java.io.File(path).name.removeSuffix(".gguf")
            }
        }
        ChatUiState(
            character = character,
            messages = messages,
            streamingText = engineState.streaming,
            status = engineState.status,
            statusMessage = _statusMessage.value,
            usingGpu = engine.isUsingGpu,
            usingNano = engineState.usingNano,
            selectedModelName = modelName,
            dialogueMode = engineState.mode,
            activeEngineLabel = engineState.label,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    /** Personas disponibles pour le sélecteur de la barre de chat (voir ChatScreen). */
    val personas: StateFlow<List<UserPersonaEntity>> = repository.observePersonas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Change le persona utilisateur actif pour CE personnage uniquement (voir
     *  CharacterRepository.setActivePersonaForCharacter) — [personaId] à null revient au
     *  persona par défaut plutôt qu'à un choix figé. */
    fun setActivePersona(personaId: Long?) {
        viewModelScope.launch { repository.setActivePersonaForCharacter(characterId, personaId) }
    }

    fun updateMemoryNotes(notes: String) {
        viewModelScope.launch { repository.updateMemoryNotes(characterId, notes) }
    }

    fun setAffectionLevel(level: Int) {
        viewModelScope.launch { repository.setAffectionLevel(characterId, level) }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            val character = repository.getCharacter(characterId) ?: return@launch
            repository.appendMessage(characterId, MessageRole.USER, trimmed)
            repository.incrementAffection(characterId)

            val settings = settingsRepository.settings.first()
            val recentHistory = repository.getMessages(characterId)
            val backend = resolveActiveBackend(
                preference = settings.enginePreference,
                userMessage = trimmed,
                recentHistory = recentHistory,
                hasLocalModel = !settings.selectedModelPath.isNullOrBlank(),
                allowNsfwPreference = settings.allowNsfwMode,
            )

            when (backend) {
                EngineBackend.AICORE -> {
                    _usingNano.value = true
                    _activeEngineLabel.value = "⚡ Gemini Nano (NPU • SFW)"
                    runNanoGeneration(
                        character = character,
                        settings = settings,
                        allowFallbackToLlama = settings.enginePreference == EngineBackend.AUTO,
                    )
                    return@launch
                }
                EngineBackend.CLOUD_FREE_NO_KEY, EngineBackend.CLOUD_OPENROUTER, EngineBackend.CLOUD_KOBOLD_HORDE,
                EngineBackend.CLOUD_CUSTOM_OPENAI, EngineBackend.CLOUD_GROQ, EngineBackend.CLOUD_GEMINI -> {
                    _usingNano.value = false
                    _activeEngineLabel.value = "☁️ ${settings.cloudModelName.substringAfterLast('/')}"
                    runCloudGeneration(character, settings, backend)
                    return@launch
                }
                else -> {
                    _usingNano.value = false
                    val modelBaseName = settings.selectedModelPath?.let { java.io.File(it).name.removeSuffix(".gguf") } ?: "IA Locale"
                    _activeEngineLabel.value = "🔓 $modelBaseName (Non censurée • NSFW)"
                    if (settings.selectedModelPath == null) {
                        _status.value = EngineStatus.NO_MODEL_CONFIGURED
                        _statusMessage.value = "Choisis un modèle dans les réglages avant de discuter."
                        return@launch
                    }
                    val loaded = loadModelIfNeeded(settings)
                    if (!loaded) return@launch
                    runGeneration(character, settings.copy(allowNsfwMode = true), allowGpuRetry = true)
                }
            }
        }
    }

    /**
     * Détermine le moteur à utiliser pour ce message avec relais intelligent SFW ↔ NSFW.
     * En mode [EngineBackend.AUTO], analyse si le dialogue relève du domaine adulte / NSFW :
     * si oui, l'IA locale non censurée prend le relais pour garantir des réponses débridées et
     * cohérentes ; sinon, Gemini Nano (NPU ⚡) répond avec une rapidité maximale.
     */
    private suspend fun resolveActiveBackend(
        preference: EngineBackend,
        userMessage: String = "",
        recentHistory: List<ChatMessageEntity> = emptyList(),
        hasLocalModel: Boolean = false,
        allowNsfwPreference: Boolean = false,
    ): EngineBackend = when (preference) {
        EngineBackend.LLAMA_CPP -> EngineBackend.LLAMA_CPP
        EngineBackend.AICORE -> EngineBackend.AICORE
        EngineBackend.CLOUD_FREE_NO_KEY -> EngineBackend.CLOUD_FREE_NO_KEY
        EngineBackend.CLOUD_OPENROUTER -> EngineBackend.CLOUD_OPENROUTER
        EngineBackend.CLOUD_KOBOLD_HORDE -> EngineBackend.CLOUD_KOBOLD_HORDE
        EngineBackend.CLOUD_CUSTOM_OPENAI -> EngineBackend.CLOUD_CUSTOM_OPENAI
        EngineBackend.CLOUD_GROQ -> EngineBackend.CLOUD_GROQ
        EngineBackend.CLOUD_GEMINI -> EngineBackend.CLOUD_GEMINI
        EngineBackend.AUTO -> {
            val nanoAvailable = nanoBridge.checkAvailability() == NanoBridge.NanoAvailability.AVAILABLE
            when (_dialogueMode.value) {
                DialogueMode.FORCE_NSFW -> {
                    if (hasLocalModel) EngineBackend.LLAMA_CPP else if (nanoAvailable) EngineBackend.AICORE else EngineBackend.LLAMA_CPP
                }
                DialogueMode.FORCE_SFW -> {
                    if (nanoAvailable) EngineBackend.AICORE else EngineBackend.LLAMA_CPP
                }
                DialogueMode.AUTO_HYBRID -> {
                    val isNsfw = allowNsfwPreference || DialogueRouter.isNsfw(userMessage, recentHistory)
                    if (isNsfw && hasLocalModel) {
                        // Bascule fluide et automatique sur l'IA locale non censurée
                        EngineBackend.LLAMA_CPP
                    } else if (nanoAvailable) {
                        // SFW : Gemini Nano NPU
                        EngineBackend.AICORE
                    } else {
                        EngineBackend.LLAMA_CPP
                    }
                }
            }
        }
    }

    /**
     * Résout le persona utilisateur actif pour [character] (voir
     * [CharacterRepository.resolveActivePersona] — choix explicite du personnage, sinon persona
     * par défaut) et le convertit au format [UserProfile] attendu par [PromptBuilder]. Retombe
     * sur l'ancien profil unique des réglages si aucun persona n'existe encore (ne devrait
     * normalement pas arriver, [characterRepository.ensureDefaultPersonaSeeded] en crée un au
     * premier lancement — filet de sécurité plutôt qu'un chemin attendu).
     */
    private suspend fun resolveUserProfile(character: CharacterEntity): UserProfile {
        val persona = repository.resolveActivePersona(character)
        if (persona != null) {
            return UserProfile(
                name = persona.name,
                age = persona.age,
                gender = runCatching { UserGender.valueOf(persona.gender) }.getOrDefault(UserGender.NON_PRECISE),
                description = persona.description,
            )
        }
        return settingsRepository.userProfile.first()
    }

    private suspend fun runCloudGeneration(
        character: CharacterEntity,
        settings: EngineSettings,
        backend: EngineBackend,
    ) {
        _status.value = EngineStatus.GENERATING
        _streamingText.value = ""

        val fullHistory = repository.getMessages(characterId)
        val lastUserMessage = fullHistory.lastOrNull { it.role == MessageRole.USER }?.content.orEmpty()
        val turns = PromptBuilder.buildTurns(
            character = character,
            history = fullHistory.dropLast(1),
            newUserMessage = lastUserMessage,
            engine = engine,
            contextSize = settings.contextSize,
            reservedForResponse = settings.maxResponseTokens,
            userProfile = resolveUserProfile(character),
            allowNsfw = settings.allowNsfwMode,
        )

        val flow = if (backend == EngineBackend.CLOUD_FREE_NO_KEY) {
            cloudBridge.generateFreeNoKeyCloud(
                turns = turns,
                maxTokens = settings.maxResponseTokens,
                temperature = settings.temperature,
            )
        } else if (backend == EngineBackend.CLOUD_KOBOLD_HORDE) {
            cloudBridge.generateKoboldHorde(
                apiKey = settings.cloudApiKey,
                modelName = settings.cloudModelName,
                turns = turns,
                maxTokens = settings.maxResponseTokens,
                temperature = settings.temperature,
            )
        } else if (backend == EngineBackend.CLOUD_GROQ) {
            // Groq expose une API compatible OpenAI : on réutilise le client générique plutôt
            // qu'une fonction dédiée, seule l'URL d'endpoint change. Plusieurs clés (une par
            // ligne dans les réglages) tournent automatiquement en cas de clé invalide/quota
            // atteint — voir CloudEngineBridge.generateWithKeyRotation.
            cloudBridge.generateWithKeyRotation(parseApiKeys(settings.groqApiKey)) { key ->
                cloudBridge.generateOpenAiCompatible(
                    endpointUrl = "https://api.groq.com/openai/v1/chat/completions",
                    apiKey = key,
                    modelName = settings.groqModelName,
                    turns = turns,
                    maxTokens = settings.maxResponseTokens,
                    temperature = settings.temperature,
                )
            }
        } else if (backend == EngineBackend.CLOUD_GEMINI) {
            cloudBridge.generateWithKeyRotation(parseApiKeys(settings.geminiApiKey)) { key ->
                cloudBridge.generateGemini(
                    apiKey = key,
                    modelName = settings.geminiModelName,
                    turns = turns,
                    maxTokens = settings.maxResponseTokens,
                    temperature = settings.temperature,
                )
            }
        } else {
            cloudBridge.generateWithKeyRotation(parseApiKeys(settings.cloudApiKey)) { key ->
                cloudBridge.generateOpenAiCompatible(
                    endpointUrl = settings.cloudEndpointUrl,
                    apiKey = key,
                    modelName = settings.cloudModelName,
                    turns = turns,
                    maxTokens = settings.maxResponseTokens,
                    temperature = settings.temperature,
                )
            }
        }

        flow.collectLatest { event ->
            when (event) {
                is GenerationEvent.Token -> _streamingText.value += event.text
                is GenerationEvent.Done -> {
                    val text = _streamingText.value
                    _streamingText.value = ""
                    _status.value = EngineStatus.IDLE
                    if (text.isNotBlank()) {
                        splitIntoBubbles(cleanSpeakerPrefix(text, character.name)).forEach {
                            repository.appendMessage(characterId, MessageRole.ASSISTANT, it)
                        }
                    } else {
                        _statusMessage.value = "Réponse vide reçue du serveur Cloud."
                    }
                }
                is GenerationEvent.Error -> {
                    _status.value = EngineStatus.LOAD_ERROR
                    _statusMessage.value = event.message
                    _streamingText.value = ""
                }
                is GenerationEvent.GpuFailure -> Unit
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
            userProfile = resolveUserProfile(character),
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
                    if (text.isNotBlank() && !DialogueRouter.isRefusalOrEmpty(text)) {
                        splitIntoBubbles(cleanSpeakerPrefix(text, character.name)).forEach {
                            repository.appendMessage(characterId, MessageRole.ASSISTANT, it)
                        }
                    } else {
                        // Refus AICore ou réponse vide -> bascule automatique sur l'IA locale non censurée
                        nanoFailed = true
                        nanoErrorMessage = "Contenu filtré par AICore (relais IA non censurée activé)"
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
                val localName = java.io.File(settings.selectedModelPath).name.removeSuffix(".gguf")
                _activeEngineLabel.value = "🔓 $localName (Relais non censuré • NSFW)"
                _statusMessage.value = "Relais IA Locale activé ($localName)."
                if (loadModelIfNeeded(settings)) {
                    runGeneration(character, settings.copy(allowNsfwMode = true), allowGpuRetry = true)
                }
            } else {
                _status.value = EngineStatus.LOAD_ERROR
                _statusMessage.value = nanoErrorMessage
                    ?: "Gemini Nano (AICore) est indisponible sur cet appareil."
            }
        }
    }

    private suspend fun loadModelIfNeeded(settings: EngineSettings): Boolean {
        val path = settings.selectedModelPath
        if (path.isNullOrBlank()) {
            _status.value = EngineStatus.NO_MODEL_CONFIGURED
            _statusMessage.value = "Choisis un modèle dans les réglages avant de discuter."
            return false
        }
        val file = java.io.File(path)
        if (!file.exists() || !file.isFile) {
            _status.value = EngineStatus.NO_MODEL_CONFIGURED
            _statusMessage.value = "Le fichier du modèle sélectionné est introuvable sur l'appareil."
            return false
        }

        _status.value = EngineStatus.LOADING_MODEL
        val threads = if (settings.threads > 0) settings.threads else engine.recommendedThreadCount()
        val result = engine.ensureModelLoaded(
            modelPath = path,
            contextSize = settings.contextSize,
            useGpu = settings.useGpu,
            gpuLayers = settings.gpuLayers,
            threads = threads,
        )
        if (result.isSuccess) {
            _status.value = EngineStatus.IDLE
            return true
        }

        // Même repli automatique CPU que pour un échec de GÉNÉRATION en GPU (voir runGeneration) :
        // sur certains appareils, le pilote Vulkan peut faire échouer — ou carrément bloquer,
        // voir InferenceEngine.ensureModelLoaded et docs/VULKAN_NOTES.md — le CHARGEMENT du
        // modèle, pas seulement la génération. Sans ce repli, un appareil au pilote GPU capricieux
        // restait bloqué sur "chargement du modèle" (ou en échec) sans aucun recours.
        if (settings.useGpu && !gpuRetryUsed) {
            gpuRetryUsed = true
            settingsRepository.markGpuUnstable()
            _statusMessage.value = "Le GPU (Vulkan) n'a pas répondu pendant le chargement : nouvelle tentative en mode CPU."
            return loadModelIfNeeded(settingsRepository.settings.first())
        }

        _status.value = EngineStatus.LOAD_ERROR
        _statusMessage.value = result.exceptionOrNull()?.message ?: "Échec du chargement du modèle"
        return false
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
            userProfile = resolveUserProfile(character),
            allowNsfw = settings.allowNsfwMode || _dialogueMode.value != DialogueMode.FORCE_SFW,
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
                        splitIntoBubbles(cleanSpeakerPrefix(text, character.name)).forEach {
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
