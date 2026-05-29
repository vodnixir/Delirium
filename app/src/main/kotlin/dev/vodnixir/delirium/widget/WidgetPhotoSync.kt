package dev.vodnixir.delirium.widget

import android.content.Context
import android.util.Log
import dev.vodnixir.delirium.DeliriumApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

private const val TAG = "DeliriumWidget"

/**
 * Downloads the latest photo for a connection, caches it, and re-renders any
 * widget bound to that connection. Shared by the FCM push path, the WorkManager
 * pull fallback, and the widget config screen's initial fill.
 */
object WidgetPhotoSync {

    /** Pulls the newest photo sent by the friend (not by current user) for [connectionId]. */
    suspend fun syncConnection(context: Context, connectionId: String): Boolean {
        val container = (context.applicationContext as DeliriumApplication).container
        val myUid = container.authRepository.currentUserId
        Log.d(TAG, "syncConnection: connectionId=$connectionId myUid=$myUid")
        if (myUid == null) {
            Log.w(TAG, "syncConnection: no current user, skipping")
            return false
        }
        val photo = container.photoRepository.latestPhotoExcludingSender(connectionId, myUid)
        Log.d(TAG, "syncConnection: latestPhotoExcludingSender -> $photo")
        if (photo == null) return false
        val alreadyCached = container.preferencesRepository.cachedPhotoId(connectionId) == photo.id &&
            container.photoCache.pathFor(connectionId) != null
        if (alreadyCached) {
            Log.d(TAG, "syncConnection: already cached, refreshing widget anyway")
            WidgetUpdater.refreshConnection(context, connectionId)
            return false
        }
        val bytes = download(photo.storageUrl)
        container.photoCache.saveFromBytes(connectionId, bytes)
        container.preferencesRepository.setCachedPhotoId(connectionId, photo.id)
        Log.d(TAG, "syncConnection: cached new photo ${photo.id}, refreshing widget")
        WidgetUpdater.refreshConnection(context, connectionId)
        return true
    }

    /** Caches a photo we already have a direct URL for (FCM push payload). */
    suspend fun syncFromUrl(context: Context, connectionId: String, photoUrl: String) {
        val container = (context.applicationContext as DeliriumApplication).container
        val bytes = download(photoUrl)
        container.photoCache.saveFromBytes(connectionId, bytes)
        WidgetUpdater.refreshConnection(context, connectionId)
    }

    /** Refreshes every connection the current user belongs to. */
    suspend fun syncAll(context: Context) {
        val container = (context.applicationContext as DeliriumApplication).container
        container.connectionRepository.getMyConnectionIds().forEach { connectionId ->
            runCatching { syncConnection(context, connectionId) }
        }
    }

    private suspend fun download(url: String): ByteArray = withContext(Dispatchers.IO) {
        URL(url).openStream().use { it.readBytes() }
    }
}
