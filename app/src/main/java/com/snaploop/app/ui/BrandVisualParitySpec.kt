package com.snaploop.app.ui

/**
 * Stable geometry/interaction contract mirrored from the pinned iOS BrandVisuals.swift
 * and MyPicsTubeBrand.swift implementation. Keeping these values out of composables
 * makes visual parity reviewable and protects them with ordinary JVM tests.
 */
internal object BrandVisualParitySpec {
    const val COMPACT_MARK_DP = 30
    const val REGULAR_MARK_DP = 48
    const val MARK_CORNER_RATIO = 0.22f
    const val MARK_SHADOW_RATIO = 0.10f
    const val MARK_SHADOW_ALPHA = 0.24f

    // SwiftUI .headline is 17pt at the default Dynamic Type size.
    const val COMPACT_WORDMARK_SP = 17
    const val REGULAR_WORDMARK_SP = 34

    const val PREMIUM_CARD_RADIUS_DP = 24
    const val PREMIUM_CARD_SHADOW_DP = 18
    const val PREMIUM_CARD_SHADOW_ALPHA = 0.08f

    const val PRIMARY_BUTTON_HEIGHT_DP = 54
    const val PRIMARY_BUTTON_RADIUS_DP = 19
    const val PRIMARY_BUTTON_HORIZONTAL_CONTENT_PADDING_DP = 14
    const val PRIMARY_BUTTON_SHADOW_DP = 12
    const val PRIMARY_BUTTON_SHADOW_PRESSED_DP = 4
    const val PRIMARY_BUTTON_SHADOW_ALPHA = 0.22f
    const val PRIMARY_BUTTON_SHADOW_PRESSED_ALPHA = 0.10f
    const val PRIMARY_BUTTON_PRESSED_SCALE = 0.985f
    const val PRIMARY_BUTTON_PRESS_ANIMATION_MS = 160

    const val DARK_LILAC_GLOW_ALPHA = 0.16f
    const val DARK_HOT_PINK_GLOW_ALPHA = 0.12f
    const val LIGHT_HOT_PINK_GLOW_ALPHA = 0.055f
    const val LIGHT_LILAC_GLOW_ALPHA = 0.045f
}
