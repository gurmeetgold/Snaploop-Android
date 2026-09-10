package com.snaploop.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.ui.graphics.vector.ImageVector
import com.snaploop.app.model.EventCategory

/** Shared semantic category icon language across Home, Create/Edit and Event surfaces. */
internal fun eventCategoryIcon(category: EventCategory): ImageVector = when (category) {
    EventCategory.trip -> Icons.Filled.Flight
    EventCategory.wedding -> Icons.Filled.Favorite
    EventCategory.party -> Icons.Filled.Celebration
    EventCategory.birthday -> Icons.Filled.Cake
    EventCategory.conference -> Icons.Filled.BusinessCenter
    EventCategory.family -> Icons.Filled.Groups
    EventCategory.sports -> Icons.Filled.SportsSoccer
    EventCategory.other -> Icons.Filled.PhotoLibrary
}
