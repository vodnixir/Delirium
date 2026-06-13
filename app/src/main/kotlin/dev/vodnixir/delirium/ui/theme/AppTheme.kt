package dev.vodnixir.delirium.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import dev.vodnixir.delirium.R

/**
 * Selectable app themes. The palette of each is defined here so new themes can
 * be added by appending an entry — the picker, persistence and [colorScheme]
 * mapping all derive from this enum, so nothing else needs to change.
 */
enum class AppTheme(val key: String, @StringRes val displayNameRes: Int, val isDark: Boolean) {
    MidnightPurple("midnight_purple", R.string.theme_midnight, isDark = true),
    Ocean("ocean", R.string.theme_ocean, isDark = true),
    Sunset("sunset", R.string.theme_sunset, isDark = true),
    Daylight("daylight", R.string.theme_daylight, isDark = false);

    companion object {
        val Default = MidnightPurple

        fun fromKey(key: String?): AppTheme =
            entries.firstOrNull { it.key == key } ?: Default
    }
}

/** The signature accent color, handy for one-off highlights outside Material. */
val AppTheme.accent: Color
    get() = when (this) {
        AppTheme.MidnightPurple -> MidnightPrimary
        AppTheme.Ocean -> OceanPrimary
        AppTheme.Sunset -> SunsetPrimary
        AppTheme.Daylight -> DayPrimary
    }

fun AppTheme.colorScheme(): ColorScheme = when (this) {
    AppTheme.MidnightPurple -> darkColorScheme(
        primary = MidnightPrimary,
        onPrimary = MidnightOnPrimary,
        primaryContainer = MidnightPrimaryContainer,
        onPrimaryContainer = MidnightOnPrimaryContainer,
        secondary = MidnightSecondary,
        onSecondary = MidnightOnPrimary,
        tertiary = MidnightTertiary,
        onTertiary = MidnightOnPrimary,
        background = MidnightBg,
        onBackground = MidnightOnSurface,
        surface = MidnightSurface,
        onSurface = MidnightOnSurface,
        surfaceVariant = MidnightSurfaceVariant,
        onSurfaceVariant = MidnightOnSurfaceVariant,
        surfaceContainer = MidnightSurfaceHigh,
        surfaceContainerHigh = MidnightSurfaceHigh,
        outline = MidnightOutline,
        error = DangerRed,
    )
    AppTheme.Ocean -> darkColorScheme(
        primary = OceanPrimary,
        onPrimary = OceanOnPrimary,
        primaryContainer = OceanPrimaryContainer,
        onPrimaryContainer = OceanOnPrimaryContainer,
        secondary = OceanSecondary,
        onSecondary = OceanOnPrimary,
        tertiary = OceanTertiary,
        onTertiary = OceanOnPrimary,
        background = OceanBg,
        onBackground = OceanOnSurface,
        surface = OceanSurface,
        onSurface = OceanOnSurface,
        surfaceVariant = OceanSurfaceVariant,
        onSurfaceVariant = OceanOnSurfaceVariant,
        surfaceContainer = OceanSurfaceHigh,
        surfaceContainerHigh = OceanSurfaceHigh,
        outline = OceanOutline,
        error = DangerRed,
    )
    AppTheme.Sunset -> darkColorScheme(
        primary = SunsetPrimary,
        onPrimary = SunsetOnPrimary,
        primaryContainer = SunsetPrimaryContainer,
        onPrimaryContainer = SunsetOnPrimaryContainer,
        secondary = SunsetSecondary,
        onSecondary = SunsetOnPrimary,
        tertiary = SunsetTertiary,
        onTertiary = SunsetOnPrimary,
        background = SunsetBg,
        onBackground = SunsetOnSurface,
        surface = SunsetSurface,
        onSurface = SunsetOnSurface,
        surfaceVariant = SunsetSurfaceVariant,
        onSurfaceVariant = SunsetOnSurfaceVariant,
        surfaceContainer = SunsetSurfaceHigh,
        surfaceContainerHigh = SunsetSurfaceHigh,
        outline = SunsetOutline,
        error = DangerRed,
    )
    AppTheme.Daylight -> lightColorScheme(
        primary = DayPrimary,
        onPrimary = DayOnPrimary,
        primaryContainer = DayPrimaryContainer,
        onPrimaryContainer = DayOnPrimaryContainer,
        secondary = DaySecondary,
        onSecondary = DayOnPrimary,
        tertiary = DayTertiary,
        onTertiary = DayOnPrimary,
        background = DayBg,
        onBackground = DayOnSurface,
        surface = DaySurface,
        onSurface = DayOnSurface,
        surfaceVariant = DaySurfaceVariant,
        onSurfaceVariant = DayOnSurfaceVariant,
        surfaceContainer = DaySurfaceHigh,
        surfaceContainerHigh = DaySurfaceHigh,
        outline = DayOutline,
        error = DayError,
    )
}
