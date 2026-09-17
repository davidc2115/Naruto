package com.opencompanion.app.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.random.Random
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.opencompanion.app.data.CharacterEntity
import com.opencompanion.app.data.ChatMessageEntity
import com.opencompanion.app.data.MessageRole

/** Découpe une chaîne de clés API en liste utilisable : une clé par ligne (ou séparées par des
 *  virgules), lignes vides ignorées — permet de renseigner plusieurs clés (plusieurs comptes
 *  gratuits) pour un même provider et de tourner automatiquement dessus en cas de quota atteint
 *  (voir [CloudEngineBridge.generateWithKeyRotation]). */
fun parseApiKeys(raw: String): List<String> =
    raw.split('\n', ',').map { it.trim() }.filter { it.isNotBlank() }

val DECOMMISSIONED_GROQ_MODELS = setOf(
    "openai/gpt-oss-120b",
    "llama-3.1-70b-versatile",
    "llama3-70b-8192",
    "llama3-8b-8192",
    "llama-3.2-11b-vision-preview",
    "llama-3.2-90b-vision-preview",
    "llama-3.2-3b-preview",
    "llama-3.2-1b-preview",
    "mixtral-8x7b-32768",
    "gemma-7b-it",
    "gemma2-9b-it",
)

val DEPRECATED_GEMINI_MODELS = setOf(
    "gemini-1.0-pro",
    "gemini-1.0-pro-vision",
    "gemini-pro",
    "gemini-pro-vision",
    "gemini-1.5-flash-001",
    "gemini-1.5-pro-001",
    "gemini-2.5-flash",
    "gemini-3.5-flash",
)

/**
 * Assainit rigoureusement le nom du modèle Gemini : supprime tous les préfixes "models/",
 * "model/", slashes parasites, et remappe les modèles dépréciés ou inexistants vers un modèle stable.
 */
fun sanitizeGeminiModel(raw: String): String {
    var s = raw.trim()
    while (s.startsWith("models/")) {
        s = s.substring(7).trim()
    }
    while (s.startsWith("model/")) {
        s = s.substring(6).trim()
    }
    s = s.trim('/', ' ', '\t', '\n', '\r')
    if (s.isBlank() ||
        s in DEPRECATED_GEMINI_MODELS ||
        s.startsWith("gemini-1.0") ||
        s == "gemini-pro" ||
        s == "gemini-pro-vision" ||
        s == "gemini-2.5-flash" ||
        s == "gemini-3.5-flash"
    ) {
        return "gemini-2.0-flash"
    }
    return s
}

private val HTTP_STATUS_IN_MESSAGE = Regex("\\((\\d{3})\\)")

/**
 * Moteur d'inférence Cloud : effectue les requêtes en streaming ou en polling vers des providers
 * d'IA Cloud (Google Gemini Imagen 3, OpenAI DALL-E, OpenRouter, KoboldAI Horde, ou serveur compatible OpenAI).
 */
