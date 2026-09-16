package com.opencompanion.app.ui.characterlist

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opencompanion.app.data.ActiveChatConversation
import com.opencompanion.app.data.CharacterEntity
import com.opencompanion.app.data.MessageRole
import com.opencompanion.app.data.resolveCharacterPlaceholders
import com.opencompanion.app.ui.components.CharacterAvatar
import com.opencompanion.app.ui.theme.BrandGradient
import com.opencompanion.app.ui.theme.ScrimGradient
import kotlinx.coroutines.launch

/**
 * Écran d'accueil « Découverte » façon SpicyChat/RosyTalk : grille de cartes personnage (avatar
 * en fond, nom + tags en surimpression) plutôt qu'une simple liste — c'est la mise en page que
 * ces apps utilisent quasi universellement pour leur catalogue de personnages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterListScreen(
    viewModel: CharacterListViewModel,
    onOpenCharacterDetail: (Long) -> Unit,
    onOpenChat: (Long) -> Unit,
    onCreateCharacter: () -> Unit,
    onEditCharacter: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onBrowseImport: () -> Unit,
    onOpenPersonas: () -> Unit,
) {
    val characters by viewModel.filteredCharacters.collectAsState()
    val allCharacters by viewModel.characters.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()
    val selectedTemperament by viewModel.selectedTemperament.collectAsState()
    val popularTags by viewModel.popularTags.collectAsState()
    val importMessage by viewModel.importMessage.collectAsState()
    val activeChats by viewModel.activeChats.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importFromUri(it) }
    }
    // Sélection multiple : permet d'importer d'un coup toute une collection de fiches
    // personnage déjà téléchargées (.png / .json), sans repasser par le sélecteur pour chacune.
    val multiFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importFromUris(uris)
    }

    LaunchedEffect(importMessage) {
        importMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeImportMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (currentTab == HomeTab.DISCOVERY) "Explorer (${allCharacters.size})" else "Conversations (${activeChats.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenPersonas) {
                        Icon(Icons.Filled.Person, contentDescription = "Mes personas")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Réglages")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                NavigationBarItem(
                    selected = currentTab == HomeTab.DISCOVERY,
                    onClick = { viewModel.selectTab(HomeTab.DISCOVERY) },
                    icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = "Personnages") },
                    label = { Text("Personnages") },
                )
                NavigationBarItem(
                    selected = currentTab == HomeTab.CHATS,
                    onClick = { viewModel.selectTab(HomeTab.CHATS) },
                    icon = {
                        if (activeChats.isNotEmpty()) {
                            BadgedBox(badge = { Badge { Text("${activeChats.size}") } }) {
                                Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "Chats")
                            }
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "Chats")
                        }
                    },
                    label = { Text("Chats") },
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenSettings,
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Réglages") },
                    label = { Text("Réglages") },
                )
            }
        },
        floatingActionButton = {
            if (currentTab == HomeTab.DISCOVERY) {
                Box {
                    FloatingActionButton(
                        onClick = { menuExpanded = true },
                        containerColor = Color.Transparent,
                        elevation = FloatingActionButtonDefaultsElevation(),
                        modifier = Modifier.background(BrandGradient, shape = RoundedCornerShape(16.dp)),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Nouveau", tint = Color.White)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Créer un personnage") },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            onClick = { menuExpanded = false; onCreateCharacter() },
                        )
                        DropdownMenuItem(
                            text = { Text("Importer un fichier (.png / .json)") },
                            leadingIcon = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                filePicker.launch(
                                    arrayOf(
                                        "image/png",
                                        "application/json",
                                        "text/plain",
                                        "application/octet-stream",
                                    ),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Importer plusieurs fiches (.png / .json)") },
                            leadingIcon = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                multiFilePicker.launch(
                                    arrayOf(
                                        "image/png",
                                        "application/json",
                                        "text/plain",
                                        "application/octet-stream",
                                    ),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Importer depuis une URL") },
                            leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                            onClick = { menuExpanded = false; showUrlDialog = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Parcourir un site pour importer") },
                            leadingIcon = { Icon(Icons.Filled.Language, contentDescription = null) },
                            onClick = { menuExpanded = false; onBrowseImport() },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (currentTab == HomeTab.CHATS) {
            ActiveChatsView(
                activeChats = activeChats,
                onOpenChat = onOpenChat,
                onExploreCharacters = { viewModel.selectTab(HomeTab.DISCOVERY) },
                modifier = Modifier.padding(padding),
            )
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
            // Barre de recherche
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Rechercher par nom, tag, scénario...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Rechercher") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Effacer")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )

            // Filtres par Tempéraments (16 tempéraments uniques avec emojis)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    FilterChip(
                        selected = selectedTemperament == null,
                        onClick = { viewModel.setSelectedTemperament(null) },
                        label = { Text("🎭 Tous (${allCharacters.size})") },
                        shape = RoundedCornerShape(16.dp),
                    )
                }
                items(ALL_TEMPERAMENTS) { item ->
                    FilterChip(
                        selected = selectedTemperament == item.name,
                        onClick = { viewModel.setSelectedTemperament(item.name) },
                        label = { Text("${item.emoji} ${item.name}") },
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }

            // Filtres par tags populaires
            if (popularTags.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = selectedTag == null,
                            onClick = { viewModel.setSelectedTag(null) },
                            label = { Text("Tous (${allCharacters.size})") },
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                    items(popularTags) { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { viewModel.setSelectedTag(tag) },
                            label = { Text("#$tag") },
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                }
            }

            // Affichage de la liste / grille
            if (allCharacters.isEmpty()) {
                EmptyState(Modifier.fillMaxSize(), onCreateCharacter, onBrowseImport)
            } else if (characters.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.height(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Aucun personnage trouvé",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Aucun résultat pour cette recherche ou ce tag.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            viewModel.setSearchQuery("")
                            viewModel.setSelectedTag(null)
                            viewModel.setSelectedTemperament(null)
                        }) {
                            Text("Réinitialiser les filtres")
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(characters, key = { it.id }) { character ->
                        CharacterCard(
                            character = character,
                            onClick = { onOpenCharacterDetail(character.id) },
                            onQuickChat = { onOpenChat(character.id) },
                            onEdit = { onEditCharacter(character.id) },
                            onDelete = { scope.launch { viewModel.deleteCharacter(character) } },
                        )
                    }
                }
            }
        }
    }
    }

    if (showUrlDialog) {
        UrlImportDialog(
            onDismiss = { showUrlDialog = false },
            onConfirm = { url -> showUrlDialog = false; viewModel.importFromUrl(url) },
        )
    }
}

/** [FloatingActionButton] applique sa propre teinte de conteneur par-dessus le fond qu'on lui
 *  donne : une élévation à 0 partout évite qu'une ombre Material standard ne vienne casser le
 *  dégradé de marque posé en arrière-plan du bouton. */
