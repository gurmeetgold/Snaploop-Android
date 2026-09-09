package com.snaploop.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SnapLoopPushRegistrationContractTest {
    @Test
    fun `android payload matches authoritative backend callable schema`() {
        val payload = SnapLoopPushRegistrationContract.payload(
            token = "fcm-token-value",
            appBundleId = "com.snaploop.app",
        )

        assertEquals(
            mapOf(
                "token" to "fcm-token-value",
                "platform" to "android",
                "appBundleId" to "com.snaploop.app",
            ),
            payload,
        )
        assertFalse(payload.containsKey("userId"))
        assertFalse(payload.containsKey("fcmToken"))
        assertFalse(payload.containsValue("ios"))
    }
}
