package dev.vodnixir.delirium.ui.friends

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vodnixir.delirium.data.connection.ConnectionRepository
import dev.vodnixir.delirium.domain.model.Friend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

class FriendsViewModel(
    connectionRepository: ConnectionRepository,
) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val friends: StateFlow<List<Friend>> = connectionRepository.observeMyFriends()
        .catch { t ->
            Log.e(TAG, "friends listener failed", t)
            _error.value = t.message ?: "Failed to load friends"
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    fun dismissError() {
        _error.value = null
    }

    private companion object {
        const val TAG = "FriendsViewModel"
    }
}
