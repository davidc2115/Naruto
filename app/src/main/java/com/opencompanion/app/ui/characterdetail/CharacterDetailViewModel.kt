package com.opencompanion.app.ui.characterdetail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencompanion.app.data.CharacterEntity
import com.opencompanion.app.data.CharacterRepository
import com.opencompanion.app.data.ChatMessageEntity
import com.opencompanion.app.data.MessageRole
import com.opencompanion.app.data.SettingsRepository
import com.opencompanion.app.data.UserProfile
import com.opencompanion.app.data.resolveCharacterPlaceholders
import com.opencompanion.app.engine.CloudEngineBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class CharacterPhotoStyle(val title: String, val subtitle: String) {
    PORTRAIT("📸 Portrait fidèle (Visage & Buste)", "Conforme aux cheveux, yeux, teint et âge du personnage"),
    INTIMATE("💋 Photo Boudoir / Intime (NSFW)", "Pose sensuelle mettant en valeur sa morphologie et ses atours"),
    SCENARIO("🏢 Métier & Scène de vie", "Contexte professionnel ou quotidien lié à son histoire"),
    CUSTOM("✍️ Consigne libre / Personnalisée", "Décrivez précisément le décor, la tenue ou l'action"),
}

data class CharacterDetailUiState(
    val character: CharacterEntity? = null,
    val messages: List<ChatMessageEntity> = emptyList(),
    val userName: String = "Utilisateur",
    val isLoading: Boolean = true,
    val isGeneratingImage: Boolean = false,
    val generationStatus: String? = null,
    val generationError: String? = null,
    val lastGeneratedPath: String? = null,
) {
    val messageCount: Int get() = messages.size
    val hasConversation: Boolean get() = messages.isNotEmpty()
    val lastMessage: ChatMessageEntity? get() = messages.lastOrNull()
}

