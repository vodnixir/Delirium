package dev.vodnixir.delirium.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.vodnixir.delirium.DeliriumApplication
import dev.vodnixir.delirium.R
import dev.vodnixir.delirium.messaging.AppNotifications

/**
 * Fires the daily "take a photo" reminder — but only when it is enabled and the
 * user has not already posted today (the smart-skip).
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = (applicationContext as DeliriumApplication).container.preferencesRepository
        if (prefs.reminderEnabledOnce()) {
            val today = System.currentTimeMillis() / 86_400_000L
            if (prefs.lastPostDayOnce() != today) {
                AppNotifications.showReminder(
                    applicationContext,
                    applicationContext.getString(R.string.notif_reminder_body),
                )
            }
        }
        return Result.success()
    }
}
