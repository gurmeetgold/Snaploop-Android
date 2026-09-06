package com.snaploop.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/**
 * Converts an OEM camera capture into a bounded, upright JPEG before it enters Face Setup.
 *
 * Modern phone cameras can produce 20-50+ MP JPEGs. Reading those files wholesale and then
 * decoding them can create large transient allocations. This helper reads image bounds first,
 * decodes with a power-of-two sample size, applies EXIF orientation, and recompresses the result.
 */
internal fun prepareFaceCapture(
    file: File,
    maxLongEdge: Int = 2048,
    jpegQuality: Int = 92,
): ByteArray? {
    if (!file.exists() || file.length() <= 0L) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    val longestEdge = max(bounds.outWidth, bounds.outHeight)
    while (longestEdge / sampleSize > maxLongEdge) {
        sampleSize *= 2
    }

    val decoded = BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    ) ?: return null

    val orientation = runCatching {
        ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val matrix = Matrix()
    val needsTransform = when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> { matrix.setScale(-1f, 1f); true }
        ExifInterface.ORIENTATION_ROTATE_180 -> { matrix.setRotate(180f); true }
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> { matrix.setRotate(180f); matrix.postScale(-1f, 1f); true }
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f); true }
        ExifInterface.ORIENTATION_ROTATE_90 -> { matrix.setRotate(90f); true }
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f); true }
        ExifInterface.ORIENTATION_ROTATE_270 -> { matrix.setRotate(-90f); true }
        else -> false
    }

    val upright = if (needsTransform) {
        runCatching {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        }.getOrElse { decoded }
    } else {
        decoded
    }

    return try {
        val output = ByteArrayOutputStream(512 * 1024)
        val compressed = upright.compress(
            Bitmap.CompressFormat.JPEG,
            jpegQuality.coerceIn(80, 95),
            output,
        )
        if (!compressed) null else output.toByteArray().takeIf { it.isNotEmpty() }
    } finally {
        if (upright !== decoded) upright.recycle()
        decoded.recycle()
    }
}
