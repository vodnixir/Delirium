package dev.vodnixir.delirium.data.photo

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import dev.vodnixir.delirium.domain.model.Photo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class PhotoRepository(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) {
    private val photos = firestore.collection("photos")
    private val connections = firestore.collection("connections")

    fun observePhotos(connectionId: String): Flow<List<Photo>> = callbackFlow {
        val reg = photos
            .whereEqualTo("connectionId", connectionId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(FEED_LIMIT)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toPhoto() }.orEmpty()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun latestPhoto(connectionId: String): Photo? {
        val snap = photos
            .whereEqualTo("connectionId", connectionId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
        return snap.documents.firstOrNull()?.toPhoto()
    }

    /** Returns the most recent photo in [connectionId] NOT sent by [myUid]. */
    suspend fun latestPhotoExcludingSender(connectionId: String, myUid: String): Photo? {
        val snap = photos
            .whereEqualTo("connectionId", connectionId)
            .whereNotEqualTo("senderId", myUid)
            .orderBy("senderId")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
        return snap.documents.firstOrNull()?.toPhoto()
    }

    suspend fun uploadPhoto(
        connectionId: String,
        senderId: String,
        jpegBytes: ByteArray,
        caption: String? = null,
    ): String {
        val photoId = photos.document().id
        val path = "photos/$connectionId/$photoId.jpg"
        val storageRef = storage.reference.child(path)
        val metadata = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build()
        storageRef.putBytes(jpegBytes, metadata).await()
        val downloadUrl = storageRef.downloadUrl.await().toString()
        photos.document(photoId).set(
            buildMap {
                put("connectionId", connectionId)
                put("senderId", senderId)
                put("storageUrl", downloadUrl)
                put("createdAt", FieldValue.serverTimestamp())
                if (caption != null) put("caption", caption)
            },
        ).await()
        connections.document(connectionId).set(
            mapOf(
                "lastPhotoAt" to System.currentTimeMillis(),
                "lastPhotoUrl" to downloadUrl,
            ),
            SetOptions.merge(),
        ).await()
        return photoId
    }

    private fun DocumentSnapshot.toPhoto(): Photo? {
        val connectionId = getString("connectionId") ?: return null
        val senderId = getString("senderId") ?: return null
        val storageUrl = getString("storageUrl") ?: return null
        val caption = getString("caption")
        val createdAt = getTimestamp("createdAt")?.toDate()?.time ?: 0L
        return Photo(id, connectionId, senderId, storageUrl, caption, createdAt)
    }

    private companion object {
        const val FEED_LIMIT = 50L
    }
}
