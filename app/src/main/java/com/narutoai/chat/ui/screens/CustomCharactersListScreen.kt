package com.narutoai.chat.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.narutoai.chat.data.CustomCharacterEntity
import com.narutoai.chat.utils.AutoTagger
import com.narutoai.chat.viewmodel.CustomCharactersViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomCharactersListScreen(
    onNavigateBack: () -> Unit,
    onCreateNew: () -> Unit,
    onEditCharacter: (CustomCharacterEntity) -> Unit,
    onSelectCharacter: (CustomCharacterEntity) -> Unit,
    viewModel: CustomCharactersViewModel = viewModel()
) {
    val characters by viewModel.characters.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    var characterToDelete by remember { mutableStateOf<CustomCharacterEntity?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Debug: log au chargement et changement
    LaunchedEffect(Unit) {
        android.util.Log.d("CustomCharactersList", "Screen mounted")
    }
    
    LaunchedEffect(characters.size) {
        android.util.Log.d("CustomCharactersList", "Characters: ${characters.size} items")
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mes personnages (${characters.size})") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Force refresh en naviguant
                        android.util.Log.d("CustomCharactersList", "Manual refresh requested")
                    }) {
                        Icon(Icons.Default.Refresh, "Actualiser")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateNew,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "Créer nouveau")
            }
        }
    ) { padding ->
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Erreur",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = errorMessage ?: "Erreur inconnue",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                characters.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = "Aucun personnage",
                            modifier = Modifier.size(96.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Aucun personnage créé",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Appuyez sur + pour créer votre premier personnage",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = "${characters.size} personnage${if (characters.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        
                        items(characters, key = { it.id }) { character ->
                            CustomCharacterCard(
                                character = character,
                                onSelect = { onSelectCharacter(character) },
                                onEdit = { onEditCharacter(character) },
                                onDelete = {
                                    characterToDelete = character
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Dialogue de confirmation suppression
    if (showDeleteDialog && characterToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, "Supprimer") },
            title = { Text("Supprimer le personnage ?") },
            text = {
                Text("Voulez-vous vraiment supprimer \"${characterToDelete?.name}\" ? Cette action est irréversible.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        characterToDelete?.let { viewModel.deleteCharacter(it) }
                        showDeleteDialog = false
                        characterToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomCharacterCard(
    character: CustomCharacterEntity,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (character.avatarImagePath.isNotEmpty() && File(character.avatarImagePath).exists()) {
                    AsyncImage(
                        model = File(character.avatarImagePath),
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = character.name.take(2).uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            // Infos
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = character.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (character.isAutoGenerated) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "Auto-généré",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                
                Spacer(Modifier.height(4.dp))
                
                Text(
                    text = character.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(Modifier.height(8.dp))
                
                // Tags
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (character.age.isNotEmpty()) {
                        Chip(text = "⌛ ${character.age}")
                    }
                    if (character.gender.isNotEmpty()) {
                        Chip(text = "⚧ ${character.gender}")
                    }
                    if (character.hairColor.isNotEmpty()) {
                        Chip(text = "👱 ${character.hairColor}")
                    }
                    // Tags auto (JSON) + fallback si ancien personnage (tags vides)
                    val autoTags = remember(character.tags, character.gender, character.hairColor, character.eyeColor, character.skinTone, character.bodyType, character.age, character.height) {
                        val fromDb = try {
                            val json = org.json.JSONArray(character.tags)
                            buildList {
                                for (i in 0 until json.length()) add(json.optString(i))
                            }.filter { it.isNotBlank() }
                        } catch (_: Exception) {
                            emptyList()
                        }

                        if (fromDb.isNotEmpty()) {
                            fromDb
                        } else {
                            AutoTagger.generateTags(
                                gender = character.gender,
                                hairColor = character.hairColor,
                                eyeColor = character.eyeColor,
                                skinTone = character.skinTone,
                                bodyType = character.bodyType,
                                age = character.age,
                                height = character.height
                            )
                        }
                    }

                    autoTags.take(3).forEach { tag ->
                        if (tag.isNotBlank()) Chip(text = "#$tag")
                    }
                }
            }
            
            // Actions
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Éditer",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Supprimer",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun Chip(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
