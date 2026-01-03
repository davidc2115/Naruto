package com.narutoai.chat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.narutoai.chat.models.Character
import com.narutoai.chat.ui.screens.*
import com.narutoai.chat.utils.CharacterConverter
import com.narutoai.chat.viewmodel.ChatViewModel
import com.narutoai.chat.viewmodel.CreateCharacterViewModel

/**
 * Onglets de navigation principaux
 */
enum class MainTab {
    EXPLORER,
    CHATS,
    SETTINGS
}

/**
 * Écrans de navigation secondaires
 */
sealed class Screen {
    object MAIN : Screen() // Écran principal avec bottom nav
    object CHARACTER_DETAIL : Screen()
    object CHAT : Screen()
    object USER_PROFILE : Screen()
    data class CREATE_CHARACTER(val editCharacterId: String? = null) : Screen()
    object ADMIN_TAGS : Screen()
}

@Composable
fun NarutoAIChatApp(viewModel: ChatViewModel) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.MAIN) }
    var currentTab by remember { mutableStateOf(MainTab.EXPLORER) }
    var characterForDetail by remember { mutableStateOf<Character?>(null) }
    val selectedCharacter = viewModel.selectedCharacter.value
    
    when (val screen = currentScreen) {
        Screen.MAIN -> {
            MainScreenWithBottomNav(
                currentTab = currentTab,
                onTabSelected = { currentTab = it },
                chatViewModel = viewModel,
                onCharacterSelected = { character ->
                    characterForDetail = character
                    currentScreen = Screen.CHARACTER_DETAIL
                },
                onCreateCharacter = {
                    currentScreen = Screen.CREATE_CHARACTER()
                },
                onEditCustomCharacter = { entity ->
                    currentScreen = Screen.CREATE_CHARACTER(editCharacterId = entity.id)
                },
                onSettingsNavigate = { destination ->
                    when (destination) {
                        "user_profile" -> currentScreen = Screen.USER_PROFILE
                        "admin_tags" -> currentScreen = Screen.ADMIN_TAGS
                    }
                }
            )
        }
        
        Screen.CHARACTER_DETAIL -> {
            characterForDetail?.let { character ->
                CharacterProfileScreen(
                    character = character,
                    hasSavedConversation = viewModel.hasSavedConversation(character.id),
                    onBackClick = {
                        currentScreen = Screen.MAIN
                    },
                    onStartChat = { loadSaved ->
                        viewModel.selectCharacter(character, loadSaved)
                        currentScreen = Screen.CHAT
                    }
                )
            }
        }
        
        Screen.CHAT -> {
            if (selectedCharacter != null) {
                ChatScreen(
                    viewModel = viewModel,
                    character = selectedCharacter,
                    onBackClick = {
                        currentScreen = Screen.CHARACTER_DETAIL
                        characterForDetail = selectedCharacter
                    }
                )
            } else {
                currentScreen = Screen.MAIN
            }
        }
        
        Screen.USER_PROFILE -> {
            UserProfileScreen(
                currentProfile = viewModel.userProfile.value,
                onBackClick = {
                    currentScreen = Screen.MAIN
                    currentTab = MainTab.SETTINGS
                },
                onSaveProfile = { profile ->
                    viewModel.saveUserProfile(profile)
                    currentScreen = Screen.MAIN
                    currentTab = MainTab.SETTINGS
                }
            )
        }
        
        is Screen.CREATE_CHARACTER -> {
            CreateCharacterScreen(
                onNavigateBack = {
                    currentScreen = Screen.MAIN
                    currentTab = MainTab.EXPLORER
                },
                onCharacterCreated = {
                    // Rediriger vers l'onglet Explorer pour voir le personnage créé
                    currentScreen = Screen.MAIN
                    currentTab = MainTab.EXPLORER
                },
                editCharacterId = screen.editCharacterId,
                // Utiliser une clé basée sur l'ID pour éviter la réutilisation du ViewModel
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    key = screen.editCharacterId ?: "new_character"
                )
            )
        }
        
        Screen.ADMIN_TAGS -> {
            AdminTagsScreen(
                onNavigateBack = {
                    currentScreen = Screen.MAIN
                    currentTab = MainTab.SETTINGS
                }
            )
        }
    }
}

/**
 * Écran principal avec bottom navigation bar
 */
@Composable
fun MainScreenWithBottomNav(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    chatViewModel: ChatViewModel,
    onCharacterSelected: (Character) -> Unit,
    onCreateCharacter: () -> Unit,
    onEditCustomCharacter: (com.narutoai.chat.data.CustomCharacterEntity) -> Unit,
    onSettingsNavigate: (String) -> Unit
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Explore, contentDescription = "Explorer") },
                    label = { Text("Explorer") },
                    selected = currentTab == MainTab.EXPLORER,
                    onClick = { onTabSelected(MainTab.EXPLORER) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chats") },
                    label = { Text("Chats") },
                    selected = currentTab == MainTab.CHATS,
                    onClick = { onTabSelected(MainTab.CHATS) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Configuration") },
                    label = { Text("Config") },
                    selected = currentTab == MainTab.SETTINGS,
                    onClick = { onTabSelected(MainTab.SETTINGS) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentTab) {
                MainTab.EXPLORER -> {
                    ExplorerScreen(
                        onCharacterSelected = onCharacterSelected,
                        onCreateCharacter = onCreateCharacter,
                        onEditCustomCharacter = onEditCustomCharacter
                    )
                }
                
                MainTab.CHATS -> {
                    ChatsScreen(
                        viewModel = chatViewModel,
                        onChatSelected = onCharacterSelected
                    )
                }
                
                MainTab.SETTINGS -> {
                    SettingsScreen(
                        viewModel = chatViewModel,
                        onBackClick = null, // Pas de retour, on est dans le bottom nav
                        onAdminTagsClick = {
                            onSettingsNavigate("admin_tags")
                        },
                        onUserProfileClick = {
                            onSettingsNavigate("user_profile")
                        }
                    )
                }
            }
        }
    }
}
