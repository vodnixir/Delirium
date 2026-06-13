package dev.vodnixir.delirium.data.chat

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dev.vodnixir.delirium.domain.model.Message
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ChatRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    private val photos = firestore.collection("photos")

    fun observeThread(photoId: String): Flow<List<Message>> = callbackFlow {
        val reg = photos.document(photoId).collection(MESSAGES)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(THREAD_LIMIT)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toMessage() }.orEmpty()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun sendMessage(photoId: String, text: String) {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        photos.document(photoId).collection(MESSAGES).add(
            mapOf(
                "fromUserId" to uid,
                "text" to trimmed,
                "createdAt" to System.currentTimeMillis(),
            ),
        ).await()
    }

    private fun DocumentSnapshot.toMessage(): Message? {
        val fromUserId = getString("fromUserId") ?: return null
        val text = getString("text") ?: return null
        val createdAt = getLong("createdAt") ?: 0L
        return Message(id, fromUserId, text, createdAt)
    }

    private companion object {
        const val MESSAGES = "messages"
        const val THREAD_LIMIT = 200L
    }
}
