package com.opencompanion.app.ui.characterlist

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencompanion.app.charactercard.CharacterImportManager
import com.opencompanion.app.data.CharacterEntity
import com.opencompanion.app.data.CharacterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HomeTab {
    DISCOVERY,
    CHATS,
}

data class TemperamentTagItem(val name: String, val emoji: String)

val ALL_TEMPERAMENTS = listOf(
    TemperamentTagItem("Timide", "🙈"),
    TemperamentTagItem("Séductrice", "🔥"),
    TemperamentTagItem("Taquine", "😏"),
    TemperamentTagItem("Flirteuse", "💋"),
    TemperamentTagItem("Joueuse", "🎲"),
    TemperamentTagItem("Provocatrice", "⚡"),
    TemperamentTagItem("Aguicheuse", "💄"),
    TemperamentTagItem("Sensuelle", "✨"),
    TemperamentTagItem("Douce", "🌸"),
    TemperamentTagItem("Autoritaire", "👑"),
    TemperamentTagItem("Réservée", "🤫"),
    TemperamentTagItem("Passionnée", "🌋"),
    TemperamentTagItem("Pétillante", "🎉"),
    TemperamentTagItem("Épicurienne", "🍷"),
    TemperamentTagItem("Mystérieuse", "🌙"),
    TemperamentTagItem("Fière", "💎"),
)

class CharacterListViewModel(
    private val repository: CharacterRepository,
    private val importManager: CharacterImportManager,
) : ViewModel() {

    val characters: StateFlow<List<CharacterEntity>> =
        repository.observeCharacters().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeChats: StateFlow<List<com.opencompanion.app.data.ActiveChatConversation>> =
        repository.observeActiveChats().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentTab = MutableStateFlow(HomeTab.DISCOVERY)

    fun selectTab(tab: HomeTab) {
        currentTab.value = tab
    }

    val searchQuery = MutableStateFlow("")
    val selectedTag = MutableStateFlow<String?>(null)
    val selectedTemperament = MutableStateFlow<String?>(null)

    val filteredCharacters: StateFlow<List<CharacterEntity>> =
        combine(characters, searchQuery, selectedTag, selectedTemperament) { list, query, tag, temp ->
            list.filter { char ->
                val matchesQuery = query.isBlank() ||
                    char.name.contains(query, ignoreCase = true) ||
                    char.description.contains(query, ignoreCase = true) ||
                    char.personality.contains(query, ignoreCase = true) ||
                    char.scenario.contains(query, ignoreCase = true) ||
                    char.tags.any { it.contains(query, ignoreCase = true) }
                val matchesTag = tag == null || char.tags.any { it.equals(tag, ignoreCase = true) }
                val matchesTemp = temp == null || char.tags.any { it.contains(temp, ignoreCase = true) } || char.personality.contains(temp, ignoreCase = true)
                matchesQuery && matchesTag && matchesTemp
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val popularTags: StateFlow<List<String>> =
        characters.map { list ->
            list.flatMap { it.tags }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .toList()
                .sortedByDescending { it.second }
                .map { it.first }
                .take(25)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setSelectedTag(tag: String?) {
        selectedTag.value = if (selectedTag.value == tag) null else tag
    }

    fun setSelectedTemperament(temp: String?) {
        selectedTemperament.value = if (selectedTemperament.value == temp) null else temp
    }

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage.asStateFlow()

    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            when (val result = importManager.importFromUri(uri)) {
                is CharacterImportManager.ImportResult.Success ->
                    _importMessage.value = "« ${result.name} » importé."
                is CharacterImportManager.ImportResult.Failure ->
                    _importMessage.value = "Import impossible : ${result.reason}"
            }
        }
    }

    /**
     * Import en lot : le sélecteur de fichiers ("Importer plusieurs fiches") peut renvoyer
     * plusieurs fichiers d'un coup — on les importe l'un après l'autre (chacun reste indépendant :
     * un fichier invalide n'interrompt pas les suivants) puis on résume le résultat en un seul
     * message plutôt que d'empiler une snackbar par fichier.
     */
    fun importFromUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var successCount = 0
            val failures = mutableListOf<String>()
            for (uri in uris) {
                when (val result = importManager.importFromUri(uri)) {
                    is CharacterImportManager.ImportResult.Success -> successCount++
                    is CharacterImportManager.ImportResult.Failure -> failures += result.reason
                }
            }
            _importMessage.value = buildString {
                append(
                    when (successCount) {
                        0 -> "Aucun personnage importé."
                        1 -> "1 personnage importé."
                        else -> "$successCount personnages importés."
                    },
                )
                if (failures.isNotEmpty()) {
                    append(" ${failures.size} échec(s).")
                }
            }
        }
    }

    fun importFromUrl(url: String) {
        viewModelScope.launch {
            when (val result = importManager.importFromUrl(url)) {
                is CharacterImportManager.ImportResult.Success ->
                    _importMessage.value = "« ${result.name} » importé."
                is CharacterImportManager.ImportResult.Failure ->
                    _importMessage.value = "Import impossible : ${result.reason}"
            }
        }
    }

    fun deleteCharacter(character: CharacterEntity) {
        viewModelScope.launch { repository.deleteCharacter(character) }
    }

    fun consumeImportMessage() {
        _importMessage.value = null
    }
}
