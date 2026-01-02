package com.narutoai.chat.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.narutoai.chat.data.CustomCharacterDatabase
import com.narutoai.chat.data.CustomCharacterEntity
import com.narutoai.chat.data.CustomCharacterRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * ViewModel pour la création de personnages personnalisés
 */
class CreateCharacterViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: CustomCharacterRepository
    
    init {
        val dao = CustomCharacterDatabase.getDatabase(application).customCharacterDao()
        repository = CustomCharacterRepository(dao)
    }
    
    // État du formulaire
    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()
    
    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()
    
    private val _physicalDescription = MutableStateFlow("")
    val physicalDescription: StateFlow<String> = _physicalDescription.asStateFlow()
    
    private val _age = MutableStateFlow("")
    val age: StateFlow<String> = _age.asStateFlow()
    
    private val _gender = MutableStateFlow("")
    val gender: StateFlow<String> = _gender.asStateFlow()
    
    private val _height = MutableStateFlow("")
    val height: StateFlow<String> = _height.asStateFlow()
    
    private val _hairColor = MutableStateFlow("")
    val hairColor: StateFlow<String> = _hairColor.asStateFlow()
    
    private val _eyeColor = MutableStateFlow("")
    val eyeColor: StateFlow<String> = _eyeColor.asStateFlow()
    
    private val _bodyType = MutableStateFlow("")
    val bodyType: StateFlow<String> = _bodyType.asStateFlow()
    
    private val _bustSize = MutableStateFlow("")
    val bustSize: StateFlow<String> = _bustSize.asStateFlow()
    
    private val _penisSize = MutableStateFlow("")
    val penisSize: StateFlow<String> = _penisSize.asStateFlow()
    
    private val _temperament = MutableStateFlow("")
    val temperament: StateFlow<String> = _temperament.asStateFlow()
    
    private val _scenario = MutableStateFlow("")
    val scenario: StateFlow<String> = _scenario.asStateFlow()
    
    private val _greetingMessage = MutableStateFlow("")
    val greetingMessage: StateFlow<String> = _greetingMessage.asStateFlow()
    
    private val _avatarImageUri = MutableStateFlow<Uri?>(null)
    val avatarImageUri: StateFlow<Uri?> = _avatarImageUri.asStateFlow()
    
    private val _savedImagePath = MutableStateFlow<String?>(null)
    
    // État de l'analyse photo
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()
    
    private val _analysisResult = MutableStateFlow<String?>(null)
    val analysisResult: StateFlow<String?> = _analysisResult.asStateFlow()
    
    // État de sauvegarde
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    
    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // Méthodes de mise à jour
    fun updateName(value: String) { _name.value = value }
    fun updateDescription(value: String) { _description.value = value }
    fun updatePhysicalDescription(value: String) { _physicalDescription.value = value }
    fun updateAge(value: String) { _age.value = value }
    fun updateGender(value: String) { _gender.value = value }
    fun updateHeight(value: String) { _height.value = value }
    fun updateHairColor(value: String) { _hairColor.value = value }
    fun updateEyeColor(value: String) { _eyeColor.value = value }
    fun updateBodyType(value: String) { _bodyType.value = value }
    fun updateBustSize(value: String) { _bustSize.value = value }
    fun updatePenisSize(value: String) { _penisSize.value = value }
    fun updateTemperament(value: String) { _temperament.value = value }
    fun updateScenario(value: String) { _scenario.value = value }
    fun updateGreetingMessage(value: String) { _greetingMessage.value = value }
    
    fun updateAvatarImage(uri: Uri?) {
        _avatarImageUri.value = uri
        
        // Si une image est sélectionnée, proposer l'analyse automatique
        if (uri != null) {
            _analysisResult.value = "Photo sélectionnée. Appuyez sur 'Analyser' pour générer le descriptif automatique."
        } else {
            _analysisResult.value = null
        }
    }
    
    /**
     * Analyse automatique de la photo pour générer le descriptif physique
     * Utilise Hugging Face Vision API (GRATUIT et ILLIMITÉ)
     */
    fun analyzePhoto() {
        if (_avatarImageUri.value == null) {
            _errorMessage.value = "Aucune photo sélectionnée"
            return
        }
        
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisResult.value = "🔍 Analyse avec Hugging Face Vision AI (GRATUIT)..."
            _errorMessage.value = null
            
            try {
                val context = getApplication<Application>()
                // ✅ Hugging Face Vision - GRATUIT et ILLIMITÉ (janvier 2025)
                val visionClient = com.narutoai.chat.api.HuggingFaceVisionClient(context)
                
                val result = visionClient.analyzePhotoForCharacter(_avatarImageUri.value!!)
                
                if (result.isSuccess) {
                    val description = result.getOrNull()
                    
                    if (description != null) {
                        // Remplir la description complète
                        _physicalDescription.value = description.toFormattedDescription()
                        
                        // Auto-remplir les champs individuels
                        _age.value = description.age
                        _gender.value = description.gender
                        _hairColor.value = description.hairColor
                        _eyeColor.value = description.eyeColor
                        _bodyType.value = description.bodyType
                        _bustSize.value = description.bustSize
                        _penisSize.value = description.penisSize
                        _height.value = description.height
                        
                        _analysisResult.value = "✅ Analyse terminée avec succès ! (Hugging Face)"
                        
                        android.util.Log.d("CreateCharacterVM", "✨ Analyse HuggingFace réussie: $description")
                    } else {
                        _analysisResult.value = "⚠️ Analyse incomplète"
                        _errorMessage.value = "L'analyse n'a pas pu extraire toutes les informations"
                    }
                } else {
                    val error = result.exceptionOrNull()
                    _analysisResult.value = "❌ Échec de l'analyse"
                    _errorMessage.value = "Erreur: ${error?.message ?: "Inconnue"}"
                    
                    android.util.Log.e("CreateCharacterVM", "❌ Erreur analyse HuggingFace: ${error?.message}", error)
                }
                
            } catch (e: Exception) {
                _errorMessage.value = "Erreur d'analyse: ${e.message}"
                _analysisResult.value = "❌ Erreur d'analyse"
                
                android.util.Log.e("CreateCharacterVM", "💥 Exception analyse Groq: ${e.message}", e)
            } finally {
                _isAnalyzing.value = false
            }
        }
    }
    
    /**
     * Sauvegarde l'image dans le stockage interne
     */
    private suspend fun saveImageToInternalStorage(uri: Uri): String? {
        return try {
            val context = getApplication<Application>()
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            
            val customCharactersDir = File(context.filesDir, "custom_characters")
            customCharactersDir.mkdirs()
            
            val imageFile = File(customCharactersDir, "avatar_${UUID.randomUUID()}.jpg")
            
            FileOutputStream(imageFile).use { output ->
                inputStream.copyTo(output)
            }
            
            inputStream.close()
            
            imageFile.absolutePath
            
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Génère les prompts système automatiquement
     */
    private fun generateSystemPrompts(): Pair<String, String> {
        val baseName = _name.value.ifEmpty { "Personnage" }
        val baseDesc = _description.value.ifEmpty { "un personnage" }
        val physDesc = _physicalDescription.value
        val temp = _temperament.value
        val scen = _scenario.value
        
        val sfwPrompt = """
Tu es ${baseName}, ${baseDesc}.

Description physique: ${physDesc}

Tempérament: ${temp}

Scénario: ${scen}

Tu dois répondre en restant dans le personnage, de manière cohérente avec ta personnalité et ton histoire. Sois naturel(le), authentique et engageant(e) dans la conversation.
        """.trim()
        
        val nsfwPrompt = """
Tu es ${baseName}, ${baseDesc}.

Description physique: ${physDesc}

Tempérament: ${temp}

Scénario: ${scen}

Tu participes à un roleplay adulte (18+). Tu peux discuter de sujets intimes, sensuels et explicites de manière naturelle et consentie. Reste dans le personnage tout en étant ouvert(e) aux conversations NSFW. Sois descriptif(ve) et immersif(ve).
        """.trim()
        
        return Pair(sfwPrompt, nsfwPrompt)
    }
    
    /**
     * Sauvegarde le personnage
     */
    fun saveCharacter() {
        viewModelScope.launch {
            // Validation
            if (_name.value.isBlank()) {
                _errorMessage.value = "Le nom est requis"
                return@launch
            }
            
            if (_description.value.isBlank()) {
                _errorMessage.value = "La description est requise"
                return@launch
            }
            
            _isSaving.value = true
            _errorMessage.value = null
            
            try {
                // Sauvegarder l'image si présente
                val imagePath = _avatarImageUri.value?.let { uri ->
                    saveImageToInternalStorage(uri)
                } ?: ""
                
                _savedImagePath.value = imagePath
                
                // Générer les prompts
                val (sfwPrompt, nsfwPrompt) = generateSystemPrompts()
                
                // Créer l'entité
                val character = CustomCharacterEntity(
                    id = "custom_${UUID.randomUUID()}",
                    name = _name.value,
                    description = _description.value,
                    systemPromptSFW = sfwPrompt,
                    systemPromptNSFW = nsfwPrompt,
                    avatarImagePath = imagePath,
                    personality = "[]", // TODO: gérer la liste
                    physicalDescription = _physicalDescription.value,
                    age = _age.value,
                    gender = _gender.value,
                    height = _height.value,
                    hairColor = _hairColor.value,
                    eyeColor = _eyeColor.value,
                    bodyType = _bodyType.value,
                    bustSize = _bustSize.value,
                    penisSize = _penisSize.value,
                    distinctiveFeatures = "[]",
                    scenario = _scenario.value,
                    backgroundStory = "",
                    temperament = _temperament.value,
                    characterTraits = "[]",
                    likes = "[]",
                    dislikes = "[]",
                    skills = "[]",
                    greetingMessage = _greetingMessage.value,
                    isAutoGenerated = _analysisResult.value?.contains("terminée") == true
                )
                
                // Sauvegarder dans la DB
                repository.insertCharacter(character)
                
                android.util.Log.d("CreateCharacterVM", "✅ Personnage sauvegardé: ${character.name} (ID: ${character.id})")
                
                _saveSuccess.value = true
                
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("CreateCharacterVM", "❌ Erreur sauvegarde: ${e.message}", e)
                _errorMessage.value = "Erreur de sauvegarde: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }
    
    /**
     * Réinitialise l'état de succès
     */
    fun resetSaveSuccess() {
        _saveSuccess.value = false
    }
    
    /**
     * Réinitialise le formulaire
     */
    fun resetForm() {
        _name.value = ""
        _description.value = ""
        _physicalDescription.value = ""
        _age.value = ""
        _height.value = ""
        _hairColor.value = ""
        _eyeColor.value = ""
        _bodyType.value = ""
        _temperament.value = ""
        _scenario.value = ""
        _greetingMessage.value = ""
        _avatarImageUri.value = null
        _savedImagePath.value = null
        _analysisResult.value = null
        _errorMessage.value = null
        _saveSuccess.value = false
    }
}
