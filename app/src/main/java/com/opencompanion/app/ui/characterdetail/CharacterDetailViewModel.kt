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
    SELFIE("📱 Vrai Selfie Miroir / Caméra (POV)", "Selfie smartphone décomplexé, pose intime prise sur le vif"),
    PROVOCATIVE("🔥 Provocante & Sensuelle (NSFW)", "Pose audacieuse, lingerie fine ou cambrure suggestive"),
    PORTRAIT("📸 Portrait fidèle (Visage & Buste)", "Conforme aux cheveux, yeux, teint et âge du personnage"),
    INTIMATE("💋 Photo Boudoir & Chambre", "Atmosphère feutrée, satin, ambiance tamisée"),
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

            var geminiKey = settings.geminiApiKey.trim()
            if (geminiKey.isBlank() && (settings.cloudApiKey.trim().startsWith("AIza") || settings.cloudApiKey.trim().length > 30 && !settings.cloudApiKey.trim().startsWith("gsk_") && !settings.cloudApiKey.trim().startsWith("sk-"))) {
                geminiKey = settings.cloudApiKey.trim()
            }
            val openAiKey = settings.openAiApiKey.trim().ifBlank {
                if (settings.cloudApiKey.trim().startsWith("sk-")) settings.cloudApiKey.trim() else ""
            }
            val cloudKey = settings.cloudApiKey.trim()
            val hordeKey = settings.hordeApiKey.trim().ifBlank { "0000000000" }

            val outputDir = File(context.filesDir, "character_photos").apply { mkdirs() }

            val desc = character.description
            val scenarioText = character.scenario.trim()
            val scenesMatch = Regex("""Scènes\s*&\s*Postures\s*:\s*([^•\n]+)""").find(desc)?.groupValues?.get(1)?.trim() ?: ""

            val instruction = when (style) {
                CharacterPhotoStyle.SELFIE -> 
                    "Spontaneous authentic smartphone mirror selfie, holding smartphone, wearing stylish flattering attire, chic modern decorated bedroom or upscale bathroom visible in background with realistic mirrors and warm ambient lighting, candid unposed framing" + (if (!customInstruction.isNullOrBlank()) ", $customInstruction" else "")
                
                CharacterPhotoStyle.PROVOCATIVE -> 
                    "Alluring sensual boudoir photo, wearing exquisite sheer black lace lingerie with delicate satin ribbons, arched posture, warm moody lighting in a luxurious private hotel suite with plush furnishings and ambient lamps, strictly non-explicit aesthetic glamour" + (if (!customInstruction.isNullOrBlank()) ", $customInstruction" else "")
                
                CharacterPhotoStyle.PORTRAIT -> 
                    "Authentic photographic waist-up portrait of a real living person, seated naturally in an elegant sunlit Parisian café or charming decorated living room with visible architectural background depth, warm natural daylight, engaging warm eye contact, lifelike expression" + (if (!customInstruction.isNullOrBlank()) ", $customInstruction" else "")
                
                CharacterPhotoStyle.INTIMATE -> 
                    "Tasteful sensual boudoir photograph, sitting comfortably on a soft bed with rumpled linen in a cozy dimly lit bedroom, wearing a delicate silk satin slip or nightgown, soft bedside table lamp lighting, romantic atmosphere, authentic rich room background, strictly non-explicit" + (if (!customInstruction.isNullOrBlank()) ", $customInstruction" else "")
                
                CharacterPhotoStyle.SCENARIO -> {
                    val sceneTarget = when {
                        scenesMatch.isNotBlank() -> scenesMatch
                        scenarioText.isNotBlank() -> scenarioText
                        character.description.contains("cuisine", ignoreCase = true) -> "in a warm rustic kitchen with marble countertops"
                        character.description.contains("bureau", ignoreCase = true) -> "in a stylish modern private office"
                        character.description.contains("bibliothèque", ignoreCase = true) -> "in a quiet historic university library between high wooden bookstacks"
                        else -> "in her natural authentic lifestyle environment matching her story"
                    }
                    "Authentic photographic lifestyle scene: $sceneTarget, natural posture and candid interaction with the environment, detailed architectural and furniture background" + (if (!customInstruction.isNullOrBlank()) ", $customInstruction" else "")
                }
                
                CharacterPhotoStyle.CUSTOM -> 
                    customInstruction?.ifBlank { "Alluring natural posture, attractive styling, rich environmental background" } ?: "Alluring natural posture, attractive styling, rich environmental background"
            }

            _generationStatus.value = "Génération de la photo en cours..."

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
                recentMessages = emptyList<ChatMessageEntity>(),
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

    fun uploadAvatarToGitHub(
        mediaPath: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> },
    ) {
        viewModelScope.launch {
            val character = repository.getCharacter(characterId) ?: return@launch
            val safeName = character.name
                .lowercase()
                .replace(Regex("[^a-z0-9]"), "_")
                .trim('_')
            val targetFilename = "${safeName}.jpg"

            val result = cloudBridge.uploadAvatarToGitHub(
                characterName = character.name,
                localFilePath = mediaPath,
                targetFilename = targetFilename,
            )

            result.onSuccess { assetPath ->
                // Mise à jour de l'avatar avec le chemin d'asset GitHub officiel
                repository.updateAvatar(characterId, assetPath)
                onResult(true, "Image envoyée avec succès sur GitHub ($targetFilename) !")
            }.onFailure { err ->
                onResult(false, "Échec de l'envoi sur GitHub : ${err.message}")
            }
        }
    }
}
