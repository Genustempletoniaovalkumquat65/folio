package com.mccal.folio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * iPhone Duo's side-rail Dynamic Island: a live activity (now playing, a call, a timer, navigation, progress)
 * grows the rail downward under the status, and leaves again when it ends. Tap for the expanded card beside
 * the rail, with its controls; tapping the card's header opens the app.
 */
@Composable
internal fun RailLiveActivity(activity: IslandActivity?, width: androidx.compose.ui.unit.Dp, leftHanded: Boolean) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    // Keep showing the last activity while the rail shrinks away.
    var shown by remember { mutableStateOf(activity) }
    if (activity != null) shown = activity
    LaunchedEffect(activity == null) { if (activity == null) expanded = false }
    val reduceMotion = LocalReduceMotion.current
    AnimatedVisibility(activity != null,
        enter = if (reduceMotion) fadeIn() else expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = if (reduceMotion) fadeOut() else shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()) {
        val current = shown ?: return@AnimatedVisibility
        val glyph = (width - 22.dp).coerceIn(28.dp, 46.dp)
        Box {
            Column(Modifier.width(width).clip(RoundedCornerShape(30.dp)).background(Color.Black.copy(alpha = .82f))
                .border(1.dp, RailBorder, RoundedCornerShape(30.dp))
                .clickable(onClickLabel = "Show ${current.title}") { expanded = !expanded }
                .padding(vertical = 10.dp).testTag("rail-live-activity")
                .semantics { contentDescription = current.title },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AnimatedContent(current::class to current.packageName, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "rail activity") { _ ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        when (current) {
                            // The island's row layout (icon + timer) is too wide for the rail: stack it.
                            is IslandActivity.Call -> {
                                if (current.incoming) LeadingGlyph(IslandContent.Live(current), glyph)
                                else CircleGlyph(Icons.Rounded.Call, IslandGreen, glyph)
                                if (!current.incoming) Chronometer(remember(current.key) { current.since ?: System.currentTimeMillis() }, false, IslandGreen, 12.sp)
                            }
                            is IslandActivity.Navigation -> {
                                LeadingGlyph(IslandContent.Live(current), glyph)
                                Text(current.subtitle ?: current.title, color = Color.White, fontSize = 10.sp, maxLines = 2, lineHeight = 12.sp,
                                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 4.dp))
                            }
                            is IslandActivity.Timer -> {
                                LeadingGlyph(IslandContent.Live(current), glyph)
                                Chronometer(current.base, current.countDown, IslandOrange, 12.sp)
                            }
                            else -> {
                                LeadingGlyph(IslandContent.Live(current), glyph)
                                TrailingGlyph(IslandContent.Live(current), 18.dp)
                            }
                        }
                    }
                }
            }
            // The expanded card opens beside the rail, toward the screen, like the island expanding.
            if (expanded) Popup(alignment = if (leftHanded) Alignment.TopStart else Alignment.TopEnd,
                offset = with(androidx.compose.ui.platform.LocalDensity.current) {
                    androidx.compose.ui.unit.IntOffset(((if (leftHanded) 1 else -1) * (width + 8.dp).toPx()).toInt(), 0)
                },
                onDismissRequest = { expanded = false }, properties = PopupProperties(focusable = true)) {
                Box(Modifier.widthIn(max = 340.dp).clip(RoundedCornerShape(30.dp)).background(Color.Black.copy(alpha = .92f))
                    .border(1.dp, RailBorder, RoundedCornerShape(30.dp)).testTag("rail-live-activity-card")) {
                    ExpandedCardContent(current) { expanded = false; IslandListenerService.open(context, current) }
                }
            }
        }
    }
}
