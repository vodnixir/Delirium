package dev.vodnixir.delirium.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.absoluteValue

/**
 * Circular avatar. Renders [imageUrl] when present, otherwise a tinted disc with
 * the person's initials. The tint is derived from [name] so each friend gets a
 * stable, distinct color.
 */
@Composable
fun DAvatar(
    name: String,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    size: Dp = 48.dp,
) {
    val gradient = remember(name) { avatarGradient(name) }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(gradient),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            Text(
                text = initials(name),
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value / 2.4f).sp,
            )
        }
    }
}

private fun initials(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

private val avatarPalette = listOf(
    0xFFB794FF to 0xFFF2A6D6,
    0xFF5FD4D6 to 0xFF8FB8FF,
    0xFFFF9E80 to 0xFFFFC25C,
    0xFF7BE0A4 to 0xFF5FD4D6,
    0xFFFF8FB1 to 0xFFB794FF,
)

private fun avatarGradient(name: String): Brush {
    val idx = (name.hashCode().absoluteValue) % avatarPalette.size
    val (a, b) = avatarPalette[idx]
    return Brush.linearGradient(listOf(Color(a), Color(b)))
}
