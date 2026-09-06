package com.snaploop.app.face

import android.content.Context
import com.snaploop.app.core.FaceModelPolicy
import java.security.MessageDigest

object ModelAssetVerifier {
    const val ASSET_PATH = "models/glintr100.onnx"
    fun readVerified(context: Context): ByteArray {
        val bytes = context.assets.open(ASSET_PATH).use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(digest.equals(FaceModelPolicy.SOURCE_MODEL_SHA256, ignoreCase = true)) {
            "SnapLoop Face Engine model failed integrity validation"
        }
        return bytes
    }
}
