package com.snaploop.app.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.snaploop.app.domain.PhotoMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Android equivalent of pinned iOS PhotoBulkActions.
 *
 * Matched previews are loaded sequentially at the same 2560px working bound used by iOS so bulk
 * actions do not retain several full-resolution photos in memory. Android 10+ writes app-created
 * images through MediaStore/scoped storage. Android 8-9 uses the public Pictures directory and is
 * invoked only after the UI has obtained the legacy WRITE_EXTERNAL_STORAGE permission.
 */
internal class AndroidPhotoBulkActions(context: Context) {
    private val appContext = context.applicationContext
    private val loader = MatchedThumbnailLoader(appContext)

    suspend fun saveToPhotoLibrary(matches: List<PhotoMatch>): Int = withContext(Dispatchers.IO) {
        var saved = 0
        matches.forEachIndexed { index, match ->
            val bitmap = runCatching { loader.load(match.thumbnailPath, MAX_ACTION_PIXEL_SIZE) }.getOrNull()
                ?: return@forEachIndexed
            try {
                saveBitmap(bitmap, index)
                saved += 1
            } finally {
                bitmap.recycle()
            }
        }
        saved
    }

    suspend fun prepareShareUris(matches: List<PhotoMatch>): List<Uri> = withContext(Dispatchers.IO) {
        val directory = File(appContext.cacheDir, SHARE_DIRECTORY).apply { mkdirs() }
        directory.listFiles()?.forEach { file -> runCatching { file.delete() } }

        val uris = mutableListOf<Uri>()
        matches.forEachIndexed { index, match ->
            val bitmap = runCatching { loader.load(match.thumbnailPath, MAX_ACTION_PIXEL_SIZE) }.getOrNull()
                ?: return@forEachIndexed
            try {
                val file = File(directory, "SnapLoop_${System.currentTimeMillis()}_${index}_${UUID.randomUUID()}.jpg")
                FileOutputStream(file).use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                        "Photo could not be prepared for sharing"
                    }
                    output.fd.sync()
                }
                uris += FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    file,
                )
            } finally {
                bitmap.recycle()
            }
        }
        uris
    }

    private fun saveBitmap(bitmap: Bitmap, index: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveScoped(bitmap, index)
        } else {
            saveLegacy(bitmap, index)
        }
    }

    private fun saveScoped(bitmap: Bitmap, index: Int) {
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName(index))
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SnapLoop")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Photo Library could not create an image")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    "Photo could not be encoded"
                }
            } ?: error("Photo Library could not open the image")

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(bitmap: Bitmap, index: Int) {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "SnapLoop",
        ).apply {
            check(exists() || mkdirs()) { "Photo Library folder could not be created" }
        }
        val file = File(directory, displayName(index))
        try {
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    "Photo could not be encoded"
                }
                output.fd.sync()
            }
            MediaScannerConnection.scanFile(
                appContext,
                arrayOf(file.absolutePath),
                arrayOf("image/jpeg"),
                null,
            )
        } catch (t: Throwable) {
            runCatching { file.delete() }
            throw t
        }
    }

    private fun displayName(index: Int): String =
        "SnapLoop_${System.currentTimeMillis()}_${index}_${UUID.randomUUID()}.jpg"

    private companion object {
        const val MAX_ACTION_PIXEL_SIZE = 2560
        const val JPEG_QUALITY = 95
        const val SHARE_DIRECTORY = "shared-photos"
    }
}
