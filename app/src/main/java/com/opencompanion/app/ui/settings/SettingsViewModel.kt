package com.opencompanion.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencompanion.app.data.EngineBackend
import com.opencompanion.app.data.EngineSettings
import com.opencompanion.app.data.SettingsRepository
import com.opencompanion.app.data.UserGender
import com.opencompanion.app.data.UserProfile
import com.opencompanion.app.engine.InferenceEngine
import com.opencompanion.app.engine.ModelManager
import com.opencompanion.app.engine.NanoBridge
import com.opencompanion.app.engine.RecommendedModels
import com.opencompanion.app.engine.CloudEngineBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: EngineSettings = EngineSettings(),
    val userProfile: UserProfile = UserProfile(),
    val localModels: List<ModelManager.LocalModel> = emptyList(),
    val downloadProgress: Float? = null,
    val message: String? = null,
    val vulkanCompiledIn: Boolean = false,
    val deviceReportsVulkan: Boolean = false,
    val nanoAvailability: NanoBridge.NanoAvailability = NanoBridge.NanoAvailability.UNAVAILABLE,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val modelManager: ModelManager,
    private val engine: InferenceEngine,
    private val nanoBridge: NanoBridge,
    private val cloudEngineBridge: CloudEngineBridge,
) : ViewModel() {

    private val _localModels = MutableStateFlow(modelManager.listLocalModels())
    private val _downloadProgress = MutableStateFlow<Float?>(null)
    private val _message = MutableStateFlow<String?>(null)
    private val _nanoAvailability = MutableStateFlow(NanoBridge.NanoAvailability.UNAVAILABLE)

    private val _groqModels = MutableStateFlow<List<String>>(
        listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "deepseek-r1-distill-llama-70b",
            "qwen-2.5-32b",
            "qwen-qwq-32b",
        )
    )
    val groqModels: StateFlow<List<String>> = _groqModels.asStateFlow()

    private val _geminiModels = MutableStateFlow<List<String>>(
        listOf(
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite-preview-02-05",
            "gemini-1.5-flash",
            "gemini-1.5-pro",
        )
    )
    val geminiModels: StateFlow<List<String>> = _geminiModels.asStateFlow()

    private val _openAiModels = MutableStateFlow<List<String>>(
        listOf(
            "gpt-4o-mini",
            "gpt-4o",
            "o3-mini",
            "gpt-4-turbo",
        )
    )
    val openAiModels: StateFlow<List<String>> = _openAiModels.asStateFlow()

    private val _openRouterModels = MutableStateFlow<List<String>>(
        listOf(
            "nousresearch/hermes-3-llama-3.1-8b:free",
            "meta-llama/llama-3.3-70b-instruct:free",
            "deepseek/deepseek-r1:free",
        )
    )
    val openRouterModels: StateFlow<List<String>> = _openRouterModels.asStateFlow()

    private val _isFetchingModels = MutableStateFlow(false)
    val isFetchingModels: StateFlow<Boolean> = _isFetchingModels.asStateFlow()

    init {
        refreshNanoAvailability()
    }

    val uiState: StateFlow<SettingsUiState> =
        kotlinx.coroutines.flow.combine(settingsRepository.settings, settingsRepository.userProfile) { settings, profile ->
            settings to profile
        }.let { settingsAndProfileFlow ->
            kotlinx.coroutines.flow.combine(
                settingsAndProfileFlow, _localModels, _downloadProgress, _message, _nanoAvailability,
            ) { (settings, profile), models, progress, message, nanoAvailability ->
                SettingsUiState(
                    settings = settings,
                    userProfile = profile,
                    localModels = models,
                    downloadProgress = progress,
                    message = message,
                    vulkanCompiledIn = engine.vulkanCompiledIn,
                    deviceReportsVulkan = engine.deviceReportsVulkanHardware(),
                    nanoAvailability = nanoAvailability,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun refreshNanoAvailability() {
        viewModelScope.launch { _nanoAvailability.value = nanoBridge.checkAvailability() }
    }

    /** Déclenche le téléchargement de Gemini Nano par AICore quand l'état est DOWNLOADABLE. */
    fun downloadNano() {
        viewModelScope.launch {
            nanoBridge.download().collect { event ->
                when (event) {
                    is NanoBridge.NanoDownloadEvent.Completed -> {
                        _message.value = "Gemini Nano est prêt sur cet appareil."
                        refreshNanoAvailability()
                    }
                    is NanoBridge.NanoDownloadEvent.Failed -> _message.value = event.message
                    else -> Unit
                }
            }
        }
    }

    fun setEnginePreference(value: EngineBackend) =
        viewModelScope.launch { settingsRepository.setEnginePreference(value) }

    /** Télécharge un modèle depuis la liste [RecommendedModels.ALL] (Réglages → Modèle). */
    fun downloadRecommendedModel(entry: RecommendedModels.Entry) {
        viewModelScope.launch {
            modelManager.importFromDirectUrl(entry.downloadUrl, entry.fileName).collect { progress ->
                when (progress) {
                    is ModelManager.ImportProgress.Downloading -> {
                        _downloadProgress.value = if (progress.totalBytes > 0) {
                            progress.bytesRead.toFloat() / progress.totalBytes
                        } else null
                    }
                    is ModelManager.ImportProgress.Done -> {
                        _downloadProgress.value = null
                        refreshLocalModels()
                        _message.value = "Modèle « ${progress.model.displayName} » téléchargé."
                        selectModel(progress.model.file.absolutePath)
                    }
                    is ModelManager.ImportProgress.Failed -> {
                        _downloadProgress.value = null
                        _message.value = "Échec du téléchargement : ${progress.message}"
                    }
                }
            }
        }
    }

    private fun refreshLocalModels() {
        _localModels.value = modelManager.listLocalModels()
    }

    fun importFromUri(uri: Uri, suggestedName: String) {
        viewModelScope.launch {
            modelManager.importFromContentUri(uri, suggestedName).fold(
                onSuccess = {
                    refreshLocalModels()
                    _message.value = "Modèle « ${it.displayName} » importé."
                    selectModel(it.file.absolutePath)
                },
                onFailure = { _message.value = "Import impossible : ${it.message}" },
            )
        }
    }

    fun importFromUrl(url: String) {
        viewModelScope.launch {
            modelManager.importFromDirectUrl(url, url.substringAfterLast('/')).collect { progress ->
                when (progress) {
                    is ModelManager.ImportProgress.Downloading -> {
                        _downloadProgress.value = if (progress.totalBytes > 0) {
                            progress.bytesRead.toFloat() / progress.totalBytes
                        } else null
                    }
                    is ModelManager.ImportProgress.Done -> {
                        _downloadProgress.value = null
                        refreshLocalModels()
                        _message.value = "Modèle « ${progress.model.displayName} » téléchargé."
                        selectModel(progress.model.file.absolutePath)
                    }
                    is ModelManager.ImportProgress.Failed -> {
                        _downloadProgress.value = null
                        _message.value = "Échec du téléchargement : ${progress.message}"
                    }
                }
            }
        }
    }

    fun selectModel(path: String) {
        viewModelScope.launch {
            settingsRepository.setSelectedModelPath(path)
            engine.unload() // le prochain message rechargera avec les nouveaux réglages
        }
    }

    fun deleteModel(model: ModelManager.LocalModel) {
        viewModelScope.launch {
            val wasSelected = uiState.value.settings.selectedModelPath == model.file.absolutePath
            modelManager.delete(model)
            refreshLocalModels()
            if (wasSelected) {
                settingsRepository.setSelectedModelPath(null)
                engine.unload()
            }
        }
    }

    fun setUseGpu(enabled: Boolean) = viewModelScope.launch { settingsRepository.setUseGpu(enabled) }
    fun setGpuLayers(value: Int) = viewModelScope.launch { settingsRepository.setGpuLayers(value) }
    fun setContextSize(size: Int) = viewModelScope.launch { settingsRepository.setContextSize(size) }
    fun setMaxResponseTokens(tokens: Int) = viewModelScope.launch { settingsRepository.setMaxResponseTokens(tokens) }
    fun setTemperature(value: Float) = viewModelScope.launch { settingsRepository.setTemperature(value) }
    fun setTopK(value: Int) = viewModelScope.launch { settingsRepository.setTopK(value) }
    fun setTopP(value: Float) = viewModelScope.launch { settingsRepository.setTopP(value) }
    fun setRepeatPenalty(value: Float) = viewModelScope.launch { settingsRepository.setRepeatPenalty(value) }
    fun setThreads(value: Int) = viewModelScope.launch { settingsRepository.setThreads(value) }

    fun setUserName(value: String) = viewModelScope.launch { settingsRepository.setUserName(value) }
    fun setUserAge(value: Int?) = viewModelScope.launch { settingsRepository.setUserAge(value) }
    fun setUserGender(value: UserGender) = viewModelScope.launch { settingsRepository.setUserGender(value) }

    fun setCloudApiKey(key: String) = viewModelScope.launch { settingsRepository.setCloudApiKey(key) }
    fun setCloudModelName(model: String) = viewModelScope.launch { settingsRepository.setCloudModelName(model) }
    fun setCloudEndpointUrl(url: String) = viewModelScope.launch { settingsRepository.setCloudEndpointUrl(url) }

    fun setGroqApiKey(key: String) = viewModelScope.launch { settingsRepository.setGroqApiKey(key) }
    fun setGroqModelName(model: String) = viewModelScope.launch { settingsRepository.setGroqModelName(model) }
    fun setGeminiApiKey(key: String) = viewModelScope.launch { settingsRepository.setGeminiApiKey(key) }
    fun setGeminiModelName(model: String) = viewModelScope.launch { settingsRepository.setGeminiModelName(model) }
    fun setOpenAiApiKey(key: String) = viewModelScope.launch { settingsRepository.setOpenAiApiKey(key) }
    fun setOpenAiModelName(model: String) = viewModelScope.launch { settingsRepository.setOpenAiModelName(model) }
    fun setAllowNsfwMode(enabled: Boolean) = viewModelScope.launch { settingsRepository.setAllowNsfwMode(enabled) }

    fun refreshGroqModels() {
        viewModelScope.launch {
            val key = uiState.value.settings.groqApiKey
            if (key.isBlank()) {
                _message.value = "Entre ta clé API Groq d'abord pour lister les modèles."
                return@launch
            }
            _isFetchingModels.value = true
            cloudEngineBridge.fetchGroqModels(key).fold(
                onSuccess = { list ->
                    _groqModels.value = list
                    _message.value = "${list.size} modèles Groq actifs récupérés."
                },
                onFailure = { err ->
                    _message.value = "Erreur chargement modèles Groq : ${err.message}"
                }
            )
            _isFetchingModels.value = false
        }
    }

    fun refreshGeminiModels() {
        viewModelScope.launch {
            val key = uiState.value.settings.geminiApiKey
            if (key.isBlank()) {
                _message.value = "Entre ta clé API Gemini d'abord pour lister les modèles."
                return@launch
            }
            _isFetchingModels.value = true
            cloudEngineBridge.fetchGeminiModels(key).fold(
                onSuccess = { list ->
                    _geminiModels.value = list
                    _message.value = "${list.size} modèles Gemini récupérés."
                },
                onFailure = { err ->
                    _message.value = "Erreur chargement modèles Gemini : ${err.message}"
                }
            )
            _isFetchingModels.value = false
        }
    }

    fun refreshOpenAiModels() {
        viewModelScope.launch {
            val key = uiState.value.settings.openAiApiKey
            if (key.isBlank()) {
                _message.value = "Entre ta clé API OpenAI d'abord pour lister les modèles."
                return@launch
            }
            _isFetchingModels.value = true
            cloudEngineBridge.fetchOpenAiModels(key).fold(
                onSuccess = { list ->
                    _openAiModels.value = list
                    _message.value = "${list.size} modèles OpenAI récupérés."
                },
                onFailure = { err ->
                    _message.value = "Erreur chargement modèles OpenAI : ${err.message}"
                }
            )
            _isFetchingModels.value = false
        }
    }

    fun refreshOpenRouterModels() {
        viewModelScope.launch {
            _isFetchingModels.value = true
            cloudEngineBridge.fetchOpenRouterModels().fold(
                onSuccess = { list ->
                    _openRouterModels.value = list
                    _message.value = "${list.size} modèles OpenRouter récupérés."
                },
                onFailure = { err ->
                    _message.value = "Erreur chargement modèles OpenRouter : ${err.message}"
                }
            )
            _isFetchingModels.value = false
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
