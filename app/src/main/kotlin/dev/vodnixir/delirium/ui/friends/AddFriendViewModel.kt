package dev.vodnixir.delirium.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vodnixir.delirium.data.auth.AuthRepository
import dev.vodnixir.delirium.data.connection.ConnectionRepository
import dev.vodnixir.delirium.data.local.PreferencesRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AddFriendPhase { Choosing, Loading, ShowingCode, EnteringCode }

data class AddFriendUiState(
    val name: String = "",
    val inputCode: String = "",
    val phase: AddFriendPhase = AddFriendPhase.Choosing,
    val generatedCode: String? = null,
    val error: String? = null,
)

sealed interface AddFriendEvent {
    data object Done : AddFriendEvent
}

class AddFriendViewModel(
    private val authRepository: AuthRepository,
    private val connectionRepository: ConnectionRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddFriendUiState())
    val state: StateFlow<AddFriendUiState> = _state.asStateFlow()

    private val _events = Channel<AddFriendEvent>(Channel.BUFFERED)
    val events: Flow<AddFriendEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.myNameOnce()?.let { saved ->
                _state.update { it.copy(name = saved) }
            }
        }
    }

    fun onNameChange(value: String) {
        _state.update { it.copy(name = value.take(MAX_NAME)) }
    }

    fun onInputCodeChange(value: String) {
        _state.update { it.copy(inputCode = value.filter(Char::isDigit).take(CODE_LENGTH)) }
    }

    fun onEnterCodeClicked() {
        _state.update { it.copy(phase = AddFriendPhase.EnteringCode) }
    }

    fun onCreateCodeClicked() {
        val name = _state.value.name.trim()
        if (name.isEmpty()) {
            _state.update { it.copy(error = "Введите имя") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(phase = AddFriendPhase.Loading) }
            runCatching {
                authRepository.signInAnonymouslyIfNeeded()
                preferencesRepository.setMyName(name)
                val connectionId = connectionRepository.createConnection(name)
                connectionRepository.createInvite(connectionId)
            }.onSuccess { code ->
                _state.update { it.copy(phase = AddFriendPhase.ShowingCode, generatedCode = code) }
            }.onFailure { e ->
                _state.update {
                    it.copy(phase = AddFriendPhase.Choosing, error = e.message ?: "Не удалось создать код")
                }
            }
        }
    }

    fun onJoinClicked() {
        val name = _state.value.name.trim()
        val code = _state.value.inputCode
        if (name.isEmpty()) {
            _state.update { it.copy(error = "Введите имя") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(phase = AddFriendPhase.Loading) }
            runCatching {
                authRepository.signInAnonymouslyIfNeeded()
                preferencesRepository.setMyName(name)
                connectionRepository.joinByInvite(code, name)
            }.onSuccess {
                _events.send(AddFriendEvent.Done)
            }.onFailure { e ->
                _state.update {
                    it.copy(phase = AddFriendPhase.EnteringCode, error = e.message ?: "Не удалось присоединиться")
                }
            }
        }
    }

    fun onCodeShared() {
        viewModelScope.launch { _events.send(AddFriendEvent.Done) }
    }

    fun onBackToChoosing() {
        _state.update { it.copy(phase = AddFriendPhase.Choosing, generatedCode = null) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private companion object {
        const val CODE_LENGTH = 6
        const val MAX_NAME = 30
    }
}
