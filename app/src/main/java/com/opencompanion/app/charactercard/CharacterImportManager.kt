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

    suspend fun importFromUri(uri: Uri, autoTranslateFrench: Boolean = true): ImportResult = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext ImportResult.Failure("Impossible de lire le fichier sélectionné")
            importFromBytesInternal(bytes, autoTranslateFrench)
        } catch (e: Exception) {
            ImportResult.Failure(e.message ?: "Erreur d'import")
        }
    }

    /**
     * Import direct depuis un texte brut — un JSON de fiche personnage collé ou partagé tel quel.
     */
    suspend fun importFromText(text: String, autoTranslateFrench: Boolean = true): ImportResult = withContext(Dispatchers.IO) {
        importFromBytesInternal(text.toByteArray(Charsets.UTF_8), autoTranslateFrench)
    }

    /**
     * Import direct depuis une URL (image PNG avec fiche embarquée, JSON brut, ou page HTML).
     */
    suspend fun importFromUrl(
        url: String,
        cookieHeader: String? = null,
        autoTranslateFrench: Boolean = true,
    ): ImportResult = withContext(Dispatchers.IO) {
        var currentUrl = url
        var redirects = 0
        val maxRedirects = 10
        var bytes: ByteArray? = null
        var lastResponseCode = -1

        try {
            while (redirects < maxRedirects) {
                val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 30_000
                    readTimeout = 30_000
                    instanceFollowRedirects = false
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 OpenCompanion/1.0"
                    )
                    setRequestProperty(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/png,application/json,*/*;q=0.8"
                    )
                    if (!cookieHeader.isNullOrBlank()) setRequestProperty("Cookie", cookieHeader)
                }

                try {
                    lastResponseCode = connection.responseCode
                    if (lastResponseCode in 300..399) {
                        val location = connection.getHeaderField("Location")
                        if (location.isNullOrBlank()) {
                            return@withContext ImportResult.Failure("Redirection sans en-tête Location ($lastResponseCode)")
                        }
                        currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                            location
                        } else {
                            URL(URL(currentUrl), location).toString()
                        }
                        redirects++
                    } else if (lastResponseCode in 200..299) {
                        bytes = connection.inputStream.use { it.readBytes() }
                        break
                    } else {
                        return@withContext ImportResult.Failure("Le serveur a répondu $lastResponseCode")
                    }
                } finally {
                    connection.disconnect()
                }
            }

            if (bytes == null) {
                return@withContext ImportResult.Failure("Trop de redirections HTTP (limite: $maxRedirects)")
            }

            importFromBytesInternal(bytes, autoTranslateFrench)
        } catch (e: Exception) {
            ImportResult.Failure(e.message ?: "Erreur réseau")
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
            val text = bytes.toString(Charsets.UTF_8).removePrefix(CharacterCardCodec.UTF8_BOM).trim()
            if (text.startsWith("<!DOCTYPE", ignoreCase = true) || text.startsWith("<html", ignoreCase = true)) {
                val extractedJson = tryExtractJsonFromHtml(text)
                if (extractedJson != null) {
                    jsonText = extractedJson
                } else {
                    return ImportResult.Failure("La page HTML ne contient pas de données de personnage reconnues")
                }
            } else if (text.isNotEmpty() && text.first() == '{') {
                jsonText = text
            } else {
                return ImportResult.Failure("Format non reconnu : ni PNG avec fiche embarquée, ni JSON")
            }
        }

        val cardData = try {
            CharacterCardCodec.decode(jsonText)
        } catch (e: Exception) {
            return ImportResult.Failure("JSON de fiche personnage invalide : ${e.message}")
        }

        if (cardData.name.isBlank() && cardData.description.isBlank() && cardData.firstMes.isBlank()) {
            return ImportResult.Failure("Aucune donnée de personnage n'a pu être extraite")
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

    private fun tryExtractJsonFromHtml(html: String): String? {
        val nextDataMatch = Regex("""<script\s+id="__NEXT_DATA__"\s+type="application/json">([^<]+)</script>""").find(html)
        if (nextDataMatch != null) {
            val jsonContent = nextDataMatch.groupValues[1]
            if (jsonContent.contains("\"character\"") || jsonContent.contains("\"chara\"") || jsonContent.contains("\"card\"") || jsonContent.contains("\"name\"")) {
                return jsonContent
            }
        }

        val genericMatch = Regex("""<script[^>]*type="application/json"[^>]*>([^<]+)</script>""").findAll(html)
        for (match in genericMatch) {
            val content = match.groupValues[1].trim()
            if (content.startsWith("{") && (content.contains("\"name\"") || content.contains("\"first_mes\"") || content.contains("\"description\""))) {
                return content
            }
        }

        return null
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
