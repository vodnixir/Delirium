package dev.vodnixir.delirium

import android.app.Application
import dev.vodnixir.delirium.data.AppContainer
import dev.vodnixir.delirium.data.DefaultAppContainer
import dev.vodnixir.delirium.widget.WidgetWorkScheduler

class DeliriumApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        // Pull fallback so widgets stay fresh even when FCM pushes are dropped.
        WidgetWorkScheduler.schedulePeriodic(this)
    }
}
