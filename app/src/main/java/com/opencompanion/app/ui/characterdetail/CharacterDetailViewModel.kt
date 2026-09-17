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
    PORTRAIT("📸 Portrait (Visage uniquement)", "Gros plan exclusif sur le visage, yeux captivants, lèvres et grain de peau"),
    SELFIE("📱 Selfie Sexy (Visage & Torse)", "Selfie décomplexé : vue plongeante décolleté, face ou profil aléatoire"),
    PROVOCATIVE("🔥 Provocante & Sensuelle", "Lingerie fine, nuisette transparente, corset, décolleté plongeant et pose cambrée"),
    INTIMATE("💋 Boudoir & Chambre", "Lit ou salle de bain, peignoir satin, jambes nues, décolleté plongeant et cambrure"),
    ELEGANT("👠 Élégante & Sexy", "Robe courte moulante décolletée, minijupe, bas nylon/résille, talons hauts ou tailleur serré"),
    SCENARIO("🏢 Métier & Scène de vie", "Contexte professionnel ou quotidien lié à son histoire et ses scènes"),
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

            val sceneTarget = when {
                scenesMatch.isNotBlank() -> scenesMatch.take(120)
                scenarioText.isNotBlank() -> scenarioText.take(120)
                character.description.contains("bibliothèque", ignoreCase = true) -> "in a historic university library with oak bookshelves, wooden desk and warm lamp"
                character.description.contains("bureau", ignoreCase = true) -> "in an intimate private study office with wooden bookshelves and desk"
                character.description.contains("cuisine", ignoreCase = true) -> "in a warm rustic kitchen with marble countertops"
                character.description.contains("atelier", ignoreCase = true) -> "in a bright artist studio with easel and canvases"
                character.description.contains("cabinet", ignoreCase = true) -> "in a private medical consultation office"
                else -> "in her authentic living space matching her scenario"
            }

            val instruction = when (style) {
                CharacterPhotoStyle.PORTRAIT -> 
                    "EXTREME CLOSE-UP MACRO PORTRAIT OF THE FACE ONLY, tight headshot crop framing only the face from chin to forehead, strictly focusing on gorgeous facial features, expressive eyes, soft parted lips, hair framing cheeks, piercing direct eye contact. In the background, softly blurred atmosphere of $sceneTarget. STRICTLY FACE ONLY, NO torso, NO bust, NO chest, NO body" + (if (!customInstruction.isNullOrBlank()) ", $customInstruction" else "")
                
                CharacterPhotoStyle.SELFIE -> {
                    val selfieAngles = listOf(
                        "Spontaneous handheld smartphone selfie, framing face and bust-up cleavage, direct high-angle shot looking slightly down at her enticing cleavage and smile, attractive low-cut top, background clearly showing $sceneTarget",
                        "Spontaneous handheld smartphone front camera selfie, framing face and torso, confident seductive smile, low-cut flattering top accentuating curves and bust, background of $sceneTarget",
                        "Alluring handheld smartphone selfie, looking back over shoulder with a smoldering gaze, arched waist, highlighting curves and bust, inside $sceneTarget"
                    )
                    selfieAngles.random() + (if (!customInstruction.isNullOrBlank()) ", $customInstruction" else "")
                }
                
                CharacterPhotoStyle.PROVOCATIVE -> {
                    val provocativeOutfits = listOf(
                        "wearing an exquisitely sexy opaque black floral lace corset covering her breasts, provocative arched waist pose highlighting curves and cleavage, smoldering seductive gaze",
                        "wearing a sheer translucent babydoll nightie with plunging neckline and delicate lace trim covering breasts, highlighting arched waist and curves, provocative posture",
                        "wearing an alluring teasing low-cut silk camisole and lace shorts, provocative and glamorous, sensual eye contact"
                    )
                    val chosenOutfit = provocativeOutfits.random()
                    "Alluring highly provocative aesthetic glamour photo, $chosenOutfit, inside $sceneTarget with authentic furniture and warm ambient lighting, strictly non-explicit artistic glamour" + (if (!customInstruction.isNullOrBlank()) ", $customInstruction" else "")
                }
                
                CharacterPhotoStyle.INTIMATE -> {
                    val intimateScenes = listOf(
                        "sitting gracefully on a plush bed or chaise with white sheets, wearing an elegant silk satin robe and lace slip covering breasts, bare toned legs, leaning forward showing alluring cleavage, warm golden lighting, inside $sceneTarget",
                        "in an upscale modern bathroom, sitting poised on the edge of the marble vanity, wearing a silk nightgown covering breasts, bare legs, arched back, alluring confident eye contact, warm candlelit ambiance, inside $sceneTarget",
                        "lounging sensually across a plush sofa, arched waist accentuating shapely curves and bust, looking over shoulder with a sultry gaze, cozy evening mood, inside $sceneTarget"
                    )
                    val chosenScene = intimateScenes.random()
                    "Tasteful sensual boudoir and bedroom photography, $chosenScene, authentic tangible room background with visible furniture and decor, warm moody cinematic lighting, seductive allure, strictly non-explicit" + (if (!customInstruction.isNullOrBlank()) ", $customInstruction" else "")
                }
                
                CharacterPhotoStyle.ELEGANT -> {
                    val elegantStyles = listOf(
                        "wearing an ultra-tight short bodycon mini-dress with a deeply plunging neckline, sheer black nylon stockings, high stiletto heels, arched posture highlighting curves and bust",
                        "wearing a stylish daring short miniskirt and form-fitting cropped top accentuating bust and waist, sheer stockings and high heels, glamorous nightlife look",
                        "wearing a sleek tailored pencil skirt and unbuttoned silk blouse hugging curves and bust, sheer stockings, high heels, chic provocative style"
                    )
                    val chosenElegant = elegantStyles.random()
                    "High-fashion glamorous photo: $chosenElegant, posing confidently inside $sceneTarget, confident alluring posture, seductive smile, cinematic lighting, tangible background depth" + (if (!customInstruction.isNullOrBlank()) ", $customInstruction" else "")
                }

                CharacterPhotoStyle.SCENARIO -> {
                    "Authentic photographic scene directly inside: $sceneTarget, natural provocative posture and candid interaction with the environment, tangible furniture, books, decor and warm cinematic lighting" + (if (!customInstruction.isNullOrBlank()) ", $customInstruction" else "")
                }
                
                CharacterPhotoStyle.CUSTOM -> 
                    customInstruction?.ifBlank { "Alluring natural posture, attractive styling, tangible background of $sceneTarget" } ?: "Alluring natural posture, attractive styling, tangible background of $sceneTarget"
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

    fun uploadCharacterCardToGitHub(
        onResult: (Boolean, String) -> Unit = { _, _ -> },
    ) {
        viewModelScope.launch {
            val character = repository.getCharacter(characterId) ?: return@launch
            val result = cloudBridge.uploadCharacterCardToGitHub(character)
            result.onSuccess { path ->
                onResult(true, "Fiche de ${character.name} sauvegardée sur GitHub ($path) !")
            }.onFailure { err ->
                onResult(false, "Échec de l'envoi sur GitHub : ${err.message}")
            }
        }
    }
}
