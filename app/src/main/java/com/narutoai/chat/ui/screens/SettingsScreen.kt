package com.narutoai.chat.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.narutoai.chat.api.KeyStats
import com.narutoai.chat.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onBackClick: () -> Unit
) {
    var newGroqKey by remember { mutableStateOf("") }
    var replicateKey by remember { mutableStateOf(viewModel.replicateApiKey.value ?: "") }
    var showAddKeyDialog by remember { mutableStateOf(false) }
    var keyStats by remember { mutableStateOf<List<KeyStats>>(emptyList()) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String?>?>(null) }
    var showPassword by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    
    // Charger les statistiques des clés
    LaunchedEffect(Unit) {
        keyStats = viewModel.getGroqKeyManager().getAllKeysWithStats()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section Groq API
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Key, "Clés API")
                            Text(
                                text = "Clés API Groq",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Text(
                            text = "Gérez vos clés API Groq pour le chat. Plusieurs clés tournent automatiquement.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        
                        Button(
                            onClick = { showAddKeyDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Ajouter une clé Groq")
                        }
                        
                        Button(
                            onClick = {
                                viewModel.testGroqConnection { success, error ->
                                    testResult = Pair(success, error)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(Icons.Default.CheckCircle, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Tester la connexion")
                        }
                        
                        if (testResult != null) {
                            Surface(
                                color = if (testResult!!.first) Color(0xFF4CAF50) else Color(0xFFF44336),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (testResult!!.first) {
                                        "✅ Connexion réussie!"
                                    } else {
                                        "❌ Échec: ${testResult!!.second}"
                                    },
                                    modifier = Modifier.padding(12.dp),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
            
            // Liste des clés Groq
            if (keyStats.isNotEmpty()) {
                item {
                    Text(
                        text = "Clés configurées (${keyStats.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                items(keyStats) { stat ->
                    KeyStatCard(
                        keyStat = stat,
                        onRemove = {
                            coroutineScope.launch {
                                viewModel.getGroqKeyManager().removeKey(stat.fullKey)
                                keyStats = viewModel.getGroqKeyManager().getAllKeysWithStats()
                            }
                        }
                    )
                }
            }
            
            // Section Replicate API
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PhotoLibrary, "Images/Vidéos")
                            Text(
                                text = "Clé API Replicate",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Text(
                            text = "Pour générer des images et vidéos. Obtenez une clé gratuite sur replicate.com",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                        
                        OutlinedTextField(
                            value = replicateKey,
                            onValueChange = { replicateKey = it },
                            label = { Text("Clé Replicate") },
                            placeholder = { Text("r8_...") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (showPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        "Toggle visibility"
                                    )
                                }
                            },
                            singleLine = true
                        )
                        
                        Button(
                            onClick = {
                                viewModel.setReplicateApiKey(replicateKey)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = replicateKey.isNotBlank()
                        ) {
                            Icon(Icons.Default.Save, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Sauvegarder")
                        }
                    }
                }
            }
            
            // Informations
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, "Info")
                            Text(
                                text = "Informations",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        InfoRow("🚀 Groq API", "console.groq.com")
                        InfoRow("🎨 Replicate API", "replicate.com")
                        InfoRow("📊 Limite Groq", "14,400 req/jour gratuit")
                        InfoRow("🔄 Rotation", "Automatique entre clés")
                    }
                }
            }
        }
    }
    
    // Dialog pour ajouter une clé
    if (showAddKeyDialog) {
        AlertDialog(
            onDismissRequest = { showAddKeyDialog = false },
            title = { Text("Ajouter une clé Groq") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Entrez votre clé API Groq (commence par gsk_)")
                    OutlinedTextField(
                        value = newGroqKey,
                        onValueChange = { newGroqKey = it },
                        label = { Text("Clé API") },
                        placeholder = { Text("gsk_...") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.getGroqKeyManager().addKey(newGroqKey)
                            keyStats = viewModel.getGroqKeyManager().getAllKeysWithStats()
                            newGroqKey = ""
                            showAddKeyDialog = false
                        }
                    },
                    enabled = newGroqKey.startsWith("gsk_")
                ) {
                    Text("Ajouter")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddKeyDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
fun KeyStatCard(
    keyStat: KeyStats,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = keyStat.key,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (keyStat.isActive) {
                        Surface(
                            color = Color(0xFF4CAF50),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
                
                Text(
                    text = "✅ ${keyStat.usageCount} réussies • ❌ ${keyStat.errorCount} erreurs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    "Supprimer",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
