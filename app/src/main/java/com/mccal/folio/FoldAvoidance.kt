package com.mccal.folio

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

/** A half-open hinge in window pixels: [vertical] is a book-style fold, otherwise a laptop/tabletop fold. */
@Immutable
internal data class HalfOpenHinge(val vertical: Boolean, val startPx: Int, val endPx: Int)

internal val LocalHalfOpenHinge = staticCompositionLocalOf<HalfOpenHinge?> { null }

@Composable
internal fun rememberHalfOpenHinge(activity: Activity): HalfOpenHinge? {
    val info by remember(activity) { WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity) }
        .collectAsStateWithLifecycle(initialValue = null)
    val fold = info?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull { it.state == FoldingFeature.State.HALF_OPENED }
        ?: return null
    val vertical = fold.orientation == FoldingFeature.Orientation.VERTICAL
    return HalfOpenHinge(vertical, if (vertical) fold.bounds.left else fold.bounds.top, if (vertical) fold.bounds.right else fold.bounds.bottom)
}

/**
 * iPhone Duo-style fold avoidance for sheets, alerts and menus: flat, content fills the box; partially
 * folded like a book it moves to the right half (thumb side), and folded like a laptop it moves to the
 * bottom half, the stable side resting on the table.
 */
@Composable
internal fun FoldAvoidingBox(modifier: Modifier = Modifier, contentAlignment: Alignment = Alignment.Center, content: @Composable BoxScope.() -> Unit) {
    val hinge = LocalHalfOpenHinge.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val area = when {
            hinge == null -> Modifier.fillMaxSize()
            hinge.vertical -> Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                .width((maxWidth - with(density) { hinge.endPx.toDp() } - 12.dp).coerceAtLeast(maxWidth / 2))
            else -> Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .height((maxHeight - with(density) { hinge.endPx.toDp() } - 12.dp).coerceAtLeast(maxHeight / 2))
        }
        Box(area, contentAlignment = contentAlignment, content = content)
    }
}
