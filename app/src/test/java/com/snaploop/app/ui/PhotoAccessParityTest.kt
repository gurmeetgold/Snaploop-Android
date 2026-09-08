package com.snaploop.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoAccessParityTest {
    @Test
    fun `full permission maps to all photos`() {
        assertEquals(PhotoAccessState.AUTHORIZED, PhotoAccessParity.resolve(34, true, true, true))
    }

    @Test
    fun `android 14 selected permission maps to limited`() {
        assertEquals(PhotoAccessState.LIMITED, PhotoAccessParity.resolve(34, false, true, true))
    }

    @Test
    fun `requested but not granted maps to denied`() {
        assertEquals(PhotoAccessState.DENIED, PhotoAccessParity.resolve(34, false, false, true))
    }

    @Test
    fun `never requested maps to not requested`() {
        assertEquals(PhotoAccessState.NOT_REQUESTED, PhotoAccessParity.resolve(34, false, false, false))
    }

    @Test
    fun `pre android 14 has no selected photos state`() {
        assertEquals(PhotoAccessState.NOT_REQUESTED, PhotoAccessParity.resolve(33, false, true, false))
    }
}
