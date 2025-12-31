package com.narutoai.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.narutoai.chat.data.Characters
import com.narutoai.chat.data.CharacterPackLoader
import com.narutoai.chat.utils.CharacterConverter
import com.narutoai.chat.utils.CharacterTagUtils
import com.narutoai.chat.viewmodel.CustomCharactersViewModel
import com.narutoai.chat.models.Character
import com.narutoai.chat.models.CharacterCategory
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterSelectionScreen(
    onCharacterSelected: (Character) -> Unit,
    onSettingsClick: () -> Unit = {},
    onUserProfileClick: () -> Unit = {},
    onCreateCharacterClick: () -> Unit = {},
    onCustomCharactersClick: () -> Unit = {},
    chatViewModel: com.narutoai.chat.viewmodel.ChatViewModel? = null
) {
    var selectedCategory by remember { mutableStateOf<CharacterCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val selectedTags = remember { mutableStateListOf<String>() }

    // Charger les personnages custom depuis Room pour les afficher aussi ici
    val customVm: CustomCharactersViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val customEntities by customVm.characters.collectAsState()
    val customCharacters = remember(customEntities) {
        customEntities.map { CharacterConverter.toCharacter(it) }
    }

    // Charger le pack (assets)
    val context = LocalContext.current
    var packCharacters by remember { mutableStateOf<List<Character>>(emptyList()) }
    LaunchedEffect(Unit) {
        packCharacters = withContext(Dispatchers.IO) {
            CharacterPackLoader.load(context)
        }
    }
    
    val baseCharacters = remember(packCharacters) {
        // Persos intégrés + pack
        Characters.allCharacters + packCharacters
    }

    val categoryFiltered = remember(baseCharacters, selectedCategory) {
        when (selectedCategory) {
            null -> baseCharacters
            CharacterCategory.NARUTO -> baseCharacters.filter { it.category == CharacterCategory.NARUTO }
            CharacterCategory.CELEBRITY_MALE, CharacterCategory.CELEBRITY_FEMALE ->
                baseCharacters.filter { it.category == CharacterCategory.CELEBRITY_MALE || it.category == CharacterCategory.CELEBRITY_FEMALE }
            else -> baseCharacters
        }
    }

    val combinedCharacters = remember(customCharacters, categoryFiltered, selectedCategory) {
        // On n'affiche les customs que dans "Tous" (sinon, on conserve le filtre existant)
        val ids = HashSet<String>()
        if (selectedCategory == null) {
            (customCharacters + categoryFiltered).filter { ids.add(it.id) }
        } else {
            categoryFiltered.filter { ids.add(it.id) }
        }
    }

    val suggestedTags = remember(combinedCharacters) {
        // Top tags les plus fréquents
        val counts = mutableMapOf<String, Int>()
        combinedCharacters.forEach { c ->
            CharacterTagUtils.uiTagsFor(c).forEach { t -> counts[t] = (counts[t] ?: 0) + 1 }
        }
        counts.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(20)
    }

    val filteredCharacters = remember(combinedCharacters, searchQuery, selectedTags) {
        val q = searchQuery.trim().lowercase()
        combinedCharacters.filter { c ->
            val tags = CharacterTagUtils.uiTagsFor(c)
            val matchesQuery = q.isEmpty() ||
                c.name.lowercase().contains(q) ||
                c.description.lowercase().contains(q) ||
                tags.any { it.contains(q) }
            val matchesTags = selectedTags.isEmpty() || selectedTags.all { it in tags }
            matchesQuery && matchesTags
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Naruto AI Chat",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onCustomCharactersClick) {
                        Icon(Icons.Default.People, "Mes personnages")
                    }
                    IconButton(onClick = onUserProfileClick) {
                        Icon(Icons.Default.Person, "Mon Profil")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, "Paramètres")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateCharacterClick,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, "Créer")
                    Text("Créer personnage")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Category tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryChip(
                    label = "Tous",
                    isSelected = selectedCategory == null,
                    onClick = { selectedCategory = null }
                )
                CategoryChip(
                    label = "Naruto",
                    isSelected = selectedCategory == CharacterCategory.NARUTO,
                    onClick = { selectedCategory = CharacterCategory.NARUTO }
                )
                CategoryChip(
                    label = "Célébrités",
                    isSelected = selectedCategory == CharacterCategory.CELEBRITY_MALE || 
                               selectedCategory == CharacterCategory.CELEBRITY_FEMALE,
                    onClick = {
                        selectedCategory = if (selectedCategory == CharacterCategory.CELEBRITY_MALE) {
                            CharacterCategory.CELEBRITY_FEMALE
                        } else {
                            CharacterCategory.CELEBRITY_MALE
                        }
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Recherche + tags
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Rechercher") },
                label = { Text("Rechercher par nom / description / tag") },
                placeholder = { Text("Ex: brune, romantique, ninja…") }
            )

            if (suggestedTags.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(suggestedTags) { tag ->
                        FilterChip(
                            selected = selectedTags.contains(tag),
                            onClick = {
                                if (selectedTags.contains(tag)) selectedTags.remove(tag) else selectedTags.add(tag)
                            },
                            label = { Text("#$tag") }
                        )
                    }
                }
            }
            
            // Character list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredCharacters) { character ->
                    CharacterCard(
                        character = character,
                        onClick = { onCharacterSelected(character) },
                        viewModel = chatViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun CharacterCard(
    character: Character,
    onClick: () -> Unit,
    viewModel: com.narutoai.chat.viewmodel.ChatViewModel? = null
) {
    var thumbnailUrl by remember { mutableStateOf(character.thumbnailUrl) }
    
    // Générer automatiquement une vignette (Pollination) si pas d'image locale.
    // Le ViewModel met en cache en SharedPreferences pour éviter de spammer l'API.
    LaunchedEffect(character.id) {
        if (thumbnailUrl.isEmpty() && character.imageResId == 0 && character.physicalDescription.isNotEmpty() && viewModel != null) {
            viewModel.generateCharacterThumbnail(character) { url ->
                thumbnailUrl = url
            }
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar (image si dispo, sinon emoji)
            val imageModel = when {
                thumbnailUrl.isNotEmpty() -> thumbnailUrl
                character.imageResId != 0 -> character.imageResId
                else -> null
            }
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = character.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = character.avatarEmoji,
                        fontSize = 32.sp
                    )
                }
            }
            
            // Character info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = character.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Tags (personality)
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Afficher des tags "recherche" (inclut genre/couleur cheveux/etc)
                    CharacterTagUtils.uiTagsFor(character).take(3).forEach { trait ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = trait,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                
                // Galerie d'images (aperçu 3 premières images)
                if (character.gallery.isNotEmpty()) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(character.gallery.take(3)) { imageUri ->
                            val imageModel = if (imageUri.startsWith("drawable://")) {
                                // Extraire le nom du fichier (sans .jpg)
                                val fileName = imageUri.removePrefix("drawable://").removeSuffix(".jpg")
                                // Construire l'ID de ressource dynamiquement
                                val resId = context.resources.getIdentifier(fileName, "drawable", context.packageName)
                                if (resId != 0) resId else imageUri
                            } else {
                                imageUri
                            }
                            
                            AsyncImage(
                                model = imageModel,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
    }
}
