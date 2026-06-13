package dev.vodnixir.delirium.ui.profile

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vodnixir.delirium.R
import dev.vodnixir.delirium.data.auth.AuthRepository
import dev.vodnixir.delirium.data.connection.ConnectionRepository
import dev.vodnixir.delirium.data.local.PreferencesRepository
import dev.vodnixir.delirium.data.photo.PhotoRepository
import dev.vodnixir.delirium.ui.camera.compressUriToJpeg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the profile hub: display name, avatar and sign-out. */
class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val connectionRepository: ConnectionRepository,
    private val photoRepository: PhotoRepository,
    private val preferencesRepository: PreferencesRepository,
    private val appContext: Context,
) : ViewModel() {

    private val defaultName: String get() = appContext.getString(R.string.profile_title)

    val displayName: StateFlow<String> = preferencesRepository.myName
        .map { it?.takeIf(String::isNotBlank) ?: defaultName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), defaultName)

    val avatarUrl: StateFlow<String?> = preferencesRepository.myAvatarUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun saveName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                preferencesRepository.setMyName(trimmed)
                connectionRepository.updateMyName(trimmed)
            }.onFailure { Log.e(TAG, "saveName failed", it) }
        }
    }

    fun setAvatar(uri: Uri) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            runCatching {
                val uid = authRepository.currentUserId ?: error("Not signed in")
                val bytes = compressUriToJpeg(appContext, uri)
                val url = photoRepository.uploadAvatar(uid, bytes)
                preferencesRepository.setMyAvatarUrl(url)
                connectionRepository.updateMyAvatar(url)
            }.onFailure { Log.e(TAG, "setAvatar failed", it) }
            _busy.value = false
        }
    }

    fun signOut() = authRepository.signOut()

    private companion object {
        const val TAG = "ProfileViewModel"
    }
}
