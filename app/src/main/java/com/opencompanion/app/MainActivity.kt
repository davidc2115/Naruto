package com.opencompanion.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.opencompanion.app.charactercard.CharacterImportManager
import com.opencompanion.app.ui.AppNav
import com.opencompanion.app.ui.theme.OpenCompanionTheme
import kotlinx.coroutines.launch

/**
 * Point d'entrée unique. Gère aussi la réception d'un partage ("Envoyer vers OpenCompanion")
 * depuis une autre application — navigateur, galerie, gestionnaire de fichiers — pour un import
 * de personnage fluide sans passer par le sélecteur de fichiers (voir AndroidManifest.xml).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as OpenCompanionApplication
        handleIncomingShare(intent, app)

        setContent {
            OpenCompanionTheme {
                AppNav(app)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShare(intent, application as OpenCompanionApplication)
    }

    private fun handleIncomingShare(intent: Intent?, app: OpenCompanionApplication) {
        if (intent?.action != Intent.ACTION_SEND) return

        val streamUri = intent.getParcelableExtraCompat()
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)

        app.applicationScope.launch {
            val result = when {
                streamUri != null -> app.characterImportManager.importFromUri(streamUri)
                !sharedText.isNullOrBlank() && looksLikeUrl(sharedText) ->
                    app.characterImportManager.importFromUrl(sharedText.trim())
                else -> null
            }
            val message = when (result) {
                is CharacterImportManager.ImportResult.Success -> "« ${result.name} » importé."
                is CharacterImportManager.ImportResult.Failure -> "Import impossible : ${result.reason}"
                null -> null
            }
            message?.let { runOnUiThread { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() } }
        }
    }

    private fun looksLikeUrl(text: String): Boolean =
        text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)

    @Suppress("DEPRECATION")
    private fun Intent.getParcelableExtraCompat(): Uri? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_STREAM)
        }
}
