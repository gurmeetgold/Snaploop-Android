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

    /**
     * Onboarding is a device-level pre-auth surface. Its visibility must not depend on the current
     * Firebase/session gate: a clean install with no completion marker shows onboarding even if an
     * authentication session is restored before the coordinator reaches AUTH.
     */
    fun shouldPresent(completed: Boolean): Boolean = !completed
}
