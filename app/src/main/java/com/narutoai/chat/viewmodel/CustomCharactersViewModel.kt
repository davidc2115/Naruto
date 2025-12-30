package com.narutoai.chat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.narutoai.chat.data.CustomCharacterDatabase
import com.narutoai.chat.data.CustomCharacterEntity
import com.narutoai.chat.data.CustomCharacterRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel pour la liste des personnages personnalisés
 */
class CustomCharactersViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: CustomCharacterRepository
    
    init {
        val dao = CustomCharacterDatabase.getDatabase(application).customCharacterDao()
        repository = CustomCharacterRepository(dao)
    }
    
    // Liste des personnages
    val characters: StateFlow<List<CustomCharacterEntity>> = repository.allCharacters
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
     * Supprime un personnage
     */
    fun deleteCharacter(character: CustomCharacterEntity) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                repository.deleteCharacter(character)
                
                // Supprimer aussi l'image si elle existe
                if (character.avatarImagePath.isNotEmpty()) {
                    try {
                        val imageFile = java.io.File(character.avatarImagePath)
                        if (imageFile.exists()) {
                            imageFile.delete()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("CustomCharactersVM", "Erreur suppression image: ${e.message}")
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
     * Récupère un personnage par ID
     */
    suspend fun getCharacterById(id: String): CustomCharacterEntity? {
        return try {
            repository.getCharacterById(id)
        } catch (e: Exception) {
            _errorMessage.value = "Erreur de chargement: ${e.message}"
            null
        }
    }
    
    /**
     * Compte total de personnages
     */
    suspend fun getCharacterCount(): Int {
        return try {
            repository.getCharacterCount()
        } catch (e: Exception) {
            0
        }
    }
}
