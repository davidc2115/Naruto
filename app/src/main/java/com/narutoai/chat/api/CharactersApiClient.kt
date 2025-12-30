package com.narutoai.chat.api

import android.content.Context
import android.util.Log
import com.narutoai.chat.models.Character
import com.narutoai.chat.models.CharacterCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client pour API centralisée des personnages
 * Serveur: Freebox (88.174.155.230:33500)
 */
class CharactersApiClient(private val context: Context) {
    
    companion object {
        private const val TAG = "CharactersApiClient"
        
        // API URL
        private const val BASE_URL = "http://88.174.155.230:33500"
        private const val API_CHARACTERS = "$BASE_URL/api/characters"
        private const val API_STATS = "$BASE_URL/api/stats"
        
        private const val TIMEOUT_SECONDS = 30L
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    /**
     * Récupère tous les personnages depuis l'API
     */
    suspend fun getAllCharacters(): Result<List<Character>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching characters from API...")
            
            val request = Request.Builder()
                .url(API_CHARACTERS)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "API error: ${response.code}")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            
            val body = response.body?.string()
            response.close()
            
            if (body == null) {
                Log.e(TAG, "Empty response")
                return@withContext Result.failure(Exception("Empty response"))
            }
            
            // Parse JSON
            val json = JSONObject(body)
            val success = json.optBoolean("success", false)
            
            if (!success) {
                Log.e(TAG, "API returned success=false")
                return@withContext Result.failure(Exception("API error"))
            }
            
            val charactersArray = json.getJSONArray("characters")
            val characters = parseCharactersArray(charactersArray)
            
            Log.d(TAG, "✅ Fetched ${characters.size} characters")
            Result.success(characters)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching characters", e)
            Result.failure(e)
        }
    }
    
    /**
     * Récupère un personnage spécifique
     */
    suspend fun getCharacter(id: String): Result<Character> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching character: $id")
            
            val request = Request.Builder()
                .url("$API_CHARACTERS/$id")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "API error: ${response.code}")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            
            val body = response.body?.string()
            response.close()
            
            if (body == null) {
                return@withContext Result.failure(Exception("Empty response"))
            }
            
            val json = JSONObject(body)
            val success = json.optBoolean("success", false)
            
            if (!success) {
                return@withContext Result.failure(Exception("Character not found"))
            }
            
            val characterJson = json.getJSONObject("character")
            val character = parseCharacter(characterJson)
            
            Log.d(TAG, "✅ Fetched character: ${character.name}")
            Result.success(character)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching character: $id", e)
            Result.failure(e)
        }
    }
    
    /**
     * Ajoute un personnage personnalisé sur l'API
     */
    suspend fun addCharacter(character: Character): Result<Character> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Adding character: ${character.name}")
            
            val json = characterToJson(character)
            val body = json.toString().toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(API_CHARACTERS)
                .post(body)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "API error: ${response.code}")
                val errorBody = response.body?.string()
                Log.e(TAG, "Error: $errorBody")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            
            val responseBody = response.body?.string()
            response.close()
            
            if (responseBody == null) {
                return@withContext Result.failure(Exception("Empty response"))
            }
            
            val responseJson = JSONObject(responseBody)
            val success = responseJson.optBoolean("success", false)
            
            if (!success) {
                return@withContext Result.failure(Exception("Failed to add character"))
            }
            
            Log.d(TAG, "✅ Character added: ${character.name}")
            Result.success(character)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error adding character", e)
            Result.failure(e)
        }
    }
    
    /**
     * Parse tableau de personnages JSON
     */
    private fun parseCharactersArray(array: JSONArray): List<Character> {
        val characters = mutableListOf<Character>()
        
        for (i in 0 until array.length()) {
            try {
                val json = array.getJSONObject(i)
                characters.add(parseCharacter(json))
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing character at index $i", e)
            }
        }
        
        return characters
    }
    
    /**
     * Parse un personnage JSON en objet Character
     */
    private fun parseCharacter(json: JSONObject): Character {
        return Character(
            id = json.getString("id"),
            name = json.getString("name"),
            description = json.getString("description"),
            category = parseCategoryString(json.optString("category", "custom")),
            systemPromptSFW = json.optString("systemPromptSFW", ""),
            systemPromptNSFW = json.optString("systemPromptNSFW", ""),
            avatarEmoji = "👤",
            personality = parseJsonArray(json.optJSONArray("personality")),
            imageUrl = json.optString("avatarUrl", ""),
            imageResId = 0,
            
            // Physique
            physicalDescription = json.optString("physicalDescription", ""),
            age = json.optString("age", "18+"),
            height = json.optString("height", ""),
            hairColor = json.optString("hairColor", ""),
            eyeColor = json.optString("eyeColor", ""),
            bodyType = json.optString("bodyType", ""),
            distinctiveFeatures = parseJsonArray(json.optJSONArray("distinctiveFeatures")),
            
            // Background
            scenario = "",
            backgroundStory = "",
            temperament = "",
            characterTraits = parseJsonArray(json.optJSONArray("personality")),
            likes = emptyList(),
            dislikes = emptyList(),
            skills = emptyList(),
            
            // Galeries
            gallery = emptyList(),
            galleryNSFW = parseJsonArray(json.optJSONArray("galleryNSFW")),
            thumbnailUrl = json.optString("avatarUrl", ""),
            greetingMessage = json.optString("greetingMessage", "Bonjour !")
        )
    }
    
    /**
     * Convertit Character en JSON
     */
    private fun characterToJson(character: Character): JSONObject {
        return JSONObject().apply {
            put("id", character.id)
            put("name", character.name)
            put("description", character.description)
            put("category", categoryToString(character.category))
            put("age", character.age)
            put("personality", JSONArray(character.personality))
            put("physicalDescription", character.physicalDescription)
            put("height", character.height)
            put("hairColor", character.hairColor)
            put("eyeColor", character.eyeColor)
            put("bodyType", character.bodyType)
            put("avatarUrl", character.imageUrl)
            put("galleryNSFW", JSONArray(character.galleryNSFW))
            put("systemPromptSFW", character.systemPromptSFW)
            put("systemPromptNSFW", character.systemPromptNSFW)
            put("greetingMessage", character.greetingMessage)
        }
    }
    
    /**
     * Parse JSONArray en List<String>
     */
    private fun parseJsonArray(jsonArray: JSONArray?): List<String> {
        if (jsonArray == null) return emptyList()
        
        val list = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getString(i))
        }
        return list
    }
    
    /**
     * Parse catégorie string
     */
    private fun parseCategoryString(str: String): CharacterCategory {
        return when (str.lowercase()) {
            "celebrity_male" -> CharacterCategory.CELEBRITY_MALE
            "celebrity_female" -> CharacterCategory.CELEBRITY_FEMALE
            "custom" -> CharacterCategory.CELEBRITY_MALE // Default
            else -> CharacterCategory.CELEBRITY_MALE
        }
    }
    
    /**
     * Convertit catégorie en string
     */
    private fun categoryToString(category: CharacterCategory): String {
        return when (category) {
            CharacterCategory.CELEBRITY_MALE -> "celebrity_male"
            CharacterCategory.CELEBRITY_FEMALE -> "celebrity_female"
            else -> "custom"
        }
    }
}
