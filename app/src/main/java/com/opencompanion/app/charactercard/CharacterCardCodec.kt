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

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun decode(jsonText: String): CharacterCardData {
        val root = json.parseToJsonElement(jsonText).jsonObject
        val dataObject: JsonObject = if (root["spec"]?.jsonPrimitive?.contentOrNull?.startsWith("chara_card") == true) {
            root["data"]?.jsonObject ?: root
        } else {
            root
        }
        return json.decodeFromJsonElement<CharacterCardData>(dataObject)
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
