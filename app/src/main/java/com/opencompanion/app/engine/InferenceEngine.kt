package com.opencompanion.app.engine

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
    private var loadedGpuLayers: Int = 0
    private val lifecycleMutex = Mutex()

    // Scope de vie du moteur (survit à l'annulation d'un chargement en timeout, voir plus bas) :
    // une seule instance d'InferenceEngine vit pour toute l'app, jamais fermée explicitement.
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** true si CE build a été compilé avec le backend Vulkan (indépendant du GPU réel de l'appareil). */
    val vulkanCompiledIn: Boolean by lazy { LlamaBridge.nativeHasVulkanSupport() }

    /** true si le système Android annonce un GPU compatible Vulkan. Ne garantit pas
     *  qu'aucun pilote ne plantera à l'exécution — voir docs/VULKAN_NOTES.md. */
    fun deviceReportsVulkanHardware(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)

    val isModelLoaded: Boolean get() = handle != 0L
    val currentModelPath: String? get() = loadedModelPath
    val isUsingGpu: Boolean get() = loadedWithGpu

    /**
     * Nombre de threads CPU recommandés. Sur architecture mobile ARM (big.LITTLE), cibler 4 threads
     * (les cœurs de performance) évite la contention avec les cœurs d'efficacité lents, réduit
     * la surchauffe CPU et offre les meilleures performances en mode hybride Vulkan+CPU.
     */
    fun recommendedThreadCount(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return if (cores <= 4) cores.coerceAtLeast(2) else (cores / 2).coerceIn(4, 6)
    }

    /**
     * Charge [modelPath] si nécessaire (ne recharge pas si déjà chargé avec les mêmes réglages).
     * @param gpuLayers nombre de couches à décharger sur le GPU quand [useGpu] est vrai : 0 = CPU
     *   pur, une valeur inférieure au nombre réel de couches du modèle donne un mode hybride
     *   CPU+GPU (llama.cpp répartit le réseau entre les deux), 999 (ou plus) décharge tout sur le
     *   GPU — llama.cpp borne automatiquement au nombre réel de couches, c'est la convention
     *   standard pour "autant que possible". Voir [com.opencompanion.app.data.SettingsRepository.gpuLayers].
     */
    suspend fun ensureModelLoaded(
        modelPath: String,
        contextSize: Int,
        useGpu: Boolean,
        gpuLayers: Int = 999,
        threads: Int = recommendedThreadCount(),
    ): Result<Unit> = lifecycleMutex.withLock {
        val wantGpu = useGpu && vulkanCompiledIn
        val wantGpuLayers = if (wantGpu) gpuLayers.coerceAtLeast(0) else 0
        if (handle != 0L && loadedModelPath == modelPath && loadedWithGpu == wantGpu &&
            loadedGpuLayers == wantGpuLayers
        ) {
            return@withLock Result.success(Unit)
        }
        val file = java.io.File(modelPath)
        if (!file.exists() || !file.isFile) {
            return@withLock Result.failure(
                IllegalArgumentException("Fichier de modèle introuvable : ${file.name}")
            )
        }
        unloadLocked()

        // nativeLoadModel() est un appel JNI bloquant, pas une fonction suspend : une fois entré
        // dans le code C++ (lecture du fichier GGUF, et surtout initialisation du backend Vulkan —
        // sur certains appareils, le pilote GPU peut bloquer indéfiniment à ce moment plutôt que
        // d'échouer proprement, voir docs/VULKAN_NOTES.md), rien côté Kotlin ne peut l'interrompre :
        // un withTimeoutOrNull() qui l'entourerait directement attendrait quand même la fin réelle
        // de l'appel avant de constater le dépassement. On le lance donc sur son propre thread
        // (engineScope, indépendant du withTimeoutOrNull qui suit) : ATTENDRE son résultat (await())
        // est un vrai point de suspension, donc annulable — ça permet à l'appelant de reprendre la
        // main après le délai plutôt que de rester bloqué sur "chargement du modèle" pour toujours,
        // même si l'appel natif continue, lui, de tourner en arrière-plan sur son thread.
        val loadDeferred = engineScope.async {
            LlamaBridge.nativeLoadModel(modelPath, contextSize, wantGpuLayers, threads)
        }
        val newHandle = withTimeoutOrNull(MODEL_LOAD_TIMEOUT_MS) { loadDeferred.await() }
        if (newHandle == null) {
            // Délai dépassé : on ne peut pas tuer l'appel natif en cours, seulement cesser de
            // l'attendre. S'il finit par aboutir après coup, on libère aussitôt la session
            // orpheline (elle ne sera jamais assignée à `handle`) pour ne pas fuir la mémoire
            // native — sans ce nettoyage, une timeout suivie d'un chargement qui réussit quand
            // même en arrière-plan laisserait un modèle entier (potentiellement plusieurs Go)
            // chargé en mémoire sans jamais être libéré.
            engineScope.launch {
                val orphan = runCatching { loadDeferred.await() }.getOrNull()
                if (orphan != null && orphan != 0L) {
                    Log.w(
                        "OpenCompanion",
                        "Chargement natif abouti après le délai : libération de la session orpheline",
                    )
                    LlamaBridge.nativeFreeModel(orphan)
                }
            }
            return@withLock Result.failure(
                IllegalStateException(
                    "Le chargement du modèle n'a pas répondu en ${MODEL_LOAD_TIMEOUT_MS / 1000}s " +
                        "(souvent un pilote GPU Vulkan qui bloque à l'initialisation sur cet appareil)"
                )
            )
        }
        if (newHandle == 0L) {
            return@withLock Result.failure(
                IllegalStateException("Échec du chargement du modèle (fichier invalide, ou mémoire insuffisante pour ce contexte)")
            )
        }
        handle = newHandle
        loadedModelPath = modelPath
        loadedWithGpu = wantGpu
        loadedGpuLayers = wantGpuLayers
        Result.success(Unit)
    }

    private companion object {
        /** Généreux : un gros modèle sur stockage lent + compilation des pipelines Vulkan peut
         *  légitimement prendre du temps. Au-delà, on considère l'appareil bloqué plutôt que lent. */
        const val MODEL_LOAD_TIMEOUT_MS = 45_000L
    }

    suspend fun unload() = lifecycleMutex.withLock { unloadLocked() }

    private fun unloadLocked() {
        if (handle != 0L) {
            LlamaBridge.nativeFreeModel(handle)
            handle = 0
        }
        loadedModelPath = null
        loadedWithGpu = false
        loadedGpuLayers = 0
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
