package com.coincide.drive.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_preferences")

/**
 * Manager class wrapping Jetpack DataStore Preferences for sync settings and execution states.
 */
class SyncPreferencesManager(private val context: Context) {

    private object PreferencesKeys {
        val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val KEY_ALLOW_MOBILE_DATA = booleanPreferencesKey("allow_mobile_data")
        val KEY_BLOCK_ROAMING = booleanPreferencesKey("block_roaming")
        val KEY_ONLY_WHEN_CHARGING = booleanPreferencesKey("only_when_charging")
        val KEY_SYNC_INTERVAL_MINUTES = longPreferencesKey("sync_interval_minutes")
        val KEY_LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        val KEY_IS_SYNC_IN_PROGRESS = booleanPreferencesKey("is_sync_in_progress")
        val KEY_IS_PRO_UNLOCKED = booleanPreferencesKey("is_pro_unlocked")
        val KEY_UNLOCKED_EMAIL = stringPreferencesKey("unlocked_email")
        val KEY_UNLOCKED_CODE = stringPreferencesKey("unlocked_code")
    }

    val syncPreferencesFlow: Flow<SyncPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            SyncPreferences(
                wifiOnly = preferences[PreferencesKeys.KEY_WIFI_ONLY] ?: true,
                allowMobileData = preferences[PreferencesKeys.KEY_ALLOW_MOBILE_DATA] ?: false,
                blockRoaming = preferences[PreferencesKeys.KEY_BLOCK_ROAMING] ?: true,
                onlyWhenCharging = preferences[PreferencesKeys.KEY_ONLY_WHEN_CHARGING] ?: false,
                syncIntervalMinutes = preferences[PreferencesKeys.KEY_SYNC_INTERVAL_MINUTES] ?: 60L,
                lastSyncTimestamp = preferences[PreferencesKeys.KEY_LAST_SYNC_TIMESTAMP] ?: 0L,
                isSyncInProgress = preferences[PreferencesKeys.KEY_IS_SYNC_IN_PROGRESS] ?: false,
                isProUnlocked = preferences[PreferencesKeys.KEY_IS_PRO_UNLOCKED] ?: false,
                unlockedEmail = preferences[PreferencesKeys.KEY_UNLOCKED_EMAIL] ?: "",
                unlockedCode = preferences[PreferencesKeys.KEY_UNLOCKED_CODE] ?: ""
            )
        }

    suspend fun getSyncPreferences(): SyncPreferences = syncPreferencesFlow.first()

    suspend fun setWifiOnly(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_WIFI_ONLY] = enabled
        }
    }

    suspend fun setAllowMobileData(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_ALLOW_MOBILE_DATA] = enabled
        }
    }

    suspend fun setBlockRoaming(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_BLOCK_ROAMING] = enabled
        }
    }

    suspend fun setOnlyWhenCharging(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_ONLY_WHEN_CHARGING] = enabled
        }
    }

    suspend fun setSyncIntervalMinutes(minutes: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_SYNC_INTERVAL_MINUTES] = minutes
        }
    }

    suspend fun setLastSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_LAST_SYNC_TIMESTAMP] = timestamp
        }
    }

    suspend fun setSyncInProgress(inProgress: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_IS_SYNC_IN_PROGRESS] = inProgress
        }
    }

    suspend fun setProUnlocked(unlocked: Boolean, email: String, code: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_IS_PRO_UNLOCKED] = unlocked
            preferences[PreferencesKeys.KEY_UNLOCKED_EMAIL] = email
            preferences[PreferencesKeys.KEY_UNLOCKED_CODE] = code
        }
    }

    suspend fun updatePreferences(preferences: SyncPreferences) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.KEY_WIFI_ONLY] = preferences.wifiOnly
            prefs[PreferencesKeys.KEY_ALLOW_MOBILE_DATA] = preferences.allowMobileData
            prefs[PreferencesKeys.KEY_BLOCK_ROAMING] = preferences.blockRoaming
            prefs[PreferencesKeys.KEY_ONLY_WHEN_CHARGING] = preferences.onlyWhenCharging
            prefs[PreferencesKeys.KEY_SYNC_INTERVAL_MINUTES] = preferences.syncIntervalMinutes
            prefs[PreferencesKeys.KEY_LAST_SYNC_TIMESTAMP] = preferences.lastSyncTimestamp
            prefs[PreferencesKeys.KEY_IS_SYNC_IN_PROGRESS] = preferences.isSyncInProgress
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: SyncPreferencesManager? = null

        fun getInstance(context: Context): SyncPreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncPreferencesManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
