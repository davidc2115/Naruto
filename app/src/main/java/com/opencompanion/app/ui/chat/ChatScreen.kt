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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import com.opencompanion.app.engine.DialogueMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.opencompanion.app.data.CharacterEntity
import com.opencompanion.app.data.ChatMessageEntity
import com.opencompanion.app.data.MessageRole
import com.opencompanion.app.data.UserPersonaEntity
import com.opencompanion.app.ui.components.CharacterAvatar
import com.opencompanion.app.ui.theme.BrandGradient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPersonas: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val personas by viewModel.personas.collectAsState()
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showPersonaPicker by remember { mutableStateOf(false) }
    var showRelationshipDialog by remember { mutableStateOf(false) }
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
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CharacterAvatar(
                            avatarPath = state.character?.avatarPath,
                            name = state.character?.name.orEmpty(),
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(Modifier.size(10.dp))
                        Column {
                        Text(state.character?.name ?: "…", style = MaterialTheme.typography.titleMedium)
                        if (state.character != null) {
                            val modeTag = when (state.dialogueMode) {
                                DialogueMode.AUTO_HYBRID -> "Auto"
                                DialogueMode.FORCE_NSFW -> "NSFW 🔓"
                                DialogueMode.FORCE_SFW -> "SFW ⚡"
                            }
                            val engineInfo = state.activeEngineLabel ?: when {
                                state.status == EngineStatus.LOADING_MODEL -> {
                                    val name = state.selectedModelName ?: "Modèle local"
                                    "⏳ Chargement de $name…"
                                }
                                state.usingNano -> "⚡ NPU (Gemini Nano)"
                                state.selectedModelName != null -> {
                                    val hw = if (state.usingGpu) "GPU" else "CPU"
                                    "🧠 ${state.selectedModelName} ($hw)"
                                }
                                state.usingGpu -> "🧠 Modèle local (GPU)"
                                else -> "🧠 Modèle local (CPU)"
                            }
                            Text(
                                "$engineInfo • $modeTag",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Retour") }
                },
                actions = {
                    // Bascule fluide SFW ↔ NSFW (1 clic)
                    IconButton(onClick = { viewModel.cycleDialogueMode() }) {
                        val (icon, tint, desc) = when (state.dialogueMode) {
                            DialogueMode.AUTO_HYBRID -> Triple(
                                Icons.Filled.AutoAwesome,
                                MaterialTheme.colorScheme.primary,
                                "Mode Auto : SFW ⚡ NPU ↔ NSFW 🔓 Locale"
                            )
                            DialogueMode.FORCE_NSFW -> Triple(
                                Icons.Filled.Bolt,
                                MaterialTheme.colorScheme.error,
                                "Mode Débridé NSFW forcé (IA Locale)"
                            )
                            DialogueMode.FORCE_SFW -> Triple(
                                Icons.Filled.Bolt,
                                MaterialTheme.colorScheme.tertiary,
                                "Mode SFW forcé (Gemini Nano NPU)"
                            )
                        }
                        Icon(icon, contentDescription = desc, tint = tint)
                    }
                    if (state.character != null) {
                        IconButton(onClick = { showRelationshipDialog = true }) {
                            Icon(
                                Icons.Filled.Favorite,
                                contentDescription = "Relation & mémoire (${state.character?.affectionLevel ?: 0}/100)",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Réglages")
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(if (state.dialogueMode == DialogueMode.AUTO_HYBRID) "✓ Mode Auto SFW ↔ NSFW" else "Mode Auto SFW ↔ NSFW") },
                            leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = { menuExpanded = false; viewModel.setDialogueMode(DialogueMode.AUTO_HYBRID) },
                        )
                        DropdownMenuItem(
                            text = { Text(if (state.dialogueMode == DialogueMode.FORCE_NSFW) "✓ Mode Débridé NSFW 🔓" else "Mode Débridé NSFW 🔓") },
                            leadingIcon = { Icon(Icons.Filled.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuExpanded = false; viewModel.setDialogueMode(DialogueMode.FORCE_NSFW) },
                        )
                        DropdownMenuItem(
                            text = { Text(if (state.dialogueMode == DialogueMode.FORCE_SFW) "✓ Mode SFW ⚡ (Gemini Nano)" else "Mode SFW ⚡ (Gemini Nano)") },
                            leadingIcon = { Icon(Icons.Filled.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                            onClick = { menuExpanded = false; viewModel.setDialogueMode(DialogueMode.FORCE_SFW) },
                        )
                        DropdownMenuItem(
                            text = { Text("Changer de persona") },
                            onClick = { menuExpanded = false; showPersonaPicker = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Effacer l'historique") },
                            onClick = { menuExpanded = false; viewModel.clearHistory() },
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
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

    if (showPersonaPicker) {
        PersonaPickerDialog(
            personas = personas,
            activePersonaId = state.character?.activePersonaId,
            onDismiss = { showPersonaPicker = false },
            onSelect = { personaId -> showPersonaPicker = false; viewModel.setActivePersona(personaId) },
            onManagePersonas = { showPersonaPicker = false; onOpenPersonas() },
        )
    }

    if (showRelationshipDialog) {
        state.character?.let { character ->
            RelationshipDialog(
                character = character,
                onDismiss = { showRelationshipDialog = false },
                onAffectionChange = viewModel::setAffectionLevel,
                onMemoryNotesChange = viewModel::updateMemoryNotes,
            )
        }
    }
}

/**
 * Édition du niveau de relation ("évolution avec chaque personnage" demandée) et des notes de
 * mémoire persistantes — voir CharacterEntity.affectionLevel/memoryNotes et
 * PromptBuilder.relationshipDirective pour comment c'est réinjecté dans le prompt. Le niveau
 * progresse tout seul avec les échanges (voir ChatViewModel.sendMessage) mais reste ajustable
 * ici manuellement, par exemple pour corriger une évolution qui ne correspond pas à la scène.
 */
@Composable
private fun RelationshipDialog(
    character: CharacterEntity,
    onDismiss: () -> Unit,
    onAffectionChange: (Int) -> Unit,
    onMemoryNotesChange: (String) -> Unit,
) {
    var affection by remember(character.id) { mutableStateOf(character.affectionLevel.toFloat()) }
    var notes by remember(character.id) { mutableStateOf(character.memoryNotes) }

    AlertDialog(
        onDismissRequest = { onAffectionChange(affection.toInt()); onMemoryNotesChange(notes); onDismiss() },
        title = { Text("Relation & mémoire — ${character.name}") },
        text = {
            Column {
                Text(
                    "Niveau de relation : ${affection.toInt()}/100 (${character.relationshipStage})",
                    style = MaterialTheme.typography.bodyMedium,
                )
                androidx.compose.material3.Slider(
                    value = affection,
                    onValueChange = { affection = it },
                    valueRange = 0f..100f,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Progresse automatiquement au fil des messages ; ajustable ici manuellement.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text("Mémoire (notes libres)", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("Faits importants à retenir pour cette histoire…") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAffectionChange(affection.toInt()); onMemoryNotesChange(notes); onDismiss() }) {
                Text("Fermer")
            }
        },
    )
}

/**
 * Choix du persona utilisateur pour CETTE conversation uniquement (voir
 * ChatViewModel.setActivePersona) — "Persona par défaut" revient explicitement au persona marqué
 * par défaut dans le gestionnaire plutôt que de figer un choix qui deviendrait incohérent si ce
 * défaut change plus tard.
 */
@Composable
private fun PersonaPickerDialog(
    personas: List<UserPersonaEntity>,
    activePersonaId: Long?,
    onDismiss: () -> Unit,
    onSelect: (Long?) -> Unit,
    onManagePersonas: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Changer de persona") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = activePersonaId == null, onClick = { onSelect(null) })
                    Text("Persona par défaut")
                }
                personas.forEach { persona ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = activePersonaId == persona.id,
                            onClick = { onSelect(persona.id) },
                        )
                        Text(persona.name)
                    }
                }
                if (personas.isEmpty()) {
                    Text(
                        "Aucun persona créé pour l'instant.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onManagePersonas) { Text("Gérer mes personas") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
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

/**
 * Bulles asymétriques façon SpicyChat/RosyTalk : dégradé de marque plein pour l'utilisateur
 * (coin bas-droit "pointu"), surface neutre sombre pour le personnage (coin bas-gauche
 * "pointu") — la forme de la bulle suffit à distinguer les deux sans dépendre uniquement de
 * l'alignement gauche/droite, utile notamment pour les lecteurs peu habitués au motif.
 */
private val UserBubbleShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
private val CharBubbleShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)

@Composable
private fun MessageBubble(message: ChatMessageEntity) {
    val isUser = message.role == MessageRole.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Box(
            modifier = Modifier
                .clip(if (isUser) UserBubbleShape else CharBubbleShape)
                .background(
                    if (isUser) BrandGradient else Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant),
                    ),
                )
                .widthIn(max = 300.dp),
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
        Box(
            modifier = Modifier
                .clip(CharBubbleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .widthIn(max = 300.dp),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (loadingModel) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.secondary)
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
    // Bulle utilisateur = dégradé de marque plein (rose→violet) : le texte y est toujours blanc,
    // avec des variantes légèrement teintées pour action/pensée plutôt que les couleurs d'accent
    // secondary/tertiary du thème, qui se distingueraient mal sur un fond déjà rose/violet.
    val dialogueColor = if (isUser) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val actionColor = if (isUser) androidx.compose.ui.graphics.Color(0xFFFFE1EC) else MaterialTheme.colorScheme.secondary
    val thoughtColor = if (isUser) androidx.compose.ui.graphics.Color(0xFFF3E8FF) else MaterialTheme.colorScheme.tertiary
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
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
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
                val sendButtonBrush = if (isGenerating) {
                    val c = MaterialTheme.colorScheme.surfaceVariant
                    Brush.linearGradient(listOf(c, c))
                } else {
                    BrandGradient
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(sendButtonBrush),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isGenerating) {
                        IconButton(onClick = onStop) {
                            Icon(Icons.Filled.Stop, contentDescription = "Arrêter", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        IconButton(onClick = onSend) {
                            Icon(Icons.Filled.Send, contentDescription = "Envoyer", tint = androidx.compose.ui.graphics.Color.White)
                        }
                    }
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
