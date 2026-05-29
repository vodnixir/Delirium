package dev.vodnixir.delirium.ui.draw

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vodnixir.delirium.data.auth.AuthRepository
import dev.vodnixir.delirium.data.photo.PhotoRepository
import dev.vodnixir.delirium.ui.camera.toCompressedJpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DrawUploadState {
    data object Idle : DrawUploadState
    data object Sending : DrawUploadState
    data class Error(val message: String) : DrawUploadState
}

sealed interface DrawEvent {
    data object Sent : DrawEvent
}

class DrawViewModel(
    private val connectionId: String,
    private val authRepository: AuthRepository,
    private val photoRepository: PhotoRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<DrawUploadState>(DrawUploadState.Idle)
    val state: StateFlow<DrawUploadState> = _state.asStateFlow()

    private val _events = Channel<DrawEvent>(Channel.BUFFERED)
    val events: Flow<DrawEvent> = _events.receiveAsFlow()

    fun send(bitmap: Bitmap) {
        if (_state.value is DrawUploadState.Sending) return
        viewModelScope.launch {
            _state.value = DrawUploadState.Sending
            runCatching {
                val uid = authRepository.currentUserId ?: error("Not signed in")
                val bytes = withContext(Dispatchers.Default) { bitmap.toCompressedJpeg() }
                photoRepository.uploadPhoto(connectionId, uid, bytes)
            }.onSuccess {
                _state.value = DrawUploadState.Idle
                _events.send(DrawEvent.Sent)
            }.onFailure { e ->
                _state.value = DrawUploadState.Error(e.message ?: "Upload failed")
            }
        }
    }

    fun clearError() {
        _state.value = DrawUploadState.Idle
    }
}
