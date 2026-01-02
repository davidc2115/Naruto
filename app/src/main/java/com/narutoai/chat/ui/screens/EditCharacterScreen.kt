package com.narutoai.chat.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.narutoai.chat.viewmodel.EditCharacterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCharacterScreen(
    characterId: String?,
    onNavigateBack: () -> Unit,
    onCharacterSaved: () -> Unit,
    viewModel: EditCharacterViewModel = viewModel()
) {
    // Charger le personnage si un ID est fourni
    LaunchedEffect(characterId) {
        characterId?.let {
            viewModel.loadCharacter(it)
        }
    }
    
    val name by viewModel.name.collectAsState()
    val description by viewModel.description.collectAsState()
    val physicalDescription by viewModel.physicalDescription.collectAsState()
    val age by viewModel.age.collectAsState()
    val gender by viewModel.gender.collectAsState()
    val height by viewModel.height.collectAsState()
    val hairColor by viewModel.hairColor.collectAsState()
    val eyeColor by viewModel.eyeColor.collectAsState()
    val bodyType by viewModel.bodyType.collectAsState()
    val bustSize by viewModel.bustSize.collectAsState()
    val penisSize by viewModel.penisSize.collectAsState()
    val temperament by viewModel.temperament.collectAsState()
    val scenario by viewModel.scenario.collectAsState()
    val greetingMessage by viewModel.greetingMessage.collectAsState()
    val avatarImageUri by viewModel.avatarImageUri.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.updateAvatarImage(uri)
    }
    
    // Gestion du succès
    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            kotlinx.coroutines.delay(500)
            viewModel.resetSaveSuccess()
            onCharacterSaved()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("✏️ Modifier le personnage") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // Section Photo
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "📷 Photo du personnage",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { imagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarImageUri != null) {
                            AsyncImage(
                                model = avatarImageUri,
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.AddAPhoto,
                                contentDescription = "Ajouter photo",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, "Galerie", modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Changer la photo")
                    }
                    
                    // Analyse IA automatique
                    if (avatarImageUri != null) {
                        Button(
                            onClick = { viewModel.analyzePhoto() },
                            enabled = !isAnalyzing,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Analyse en cours...")
                            } else {
                                Icon(Icons.Default.AutoAwesome, "Analyse IA", modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("🤖 Analyser avec IA (Groq)")
                            }
                        }
                        
                        analysisResult?.let {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        it.contains("✅") -> MaterialTheme.colorScheme.primaryContainer
                                        it.contains("❌") -> MaterialTheme.colorScheme.errorContainer
                                        else -> MaterialTheme.colorScheme.secondaryContainer
                                    }
                                )
                            ) {
                                Text(
                                    text = it,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
            
            // Section Informations de base
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "📝 Informations de base",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    OutlinedTextField(
                        value = name,
                        onValueChange = { viewModel.updateName(it) },
                        label = { Text("Nom *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = description,
                        onValueChange = { viewModel.updateDescription(it) },
                        label = { Text("Description courte *") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3,
                        placeholder = { Text("Ex: Une guerrière ninja courageuse et déterminée") }
                    )
                }
            }
            
            // Section Apparence physique
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "👤 Apparence physique",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    OutlinedTextField(
                        value = physicalDescription,
                        onValueChange = { viewModel.updatePhysicalDescription(it) },
                        label = { Text("Description physique détaillée") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 6,
                        placeholder = { Text("Description complète de l'apparence...") }
                    )
                    
                    // Genre
                    OutlinedTextField(
                        value = gender,
                        onValueChange = { viewModel.updateGender(it) },
                        label = { Text("Genre") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Homme, Femme, Non-binaire...") }
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = age,
                            onValueChange = { viewModel.updateAge(it) },
                            label = { Text("Âge") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("20-35, ado...") }
                        )
                        
                        OutlinedTextField(
                            value = height,
                            onValueChange = { viewModel.updateHeight(it) },
                            label = { Text("Taille") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("165cm, grande...") }
                        )
                    }
                    
                    OutlinedTextField(
                        value = hairColor,
                        onValueChange = { viewModel.updateHairColor(it) },
                        label = { Text("Couleur cheveux") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Châtain court, blond long...") }
                    )
                    
                    OutlinedTextField(
                        value = eyeColor,
                        onValueChange = { viewModel.updateEyeColor(it) },
                        label = { Text("Couleur yeux") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Marron, bleu, vert...") }
                    )
                    
                    OutlinedTextField(
                        value = bodyType,
                        onValueChange = { viewModel.updateBodyType(it) },
                        label = { Text("Type de corps") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Mince, athlétique...") }
                    )
                    
                    // Taille de poitrine (si féminin)
                    OutlinedTextField(
                        value = bustSize,
                        onValueChange = { viewModel.updateBustSize(it) },
                        label = { Text("Taille de poitrine (si féminin)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Bonnet A, B, C, D, E...") }
                    )
                    
                    // Taille du sexe (si masculin)
                    OutlinedTextField(
                        value = penisSize,
                        onValueChange = { viewModel.updatePenisSize(it) },
                        label = { Text("Taille du sexe (si masculin)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("16cm, 18cm, 20cm...") }
                    )
                }
            }
            
            // Section Personnalité
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "💭 Personnalité & Background",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    OutlinedTextField(
                        value = temperament,
                        onValueChange = { viewModel.updateTemperament(it) },
                        label = { Text("Tempérament") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        placeholder = { Text("Ex: Joyeux, déterminé, protecteur...") }
                    )
                    
                    OutlinedTextField(
                        value = scenario,
                        onValueChange = { viewModel.updateScenario(it) },
                        label = { Text("Scénario / Contexte") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        placeholder = { Text("Ex: Ninja du village caché, en mission...") }
                    )
                    
                    OutlinedTextField(
                        value = greetingMessage,
                        onValueChange = { viewModel.updateGreetingMessage(it) },
                        label = { Text("Message d'accueil") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        placeholder = { Text("Ex: Salut ! Je suis [Nom], ravi de te rencontrer !") }
                    )
                }
            }
            
            // Message d'erreur
            errorMessage?.let {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Erreur",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            
            // Bouton de sauvegarde
            Button(
                onClick = { viewModel.saveCharacter() },
                enabled = !isSaving && name.isNotBlank() && description.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Sauvegarde...")
                } else {
                    Icon(Icons.Default.Save, "Sauvegarder", modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Sauvegarder les modifications", style = MaterialTheme.typography.titleMedium)
                }
            }
            
            // Espacement final
            Spacer(Modifier.height(16.dp))
        }
    }
}
