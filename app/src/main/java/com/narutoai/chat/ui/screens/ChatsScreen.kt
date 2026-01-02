package com.narutoai.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.narutoai.chat.models.Character
import com.narutoai.chat.viewmodel.ChatViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Représente une conversation sauvegardée
 */
data class SavedChat(
    val character: Character,
    val lastMessage: String,
    val lastMessageTime: Long,
    val messageCount: Int
)

/**
 * Écran CHATS - Liste de toutes les conversations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    viewModel: ChatViewModel,
    onChatSelected: (Character) -> Unit
) {
    // Récupérer toutes les conversations sauvegardées
    val savedChats = remember {
        // TODO: Implémenter la récupération depuis SharedPreferences
        // Pour l'instant on peut récupérer la liste des personnages avec conversations
        mutableStateListOf<SavedChat>()
    }
    
    // Charger les conversations au montage
    LaunchedEffect(Unit) {
        // Scanner les conversations sauvegardées
        val context = viewModel.getApplication<android.app.Application>()
        val prefs = context.getSharedPreferences("chat_conversations", android.content.Context.MODE_PRIVATE)
        
        // Récupérer tous les IDs de personnages avec conversations
        val allKeys = prefs.all.keys.filter { it.endsWith("_messages") }
        
        allKeys.forEach { key ->
            val characterId = key.removeSuffix("_messages")
            val messagesJson = prefs.getString(key, null)
            
            if (!messagesJson.isNullOrEmpty() && messagesJson != "[]") {
                // Récupérer les infos du personnage
                // TODO: Charger le Character depuis l'ID
                android.util.Log.d("ChatsScreen", "Found chat for character: $characterId")
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Conversations",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                actions = {
                    Text(
                        text = "${savedChats.size} chat${if (savedChats.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (savedChats.isEmpty()) {
                // État vide
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = "Aucune conversation",
                        modifier = Modifier.size(96.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Aucune conversation",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Commencez à discuter avec un personnage pour voir vos conversations ici",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Liste des conversations
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(savedChats, key = { it.character.id }) { chat ->
                        ChatCard(
                            savedChat = chat,
                            onClick = { onChatSelected(chat.character) },
                            onDelete = {
                                // TODO: Implémenter suppression
                                savedChats.remove(chat)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Card pour une conversation sauvegardée
 */
@Composable
fun ChatCard(
    savedChat: SavedChat,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRENCH) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
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
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (savedChat.character.imageResId != 0) {
                    AsyncImage(
                        model = savedChat.character.imageResId,
                        contentDescription = savedChat.character.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = savedChat.character.name.take(2).uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
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
                Text(
                    text = savedChat.character.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(Modifier.height(4.dp))
                
                Text(
                    text = savedChat.lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(Modifier.height(4.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Message,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${savedChat.messageCount} messages",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dateFormat.format(Date(savedChat.lastMessageTime)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Actions
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Supprimer conversation",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
