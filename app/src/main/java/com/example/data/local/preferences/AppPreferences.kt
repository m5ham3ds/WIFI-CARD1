package com.example.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class AppPreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        val KEY_VIBRATE = booleanPreferencesKey("vibrate_on_success")
        val KEY_SOUND = booleanPreferencesKey("sound_on_success")
        val KEY_AUTO_EXPORT = booleanPreferencesKey("auto_export")
        val KEY_THREAD_COUNT = stringPreferencesKey("thread_count")
        val KEY_DEFAULT_ROUTER_ID = longPreferencesKey("default_router_id")
        val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        val KEY_ENABLE_PRELOAD = booleanPreferencesKey("enable_preload_pages")
        val KEY_IS_UNLOCKED = booleanPreferencesKey("is_unlocked")
        val KEY_FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
        val KEY_PAGE_LOAD_DELAY = longPreferencesKey("page_load_delay")
        val KEY_CARD_TEST_DELAY = longPreferencesKey("card_test_delay")
        val KEY_SCREENSHOT_DELAY = longPreferencesKey("screenshot_delay")
        val KEY_SUCCESS_ACTION = stringPreferencesKey("success_action")
        
        // Home settings state
        val KEY_LAST_CARD_PREFIX = stringPreferencesKey("last_card_prefix")
        val KEY_LAST_CARD_LENGTH = intPreferencesKey("last_card_length")
        val KEY_LAST_CARD_COUNT = intPreferencesKey("last_card_count")
        val KEY_LAST_CHARSET = stringPreferencesKey("last_charset")

        fun getLanguageSync(context: Context): String {
            return com.example.util.LocaleHelper.getPersistedLocale(context)
        }
    }

    private val safeData: Flow<Preferences> = dataStore.data
        .catch { e ->
            timber.log.Timber.e(e, "Error reading AppPreferences DataStore")
            emit(emptyPreferences())
        }

    val vibrateOnSuccess: Flow<Boolean> = safeData.map { it[KEY_VIBRATE] ?: true }
    val soundOnSuccess: Flow<Boolean> = safeData.map { it[KEY_SOUND] ?: false }
    val autoExport: Flow<Boolean> = safeData.map { it[KEY_AUTO_EXPORT] ?: false }
    val threadCount: Flow<Int> = safeData.map { prefs ->
        val v = prefs[KEY_THREAD_COUNT] ?: "1"
        v.toIntOrNull() ?: 1
    }
    val defaultRouterId: Flow<Long> = safeData.map { it[KEY_DEFAULT_ROUTER_ID] ?: 0L }
    val appLanguage: Flow<String> = safeData.map { it[KEY_APP_LANGUAGE] ?: "ar" }
    val enablePreload: Flow<Boolean> = safeData.map { it[KEY_ENABLE_PRELOAD] ?: true }
    val isUnlocked: Flow<Boolean> = safeData.map { it[KEY_IS_UNLOCKED] ?: false }
    val failedAttempts: Flow<Int> = safeData.map { it[KEY_FAILED_ATTEMPTS] ?: 0 }
    val pageLoadDelay: Flow<Long> = safeData.map { it[KEY_PAGE_LOAD_DELAY] ?: 2000L }
    val cardTestDelay: Flow<Long> = safeData.map { it[KEY_CARD_TEST_DELAY] ?: 3000L }
    val screenshotDelay: Flow<Long> = safeData.map { it[KEY_SCREENSHOT_DELAY] ?: 2000L }
    val successAction: Flow<String> = safeData.map { it[KEY_SUCCESS_ACTION] ?: "stop" }

    suspend fun setUnlocked(isUnlocked: Boolean) {
        dataStore.edit { it[KEY_IS_UNLOCKED] = isUnlocked }
    }

    suspend fun incrementFailedAttempts() {
        dataStore.edit { 
            val current = it[KEY_FAILED_ATTEMPTS] ?: 0
            it[KEY_FAILED_ATTEMPTS] = current + 1
        }
    }
    
    val lastCardPrefix: Flow<String> = safeData.map { it[KEY_LAST_CARD_PREFIX] ?: "" }
    val lastCardLength: Flow<Int> = safeData.map { it[KEY_LAST_CARD_LENGTH] ?: 6 }
    val lastCardCount: Flow<Int> = safeData.map { it[KEY_LAST_CARD_COUNT] ?: 10 }
    val lastCharset: Flow<String> = safeData.map { it[KEY_LAST_CHARSET] ?: "0123456789" }

    suspend fun saveHomeSettings(prefix: String, length: Int, count: Int, charset: String, routerId: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_CARD_PREFIX] = prefix
            prefs[KEY_LAST_CARD_LENGTH] = length
            prefs[KEY_LAST_CARD_COUNT] = count
            prefs[KEY_LAST_CHARSET] = charset
            if (routerId != -1L) {
                prefs[KEY_DEFAULT_ROUTER_ID] = routerId
            }
        }
    }

    suspend fun setAppLanguage(lang: String) {
        dataStore.edit { it[KEY_APP_LANGUAGE] = lang }
    }

    suspend fun setVibrateOnSuccess(enabled: Boolean) {
        dataStore.edit { it[KEY_VIBRATE] = enabled }
    }

    suspend fun setSoundOnSuccess(enabled: Boolean) {
        dataStore.edit { it[KEY_SOUND] = enabled }
    }
    
    suspend fun setPageLoadDelay(delay: Long) {
        dataStore.edit { it[KEY_PAGE_LOAD_DELAY] = delay }
    }
    
    suspend fun setCardTestDelay(delay: Long) {
        dataStore.edit { it[KEY_CARD_TEST_DELAY] = delay }
    }

    suspend fun setScreenshotDelay(delay: Long) {
        dataStore.edit { it[KEY_SCREENSHOT_DELAY] = delay }
    }

    suspend fun setEnablePreload(enabled: Boolean) {
        dataStore.edit { it[KEY_ENABLE_PRELOAD] = enabled }
    }

    suspend fun setThreadCount(count: String) {
        dataStore.edit { it[KEY_THREAD_COUNT] = count }
    }

    suspend fun setDefaultRouterId(routerId: Long) {
        dataStore.edit { it[KEY_DEFAULT_ROUTER_ID] = routerId }
    }

    suspend fun setSuccessAction(action: String) {
        dataStore.edit { it[KEY_SUCCESS_ACTION] = action }
    }
}
