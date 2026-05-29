package dev.vodnixir.delirium.messaging

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.vodnixir.delirium.DeliriumApplication
import dev.vodnixir.delirium.widget.WidgetPhotoSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class DeliriumMessagingService : FirebaseMessagingService() {

    private val tokenScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val connectionId = message.data["connectionId"]
        val photoUrl = message.data["photoUrl"]
        val service = this
        // High-priority FCM data messages give us ~20s to do background work.
        // Block here so the OS keeps the service alive until the widget updates.
        runBlocking {
            runCatching {
                when {
                    // Preferred path: payload carries the connection and a direct URL.
                    connectionId != null && photoUrl != null ->
                        WidgetPhotoSync.syncFromUrl(service, connectionId, photoUrl)
                    // Payload only names the connection: pull its latest from Firestore.
                    connectionId != null ->
                        WidgetPhotoSync.syncConnection(service, connectionId)
                    // Legacy/unspecified payload: refresh everything we belong to.
                    else ->
                        WidgetPhotoSync.syncAll(service)
                }
            }
        }
    }

    override fun onNewToken(token: String) {
        tokenScope.launch {
            runCatching {
                val container = (application as DeliriumApplication).container
                container.fcmTokenSyncer.setToken(token)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tokenScope.cancel()
    }
}
