package com.opencompanion.app.engine

/**
 * Décodeur UTF-8 incrémental pour le streaming token par token.
 *
 * Un tokenizer BPE peut couper un caractère multi-octets (accent français,
 * emoji…) entre deux tokens : chaque appel à [LlamaBridge.nativeGenerate]
 * peut donc livrer un fragment d'octets UTF-8 invalide *isolément* mais
 * valide une fois concaténé au fragment suivant. Ce décodeur conserve les
 * octets de tête incomplets d'un appel à l'autre et ne renvoie que du texte
 * décodable avec certitude.
 *
 * Non thread-safe : une instance par génération en cours.
 */
class Utf8StreamDecoder {
    private val pending = mutableListOf<Byte>()

    /** Nombre total d'octets attendus pour une séquence UTF-8 démarrant par [firstByte]. */
    private fun expectedLength(firstByte: Byte): Int {
        val b = firstByte.toInt() and 0xFF
        return when {
            b and 0x80 == 0x00 -> 1 // 0xxxxxxx
            b and 0xE0 == 0xC0 -> 2 // 110xxxxx
            b and 0xF0 == 0xE0 -> 3 // 1110xxxx
            b and 0xF8 == 0xF0 -> 4 // 11110xxx
            else -> 1 // octet de continuation isolé ou invalide : on l'isole pour ne pas bloquer
        }
    }

    /** Ajoute des octets et renvoie le texte décodable dès maintenant (peut être vide). */
    fun push(bytes: ByteArray): String {
        pending.addAll(bytes.toList())

        var completeUpTo = 0
        var i = 0
        while (i < pending.size) {
            val needed = expectedLength(pending[i])
            if (i + needed > pending.size) {
                // Séquence incomplète en fin de buffer : on attend le prochain fragment.
                break
            }
            i += needed
            completeUpTo = i
        }

        if (completeUpTo == 0) return ""

        val ready = ByteArray(completeUpTo) { pending[it] }
        repeat(completeUpTo) { pending.removeAt(0) }
        return ready.toString(Charsets.UTF_8)
    }

    /** À appeler en fin de génération : renvoie les octets restants tels quels (best effort). */
    fun flush(): String {
        if (pending.isEmpty()) return ""
        val remaining = pending.toByteArray()
        pending.clear()
        return remaining.toString(Charsets.UTF_8)
    }
}
