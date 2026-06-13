package dev.vodnixir.delirium.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vodnixir.delirium.R
import dev.vodnixir.delirium.data.auth.AuthRepository
import dev.vodnixir.delirium.data.outbox.OutboxRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds a just-captured photo while the user adds a caption and picks
 * recipients, then uploads one copy per selected connection.
 */
data class CaptureUiState(
    val captured: ByteArray? = null,
    val caption: String = "",
    val selected: Set<String> = emptySet(),
    val sending: Boolean = false,
    val error: String? = null,
) {
    val previewing: Boolean get() = captured != null

    // ByteArray needs structural equals/hashCode for correct state diffing.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CaptureUiState) return false
        return caption == other.caption &&
            selected == other.selected &&
            sending == other.sending &&
            error == other.error &&
            captured.contentEquals(other.captured)
    }

    override fun hashCode(): Int {
        var result = captured?.contentHashCode() ?: 0
        result = 31 * result + caption.hashCode()
        result = 31 * result + selected.hashCode()
        result = 31 * result + sending.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }
}

sealed interface CaptureEvent {
    data object Sent : CaptureEvent
}

class CaptureViewModel(
    private val authRepository: AuthRepository,
    private val outboxRepository: OutboxRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    private val _events = Channel<CaptureEvent>(Channel.BUFFERED)
    val events: Flow<CaptureEvent> = _events.receiveAsFlow()

    fun onCaptured(jpeg: ByteArray, defaultRecipients: Set<String>) {
        _state.value = CaptureUiState(captured = jpeg, selected = defaultRecipients)
    }

    fun discard() {
        _state.value = CaptureUiState()
    }

    fun onCaptionChange(value: String) =
        _state.update { it.copy(caption = value.take(MAX_CAPTION)) }

    fun toggleRecipient(connectionId: String) = _state.update { state ->
        val next = state.selected.toMutableSet().apply {
            if (!add(connectionId)) remove(connectionId)
        }
        state.copy(selected = next)
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun send() {
        val snapshot = _state.value
        val bytes = snapshot.captured ?: return
        if (snapshot.sending) return
        if (snapshot.selected.isEmpty()) {
            _state.update { it.copy(error = appContext.getString(R.string.capture_pick_recipient)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null) }
            runCatching {
                val uid = authRepository.currentUserId ?: error("Not signed in")
                val caption = snapshot.caption.trim().ifBlank { null }
                // Queue one copy per recipient; the worker uploads now (online) or
                // as soon as connectivity returns.
                withContext(Dispatchers.IO) {
                    snapshot.selected.forEach { connectionId ->
                        outboxRepository.enqueue(connectionId, uid, bytes, caption)
                    }
                }
            }.onSuccess {
                _state.value = CaptureUiState()
                _events.send(CaptureEvent.Sent)
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        sending = false,
                        error = e.message ?: appContext.getString(R.string.capture_send_failed),
                    )
                }
            }
        }
    }

    private companion object {
        const val MAX_CAPTION = 120
    }
}
