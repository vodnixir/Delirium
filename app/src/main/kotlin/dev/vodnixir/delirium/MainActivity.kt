package dev.vodnixir.delirium

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dev.vodnixir.delirium.ui.nav.AppNav
import dev.vodnixir.delirium.ui.theme.DeliriumTheme
import dev.vodnixir.delirium.widget.WidgetWorkScheduler

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DeliriumTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNav()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Catch any photos missed while the app was backgrounded, without waiting
        // for the periodic worker or an FCM push.
        WidgetWorkScheduler.refreshAllNow(applicationContext)
    }
}
