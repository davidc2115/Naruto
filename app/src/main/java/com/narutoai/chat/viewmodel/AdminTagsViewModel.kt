package com.narutoai.chat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel pour l'écran d'administration des tags
 */
class AdminTagsViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        // Mot de passe administrateur (à configurer)
        // Pour plus de sécurité, stockez-le en SharedPreferences chiffré
        private const val ADMIN_PASSWORD = "bagbot"  // Même mot de passe que paramètres
    }
    
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    /**
     * Authentifier l'utilisateur
     */
    fun authenticate(password: String) {
        if (password == ADMIN_PASSWORD) {
            _isAuthenticated.value = true
            _errorMessage.value = null
            android.util.Log.d("AdminTagsVM", "✅ Authentification réussie")
        } else {
            _errorMessage.value = "Mot de passe incorrect"
            android.util.Log.w("AdminTagsVM", "❌ Mot de passe incorrect")
        }
    }
    
    /**
     * Déconnecter l'utilisateur
     */
    fun logout() {
        _isAuthenticated.value = false
        _errorMessage.value = null
        android.util.Log.d("AdminTagsVM", "👋 Déconnexion")
    }
    
    /**
     * Ajouter un tag à un personnage
     */
    fun addTag(characterId: String, key: String, value: String) {
        // TODO: Implémenter la sauvegarde des tags
        // Option 1: Modifier Characters.kt (statique)
        // Option 2: Créer une table de tags en Room
        // Option 3: Utiliser SharedPreferences JSON
        
        android.util.Log.d("AdminTagsVM", "Ajout tag: $characterId -> $key = $value")
    }
    
    /**
     * Supprimer un tag d'un personnage
     */
    fun removeTag(characterId: String, key: String) {
        // TODO: Implémenter la suppression des tags
        android.util.Log.d("AdminTagsVM", "Suppression tag: $characterId -> $key")
    }
    
    /**
     * Récupérer les tags d'un personnage
     */
    fun getTags(characterId: String): Map<String, String> {
        // TODO: Implémenter la récupération des tags
        return emptyMap()
    }
}
