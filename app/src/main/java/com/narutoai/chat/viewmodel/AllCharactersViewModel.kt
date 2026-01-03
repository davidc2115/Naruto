package com.narutoai.chat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.narutoai.chat.data.CustomCharacterDatabase
import com.narutoai.chat.data.CustomCharacterEntity
import com.narutoai.chat.data.CustomCharacterRepository
import com.narutoai.chat.data.Characters
import com.narutoai.chat.models.Character
import com.narutoai.chat.utils.CharacterConverter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Wrapper pour unifier les personnages intégrés et personnalisés
 */
data class UnifiedCharacter(
    val character: Character,
    val isBuiltIn: Boolean, // true = intégré APK, false = personnalisé
    val customEntity: CustomCharacterEntity? = null
)

/**
 * ViewModel pour gérer TOUS les personnages (intégrés + créés)
 */
class AllCharactersViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: CustomCharacterRepository
    
    init {
        val dao = CustomCharacterDatabase.getDatabase(application).customCharacterDao()
        repository = CustomCharacterRepository(dao)
        
        android.util.Log.d("AllCharactersVM", "ViewModel initialisé")
    }
    
    // Liste combinée de tous les personnages
    val allCharacters: StateFlow<List<UnifiedCharacter>> = repository.allCharacters
        .map { customList ->
            // IDs des personnages modifiés dans la BDD
            val modifiedBuiltInIds = customList
                .filter { entity -> Characters.allCharacters.any { it.id == entity.id } }
                .map { it.id }
                .toSet()
            
            val customChars = customList.map { entity ->
                // Si c'est un personnage intégré modifié, marquer comme tel
                val isModifiedBuiltIn = entity.id in modifiedBuiltInIds
                UnifiedCharacter(
                    character = CharacterConverter.toCharacter(entity),
                    isBuiltIn = false, // Toujours false pour les personnages de la BDD
                    customEntity = entity
                )
            }
            
            // Filtrer les personnages intégrés qui ont été modifiés (présents dans la BDD)
            val builtInChars = Characters.allCharacters
                .filter { char -> char.id !in modifiedBuiltInIds } // Exclure si modifié
                .map { char ->
                    UnifiedCharacter(
                        character = char,
                        isBuiltIn = true,
                        customEntity = null
                    )
                }
            
            // Personnages personnalisés en premier, puis intégrés non-modifiés
            val combined = customChars + builtInChars
            
            android.util.Log.d("AllCharactersVM", "📋 Total: ${combined.size} personnages (${customChars.size} BDD + ${builtInChars.size} intégrés, ${modifiedBuiltInIds.size} modifiés)")
            
            combined
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // États
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    /**
     * Supprime un personnage personnalisé
     */
    fun deleteCustomCharacter(entity: CustomCharacterEntity) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                repository.deleteCharacter(entity)
                
                // Supprimer aussi l'image si elle existe
                if (entity.avatarImagePath.isNotEmpty()) {
                    try {
                        val imageFile = java.io.File(entity.avatarImagePath)
                        if (imageFile.exists()) {
                            imageFile.delete()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AllCharactersVM", "Erreur suppression image: ${e.message}")
                    }
                }
                
            } catch (e: Exception) {
                _errorMessage.value = "Erreur de suppression: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Récupère un personnage personnalisé par ID
     */
    suspend fun getCustomCharacterById(id: String): CustomCharacterEntity? {
        return try {
            repository.getCharacterById(id)
        } catch (e: Exception) {
            _errorMessage.value = "Erreur de chargement: ${e.message}"
            null
        }
    }
}
