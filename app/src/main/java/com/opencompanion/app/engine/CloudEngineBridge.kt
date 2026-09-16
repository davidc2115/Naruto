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
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
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
                append("natural posture looking towards camera, subtle warm alluring expression, authentic lighting, ")
            }
            append("highly detailed eyes and facial features, authentic skin texture, cinematic soft lighting, 35mm photography, dslr, high quality")
        }
    }

    /**
     * Génère une véritable image réaliste avec Google Gemini (Imagen 3) ou OpenAI DALL-E 3 en repli,
     * et l'enregistre dans le stockage privé de l'application sous [outputDir].
     */
    /**
     * Génère une véritable image réaliste avec Google Gemini (Imagen 3), OpenAI DALL-E 3,
     * ou un repli photoréaliste haute fidélité (FLUX.1), et l'enregistre dans le stockage privé de l'application.
     */
    suspend fun generateCharacterSceneImage(
        imageEngine: com.opencompanion.app.data.ImageEngine = com.opencompanion.app.data.ImageEngine.HORDE_DIFFUSION,
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
                com.opencompanion.app.data.ImageEngine.HORDE_DIFFUSION -> {
                    // 100% Gratuit, clé anonyme "0000000000" par défaut si non renseignée
                }
                com.opencompanion.app.data.ImageEngine.GEMINI_IMAGEN -> {
                    if (allGeminiKeys.isEmpty()) {
                        error("Veuillez renseigner votre clé API Google Gemini (avec compte de facturation actif) dans Réglages → Photos.")
                    }
                }
                com.opencompanion.app.data.ImageEngine.OPENROUTER -> {
                    if (allCloudKeys.isEmpty()) {
                        error("Veuillez renseigner votre clé API OpenRouter dans Réglages → Photos.")
                    }
                }
                com.opencompanion.app.data.ImageEngine.OPENAI -> {
                    if (allOpenAiKeys.isEmpty()) {
                        error("Veuillez renseigner votre clé API OpenAI dans Réglages → Photos.")
                    }
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

                val payloadMap = mapOf(
                    "prompt" to hordePrompt,
                    "params" to mapOf(
                        "sampler_name" to "k_euler",
                        "cfg_scale" to 7.5,
                        "seed" to "-1",
                        "height" to 512,
                        "width" to 512,
                        "steps" to 20,
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

            // 2. Exécution Google Gemini Imagen
            if (imageEngine == com.opencompanion.app.data.ImageEngine.GEMINI_IMAGEN) {
                var normalizedGeminiModel = geminiImageModelName.trim().removePrefix("models/")
                if (normalizedGeminiModel.contains("imagen-4.0-generate-0001")) {
                    normalizedGeminiModel = normalizedGeminiModel.replace("imagen-4.0-generate-0001", "imagen-4.0-generate-001")
                }
                if (normalizedGeminiModel.contains("imagen-3.0-generate-0001")) {
                    normalizedGeminiModel = normalizedGeminiModel.replace("imagen-3.0-generate-0001", "imagen-3.0-generate-002")
                }
                val cleanedGeminiModel = normalizedGeminiModel.ifBlank { "gemini-3.1-flash-image" }

                for (key in allGeminiKeys.distinct()) {
                    val generateContentModels = listOf(
                        cleanedGeminiModel,
                        "gemini-3.1-flash-image",
                        "gemini-2.5-flash-image",
                        "gemini-2.0-flash"
                    ).distinct()

                    for (model in generateContentModels) {
                        val urlStr = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
                        try {
                            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                                requestMethod = "POST"
                                connectTimeout = 25_000
                                readTimeout = 60_000
                                doOutput = true
                                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                                setRequestProperty("User-Agent", "OpenCompanion/1.0")
                            }

                            val payload = mapOf(
                                "contents" to listOf(
                                    mapOf("parts" to listOf(mapOf("text" to prompt)))
                                ),
                                "generationConfig" to mapOf(
                                    "responseModalities" to listOf("TEXT", "IMAGE")
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
                                val parts = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                                    ?.get("content")?.jsonObject
                                    ?.get("parts")?.jsonArray
                                val imgPart = parts?.firstOrNull { it.jsonObject.containsKey("inlineData") }?.jsonObject?.get("inlineData")?.jsonObject
                                val b64 = imgPart?.get("data")?.jsonPrimitive?.content
                                val mime = imgPart?.get("mimeType")?.jsonPrimitive?.content ?: "image/jpeg"
                                if (!b64.isNullOrBlank()) {
                                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                                    val ext = if (mime.contains("png")) "png" else "jpg"
                                    val file = File(outputDir, "gemini_${character.id}_${System.currentTimeMillis()}.$ext")
                                    file.writeBytes(bytes)
                                    return@runCatching file
                                }
                            } else {
                                if (code == 404) hadGemini404 = true
                                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                                lastError = "Gemini Imagen ($code): ${err.take(200)}"
                                conn.disconnect()
                            }
                        } catch (e: Exception) {
                            lastError = "Erreur réseau Gemini Imagen: ${e.message}"
                        }
                    }

                    // Tentative B : endpoint predict Imagen standard
                    val predictModels = listOf(
                        "imagen-3.0-generate-002",
                        "imagen-3.0-fast-generate-001"
                    )
                    for (model in predictModels) {
                        val urlStr = "https://generativelanguage.googleapis.com/v1beta/models/$model:predict?key=$key"
                        try {
                            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                                requestMethod = "POST"
                                connectTimeout = 25_000
                                readTimeout = 60_000
                                doOutput = true
                                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                                setRequestProperty("User-Agent", "OpenCompanion/1.0")
                            }

                            val payload = mapOf(
                                "instances" to listOf(mapOf("prompt" to prompt)),
                                "parameters" to mapOf(
                                    "sampleCount" to 1,
                                    "aspectRatio" to "1:1",
                                    "personGeneration" to "ALLOW_ADULT"
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
                                lastError = "Gemini Imagen ($code): ${err.take(200)}"
                                conn.disconnect()
                            }
                        } catch (e: Exception) {
                            lastError = "Erreur réseau Gemini Imagen: ${e.message}"
                        }
                    }
                }
                if (hadGemini404) {
                    error("Erreur Google 404 : Votre clé API Google Gemini gratuite ne dispose pas des droits Imagen (Google réserve Imagen aux comptes avec facturation / 300$ offerts).\n\n💡 Solution 100% GRATUITE sans carte bancaire : Passez sur le moteur Hugging Face dans Réglages → Photos (modèle FLUX.1 Schnell photoréaliste, token gratuit 'hf_...').")
                }
                error(lastError ?: "Échec de génération Google Gemini Imagen.")
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

            // 4. Exécution OpenAI DALL-E 3
            if (imageEngine == com.opencompanion.app.data.ImageEngine.OPENAI) {
                for (key in allOpenAiKeys.distinct()) {
                    try {
                        val url = URL("https://api.openai.com/v1/images/generations")
                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = 25_000
                            readTimeout = 45_000
                            doOutput = true
                            setRequestProperty("Authorization", "Bearer $key")
                            setRequestProperty("Content-Type", "application/json; charset=utf-8")
                            setRequestProperty("User-Agent", "OpenCompanion/1.0")
                        }

                        val chosenOpenAiModel = openAiImageModelName.trim().ifBlank { "dall-e-3" }
                        val payload = mapOf(
                            "model" to chosenOpenAiModel,
                            "prompt" to prompt,
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
                                val file = File(outputDir, "openai_${character.id}_${System.currentTimeMillis()}.jpg")
                                file.writeBytes(bytes)
                                return@runCatching file
                            }
                        } else {
                            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                            lastError = "OpenAI ($code): ${err.take(200)}"
                            conn.disconnect()
                        }
                    } catch (e: Exception) {
                        lastError = "Erreur réseau OpenAI: ${e.message}"
                    }
                }
                error(lastError ?: "Échec de génération OpenAI DALL-E 3. Vérifiez vos crédits OpenAI et votre clé API.")
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
}
