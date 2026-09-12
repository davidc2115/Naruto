package com.opencompanion.app.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** Découpe une chaîne de clés API en liste utilisable : une clé par ligne (ou séparées par des
 *  virgules), lignes vides ignorées — permet de renseigner plusieurs clés (plusieurs comptes
 *  gratuits) pour un même provider et de tourner automatiquement dessus en cas de quota atteint
 *  (voir [CloudEngineBridge.generateWithKeyRotation]). */
fun parseApiKeys(raw: String): List<String> =
    raw.split('\n', ',').map { it.trim() }.filter { it.isNotBlank() }

private val HTTP_STATUS_IN_MESSAGE = Regex("\\((\\d{3})\\)")

/** true si [message] (formaté par les fonctions generate* de [CloudEngineBridge], ex.
 *  "Gemini (429) : ...") correspond à une erreur d'authentification ou de quota — les seuls cas
 *  où réessayer avec une AUTRE clé API a une chance de résoudre le problème (401/403 = clé
 *  invalide/refusée, 429 = quota épuisé). Une 404 (mauvais nom de modèle/URL) ou une erreur
 *  réseau échouerait de la même façon avec n'importe quelle clé : inutile de toutes les essayer. */
private fun isKeyRotationCandidate(message: String): Boolean {
    val code = HTTP_STATUS_IN_MESSAGE.find(message)?.groupValues?.get(1)?.toIntOrNull() ?: return false
    return code == 401 || code == 403 || code == 429
}

/**
 * Moteur d'inférence Cloud : effectue les requêtes en streaming ou en polling vers des providers
 * d'IA Cloud gratuits, illimités et sans censure (OpenRouter, KoboldAI Horde, Pollinations, ou serveur compatible OpenAI).
 */
class CloudEngineBridge {

    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Essaie [attempt] successivement avec chaque clé de [apiKeys] tant que l'échec est une
     * erreur d'authentification/quota (voir [isKeyRotationCandidate]) et qu'AUCUN token n'a
     * encore été reçu — pour que plusieurs clés API gratuites du même provider servent de
     * réserve les unes des autres, sans surveillance manuelle à chaque quota atteint. Une fois
     * qu'un token a été émis, on ne bascule plus : rejouer la génération sur une autre clé
     * dupliquerait/mélangerait une réponse déjà commencée plutôt que de vraiment la réparer.
     * [apiKeys] vide = une seule tentative avec une clé vide (comportement identique à avant
     * l'introduction du multi-clés, pour les backends qui tolèrent une clé absente).
     */
    fun generateWithKeyRotation(
        apiKeys: List<String>,
        attempt: (String) -> Flow<GenerationEvent>,
    ): Flow<GenerationEvent> = channelFlow {
        val keys = apiKeys.ifEmpty { listOf("") }
        var lastErrorMessage: String? = null
        for ((index, key) in keys.withIndex()) {
            var tokenEmitted = false
            var shouldTryNextKey = false
            attempt(key).collect { event ->
                when (event) {
                    is GenerationEvent.Token -> {
                        tokenEmitted = true
                        send(event)
                    }
                    is GenerationEvent.Error -> {
                        lastErrorMessage = event.message
                        if (!tokenEmitted && index < keys.lastIndex && isKeyRotationCandidate(event.message)) {
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
     * N'exige aucun compte ni clé : utilise les services publics anonymes débridés (Pollinations ➔ KoboldHorde anonyme).
     */
    fun generateFreeNoKeyCloud(
        turns: List<ChatTurn>,
        maxTokens: Int = 768,
        temperature: Float = 0.8f,
    ): Flow<GenerationEvent> = channelFlow {
        var success = false

        // 1. Tentative via Pollinations AI (Accès anonyme public sans clé)
        try {
            val url = URL("https://text.pollinations.ai/openai")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 OpenCompanion/1.0"
                )
            }

            val messagesList = turns.map { turn -> mapOf("role" to turn.role, "content" to turn.content) }
            val payload = mapOf(
                "model" to "openai",
                "messages" to messagesList,
                "temperature" to temperature,
                "max_tokens" to maxTokens
            )

            val jsonBody = buildJsonString(payload)
            conn.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            if (conn.responseCode in 200..299) {
                val respText = conn.inputStream.bufferedReader().use { it.readText() }
                val extractedText = parseJsonTextOrChoice(respText)
                if (extractedText.isNotBlank()) {
                    send(GenerationEvent.Token(extractedText))
                    send(GenerationEvent.Done)
                    success = true
                }
            }
            conn.disconnect()
        } catch (_: Exception) {}

        if (success) return@channelFlow

        // 2. Repli automatique sur KoboldAI Horde (Clé publique anonyme 0000000000 - 100% Sans Clé / Sans Inscription)
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

            val messagesList = turns.map { turn ->
                mapOf("role" to turn.role, "content" to turn.content)
            }

            val payloadMap = mutableMapOf<String, Any>(
                "model" to modelName.ifBlank { "nousresearch/hermes-3-llama-3.1-8b:free" },
                "messages" to messagesList,
                "temperature" to temperature,
                "max_tokens" to maxTokens,
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
    ): Flow<GenerationEvent> = channelFlow {
        var connection: HttpURLConnection? = null
        try {
            if (apiKey.isBlank()) {
                send(GenerationEvent.Error("Aucune clé API Gemini configurée (Réglages → Moteur d'IA)."))
                return@channelFlow
            }
            val model = modelName.ifBlank { "gemini-2.0-flash" }
            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent" +
                    "?alt=sse&key=${apiKey.trim()}"
            )
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }

            val systemText = turns.firstOrNull { it.role == "system" }?.content
            val contents = turns.filter { it.role != "system" }.map { turn ->
                mapOf(
                    "role" to if (turn.role == "assistant") "model" else "user",
                    "parts" to listOf(mapOf("text" to turn.content)),
                )
            }
            val payloadMap = mutableMapOf<String, Any>(
                "contents" to contents,
                "generationConfig" to mapOf(
                    "temperature" to temperature,
                    "maxOutputTokens" to maxTokens,
                ),
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
                send(GenerationEvent.Error("Gemini ($responseCode) : ${parseErrorMessage(errorText, responseCode)}"))
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
    ): Flow<GenerationEvent> = generateWithKeyRotation(parseApiKeys(apiKey)) { key ->
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
        val candidates = root["candidates"]?.jsonArray ?: return null
        if (candidates.isEmpty()) return null
        val content = candidates[0].jsonObject["content"]?.jsonObject ?: return null
        val parts = content["parts"]?.jsonArray ?: return null
        return parts.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
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
            msg ?: "Erreur HTTP $code"
        } catch (_: Exception) {
            "Erreur HTTP $code"
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
}
