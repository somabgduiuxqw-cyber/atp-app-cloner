package com.atp.cloner.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.atp.cloner.ui.theme.AppThemeMode
import com.atp.cloner.ui.theme.DarkThemeVariant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "atp_cloner_preferences")

class ThemePreferencesRepository(private val context: Context) {

    companion object {
        val KEY_THEME_MODE = stringPreferencesKey("app_theme_mode")
        val KEY_DARK_VARIANT = stringPreferencesKey("app_dark_variant")
    }

    val themeModeFlow: Flow<AppThemeMode> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_THEME_MODE] ?: AppThemeMode.SYSTEM.name
        try {
            AppThemeMode.valueOf(name)
        } catch (e: Exception) {
            AppThemeMode.SYSTEM
        }
    }

    val darkVariantFlow: Flow<DarkThemeVariant> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_DARK_VARIANT] ?: DarkThemeVariant.DARK_BLUE.name
        try {
            DarkThemeVariant.valueOf(name)
        } catch (e: Exception) {
            DarkThemeVariant.DARK_BLUE
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun setDarkVariant(variant: DarkThemeVariant) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DARK_VARIANT] = variant.name
        }
    }
}
