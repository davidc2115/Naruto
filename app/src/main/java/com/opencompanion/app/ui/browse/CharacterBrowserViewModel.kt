package com.opencompanion.app.ui.browse

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencompanion.app.charactercard.CharacterImportManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Logique d'import pour [CharacterBrowserScreen] : le navigateur intégré ne fait *que* de
 * l'affichage (WebView) et de la détection de téléchargement/contenu, tout le travail
 * d'interprétation des octets récupérés passe par ici puis par [CharacterImportManager], comme
 * pour les autres voies d'import (fichier, URL collée, partage).
 */
class CharacterBrowserViewModel(
    private val importManager: CharacterImportManager,
) : ViewModel() {

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    /** Téléchargement classique (http/https) intercepté dans la WebView — fiche PNG ou JSON
     *  servie comme un vrai fichier téléchargeable. [cookieHeader] permet de rester authentifié
     *  auprès du site si le lien de téléchargement nécessite la session ouverte dans la page. */
    fun importFromDownloadUrl(url: String, cookieHeader: String?, autoTranslateFrench: Boolean = true) {
        setImporting()
        viewModelScope.launch {
            report(importManager.importFromUrl(url, cookieHeader), autoTranslateFrench)
        }
    }

    /**
     * Téléchargement "blob:" (contenu généré en mémoire par la page, très courant pour un bouton
     * "Télécharger" en JavaScript).
     */
    fun importFromDataUri(dataUri: String, autoTranslateFrench: Boolean = true) {
        setImporting()
        viewModelScope.launch {
            val base64 = dataUri.substringAfter(",", missingDelimiterValue = "")
            if (base64.isEmpty()) {
                report(CharacterImportManager.ImportResult.Failure("Contenu téléchargé illisible"), autoTranslateFrench)
                return@launch
            }
            val bytes = try {
                Base64.decode(base64, Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                report(CharacterImportManager.ImportResult.Failure("Contenu téléchargé illisible"), autoTranslateFrench)
                return@launch
            }
            report(importManager.importFromBytes(bytes, autoTranslateFrench), autoTranslateFrench)
        }
    }

    /**
     * Bouton "Importer cette page" : filet de rattrapage pour les sites qui affichent le JSON.
     */
    fun importCurrentPage(url: String, pageText: String?, cookieHeader: String?, autoTranslateFrench: Boolean = true) {
        setImporting()
        viewModelScope.launch {
            val trimmed = pageText?.trim()
            val result = if (!trimmed.isNullOrEmpty() && trimmed.first() == '{') {
                importManager.importFromText(trimmed, autoTranslateFrench)
            } else {
                importManager.importFromUrl(url, cookieHeader, autoTranslateFrench)
            }
            report(result, autoTranslateFrench)
        }
    }

    private fun setImporting() {
        _isImporting.value = true
    }

    private fun report(result: CharacterImportManager.ImportResult, translated: Boolean = false) {
        _isImporting.value = false
        _importMessage.value = when (result) {
            is CharacterImportManager.ImportResult.Success -> {
                val suffix = if (translated) " (traduit en FR 🇫🇷)" else ""
                "« ${result.name} » importé$suffix."
            }
            is CharacterImportManager.ImportResult.Failure -> "Import impossible : ${result.reason}"
        }
    }

    fun consumeImportMessage() {
        _importMessage.value = null
    }
}
