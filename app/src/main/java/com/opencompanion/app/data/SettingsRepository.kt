package com.opencompanion.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "opencompanion_settings")

/**
 * Moteur d'inférence utilisé pour générer les réponses. [AUTO] essaie Gemini Nano (AICore)
 * quand il est disponible sur l'appareil et retombe automatiquement sur llama.cpp sinon (voir
 * ChatViewModel) ; [AICORE] et [LLAMA_CPP] forcent explicitement l'un ou l'autre — voir
 * docs/MODELES_ET_AICORE.md pour le détail des compromis de chacun.
 */
enum class EngineBackend {
    CLOUD_FREE_NO_KEY,
    AUTO,
    AICORE,
    LLAMA_CPP,
    CLOUD_OPENROUTER,
    CLOUD_KOBOLD_HORDE,
    CLOUD_CUSTOM_OPENAI,
    // Backends demandés explicitement par l'utilisateur : nécessitent chacun sa propre clé API
    // (compte Groq / compte Google AI Studio), donc pas de bascule automatique de l'un vers
    // l'autre — seulement une sélection manuelle dans les réglages (voir ChatViewModel).
    CLOUD_GROQ,
    CLOUD_GEMINI
}

/** Genre déclaré par l'utilisateur, injecté dans le prompt système (voir PromptBuilder) pour
 *  que le personnage puisse s'adresser à lui de façon cohérente (accords, tournures...).
 *  [NON_PRECISE] : aucune information n'est ajoutée au prompt, le modèle reste neutre. */
enum class UserGender { NON_PRECISE, FEMME, HOMME, AUTRE }

/**
 * Profil de l'utilisateur (pas du personnage) : prénom, âge, genre — utilisés pour résoudre le
 * jeton `{{user}}` des fiches personnage et pour informer le modèle de qui lui parle, afin que
 * les réponses soient adressées de façon réaliste plutôt qu'à un "Utilisateur" générique et
 * sans visage. Entièrement optionnel : un champ laissé vide/non précisé n'apparaît simplement
 * pas dans le prompt (voir PromptBuilder.userProfileDirective).
 */
data class UserProfile(
    val name: String = "",
    val age: Int? = null,
    val gender: UserGender = UserGender.NON_PRECISE,
    /** Bio/description du persona actif (voir [UserPersonaEntity]), injectée dans le prompt
     *  système pour donner corps à l'identité endossée par l'utilisateur, au-delà du simple nom. */
    val description: String = "",
) {
    /** Nom à afficher/injecter dans le prompt : jamais vide, retombe sur un générique neutre. */
    val displayName: String get() = name.ifBlank { "Utilisateur" }
}

data class EngineSettings(
    val selectedModelPath: String? = null,
    val useGpu: Boolean = true,
    // 999 décharge la totalité des couches du réseau sur le GPU Vulkan quand le GPU est activé.
    // Décharger partiellement (ex. 20 couches) imposait un va-et-vient synchrone entre CPU et GPU
    // à chaque token sur mobile, ce qui ralentissait l'inférence de façon extrême.
    val gpuLayers: Int = 999,
    val contextSize: Int = 4096,
    // 768 plutôt que 512 : un modèle "raisonneur" (Qwen3, preset par défaut) consomme souvent
    // 150 à 250 tokens dans un bloc <think>...</think> retiré de l'affichage (voir
    // ThinkBlockFilter) avant même de commencer sa vraie réponse — avec seulement 512, la
    // réponse visible pouvait être tronquée à quelques mots, voire totalement vide.
    val maxResponseTokens: Int = 768,
    // 0.9 plutôt que 0.8 : combiné aux pénalités freq/presence désormais actives côté natif
    // (voir opencompanion_bridge.cpp), une température un peu plus haute réduit nettement la
    // tendance des petits modèles quantifiés à retomber sur les mêmes formulations d'un tour à
    // l'autre, sans basculer dans l'incohérence pour un modèle de cette taille.
    val temperature: Float = 0.9f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val repeatPenalty: Float = 1.1f,
    val threads: Int = 0, // 0 = laisser InferenceEngine choisir une valeur recommandée
    // AUTO (Gemini Nano si dispo, sinon llama.cpp local) plutôt que CLOUD_FREE_NO_KEY par défaut :
    // ce dernier envoie silencieusement les messages à des services anonymes tiers (Pollinations,
    // KoboldAI Horde) sans que l'utilisateur ait rien configuré ni choisi. Les backends cloud
    // (Groq, Gemini, ou un provider personnalisé) restent disponibles mais doivent être activés
    // explicitement dans les réglages, avec la clé API de l'utilisateur.
    val enginePreference: EngineBackend = EngineBackend.AUTO,
    // false par défaut : le mode NSFW doit être un choix explicite de l'utilisateur (opt-in),
    // jamais activé silencieusement de base.
    val allowNsfwMode: Boolean = false,
    val cloudApiKey: String = "",
    val cloudModelName: String = "Hermes-3-Llama-3.1-8B",
    val cloudEndpointUrl: String = "https://openrouter.ai/api/v1/chat/completions",
    val groqApiKey: String = "",
    val groqModelName: String = "llama-3.3-70b-versatile",
    val geminiApiKey: String = "",
    val geminiModelName: String = "gemini-2.0-flash",
)

