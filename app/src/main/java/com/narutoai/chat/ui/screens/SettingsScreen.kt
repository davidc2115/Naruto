package com.narutoai.chat.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.narutoai.chat.api.KeyStats
import com.narutoai.chat.data.PreferencesManager
import com.narutoai.chat.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onBackClick: () -> Unit,
    onAdminTagsClick: (() -> Unit)? = null
) {
    var newGroqKey by remember { mutableStateOf("") }
    var showAddKeyDialog by remember { mutableStateOf(false) }
    var keyStats by remember { mutableStateOf<List<KeyStats>>(emptyList()) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String?>?>(null) }
    
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
            // Section Google Gemini Vision (NOUVEAU - pour analyse d'images)
            item {
                var geminiKey by remember { 
                    val context = LocalContext.current
                    val prefs = context.getSharedPreferences("naruto_ai_prefs", android.content.Context.MODE_PRIVATE)
                    mutableStateOf(prefs.getString("gemini_api_key", "") ?: "")
                }
                var showGeminiKeyInput by remember { mutableStateOf(false) }
                var showGeminiPassword by remember { mutableStateOf(false) }
                
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
                            Icon(Icons.Default.PhotoCamera, "Vision API")
                            Text(
                                text = "🆕 Google Gemini Vision",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Text(
                            text = "Clé API GRATUITE pour analyser les photos lors de la création de personnages. 60 requêtes/min, 1500/jour.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                        
                        if (geminiKey.isEmpty() || showGeminiKeyInput) {
                            OutlinedTextField(
                                value = geminiKey,
                                onValueChange = { geminiKey = it },
                                label = { Text("Clé API Google Gemini") },
                                placeholder = { Text("AIzaSy...") },
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = if (showGeminiPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showGeminiPassword = !showGeminiPassword }) {
                                        Icon(
                                            if (showGeminiPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            null
                                        )
                                    }
                                },
                                singleLine = true
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val context = LocalContext.current
                                        val prefs = context.getSharedPreferences("naruto_ai_prefs", android.content.Context.MODE_PRIVATE)
                                        prefs.edit().putString("gemini_api_key", geminiKey).apply()
                                        showGeminiKeyInput = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = geminiKey.startsWith("AIza")
                                ) {
                                    Icon(Icons.Default.Save, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Enregistrer")
                                }
                                
                                if (geminiKey.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { showGeminiKeyInput = false },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Annuler")
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "✅ Clé configurée: ${geminiKey.take(10)}...${geminiKey.takeLast(4)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { showGeminiKeyInput = true }) {
                                    Icon(Icons.Default.Edit, "Modifier")
                                }
                            }
                        }
                        
                        OutlinedButton(
                            onClick = { 
                                val context = LocalContext.current
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    data = android.net.Uri.parse("https://makersuite.google.com/app/apikey")
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.OpenInNew, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Obtenir une clé gratuite")
                        }
                    }
                }
            }
            
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
                                text = "Clés API Groq (Chat)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Text(
                            text = "Gérez vos clés API Groq pour le chat uniquement. Plusieurs clés tournent automatiquement.",
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
                        
                        InfoRow("🚀 Groq API", "Chat uniquement (console.groq.com)")
                        InfoRow("🎨 Google Gemini", "Analyse photos GRATUITE")
                        InfoRow("🖼️ Pollination AI", "Génération images/vidéos gratuite")
                        InfoRow("📊 Limite Groq", "14,400 req/jour gratuit")
                        InfoRow("📊 Limite Gemini", "60 req/min, 1500/jour gratuit")
                        InfoRow("🔄 Rotation", "Automatique entre clés")
                    }
                }
            }
            
            // Administration (protégé par mot de passe)
            if (onAdminTagsClick != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Shield,
                                    null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Administration",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            
                            Text(
                                text = "Zone réservée à l'administrateur (protégée par mot de passe)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            )
                            
                            Button(
                                onClick = onAdminTagsClick,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.AdminPanelSettings, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Gestion des tags")
                            }
                        }
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

@Composable
fun ApiSelectionRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
