package com.snaploop.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.snaploop.app.security.EncryptedFaceReferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Displays the encrypted, device-local Face Setup reference without publishing or copying it.
 * Decryption exists only in memory for this composable; the persisted JPEG remains in no-backup
 * storage protected by Android Keystore AES-GCM.
 */
@Composable
internal fun FaceReferenceThumbnail(
    userId: String?,
    fallbackInitial: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = userId,
    ) {
        value = withContext(Dispatchers.IO) {
            val uid = userId?.takeIf { it.isNotBlank() } ?: return@withContext null
            val bytes = EncryptedFaceReferenceStore(context.applicationContext).loadPreferred(uid)
                ?: return@withContext null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    Box(
        modifier = modifier.clip(CircleShape).background(Color(0xFFF0E8FF)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = "Your Face Setup photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                fallbackInitial.take(1).uppercase().ifBlank { "?" },
                fontSize = 25.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF8E63F6),
            )
        }
    }
}
