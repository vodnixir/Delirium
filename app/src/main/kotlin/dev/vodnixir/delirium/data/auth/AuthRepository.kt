package dev.vodnixir.delirium.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.io.IOException

/**
 * Username / password auth. Credentials are exchanged with Cloud Functions
 * (registerUser / loginUser) which mint a Firebase custom token; we then sign
 * in with that token so the uid is stable across sessions.
 */
class AuthRepository(
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,
) {
    val currentUserId: String?
        get() = auth.currentUser?.uid

    val isSignedIn: Boolean
        get() = auth.currentUser != null

    suspend fun register(username: String, password: String, displayName: String): String =
        signInWithToken(
            callForToken(
                "registerUser",
                mapOf(
                    "username" to username,
                    "password" to password,
                    "displayName" to displayName,
                ),
            ),
        )

    suspend fun login(username: String, password: String): String =
        signInWithToken(
            callForToken(
                "loginUser",
                mapOf("username" to username, "password" to password),
            ),
        )

    fun signOut() {
        auth.signOut()
    }

    private suspend fun signInWithToken(token: String): String {
        val result = auth.signInWithCustomToken(token).await()
        return result.user?.uid ?: error("Custom-token sign-in returned no user")
    }

    private suspend fun callForToken(name: String, data: Map<String, Any>): String {
        val response = callWithRetry(name, data)
        @Suppress("UNCHECKED_CAST")
        val payload = response.getData() as? Map<String, Any?>
            ?: error("Malformed response from $name")
        return payload["token"] as? String ?: error("No token in response from $name")
    }

    /**
     * Calls a callable function, retrying transient transport failures. Firebase
     * surfaces a dropped/timed-out connection as a [FirebaseFunctionsException]
     * with code INTERNAL (literal message "INTERNAL"), which is indistinguishable
     * from a real server crash and useless to the user. We retry those a few
     * times, then translate the final failure into a clear network message.
     * Explicit business errors (wrong password, name taken) are NOT retried.
     */
    private suspend fun callWithRetry(name: String, data: Map<String, Any>): HttpsCallableResult {
        var lastError: Exception? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return functions.getHttpsCallable(name).call(data).await()
            } catch (e: FirebaseFunctionsException) {
                if (e.code !in TRANSIENT_CODES) throw e
                lastError = e
            } catch (e: IOException) {
                lastError = e
            }
            if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_DELAY_MS * (attempt + 1))
        }
        throw NetworkException(lastError)
    }

    /**
     * Thrown when the server is unreachable after retries. The UI maps this type to
     * a localized message (see AuthViewModel); this text is just a dev fallback.
     */
    class NetworkException(cause: Throwable?) : Exception("Server unreachable", cause)

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 700L
        val TRANSIENT_CODES = setOf(
            FirebaseFunctionsException.Code.INTERNAL,
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
            FirebaseFunctionsException.Code.ABORTED,
        )
    }
}
