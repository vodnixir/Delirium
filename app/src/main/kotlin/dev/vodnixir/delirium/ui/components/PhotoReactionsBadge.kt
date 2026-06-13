package dev.vodnixir.delirium.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vodnixir.delirium.ui.theme.PillShape

/**
 * Compact overlay of a photo's reactions for grid/feed thumbnails: shows up to
 * three distinct emojis and the total count when more than one reaction exists.
 */
@Composable
fun PhotoReactionsBadge(emojis: List<String>, modifier: Modifier = Modifier) {
    if (emojis.isEmpty()) return
    val distinct = emojis.distinct().take(3)
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        distinct.forEach { Text(text = it, fontSize = 13.sp) }
        if (emojis.size > 1) {
            Text(
                text = " ${emojis.size}",
                color = Color.White,
                fontSize = 11.sp,
            )
        }
    }
}
