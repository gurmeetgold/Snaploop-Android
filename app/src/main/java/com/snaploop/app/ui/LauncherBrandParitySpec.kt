package com.snaploop.app.ui

/**
 * Pure Android brand-asset contract derived from the product-owner supplied
 * pinned iOS AppIcon artwork.
 *
 * Launcher and in-app resource names stay deliberately separate even while
 * both use the same supplied artwork. That keeps screen code independent from
 * any later recovery of the distinct pinned iOS SnapLoopBrandMark raster.
 */
object LauncherBrandParitySpec {
    const val SOURCE_PIXEL_SIZE = 1024
    const val ANDROID_RUNTIME_PIXEL_SIZE = 192
    const val RESOURCE_NAME = "snaploop_app_icon"
    const val IN_APP_RESOURCE_NAME = "snaploop_brand_mark"
    const val PINNED_IOS_APP_ICON_GIT_BLOB = "4e9c538553ef26346fe856b41c06518abab680d7"

    fun isRuntimeSizeSufficientForXxxhdpiLauncher(): Boolean =
        ANDROID_RUNTIME_PIXEL_SIZE >= 48 * 4

    fun usesSeparateSemanticResourceNames(): Boolean =
        RESOURCE_NAME != IN_APP_RESOURCE_NAME
}
