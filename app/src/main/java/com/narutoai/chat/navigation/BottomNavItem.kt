package com.narutoai.chat.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Explorer : BottomNavItem(
        route = "explorer",
        title = "Explorer",
        icon = Icons.Default.Explore
    )
    
    object Chat : BottomNavItem(
        route = "chat",
        title = "Chat",
        icon = Icons.Default.ChatBubble
    )
    
    object Create : BottomNavItem(
        route = "create",
        title = "Créer",
        icon = Icons.Default.Add
    )
    
    object Admin : BottomNavItem(
        route = "admin",
        title = "Admin",
        icon = Icons.Default.Settings
    )
    
    companion object {
        val items = listOf(Explorer, Chat, Create, Admin)
    }
}
