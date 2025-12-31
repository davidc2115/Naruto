package com.narutoai.chat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Extension DataStore partagée pour toute l'application
 * Utilisée pour stocker les clés API et autres préférences
 */
val Context.apiKeysDataStore: DataStore<Preferences> by preferencesDataStore(name = "api_keys")
