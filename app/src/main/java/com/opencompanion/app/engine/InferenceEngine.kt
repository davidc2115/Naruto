package com.opencompanion.app.engine

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Un tour de dialogue générique, indépendant du format texte final (voir applyChatTemplate). */
data class ChatTurn(val role: String, val content: String)

data class GenerationParams(
    val prompt: String,
    val maxTokens: Int = 768,
    val temperature: Float = 0.8f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val repeatPenalty: Float = 1.1f,
    val seed: Long = -1L,
)

sealed class GenerationEvent {
    data class Token(val text: String) : GenerationEvent()
    data object Done : GenerationEvent()
    data class Error(val message: String) : GenerationEvent()
    /** La génération a échoué côté backend GPU (Vulkan) : l'appelant peut recharger en CPU. */
    data object GpuFailure : GenerationEvent()
}

/**
 * Façade haut niveau au-dessus de [LlamaBridge] : gestion du cycle de vie du
 * modèle chargé, choix CPU/GPU, et streaming de texte déjà décodé en UTF-8
 * (voir [Utf8StreamDecoder]).
 *
 * Une seule instance vit pour toute l'app (voir OpenCompanionApplication) :
 * changer de personnage ne recharge PAS le modèle, seul le prompt change.
 */
class InferenceEngine(private val context: Context) {

    private var handle: Long = 0
    private var loadedModelPath: String? = null
    private var loadedWithGpu: Boolean = false
    private val lifecycleMutex = Mutex()

    /** true si CE build a été compilé avec le backend Vulkan (indépendant du GPU réel de l'appareil). */
    val vulkanCompiledIn: Boolean by lazy { LlamaBridge.nativeHasVulkanSupport() }

    /** true si le système Android annonce un GPU compatible Vulkan. Ne garantit pas
     *  qu'aucun pilote ne plantera à l'exécution — voir docs/VULKAN_NOTES.md. */
    fun deviceReportsVulkanHardware(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)

    val isModelLoaded: Boolean get() = handle != 0L
    val currentModelPath: String? get() = loadedModelPath
    val isUsingGpu: Boolean get() = loadedWithGpu

    fun recommendedThreadCount(): Int =
        (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 8)

    /**
     * Charge [modelPath] si nécessaire (ne recharge pas si déjà chargé avec les mêmes réglages).
     * @param nGpuLayers -1 = décharger autant de couches que possible sur le GPU, 0 = CPU pur.
     */
    suspend fun ensureModelLoaded(
        modelPath: String,
        contextSize: Int,
        useGpu: Boolean,
        threads: Int = recommendedThreadCount(),
    ): Result<Unit> = lifecycleMutex.withLock {
        val wantGpu = useGpu && vulkanCompiledIn
        if (handle != 0L && loadedModelPath == modelPath && loadedWithGpu == wantGpu) {
            return@withLock Result.success(Unit)
        }
        unloadLocked()

        // 999 : au-delà du nombre réel de couches du modèle, llama.cpp borne automatiquement —
        // c'est la convention standard pour "décharger tout ce qui est possible sur le GPU".
        val nGpuLayers = if (wantGpu) 999 else 0
        val newHandle = withContext(Dispatchers.IO) {
            LlamaBridge.nativeLoadModel(modelPath, contextSize, nGpuLayers, threads)
        }
        if (newHandle == 0L) {
            return@withLock Result.failure(
                IllegalStateException("Échec du chargement du modèle (fichier invalide, ou mémoire insuffisante pour ce contexte)")
            )
        }
        handle = newHandle
        loadedModelPath = modelPath
        loadedWithGpu = wantGpu
        Result.success(Unit)
    }

    suspend fun unload() = lifecycleMutex.withLock { unloadLocked() }

    private fun unloadLocked() {
        if (handle != 0L) {
            LlamaBridge.nativeFreeModel(handle)
            handle = 0
        }
        loadedModelPath = null
        loadedWithGpu = false
    }

    /** Nombre approximatif de tokens que consommerait [text] dans le contexte actuel. */
    fun tokenCount(text: String): Int {
        val h = handle
        if (h == 0L || text.isEmpty()) return 0
        return LlamaBridge.nativeTokenCount(h, text.toByteArray(Charsets.UTF_8))
    }

    /**
     * Formate [turns] (rôles "system"/"user"/"assistant") selon le patron de dialogue propre
     * au modèle chargé. Renvoie null si aucun modèle n'est chargé ou si le moteur natif n'a pas
     * pu appliquer de patron — dans ce cas, [com.opencompanion.app.prompt.PromptBuilder] retombe
     * sur un format générique.
     */
    fun applyChatTemplate(turns: List<ChatTurn>, addAssistant: Boolean = true): String? {
        val h = handle
        if (h == 0L || turns.isEmpty()) return null
        val roles = turns.map { it.role }.toTypedArray()
        val contents = turns.map { it.content.toByteArray(Charsets.UTF_8) }.toTypedArray()
        val bytes = LlamaBridge.nativeApplyChatTemplate(h, roles, contents, addAssistant) ?: return null
        return bytes.toString(Charsets.UTF_8)
    }

    /** Demande l'arrêt anticipé d'une génération en cours (bouton Stop). */
    fun requestStop() {
        val h = handle
        if (h != 0L) LlamaBridge.nativeAbortGeneration(h)
    }

    /**
     * Lance une génération et émet le texte au fur et à mesure. La collecte peut être annulée
     * normalement (annulation de coroutine) ; pour une interruption immédiate côté moteur natif,
     * appeler [requestStop] en plus (le thread natif ne connaît pas l'annulation Kotlin tant
     * qu'il n'a pas rendu la main entre deux tokens).
     */
    fun generate(params: GenerationParams): Flow<GenerationEvent> = channelFlow {
        val currentHandle = handle
        if (currentHandle == 0L) {
            send(GenerationEvent.Error("Aucun modèle chargé"))
            return@channelFlow
        }

        val decoder = Utf8StreamDecoder()
        // Retire les blocs <think>...</think> (modèles "raisonneurs" type Qwen3) avant que le
        // texte n'atteigne l'UI/l'historique — voir ThinkBlockFilter pour le contexte complet.
        val thinkFilter = ThinkBlockFilter()
        val callback = LlamaBridge.TokenCallback { bytes ->
            val visible = thinkFilter.push(decoder.push(bytes))
            if (visible.isNotEmpty()) trySend(GenerationEvent.Token(visible))
            isActive
        }

        val resultCode = withContext(Dispatchers.Default) {
            LlamaBridge.nativeGenerate(
                currentHandle,
                params.prompt.toByteArray(Charsets.UTF_8),
                params.maxTokens,
                params.temperature,
                params.topK,
                params.topP,
                params.repeatPenalty,
                params.seed,
                callback,
            )
        }

        val tail = thinkFilter.push(decoder.flush()) + thinkFilter.flush()
        if (tail.isNotEmpty()) send(GenerationEvent.Token(tail))

        when {
            resultCode == 0 || resultCode == 1 -> send(GenerationEvent.Done)
            resultCode <= -6 -> send(GenerationEvent.GpuFailure)
            else -> send(GenerationEvent.Error("Erreur du moteur d'inférence (code $resultCode)"))
        }
    }
}
