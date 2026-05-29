package dev.vodnixir.delirium.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.vodnixir.delirium.MainActivity
import dev.vodnixir.delirium.R
import java.io.File

private const val TAG = "DeliriumWidget"

class DeliriumGlanceWidget : GlanceAppWidget() {

    // Render at the host-reported size so the photo fills any resize the user picks.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Read display inputs from Glance per-widget state INSIDE provideContent.
        // The provideGlance body runs once per session; only the content lambda
        // recomposes. Sourcing connection + photo from currentState means an
        // external update() (config bind, FCM/worker refresh) actually repaints —
        // computing them in the body would freeze the widget on its first render.
        provideContent {
            val prefs = currentState<Preferences>()
            val connectionId = prefs[KEY_CONNECTION_ID]
            val path = prefs[KEY_PHOTO_PATH]
            Log.d(TAG, "provideGlance: id=$id connectionId=$connectionId path=$path")
            val bitmap = remember(path) {
                path?.takeIf { it.isNotBlank() && File(it).exists() }
                    ?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
            }
            val placeholder = if (connectionId == null) {
                context.getString(R.string.widget_tap_configure)
            } else {
                context.getString(R.string.app_name)
            }
            WidgetContent(bitmap, placeholder)
        }
    }

    @Composable
    private fun WidgetContent(bitmap: Bitmap?, placeholder: String) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(24.dp)
                .background(Color(0xFF1A1024))
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            when {
                bitmap != null -> Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(24.dp),
                )

                else -> Text(
                    text = placeholder,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = GlanceModifier.padding(16.dp),
                )
            }
        }
    }

    companion object {
        val KEY_CONNECTION_ID = stringPreferencesKey("widget_connection_id")
        val KEY_PHOTO_PATH = stringPreferencesKey("widget_photo_path")
    }
}
