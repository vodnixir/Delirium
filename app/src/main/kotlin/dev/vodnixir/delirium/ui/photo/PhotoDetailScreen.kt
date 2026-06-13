package dev.vodnixir.delirium.ui.photo

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.vodnixir.delirium.R
import dev.vodnixir.delirium.domain.model.Message
import dev.vodnixir.delirium.ui.components.EmojiBurst
import dev.vodnixir.delirium.ui.components.EmojiRain
import dev.vodnixir.delirium.ui.components.VideoPlayer
import dev.vodnixir.delirium.util.saveImageToGallery
import kotlinx.coroutines.launch

private val ReactionPalette = listOf("❤️", "😂", "😮", "🔥", "😍")

private val EmojiPicker = listOf(
    "❤️", "😂", "😮", "🔥", "😍", "👍", "👎", "😢",
    "😡", "🎉", "🙏", "👏", "💯", "🥹", "😅", "😎",
    "🤔", "🥳", "😴", "🤯", "😱", "🤩", "😇", "🙄",
    "🫶", "💪", "✨", "🌟", "💔", "👀", "🤝", "🤡",
)

@Composable
fun PhotoDetailScreen(
    viewModel: PhotoDetailViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reactions by viewModel.reactions.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedMsg = stringResource(R.string.photo_saved)
    val saveFailedMsg = stringResource(R.string.photo_save_failed)

    var burst by remember { mutableStateOf<EmojiBurst?>(null) }
    var burstCounter by remember { mutableIntStateOf(0) }
    var chatOpen by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var pickerOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    fun react(emoji: String) {
        viewModel.react(emoji)
        burstCounter += 1
        burst = EmojiBurst(burstCounter, emoji)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (val s = state) {
            is PhotoDetailState.Loading -> {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            is PhotoDetailState.Error -> {
                Text(
                    text = s.message,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }

            is PhotoDetailState.Ready -> {
                val photo = s.photo
                if (photo.isVideo) {
                    VideoPlayer(
                        url = photo.storageUrl,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AsyncImage(
                        model = photo.storageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                EmojiRain(
                    burst = burst,
                    modifier = Modifier.fillMaxSize(),
                )

                TopBar(
                    authorName = s.names[photo.senderId]?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.photo_unknown_author),
                    createdAt = photo.createdAt,
                    saving = saving,
                    canSave = !photo.isVideo,
                    onSave = {
                        scope.launch {
                            saving = true
                            val ok = saveImageToGallery(context, photo.storageUrl)
                            saving = false
                            Toast.makeText(
                                context,
                                if (ok) savedMsg else saveFailedMsg,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onClose = onClose,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                BottomPanel(
                    caption = photo.caption,
                    seen = photo.senderId == viewModel.currentUserId &&
                        photo.seenBy.any { it != viewModel.currentUserId },
                    reactionEmojis = reactions.map { it.emoji },
                    palette = ReactionPalette,
                    onReact = ::react,
                    onOpenPicker = { pickerOpen = true },
                    chatOpen = chatOpen,
                    onToggleChat = { chatOpen = !chatOpen },
                    messages = messages,
                    names = s.names,
                    currentUserId = viewModel.currentUserId,
                    draft = draft,
                    onDraftChange = { draft = it },
                    sending = sending,
                    onSend = {
                        viewModel.sendMessage(draft)
                        draft = ""
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        if (pickerOpen) {
            EmojiPickerDialog(
                onPick = { emoji -> react(emoji); pickerOpen = false },
                onDismiss = { pickerOpen = false },
            )
        }
    }
}

@Composable
private fun TopBar(
    authorName: String,
    createdAt: Long,
    saving: Boolean,
    canSave: Boolean,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.common_back),
                tint = Color.White,
            )
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = authorName,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = DateUtils.getRelativeTimeSpanString(createdAt).toString(),
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (canSave) {
            if (saving) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.padding(12.dp).width(22.dp),
                )
            } else {
                IconButton(onClick = onSave) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = stringResource(R.string.photo_save_to_gallery),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BottomPanel(
    caption: String?,
    seen: Boolean,
    reactionEmojis: List<String>,
    palette: List<String>,
    onReact: (String) -> Unit,
    onOpenPicker: () -> Unit,
    chatOpen: Boolean,
    onToggleChat: () -> Unit,
    messages: List<Message>,
    names: Map<String, String>,
    currentUserId: String?,
    draft: String,
    onDraftChange: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (!caption.isNullOrBlank()) {
            Text(
                text = caption,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        if (seen) {
            Text(
                text = stringResource(R.string.photo_seen),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        if (reactionEmojis.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                reactionEmojis.forEach { emoji ->
                    Text(text = emoji, fontSize = 24.sp)
                }
            }
        }

        if (chatOpen) {
            ChatThread(
                messages = messages,
                names = names,
                currentUserId = currentUserId,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Tap to react; long-press any emoji to open the full picker.
            palette.forEach { emoji ->
                Text(
                    text = emoji,
                    fontSize = 28.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = { onReact(emoji) },
                            onLongClick = onOpenPicker,
                        )
                        .padding(6.dp),
                )
            }
            Text(
                text = "＋",
                fontSize = 26.sp,
                color = Color.White,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onOpenPicker)
                    .padding(6.dp),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onToggleChat) {
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    contentDescription = stringResource(R.string.photo_chat),
                    tint = if (chatOpen) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
        }

        if (chatOpen) {
            MessageInput(
                draft = draft,
                onDraftChange = onDraftChange,
                sending = sending,
                onSend = onSend,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun EmojiPickerDialog(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
        title = { Text(stringResource(R.string.photo_pick_reaction)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.heightIn(max = 320.dp),
            ) {
                gridItems(EmojiPicker) { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 26.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onPick(emoji) }
                            .padding(8.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun ChatThread(
    messages: List<Message>,
    names: Map<String, String>,
    currentUserId: String?,
    modifier: Modifier = Modifier,
) {
    if (messages.isEmpty()) {
        Text(
            text = stringResource(R.string.photo_chat_empty),
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.padding(vertical = 12.dp),
        )
        return
    }
    val listState = rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().heightIn(max = 240.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(items = messages, key = { it.id }) { message ->
            val mine = message.fromUserId == currentUserId
            MessageBubble(
                text = message.text,
                authorName = if (mine) {
                    null
                } else {
                    names[message.fromUserId]?.takeIf { it.isNotBlank() }
                },
                mine = mine,
            )
        }
    }
}

@Composable
private fun MessageBubble(
    text: String,
    authorName: String?,
    mine: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (mine) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.16f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (authorName != null) {
                    Text(
                        text = authorName,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(
                    text = text,
                    color = if (mine) MaterialTheme.colorScheme.onPrimary else Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun MessageInput(
    draft: String,
    onDraftChange: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.photo_message_hint)) },
            shape = RoundedCornerShape(24.dp),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { onSend() }),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onSend,
            enabled = draft.isNotBlank() && !sending,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.photo_send),
                tint = if (draft.isNotBlank() && !sending) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
            )
        }
    }
}
