package dev.vodnixir.delirium.ui.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vodnixir.delirium.data.connection.ConnectionRepository
import dev.vodnixir.delirium.domain.model.Friend
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

class WidgetConfigViewModel(
    connectionRepository: ConnectionRepository,
) : ViewModel() {

    val friends: StateFlow<List<Friend>> = connectionRepository.observeMyFriends()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())
}
