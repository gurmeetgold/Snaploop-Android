package com.snaploop.app.ui

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.snaploop.app.data.FirebaseMatchRepository
import com.snaploop.app.domain.PhotoMatch
import com.snaploop.app.domain.PhotoMatchDeduplication
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private class AndroidPhotoFavoritesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("snaploop.photo.favorites", Context.MODE_PRIVATE)

    fun load(userId: String?): Set<String> =
        userId?.takeIf(String::isNotBlank)?.let { prefs.getStringSet("favorites.$it", emptySet())?.toSet() }.orEmpty()

    fun save(userId: String?, ids: Set<String>) {
        val uid = userId?.takeIf(String::isNotBlank) ?: return
        prefs.edit().putStringSet("favorites.$uid", ids.toSet()).apply()
    }
}

/**
 * Shared Event/My Photos gallery surface matching the iOS interaction model:
 * branded result banner, Favorites filter, compact 2/3/4/6 density menu,
 * selection mode, bulk Save/Share/Favorite actions, full-screen photo detail,
 * Favorite and Not Me correction.
 *
 * The root Gallery is the only iOS surface that performs migration-aware logical-source
 * deduplication. Event-local My Photos intentionally keeps the Event model's ID semantics.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun ParityPhotoGallery(
    title: String,
    subtitle: String,
    photos: List<PhotoMatch>,
    userId: String?,
    onRefresh: () -> Unit,
    onBack: (() -> Unit)? = null,
    acrossAllEvents: Boolean = onBack == null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { AndroidPhotoFavoritesStore(context) }
    val matches = remember { FirebaseMatchRepository() }
    val bulkActions = remember(context) { AndroidPhotoBulkActions(context) }
    var columns by rememberSaveable(title) { mutableIntStateOf(2) }
    var densityMenuOpen by rememberSaveable(title) { mutableStateOf(false) }
    var favoritesOnly by rememberSaveable(title) { mutableStateOf(false) }
    var selecting by rememberSaveable(title) { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var favorites by remember(userId) { mutableStateOf(store.load(userId)) }
    var detail by remember { mutableStateOf<PhotoMatch?>(null) }
    var notMeConfirmation by remember { mutableStateOf<PhotoMatch?>(null) }
    var correctingNotMe by remember { mutableStateOf(false) }
    var locallyDismissedKeys by remember(userId, acrossAllEvents) { mutableStateOf(setOf<String>()) }
    var correctionError by remember(userId) { mutableStateOf<String?>(null) }
    var bulkBusy by remember { mutableStateOf(false) }
    var bulkMessage by remember { mutableStateOf<String?>(null) }
    var pendingLegacySave by remember { mutableStateOf<List<PhotoMatch>?>(null) }

    fun setFavorite(id: String, favorite: Boolean) {
        val next = if (favorite) favorites + id else favorites - id
        favorites = next
        store.save(userId, next)
    }

    fun endSelection() {
        selecting = false
        selected = emptySet()
        bulkMessage = null
    }

    fun dismissalKey(match: PhotoMatch): String =
        if (acrossAllEvents) PhotoMatchDeduplication.logicalSourceKey(match) else match.id

    val canonicalPhotos = if (acrossAllEvents) PhotoMatchDeduplication.unique(photos) else photos
    val availablePhotos = canonicalPhotos.filterNot { dismissalKey(it) in locallyDismissedKeys }
    val visible = if (favoritesOnly) availablePhotos.filter { it.id in favorites } else availablePhotos
    val selectedMatches = visible.filter { it.id in selected }
    val allSelectedAreFavorites = selectedMatches.isNotEmpty() && selectedMatches.all { it.id in favorites }

    val performSave: (List<PhotoMatch>) -> Unit = { chosen ->
        if (chosen.isNotEmpty() && !bulkBusy) {
            scope.launch {
                bulkBusy = true
                bulkMessage = null
                runCatching { bulkActions.saveToPhotoLibrary(chosen) }
                    .onSuccess { count ->
                        bulkMessage = when (count) {
                            0 -> "Selected photos couldn't be saved."
                            1 -> "Saved 1 photo."
                            else -> "Saved $count photos."
                        }
                    }
                    .onFailure { bulkMessage = "Some photos couldn't be saved." }
                bulkBusy = false
            }
        }
    }

    val legacyWritePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = pendingLegacySave
        pendingLegacySave = null
        if (granted && !pending.isNullOrEmpty()) {
            performSave(pending)
        } else if (!granted) {
            bulkMessage = "Allow storage access to save photos."
        }
    }

    fun saveSelected() {
        val chosen = selectedMatches
        if (chosen.isEmpty() || bulkBusy) return
        val needsLegacyPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsLegacyPermission) {
            pendingLegacySave = chosen
            legacyWritePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            performSave(chosen)
        }
    }

    fun shareSelected() {
        val chosen = selectedMatches
        if (chosen.isEmpty() || bulkBusy) return
        scope.launch {
            bulkBusy = true
            bulkMessage = null
            val uris = runCatching { bulkActions.prepareShareUris(chosen) }.getOrElse {
                bulkMessage = "Selected photos couldn't be prepared."
                emptyList()
            }
            if (uris.isNotEmpty()) {
                val clip = ClipData.newUri(context.contentResolver, "SnapLoop photo", uris.first()).apply {
                    uris.drop(1).forEach { addItem(ClipData.Item(it)) }
                }
                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/jpeg"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    clipData = clip
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching {
                    context.startActivity(Intent.createChooser(shareIntent, "Share SnapLoop photos"))
                }.onFailure {
                    bulkMessage = "Selected photos couldn't be shared."
                }
            } else if (bulkMessage == null) {
                bulkMessage = "Selected photos couldn't be prepared."
            }
            bulkBusy = false
        }
    }

    fun favoriteSelected() {
        if (selectedMatches.isEmpty() || bulkBusy) return
        val ids = selectedMatches.mapTo(mutableSetOf()) { it.id }
        val nextValue = !allSelectedAreFavorites
        favorites = if (nextValue) favorites + ids else favorites - ids
        store.save(userId, favorites)
        bulkMessage = if (nextValue) "Added to Favorites." else "Removed from Favorites."
        if (favoritesOnly && !nextValue) selected = emptySet()
    }

    Column(modifier.fillMaxSize().background(SnapGradients.SoftWash).padding(vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Text("‹", fontSize = 34.sp, fontWeight = FontWeight.Light)
                }
            } else {
                Box(Modifier.size(48.dp))
            }
            Text(
                title,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
            )
            IconButton(onClick = {
                correctionError = null
                onRefresh()
            }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }

        GalleryInsightBanner(
            count = availablePhotos.size,
            acrossAllEvents = acrossAllEvents,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp),
        )

        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = !favoritesOnly,
                onClick = {
                    favoritesOnly = false
                    selected = emptySet()
                    bulkMessage = null
                },
                label = { Text("All", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                leadingIcon = { Icon(Icons.Filled.GridView, contentDescription = null, modifier = Modifier.size(16.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SnapColors.Lilac.copy(alpha = 0.14f),
                    selectedLabelColor = SnapColors.Lilac,
                    selectedLeadingIconColor = SnapColors.Lilac,
                ),
            )
            FilterChip(
                selected = favoritesOnly,
                onClick = {
                    favoritesOnly = true
                    selected = emptySet()
                    bulkMessage = null
                },
                label = { Text("Favorites", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                leadingIcon = { Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SnapColors.Coral.copy(alpha = 0.13f),
                    selectedLabelColor = SnapColors.Coral,
                    selectedLeadingIconColor = SnapColors.Coral,
                ),
            )
            Box(Modifier.weight(1f))
            if (selecting) {
                Text("${selected.size} selected", color = Color(0xFF6B6670), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = ::endSelection, enabled = !bulkBusy) {
                    Text(
                        "Cancel",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            } else {
                TextButton(
                    onClick = {
                        selecting = true
                        selected = emptySet()
                        bulkMessage = null
                    },
                    enabled = visible.isNotEmpty(),
                ) {
                    Text("Select", fontWeight = FontWeight.Bold)
                }
                Box {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                    ) {
                        TextButton(onClick = { densityMenuOpen = true }) {
                            Icon(Icons.Filled.GridView, contentDescription = null, tint = SnapColors.Lilac, modifier = Modifier.size(18.dp))
                            Text("  $columns", color = SnapColors.Lilac, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    DropdownMenu(
                        expanded = densityMenuOpen,
                        onDismissRequest = { densityMenuOpen = false },
                    ) {
                        listOf(2, 3, 4, 6).forEach { count ->
                            DropdownMenuItem(
                                text = { Text("$count per row") },
                                onClick = {
                                    columns = count
                                    densityMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }
        }

        correctionError?.let {
            Text(
                it,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                color = Color(0xFFB3261E),
                fontSize = 12.sp,
            )
        }

        if (selecting) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.96f),
                tonalElevation = 2.dp,
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp)) {
                    bulkMessage?.let {
                        Text(
                            it,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                            color = Color(0xFF6B6670),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GallerySelectionAction(
                            label = "Save selected photos",
                            icon = Icons.Filled.Download,
                            enabled = selected.isNotEmpty() && !bulkBusy,
                            onClick = ::saveSelected,
                        )
                        GallerySelectionAction(
                            label = "Share selected photos",
                            icon = Icons.Filled.Share,
                            enabled = selected.isNotEmpty() && !bulkBusy,
                            onClick = ::shareSelected,
                        )
                        GallerySelectionAction(
                            label = if (allSelectedAreFavorites) "Remove selected photos from Favorites" else "Favorite selected photos",
                            icon = if (allSelectedAreFavorites) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            enabled = selected.isNotEmpty() && !bulkBusy,
                            onClick = ::favoriteSelected,
                        )
                    }
                }
            }
        }

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (favoritesOnly) "No favorites yet" else "No photos of you yet",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (favoritesOnly) {
                                "Open a photo and tap Favorite to keep it here."
                            } else {
                                "SnapLoop automatically checks eligible Events for new matched photos. You can also use Scan Photos from an Event at any time."
                            },
                            modifier = Modifier.padding(top = 6.dp),
                            color = Color(0xFF6B6670),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            return@Column
        }

        val gridSpacing = if (columns >= 6) 4.dp else 8.dp
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(gridSpacing),
            verticalArrangement = Arrangement.spacedBy(gridSpacing),
        ) {
            items(visible, key = { it.id }) { match ->
                val isSelected = match.id in selected
                val compact = columns >= 6
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(if (compact) 6.dp else 14.dp))
                        .clickable {
                            if (selecting) {
                                selected = if (isSelected) selected - match.id else selected + match.id
                                bulkMessage = null
                            } else {
                                detail = match
                            }
                        },
                ) {
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                        MatchedThumbnailCell(
                            path = match.thumbnailPath,
                            modifier = Modifier.fillMaxWidth().size(maxWidth),
                            maxPixelSize = when (columns) {
                                2 -> 900
                                3 -> 700
                                4 -> 540
                                else -> 400
                            },
                        )
                    }

                    if (selecting) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(if (compact) 3.dp else 7.dp),
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.45f),
                        ) {
                            Text(
                                if (isSelected) "✓" else "○",
                                color = Color.White,
                                fontSize = if (compact) 13.sp else 18.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    } else if (match.id in favorites) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = "Favorite",
                            tint = Color(0xFFFF2B90),
                            modifier = Modifier.align(Alignment.TopEnd).padding(if (compact) 3.dp else 7.dp).size(if (compact) 12.dp else 18.dp),
                        )
                    }
                }
            }
        }
    }

    detail?.let { match ->
        ParityFullScreenPhotoViewer(
            matches = visible,
            initialMatchId = match.id,
            isFavorite = { it.id in favorites },
            onFavoriteChanged = { item, value -> setFavorite(item.id, value) },
            onDismiss = { detail = null },
        )
    }

    notMeConfirmation?.let { match ->
        AlertDialog(
            onDismissRequest = { if (!correctingNotMe) notMeConfirmation = null },
            title = { Text("Not you in this photo?") },
            text = {
                Text("SnapLoop will remove this match from your Gallery. This does not delete the source photo from the owner's phone.")
            },
            confirmButton = {
                TextButton(
                    enabled = !correctingNotMe && !userId.isNullOrBlank(),
                    onClick = {
                        val uid = userId ?: return@TextButton
                        correctingNotMe = true
                        correctionError = null

                        // Pinned iOS semantics differ by surface: Event My Photos removes this exact
                        // match ID, while the root Gallery removes all rows representing the same
                        // logical source photo during legacy/source-scoped migration.
                        locallyDismissedKeys = locallyDismissedKeys + dismissalKey(match)
                        favorites = favorites - match.id
                        store.save(userId, favorites)
                        detail = null
                        notMeConfirmation = null

                        scope.launch {
                            runCatching { matches.dismissAppearance(match.id, uid) }
                                .onFailure {
                                    correctionError = "Couldn't save the Not Me correction. Refresh and try again."
                                }
                            correctingNotMe = false
                        }
                    },
                ) {
                    Text(if (correctingNotMe) "Removing…" else "Not Me", color = Color(0xFFB3261E), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !correctingNotMe,
                    onClick = { notMeConfirmation = null },
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun GallerySelectionAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun GalleryInsightBanner(
    count: Int,
    acrossAllEvents: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    SnapGradients.SoftWash,
                )
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(count.toString(), fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Column(Modifier.padding(start = 14.dp)) {
                Text(
                    if (acrossAllEvents) {
                        if (count == 1) "photo of you found across all events" else "photos of you found across all events"
                    } else {
                        if (count == 1) "photo of you found in this Event" else "photos of you found in this Event"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun PhotoMatchDetailDialog(
    match: PhotoMatch,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onNotMe: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Photo of You", fontWeight = FontWeight.Black) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MatchedThumbnailCell(
                    path = match.thumbnailPath,
                    modifier = Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(20.dp)),
                    maxPixelSize = 1200,
                )
                Text(
                    formatCapturedAt(match.capturedAtMillis),
                    color = Color(0xFF6B6670),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text("Event photo", color = Color(0xFF6B6670), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onFavorite) {
                    Icon(
                        if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                    )
                    Text(if (favorite) "  Remove Favorite" else "  Add to Favorites", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onNotMe) {
                    Text("Not Me", color = Color(0xFFB3261E), fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", fontWeight = FontWeight.Bold) }
        },
    )
}

private fun formatCapturedAt(millis: Long): String = runCatching {
    DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))
}.getOrDefault("")
