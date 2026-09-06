package com.snaploop.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object SnapColors {
    val Orange=Color(0xFFFF571A); val Coral=Color(0xFFFF304A); val HotPink=Color(0xFFFF0070)
    val Magenta=Color(0xFFE800B8); val Lilac=Color(0xFF7D14FF); val Blue=Color(0xFF453BFF)
    val Ink=Color(0xFF0B0D1A); val Canvas=Color(0xFFFFFEFF); val Surface=Color.White
}

@Composable fun SnapLoopTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(primary=SnapColors.HotPink,secondary=SnapColors.Lilac)
    else lightColorScheme(primary=SnapColors.HotPink,secondary=SnapColors.Lilac,background=SnapColors.Canvas,surface=SnapColors.Surface,onBackground=SnapColors.Ink,onSurface=SnapColors.Ink)
    MaterialTheme(colorScheme=colors, content=content)
}
