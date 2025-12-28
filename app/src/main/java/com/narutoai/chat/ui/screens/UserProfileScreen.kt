package com.narutoai.chat.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.narutoai.chat.models.Gender
import com.narutoai.chat.models.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    currentProfile: UserProfile,
    onBackClick: () -> Unit,
    onSaveProfile: (UserProfile) -> Unit
) {
    var pseudo by remember { mutableStateOf(currentProfile.pseudo) }
    var age by remember { mutableStateOf(currentProfile.age?.toString() ?: "") }
    var selectedGender by remember { mutableStateOf(currentProfile.gender) }
    var bio by remember { mutableStateOf(currentProfile.bio) }
    var expandedGender by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("👤 Mon Profil") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // En-tête
            Text(
                text = "Personnalise ton expérience",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Tes informations seront utilisées pour adapter les conversations avec les personnages.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Pseudo
            OutlinedTextField(
                value = pseudo,
                onValueChange = { pseudo = it },
                label = { Text("Pseudo") },
                placeholder = { Text("Comment veux-tu être appelé(e) ?") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Text("👤") }
            )
            
            // Âge
            OutlinedTextField(
                value = age,
                onValueChange = { newAge ->
                    if (newAge.isEmpty() || newAge.all { it.isDigit() }) {
                        age = newAge
                    }
                },
                label = { Text("Âge (optionnel)") },
                placeholder = { Text("18") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Text("🎂") }
            )
            
            // Genre
            ExposedDropdownMenuBox(
                expanded = expandedGender,
                onExpandedChange = { expandedGender = !expandedGender }
            ) {
                OutlinedTextField(
                    value = selectedGender.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Genre") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedGender) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                
                ExposedDropdownMenu(
                    expanded = expandedGender,
                    onDismissRequest = { expandedGender = false }
                ) {
                    Gender.entries.forEach { gender ->
                        DropdownMenuItem(
                            text = { Text(gender.displayName) },
                            onClick = {
                                selectedGender = gender
                                expandedGender = false
                            },
                            leadingIcon = {
                                Text(
                                    when (gender) {
                                        Gender.MALE -> "♂️"
                                        Gender.FEMALE -> "♀️"
                                        Gender.NON_BINARY -> "⚧️"
                                        Gender.NOT_SPECIFIED -> "❓"
                                    }
                                )
                            }
                        )
                    }
                }
            }
            
            // Bio
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Bio (optionnel)") },
                placeholder = { Text("Quelques mots sur toi...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 5,
                leadingIcon = { Text("📝") }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Exemple d'utilisation
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "💡 Comment c'est utilisé ?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "• Pseudo: Les personnages t'appelleront par ce nom",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Text(
                        text = "• Genre: Adapte les pronoms et l'approche des personnages",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Text(
                        text = "• Âge: Influence le style des conversations",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Text(
                        text = "• Bio: Permet aux personnages de mieux te connaître",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Bouton Sauvegarder
            Button(
                onClick = {
                    val newProfile = UserProfile(
                        pseudo = pseudo.trim(),
                        age = age.toIntOrNull(),
                        gender = selectedGender,
                        bio = bio.trim()
                    )
                    onSaveProfile(newProfile)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = pseudo.isNotBlank()
            ) {
                Text(
                    text = "💾 Sauvegarder mon profil",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            // Note de confidentialité
            Text(
                text = "🔒 Tes informations restent sur ton appareil et ne sont jamais partagées.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
