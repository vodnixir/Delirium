package dev.vodnixir.delirium.data.reaction

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import dev.vodnixir.delirium.domain.model.Reaction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ReactionsRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    private val photos = firestore.collection("photos")

    fun observeReactions(photoId: String): Flow<List<Reaction>> = callbackFlow {
        val reg = photos.document(photoId).collection(REACTIONS)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents
                    ?.mapNotNull { it.toReaction() }
                    ?.sortedBy { it.createdAt }
                    .orEmpty()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    /** One reaction per user per photo: the doc id is the user's uid, so a new
     *  emoji replaces the previous one rather than stacking up. */
    suspend fun react(photoId: String, emoji: String) {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        photos.document(photoId).collection(REACTIONS).document(uid).set(
            mapOf(
                "fromUserId" to uid,
                "emoji" to emoji,
                "createdAt" to System.currentTimeMillis(),
            ),
        ).await()
    }

    /** Records that the current user has opened a photo (for "seen" status). */
    suspend fun markSeen(photoId: String) {
        val uid = auth.currentUser?.uid ?: return
        photos.document(photoId).collection(VIEWS).document(uid)
            .set(mapOf("seenAt" to System.currentTimeMillis())).await()
    }

    private fun DocumentSnapshot.toReaction(): Reaction? {
        val fromUserId = getString("fromUserId") ?: return null
        val emoji = getString("emoji") ?: return null
        val createdAt = getLong("createdAt") ?: 0L
        return Reaction(id, fromUserId, emoji, createdAt)
    }

    private companion object {
        const val REACTIONS = "reactions"
        const val VIEWS = "views"
    }
}
