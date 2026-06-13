package dev.vodnixir.delirium.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vodnixir.delirium.R
import dev.vodnixir.delirium.data.auth.AuthRepository
import dev.vodnixir.delirium.data.local.PreferencesRepository
import dev.vodnixir.delirium.data.messaging.FcmTokenSyncer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val username: String = "",
    val password: String = "",
    val displayName: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

sealed interface AuthEvent {
    data object Success : AuthEvent
}

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository,
    private val fcmTokenSyncer: FcmTokenSyncer,
    private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events: Flow<AuthEvent> = _events.receiveAsFlow()

    fun onUsernameChange(value: String) =
        _state.update { it.copy(username = value.trim().lowercase().take(MAX_USERNAME)) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value.take(MAX_PASSWORD)) }

    fun onDisplayNameChange(value: String) =
        _state.update { it.copy(displayName = value.take(MAX_NAME)) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun login() = submit(requireDisplayName = false) { s ->
        authRepository.login(s.username, s.password)
    }

    fun register() = submit(requireDisplayName = true) { s ->
        authRepository.register(s.username, s.password, s.displayName.trim())
    }

    private fun submit(
        requireDisplayName: Boolean,
        action: suspend (AuthUiState) -> String,
    ) {
        val snapshot = _state.value
        val missing = snapshot.username.isBlank() ||
            snapshot.password.isBlank() ||
            (requireDisplayName && snapshot.displayName.isBlank())
        if (missing) {
            _state.update { it.copy(error = appContext.getString(R.string.auth_fill_all)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { action(snapshot) }
                .onSuccess {
                    if (requireDisplayName) {
                        preferencesRepository.setMyName(snapshot.displayName.trim())
                    }
                    runCatching { fcmTokenSyncer.syncCurrentToken() }
                    _events.send(AuthEvent.Success)
                }
                .onFailure { e ->
                    val message = when (e) {
                        is AuthRepository.NetworkException ->
                            appContext.getString(R.string.error_network)
                        else -> e.message ?: appContext.getString(R.string.common_error)
                    }
                    _state.update { it.copy(loading = false, error = message) }
                }
        }
    }

    private companion object {
        const val MAX_USERNAME = 20
        const val MAX_PASSWORD = 64
        const val MAX_NAME = 30
    }
}
