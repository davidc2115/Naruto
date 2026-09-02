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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CharacterListViewModel(
    private val repository: CharacterRepository,
    private val importManager: CharacterImportManager,
) : ViewModel() {

    val characters: StateFlow<List<CharacterEntity>> =
        repository.observeCharacters().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
