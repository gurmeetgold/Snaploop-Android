package com.snaploop.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ShareEventParitySpecTest {
    @Test
    fun `share event geometry stays pinned to iOS`() {
        assertEquals(64, ShareEventParitySpec.BRAND_MARK_DP)
        assertEquals(18, ShareEventParitySpec.CONTENT_SPACING_DP)
        assertEquals(220, ShareEventParitySpec.QR_SIZE_DP)
        assertEquals(50, ShareEventParitySpec.SECONDARY_ACTION_HEIGHT_DP)
        assertEquals(17, ShareEventParitySpec.SECONDARY_ACTION_RADIUS_DP)
        assertEquals(42, ShareEventParitySpec.PHONE_ICON_WELL_DP)
        assertEquals(1_500L, ShareEventParitySpec.COPY_FEEDBACK_DURATION_MS)
    }

    @Test
    fun `share event exposes only pinned copy actions`() {
        assertEquals(
            listOf(ShareEventParitySpec.CopyAction.CODE, ShareEventParitySpec.CopyAction.LINK),
            ShareEventParitySpec.CopyAction.entries,
        )
        assertFalse(ShareEventParitySpec.CopyAction.entries.any { it.name.contains("FULL") })
    }

    @Test
    fun `copy confirmation title matches iOS`() {
        assertEquals("Copy Code", ShareEventParitySpec.actionTitle(ShareEventParitySpec.CopyAction.CODE, false))
        assertEquals("Copy Link", ShareEventParitySpec.actionTitle(ShareEventParitySpec.CopyAction.LINK, false))
        assertEquals("Copied", ShareEventParitySpec.actionTitle(ShareEventParitySpec.CopyAction.CODE, true))
        assertEquals("Copied", ShareEventParitySpec.actionTitle(ShareEventParitySpec.CopyAction.LINK, true))
    }

    @Test
    fun `phone invite copy stays pinned`() {
        assertEquals("Invite by Phone or Contacts", ShareEventParitySpec.PHONE_INVITE_TITLE)
        assertEquals(
            "Existing users get an in-app invite; others can receive the link.",
            ShareEventParitySpec.PHONE_INVITE_SUBTITLE,
        )
    }
}
