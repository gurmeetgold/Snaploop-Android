package com.snaploop.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Canonical Android representation of the production SnapLoop visual system.
 * Values intentionally mirror iOS Theme.swift so feature screens do not invent
 * their own approximations of the brand palette.
 */
object SnapColors {
    val Orange = Color(0xFFFF571A)
    val Coral = Color(0xFFFF304A)
    val CoralDeep = Color(0xFFF50F3D)
    val CoralSoft = Color(0xFFFFE6ED)
    val Peach = Color(0xFFFF6E24)
    val HotPink = Color(0xFFFF0070)
    val Magenta = Color(0xFFE800B8)
    val Lilac = Color(0xFF7D14FF)
    val LilacSoft = Color(0xFFF2E8FF)
    val Blue = Color(0xFF453BFF)
    val BlueSoft = Color(0xFFE8E8FF)
    val Mint = Color(0xFF0DCA75)
    val Amber = Color(0xFFFFA114)

    val Ink = Color(0xFF0B0D1A)
    val Canvas = Color(0xFFFFFEFF)
    val Surface = Color.White
    val ElevatedSurface = Color(0xFFFFFEFF)
    val SubtleSurface = Color(0xFFFAF8FD)
    val Divider = Color.Black.copy(alpha = 0.07f)
    val Secondary = Color(0xFF6B6670)

    val DarkInk = Color(0xFFF7F7FF)
    val DarkCanvas = Color(0xFF050612)
    val DarkSurface = Color(0xFF0B0E1B)
    val DarkElevatedSurface = Color(0xFF0F1221)
    val DarkSubtleSurface = Color(0xFF131626)
    val DarkDivider = Color.White.copy(alpha = 0.11f)
}

object SnapGradients {
    val Brand = Brush.linearGradient(
        listOf(
            SnapColors.Peach,
            SnapColors.Coral,
            SnapColors.HotPink,
            SnapColors.Magenta,
            SnapColors.Lilac,
            SnapColors.Blue,
        ),
    )

    val Coral = Brush.linearGradient(
        listOf(SnapColors.Peach, SnapColors.Coral, SnapColors.HotPink),
    )

    val Social = Brush.linearGradient(
        listOf(
            SnapColors.Coral,
            SnapColors.HotPink,
            SnapColors.Magenta,
            SnapColors.Lilac,
            SnapColors.Blue,
        ),
    )

    val Violet = Social
    val Gallery = Brand

    val SoftWash = Brush.linearGradient(
        listOf(
            Color(0xFFFFF2F7).copy(alpha = 0.76f),
            SnapColors.CoralSoft.copy(alpha = 0.54f),
            SnapColors.LilacSoft.copy(alpha = 0.52f),
            SnapColors.BlueSoft.copy(alpha = 0.42f),
        ),
    )
}

@Composable
fun SnapLoopTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = SnapColors.Coral,
            secondary = SnapColors.Lilac,
            tertiary = SnapColors.Blue,
            background = SnapColors.DarkCanvas,
            surface = SnapColors.DarkSurface,
            surfaceVariant = SnapColors.DarkSubtleSurface,
            onBackground = SnapColors.DarkInk,
            onSurface = SnapColors.DarkInk,
        )
    } else {
        lightColorScheme(
            primary = SnapColors.Coral,
            secondary = SnapColors.Lilac,
            tertiary = SnapColors.Blue,
            background = SnapColors.Canvas,
            surface = SnapColors.Surface,
            surfaceVariant = SnapColors.SubtleSurface,
            onBackground = SnapColors.Ink,
            onSurface = SnapColors.Ink,
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}
