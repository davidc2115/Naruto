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
                val name = o.optString("name")
                val description = o.optString("description")
                val physicalDescription = o.optString("physicalDescription")
                val scenario = o.optString("scenario")
                val temperament = o.optString("temperament")

                add(
                    Character(
                        id = o.optString("id"),
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
                        greetingMessage = o.optString("greetingMessage")
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
}

