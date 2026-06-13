package dev.vodnixir.delirium.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import android.content.Context
import dev.vodnixir.delirium.R
import dev.vodnixir.delirium.domain.model.Friend
import dev.vodnixir.delirium.ui.components.DAvatar
import dev.vodnixir.delirium.ui.components.EmptyState
import dev.vodnixir.delirium.ui.theme.PillShape

private const val MAX_FRIENDS = 20

private enum class FriendsTab(@StringRes val labelRes: Int) {
    Friends(R.string.friends_title),
    Chats(R.string.tab_chats),
}

/**
 * Right zone of the main scaffold — friends and chats behind a segmented toggle.
 * Enforces the 20-friend cap in the UI; pending (not-yet-joined) connections are
 * shown with a "waiting" hint.
 */
@Composable
fun FriendsPage(
    friends: List<Friend>,
    onAddFriend: () -> Unit,
    onOpenFriend: (connectionId: String, name: String) -> Unit,
    onOpenProfile: () -> Unit,
    onDeleteFriend: (connectionId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(FriendsTab.Friends) }

    Column(modifier = modifier.fillMaxSize()) {
        Spacer(Modifier.height(TopBarSpace))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onOpenProfile) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = stringResource(R.string.profile_title),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        SegmentedToggle(
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(12.dp))

        when (tab) {
            FriendsTab.Friends -> FriendsList(
                friends = friends,
                onAddFriend = onAddFriend,
                onOpenFriend = onOpenFriend,
                onDeleteFriend = onDeleteFriend,
            )
            FriendsTab.Chats -> ChatsList(
                friends = friends,
                onOpenFriend = onOpenFriend,
            )
        }
    }
}

@Composable
private fun FriendsList(
    friends: List<Friend>,
    onAddFriend: () -> Unit,
    onOpenFriend: (connectionId: String, name: String) -> Unit,
    onDeleteFriend: (connectionId: String) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Friend?>(null) }

    if (friends.isEmpty()) {
        EmptyState(
            emoji = "👋",
            title = stringResource(R.string.friends_empty_title),
            subtitle = stringResource(R.string.friends_empty_subtitle),
            actionText = stringResource(R.string.friends_add),
            onAction = onAddFriend,
        )
        return
    }

    val atLimit = friends.size >= MAX_FRIENDS

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.friends_count, friends.size, MAX_FRIENDS),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        item {
            if (atLimit) LimitNotice() else AddFriendRow(onClick = onAddFriend)
        }
        items(items = friends, key = { it.connectionId }) { friend ->
            FriendRow(
                friend = friend,
                onClick = { onOpenFriend(friend.connectionId, friend.displayName) },
                onDelete = { pendingDelete = friend },
            )
        }
    }

    pendingDelete?.let { friend ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.friends_delete_title)) },
            text = {
                Text(stringResource(R.string.friends_delete_message, friend.displayName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteFriend(friend.connectionId)
                        pendingDelete = null
                    },
                ) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun ChatsList(
    friends: List<Friend>,
    onOpenFriend: (connectionId: String, name: String) -> Unit,
) {
    val chats = remember(friends) {
        friends.filter { it.friendUid != null && it.lastPhotoAt > 0L }
            .sortedByDescending { it.lastPhotoAt }
    }

    if (chats.isEmpty()) {
        EmptyState(
            emoji = "💬",
            title = stringResource(R.string.chats_empty_title),
            subtitle = stringResource(R.string.chats_empty_subtitle),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items = chats, key = { it.connectionId }) { friend ->
            ChatRow(
                friend = friend,
                onClick = { onOpenFriend(friend.connectionId, friend.displayName) },
            )
        }
    }
}

@Composable
private fun SegmentedToggle(
    selected: FriendsTab,
    onSelect: (FriendsTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(colors.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FriendsTab.entries.forEach { item ->
            SegmentButton(
                label = stringResource(item.labelRes),
                selected = item == selected,
                onClick = { onSelect(item) },
            )
        }
    }
}

@Composable
private fun RowScope.SegmentButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(PillShape)
            .background(if (selected) colors.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddFriendRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.PersonAdd,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.friends_add),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun LimitNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.friends_limit_reached, MAX_FRIENDS),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FriendRow(friend: Friend, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DAvatar(name = friend.displayName, imageUrl = friend.avatarUrl ?: friend.lastPhotoUrl, size = 52.dp)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = friend.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (friend.friendUid == null) stringResource(R.string.friends_waiting)
                else stringResource(R.string.friends_open_chat),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StreakBadge(friend)
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.friends_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A small flame with the Snapstreak length; dim when today's exchange is pending. */
@Composable
private fun StreakBadge(friend: Friend) {
    if (friend.streakCount <= 0) return
    Text(
        text = "🔥 ${friend.streakCount}",
        style = MaterialTheme.typography.labelLarge,
        color = if (friend.streakActiveToday) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        },
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun ChatRow(friend: Friend, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DAvatar(name = friend.displayName, imageUrl = friend.avatarUrl ?: friend.lastPhotoUrl, size = 52.dp)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = friend.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.chats_last_photo),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StreakBadge(friend)
        Spacer(Modifier.width(4.dp))
        Text(
            text = relativeTime(LocalContext.current, friend.lastPhotoAt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun relativeTime(context: Context, millis: Long): String {
    if (millis <= 0L) return ""
    val diff = System.currentTimeMillis() - millis
    return when {
        diff < 60_000L -> context.getString(R.string.time_now)
        diff < 3_600_000L -> context.getString(R.string.time_minutes, (diff / 60_000L).toInt())
        diff < 86_400_000L -> context.getString(R.string.time_hours, (diff / 3_600_000L).toInt())
        diff < 604_800_000L -> context.getString(R.string.time_days, (diff / 86_400_000L).toInt())
        else -> context.getString(R.string.time_weeks, (diff / 604_800_000L).toInt())
    }
}
