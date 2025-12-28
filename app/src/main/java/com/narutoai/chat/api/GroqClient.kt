package com.narutoai.chat.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client Groq avec support multi-clés et rotation automatique
 * Remplace l'ancien LlamaClient qui utilisait Freebox
 */
class GroqClient(context: Context) {
    
    private val apiKeyManager = ApiKeyManager(context)
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    companion object {
        private const val GROQ_BASE_URL = "https://api.groq.com/openai/v1"
        private const val DEFAULT_MODEL = "llama-3.3-70b-versatile"
        private const val RATE_LIMIT_CODE = 429
        private const val UNAUTHORIZED_CODE = 401
    }
    
    /**
     * Initialise le client (charge les clés API)
     */
    suspend fun initialize() {
        apiKeyManager.initialize()
    }
    
    /**
     * Envoie un message et reçoit une réponse
     * @param systemPrompt Le prompt système (personnalité du personnage)
     * @param userMessage Le message de l'utilisateur
     * @param conversationHistory L'historique de conversation pour le contexte
     * @return La réponse de l'IA
     */
    suspend fun chat(
        systemPrompt: String,
        userMessage: String,
        conversationHistory: List<Pair<String, String>> = emptyList(),
        temperature: Double = 0.8,
        maxTokens: Int = 500
    ): Result<String> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        
        // Essayer avec chaque clé disponible
        repeat(apiKeyManager.getKeyCount().coerceAtLeast(1)) {
            val apiKey = apiKeyManager.getCurrentKey()
            if (apiKey == null) {
                return@withContext Result.failure(
                    Exception("Aucune clé API configurée. Veuillez ajouter au moins une clé Groq dans les paramètres.")
                )
            }
            
            try {
                val messages = JSONArray()
                
                // Ajouter le prompt système
                messages.put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                
                // Ajouter l'historique de conversation (limité pour performance)
                conversationHistory.takeLast(10).forEach { (role, content) ->
                    messages.put(JSONObject().apply {
                        put("role", role)
                        put("content", content)
                    })
                }
                
                // Ajouter le message actuel
                messages.put(JSONObject().apply {
                    put("role", "user")
                    put("content", userMessage)
                })
                
                val jsonBody = JSONObject().apply {
                    put("model", DEFAULT_MODEL)
                    put("messages", messages)
                    put("temperature", temperature)
                    put("max_tokens", maxTokens)
                    put("top_p", 0.9)
                    put("stream", false)
                }
                
                val request = Request.Builder()
                    .url("$GROQ_BASE_URL/chat/completions")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                
                client.newCall(request).execute().use { response ->
                    when (response.code) {
                        RATE_LIMIT_CODE -> {
                            // Rate limit atteint, passer à la clé suivante
                            apiKeyManager.reportKeyError(apiKey)
                            lastException = IOException("Rate limit atteint pour cette clé, rotation vers la suivante...")
                            return@use // Continue avec la prochaine clé
                        }
                        UNAUTHORIZED_CODE -> {
                            // Clé invalide
                            apiKeyManager.reportKeyError(apiKey)
                            lastException = IOException("Clé API invalide")
                            return@use
                        }
                        in 200..299 -> {
                            // Succès
                            val responseBody = response.body?.string()
                                ?: return@withContext Result.failure(IOException("Réponse vide"))
                            
                            val jsonResponse = JSONObject(responseBody)
                            val content = jsonResponse
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")
                            
                            apiKeyManager.reportKeySuccess(apiKey)
                            return@withContext Result.success(content.trim())
                        }
                        else -> {
                            lastException = IOException("Erreur HTTP ${response.code}: ${response.message}")
                            return@use
                        }
                    }
                }
            } catch (e: Exception) {
                lastException = e
                apiKeyManager.reportKeyError(apiKey)
            }
        }
        
        // Si toutes les clés ont échoué
        Result.failure(lastException ?: Exception("Erreur inconnue"))
    }
    
    /**
     * Teste la connexion avec l'API Groq
     */
    suspend fun ping(): Result<Boolean> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyManager.getCurrentKey()
            ?: return@withContext Result.failure(Exception("Aucune clé API configurée"))
        
        try {
            val request = Request.Builder()
                .url("$GROQ_BASE_URL/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    apiKeyManager.reportKeySuccess(apiKey)
                    Result.success(true)
                } else {
                    Result.failure(IOException("Échec du test: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Obtient le gestionnaire de clés API
     */
    fun getKeyManager(): ApiKeyManager = apiKeyManager
}
