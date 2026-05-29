package dev.vodnixir.delirium.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Pull fallback for widget freshness: when an FCM push is missed (Doze, killed
 * app, throttled sender), this re-checks Firestore for newer photos. Triggered
 * periodically and as a one-shot right after a widget is configured.
 */
class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val connectionId = inputData.getString(KEY_CONNECTION_ID)
        return runCatching {
            if (connectionId != null) {
                WidgetPhotoSync.syncConnection(applicationContext, connectionId)
            } else {
                WidgetPhotoSync.syncAll(applicationContext)
            }
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        const val KEY_CONNECTION_ID = "connection_id"
    }
}
