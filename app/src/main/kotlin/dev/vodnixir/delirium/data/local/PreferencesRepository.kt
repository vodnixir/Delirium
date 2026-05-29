package dev.vodnixir.delirium.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
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

    /** Id of the photo currently cached for a connection's widget, if any. */
    suspend fun cachedPhotoId(connectionId: String): String? =
        context.dataStore.data.first()[cachedPhotoKey(connectionId)]

    suspend fun setCachedPhotoId(connectionId: String, photoId: String) {
        context.dataStore.edit { prefs -> prefs[cachedPhotoKey(connectionId)] = photoId }
    }

    private fun cachedPhotoKey(connectionId: String) =
        stringPreferencesKey("widget_photo_$connectionId")

    private companion object {
        val KEY_MY_NAME = stringPreferencesKey("my_name")
    }
}
