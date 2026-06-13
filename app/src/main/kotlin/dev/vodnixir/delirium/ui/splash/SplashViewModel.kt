package dev.vodnixir.delirium.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vodnixir.delirium.data.auth.AuthRepository
import dev.vodnixir.delirium.data.messaging.FcmTokenSyncer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SplashTarget { Pending, Login, Main }

class SplashViewModel(
    private val authRepository: AuthRepository,
    private val fcmTokenSyncer: FcmTokenSyncer,
) : ViewModel() {

    private val _target = MutableStateFlow(SplashTarget.Pending)
    val target: StateFlow<SplashTarget> = _target.asStateFlow()

    init {
        viewModelScope.launch {
            _target.value = if (authRepository.isSignedIn) {
                // Token sync is best-effort; do not block splash on it.
                runCatching { fcmTokenSyncer.syncCurrentToken() }
                SplashTarget.Main
            } else {
                SplashTarget.Login
            }
        }
    }
}
