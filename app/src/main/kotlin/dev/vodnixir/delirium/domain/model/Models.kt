package dev.vodnixir.delirium.domain.model

data class Connection(
    val id: String = "",
    val members: List<String> = emptyList(),
    val names: Map<String, String> = emptyMap(),
    val avatars: Map<String, String> = emptyMap(),
    val createdAt: Long = 0L,
    val lastPhotoAt: Long = 0L,
    val lastPhotoUrl: String? = null,
    val streakCount: Int = 0,
    val streakDay: Long = 0L,
)

data class UserDoc(
    val displayName: String = "",
    val fcmToken: String = "",
    val avatarUrl: String = "",
)

data class Photo(
    val id: String = "",
    val connectionId: String = "",
    val senderId: String = "",
    val storageUrl: String = "",
    val caption: String? = null,
    val createdAt: Long = 0L,
    /** Denormalized emoji reactions (one per reacting member) for grid snippets. */
    val reactionEmojis: List<String> = emptyList(),
    /** Uids who have opened this photo (denormalized for "seen" status). */
    val seenBy: List<String> = emptyList(),
    /** "image" or "video". Absent on old docs → treated as image. */
    val mediaType: String = MEDIA_IMAGE,
    /** For videos: a still frame shown in grids and the widget. Null for images. */
    val thumbnailUrl: String? = null,
) {
    val isVideo: Boolean get() = mediaType == MEDIA_VIDEO

    /** URL to show in a still (grid/widget): the thumbnail for video, else the photo. */
    val displayUrl: String get() = thumbnailUrl?.takeIf { it.isNotBlank() } ?: storageUrl

    companion object {
        const val MEDIA_IMAGE = "image"
        const val MEDIA_VIDEO = "video"
    }
}

data class Invite(
    val code: String = "",
    val connectionId: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val used: Boolean = false,
)

/** An emoji reaction left on a photo. Stored at photos/{photoId}/reactions/{userId}. */
data class Reaction(
    val id: String = "",
    val fromUserId: String = "",
    val emoji: String = "",
    val createdAt: Long = 0L,
)

/** A chat message in a photo's thread. Stored at photos/{photoId}/messages/{id}. */
data class Message(
    val id: String = "",
    val fromUserId: String = "",
    val text: String = "",
    val createdAt: Long = 0L,
)

/** UI-friendly view of a connection from the current user's perspective. */
data class Friend(
    val connectionId: String,
    val friendUid: String?,
    val displayName: String,
    val avatarUrl: String? = null,
    val lastPhotoAt: Long = 0L,
    val lastPhotoUrl: String? = null,
    /** Snapstreak length with this friend; 0 when expired. */
    val streakCount: Int = 0,
    /** True if today's exchange is already done (full flame, not at risk). */
    val streakActiveToday: Boolean = false,
)
