package dev.vodnixir.delirium

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vodnixir.delirium.ui.nav.AppNav
import dev.vodnixir.delirium.ui.theme.AppTheme
import dev.vodnixir.delirium.ui.theme.DeliriumTheme
import dev.vodnixir.delirium.widget.DeliriumGlanceWidget
import dev.vodnixir.delirium.widget.WidgetWorkScheduler

class MainActivity : ComponentActivity() {
    // Connection a widget tap asked us to open; consumed once by AppNav. Updated by
    // onNewIntent so a warm launch from another widget reroutes too.
    private var pendingConnectionId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingConnectionId = intent?.connectionIdExtra()
        val preferences = (application as DeliriumApplication).container.preferencesRepository
        setContent {
            val themeKey by preferences.themeKey.collectAsStateWithLifecycle(initialValue = null)
            val theme = AppTheme.fromKey(themeKey)
            val view = LocalView.current
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !theme.isDark
                controller.isAppearanceLightNavigationBars = !theme.isDark
            }
            DeliriumTheme(theme = theme) {
                NotificationPermissionEffect()
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNav(
                        deepLinkConnectionId = pendingConnectionId,
                        onDeepLinkConsumed = { pendingConnectionId = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.connectionIdExtra()?.let { pendingConnectionId = it }
    }

    private fun Intent.connectionIdExtra(): String? =
        getStringExtra(DeliriumGlanceWidget.EXTRA_CONNECTION_ID)?.takeIf { it.isNotBlank() }

    @Composable
    private fun NotificationPermissionEffect() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { }
        LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    override fun onStart() {
        super.onStart()
        // Catch any photos missed while the app was backgrounded, without waiting
        // for the periodic worker or an FCM push.
        WidgetWorkScheduler.refreshAllNow(applicationContext)
    }
}
