package com.snaploop.app.face

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.snaploop.app.core.Embeddings
import com.snaploop.app.core.FaceModelPolicy
import java.nio.FloatBuffer

/** Accepts an already canonical 112x112 RGB crop. Detection/alignment lives outside this class. */
class AuraFaceEngine(context: Context) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String

    init {
        val bytes = ModelAssetVerifier.readVerified(context)
        session = env.createSession(bytes, OrtSession.SessionOptions())
        inputName = session.inputNames.singleOrNull() ?: error("Unexpected AuraFace input signature")
        val output = session.outputInfo.values.singleOrNull() ?: error("Unexpected AuraFace output signature")
        require(output.info.toString().contains("512")) { "AuraFace output must be 512-D" }
    }

    fun embedCanonical112(rgb: IntArray): FloatArray {
        require(rgb.size == 112 * 112)
        val chw = FloatArray(3 * 112 * 112)
        for (i in rgb.indices) {
            val p = rgb[i]
            val r = (p shr 16) and 0xff; val g = (p shr 8) and 0xff; val b = p and 0xff
            chw[i] = (r - 127.5f) / 127.5f
            chw[112 * 112 + i] = (g - 127.5f) / 127.5f
            chw[2 * 112 * 112 + i] = (b - 127.5f) / 127.5f
        }
        OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), longArrayOf(1,3,112,112)).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val raw = when (val value = result[0].value) {
                    is Array<*> -> (value[0] as FloatArray)
                    is FloatArray -> value
                    else -> error("Unexpected AuraFace output type")
                }
                require(raw.size == FaceModelPolicy.EMBEDDING_DIMENSION)
                return Embeddings.normalize(raw) ?: error("Invalid AuraFace embedding")
            }
        }
    }

    override fun close() { session.close() }
}
