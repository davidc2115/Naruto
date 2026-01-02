package com.narutoai.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.narutoai.chat.data.Characters
import com.narutoai.chat.models.Character
import com.narutoai.chat.ui.screens.*
import com.narutoai.chat.utils.CharacterConverter
import com.narutoai.chat.viewmodel.ChatViewModel
import com.narutoai.chat.viewmodel.CustomCharactersViewModel

@Composable
fun NarutoAIChatApp(viewModel: ChatViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    // Charger les personnages custom
    val customViewModel: CustomCharactersViewModel = viewModel()
    val customCharacters by customViewModel.characters.collectAsState(initial = emptyList())
    
    // Convertir les custom characters en Character
    val customCharacterModels = remember(customCharacters) {
        customCharacters.map { entity ->
            CharacterConverter.toCharacter(entity)
        }
    }
    
    // Combiner prédéfinis + custom
    val allCharacters = remember(customCharacterModels) {
        Characters.allCharacters + customCharacterModels
    }
    
    // Fonction helper pour trouver un personnage par ID
    fun findCharacterById(id: String): Character? {
        return allCharacters.find { it.id == id }
    }
    
    // Gérer le bouton back système
    BackHandler(enabled = currentRoute != "main") {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        }
    }
    
    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        // Écran principal avec Bottom Nav Bar
        composable("main") {
            MainScreen(
                onCharacterClick = { character ->
                    navController.navigate("character_profile/${character.id}")
                },
                onStartChat = { characterId ->
                    navController.navigate("chat/$characterId")
                },
                onCreateCharacter = {
                    navController.navigate("create_character")
                },
                onEditCharacter = { characterId ->
                    navController.navigate("edit_character?id=$characterId")
                }
            )
        }
        
        // Profil de personnage
        composable(
            route = "character_profile/{characterId}",
            arguments = listOf(navArgument("characterId") { type = NavType.StringType })
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getString("characterId")
            val character = characterId?.let { findCharacterById(it) }
            
            if (character != null) {
                CharacterProfileScreen(
                    character = character,
                    hasSavedConversation = viewModel.hasSavedConversation(character.id),
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onStartChat = { loadSaved ->
                        viewModel.selectCharacter(character, loadSaved)
                        navController.navigate("chat/${character.id}")
                    },
                    onEditClick = {
                        navController.navigate("edit_character?id=${character.id}")
                    }
                )
            } else {
                // Personnage non trouvé, retour à l'écran principal
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }
        
        // Chat
        composable(
            route = "chat/{characterId}",
            arguments = listOf(navArgument("characterId") { type = NavType.StringType })
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getString("characterId")
            val selectedCharacter = viewModel.selectedCharacter.value
            
            if (selectedCharacter != null && selectedCharacter.id == characterId) {
                ChatScreen(
                    viewModel = viewModel,
                    character = selectedCharacter,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            } else {
                // Recharger le personnage si nécessaire (prédéfini OU custom)
                val character = characterId?.let { findCharacterById(it) }
                if (character != null) {
                    LaunchedEffect(Unit) {
                        viewModel.selectCharacter(character, true)
                    }
                    ChatScreen(
                        viewModel = viewModel,
                        character = character,
                        onBackClick = {
                            navController.popBackStack()
                        }
                    )
                } else {
                    // Personnage non trouvé
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                    }
                }
            }
        }
        
        // Création de personnage
        composable("create_character") {
            CreateCharacterScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onCharacterCreated = {
                    // Retour à l'écran principal (onglet Explorer)
                    navController.popBackStack()
                }
            )
        }
        
        // Édition de personnage
        composable(
            route = "edit_character?id={characterId}",
            arguments = listOf(navArgument("characterId") { 
                type = NavType.StringType
                nullable = true
            })
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getString("characterId")
            
            EditCharacterScreen(
                characterId = characterId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onCharacterSaved = {
                    navController.popBackStack()
                }
            )
        }
        
        // Settings (ancien écran conservé pour compatibilité)
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                },
                onAdminTagsClick = {
                    navController.navigate("admin_tags")
                }
            )
        }
        
        // User Profile (ancien écran conservé)
        composable("user_profile") {
            UserProfileScreen(
                currentProfile = viewModel.userProfile.value,
                onBackClick = {
                    navController.popBackStack()
                },
                onSaveProfile = { profile ->
                    viewModel.saveUserProfile(profile)
                    navController.popBackStack()
                }
            )
        }
        
        // Admin Tags (ancien écran)
        composable("admin_tags") {
            AdminTagsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
