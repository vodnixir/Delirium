package dev.vodnixir.delirium.ui.widget

import android.appwidget.AppWidgetManager
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import dev.vodnixir.delirium.ui.theme.DeliriumTheme
import dev.vodnixir.delirium.widget.WidgetUpdater
import dev.vodnixir.delirium.widget.WidgetWorkScheduler
import kotlinx.coroutines.launch

class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If the user backs out, the host must not add a half-configured widget.
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            DeliriumTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WidgetConfigScreen(onFriendSelected = ::bindAndFinish)
                }
            }
        }
    }

    private fun bindAndFinish(connectionId: String) {
        lifecycleScope.launch {
            runCatching {
                WidgetUpdater.bindConnection(applicationContext, appWidgetId, connectionId)
            }
            // Fetch the friend's latest photo so the widget isn't blank.
            WidgetWorkScheduler.refreshNow(applicationContext, connectionId)

            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }
}
