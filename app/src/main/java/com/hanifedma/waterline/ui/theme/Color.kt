package com.hanifedma.waterline.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * The web app's palette, verbatim, so Waterline looks like itself wherever it
 * runs. Material 3's own colour roles don't cover everything this app needs —
 * three surface tiers, a "past your goal" amber, a ring track — so those live
 * in [WaterlineColors] and reach the UI through a CompositionLocal.
 */

// ---------- Dark (default) — matched to hanifedma.com/waterline ----------
val DarkBg = Color(0xFF0F0F0F)
val DarkSurface = Color(0xFF1A1A1A)
val DarkSurface2 = Color(0xFF202021)
val DarkSurface3 = Color(0xFF282829)
val DarkBorder = Color(0xFF333333)
val DarkBorderStrong = Color(0xFF454545)
val DarkText = Color(0xFFF0F0F0)
val DarkMuted = Color(0xFFA6A6A6)
val DarkFaint = Color(0xFF8A8A8A)
val DarkAccent = Color(0xFF22C55E)
val DarkAccentHover = Color(0xFF4ADE80)
val DarkAccentSoft = Color(0x2922C55E)
val DarkAccentContrast = Color(0xFF052E16)
val DarkWin = Color(0xFFF59E0B)
val DarkWinSoft = Color(0x29F59E0B)
val DarkDanger = Color(0xFFF4566B)

// ---------- Light ----------
val LightBg = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurface2 = Color(0xFFF3F4F6)
val LightSurface3 = Color(0xFFE8EAED)
val LightBorder = Color(0xFFE5E7EB)
val LightBorderStrong = Color(0xFFD1D5DB)
val LightText = Color(0xFF1A1A1A)
val LightMuted = Color(0xFF565B64)
val LightFaint = Color(0xFF6B7280)
val LightAccent = Color(0xFF16A34A)
val LightAccentHover = Color(0xFF15803D)
val LightAccentSoft = Color(0x1A16A34A)
val LightAccentContrast = Color(0xFFFFFFFF)
val LightWin = Color(0xFFB45309)
val LightWinSoft = Color(0x1AB45309)
val LightDanger = Color(0xFFE11D48)

/** The colours this app draws with, beyond Material's own roles. */
data class WaterlineColors(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val accent: Color,
    val accentHover: Color,
    val accentSoft: Color,
    val accentContrast: Color,
    val win: Color,
    val winSoft: Color,
    val danger: Color,
)

val DarkWaterlineColors = WaterlineColors(
    dark = true,
    bg = DarkBg, surface = DarkSurface, surface2 = DarkSurface2, surface3 = DarkSurface3,
    border = DarkBorder, borderStrong = DarkBorderStrong,
    text = DarkText, muted = DarkMuted, faint = DarkFaint,
    accent = DarkAccent, accentHover = DarkAccentHover, accentSoft = DarkAccentSoft,
    accentContrast = DarkAccentContrast,
    win = DarkWin, winSoft = DarkWinSoft, danger = DarkDanger,
)

val LightWaterlineColors = WaterlineColors(
    dark = false,
    bg = LightBg, surface = LightSurface, surface2 = LightSurface2, surface3 = LightSurface3,
    border = LightBorder, borderStrong = LightBorderStrong,
    text = LightText, muted = LightMuted, faint = LightFaint,
    accent = LightAccent, accentHover = LightAccentHover, accentSoft = LightAccentSoft,
    accentContrast = LightAccentContrast,
    win = LightWin, winSoft = LightWinSoft, danger = LightDanger,
)

/** Reachable anywhere as `Waterline.colors`. */
object Waterline {
    val colors: WaterlineColors
        @Composable @ReadOnlyComposable get() = LocalWaterlineColors.current
}
