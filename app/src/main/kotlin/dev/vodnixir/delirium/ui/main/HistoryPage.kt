package dev.vodnixir.delirium.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.vodnixir.delirium.R
import dev.vodnixir.delirium.domain.model.Friend
import dev.vodnixir.delirium.domain.model.Photo
import dev.vodnixir.delirium.ui.components.DChip
import dev.vodnixir.delirium.ui.components.EmptyState
import dev.vodnixir.delirium.ui.components.PhotoReactionsBadge

// Clears the floating top tab bar drawn by MainScreen. Shared by the other zones.
internal val TopBarSpace = 56.dp

/**
 * Left zone of the main scaffold — the merged photo history across every
 * connection. A friend filter narrows the grid client-side (cheap, since the
 * whole feed is already in memory).
 */
@Composable
fun HistoryPage(
    friends: List<Friend>,
    feed: List<Photo>,
    onOpenPhoto: (photoId: String, connectionId: String) -> Unit,
    onOpenRecap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<String?>(null) }

    // Drop the filter if the chosen friend is no longer around.
    LaunchedEffect(friends) {
        if (selected != null && friends.none { it.connectionId == selected }) selected = null
    }

    val nameByConnection = remember(friends) {
        friends.associate { it.connectionId to it.displayName }
    }
    val visible = remember(feed, selected) {
        if (selected == null) feed else feed.filter { it.connectionId == selected }
    }

    if (feed.isEmpty()) {
        EmptyState(
            emoji = "🗂️",
            title = stringResource(R.string.history_empty_title),
            subtitle = stringResource(R.string.history_empty_subtitle),
            modifier = modifier,
        )
        return
    }

    val friendsWithPhotos = remember(friends, feed) {
        val ids = feed.mapTo(HashSet()) { it.connectionId }
        friends.filter { it.connectionId in ids }
    }

    Column(modifier = modifier) {
        Spacer(Modifier.height(TopBarSpace))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                DChip(
                    label = stringResource(R.string.history_filter_all),
                    selected = selected == null,
                    onClick = { selected = null },
                )
            }
            items(items = friendsWithPhotos, key = { it.connectionId }) { friend ->
                DChip(
                    label = friend.displayName,
                    selected = selected == friend.connectionId,
                    onClick = { selected = friend.connectionId },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items = visible, key = { it.id }) { photo ->
                HistoryCell(
                    photo = photo,
                    friendName = nameByConnection[photo.connectionId],
                    onClick = { onOpenPhoto(photo.id, photo.connectionId) },
                )
            }
        }
    }
}

@Composable
private fun HistoryCell(
    photo: Photo,
    friendName: String?,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = photo.displayUrl,
            contentDescription = friendName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (photo.isVideo) {
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = stringResource(R.string.video_play),
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(44.dp),
            )
        }

        if (photo.reactionEmojis.isNotEmpty()) {
            PhotoReactionsBadge(
                emojis = photo.reactionEmojis,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
        }

        val label = photo.caption?.takeIf { it.isNotBlank() } ?: friendName
        if (label != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                        ),
                    ),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}
