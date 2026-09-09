package com.snaploop.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneInviteVisualParitySpecTest {
    @Test
    fun `country and phone controls match pinned iOS geometry`() {
        assertEquals(58, PhoneInviteVisualParitySpec.COUNTRY_SELECTOR_HEIGHT_DP)
        assertEquals(58, PhoneInviteVisualParitySpec.PHONE_FIELD_HEIGHT_DP)
        assertEquals(15, PhoneInviteVisualParitySpec.CONTROL_RADIUS_DP)
        assertEquals(11, PhoneInviteVisualParitySpec.COUNTRY_HORIZONTAL_PADDING_DP)
        assertEquals(0.18f, PhoneInviteVisualParitySpec.COUNTRY_FILL_ALPHA)
    }

    @Test
    fun `contacts and disabled send treatment match pinned iOS`() {
        assertEquals(48, PhoneInviteVisualParitySpec.CONTACT_BUTTON_HEIGHT_DP)
        assertEquals(0.11f, PhoneInviteVisualParitySpec.CONTACT_FILL_ALPHA)
        assertEquals(0.50f, PhoneInviteVisualParitySpec.SEND_DISABLED_ALPHA)
    }

    @Test
    fun `invitation status treatment matches pinned iOS`() {
        assertEquals(34, PhoneInviteVisualParitySpec.STATUS_ICON_SIZE_DP)
        assertEquals(0.12f, PhoneInviteVisualParitySpec.STATUS_ICON_FILL_ALPHA)
        assertEquals(8, PhoneInviteVisualParitySpec.STATUS_CAPSULE_HORIZONTAL_PADDING_DP)
        assertEquals(5, PhoneInviteVisualParitySpec.STATUS_CAPSULE_VERTICAL_PADDING_DP)
        assertEquals(0.22f, PhoneInviteVisualParitySpec.STATUS_CAPSULE_FILL_ALPHA)
    }
}
