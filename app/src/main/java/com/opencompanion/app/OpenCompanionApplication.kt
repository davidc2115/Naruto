package com.opencompanion.app

import android.app.Application
import com.opencompanion.app.charactercard.CharacterImportManager
import com.opencompanion.app.data.AppDatabase
import com.opencompanion.app.data.CharacterRepository
import com.opencompanion.app.data.SettingsRepository
import com.opencompanion.app.engine.InferenceEngine
import com.opencompanion.app.engine.ModelManager
import com.opencompanion.app.engine.NanoBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Composition manuelle des dépendances (pas de framework d'injection : le
 * graphe est petit et rester explicite facilite la lecture pour un projet
 * qui se veut simple à auditer/forker).
 */
class OpenCompanionApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { AppDatabase.getInstance(this) }
    val characterRepository by lazy { CharacterRepository(database.characterDao(), database.chatDao()) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val modelManager by lazy { ModelManager(this) }
    val inferenceEngine by lazy { InferenceEngine(this) }
    val nanoBridge by lazy { NanoBridge(this) }
    val characterImportManager by lazy { CharacterImportManager(this, characterRepository) }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            characterRepository.seedSampleCharactersIfEmpty()
        }
    }
}
