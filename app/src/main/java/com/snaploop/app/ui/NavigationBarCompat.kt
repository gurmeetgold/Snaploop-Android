package com.snaploop.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * The pinned iOS MainTabView is a standard three-item TabView with Theme.coral as its selected tint
 * and Theme.surface at 0.97 opacity. Each Android product tab therefore receives exactly one third
 * of the window width, paints that adaptive surface, and uses the canonical coral selection tint.
 */
@Composable
internal fun NavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
) {
    val windowWidthPx = LocalWindowInfo.current.containerSize.width.coerceAtLeast(1)
    val itemWidth = with(LocalDensity.current) {
        windowWidthPx.toDp() / BrandVisualParitySpec.MAIN_TAB_COUNT
    }
    val contentColor = if (selected) {
        SnapColors.Coral
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
    }

    Column(
        modifier = Modifier
            .width(itemWidth)
            .height(BrandVisualParitySpec.MAIN_TAB_ITEM_HEIGHT_DP.dp)
            .background(
                MaterialTheme.colorScheme.surface.copy(
                    alpha = BrandVisualParitySpec.MAIN_TAB_SURFACE_ALPHA,
                ),
            )
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            icon()
            label()
        }
    }
}
