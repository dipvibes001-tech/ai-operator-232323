package com.aioperator.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ai_operator_prefs")

class PreferencesRepository(private val context: Context) {

    private val apiKeyKey = stringPreferencesKey("anthropic_api_key")

    val apiKeyFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[apiKeyKey] ?: ""
    }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { prefs ->
            prefs[apiKeyKey] = key.trim()
        }
    }
}