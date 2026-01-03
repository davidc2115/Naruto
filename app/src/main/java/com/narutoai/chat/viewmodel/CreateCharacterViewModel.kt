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
    
    private val _height = MutableStateFlow("")
    val height: StateFlow<String> = _height.asStateFlow()
    
    private val _hairColor = MutableStateFlow("")
    val hairColor: StateFlow<String> = _hairColor.asStateFlow()
    
    private val _eyeColor = MutableStateFlow("")
    val eyeColor: StateFlow<String> = _eyeColor.asStateFlow()
    
    private val _bodyType = MutableStateFlow("")
    val bodyType: StateFlow<String> = _bodyType.asStateFlow()
    
    private val _gender = MutableStateFlow("")
    val gender: StateFlow<String> = _gender.asStateFlow()
    
    private val _breastSize = MutableStateFlow("")
    val breastSize: StateFlow<String> = _breastSize.asStateFlow()
    
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
    
    // ID du personnage en cours d'édition (null = création)
    private val _editingCharacterId = MutableStateFlow<String?>(null)
    val editingCharacterId: StateFlow<String?> = _editingCharacterId.asStateFlow()
    
    // Méthodes de mise à jour
    fun updateName(value: String) { _name.value = value }
    fun updateDescription(value: String) { _description.value = value }
    fun updatePhysicalDescription(value: String) { _physicalDescription.value = value }
    fun updateAge(value: String) { _age.value = value }
    fun updateHeight(value: String) { _height.value = value }
    fun updateHairColor(value: String) { _hairColor.value = value }
    fun updateEyeColor(value: String) { _eyeColor.value = value }
    fun updateBodyType(value: String) { _bodyType.value = value }
    fun updateGender(value: String) { _gender.value = value }
    fun updateBreastSize(value: String) { _breastSize.value = value }
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
     * Charge un personnage existant pour édition
     */
    fun loadCharacterForEdit(characterId: String) {
        viewModelScope.launch {
            try {
                // D'abord essayer de charger depuis la BDD personnalisée
                val character = repository.getCharacterById(characterId)
                if (character != null) {
                    _editingCharacterId.value = characterId
                    _name.value = character.name
                    _description.value = character.description
                    _physicalDescription.value = character.physicalDescription
                    _age.value = character.age
                    _height.value = character.height
                    _hairColor.value = character.hairColor
                    _eyeColor.value = character.eyeColor
                    _bodyType.value = character.bodyType
                    _gender.value = character.gender
                    _breastSize.value = character.breastSize
                    _penisSize.value = character.penisSize
                    _temperament.value = character.temperament
                    _scenario.value = character.scenario
                    _greetingMessage.value = character.greetingMessage
                    
                    // Charger l'image si elle existe
                    if (character.avatarImagePath.isNotEmpty()) {
                        val file = File(character.avatarImagePath)
                        if (file.exists()) {
                            _avatarImageUri.value = Uri.fromFile(file)
                            _savedImagePath.value = character.avatarImagePath
                        }
                    }
                    
                    android.util.Log.d("CreateCharacterVM", "✅ Personnage personnalisé chargé pour édition: ${character.name}")
                } else {
                    // Si pas trouvé, charger depuis les personnages intégrés
                    val builtInCharacter = com.narutoai.chat.data.Characters.allCharacters.find { it.id == characterId }
                    if (builtInCharacter != null) {
                        // Créer un nouvel ID pour la copie
                        _editingCharacterId.value = "custom_${java.util.UUID.randomUUID()}"
                        _name.value = builtInCharacter.name + " (Modifié)"
                        _description.value = builtInCharacter.description
                        _physicalDescription.value = builtInCharacter.physicalDescription
                        _age.value = builtInCharacter.age
                        _height.value = builtInCharacter.height
                        _hairColor.value = builtInCharacter.hairColor
                        _eyeColor.value = builtInCharacter.eyeColor
                        _bodyType.value = builtInCharacter.bodyType
                        _gender.value = builtInCharacter.gender
                        _breastSize.value = builtInCharacter.breastSize
                        _penisSize.value = builtInCharacter.penisSize
                        _temperament.value = builtInCharacter.temperament
                        _scenario.value = builtInCharacter.scenario
                        _greetingMessage.value = builtInCharacter.greetingMessage
                        
                        // Pas d'image pour les personnages intégrés (on utilise imageResId)
                        _avatarImageUri.value = null
                        _savedImagePath.value = null
                        
                        android.util.Log.d("CreateCharacterVM", "✅ Personnage intégré chargé pour modification: ${builtInCharacter.name}")
                    } else {
                        _errorMessage.value = "Personnage introuvable"
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Erreur de chargement: ${e.message}"
                android.util.Log.e("CreateCharacterVM", "Erreur chargement: ${e.message}", e)
            }
        }
    }
    
    /**
     * Analyse automatique de la photo pour générer le descriptif physique
     * Utilise Groq Vision API
     */
    fun analyzePhoto() {
        if (_avatarImageUri.value == null) {
            _errorMessage.value = "Aucune photo sélectionnée"
            return
        }
        
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisResult.value = "Analyse en cours avec Groq Vision..."
            _errorMessage.value = null
            
            try {
                val context = getApplication<Application>()
                val visionClient = com.narutoai.chat.api.GroqVisionClient(context)
                
                val result = visionClient.analyzePhotoForCharacter(_avatarImageUri.value!!)
                
                if (result.isSuccess) {
                    val description = result.getOrNull()
                    
                    if (description != null) {
                        // Remplir la description complète
                        _physicalDescription.value = description.toFormattedDescription()
                        
                        // Auto-remplir les champs individuels avec normalisation
                        _age.value = description.age
                        _gender.value = normalizeGender(description.gender)
                        _hairColor.value = description.hairColor
                        _eyeColor.value = description.eyeColor
                        _bodyType.value = description.bodyType
                        _breastSize.value = normalizeBreastSize(description.breastSize)
                        _penisSize.value = normalizePenisSize(description.penisSize)
                        _height.value = description.height
                        
                        _analysisResult.value = "✅ Analyse terminée avec succès !"
                        
                        android.util.Log.d("CreateCharacterVM", "Analyse réussie: $description")
                    } else {
                        _analysisResult.value = "⚠️ Analyse incomplète"
                        _errorMessage.value = "L'analyse n'a pas pu extraire toutes les informations"
                    }
                } else {
                    val error = result.exceptionOrNull()
                    _analysisResult.value = "❌ Échec de l'analyse"
                    _errorMessage.value = "Erreur: ${error?.message ?: "Inconnue"}"
                    
                    android.util.Log.e("CreateCharacterVM", "Erreur analyse: ${error?.message}", error)
                }
                
            } catch (e: Exception) {
                _errorMessage.value = "Erreur d'analyse: ${e.message}"
                _analysisResult.value = "❌ Erreur d'analyse"
                
                android.util.Log.e("CreateCharacterVM", "Exception analyse: ${e.message}", e)
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
                    // Si on édite et qu'on garde la même image
                    if (_savedImagePath.value != null && uri.toString().contains(_savedImagePath.value!!)) {
                        _savedImagePath.value
                    } else {
                        saveImageToInternalStorage(uri)
                    }
                } ?: _savedImagePath.value ?: ""
                
                _savedImagePath.value = imagePath
                
                // Générer les prompts
                val (sfwPrompt, nsfwPrompt) = generateSystemPrompts()
                
                // Créer ou mettre à jour l'entité
                val characterId = _editingCharacterId.value ?: "custom_${UUID.randomUUID()}"
                val character = CustomCharacterEntity(
                    id = characterId,
                    name = _name.value,
                    description = _description.value,
                    systemPromptSFW = sfwPrompt,
                    systemPromptNSFW = nsfwPrompt,
                    avatarImagePath = imagePath,
                    personality = "[]", // TODO: gérer la liste
                    physicalDescription = _physicalDescription.value,
                    age = _age.value,
                    height = _height.value,
                    hairColor = _hairColor.value,
                    eyeColor = _eyeColor.value,
                    bodyType = _bodyType.value,
                    gender = _gender.value,
                    breastSize = _breastSize.value,
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
                
                val action = if (_editingCharacterId.value != null) "modifié" else "sauvegardé"
                android.util.Log.d("CreateCharacterVM", "✅ Personnage $action: ${character.name} (ID: ${character.id})")
                
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
     * Normalise le genre de l'API vers le format du dropdown
     */
    private fun normalizeGender(gender: String): String {
        return when (gender.lowercase().trim()) {
            "homme", "male", "man", "m", "h" -> "Homme"
            "femme", "female", "woman", "f" -> "Femme"
            "autre", "other", "non-binary", "non binaire" -> "Autre"
            else -> gender.replaceFirstChar { it.uppercase() }
        }
    }
    
    /**
     * Normalise la taille de poitrine de l'API vers le format du dropdown
     */
    private fun normalizeBreastSize(size: String): String {
        return when (size.lowercase().trim()) {
            "petite", "small", "petit" -> "Petite"
            "moyenne", "medium", "average", "moyen" -> "Moyenne"
            "généreuse", "generous", "large" -> "Généreuse"
            "très généreuse", "very generous", "très large", "extra large", "xl" -> "Très généreuse"
            else -> if (size.isNotEmpty()) size.replaceFirstChar { it.uppercase() } else ""
        }
    }
    
    /**
     * Normalise la taille du pénis de l'API vers le format du dropdown
     */
    private fun normalizePenisSize(size: String): String {
        return when (size.lowercase().trim()) {
            "moyenne", "medium", "average", "moyen" -> "Moyenne"
            "au-dessus de la moyenne", "above average", "au dessus", "large" -> "Au-dessus de la moyenne"
            "grande", "big", "grand" -> "Grande"
            "très grande", "very large", "extra large", "xl" -> "Très grande"
            else -> if (size.isNotEmpty()) size.replaceFirstChar { it.uppercase() } else ""
        }
    }
    
    /**
     * Crée un personnage complet automatiquement depuis une photo
     * Utilise Groq Vision pour générer TOUT le profil
     */
    fun createCharacterFromPhoto(imageUri: Uri) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisResult.value = "Analyse complète en cours..."
            _errorMessage.value = null
            
            try {
                val context = getApplication<Application>()
                val visionClient = com.narutoai.chat.api.GroqVisionClient(context)
                
                // Première analyse : description physique
                val physicalResult = visionClient.analyzePhotoForCharacter(imageUri)
                
                if (physicalResult.isSuccess) {
                    val description = physicalResult.getOrNull()
                    
                    if (description != null) {
                        // Remplir automatiquement tous les champs avec normalisation
                        _avatarImageUri.value = imageUri
                        _physicalDescription.value = description.toFormattedDescription()
                        _age.value = description.age
                        _gender.value = normalizeGender(description.gender)
                        _hairColor.value = description.hairColor
                        _eyeColor.value = description.eyeColor
                        _bodyType.value = description.bodyType
                        _breastSize.value = normalizeBreastSize(description.breastSize)
                        _penisSize.value = normalizePenisSize(description.penisSize)
                        _height.value = description.height
                        
                        // Générer un nom basique
                        val normalizedGender = normalizeGender(description.gender)
                        val defaultName = when (normalizedGender) {
                            "Femme" -> "Personnage Féminin"
                            "Homme" -> "Personnage Masculin"
                            else -> "Personnage"
                        }
                        _name.value = defaultName
                        
                        // Générer description courte
                        _description.value = "Un personnage ${description.gender.lowercase()} avec ${description.hairColor} et ${description.eyeColor}"
                        
                        // Générer tempérament par défaut
                        _temperament.value = "Sympathique, ouvert, intéressant"
                        
                        // Message d'accueil générique
                        _greetingMessage.value = "Salut ! Je suis ravi(e) de faire ta connaissance !"
                        
                        _analysisResult.value = "✅ Personnage créé depuis la photo ! Vous pouvez modifier les détails avant de sauvegarder."
                        
                        android.util.Log.d("CreateCharacterVM", "✅ Personnage auto-généré depuis photo")
                    } else {
                        _analysisResult.value = "⚠️ Analyse incomplète"
                        _errorMessage.value = "Impossible d'extraire les informations de la photo"
                    }
                } else {
                    val error = physicalResult.exceptionOrNull()
                    _analysisResult.value = "❌ Échec de l'analyse"
                    _errorMessage.value = "Erreur: ${error?.message ?: "Inconnue"}"
                    android.util.Log.e("CreateCharacterVM", "Erreur création depuis photo: ${error?.message}", error)
                }
                
            } catch (e: Exception) {
                _errorMessage.value = "Erreur de création: ${e.message}"
                _analysisResult.value = "❌ Erreur"
                android.util.Log.e("CreateCharacterVM", "Exception création depuis photo: ${e.message}", e)
            } finally {
                _isAnalyzing.value = false
            }
        }
    }
    
    /**
     * Réinitialise le formulaire
     */
    fun resetForm() {
        _editingCharacterId.value = null
        _name.value = ""
        _description.value = ""
        _physicalDescription.value = ""
        _age.value = ""
        _height.value = ""
        _hairColor.value = ""
        _eyeColor.value = ""
        _bodyType.value = ""
        _gender.value = ""
        _breastSize.value = ""
        _penisSize.value = ""
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
