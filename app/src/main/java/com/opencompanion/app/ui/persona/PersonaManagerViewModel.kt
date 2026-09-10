package com.opencompanion.app.ui.persona

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencompanion.app.data.CharacterRepository
import com.opencompanion.app.data.UserGender
import com.opencompanion.app.data.UserPersonaEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Gère la liste des personas utilisateur (voir [UserPersonaEntity]) : créer, modifier, supprimer,
 * marquer par défaut. Chaque persona peut ensuite être choisi indépendamment pour chaque
 * personnage/conversation (voir ChatViewModel.setActivePersona) — la fonctionnalité "personas
 * utilisateur multiples" des apps façon RosyTalk.
 */
class PersonaManagerViewModel(private val repository: CharacterRepository) : ViewModel() {

    val personas: StateFlow<List<UserPersonaEntity>> = repository.observePersonas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editingPersona = MutableStateFlow<UserPersonaEntity?>(null)
    val editingPersona: StateFlow<UserPersonaEntity?> = _editingPersona.asStateFlow()

    /** Ouvre l'éditeur pour un nouveau persona vierge, ou pour [existing] si fourni. */
    fun startEditing(existing: UserPersonaEntity? = null) {
        _editingPersona.value = existing ?: UserPersonaEntity(name = "")
    }

    fun stopEditing() {
        _editingPersona.value = null
    }

    fun updateDraft(transform: (UserPersonaEntity) -> UserPersonaEntity) {
        _editingPersona.value = _editingPersona.value?.let(transform)
    }

    fun saveDraft() {
        val draft = _editingPersona.value ?: return
        if (draft.name.isBlank()) return
        viewModelScope.launch {
            repository.savePersona(draft)
            _editingPersona.value = null
        }
    }

    fun deletePersona(persona: UserPersonaEntity) {
        viewModelScope.launch { repository.deletePersona(persona) }
    }

    fun setDefault(persona: UserPersonaEntity) {
        viewModelScope.launch { repository.setDefaultPersona(persona.id) }
    }
}

fun userGenderLabelFor(gender: UserGender): String = when (gender) {
    UserGender.NON_PRECISE -> "Non précisé"
    UserGender.FEMME -> "Femme"
    UserGender.HOMME -> "Homme"
    UserGender.AUTRE -> "Autre"
}
