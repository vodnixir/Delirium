package dev.vodnixir.delirium.data.connection

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import dev.vodnixir.delirium.R
import dev.vodnixir.delirium.domain.model.Connection
import dev.vodnixir.delirium.domain.model.Friend
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class ConnectionRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val appContext: Context,
) {
    private val connections = firestore.collection("connections")
    private val users = firestore.collection("users")
    private val invites = firestore.collection("invites")

    /** Real-time list of the current user's friends, newest activity first. */
    fun observeMyFriends(): Flow<List<Friend>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val reg = connections
            .whereArrayContains("members", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents
                    ?.mapNotNull { it.toConnection() }
                    ?.map { it.toFriend(uid) }
                    ?.sortedByDescending { it.lastPhotoAt }
                    .orEmpty()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun getMyConnectionIds(): List<String> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        val snap = connections.whereArrayContains("members", uid).get().await()
        return snap.documents.map { it.id }
    }

    suspend fun getConnection(connectionId: String): Connection? =
        connections.document(connectionId).get().await().toConnection()

    /** Number of connections the current user belongs to (their friend count). */
    suspend fun friendCount(): Int {
        val uid = auth.currentUser?.uid ?: return 0
        return connections.whereArrayContains("members", uid).get().await().size()
    }

    private suspend fun requireUnderFriendLimit(uid: String) {
        val count = connections.whereArrayContains("members", uid).get().await().size()
        if (count >= MAX_FRIENDS) {
            throw IllegalStateException(
                appContext.getString(R.string.friends_limit_reached, MAX_FRIENDS),
            )
        }
    }

    /** Creates a fresh connection containing only the current user. */
    suspend fun createConnection(myName: String): String {
        val uid = requireUid()
        requireUnderFriendLimit(uid)
        val connectionId = connections.document().id
        connections.document(connectionId).set(
            mapOf(
                "members" to listOf(uid),
                "names" to mapOf(uid to myName),
                "createdAt" to FieldValue.serverTimestamp(),
                "lastPhotoAt" to 0L,
            ),
        ).await()
        users.document(uid).set(
            mapOf("displayName" to myName),
            SetOptions.merge(),
        ).await()
        return connectionId
    }

    suspend fun createInvite(connectionId: String): String {
        val uid = requireUid()
        repeat(MAX_CODE_ATTEMPTS) {
            val code = generateCode()
            val docRef = invites.document(code)
            val created = runCatching {
                firestore.runTransaction { tx ->
                    val snap = tx.get(docRef)
                    if (snap.exists()) return@runTransaction false
                    tx.set(
                        docRef,
                        mapOf(
                            "connectionId" to connectionId,
                            "createdBy" to uid,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "expiresAt" to (System.currentTimeMillis() + INVITE_TTL_MS),
                            "used" to false,
                        ),
                    )
                    true
                }.await()
            }.getOrDefault(false)
            if (created) return code
        }
        error("Could not generate a unique invite code")
    }

    suspend fun joinByInvite(rawCode: String, myName: String): String {
        val uid = requireUid()
        requireUnderFriendLimit(uid)
        val code = rawCode.trim()
        val connectionId = firestore.runTransaction { tx ->
            val inviteRef = invites.document(code)
            val invite = tx.get(inviteRef)
            if (!invite.exists()) throw IllegalStateException("Invalid code")
            if (invite.getBoolean("used") == true) throw IllegalStateException("Code already used")
            val expiresAt = invite.getLong("expiresAt") ?: 0L
            if (expiresAt in 1 until System.currentTimeMillis()) {
                throw IllegalStateException("Code expired")
            }
            val connId = invite.getString("connectionId")
                ?: throw IllegalStateException("Malformed invite")
            val connRef = connections.document(connId)
            val conn = tx.get(connRef)
            if (!conn.exists()) throw IllegalStateException("Connection no longer exists")

            tx.update(connRef, "members", FieldValue.arrayUnion(uid))
            tx.update(connRef, "names.$uid", myName)
            tx.update(inviteRef, "used", true)
            connId
        }.await()
        users.document(uid).set(
            mapOf("displayName" to myName),
            SetOptions.merge(),
        ).await()
        return connectionId
    }

    private fun Connection.toFriend(myUid: String): Friend {
        val friendUid = members.firstOrNull { it != myUid }
        val name = friendUid?.let { names[it] }?.takeIf { it.isNotBlank() }
        val avatar = friendUid?.let { avatars[it] }?.takeIf { it.isNotBlank() }
        val today = System.currentTimeMillis() / DAY_MS
        val streakAlive = streakCount > 0 && today - streakDay <= 1
        return Friend(
            connectionId = id,
            friendUid = friendUid,
            displayName = name ?: appContext.getString(R.string.photo_unknown_author),
            avatarUrl = avatar,
            lastPhotoAt = lastPhotoAt,
            lastPhotoUrl = lastPhotoUrl,
            streakCount = if (streakAlive) streakCount else 0,
            streakActiveToday = today == streakDay,
        )
    }

    private fun DocumentSnapshot.toConnection(): Connection? {
        if (!exists()) return null
        @Suppress("UNCHECKED_CAST")
        val members = get("members") as? List<String> ?: return null
        @Suppress("UNCHECKED_CAST")
        val names = (get("names") as? Map<String, String>).orEmpty()
        @Suppress("UNCHECKED_CAST")
        val avatars = (get("avatars") as? Map<String, String>).orEmpty()
        val createdAt = getTimestamp("createdAt")?.toDate()?.time ?: 0L
        val lastPhotoAt = getLong("lastPhotoAt") ?: 0L
        val lastPhotoUrl = getString("lastPhotoUrl")
        val streakCount = (getLong("streakCount") ?: 0L).toInt()
        val streakDay = getLong("streakDay") ?: 0L
        return Connection(
            id = id,
            members = members,
            names = names,
            avatars = avatars,
            createdAt = createdAt,
            lastPhotoAt = lastPhotoAt,
            lastPhotoUrl = lastPhotoUrl,
            streakCount = streakCount,
            streakDay = streakDay,
        )
    }

    /** Updates my display name everywhere: my user doc and each shared connection. */
    suspend fun updateMyName(name: String) {
        val uid = requireUid()
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        users.document(uid).set(mapOf("displayName" to trimmed), SetOptions.merge()).await()
        getMyConnectionIds().forEach { connId ->
            runCatching { connections.document(connId).update("names.$uid", trimmed).await() }
        }
    }

    /** Publishes my avatar url to my user doc and each shared connection. */
    suspend fun updateMyAvatar(url: String) {
        val uid = requireUid()
        users.document(uid).set(mapOf("avatarUrl" to url), SetOptions.merge()).await()
        getMyConnectionIds().forEach { connId ->
            runCatching { connections.document(connId).update("avatars.$uid", url).await() }
        }
    }

    /** Removes the friendship for everyone; a Cloud Function wipes its photos. */
    suspend fun deleteConnection(connectionId: String) {
        connections.document(connectionId).delete().await()
    }

    private fun requireUid(): String =
        auth.currentUser?.uid ?: error("Not signed in")

    private fun generateCode(): String =
        (1..CODE_LENGTH).joinToString("") { Random.nextInt(0, 10).toString() }

    private companion object {
        const val CODE_LENGTH = 6
        const val MAX_CODE_ATTEMPTS = 10
        const val INVITE_TTL_MS = 24L * 60L * 60L * 1000L
        const val MAX_FRIENDS = 20
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
