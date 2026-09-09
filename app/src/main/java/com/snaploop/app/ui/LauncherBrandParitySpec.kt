package com.snaploop.app.ui

/**
 * Pure launcher-brand contract derived from the pinned iOS AppIcon source.
 *
 * The Android runtime resource is intentionally generated from the exact
 * 1024×1024 pinned AppIcon supplied by the product owner. Keep this contract
 * separate from the in-app SnapLoopBrandMark raster, which is a distinct iOS
 * asset and must not be silently substituted.
 */
object LauncherBrandParitySpec {
    const val SOURCE_PIXEL_SIZE = 1024
    const val ANDROID_RUNTIME_PIXEL_SIZE = 192
    const val RESOURCE_NAME = "snaploop_app_icon"
    const val PINNED_IOS_APP_ICON_GIT_BLOB = "4e9c538553ef26346fe856b41c06518abab680d7"

    fun isRuntimeSizeSufficientForXxxhdpiLauncher(): Boolean =
        ANDROID_RUNTIME_PIXEL_SIZE >= 48 * 4
}
