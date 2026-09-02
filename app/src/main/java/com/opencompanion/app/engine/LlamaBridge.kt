package com.opencompanion.app.engine

/**
 * Déclarations JNI brutes vers le moteur natif (llama.cpp + notre pont C++,
 * voir app/src/main/cpp/opencompanion_bridge.cpp).
 *
 * Ne pas utiliser directement depuis l'UI : passer par [InferenceEngine], qui
 * ajoute la gestion des coroutines, le décodage UTF-8 incrémental des
 * fragments de token et le repli automatique CPU si le GPU (Vulkan) échoue.
 */
object LlamaBridge {

    init {
        System.loadLibrary("opencompanion_bridge")
        nativeBackendInit()
    }

    /** Interface de rappel appelée depuis le thread natif pour chaque fragment de token généré.
     *  [utf8Bytes] contient des octets UTF-8 bruts, potentiellement un caractère multi-octets
     *  incomplet : voir [Utf8StreamDecoder]. Retourner `false` demande l'arrêt de la génération. */
    fun interface TokenCallback {
        fun onToken(utf8Bytes: ByteArray): Boolean
    }

    private external fun nativeBackendInit()

    /** true si ce build a été compilé avec le backend Vulkan (GGML_VULKAN=ON). */
    external fun nativeHasVulkanSupport(): Boolean

    /**
     * Charge un modèle GGUF depuis [modelPath].
     * @param nGpuLayers nombre de couches à décharger sur le GPU (0 = CPU pur).
     * @return un handle opaque (> 0) à réutiliser pour les appels suivants, ou 0 en cas d'échec.
     */
    external fun nativeLoadModel(modelPath: String, nCtx: Int, nGpuLayers: Int, nThreads: Int): Long

    external fun nativeFreeModel(handle: Long)

    external fun nativeAbortGeneration(handle: Long)

    /** Nombre de tokens que produirait [textUtf8] — utilisé pour dimensionner l'historique. */
    external fun nativeTokenCount(handle: Long, textUtf8: ByteArray): Int

    /**
     * Formate une liste de messages (rôles "system"/"user"/"assistant") selon le patron de
     * dialogue embarqué dans le modèle GGUF chargé (ou un format par défaut raisonnable si le
     * modèle n'en fournit pas) — voir llama_chat_apply_template. Renvoie les octets UTF-8 du
     * prompt final prêt à passer à [nativeGenerate], ou null en cas d'échec.
     */
    external fun nativeApplyChatTemplate(
        handle: Long,
        roles: Array<String>,
        contentsUtf8: Array<ByteArray>,
        addAssistant: Boolean,
    ): ByteArray?

    /**
     * Génère une complétion de façon **bloquante** — à appeler depuis un thread de calcul
     * (Dispatchers.Default), jamais depuis le thread UI.
     *
     * @return 0 = terminé normalement (fin de séquence), 1 = arrêté (utilisateur ou nPredict
     *   atteint), négatif = erreur (voir logs natifs "OpenCompanionNative").
     */
    external fun nativeGenerate(
        handle: Long,
        promptUtf8: ByteArray,
        nPredict: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        repeatPenalty: Float,
        seed: Long,
        callback: TokenCallback,
    ): Int
}
