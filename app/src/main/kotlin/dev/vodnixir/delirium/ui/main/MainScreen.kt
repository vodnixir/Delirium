package dev.vodnixir.delirium.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vodnixir.delirium.R
import dev.vodnixir.delirium.ui.camera.toCompressedJpeg
import dev.vodnixir.delirium.ui.components.PageIndicator
import dev.vodnixir.delirium.ui.draw.DrawCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGE_HISTORY = 0
private const val PAGE_CAPTURE = 1
private const val PAGE_FRIENDS = 2
private const val PAGE_COUNT = 3

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    captureViewModel: CaptureViewModel,
    onAddFriend: () -> Unit,
    onOpenFriend: (connectionId: String, name: String) -> Unit,
    onOpenPhoto: (photoId: String, connectionId: String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenRecap: () -> Unit,
) {
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val feed by viewModel.feed.collectAsStateWithLifecycle()
    val captureState by captureViewModel.state.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = PAGE_CAPTURE) { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    var drawing by remember { mutableStateOf(false) }

    // Only fully-joined connections (another member present) can receive photos.
    val recipients = remember(friends) { friends.filter { it.friendUid != null } }

    // After a successful send, drop the user into History to see the result.
    LaunchedEffect(captureViewModel) {
        captureViewModel.events.collectLatest { event ->
            when (event) {
                CaptureEvent.Sent -> pagerState.animateScrollToPage(PAGE_HISTORY)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                PAGE_HISTORY -> HistoryPage(
                    friends = friends,
                    feed = feed,
                    onOpenPhoto = onOpenPhoto,
                    onOpenRecap = onOpenRecap,
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                )
                PAGE_CAPTURE -> CapturePage(
                    active = pagerState.currentPage == PAGE_CAPTURE && !captureState.previewing && !drawing,
                    onCapture = { bytes ->
                        captureViewModel.onCaptured(
                            jpeg = bytes,
                            defaultRecipients = recipients.map { it.connectionId }.toSet(),
                        )
                    },
                    onDraw = { drawing = true },
                    modifier = Modifier.fillMaxSize(),
                )
                PAGE_FRIENDS -> FriendsPage(
                    friends = friends,
                    onAddFriend = onAddFriend,
                    onOpenFriend = onOpenFriend,
                    onOpenProfile = onOpenProfile,
                    onDeleteFriend = viewModel::deleteFriend,
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                )
            }
        }

        // Chrome (tabs + indicator) only when not reviewing a captured photo.
        AnimatedVisibility(
            visible = !captureState.previewing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopTabs(
                currentPage = pagerState.currentPage,
                onSelect = { target -> scope.launch { pagerState.animateScrollToPage(target) } },
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            )
        }

        AnimatedVisibility(
            visible = !captureState.previewing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PageIndicator(
                pageCount = PAGE_COUNT,
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
            )
        }

        AnimatedVisibility(
            visible = captureState.previewing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            PhotoPreview(
                state = captureState,
                recipients = recipients,
                onCaptionChange = captureViewModel::onCaptionChange,
                onToggleRecipient = captureViewModel::toggleRecipient,
                onSend = captureViewModel::send,
                onDiscard = captureViewModel::discard,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Drawing started from the capture page: when finished, the result flows
        // into the same preview/recipient step as a photo.
        if (drawing) {
            DrawCanvas(
                sending = false,
                onSend = { bmp ->
                    scope.launch {
                        val bytes = withContext(Dispatchers.Default) { bmp.toCompressedJpeg() }
                        captureViewModel.onCaptured(
                            jpeg = bytes,
                            defaultRecipients = recipients.map { it.connectionId }.toSet(),
                        )
                        drawing = false
                    }
                },
                onClose = { drawing = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TopTabs(
    currentPage: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        TabLabel(stringResource(R.string.tab_history), currentPage == PAGE_HISTORY) { onSelect(PAGE_HISTORY) }
        TabLabel(stringResource(R.string.feed_camera), currentPage == PAGE_CAPTURE) { onSelect(PAGE_CAPTURE) }
        TabLabel(stringResource(R.string.friends_title), currentPage == PAGE_FRIENDS) { onSelect(PAGE_FRIENDS) }
    }
}

@Composable
private fun TabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
