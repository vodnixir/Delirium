package dev.vodnixir.delirium.domain.model

data class Connection(
    val id: String = "",
    val members: List<String> = emptyList(),
    val names: Map<String, String> = emptyMap(),
    val createdAt: Long = 0L,
    val lastPhotoAt: Long = 0L,
    val lastPhotoUrl: String? = null,
)

data class UserDoc(
    val displayName: String = "",
    val fcmToken: String = "",
)

data class Photo(
    val id: String = "",
    val connectionId: String = "",
    val senderId: String = "",
    val storageUrl: String = "",
    val caption: String? = null,
    val createdAt: Long = 0L,
)

data class Invite(
    val code: String = "",
    val connectionId: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val used: Boolean = false,
)

/** UI-friendly view of a connection from the current user's perspective. */
data class Friend(
    val connectionId: String,
    val friendUid: String?,
    val displayName: String,
    val lastPhotoAt: Long,
    val lastPhotoUrl: String?,
)
