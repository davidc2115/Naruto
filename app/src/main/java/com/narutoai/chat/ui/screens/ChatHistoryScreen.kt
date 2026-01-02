package com.narutoai.chat.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narutoai.chat.data.ConversationManager
import com.narutoai.chat.data.Characters
import java.text.SimpleDateFormat
import java.util.*

data class ConversationHistory(
    val characterId: String,
    val characterName: String,
    val characterEmoji: String,
    val lastMessage: String,
    val messageCount: Int,
    val lastMessageTime: Long,
    val isNSFW: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistoryScreen(
    onConversationClick: (characterId: String) -> Unit
) {
    val context = LocalContext.current
    val conversationManager = remember { ConversationManager(context) }
    
    // Charger les personnages custom
    val customViewModel: com.narutoai.chat.viewmodel.CustomCharactersViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val customCharacters by customViewModel.characters.collectAsState(initial = emptyList())
    
    // Convertir les custom characters en Character
    val customCharacterModels = remember(customCharacters) {
        customCharacters.map { entity ->
            com.narutoai.chat.utils.CharacterConverter.toCharacter(entity)
        }
    }
    
    // Combiner prédéfinis + custom
    val allCharacters = remember(customCharacterModels) {
        Characters.allCharacters + customCharacterModels
    }
    
    // État local pour les conversations
    var conversations by remember { mutableStateOf<List<ConversationHistory>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Charger les conversations au démarrage
    LaunchedEffect(allCharacters) {
        isLoading = true
        try {
            // Récupérer TOUS les IDs de conversations sauvegardées
            val conversationIds = conversationManager.getAllConversationIds()
            val allConversations = mutableListOf<ConversationHistory>()
            
            conversationIds.forEach { characterId ->
                // Charger la conversation
                val messages = conversationManager.loadConversation(characterId)
                if (messages != null && messages.isNotEmpty()) {
                    // Trouver le personnage (prédéfini OU custom)
                    val character = allCharacters.find { it.id == characterId }
                    
                    if (character != null) {
                        val isNSFW = conversationManager.getIsNSFW(characterId)
                        allConversations.add(
                            ConversationHistory(
                                characterId = character.id,
                                characterName = character.name,
                                characterEmoji = character.avatarEmoji,
                                lastMessage = messages.lastOrNull()?.content ?: "",
                                messageCount = messages.size,
                                lastMessageTime = messages.lastOrNull()?.timestamp ?: 0L,
                                isNSFW = isNSFW
                            )
                        )
                    }
                }
            }
            
            // Trier par date (plus récent en premier)
            conversations = allConversations.sortedByDescending { it.lastMessageTime }
        } catch (e: Exception) {
            android.util.Log.e("ChatHistory", "Erreur chargement: ${e.message}", e)
        } finally {
            isLoading = false
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Titre
        Text(
            text = "💬 Historique des chats",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            conversations.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "Aucune conversation",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Aucune conversation pour le moment",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Commencez un chat pour qu'il apparaisse ici !",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(conversations) { conv ->
                        ConversationCard(
                            conversation = conv,
                            onClick = { onConversationClick(conv.characterId) },
                            onDelete = {
                                // Supprimer la conversation
                                conversationManager.deleteConversation(conv.characterId)
                                // Recharger
                                conversations = conversations.filter { 
                                    it.characterId != conv.characterId
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationCard(
    conversation: ConversationHistory,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Emoji
            Text(
                text = conversation.characterEmoji,
                fontSize = 40.sp
            )
            
            // Infos
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = conversation.characterName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (conversation.isNSFW) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "NSFW",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                
                Text(
                    text = conversation.lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${conversation.messageCount} messages",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = formatTimestamp(conversation.lastMessageTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Bouton supprimer
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Supprimer",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    
    // Dialog de confirmation
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Supprimer la conversation ?") },
            text = { Text("Cette action est irréversible.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
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

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "À l'instant"
        diff < 3_600_000 -> "${diff / 60_000} min"
        diff < 86_400_000 -> "${diff / 3_600_000}h"
        diff < 604_800_000 -> "${diff / 86_400_000}j"
        else -> SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(timestamp))
    }
}
