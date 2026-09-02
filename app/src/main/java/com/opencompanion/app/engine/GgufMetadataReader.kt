package com.opencompanion.app.engine

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lecteur minimal d'en-tête GGUF : extrait quelques métadonnées utiles pour
 * l'UI (nom, architecture, taille de contexte d'entraînement) sans jamais
 * charger le modèle complet ni son vocabulaire en mémoire.
 *
 * Format GGUF (petit-boutien) :
 *   magic "GGUF" (4o) | version u32 | tensor_count u64 | metadata_kv_count u64
 *   puis metadata_kv_count paires (clé gguf_string, type u32, valeur)
 *
 * Référence : spec GGUF de ggml/llama.cpp. On ignore volontairement toute
 * clé qui ne figure pas dans [interestingKeys] et on saute les tableaux
 * (souvent le vocabulaire complet, potentiellement plusieurs Mo) sans les
 * matérialiser.
 */
object GgufMetadataReader {

    data class ModelMetadata(
        val name: String?,
        val architecture: String?,
        val quantization: String?,
        val contextLength: Long?,
        val fileSizeBytes: Long,
    )

    private val interestingKeys = setOf(
        "general.name",
        "general.architecture",
        "general.quantization_version",
        "general.file_type",
    )

    private const val TYPE_UINT8 = 0
    private const val TYPE_INT8 = 1
    private const val TYPE_UINT16 = 2
    private const val TYPE_INT16 = 3
    private const val TYPE_UINT32 = 4
    private const val TYPE_INT32 = 5
    private const val TYPE_FLOAT32 = 6
    private const val TYPE_BOOL = 7
    private const val TYPE_STRING = 8
    private const val TYPE_ARRAY = 9
    private const val TYPE_UINT64 = 10
    private const val TYPE_INT64 = 11
    private const val TYPE_FLOAT64 = 12

    fun read(path: String): ModelMetadata? {
        val file = RandomAccessFile(path, "r")
        return try {
            val fileSize = file.length()
            val magic = ByteArray(4).also { file.readFully(it) }
            if (magic.toString(Charsets.US_ASCII) != "GGUF") return null

            val littleEndianBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)

            fun readU32(): Long {
                littleEndianBuf.clear().limit(4)
                file.channel.read(littleEndianBuf)
                littleEndianBuf.flip()
                return littleEndianBuf.int.toLong() and 0xFFFFFFFFL
            }

            fun readU64(): Long {
                littleEndianBuf.clear().limit(8)
                file.channel.read(littleEndianBuf)
                littleEndianBuf.flip()
                return littleEndianBuf.long
            }

            fun readString(): String {
                val len = readU64()
                val bytes = ByteArray(len.coerceIn(0, 1 shl 20).toInt()) // borne défensive : 1 Mio max
                file.readFully(bytes)
                if (len > bytes.size) file.seek(file.filePointer + (len - bytes.size))
                return bytes.toString(Charsets.UTF_8)
            }

            fun scalarSize(type: Int): Int = when (type) {
                TYPE_UINT8, TYPE_INT8, TYPE_BOOL -> 1
                TYPE_UINT16, TYPE_INT16 -> 2
                TYPE_UINT32, TYPE_INT32, TYPE_FLOAT32 -> 4
                TYPE_UINT64, TYPE_INT64, TYPE_FLOAT64 -> 8
                else -> 0
            }

            // Saute une valeur de type [type] sans la matérialiser (utilisé pour les tableaux
            // et les clés qui ne nous intéressent pas).
            fun skipValue(type: Int) {
                when (type) {
                    TYPE_STRING -> {
                        val len = readU64()
                        file.seek(file.filePointer + len)
                    }
                    TYPE_ARRAY -> {
                        val elemType = readU32().toInt()
                        val count = readU64()
                        if (elemType == TYPE_STRING) {
                            repeat(count.toInt().coerceAtLeast(0)) {
                                val len = readU64()
                                file.seek(file.filePointer + len)
                            }
                        } else {
                            val elemSize = scalarSize(elemType)
                            file.seek(file.filePointer + count * elemSize)
                        }
                    }
                    else -> {
                        val size = scalarSize(type)
                        file.seek(file.filePointer + size)
                    }
                }
            }

            fun readScalarAsLong(type: Int): Long? = when (type) {
                TYPE_UINT8, TYPE_INT8 -> file.readByte().toLong()
                TYPE_UINT16, TYPE_INT16 -> {
                    littleEndianBuf.clear().limit(2); file.channel.read(littleEndianBuf)
                    littleEndianBuf.flip(); littleEndianBuf.short.toLong()
                }
                TYPE_UINT32, TYPE_INT32 -> readU32()
                TYPE_UINT64, TYPE_INT64 -> readU64()
                else -> { skipValue(type); null }
            }

            val version = readU32()
            if (version < 2) return ModelMetadata(null, null, null, null, fileSize)
            val tensorCount = readU64()
            val kvCount = readU64()

            var name: String? = null
            var architecture: String? = null
            var quantization: String? = null
            var contextLength: Long? = null

            for (i in 0 until kvCount) {
                if (file.filePointer >= fileSize) break
                val key = readString()
                val type = readU32().toInt()
                val wanted = key in interestingKeys || key.endsWith(".context_length")
                if (!wanted) {
                    skipValue(type)
                    continue
                }
                when (key) {
                    "general.name" -> if (type == TYPE_STRING) name = readString() else skipValue(type)
                    "general.architecture" -> if (type == TYPE_STRING) architecture = readString() else skipValue(type)
                    "general.file_type" -> quantization = readScalarAsLong(type)?.toString() ?: quantization
                    else -> if (key.endsWith(".context_length")) {
                        contextLength = readScalarAsLong(type) ?: contextLength
                    } else {
                        skipValue(type)
                    }
                }
            }

            ModelMetadata(name, architecture, quantization, contextLength, fileSize)
        } catch (_: Exception) {
            // Fichier tronqué, format inattendu, etc. — on dégrade proprement plutôt que de crasher :
            // l'appelant retombe sur le nom de fichier et la taille.
            null
        } finally {
            file.close()
        }
    }
}
