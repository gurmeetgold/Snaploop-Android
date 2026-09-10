package com.snaploop.app.ui

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.snaploop.app.data.FirebaseEventRepository
import com.snaploop.app.domain.PhotoMatch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private data class PhotoViewerMetadata(
    val ownerLabel: String = "Event member",
    val eventLabel: String = "Event",
)

private data class PhotoViewerEventMetadata(
    val eventLabel: String,
    val ownerLabels: Map<String, String>,
)

/** Ephemeral per-viewer metadata cache. No photo, URI or biometric data is retained here. */
private class PhotoViewerMetadataResolver(
    private val repository: FirebaseEventRepository = FirebaseEventRepository(),
) {
    private val cache = mutableMapOf<String, PhotoViewerEventMetadata>()

    suspend fun resolve(match: PhotoMatch): PhotoViewerMetadata {
        val eventMetadata = cache[match.eventId] ?: run {
            val event = runCatching { repository.fetchEvent(match.eventId) }.getOrNull()
            val members = runCatching { repository.members(match.eventId) }.getOrNull().orEmpty()
            PhotoViewerEventMetadata(
                eventLabel = event?.name ?: "Event",
                ownerLabels = buildMap {
                    members.forEach { member ->
                        member.displayName
                            ?.trim()
                            ?.takeIf(String::isNotEmpty)
                            ?.let { put(member.userId, it) }
                    }
                },
            ).also { cache[match.eventId] = it }
        }
        val ownUserId = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
        return PhotoViewerMetadata(
            ownerLabel = eventMetadata.ownerLabels[match.ownerUserId]
                ?: if (match.ownerUserId == ownUserId) "You" else "Event member",
            eventLabel = eventMetadata.eventLabel,
        )
    }
}

private sealed interface ViewerPhotoLoadState {
    data object Loading : ViewerPhotoLoadState
    data class Ready(val bitmap: Bitmap) : ViewerPhotoLoadState
    data object Failed : ViewerPhotoLoadState
}

