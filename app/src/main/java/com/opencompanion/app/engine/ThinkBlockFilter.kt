package com.opencompanion.app.engine

/**
 * Filtre streaming qui retire les blocs `<think>...</think>` (et la variante `<thinking>`) du
 * texte généré avant qu'il n'atteigne l'UI et l'historique de conversation.
 *
 * Contexte : les modèles "raisonneurs" comme Qwen3 émettent systématiquement un bloc de
 * raisonnement interne — en anglais, quelle que soit la langue demandée — avant leur vraie
 * réponse, via leur patron de dialogue embarqué. Sans ce filtre, ce bloc (souvent la majorité
 * des tokens produits) s'affichait tel quel dans le chat et grignotait l'essentiel du budget de
 * tokens de la réponse, ce qui pouvait donner l'impression que la conversation "tourne en
 * rond" ou revient sans cesse sur la réplique d'ouverture du personnage : la vraie réponse,
 * une fois le budget presque épuisé par le raisonnement, se réduisait à une paraphrase très
 * courte de ce que le personnage avait déjà dit.
 *
 * Fonctionne en flux (le tag peut être coupé entre deux fragments de tokens successifs) : le
 * texte hors bloc est renvoyé immédiatement par [push], le contenu à l'intérieur d'un bloc est
 * simplement jeté. Une instance par génération, non thread-safe — comme [Utf8StreamDecoder].
 */
class ThinkBlockFilter {
    private val buffer = StringBuilder()
    private var insideThink = false

    /** Ajoute du texte déjà décodé en UTF-8 et renvoie la part visible (hors bloc), immédiatement
     *  disponible (peut être vide, y compris si tout le fragment appartient à un bloc de pensée). */
    fun push(chunk: String): String {
        buffer.append(chunk)
        val out = StringBuilder()

        var progressed = true
        while (progressed) {
            progressed = false
            if (!insideThink) {
                val match = findEarliestTag(buffer, OPEN_TAGS)
                if (match != null) {
                    out.append(buffer, 0, match.index)
                    buffer.delete(0, match.index + match.tag.length)
                    insideThink = true
                    progressed = true
                } else {
                    // Pas de tag d'ouverture complet pour l'instant : on peut tout émettre, sauf
                    // un éventuel début de tag coupé en fin de buffer (à confirmer/infirmer au
                    // prochain fragment).
                    val holdBack = longestPartialTagSuffix(buffer, OPEN_TAGS)
                    val emitUpTo = buffer.length - holdBack
                    if (emitUpTo > 0) {
                        out.append(buffer, 0, emitUpTo)
                        buffer.delete(0, emitUpTo)
                    }
                }
            } else {
                val match = findEarliestTag(buffer, CLOSE_TAGS)
                if (match != null) {
                    buffer.delete(0, match.index + match.tag.length)
                    insideThink = false
                    progressed = true
                } else {
                    // À l'intérieur d'un bloc de pensée : tout est jeté, sauf un éventuel début
                    // de tag fermant coupé en fin de buffer.
                    val holdBack = longestPartialTagSuffix(buffer, CLOSE_TAGS)
                    if (buffer.length > holdBack) {
                        buffer.delete(0, buffer.length - holdBack)
                    }
                }
            }
        }
        return out.toString()
    }

    /** À appeler en fin de génération. Si la génération s'est arrêtée au milieu d'un bloc de
     *  pensée (budget de tokens épuisé avant la fermeture), il n'y a rien de fiable à montrer :
     *  on ne renvoie que le texte en attente hors bloc, jamais du contenu de raisonnement brut. */
    fun flush(): String {
        val result = if (!insideThink) buffer.toString() else ""
        buffer.setLength(0)
        insideThink = false
        return result
    }

    private data class TagMatch(val index: Int, val tag: String)

    private fun findEarliestTag(sb: StringBuilder, tags: List<String>): TagMatch? {
        val haystack = sb.toString().lowercase()
        var best: TagMatch? = null
        for (tag in tags) {
            val idx = haystack.indexOf(tag)
            if (idx >= 0 && (best == null || idx < best.index)) best = TagMatch(idx, tag)
        }
        return best
    }

    /** Longueur du plus long suffixe de [sb] qui pourrait être le début d'un des [tags] — pour ne
     *  jamais émettre (ou jeter) prématurément un tag coupé entre deux fragments du flux. */
    private fun longestPartialTagSuffix(sb: StringBuilder, tags: List<String>): Int {
        val haystack = sb.toString().lowercase()
        val n = haystack.length
        var maxLen = 0
        for (tag in tags) {
            val limit = minOf(tag.length - 1, n)
            for (len in limit downTo 1) {
                if (haystack.regionMatches(n - len, tag, 0, len)) {
                    if (len > maxLen) maxLen = len
                    break
                }
            }
        }
        return maxLen
    }

    private companion object {
        val OPEN_TAGS = listOf("<think>", "<thinking>")
        val CLOSE_TAGS = listOf("</think>", "</thinking>")
    }
}
