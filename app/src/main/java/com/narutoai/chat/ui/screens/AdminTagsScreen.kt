package com.narutoai.chat.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.narutoai.chat.data.Characters
import com.narutoai.chat.models.Character
import com.narutoai.chat.viewmodel.AdminTagsViewModel

/**
 * Écran d'administration des tags (protégé par mot de passe)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminTagsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdminTagsViewModel = viewModel()
) {
    val isAuthenticated by viewModel.isAuthenticated.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    if (!isAuthenticated) {
        // Écran de connexion
        AuthenticationScreen(
            onAuthenticate = { password ->
                viewModel.authenticate(password)
            },
            errorMessage = errorMessage,
            onCancel = onNavigateBack
        )
    } else {
        // Écran d'administration
        TagsManagementScreen(
            viewModel = viewModel,
            onNavigateBack = onNavigateBack
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticationScreen(
    onAuthenticate: (String) -> Unit,
    errorMessage: String?,
    onCancel: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔒 Administration") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = "Verrouillé",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(Modifier.height(24.dp))
            
            Text(
                text = "Zone d'administration",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                text = "Entrez le mot de passe administrateur",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(32.dp))
            
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Mot de passe") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Masquer" else "Afficher"
                        )
                    }
                },
                isError = errorMessage != null
            )
            
            if (errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(Modifier.height(24.dp))
            
            Button(
                onClick = { onAuthenticate(password) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = password.isNotEmpty()
            ) {
                Icon(Icons.Default.LockOpen, "Déverrouiller")
                Spacer(Modifier.width(8.dp))
                Text("Se connecter")
            }
            
            Spacer(Modifier.height(16.dp))
            
            TextButton(onClick = onCancel) {
                Text("Annuler")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsManagementScreen(
    viewModel: AdminTagsViewModel,
    onNavigateBack: () -> Unit
) {
    val characters = remember { Characters.allCharacters }
    var selectedCharacter by remember { mutableStateOf<Character?>(null) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🏷️ Gestion des tags") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(Icons.Default.Logout, "Déconnexion")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Info
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, "Info", tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Mode Administration",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Vous pouvez ajouter/modifier les tags des personnages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Liste des personnages
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(characters) { character ->
                    CharacterTagCard(
                        character = character,
                        onAddTag = {
                            selectedCharacter = character
                            showAddTagDialog = true
                        },
                        onRemoveTag = { tag ->
                            // TODO: Implémenter suppression tag
                        }
                    )
                }
            }
        }
    }
    
    // Dialog ajout tag
    if (showAddTagDialog && selectedCharacter != null) {
        AddTagDialog(
            characterName = selectedCharacter!!.name,
            onDismiss = { showAddTagDialog = false },
            onConfirm = { tagKey, tagValue ->
                // TODO: Sauvegarder le tag
                showAddTagDialog = false
            }
        )
    }
}

@Composable
fun CharacterTagCard(
    character: Character,
    onAddTag: () -> Unit,
    onRemoveTag: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = onAddTag) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Ajouter tag",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Tags existants (pour l'instant on affiche des exemples)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Exemples de tags
                if (character.age.isNotEmpty()) {
                    TagChip(
                        text = "Âge: ${character.age}",
                        onRemove = { onRemoveTag("age") }
                    )
                }
                if (character.hairColor.isNotEmpty()) {
                    TagChip(
                        text = "Cheveux: ${character.hairColor}",
                        onRemove = { onRemoveTag("hairColor") }
                    )
                }
                
                // Message si aucun tag
                if (character.age.isEmpty() && character.hairColor.isEmpty()) {
                    Text(
                        text = "Aucun tag",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun TagChip(
    text: String,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Supprimer",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun AddTagDialog(
    characterName: String,
    onDismiss: () -> Unit,
    onConfirm: (key: String, value: String) -> Unit
) {
    var tagKey by remember { mutableStateOf("") }
    var tagValue by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un tag pour $characterName") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = tagKey,
                    onValueChange = { tagKey = it },
                    label = { Text("Clé (ex: age, hairColor)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = tagValue,
                    onValueChange = { tagValue = it },
                    label = { Text("Valeur (ex: 25 ans, blond)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(tagKey, tagValue) },
                enabled = tagKey.isNotEmpty() && tagValue.isNotEmpty()
            ) {
                Text("Ajouter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
