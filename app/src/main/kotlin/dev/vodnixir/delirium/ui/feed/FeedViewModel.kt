package dev.vodnixir.delirium.ui.feed

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vodnixir.delirium.data.auth.AuthRepository
import dev.vodnixir.delirium.data.connection.ConnectionRepository
import dev.vodnixir.delirium.data.outbox.OutboxRepository
import dev.vodnixir.delirium.data.photo.PhotoRepository
import dev.vodnixir.delirium.domain.model.Photo
import dev.vodnixir.delirium.ui.camera.compressUriToJpeg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FeedViewModel(
    private val connectionId: String,
    initialFriendName: String,
    private val authRepository: AuthRepository,
    private val photoRepository: PhotoRepository,
    private val connectionRepository: ConnectionRepository,
    private val outboxRepository: OutboxRepository,
    private val appContext: Context,
) : ViewModel() {

    // Seeded from the nav arg (instant title), then resolved from Firestore. The
    // resolve matters for widget deep links, which pass no name.
    private val _friendName = MutableStateFlow(initialFriendName)
    val friendName: StateFlow<String> = _friendName.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

    init {
        if (initialFriendName.isBlank()) resolveFriendName()
    }

    private fun resolveFriendName() {
        viewModelScope.launch {
            runCatching {
                val myUid = authRepository.currentUserId
                val conn = connectionRepository.getConnection(connectionId)
                    ?: return@runCatching null
                val friendUid = conn.members.firstOrNull { it != myUid }
                conn.names[friendUid]?.takeIf { it.isNotBlank() }
            }.getOrNull()?.let { _friendName.value = it }
        }
    }

    val photos: StateFlow<List<Photo>> = photoRepository.observePhotos(connectionId)
        .catch { t ->
            Log.e(TAG, "photos listener failed", t)
            _error.value = t.message ?: "Feed failed to load"
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    fun uploadFromGallery(uri: Uri) {
        if (_uploading.value) return
        viewModelScope.launch {
            _uploading.value = true
            runCatching {
                val uid = authRepository.currentUserId ?: error("Not signed in")
                val bytes = compressUriToJpeg(appContext, uri)
                outboxRepository.enqueue(connectionId, uid, bytes)
            }.onFailure { e ->
                Log.e(TAG, "gallery upload failed", e)
                _error.value = e.message ?: "Upload failed"
            }
            _uploading.value = false
        }
    }

    fun dismissError() {
        _error.value = null
    }

    private companion object {
        const val TAG = "FeedViewModel"
    }
}
