package com.narutoai.chat.ui

import androidx.compose.runtime.*
import com.narutoai.chat.models.Character
import com.narutoai.chat.ui.screens.AdminTagsScreen
import com.narutoai.chat.ui.screens.CharacterProfileScreen
import com.narutoai.chat.ui.screens.CharacterSelectionScreen
import com.narutoai.chat.ui.screens.ChatScreen
import com.narutoai.chat.ui.screens.CreateCharacterScreen
import com.narutoai.chat.ui.screens.CustomCharactersListScreen
import com.narutoai.chat.ui.screens.SettingsScreen
import com.narutoai.chat.ui.screens.UserProfileScreen
import com.narutoai.chat.utils.CharacterConverter
import com.narutoai.chat.viewmodel.ChatViewModel

sealed class Screen {
    object CHARACTER_SELECTION : Screen()
    object CHARACTER_DETAIL : Screen()
    object CHAT : Screen()
    object SETTINGS : Screen()
    object USER_PROFILE : Screen()
    object CREATE_CHARACTER : Screen()
    object CUSTOM_CHARACTERS_LIST : Screen()
    object ADMIN_TAGS : Screen()
}

@Composable
fun NarutoAIChatApp(viewModel: ChatViewModel) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.CHARACTER_SELECTION) }
    var characterForDetail by remember { mutableStateOf<Character?>(null) }
    val selectedCharacter = viewModel.selectedCharacter.value
    
    when (currentScreen) {
        Screen.CHARACTER_SELECTION -> {
            CharacterSelectionScreen(
                onCharacterSelected = { character ->
                    characterForDetail = character
                    currentScreen = Screen.CHARACTER_DETAIL
                },
                onSettingsClick = {
                    currentScreen = Screen.SETTINGS
                },
                onUserProfileClick = {
                    currentScreen = Screen.USER_PROFILE
                },
                onCreateCharacterClick = {
                    currentScreen = Screen.CREATE_CHARACTER
                },
                onCustomCharactersClick = {
                    currentScreen = Screen.CUSTOM_CHARACTERS_LIST
                },
                viewModel = viewModel
            )
        }
        
        Screen.CHARACTER_DETAIL -> {
            characterForDetail?.let { character ->
                CharacterProfileScreen(
                    character = character,
                    hasSavedConversation = viewModel.hasSavedConversation(character.id),
                    onBackClick = {
                        currentScreen = Screen.CHARACTER_SELECTION
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
                currentScreen = Screen.CHARACTER_SELECTION
            }
        }
        
        Screen.SETTINGS -> {
            SettingsScreen(
                viewModel = viewModel,
                onBackClick = {
                    currentScreen = Screen.CHARACTER_SELECTION
                },
                onAdminTagsClick = {
                    currentScreen = Screen.ADMIN_TAGS
                }
            )
        }
        
        Screen.USER_PROFILE -> {
            UserProfileScreen(
                currentProfile = viewModel.userProfile.value,
                onBackClick = {
                    currentScreen = Screen.CHARACTER_SELECTION
                },
                onSaveProfile = { profile ->
                    viewModel.saveUserProfile(profile)
                    currentScreen = Screen.CHARACTER_SELECTION
                }
            )
        }
        
        Screen.CREATE_CHARACTER -> {
            CreateCharacterScreen(
                onNavigateBack = {
                    currentScreen = Screen.CUSTOM_CHARACTERS_LIST
                },
                onCharacterCreated = {
                    // Rediriger vers la liste pour voir le personnage créé
                    currentScreen = Screen.CUSTOM_CHARACTERS_LIST
                }
            )
        }
        
        Screen.CUSTOM_CHARACTERS_LIST -> {
            CustomCharactersListScreen(
                onNavigateBack = {
                    currentScreen = Screen.CHARACTER_SELECTION
                },
                onCreateNew = {
                    currentScreen = Screen.CREATE_CHARACTER
                },
                onEditCharacter = { entity ->
                    // TODO: Écran d'édition
                    android.util.Log.d("NarutoApp", "Edit character: ${entity.name}")
                    // Pour l'instant, afficher profil
                    val character = CharacterConverter.toCharacter(entity)
                    characterForDetail = character
                    currentScreen = Screen.CHARACTER_DETAIL
                },
                onSelectCharacter = { entity ->
                    // Convertir et lancer chat
                    val character = CharacterConverter.toCharacter(entity)
                    android.util.Log.d("NarutoApp", "Select custom character: ${character.name}")
                    characterForDetail = character
                    currentScreen = Screen.CHARACTER_DETAIL
                }
            )
        }
        
        Screen.ADMIN_TAGS -> {
            AdminTagsScreen(
                onNavigateBack = {
                    currentScreen = Screen.SETTINGS
                }
            )
        }
    }
}
