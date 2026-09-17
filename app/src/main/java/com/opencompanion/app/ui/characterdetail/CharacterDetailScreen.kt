package com.opencompanion.app.ui.characterdetail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opencompanion.app.data.resolveCharacterPlaceholders
import com.opencompanion.app.ui.components.CharacterAvatar
import com.opencompanion.app.ui.components.MediaLightboxDialog
import com.opencompanion.app.ui.components.MediaThumbnailItem
import com.opencompanion.app.ui.theme.AccentPink
import com.opencompanion.app.ui.theme.BrandGradient

/**
 * Écran de présentation immersive du personnage.
 * Permet de découvrir la fiche complète et offre deux options claires :
 * 1. "Reprendre la conversation" (ou démarrer si 0 message)
 * 2. "Nouvelle conversation" (effacer l'historique et recommencer à zéro)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CharacterDetailScreen(
    viewModel: CharacterDetailViewModel,
    onBack: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onEditCharacter: (Long) -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showLightbox by remember { mutableStateOf(false) }
    var lightboxIndex by remember { mutableIntStateOf(0) }
    var showAddMediaDialog by remember { mutableStateOf(false) }
    var mediaUrlInput by remember { mutableStateOf("") }
    var showGeneratePhotoDialog by remember { mutableStateOf(false) }
    var selectedPhotoStyle by remember { mutableStateOf(CharacterPhotoStyle.PORTRAIT) }
    var customPromptInput by remember { mutableStateOf("") }
    var setAsAvatarOption by remember { mutableStateOf(true) }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let { viewModel.addGalleryMediaFromUri(it, context) }
    }

    val character = state.character
    val allMedia = remember(character?.avatarPath, character?.galleryMedia, character?.name) {
        val list = mutableListOf<String>()
        character?.avatarPath?.takeIf { it.isNotBlank() }?.let { list.add(it) }
        character?.galleryMedia?.let { list.addAll(it) }

        // Découverte automatique des photos du pack embarqué si absentes de galleryMedia
        val safeName = character?.name
            ?.replace(Regex("""\s+\d+$"""), "")
            ?.replace(Regex("""\s*\(.*?\)$"""), "")
            ?.trim()
            ?.let { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFD) }
            ?.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            ?.lowercase()
            ?.replace(".", "")
            ?.replace(Regex("[^a-z0-9_]+"), "_")
            ?.trim('_')

        if (!safeName.isNullOrBlank()) {
            listOf(
                "asset:///avatars/${safeName}.jpg",
                "asset:///avatars/${safeName}_photo.jpg",
                "asset:///avatars/${safeName}_sexy.jpg",
                "asset:///avatars/${safeName}_intime.jpg",
                "asset:///avatars/${safeName}_cuisine.jpg",
                "asset:///avatars/${safeName}_voiture.jpg",
                "asset:///avatars/${safeName}_hotel.jpg",
            ).forEach { candidate ->
                val assetPath = candidate.removePrefix("asset:///")
                val exists = runCatching {
                    context.assets.open(assetPath).use { true }
                }.getOrDefault(false)
                if (exists && !list.contains(candidate)) {
                    list.add(candidate)
                }
            }
        }

        list.distinct()
    }
    if (character == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Personnage") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text("Personnage introuvable.")
                }
            }
        }
        return
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            icon = { Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Recommencer la conversation ?") },
            text = {
                Text(
                    "Tous les messages échangés avec ${character.name} seront supprimés. " +
                        "La discussion repartira du message d'introduction initial."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        viewModel.startNewConversation { onOpenChat(character.id) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Oui, recommencer à zéro")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Annuler")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        character.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { onEditCharacter(character.id) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Modifier la fiche")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Bouton 1 : Reprendre la conversation en cours
                    Button(
                        onClick = { onOpenChat(character.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(BrandGradient, shape = RoundedCornerShape(16.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(
                            if (state.hasConversation) Icons.AutoMirrored.Filled.Message else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (state.hasConversation) "Reprendre la conversation" else "Démarrer la discussion",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }

                    // Bouton 2 : Redémarrer une nouvelle conversation
                    if (state.hasConversation) {
                        OutlinedButton(
                            onClick = { showResetConfirmDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Recommencer une nouvelle conversation")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Hero Header avec Portrait Avatar
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable {
                                if (allMedia.isNotEmpty()) {
                                    lightboxIndex = 0
                                    showLightbox = true
                                }
                            },
                    ) {
                        CharacterAvatar(
                            avatarPath = character.avatarPath,
                            name = character.name,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(24.dp),
                        )
                        Surface(
                            color = Color(0x66000000),
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                        ) {
                            Icon(
                                Icons.Filled.ZoomIn,
                                contentDescription = "Agrandir",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        character.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    if (character.creator.isNotBlank()) {
                        Text(
                            "Créé par ${character.creator}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Bouton / État de régénération d'avatar IA
                    if (state.isGeneratingImage) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Génération d'avatar IA en cours...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        state.generationStatus ?: "Horde Diffusion génère votre portrait fidèle...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                selectedPhotoStyle = CharacterPhotoStyle.PORTRAIT
                                setAsAvatarOption = true
                                customPromptInput = ""
                                showGeneratePhotoDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "✨ Régénérer l'avatar avec l'IA (Gratuit)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }

                    if (character.tags.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            character.tags.forEach { tag ->
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        "#$tag",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Galerie Photos, GIFs & Vidéos
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Collections,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Galerie Médias",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            if (allMedia.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape,
                                ) {
                                    Text(
                                        "${allMedia.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                onClick = {
                                    selectedPhotoStyle = CharacterPhotoStyle.PORTRAIT
                                    setAsAvatarOption = false
                                    customPromptInput = ""
                                    showGeneratePhotoDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                enabled = !state.isGeneratingImage,
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Générer IA", style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.width(6.dp))
                            TextButton(
                                onClick = { showAddMediaDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Ajouter", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    if (allMedia.isEmpty()) {
                        Text(
                            "Aucun média dans la galerie. Touchez « Ajouter » pour importer des photos, GIFs ou vidéos !",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            itemsIndexed(allMedia) { index, mediaPath ->
                                MediaThumbnailItem(
                                    mediaPath = mediaPath,
                                    modifier = Modifier.size(width = 100.dp, height = 130.dp),
                                    onClick = {
                                        lightboxIndex = index
                                        showLightbox = true
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Carte Relation / Affinité
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = AccentPink,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Relation : ${character.relationshipStage}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "${character.affectionLevel}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentPink,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = (character.affectionLevel / 100f).coerceIn(0f, 1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = AccentPink,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }

            // Statut de la conversation en cours
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.hasConversation)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.Message,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (state.hasConversation)
                                "Conversation active (${state.messageCount} messages)"
                            else "Aucune conversation en cours",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (state.lastMessage != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Dernier échange : \"${state.lastMessage?.content?.take(100)}...\"",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val profile = remember(character.description, character.personality) {
                parseCharacterProfile(character.description, character.personality)
            }

            // 1. Présentation & Rôle
            if (profile.presentation.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Person, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Présentation & Rôle", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            profile.presentation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // 2. Description physique détaillée
            if (profile.statBadges.isNotEmpty() || profile.physicalTraits.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Description physique détaillée", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }

                        // Badges pour mensurations clés (Âge, Taille, Poids, Poitrine, etc.)
                        if (profile.statBadges.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                profile.statBadges.forEach { badge ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Text(
                                            text = badge,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }

                        // Traits physiques détaillés (Morphologie, Cheveux, Yeux, Style, Postures)
                        if (profile.physicalTraits.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                profile.physicalTraits.forEach { trait ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Text("• ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = trait,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Tempérament & Caractère
            if (profile.temperamentTitle.isNotBlank() || profile.temperamentDirective.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, Modifier.size(18.dp), tint = AccentPink)
                            Spacer(Modifier.width(8.dp))
                            Text("Tempérament & Caractère", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }

                        if (profile.temperamentTitle.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                color = AccentPink.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    text = "✨ ${profile.temperamentTitle}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentPink,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }

                        if (profile.temperamentDirective.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = profile.temperamentDirective,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            // 4. Personnalité & Comportement
            if (profile.personality.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Psychology, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Personnalité & Comportement", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            profile.personality,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // 5. Scénario de départ
            if (character.scenario.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Scénario de départ", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            character.scenario,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // 6. Message d'introduction
            if (character.firstMessage.isNotBlank()) {
                val greeting = resolveCharacterPlaceholders(character.firstMessage, character, state.userName)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ShortText, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Message d'introduction", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "« $greeting »",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    if (showLightbox && allMedia.isNotEmpty()) {
        MediaLightboxDialog(
            mediaList = allMedia,
            initialIndex = lightboxIndex.coerceIn(0, allMedia.size - 1),
            characterName = character.name,
            onDismiss = { showLightbox = false },
            onDeleteMedia = { mediaToDelete ->
                viewModel.removeGalleryMedia(mediaToDelete)
            },
            onSetAsAvatar = { mediaPath ->
                viewModel.setAsAvatar(mediaPath)
                android.widget.Toast.makeText(context, "Photo de profil mise à jour !", android.widget.Toast.LENGTH_SHORT).show()
            },
            onUploadToGitHub = { mediaPath ->
                android.widget.Toast.makeText(context, "Envoi vers GitHub en cours...", android.widget.Toast.LENGTH_SHORT).show()
                viewModel.uploadAvatarToGitHub(mediaPath) { success, msg ->
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    if (showGeneratePhotoDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!state.isGeneratingImage) showGeneratePhotoDialog = false
            },
            icon = {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            },
            title = {
                Text(
                    "Générer une photo IA (Horde Diffusion)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "⚡ 100% Gratuit • Sans clé requise • Photoréaliste & Non censuré",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "L'IA va composer une image fidèle à la description physique détaillée de ${character.name} (cheveux, yeux, visage, morphologie, âge) :",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    CharacterPhotoStyle.entries.forEach { styleOption ->
                        val isSelected = selectedPhotoStyle == styleOption
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPhotoStyle = styleOption },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                }
                            ),
                            border = if (isSelected) {
                                androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            } else null,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedPhotoStyle = styleOption },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                                )
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(
                                        styleOption.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        styleOption.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    if (selectedPhotoStyle == CharacterPhotoStyle.CUSTOM) {
                        OutlinedTextField(
                            value = customPromptInput,
                            onValueChange = { customPromptInput = it },
                            label = { Text("Consigne personnalisée (décor, tenue, pose)") },
                            placeholder = { Text("Ex: assise au piano en robe du soir...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { setAsAvatarOption = !setAsAvatarOption }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = setAsAvatarOption,
                            onCheckedChange = { setAsAvatarOption = it },
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Remplacer la photo de profil par cette nouvelle image",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGeneratePhotoDialog = false
                        viewModel.generateCharacterImage(
                            style = selectedPhotoStyle,
                            customInstruction = customPromptInput.takeIf { it.isNotBlank() },
                            setAsAvatar = setAsAvatarOption,
                            onComplete = { success, error ->
                                if (success) {
                                    val msg = if (setAsAvatarOption) "Nouvel avatar généré et mis à jour !" else "Nouvelle photo ajoutée à la galerie !"
                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    android.widget.Toast.makeText(context, error ?: "Erreur de génération", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    },
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Générer maintenant")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGeneratePhotoDialog = false }) {
                    Text("Annuler")
                }
            },
        )
    }

    if (showAddMediaDialog) {
        AlertDialog(
            onDismissRequest = { showAddMediaDialog = false },
            title = { Text("Ajouter un média") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Importez une photo, un GIF animé ou une vidéo (MP4/WebM) pour enrichir la galerie de ${character.name}.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = {
                            showAddMediaDialog = false
                            mediaPickerLauncher.launch("*/*")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Choisir depuis mon appareil")
                    }
                    Text(
                        "— OU —",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    OutlinedTextField(
                        value = mediaUrlInput,
                        onValueChange = { mediaUrlInput = it },
                        placeholder = { Text("https://... (URL d'image, GIF ou vidéo)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val url = mediaUrlInput.trim()
                        if (url.isNotBlank()) {
                            viewModel.addGalleryMediaFromUrl(url)
                            mediaUrlInput = ""
                            showAddMediaDialog = false
                        }
                    },
                    enabled = mediaUrlInput.isNotBlank(),
                ) {
                    Text("Ajouter par URL")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMediaDialog = false }) {
                    Text("Annuler")
                }
            },
        )
    }
}

