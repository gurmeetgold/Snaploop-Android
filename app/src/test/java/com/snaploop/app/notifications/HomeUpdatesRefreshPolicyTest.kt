package com.snaploop.app.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUpdatesRefreshPolicyTest {
    @Test
    fun sameUserAndGenerationDoNotReload() {
        assertFalse(HomeUpdatesRefreshPolicy.shouldReload("user-1", 3, "user-1", 3))
    }

    @Test
    fun manualGenerationChangeReloads() {
        assertTrue(HomeUpdatesRefreshPolicy.shouldReload("user-1", 3, "user-1", 4))
    }

    @Test
    fun authenticatedUserChangeReloads() {
        assertTrue(HomeUpdatesRefreshPolicy.shouldReload("user-1", 3, "user-2", 3))
    }

    @Test
    fun missingCurrentUserNeverReloads() {
        assertFalse(HomeUpdatesRefreshPolicy.shouldReload("user-1", 3, null, 4))
        assertFalse(HomeUpdatesRefreshPolicy.shouldReload("user-1", 3, "   ", 4))
    }
}
