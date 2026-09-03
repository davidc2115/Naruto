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
) : ViewModel() {

    private val _localModels = MutableStateFlow(modelManager.listLocalModels())
    private val _downloadProgress = MutableStateFlow<Float?>(null)
    private val _message = MutableStateFlow<String?>(null)
    private val _nanoAvailability = MutableStateFlow(NanoBridge.NanoAvailability.UNAVAILABLE)

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

    fun consumeMessage() {
        _message.value = null
    }
}
