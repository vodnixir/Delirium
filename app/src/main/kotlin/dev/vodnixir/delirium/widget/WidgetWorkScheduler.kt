package dev.vodnixir.delirium.widget

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object WidgetWorkScheduler {

    private const val PERIODIC_NAME = "delirium_widget_refresh_periodic"
    private const val ONESHOT_PREFIX = "delirium_widget_refresh_once_"
    private const val ONESHOT_ALL_NAME = "delirium_widget_refresh_all_once"

    /**
     * Keeps all widgets fresh even when FCM pushes are dropped. 15 minutes is the
     * WorkManager floor for periodic work; prompt updates come from FCM, this is
     * only the missed-push safety net.
     */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
        )
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Pulls the latest photo for every connection right now (e.g. on app open). */
    fun refreshAllNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setConstraints(networkConstraints())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONESHOT_ALL_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /** Fetches the latest photo for one connection right now (e.g. after config). */
    fun refreshNow(context: Context, connectionId: String) {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setConstraints(networkConstraints())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(WidgetRefreshWorker.KEY_CONNECTION_ID to connectionId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONESHOT_PREFIX + connectionId,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun networkConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
