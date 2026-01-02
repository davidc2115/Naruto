package com.narutoai.chat.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.narutoai.chat.data.CustomCharacterDatabase
import com.narutoai.chat.data.CustomCharacterEntity
import com.narutoai.chat.data.CustomCharacterRepository
import com.narutoai.chat.data.PhysicalDescription
import com.narutoai.chat.models.Character
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray

class EditCharacterViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: CustomCharacterRepository
    
    init {
        val dao = CustomCharacterDatabase.getDatabase(application).customCharacterDao()
        repository = CustomCharacterRepository(dao)
    }
    
    // État du personnage en cours d'édition
    private val _characterId = MutableStateFlow<String?>(null)
    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name
    
    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description
    
    private val _physicalDescription = MutableStateFlow("")
    val physicalDescription: StateFlow<String> = _physicalDescription
    
    private val _age = MutableStateFlow("")
    val age: StateFlow<String> = _age
    
    private val _gender = MutableStateFlow("")
    val gender: StateFlow<String> = _gender
    
    private val _height = MutableStateFlow("")
    val height: StateFlow<String> = _height
    
    private val _hairColor = MutableStateFlow("")
    val hairColor: StateFlow<String> = _hairColor
    
    private val _eyeColor = MutableStateFlow("")
    val eyeColor: StateFlow<String> = _eyeColor
    
    private val _bodyType = MutableStateFlow("")
    val bodyType: StateFlow<String> = _bodyType
    
    private val _bustSize = MutableStateFlow("")
    val bustSize: StateFlow<String> = _bustSize
    
    private val _penisSize = MutableStateFlow("")
    val penisSize: StateFlow<String> = _penisSize
    
    private val _temperament = MutableStateFlow("")
    val temperament: StateFlow<String> = _temperament
    
    private val _scenario = MutableStateFlow("")
    val scenario: StateFlow<String> = _scenario
    
    private val _greetingMessage = MutableStateFlow("")
    val greetingMessage: StateFlow<String> = _greetingMessage
    
    private val _avatarImageUri = MutableStateFlow<Uri?>(null)
    val avatarImageUri: StateFlow<Uri?> = _avatarImageUri
    
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving
    
    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage
    
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing
    
    private val _analysisResult = MutableStateFlow<String?>(null)
    val analysisResult: StateFlow<String?> = _analysisResult
    
    /**
     * Charge un personnage existant pour édition
     */
    fun loadCharacter(characterId: String) {
        viewModelScope.launch {
            try {
                val entity = repository.getCharacterById(characterId)
                if (entity != null) {
                    _characterId.value = entity.id
                    _name.value = entity.name
                    _description.value = entity.description
                    _physicalDescription.value = entity.physicalDescription
                    _age.value = entity.age
                    _gender.value = entity.gender
                    _height.value = entity.height
                    _hairColor.value = entity.hairColor
                    _eyeColor.value = entity.eyeColor
                    _bodyType.value = entity.bodyType
                    _bustSize.value = entity.bustSize
                    _penisSize.value = entity.penisSize
                    _temperament.value = entity.temperament
                    _scenario.value = entity.scenario
                    _greetingMessage.value = entity.greetingMessage
                    
                    // Charger l'image si elle existe
                    if (entity.avatarImagePath.isNotBlank()) {
                        _avatarImageUri.value = Uri.parse(entity.avatarImagePath)
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Erreur lors du chargement: ${e.message}"
            }
        }
    }
    
    /**
     * Charge un personnage pré-défini pour créer une copie éditable
     */
    fun loadPredefinedCharacter(character: Character) {
        // Créer un nouvel ID pour la copie
        _characterId.value = java.util.UUID.randomUUID().toString()
        _name.value = character.name + " (Copie)"
        _description.value = character.description
        _physicalDescription.value = character.physicalDescription
        _age.value = character.age
        _height.value = character.height
        _hairColor.value = character.hairColor
        _eyeColor.value = character.eyeColor
        _bodyType.value = character.bodyType
        _temperament.value = character.temperament
        _scenario.value = character.scenario
        _greetingMessage.value = character.greetingMessage
    }
    
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
    fun updateAvatarImage(uri: Uri?) { _avatarImageUri.value = uri }
    
    fun resetSaveSuccess() {
        _saveSuccess.value = false
    }
    
    /**
     * Analyse automatique de la photo
     */
    fun analyzePhoto() {
        if (_avatarImageUri.value == null) {
            _errorMessage.value = "Aucune photo sélectionnée"
            return
        }
        
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisResult.value = "🔍 Analyse avec Groq Vision AI..."
            _errorMessage.value = null
            
            try {
                val context = getApplication<Application>()
                val visionClient = com.narutoai.chat.api.GroqVisionClient(context)
                
                val result = visionClient.analyzePhotoForCharacter(_avatarImageUri.value!!)
                
                if (result.isSuccess) {
                    val description = result.getOrNull()
                    
                    if (description != null) {
                        _physicalDescription.value = description.toFormattedDescription()
                        _age.value = description.age
                        _hairColor.value = description.hairColor
                        _eyeColor.value = description.eyeColor
                        _bodyType.value = description.bodyType
                        _height.value = description.height
                        
                        _analysisResult.value = "✅ Analyse terminée avec succès !"
                        android.util.Log.d("EditCharacterVM", "✨ Analyse Groq réussie")
                    } else {
                        _analysisResult.value = "⚠️ Analyse incomplète"
                        _errorMessage.value = "L'analyse n'a pas pu extraire toutes les informations"
                    }
                } else {
                    val error = result.exceptionOrNull()
                    _analysisResult.value = "❌ Échec de l'analyse"
                    _errorMessage.value = "Erreur: ${error?.message ?: "Inconnue"}"
                }
                
            } catch (e: Exception) {
                _errorMessage.value = "Erreur d'analyse: ${e.message}"
                _analysisResult.value = "❌ Erreur d'analyse"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }
    
    /**
     * Sauvegarde les modifications
     */
    fun saveCharacter() {
        if (_name.value.isBlank() || _description.value.isBlank()) {
            _errorMessage.value = "Nom et description sont obligatoires"
            return
        }
        
        viewModelScope.launch {
            _isSaving.value = true
            _errorMessage.value = null
            
            try {
                val characterId = _characterId.value ?: java.util.UUID.randomUUID().toString()
                
                // Générer les system prompts
                val systemPromptSFW = generateSystemPrompt(false)
                val systemPromptNSFW = generateSystemPrompt(true)
                
                // Créer l'entité
                val entity = CustomCharacterEntity(
                    id = characterId,
                    name = _name.value,
                    description = _description.value,
                    systemPromptSFW = systemPromptSFW,
                    systemPromptNSFW = systemPromptNSFW,
                    avatarImagePath = _avatarImageUri.value?.toString() ?: "",
                    personality = JSONArray(listOf(_temperament.value)).toString(),
                    physicalDescription = _physicalDescription.value,
                    age = _age.value,
                    gender = _gender.value,
                    height = _height.value,
                    hairColor = _hairColor.value,
                    eyeColor = _eyeColor.value,
                    bodyType = _bodyType.value,
                    bustSize = _bustSize.value,
                    penisSize = _penisSize.value,
                    scenario = _scenario.value,
                    temperament = _temperament.value,
                    greetingMessage = _greetingMessage.value
                )
                
                repository.insertCharacter(entity)
                
                _saveSuccess.value = true
                
            } catch (e: Exception) {
                _errorMessage.value = "Erreur lors de la sauvegarde: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }
    
    private fun generateSystemPrompt(isNSFW: Boolean): String {
        return buildString {
            append("Tu es ${_name.value}. ")
            append(_description.value)
            
            if (_physicalDescription.value.isNotBlank()) {
                append("\n\nApparence physique: ")
                append(_physicalDescription.value)
            }
            
            if (_temperament.value.isNotBlank()) {
                append("\n\nTempérament: ")
                append(_temperament.value)
            }
            
            if (_scenario.value.isNotBlank()) {
                append("\n\nContexte: ")
                append(_scenario.value)
            }
            
            if (isNSFW) {
                append("\n\nTu peux avoir des conversations intimes et sensuelles.")
            } else {
                append("\n\nTu restes respectueux et évites le contenu explicite.")
            }
            
            append("\n\nRéponds toujours en restant dans ton rôle.")
        }
    }
}
