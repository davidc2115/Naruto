package com.opencompanion.app.engine

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gestion des modèles GGUF stockés localement.
 *
 * Volontairement générique et sans dépendance à un fournisseur particulier :
 * aucune clé d'API, aucun appel à l'API HuggingFace. Deux façons d'obtenir un
 * modèle :
 *   - [importFromContentUri] : l'utilisateur choisit un fichier .gguf déjà
 *     présent sur l'appareil via le sélecteur de fichiers système (Storage
 *     Access Framework) — aucun réseau impliqué.
 *   - [importFromDirectUrl] : téléchargement HTTP(S) direct d'une URL fournie
 *     par l'utilisateur (n'importe quelle source : serveur personnel, dépôt
 *     communautaire, etc.), sans en-tête d'authentification.
 */
class ModelManager(private val context: Context) {

    data class LocalModel(
        val file: File,
        val displayName: String,
        val architecture: String?,
        val quantization: String?,
        val contextLength: Long?,
        val sizeBytes: Long,
    )

    sealed class ImportProgress {
        data class Downloading(val bytesRead: Long, val totalBytes: Long) : ImportProgress()
        data class Done(val model: LocalModel) : ImportProgress()
        data class Failed(val message: String) : ImportProgress()
    }

    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    fun listLocalModels(): List<LocalModel> =
        modelsDir.listFiles { f -> f.isFile && f.extension.equals("gguf", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { toLocalModel(it) }
            ?: emptyList()

    fun delete(model: LocalModel): Boolean = model.file.delete()

    private fun toLocalModel(file: File): LocalModel {
        val meta = GgufMetadataReader.read(file.absolutePath)
        return LocalModel(
            file = file,
            displayName = meta?.name?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
            architecture = meta?.architecture,
            quantization = meta?.quantization,
            contextLength = meta?.contextLength,
            sizeBytes = meta?.fileSizeBytes ?: file.length(),
        )
    }

    /** Copie un fichier .gguf choisi par l'utilisateur (SAF) vers le stockage privé de l'app. */
    suspend fun importFromContentUri(uri: Uri, suggestedName: String): Result<LocalModel> =
        withContext(Dispatchers.IO) {
            runCatching {
                val destName = sanitizeFileName(suggestedName).ifBlank { "modele-${System.currentTimeMillis()}.gguf" }
                val dest = uniqueDestination(destName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output, bufferSize = 1 shl 20) }
                } ?: error("Impossible d'ouvrir le fichier sélectionné")
                toLocalModel(dest)
            }
        }

    /**
     * Télécharge un modèle depuis une URL directe fournie par l'utilisateur.
     * Émet la progression puis le résultat final via un [Flow] (annulable en
     * annulant la collecte).
     */
    fun importFromDirectUrl(url: String, suggestedName: String): Flow<ImportProgress> = callbackFlow {
        val destName = sanitizeFileName(suggestedName.ifBlank { url.substringAfterLast('/') })
            .ifBlank { "modele-${System.currentTimeMillis()}.gguf" }
        val dest = uniqueDestination(destName)
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
                trySend(ImportProgress.Failed("Le serveur a répondu ${connection.responseCode}"))
                close()
                return@callbackFlow
            }
            val total = connection.contentLengthLong
            var read = 0L
            connection.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        read += n
                        trySend(ImportProgress.Downloading(read, total))
                    }
                }
            }
            trySend(ImportProgress.Done(toLocalModel(dest)))
        } catch (e: Exception) {
            dest.delete()
            trySend(ImportProgress.Failed(e.message ?: e.toString()))
        } finally {
            connection?.disconnect()
            close()
        }
        awaitClose { connection?.disconnect() }
    }

    private fun uniqueDestination(name: String): File {
        var candidate = File(modelsDir, name)
        var index = 1
        val base = candidate.nameWithoutExtension
        val ext = candidate.extension.ifBlank { "gguf" }
        while (candidate.exists()) {
            candidate = File(modelsDir, "$base-$index.$ext")
            index++
        }
        return candidate
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return if (cleaned.endsWith(".gguf", ignoreCase = true)) cleaned else "$cleaned.gguf"
    }
}
