package com.narutoai.chat.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.narutoai.chat.MainActivity
import com.narutoai.chat.R

/**
 * Gestionnaire de notifications pour génération d'images/vidéos
 * v2.26.0: Notifications quand génération terminée
 */
object NotificationHelper {
    
    private const val CHANNEL_ID = "generation_channel"
    private const val CHANNEL_NAME = "Génération Images/Vidéos"
    private const val CHANNEL_DESCRIPTION = "Notifications pour génération d'images et vidéos"
    
    // IDs de notifications
    const val NOTIFICATION_ID_IMAGE = 1001
    const val NOTIFICATION_ID_VIDEO = 1002
    
    /**
     * Créer le canal de notifications (Android 8.0+)
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Afficher notification de progression
     */
    fun showProgressNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Ne peut pas être balayée
            .setProgress(0, 0, true) // Barre de progression indéterminée
            .build()
        
        with(NotificationManagerCompat.from(context)) {
            try {
                notify(notificationId, notification)
            } catch (e: SecurityException) {
                android.util.Log.e("NotificationHelper", "Permission refusée: ${e.message}")
            }
        }
    }
    
    /**
     * Afficher notification de succès
     */
    fun showSuccessNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Se ferme au clic
            .build()
        
        with(NotificationManagerCompat.from(context)) {
            try {
                notify(notificationId, notification)
            } catch (e: SecurityException) {
                android.util.Log.e("NotificationHelper", "Permission refusée: ${e.message}")
            }
        }
    }
    
    /**
     * Afficher notification d'erreur
     */
    fun showErrorNotification(
        context: Context,
        notificationId: Int,
        title: String,
        error: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(error)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        with(NotificationManagerCompat.from(context)) {
            try {
                notify(notificationId, notification)
            } catch (e: SecurityException) {
                android.util.Log.e("NotificationHelper", "Permission refusée: ${e.message}")
            }
        }
    }
    
    /**
     * Annuler une notification
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        with(NotificationManagerCompat.from(context)) {
            cancel(notificationId)
        }
    }
}