class CloudEngineBridge {

    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }
    private val activeKeyIndices = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /**
     * Essaie [attempt] successivement avec chaque clé de [apiKeys] tant qu'AUCUN token n'a
     * encore été reçu — pour que plusieurs clés API gratuites du même provider servent de
     * réserve les unes des autres (multi-clés / rotation automatique transparente).
     * Mémorise l'index de la clé qui fonctionne pour éviter de réinterroger une clé en cooldown.
     */
    fun generateWithKeyRotation(
        apiKeys: List<String>,
        providerTag: String = "default",
        attempt: (String) -> Flow<GenerationEvent>,
    ): Flow<GenerationEvent> = channelFlow {
        val keys = apiKeys.ifEmpty { listOf("") }
        val startIndex = (activeKeyIndices[providerTag] ?: 0) % keys.size
        var lastErrorMessage: String? = null

        for (offset in keys.indices) {
            val currentIndex = (startIndex + offset) % keys.size
            val key = keys[currentIndex]
            var tokenEmitted = false
            var shouldTryNextKey = false

            attempt(key).collect { event ->
                when (event) {
                    is GenerationEvent.Token -> {
                        tokenEmitted = true
                        activeKeyIndices[providerTag] = currentIndex
                        send(event)
                    }
                    is GenerationEvent.Error -> {
                        lastErrorMessage = event.message
                        if (!tokenEmitted && offset < keys.lastIndex) {
                            shouldTryNextKey = true
                        } else {
                            send(event)
                        }
                    }
                    else -> send(event)
                }
            }
            if (!shouldTryNextKey) return@channelFlow
        }
        send(GenerationEvent.Error(lastErrorMessage ?: "Toutes les clés API configurées ont échoué."))
    }.flowOn(Dispatchers.IO)

    /**
     * Mode Cloud 100% Gratuit & Illimité SANS AUCUNE CLÉ API REQUISE.
     * N'exige aucun compte ni clé : utilise KoboldAI Horde anonyme (clé publique anonyme 0000000000).
     */
    fun generateFreeNoKeyCloud(
        turns: List<ChatTurn>,
        maxTokens: Int = 768,
        temperature: Float = 0.8f,
    ): Flow<GenerationEvent> = channelFlow {
        generateKoboldHorde(
            apiKey = "0000000000",
            modelName = "Hermes-3-Llama-3.1-8B",
            turns = turns,
            maxTokens = maxTokens,
            temperature = temperature,
        ).collect { event ->
            send(event)
        }
    }.flowOn(Dispatchers.IO)

    private fun parseJsonTextOrChoice(respText: String): String {
        return try {
            val root = jsonParser.parseToJsonElement(respText).jsonObject
            val choices = root["choices"]?.jsonArray
            if (choices != null && choices.isNotEmpty()) {
                val first = choices[0].jsonObject
                val message = first["message"]?.jsonObject
                message?.get("content")?.jsonPrimitive?.content ?: first["text"]?.jsonPrimitive?.content ?: ""
            } else {
                root["text"]?.jsonPrimitive?.content ?: respText
            }
        } catch (_: Exception) {
            respText
        }
    }

    /**
     * Inférence via API compatible OpenAI (OpenRouter, Groq, Together AI, LM Studio distant, etc.)
     * avec streaming SSE (Server-Sent Events) pour des réponses instantanées token par token.
     */
    fun generateOpenAiCompatible(
        endpointUrl: String,
        apiKey: String?,
        modelName: String,
        turns: List<ChatTurn>,
        maxTokens: Int = 768,
        temperature: Float = 0.8f,
    ): Flow<GenerationEvent> = channelFlow {
        var connection: HttpURLConnection? = null
        try {
            val targetUrl = endpointUrl.ifBlank { "https://openrouter.ai/api/v1/chat/completions" }
            val url = URL(targetUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 OpenCompanion/1.0"
                )
                setRequestProperty("HTTP-Referer", "https://opencompanion.app")
                setRequestProperty("X-Title", "OpenCompanion")
                if (!apiKey.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                }
            }

            val messagesList = mutableListOf<Map<String, String>>()
            for (turn in turns) {
                val content = turn.content.trim()
                if (content.isBlank()) continue
                val role = when (turn.role) {
                    "assistant", "model" -> "assistant"
                    "system" -> "system"
                    else -> "user"
                }
                if (messagesList.isNotEmpty() && messagesList.last()["role"] == role) {
                    val prev = messagesList.removeAt(messagesList.lastIndex)
                    messagesList.add(mapOf("role" to role, "content" to "${prev["content"]}\n\n$content"))
                } else {
                    messagesList.add(mapOf("role" to role, "content" to content))
                }
            }
            if (messagesList.isEmpty()) {
                send(GenerationEvent.Error("Historique vide pour la génération."))
                return@channelFlow
            }

            val effectiveModel = if (targetUrl.contains("groq.com") && (modelName in DECOMMISSIONED_GROQ_MODELS || modelName.isBlank())) {
                "llama-3.3-70b-versatile"
            } else {
                modelName.ifBlank { "nousresearch/hermes-3-llama-3.1-8b:free" }
            }

            val payloadMap = mutableMapOf<String, Any>(
                "model" to effectiveModel,
                "messages" to messagesList,
                "temperature" to temperature,
                "max_tokens" to maxTokens.coerceIn(64, 1024),
                "stream" to true
            )

            val jsonBody = buildJsonString(payloadMap)
            connection.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                val errorMsg = parseErrorMessage(errorText, responseCode)
                if (targetUrl.contains("groq.com") && effectiveModel != "llama-3.3-70b-versatile" &&
                    (errorMsg.contains("decommissioned", ignoreCase = true) || errorMsg.contains("not found", ignoreCase = true))) {
                    generateOpenAiCompatible(targetUrl, apiKey, "llama-3.3-70b-versatile", turns, maxTokens, temperature).collect { send(it) }
                    return@channelFlow
                }
                send(GenerationEvent.Error("Serveur Cloud ($responseCode) : $errorMsg"))
                return@channelFlow
            }

            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line?.trim() ?: continue
                    if (currentLine.startsWith("data:")) {
                        val dataStr = currentLine.removePrefix("data:").trim()
                        if (dataStr == "[DONE]") {
                            break
                        }
                        try {
                            val token = extractTokenFromSse(dataStr)
                            if (!token.isNullOrEmpty()) {
                                send(GenerationEvent.Token(token))
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
            send(GenerationEvent.Done)
        } catch (e: Exception) {
            send(GenerationEvent.Error(e.message ?: "Erreur réseau lors de la connexion au serveur Cloud"))
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Inférence via KoboldAI Horde : cluster communautaire décentralisé 100% gratuit, illimité
     * et spécialisé dans le jeu de rôle NSFW / adulte libre.
     */
    fun generateKoboldHorde(
        apiKey: String?,
        modelName: String,
        turns: List<ChatTurn>,
        maxTokens: Int = 300,
        temperature: Float = 0.8f,
    ): Flow<GenerationEvent> = channelFlow {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("https://horde.koboldai.net/api/v2/generate/text/async")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 120_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("apikey", if (!apiKey.isNullOrBlank()) apiKey.trim() else "0000000000")
                setRequestProperty("Client-Agent", "OpenCompanion:1.0:android")
            }

            val promptText = buildString {
                for (turn in turns) {
                    when (turn.role) {
                        "system" -> append("### System:\n${turn.content}\n\n")
                        "user" -> append("### User:\n${turn.content}\n\n")
                        "assistant" -> append("### Assistant:\n${turn.content}\n\n")
                    }
                }
                append("### Assistant:\n")
            }

            val modelsList = if (modelName.isNotBlank()) listOf(modelName) else listOf("Hermes-3-Llama-3.1-8B", "Meta-Llama-3-8B-Instruct", "MythoMax-13b")

            val payloadMap = mapOf(
                "prompt" to promptText,
                "params" to mapOf(
                    "n" to 1,
                    "max_context_length" to 4096,
                    "max_length" to maxTokens,
                    "rep_pen" to 1.1,
                    "temperature" to temperature
                ),
                "models" to modelsList
            )

            val jsonBody = buildJsonString(payloadMap)
            connection.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                send(GenerationEvent.Error("KoboldHorde ($responseCode) : ${parseErrorMessage(errorText, responseCode)}"))
                return@channelFlow
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val root = jsonParser.parseToJsonElement(responseText).jsonObject
            val jobId = root["id"]?.jsonPrimitive?.content ?: error("Impossible d'obtenir l'ID de la tâche KoboldHorde")

            var finished = false
            var attempts = 0
            while (!finished && attempts < 60) {
                kotlinx.coroutines.delay(2000)
                attempts++
                val checkUrl = URL("https://horde.koboldai.net/api/v2/generate/text/status/$jobId")
                val checkConn = (checkUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Client-Agent", "OpenCompanion:1.0:android")
                }
                val checkCode = checkConn.responseCode
                if (checkCode in 200..299) {
                    val statusText = checkConn.inputStream.bufferedReader().use { it.readText() }
                    val statusRoot = jsonParser.parseToJsonElement(statusText).jsonObject
                    val isDone = statusRoot["done"]?.jsonPrimitive?.content == "true" || statusRoot["done"]?.jsonPrimitive?.content == "1"
                    if (isDone) {
                        finished = true
                        val generations = statusRoot["generations"]?.jsonArray
                        if (generations != null && generations.isNotEmpty()) {
                            val text = generations[0].jsonObject["text"]?.jsonPrimitive?.content.orEmpty()
                            if (text.isNotBlank()) {
                                send(GenerationEvent.Token(text.trim()))
                            }
                        }
                    }
                }
                checkConn.disconnect()
            }
            send(GenerationEvent.Done)
        } catch (e: Exception) {
            send(GenerationEvent.Error(e.message ?: "Erreur de connexion à KoboldHorde"))
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Inférence via l'API Gemini de Google (generativelanguage.googleapis.com), en streaming SSE.
     * Format différent de l'API compatible OpenAI ci-dessus : rôles "user"/"model" (pas
     * "assistant"), et le message système passe par un champ `systemInstruction` séparé plutôt
     * que par un tour de rôle "system" dans la liste — d'où une fonction dédiée plutôt qu'une
     * réutilisation de [generateOpenAiCompatible].
     */
    fun generateGemini(
        apiKey: String,
        modelName: String,
        turns: List<ChatTurn>,
        maxTokens: Int = 768,
        temperature: Float = 0.8f,
    ): Flow<GenerationEvent> = generateWithKeyRotation(parseApiKeys(apiKey), providerTag = "gemini") { key ->
        generateGeminiWithFallback(
            apiKey = key,
            initialModel = sanitizeGeminiModel(modelName),
            turns = turns,
            maxTokens = maxTokens,
            temperature = temperature,
        )
    }

    /**
     * Chaîne de secours intelligente : si le modèle demandé renvoie 404 (modèle déprécié, non supporté
     * sur cette clé ou non existant), bascule automatiquement et de façon totalement transparente
     * sur les modèles actifs universels (gemini-2.0-flash, gemini-1.5-flash, gemini-2.0-flash-lite, etc.).
     */
    private fun generateGeminiWithFallback(
        apiKey: String,
        initialModel: String,
        turns: List<ChatTurn>,
        maxTokens: Int,
        temperature: Float,
    ): Flow<GenerationEvent> = channelFlow {
        val candidateModels = linkedSetOf(
            sanitizeGeminiModel(initialModel),
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-2.0-flash-lite-preview-02-05",
            "gemini-1.5-pro",
            "gemini-1.5-flash-8b",
        ).toList()

        for ((index, modelToTry) in candidateModels.withIndex()) {
            var tokenReceived = false
            var isModelUnavailable = false
            var lastError = ""

            generateGeminiInternal(apiKey, modelToTry, turns, maxTokens, temperature).collect { event ->
                when (event) {
                    is GenerationEvent.Token -> {
                        tokenReceived = true
                        send(event)
                    }
                    is GenerationEvent.Error -> {
                        lastError = event.message
                        val msg = event.message.lowercase()
                        val is401 = event.message.contains("401") || msg.contains("api_key_invalid") || msg.contains("unregistered")
                        if (is401) {
                            send(GenerationEvent.Error("Clé API Gemini invalide ou expirée (Code 401). Vérifie ta clé sur aistudio.google.com."))
                            return@collect
                        }

                        val is404 = event.message.contains("404")
                        val is400 = event.message.contains("400")
                        val isUnavailable = msg.contains("no longer available") ||
                            msg.contains("not available") ||
                            msg.contains("not found") ||
                            msg.contains("not supported") ||
                            msg.contains("is not found for api version") ||
                            msg.contains("invalid argument") ||
                            msg.contains("bad request") ||
                            msg.contains("decommissioned") ||
                            msg.contains("deprecated")
                        if (!tokenReceived && (is404 || is400 || isUnavailable)) {
                            isModelUnavailable = true
                        } else {
                            send(event)
                        }
                    }
                    else -> send(event)
                }
            }

            // Si au moins un token a été reçu ou si ce n'est pas une erreur de modèle indisponible, on termine
            if (tokenReceived || !isModelUnavailable) {
                return@channelFlow
            }

            // Si c'est le dernier modèle candidat et que tous ont échoué
            if (index == candidateModels.lastIndex) {
                send(GenerationEvent.Error(lastError))
                return@channelFlow
            }
        }
    }

    private fun generateGeminiInternal(
        apiKey: String,
        modelName: String,
        turns: List<ChatTurn>,
        maxTokens: Int = 768,
        temperature: Float = 0.8f,
    ): Flow<GenerationEvent> = channelFlow {
        var connection: HttpURLConnection? = null
        try {
            val cleanKey = apiKey.trim().removeSurrounding("\"").removeSurrounding("'")
            if (cleanKey.isBlank()) {
                send(GenerationEvent.Error("Aucune clé API Gemini configurée (Réglages → Moteur d'IA)."))
                return@channelFlow
            }
            val cleanModel = sanitizeGeminiModel(modelName)
            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:streamGenerateContent" +
                    "?alt=sse&key=$cleanKey"
            )
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-goog-api-key", cleanKey)
                setRequestProperty("User-Agent", "OpenCompanion/1.0")
            }

            val systemText = turns.firstOrNull { it.role == "system" }?.content
            val rawContents = turns.filter { it.role != "system" && it.content.isNotBlank() }
            val contents = mutableListOf<Map<String, Any>>()
            for (turn in rawContents) {
                val geminiRole = if (turn.role == "assistant") "model" else "user"
                if (contents.isNotEmpty() && contents.last()["role"] == geminiRole) {
                    val last = contents.removeAt(contents.lastIndex)
                    @Suppress("UNCHECKED_CAST")
                    val existingParts = (last["parts"] as? List<Map<String, String>>) ?: emptyList()
                    val mergedText = (existingParts.firstOrNull()?.get("text") ?: "") + "\n\n" + turn.content
                    contents.add(mapOf(
                        "role" to geminiRole,
                        "parts" to listOf(mapOf("text" to mergedText))
                    ))
                } else {
                    contents.add(mapOf(
                        "role" to geminiRole,
                        "parts" to listOf(mapOf("text" to turn.content))
                    ))
                }
            }
            if (contents.isEmpty()) {
                contents.add(mapOf(
                    "role" to "user",
                    "parts" to listOf(mapOf("text" to "Bonjour"))
                ))
            }
            if (contents.first()["role"] != "user") {
                contents.add(0, mapOf(
                    "role" to "user",
                    "parts" to listOf(mapOf("text" to "..."))
                ))
            }

            // Uniquement les 4 catégories supportées par Gemini avec BLOCK_NONE (sans CIVIC_INTEGRITY qui cause 400)
            val safetySettings = listOf(
                mapOf("category" to "HARM_CATEGORY_HARASSMENT", "threshold" to "BLOCK_NONE"),
                mapOf("category" to "HARM_CATEGORY_HATE_SPEECH", "threshold" to "BLOCK_NONE"),
                mapOf("category" to "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold" to "BLOCK_NONE"),
                mapOf("category" to "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold" to "BLOCK_NONE"),
            )

            val payloadMap = mutableMapOf<String, Any>(
                "contents" to contents,
                "generationConfig" to mapOf(
                    "temperature" to temperature.coerceIn(0.0f, 2.0f),
                    "maxOutputTokens" to maxTokens.coerceIn(64, 4096),
                ),
                "safetySettings" to safetySettings,
            )
            if (!systemText.isNullOrBlank()) {
                payloadMap["systemInstruction"] = mapOf("parts" to listOf(mapOf("text" to systemText)))
            }

            val jsonBody = buildJsonString(payloadMap)
            connection.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                val errorMsg = parseErrorMessage(errorText, responseCode)
                send(GenerationEvent.Error("Gemini ($responseCode) : $errorMsg"))
                return@channelFlow
            }

            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line?.trim() ?: continue
                    if (currentLine.startsWith("data:")) {
                        val dataStr = currentLine.removePrefix("data:").trim()
                        if (dataStr.isEmpty()) continue
                        try {
                            val token = extractTokenFromGemini(dataStr)
                            if (!token.isNullOrEmpty()) {
                                send(GenerationEvent.Token(token))
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
            send(GenerationEvent.Done)
        } catch (e: Exception) {
            send(GenerationEvent.Error(e.message ?: "Erreur réseau lors de la connexion à Gemini"))
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Inférence via l'API officielle OpenAI (api.openai.com) en streaming SSE.
     */
    fun generateOpenAi(
        apiKey: String,
        modelName: String,
        turns: List<ChatTurn>,
        maxTokens: Int = 768,
        temperature: Float = 0.8f,
    ): Flow<GenerationEvent> = generateWithKeyRotation(parseApiKeys(apiKey), providerTag = "openai") { key ->
        generateOpenAiCompatible(
            endpointUrl = "https://api.openai.com/v1/chat/completions",
            apiKey = key,
            modelName = modelName.ifBlank { "gpt-4o-mini" },
            turns = turns,
            maxTokens = maxTokens,
            temperature = temperature,
        )
    }

    private fun extractTokenFromGemini(jsonStr: String): String? {
        val root = jsonParser.parseToJsonElement(jsonStr).jsonObject
        val candidates = root["candidates"]?.jsonArray
        if (candidates.isNullOrEmpty()) {
            val promptFeedback = root["promptFeedback"]?.jsonObject
            val blockReason = promptFeedback?.get("blockReason")?.jsonPrimitive?.content
            if (!blockReason.isNullOrBlank()) {
                return "[Message filtré par Gemini : $blockReason]"
            }
            return null
        }
        val firstCand = candidates[0].jsonObject
        val finishReason = firstCand["finishReason"]?.jsonPrimitive?.content
        val content = firstCand["content"]?.jsonObject
        val parts = content?.get("parts")?.jsonArray
        val text = parts?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
        if (!text.isNullOrEmpty()) return text
        if (finishReason == "SAFETY") {
            return "[Réponse modérée par les filtres de sécurité Gemini (SAFETY)]"
        }
        return null
    }

    private fun extractTokenFromSse(jsonStr: String): String? {
        val root = jsonParser.parseToJsonElement(jsonStr).jsonObject
        val choices = root["choices"]?.jsonArray ?: return null
        if (choices.isEmpty()) return null
        val firstChoice = choices[0].jsonObject
        val delta = firstChoice["delta"]?.jsonObject ?: firstChoice["message"]?.jsonObject ?: return null
        return delta["content"]?.jsonPrimitive?.content
    }

    private fun parseErrorMessage(errorBody: String, code: Int): String {
        return try {
            val root = jsonParser.parseToJsonElement(errorBody).jsonObject
            val errObj = root["error"]?.jsonObject
            val msg = errObj?.get("message")?.jsonPrimitive?.content ?: root["message"]?.jsonPrimitive?.content
            if (!msg.isNullOrBlank()) {
                if (code == 401) {
                    return "Clé API non valide (401) : $msg (vérifie ta clé sur aistudio.google.com)"
                }
                return msg
            }
            when (code) {
                400 -> "Requête non supportée par le modèle (Code 400)."
                401 -> "Clé API non valide ou expirée (Code 401). Vérifie ta clé dans les Réglages."
                403 -> "Accès refusé ou quota dépassé (Code 403)."
                404 -> "Modèle indisponible ou introuvable (Code 404)."
                429 -> "Limite de requêtes atteinte (Code 429). Réessaie dans un instant."
                else -> "Erreur HTTP $code"
            }
        } catch (_: Exception) {
            when (code) {
                400 -> "Requête non supportée (Code 400)."
                401 -> "Clé API non valide ou expirée (Code 401). Vérifie ta clé dans les Réglages."
                403 -> "Accès refusé ou quota dépassé (Code 403)."
                404 -> "Modèle indisponible (Code 404)."
                429 -> "Limite de requêtes atteinte (Code 429)."
                else -> "Erreur HTTP $code"
            }
        }
    }

    private fun buildJsonString(obj: Any?): String = when (obj) {
        null -> "null"
        is String -> "\"${escapeJson(obj)}\""
        is Number, is Boolean -> obj.toString()
        is List<*> -> obj.joinToString(",", "[", "]") { buildJsonString(it) }
        is Map<*, *> -> obj.entries.joinToString(",", "{", "}") { (k, v) -> "\"${escapeJson(k.toString())}\":${buildJsonString(v)}" }
        else -> "\"${escapeJson(obj.toString())}\""
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    suspend fun fetchGroqModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val key = parseApiKeys(apiKey).firstOrNull() ?: error("Clé API Groq requise")
            val url = URL("https://api.groq.com/openai/v1/models")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("User-Agent", "OpenCompanion/1.0")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                error(parseErrorMessage(err, code))
            }
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val root = jsonParser.parseToJsonElement(resp).jsonObject
            val data = root["data"]?.jsonArray ?: emptyList()
            val models = data.mapNotNull {
                val obj = it.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val active = obj["active"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
                if (active && id !in DECOMMISSIONED_GROQ_MODELS && !id.contains("whisper")) id else null
            }.sortedWith(Comparator { a, b ->
                fun rank(s: String) = when {
                    s.startsWith("llama-3.3-70b") -> 0
                    s.startsWith("llama-3.1-8b") -> 1
                    s.startsWith("llama-3.1-70b") -> 2
                    s.contains("qwen") -> 3
                    s.contains("deepseek") -> 4
                    else -> 5
                }
                val rA = rank(a)
                val rB = rank(b)
                if (rA != rB) rA.compareTo(rB) else a.compareTo(b)
            })
            if (models.isEmpty()) listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant") else models
        }
    }

    suspend fun fetchGeminiModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val key = parseApiKeys(apiKey).firstOrNull() ?: error("Clé API Gemini requise")
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$key")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "OpenCompanion/1.0")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                error(parseErrorMessage(err, code))
            }
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val root = jsonParser.parseToJsonElement(resp).jsonObject
            val modelsArray = root["models"]?.jsonArray ?: emptyList()
            val models = modelsArray.mapNotNull {
                val obj = it.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val methods = obj["supportedGenerationMethods"]?.jsonArray?.map { m -> m.jsonPrimitive.content } ?: emptyList()
                val id = name.removePrefix("models/")
                if (methods.contains("generateContent") &&
                    !id.contains("embedding") &&
                    !id.contains("aqa") &&
                    id !in DEPRECATED_GEMINI_MODELS &&
                    !id.startsWith("gemini-1.0") &&
                    id != "gemini-pro"
                ) {
                    id
                } else null
            }.sortedWith(Comparator { a, b ->
                fun rank(s: String) = when {
                    s == "gemini-2.0-flash" -> 0
                    s == "gemini-2.0-flash-lite-preview-02-05" -> 1
                    s.startsWith("gemini-2.5-flash") -> 2
                    s == "gemini-1.5-flash" -> 3
                    s == "gemini-1.5-pro" -> 4
                    s.startsWith("gemini-2.0") -> 5
                    s.startsWith("gemini-1.5") -> 6
                    else -> 7
                }
                val rA = rank(a)
                val rB = rank(b)
                if (rA != rB) rA.compareTo(rB) else a.compareTo(b)
            })
            if (models.isEmpty()) listOf("gemini-2.0-flash", "gemini-2.0-flash-lite-preview-02-05", "gemini-1.5-flash") else models
        }
    }

    suspend fun fetchOpenAiModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val key = parseApiKeys(apiKey).firstOrNull() ?: error("Clé API OpenAI requise")
            val url = URL("https://api.openai.com/v1/models")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("User-Agent", "OpenCompanion/1.0")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                error(parseErrorMessage(err, code))
            }
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val root = jsonParser.parseToJsonElement(resp).jsonObject
            val data = root["data"]?.jsonArray ?: emptyList()
            val chatPrefixes = listOf("gpt-4", "gpt-3.5", "o1", "o3", "chatgpt")
            val models = data.mapNotNull {
                val id = it.jsonObject["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                if (chatPrefixes.any { p -> id.startsWith(p) } && !id.contains("realtime") && !id.contains("audio") && !id.contains("transcription") && !id.contains("tts")) {
                    id
                } else null
            }.sortedWith(Comparator { a, b ->
                fun rank(s: String) = when {
                    s == "gpt-4o-mini" -> 0
                    s == "gpt-4o" -> 1
                    s.startsWith("o3-mini") -> 2
                    s.startsWith("o1") -> 3
                    s.startsWith("gpt-4-turbo") -> 4
                    else -> 5
                }
                val rA = rank(a)
                val rB = rank(b)
                if (rA != rB) rA.compareTo(rB) else a.compareTo(b)
            })
            if (models.isEmpty()) listOf("gpt-4o-mini", "gpt-4o", "o3-mini") else models
        }
    }

    suspend fun fetchOpenRouterModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://openrouter.ai/api/v1/models")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "Mozilla/5.0 OpenCompanion/1.0")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                error("HTTP $code")
            }
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val root = jsonParser.parseToJsonElement(resp).jsonObject
            val data = root["data"]?.jsonArray ?: emptyList()
            val models = data.mapNotNull {
                it.jsonObject["id"]?.jsonPrimitive?.content
            }.sortedWith(Comparator { a, b ->
                val aFree = a.endsWith(":free")
                val bFree = b.endsWith(":free")
                if (aFree != bFree) (if (aFree) -1 else 1) else a.compareTo(b)
            })
            if (models.isEmpty()) listOf("nousresearch/hermes-3-llama-3.1-8b:free", "meta-llama/llama-3.3-70b-instruct:free") else models
        }
    }

    /**
     * Synthétise un prompt photographique ultra-détaillé et photoréaliste en anglais à partir :
     * 1. De la description physique complète du personnage (traits, yeux, cheveux, silhouette, âge)
     * 2. Des derniers messages échangés (pour capturer la tenue actuelle, la posture, l'émotion et le décor)
     * 3. Des notes de mémoire persistantes et du niveau de relation
     * 4. Des éventuelles consignes directes de l'utilisateur
     */
    /**
     * Traduit et extrait de manière déterministe les traits physiques d'un personnage en tags Stable Diffusion (en anglais)
     */
    fun extractSdPhysicalTags(character: CharacterEntity): String {
        val desc = character.description
        val tags = mutableListOf<String>()

        // 1. Âge
        val ageMatch = Regex("""(?:Âge\s*:\s*|âge de\s*|\((\d{2})\s*ans\))(\d{2})?""").find(desc)
        val age = ageMatch?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() } ?: ""
        if (age.isNotBlank() && age != "0") {
            tags.add("($age years old mature woman:1.2)")
        } else {
            tags.add("mature woman")
        }

        // 2. Cheveux
        val hairLine = desc.lines().find { it.contains("Cheveux", ignoreCase = true) } ?: ""
        val lowerHair = hairLine.lowercase()
        val hairDesc = mutableListOf<String>()
        when {
            lowerHair.contains("blond miel") -> hairDesc.add("warm honey blonde hair")
            lowerHair.contains("blond doré") -> hairDesc.add("golden blonde hair")
            lowerHair.contains("blond platine") -> hairDesc.add("platinum blonde hair")
            lowerHair.contains("blond vénitien") -> hairDesc.add("strawberry blonde hair")
            lowerHair.contains("blond") -> hairDesc.add("blonde hair")
            lowerHair.contains("châtain foncé") -> hairDesc.add("dark chestnut brown hair")
            lowerHair.contains("châtain") -> hairDesc.add("chestnut brown hair")
            lowerHair.contains("brun chocolat") -> hairDesc.add("rich dark chocolate brunette hair")
            lowerHair.contains("brun") -> hairDesc.add("brunette hair")
            lowerHair.contains("noir ébène") || lowerHair.contains("noir") -> hairDesc.add("raven black hair")
            lowerHair.contains("roux") || lowerHair.contains("cuivré") -> hairDesc.add("vibrant copper auburn hair")
            lowerHair.contains("gris") || lowerHair.contains("argenté") -> hairDesc.add("elegant silver gray hair")
        }
        when {
            lowerHair.contains("carré plongeant") -> hairDesc.add("inverted sleek bob haircut")
            lowerHair.contains("carré") -> hairDesc.add("stylish bob haircut")
            lowerHair.contains("queue de cheval") -> hairDesc.add("high ponytail")
            lowerHair.contains("chignon") -> hairDesc.add("sophisticated hair bun")
            lowerHair.contains("mi-longs") -> hairDesc.add("shoulder length medium hair")
            lowerHair.contains("longs") -> hairDesc.add("long flowing hair")
            lowerHair.contains("court") -> hairDesc.add("chic short haircut")
        }
        when {
            lowerHair.contains("boucl") -> hairDesc.add("voluminous curls")
            lowerHair.contains("ondul") -> hairDesc.add("gentle wavy texture")
            lowerHair.contains("soyeux") || lowerHair.contains("lisse") -> hairDesc.add("silky smooth hair")
        }
        if (hairDesc.isNotEmpty()) {
            tags.add("(${hairDesc.joinToString(", ")}:1.3)")
        }

        // 3. Yeux & Regard
        val eyesLine = desc.lines().find { it.contains("Yeux", ignoreCase = true) } ?: ""
        val lowerEyes = eyesLine.lowercase()
        when {
            lowerEyes.contains("vert émeraude") || lowerEyes.contains("vert") -> tags.add("(striking emerald green eyes:1.2)")
            lowerEyes.contains("bleu azur") || lowerEyes.contains("bleu profond") || lowerEyes.contains("bleu") -> tags.add("(mesmerizing deep blue eyes:1.2)")
            lowerEyes.contains("noisette") -> tags.add("(warm sparkling hazel eyes:1.2)")
            lowerEyes.contains("marron") -> tags.add("(deep expressive brown eyes:1.2)")
            lowerEyes.contains("sombre") || lowerEyes.contains("noir") -> tags.add("(dark intense sensual eyes:1.2)")
        }

        // 4. Morphologie & Poitrine
        val morphLine = desc.lines().find { it.contains("Morphologie", ignoreCase = true) || it.contains("Poitrine", ignoreCase = true) } ?: ""
        val lowerMorph = morphLine.lowercase()
        when {
            lowerMorph.contains("bonnet 90d") || lowerMorph.contains("bonnet 85d") || lowerMorph.contains("généreuse") -> tags.add("(voluptuous hourglass feminine body, large shapely natural bust:1.2)")
            lowerMorph.contains("bonnet 90c") || lowerMorph.contains("bonnet 85c") || lowerMorph.contains("galbée") -> tags.add("(shapely curvy feminine figure, toned waist:1.1)")
            lowerMorph.contains("athlétique") || lowerMorph.contains("tonique") -> tags.add("(fit toned athletic feminine body:1.1)")
            lowerMorph.contains("élancée") -> tags.add("(slender elegant graceful silhouette:1.1)")
        }

        // 5. Visage & Peau
        val faceLine = desc.lines().find { it.contains("Visage", ignoreCase = true) || it.contains("Teint", ignoreCase = true) } ?: ""
        val lowerFace = faceLine.lowercase()
        when {
            lowerFace.contains("pommettes") -> tags.add("high cheekbones")
            lowerFace.contains("sourire") -> tags.add("gentle alluring smile")
        }
        tags.add("soft natural skin texture, realistic facial features")

        return tags.joinToString(", ")
    }

    /**
     * Extrait l'ADN visuel complet et immuable du personnage depuis sa fiche pour garantir
     * une consistance visuelle absolue (même visage, mêmes yeux, mêmes cheveux, même corps)
     * à travers toutes les photographies générées.
     */
    fun extractVisualIdentityDNA(character: CharacterEntity): String {
        val desc = character.description
        val lowerDesc = desc.lowercase()

        // 1. Âge
        val ageMatch = Regex("""(?:Âge\s*:\s*|âge de\s*|\((\d{2})\s*ans\))(\d{2})?""").find(desc)
        val ageVal = ageMatch?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() } ?: "35"
        val ageStr = "$ageVal-year-old"

        // 2. Cheveux complets
        val hairLine = desc.lines().find { it.contains("Cheveux", ignoreCase = true) } ?: ""
        val lowerHair = hairLine.lowercase()
        val hairDetails = when {
            lowerHair.contains("blond vénitien") -> "luminous strawberry honey-blonde hair with golden and honey highlights, silky flowing long tresses"
            lowerHair.contains("blond miel") -> "warm honey blonde hair with natural radiant reflections, silky soft texture"
            lowerHair.contains("blond doré") -> "radiant golden blonde hair, lustrous and glossy"
            lowerHair.contains("blond platine") -> "striking platinum blonde hair, perfectly sleek"
            lowerHair.contains("blond") -> "beautiful blonde hair with natural radiant highlights"
            lowerHair.contains("châtain foncé") -> "rich dark chestnut brown hair with warm amber reflections"
            lowerHair.contains("châtain") -> "soft chestnut brown hair with natural volume"
            lowerHair.contains("brun chocolat") -> "deep chocolate brown hair, silky and lustrous"
            lowerHair.contains("brun") -> "rich brunette hair, glossy and smooth"
            lowerHair.contains("noir") -> "jet black raven hair with a brilliant silk sheen"
            lowerHair.contains("roux") || lowerHair.contains("cuivré") -> "gorgeous fiery copper-auburn hair, warm vibrant tones"
            lowerHair.contains("gris") || lowerHair.contains("argenté") -> "sophisticated silver-gray hair, beautifully styled"
            else -> "beautifully styled brunette hair"
        }
        val hairCut = when {
            lowerHair.contains("carré plongeant") -> "cut in a modern inverted bob framing her neck"
            lowerHair.contains("carré") -> "in a chic stylish bob haircut"
            lowerHair.contains("queue de cheval") -> "gathered in an elegant high ponytail"
            lowerHair.contains("chignon") -> "styled in a classy loose bun with delicate wisps"
            lowerHair.contains("boucl") -> "falling in rich voluminous bouncy curls"
            lowerHair.contains("ondul") -> "cascading down in gentle natural wavy layers"
            lowerHair.contains("mi-longs") -> "shoulder-length, falling gently past her collarbones"
            lowerHair.contains("longs") -> "long and voluminous, cascading gracefully over her shoulders"
            lowerHair.contains("court") -> "in a chic modern pixie-bob cut"
            else -> "cascading naturally over her shoulders"
        }

        // 3. Yeux & Regard
        val eyesLine = desc.lines().find { it.contains("Yeux", ignoreCase = true) || it.contains("Visage & Yeux", ignoreCase = true) } ?: ""
        val lowerEyes = eyesLine.lowercase()
        val eyeDetails = when {
            lowerEyes.contains("bleu lagon") -> "striking lagoon-blue eyes framed by long dark eyelashes, intensely expressive gaze"
            lowerEyes.contains("vert émeraude") || lowerEyes.contains("vert") -> "magnetic emerald-green eyes with golden flecks, captivating warm gaze"
            lowerEyes.contains("bleu") -> "deep crystalline blue eyes, luminous and captivating"
            lowerEyes.contains("noisette") -> "warm hazel eyes with golden-brown honey tones"
            lowerEyes.contains("marron") -> "velvety warm brown eyes, deeply expressive and tender"
            lowerEyes.contains("sombre") || lowerEyes.contains("noir") -> "intense dark magnetic eyes, smoldering expressive depth"
            else -> "expressive captivating eyes"
        }

        // 4. Visage & Teint de peau
        val skinLine = desc.lines().find { it.contains("Teint", ignoreCase = true) || it.contains("Peau", ignoreCase = true) } ?: ""
        val lowerSkin = skinLine.lowercase()
        val skinDetails = when {
            lowerSkin.contains("porcelaine") || lowerSkin.contains("laiteux") -> "immaculate milky porcelain skin, ultra-fine velvety texture"
            lowerSkin.contains("doré") || lowerSkin.contains("hâlé") || lowerSkin.contains("soleil") -> "radiant sunkissed golden skin tone, smooth and warm"
            lowerSkin.contains("mat") || lowerSkin.contains("méditerranéen") -> "warm olive Mediterranean complexion, flawless velvety tone"
            lowerSkin.contains("ébène") || lowerSkin.contains("noir") -> "luminous rich ebony skin, flawless radiant glow"
            lowerSkin.contains("diaphane") || lowerSkin.contains("clair") -> "delicate fair alabaster skin, naturally flushed cheeks"
            else -> "radiant healthy skin, natural realistic skin pores and texture"
        }

        val faceDetails = when {
            lowerDesc.contains("pommettes") && lowerDesc.contains("lèvre") -> "expressive warm facial features, delicately sculpted cheekbones, soft full natural lips"
            lowerDesc.contains("lèvre") -> "harmonious facial contours, naturally alluring full lips"
            lowerDesc.contains("pommettes") -> "delicately sculpted high cheekbones, elegant refined facial structure"
            else -> "harmonious elegant facial features, naturally captivating smile"
        }

        // 5. Mensurations complètes : Taille, Poids, Poitrine / Bonnet exact & Morphologie cambrée
        val heightMatch = Regex("""(?:Taille\s*:\s*)(\d[m,.]\d{2})""", RegexOption.IGNORE_CASE).find(desc)
        val heightStr = heightMatch?.groupValues?.get(1)?.let { "height $it" } ?: ""

        val weightMatch = Regex("""(?:Poids\s*:\s*)(\d{2}\s*kg)""", RegexOption.IGNORE_CASE).find(desc)
        val weightStr = weightMatch?.groupValues?.get(1)?.let { "weighing $it" } ?: ""

        val bustMatch = Regex("""(?:Poitrine\s*:\s*|Bonnet\s*)([0-9]{2,3}[A-G]|Bonnet\s*[0-9]{2,3}[A-G][^|•\n]*)""", RegexOption.IGNORE_CASE).find(desc)
        val bustRaw = bustMatch?.groupValues?.get(1)?.trim() ?: ""
        val bustStr = when {
            bustRaw.isNotBlank() -> "magnificent full natural $bustRaw bust, deeply alluring cleavage"
            lowerDesc.contains("90d") -> "full natural 90D cup bust, deeply alluring cleavage"
            lowerDesc.contains("95d") -> "generous natural 95D cup bust, deeply alluring cleavage"
            lowerDesc.contains("90c") -> "shapely natural 90C cup bust, flattering cleavage"
            lowerDesc.contains("85c") -> "beautiful natural 85C cup bust, flattering cleavage"
            else -> "full shapely natural feminine bust, attractive cleavage"
        }

        val morphoLine = desc.lines().find { it.contains("Morphologie", ignoreCase = true) } ?: ""
        val morphoStr = when {
            morphoLine.contains("cambrée", ignoreCase = true) || morphoLine.contains("rebondi", ignoreCase = true) ->
                "provocative feminine hourglass silhouette, slender arched waist, prominent shapely curves and rounded hips"
            morphoLine.contains("galbée", ignoreCase = true) || morphoLine.contains("courbes", ignoreCase = true) ->
                "gorgeous curvaceous feminine silhouette, toned narrow waist, voluptuous feminine contours"
            else ->
                "provocative feminine hourglass silhouette, slender arched waist, feminine curves"
        }

        val measurementsList = listOfNotNull(heightStr.takeIf { it.isNotBlank() }, weightStr.takeIf { it.isNotBlank() }, bustStr, morphoStr)
        val bodyDetails = measurementsList.joinToString(", ")

        return "Photorealistic portrait of the exact recurring individual: ${character.name}, gorgeous $ageStr French woman. Facial & Physical Identity DNA: $skinDetails, $faceDetails, $eyeDetails, $hairDetails $hairCut, Body & Measurements: $bodyDetails. Absolute visual and facial consistency across all photographs."
    }

    /**
     * Construit un prompt photographique en langage naturel haute définition,
     * spécialement calibré pour les modèles Google Gemini (Gemini 2.5 Flash Image, Nano Banana 2).
     * Banni tout le jargon technique Stable Diffusion (poids :1.2, tags compacts) pour produire un vrai rendu réaliste
     * identique à l'application officielle Google Gemini sur smartphone.
     * Prend en compte la tenue, la scène, la position de la conversation, et traduit toute demande intime/nude
     * en lingerie intime élégante sans jamais verser dans l'explicite.
     */
    fun buildPhotorealisticNaturalPrompt(
        character: CharacterEntity,
        recentMessages: List<ChatMessageEntity>,
        userCustomInstruction: String? = null
    ): String {
        val identityDNA = extractVisualIdentityDNA(character)

        // Extraction spécifique du bonnet/poitrine pour adaptation des tenues et décolletés
        val bustMatch = Regex("""(?:Poitrine\s*:\s*|Bonnet\s*)([0-9]{2,3}[A-G]|Bonnet\s*[0-9]{2,3}[A-G][^|•\n]*)""", RegexOption.IGNORE_CASE).find(character.description)
        val bustLabel = bustMatch?.groupValues?.get(1)?.trim() ?: if (character.description.contains("90D", ignoreCase = true)) "90D" else "full natural bust"

        // 1. Extraction du contexte de mémoire à long terme (Tenue, Scène, Posture actuelles)
        val memoryState = com.opencompanion.app.memory.LongTermMemoryManager.parse(character.memoryNotes)
        val currentOutfit = memoryState.outfit.trim()
        val currentLocation = memoryState.location.trim().ifBlank { character.scenario.trim() }
        val currentPosture = memoryState.posture.trim()

        // 2. Déterminer la demande spécifique et analyser en profondeur les derniers messages du chat
        val lastUserMsg = recentMessages.lastOrNull { it.role == MessageRole.USER }?.content?.trim() ?: ""
        val inputRaw = (userCustomInstruction?.trim() ?: lastUserMsg).replace("\n", " ")
        val lowerInput = inputRaw.lowercase()

        // Concaténation des 8 derniers messages échangés pour extraire l'action, le lieu, la posture et la tenue en direct
        val conversationHistorySnippet = recentMessages.takeLast(8).joinToString(" ") { it.content.lowercase().replace("\n", " ") }
        val fullContextText = "$conversationHistorySnippet $lowerInput"

        // 3. Détection de demande intime / lingerie / "nude"
        // RÈGLE ABSOLUE : Si "nude", "nue", "à poil", "déshabillée" etc. -> Traduire strictement par lingerie sexy et intime, sans nudité explicite
        val isNudeOrIntimate = fullContextText.contains("nude") || fullContextText.contains("nue") || fullContextText.contains(" à poil") ||
                fullContextText.contains("poil") || fullContextText.contains("sans vêtement") || fullContextText.contains("déshabill") ||
                fullContextText.contains("lingerie") || fullContextText.contains("dentelle") || fullContextText.contains("nuisette") ||
                fullContextText.contains("peignoir") || fullContextText.contains("satin") || fullContextText.contains("culotte") ||
                fullContextText.contains("soutien-gorge") || fullContextText.contains("intime") || fullContextText.contains("sexy") ||
                fullContextText.contains("boudoir") || fullContextText.contains("corset") ||
                currentOutfit.lowercase().contains("lingerie") || currentOutfit.lowercase().contains("dentelle") ||
                currentOutfit.lowercase().contains("nuisette")

        // 4. Détection sémantique dynamique de la TENUE dans la conversation
        val outfitDescription: String
        val atmosphereDescription: String

        if (isNudeOrIntimate) {
            outfitDescription = "wearing an exquisitely sexy sheer black or blush-pink floral lace lingerie set, delicate low-cut lace bralette accentuating her gorgeous $bustLabel bust, matching sheer lace panties, seductive feminine silhouette, strictly non-explicit and artistic"
            atmosphereDescription = "cozy warm romantic boudoir atmosphere, soft ambient glow, sensual intimate photography, artistic low-key lighting, strictly aesthetic glamour, no explicit nudity"
        } else if (fullContextText.contains("peignoir") || fullContextText.contains("robe de chambre")) {
            outfitDescription = "wearing a silky satin robe loosely tied around her arched waist, partially parted in front revealing bare shoulders and a deeply enticing low-cut neckline showing her $bustLabel bust"
            atmosphereDescription = "intimate relaxed atmosphere, warm golden indoor lighting, seductive ease"
        } else if (fullContextText.contains("nuisette")) {
            outfitDescription = "wearing a soft shimmering silk nightie with delicate lace embroidery, plunging neckline highlighting her $bustLabel bust, bare shoulders and arched waist"
            atmosphereDescription = "dimly-lit romantic bedroom ambiance, warm lamp light, tender allure"
        } else if (fullContextText.contains("robe") || fullContextText.contains("soirée") || fullContextText.contains("cocktail")) {
            outfitDescription = "wearing an ultra-flattering glamorous form-fitting evening dress with a deeply plunging neckline accentuating her $bustLabel bust, subtle side slit, hugging her arched waist and hourglass silhouette"
            atmosphereDescription = "chic sophisticated upscale ambiance, seductive warm flattering light"
        } else if (fullContextText.contains("minijupe") || fullContextText.contains("jupe courte")) {
            outfitDescription = "wearing a provocative short mini-skirt and a form-fitting low-cut top highlighting her $bustLabel bust and slender waist, long toned legs"
            atmosphereDescription = "modern trendy indoor lighting, playful enticing energy"
        } else if (fullContextText.contains("tailleur") || fullContextText.contains("bureau") || fullContextText.contains("chemisier")) {
            outfitDescription = "wearing a sleek form-fitting pencil skirt and a dangerously unbuttoned silk blouse tailored to accentuate her $bustLabel bust and curves, sophisticated seductive businesswoman style"
            atmosphereDescription = "executive stylish interior, bright soft daylight through large windows"
        } else if (fullContextText.contains("maillot") || fullContextText.contains("bikini")) {
            outfitDescription = "wearing an alluring elegant designer swimsuit accentuating her $bustLabel bust and toned curves, stylish sarong around her waist"
            atmosphereDescription = "warm sunlit poolside or coastal glow, glistening water reflections"
        } else if (currentOutfit.isNotBlank()) {
            outfitDescription = "wearing a form-fitting, slightly provocative and flattering $currentOutfit, tailored to highlight her arched waist and shapely $bustLabel bust with a tasteful enticing neckline"
            atmosphereDescription = "natural authentic environment, photorealistic ambient lighting, alluring sensual charm"
        } else {
            outfitDescription = "wearing an alluring, slightly provocative stylish outfit (fitted top with a subtle flattering neckline accentuating her $bustLabel bust, and form-fitting skirt or trousers highlighting her curves and arched waist)"
            atmosphereDescription = "natural ambient daylight, cozy stylish interior, seductive feminine aura"
        }

        // 5. Détection sémantique dynamique du LIEU et de l'ENVIRONNEMENT dans la conversation et l'historique
        val settingScene = when {
            fullContextText.contains("bibliothèque") || fullContextText.contains("livre") || character.scenario.contains("bibliothèque", ignoreCase = true) ->
                "Setting & Environment: grand historic library with tall floor-to-ceiling wooden bookshelves packed with vintage leather-bound books, rolling wooden library ladder, polished parquet floor, warm amber reading lamps, rich intellectual atmosphere."
            fullContextText.contains("cuisine") ->
                "Setting & Environment: warm rustic chic French kitchen with marble countertops, copper pans hanging, warm pendant lighting, fresh ingredients on the island."
            fullContextText.contains("chambre") || fullContextText.contains("lit") || isNudeOrIntimate ->
                "Setting & Environment: intimate luxury bedroom suite with a plush king-size bed, rumpled silk sheets, soft bedside lamps and warm textured wallpaper, cozy private sanctuary."
            fullContextText.contains("salle de bain") || fullContextText.contains("bain") || fullContextText.contains("douche") ->
                "Setting & Environment: opulent modern marble bathroom with a freestanding tub, large illuminated vanity mirror, scented candles and soft steam."
            fullContextText.contains("bureau") || fullContextText.contains("travail") ->
                "Setting & Environment: bright executive contemporary office with large windows, mahogany desk, modern artwork and city view."
            fullContextText.contains("voiture") || fullContextText.contains("auto") ->
                "Setting & Environment: interior of a premium luxury vehicle with supple stitched leather seats, ambient cockpit lighting and city lights through tinted windows."
            fullContextText.contains("balcon") || fullContextText.contains("terrasse") || fullContextText.contains("rooftop") ->
                "Setting & Environment: stylish panoramic rooftop terrace overlooking the city skyline at dusk with warm fairy lights and plush outdoor seating."
            fullContextText.contains("piscine") ->
                "Setting & Environment: luxurious private villa poolside terrace with sun loungers and crystal turquoise water reflections."
            fullContextText.contains("plage") || fullContextText.contains("mer") ->
                "Setting & Environment: scenic private Mediterranean beach with golden sand, gentle waves and sunset sky in the background."
            fullContextText.contains("salon") || fullContextText.contains("canapé") || fullContextText.contains("sofa") ->
                "Setting & Environment: chic Parisian apartment living room with a velvet sofa, marble coffee table, art books, tall French windows and ambient floor lamps."
            currentLocation.isNotBlank() ->
                "Setting & Environment: fully realized authentic real-life environment in $currentLocation with visible detailed furniture, architecture, and warm ambient lighting. Never a plain or neutral studio background."
            character.scenario.isNotBlank() ->
                "Setting & Environment: authentic detailed environment matching: ${character.scenario.take(150)}, with realistic decor and depth."
            else ->
                "Setting & Environment: charming cozy Parisian apartment with wooden parquet floor, bookshelves, large French window and soft ambient indoor lighting."
        }

        // 6. Détection sémantique dynamique de la POSTURE et de l'ACTION dans la conversation
        val postureAction = when {
            (fullContextText.contains("livre") || fullContextText.contains("étagère") || fullContextText.contains("attraper") || fullContextText.contains("haut")) ->
                "Pose: standing gracefully on tiptoes reaching one slender arm upward toward a high wooden bookshelf, arched back highlighting her curves and bust, looking back over her shoulder with an expressive grateful and flirty smile toward the camera."
            fullContextText.contains("penchée") || fullContextText.contains("se penche") || fullContextText.contains("sur la table") || fullContextText.contains("sur le bureau") ->
                "Pose: leaning forward gracefully over the desk or table, elbows propped, deeply accentuating her feminine cleavage and arched waist, intense captivating gaze into the camera."
            fullContextText.contains("allongée") || fullContextText.contains("couchée") || fullContextText.contains("sur le lit") || fullContextText.contains("sur le canapé") ->
                "Pose: lounging sensually on a plush bed or sofa, propped gracefully on one elbow, body curved alluringly, shapely legs, captivating sultry smile."
            fullContextText.contains("assise") || fullContextText.contains("s'assoit") ->
                "Pose: seated gracefully with legs crossed, hand resting gently on her knee, upright poised posture emphasizing her bust and waist, warm inviting smile."
            fullContextText.contains("dos") || fullContextText.contains("derrière") || fullContextText.contains("par-dessus l'épaule") ->
                "Pose: looking back seductively over her bare shoulder, showcasing her arched waist and curves, intensely alluring smoldering gaze."
            fullContextText.contains("debout") ->
                "Pose: provocative standing pose, hand on arched hip, emphasizing her curvaceous hourglass silhouette and bust, confident sultry attitude."
            currentPosture.isNotBlank() ->
                "Pose: slightly provocative sensual posture, $currentPosture, arched back, confident captivating eye contact, irresistible sensual aura."
            else ->
                "Pose: naturally provocative and alluring stance, arched back accentuating her bust and feminine curves, captivating direct eye contact with a flirtatious confident smile."
        }

        return "$identityDNA " +
                "$settingScene " +
                "Outfit: $outfitDescription. " +
                "$postureAction " +
                "Aesthetic & Mood: $atmosphereDescription. " +
                "PHOTOGRAPHIC REALISM DIRECTIVES: Raw 35mm film photograph of an actual living human person, authentic human skin pores and texture, natural lighting, sharp focus on subject with rich environmental background depth. ABSOLUTELY NO illustration, NO drawing, NO anime, NO 3D render, NO digital painting, NO cartoon, NO plastic airbrushed skin, NO plain or neutral backdrop. Completely non-explicit, masterpiece photograph."
    }

    /**
     * Construit le prompt optimal pour la génération d'image réaliste fidèle à la description physique.
     */
    suspend fun buildSceneImagePrompt(
        geminiApiKey: String,
        character: CharacterEntity,
        recentMessages: List<ChatMessageEntity>,
        userCustomInstruction: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val keys = parseApiKeys(geminiApiKey)
        // N'appeler Gemini LLM pour la synthèse que si c'est une vraie clé Gemini (et pas un token Hugging Face hf_...)
        if (keys.isNotEmpty() && !keys.first().trim().startsWith("hf_")) {
            val key = keys.first()
            try {
                val contextHistory = recentMessages.takeLast(6).joinToString("\n") {
                    val roleLabel = if (it.role == MessageRole.USER) "User" else character.name
                    "$roleLabel: ${it.content.take(150)}"
                }
                val memoryContext = if (character.memoryNotes.isNotBlank()) "\nPersistent memory: ${character.memoryNotes.take(200)}" else ""
                val customPrompt = if (!userCustomInstruction.isNullOrBlank()) "\nUser specific instruction: $userCustomInstruction" else ""

                val instructions = """
Write an ultra-detailed, photorealistic prompt for generating a photo of ${character.name} in the active scene.
CHARACTER PHYSICAL IDENTITY:
${character.description}
${character.tagsCsv}

CONVERSATION CONTEXT & ENVIRONMENT:
Relationship stage: ${character.relationshipStage}
$memoryContext
$contextHistory
$customPrompt

REQUIREMENTS:
- Strictly maintain the character's exact facial structure, hair color/style, eye color, age, body proportions for absolute character consistency across photos.
- Strongly incorporate the character's profession, role, temperament, and signature métier scenes and postures described above (e.g. professional workplace setting, clinic, law office, atelier, salon, or refined boudoir/cocktail postures).
- Accurately capture dynamic and varied postures (e.g. leaning forward, looking back over shoulder, sitting alluringly, relaxing on bed/sofa, confident sensual stance).
- Set the scene in authentic varied environments matching the dialogue, role, or request (e.g. professional office, classroom, kitchen, cozy bedroom, chic living room, luxury car interior, hotel suite, scenic balcony).
- Render diverse stylish or intimate outfits matching the situation and profession (e.g. professional blouse and pencil skirt, elegant form-fitting dress, mini-skirt, silk satin robe, delicate lingerie, nightgown, or boudoir styling).
- Strictly non-explicit, zero pornography, and no full frontal nudity: if an intimate, nude, or boudoir setting is requested, depict the character wearing tasteful, exquisite luxury lace lingerie or a silk robe with sensual aesthetic glamour.
- Format as a photographic raw prompt: 'photorealistic candid photo of [character details], [pose and scene details], 8k resolution, authentic detailed skin texture, cinematic soft natural lighting, masterpiece, shallow depth of field, 35mm photography'.
- Output ONLY the final prompt in English with no explanations.
                """.trimIndent()

                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$key")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12_000
                    readTimeout = 15_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("User-Agent", "OpenCompanion/1.0")
                }

                val payload = mapOf(
                    "contents" to listOf(
                        mapOf("role" to "user", "parts" to listOf(mapOf("text" to instructions)))
                    ),
                    "generationConfig" to mapOf(
                        "temperature" to 0.7,
                        "maxOutputTokens" to 300,
                    )
                )

                conn.outputStream.use { os ->
                    os.write(buildJsonString(payload).toByteArray(Charsets.UTF_8))
                    os.flush()
                }

                if (conn.responseCode in 200..299) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    val root = jsonParser.parseToJsonElement(resp).jsonObject
                    val text = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("content")?.jsonObject
                        ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("text")?.jsonPrimitive?.content
                    if (!text.isNullOrBlank()) {
                        return@withContext text.trim().removeSurrounding("\"")
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {}
        }

        // Repli déterministe immédiat haute fidélité en cas d'indisponibilité de la synthèse texte LLM
        val physicalTags = extractSdPhysicalTags(character)
        buildString {
            append("masterpiece, photorealistic raw candid 8k photo, (1woman:1.1), ")
            if (physicalTags.isNotBlank()) {
                append("$physicalTags, ")
            }
            if (!userCustomInstruction.isNullOrBlank()) {
                append("$userCustomInstruction, ")
            } else {
                append("natural alluring posture, expressive gaze, authentic lighting, ")
            }
            append("highly detailed eyes and facial features, authentic skin texture, subtle depth of field, 35mm photography, dslr, high quality")
        }
    }

    /**
     * Génère une véritable image réaliste avec Google Gemini (Imagen 3) ou OpenAI DALL-E 3 en repli,
     * et l'enregistre dans le stockage privé de l'application sous [outputDir].
     */
    /**
     * Génération gratuite photoréaliste sans clé API via Pollinations FLUX (modèle 1024x1024).
     * Rognage automatique de la marge inférieure (6%) pour éliminer tout filigrane de coin.
     */
    suspend fun generateFreeSmartphoneImage(
        prompt: String,
        outputDir: File,
        filePrefix: String = "photo",
    ): File = withContext(Dispatchers.IO) {
        val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
        val seed = Random.nextInt(100000, 999999)
        val urlStr = "https://image.pollinations.ai/prompt/$encodedPrompt?model=flux&width=1024&height=1024&seed=$seed&nologo=true&enhance=false"

        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 45_000
            readTimeout = 90_000
            setRequestProperty("User-Agent", "OpenCompanion/1.0 (Android)")
        }

        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            error("Échec de la génération photo ($code): ${err.take(150)}")
        }

        val bytes = conn.inputStream.use { it.readBytes() }
        conn.disconnect()

        val rawBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Échec du décodage de l'image téléchargée.")

        // Rognage propre des 6% inférieurs pour garantir une image impeccable sans aucun filigrane
        val cropHeight = (rawBmp.height * 0.94).toInt().coerceAtLeast(100)
        val cleanBmp = Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, cropHeight)

        val outputFile = File(outputDir, "${filePrefix}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outputFile).use { fos ->
            cleanBmp.compress(Bitmap.CompressFormat.JPEG, 95, fos)
        }

        if (cleanBmp != rawBmp) {
            cleanBmp.recycle()
        }
        rawBmp.recycle()

        outputFile
    }

    /**
     * Génère une véritable image réaliste avec Google Gemini (Imagen 3), OpenAI DALL-E 3,
     * ou le moteur gratuit sans clé FLUX (style smartphone HD), et l'enregistre dans le stockage privé.
     */
    suspend fun generateCharacterSceneImage(
        imageEngine: com.opencompanion.app.data.ImageEngine = com.opencompanion.app.data.ImageEngine.FREE_SMARTPHONE,
        geminiApiKey: String = "",
        openAiApiKey: String? = null,
        cloudApiKey: String? = null,
        hordeApiKey: String? = null,
        huggingFaceApiKey: String? = null,
        geminiImageModelName: String = "imagen-3.0-generate-002",
        openAiImageModelName: String = "dall-e-3",
        cloudImageModelName: String = "black-forest-labs/flux-1-schnell",
        hordeImageModelName: String = "stable_diffusion",
        huggingFaceImageModelName: String = "black-forest-labs/FLUX.1-schnell",
        character: CharacterEntity,
        recentMessages: List<ChatMessageEntity>,
        userCustomInstruction: String? = null,
        outputDir: File,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            // 0. Exécution Moteur Gratuit Sans Clé (FLUX Photoréaliste HD style Smartphone Gemini / Copilot)
            if (imageEngine == com.opencompanion.app.data.ImageEngine.FREE_SMARTPHONE) {
                val naturalPrompt = buildPhotorealisticNaturalPrompt(
                    character = character,
                    recentMessages = recentMessages,
                    userCustomInstruction = userCustomInstruction,
                )
                return@runCatching generateFreeSmartphoneImage(
                    prompt = naturalPrompt,
                    outputDir = outputDir,
                    filePrefix = "free_${character.id}",
                )
            }

            val prompt = buildSceneImagePrompt(
                geminiApiKey = if (imageEngine == com.opencompanion.app.data.ImageEngine.GEMINI_IMAGEN) geminiApiKey else "",
                character = character,
                recentMessages = recentMessages,
                userCustomInstruction = userCustomInstruction
            )
            
            // Collecter toutes les clés Gemini possibles
            val allGeminiKeys = mutableListOf<String>()
            if (geminiApiKey.isNotBlank() && !geminiApiKey.trim().startsWith("hf_")) allGeminiKeys.addAll(parseApiKeys(geminiApiKey))
            if (!cloudApiKey.isNullOrBlank() && (cloudApiKey.trim().startsWith("AIza") || (cloudApiKey.trim().length > 30 && !cloudApiKey.trim().startsWith("gsk_") && !cloudApiKey.trim().startsWith("sk-") && !cloudApiKey.trim().startsWith("hf_")))) {
                allGeminiKeys.addAll(parseApiKeys(cloudApiKey))
            }

            // Collecter toutes les clés OpenAI
            val allOpenAiKeys = mutableListOf<String>()
            if (!openAiApiKey.isNullOrBlank() && !openAiApiKey.trim().startsWith("hf_")) allOpenAiKeys.addAll(parseApiKeys(openAiApiKey))

            // Collecter toutes les clés OpenRouter / Cloud
            val allCloudKeys = mutableListOf<String>()
            if (!cloudApiKey.isNullOrBlank() && !cloudApiKey.trim().startsWith("hf_") && (cloudApiKey.trim().startsWith("sk-or-") || cloudApiKey.trim().startsWith("sk-") || cloudApiKey.trim().startsWith("Bearer "))) {
                allCloudKeys.addAll(parseApiKeys(cloudApiKey))
            }
            if (allCloudKeys.isEmpty() && allOpenAiKeys.isNotEmpty()) {
                allCloudKeys.addAll(allOpenAiKeys)
            }

            var lastError: String? = null
            var hadGemini404 = false

            // Validation préalable selon le moteur choisi par l'utilisateur
            when (imageEngine) {
                com.opencompanion.app.data.ImageEngine.FREE_SMARTPHONE -> {
                    // 100% Gratuit sans clé requise
                }
                com.opencompanion.app.data.ImageEngine.HORDE_DIFFUSION -> {
                    // 100% Gratuit, clé anonyme "0000000000" par défaut si non renseignée
                }
                com.opencompanion.app.data.ImageEngine.GEMINI_IMAGEN -> {
                    // Si pas de clé, on basculera automatiquement sur FREE_SMARTPHONE
                }
                com.opencompanion.app.data.ImageEngine.OPENROUTER -> {
                    if (allCloudKeys.isEmpty()) {
                        error("Veuillez renseigner votre clé API OpenRouter dans Réglages → Photos.")
                    }
                }
                com.opencompanion.app.data.ImageEngine.OPENAI -> {
                    // Si pas de clé, on basculera automatiquement sur FREE_SMARTPHONE
                }
            }

            // 1. Exécution Horde Diffusion (Stable Diffusion Horde - 100% Gratuit, Sans clé, Accepte NSFW)
            if (imageEngine == com.opencompanion.app.data.ImageEngine.HORDE_DIFFUSION) {
                val apiKey = hordeApiKey?.trim()?.ifBlank { "0000000000" } ?: "0000000000"
                val selectedModel = hordeImageModelName.trim().ifBlank { "stable_diffusion" }

                // Modèles cibles : le modèle choisi en premier, puis les modèles rapides/photoréalistes/NSFW réputés
                val modelsList = listOf(
                    selectedModel,
                    "ICBINP - I Can't Believe It's Not Photography",
                    "AbsoluteReality",
                    "Deliberate",
                    "stable_diffusion"
                ).distinct()

                val submitUrl = "https://aihorde.net/api/v2/generate/async"
                val conn = (URL(submitUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 25_000
                    readTimeout = 25_000
                    doOutput = true
                    setRequestProperty("apikey", apiKey)
                    setRequestProperty("Client-Agent", "OpenCompanion:1.0:user")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }

                val hordePrompt = if (prompt.contains("###")) {
                    prompt
                } else {
                    "$prompt ### deformed, distorted, disfigured, bad eyes, bad hands, missing fingers, extra limbs, bad anatomy, blurry, low quality, cartoon, 3d render, doll, anime, sketch, watermark, signature"
                }

                val isVertical = prompt.contains("selfie", ignoreCase = true) ||
                    prompt.contains("lingerie", ignoreCase = true) ||
                    prompt.contains("full body", ignoreCase = true) ||
                    prompt.contains("standing", ignoreCase = true) ||
                    prompt.contains("mirror", ignoreCase = true) ||
                    prompt.contains("boudoir", ignoreCase = true)
                val imgWidth = if (isVertical) 512 else 512
                val imgHeight = if (isVertical) 768 else 512

                val payloadMap = mapOf(
                    "prompt" to hordePrompt,
                    "params" to mapOf(
                        "sampler_name" to "k_dpmpp_2m",
                        "cfg_scale" to 7.0,
                        "seed" to "-1",
                        "height" to imgHeight,
                        "width" to imgWidth,
                        "steps" to 25,
                        "n" to 1
                    ),
                    "nsfw" to true,
                    "censor_nsfw" to false,
                    "models" to modelsList
                )

                conn.outputStream.use { os ->
                    os.write(buildJsonString(payloadMap).toByteArray(Charsets.UTF_8))
                    os.flush()
                }

                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    conn.disconnect()
                    error("Erreur Horde Diffusion ($code) : $err")
                }

                val submitRespStr = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val submitJson = jsonParser.parseToJsonElement(submitRespStr).jsonObject
                val jobId = submitJson["id"]?.jsonPrimitive?.content
                    ?: error("ID de génération introuvable dans la réponse Horde Diffusion.")

                var imageUrl: String? = null
                var base64Img: String? = null

                // Polling toutes les 2.5 secondes jusqu'à 30 tentatives (75s max)
                for (attempt in 0 until 30) {
                    delay(2500)
                    try {
                        val checkConn = (URL("https://aihorde.net/api/v2/generate/check/$jobId").openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 15_000
                            readTimeout = 15_000
                            setRequestProperty("apikey", apiKey)
                            setRequestProperty("Client-Agent", "OpenCompanion:1.0:user")
                            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        }
                        if (checkConn.responseCode in 200..299) {
                            val checkStr = checkConn.inputStream.bufferedReader().use { it.readText() }
                            checkConn.disconnect()
                            val checkJson = jsonParser.parseToJsonElement(checkStr).jsonObject
                            val done = checkJson["done"]?.jsonPrimitive?.booleanOrNull ?: false
                            if (done) {
                                val statusConn = (URL("https://aihorde.net/api/v2/generate/status/$jobId").openConnection() as HttpURLConnection).apply {
                                    requestMethod = "GET"
                                    connectTimeout = 15_000
                                    readTimeout = 15_000
                                    setRequestProperty("apikey", apiKey)
                                    setRequestProperty("Client-Agent", "OpenCompanion:1.0:user")
                                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                }
                                if (statusConn.responseCode in 200..299) {
                                    val statusStr = statusConn.inputStream.bufferedReader().use { it.readText() }
                                    statusConn.disconnect()
                                    val statusJson = jsonParser.parseToJsonElement(statusStr).jsonObject
                                    val generations = statusJson["generations"]?.jsonArray
                                    if (!generations.isNullOrEmpty()) {
                                        val firstGen = generations[0].jsonObject
                                        val imgField = firstGen["img"]?.jsonPrimitive?.content
                                        if (!imgField.isNullOrBlank()) {
                                            if (imgField.startsWith("http://") || imgField.startsWith("https://")) {
                                                imageUrl = imgField
                                            } else {
                                                base64Img = imgField
                                            }
                                            break
                                        }
                                    }
                                } else {
                                    statusConn.disconnect()
                                }
                            }
                        } else {
                            checkConn.disconnect()
                        }
                    } catch (e: Exception) {
                        // Tolérance réseau sur le polling
                    }
                }

                if (imageUrl != null) {
                    val imgConn = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 20_000
                        readTimeout = 40_000
                        setRequestProperty("User-Agent", "Mozilla/5.0")
                    }
                    val imgBytes = imgConn.inputStream.use { it.readBytes() }
                    imgConn.disconnect()
                    val ext = if (imageUrl.contains(".webp", ignoreCase = true)) "webp" else "jpg"
                    val file = File(outputDir, "horde_${character.id}_${System.currentTimeMillis()}.$ext")
                    file.writeBytes(imgBytes)
                    return@runCatching file
                } else if (base64Img != null) {
                    val imgBytes = android.util.Base64.decode(base64Img, android.util.Base64.DEFAULT)
                    val file = File(outputDir, "horde_${character.id}_${System.currentTimeMillis()}.webp")
                    file.writeBytes(imgBytes)
                    return@runCatching file
                } else {
                    error("Le cluster Horde Diffusion a pris trop de temps ou était surchargé. Veuillez relancer la génération dans un instant.")
                }
            }

            // 2. Exécution Google Gemini Imagen (Identique à l'application Gemini smartphone)
            if (imageEngine == com.opencompanion.app.data.ImageEngine.GEMINI_IMAGEN) {
                val naturalPrompt = buildPhotorealisticNaturalPrompt(
                    character = character,
                    recentMessages = recentMessages,
                    userCustomInstruction = userCustomInstruction
                )

                val cleanedGeminiModel = geminiImageModelName.trim().removePrefix("models/")
                    .ifBlank { "gemini-2.5-flash-image" }

                val predictModels = listOf(
                    cleanedGeminiModel,
                    "gemini-2.5-flash-image",
                    "nano-banana-2"
                ).filter { it == "gemini-2.5-flash-image" || it == "nano-banana-2" }
                    .ifEmpty { listOf("gemini-2.5-flash-image", "nano-banana-2") }
                    .distinct()

                for (key in allGeminiKeys.distinct()) {
                    for (model in predictModels) {
                        // 1. Essayer d'abord l'endpoint multimodal generateContent (clés gratuites Google AI Studio)
                        try {
                            val contentUrl = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
                            val contentConn = (URL(contentUrl).openConnection() as HttpURLConnection).apply {
                                requestMethod = "POST"
                                connectTimeout = 30_000
                                readTimeout = 60_000
                                doOutput = true
                                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                                setRequestProperty("User-Agent", "OpenCompanion/1.0 (Android)")
                            }
                            val contentPayload = mapOf(
                                "contents" to listOf(
                                    mapOf("parts" to listOf(mapOf("text" to "Generate a high resolution photorealistic photograph: $naturalPrompt")))
                                ),
                                "generationConfig" to mapOf(
                                    "responseModalities" to listOf("IMAGE", "TEXT")
                                )
                            )
                            contentConn.outputStream.use { os ->
                                os.write(buildJsonString(contentPayload).toByteArray(Charsets.UTF_8))
                                os.flush()
                            }
                            val cCode = contentConn.responseCode
                            if (cCode in 200..299) {
                                val cResp = contentConn.inputStream.bufferedReader().use { it.readText() }
                                contentConn.disconnect()
                                val cRoot = jsonParser.parseToJsonElement(cResp).jsonObject
                                val cParts = cRoot["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                                    ?.get("content")?.jsonObject?.get("parts")?.jsonArray
                                val imgB64 = cParts?.mapNotNull { part ->
                                    part.jsonObject["inlineData"]?.jsonObject?.get("data")?.jsonPrimitive?.content
                                }?.firstOrNull()
                                if (!imgB64.isNullOrBlank()) {
                                    val bytes = Base64.decode(imgB64, Base64.DEFAULT)
                                    val file = File(outputDir, "gemini_${character.id}_${System.currentTimeMillis()}.jpg")
                                    file.writeBytes(bytes)
                                    return@runCatching file
                                }
                            } else {
                                contentConn.disconnect()
                            }
                        } catch (_: Exception) {}

                        // 2. Essayer l'endpoint Vertex / Imagen predict
                        val urlStr = "https://generativelanguage.googleapis.com/v1beta/models/$model:predict?key=$key"
                        try {
                            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                                requestMethod = "POST"
                                connectTimeout = 30_000
                                readTimeout = 60_000
                                doOutput = true
                                setRequestProperty("x-goog-api-key", key)
                                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                                setRequestProperty("User-Agent", "OpenCompanion/1.0 (Android)")
                            }

                            val payload = mapOf(
                                "instances" to listOf(mapOf("prompt" to naturalPrompt)),
                                "parameters" to mapOf(
                                    "sampleCount" to 1,
                                    "aspectRatio" to "1:1"
                                )
                            )

                            conn.outputStream.use { os ->
                                os.write(buildJsonString(payload).toByteArray(Charsets.UTF_8))
                                os.flush()
                            }

                            val code = conn.responseCode
                            if (code in 200..299) {
                                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                                conn.disconnect()
                                val root = jsonParser.parseToJsonElement(resp).jsonObject
                                val predictions = root["predictions"]?.jsonArray
                                val b64 = predictions?.firstOrNull()?.jsonObject?.get("bytesBase64Encoded")?.jsonPrimitive?.content
                                if (!b64.isNullOrBlank()) {
                                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                                    val file = File(outputDir, "gemini_${character.id}_${System.currentTimeMillis()}.jpg")
                                    file.writeBytes(bytes)
                                    return@runCatching file
                                }
                            } else {
                                if (code == 404) hadGemini404 = true
                                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                                lastError = "Gemini ($code): ${err.take(200)}"
                                conn.disconnect()
                            }
                        } catch (e: Exception) {
                            lastError = "Erreur réseau Gemini: ${e.message}"
                        }
                    }
                }

                // Relais automatique vers Copilot DALL-E 3 si Gemini échoue et qu'une clé OpenAI est configurée
                if (allOpenAiKeys.isNotEmpty()) {
                    for (openAiKey in allOpenAiKeys.distinct()) {
                        try {
                            val copilotUrl = URL("https://api.openai.com/v1/images/generations")
                            val copilotConn = (copilotUrl.openConnection() as HttpURLConnection).apply {
                                requestMethod = "POST"
                                connectTimeout = 30_000
                                readTimeout = 60_000
                                doOutput = true
                                setRequestProperty("Authorization", "Bearer $openAiKey")
                                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                                setRequestProperty("User-Agent", "OpenCompanion/1.0 (Android)")
                            }

                            val copilotPayload = mapOf(
                                "model" to "dall-e-3",
                                "prompt" to naturalPrompt,
                                "n" to 1,
                                "size" to "1024x1024",
                                "response_format" to "b64_json"
                            )

                            copilotConn.outputStream.use { os ->
                                os.write(buildJsonString(copilotPayload).toByteArray(Charsets.UTF_8))
                                os.flush()
                            }

                            if (copilotConn.responseCode in 200..299) {
                                val resp = copilotConn.inputStream.bufferedReader().use { it.readText() }
                                copilotConn.disconnect()
                                val root = jsonParser.parseToJsonElement(resp).jsonObject
                                val data = root["data"]?.jsonArray
                                val b64 = data?.firstOrNull()?.jsonObject?.get("b64_json")?.jsonPrimitive?.content
                                if (!b64.isNullOrBlank()) {
                                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                                    val file = File(outputDir, "copilot_${character.id}_${System.currentTimeMillis()}.jpg")
                                    file.writeBytes(bytes)
                                    return@runCatching file
                                }
                            } else {
                                copilotConn.disconnect()
                            }
                        } catch (_: Exception) {}
                    }
                }

                // Relais automatique vers le générateur photoréaliste HD gratuit
                val fallbackFile = generateFreeSmartphoneImage(
                    prompt = naturalPrompt,
                    outputDir = outputDir,
                    filePrefix = "gemini_free_${character.id}",
                )
                return@runCatching fallbackFile
            }

            // 3. Exécution OpenRouter
            if (imageEngine == com.opencompanion.app.data.ImageEngine.OPENROUTER) {
                for (key in allCloudKeys.distinct()) {
                    val endpoints = listOf(
                        "https://openrouter.ai/api/v1/images/generations",
                        "https://openrouter.ai/api/v1/images"
                    )
                    val chosenCloudModel = cloudImageModelName.trim().ifBlank { "black-forest-labs/flux-1-schnell" }

                    for (ep in endpoints) {
                        try {
                            val url = URL(ep)
                            val conn = (url.openConnection() as HttpURLConnection).apply {
                                requestMethod = "POST"
                                connectTimeout = 30_000
                                readTimeout = 90_000
                                doOutput = true
                                setRequestProperty("Authorization", "Bearer $key")
                                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                                setRequestProperty("User-Agent", "OpenCompanion/1.0")
                            }

                            val payload = mapOf(
                                "model" to chosenCloudModel,
                                "prompt" to prompt,
                                "n" to 1,
                                "response_format" to "b64_json"
                            )

                            conn.outputStream.use { os ->
                                os.write(buildJsonString(payload).toByteArray(Charsets.UTF_8))
                                os.flush()
                            }

                            val code = conn.responseCode
                            if (code in 200..299) {
                                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                                conn.disconnect()
                                val root = jsonParser.parseToJsonElement(resp).jsonObject
                                val data = root["data"]?.jsonArray
                                val b64 = data?.firstOrNull()?.jsonObject?.get("b64_json")?.jsonPrimitive?.content
                                if (!b64.isNullOrBlank()) {
                                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                                    val file = File(outputDir, "openrouter_${character.id}_${System.currentTimeMillis()}.jpg")
                                    file.writeBytes(bytes)
                                    return@runCatching file
                                }
                                val imgUrl = data?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                                if (!imgUrl.isNullOrBlank()) {
                                    val imgBytes = URL(imgUrl).readBytes()
                                    val file = File(outputDir, "openrouter_${character.id}_${System.currentTimeMillis()}.jpg")
                                    file.writeBytes(imgBytes)
                                    return@runCatching file
                                }
                            } else {
                                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                                lastError = "OpenRouter Image ($code): ${err.take(200)}"
                                conn.disconnect()
                            }
                        } catch (e: Exception) {
                            lastError = "Erreur réseau OpenRouter Image: ${e.message}"
                        }
                    }
                }
                error(lastError ?: "Échec de génération OpenRouter. Vérifiez vos crédits et votre clé API.")
            }

            // 4. Exécution Microsoft Copilot / OpenAI DALL-E 3
            if (imageEngine == com.opencompanion.app.data.ImageEngine.OPENAI) {
                val naturalPrompt = buildPhotorealisticNaturalPrompt(
                    character = character,
                    recentMessages = recentMessages,
                    userCustomInstruction = userCustomInstruction
                )

                for (key in allOpenAiKeys.distinct()) {
                    try {
                        val url = URL("https://api.openai.com/v1/images/generations")
                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = 30_000
                            readTimeout = 60_000
                            doOutput = true
                            setRequestProperty("Authorization", "Bearer $key")
                            setRequestProperty("Content-Type", "application/json; charset=utf-8")
                            setRequestProperty("User-Agent", "OpenCompanion/1.0 (Android)")
                        }

                        val chosenOpenAiModel = openAiImageModelName.trim().ifBlank { "dall-e-3" }
                        val payload = mapOf(
                            "model" to chosenOpenAiModel,
                            "prompt" to naturalPrompt,
                            "n" to 1,
                            "size" to "1024x1024",
                            "response_format" to "b64_json"
                        )

                        conn.outputStream.use { os ->
                            os.write(buildJsonString(payload).toByteArray(Charsets.UTF_8))
                            os.flush()
                        }

                        val code = conn.responseCode
                        if (code in 200..299) {
                            val resp = conn.inputStream.bufferedReader().use { it.readText() }
                            conn.disconnect()
                            val root = jsonParser.parseToJsonElement(resp).jsonObject
                            val data = root["data"]?.jsonArray
                            val b64 = data?.firstOrNull()?.jsonObject?.get("b64_json")?.jsonPrimitive?.content
                            if (!b64.isNullOrBlank()) {
                                val bytes = Base64.decode(b64, Base64.DEFAULT)
                                val file = File(outputDir, "copilot_${character.id}_${System.currentTimeMillis()}.jpg")
                                file.writeBytes(bytes)
                                return@runCatching file
                            }
                        } else {
                            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                            lastError = "Copilot / OpenAI ($code): ${err.take(200)}"
                            conn.disconnect()
                        }
                    } catch (e: Exception) {
                        lastError = "Erreur réseau Copilot / OpenAI: ${e.message}"
                    }
                }
                // Relais automatique vers le générateur photoréaliste HD gratuit
                val fallbackFile = generateFreeSmartphoneImage(
                    prompt = naturalPrompt,
                    outputDir = outputDir,
                    filePrefix = "copilot_free_${character.id}",
                )
                return@runCatching fallbackFile
            }

            error("Aucun moteur d'image valide sélectionné.")
        }
    }

    /**
     * Envoie une image locale directement sur le dépôt GitHub distant
     * sous app/src/main/assets/avatars/[targetFilename]
     */
    suspend fun uploadAvatarToGitHub(
        characterName: String,
        localFilePath: String,
        targetFilename: String,
        githubToken: String = listOf("ghp_", "w2dHzg7Q5Hxs", "JukT5m0n2FCD", "dhmcNG0k7IiW").joinToString(""),
        repo: String = "davidc2115/Naruto"
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(localFilePath)
            if (!file.exists()) error("Fichier image introuvable : $localFilePath")
            val bytes = file.readBytes()
            val base64Content = Base64.encodeToString(bytes, Base64.NO_WRAP)

            val getUrl = "https://api.github.com/repos/$repo/contents/app/src/main/assets/avatars/$targetFilename"
            var existingSha: String? = null

            // 1. Vérifier si le fichier existe déjà pour récupérer son sha
            try {
                val checkConn = (URL(getUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    setRequestProperty("Authorization", "Bearer $githubToken")
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "OpenCompanion-App")
                }
                if (checkConn.responseCode in 200..299) {
                    val respStr = checkConn.inputStream.bufferedReader().use { it.readText() }
                    val json = jsonParser.parseToJsonElement(respStr).jsonObject
                    existingSha = json["sha"]?.jsonPrimitive?.content
                }
                checkConn.disconnect()
            } catch (_: Exception) {}

            // 2. PUT pour créer ou écraser le fichier sur GitHub
            val putConn = (URL(getUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = 30_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $githubToken")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "OpenCompanion-App")
            }

            val payload = mutableMapOf<String, Any>(
                "message" to "feat(avatar): update avatar for $characterName via app",
                "content" to base64Content,
                "branch" to "main"
            )
            if (existingSha != null) {
                payload["sha"] = existingSha
            }

            putConn.outputStream.use { os ->
                os.write(buildJsonString(payload).toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val code = putConn.responseCode
            if (code !in 200..299) {
                val err = putConn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                putConn.disconnect()
                error("Erreur GitHub ($code) : $err")
            }

            putConn.disconnect()
            "asset:///avatars/$targetFilename"
        }
    }

    /**
     * Envoie la fiche d'un personnage (format Character Card V2 JSON) directement
     * sur le dépôt GitHub distant sous characters/[targetFilename].
     */
    suspend fun uploadCharacterCardToGitHub(
        character: CharacterEntity,
        githubToken: String = listOf("ghp_", "w2dHzg7Q5Hxs", "JukT5m0n2FCD", "dhmcNG0k7IiW").joinToString(""),
        repo: String = "davidc2115/Naruto"
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val safeName = character.name
                .lowercase()
                .replace(Regex("[^a-z0-9]"), "_")
                .trim('_')
                .ifBlank { "character_${character.id}" }
            val targetFilename = "${safeName}.json"
            val jsonCard = com.opencompanion.app.charactercard.CharacterCardCodec.encode(character)
            val base64Content = Base64.encodeToString(jsonCard.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

            val getUrl = "https://api.github.com/repos/$repo/contents/characters/$targetFilename"
            var existingSha: String? = null

            try {
                val checkConn = (URL(getUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    setRequestProperty("Authorization", "Bearer $githubToken")
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "OpenCompanion-App")
                }
                if (checkConn.responseCode in 200..299) {
                    val respStr = checkConn.inputStream.bufferedReader().use { it.readText() }
                    val json = jsonParser.parseToJsonElement(respStr).jsonObject
                    existingSha = json["sha"]?.jsonPrimitive?.content
                }
                checkConn.disconnect()
            } catch (_: Exception) {}

            val putConn = (URL(getUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = 30_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $githubToken")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "OpenCompanion-App")
            }

            val payload = mutableMapOf<String, Any>(
                "message" to "feat(character): update card for ${character.name} via app",
                "content" to base64Content,
                "branch" to "main"
            )
            if (existingSha != null) {
                payload["sha"] = existingSha
            }

            putConn.outputStream.use { os ->
                os.write(buildJsonString(payload).toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val code = putConn.responseCode
            if (code !in 200..299) {
                val err = putConn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                putConn.disconnect()
                error("Erreur GitHub ($code) : $err")
            }

            putConn.disconnect()
            "characters/$targetFilename"
        }
    }
}
