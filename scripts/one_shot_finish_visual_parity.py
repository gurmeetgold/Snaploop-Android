from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected source exactly once, found {count}")
    path.write_text(text.replace(old, new, 1))


gallery = Path("app/src/main/java/com/snaploop/app/ui/ParityPhotoGallery.kt")

replace_once(
    gallery,
    '''import androidx.compose.material3.FilterChip\n''',
    '''import androidx.compose.material3.FilterChip\nimport androidx.compose.material3.FilterChipDefaults\nimport androidx.compose.material3.MaterialTheme\n''',
    "Gallery Material imports",
)

replace_once(
    gallery,
    '''    Column(modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {''',
    '''    Column(modifier.fillMaxSize().padding(vertical = 8.dp)) {''',
    "Gallery root horizontal padding",
)

replace_once(
    gallery,
    '''        GalleryInsightBanner(\n            count = availablePhotos.size,\n            acrossAllEvents = acrossAllEvents,\n            modifier = Modifier.padding(top = 4.dp),\n        )''',
    '''        GalleryInsightBanner(\n            count = availablePhotos.size,\n            acrossAllEvents = acrossAllEvents,\n            modifier = Modifier.padding(horizontal = 16.dp, top = 4.dp),\n        )''',
    "Gallery banner margin",
)

replace_once(
    gallery,
    '''        Row(\n            Modifier.fillMaxWidth().padding(top = 10.dp),''',
    '''        Row(\n            Modifier.fillMaxWidth().padding(horizontal = 16.dp, top = 10.dp),''',
    "Gallery controls margin",
)

replace_once(
    gallery,
    '''            FilterChip(\n                selected = !favoritesOnly,\n                onClick = {\n                    favoritesOnly = false\n                    selected = emptySet()\n                    bulkMessage = null\n                },\n                label = { Text("All") },\n            )''',
    '''            FilterChip(\n                selected = !favoritesOnly,\n                onClick = {\n                    favoritesOnly = false\n                    selected = emptySet()\n                    bulkMessage = null\n                },\n                label = { Text("All", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },\n                leadingIcon = { Icon(Icons.Filled.GridView, contentDescription = null, modifier = Modifier.size(16.dp)) },\n                colors = FilterChipDefaults.filterChipColors(\n                    selectedContainerColor = SnapColors.Lilac.copy(alpha = 0.14f),\n                    selectedLabelColor = SnapColors.Lilac,\n                    selectedLeadingIconColor = SnapColors.Lilac,\n                ),\n            )''',
    "All filter visual semantics",
)

replace_once(
    gallery,
    '''            FilterChip(\n                selected = favoritesOnly,\n                onClick = {\n                    favoritesOnly = true\n                    selected = emptySet()\n                    bulkMessage = null\n                },\n                label = { Text("Favorites") },\n            )''',
    '''            FilterChip(\n                selected = favoritesOnly,\n                onClick = {\n                    favoritesOnly = true\n                    selected = emptySet()\n                    bulkMessage = null\n                },\n                label = { Text("Favorites", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },\n                leadingIcon = { Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) },\n                colors = FilterChipDefaults.filterChipColors(\n                    selectedContainerColor = SnapColors.Coral.copy(alpha = 0.13f),\n                    selectedLabelColor = SnapColors.Coral,\n                    selectedLeadingIconColor = SnapColors.Coral,\n                ),\n            )''',
    "Favorites filter visual semantics",
)

replace_once(
    gallery,
    '''                Box {\n                    TextButton(onClick = { densityMenuOpen = true }) {\n                        Icon(Icons.Filled.GridView, contentDescription = null, modifier = Modifier.size(18.dp))\n                        Text("  $columns", fontWeight = FontWeight.Bold)\n                    }''',
    '''                Box {\n                    Surface(\n                        shape = RoundedCornerShape(50),\n                        color = MaterialTheme.colorScheme.surface,\n                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),\n                    ) {\n                        TextButton(onClick = { densityMenuOpen = true }) {\n                            Icon(Icons.Filled.GridView, contentDescription = null, tint = SnapColors.Lilac, modifier = Modifier.size(18.dp))\n                            Text("  $columns", color = SnapColors.Lilac, fontSize = 13.sp, fontWeight = FontWeight.Bold)\n                        }\n                    }''',
    "Density capsule",
)

