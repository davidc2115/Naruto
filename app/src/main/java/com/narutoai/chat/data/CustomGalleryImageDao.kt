package com.narutoai.chat.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO pour gérer les images de galerie personnalisées
 */
@Dao
interface CustomGalleryImageDao {
    
    /**
     * Récupère toutes les images d'un personnage
     */
    @Query("SELECT * FROM custom_gallery_images WHERE characterId = :characterId ORDER BY createdAt DESC")
    fun getImagesForCharacter(characterId: String): Flow<List<CustomGalleryImage>>
    
    /**
     * Récupère les images NSFW d'un personnage
     */
    @Query("SELECT * FROM custom_gallery_images WHERE characterId = :characterId AND isNSFW = 1 ORDER BY createdAt DESC")
    fun getNSFWImagesForCharacter(characterId: String): Flow<List<CustomGalleryImage>>
    
    /**
     * Récupère les images SFW d'un personnage
     */
    @Query("SELECT * FROM custom_gallery_images WHERE characterId = :characterId AND isNSFW = 0 ORDER BY createdAt DESC")
    fun getSFWImagesForCharacter(characterId: String): Flow<List<CustomGalleryImage>>
    
    /**
     * Ajoute une image à la galerie
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: CustomGalleryImage): Long
    
    /**
     * Supprime une image de la galerie
     */
    @Delete
    suspend fun deleteImage(image: CustomGalleryImage)
    
    /**
     * Supprime une image par son ID
     */
    @Query("DELETE FROM custom_gallery_images WHERE id = :imageId")
    suspend fun deleteImageById(imageId: Long)
    
    /**
     * Supprime toutes les images d'un personnage
     */
    @Query("DELETE FROM custom_gallery_images WHERE characterId = :characterId")
    suspend fun deleteAllImagesForCharacter(characterId: String)
    
    /**
     * Compte le nombre d'images d'un personnage
     */
    @Query("SELECT COUNT(*) FROM custom_gallery_images WHERE characterId = :characterId")
    suspend fun getImageCount(characterId: String): Int
}
