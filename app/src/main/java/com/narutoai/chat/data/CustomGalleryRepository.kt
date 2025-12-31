package com.narutoai.chat.data

import kotlinx.coroutines.flow.Flow

/**
 * Repository pour gérer les images de galerie personnalisées
 */
class CustomGalleryRepository(private val dao: CustomGalleryImageDao) {
    
    /**
     * Récupère toutes les images d'un personnage
     */
    fun getImagesForCharacter(characterId: String): Flow<List<CustomGalleryImage>> {
        return dao.getImagesForCharacter(characterId)
    }
    
    /**
     * Récupère les images NSFW d'un personnage
     */
    fun getNSFWImagesForCharacter(characterId: String): Flow<List<CustomGalleryImage>> {
        return dao.getNSFWImagesForCharacter(characterId)
    }
    
    /**
     * Récupère les images SFW d'un personnage
     */
    fun getSFWImagesForCharacter(characterId: String): Flow<List<CustomGalleryImage>> {
        return dao.getSFWImagesForCharacter(characterId)
    }
    
    /**
     * Ajoute une image à la galerie d'un personnage
     */
    suspend fun addImageToGallery(
        characterId: String,
        imagePath: String,
        isNSFW: Boolean
    ): Long {
        val image = CustomGalleryImage(
            characterId = characterId,
            imagePath = imagePath,
            isNSFW = isNSFW
        )
        return dao.insertImage(image)
    }
    
    /**
     * Supprime une image de la galerie
     */
    suspend fun deleteImage(image: CustomGalleryImage) {
        dao.deleteImage(image)
    }
    
    /**
     * Supprime une image par son ID
     */
    suspend fun deleteImageById(imageId: Long) {
        dao.deleteImageById(imageId)
    }
    
    /**
     * Supprime toutes les images d'un personnage
     */
    suspend fun deleteAllImagesForCharacter(characterId: String) {
        dao.deleteAllImagesForCharacter(characterId)
    }
    
    /**
     * Compte le nombre d'images d'un personnage
     */
    suspend fun getImageCount(characterId: String): Int {
        return dao.getImageCount(characterId)
    }
}
