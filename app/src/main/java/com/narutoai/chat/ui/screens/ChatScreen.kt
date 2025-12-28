package com.narutoai.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narutoai.chat.models.Character
import com.narutoai.chat.models.ChatMessage
import com.narutoai.chat.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    character: Character,
    onBackClick: () -> Unit
) {
    val messages by viewModel.messages
    val isNSFWMode by viewModel.isNSFWMode
    val isLoading by viewModel.isLoading
    val error by viewModel.error
    val isGeneratingImage by viewModel.isGeneratingImage
    val isGeneratingVideo by viewModel.isGeneratingVideo
    
    var inputText by remember { mutableStateOf("") }
    var showMediaMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Auto-scroll to bottom when new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }
    
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
                        Column {
                            Text(
                                text = character.name,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isNSFWMode) "Mode NSFW" else "Mode SFW",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isNSFWMode) Color(0xFFFF6B6B) else Color(0xFF4ECDC4)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                    }
                },
                actions = {
                    // Bouton génération de média
                    IconButton(onClick = { showMediaMenu = !showMediaMenu }) {
                        Icon(Icons.Default.PhotoLibrary, "Générer image/vidéo")
                    }
                    DropdownMenu(
                        expanded = showMediaMenu,
                        onDismissRequest = { showMediaMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("📸 Générer Image") },
                            onClick = {
                                showMediaMenu = false
                                viewModel.generateImageFromConversation()
                            },
                            leadingIcon = {
                                if (isGeneratingImage) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    Icon(Icons.Default.Image, null)
                                }
                            },
                            enabled = !isGeneratingImage && !isGeneratingVideo
                        )
                        DropdownMenuItem(
                            text = { Text("🎬 Générer Vidéo") },
                            onClick = {
                                showMediaMenu = false
                                viewModel.generateVideoFromConversation()
                            },
                            leadingIcon = {
                                if (isGeneratingVideo) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    Icon(Icons.Default.VideoLibrary, null)
                                }
                            },
                            enabled = !isGeneratingImage && !isGeneratingVideo
                        )
                    }
                    
                    IconButton(
                        onClick = { viewModel.toggleNSFWMode() }
                    ) {
                        Icon(
                            if (isNSFWMode) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Changer de mode",
                            tint = if (isNSFWMode) Color(0xFFFF6B6B) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { viewModel.clearChat() }) {
                        Icon(Icons.Default.Delete, "Effacer la conversation")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            // Input field
            Surface(
                shadowElevation = 8.dp,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { newText -> inputText = newText },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Écris un message...") },
                        enabled = !isLoading,
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendMessage(inputText)
                                    inputText = ""
                                    keyboardController?.hide()
                                }
                            }
                        ),
                        singleLine = false
                    )
                    
                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        },
                        enabled = !isLoading && inputText.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Envoyer")
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Image de fond du personnage (semi-transparente)
            if (character.imageResId != 0) {
                AsyncImage(
                    model = character.imageResId,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.15f), // Très transparent pour ne pas gêner la lecture
                    contentScale = ContentScale.Crop
                )
            }
            
            // Messages list
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Welcome message
                if (messages.isEmpty()) {
                    item {
                        WelcomeCard(character, isNSFWMode)
                    }
                }
                
                items(messages) { message ->
                    MessageBubble(message, character)
                }
                
                // Loading indicator
                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "${character.name} écrit...",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Error snackbar
            if (error != null) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("OK")
                        }
                    }
                ) {
                    Text(text = error ?: "Erreur inconnue")
                }
            }
        }
    }
}

@Composable
fun WelcomeCard(character: Character, isNSFWMode: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = character.avatarEmoji,
                fontSize = 48.sp
            )
            Text(
                text = "Discuter avec ${character.name}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = character.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
            )
            
            if (isNSFWMode) {
                Surface(
                    color = Color(0xFFFF6B6B),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "⚠️ NSFW Mode Active",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
            
            Text(
                text = "Commence la conversation !",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage, character: Character) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            color = if (message.isUser) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) // Transparent
            } else {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f) // Transparent
            },
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!message.isUser) {
                        Text(
                            text = character.avatarEmoji,
                            fontSize = 20.sp
                        )
                    }
                    
                    Text(
                        text = if (message.isUser) {
                            AnnotatedString(message.content)
                        } else {
                            formatRoleplayText(message.content)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message.isUser) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                }
                
                // Afficher l'image générée si présente
                message.imageUrl?.let { imageUrl ->
                    coil.compose.SubcomposeAsyncImage(
                        model = imageUrl,
                        contentDescription = "Image générée",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 300.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        },
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.errorContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ImageNotSupported,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        "Erreur chargement",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    )
                }
                
                // Afficher la vidéo générée si présente
                message.videoUrl?.let { videoUrl ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Afficher l'aperçu (image si c'est Pollination AI)
                        coil.compose.SubcomposeAsyncImage(
                            model = videoUrl,
                            contentDescription = "Vidéo générée",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 150.dp, max = 200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop,
                            loading = {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            },
                            error = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.errorContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.ImageNotSupported,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        )
                        
                        // Bouton pour ouvrir le lien
                        val context = androidx.compose.ui.platform.LocalContext.current
                        androidx.compose.material3.TextButton(
                            onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(videoUrl))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ouvrir dans le navigateur", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun formatRoleplayText(text: String): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        val dialogueRegex = Regex("\"([^\"]+)\"")
        val actionRegex = Regex("\\*([^*]+)\\*")
        val thoughtRegex = Regex("\\(([^)]+)\\)")
        
        // Trouver tous les matches
        val dialogueMatches = dialogueRegex.findAll(text).toList()
        val actionMatches = actionRegex.findAll(text).toList()
        val thoughtMatches = thoughtRegex.findAll(text).toList()
        
        // Combiner et trier tous les matches par position
        val allMatches = (dialogueMatches + actionMatches + thoughtMatches)
            .sortedBy { it.range.first }
        
        allMatches.forEach { match ->
            // Ajouter le texte avant le match (texte normal)
            if (currentIndex < match.range.first) {
                append(text.substring(currentIndex, match.range.first))
            }
            
            // Ajouter le match avec style
            when {
                match in dialogueMatches -> {
                    // Dialogue en blanc (couleur normale)
                    append(match.value)
                }
                match in actionMatches -> {
                    // Actions en orange/jaune
                    withStyle(style = SpanStyle(color = Color(0xFFFFB347))) {
                        append(match.value)
                    }
                }
                match in thoughtMatches -> {
                    // Pensées en gris/bleu clair italique
                    withStyle(style = SpanStyle(color = Color(0xFF9DB4C0), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                        append(match.value)
                    }
                }
            }
            
            currentIndex = match.range.last + 1
        }
        
        // Ajouter le reste du texte
        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}
