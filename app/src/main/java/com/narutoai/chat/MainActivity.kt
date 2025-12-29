package com.narutoai.chat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.narutoai.chat.ui.NarutoAIChatApp
import com.narutoai.chat.ui.theme.NarutoAIChatTheme
import com.narutoai.chat.utils.NotificationHelper
import com.narutoai.chat.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    
    // Launcher pour demander permission notifications
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            android.util.Log.d("MainActivity", "✅ Permission notifications accordée")
        } else {
            android.util.Log.w("MainActivity", "⚠️ Permission notifications refusée")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Créer canal de notifications
        NotificationHelper.createNotificationChannel(this)
        android.util.Log.d("MainActivity", "📢 Canal de notifications créé")
        
        // Demander permission notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                android.util.Log.d("MainActivity", "📢 Demande permission notifications")
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                android.util.Log.d("MainActivity", "✅ Permission notifications déjà accordée")
            }
        }
        
        enableEdgeToEdge()
        setContent {
            NarutoAIChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: ChatViewModel = viewModel()
                    NarutoAIChatApp(viewModel = viewModel)
                }
            }
        }
    }
}
