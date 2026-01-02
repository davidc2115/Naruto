package com.narutoai.chat.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.narutoai.chat.data.predefinedCharacters
import com.narutoai.chat.models.Character
import com.narutoai.chat.models.CharacterCategory
import com.narutoai.chat.viewmodel.CustomCharactersViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    onCharacterClick: (Character) -> Unit,
    customViewModel: CustomCharactersViewModel = viewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    
    // Récupérer les personnages custom
    val customCharacters by customViewModel.characters.collectAsState(initial = emptyList())
    
    // Convertir CustomCharacterEntity en Character
    val customCharacterModels = customCharacters.map { entity ->
        Character(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            category = CharacterCategory.CELEBRITY_FEMALE, // Par défaut
            systemPromptSFW = entity.systemPromptSFW,
            systemPromptNSFW = entity.systemPromptNSFW,
            avatarEmoji = "👤",
            personality = try { 
                org.json.JSONArray(entity.personality).let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }
            } catch (e: Exception) { emptyList() },
            physicalDescription = entity.physicalDescription,
            age = entity.age,
            height = entity.height,
            hairColor = entity.hairColor,
            eyeColor = entity.eyeColor,
            bodyType = entity.bodyType,
            scenario = entity.scenario,
            temperament = entity.temperament,
            greetingMessage = entity.greetingMessage
        )
    }
    
    // Combiner personnages prédéfinis + custom
    val allCharacters = predefinedCharacters + customCharacterModels
    
    // Tags disponibles
    val availableTags = remember(allCharacters) {
        buildSet {
            allCharacters.forEach { char ->
                // Tags par catégorie
                when (char.category) {
                    CharacterCategory.NARUTO -> add("Naruto")
                    CharacterCategory.CELEBRITY_MALE -> add("Célébrité")
                    CharacterCategory.CELEBRITY_FEMALE -> add("Célébrité")
                }
                
                // Tags par âge
                val ageLower = char.age.lowercase()
                when {
                    ageLower.contains("ado") || ageLower.contains("teen") -> add("Ado")
                    ageLower.contains("18") || ageLower.contains("20") || ageLower.contains("jeune") -> add("Jeune adulte")
                    ageLower.contains("30") || ageLower.contains("40") || ageLower.contains("adulte") -> add("Adulte")
                    ageLower.contains("50") || ageLower.contains("60") || ageLower.contains("mature") -> add("Mature")
                }
                
                // Tags custom si le personnage est custom
                if (customCharacterModels.any { it.id == char.id }) {
                    add("Custom")
                }
            }
        }.toList().sorted()
    }
    
    // Filtrer les personnages
    val filteredCharacters = remember(allCharacters, searchQuery, selectedTag) {
        allCharacters.filter { char ->
            val matchesSearch = searchQuery.isBlank() || 
                char.name.contains(searchQuery, ignoreCase = true) ||
                char.description.contains(searchQuery, ignoreCase = true)
            
            val matchesTag = selectedTag == null || when (selectedTag) {
                "Naruto" -> char.category == CharacterCategory.NARUTO
                "Célébrité" -> char.category == CharacterCategory.CELEBRITY_MALE || 
                             char.category == CharacterCategory.CELEBRITY_FEMALE
                "Custom" -> customCharacterModels.any { it.id == char.id }
                "Ado" -> char.age.lowercase().let { it.contains("ado") || it.contains("teen") }
                "Jeune adulte" -> char.age.lowercase().let { it.contains("18") || it.contains("20") || it.contains("jeune") }
                "Adulte" -> char.age.lowercase().let { it.contains("30") || it.contains("40") || it.contains("adulte") }
                "Mature" -> char.age.lowercase().let { it.contains("50") || it.contains("60") || it.contains("mature") }
                else -> true
            }
            
            matchesSearch && matchesTag
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Titre
        Text(
            text = "🌍 Explorer",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Barre de recherche
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Rechercher un personnage...") },
            leadingIcon = { Icon(Icons.Default.Search, "Recherche") },
            singleLine = true,
            shape = RoundedCornerShape(24.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Tags/Filtres
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            // Bouton "Tous"
            item {
                FilterChip(
                    selected = selectedTag == null,
                    onClick = { selectedTag = null },
                    label = { Text("Tous (${allCharacters.size})") }
                )
            }
            
            // Tags dynamiques
            items(availableTags) { tag ->
                val count = allCharacters.count { char ->
                    when (tag) {
                        "Naruto" -> char.category == CharacterCategory.NARUTO
                        "Célébrité" -> char.category == CharacterCategory.CELEBRITY_MALE || 
                                     char.category == CharacterCategory.CELEBRITY_FEMALE
                        "Custom" -> customCharacterModels.any { it.id == char.id }
                        "Ado" -> char.age.lowercase().let { it.contains("ado") || it.contains("teen") }
                        "Jeune adulte" -> char.age.lowercase().let { it.contains("18") || it.contains("20") || it.contains("jeune") }
                        "Adulte" -> char.age.lowercase().let { it.contains("30") || it.contains("40") || it.contains("adulte") }
                        "Mature" -> char.age.lowercase().let { it.contains("50") || it.contains("60") || it.contains("mature") }
                        else -> false
                    }
                }
                
                FilterChip(
                    selected = selectedTag == tag,
                    onClick = { selectedTag = if (selectedTag == tag) null else tag },
                    label = { Text("$tag ($count)") }
                )
            }
        }
        
        // Grille de personnages
        if (filteredCharacters.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucun personnage trouvé",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredCharacters) { character ->
                    CharacterCard(
                        character = character,
                        onClick = { onCharacterClick(character) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterCard(
    character: Character,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Emoji/Avatar
            Text(
                text = character.avatarEmoji,
                fontSize = 48.sp,
                modifier = Modifier.padding(8.dp)
            )
            
            // Nom
            Text(
                text = character.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Description courte
            Text(
                text = character.description,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
