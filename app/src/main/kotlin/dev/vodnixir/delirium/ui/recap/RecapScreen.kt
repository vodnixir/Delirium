package dev.vodnixir.delirium.ui.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.vodnixir.delirium.R
import dev.vodnixir.delirium.domain.model.Photo
import dev.vodnixir.delirium.ui.components.VideoPlayer
import dev.vodnixir.delirium.ui.components.EmptyState

private const val SLIDE_MS = 3_000

@Composable
fun RecapScreen(
    viewModel: RecapViewModel,
    onClose: () -> Unit,
) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()

    if (photos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            EmptyState(
                emoji = "✨",
                title = stringResource(R.string.recap_empty_title),
                subtitle = stringResource(R.string.recap_empty_subtitle),
                actionText = stringResource(R.string.common_close),
                onAction = onClose,
            )
        }
        return
    }

    RecapPlayer(photos = photos, onClose = onClose)
}

@Composable
private fun RecapPlayer(
    photos: List<Photo>,
    onClose: () -> Unit,
) {
    var index by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }

    // Drive the current segment; advance (or close) when it fills. Restarts
    // whenever [index] changes, e.g. after a tap.
    LaunchedEffect(index) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(SLIDE_MS, easing = LinearEasing))
        if (index < photos.lastIndex) index++ else onClose()
    }

    val current = photos[index.coerceIn(0, photos.lastIndex)]

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (current.isVideo) {
            VideoPlayer(
                url = current.storageUrl,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AsyncImage(
                model = current.storageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Tap zones: left third goes back, right two-thirds goes forward.
        Row(modifier = Modifier.fillMaxSize()) {
            TapZone(weight = 1f, onClick = { if (index > 0) index-- })
            TapZone(
                weight = 2f,
                onClick = { if (index < photos.lastIndex) index++ else onClose() },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                photos.forEachIndexed { i, _ ->
                    val fraction = when {
                        i < index -> 1f
                        i == index -> progress.value
                        else -> 0f
                    }
                    SegmentBar(fraction = fraction, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = Color.White,
                    )
                }
            }
        }

        val caption = current.caption?.takeIf { it.isNotBlank() }
        if (caption != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                        ),
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun RowScope.TapZone(
    weight: Float,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .weight(weight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    )
}

@Composable
private fun SegmentBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.3f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(RoundedCornerShape(50))
                .background(Color.White),
        )
    }
}
