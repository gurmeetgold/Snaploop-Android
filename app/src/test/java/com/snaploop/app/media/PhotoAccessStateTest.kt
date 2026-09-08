package com.snaploop.app.media

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoAccessStateTest {
    @Test
    fun android14_fullPermissionWinsOverSelectedPermission() {
        assertEquals(
            PhotoAccessLevel.FULL,
            PhotoAccessState.resolve(
                sdkInt = 34,
                fullGranted = true,
                selectedGranted = true,
                permissionRequested = true,
            ),
        )
    }

    @Test
    fun android14_selectedPermissionIsLimitedNotFull() {
        assertEquals(
            PhotoAccessLevel.SELECTED,
            PhotoAccessState.resolve(
                sdkInt = 34,
                fullGranted = false,
                selectedGranted = true,
                permissionRequested = true,
            ),
        )
    }

    @Test
    fun neverRequestedIsDifferentFromDenied() {
        assertEquals(
            PhotoAccessLevel.NOT_REQUESTED,
            PhotoAccessState.resolve(
                sdkInt = 34,
                fullGranted = false,
                selectedGranted = false,
                permissionRequested = false,
            ),
        )
        assertEquals(
            PhotoAccessLevel.DENIED,
            PhotoAccessState.resolve(
                sdkInt = 34,
                fullGranted = false,
                selectedGranted = false,
                permissionRequested = true,
            ),
        )
    }

    @Test
    fun preAndroid14_hasOnlyFullOrNoAccess() {
        assertEquals(
            PhotoAccessLevel.FULL,
            PhotoAccessState.resolve(
                sdkInt = 33,
                fullGranted = true,
                selectedGranted = false,
                permissionRequested = true,
            ),
        )
        assertEquals(
            PhotoAccessLevel.DENIED,
            PhotoAccessState.resolve(
                sdkInt = 33,
                fullGranted = false,
                selectedGranted = true,
                permissionRequested = true,
            ),
        )
    }
}
