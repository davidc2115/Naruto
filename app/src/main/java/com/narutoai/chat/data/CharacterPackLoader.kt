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
        // Déjà long -> ne pas surcharger
        if (baseClean.length >= 220) return baseClean

        val vibe = when {
            personality.any { it.contains("goth", ignoreCase = true) } -> "une aura sombre et magnétique"
            personality.any { it.contains("romantique", ignoreCase = true) } -> "une présence douce et attirante"
            personality.any { it.contains("dominant", ignoreCase = true) || it.contains("dominante", ignoreCase = true) } -> "une présence contrôlée et sûre d’elle"
            personality.any { it.contains("fantasy", ignoreCase = true) } -> "une aura presque irréelle"
            else -> "une présence marquante"
        }

        val parts = mutableListOf<String>()
        if (baseClean.isNotEmpty()) parts.add(baseClean.trimEnd('.', ' '))

        val details = buildList {
            if (age.isNotBlank()) add("Âge: $age")
            if (height.isNotBlank()) add("Taille: $height")
            if (hairColor.isNotBlank()) add("Cheveux: $hairColor")
            if (eyeColor.isNotBlank()) add("Yeux: $eyeColor")
            if (bodyType.isNotBlank()) add("Silhouette: $bodyType")
        }

        if (details.isNotEmpty()) {
            parts.add(details.joinToString(". ") + ".")
        }

        parts.add("Détails: peau naturelle, expression vivante, regard expressif; $vibe.")
        parts.add("Style: portrait hyper-réaliste, lumière studio, arrière-plan neutre, tenue habillée.")

        return parts.joinToString(" ").replace("\\s+".toRegex(), " ").trim()
    }
}

