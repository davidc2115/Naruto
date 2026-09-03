package com.opencompanion.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.opencompanion.app.data.ChatMessageEntity
import com.opencompanion.app.data.MessageRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var menuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.messages.size, state.streamingText) {
        val target = state.messages.size // +1 pour la bulle de streaming si présente
        if (target > 0) listState.animateScrollToItem((target - 1).coerceAtLeast(0))
    }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeStatusMessage()
        }
    }

    val isGenerating = state.status == EngineStatus.GENERATING || state.status == EngineStatus.LOADING_MODEL

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.character?.name ?: "…")
                        if (state.character != null) {
                            Text(
                                when {
                                    state.usingNano -> "⚡ Gemini Nano"
                                    state.usingGpu -> "GPU (Vulkan)"
                                    else -> "CPU"
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Retour") }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Réglages")
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Effacer l'historique") },
                            onClick = { menuExpanded = false; viewModel.clearHistory() },
                        )
                    }
                },
            )
        },
        bottomBar = {
            ChatInputBar(
                value = input,
                onValueChange = { input = it },
                isGenerating = isGenerating,
                onSend = {
                    if (input.text.isNotBlank()) {
                        viewModel.sendMessage(input.text)
                        input = TextFieldValue("")
                    }
                },
                onStop = viewModel::stopGeneration,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.status == EngineStatus.NO_MODEL_CONFIGURED) {
                ModelMissingBanner(onOpenSettings)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
                if (state.streamingText.isNotEmpty() || state.status == EngineStatus.LOADING_MODEL) {
                    item(key = "streaming") {
                        StreamingBubble(state.streamingText, state.status == EngineStatus.LOADING_MODEL)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelMissingBanner(onOpenSettings: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Aucun modèle sélectionné.", style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Ouvrir les réglages")
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessageEntity) {
    val isUser = message.role == MessageRole.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = formatRoleplayText(message.content, isUser),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun StreamingBubble(text: String, loadingModel: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (loadingModel) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text("  Chargement du modèle…")
                } else {
                    Text(if (text.isEmpty()) AnnotatedString("…") else formatRoleplayText(text, isUser = false))
                }
            }
        }
    }
}

/**
 * Convertit un message brut en texte stylé : dialogue dans la couleur de contenu par défaut de
 * la bulle, *actions* en italique dans une couleur d'accent (secondary), (pensées) en italique
 * dans une autre couleur d'accent (tertiary) — voir MessageFormatting.kt pour la convention et
 * le parseur. Les trois couleurs sont choisies explicitement plutôt que de laisser action/pensée
 * hériter la couleur de texte par défaut de la bulle : sans ça, une action se distinguait du
 * dialogue seulement par l'italique, pas par sa couleur, ce qui ne se voyait presque pas. [isUser]
 * adapte la couleur du dialogue au fond de la bulle (primaryContainer pour l'utilisateur,
 * surfaceVariant pour le personnage) pour rester lisible dans les deux cas.
 */
@Composable
private fun formatRoleplayText(raw: String, isUser: Boolean): AnnotatedString {
    val dialogueColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val actionColor = MaterialTheme.colorScheme.secondary
    val thoughtColor = MaterialTheme.colorScheme.tertiary
    return buildAnnotatedString {
        for (segment in parseMessageSegments(raw)) {
            when (segment) {
                is MessageSegment.Dialogue -> withStyle(SpanStyle(color = dialogueColor)) {
                    append(segment.text)
                }
                is MessageSegment.Action -> withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = actionColor)) {
                    append(segment.text)
                }
                is MessageSegment.Thought -> withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = thoughtColor)) {
                    append("‹${segment.text}›")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInputBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    isGenerating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            // Boutons "Action"/"Pensée" : insèrent les mêmes marqueurs (*…*, (…)) que ceux
            // enseignés au modèle (voir PromptBuilder.ROLEPLAY_FORMAT_DIRECTIVE), pour que
            // l'utilisateur puisse lui aussi écrire des actions/pensées mises en forme dans ses
            // propres messages, sans avoir à taper les astérisques/parenthèses de tête.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { onValueChange(wrapWithMarkers(value, "*", "*")) },
                    label = { Text("Action *…*") },
                    enabled = !isGenerating,
                )
                AssistChip(
                    onClick = { onValueChange(wrapWithMarkers(value, "(", ")")) },
                    label = { Text("Pensée (…)") },
                    enabled = !isGenerating,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Écris un message…") },
                    enabled = !isGenerating,
                    maxLines = 5,
                )
                if (isGenerating) {
                    IconButton(onClick = onStop) { Icon(Icons.Filled.Stop, contentDescription = "Arrêter") }
                } else {
                    IconButton(onClick = onSend) { Icon(Icons.Filled.Send, contentDescription = "Envoyer") }
                }
            }
        }
    }
}

/**
 * Enveloppe la sélection actuelle du champ de saisie entre [prefix]/[suffix] (ex. `*…*` pour une
 * action), ou insère `prefixsuffix` au niveau du curseur avec le curseur placé entre les deux
 * s'il n'y a pas de sélection — pour pouvoir enchaîner directement sur la frappe du contenu,
 * comme le ferait un vrai bouton de mise en forme.
 */
private fun wrapWithMarkers(value: TextFieldValue, prefix: String, suffix: String): TextFieldValue {
    val selection = value.selection
    val text = value.text
    val selectedText = text.substring(selection.min, selection.max)
    val newText = text.substring(0, selection.min) + prefix + selectedText + suffix + text.substring(selection.max)
    val cursor = if (selectedText.isEmpty()) {
        selection.min + prefix.length
    } else {
        selection.min + prefix.length + selectedText.length + suffix.length
    }
    return TextFieldValue(newText, selection = TextRange(cursor))
}
