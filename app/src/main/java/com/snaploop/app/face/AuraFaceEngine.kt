package com.snaploop.app.face

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.snaploop.app.core.Embeddings
import com.snaploop.app.core.FaceModelPolicy
import java.nio.FloatBuffer

/** Exact preprocessing used by the iOS Core ML conversion: BGR and (pixel - 127.5) / 128.0. */
internal object AuraFacePreprocessor {
    private const val SIDE = 112
    private const val PLANE = SIDE * SIDE

    fun toBgrChw(rgb: IntArray): FloatArray {
        require(rgb.size == PLANE)
        val chw = FloatArray(3 * PLANE)
        for (i in rgb.indices) {
            val pixel = rgb[i]
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            chw[i] = (b - 127.5f) / 128.0f
            chw[PLANE + i] = (g - 127.5f) / 128.0f
            chw[2 * PLANE + i] = (r - 127.5f) / 128.0f
        }
        return chw
    }
}

/**
 * Accepts an already canonical 112x112 RGB crop. Detection/alignment lives outside this class.
 *
 * AuraFace is a large model. Creating a new ONNX Runtime session for every guided capture caused
 * severe heap/native-memory pressure on lower-memory Android devices. All AndroidFacePipeline
 * instances now share one disk-backed session for the lifetime of the app process. Inference is
 * serialized because Face Setup and an Event scan never need concurrent identity inference, and
 * this keeps peak memory predictable on OEM devices such as Redmi/Xiaomi.
 */
class AuraFaceEngine(context: Context) : AutoCloseable {
    private val shared = SharedSession.get(context.applicationContext)

    fun embedCanonical112(rgb: IntArray): FloatArray = synchronized(shared.inferenceLock) {
        val chw = AuraFacePreprocessor.toBgrChw(rgb)
        OnnxTensor.createTensor(
            shared.env,
            FloatBuffer.wrap(chw),
            longArrayOf(1, 3, 112, 112),
        ).use { tensor ->
            shared.session.run(mapOf(shared.inputName to tensor)).use { result ->
                val raw = when (val value = result[0].value) {
                    is Array<*> -> (value[0] as FloatArray)
                    is FloatArray -> value
                    else -> error("Unexpected AuraFace output type")
                }
                require(raw.size == FaceModelPolicy.EMBEDDING_DIMENSION)
                Embeddings.normalize(raw) ?: error("Invalid AuraFace embedding")
            }
        }
    }

    /** Shared process session is intentionally released by process teardown, not per pipeline. */
    override fun close() = Unit

    private class SharedSession private constructor(
        val env: OrtEnvironment,
        val session: OrtSession,
        val inputName: String,
    ) {
        val inferenceLock = Any()

        companion object {
            @Volatile private var instance: SharedSession? = null

            fun get(context: Context): SharedSession =
                instance ?: synchronized(this) {
                    instance ?: create(context).also { instance = it }
                }

            private fun create(context: Context): SharedSession {
                val env = OrtEnvironment.getEnvironment()
                val model = ModelAssetVerifier.verifiedModelFile(context)
                val options = OrtSession.SessionOptions().apply {
                    // Face matching is latency-sensitive but not throughput-sensitive. Limiting CPU
                    // workers avoids large transient thread/workspace allocations on mobile.
                    setIntraOpNumThreads(1)
                    setInterOpNumThreads(1)
                }
                val session = try {
                    env.createSession(model.absolutePath, options)
                } finally {
                    options.close()
                }
                val inputName = session.inputNames.singleOrNull()
                    ?: error("Unexpected AuraFace input signature")
                val output = session.outputInfo.values.singleOrNull()
                    ?: error("Unexpected AuraFace output signature")
                require(output.info.toString().contains("512")) { "AuraFace output must be 512-D" }
                return SharedSession(env, session, inputName)
            }
        }
    }
}
