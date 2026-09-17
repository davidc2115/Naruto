package com.opencompanion.app.ui.chat

/**
 * Segmente un message selon la convention dialogue / action / pensée enseignée au modèle par
 * PromptBuilder.ROLEPLAY_FORMAT_DIRECTIVE : texte normal = dialogue, texte entre *astérisques* =
 * action/langage corporel, texte entre (parenthèses) = pensée intérieure du personnage. Rendu
 * ensuite par ChatScreen.MessageBubble (police italique + couleur dédiée par catégorie) pour des
 * bulles de conversation bien plus immersives qu'un simple mur de texte.
 *
 * Parseur caractère par caractère plutôt qu'une regex : plus robuste face à un délimiteur non
 * refermé (réponse coupée par la limite de tokens, personnage qui n'a pas encore appris la
 * convention, faute de frappe…) — dans ce cas le texte restant est conservé tel quel dans le
 * dialogue plutôt que d'être perdu ou de casser le rendu.
 *
 * Compromis assumé : une parenthèse "normale" (aparté grammatical plutôt que pensée) sera aussi
 * stylée comme une pensée. Sans ambiguïté possible côté modèle sans complexifier la convention
 * qu'on lui enseigne, et sans conséquence sur le texte réel affiché — seul son style visuel en
 * est affecté.
 */
sealed class MessageSegment {
    data class Dialogue(val text: String) : MessageSegment()
    data class Action(val text: String) : MessageSegment()
    data class Thought(val text: String) : MessageSegment()
    data class Speaker(val name: String) : MessageSegment()
}

fun parseMessageSegments(raw: String): List<MessageSegment> {
    val segments = mutableListOf<MessageSegment>()
    val dialogueBuf = StringBuilder()

    fun flushDialogue() {
        if (dialogueBuf.isNotEmpty()) {
            segments.add(MessageSegment.Dialogue(dialogueBuf.toString()))
            dialogueBuf.clear()
        }
    }

    var i = 0
    val n = raw.length
    while (i < n) {
        // 1. Détection des noms de locuteurs multi-personnages : **Nom** : ou [Nom] :
        if (raw.startsWith("**", i)) {
            val close = raw.indexOf("**", i + 2)
            if (close in (i + 3)..(i + 35)) {
                val candidateName = raw.substring(i + 2, close).trim()
                var after = close + 2
                while (after < n && raw[after] == ' ') after++
                if (after < n && raw[after] == ':') {
                    flushDialogue()
                    segments.add(MessageSegment.Speaker(candidateName))
                    i = after + 1
                    while (i < n && (raw[i] == ' ' || raw[i] == '\t')) i++
                    continue
                }
            }
        }

        if (raw[i] == '[') {
            val close = raw.indexOf(']', i + 1)
            if (close in (i + 2)..(i + 35)) {
                val candidateName = raw.substring(i + 1, close).trim()
                var after = close + 1
                while (after < n && raw[after] == ' ') after++
                if (after < n && raw[after] == ':') {
                    flushDialogue()
                    segments.add(MessageSegment.Speaker(candidateName))
                    i = after + 1
                    while (i < n && (raw[i] == ' ' || raw[i] == '\t')) i++
                    continue
                }
            }
        }

        // 2. Détection d'un nom de locuteur en début de ligne : Nom :
        if (i == 0 || raw[i - 1] == '\n') {
            val lineEnd = raw.indexOf('\n', i).let { if (it < 0) n else it }
            val colon = raw.indexOf(':', i)
            if (colon in (i + 2) until lineEnd && (colon - i) <= 30) {
                val candidate = raw.substring(i, colon).trim()
                if (candidate.matches(Regex("""^[A-ZÉÈÀÂÇÎÏÔ][a-zA-Z0-9À-ÿ\s.'-]{1,27}$""")) &&
                    !candidate.contains('*') && !candidate.contains('(') && !candidate.contains(')')) {
                    flushDialogue()
                    segments.add(MessageSegment.Speaker(candidate))
                    i = colon + 1
                    while (i < n && (raw[i] == ' ' || raw[i] == '\t')) i++
                    continue
                }
            }
        }

        when (val c = raw[i]) {
            '*' -> {
                val close = raw.indexOf('*', i + 1)
                if (close < 0) {
                    dialogueBuf.append(c)
                    i++
                } else {
                    val inner = raw.substring(i + 1, close).trim()
                    flushDialogue()
                    if (inner.isNotEmpty()) segments.add(MessageSegment.Action(inner))
                    i = close + 1
                }
            }
            '(' -> {
                val close = raw.indexOf(')', i + 1)
                if (close < 0) {
                    dialogueBuf.append(c)
                    i++
                } else {
                    val inner = raw.substring(i + 1, close).trim()
                    flushDialogue()
                    if (inner.isNotEmpty()) segments.add(MessageSegment.Thought(inner))
                    i = close + 1
                }
            }
            else -> {
                dialogueBuf.append(c)
                i++
            }
        }
    }
    flushDialogue()
    return segments
}
