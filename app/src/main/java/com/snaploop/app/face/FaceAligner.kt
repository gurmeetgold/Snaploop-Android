package com.snaploop.app.face

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.snaploop.app.core.SnapLoopException
import com.snaploop.app.core.awaitResult
import java.io.ByteArrayInputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

internal data class AlignedFace(
    val bitmap: Bitmap,
    val sizeFraction: Double,
    val quality: Double?,
    val interocularPixels: Double,
    val yawDegrees: Double?,
    val pitchDegrees: Double?,
    val rollDegrees: Double?,
)

internal data class FaceAlignmentDiagnostics(
    val facesDetected: Int,
    val facesWithUsableLandmarks: Int,
    val alignmentFailures: Int,
    val alignedFaces: List<AlignedFace>,
)

/**
 * Canonical five-point ArcFace alignment on Android. ML Kit is used only for local face/landmark
 * detection; no image leaves the device. The bundled detector avoids a network dependency.
 */
internal class MlKitFaceAligner(
    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.04f)
            .build()
    ),
) : AutoCloseable {

    suspend fun diagnostics(imageData: ByteArray, outputSize: Int = 112): FaceAlignmentDiagnostics {
        val upright = decodeUpright(imageData) ?: throw SnapLoopException.InvalidData("Photo could not be decoded")
        val faces = try { detector.process(InputImage.fromBitmap(upright, 0)).awaitResult() }
        catch (t: Throwable) { upright.recycle(); throw SnapLoopException.Backend("face_detection_failed", "Face detection failed", t) }

        val shorter = max(1, min(upright.width, upright.height)).toDouble()
        val target = CanonicalFaceGeometry.targets(outputSize)
        val aligned = mutableListOf<AlignedFace>()
        var usable = 0
        var failures = 0

        for (face in faces) {
            val source = fivePoints(face)
            if (source == null) { failures += 1; continue }
            usable += 1
            val transform = CanonicalFaceGeometry.estimateSimilarity(source, target)
            if (transform == null) { failures += 1; continue }
            val rendered = render(upright, transform, outputSize)
            if (rendered == null) { failures += 1; continue }

            val eyes = source.take(2)
            val interocular = hypot(eyes[1].x - eyes[0].x, eyes[1].y - eyes[0].y)
            val facePixels = max(face.boundingBox.width(), face.boundingBox.height()).toDouble()
            aligned += AlignedFace(
                bitmap = rendered,
                sizeFraction = facePixels / shorter,
                // iOS capture quality is optional. ML Kit exposes no equivalent calibrated score,
                // so null intentionally follows the same iOS fail-safe branch that skips this optional gate.
                quality = null,
                interocularPixels = interocular,
                yawDegrees = face.headEulerAngleY.toDouble(),
                pitchDegrees = face.headEulerAngleX.toDouble(),
                rollDegrees = face.headEulerAngleZ.toDouble(),
            )
        }
        upright.recycle()
        return FaceAlignmentDiagnostics(faces.size, usable, failures, aligned)
    }

    private fun fivePoints(face: Face): List<Point2>? {
        fun point(type: Int): Point2? = face.getLandmark(type)?.position?.let { Point2(it.x.toDouble(), it.y.toDouble()) }
        val eyeA = point(FaceLandmark.LEFT_EYE) ?: return null
        val eyeB = point(FaceLandmark.RIGHT_EYE) ?: return null
        val nose = point(FaceLandmark.NOSE_BASE) ?: return null
        val mouthA = point(FaceLandmark.MOUTH_LEFT) ?: return null
        val mouthB = point(FaceLandmark.MOUTH_RIGHT) ?: return null
        val eyes = listOf(eyeA, eyeB).sortedBy { it.x }
        val mouth = listOf(mouthA, mouthB).sortedBy { it.x }
        return listOf(eyes[0], eyes[1], nose, mouth[0], mouth[1])
    }

    private fun render(source: Bitmap, transform: SimilarityTransform, outputSize: Int): Bitmap? = runCatching {
        val out = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        val matrix = Matrix().apply {
            setValues(floatArrayOf(
                transform.c.toFloat(), (-transform.s).toFloat(), transform.tx.toFloat(),
                transform.s.toFloat(), transform.c.toFloat(), transform.ty.toFloat(),
                0f, 0f, 1f,
            ))
        }
        canvas.drawBitmap(source, matrix, null)
        out
    }.getOrNull()

    private fun decodeUpright(bytes: ByteArray): Bitmap? {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        // Camera JPEGs from modern Android devices can be tens of megapixels. Decoding them at
        // full ARGB resolution can consume 100-250+ MB, and applying EXIF rotation may briefly
        // require another bitmap of comparable size. Bound the decode before ML Kit/ArcFace work.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        val longestEdge = max(bounds.outWidth, bounds.outHeight)
        while (longestEdge / sampleSize > MAX_DECODE_LONG_EDGE) {
            sampleSize *= 2
        }

        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return null

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { matrix.setRotate(180f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return decoded
        }
        return runCatching { Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true) }
            .onSuccess { if (it !== decoded) decoded.recycle() }
            .getOrElse { decoded }
    }

    override fun close() { detector.close() }

    private companion object {
        // 2K is sufficient for accurate ML Kit face landmarks while keeping bitmap memory bounded.
        const val MAX_DECODE_LONG_EDGE = 2048
    }
}
