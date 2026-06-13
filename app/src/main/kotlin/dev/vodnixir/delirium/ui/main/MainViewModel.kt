package dev.vodnixir.delirium.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vodnixir.delirium.data.connection.ConnectionRepository
import dev.vodnixir.delirium.data.photo.PhotoRepository
import dev.vodnixir.delirium.domain.model.Friend
import dev.vodnixir.delirium.domain.model.Photo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Shared state for the main scaffold; the friend list feeds every zone. */
class MainViewModel(
    private val connectionRepository: ConnectionRepository,
    photoRepository: PhotoRepository,
) : ViewModel() {

    val friends: StateFlow<List<Friend>> = connectionRepository.observeMyFriends()
        .catch { t ->
            Log.e(TAG, "friends listener failed", t)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /** Merged history feed across all of my connections, newest first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val feed: StateFlow<List<Photo>> = friends
        .map { list -> list.map { it.connectionId } }
        .distinctUntilChanged()
        .flatMapLatest { ids -> photoRepository.observeFeed(ids) }
        .catch { t ->
            Log.e(TAG, "feed listener failed", t)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /** Removes the friendship for both sides; a Cloud Function wipes its photos. */
    fun deleteFriend(connectionId: String) {
        viewModelScope.launch {
            runCatching { connectionRepository.deleteConnection(connectionId) }
                .onFailure { Log.e(TAG, "deleteFriend failed", it) }
        }
    }

    private companion object {
        const val TAG = "MainViewModel"
    }
}
