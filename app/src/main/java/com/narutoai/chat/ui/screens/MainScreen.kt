package com.narutoai.chat.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.narutoai.chat.models.Character
import com.narutoai.chat.navigation.BottomNavItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onCharacterClick: (Character) -> Unit,
    onStartChat: (characterId: String) -> Unit,
    onCreateCharacter: () -> Unit,
    onEditCharacter: (String?) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomNavItem.items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                // Éviter les duplications dans la pile
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // Éviter plusieurs copies de la même destination
                                launchSingleTop = true
                                // Restaurer l'état lors de la re-sélection
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Explorer.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            // Explorer
            composable(BottomNavItem.Explorer.route) {
                ExplorerScreen(
                    onCharacterClick = onCharacterClick
                )
            }
            
            // Chat History
            composable(BottomNavItem.Chat.route) {
                ChatHistoryScreen(
                    onConversationClick = onStartChat
                )
            }
            
            // Create
            composable(BottomNavItem.Create.route) {
                // Rediriger vers l'écran de création
                LaunchedEffect(Unit) {
                    onCreateCharacter()
                }
            }
            
            // Admin
            composable(BottomNavItem.Admin.route) {
                AdminScreen()
            }
        }
    }
}
