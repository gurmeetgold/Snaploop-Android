package com.snaploop.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class PhotoAccessState {
    NOT_REQUESTED,
    LIMITED,
    AUTHORIZED,
    DENIED,
}

/**
 * Android mapping for the four user-facing states used by pinned iOS SettingsView.
 * Android 14's selected-photos permission maps to iOS `.limited`.
 */
internal object PhotoAccessParity {
    private const val PREFS = "snaploop.photo_access"
    private const val KEY_REQUESTED = "requested"

    fun state(context: Context): PhotoAccessState {
        val fullPermission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val fullGranted = ContextCompat.checkSelfPermission(context, fullPermission) == PackageManager.PERMISSION_GRANTED
        val selectedGranted = Build.VERSION.SDK_INT >= 34 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        val requested = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_REQUESTED, false)
        return resolve(Build.VERSION.SDK_INT, fullGranted, selectedGranted, requested)
    }

    fun markRequested(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_REQUESTED, true)
            .apply()
    }

    fun requestPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun description(state: PhotoAccessState): String = when (state) {
        PhotoAccessState.AUTHORIZED -> "All Photos"
        PhotoAccessState.LIMITED -> "Selected Photos only — you can add more anytime"
        PhotoAccessState.DENIED -> "Photo access is off"
        PhotoAccessState.NOT_REQUESTED -> "Photo access has not been requested yet"
    }

    internal fun resolve(
        apiLevel: Int,
        fullGranted: Boolean,
        selectedGranted: Boolean,
        requested: Boolean,
    ): PhotoAccessState = when {
        fullGranted -> PhotoAccessState.AUTHORIZED
        apiLevel >= 34 && selectedGranted -> PhotoAccessState.LIMITED
        requested -> PhotoAccessState.DENIED
        else -> PhotoAccessState.NOT_REQUESTED
    }
}
