package dev.vodnixir.delirium.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

/** A single emoji-rain burst. Bump [id] (e.g. an incrementing counter) to fire a new burst. */
data class EmojiBurst(val id: Int, val emoji: String)

/**
 * Plays a short celebratory rain of [burst]'s emoji across the parent bounds.
 * Pass a new [EmojiBurst] (with a changed id) to retrigger; pass null to render nothing.
 */
@Composable
fun EmojiRain(
    burst: EmojiBurst?,
    modifier: Modifier = Modifier,
    particleCount: Int = 16,
) {
    burst ?: return
    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        // key() restarts every particle whenever a new burst fires.
        key(burst.id) {
            repeat(particleCount) { index ->
                EmojiParticle(
                    emoji = burst.emoji,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    index = index,
                )
            }
        }
    }
}

@Composable
private fun EmojiParticle(
    emoji: String,
    widthPx: Float,
    heightPx: Float,
    index: Int,
) {
    val startXFraction = remember(index) { Random.nextFloat() }
    val drift = remember(index) { (Random.nextFloat() - 0.5f) * 0.25f * widthPx }
    val startDelay = remember(index) { Random.nextInt(0, 280).toLong() }
    val sizeSp = remember(index) { Random.nextInt(22, 40) }
    val progress = remember(index) { Animatable(0f) }

    LaunchedEffect(index) {
        delay(startDelay)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1300, easing = LinearOutSlowInEasing),
        )
    }

    Text(
        text = emoji,
        fontSize = sizeSp.sp,
        modifier = Modifier.graphicsLayer {
            val p = progress.value
            translationX = startXFraction * widthPx + drift * p
            translationY = p * heightPx
            alpha = (1f - p).coerceIn(0f, 1f)
            val pop = 0.8f + 0.4f * (1f - kotlin.math.abs(p - 0.2f).coerceIn(0f, 1f))
            scaleX = pop
            scaleY = pop
        },
    )
}
