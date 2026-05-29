package dev.vodnixir.delirium.widget

import android.content.Context

/**
 * Persistent `appWidgetId -> connectionId` map, kept independently of Glance
 * state. Writing the binding here first (before touching Glance) guarantees the
 * widget can resolve its friend on the very next render, even if the Glance
 * state write or `getGlanceIdBy` lookup misbehaves for a freshly-placed widget.
 */
object WidgetBindings {
    private const val PREFS = "delirium_widget_bindings"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setConnection(context: Context, appWidgetId: Int, connectionId: String) {
        prefs(context).edit().putString(appWidgetId.toString(), connectionId).apply()
    }

    fun getConnection(context: Context, appWidgetId: Int): String? =
        prefs(context).getString(appWidgetId.toString(), null)

    fun remove(context: Context, appWidgetId: Int) {
        prefs(context).edit().remove(appWidgetId.toString()).apply()
    }
}
