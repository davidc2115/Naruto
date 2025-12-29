package com.narutoai.chat.viewmodel

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.narutoai.chat.api.GroqClient
import com.narutoai.chat.api.ImageGenerationClient
import com.narutoai.chat.api.VideoGenerationClient
import com.narutoai.chat.api.PollinationAIClient
import com.narutoai.chat.models.Character
import com.narutoai.chat.models.ChatMessage
import com.narutoai.chat.models.UserProfile
import com.narutoai.chat.models.Gender
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    
    private val sharedPreferences = application.getSharedPreferences("naruto_ai_prefs", android.content.Context.MODE_PRIVATE)
    private val conversationManager = com.narutoai.chat.data.ConversationManager(application.applicationContext)
    
    private val _userProfile = mutableStateOf(loadUserProfile())
    val userProfile: State<UserProfile> = _userProfile
    
    private val _selectedCharacter = mutableStateOf<Character?>(null)
    val selectedCharacter: State<Character?> = _selectedCharacter
    
    private val _messages = mutableStateOf<List<ChatMessage>>(emptyList())
    val messages: State<List<ChatMessage>> = _messages
    
    private val _isNSFWMode = mutableStateOf(false)
    val isNSFWMode: State<Boolean> = _isNSFWMode
    
    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading
    
    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error
    
    private val _isGeneratingImage = mutableStateOf(false)
    val isGeneratingImage: State<Boolean> = _isGeneratingImage
    
    private val _isGeneratingVideo = mutableStateOf(false)
    val isGeneratingVideo: State<Boolean> = _isGeneratingVideo
    
    private val _generatedImageUrl = mutableStateOf<String?>(null)
    val generatedImageUrl: State<String?> = _generatedImageUrl
    
    private val _generatedVideoUrl = mutableStateOf<String?>(null)
    val generatedVideoUrl: State<String?> = _generatedVideoUrl
    
    private val _replicateApiKey = mutableStateOf<String?>(null)
    val replicateApiKey: State<String?> = _replicateApiKey
    
    private val groqClient = GroqClient(application.applicationContext)
    private val imageClient = ImageGenerationClient(application.applicationContext)
    private val videoClient = VideoGenerationClient(application.applicationContext)
    private val pollinationAIClient = PollinationAIClient()
    private val freeboxMediaClient = com.narutoai.chat.api.FreeboxMediaClient(pollinationAIClient)
    
    init {
        viewModelScope.launch {
            groqClient.initialize()
        }
    }
    
    private fun loadUserProfile(): UserProfile {
        val pseudo = sharedPreferences.getString("user_pseudo", "") ?: ""
        val age = sharedPreferences.getInt("user_age", -1).takeIf { it >= 0 }
        val genderOrdinal = sharedPreferences.getInt("user_gender", Gender.NOT_SPECIFIED.ordinal)
        val gender = Gender.entries.getOrNull(genderOrdinal) ?: Gender.NOT_SPECIFIED
        val bio = sharedPreferences.getString("user_bio", "") ?: ""
        
        return UserProfile(pseudo, age, gender, bio)
    }
    
    fun saveUserProfile(profile: UserProfile) {
        sharedPreferences.edit().apply {
            putString("user_pseudo", profile.pseudo)
            if (profile.age != null) {
                putInt("user_age", profile.age)
            } else {
                putInt("user_age", -1)
            }
            putInt("user_gender", profile.gender.ordinal)
            putString("user_bio", profile.bio)
            apply()
        }
        _userProfile.value = profile
    }
    
    private fun getUserContext(): String {
        val profile = _userProfile.value
        if (profile.pseudo.isEmpty()) return ""
        
        val parts = mutableListOf<String>()
        parts.add("L'utilisateur s'appelle ${profile.pseudo}")
        
        if (profile.gender != Gender.NOT_SPECIFIED) {
            parts.add("Genre: ${profile.gender.displayName}")
            if (profile.gender.pronoun.isNotEmpty()) {
                parts.add("Pronom: ${profile.gender.pronoun}")
            }
        }
        
        if (profile.age != null) {
            parts.add("Âge: ${profile.age} ans")
        }
        
        if (profile.bio.isNotBlank()) {
            parts.add("Bio: ${profile.bio}")
        }
        
        return parts.joinToString(". ") + "."
    }
    
    fun selectCharacter(character: Character, loadSaved: Boolean = true) {
        _selectedCharacter.value = character
        _error.value = null
        
        // Charger conversation sauvegardée si demandé
        if (loadSaved && conversationManager.hasConversation(character.id)) {
            val savedMessages = conversationManager.loadConversation(character.id)
            val savedNSFW = conversationManager.getIsNSFW(character.id)
            
            _messages.value = savedMessages ?: emptyList()
            _isNSFWMode.value = savedNSFW
        } else {
            // Nouvelle conversation
            _messages.value = emptyList()
            _isNSFWMode.value = false
            
            // Ajouter message d'accueil automatique si disponible
            if (character.greetingMessage.isNotEmpty()) {
                viewModelScope.launch {
                    kotlinx.coroutines.delay(500)
                    val greetingMsg = ChatMessage(
                        content = character.greetingMessage,
                        isUser = false
                    )
                    _messages.value = listOf(greetingMsg)
                }
            }
        }
    }
    
    fun hasSavedConversation(characterId: String): Boolean {
        return conversationManager.hasConversation(characterId)
    }
    
    fun startNewConversation() {
        val character = _selectedCharacter.value ?: return
        
        // Supprimer l'ancienne conversation
        conversationManager.deleteConversation(character.id)
        
        // Réinitialiser
        selectCharacter(character, loadSaved = false)
    }
    
    fun saveCurrentConversation() {
        val character = _selectedCharacter.value ?: return
        if (_messages.value.isEmpty()) return
        
        conversationManager.saveConversation(
            characterId = character.id,
            messages = _messages.value,
            isNSFW = _isNSFWMode.value
        )
    }
    
    fun toggleNSFWMode() {
        _isNSFWMode.value = !_isNSFWMode.value
        // Optionally clear messages when switching modes
        // _messages.value = emptyList()
    }
    
    fun sendMessage(text: String) {
        val character = _selectedCharacter.value ?: return
        if (text.isBlank()) return
        
        // Add user message
        val userMessage = ChatMessage(
            content = text,
            isUser = true
        )
        _messages.value = _messages.value + userMessage
        
        // Get AI response
        _isLoading.value = true
        _error.value = null
        
        viewModelScope.launch {
            try {
                val baseSystemPrompt = if (_isNSFWMode.value) {
                    character.systemPromptNSFW
                } else {
                    character.systemPromptSFW
                }
                
                // Ajouter contexte utilisateur au prompt
                val userContext = getUserContext()
                val systemPrompt = if (userContext.isNotEmpty()) {
                    "$baseSystemPrompt\n\n[CONTEXTE UTILISATEUR]\n$userContext\nUtilise ces informations pour personnaliser tes réponses."
                } else {
                    baseSystemPrompt
                }
                
                // Build conversation history
                val history = _messages.value.takeLast(20).map { msg ->
                    val role = if (msg.isUser) "user" else "assistant"
                    role to msg.content
                }
                
                val result = groqClient.chat(
                    systemPrompt = systemPrompt,
                    userMessage = text,
                    conversationHistory = history.dropLast(1), // Exclude the message we just added
                    temperature = if (_isNSFWMode.value) 0.9 else 0.8,
                    maxTokens = 500,
                    isNSFW = _isNSFWMode.value // IMPORTANT: Passer le flag NSFW
                )
                
                result.fold(
                    onSuccess = { response ->
                        val aiMessage = ChatMessage(
                            content = response,
                            isUser = false
                        )
                        _messages.value = _messages.value + aiMessage
                        _isLoading.value = false
                        
                        // Sauvegarder automatiquement après chaque message
                        saveCurrentConversation()
                    },
                    onFailure = { exception ->
                        _error.value = exception.message ?: "Unknown error"
                        _isLoading.value = false
                    }
                )
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
                _isLoading.value = false
            }
        }
    }
    
    fun clearError() {
        _error.value = null
    }
    
    fun clearChat() {
        _messages.value = emptyList()
    }
    
    fun goBack() {
        _selectedCharacter.value = null
        _messages.value = emptyList()
        _isNSFWMode.value = false
        _error.value = null
        _generatedImageUrl.value = null
        _generatedVideoUrl.value = null
    }
    
    /**
     * Définit la clé API Replicate pour la génération d'images/vidéos
     */
    fun setReplicateApiKey(key: String) {
        _replicateApiKey.value = key
    }
    
    /**
     * Génère une image basée sur le contexte de la conversation
     * Utilise FreeboxMediaClient (Stable Diffusion local)
     */
    fun generateImageFromConversation() {
        val character = _selectedCharacter.value ?: run {
            _error.value = "Aucun personnage sélectionné"
            return
        }
        
        if (_messages.value.isEmpty()) {
            _error.value = "Discutez d'abord avec le personnage avant de générer une image"
            return
        }
        
        _isGeneratingImage.value = true
        _error.value = null
        
        // Ajouter message de statut
        val statusMessage = ChatMessage(
            content = "🎨 Génération d'image en cours...",
            isUser = false
        )
        _messages.value = _messages.value + statusMessage
        
        viewModelScope.launch {
            try {
                // Prendre les derniers messages pour le contexte
                val context = _messages.value.filter { !it.content.startsWith("🎨") && !it.content.startsWith("❌") }
                    .takeLast(5).joinToString("\n") { msg ->
                        val role = if (msg.isUser) "User" else character.name
                        "$role: ${msg.content}"
                    }
                
                // Créer un prompt d'image avec Groq
                val nsfwContext = if (_isNSFWMode.value) {
                    "\n\nIMPORTANT: Generate an EXPLICIT NSFW/adult/erotic scene. Include nudity, sensual poses, intimate details, sexual content if contextually appropriate."
                } else {
                    ""
                }
                
                val promptRequest = """
                    Based on this conversation with ${character.name}:
                    $context
                    
                    Physical description of ${character.name}:
                    ${character.physicalDescription}$nsfwContext
                    
                    Create a detailed prompt in ENGLISH (max 75 words) for generating ${if (_isNSFWMode.value) "an NSFW/adult/erotic" else "a hyper-realistic"} image of this scene.
                    Include: character's physical features, setting, mood, lighting, and action${if (_isNSFWMode.value) ", nudity, sensual/sexual elements" else ""}.
                    Respond ONLY with the English prompt, no explanation.
                """.trimIndent()
                
                val promptResult = groqClient.chat(
                    systemPrompt = "You are an expert at creating detailed prompts for AI image generation. Focus on visual details, lighting, and atmosphere.",
                    userMessage = promptRequest,
                    maxTokens = 150,
                    isNSFW = _isNSFWMode.value // Permettre prompts NSFW
                )
                
                val imagePrompt = promptResult.getOrNull()
                    ?: run {
                        val errorMsg = "❌ Échec de création du prompt avec Groq"
                        _error.value = errorMsg
                        _isGeneratingImage.value = false
                        _messages.value = _messages.value.dropLast(1) + ChatMessage(
                            content = errorMsg,
                            isUser = false
                        )
                        return@launch
                    }
                
                // Utiliser Freebox (fallback automatique sur Pollination AI si inaccessible)
                // Délai pour éviter rate limit après Groq
                kotlinx.coroutines.delay(2000)
                
                val style = if (character.category == com.narutoai.chat.models.CharacterCategory.NARUTO) "anime" else "realistic"
                
                // Générer avec Freebox (fallback Pollination AI intégré)
                // Paramètres optimisés pour vitesse sur ARM CPU
                val result = freeboxMediaClient.generateImage(
                    prompt = imagePrompt,
                    width = 512, // Réduit pour vitesse
                    height = 512, // Réduit pour vitesse
                    steps = 12, // Réduit pour vitesse (12 au lieu de 25)
                    cfgScale = 6.0, // Réduit pour vitesse
                    isNSFW = _isNSFWMode.value
                )
                
                result.fold(
                    onSuccess = { imageUrl ->
                        _generatedImageUrl.value = imageUrl
                        _isGeneratingImage.value = false
                        
                        // Remplacer le message de statut par l'image AVEC URL
                        val source = if (imageUrl.startsWith("http")) "Cloud API" else "Local"
                        _messages.value = _messages.value.dropLast(1) + ChatMessage(
                            content = "✅ Image générée avec succès ($source)",
                            isUser = false,
                            imageUrl = imageUrl // AJOUT: Inclure l'URL de l'image
                        )
                    },
                    onFailure = { exception ->
                        val errorMsg = "❌ Erreur génération image: ${exception.message}\n\n💡 Conseil: Vérifiez votre connexion Internet."
                        _error.value = errorMsg
                        _isGeneratingImage.value = false
                        _messages.value = _messages.value.dropLast(1) + ChatMessage(
                            content = errorMsg,
                            isUser = false
                        )
                    }
                )
            } catch (e: Exception) {
                val errorMsg = "❌ Erreur: ${e.message}"
                _error.value = errorMsg
                _isGeneratingImage.value = false
                _messages.value = _messages.value.dropLast(1) + ChatMessage(
                    content = errorMsg,
                    isUser = false
                )
            }
        }
    }
    
    /**
     * Génère une vidéo basée sur le contexte de la conversation
     * Utilise FreeboxMediaClient
     */
    fun generateVideoFromConversation() {
        val character = _selectedCharacter.value ?: run {
            _error.value = "Aucun personnage sélectionné"
            return
        }
        
        if (_messages.value.isEmpty()) {
            _error.value = "Discutez d'abord avec le personnage avant de générer une vidéo"
            return
        }
        
        _isGeneratingVideo.value = true
        _error.value = null
        
                // Ajouter message de statut
        val statusMessage = ChatMessage(
            content = "🎬 Génération de vidéo en cours... (5 secondes, cela peut prendre 1-2 minutes)",
            isUser = false
        )
        _messages.value = _messages.value + statusMessage
        
        viewModelScope.launch {
            try {
                val context = _messages.value.filter { !it.content.startsWith("🎬") && !it.content.startsWith("❌") }
                    .takeLast(5).joinToString("\n") { msg ->
                        val role = if (msg.isUser) "User" else character.name
                        "$role: ${msg.content}"
                    }
                
                // Créer un prompt vidéo avec Groq
                val promptRequest = """
                    Based on this conversation with ${character.name}:
                    $context
                    
                    Physical description: ${character.physicalDescription}
                    
                    Create a detailed video prompt in ENGLISH (max 75 words) for a 5 second animated scene.
                    Include: character movement, action, camera angle, lighting, atmosphere, and transitions.
                    Make it cinematic and dynamic with smooth motion.
                    Respond ONLY with the English prompt, no explanation.
                """.trimIndent()
                
                val promptResult = groqClient.chat(
                    systemPrompt = "You are an expert at creating cinematic video prompts with movement and action details.",
                    userMessage = promptRequest,
                    maxTokens = 150,
                    isNSFW = _isNSFWMode.value // Permettre prompts NSFW pour vidéos
                )
                
                val videoPrompt = promptResult.getOrNull()
                    ?: run {
                        val errorMsg = "❌ Échec de création du prompt vidéo"
                        _error.value = errorMsg
                        _isGeneratingVideo.value = false
                        _messages.value = _messages.value.dropLast(1) + ChatMessage(
                            content = errorMsg,
                            isUser = false
                        )
                        return@launch
                    }
                
                // Utiliser Freebox (fallback automatique sur Pollination AI si inaccessible)
                // Délai pour éviter rate limit après Groq
                kotlinx.coroutines.delay(2000)
                
                // Générer une vraie vidéo MP4 avec Pollination AI Video
                val result = freeboxMediaClient.generateVideo(
                    prompt = "$videoPrompt, smooth motion, cinematic, fluid animation, dynamic scene",
                    width = 512,
                    height = 512,
                    duration = 5, // 5 secondes de vidéo
                    isNSFW = _isNSFWMode.value
                )
                
                result.fold(
                    onSuccess = { videoUrl ->
                        _generatedVideoUrl.value = videoUrl
                        _isGeneratingVideo.value = false
                        
                        val source = "Pollination AI Video"
                        _messages.value = _messages.value.dropLast(1) + ChatMessage(
                            content = "✅ Vidéo générée (5s MP4, $source)",
                            isUser = false,
                            videoUrl = videoUrl // AJOUT: Inclure l'URL de la vidéo
                        )
                    },
                    onFailure = { exception ->
                        val errorMsg = "❌ Erreur génération vidéo: ${exception.message}"
                        _error.value = errorMsg
                        _isGeneratingVideo.value = false
                        _messages.value = _messages.value.dropLast(1) + ChatMessage(
                            content = errorMsg,
                            isUser = false
                        )
                    }
                )
            } catch (e: Exception) {
                val errorMsg = "❌ Erreur: ${e.message}"
                _error.value = errorMsg
                _isGeneratingVideo.value = false
                _messages.value = _messages.value.dropLast(1) + ChatMessage(
                    content = errorMsg,
                    isUser = false
                )
            }
        }
    }
    
    /**
     * Génère une galerie d'images pour un personnage avec Pollination AI
     */
    fun generateCharacterGallery(character: Character, count: Int = 6, onComplete: (List<String>) -> Unit) {
        _isGeneratingImage.value = true
        _error.value = null
        
        viewModelScope.launch {
            try {
                val result = pollinationAIClient.generateCharacterGallery(
                    characterName = character.name,
                    physicalDescription = character.physicalDescription,
                    style = if (character.category == com.narutoai.chat.models.CharacterCategory.NARUTO) "anime" else "realistic",
                    count = count
                )
                
                result.fold(
                    onSuccess = { images ->
                        _isGeneratingImage.value = false
                        onComplete(images)
                    },
                    onFailure = { exception ->
                        _error.value = "Erreur génération galerie: ${exception.message}"
                        _isGeneratingImage.value = false
                    }
                )
            } catch (e: Exception) {
                _error.value = "Erreur: ${e.message}"
                _isGeneratingImage.value = false
            }
        }
    }
    
    /**
     * Génère une vignette pour un personnage avec Pollination AI
     */
    fun generateCharacterThumbnail(character: Character, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val result = pollinationAIClient.generateCharacterThumbnail(
                    characterName = character.name,
                    physicalDescription = character.physicalDescription,
                    style = if (character.category == com.narutoai.chat.models.CharacterCategory.NARUTO) "anime" else "realistic"
                )
                
                result.fold(
                    onSuccess = { thumbnailUrl ->
                        onComplete(thumbnailUrl)
                    },
                    onFailure = { exception ->
                        _error.value = "Erreur génération vignette: ${exception.message}"
                    }
                )
            } catch (e: Exception) {
                _error.value = "Erreur: ${e.message}"
            }
        }
    }
    
    /**
     * Génère une image à partir d'un prompt personnalisé
     */
    fun generateCustomImage(prompt: String, style: String = "anime") {
        val apiKey = _replicateApiKey.value
        
        if (apiKey.isNullOrBlank()) {
            _error.value = "Clé API Replicate requise"
            return
        }
        
        _isGeneratingImage.value = true
        _error.value = null
        
        viewModelScope.launch {
            try {
                val result = imageClient.generateImage(prompt, style, apiKey)
                
                result.fold(
                    onSuccess = { imageUrl ->
                        _generatedImageUrl.value = imageUrl
                        _isGeneratingImage.value = false
                        
                        val imageMessage = ChatMessage(
                            content = "[Image: $prompt] $imageUrl",
                            isUser = false
                        )
                        _messages.value = _messages.value + imageMessage
                    },
                    onFailure = { exception ->
                        _error.value = "Erreur: ${exception.message}"
                        _isGeneratingImage.value = false
                    }
                )
            } catch (e: Exception) {
                _error.value = "Erreur: ${e.message}"
                _isGeneratingImage.value = false
            }
        }
    }
    
    /**
     * Obtient le gestionnaire de clés API Groq
     */
    fun getGroqKeyManager() = groqClient.getKeyManager()
    
    /**
     * Teste la connexion Groq
     */
    fun testGroqConnection(onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = groqClient.ping()
            result.fold(
                onSuccess = { onResult(true, null) },
                onFailure = { onResult(false, it.message) }
            )
        }
    }
}
