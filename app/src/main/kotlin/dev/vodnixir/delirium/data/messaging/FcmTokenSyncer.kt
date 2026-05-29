package dev.vodnixir.delirium.data.messaging

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class FcmTokenSyncer(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    private val users = firestore.collection("users")

    suspend fun syncCurrentToken() {
        val uid = auth.currentUser?.uid ?: return
        val token = FirebaseMessaging.getInstance().token.await()
        writeToken(uid, token)
    }

    suspend fun setToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        writeToken(uid, token)
    }

    private suspend fun writeToken(uid: String, token: String) {
        users.document(uid)
            .set(mapOf("fcmToken" to token), SetOptions.merge())
            .await()
    }
}
