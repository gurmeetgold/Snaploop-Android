package com.snaploop.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Memory-bounded matched-photo preview loader mirroring the iOS StorageThumbnailLoader contract.
 *
 * Direct Firebase Storage reads are attempted first. Production Storage rules can intentionally
 * reject a recipient's direct object read even though the backend has authorized that recipient;
 * in that case we use the same getMatchedThumbnail callable fallback as iOS.
 *
 * Compressed bytes are persisted in cacheDir and decoded with sampling, so a Gallery refresh never
 * needs to retain a multi-megabyte compressed blob plus a full-resolution Bitmap in the Java heap.
 */
internal class MatchedThumbnailLoader(
    context: Context,
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
) {
    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.cacheDir, "matched-thumbnails").apply { mkdirs() }

    private companion object {
        @Volatile var directStorageReadable: Boolean? = null
        val directStorageProbeMutex = Mutex()
    }

    suspend fun load(path: String?, maxPixelSize: Int = 720): Bitmap = withContext(Dispatchers.IO) {
        val normalizedPath = path?.trim().orEmpty()
        require(normalizedPath.isNotEmpty()) { "Matched photo has no preview path" }
        val target = maxPixelSize.coerceAtLeast(320)
        val cacheFile = cacheFile(normalizedPath)

        if (!cacheFile.isFile || cacheFile.length() <= 0L) {
            val bytes = fetchBytes(normalizedPath)
            require(bytes.isNotEmpty()) { "Matched photo preview is empty" }
            val temp = File(cacheDir, "${cacheFile.name}.tmp-${android.os.Process.myPid()}")
            try {
                FileOutputStream(temp).use { output ->
                    output.write(bytes)
                    output.fd.sync()
                }
                if (cacheFile.exists()) cacheFile.delete()
                check(temp.renameTo(cacheFile)) { "Could not cache matched photo preview" }
            } finally {
                temp.delete()
            }
        }

        decodeSampled(cacheFile, target)
            ?: run {
                cacheFile.delete()
                error("Matched photo preview could not be decoded")
            }
    }

    private suspend fun fetchBytes(path: String): ByteArray {
        return when (directStorageReadable) {
            false -> authorizedFallback(path)
            true -> directStorageOrFallback(path)
            null -> directStorageProbeMutex.withLock {
                // Re-check after waiting: another Gallery cell may have completed the probe.
                when (directStorageReadable) {
                    false -> authorizedFallback(path)
                    true -> directStorageOrFallback(path)
                    null -> directStorageOrFallback(path)
                }
            }
        }
    }

    private suspend fun directStorageOrFallback(path: String): ByteArray = try {
        // Published previews are already bounded by SnapLoop's thumbnail policy. Keep this cap
        // larger than iOS's current maximum to tolerate older rows during migration.
        storage.reference.child(path).getBytes(12L * 1024L * 1024L).await().also {
            directStorageReadable = true
        }
    } catch (error: Throwable) {
        // Production recipient rules can intentionally reject direct object reads. Remember
        // that authorization result process-wide so the other Gallery cells go straight to the
        // authorized callable instead of each waiting for an identical failing Storage request.
        if ((error as? StorageException)?.errorCode == StorageException.ERROR_NOT_AUTHORIZED) {
            directStorageReadable = false
        }
        authorizedFallback(path)
    }

    private suspend fun authorizedFallback(path: String): ByteArray {
        val parts = path.split('/').filter(String::isNotEmpty)
        require(
            parts.size == 6 &&
                parts[0] == "events" &&
                parts[2] == "photos" &&
                parts[5] == "thumbnail.jpg"
        ) { "Matched photo preview path is invalid" }

        val eventId = parts[1]
        val photoId = parts[4]
        val raw = functions.getHttpsCallable("getMatchedThumbnail")
            .call(mapOf("eventId" to eventId, "photoId" to photoId))
            .await().data
        val wrapper = raw as? Map<*, *> ?: error("Matched photo preview response is malformed")
        val encoded = wrapper["base64"] as? String
            ?: error("Matched photo preview response is missing image data")
        return Base64.decode(encoded, Base64.DEFAULT)
    }

    private fun cacheFile(path: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(path.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$digest.jpg")
    }

    private fun decodeSampled(file: File, maxPixelSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > maxPixelSize * 2) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            },
        ) ?: return null

        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= maxPixelSize) return decoded
        val scale = maxPixelSize.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            maxOf(1, (decoded.width * scale).toInt()),
            maxOf(1, (decoded.height * scale).toInt()),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }
}

private sealed interface ThumbnailLoadState {
    data object Loading : ThumbnailLoadState
    data class Ready(val bitmap: Bitmap) : ThumbnailLoadState
    data object Failed : ThumbnailLoadState
}

/** Reusable Gallery/Event photo surface with an explicit loading/error state instead of a silent placeholder. */
@Composable
internal fun MatchedThumbnailCell(
    path: String?,
    modifier: Modifier = Modifier,
    maxPixelSize: Int = 720,
) {
    val context = LocalContext.current
    val loader = remember(context) { MatchedThumbnailLoader(context) }
    val loadState by produceState<ThumbnailLoadState>(
        initialValue = ThumbnailLoadState.Loading,
        key1 = path,
        key2 = maxPixelSize,
    ) {
        value = ThumbnailLoadState.Loading
        value = runCatching { loader.load(path, maxPixelSize) }
            .fold(
                onSuccess = { ThumbnailLoadState.Ready(it) },
                onFailure = { ThumbnailLoadState.Failed },
            )
    }

    Box(
        modifier = modifier.background(Color(0xFFF5F0F8)),
        contentAlignment = Alignment.Center,
    ) {
        when (val state = loadState) {
            ThumbnailLoadState.Loading -> CircularProgressIndicator()
            ThumbnailLoadState.Failed -> Icon(
                imageVector = Icons.Filled.BrokenImage,
                contentDescription = "Matched photo unavailable",
                tint = Color(0xFF777078),
            )
            is ThumbnailLoadState.Ready -> Image(
                bitmap = state.bitmap.asImageBitmap(),
                contentDescription = "Matched Event photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
