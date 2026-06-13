package dev.vodnixir.delirium.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vodnixir.delirium.data.auth.AuthRepository
import dev.vodnixir.delirium.data.outbox.OutboxRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface CameraUploadState {
    data object Idle : CameraUploadState
    data object Sending : CameraUploadState
    data class Error(val message: String) : CameraUploadState
}

sealed interface CameraEvent {
    data object PhotoSent : CameraEvent
}

class CameraViewModel(
    private val connectionId: String,
    private val authRepository: AuthRepository,
    private val outboxRepository: OutboxRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<CameraUploadState>(CameraUploadState.Idle)
    val state: StateFlow<CameraUploadState> = _state.asStateFlow()

    private val _events = Channel<CameraEvent>(Channel.BUFFERED)
    val events: Flow<CameraEvent> = _events.receiveAsFlow()

    fun sendPhoto(jpegBytes: ByteArray) {
        if (_state.value is CameraUploadState.Sending) return
        viewModelScope.launch {
            _state.value = CameraUploadState.Sending
            runCatching {
                val uid = authRepository.currentUserId
                    ?: error("Not signed in")
                withContext(Dispatchers.IO) {
                    outboxRepository.enqueue(connectionId, uid, jpegBytes)
                }
            }.onSuccess {
                _state.value = CameraUploadState.Idle
                _events.send(CameraEvent.PhotoSent)
            }.onFailure { e ->
                _state.value = CameraUploadState.Error(e.message ?: "Upload failed")
            }
        }
    }

    /**
     * Queues a recorded clip: extracts a still thumbnail, hands both to the
     * outbox, then deletes the temp recording (the outbox keeps its own copy).
     */
    fun sendVideo(videoFile: File) {
        if (_state.value is CameraUploadState.Sending) return
        viewModelScope.launch {
            _state.value = CameraUploadState.Sending
            runCatching {
                val uid = authRepository.currentUserId ?: error("Not signed in")
                withContext(Dispatchers.IO) {
                    val mp4 = videoFile.readBytes()
                    val thumb = extractVideoThumbnailJpeg(videoFile)
                    outboxRepository.enqueueVideo(connectionId, uid, mp4, thumb)
                    videoFile.delete()
                }
            }.onSuccess {
                _state.value = CameraUploadState.Idle
                _events.send(CameraEvent.PhotoSent)
            }.onFailure { e ->
                _state.value = CameraUploadState.Error(e.message ?: "Upload failed")
            }
        }
    }

    fun clearError() {
        _state.value = CameraUploadState.Idle
    }
}
