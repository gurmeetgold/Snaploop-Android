package com.snaploop.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared visual primitives backed only by the canonical SnapLoop theme tokens. */
@Composable
internal fun ParityBrandBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background == SnapColors.DarkCanvas
    val background = if (dark) {
        Brush.linearGradient(
            listOf(
                SnapColors.DarkCanvas,
                SnapColors.DarkSurface,
                SnapColors.DarkCanvas,
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White,
                Color(0xFFFFF9FB),
                Color(0xFFFBF7FF),
                Color.White,
            ),
        )
    }
    Box(modifier.fillMaxSize().background(background), content = content)
}

/**
 * In-app mark container. The production raster mark will replace the centered
 * glyph without changing any feature-screen geometry.
 */
@Composable
internal fun ParityBrandMark(size: Int) {
    val shape = RoundedCornerShape((size * 0.22f).dp)
    Box(
        Modifier
            .size(size.dp)
            .shadow((size * 0.10f).dp, shape, ambientColor = SnapColors.HotPink.copy(alpha = 0.20f), spotColor = SnapColors.HotPink.copy(alpha = 0.20f))
            .background(SnapGradients.Brand, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "S",
            color = Color.White,
            fontSize = (size * 0.52f).sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
internal fun ParityBrandWordmark(compact: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 9.dp),
    ) {
        ParityBrandMark(if (compact) 30 else 48)
        Text(
            "SnapLoop",
            fontSize = if (compact) 20.sp else 34.sp,
            fontWeight = FontWeight.Black,
            color = SnapColors.Coral,
        )
    }
}

@Composable
internal fun ParityPremiumCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = SnapColors.HotPink.copy(alpha = 0.06f), spotColor = SnapColors.HotPink.copy(alpha = 0.08f))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f), shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
internal fun ParityPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    if (enabled) SnapGradients.Brand
                    else Brush.linearGradient(listOf(Color(0xFFB9B9C0), Color(0xFF8F8F98))),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
            )
        }
    }
}
