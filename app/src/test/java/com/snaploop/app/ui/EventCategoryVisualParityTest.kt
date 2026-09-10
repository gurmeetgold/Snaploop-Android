package com.snaploop.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.SportsSoccer
import com.snaploop.app.model.EventCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class EventCategoryVisualParityTest {
    @Test fun `birthday uses cake icon`() {
        assertEquals(Icons.Filled.Cake, eventCategoryIcon(EventCategory.birthday))
    }

    @Test fun `trip uses flight icon`() {
        assertEquals(Icons.Filled.Flight, eventCategoryIcon(EventCategory.trip))
    }

    @Test fun `sports uses sports icon instead of placeholder ellipsis`() {
        assertEquals(Icons.Filled.SportsSoccer, eventCategoryIcon(EventCategory.sports))
    }
}
