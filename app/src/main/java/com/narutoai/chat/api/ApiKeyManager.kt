package com.narutoai.chat.api

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// Extension pour DataStore
private val Context.dataStore by preferencesDataStore(name = "api_keys")

/**
 * Gestionnaire de clés API avec rotation automatique
 * Permet d'ajouter plusieurs clés Groq qui tournent automatiquement
 */
class ApiKeyManager(private val context: Context) {
    
    private val mutex = Mutex()
    private val apiKeys = mutableListOf<String>()
    private val keyUsageCount = ConcurrentHashMap<String, AtomicInteger>()
    private val keyErrorCount = ConcurrentHashMap<String, AtomicInteger>()
    private var currentKeyIndex = 0
    
    companion object {
        private val API_KEYS_KEY = stringPreferencesKey("api_keys")
        private const val KEY_SEPARATOR = "|||"
        private const val MAX_ERRORS_PER_KEY = 3
        
        // Clés par défaut (à remplacer par l'utilisateur)
        private val DEFAULT_KEYS = listOf<String>(
            // L'utilisateur devra ajouter ses clés via l'interface
        )
    }
    
    /**
     * Initialise le gestionnaire avec les clés sauvegardées
     */
    suspend fun initialize() {
        mutex.withLock {
            val savedKeys = loadKeysFromStorage()
            if (savedKeys.isNotEmpty()) {
                apiKeys.addAll(savedKeys)
            } else {
                apiKeys.addAll(DEFAULT_KEYS)
            }
            
            // Initialiser les compteurs
            apiKeys.forEach { key ->
                keyUsageCount[key] = AtomicInteger(0)
                keyErrorCount[key] = AtomicInteger(0)
            }
        }
    }
    
    /**
     * Obtient la clé API courante avec rotation automatique
     */
    suspend fun getCurrentKey(): String? {
        mutex.withLock {
            if (apiKeys.isEmpty()) {
                return null
            }
            
            // Trouver la prochaine clé valide
            var attempts = 0
            while (attempts < apiKeys.size) {
                val key = apiKeys[currentKeyIndex]
                val errors = keyErrorCount[key]?.get() ?: 0
                
                // Si la clé a trop d'erreurs, passer à la suivante
                if (errors < MAX_ERRORS_PER_KEY) {
                    return key
                }
                
                // Passer à la clé suivante
                currentKeyIndex = (currentKeyIndex + 1) % apiKeys.size
                attempts++
            }
            
            // Toutes les clés ont des erreurs, réinitialiser les compteurs
            keyErrorCount.values.forEach { it.set(0) }
            return apiKeys.firstOrNull()
        }
    }
    
    /**
     * Passe à la prochaine clé (appelé en cas d'erreur de rate limit)
     */
    suspend fun rotateToNextKey() {
        mutex.withLock {
            if (apiKeys.size > 1) {
                currentKeyIndex = (currentKeyIndex + 1) % apiKeys.size
            }
        }
    }
    
    /**
     * Signale une erreur pour la clé courante
     */
    suspend fun reportKeyError(key: String) {
        keyErrorCount[key]?.incrementAndGet()
        // Rotation automatique après une erreur
        rotateToNextKey()
    }
    
    /**
     * Signale un succès pour une clé (réinitialise le compteur d'erreurs)
     */
    fun reportKeySuccess(key: String) {
        keyUsageCount[key]?.incrementAndGet()
        keyErrorCount[key]?.set(0)
    }
    
    /**
     * Ajoute une nouvelle clé API
     */
    suspend fun addKey(key: String) {
        mutex.withLock {
            if (key.isNotBlank() && !apiKeys.contains(key)) {
                apiKeys.add(key)
                keyUsageCount[key] = AtomicInteger(0)
                keyErrorCount[key] = AtomicInteger(0)
                saveKeysToStorage()
            }
        }
    }
    
    /**
     * Retire une clé API
     */
    suspend fun removeKey(key: String) {
        mutex.withLock {
            apiKeys.remove(key)
            keyUsageCount.remove(key)
            keyErrorCount.remove(key)
            if (currentKeyIndex >= apiKeys.size) {
                currentKeyIndex = 0
            }
            saveKeysToStorage()
        }
    }
    
    /**
     * Obtient toutes les clés avec leurs statistiques
     */
    suspend fun getAllKeysWithStats(): List<KeyStats> {
        mutex.withLock {
            return apiKeys.mapIndexed { index, key ->
                KeyStats(
                    key = maskKey(key),
                    fullKey = key,
                    usageCount = keyUsageCount[key]?.get() ?: 0,
                    errorCount = keyErrorCount[key]?.get() ?: 0,
                    isActive = index == currentKeyIndex
                )
            }
        }
    }
    
    /**
     * Obtient le nombre de clés disponibles
     */
    fun getKeyCount(): Int = apiKeys.size
    
    /**
     * Masque une clé pour l'affichage (montre seulement les 8 premiers caractères)
     */
    private fun maskKey(key: String): String {
        return if (key.length > 12) {
            "${key.take(8)}...${key.takeLast(4)}"
        } else {
            key
        }
    }
    
    /**
     * Sauvegarde les clés dans le stockage persistant
     */
    private suspend fun saveKeysToStorage() {
        context.dataStore.edit { preferences ->
            preferences[API_KEYS_KEY] = apiKeys.joinToString(KEY_SEPARATOR)
        }
    }
    
    /**
     * Charge les clés depuis le stockage persistant
     */
    private suspend fun loadKeysFromStorage(): List<String> {
        return context.dataStore.data.map { preferences ->
            preferences[API_KEYS_KEY]?.split(KEY_SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
        }.first()
    }
    
    /**
     * Réinitialise tous les compteurs
     */
    suspend fun resetStats() {
        mutex.withLock {
            keyUsageCount.values.forEach { it.set(0) }
            keyErrorCount.values.forEach { it.set(0) }
        }
    }
}

/**
 * Statistiques d'une clé API
 */
data class KeyStats(
    val key: String,           // Clé masquée pour affichage
    val fullKey: String,       // Clé complète
    val usageCount: Int,       // Nombre d'utilisations réussies
    val errorCount: Int,       // Nombre d'erreurs
    val isActive: Boolean      // Est la clé actuellement utilisée
)
