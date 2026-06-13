package dev.vodnixir.delirium.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "delirium_prefs")

class PreferencesRepository(private val context: Context) {

    val myName: Flow<String?> = context.dataStore.data.map { it[KEY_MY_NAME] }

    suspend fun myNameOnce(): String? = myName.first()

    suspend fun setMyName(name: String) {
        context.dataStore.edit { prefs -> prefs[KEY_MY_NAME] = name }
    }

    /** My avatar url, cached locally for instant display in the profile. */
    val myAvatarUrl: Flow<String?> = context.dataStore.data.map { it[KEY_MY_AVATAR] }

    suspend fun setMyAvatarUrl(url: String) {
        context.dataStore.edit { prefs -> prefs[KEY_MY_AVATAR] = url }
    }

    /** Selected UI theme key (see AppTheme). Null until the user picks one. */
    val themeKey: Flow<String?> = context.dataStore.data.map { it[KEY_THEME] }

    suspend fun setThemeKey(key: String) {
        context.dataStore.edit { prefs -> prefs[KEY_THEME] = key }
    }

    // --- Daily photo reminder ---

    val reminderEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_REMINDER_ENABLED] ?: false }

    suspend fun reminderEnabledOnce(): Boolean = reminderEnabled.first()

    suspend fun setReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_REMINDER_ENABLED] = enabled }
    }

    /** Reminder time as minutes-of-day (e.g. 19:00 = 1140). Default 19:00. */
    val reminderMinutesOfDay: Flow<Int> =
        context.dataStore.data.map { it[KEY_REMINDER_TIME] ?: DEFAULT_REMINDER_TIME }

    suspend fun reminderMinutesOnce(): Int = reminderMinutesOfDay.first()

    suspend fun setReminderMinutesOfDay(minutes: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_REMINDER_TIME] = minutes }
    }

    /** Epoch-day of the last photo I sent; used to skip the reminder if I posted. */
    suspend fun lastPostDayOnce(): Long = context.dataStore.data.first()[KEY_LAST_POST_DAY] ?: 0L

    suspend fun markPostedToday() {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_POST_DAY] = System.currentTimeMillis() / 86_400_000L
        }
    }

    /** Id of the photo currently cached for a connection's widget, if any. */
    suspend fun cachedPhotoId(connectionId: String): String? =
        context.dataStore.data.first()[cachedPhotoKey(connectionId)]

    suspend fun setCachedPhotoId(connectionId: String, photoId: String) {
        context.dataStore.edit { prefs -> prefs[cachedPhotoKey(connectionId)] = photoId }
    }

    private fun cachedPhotoKey(connectionId: String) =
        stringPreferencesKey("widget_photo_$connectionId")

    private companion object {
        const val DEFAULT_REMINDER_TIME = 19 * 60 // 19:00
        val KEY_MY_NAME = stringPreferencesKey("my_name")
        val KEY_MY_AVATAR = stringPreferencesKey("my_avatar")
        val KEY_THEME = stringPreferencesKey("app_theme")
        val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val KEY_REMINDER_TIME = intPreferencesKey("reminder_time")
        val KEY_LAST_POST_DAY = longPreferencesKey("last_post_day")
    }
}
