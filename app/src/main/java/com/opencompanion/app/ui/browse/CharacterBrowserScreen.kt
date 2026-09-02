package com.opencompanion.app.ui.browse

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Navigateur intégré pour importer une fiche personnage directement depuis un site
 * communautaire, sans quitter l'appli ni passer par le sélecteur de fichiers du système.
 *
 * Fonctionnement :
 *  - L'utilisateur navigue librement (barre d'adresse + WebView classique) vers le site de son
 *    choix — volontairement pas de liste de sites imposée ici : le choix des sites communautaires
 *    à utiliser reste entièrement à l'utilisateur.
 *  - Un clic sur un lien de téléchargement PNG/JSON classique (requête http(s) normale) est
 *    intercepté par [android.webkit.WebView.setDownloadListener] et importé automatiquement,
 *    avec les cookies de la page pour rester authentifié si le site l'exige.
 *  - Beaucoup de sites modernes déclenchent plutôt un téléchargement "blob:" généré en JavaScript
 *    (le fichier n'existe qu'en mémoire dans la page) : dans ce cas, un petit script est injecté
 *    pour relire ce blob et le renvoyer ici en base64 via [BlobImportBridge].
 *  - Si un site affiche simplement le JSON de la fiche comme texte de page (au lieu d'un vrai
 *    téléchargement), le bouton "Importer cette page" ci-dessous sert de filet de rattrapage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CharacterBrowserScreen(
    viewModel: CharacterBrowserViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val importMessage by viewModel.importMessage.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var addressText by remember { mutableStateOf("https://") }
    var currentUrl by remember { mutableStateOf("") }
    var loadProgress by remember { mutableStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Cookies nécessaires pour que la session du site (connexion, préférences d'âge/
            // contenu propres au site) suive jusqu'au lien de téléchargement intercepté.
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }
    }

    LaunchedEffect(importMessage) {
        importMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeImportMessage()
        }
    }

    DisposableEffect(Unit) {
        onDispose { webView.destroy() }
    }

    BackHandler(enabled = canGoBack) { webView.goBack() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Importer depuis un site") },
                    navigationIcon = {
                        IconButton(onClick = { if (webView.canGoBack()) webView.goBack() else onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                        }
                    },
                )
                if (loadProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { loadProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = addressText,
                        onValueChange = { addressText = it },
                        placeholder = { Text("Adresse du site (ex. https://…)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { webView.loadUrl(normalizeUrl(addressText)) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Aller")
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Importer cette page") },
                icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                onClick = {
                    val cookies = CookieManager.getInstance().getCookie(currentUrl)
                    webView.evaluateJavascript(
                        "(function(){return document.body ? document.body.innerText : '';})();",
                    ) { rawResult ->
                        viewModel.importCurrentPage(currentUrl, unescapeJsString(rawResult), cookies)
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    webView.apply {
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                currentUrl = url
                                addressText = url
                                canGoBack = view.canGoBack()
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                currentUrl = url
                                canGoBack = view.canGoBack()
                            }
                        }
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                loadProgress = newProgress
                            }
                        }
                        addJavascriptInterface(
                            BlobImportBridge(
                                onReady = { dataUri -> viewModel.importFromDataUri(dataUri) },
                            ),
                            "AndroidCardImporter",
                        )
                        setDownloadListener { url, _, _, _, _ ->
                            if (url.startsWith("blob:")) {
                                // Un blob: n'existe qu'en mémoire côté page : on demande à la page
                                // elle-même de le relire et de nous le renvoyer en base64, plutôt
                                // que de tenter un GET classique dessus (impossible, pas une vraie
                                // ressource réseau).
                                evaluateJavascript(BLOB_CAPTURE_JS.replace("__BLOB_URL__", url), null)
                            } else {
                                val cookies = CookieManager.getInstance().getCookie(url)
                                viewModel.importFromDownloadUrl(url, cookies)
                            }
                        }
                    }
                },
            )

            if (currentUrl.isEmpty()) {
                Text(
                    "Navigue vers le site communautaire de fiches personnage de ton choix, puis " +
                        "télécharge une fiche (PNG ou JSON) : elle sera importée automatiquement.\n\n" +
                        "Si le site affiche le JSON directement comme texte plutôt que de proposer " +
                        "un téléchargement, utilise le bouton « Importer cette page ».",
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(24.dp),
                )
            }

            if (isImporting) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

/** Pont JavaScript minimal : ne reçoit qu'une chaîne (Data URI base64 ou message d'erreur), sans
 *  aucune action sensible côté natif — la validation réelle (format PNG/JSON reconnu) a de toute
 *  façon lieu ensuite dans CharacterImportManager, comme pour toute autre source d'import. */
private class BlobImportBridge(private val onReady: (String) -> Unit) {
    @JavascriptInterface
    fun onBlobReady(dataUri: String) {
        onReady(dataUri)
    }

    @JavascriptInterface
    fun onBlobError(message: String) {
        // Pas d'action : l'échec silencieux d'une lecture de blob n'est pas assez rare ou
        // actionnable pour justifier une snackbar (l'utilisateur peut toujours se rabattre sur
        // "Importer cette page" ou l'import par URL classique).
    }
}

private const val BLOB_CAPTURE_JS = """
(function() {
    fetch('__BLOB_URL__').then(function(r) { return r.blob(); }).then(function(b) {
        var reader = new FileReader();
        reader.onloadend = function() { AndroidCardImporter.onBlobReady(reader.result); };
        reader.onerror = function() { AndroidCardImporter.onBlobError('lecture du blob impossible'); };
        reader.readAsDataURL(b);
    }).catch(function(e) { AndroidCardImporter.onBlobError(String(e)); });
})();
"""

private fun normalizeUrl(input: String): String {
    val trimmed = input.trim()
    return if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        trimmed
    } else {
        "https://$trimmed"
    }
}

/** evaluateJavascript() renvoie sa valeur encodée comme un littéral JSON (entre guillemets,
 *  échappements \" \\n …) : on la décode pour retrouver le texte brut de la page. */
private fun unescapeJsString(raw: String?): String? {
    if (raw.isNullOrEmpty() || raw == "null") return null
    val body = raw.removeSurrounding("\"")
    return body
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}
