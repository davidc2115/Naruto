package com.narutoai.chat.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.narutoai.chat.models.ChatMessage

/**
 * Gestionnaire de sauvegarde et chargement des conversations
 * Utilise SharedPreferences + Gson pour la persistance
 */
class ConversationManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "conversations",
        Context.MODE_PRIVATE
    )
    private val gson = Gson()
    
    /**
     * Sauvegarde une conversation pour un personnage
     */
    fun saveConversation(characterId: String, messages: List<ChatMessage>, isNSFW: Boolean) {
        val json = gson.toJson(messages)
        prefs.edit().apply {
            putString("messages_$characterId", json)
            putBoolean("nsfw_$characterId", isNSFW)
            putLong("timestamp_$characterId", System.currentTimeMillis())
            apply()
        }
    }
    
    /**
     * Charge une conversation pour un personnage
     */
    fun loadConversation(characterId: String): List<ChatMessage>? {
        val json = prefs.getString("messages_$characterId", null) ?: return null
        return try {
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            gson.fromJson<List<ChatMessage>>(json, type)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Vérifie si une conversation existe
     */
    fun hasConversation(characterId: String): Boolean {
        return prefs.contains("messages_$characterId")
    }
    
    /**
     * Supprime une conversation
     */
    fun deleteConversation(characterId: String) {
        prefs.edit().apply {
            remove("messages_$characterId")
            remove("nsfw_$characterId")
            remove("timestamp_$characterId")
            apply()
        }
    }
    
    /**
     * Récupère le mode NSFW sauvegardé
     */
    fun getIsNSFW(characterId: String): Boolean {
        return prefs.getBoolean("nsfw_$characterId", false)
    }
    
    /**
     * Récupère le timestamp de la dernière sauvegarde
     */
    fun getLastSaveTime(characterId: String): Long {
        return prefs.getLong("timestamp_$characterId", 0)
    }
    
    /**
     * Liste tous les personnages avec conversations sauvegardées
     */
    fun getAllConversationIds(): List<String> {
        return prefs.all.keys
            .filter { it.startsWith("messages_") }
            .map { it.removePrefix("messages_") }
    }
}
