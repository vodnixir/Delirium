package dev.vodnixir.delirium.ui.photo

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vodnixir.delirium.data.auth.AuthRepository
import dev.vodnixir.delirium.data.chat.ChatRepository
import dev.vodnixir.delirium.data.connection.ConnectionRepository
import dev.vodnixir.delirium.data.photo.PhotoRepository
import dev.vodnixir.delirium.data.reaction.ReactionsRepository
import dev.vodnixir.delirium.domain.model.Message
import dev.vodnixir.delirium.domain.model.Photo
import dev.vodnixir.delirium.domain.model.Reaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PhotoDetailState {
    data object Loading : PhotoDetailState
    data class Ready(val photo: Photo, val names: Map<String, String>) : PhotoDetailState
    data class Error(val message: String) : PhotoDetailState
}

class PhotoDetailViewModel(
    private val photoId: String,
    private val connectionId: String,
    authRepository: AuthRepository,
    private val photoRepository: PhotoRepository,
    private val connectionRepository: ConnectionRepository,
    private val reactionsRepository: ReactionsRepository,
    private val chatRepository: ChatRepository,
) : ViewModel() {

    val currentUserId: String? = authRepository.currentUserId

    private val _state = MutableStateFlow<PhotoDetailState>(PhotoDetailState.Loading)
    val state: StateFlow<PhotoDetailState> = _state.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    val reactions: StateFlow<List<Reaction>> = reactionsRepository.observeReactions(photoId)
        .catch { t ->
            Log.e(TAG, "reactions listener failed", t)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val messages: StateFlow<List<Message>> = chatRepository.observeThread(photoId)
        .catch { t ->
            Log.e(TAG, "messages listener failed", t)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    init {
        viewModelScope.launch {
            runCatching {
                val photo = photoRepository.getPhoto(photoId)
                    ?: error("Photo not found")
                val names = connectionRepository.getConnection(connectionId)?.names.orEmpty()
                photo to names
            }.onSuccess { (photo, names) ->
                _state.value = PhotoDetailState.Ready(photo, names)
                // Mark the photo as seen (only meaningful for received photos).
                if (photo.senderId != currentUserId) {
                    runCatching { reactionsRepository.markSeen(photoId) }
                }
            }.onFailure { e ->
                Log.e(TAG, "failed to load photo", e)
                _state.value = PhotoDetailState.Error(e.message ?: "Failed to load")
            }
        }
    }

    fun react(emoji: String) {
        viewModelScope.launch {
            runCatching { reactionsRepository.react(photoId, emoji) }
                .onFailure { Log.e(TAG, "react failed", it) }
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _sending.value) return
        viewModelScope.launch {
            _sending.value = true
            runCatching { chatRepository.sendMessage(photoId, trimmed) }
                .onFailure { Log.e(TAG, "sendMessage failed", it) }
            _sending.value = false
        }
    }

    private companion object {
        const val TAG = "PhotoDetailViewModel"
    }
}
