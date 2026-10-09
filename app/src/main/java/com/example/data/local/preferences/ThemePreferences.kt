package com.example.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_settings")

class ThemePreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_PRIMARY_COLOR = stringPreferencesKey("primary_color")

        fun getThemeModeSync(context: Context): String {
            return context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
                .getString("theme_mode", "system") ?: "system"
        }

        fun getPrimaryColorSync(context: Context): String {
            return try {
                val defPref = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                val prefColor = defPref.getString("primary_color", null)
                if (!prefColor.isNullOrBlank()) {
                    prefColor
                } else {
                    context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
                        .getString("primary_color", "red") ?: "red"
                }
            } catch (_: Exception) {
                "red"
            }
        }

        fun setPrimaryColorSync(context: Context, colorKey: String) {
            try {
                val defPref = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                defPref.edit().putString("primary_color", colorKey).apply()
            } catch (_: Exception) {}
            try {
                context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("primary_color", colorKey)
                    .apply()
            } catch (_: Exception) {}
        }
    }

    private val safeData: Flow<Preferences> = dataStore.data
        .catch { e ->
            timber.log.Timber.e(e, "Error reading ThemePreferences DataStore")
            emit(emptyPreferences())
        }

    val themeMode: Flow<String> = safeData.map { it[KEY_THEME_MODE] ?: "system" }
    val primaryColor: Flow<String> = safeData.map { it[KEY_PRIMARY_COLOR] ?: "red" }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    suspend fun setPrimaryColor(colorKey: String) {
        dataStore.edit { it[KEY_PRIMARY_COLOR] = colorKey }
    }
}
