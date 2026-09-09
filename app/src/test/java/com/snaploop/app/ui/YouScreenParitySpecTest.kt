package com.snaploop.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class YouScreenParitySpecTest {
    @Test
    fun `you screen geometry stays pinned to iOS settings`() {
        assertEquals(20, YouScreenParitySpec.HORIZONTAL_PADDING_DP)
        assertEquals(16, YouScreenParitySpec.VERTICAL_PADDING_DP)
        assertEquals(18, YouScreenParitySpec.SECTION_SPACING_DP)
        assertEquals(66, YouScreenParitySpec.PROFILE_THUMBNAIL_DP)
        assertEquals(34, YouScreenParitySpec.PROFILE_MARK_DP)
        assertEquals(15, YouScreenParitySpec.PROFILE_METADATA_ICON_DP)
        assertEquals(15, YouScreenParitySpec.PROFILE_STATUS_ICON_DP)
        assertEquals(7, YouScreenParitySpec.PROFILE_METADATA_SPACING_DP)
        assertEquals(36, YouScreenParitySpec.ICON_BADGE_DP)
        assertEquals(11, YouScreenParitySpec.ICON_BADGE_RADIUS_DP)
        assertEquals(8, YouScreenParitySpec.ACCOUNT_ROW_VERTICAL_PADDING_DP)
        assertEquals(48, YouScreenParitySpec.ACCOUNT_DIVIDER_START_DP)
    }
}
