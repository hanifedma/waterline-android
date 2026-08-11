package com.hanifedma.waterline.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Waterline's theme.
 *
 * Deliberately NOT using Material You dynamic colour: the app's identity is
 * one specific green on near-black, shared with the web app, and letting the
 * device wallpaper repaint it would break that. The theme follows the app's
 * own setting rather than the system's, because the web app has an in-app
 * toggle and the two should behave identically.
 */

val LocalWaterlineColors = staticCompositionLocalOf { DarkWaterlineColors }

private fun scheme(c: WaterlineColors) = if (c.dark) {
    darkColorScheme(
        primary = c.accent,
        onPrimary = c.accentContrast,
        primaryContainer = c.accentSoft,
        onPrimaryContainer = c.accent,
        secondary = c.accent,
        onSecondary = c.accentContrast,
        background = c.bg,
        onBackground = c.text,
        surface = c.surface,
        onSurface = c.text,
        surfaceVariant = c.surface2,
        onSurfaceVariant = c.muted,
        outline = c.border,
        outlineVariant = c.border,
        error = c.danger,
        onError = c.accentContrast,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface2,
        surfaceContainerHighest = c.surface3,
        surfaceContainerLow = c.surface2,
        surfaceContainerLowest = c.bg,
        inverseSurface = c.text,
        inverseOnSurface = c.bg,
        scrim = c.bg,
    )
} else {
    lightColorScheme(
        primary = c.accent,
        onPrimary = c.accentContrast,
        primaryContainer = c.accentSoft,
        onPrimaryContainer = c.accent,
        secondary = c.accent,
        onSecondary = c.accentContrast,
        background = c.bg,
        onBackground = c.text,
        surface = c.surface,
        onSurface = c.text,
        surfaceVariant = c.surface2,
        onSurfaceVariant = c.muted,
        outline = c.border,
        outlineVariant = c.border,
        error = c.danger,
        onError = c.accentContrast,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface2,
        surfaceContainerHighest = c.surface3,
        surfaceContainerLow = c.surface2,
        surfaceContainerLowest = c.bg,
        inverseSurface = c.text,
        inverseOnSurface = c.bg,
        scrim = c.text,
    )
}

@Composable
fun WaterlineTheme(
    darkTheme: Boolean = true, // dark is the app's default, as on the web
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkWaterlineColors else LightWaterlineColors
    CompositionLocalProvider(LocalWaterlineColors provides colors) {
        MaterialTheme(
            colorScheme = scheme(colors),
            typography = Typography,
            content = content,
        )
    }
}
