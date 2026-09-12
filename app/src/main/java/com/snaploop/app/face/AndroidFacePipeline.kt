package com.snaploop.app.face

import android.content.Context
import android.graphics.Bitmap
import com.snaploop.app.core.DetectedEmbedding
import com.snaploop.app.core.FaceModelPolicy
import com.snaploop.app.core.SnapLoopException
import kotlin.math.abs

data class FacePipelineDiagnostics(
    val facesDetected: Int,
    val facesWithUsableLandmarks: Int,
    val alignmentFailures: Int,
    val embeddedCount: Int,
    val rejectionReasons: List<String>,
)

/** Production Android face pipeline mirroring iOS v5.2 gates and AuraFace embedding semantics. */
class AndroidFacePipeline(context: Context, fastDetection: Boolean = false) : AutoCloseable {
    private val aligner = MlKitFaceAligner(fastDetection = fastDetection)
    private val engine = AuraFaceEngine(context.applicationContext)

    suspend fun detectFaces(imageData: ByteArray): List<DetectedEmbedding> = process(imageData).first
    suspend fun detectFaces(bitmap: Bitmap): List<DetectedEmbedding> = process(bitmap).first

    suspend fun embeddingForSelfie(imageData: ByteArray): FloatArray {
        val alignment = aligner.diagnostics(imageData, 112)
        try {
            if (alignment.facesDetected == 0) throw SnapLoopException.InvalidData("No face detected")
            if (alignment.facesDetected != 1) throw SnapLoopException.InvalidData("Use a photo with only your face")
            if (alignment.facesWithUsableLandmarks != 1 || alignment.alignmentFailures != 0 || alignment.alignedFaces.size != 1) {
                throw SnapLoopException.InvalidData("Face landmarks could not be aligned")
            }
            val face = alignment.alignedFaces.single()
            validatePreModelGates(face)?.let { throw SnapLoopException.InvalidData(it) }
            return embed(face)
        } finally {
            alignment.alignedFaces.forEach { it.bitmap.recycle() }
        }
    }

    suspend fun diagnose(imageData: ByteArray): FacePipelineDiagnostics = process(imageData).second

    private suspend fun process(imageData: ByteArray): Pair<List<DetectedEmbedding>, FacePipelineDiagnostics> =
        processAlignment(aligner.diagnostics(imageData, 112))

    private suspend fun process(bitmap: Bitmap): Pair<List<DetectedEmbedding>, FacePipelineDiagnostics> =
        processAlignment(aligner.diagnostics(bitmap, 112))

    private fun processAlignment(alignment: FaceAlignmentDiagnostics): Pair<List<DetectedEmbedding>, FacePipelineDiagnostics> {
        val embeddings = mutableListOf<DetectedEmbedding>()
        val rejections = mutableListOf<String>()
        try {
            for (face in alignment.alignedFaces) {
                val rejection = validatePreModelGates(face)
                if (rejection != null) {
                    rejections += rejection
                    continue
                }
                try {
                    embeddings += DetectedEmbedding(embed(face), face.sizeFraction)
                } catch (_: Throwable) {
                    rejections += "embedding failed"
                }
            }
            return embeddings to FacePipelineDiagnostics(
                facesDetected = alignment.facesDetected,
                facesWithUsableLandmarks = alignment.facesWithUsableLandmarks,
                alignmentFailures = alignment.alignmentFailures,
                embeddedCount = embeddings.size,
                rejectionReasons = rejections,
            )
        } finally {
            alignment.alignedFaces.forEach { it.bitmap.recycle() }
        }
    }

    private fun validatePreModelGates(face: AlignedFace): String? {
        if (face.quality != null && face.quality < 0.15) return "capture quality below minimum"
        if (face.interocularPixels < 12.0) return "eye distance below 12px"
        if (face.yawDegrees?.let { abs(it) > FaceModelPolicy.MAXIMUM_RECOGNITION_YAW_DEGREES } == true) return "yaw outside allowed range"
        return null
    }

    private fun embed(face: AlignedFace): FloatArray {
        val pixels = IntArray(112 * 112)
        face.bitmap.getPixels(pixels, 0, 112, 0, 0, 112, 112)
        return engine.embedCanonical112(pixels)
    }

    override fun close() {
        aligner.close()
        engine.close()
    }
}
