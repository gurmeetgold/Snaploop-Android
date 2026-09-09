package com.snaploop.app.notifications

/**
 * Pure client-side mirror of the backend `registerPushToken` callable schema.
 *
 * The backend branch `fix/android-push-platform-contract` normalizes `android`
 * to the Android delivery path and derives the user ID from callable auth.
 */
internal object SnapLoopPushRegistrationContract {
    const val TOKEN_FIELD = "token"
    const val PLATFORM_FIELD = "platform"
    const val APP_BUNDLE_ID_FIELD = "appBundleId"
    const val ANDROID_PLATFORM = "android"

    fun payload(token: String, appBundleId: String): Map<String, String> = mapOf(
        TOKEN_FIELD to token,
        PLATFORM_FIELD to ANDROID_PLATFORM,
        APP_BUNDLE_ID_FIELD to appBundleId,
    )
}
