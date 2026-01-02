package com.narutoai.chat.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.*

class TTSHelper(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isSpeaking = false
    
    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.FRENCH)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback to English
                    tts?.setLanguage(Locale.US)
                }
                isInitialized = true
                Log.d("TTSHelper", "✅ TTS initialisé")
            } else {
                Log.e("TTSHelper", "❌ Échec initialisation TTS")
            }
        }
        
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
            }
            
            override fun onDone(utteranceId: String?) {
                isSpeaking = false
            }
            
            override fun onError(utteranceId: String?) {
                isSpeaking = false
                Log.e("TTSHelper", "❌ Erreur TTS: $utteranceId")
            }
        })
    }
    
    /**
     * Lit un texte à voix haute
     */
    fun speak(text: String, utteranceId: String = "message_${System.currentTimeMillis()}") {
        if (!isInitialized) {
            Log.w("TTSHelper", "⚠️ TTS non initialisé")
            return
        }
        
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }
    
    /**
     * Ajoute un texte à la file de lecture
     */
    fun queue(text: String, utteranceId: String = "message_${System.currentTimeMillis()}") {
        if (!isInitialized) {
            Log.w("TTSHelper", "⚠️ TTS non initialisé")
            return
        }
        
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }
    
    /**
     * Arrête la lecture en cours
     */
    fun stop() {
        tts?.stop()
        isSpeaking = false
    }
    
    /**
     * Vérifie si TTS est en train de parler
     */
    fun isSpeaking(): Boolean {
        return isSpeaking
    }
    
    /**
     * Libère les ressources TTS
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
    
    /**
     * Change la vitesse de lecture
     */
    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate) // 0.5 = lent, 1.0 = normal, 2.0 = rapide
    }
    
    /**
     * Change la hauteur de la voix
     */
    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch) // 0.5 = grave, 1.0 = normal, 2.0 = aigu
    }
}
