package com.opencompanion.app.charactercard

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Modèle de données correspondant à la spec ouverte "Character Card V2"
 * (github.com/malfoyslastname/character-card-spec-v2), utilisée par la
 * plupart des sites communautaires de fiches de personnages pour IA
 * conversationnelle. Reproduire fidèlement ces noms de champs permet un
 * import/export interopérable sans format maison.
 */
@Serializable
data class CharacterCardV2(
    val spec: String = "chara_card_v2",
    @SerialName("spec_version") val specVersion: String = "2.0",
    val data: CharacterCardData,
)

@Serializable
data class CharacterCardData(
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    @SerialName("first_mes") val firstMes: String = "",
    @SerialName("mes_example") val mesExample: String = "",
    @SerialName("creator_notes") val creatorNotes: String = "",
    @SerialName("system_prompt") val systemPrompt: String = "",
    @SerialName("post_history_instructions") val postHistoryInstructions: String = "",
    @SerialName("alternate_greetings") val alternateGreetings: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val creator: String = "",
    @SerialName("character_version") val characterVersion: String = "",
    val extensions: JsonObject? = null,
)
