package com.opencompanion.app.ui.persona

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.opencompanion.app.data.UserGender
import com.opencompanion.app.data.UserPersonaEntity
import com.opencompanion.app.ui.components.CharacterAvatar
import com.opencompanion.app.ui.theme.BrandGradient

/**
 * Gestion des personas utilisateur : liste, création, édition, suppression, choix du persona
 * par défaut — voir [PersonaManagerViewModel]. Le choix du persona actif PAR PERSONNAGE se fait
 * lui depuis l'écran de chat (voir ChatScreen), pas ici.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaManagerScreen(viewModel: PersonaManagerViewModel, onBack: () -> Unit) {
    val personas by viewModel.personas.collectAsState()
    val editing by viewModel.editingPersona.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Mes personas", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Retour") }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.startEditing() },
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                    focusedElevation = 0.dp,
                ),
                modifier = Modifier.background(BrandGradient, shape = MaterialTheme.shapes.large),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Nouveau persona", tint = androidx.compose.ui.graphics.Color.White)
            }
        },
    ) { padding ->
        if (personas.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Aucun persona pour l'instant",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Crée une identité que tu pourras endosser dans tes conversations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                items(personas, key = { it.id }) { persona ->
                    PersonaRow(
                        persona = persona,
                        onEdit = { viewModel.startEditing(persona) },
                        onDelete = { viewModel.deletePersona(persona) },
                        onSetDefault = { viewModel.setDefault(persona) },
                    )
                }
            }
        }
    }

    editing?.let { draft ->
        PersonaEditorDialog(
            draft = draft,
            onChange = { transform -> viewModel.updateDraft(transform) },
            onDismiss = viewModel::stopEditing,
            onSave = viewModel::saveDraft,
        )
    }
}

@Composable
private fun PersonaRow(
    persona: UserPersonaEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CharacterAvatar(avatarPath = persona.avatarPath, name = persona.name, modifier = Modifier.size(48.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(persona.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val details = buildList {
                    persona.age?.let { add("$it ans") }
                    if (persona.gender != UserGender.NON_PRECISE.name) {
                        add(userGenderLabelFor(runCatching { UserGender.valueOf(persona.gender) }.getOrDefault(UserGender.NON_PRECISE)))
                    }
                }.joinToString(" · ")
                if (details.isNotBlank()) {
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onSetDefault) {
                Icon(
                    if (persona.isDefault) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Persona par défaut",
                    tint = if (persona.isDefault) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Modifier") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Supprimer") }
        }
    }
}

@Composable
private fun PersonaEditorDialog(
    draft: UserPersonaEntity,
    onChange: ((UserPersonaEntity) -> UserPersonaEntity) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.id == 0L) "Nouveau persona" else "Modifier le persona") },
        text = {
            Column {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { name -> onChange { it.copy(name = name) } },
                    label = { Text("Nom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft.age?.toString() ?: "",
                    onValueChange = { text -> onChange { it.copy(age = text.filter(Char::isDigit).take(3).toIntOrNull()) } },
                    label = { Text("Âge (facultatif)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("Genre", style = MaterialTheme.typography.bodyMedium)
                UserGender.entries.forEach { gender ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = draft.gender == gender.name,
                            onClick = { onChange { it.copy(gender = gender.name) } },
                        )
                        Text(userGenderLabelFor(gender))
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = draft.description,
                    onValueChange = { desc -> onChange { it.copy(description = desc) } },
                    label = { Text("Description / bio (facultatif)") },
                    placeholder = { Text("ex. écrivain timide qui n'ose jamais dire ce qu'il pense") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = draft.name.isNotBlank()) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
