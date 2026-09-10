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
    private val personaDao: UserPersonaDao,
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

    // --- Personas utilisateur (voir UserPersonaEntity) --------------------------------------

    fun observePersonas(): Flow<List<UserPersonaEntity>> = personaDao.observeAll()

    suspend fun getPersona(id: Long): UserPersonaEntity? = personaDao.getById(id)

    suspend fun savePersona(persona: UserPersonaEntity): Long {
        val id = personaDao.upsert(persona)
        // Le tout premier persona créé devient automatiquement le persona par défaut : sans ça,
        // un utilisateur qui crée un unique persona devrait encore explicitement le marquer par
        // défaut pour qu'il serve à quoi que ce soit.
        if (persona.isDefault || personaDao.count() == 1) {
            setDefaultPersona(if (id > 0) id else persona.id)
        }
        return id
    }

    suspend fun deletePersona(persona: UserPersonaEntity) = personaDao.delete(persona)

    /** Garantit qu'un seul persona est marqué par défaut à la fois. */
    suspend fun setDefaultPersona(id: Long) {
        val persona = personaDao.getById(id) ?: return
        personaDao.update(persona.copy(isDefault = true))
        personaDao.clearDefaultExcept(id)
    }

    /**
     * Résout le persona utilisateur actif pour [character] : son choix explicite
     * ([CharacterEntity.activePersonaId]) s'il existe encore, sinon le persona par défaut, sinon
     * null si aucun persona n'a encore été créé (l'appelant retombe alors sur un profil
     * générique — voir ChatViewModel).
     */
    suspend fun resolveActivePersona(character: CharacterEntity): UserPersonaEntity? {
        character.activePersonaId?.let { id -> personaDao.getById(id)?.let { return it } }
        return personaDao.getDefault() ?: personaDao.getAll().firstOrNull()
    }

    suspend fun setActivePersonaForCharacter(characterId: Long, personaId: Long?) {
        val character = characterDao.getById(characterId) ?: return
        characterDao.update(character.copy(activePersonaId = personaId, updatedAt = System.currentTimeMillis()))
    }

    /**
     * Amorce un premier persona à partir de l'ancien profil unique stocké dans les réglages
     * ([SettingsRepository.UserProfile]), la toute première fois qu'un persona est nécessaire
     * mais qu'aucun n'existe encore — pour qu'un nom déjà renseigné avant l'arrivée des personas
     * multiples ne soit pas perdu silencieusement.
     */
    suspend fun ensureDefaultPersonaSeeded(legacyProfile: UserProfile) {
        if (personaDao.count() > 0) return
        savePersona(
            UserPersonaEntity(
                name = legacyProfile.name.ifBlank { "Moi" },
                age = legacyProfile.age,
                gender = legacyProfile.gender.name,
                isDefault = true,
            ),
        )
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
            firstMessage = "*lève les yeux de ses notes en t'entendant arriver* Ah, te voilà ! " +
                "J'étais justement en train de trier mes notes sur une expédition complètement " +
                "folle. Tu as une minute ? J'ai plein de choses à raconter.",
            exampleDialogue = "{{user}} : Tu reviens d'où cette fois ?\n" +
                "{{char}} : *pose son sac à dos avec un soupir de soulagement* D'un site que " +
                "personne n'avait cartographié depuis des décennies !\n" +
                "{{user}} : Sérieux ? Raconte.\n" +
                "{{char}} : Une porte scellée qu'il a fallu crocheter à la lampe frontale. (elle " +
                "retient un fou rire en y repensant)\n" +
                "\n" +
                "Une porte, oui. Ne me demande pas ce qu'il y avait derrière, tu vas pas y croire.",
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
            firstMessage = "*referme un vieil ouvrage avec précaution et te regarde par-dessus " +
                "ses lunettes* Bienvenue. Les rayonnages sont vastes et le temps, ici, n'a pas " +
                "vraiment cours. Que cherches-tu aujourd'hui ?",
            exampleDialogue = "{{user}} : Tu as déjà tout lu ?\n" +
                "{{char}} : Presque. Il me reste une étagère entière, là-bas. *désigne un coin " +
                "sombre du regard*\n" +
                "{{user}} : Ça parle de quoi ?\n" +
                "{{char}} : Navigation, XVIIe siècle. (il sent déjà la question suivante arriver) " +
                "Passionnant, à petites doses.",
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
            firstMessage = "*essuie ses mains sur son tablier et te fait signe d'approcher* " +
                "Salut ! Installe-toi, le four a encore vingt minutes. Dis-moi tout, ou goûte ça " +
                "en attendant — nouvelle recette, verdict sans pitié accepté.",
            exampleDialogue = "{{user}} : Tu cuisines quoi aujourd'hui ?\n" +
                "{{char}} : Une recette piquée à une grand-mère sur un marché. *te tend une " +
                "petite cuillère* Goûte.\n" +
                "{{user}} : C'est bon !\n" +
                "{{char}} : (elle croise discrètement les doigts, sans l'admettre à voix haute) " +
                "Ah, ça me rassure.",
            tagsCsv = "chaleureux,exemple",
            creatorNotes = "Personnage d'exemple fourni avec l'application.",
            isBundledSample = true,
        ),
    )
}
