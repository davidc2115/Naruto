package com.opencompanion.app.charactercard

import android.content.Context
import android.net.Uri
import com.opencompanion.app.data.CharacterEntity
import com.opencompanion.app.data.CharacterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Point d'entrée unique pour importer un personnage, quelle que soit sa
 * source : fichier choisi dans le sélecteur système, image/JSON partagé
 * depuis une autre application (voir ShareReceiverActivity), ou URL directe
 * collée par l'utilisateur. Toutes ces voies convergent vers
 * [importFromBytes] : dès que les octets sont en main, l'origine n'a plus
 * d'importance — c'est ce qui rend l'import "fluide" quelle que soit la
 * provenance, sans intégration propriétaire à un site en particulier.
 */
class CharacterImportManager(
    private val context: Context,
    private val repository: CharacterRepository,
) {
    sealed class ImportResult {
        data class Success(val characterId: Long, val name: String) : ImportResult()
        data class Failure(val reason: String) : ImportResult()
    }

    suspend fun importFromUri(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext ImportResult.Failure("Impossible de lire le fichier sélectionné")
            importFromBytesInternal(bytes)
        } catch (e: Exception) {
            ImportResult.Failure(e.message ?: "Erreur d'import")
        }
    }

    /**
     * Import direct depuis un texte brut — un JSON de fiche personnage collé ou partagé tel
     * quel, sans passer par un fichier (certains sites communautaires proposent "copier/partager
     * le JSON" plutôt qu'un téléchargement). Voir aussi MainActivity.handleIncomingShare : avant
     * cet ajout, un partage de texte qui n'était pas reconnu comme une URL ne faisait
     * strictement rien — aucun message, aucune erreur — ce qui donnait l'impression que l'import
     * était cassé alors que rien n'avait simplement été tenté.
     */
    suspend fun importFromText(text: String): ImportResult = withContext(Dispatchers.IO) {
        importFromBytesInternal(text.toByteArray(Charsets.UTF_8))
    }

    /**
     * Import direct depuis une URL (image PNG avec fiche embarquée, ou JSON brut).
     * @param cookieHeader en-tête `Cookie` optionnel (session du site), utilisé quand ce lien de
     * téléchargement provient du navigateur intégré ([com.opencompanion.app.ui.browse.CharacterBrowserScreen])
     * et que le site exige d'être connecté pour servir le fichier — une requête HTTP "à froid"
     * sans les cookies de la page recevrait sinon une erreur 401/403 alors que le fichier est
     * bien accessible depuis la page elle-même.
     */
    suspend fun importFromUrl(url: String, cookieHeader: String? = null): ImportResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                if (!cookieHeader.isNullOrBlank()) setRequestProperty("Cookie", cookieHeader)
            }
            connection.connect()
            if (connection.responseCode !in 200..299) {
                return@withContext ImportResult.Failure("Le serveur a répondu ${connection.responseCode}")
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            importFromBytesInternal(bytes)
        } catch (e: Exception) {
            ImportResult.Failure(e.message ?: "Erreur réseau")
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Point d'entrée public pour un import déjà réduit à des octets bruts — utilisé par
     * [importFromUri]/[importFromText]/[importFromUrl] ci-dessus.
     */
    suspend fun importFromBytes(bytes: ByteArray, autoTranslateFrench: Boolean = true): ImportResult = withContext(Dispatchers.IO) {
        importFromBytesInternal(bytes, autoTranslateFrench)
    }

    private suspend fun importFromBytesInternal(bytes: ByteArray, autoTranslateFrench: Boolean = true): ImportResult {
        val jsonText: String
        var avatarBytes: ByteArray? = null

        if (PngCharaChunkCodec.isPng(bytes)) {
            jsonText = PngCharaChunkCodec.extractCharacterJson(bytes)
                ?: return ImportResult.Failure(
                    "Ce PNG ne contient pas de fiche personnage reconnue (chunk 'chara'/'ccv3' absent)"
                )
            avatarBytes = bytes
        } else {
            // .removePrefix(UTF8_BOM) : un fichier JSON téléchargé depuis un navigateur (Windows
            // en particulier) commence très souvent par un BOM UTF-8 invisible.
            jsonText = bytes.toString(Charsets.UTF_8).removePrefix(CharacterCardCodec.UTF8_BOM).trim()
            if (jsonText.isEmpty() || jsonText.first() != '{') {
                return ImportResult.Failure("Format non reconnu : ni PNG avec fiche embarquée, ni JSON")
            }
        }

        val cardData = try {
            CharacterCardCodec.decode(jsonText)
        } catch (e: Exception) {
            return ImportResult.Failure("JSON de fiche personnage invalide : ${e.message}")
        }

        var entity = cardData.toEntity()
        if (autoTranslateFrench) {
            entity = CharacterTranslator.translateEntityToFrench(entity)
        }
        if (avatarBytes != null) {
            entity = entity.copy(avatarPath = saveAvatar(avatarBytes, entity.name))
        }

        val id = repository.saveCharacter(entity)
        return ImportResult.Success(id, entity.name)
    }

    private fun saveAvatar(bytes: ByteArray, name: String): String {
        val dir = File(context.filesDir, "avatars").apply { mkdirs() }
        val file = File(dir, "${sanitizeFileName(name)}-${System.currentTimeMillis()}.png")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    /** Exporte en PNG (avatar + fiche embarquée) si un avatar existe, sinon en JSON brut. */
    suspend fun exportAsPngOrJson(character: CharacterEntity): File = withContext(Dispatchers.IO) {
        val avatarFile = character.avatarPath?.let { File(it) }?.takeIf { it.exists() }
        if (avatarFile == null) return@withContext exportAsJson(character)

        val json = CharacterCardCodec.encode(character)
        val embedded = PngCharaChunkCodec.embedCharacterJson(avatarFile.readBytes(), json)
        val outFile = exportsDir().resolve("${sanitizeFileName(character.name)}.png")
        outFile.writeBytes(embedded)
        outFile
    }

    suspend fun exportAsJson(character: CharacterEntity): File = withContext(Dispatchers.IO) {
        val json = CharacterCardCodec.encode(character)
        val outFile = exportsDir().resolve("${sanitizeFileName(character.name)}.json")
        outFile.writeText(json)
        outFile
    }

    private fun exportsDir(): File = File(context.cacheDir, "exports").apply { mkdirs() }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "personnage" }
}
