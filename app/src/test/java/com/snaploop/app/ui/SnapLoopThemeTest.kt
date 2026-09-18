package com.snaploop.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SnapLoopThemeTest {
    @Test
    fun `app font scale is slightly larger than previous compact setting`() {
        assertEquals(0.96f, APP_FONT_SCALE_MULTIPLIER)
    }
}
