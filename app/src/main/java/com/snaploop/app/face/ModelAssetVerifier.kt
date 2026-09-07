package com.snaploop.app.face

import android.content.Context
import com.snaploop.app.core.FaceModelPolicy
import java.io.File
import java.security.MessageDigest

/**
 * Materializes the bundled AuraFace model without ever holding the ~260 MB ONNX file in a
 * Java/Kotlin ByteArray. Loading the whole asset with readBytes() exhausted the managed heap on
 * real Redmi devices while Face Setup was also holding CameraX/ML Kit frames.
 *
 * The verified copy lives in no-backup app storage and is named by the expected SHA-256. A partial
 * or failed copy is written to a temporary file and never becomes visible as the canonical model.
 */
object ModelAssetVerifier {
    const val ASSET_PATH = "models/glintr100.onnx"

    fun verifiedModelFile(context: Context): File {
        val expected = FaceModelPolicy.SOURCE_MODEL_SHA256.lowercase()
        val directory = File(context.noBackupFilesDir, "face-models").apply { mkdirs() }
        val target = File(directory, "glintr100-$expected.onnx")

        // The app sandbox is private and the digest is embedded in the immutable filename. Once a
        // verified copy has been atomically promoted, re-hashing 260 MB on every Face Setup is both
        // unnecessary and slow.
        if (target.isFile && target.length() > 0L) return target

        val temp = File(directory, "${target.name}.tmp-${android.os.Process.myPid()}")
        temp.delete()

        val digest = MessageDigest.getInstance("SHA-256")
        try {
            context.assets.open(ASSET_PATH).buffered(64 * 1024).use { input ->
                temp.outputStream().buffered(64 * 1024).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            }

            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            check(actual.equals(expected, ignoreCase = true)) {
                "SnapLoop Face Engine model failed integrity validation"
            }
            check(temp.length() > 0L) { "SnapLoop Face Engine model is empty" }

            if (target.exists()) target.delete()
            check(temp.renameTo(target)) {
                "SnapLoop Face Engine model could not be prepared"
            }
            return target
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
    }
}
