package dev.vodnixir.delirium.ui.draw

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Per-friend drawing screen: draws on a canvas and uploads straight to [viewModel]. */
@Composable
fun DrawScreen(
    viewModel: DrawViewModel,
    backgroundUri: String?,
    onClose: () -> Unit,
    onSent: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { if (it is DrawEvent.Sent) onSent() }
    }

    DrawCanvas(
        sending = state is DrawUploadState.Sending,
        onSend = { bmp -> viewModel.send(bmp) },
        onClose = onClose,
        backgroundUri = backgroundUri,
    )
}
