package com.example.shelflife_ai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferences(private val context: Context) {

    companion object {
        val TUTORIAL_SEEN_KEY = booleanPreferencesKey("tutorial_seen")
    }

    val isTutorialSeen: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[TUTORIAL_SEEN_KEY] ?: false
    }

    suspend fun setTutorialSeen(seen: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[TUTORIAL_SEEN_KEY] = seen
        }
    }
}
