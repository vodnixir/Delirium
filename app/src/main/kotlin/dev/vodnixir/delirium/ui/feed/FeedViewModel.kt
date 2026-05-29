package dev.vodnixir.delirium.ui.feed

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vodnixir.delirium.data.auth.AuthRepository
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
    private val authRepository: AuthRepository,
    private val photoRepository: PhotoRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

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
                photoRepository.uploadPhoto(connectionId, uid, bytes)
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
