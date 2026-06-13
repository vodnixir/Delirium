package dev.vodnixir.delirium

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dev.vodnixir.delirium.crash.CrashReporter
import dev.vodnixir.delirium.data.AppContainer
import dev.vodnixir.delirium.data.DefaultAppContainer
import dev.vodnixir.delirium.messaging.AppNotifications
import dev.vodnixir.delirium.reminder.ReminderScheduler
import dev.vodnixir.delirium.widget.WidgetWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DeliriumApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        container = DefaultAppContainer(this)
        AppNotifications.ensureChannels(this)
        // Pull fallback so widgets stay fresh even when FCM pushes are dropped.
        WidgetWorkScheduler.schedulePeriodic(this)
        // Re-arm uploads for any photos still queued from a previous run.
        runCatching { container.outboxRepository.resyncPending() }
        appScope.launch {
            // Ship any crashes captured in previous sessions to the admin panel.
            runCatching {
                CrashReporter.uploadPending(
                    this@DeliriumApplication,
                    FirebaseFirestore.getInstance(),
                    FirebaseAuth.getInstance(),
                )
            }
            // Restore the daily reminder schedule from saved preferences.
            val prefs = container.preferencesRepository
            ReminderScheduler.reschedule(
                this@DeliriumApplication,
                prefs.reminderEnabledOnce(),
                prefs.reminderMinutesOnce(),
            )
        }
    }
}
