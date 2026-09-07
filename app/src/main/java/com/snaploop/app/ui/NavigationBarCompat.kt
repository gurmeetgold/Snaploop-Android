package com.snaploop.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp

/**
 * Material3 NavigationBarItem is a RowScope extension. SnapLoop extracts tab items into a reusable
 * composable, so this package-local compatibility item keeps the same call shape without leaking
 * RowScope through the shell API.
 *
 * The previous implementation used only `widthIn(min = 92.dp)`, which left all three tabs clustered
 * at the start of wide/edge-to-edge navigation bars on some devices (including Redmi). Each of the
 * three product tabs now receives exactly one third of the window width, matching the centered iOS
 * Home / Gallery / You layout.
 */
@Composable
internal fun NavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
) {
    val windowWidthPx = LocalWindowInfo.current.containerSize.width.coerceAtLeast(1)
    val itemWidth = with(LocalDensity.current) { windowWidthPx.toDp() / 3 }

    Column(
        modifier = Modifier
            .width(itemWidth)
            .height(64.dp)
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick)
            .alpha(if (selected) 1f else 0.68f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        label()
    }
}
