package com.narutoai.chat.ui

import androidx.compose.runtime.*
import com.narutoai.chat.models.Character
import com.narutoai.chat.ui.screens.CharacterProfileScreen
import com.narutoai.chat.ui.screens.CharacterSelectionScreen
import com.narutoai.chat.ui.screens.ChatScreen
import com.narutoai.chat.ui.screens.SettingsScreen
import com.narutoai.chat.viewmodel.ChatViewModel

enum class Screen {
    CHARACTER_SELECTION,
    CHARACTER_DETAIL,
    CHAT,
    SETTINGS
}

@Composable
fun NarutoAIChatApp(viewModel: ChatViewModel) {
    var currentScreen by remember { mutableStateOf(Screen.CHARACTER_SELECTION) }
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
                viewModel = viewModel
            )
        }
        
        Screen.CHARACTER_DETAIL -> {
            characterForDetail?.let { character ->
                CharacterProfileScreen(
                    character = character,
                    onBackClick = {
                        currentScreen = Screen.CHARACTER_SELECTION
                    },
                    onStartChat = {
                        viewModel.selectCharacter(character)
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
    }
}
