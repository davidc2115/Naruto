package com.opencompanion.app.charactercard

import android.util.Base64
import java.util.zip.CRC32
import java.util.zip.Inflater

/**
 * Lecture/écriture d'une fiche personnage encodée en base64 dans un chunk
 * PNG `tEXt` — c'est ainsi que la quasi-totalité des sites communautaires de
 * "character cards" encodent une fiche dans son image d'avatar, pour qu'un
 * seul fichier PNG suffise à tout transporter. Mot-clé "chara" (spec v2,
 * dominante) ou "ccv3" (spec v3) reconnus en lecture ; écriture en "chara"
 * pour rester compatible avec le plus d'outils possible. Chunks `tEXt` (le cas très
 * majoritaire) ET `iTXt` (texte international, parfois compressé zlib) sont lus : certains
 * outils qui rouvrent puis réenregistrent l'image (éditeurs, certains navigateurs) convertissent
 * silencieusement `tEXt` en `iTXt`, ce qui rendait la fiche illisible pour ce dépôt avant cet
 * ajout, alors que le PNG restait par ailleurs parfaitement valide.
 *
 * Implémentation volontairement indépendante de toute bibliothèque externe :
 * on ne fait que naviguer la structure de chunks PNG (signature + suite de
 * blocs longueur/type/données/CRC), sans jamais décoder l'image elle-même.
 */
object PngCharaChunkCodec {

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= 8 && (0 until 8).all { bytes[it] == PNG_SIGNATURE[it] }

    /** Extrait et décode le JSON de la fiche personnage embarquée, ou null si absente/invalide. */
    fun extractCharacterJson(bytes: ByteArray): String? {
        if (!isPng(bytes)) return null

        var offset = 8
        var charaBase64: String? = null
        var ccv3Base64: String? = null

        while (offset + 8 <= bytes.size) {
            val length = readIntBE(bytes, offset)
            if (length < 0) break // chunk > 2 Go : ne devrait jamais arriver pour une fiche texte
            val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            val dataStart = offset + 8
            if (dataStart + length + 4 > bytes.size) break

            if (type == "tEXt") {
                val chunk = bytes.copyOfRange(dataStart, dataStart + length)
                val nullIndex = chunk.indexOf(0.toByte())
                if (nullIndex > 0) {
                    val keyword = String(chunk, 0, nullIndex, Charsets.ISO_8859_1)
                    val text = String(chunk, nullIndex + 1, chunk.size - nullIndex - 1, Charsets.ISO_8859_1)
                    when (keyword) {
                        "chara" -> charaBase64 = text
                        "ccv3" -> ccv3Base64 = text
                    }
                }
            } else if (type == "iTXt") {
                val chunk = bytes.copyOfRange(dataStart, dataStart + length)
                readItxtChunk(chunk)?.let { (keyword, text) ->
                    when (keyword) {
                        "chara" -> if (charaBase64 == null) charaBase64 = text
                        "ccv3" -> if (ccv3Base64 == null) ccv3Base64 = text
                    }
                }
            }

            offset = dataStart + length + 4 // + CRC
            if (type == "IEND") break
        }

        val base64 = charaBase64 ?: ccv3Base64 ?: return null
        return try {
            String(Base64.decode(base64, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Décode le contenu d'un chunk `iTXt` (International text) : mot-clé (latin-1, terminé par
     * un octet nul), drapeau de compression (1 octet), méthode de compression (1 octet, seule la
     * valeur 0 = zlib/deflate est définie par la spec PNG), balise de langue (ASCII, terminée par
     * un octet nul, ignorée ici), mot-clé traduit (UTF-8, terminé par un octet nul, ignoré ici),
     * puis le texte lui-même (UTF-8, éventuellement compressé zlib si le drapeau est à 1).
     * Renvoie `null` si la structure du chunk ne correspond pas à ce format (chunk malformé ou
     * tronqué) plutôt que de lever une exception — un PNG "avatar" contient presque toujours
     * d'autres chunks `iTXt`/`tEXt` sans rapport (titre, logiciel d'origine, etc.) qu'il faut
     * pouvoir ignorer silencieusement.
     */
    private fun readItxtChunk(chunk: ByteArray): Pair<String, String>? {
        val keywordEnd = chunk.indexOf(0.toByte())
        if (keywordEnd <= 0) return null
        val keyword = String(chunk, 0, keywordEnd, Charsets.ISO_8859_1)

        // + 2 : drapeau de compression (1 octet) + méthode de compression (1 octet).
        var pos = keywordEnd + 1 + 2
        if (pos > chunk.size) return null
        val compressionFlag = chunk[keywordEnd + 1].toInt()

        val langEnd = chunk.indexOfFrom(0.toByte(), pos)
        if (langEnd < 0) return null
        pos = langEnd + 1

        val translatedKeywordEnd = chunk.indexOfFrom(0.toByte(), pos)
        if (translatedKeywordEnd < 0) return null
        pos = translatedKeywordEnd + 1

        val textBytes = chunk.copyOfRange(pos, chunk.size)
        val decodedBytes = if (compressionFlag == 1) {
            inflateZlib(textBytes) ?: return null
        } else {
            textBytes
        }
        return keyword to String(decodedBytes, Charsets.UTF_8)
    }

    /** Renvoie l'index de la première occurrence de [element] à partir de [startIndex], ou -1. */
    private fun ByteArray.indexOfFrom(element: Byte, startIndex: Int): Int {
        for (i in startIndex until size) if (this[i] == element) return i
        return -1
    }

    private fun inflateZlib(data: ByteArray): ByteArray? = try {
        val inflater = Inflater()
        inflater.setInput(data)
        val output = java.io.ByteArrayOutputStream(data.size * 3)
        val buffer = ByteArray(4096)
        while (!inflater.finished()) {
            val n = inflater.inflate(buffer)
            if (n == 0) {
                if (inflater.needsInput() || inflater.needsDictionary()) break
            }
            output.write(buffer, 0, n)
        }
        inflater.end()
        output.toByteArray()
    } catch (e: Exception) {
        null
    }

    /**
     * Renvoie une copie de [pngBytes] avec [characterJson] embarqué dans un nouveau chunk
     * `tEXt/chara`, inséré juste après IHDR. Le reste de l'image (l'avatar) n'est pas modifié.
     */
    fun embedCharacterJson(pngBytes: ByteArray, characterJson: String): ByteArray {
        require(isPng(pngBytes)) { "L'image fournie n'est pas un PNG valide" }

        val base64 = Base64.encodeToString(characterJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val chunkData = "chara".toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0) +
            base64.toByteArray(Charsets.US_ASCII)
        val newChunk = buildChunk("tEXt", chunkData)

        val ihdrLength = readIntBE(pngBytes, 8)
        val insertPos = 8 + 8 + ihdrLength + 4 // signature + (longueur+type) IHDR + données + CRC

        val output = ByteArray(pngBytes.size + newChunk.size)
        System.arraycopy(pngBytes, 0, output, 0, insertPos)
        System.arraycopy(newChunk, 0, output, insertPos, newChunk.size)
        System.arraycopy(pngBytes, insertPos, output, insertPos + newChunk.size, pngBytes.size - insertPos)
        return output
    }

    private fun buildChunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply { update(typeBytes); update(data) }.value.toInt()

        val out = ByteArray(4 + 4 + data.size + 4)
        writeIntBE(out, 0, data.size)
        System.arraycopy(typeBytes, 0, out, 4, 4)
        System.arraycopy(data, 0, out, 8, data.size)
        writeIntBE(out, 8 + data.size, crc)
        return out
    }

    private fun readIntBE(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun writeIntBE(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }
}