/**
 * Persiste les réglages moteur via DataStore. [gpuDisabledAfterFailure] est distinct de
 * [EngineSettings.useGpu] : c'est un repli automatique et silencieux déclenché après un
 * plantage du backend Vulkan (voir ChatViewModel), alors que useGpu reflète le choix explicite
 * de l'utilisateur dans les réglages.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val MODEL_PATH = stringPreferencesKey("selected_model_path")
        val USE_GPU = booleanPreferencesKey("use_gpu")
        val GPU_LAYERS = intPreferencesKey("gpu_layers")
        val GPU_DISABLED_AFTER_FAILURE = booleanPreferencesKey("gpu_disabled_after_failure")
        val CONTEXT_SIZE = intPreferencesKey("context_size")
        val MAX_TOKENS = intPreferencesKey("max_response_tokens")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val TOP_K = intPreferencesKey("top_k")
        val TOP_P = floatPreferencesKey("top_p")
        val REPEAT_PENALTY = floatPreferencesKey("repeat_penalty")
        val THREADS = intPreferencesKey("threads")
        val ENGINE_BACKEND = stringPreferencesKey("engine_backend")
        val ALLOW_NSFW_MODE = booleanPreferencesKey("allow_nsfw_mode")
        val USER_NAME = stringPreferencesKey("user_profile_name")
        val USER_AGE = intPreferencesKey("user_profile_age")
        val USER_GENDER = stringPreferencesKey("user_profile_gender")
        val CLOUD_API_KEY = stringPreferencesKey("cloud_api_key")
        val CLOUD_MODEL_NAME = stringPreferencesKey("cloud_model_name")
        val CLOUD_ENDPOINT_URL = stringPreferencesKey("cloud_endpoint_url")
        val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        val GROQ_MODEL_NAME = stringPreferencesKey("groq_model_name")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL_NAME = stringPreferencesKey("gemini_model_name")
    }

    val settings: Flow<EngineSettings> = context.dataStore.data.map { prefs ->
        EngineSettings(
            selectedModelPath = prefs[Keys.MODEL_PATH],
            useGpu = (prefs[Keys.USE_GPU] ?: true) && !(prefs[Keys.GPU_DISABLED_AFTER_FAILURE] ?: false),
            gpuLayers = prefs[Keys.GPU_LAYERS] ?: 999,
            contextSize = prefs[Keys.CONTEXT_SIZE] ?: 4096,
            maxResponseTokens = prefs[Keys.MAX_TOKENS] ?: 768,
            temperature = prefs[Keys.TEMPERATURE] ?: 0.9f,
            topK = prefs[Keys.TOP_K] ?: 40,
            topP = prefs[Keys.TOP_P] ?: 0.95f,
            repeatPenalty = prefs[Keys.REPEAT_PENALTY] ?: 1.1f,
            threads = prefs[Keys.THREADS] ?: 0,
            enginePreference = prefs[Keys.ENGINE_BACKEND]?.let {
                runCatching { EngineBackend.valueOf(it) }.getOrNull()
            } ?: EngineBackend.AUTO,
            allowNsfwMode = prefs[Keys.ALLOW_NSFW_MODE] ?: false,
            cloudApiKey = prefs[Keys.CLOUD_API_KEY] ?: "",
            cloudModelName = prefs[Keys.CLOUD_MODEL_NAME] ?: "nousresearch/hermes-3-llama-3.1-8b:free",
            cloudEndpointUrl = prefs[Keys.CLOUD_ENDPOINT_URL] ?: "https://openrouter.ai/api/v1/chat/completions",
            groqApiKey = prefs[Keys.GROQ_API_KEY] ?: "",
            groqModelName = prefs[Keys.GROQ_MODEL_NAME] ?: "llama-3.3-70b-versatile",
            geminiApiKey = prefs[Keys.GEMINI_API_KEY] ?: "",
            geminiModelName = prefs[Keys.GEMINI_MODEL_NAME] ?: "gemini-2.0-flash",
        )
    }

    suspend fun setSelectedModelPath(path: String?) = context.dataStore.edit {
        if (path == null) it.remove(Keys.MODEL_PATH) else it[Keys.MODEL_PATH] = path
    }

    suspend fun setUseGpu(enabled: Boolean) = context.dataStore.edit {
        it[Keys.USE_GPU] = enabled
        if (enabled) it[Keys.GPU_DISABLED_AFTER_FAILURE] = false
    }

    /** [value] est borné à [0, 999] : 0 = CPU pur, 999 = toutes les couches sur GPU (llama.cpp
     *  borne de toute façon en interne au nombre réel de couches du modèle chargé). */
    suspend fun setGpuLayers(value: Int) = context.dataStore.edit {
        it[Keys.GPU_LAYERS] = value.coerceIn(0, 999)
    }

    /** Appelé après un échec de génération imputable au backend GPU : désactive le GPU sans
     *  toucher à la préférence explicite de l'utilisateur, pour qu'un futur pilote/appareil
     *  puisse la réactiver simplement en rouvrant les réglages. */
    suspend fun markGpuUnstable() = context.dataStore.edit {
        it[Keys.GPU_DISABLED_AFTER_FAILURE] = true
    }

    suspend fun setContextSize(size: Int) = context.dataStore.edit { it[Keys.CONTEXT_SIZE] = size }
    suspend fun setMaxResponseTokens(tokens: Int) = context.dataStore.edit { it[Keys.MAX_TOKENS] = tokens }
    suspend fun setTemperature(value: Float) = context.dataStore.edit { it[Keys.TEMPERATURE] = value }
    suspend fun setTopK(value: Int) = context.dataStore.edit { it[Keys.TOP_K] = value }
    suspend fun setTopP(value: Float) = context.dataStore.edit { it[Keys.TOP_P] = value }
    suspend fun setRepeatPenalty(value: Float) = context.dataStore.edit { it[Keys.REPEAT_PENALTY] = value }
    suspend fun setThreads(value: Int) = context.dataStore.edit { it[Keys.THREADS] = value }
    suspend fun setEnginePreference(value: EngineBackend) = context.dataStore.edit { it[Keys.ENGINE_BACKEND] = value.name }
    suspend fun setAllowNsfwMode(enabled: Boolean) = context.dataStore.edit { it[Keys.ALLOW_NSFW_MODE] = enabled }

    suspend fun setCloudApiKey(key: String) = context.dataStore.edit {
        if (key.isBlank()) it.remove(Keys.CLOUD_API_KEY) else it[Keys.CLOUD_API_KEY] = key.trim()
    }
    suspend fun setCloudModelName(model: String) = context.dataStore.edit {
        if (model.isBlank()) it.remove(Keys.CLOUD_MODEL_NAME) else it[Keys.CLOUD_MODEL_NAME] = model.trim()
    }
    suspend fun setCloudEndpointUrl(url: String) = context.dataStore.edit {
        if (url.isBlank()) it.remove(Keys.CLOUD_ENDPOINT_URL) else it[Keys.CLOUD_ENDPOINT_URL] = url.trim()
    }

    suspend fun setGroqApiKey(key: String) = context.dataStore.edit {
        if (key.isBlank()) it.remove(Keys.GROQ_API_KEY) else it[Keys.GROQ_API_KEY] = key.trim()
    }
    suspend fun setGroqModelName(model: String) = context.dataStore.edit {
        if (model.isBlank()) it.remove(Keys.GROQ_MODEL_NAME) else it[Keys.GROQ_MODEL_NAME] = model.trim()
    }
    suspend fun setGeminiApiKey(key: String) = context.dataStore.edit {
        if (key.isBlank()) it.remove(Keys.GEMINI_API_KEY) else it[Keys.GEMINI_API_KEY] = key.trim()
    }
    suspend fun setGeminiModelName(model: String) = context.dataStore.edit {
        if (model.isBlank()) it.remove(Keys.GEMINI_MODEL_NAME) else it[Keys.GEMINI_MODEL_NAME] = model.trim()
    }

    val userProfile: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        UserProfile(
            name = prefs[Keys.USER_NAME] ?: "",
            age = prefs[Keys.USER_AGE]?.takeIf { it > 0 },
            gender = prefs[Keys.USER_GENDER]?.let {
                runCatching { UserGender.valueOf(it) }.getOrNull()
            } ?: UserGender.NON_PRECISE,
        )
    }

    suspend fun setUserName(value: String) = context.dataStore.edit {
        if (value.isBlank()) it.remove(Keys.USER_NAME) else it[Keys.USER_NAME] = value.trim()
    }

    /** [value] == null (ou <= 0) efface l'âge renseigné plutôt que de stocker une valeur invalide. */
    suspend fun setUserAge(value: Int?) = context.dataStore.edit {
        if (value == null || value <= 0) it.remove(Keys.USER_AGE) else it[Keys.USER_AGE] = value
    }

    suspend fun setUserGender(value: UserGender) = context.dataStore.edit { it[Keys.USER_GENDER] = value.name }
}
