package com.narutoai.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.narutoai.chat.models.Character

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterProfileScreen(
    character: Character,
    onBackClick: () -> Unit,
    onStartChat: () -> Unit
) {
    var selectedImageIndex by remember { mutableStateOf<Int?>(null) }
    var showNSFW by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = character.avatarEmoji,
                            fontSize = 24.sp
                        )
                        Text(
                            text = "Profil de ${character.name}",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Photo principale
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                val mainImageResId = if (character.imageResId != 0) {
                    character.imageResId
                } else {
                    // Fallback: première image de la galerie
                    if (character.gallery.isNotEmpty()) {
                        val firstImage = character.gallery[0]
                        if (firstImage.startsWith("drawable://")) {
                            val fileName = firstImage.removePrefix("drawable://").removeSuffix(".jpg")
                            context.resources.getIdentifier(fileName, "drawable", context.packageName)
                        } else 0
                    } else 0
                }
                
                AsyncImage(
                    model = mainImageResId,
                    contentDescription = character.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp, max = 400.dp),
                    contentScale = ContentScale.Crop
                )
            }
            
            // Informations
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
                        text = character.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = character.description,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    if (character.age.isNotEmpty()) {
                        CharacterInfoRow(label = "Âge", value = character.age)
                    }
                    if (character.height.isNotEmpty()) {
                        CharacterInfoRow(label = "Taille", value = character.height)
                    }
                    if (character.hairColor.isNotEmpty()) {
                        CharacterInfoRow(label = "Cheveux", value = character.hairColor)
                    }
                    if (character.eyeColor.isNotEmpty()) {
                        CharacterInfoRow(label = "Yeux", value = character.eyeColor)
                    }
                    
                    // Traits de personnalité
                    if (character.personality.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Personnalité:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            character.personality.forEach { trait ->
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        text = trait,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Galerie d'images avec toggle SFW/NSFW
            val hasGallery = character.gallery.isNotEmpty()
            val hasNSFWGallery = character.galleryNSFW.isNotEmpty()
            
            if (hasGallery || hasNSFWGallery) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val currentGallery = if (showNSFW && hasNSFWGallery) character.galleryNSFW else character.gallery
                    val galleryLabel = if (showNSFW) "🔞 NSFW" else "📸 SFW"
                    
                    Text(
                        text = "$galleryLabel Galerie (${currentGallery.size} images)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (hasGallery && hasNSFWGallery) {
                        TextButton(
                            onClick = { showNSFW = !showNSFW }
                        ) {
                            Text(if (showNSFW) "Voir SFW" else "Voir NSFW 🔞")
                        }
                    }
                }
                
                val currentGallery = if (showNSFW && hasNSFWGallery) character.galleryNSFW else character.gallery
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.height(600.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(currentGallery.size) { index ->
                        val imageUri = currentGallery[index]
                        val imageModel = if (imageUri.startsWith("drawable://")) {
                            val fileName = imageUri.removePrefix("drawable://").removeSuffix(".jpg")
                            val resId = context.resources.getIdentifier(fileName, "drawable", context.packageName)
                            if (resId != 0) resId else imageUri
                        } else {
                            imageUri
                        }
                        
                        AsyncImage(
                            model = imageModel,
                            contentDescription = "Image ${index + 1}",
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedImageIndex = index },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            
            // Bouton commencer conversation
            Button(
                onClick = onStartChat,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Commencer la conversation", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    
    // Dialog plein écran pour image agrandie
    selectedImageIndex?.let { index ->
        val currentGallery = if (showNSFW && character.galleryNSFW.isNotEmpty()) character.galleryNSFW else character.gallery
        
        Dialog(
            onDismissRequest = { selectedImageIndex = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { selectedImageIndex = null },
                contentAlignment = Alignment.Center
            ) {
                val imageUri = currentGallery[index]
                val imageModel = if (imageUri.startsWith("drawable://")) {
                    val fileName = imageUri.removePrefix("drawable://").removeSuffix(".jpg")
                    val resId = context.resources.getIdentifier(fileName, "drawable", context.packageName)
                    if (resId != 0) resId else imageUri
                } else {
                    imageUri
                }
                
                AsyncImage(
                    model = imageModel,
                    contentDescription = "Image ${index + 1} agrandie",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                
                // Bouton fermer
                IconButton(
                    onClick = { selectedImageIndex = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Fermer",
                        tint = Color.White
                    )
                }
                
                // Indicateur position
                Text(
                    text = "${index + 1} / ${currentGallery.size}",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun CharacterInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
