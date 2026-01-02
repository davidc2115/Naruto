package com.narutoai.chat.utils

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.narutoai.chat.data.CustomCharacterEntity
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object CharacterExporter {
    private val gson = Gson()
    
    /**
     * Exporte un personnage en JSON
     */
    fun exportCharacter(character: CustomCharacterEntity): String {
        return gson.toJson(character)
    }
    
    /**
     * Exporte plusieurs personnages en JSON
     */
    fun exportCharacters(characters: List<CustomCharacterEntity>): String {
        return gson.toJson(characters)
    }
    
    /**
     * Importe un personnage depuis JSON
     */
    fun importCharacter(json: String): CustomCharacterEntity? {
        return try {
            gson.fromJson(json, CustomCharacterEntity::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Importe plusieurs personnages depuis JSON
     */
    fun importCharacters(json: String): List<CustomCharacterEntity>? {
        return try {
            val type = object : TypeToken<List<CustomCharacterEntity>>() {}.type
            gson.fromJson<List<CustomCharacterEntity>>(json, type)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Sauvegarde un personnage dans un fichier
     */
    fun saveToFile(context: Context, uri: Uri, character: CustomCharacterEntity): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(exportCharacter(character))
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Charge un personnage depuis un fichier
     */
    fun loadFromFile(context: Context, uri: Uri): CustomCharacterEntity? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                InputStreamReader(inputStream).use { reader ->
                    val json = reader.readText()
                    importCharacter(json)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
