package dev.vodnixir.delirium.ui.util

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vodnixir.delirium.DeliriumApplication
import dev.vodnixir.delirium.data.AppContainer

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline factory: (AppContainer) -> VM,
): VM = viewModel(
    factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                as DeliriumApplication
            return factory(app.container) as T
        }
    },
)
