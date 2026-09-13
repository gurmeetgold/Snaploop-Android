package com.snaploop.app.face

import android.content.Context
import android.content.res.AssetFileDescriptor
import com.snaploop.app.core.FaceModelPolicy
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest

/**
 * Maps the exact bundled AuraFace ONNX asset directly from the installed APK.
 *
 * Older builds copied the ~260 MB model into no-backup storage before creating an ONNX Runtime
 * session. That preserved heap safety, but it also left users with a second full copy of the model
 * after first Face Setup. The release now packages .onnx uncompressed so AssetManager can expose a
 * file descriptor and this class can memory-map the signed APK asset instead.
 *
 * The first successful mapping for a given model digest is SHA-256 verified and recorded with a
 * tiny marker. Subsequent process launches can skip re-hashing the immutable, app-signature-
 * protected asset while still using the exact same model bytes and embedding contract.
 */
object ModelAssetVerifier {
    const val ASSET_PATH = "models/glintr100.onnx"

    class VerifiedModelMapping internal constructor(
        val buffer: ByteBuffer,
        private val asset: AssetFileDescriptor,
        private val stream: FileInputStream,
        private val channel: FileChannel,
    ) : AutoCloseable {
        override fun close() {
            runCatching { channel.close() }
            runCatching { stream.close() }
            runCatching { asset.close() }
        }
    }

    fun verifiedModelMapping(context: Context): VerifiedModelMapping {
        val expected = FaceModelPolicy.SOURCE_MODEL_SHA256.lowercase()
        val markerDirectory = File(context.noBackupFilesDir, "face-models").apply { mkdirs() }
        val verifiedMarker = File(markerDirectory, "glintr100-$expected.apk-mmap-verified")

        val asset = try {
            context.assets.openFd(ASSET_PATH)
        } catch (t: Throwable) {
            throw IllegalStateException(
                "SnapLoop Face Engine model is not directly addressable in this build",
                t,
            )
        }
        val length = asset.length
        check(length > 0L && length <= Int.MAX_VALUE.toLong()) {
            "SnapLoop Face Engine model has an invalid size"
        }

        val stream = FileInputStream(asset.fileDescriptor)
        val channel = stream.channel
        try {
            val mapped = channel.map(FileChannel.MapMode.READ_ONLY, asset.startOffset, length)
            if (!verifiedMarker.isFile) {
                val actual = sha256(mapped.asReadOnlyBuffer())
                check(actual.equals(expected, ignoreCase = true)) {
                    "SnapLoop Face Engine model failed integrity validation"
                }
                verifiedMarker.writeText(expected)
            }
            mapped.position(0)
            return VerifiedModelMapping(mapped, asset, stream, channel)
        } catch (t: Throwable) {
            runCatching { channel.close() }
            runCatching { stream.close() }
            runCatching { asset.close() }
            throw t
        }
    }

    private fun sha256(buffer: ByteBuffer): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val scratch = ByteArray(64 * 1024)
        buffer.position(0)
        while (buffer.hasRemaining()) {
            val count = minOf(buffer.remaining(), scratch.size)
            buffer.get(scratch, 0, count)
            digest.update(scratch, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
