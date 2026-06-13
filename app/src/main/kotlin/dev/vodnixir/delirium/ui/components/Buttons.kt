package dev.vodnixir.delirium.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.vodnixir.delirium.ui.theme.PillShape

/**
 * Primary call-to-action: a glowing gradient pill. Dims and shows a spinner
 * while [loading]; disables interaction when not [enabled].
 */
@Composable
fun DButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    val active = enabled && !loading
    val gradient = Brush.horizontalGradient(listOf(colors.primary, colors.tertiary))

    Box(
        modifier = modifier
            .heightIn(min = 54.dp)
            .clip(PillShape)
            .background(if (active) gradient else Brush.horizontalGradient(listOf(colors.surfaceVariant, colors.surfaceVariant)))
            .clickable(enabled = active, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = colors.onPrimary,
                modifier = Modifier.padding(2.dp),
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (active) colors.onPrimary else colors.onSurfaceVariant,
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) colors.onPrimary else colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** Secondary action: an outlined pill that inherits the theme outline. */
@Composable
fun DSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        modifier = modifier,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = LocalContentColor.current)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = if (icon != null) Modifier.padding(start = 8.dp) else Modifier,
        )
    }
}

/** Low-emphasis text action. */
@Composable
fun DTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A full-width [DButton], the common form layout. */
@Composable
fun DButtonFullWidth(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) = DButton(
    text = text,
    onClick = onClick,
    modifier = modifier.fillMaxWidth(),
    enabled = enabled,
    loading = loading,
    icon = icon,
)