data class ParsedCharacterProfile(
    val presentation: String,
    val statBadges: List<String>,
    val physicalTraits: List<String>,
    val temperamentTitle: String,
    val temperamentDirective: String,
    val personality: String,
)

fun parseCharacterProfile(rawDescription: String, rawPersonality: String): ParsedCharacterProfile {
    val (presentation, rawPhysical) = if (rawDescription.contains("Description physique", ignoreCase = true)) {
        val parts = rawDescription.split(Regex("Description physique[^\n]*", RegexOption.IGNORE_CASE), limit = 2)
        parts[0].trim() to (parts.getOrNull(1)?.trim() ?: "")
    } else {
        rawDescription.trim() to ""
    }

    val statBadges = mutableListOf<String>()
    val physicalTraits = mutableListOf<String>()

    if (rawPhysical.isNotBlank()) {
        val lines = rawPhysical.split("\n")
        for (line in lines) {
            val clean = line.trim()
            if (clean.isBlank()) continue
            if (clean.startsWith("• Temp", ignoreCase = true) || clean.startsWith("- Temp", ignoreCase = true)) {
                continue
            }
            if (clean.contains("|") && (clean.contains("Taille", ignoreCase = true) || clean.contains("Poitrine", ignoreCase = true))) {
                val stats = clean.removePrefix("•").removePrefix("-").split("|")
                for (s in stats) {
                    val st = s.trim()
                    if (st.isNotBlank()) {
                        statBadges.add(st)
                    }
                }
            } else {
                val trait = clean.removePrefix("•").removePrefix("-").trim()
                if (trait.isNotBlank()) {
                    physicalTraits.add(trait)
                }
            }
        }
    }

    val tempRegex = Regex("""Temp[ée]rament\s+([^:]+):\s*(.*)""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    val tempMatch = tempRegex.find(rawPersonality)

    val (purePersonality, tempTitle, tempDirective) = if (tempMatch != null) {
        val persBefore = rawPersonality.substring(0, tempMatch.range.first).trim()
        val title = tempMatch.groupValues[1].trim()
        var dir = tempMatch.groupValues[2].trim()
        val subIndex = dir.indexOf("Temp", ignoreCase = true)
        if (subIndex > 0) {
            dir = dir.substring(0, subIndex).trim()
        }
        Triple(persBefore, title, dir)
    } else {
        Triple(rawPersonality.trim(), "", "")
    }

    return ParsedCharacterProfile(
        presentation = presentation,
        statBadges = statBadges,
        physicalTraits = physicalTraits,
        temperamentTitle = tempTitle,
        temperamentDirective = tempDirective,
        personality = purePersonality,
    )
}

