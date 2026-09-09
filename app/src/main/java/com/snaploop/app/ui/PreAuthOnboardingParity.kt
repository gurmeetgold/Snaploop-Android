package com.snaploop.app.ui

/** Device-level onboarding semantics mirrored from pinned iOS RootView. */
internal object PreAuthOnboardingParity {
    const val GLOBAL_KEY = "snaploop.onboarding.completed"
    private const val LEGACY_PREFIX = "onboarding."
    private const val LEGACY_SUFFIX = ".v1"

    fun hasCompleted(
        globalCompleted: Boolean,
        legacyEntries: Map<String, *>,
    ): Boolean {
        if (globalCompleted) return true
        return legacyEntries.any { (key, value) ->
            key.startsWith(LEGACY_PREFIX) &&
                key.endsWith(LEGACY_SUFFIX) &&
                value == true
        }
    }
}
