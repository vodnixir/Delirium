package dev.vodnixir.delirium.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import dev.vodnixir.delirium.DeliriumApplication

private const val TAG = "DeliriumWidget"

object WidgetUpdater {

    /** Binds a freshly-placed widget instance to a friend's connection. */
    suspend fun bindConnection(context: Context, appWidgetId: Int, connectionId: String) {
        Log.d(TAG, "bindConnection: appWidgetId=$appWidgetId connectionId=$connectionId")
        // Persist the binding first so the connection survives even if the Glance
        // lookup below fails on a just-placed widget.
        WidgetBindings.setConnection(context, appWidgetId, connectionId)
        runCatching {
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            Log.d(TAG, "bindConnection: resolved glanceId=$glanceId, writing state")
            applyState(context, glanceId, connectionId)
        }.onFailure { Log.e(TAG, "bindConnection: update failed", it) }
    }

    /**
     * Re-renders every widget bound to [connectionId]. Writes the connection's
     * current cached photo into each widget's Glance state, which is what the
     * composable reads, then triggers a recomposition.
     */
    suspend fun refreshConnection(context: Context, connectionId: String) {
        val manager = GlanceAppWidgetManager(context)
        val ids = manager.getGlanceIds(DeliriumGlanceWidget::class.java)
        Log.d(TAG, "refreshConnection: connectionId=$connectionId glanceIds.size=${ids.size}")
        ids.forEach { id ->
            val appWidgetId = manager.getAppWidgetId(id)
            val bound = WidgetBindings.getConnection(context, appWidgetId)
            Log.d(TAG, "refreshConnection: id=$id appWidgetId=$appWidgetId bound=$bound")
            if (bound == connectionId) {
                applyState(context, id, connectionId)
            }
        }
    }

    /** Re-renders all Delirium widgets from their persisted bindings. */
    suspend fun refreshAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(DeliriumGlanceWidget::class.java).forEach { id ->
            val appWidgetId = manager.getAppWidgetId(id)
            applyState(context, id, WidgetBindings.getConnection(context, appWidgetId))
        }
    }

    /**
     * Pushes the connection id and its cached photo path into a widget's Glance
     * state and recomposes it. The widget reads both from `currentState`, so this
     * is the single mechanism that makes a widget repaint after a change.
     */
    private suspend fun applyState(context: Context, glanceId: GlanceId, connectionId: String?) {
        val path = connectionId?.let {
            (context.applicationContext as DeliriumApplication).container.photoCache.pathFor(it)
        }
        Log.d(TAG, "applyState: glanceId=$glanceId connectionId=$connectionId path=$path")
        updateAppWidgetState(context, glanceId) { prefs ->
            if (connectionId != null) {
                prefs[DeliriumGlanceWidget.KEY_CONNECTION_ID] = connectionId
            } else {
                prefs.remove(DeliriumGlanceWidget.KEY_CONNECTION_ID)
            }
            if (path != null) {
                prefs[DeliriumGlanceWidget.KEY_PHOTO_PATH] = path
            } else {
                prefs.remove(DeliriumGlanceWidget.KEY_PHOTO_PATH)
            }
        }
        DeliriumGlanceWidget().update(context, glanceId)
    }
}
