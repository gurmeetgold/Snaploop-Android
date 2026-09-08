package com.snaploop.app.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Explicit photo-library authorization model. A Boolean is insufficient on
 * Android 14+ because READ_MEDIA_VISUAL_USER_SELECTED grants access to only the
 * user's selected photos and must not be presented as full-library access.
 */
enum class PhotoAccessLevel {
    NOT_REQUESTED,
    DENIED,
    SELECTED,
    FULL;

    val canRead: Boolean get() = this == SELECTED || this == FULL
    val isLimited: Boolean get() = this == SELECTED
}

object PhotoAccessState {
    private const val PREFS = "snaploop.photo.access"
    private const val KEY_REQUESTED = "requested"

    fun requestPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun current(context: Context): PhotoAccessLevel {
        val fullGranted = when {
            Build.VERSION.SDK_INT >= 33 -> has(context, Manifest.permission.READ_MEDIA_IMAGES)
            else -> has(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val selectedGranted = Build.VERSION.SDK_INT >= 34 &&
            has(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        val requested = context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_REQUESTED, false)

        return resolve(
            sdkInt = Build.VERSION.SDK_INT,
            fullGranted = fullGranted,
            selectedGranted = selectedGranted,
            permissionRequested = requested,
        )
    }

    fun markRequested(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_REQUESTED, true)
            .apply()
    }

    /** Pure mapping kept public for JVM regression tests. */
    fun resolve(
        sdkInt: Int,
        fullGranted: Boolean,
        selectedGranted: Boolean,
        permissionRequested: Boolean,
    ): PhotoAccessLevel = when {
        fullGranted -> PhotoAccessLevel.FULL
        sdkInt >= 34 && selectedGranted -> PhotoAccessLevel.SELECTED
        permissionRequested -> PhotoAccessLevel.DENIED
        else -> PhotoAccessLevel.NOT_REQUESTED
    }

    private fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
