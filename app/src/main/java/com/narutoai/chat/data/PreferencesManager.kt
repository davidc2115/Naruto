package com.narutoai.chat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension pour créer DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

/**
 * Gestionnaire de préférences utilisateur
 * v2.26.0: Choix d'API de génération (Freebox / Pollination / Stable Horde)
 */
class PreferencesManager(private val context: Context) {
    
    companion object {
        private val GENERATION_API_KEY = stringPreferencesKey("generation_api")
        
        // API de génération
        const val API_POLLINATION = "pollination"
        
        // Valeur par défaut - POLLINATION AI
        const val DEFAULT_API = API_POLLINATION
    }
    
    /**
     * Récupère l'API de génération choisie
     */
    val generationApi: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[GENERATION_API_KEY] ?: DEFAULT_API
    }
    
    /**
     * Définit l'API de génération
     */
    suspend fun setGenerationApi(api: String) {
        context.dataStore.edit { preferences ->
            preferences[GENERATION_API_KEY] = api
        }
    }
    
    /**
     * Récupère l'API de génération de manière synchrone (pour usage immédiat)
     */
    suspend fun getGenerationApiSync(): String {
        var result = DEFAULT_API
        context.dataStore.data.collect { preferences ->
            result = preferences[GENERATION_API_KEY] ?: DEFAULT_API
            return@collect
        }
        return result
    }
}
