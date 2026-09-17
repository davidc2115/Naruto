package com.opencompanion.app.ui.charactereditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencompanion.app.data.CharacterEntity
import com.opencompanion.app.data.CharacterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CharacterEditorState(
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val exampleDialogue: String = "",
    val systemPromptOverride: String = "",
    val avatarPath: String? = null,
    val saved: Boolean = false,
) {
    val isNew: Boolean get() = id == 0L
    val isValid: Boolean get() = name.isNotBlank()
}

class CharacterEditorViewModel(
    private val repository: CharacterRepository,
    private val characterId: Long?,
    private val cloudBridge: com.opencompanion.app.engine.CloudEngineBridge? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(CharacterEditorState())
    val state: StateFlow<CharacterEditorState> = _state.asStateFlow()

    // Conserve les champs non exposés dans le formulaire (tags, notes, créateur…) pour ne pas
    // les perdre lors d'un enregistrement après modification d'un personnage importé.
    private var originalEntity: CharacterEntity? = null

    init {
        if (characterId != null && characterId != 0L) {
            viewModelScope.launch {
                repository.getCharacter(characterId)?.let { c ->
                    originalEntity = c
                    _state.value = CharacterEditorState(
                        id = c.id,
                        name = c.name,
                        description = c.description,
                        personality = c.personality,
                        scenario = c.scenario,
                        firstMessage = c.firstMessage,
                        exampleDialogue = c.exampleDialogue,
                        systemPromptOverride = c.systemPromptOverride,
                        avatarPath = c.avatarPath,
                    )
                }
            }
        }
    }

    fun update(transform: (CharacterEditorState) -> CharacterEditorState) {
        _state.value = transform(_state.value)
    }

    fun save(
        alsoPushToGitHub: Boolean = false,
        onGitHubResult: (Boolean, String) -> Unit = { _, _ -> },
    ) {
        val s = _state.value
        if (!s.isValid) return
        viewModelScope.launch {
            val base = originalEntity ?: CharacterEntity(name = s.name)
            val toSave = base.copy(
                id = s.id,
                name = s.name.trim(),
                description = s.description,
                personality = s.personality,
                scenario = s.scenario,
                firstMessage = s.firstMessage,
                exampleDialogue = s.exampleDialogue,
                systemPromptOverride = s.systemPromptOverride,
                avatarPath = s.avatarPath,
                isCustomizedByUser = true,
            )
            val newId = repository.saveCharacter(toSave)
            if (alsoPushToGitHub && cloudBridge != null) {
                val gitRes = cloudBridge.uploadCharacterCardToGitHub(toSave.copy(id = if (toSave.id > 0) toSave.id else newId))
                gitRes.onSuccess { path ->
                    onGitHubResult(true, "Sauvegardé localement et envoyé sur GitHub ($path) !")
                }.onFailure { err ->
                    onGitHubResult(false, "Sauvegardé en local. Échec GitHub : ${err.message}")
                }
            }
            _state.value = s.copy(saved = true)
        }
    }
}
