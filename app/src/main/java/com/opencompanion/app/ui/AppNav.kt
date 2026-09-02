package com.opencompanion.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.opencompanion.app.OpenCompanionApplication
import com.opencompanion.app.ui.browse.CharacterBrowserScreen
import com.opencompanion.app.ui.browse.CharacterBrowserViewModel
import com.opencompanion.app.ui.chat.ChatScreen
import com.opencompanion.app.ui.chat.ChatViewModel
import com.opencompanion.app.ui.charactereditor.CharacterEditorScreen
import com.opencompanion.app.ui.charactereditor.CharacterEditorViewModel
import com.opencompanion.app.ui.characterlist.CharacterListScreen
import com.opencompanion.app.ui.characterlist.CharacterListViewModel
import com.opencompanion.app.ui.settings.SettingsScreen
import com.opencompanion.app.ui.settings.SettingsViewModel

private object Routes {
    const val CHARACTERS = "characters"
    const val EDITOR = "editor?characterId={characterId}"
    const val CHAT = "chat/{characterId}"
    const val SETTINGS = "settings"
    const val BROWSE_IMPORT = "browse_import"

    fun editor(characterId: Long? = null) = "editor?characterId=${characterId ?: -1}"
    fun chat(characterId: Long) = "chat/$characterId"
}

@Composable
fun AppNav(app: OpenCompanionApplication) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.CHARACTERS) {
        composable(Routes.CHARACTERS) {
            val vm: CharacterListViewModel = viewModel(
                factory = AppViewModelFactory {
                    CharacterListViewModel(app.characterRepository, app.characterImportManager)
                },
            )
            CharacterListScreen(
                viewModel = vm,
                onOpenChat = { id -> navController.navigate(Routes.chat(id)) },
                onCreateCharacter = { navController.navigate(Routes.editor()) },
                onEditCharacter = { id -> navController.navigate(Routes.editor(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onBrowseImport = { navController.navigate(Routes.BROWSE_IMPORT) },
            )
        }

        composable(Routes.BROWSE_IMPORT) {
            val vm: CharacterBrowserViewModel = viewModel(
                factory = AppViewModelFactory { CharacterBrowserViewModel(app.characterImportManager) },
            )
            CharacterBrowserScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(
            Routes.EDITOR,
            arguments = listOf(navArgument("characterId") { type = NavType.LongType; defaultValue = -1L }),
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getLong("characterId")?.takeIf { it > 0 }
            val vm: CharacterEditorViewModel = viewModel(
                factory = AppViewModelFactory { CharacterEditorViewModel(app.characterRepository, characterId) },
            )
            CharacterEditorScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(
            Routes.CHAT,
            arguments = listOf(navArgument("characterId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getLong("characterId") ?: return@composable
            val vm: ChatViewModel = viewModel(
                factory = AppViewModelFactory {
                    ChatViewModel(
                        characterId,
                        app.characterRepository,
                        app.inferenceEngine,
                        app.nanoBridge,
                        app.settingsRepository,
                    )
                },
            )
            ChatScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(
                factory = AppViewModelFactory {
                    SettingsViewModel(app.settingsRepository, app.modelManager, app.inferenceEngine, app.nanoBridge)
                },
            )
            SettingsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
    }
}
