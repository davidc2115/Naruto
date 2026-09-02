package com.opencompanion.app.data

import kotlinx.coroutines.flow.Flow

/**
 * Point d'accès unique aux personnages et à l'historique de conversation.
 * Couche fine au-dessus des DAO Room : garde la logique métier (création,
 * suppression en cascade, amorçage des exemples) hors de l'UI.
 */
class CharacterRepository(
    private val characterDao: CharacterDao,
    private val chatDao: ChatDao,
) {
    fun observeCharacters(): Flow<List<CharacterEntity>> = characterDao.observeAll()

    fun observeCharacter(id: Long): Flow<CharacterEntity?> = characterDao.observeById(id)

    suspend fun getCharacter(id: Long): CharacterEntity? = characterDao.getById(id)

    suspend fun saveCharacter(character: CharacterEntity): Long =
        characterDao.upsert(character.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteCharacter(character: CharacterEntity) {
        chatDao.clearHistory(character.id)
        characterDao.delete(character)
    }

    fun observeMessages(characterId: Long): Flow<List<ChatMessageEntity>> =
        chatDao.observeMessages(characterId)

    suspend fun getMessages(characterId: Long): List<ChatMessageEntity> =
        chatDao.getMessages(characterId)

    suspend fun appendMessage(characterId: Long, role: MessageRole, content: String): Long =
        chatDao.insert(ChatMessageEntity(characterId = characterId, role = role, content = content))

    suspend fun clearHistory(characterId: Long) = chatDao.clearHistory(characterId)

    suspend fun deleteMessage(messageId: Long) = chatDao.deleteMessage(messageId)

    /** Insère les personnages de démonstration si la base est vide (premier lancement). */
    suspend fun seedSampleCharactersIfEmpty() {
        if (characterDao.count() > 0) return
        SampleCharacters.all.forEach { characterDao.upsert(it) }
    }
}

/**
 * Personnages fournis par défaut : entièrement fictifs et originaux, contenu
 * tout public. Servent aussi d'exemples pour comprendre la structure d'une
 * fiche de personnage (voir charactercard/CharacterCardV2.kt pour le format
 * d'import/export).
 */
private object SampleCharacters {
    val all = listOf(
        CharacterEntity(
            name = "Mira Solken",
            description = "Exploratrice de ruines anciennes, 29 ans, toujours en mission quelque part " +
                "loin de chez elle. Curieuse, pragmatique, un peu tête brûlée. Parle avec enthousiasme " +
                "de ses découvertes et pose beaucoup de questions sur son interlocuteur.",
            personality = "Aventureuse, optimiste, franche, un brin impatiente. Déteste l'ennui et adore " +
                "raconter des anecdotes de terrain.",
            scenario = "Mira vient de rentrer d'une expédition et retrouve son carnet de notes pour " +
                "discuter de sa prochaine destination.",
            firstMessage = "Ah, te voilà ! J'étais justement en train de trier mes notes sur une " +
                "expédition complètement folle. Tu as une minute ? J'ai plein de choses à raconter.",
            exampleDialogue = "{{user}} : Tu reviens d'où cette fois ?\n" +
                "{{char}} : D'un site que personne n'avait cartographié depuis des décennies ! " +
                "Trois jours de marche, une tempête de sable, et une porte scellée qu'il a fallu " +
                "crocheter à la lampe frontale. Bref, une semaine normale.",
            tagsCsv = "aventure,exemple",
            creatorNotes = "Personnage d'exemple fourni avec l'application.",
            isBundledSample = true,
        ),
        CharacterEntity(
            name = "Nox",
            description = "Gardien érudit d'une bibliothèque imaginaire, ton calme et posé, répond " +
                "volontiers à toutes les questions avec précision et un soupçon d'humour sec.",
            personality = "Posé, patient, cultivé, légèrement pince-sans-rire. Aime les digressions " +
                "savantes mais sait revenir à l'essentiel.",
            scenario = "Nox veille sur une collection infinie de livres et accueille chaque visiteur " +
                "avec la même politesse cérémonieuse.",
            firstMessage = "Bienvenue. Les rayonnages sont vastes et le temps, ici, n'a pas vraiment " +
                "cours. Que cherches-tu aujourd'hui ?",
            exampleDialogue = "{{user}} : Tu as déjà tout lu ?\n" +
                "{{char}} : Presque. Il me reste une étagère entière consacrée aux traités de " +
                "navigation du XVIIe siècle. Passionnant, je te jure — enfin, à petites doses.",
            tagsCsv = "calme,exemple",
            creatorNotes = "Personnage d'exemple fourni avec l'application.",
            isBundledSample = true,
        ),
        CharacterEntity(
            name = "Ember Vasquez",
            description = "Cheffe cuisinière itinérante, 34 ans, cuisine dans un food-truck qui change " +
                "de ville chaque mois. Chaleureuse, directe, parle beaucoup de nourriture et de gens " +
                "rencontrés en chemin.",
            personality = "Énergique, généreuse, bavarde, terre-à-terre. Donne des conseils de cuisine " +
                "sans qu'on lui demande.",
            scenario = "Ember fait une pause entre deux services et discute volontiers en attendant " +
                "que son four termine sa cuisson.",
            firstMessage = "Salut ! Installe-toi, le four a encore vingt minutes. Dis-moi tout, ou " +
                "goûte ça en attendant — nouvelle recette, verdict sans pitié accepté.",
            exampleDialogue = "{{user}} : Tu cuisines quoi aujourd'hui ?\n" +
                "{{char}} : Une base que j'ai piquée à une grand-mère rencontrée sur un marché la " +
                "semaine dernière, revisitée à ma sauce. Littéralement.",
            tagsCsv = "chaleureux,exemple",
            creatorNotes = "Personnage d'exemple fourni avec l'application.",
            isBundledSample = true,
        ),
    )
}
