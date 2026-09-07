package com.snaploop.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp

/**
 * Material3 NavigationBarItem is a RowScope extension. SnapLoop's tab item is intentionally
 * extracted into a reusable composable, so this package-local overload provides the same call
 * shape without leaking RowScope through the shell API. The NavigationBar remains the parent and
 * supplies the platform navigation surface/insets.
 */
@Composable
internal fun NavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .height(64.dp)
            .widthIn(min = 92.dp)
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick)
            .alpha(if (selected) 1f else 0.68f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        label()
    }
}
