package com.snaploop.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/** Shared visual primitives backed only by the canonical SnapLoop theme tokens. */
@Composable
internal fun ParityBrandBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background == SnapColors.DarkCanvas
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val longestSide = max(size.width, size.height)
                val base = if (dark) {
                    Brush.linearGradient(listOf(SnapColors.DarkCanvas, SnapColors.DarkCanvas))
                } else {
                    Brush.linearGradient(
                        listOf(
                            Color.White,
                            Color(0xFFFFF9FB),
                            Color(0xFFFBF7FF),
                            Color.White,
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                }
                val topRightGlow = Brush.radialGradient(
                    colors = if (dark) {
                        listOf(
                            SnapColors.Lilac.copy(alpha = BrandVisualParitySpec.DARK_LILAC_GLOW_ALPHA),
                            Color.Transparent,
                        )
                    } else {
                        listOf(
                            SnapColors.Lilac.copy(alpha = BrandVisualParitySpec.LIGHT_LILAC_GLOW_ALPHA),
                            Color.Transparent,
                        )
                    },
                    center = Offset(size.width, 0f),
                    radius = longestSide * if (dark) 0.72f else 0.58f,
                )
                val bottomLeftGlow = Brush.radialGradient(
                    colors = if (dark) {
                        listOf(
                            SnapColors.HotPink.copy(alpha = BrandVisualParitySpec.DARK_HOT_PINK_GLOW_ALPHA),
                            Color.Transparent,
                        )
                    } else {
                        listOf(
                            SnapColors.HotPink.copy(alpha = BrandVisualParitySpec.LIGHT_HOT_PINK_GLOW_ALPHA),
                            Color.Transparent,
                        )
                    },
                    center = Offset(0f, size.height),
                    radius = longestSide * if (dark) 0.70f else 0.58f,
                )
                onDrawBehind {
                    drawRect(base)
                    drawRect(topRightGlow)
                    drawRect(bottomLeftGlow)
                }
            },
        content = content,
    )
}

/**
 * In-app mark container.
 *
 * The pinned iOS app uses the canonical SnapLoopBrandMark.png raster here. Android
 * keeps the exact iOS geometry/shadow contract in this primitive; the temporary
 * center glyph is intentionally isolated to this one function until that >1 MiB
 * canonical raster is copied into Android resources without recompression.
 */
@Composable
internal fun ParityBrandMark(size: Int) {
    val shape = RoundedCornerShape((size * BrandVisualParitySpec.MARK_CORNER_RATIO).dp)
    Box(
        Modifier
            .size(size.dp)
            .shadow(
                (size * BrandVisualParitySpec.MARK_SHADOW_RATIO).dp,
                shape,
                ambientColor = SnapColors.HotPink.copy(alpha = BrandVisualParitySpec.MARK_SHADOW_ALPHA),
                spotColor = SnapColors.HotPink.copy(alpha = BrandVisualParitySpec.MARK_SHADOW_ALPHA),
            )
            .background(SnapGradients.Brand, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "S",
            color = Color.White,
            fontSize = (size * 0.52f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun ParityBrandWordmark(compact: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 9.dp),
    ) {
        ParityBrandMark(
            if (compact) BrandVisualParitySpec.COMPACT_MARK_DP
            else BrandVisualParitySpec.REGULAR_MARK_DP,
        )
        Text(
            text = "SnapLoop",
            style = TextStyle(
                brush = SnapGradients.Brand,
                fontSize = (
                    if (compact) BrandVisualParitySpec.COMPACT_WORDMARK_SP
                    else BrandVisualParitySpec.REGULAR_WORDMARK_SP
                ).sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
internal fun ParityPremiumCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(BrandVisualParitySpec.PREMIUM_CARD_RADIUS_DP.dp)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                BrandVisualParitySpec.PREMIUM_CARD_SHADOW_DP.dp,
                shape,
                ambientColor = SnapColors.HotPink.copy(
                    alpha = BrandVisualParitySpec.PREMIUM_CARD_SHADOW_ALPHA,
                ),
                spotColor = SnapColors.HotPink.copy(
                    alpha = BrandVisualParitySpec.PREMIUM_CARD_SHADOW_ALPHA,
                ),
            )
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
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) BrandVisualParitySpec.PRIMARY_BUTTON_PRESSED_SCALE else 1f,
        animationSpec = tween(BrandVisualParitySpec.PRIMARY_BUTTON_PRESS_ANIMATION_MS),
        label = "snaploop-primary-button-scale",
    )
    val shape = RoundedCornerShape(BrandVisualParitySpec.PRIMARY_BUTTON_RADIUS_DP.dp)
    val shadowSize = if (pressed && enabled) {
        BrandVisualParitySpec.PRIMARY_BUTTON_SHADOW_PRESSED_DP
    } else {
        BrandVisualParitySpec.PRIMARY_BUTTON_SHADOW_DP
    }
    val shadowAlpha = if (pressed && enabled) {
        BrandVisualParitySpec.PRIMARY_BUTTON_SHADOW_PRESSED_ALPHA
    } else {
        BrandVisualParitySpec.PRIMARY_BUTTON_SHADOW_ALPHA
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .height(BrandVisualParitySpec.PRIMARY_BUTTON_HEIGHT_DP.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                shadowSize.dp,
                shape,
                ambientColor = SnapColors.Coral.copy(alpha = if (enabled) shadowAlpha else 0f),
                spotColor = SnapColors.Coral.copy(alpha = if (enabled) shadowAlpha else 0f),
            ),
        shape = shape,
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
                )
                .padding(horizontal = BrandVisualParitySpec.PRIMARY_BUTTON_HORIZONTAL_CONTENT_PADDING_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
    }
}
