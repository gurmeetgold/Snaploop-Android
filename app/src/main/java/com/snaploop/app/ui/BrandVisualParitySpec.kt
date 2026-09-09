package com.snaploop.app.ui

/**
 * Stable geometry/interaction contract mirrored from the pinned iOS production UI.
 * Keeping these values out of composables makes visual parity reviewable and protects
 * them with ordinary JVM tests instead of allowing screen-local magic numbers to drift.
 */
internal object BrandVisualParitySpec {
    const val COMPACT_MARK_DP = 30
    const val REGULAR_MARK_DP = 48
    const val HOME_MARK_DP = 46
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

    // Pinned iOS MainTabView uses the standard TabView bar with a 0.97 surface.
    const val MAIN_TAB_COUNT = 3
    const val MAIN_TAB_ITEM_HEIGHT_DP = 64
    const val MAIN_TAB_SURFACE_ALPHA = 0.97f

    // Pinned iOS HomeView uses standard 16pt horizontal padding and 150pt action cards.
    const val HOME_HORIZONTAL_PADDING_DP = 16
    const val HOME_ACTION_CARD_MIN_HEIGHT_DP = 150

    // Pinned iOS SettingsView icon badges and Photo Access actions.
    const val SETTINGS_ICON_BADGE_DP = 36
    const val SETTINGS_ICON_BADGE_RADIUS_DP = 11
    const val SETTINGS_PHOTO_ACTION_HEIGHT_DP = 48
    const val SETTINGS_PHOTO_ACTION_RADIUS_DP = 16

    // Pinned iOS EventDashboardView hero geometry.
    const val EVENT_HERO_HEIGHT_DP = 230
    const val EVENT_HERO_RADIUS_DP = 30
    const val EVENT_HERO_SHADOW_DP = 18
    const val EVENT_HERO_SHADOW_ALPHA = 0.18f
    const val EVENT_MEMBER_AVATAR_DP = 42
    const val EVENT_MEMBER_AVATAR_BORDER_DP = 2

    // Pinned iOS Theme.GradientTile defaults.
    const val EVENT_FEATURE_TILE_HEIGHT_DP = 118
    const val EVENT_FEATURE_TILE_RADIUS_DP = 22
    const val EVENT_FEATURE_TILE_ICON_DP = 40
    const val EVENT_FEATURE_TILE_ICON_RADIUS_DP = 12
    const val EVENT_FEATURE_TILE_DECORATION_DP = 100
    const val EVENT_FEATURE_TILE_DECORATION_OFFSET_X_DP = 78
    const val EVENT_FEATURE_TILE_DECORATION_OFFSET_Y_DP = -38
    const val EVENT_FEATURE_TILE_SHADOW_DP = 14
    const val EVENT_FEATURE_TILE_SHADOW_Y_DP = 8
    const val EVENT_FEATURE_TILE_SHADOW_ALPHA = 0.18f

    const val DARK_LILAC_GLOW_ALPHA = 0.16f
    const val DARK_HOT_PINK_GLOW_ALPHA = 0.12f
    const val LIGHT_HOT_PINK_GLOW_ALPHA = 0.055f
    const val LIGHT_LILAC_GLOW_ALPHA = 0.045f
}
