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

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme

data class PopularSitePreset(val name: String, val url: String, val icon: String = "🌐")

val POPULAR_SITES = listOf(
    PopularSitePreset("Chub.ai", "https://chub.ai/"),
    PopularSitePreset("JanitorAI", "https://janitorai.com/"),
    PopularSitePreset("CharacterHub", "https://characterhub.org/"),
    PopularSitePreset("SpicyChat", "https://spicychat.ai/"),
    PopularSitePreset("Character.ai", "https://character.ai/"),
    PopularSitePreset("Pygmalion", "https://pygmalion.chat/"),
)

/**
 * Navigateur intégré pour importer une fiche personnage directement depuis un site
 * communautaire, sans quitter l'appli ni passer par le sélecteur de fichiers du système.
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
    var autoTranslateFr by remember { mutableStateOf(true) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
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
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    item {
                        FilterChip(
                            selected = autoTranslateFr,
                            onClick = { autoTranslateFr = !autoTranslateFr },
                            label = { Text("🌐 Traduire en FR à l'import") },
                        )
                    }
                    items(POPULAR_SITES) { site ->
                        AssistChip(
                            onClick = {
                                addressText = site.url
                                webView.loadUrl(site.url)
                            },
                            label = { Text("${site.icon} ${site.name}") },
                        )
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
                        viewModel.importCurrentPage(currentUrl, unescapeJsString(rawResult), cookies, autoTranslateFr)
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
                                onReady = { dataUri -> viewModel.importFromDataUri(dataUri, autoTranslateFr) },
                            ),
                            "AndroidCardImporter",
                        )
                        setDownloadListener { url, _, _, _, _ ->
                            if (url.startsWith("blob:")) {
                                evaluateJavascript(BLOB_CAPTURE_JS.replace("__BLOB_URL__", url), null)
                            } else {
                                val cookies = CookieManager.getInstance().getCookie(url)
                                viewModel.importFromDownloadUrl(url, cookies, autoTranslateFr)
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
