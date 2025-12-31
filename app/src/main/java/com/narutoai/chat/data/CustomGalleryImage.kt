package com.narutoai.chat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity pour stocker les images de galerie personnalisées
 * Permet d'ajouter des images générées à la galerie locale d'un personnage
 */
@Entity(tableName = "custom_gallery_images")
data class CustomGalleryImage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * ID du personnage (character.id)
     */
    val characterId: String,
    
    /**
     * Chemin local de l'image
     */
    val imagePath: String,
    
    /**
     * Est-ce une image NSFW ?
     */
    val isNSFW: Boolean = false,
    
    /**
     * Timestamp de création
     */
    val createdAt: Long = System.currentTimeMillis()
)
