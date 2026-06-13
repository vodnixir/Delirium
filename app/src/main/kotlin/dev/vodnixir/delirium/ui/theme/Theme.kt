package dev.vodnixir.delirium.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun DeliriumTheme(
    theme: AppTheme = AppTheme.Default,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = theme.colorScheme(),
        typography = DeliriumTypography,
        shapes = DeliriumShapes,
        content = content,
    )
}
