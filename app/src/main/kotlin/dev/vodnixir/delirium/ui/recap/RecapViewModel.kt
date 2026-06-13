package dev.vodnixir.delirium.ui.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vodnixir.delirium.data.connection.ConnectionRepository
import dev.vodnixir.delirium.data.photo.PhotoRepository
import dev.vodnixir.delirium.domain.model.Photo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Loads a small set of recent photos for the recap slideshow, ordered oldest →
 * newest so the montage plays as a little story.
 */
class RecapViewModel(
    connectionRepository: ConnectionRepository,
    photoRepository: PhotoRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val photos: StateFlow<List<Photo>> =
        flow { emit(connectionRepository.getMyConnectionIds()) }
            .flatMapLatest { ids -> photoRepository.observeFeed(ids) }
            .map { recent -> recent.take(RECAP_LIMIT).reversed() }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private companion object {
        const val RECAP_LIMIT = 20
    }
}
