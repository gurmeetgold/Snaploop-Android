package com.snaploop.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CreateEventVisualParitySpecTest {
    @Test
    fun `create event page geometry stays pinned to ios reference`() {
        assertEquals(20, CreateEventVisualParitySpec.PAGE_PADDING_DP)
        assertEquals(18, CreateEventVisualParitySpec.CONTENT_SPACING_DP)
        assertEquals(20, CreateEventVisualParitySpec.HEADER_TITLE_SP)
        assertEquals(58, CreateEventVisualParitySpec.HERO_MARK_DP)
        assertEquals(28, CreateEventVisualParitySpec.HERO_TITLE_SP)
    }

    @Test
    fun `create event controls keep ios filled field and capsule treatment`() {
        assertEquals(18, CreateEventVisualParitySpec.INPUT_RADIUS_DP)
        assertEquals(0.075f, CreateEventVisualParitySpec.INPUT_FILL_ALPHA)
        assertEquals(16, CreateEventVisualParitySpec.CATEGORY_RADIUS_DP)
        assertEquals(22, CreateEventVisualParitySpec.DATE_CAPSULE_RADIUS_DP)
        assertEquals(24, CreateEventVisualParitySpec.CANCEL_RADIUS_DP)
        assertEquals(0.78f, CreateEventVisualParitySpec.CANCEL_BORDER_ALPHA)
    }
}
