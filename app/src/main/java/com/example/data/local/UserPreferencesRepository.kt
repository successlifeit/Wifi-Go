package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wifigo_prefs")

enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val languageCode: String = "en",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val speedTestDownloadUrl: String = DEFAULT_SPEED_TEST_DOWNLOAD_URL,
    val speedTestUploadUrl: String = DEFAULT_SPEED_TEST_UPLOAD_URL
) {
    companion object {
        const val DEFAULT_SPEED_TEST_DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=8000000"
        const val DEFAULT_SPEED_TEST_UPLOAD_URL = "https://speed.cloudflare.com/__up"
    }
}

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val SPEED_TEST_DOWNLOAD_URL = stringPreferencesKey("speed_test_download_url")
        val SPEED_TEST_UPLOAD_URL = stringPreferencesKey("speed_test_upload_url")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            languageCode = prefs[Keys.LANGUAGE_CODE] ?: "en",
            themeMode = ThemeMode.fromKey(prefs[Keys.THEME_MODE]),
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            speedTestDownloadUrl = prefs[Keys.SPEED_TEST_DOWNLOAD_URL]
                ?: UserPreferences.DEFAULT_SPEED_TEST_DOWNLOAD_URL,
            speedTestUploadUrl = prefs[Keys.SPEED_TEST_UPLOAD_URL]
                ?: UserPreferences.DEFAULT_SPEED_TEST_UPLOAD_URL
        )
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setLanguageCode(code: String) {
        val normalized = if (code == "bn") "bn" else "en"
        context.dataStore.edit { it[Keys.LANGUAGE_CODE] = normalized }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.key }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setSpeedTestEndpoints(downloadUrl: String, uploadUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SPEED_TEST_DOWNLOAD_URL] = downloadUrl.trim()
            prefs[Keys.SPEED_TEST_UPLOAD_URL] = uploadUrl.trim()
        }
    }
}
