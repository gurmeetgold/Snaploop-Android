package com.snaploop.app.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** A MediaStore row. contentUri is device-local and must never be serialized to Firebase. */
data class LocalPhotoAsset(
    val id: String,
    val contentUri: Uri,
    val creationDateMillis: Long,
    val modifiedDateMillis: Long,
    val mimeType: String?,
    val width: Int,
    val height: Int,
)

enum class PhotoAccess { AUTHORIZED, LIMITED, DENIED }

class MediaStorePhotoLibrary(private val context: Context) {
    private val resolver = context.contentResolver

    fun authorizationStatus(): PhotoAccess = when {
        Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED -> PhotoAccess.AUTHORIZED
        Build.VERSION.SDK_INT >= 34 && context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED -> PhotoAccess.LIMITED
        Build.VERSION.SDK_INT <= 32 && context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED -> PhotoAccess.AUTHORIZED
        else -> PhotoAccess.DENIED
    }

    fun runtimePermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun assets(startMillis: Long, endMillis: Long): List<LocalPhotoAsset> {
        require(endMillis >= startMillis)
        if (authorizationStatus() == PhotoAccess.DENIED) return emptyList()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        val startSeconds = startMillis / 1000
        val endSeconds = endMillis / 1000
        val selection = "(${MediaStore.Images.Media.DATE_TAKEN} BETWEEN ? AND ?) OR (${MediaStore.Images.Media.DATE_TAKEN} IS NULL AND ${MediaStore.Images.Media.DATE_ADDED} BETWEEN ? AND ?)"
        val args = arrayOf(startMillis.toString(), endMillis.toString(), startSeconds.toString(), endSeconds.toString())
        val result = mutableListOf<LocalPhotoAsset>()
        resolver.query(collection, projection, selection, args, "${MediaStore.Images.Media.DATE_TAKEN} ASC, ${MediaStore.Images.Media._ID} ASC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val taken = if (cursor.isNull(takenCol)) cursor.getLong(addedCol) * 1000 else cursor.getLong(takenCol)
                if (taken !in startMillis..endMillis) continue
                result += LocalPhotoAsset(
                    id = id.toString(),
                    contentUri = ContentUris.withAppendedId(collection, id),
                    creationDateMillis = taken,
                    modifiedDateMillis = cursor.getLong(modifiedCol) * 1000,
                    mimeType = cursor.getString(mimeCol),
                    width = cursor.getInt(widthCol),
                    height = cursor.getInt(heightCol),
                )
            }
        }
        return result
    }

    /** Downsamples and bakes EXIF orientation before face processing or preview publication. */
    fun normalizedJpeg(asset: LocalPhotoAsset, maxPixelSize: Int, quality: Int = 92): ByteArray {
        require(maxPixelSize > 0); require(quality in 1..100)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(asset.contentUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: error("Photo is no longer available")
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxPixelSize * 2) sample *= 2
        val bitmap = resolver.openInputStream(asset.contentUri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
        } ?: error("Unsupported or removed photo")
        val orientation = resolver.openInputStream(asset.contentUri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            ?: ExifInterface.ORIENTATION_NORMAL
        val upright = applyOrientation(bitmap, orientation)
        val scaled = scaleDown(upright, maxPixelSize)
        val output = ByteArrayOutputStream()
        check(scaled.compress(Bitmap.CompressFormat.JPEG, quality, output))
        if (scaled !== upright) scaled.recycle()
        if (upright !== bitmap) upright.recycle()
        bitmap.recycle()
        return output.toByteArray()
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val scale = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, maxOf(1, (bitmap.width * scale).toInt()), maxOf(1, (bitmap.height * scale).toInt()), true)
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { matrix.setRotate(180f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