@Composable
private fun FloatingActionButtonDefaultsElevation() =
    androidx.compose.material3.FloatingActionButtonDefaults.elevation(
        defaultElevation = 0.dp,
        pressedElevation = 0.dp,
        hoveredElevation = 0.dp,
        focusedElevation = 0.dp,
    )

@Composable
private fun EmptyState(modifier: Modifier, onCreateCharacter: () -> Unit, onBrowseImport: () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.height(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Aucun personnage pour l'instant",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Crée ton premier personnage ou importe une fiche existante.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCreateCharacter) { Text("Créer") }
                TextButton(onClick = onBrowseImport) { Text("Parcourir") }
            }
        }
    }
}

@Composable
private fun CharacterCard(
    character: CharacterEntity,
    onClick: () -> Unit,
    onQuickChat: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(Modifier.fillMaxSize()) {
            CharacterAvatar(
                avatarPath = character.avatarPath,
                name = character.name,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxSize(),
            )

            // Voile dégradé en bas de carte pour garder le nom/tags lisibles quel que soit le
            // contenu de l'avatar, sans avoir à en connaître la couleur dominante.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .align(Alignment.BottomCenter)
                    .background(ScrimGradient),
            )

            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
            ) {
                Text(
                    character.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = character.description.ifBlank { character.tags.firstOrNull().orEmpty() }
                if (subtitle.isNotBlank()) {
                    Text(
                        resolveCharacterPlaceholders(subtitle, character),
                        color = Color(0xFFE6DEEF),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (character.tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val matchedTemp = ALL_TEMPERAMENTS.firstOrNull { temp ->
                            character.tags.any { it.equals(temp.name, ignoreCase = true) } ||
                            character.personality.contains(temp.name, ignoreCase = true)
                        }
                        if (matchedTemp != null) {
                            TagChip("${matchedTemp.emoji} ${matchedTemp.name}")
                        }
                        character.tags.firstOrNull { tag ->
                            matchedTemp == null || !tag.equals(matchedTemp.name, ignoreCase = true)
                        }?.let { otherTag ->
                            TagChip(otherTag)
                        }
                    }
                }
            }

            Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                IconButton(onClick = { menuExpanded = true }) {
                    Surface(shape = androidx.compose.foundation.shape.CircleShape, color = Color(0x66000000)) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Options",
                            tint = Color.White,
                            modifier = Modifier.padding(6.dp),
                        )
                    }
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Présentation & Profil") },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        onClick = { menuExpanded = false; onClick() },
                    )
                    DropdownMenuItem(
                        text = { Text("Discuter directement") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null) },
                        onClick = { menuExpanded = false; onQuickChat() },
                    )
                    DropdownMenuItem(
                        text = { Text("Modifier la fiche") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menuExpanded = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Supprimer") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun TagChip(text: String) {
    Surface(
        color = Color(0x33FFFFFF),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun UrlImportDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importer depuis une URL") },
        text = {
            Column {
                Text(
                    "Colle le lien direct vers un personnage ou une fiche depuis Chub AI, SpicyChat, " +
                        "Janitor AI, RosyTalk, Polybuzz, ou une URL directe (image PNG embarquée / JSON).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("https://chub.ai/characters/... ou lien direct") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (url.isNotBlank()) onConfirm(url.trim()) }, enabled = url.isNotBlank()) {
                Text("Importer")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun ActiveChatsView(
    activeChats: List<ActiveChatConversation>,
    onOpenChat: (Long) -> Unit,
    onExploreCharacters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (activeChats.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Message,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Aucune conversation en cours",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Discutez avec n'importe quel personnage pour retrouver vos échanges ici et reprendre votre histoire à tout moment !",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Button(
                        onClick = onExploreCharacters,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Explorer les personnages")
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Vos discussions actives (${activeChats.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            items(activeChats, key = { it.character.id }) { chat ->
                ActiveChatItem(
                    chat = chat,
                    onClick = { onOpenChat(chat.character.id) },
                )
            }
        }
    }
}

@Composable
private fun ActiveChatItem(
    chat: ActiveChatConversation,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CharacterAvatar(
                avatarPath = chat.character.avatarPath,
                name = chat.character.name,
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(14.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        chat.character.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        formatRelativeTime(chat.lastMessage.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    chat.character.description.substringBefore("\n").take(45),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val senderPrefix = if (chat.lastMessage.role == MessageRole.USER) "Vous : " else ""
                Text(
                    senderPrefix + chat.lastMessage.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = diff / (60 * 1000)
    val hours = diff / (60 * 60 * 1000)
    val days = diff / (24 * 60 * 60 * 1000)
    return when {
        minutes < 1 -> "À l'instant"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days == 1L -> "Hier"
        else -> "${days}j"
    }
}

