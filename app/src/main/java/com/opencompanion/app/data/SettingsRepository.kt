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
enum class EngineBackend { AUTO, AICORE, LLAMA_CPP }

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
) {
    /** Nom à afficher/injecter dans le prompt : jamais vide, retombe sur un générique neutre. */
    val displayName: String get() = name.ifBlank { "Utilisateur" }
}

data class EngineSettings(
    val selectedModelPath: String? = null,
    val useGpu: Boolean = true,
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
    val enginePreference: EngineBackend = EngineBackend.AUTO,
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
        val GPU_DISABLED_AFTER_FAILURE = booleanPreferencesKey("gpu_disabled_after_failure")
        val CONTEXT_SIZE = intPreferencesKey("context_size")
        val MAX_TOKENS = intPreferencesKey("max_response_tokens")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val TOP_K = intPreferencesKey("top_k")
        val TOP_P = floatPreferencesKey("top_p")
        val REPEAT_PENALTY = floatPreferencesKey("repeat_penalty")
        val THREADS = intPreferencesKey("threads")
        val ENGINE_BACKEND = stringPreferencesKey("engine_backend")
        val USER_NAME = stringPreferencesKey("user_profile_name")
        val USER_AGE = intPreferencesKey("user_profile_age")
        val USER_GENDER = stringPreferencesKey("user_profile_gender")
    }

    val settings: Flow<EngineSettings> = context.dataStore.data.map { prefs ->
        EngineSettings(
            selectedModelPath = prefs[Keys.MODEL_PATH],
            useGpu = (prefs[Keys.USE_GPU] ?: true) && !(prefs[Keys.GPU_DISABLED_AFTER_FAILURE] ?: false),
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
        )
    }

    suspend fun setSelectedModelPath(path: String?) = context.dataStore.edit {
        if (path == null) it.remove(Keys.MODEL_PATH) else it[Keys.MODEL_PATH] = path
    }

    suspend fun setUseGpu(enabled: Boolean) = context.dataStore.edit {
        it[Keys.USE_GPU] = enabled
        if (enabled) it[Keys.GPU_DISABLED_AFTER_FAILURE] = false
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
