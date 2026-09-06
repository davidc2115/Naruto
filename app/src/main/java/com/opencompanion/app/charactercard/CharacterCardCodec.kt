package com.opencompanion.app.charactercard

import com.opencompanion.app.data.CharacterEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Encodage/décodage JSON tolérant : accepte aussi bien une fiche V2 complète
 * (`{"spec":"chara_card_v2","data":{...}}`) que le format V1 historique où
 * les mêmes champs sont directement à la racine du JSON — les deux
 * circulent activement sur les sites communautaires.
 */
object CharacterCardCodec {

    /** BOM UTF-8 (U+FEFF), construit à partir de son point de code plutôt qu'écrit comme
     *  caractère littéral dans le fichier source, pour éviter toute ambiguïté d'encodage à
     *  l'édition — un caractère littéralement invisible dans un éditeur. Voir [decode]. */
    val UTF8_BOM: String = 0xFEFF.toChar().toString()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun decode(jsonText: String): CharacterCardData {
        val cleanJson = jsonText.removePrefix(UTF8_BOM).trim()
        val root = json.parseToJsonElement(cleanJson).jsonObject

        val dataObject: JsonObject = when {
            root["spec"]?.jsonPrimitive?.contentOrNull?.startsWith("chara_card") == true -> {
                root["data"]?.jsonObject ?: root
            }
            root.containsKey("data") && root["data"] is JsonObject -> {
                root["data"]!!.jsonObject
            }
            root.containsKey("character") && root["character"] is JsonObject -> {
                root["character"]!!.jsonObject
            }
            root.containsKey("chara") && root["chara"] is JsonObject -> {
                root["chara"]!!.jsonObject
            }
            root.containsKey("card") && root["card"] is JsonObject -> {
                root["card"]!!.jsonObject
            }
            else -> root
        }

        val decoded = try {
            json.decodeFromJsonElement<CharacterCardData>(dataObject)
        } catch (_: Exception) {
            CharacterCardData()
        }

        fun getStringField(obj: JsonObject, vararg keys: String): String {
            for (key in keys) {
                val element = obj[key] ?: continue
                val str = try { element.jsonPrimitive.contentOrNull } catch (_: Exception) { null }
                if (!str.isNullOrBlank()) return str
            }
            return ""
        }

        val finalName = decoded.name.ifBlank { getStringField(dataObject, "name", "char_name", "title") }
        val finalDescription = decoded.description.ifBlank { getStringField(dataObject, "description", "char_persona", "summary", "persona", "definition") }
        val finalPersonality = decoded.personality.ifBlank { getStringField(dataObject, "personality", "char_personality", "traits") }
        val finalScenario = decoded.scenario.ifBlank { getStringField(dataObject, "scenario", "world_scenario", "situation") }
        val finalFirstMes = decoded.firstMes.ifBlank { getStringField(dataObject, "first_mes", "greeting", "first_message", "intro") }
        val finalMesExample = decoded.mesExample.ifBlank { getStringField(dataObject, "mes_example", "example_dialogue", "dialogue_examples", "mes_examples", "examples") }
        val finalCreatorNotes = decoded.creatorNotes.ifBlank { getStringField(dataObject, "creator_notes", "comment", "notes") }
        val finalSystemPrompt = decoded.systemPrompt.ifBlank { getStringField(dataObject, "system_prompt", "custom_text", "system_prompt_override") }

        return decoded.copy(
            name = finalName,
            description = finalDescription,
            personality = finalPersonality,
            scenario = finalScenario,
            firstMes = finalFirstMes,
            mesExample = finalMesExample,
            creatorNotes = finalCreatorNotes,
            systemPrompt = finalSystemPrompt,
        )
    }

    fun encode(entity: CharacterEntity): String {
        val card = CharacterCardV2(
            data = CharacterCardData(
                name = entity.name,
                description = entity.description,
                personality = entity.personality,
                scenario = entity.scenario,
                firstMes = entity.firstMessage,
                mesExample = entity.exampleDialogue,
                creatorNotes = entity.creatorNotes,
                systemPrompt = entity.systemPromptOverride,
                tags = entity.tags,
                creator = entity.creator,
                characterVersion = entity.characterVersion,
            ),
        )
        return json.encodeToString(card)
    }
}

fun CharacterCardData.toEntity(isBundledSample: Boolean = false): CharacterEntity = CharacterEntity(
    name = name.ifBlank { "Personnage importé" },
    description = description,
    personality = personality,
    scenario = scenario,
    firstMessage = firstMes,
    exampleDialogue = mesExample,
    systemPromptOverride = systemPrompt,
    tagsCsv = tags.joinToString(","),
    creatorNotes = creatorNotes,
    creator = creator,
    characterVersion = characterVersion,
    isBundledSample = isBundledSample,
)