class CharacterDetailViewModel(
    private val characterId: Long,
    private val repository: CharacterRepository,
    private val settingsRepository: SettingsRepository,
    private val cloudBridge: CloudEngineBridge,
    private val context: Context,
) : ViewModel() {

    private val _isGenerating = MutableStateFlow(false)
    private val _generationStatus = MutableStateFlow<String?>(null)
    private val _generationError = MutableStateFlow<String?>(null)
    private val _lastGeneratedPath = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CharacterDetailUiState> = combine(
        repository.observeCharacter(characterId),
        repository.observeMessages(characterId),
        settingsRepository.userProfile,
        _isGenerating,
        _generationStatus,
        _generationError,
        _lastGeneratedPath,
    ) { params ->
        val character = params[0] as? CharacterEntity
        @Suppress("UNCHECKED_CAST")
        val messages = params[1] as? List<ChatMessageEntity> ?: emptyList()
        val userProfile = params[2] as? UserProfile ?: UserProfile()
        val isGenerating = params[3] as? Boolean ?: false
        val genStatus = params[4] as? String
        val genError = params[5] as? String
        val lastPath = params[6] as? String

        CharacterDetailUiState(
            character = character,
            messages = messages,
            userName = userProfile.displayName,
            isLoading = false,
            isGeneratingImage = isGenerating,
            generationStatus = genStatus,
            generationError = genError,
            lastGeneratedPath = lastPath,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CharacterDetailUiState())

    fun clearGenerationError() {
        _generationError.value = null
    }

    fun setAsAvatar(mediaPath: String) {
        viewModelScope.launch {
            repository.updateAvatar(characterId, mediaPath)
        }
    }

    /**
     * Génère une image IA pour le personnage via le moteur configuré (Horde Diffusion par défaut)
     * et l'enregistre dans la galerie et/ou en tant qu'avatar principal.
     */
    fun generateCharacterImage(
        style: CharacterPhotoStyle,
        customInstruction: String?,
        setAsAvatar: Boolean,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> },
    ) {
        viewModelScope.launch {
            val character = repository.getCharacter(characterId) ?: return@launch
            val settings = settingsRepository.settings.first()

            _isGenerating.value = true
            _generationStatus.value = "Préparation du prompt IA..."
            _generationError.value = null

            val geminiKey = settingsRepository.getActiveGeminiApiKey()
            val openAiKey = settingsRepository.getActiveOpenAiApiKey()
            val cloudKey = settingsRepository.getActiveCloudApiKey()
            val hordeKey = settingsRepository.getActiveHordeApiKey()

            val outputDir = File(context.filesDir, "character_photos").apply { mkdirs() }

            val instruction = when (style) {
                CharacterPhotoStyle.PORTRAIT -> "Close-up headshot portrait photo, face and bust, looking directly at camera, soft lighting" + (if (!customInstruction.isNullOrBlank()) ", $customInstruction" else "")
                CharacterPhotoStyle.INTIMATE -> "Sensual boudoir intimate photo, private bedroom setting, romantic dim moody lighting, silk nightwear" + (if (!customInstruction.isNullOrBlank()) ", $customInstruction" else "")
                CharacterPhotoStyle.SCENARIO -> "Authentic candid lifestyle photo matching her role and scenario, natural environment" + (if (!customInstruction.isNullOrBlank()) ", $customInstruction" else "")
                CharacterPhotoStyle.CUSTOM -> customInstruction?.ifBlank { "Alluring natural posture, attractive styling" } ?: "Alluring natural posture, attractive styling"
            }

            _generationStatus.value = "Génération IA en cours (Horde Diffusion)..."

            val result = cloudBridge.generateCharacterSceneImage(
                imageEngine = settings.imageEnginePreference,
                geminiApiKey = geminiKey,
                openAiApiKey = openAiKey.takeIf { it.isNotBlank() },
                cloudApiKey = cloudKey.takeIf { it.isNotBlank() },
                hordeApiKey = hordeKey,
                geminiImageModelName = settings.geminiImageModelName,
                openAiImageModelName = settings.openAiImageModelName,
                cloudImageModelName = settings.cloudImageModelName,
                hordeImageModelName = settings.hordeImageModelName,
                character = character,
                recentMessages = emptyList(),
                userCustomInstruction = instruction,
                outputDir = outputDir,
            )

            result.onSuccess { file ->
                _isGenerating.value = false
                _generationStatus.value = null
                _lastGeneratedPath.value = file.absolutePath
                if (setAsAvatar) {
                    repository.updateAvatar(characterId, file.absolutePath)
                } else {
                    repository.addGalleryMedia(characterId, file.absolutePath)
                }
                onComplete(true, null)
            }.onFailure { err ->
                _isGenerating.value = false
                _generationStatus.value = null
                val msg = err.message ?: "Échec de génération de la photo."
                _generationError.value = msg
                onComplete(false, msg)
            }
        }
    }

    /**
     * Réinitialise totalement la conversation : efface l'historique et réinjecte
     * le premier message du personnage (avec les jetons {{user}} et {{char}} résolus).
     */
    fun startNewConversation(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.clearHistory(characterId)
            val character = repository.getCharacter(characterId)
            if (character != null && character.firstMessage.isNotBlank()) {
                val profile = settingsRepository.userProfile.first()
                val greeting = resolveCharacterPlaceholders(character.firstMessage, character, profile.displayName)
                repository.appendMessage(characterId, MessageRole.ASSISTANT, greeting)
            }
            onComplete()
        }
    }

    fun addGalleryMediaFromUri(uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull() ?: return@launch
            val dir = java.io.File(context.filesDir, "gallery").apply { mkdirs() }
            val mime = context.contentResolver.getType(uri) ?: ""
            val ext = when {
                mime.contains("gif") -> "gif"
                mime.contains("video") || mime.contains("mp4") -> "mp4"
                mime.contains("png") -> "png"
                else -> "jpg"
            }
            val file = java.io.File(dir, "media_${characterId}_${System.currentTimeMillis()}.$ext")
            file.writeBytes(bytes)
            repository.addGalleryMedia(characterId, file.absolutePath)
        }
    }

    fun addGalleryMediaFromUrl(url: String) {
        viewModelScope.launch {
            if (url.isNotBlank()) {
                repository.addGalleryMedia(characterId, url.trim())
            }
        }
    }

    fun removeGalleryMedia(mediaPathOrUrl: String) {
        viewModelScope.launch {
            repository.removeGalleryMedia(characterId, mediaPathOrUrl)
        }
    }
}
