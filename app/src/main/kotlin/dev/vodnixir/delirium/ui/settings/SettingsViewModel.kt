package dev.vodnixir.delirium.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vodnixir.delirium.data.local.PreferencesRepository
import dev.vodnixir.delirium.reminder.ReminderScheduler
import dev.vodnixir.delirium.ui.theme.AppTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Holds appearance (theme) and the daily reminder schedule. */
class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val appContext: Context,
) : ViewModel() {

    val theme: StateFlow<AppTheme> = preferencesRepository.themeKey
        .map { AppTheme.fromKey(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AppTheme.Default)

    val reminderEnabled: StateFlow<Boolean> = preferencesRepository.reminderEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), false)

    val reminderMinutes: StateFlow<Int> = preferencesRepository.reminderMinutesOfDay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 19 * 60)

    fun selectTheme(theme: AppTheme) {
        viewModelScope.launch { preferencesRepository.setThemeKey(theme.key) }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setReminderEnabled(enabled)
            reschedule()
        }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            preferencesRepository.setReminderMinutesOfDay(hour * 60 + minute)
            reschedule()
        }
    }

    private suspend fun reschedule() {
        ReminderScheduler.reschedule(
            appContext,
            preferencesRepository.reminderEnabledOnce(),
            preferencesRepository.reminderMinutesOnce(),
        )
    }
}
