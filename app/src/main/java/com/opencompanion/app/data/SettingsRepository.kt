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

data class EngineSettings(
    val selectedModelPath: String? = null,
    val useGpu: Boolean = true,
    val contextSize: Int = 4096,
    val maxResponseTokens: Int = 512,
    val temperature: Float = 0.8f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val repeatPenalty: Float = 1.1f,
    val threads: Int = 0, // 0 = laisser InferenceEngine choisir une valeur recommandée
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
    }

    val settings: Flow<EngineSettings> = context.dataStore.data.map { prefs ->
        EngineSettings(
            selectedModelPath = prefs[Keys.MODEL_PATH],
            useGpu = (prefs[Keys.USE_GPU] ?: true) && !(prefs[Keys.GPU_DISABLED_AFTER_FAILURE] ?: false),
            contextSize = prefs[Keys.CONTEXT_SIZE] ?: 4096,
            maxResponseTokens = prefs[Keys.MAX_TOKENS] ?: 512,
            temperature = prefs[Keys.TEMPERATURE] ?: 0.8f,
            topK = prefs[Keys.TOP_K] ?: 40,
            topP = prefs[Keys.TOP_P] ?: 0.95f,
            repeatPenalty = prefs[Keys.REPEAT_PENALTY] ?: 1.1f,
            threads = prefs[Keys.THREADS] ?: 0,
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
}
