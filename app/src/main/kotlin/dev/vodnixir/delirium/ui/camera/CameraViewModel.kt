package dev.vodnixir.delirium.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vodnixir.delirium.data.auth.AuthRepository
import dev.vodnixir.delirium.data.photo.PhotoRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

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
    private val photoRepository: PhotoRepository,
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
                photoRepository.uploadPhoto(connectionId, uid, jpegBytes)
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
