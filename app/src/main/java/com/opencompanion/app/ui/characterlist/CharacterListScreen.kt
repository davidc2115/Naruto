package com.opencompanion.app.ui.characterlist

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opencompanion.app.data.CharacterEntity
import com.opencompanion.app.data.resolveCharacterPlaceholders
import com.opencompanion.app.ui.components.CharacterAvatar
import com.opencompanion.app.ui.theme.BrandGradient
import com.opencompanion.app.ui.theme.ScrimGradient
import kotlinx.coroutines.launch

/**
 * Écran d'accueil « Découverte » façon SpicyChat/RosyTalk : grille de cartes personnage (avatar
 * en fond, nom + tags en surimpression) plutôt qu'une simple liste — c'est la mise en page que
 * ces apps utilisent quasi universellement pour leur catalogue de personnages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterListScreen(
    viewModel: CharacterListViewModel,
    onOpenChat: (Long) -> Unit,
    onCreateCharacter: () -> Unit,
    onEditCharacter: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onBrowseImport: () -> Unit,
    onOpenPersonas: () -> Unit,
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
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Mes personnages",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenPersonas) {
                        Icon(Icons.Filled.Person, contentDescription = "Mes personas")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Réglages")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { menuExpanded = true },
                    containerColor = Color.Transparent,
                    elevation = FloatingActionButtonDefaultsElevation(),
                    modifier = Modifier.background(BrandGradient, shape = RoundedCornerShape(16.dp)),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Nouveau", tint = Color.White)
                }
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
            EmptyState(Modifier.padding(padding).fillMaxSize(), onCreateCharacter, onBrowseImport)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(characters, key = { it.id }) { character ->
                    CharacterCard(
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

/** [FloatingActionButton] applique sa propre teinte de conteneur par-dessus le fond qu'on lui
 *  donne : une élévation à 0 partout évite qu'une ombre Material standard ne vienne casser le
 *  dégradé de marque posé en arrière-plan du bouton. */
@Composable
private fun FloatingActionButtonDefaultsElevation() =
    androidx.compose.material3.FloatingActionButtonDefaults.elevation(
        defaultElevation = 0.dp,
        pressedElevation = 0.dp,
        hoveredElevation = 0.dp,
        focusedElevation = 0.dp,
    )

@Composable
private fun EmptyState(modifier: Modifier, onCreateCharacter: () -> Unit, onBrowseImport: () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.height(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Aucun personnage pour l'instant",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Crée ton premier personnage ou importe une fiche existante.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCreateCharacter) { Text("Créer") }
                TextButton(onClick = onBrowseImport) { Text("Parcourir") }
            }
        }
    }
}

@Composable
private fun CharacterCard(
    character: CharacterEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(Modifier.fillMaxSize()) {
            CharacterAvatar(
                avatarPath = character.avatarPath,
                name = character.name,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxSize(),
            )

            // Voile dégradé en bas de carte pour garder le nom/tags lisibles quel que soit le
            // contenu de l'avatar, sans avoir à en connaître la couleur dominante.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .align(Alignment.BottomCenter)
                    .background(ScrimGradient),
            )

            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
            ) {
                Text(
                    character.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = character.description.ifBlank { character.tags.firstOrNull().orEmpty() }
                if (subtitle.isNotBlank()) {
                    Text(
                        resolveCharacterPlaceholders(subtitle, character),
                        color = Color(0xFFE6DEEF),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (character.tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        character.tags.take(2).forEach { tag ->
                            TagChip(tag)
                        }
                    }
                }
            }

            Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                IconButton(onClick = { menuExpanded = true }) {
                    Surface(shape = androidx.compose.foundation.shape.CircleShape, color = Color(0x66000000)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Options",
                            tint = Color.White,
                            modifier = Modifier.padding(6.dp),
                        )
                    }
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Modifier") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menuExpanded = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Supprimer") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun TagChip(text: String) {
    Surface(
        color = Color(0x33FFFFFF),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
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
