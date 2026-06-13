package dev.vodnixir.delirium.ui.main

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.vodnixir.delirium.R
import dev.vodnixir.delirium.domain.model.Friend
import dev.vodnixir.delirium.ui.components.DAvatar
import dev.vodnixir.delirium.ui.components.DButtonFullWidth
import dev.vodnixir.delirium.ui.theme.LocketShape

/**
 * Full-screen overlay shown after capture: review the photo, add a caption and
 * choose which friends receive it.
 */
@Composable
fun PhotoPreview(
    state: CaptureUiState,
    recipients: List<Friend>,
    onCaptionChange: (String) -> Unit,
    onToggleRecipient: (String) -> Unit,
    onSend: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bytes = state.captured ?: return
    val colors = MaterialTheme.colorScheme
    val imageBitmap = remember(bytes) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onDiscard) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_cancel),
                        tint = colors.onSurface,
                    )
                }
                Text(
                    text = stringResource(R.string.preview_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Spacer(Modifier.size(48.dp))
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(LocketShape)
                    .background(colors.surfaceVariant),
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                CaptionField(
                    value = state.caption,
                    onValueChange = onCaptionChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.preview_recipients),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            if (recipients.isEmpty()) {
                Text(
                    text = stringResource(R.string.preview_no_friends),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(items = recipients, key = { it.connectionId }) { friend ->
                        RecipientChip(
                            friend = friend,
                            selected = friend.connectionId in state.selected,
                            onClick = { onToggleRecipient(friend.connectionId) },
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            state.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }

            val count = state.selected.size
            DButtonFullWidth(
                text = if (count > 0) stringResource(R.string.preview_send_count, count)
                else stringResource(R.string.preview_send),
                onClick = onSend,
                loading = state.sending,
                enabled = count > 0,
                icon = Icons.Default.Send,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun CaptionField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.scrim.copy(alpha = 0.55f))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (value.isEmpty()) {
            Text(
                text = stringResource(R.string.preview_caption_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RecipientChip(
    friend: Friend,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(64.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (selected) colors.primary else colors.surfaceVariant)
                .padding(if (selected) 3.dp else 0.dp),
            contentAlignment = Alignment.Center,
        ) {
            DAvatar(
                name = friend.displayName,
                imageUrl = friend.avatarUrl ?: friend.lastPhotoUrl,
                size = if (selected) 50.dp else 56.dp,
                modifier = Modifier.clip(androidx.compose.foundation.shape.CircleShape),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = friend.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
