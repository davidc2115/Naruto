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
    fun importFromDownloadUrl(url: String, cookieHeader: String?) {
        setImporting()
        viewModelScope.launch {
            report(importManager.importFromUrl(url, cookieHeader))
        }
    }

    /**
     * Téléchargement "blob:" (contenu généré en mémoire par la page, très courant pour un bouton
     * "Télécharger" en JavaScript) : le contenu ne peut être récupéré que par la page elle-même,
     * relu et renvoyé ici en Data URI base64 par le script injecté dans [CharacterBrowserScreen].
     */
    fun importFromDataUri(dataUri: String) {
        setImporting()
        viewModelScope.launch {
            val base64 = dataUri.substringAfter(",", missingDelimiterValue = "")
            if (base64.isEmpty()) {
                report(CharacterImportManager.ImportResult.Failure("Contenu téléchargé illisible"))
                return@launch
            }
            val bytes = try {
                Base64.decode(base64, Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                report(CharacterImportManager.ImportResult.Failure("Contenu téléchargé illisible"))
                return@launch
            }
            report(importManager.importFromBytes(bytes))
        }
    }

    /**
     * Bouton "Importer cette page" : filet de rattrapage pour les sites qui affichent le JSON
     * de la fiche directement comme texte de page plutôt que de proposer un téléchargement.
     * Si la page ne ressemble pas à du JSON, on retente comme si l'URL actuelle était elle-même
     * le fichier (image PNG avec fiche embarquée servie directement, sans détour par un clic
     * "télécharger" que la WebView aurait pu intercepter).
     */
    fun importCurrentPage(url: String, pageText: String?, cookieHeader: String?) {
        setImporting()
        viewModelScope.launch {
            val trimmed = pageText?.trim()
            val result = if (!trimmed.isNullOrEmpty() && trimmed.first() == '{') {
                importManager.importFromText(trimmed)
            } else {
                importManager.importFromUrl(url, cookieHeader)
            }
            report(result)
        }
    }

    private fun setImporting() {
        _isImporting.value = true
    }

    private fun report(result: CharacterImportManager.ImportResult) {
        _isImporting.value = false
        _importMessage.value = when (result) {
            is CharacterImportManager.ImportResult.Success -> "« ${result.name} » importé."
            is CharacterImportManager.ImportResult.Failure -> "Import impossible : ${result.reason}"
        }
    }

    fun consumeImportMessage() {
        _importMessage.value = null
    }
}