/**
 * Full-screen photo viewer mirrored from pinned iOS PhotoDetailView / ZoomablePhotoView.
 *
 * Normal-size one-finger horizontal gestures remain owned by the pager. Pinch/double-tap zoom
 * switches the current page into local pan mode; paging is re-enabled after zoom returns to 1x.
 * iOS parity intentionally keeps only back/count chrome at the top and icon-only Save/Share/
 * Favorite controls at the bottom. "Not Me" remains a gallery-selection action, not a viewer menu.
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ParityFullScreenPhotoViewer(
    matches: List<PhotoMatch>,
    initialMatchId: String,
    isFavorite: (PhotoMatch) -> Boolean,
    onFavoriteChanged: (PhotoMatch, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    if (matches.isEmpty()) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bulkActions = remember(context) { AndroidPhotoBulkActions(context) }
    val metadataResolver = remember { PhotoViewerMetadataResolver() }
    val initialIndex = remember(matches, initialMatchId) {
        matches.indexOfFirst { it.id == initialMatchId }.takeIf { it >= 0 } ?: 0
    }
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { matches.size })
    var chromeVisible by remember { mutableStateOf(true) }
    var currentPageZoomed by remember { mutableStateOf(false) }
    var actionBusy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var favoriteOverrides by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var pendingLegacySave by remember { mutableStateOf<PhotoMatch?>(null) }
    var verticalDismissOffset by remember { mutableFloatStateOf(0f) }

    val currentMatch = matches.getOrNull(pagerState.currentPage) ?: matches.first()
    val currentFavorite = favoriteOverrides[currentMatch.id] ?: isFavorite(currentMatch)
    val metadata by produceState(
        initialValue = PhotoViewerMetadata(),
        key1 = currentMatch.eventId,
        key2 = currentMatch.ownerUserId,
    ) {
        value = metadataResolver.resolve(currentMatch)
    }

    fun save(match: PhotoMatch) {
        if (actionBusy) return
        scope.launch {
            actionBusy = true
            statusMessage = null
            runCatching { bulkActions.saveToPhotoLibrary(listOf(match)) }
                .onSuccess { count ->
                    statusMessage = if (count == 1) "Saved to Photos." else "Couldn't save this photo."
                }
                .onFailure { statusMessage = "Couldn't save this photo." }
            actionBusy = false
        }
    }

    val legacyWritePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = pendingLegacySave
        pendingLegacySave = null
        if (granted && pending != null) {
            save(pending)
        } else if (!granted) {
            statusMessage = "Allow storage access to save photos."
        }
    }

    fun saveCurrent() {
        val match = currentMatch
        val needsLegacyPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsLegacyPermission) {
            pendingLegacySave = match
            legacyWritePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            save(match)
        }
    }

    fun shareCurrent() {
        if (actionBusy) return
        val match = currentMatch
        scope.launch {
            actionBusy = true
            statusMessage = null
            val uri = runCatching { bulkActions.prepareShareUris(listOf(match)).firstOrNull() }.getOrNull()
            if (uri == null) {
                statusMessage = "Couldn't prepare this photo."
            } else {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, "SnapLoop photo", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching {
                    context.startActivity(Intent.createChooser(intent, "Share SnapLoop photo"))
                }.onFailure {
                    statusMessage = "Couldn't share this photo."
                }
            }
            actionBusy = false
        }
    }

    fun toggleFavorite() {
        val match = currentMatch
        val next = !currentFavorite
        favoriteOverrides = favoriteOverrides + (match.id to next)
        onFavoriteChanged(match, next)
        statusMessage = null
    }

    BackHandler(onBack = onDismiss)

    LaunchedEffect(pagerState.currentPage) {
        currentPageZoomed = false
        statusMessage = null
        verticalDismissOffset = 0f
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(currentPageZoomed) {
                    if (!currentPageZoomed) {
                        val dismissThreshold = 105.dp.toPx()
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                if (dragAmount > 0f || verticalDismissOffset > 0f) {
                                    change.consume()
                                    verticalDismissOffset = (verticalDismissOffset + dragAmount).coerceAtLeast(0f)
                                }
                            },
                            onDragEnd = {
                                if (verticalDismissOffset > dismissThreshold) onDismiss()
                                verticalDismissOffset = 0f
                            },
                            onDragCancel = { verticalDismissOffset = 0f },
                        )
                    }
                },
            color = Color.Black,
        ) {
            Box(Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        translationY = verticalDismissOffset
                        val progress = (verticalDismissOffset / 1200f).coerceIn(0f, 0.08f)
                        scaleX = 1f - progress
                        scaleY = 1f - progress
                    },
                    userScrollEnabled = !currentPageZoomed,
                    beyondViewportPageCount = 1,
                    key = { index -> matches[index].id },
                ) { index ->
                    val match = matches[index]
                    ZoomableMatchedPhoto(
                        match = match,
                        active = index == pagerState.currentPage,
                        onTap = { chromeVisible = !chromeVisible },
                        onZoomChanged = { zoomed ->
                            if (index == pagerState.currentPage) currentPageZoomed = zoomed
                        },
                    )
                }

                if (chromeVisible) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("‹", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light)
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(
                            color = Color.Black.copy(alpha = 0.42f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                        ) {
                            Text(
                                "${pagerState.currentPage + 1} / ${matches.size}",
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        // iOS places the count capsule at the trailing edge rather than
                        // artificially centering it with a mirrored spacer.
                    }

                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.48f))
                            .navigationBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            viewerMetadataLine(metadata, currentMatch.capturedAtMillis),
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ViewerAction("Save", Icons.Filled.Download, !actionBusy, ::saveCurrent)
                            ViewerAction("Share", Icons.Filled.Share, !actionBusy, ::shareCurrent)
                            ViewerAction(
                                if (currentFavorite) "Unfavorite" else "Favorite",
                                if (currentFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                !actionBusy,
                                ::toggleFavorite,
                            )
                        }
                        if (actionBusy) {
                            CircularProgressIndicator(
                                Modifier.padding(top = 2.dp).size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            statusMessage?.let {
                                Text(
                                    it,
                                    modifier = Modifier.padding(top = 2.dp),
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(52.dp)) {
        Icon(
            icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
    }
}

private fun viewerMetadataLine(metadata: PhotoViewerMetadata, capturedAtMillis: Long): String {
    val date = runCatching {
        DateTimeFormatter.ofPattern("dd/MMM/yy", Locale.US)
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(capturedAtMillis))
    }.getOrDefault("")
    return "${metadata.ownerLabel.takeWords(6)} · ${metadata.eventLabel.takeWords(6)} · $date"
}

private fun String.takeWords(limit: Int): String =
    trim().split(Regex("\\s+")).filter(String::isNotEmpty).take(limit).joinToString(" ")

@Composable
private fun ZoomableMatchedPhoto(
    match: PhotoMatch,
    active: Boolean,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val loader = remember(context) { MatchedThumbnailLoader(context) }
    val loadState by produceState<ViewerPhotoLoadState>(
        initialValue = ViewerPhotoLoadState.Loading,
        key1 = match.thumbnailPath,
    ) {
        value = ViewerPhotoLoadState.Loading
        value = runCatching { loader.load(match.thumbnailPath, 2048) }
            .fold(
                onSuccess = { ViewerPhotoLoadState.Ready(it) },
                onFailure = { ViewerPhotoLoadState.Failed },
            )
    }
    val bitmap = (loadState as? ViewerPhotoLoadState.Ready)?.bitmap
    var scale by remember(match.id) { mutableFloatStateOf(1f) }
    var translation by remember(match.id) { mutableStateOf(Offset.Zero) }

    DisposableEffect(bitmap) {
        onDispose { bitmap?.takeIf { !it.isRecycled }?.recycle() }
    }

    LaunchedEffect(active) {
        if (!active) {
            scale = 1f
            translation = Offset.Zero
            onZoomChanged(false)
        }
    }

    LaunchedEffect(scale, active) {
        if (active) onZoomChanged(scale > 1.01f)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(match.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1.01f) {
                            scale = 1f
                            translation = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            .pointerInput(match.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            translation = if (scale <= 1.01f) Offset.Zero else translation + pan
                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale <= 1.01f) {
                        scale = 1f
                        translation = Offset.Zero
                    }
                }
            }
            .then(
                if (scale > 1.01f) {
                    Modifier.pointerInput(match.id, scale) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            translation += dragAmount
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (val state = loadState) {
            ViewerPhotoLoadState.Loading -> CircularProgressIndicator(color = Color.White)
            ViewerPhotoLoadState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Photo unavailable", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "Try the photo again.",
                    modifier = Modifier.padding(top = 6.dp),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                )
            }
            is ViewerPhotoLoadState.Ready -> Image(
                bitmap = state.bitmap.asImageBitmap(),
                contentDescription = "Matched Event photo",
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = translation.x
                    translationY = translation.y
                },
                contentScale = ContentScale.Fit,
            )
        }
    }
}
