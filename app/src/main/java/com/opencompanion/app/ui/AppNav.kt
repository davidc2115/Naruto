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
import com.opencompanion.app.ui.characterdetail.CharacterDetailScreen
import com.opencompanion.app.ui.characterdetail.CharacterDetailViewModel
import com.opencompanion.app.ui.charactereditor.CharacterEditorScreen
import com.opencompanion.app.ui.charactereditor.CharacterEditorViewModel
import com.opencompanion.app.ui.characterlist.CharacterListScreen
import com.opencompanion.app.ui.characterlist.CharacterListViewModel
import com.opencompanion.app.ui.persona.PersonaManagerScreen
import com.opencompanion.app.ui.persona.PersonaManagerViewModel
import com.opencompanion.app.ui.settings.SettingsScreen
import com.opencompanion.app.ui.settings.SettingsViewModel

private object Routes {
    const val CHARACTERS = "characters"
    const val CHARACTER_DETAIL = "character/{characterId}"
    const val EDITOR = "editor?characterId={characterId}"
    const val CHAT = "chat/{characterId}"
    const val SETTINGS = "settings"
    const val BROWSE_IMPORT = "browse_import"
    const val PERSONAS = "personas"

    fun characterDetail(characterId: Long) = "character/$characterId"
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
                onOpenCharacterDetail = { id -> navController.navigate(Routes.characterDetail(id)) },
                onOpenChat = { id -> navController.navigate(Routes.chat(id)) },
                onCreateCharacter = { navController.navigate(Routes.editor()) },
                onEditCharacter = { id -> navController.navigate(Routes.editor(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onBrowseImport = { navController.navigate(Routes.BROWSE_IMPORT) },
                onOpenPersonas = { navController.navigate(Routes.PERSONAS) },
            )
        }

        composable(
            Routes.CHARACTER_DETAIL,
            arguments = listOf(navArgument("characterId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getLong("characterId") ?: return@composable
            val vm: CharacterDetailViewModel = viewModel(
                factory = AppViewModelFactory {
                    CharacterDetailViewModel(characterId, app.characterRepository, app.settingsRepository)
                },
            )
            CharacterDetailScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenChat = { id -> navController.navigate(Routes.chat(id)) },
                onEditCharacter = { id -> navController.navigate(Routes.editor(id)) },
            )
        }

        composable(Routes.PERSONAS) {
            val vm: PersonaManagerViewModel = viewModel(
                factory = AppViewModelFactory { PersonaManagerViewModel(app.characterRepository) },
            )
            PersonaManagerScreen(viewModel = vm, onBack = { navController.popBackStack() })
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
                        app.cloudEngineBridge,
                        app.settingsRepository,
                    )
                },
            )
            ChatScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPersonas = { navController.navigate(Routes.PERSONAS) },
            )
        }

        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(
                factory = AppViewModelFactory {
                    SettingsViewModel(
                        app.settingsRepository,
                        app.modelManager,
                        app.inferenceEngine,
                        app.nanoBridge,
                        app.cloudEngineBridge,
                    )
                },
            )
            SettingsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
    }
}
