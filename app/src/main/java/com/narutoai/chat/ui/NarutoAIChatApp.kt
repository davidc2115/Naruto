package com.narutoai.chat.ui

import androidx.compose.runtime.*
import com.narutoai.chat.models.Character
import com.narutoai.chat.ui.screens.CharacterProfileScreen
import com.narutoai.chat.ui.screens.CharacterSelectionScreen
import com.narutoai.chat.ui.screens.ChatScreen
import com.narutoai.chat.ui.screens.CreateCharacterScreen
import com.narutoai.chat.ui.screens.CustomCharactersListScreen
import com.narutoai.chat.ui.screens.SettingsScreen
import com.narutoai.chat.ui.screens.UserProfileScreen
import com.narutoai.chat.viewmodel.ChatViewModel

sealed class Screen {
    object CHARACTER_SELECTION : Screen()
    object CHARACTER_DETAIL : Screen()
    object CHAT : Screen()
    object SETTINGS : Screen()
    object USER_PROFILE : Screen()
    object CREATE_CHARACTER : Screen()
    object CUSTOM_CHARACTERS_LIST : Screen()
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
                    currentScreen = Screen.CHARACTER_SELECTION
                },
                onCharacterCreated = {
                    currentScreen = Screen.CHARACTER_SELECTION
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
                onEditCharacter = { character ->
                    // TODO: Implémenter édition
                    // Pour l'instant, retour à la sélection
                    currentScreen = Screen.CHARACTER_SELECTION
                },
                onSelectCharacter = { character ->
                    // TODO: Convertir CustomCharacterEntity en Character et lancer chat
                    // Pour l'instant, retour à la sélection
                    currentScreen = Screen.CHARACTER_SELECTION
                }
            )
        }
    }
}
