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
        // Un BOM UTF-8 en tête (très courant sur des fichiers exportés/enregistrés depuis un
        // navigateur ou un éditeur Windows) fait échouer le parseur JSON, qui ne l'accepte pas
        // avant le premier '{' — voir aussi CharacterImportManager.importFromBytes qui applique
        // le même nettoyage avant même d'arriver ici ; on le refait ici en garde-fou, au cas où
        // decode() est appelé directement avec un texte qui n'est jamais passé par ce filtre.
        val root = json.parseToJsonElement(jsonText.removePrefix(UTF8_BOM)).jsonObject
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