replace_once(
    gallery,
    '''                        GallerySelectionAction(\n                            label = "Save",\n                            icon = Icons.Filled.Download,''',
    '''                        GallerySelectionAction(\n                            label = "Save selected photos",\n                            icon = Icons.Filled.Download,''',
    "Save accessibility label",
)
replace_once(
    gallery,
    '''                        GallerySelectionAction(\n                            label = "Share",\n                            icon = Icons.Filled.Share,''',
    '''                        GallerySelectionAction(\n                            label = "Share selected photos",\n                            icon = Icons.Filled.Share,''',
    "Share accessibility label",
)
replace_once(
    gallery,
    '''                            label = if (allSelectedAreFavorites) "Unfavorite" else "Favorite",''',
    '''                            label = if (allSelectedAreFavorites) "Remove selected photos from Favorites" else "Favorite selected photos",''',
    "Favorite accessibility label",
)

replace_once(
    gallery,
    '''    TextButton(onClick = onClick, enabled = enabled) {\n        Column(horizontalAlignment = Alignment.CenterHorizontally) {\n            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))\n            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)\n        }\n    }''',
    '''    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {\n        Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp))\n    }''',
    "Icon-only selection toolbar",
)

replace_once(
    gallery,
    '''            Text(count.toString(), fontSize = 38.sp, fontWeight = FontWeight.Black)''',
    '''            Text(count.toString(), fontSize = 32.sp, fontWeight = FontWeight.Bold)''',
    "Gallery banner count typography",
)

replace_once(
    gallery,
    '''                    fontWeight = FontWeight.Black,\n                )''',
    '''                    fontWeight = FontWeight.Bold,\n                    fontSize = 13.sp,\n                )''',
    "Gallery banner label typography",
)

viewer = Path("app/src/main/java/com/snaploop/app/ui/ParityFullScreenPhotoViewer.kt")
replace_once(
    viewer,
    '''    onFavoriteChanged: (PhotoMatch, Boolean) -> Unit,\n    onNotMe: (PhotoMatch) -> Unit,\n    onDismiss: () -> Unit,''',
    '''    onFavoriteChanged: (PhotoMatch, Boolean) -> Unit,\n    onDismiss: () -> Unit,''',
    "Remove obsolete viewer Not Me callback",
)
replace_once(
    gallery,
    '''            onFavoriteChanged = { item, value -> setFavorite(item.id, value) },\n            onNotMe = { item ->\n                detail = null\n                notMeConfirmation = item\n            },\n            onDismiss = { detail = null },''',
    '''            onFavoriteChanged = { item, value -> setFavorite(item.id, value) },\n            onDismiss = { detail = null },''',
    "Remove viewer Not Me wiring",
)

# Keep Not Me correction available from gallery code paths while ensuring it cannot appear in full-screen viewer.

test_path = Path("app/src/test/java/com/snaploop/app/ui/FinalVisualParityRegressionTest.kt")
test_path.write_text('''package com.snaploop.app.ui\n\nimport java.io.File\nimport org.junit.Assert.assertFalse\nimport org.junit.Assert.assertTrue\nimport org.junit.Test\n\nclass FinalVisualParityRegressionTest {\n    private fun source(name: String): String {\n        val candidates = listOf(\n            File("src/main/java/com/snaploop/app/ui/$name"),\n            File("app/src/main/java/com/snaploop/app/ui/$name"),\n        )\n        return candidates.firstOrNull { it.isFile }?.readText() ?: error("$name not found")\n    }\n\n    @Test fun `gallery filters use icon semantics and compact typography`() {\n        val source = source("ParityPhotoGallery.kt")\n        assertTrue(source.contains("leadingIcon = { Icon(Icons.Filled.GridView"))\n        assertTrue(source.contains("leadingIcon = { Icon(Icons.Filled.Favorite"))\n        assertTrue(source.contains("fontSize = 12.sp"))\n    }\n\n    @Test fun `selection toolbar is icon only like iOS`() {\n        val source = source("ParityPhotoGallery.kt")\n        assertTrue(source.contains("IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp))"))\n        assertFalse(source.contains("Text(label, fontSize = 11.sp"))\n    }\n\n    @Test fun `full screen viewer exposes no Not Me callback`() {\n        val source = source("ParityFullScreenPhotoViewer.kt")\n        assertFalse(source.contains("onNotMe:"))\n        assertFalse(source.contains("Not Me"))\n    }\n\n    @Test fun `gallery density selector is a bordered capsule`() {\n        val source = source("ParityPhotoGallery.kt")\n        assertTrue(source.contains("shape = RoundedCornerShape(50)"))\n        assertTrue(source.contains("border = androidx.compose.foundation.BorderStroke"))\n    }\n}\n''')
