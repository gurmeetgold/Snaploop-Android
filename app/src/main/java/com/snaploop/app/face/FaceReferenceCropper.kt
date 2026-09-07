package com.snaploop.app.face

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * Produces the private, device-local Face Setup thumbnail/reference from the same canonical
 * five-landmark alignment used by AuraFace. The saved image contains the aligned face region rather
 * than the full camera frame, so shoulders/background are not retained in the visible reference.
 */
internal object FaceReferenceCropper {
    suspend fun crop(jpeg: ByteArray, outputSize: Int = 256): ByteArray? {
        if (jpeg.isEmpty()) return null
        return MlKitFaceAligner().use { aligner ->
            val diagnostics = aligner.diagnostics(jpeg, outputSize)
            val selected = diagnostics.alignedFaces.maxByOrNull { it.sizeFraction }
            if (selected == null) {
                diagnostics.alignedFaces.forEach { face ->
                    if (!face.bitmap.isRecycled) face.bitmap.recycle()
                }
                return@use null
            }

            try {
                ByteArrayOutputStream().use { output ->
                    if (!selected.bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) {
                        null
                    } else {
                        output.toByteArray()
                    }
                }
            } finally {
                diagnostics.alignedFaces.forEach { face ->
                    if (!face.bitmap.isRecycled) face.bitmap.recycle()
                }
            }
        }
    }
}