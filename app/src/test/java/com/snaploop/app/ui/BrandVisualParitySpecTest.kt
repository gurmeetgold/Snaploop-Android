package com.snaploop.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BrandVisualParitySpecTest {
    @Test
    fun `brand mark geometry matches pinned iOS contract`() {
        assertEquals(30, BrandVisualParitySpec.COMPACT_MARK_DP)
        assertEquals(48, BrandVisualParitySpec.REGULAR_MARK_DP)
        assertEquals(0.22f, BrandVisualParitySpec.MARK_CORNER_RATIO)
        assertEquals(0.10f, BrandVisualParitySpec.MARK_SHADOW_RATIO)
        assertEquals(0.24f, BrandVisualParitySpec.MARK_SHADOW_ALPHA)
    }

    @Test
    fun `wordmark typography matches pinned iOS default sizes`() {
        assertEquals(17, BrandVisualParitySpec.COMPACT_WORDMARK_SP)
        assertEquals(34, BrandVisualParitySpec.REGULAR_WORDMARK_SP)
    }

    @Test
    fun `premium card geometry and shadow match pinned iOS`() {
        assertEquals(24, BrandVisualParitySpec.PREMIUM_CARD_RADIUS_DP)
        assertEquals(18, BrandVisualParitySpec.PREMIUM_CARD_SHADOW_DP)
        assertEquals(0.08f, BrandVisualParitySpec.PREMIUM_CARD_SHADOW_ALPHA)
    }

    @Test
    fun `primary button geometry and pressed state match pinned iOS`() {
        assertEquals(54, BrandVisualParitySpec.PRIMARY_BUTTON_HEIGHT_DP)
        assertEquals(19, BrandVisualParitySpec.PRIMARY_BUTTON_RADIUS_DP)
        assertEquals(14, BrandVisualParitySpec.PRIMARY_BUTTON_HORIZONTAL_CONTENT_PADDING_DP)
        assertEquals(12, BrandVisualParitySpec.PRIMARY_BUTTON_SHADOW_DP)
        assertEquals(4, BrandVisualParitySpec.PRIMARY_BUTTON_SHADOW_PRESSED_DP)
        assertEquals(0.22f, BrandVisualParitySpec.PRIMARY_BUTTON_SHADOW_ALPHA)
        assertEquals(0.10f, BrandVisualParitySpec.PRIMARY_BUTTON_SHADOW_PRESSED_ALPHA)
        assertEquals(0.985f, BrandVisualParitySpec.PRIMARY_BUTTON_PRESSED_SCALE)
        assertEquals(160, BrandVisualParitySpec.PRIMARY_BUTTON_PRESS_ANIMATION_MS)
    }

    @Test
    fun `brand background glow opacities match pinned iOS`() {
        assertEquals(0.16f, BrandVisualParitySpec.DARK_LILAC_GLOW_ALPHA)
        assertEquals(0.12f, BrandVisualParitySpec.DARK_HOT_PINK_GLOW_ALPHA)
        assertEquals(0.055f, BrandVisualParitySpec.LIGHT_HOT_PINK_GLOW_ALPHA)
        assertEquals(0.045f, BrandVisualParitySpec.LIGHT_LILAC_GLOW_ALPHA)
    }
}
