package com.snaploop.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherBrandParitySpecTest {
    @Test
    fun `launcher contract stays pinned to supplied iOS app icon`() {
        assertEquals(1024, LauncherBrandParitySpec.SOURCE_PIXEL_SIZE)
        assertEquals(192, LauncherBrandParitySpec.ANDROID_RUNTIME_PIXEL_SIZE)
        assertEquals("snaploop_app_icon", LauncherBrandParitySpec.RESOURCE_NAME)
        assertEquals(
            "4e9c538553ef26346fe856b41c06518abab680d7",
            LauncherBrandParitySpec.PINNED_IOS_APP_ICON_GIT_BLOB,
        )
        assertTrue(LauncherBrandParitySpec.isRuntimeSizeSufficientForXxxhdpiLauncher())
    }
}
