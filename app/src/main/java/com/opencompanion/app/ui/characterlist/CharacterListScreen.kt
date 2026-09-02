package com.opencompanion.app.ui.characterlist

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opencompanion.app.data.CharacterEntity
import com.opencompanion.app.data.resolveCharacterPlaceholders
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterListScreen(
    viewModel: CharacterListViewModel,
    onOpenChat: (Long) -> Unit,
    onCreateCharacter: () -> Unit,
    onEditCharacter: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onBrowseImport: () -> Unit,
) {
    val characters by viewModel.characters.collectAsState()
    val importMessage by viewModel.importMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importFromUri(it) }
    }

    LaunchedEffect(importMessage) {
        importMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeImportMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Personnages") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Réglages")
                    }
                },
            )
        },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    text = { Text("Nouveau") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { menuExpanded = true },
                )
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Créer un personnage") },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        onClick = { menuExpanded = false; onCreateCharacter() },
                    )
                    DropdownMenuItem(
                        text = { Text("Importer un fichier (.png / .json)") },
                        leadingIcon = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            // "application/octet-stream" en plus des types attendus : de nombreux
                            // gestionnaires de fichiers / fournisseurs de documents annoncent ce
                            // type générique pour un .png ou .json dont l'origine ne renseigne pas
                            // le vrai type MIME (fichier extrait d'une archive, sans extension,
                            // etc.) — sans lui, ces fichiers pourtant valides étaient invisibles
                            // dans le sélecteur. Le contenu réel est de toute façon revérifié dans
                            // CharacterImportManager (signature PNG / premier caractère '{').
                            filePicker.launch(
                                arrayOf(
                                    "image/png",
                                    "application/json",
                                    "text/plain",
                                    "application/octet-stream",
                                ),
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Importer depuis une URL") },
                        leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                        onClick = { menuExpanded = false; showUrlDialog = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Parcourir un site pour importer") },
                        leadingIcon = { Icon(Icons.Filled.Language, contentDescription = null) },
                        onClick = { menuExpanded = false; onBrowseImport() },
                    )
                }
            }
        },
    ) { padding ->
        if (characters.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Aucun personnage pour l'instant.\nCrée-en un ou importe une fiche.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxWidth()) {
                items(characters, key = { it.id }) { character ->
                    CharacterRow(
                        character = character,
                        onClick = { onOpenChat(character.id) },
                        onEdit = { onEditCharacter(character.id) },
                        onDelete = { scope.launch { viewModel.deleteCharacter(character) } },
                    )
                }
            }
        }
    }

    if (showUrlDialog) {
        UrlImportDialog(
            onDismiss = { showUrlDialog = false },
            onConfirm = { url -> showUrlDialog = false; viewModel.importFromUrl(url) },
        )
    }
}

@Composable
private fun CharacterRow(
    character: CharacterEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, modifier = Modifier.size(48.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Text(character.name.take(1).uppercase())
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(character.name, style = MaterialTheme.typography.titleMedium)
                if (character.description.isNotBlank()) {
                    Text(
                        resolveCharacterPlaceholders(character.description, character),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Options")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Modifier") }, onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(text = { Text("Supprimer") }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun UrlImportDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importer depuis une URL") },
        text = {
            Column {
                Text(
                    "Colle le lien direct vers une image PNG (fiche embarquée) ou un fichier JSON " +
                        "de personnage, depuis n'importe quel site.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (url.isNotBlank()) onConfirm(url.trim()) }, enabled = url.isNotBlank()) {
                Text("Importer")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
