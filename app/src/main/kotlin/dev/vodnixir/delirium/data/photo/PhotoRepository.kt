package dev.vodnixir.delirium.data.photo

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import dev.vodnixir.delirium.data.local.PreferencesRepository
import dev.vodnixir.delirium.domain.model.Photo
import dev.vodnixir.delirium.domain.model.Photo.Companion.MEDIA_IMAGE
import dev.vodnixir.delirium.domain.model.Photo.Companion.MEDIA_VIDEO
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class PhotoRepository(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val preferencesRepository: PreferencesRepository,
) {
    private val photos = firestore.collection("photos")
    private val connections = firestore.collection("connections")

    /**
     * Merged feed across every connection the user belongs to. Firestore caps an
     * `in` filter at [MAX_FEED_CONNECTIONS] values, which the 20-friend limit keeps
     * us under. Emits empty immediately when there are no connections.
     */
    fun observeFeed(connectionIds: List<String>): Flow<List<Photo>> = callbackFlow {
        if (connectionIds.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val reg = photos
            .whereIn("connectionId", connectionIds.take(MAX_FEED_CONNECTIONS))
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

    suspend fun getPhoto(photoId: String): Photo? =
        photos.document(photoId).get().await().toPhoto()

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
        // Records that I posted today so the daily reminder can skip me.
        runCatching { preferencesRepository.markPostedToday() }
        return photoId
    }

    /**
     * Uploads a video clip plus a still thumbnail, then writes the photo doc with
     * mediaType=video. The connection's lastPhotoUrl points at the thumbnail so
     * the (bitmap-only) widget keeps working for video posts.
     */
    suspend fun uploadVideo(
        connectionId: String,
        senderId: String,
        mp4Bytes: ByteArray,
        thumbnailJpeg: ByteArray,
        caption: String? = null,
    ): String {
        val photoId = photos.document().id
        val videoRef = storage.reference.child("photos/$connectionId/$photoId.mp4")
        videoRef.putBytes(
            mp4Bytes,
            StorageMetadata.Builder().setContentType("video/mp4").build(),
        ).await()
        val videoUrl = videoRef.downloadUrl.await().toString()

        val thumbRef = storage.reference.child("photos/$connectionId/${photoId}_thumb.jpg")
        thumbRef.putBytes(
            thumbnailJpeg,
            StorageMetadata.Builder().setContentType("image/jpeg").build(),
        ).await()
        val thumbUrl = thumbRef.downloadUrl.await().toString()

        photos.document(photoId).set(
            buildMap {
                put("connectionId", connectionId)
                put("senderId", senderId)
                put("storageUrl", videoUrl)
                put("mediaType", MEDIA_VIDEO)
                put("thumbnailUrl", thumbUrl)
                put("createdAt", FieldValue.serverTimestamp())
                if (caption != null) put("caption", caption)
            },
        ).await()
        connections.document(connectionId).set(
            mapOf(
                "lastPhotoAt" to System.currentTimeMillis(),
                "lastPhotoUrl" to thumbUrl,
            ),
            SetOptions.merge(),
        ).await()
        runCatching { preferencesRepository.markPostedToday() }
        return photoId
    }

    /** Uploads a profile picture to avatars/{uid}/avatar.jpg and returns its URL. */
    suspend fun uploadAvatar(uid: String, jpegBytes: ByteArray): String {
        val storageRef = storage.reference.child("avatars/$uid/avatar.jpg")
        val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
        storageRef.putBytes(jpegBytes, metadata).await()
        return storageRef.downloadUrl.await().toString()
    }

    private fun DocumentSnapshot.toPhoto(): Photo? {
        val connectionId = getString("connectionId") ?: return null
        val senderId = getString("senderId") ?: return null
        val storageUrl = getString("storageUrl") ?: return null
        val caption = getString("caption")
        val createdAt = getTimestamp("createdAt")?.toDate()?.time ?: 0L
        @Suppress("UNCHECKED_CAST")
        val reactionEmojis = (get("reactionEmojis") as? List<String>).orEmpty()
        @Suppress("UNCHECKED_CAST")
        val seenBy = (get("seenBy") as? List<String>).orEmpty()
        val mediaType = getString("mediaType") ?: MEDIA_IMAGE
        val thumbnailUrl = getString("thumbnailUrl")
        return Photo(
            id, connectionId, senderId, storageUrl, caption, createdAt,
            reactionEmojis, seenBy, mediaType, thumbnailUrl,
        )
    }

    private companion object {
        const val FEED_LIMIT = 50L
        const val MAX_FEED_CONNECTIONS = 30
    }
}
