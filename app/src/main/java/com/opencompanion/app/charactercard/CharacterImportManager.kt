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
            importFromBytes(bytes)
        } catch (e: Exception) {
            ImportResult.Failure(e.message ?: "Erreur d'import")
        }
    }

    /** Import direct depuis une URL (image PNG avec fiche embarquée, ou JSON brut). */
    suspend fun importFromUrl(url: String): ImportResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                instanceFollowRedirects = true
            }
            connection.connect()
            if (connection.responseCode !in 200..299) {
                return@withContext ImportResult.Failure("Le serveur a répondu ${connection.responseCode}")
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            importFromBytes(bytes)
        } catch (e: Exception) {
            ImportResult.Failure(e.message ?: "Erreur réseau")
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun importFromBytes(bytes: ByteArray): ImportResult {
        val jsonText: String
        var avatarBytes: ByteArray? = null

        if (PngCharaChunkCodec.isPng(bytes)) {
            jsonText = PngCharaChunkCodec.extractCharacterJson(bytes)
                ?: return ImportResult.Failure(
                    "Ce PNG ne contient pas de fiche personnage reconnue (chunk 'chara'/'ccv3' absent)"
                )
            avatarBytes = bytes
        } else {
            jsonText = bytes.toString(Charsets.UTF_8).trim()
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
