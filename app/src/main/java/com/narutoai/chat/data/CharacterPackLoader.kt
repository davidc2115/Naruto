package com.narutoai.chat.data

import android.content.Context
import com.narutoai.chat.models.Character
import com.narutoai.chat.models.CharacterCategory
import org.json.JSONArray
import org.json.JSONObject

/**
 * Charge un pack de personnages depuis assets (JSON).
 * Format: tableau JSON d'objets avec champs:
 * - id, name, description, avatarEmoji, category, personality[], physicalDescription, age, height, hairColor, eyeColor, bodyType, scenario, temperament, greetingMessage
 */
object CharacterPackLoader {
    private const val ASSET_FILE = "character_pack_v1.json"

    @Volatile
    private var cached: List<Character>? = null

    fun load(context: Context): List<Character> {
        cached?.let { return it }

        val jsonString = try {
            context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            // Pas de pack embarqué
            cached = emptyList()
            return emptyList()
        }

        val arr = JSONArray(jsonString)
        val characters = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val personality = o.optJSONArray("personality")?.toStringList() ?: emptyList()
                val id = o.optString("id")
                val name = o.optString("name")
                val description = o.optString("description")
                val physicalDescription = buildDetailedPhysicalDescription(
                    base = o.optString("physicalDescription"),
                    age = o.optString("age"),
                    height = o.optString("height"),
                    hairColor = o.optString("hairColor"),
                    eyeColor = o.optString("eyeColor"),
                    bodyType = o.optString("bodyType"),
                    personality = personality
                )
                val scenario = o.optString("scenario")
                val temperament = o.optString("temperament")

                // Thumbnail embarquée: drawable-nodpi/packthumb_<id>.jpg
                val thumbResId = context.resources.getIdentifier(
                    "packthumb_${id.lowercase()}",
                    "drawable",
                    context.packageName
                )

                add(
                    Character(
                        id = id,
                        name = name,
                        description = description,
                        category = parseCategory(o.optString("category")),
                        systemPromptSFW = buildSfwPrompt(
                            name = name,
                            description = description,
                            physicalDescription = physicalDescription,
                            temperament = temperament,
                            scenario = scenario
                        ),
                        systemPromptNSFW = buildNsfwPrompt(
                            name = name,
                            description = description,
                            physicalDescription = physicalDescription,
                            temperament = temperament,
                            scenario = scenario
                        ),
                        avatarEmoji = o.optString("avatarEmoji", "👤"),
                        personality = personality,

                        physicalDescription = physicalDescription,
                        age = o.optString("age"),
                        height = o.optString("height"),
                        hairColor = o.optString("hairColor"),
                        eyeColor = o.optString("eyeColor"),
                        bodyType = o.optString("bodyType"),
                        scenario = scenario,
                        temperament = temperament,
                        greetingMessage = o.optString("greetingMessage"),

                        // Image locale embarquée
                        imageResId = if (thumbResId != 0) thumbResId else 0
                    )
                )
            }
        }

        cached = characters
        return characters
    }

    private fun parseCategory(raw: String): CharacterCategory {
        return when (raw.trim().uppercase()) {
            "CELEBRITY_FEMALE" -> CharacterCategory.CELEBRITY_FEMALE
            "CELEBRITY_MALE" -> CharacterCategory.CELEBRITY_MALE
            "NARUTO" -> CharacterCategory.NARUTO
            else -> CharacterCategory.CELEBRITY_MALE
        }
    }

    private fun buildSfwPrompt(
        name: String,
        description: String,
        physicalDescription: String,
        temperament: String,
        scenario: String
    ): String {
        return """
Tu es ${name}, ${description}.

IMPORTANT: RÉPONDS TOUJOURS EN FRANÇAIS.

Description physique: ${physicalDescription}
Tempérament: ${temperament}
Scénario: ${scenario}

ROLEPLAY (OBLIGATOIRE):
- *actions entre astérisques*
- (pensées entre parenthèses)
- "dialogues"

STYLE:
- Réponses immersives mais pas trop longues (2-6 phrases).
- Reste cohérent(e) avec ton personnage.
        """.trim()
    }

    private fun buildNsfwPrompt(
        name: String,
        description: String,
        physicalDescription: String,
        temperament: String,
        scenario: String
    ): String {
        return """
Tu es ${name}, ${description}.

IMPORTANT: RÉPONDS TOUJOURS EN FRANÇAIS.

Description physique: ${physicalDescription}
Tempérament: ${temperament}
Scénario: ${scenario}

MODE ADULTE (18+):
- Roleplay adulte UNIQUEMENT entre adultes consentants.
- Demande/valide le consentement quand c'est nécessaire.
- Refuse tout contenu incestueux, impliquant des mineurs, ou non-consenti.

ROLEPLAY (OBLIGATOIRE):
- *actions entre astérisques*
- (pensées entre parenthèses)
- "dialogues"

STYLE:
- Immersif, sensuel si l'utilisateur le souhaite, sans sortir du personnage.
        """.trim()
    }

    private fun JSONArray.toStringList(): List<String> {
        return buildList {
            for (i in 0 until length()) {
                add(optString(i))
            }
        }.map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun buildDetailedPhysicalDescription(
        base: String,
        age: String,
        height: String,
        hairColor: String,
        eyeColor: String,
        bodyType: String,
        personality: List<String>
    ): String {
        val baseClean = base.trim()
        // Si déjà très long, on conserve (évite doublons excessifs)
        if (baseClean.length >= 520) return baseClean

        fun extractAgeNumber(raw: String): Int? =
            Regex("(\\d{2})").find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()

        val safeAdultAge = extractAgeNumber(age)?.let { if (it < 18) 21 else it } ?: 21
        val adultAnchor = "Adulte (${safeAdultAge} ans)"

        val vibe = when {
            personality.any { it.contains("goth", ignoreCase = true) } -> "une aura sombre et magnétique"
            personality.any { it.contains("vampire", ignoreCase = true) } -> "une élégance nocturne, presque prédatrice"
            personality.any { it.contains("romantique", ignoreCase = true) } -> "une présence douce et attirante"
            personality.any { it.contains("dominant", ignoreCase = true) || it.contains("dominante", ignoreCase = true) } -> "une assurance calme et autoritaire"
            personality.any { it.contains("sport", ignoreCase = true) || it.contains("workout", ignoreCase = true) } -> "une énergie athlétique et sûre d’elle"
            personality.any { it.contains("fantasy", ignoreCase = true) } -> "une aura légèrement irréelle"
            else -> "une présence marquante"
        }

        val hair = hairColor.takeIf { it.isNotBlank() } ?: "cheveux soignés"
        val eyes = eyeColor.takeIf { it.isNotBlank() } ?: "regard expressif"
        val morpho = bodyType.takeIf { it.isNotBlank() } ?: "silhouette harmonieuse"
        val taille = height.takeIf { it.isNotBlank() } ?: ""

        val sentences = mutableListOf<String>()

        // 1) Base (si fourni) + rappel adulte
        if (baseClean.isNotBlank()) {
            sentences.add("${baseClean.trimEnd('.', ' ')} (${adultAnchor}).")
        } else {
            sentences.add("${adultAnchor}.")
        }

        // 2) Description structurée (plus “visuelle” que des champs bruts)
        sentences.add(
            buildString {
                append("On la reconnaît à ses ")
                append(hair.lowercase())
                append(", à ses yeux ")
                append(eyes.lowercase())
                append(" et à une ")
                append(morpho.lowercase())
                if (taille.isNotBlank()) {
                    append("; elle mesure environ ")
                    append(taille)
                }
                append(".")
            }
        )

        // 3) Visage / peau / détails (génériques mais utiles pour la génération)
        sentences.add("Son visage est bien dessiné, avec une expression vivante, une peau naturelle et un regard qui capte facilement l’attention.")

        // 4) Posture / gestuelle
        sentences.add("Sa posture est assurée; ses gestes sont précis et expressifs, ce qui renforce immédiatement ${vibe}.")

        // 5) Style vestimentaire “safe” (évite infantilisation, reste adulte)
        val style = when {
            personality.any { it.contains("work", true) || it.contains("boss", true) } -> "tenue élégante et professionnelle, lignes nettes, accessoires discrets"
            personality.any { it.contains("club", true) || it.contains("nightlife", true) || it.contains("dj", true) } -> "street‑glam adulte: matières brillantes, contraste, détails soignés"
            personality.any { it.contains("books", true) || it.contains("bibli", true) } -> "style sobre et chic: couleurs douces, coupe classique, allure intellectuelle"
            personality.any { it.contains("sport", true) || it.contains("workout", true) } -> "look sportif adulte: tenue d’entraînement ajustée, pratique et propre"
            personality.any { it.contains("fantasy", true) || it.contains("vampire", true) } -> "tenue habillée et dramatique: tissus nobles, ambiance nocturne, sophistication"
            else -> "casual chic adulte: simple, flatteur, bien entretenu"
        }
        sentences.add("Elle porte généralement une $style.")

        // 6) Consignes photo (cadrage / lumière) pour aider Pollinations
        sentences.add("Rendu souhaité: portrait hyper‑réaliste, traits réalistes, lumière studio douce, fond neutre, textures fidèles (cheveux/peau/yeux).")

        return sentences.joinToString(" ").replace("\\s+".toRegex(), " ").trim()
    }
}

