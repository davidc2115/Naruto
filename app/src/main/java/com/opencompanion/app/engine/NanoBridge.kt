package com.opencompanion.app.engine

import android.content.Context
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Pont vers **Gemini Nano via AICore**, le service système Android exposé par l'API
 * ML Kit GenAI Prompt (`com.google.mlkit:genai-prompt`, encore en Beta au moment où ce code a
 * été écrit — septembre 2026). Voir docs/MODELES_ET_AICORE.md pour la synthèse complète des
 * sources et des limites vérifiées ; résumé ici :
 *
 *   - Génération 100% sur l'appareil, sans réseau — mais le *service* AICore lui-même dépend
 *     de Google Play Services, et Gemini Nano n'est réellement disponible (téléchargé et
 *     supporté) que sur une partie des appareils Android récents (Pixel en tête). Ce n'est
 *     donc PAS un remplacement universel du backend llama.cpp, plutôt un raccourci "rapide"
 *     quand l'appareil le permet.
 *   - Beta : l'API peut changer. [checkAvailability] ne doit jamais faire planter l'app si le
 *     service est absent ou si Play Services manque — toute exception est absorbée et traduite
 *     en [NanoAvailability.UNAVAILABLE], exactement comme le repli CPU protège des plantages du
 *     pilote Vulkan (voir docs/VULKAN_NOTES.md, même philosophie défensive).
 *   - Quota d'entrée/sortie strict (~4000 tokens au total, imposé par AICore) : ce pont ne fait
 *     donc AUCUNE promesse de gérer un historique de conversation long — voir
 *     [com.opencompanion.app.prompt.PromptBuilder.buildNanoPrompt] pour le budget appliqué côté
 *     app avant l'appel.
 *
 * Ce code n'a pas pu être testé sur un appareil Android réel avec Gemini Nano installé (aucun
 * matériel de ce type dans l'environnement de conception) — l'API utilisée ici (noms de
 * classes, enum, méthodes) est celle documentée officiellement par Google au moment de
 * l'écriture. À vérifier en priorité lors du premier test sur un vrai Pixel.
 */
class NanoBridge(@Suppress("UNUSED_PARAMETER") context: Context) {

    enum class NanoAvailability { AVAILABLE, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE }

    sealed class NanoDownloadEvent {
        data object Started : NanoDownloadEvent()
        data object InProgress : NanoDownloadEvent()
        data object Completed : NanoDownloadEvent()
        data class Failed(val message: String) : NanoDownloadEvent()
    }

    private var clientInstance: GenerativeModel? = null
    private val model: GenerativeModel
        get() = clientInstance ?: Generation.getClient().also { clientInstance = it }

    /**
     * Interroge AICore pour savoir si Gemini Nano peut être utilisé maintenant. Ne lève jamais
     * d'exception : sur un appareil sans Play Services / AICore, l'appel sous-jacent échoue et
     * on le traduit simplement en [NanoAvailability.UNAVAILABLE].
     */
    suspend fun checkAvailability(): NanoAvailability = runCatching {
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> NanoAvailability.AVAILABLE
            FeatureStatus.DOWNLOADABLE -> NanoAvailability.DOWNLOADABLE
            FeatureStatus.DOWNLOADING -> NanoAvailability.DOWNLOADING
            else -> NanoAvailability.UNAVAILABLE
        }
    }.getOrDefault(NanoAvailability.UNAVAILABLE)

    /** Déclenche le téléchargement (géré par AICore) quand [checkAvailability] renvoie DOWNLOADABLE. */
    fun download(): Flow<NanoDownloadEvent> = model.download()
        .map { status ->
            when (status) {
                is DownloadStatus.DownloadStarted -> NanoDownloadEvent.Started
                is DownloadStatus.DownloadProgress -> NanoDownloadEvent.InProgress
                is DownloadStatus.DownloadCompleted -> NanoDownloadEvent.Completed
                is DownloadStatus.DownloadFailed -> NanoDownloadEvent.Failed(
                    "Échec du téléchargement de Gemini Nano (voir les journaux système pour le détail)."
                )
            }
        }
        .catch { e -> emit(NanoDownloadEvent.Failed(e.message ?: e.toString())) }

    /**
     * Génère une réponse en streaming pour [prompt] (déjà mis en forme et borné en taille par
     * [com.opencompanion.app.prompt.PromptBuilder.buildNanoPrompt]). Comme pour le moteur
     * llama.cpp (voir InferenceEngine.generate), aucune exception ne doit traverser cette
     * fonction : tout échec (quota dépassé, service non disponible, appareil qui vient d'être
     * réinitialisé…) se traduit par un [GenerationEvent.Error] géré par ChatViewModel, qui peut
     * alors basculer sur llama.cpp si un modèle local est configuré.
     */
    fun generate(prompt: String): Flow<GenerationEvent> = flow {
        model.generateContentStream(prompt).collect { chunk ->
            val text = chunk.candidates.firstOrNull()?.text.orEmpty()
            if (text.isNotEmpty()) emit(GenerationEvent.Token(text))
        }
        emit(GenerationEvent.Done)
    }.catch { e ->
        emit(GenerationEvent.Error(e.message ?: "Gemini Nano (AICore) indisponible"))
    }

    /** Libère le client AICore. Sans effet s'il n'a jamais été initialisé. */
    fun close() {
        clientInstance?.let { runCatching { it.close() } }
    }
}
