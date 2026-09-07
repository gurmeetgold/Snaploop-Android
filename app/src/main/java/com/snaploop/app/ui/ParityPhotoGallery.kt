package com.snaploop.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snaploop.app.domain.PhotoMatch

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
 * Shared Event/My Photos gallery surface matching the iOS interaction model: 2/3/4/6 columns,
 * Favorites filtering, per-photo favorites and multi-select. It uses LazyVerticalGrid so density
 * scales with the actual Android screen instead of assuming an iPhone-sized viewport.
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember(context) { AndroidPhotoFavoritesStore(context) }
    var columns by rememberSaveable(title) { mutableIntStateOf(3) }
    var favoritesOnly by rememberSaveable(title) { mutableStateOf(false) }
    var selecting by rememberSaveable(title) { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var favorites by remember(userId) { mutableStateOf(store.load(userId)) }

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
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(subtitle, fontSize = 12.sp, color = Color(0xFF6B6670), textAlign = TextAlign.Center)
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
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
            TextButton(onClick = { if (selecting) endSelection() else selecting = true }) {
                Text(if (selecting) "Cancel" else "Select", fontWeight = FontWeight.Bold)
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Grid", color = Color(0xFF6B6670), fontSize = 12.sp, modifier = Modifier.padding(end = 2.dp))
            listOf(2, 3, 4, 6).forEach { count ->
                FilterChip(
                    selected = columns == count,
                    onClick = { columns = count },
                    label = { Text(count.toString()) },
                )
            }
            if (selecting && selected.isNotEmpty()) {
                Box(Modifier.weight(1f))
                TextButton(onClick = {
                    favorites = favorites + selected
                    store.save(userId, favorites)
                }) {
                    Text("♥ ${selected.size}", fontWeight = FontWeight.Bold)
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
                            if (favoritesOnly) "Tap the heart on a photo to add it here." else "Scan Event photos to find matches.",
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
                                toggleFavorite(match.id)
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
                        Text(
                            if (selecting) {
                                if (isSelected) "✓" else "○"
                            } else {
                                if (match.id in favorites) "♥" else "♡"
                            },
                            color = Color.White,
                            fontSize = if (columns >= 4) 13.sp else 17.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}