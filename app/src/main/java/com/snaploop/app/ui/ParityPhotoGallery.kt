package com.snaploop.app.ui

import android.content.Context
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import com.snaploop.app.data.FirebaseMatchRepository
import com.snaploop.app.domain.PhotoMatch
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
 * selection mode, photo detail, Favorite and Not Me correction.
 */
@Composable
internal fun ParityPhotoGallery(
    title: String,
    subtitle: String,
    photos: List<PhotoMatch>,
    userId: String?,
    onRefresh: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { AndroidPhotoFavoritesStore(context) }
    val matches = remember { FirebaseMatchRepository() }
    var columns by rememberSaveable(title) { mutableIntStateOf(3) }
    var densityMenuOpen by rememberSaveable(title) { mutableStateOf(false) }
    var favoritesOnly by rememberSaveable(title) { mutableStateOf(false) }
    var selecting by rememberSaveable(title) { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var favorites by remember(userId) { mutableStateOf(store.load(userId)) }
    var detail by remember { mutableStateOf<PhotoMatch?>(null) }
    var notMeConfirmation by remember { mutableStateOf<PhotoMatch?>(null) }
    var correctingNotMe by remember { mutableStateOf(false) }

    fun toggleFavorite(id: String) {
        val next = if (id in favorites) favorites - id else favorites + id
        favorites = next
        store.save(userId, next)
    }

    fun endSelection() {
        selecting = false
        selected = emptySet()
    }

    val visible = if (favoritesOnly) photos.filter { it.id in favorites } else photos

    Column(modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
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
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }

        GalleryInsightBanner(
            count = photos.size,
            subtitle = subtitle,
            modifier = Modifier.padding(top = 4.dp),
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = !favoritesOnly,
                onClick = { favoritesOnly = false },
                label = { Text("All") },
            )
            FilterChip(
                selected = favoritesOnly,
                onClick = { favoritesOnly = true },
                label = { Text("Favorites") },
            )
            Box(Modifier.weight(1f))
            Box {
                TextButton(onClick = { densityMenuOpen = true }) {
                    Icon(Icons.Filled.GridView, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  $columns", fontWeight = FontWeight.Bold)
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
            TextButton(onClick = { if (selecting) endSelection() else selecting = true }) {
                Text(if (selecting) "Cancel" else "Select", fontWeight = FontWeight.Bold)
            }
        }

        if (selecting && selected.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${selected.size} selected", color = Color(0xFF6B6670), fontSize = 12.sp)
                TextButton(onClick = {
                    favorites = favorites + selected
                    store.save(userId, favorites)
                }) {
                    Text("Add to Favorites", fontWeight = FontWeight.Bold)
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
                                "Tap the heart on a photo to add it here."
                            } else {
                                "Scan Event photos to find matches."
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

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            items(visible, key = { it.id }) { match ->
                val isSelected = match.id in selected
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(if (columns <= 2) 12.dp else 5.dp))
                        .clickable {
                            if (selecting) {
                                selected = if (isSelected) selected - match.id else selected + match.id
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

                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.48f),
                    ) {
                        if (selecting) {
                            Text(
                                if (isSelected) "✓" else "○",
                                color = Color.White,
                                fontSize = if (columns >= 4) 13.sp else 17.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        } else {
                            IconButton(
                                onClick = { toggleFavorite(match.id) },
                                modifier = Modifier.size(if (columns >= 4) 30.dp else 36.dp),
                            ) {
                                Icon(
                                    if (match.id in favorites) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    detail?.let { match ->
        PhotoMatchDetailDialog(
            match = match,
            favorite = match.id in favorites,
            onFavorite = { toggleFavorite(match.id) },
            onNotMe = { notMeConfirmation = match },
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
                        scope.launch {
                            runCatching { matches.dismissAppearance(match.id, uid) }
                            detail = null
                            notMeConfirmation = null
                            correctingNotMe = false
                            onRefresh()
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
private fun GalleryInsightBanner(count: Int, subtitle: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFFFF571A).copy(alpha = 0.14f),
                            Color(0xFFFF0070).copy(alpha = 0.12f),
                            Color(0xFF7D14FF).copy(alpha = 0.12f),
                        ),
                    ),
                )
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(count.toString(), fontSize = 38.sp, fontWeight = FontWeight.Black)
            Column(Modifier.padding(start = 14.dp)) {
                Text(
                    if (count == 1) "photo found of you" else "photos found of you",
                    fontWeight = FontWeight.Black,
                )
                Text(subtitle, fontSize = 11.sp, color = Color(0xFF6B6670))
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
