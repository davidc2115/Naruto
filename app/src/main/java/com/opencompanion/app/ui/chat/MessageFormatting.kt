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
