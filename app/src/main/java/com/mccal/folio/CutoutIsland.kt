package com.mccal.folio

import android.graphics.Rect
import android.view.ViewTreeObserver
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** What the compact pill currently shows: a live activity, or a brief system event on top of it. */
internal sealed interface IslandContent {
    data class Live(val activity: IslandActivity) : IslandContent
    data class Event(val event: IslandEvent) : IslandContent
}

/**
 * Dynamic Island centered on the real camera cutout of the current display. The pill is always
 * symmetric around the camera; tapping opens a separate card underneath so the pill never moves.
 */
@Composable
internal fun CutoutIsland(activity: IslandActivity?, onOpen: (IslandActivity) -> Unit) {
    val view = LocalView.current
    val density = LocalDensity.current

    // Track the cutout and window width live: after a fold the insets arrive after the first layout.
    var cutout by remember { mutableStateOf<Rect?>(null) }
    var windowWidth by remember { mutableIntStateOf(view.width) }
    DisposableEffect(view) {
        val update = ViewTreeObserver.OnGlobalLayoutListener {
            windowWidth = view.rootView.width
            cutout = view.rootWindowInsets?.displayCutout?.boundingRects?.minByOrNull { it.top }?.let(::Rect)
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(update)
        update.onGlobalLayout()
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(update) }
    }

    val eventPair by IslandEvents.latest.collectAsState()
    var eventVisible by remember { mutableStateOf<IslandEvent?>(null) }
    LaunchedEffect(eventPair) {
        val (event, at) = eventPair ?: return@LaunchedEffect
        val remaining = IslandEvents.SHOW_MS - (System.currentTimeMillis() - at)
        if (remaining <= 0) return@LaunchedEffect
        eventVisible = event; delay(remaining); eventVisible = null
    }
    val content: IslandContent? = eventVisible?.let { IslandContent.Event(it) } ?: activity?.let { IslandContent.Live(it) }
    var expanded by remember(activity?.packageName) { mutableStateOf(false) }
    if (content == null || windowWidth <= 0) return

    with(density) {
        val cam = cutout
        val camW = (cam?.width() ?: 0).toDp()
        val camH = (cam?.height() ?: 0).toDp()
        val centerX = cam?.exactCenterX() ?: (windowWidth / 2f)
        val centerY = cam?.exactCenterY()?.toDp() ?: 18.dp
        // Height hugs the camera; never taller than fits above/below its center.
        val pillH = (maxOf(camH + 8.dp, 30.dp)).coerceAtMost(maxOf(camH + 4.dp, (centerY - 2.dp) * 2))
        // Width is symmetric around the camera and capped by the room on the narrower side.
        val room = (minOf(centerX, windowWidth - centerX)).toDp() - 8.dp
        val wantW = islandWantWidth(content, camW)
        val targetW = minOf(wantW, room * 2).coerceAtLeast(camW + pillH)
        val width by animateDpAsState(targetW, spring(dampingRatio = .72f, stiffness = Spring.StiffnessMediumLow), label = "island-width")
        val left = centerX - width.toPx() / 2f
        val top = centerY - pillH / 2

        // Compact pill
        Box(Modifier.offset { IntOffset(left.roundToInt(), top.roundToPx()) }.size(width, pillH)
            .clip(RoundedCornerShape(pillH / 2)).background(Color.Black)
            .clickable(remember { MutableInteractionSource() }, null) {
                if (content is IslandContent.Live) expanded = !expanded
            }
            .semantics { contentDescription = describe(content) }
            .testTag("cutout-island")) {
            IslandPillContent(content, camW, pillH)
        }

        // Expanded card under the pill (clamped to the screen; the pill itself stays put)
        val live = (content as? IslandContent.Live)?.activity
        val cardW = 320.dp.coerceAtMost(windowWidth.toDp() - 16.dp)
        val cardLeft = (centerX - cardW.toPx() / 2f).coerceIn(8.dp.toPx(), windowWidth - cardW.toPx() - 8.dp.toPx())
        AnimatedVisibility(expanded && live != null,
            modifier = Modifier.offset { IntOffset(cardLeft.roundToInt(), (top + pillH + 8.dp).roundToPx()) },
            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(spring(dampingRatio = .75f, stiffness = Spring.StiffnessMediumLow),
                initialScale = .6f, transformOrigin = TransformOrigin(.5f, 0f)),
            exit = fadeOut(tween(140)) + scaleOut(tween(160), targetScale = .7f, transformOrigin = TransformOrigin(.5f, 0f))) {
            if (live != null) ExpandedCard(live, cardW, onOpen = { expanded = false; onOpen(live) })
        }
    }
}

/** The inside of the compact pill: glyph left of the camera, detail right of it. */
@Composable
internal fun IslandPillContent(content: IslandContent, camW: Dp, pillH: Dp) {
    Row(Modifier.fillMaxSize().padding(horizontal = pillH * .22f), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { LeadingGlyph(content, pillH - 10.dp) }
        Spacer(Modifier.width(camW + 6.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { TrailingGlyph(content, pillH - 12.dp) }
    }
}

/** Desired pill width beside the camera for this content. */
internal fun islandWantWidth(content: IslandContent, camW: Dp): Dp = when (content) {
    is IslandContent.Event -> camW + 190.dp
    is IslandContent.Live -> when (content.activity) {
        is IslandActivity.Navigation, is IslandActivity.Timer, is IslandActivity.Call -> camW + 150.dp
        else -> camW + 96.dp
    }
}

internal fun describe(content: IslandContent): String = when (content) {
    is IslandContent.Event -> when (val e = content.event) {
        is IslandEvent.Charging -> "Charging${e.level?.let { ", $it percent" } ?: ""}"
        is IslandEvent.Silent -> if (e.on) "Silent mode on" else "Silent mode off"
        is IslandEvent.Focus -> if (e.on) "Do Not Disturb on" else "Do Not Disturb off"
        is IslandEvent.Bluetooth -> "Connected${e.name?.let { " to $it" } ?: ""}"
    }
    is IslandContent.Live -> content.activity.title + ". Tap for details"
}

@Composable
private fun LeadingGlyph(content: IslandContent, size: Dp) {
    when (content) {
        is IslandContent.Event -> when (val e = content.event) {
            is IslandEvent.Charging -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bolt, null, tint = Green, modifier = Modifier.size(size * .8f))
                Text("Charging", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            is IslandEvent.Silent -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (e.on) Icons.Rounded.NotificationsOff else Icons.Rounded.NotificationsActive, null,
                    tint = if (e.on) Red else Color.White, modifier = Modifier.size(size * .75f))
                Spacer(Modifier.width(4.dp))
                Text("Silent", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            is IslandEvent.Focus -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DarkMode, null, tint = Purple, modifier = Modifier.size(size * .75f))
                Spacer(Modifier.width(4.dp))
                Text("Focus", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            is IslandEvent.Bluetooth -> Icon(Icons.Rounded.Headphones, null, tint = Blue, modifier = Modifier.size(size * .8f))
        }
        is IslandContent.Live -> when (val a = content.activity) {
            is IslandActivity.Call -> CircleGlyph(Icons.Rounded.Call, Green, size)
            is IslandActivity.Timer -> CircleGlyph(Icons.Rounded.Timer, Orange, size)
            is IslandActivity.Navigation -> CircleGlyph(Icons.Rounded.TurnRight, Blue, size)
            else -> a.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(size).clip(RoundedCornerShape(size * .28f))) }
        }
    }
}

@Composable
private fun TrailingGlyph(content: IslandContent, size: Dp) {
    when (content) {
        is IslandContent.Event -> when (val e = content.event) {
            is IslandEvent.Charging -> Text("${e.level ?: ""}%", color = Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            is IslandEvent.Silent -> Text(if (e.on) "On" else "Off", color = if (e.on) Red else Color.White.copy(alpha = .7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            is IslandEvent.Focus -> Text(if (e.on) "On" else "Off", color = if (e.on) Purple else Color.White.copy(alpha = .7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            is IslandEvent.Bluetooth -> Text(e.name ?: "Connected", color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        is IslandContent.Live -> when (val a = content.activity) {
            is IslandActivity.Media -> Bars(a.playing)
            is IslandActivity.Progress -> Ring(a.fraction, size)
            is IslandActivity.Call -> Chronometer(a.since ?: System.currentTimeMillis(), countDown = false, color = Green)
            is IslandActivity.Timer -> Chronometer(a.base, a.countDown, Orange)
            is IslandActivity.Navigation -> Text(a.subtitle ?: a.title, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ExpandedCard(activity: IslandActivity, width: Dp, onOpen: () -> Unit) {
    Column(Modifier.width(width).clip(RoundedCornerShape(30.dp)).background(Color.Black).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen)) {
            activity.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(44.dp).clip(RoundedCornerShape(11.dp))) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(activity.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val subtitle = when (activity) {
                    is IslandActivity.Media -> activity.subtitle
                    is IslandActivity.Progress -> activity.subtitle
                    is IslandActivity.Navigation -> activity.subtitle
                    else -> null
                }
                subtitle?.let { Text(it, color = Color.White.copy(alpha = .6f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            when (activity) {
                is IslandActivity.Call -> Chronometer(activity.since ?: System.currentTimeMillis(), false, Green, 20.sp)
                is IslandActivity.Timer -> Chronometer(activity.base, activity.countDown, Orange, 20.sp)
                else -> Unit
            }
        }
        when (activity) {
            is IslandActivity.Media -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val t = activity.controller.transportControls
                Icon(Icons.Rounded.FastRewind, "Previous", tint = Color.White, modifier = Modifier.size(34.dp).clip(CircleShape).clickable { t.skipToPrevious() })
                Icon(if (activity.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", tint = Color.White,
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable { if (activity.playing) t.pause() else t.play() })
                Icon(Icons.Rounded.FastForward, "Next", tint = Color.White, modifier = Modifier.size(34.dp).clip(CircleShape).clickable { t.skipToNext() })
            }
            is IslandActivity.Progress -> activity.fraction?.let { f ->
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = .2f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(Green))
                }
            }
            is IslandActivity.Call -> Text("Tap to return to the call", color = Color.White.copy(alpha = .6f), fontSize = 13.sp)
            else -> Unit
        }
    }
}

@Composable
private fun CircleGlyph(icon: ImageVector, color: Color, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = .22f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(size * .62f))
    }
}

@Composable
private fun Chronometer(base: Long, countDown: Boolean, color: Color, fontSize: androidx.compose.ui.unit.TextUnit = 13.sp) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(1_000L - (value % 1_000L)) }
    }
    val seconds = ((if (countDown) base - now else now - base) / 1000).coerceAtLeast(0)
    val text = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%d:%02d".format(seconds / 60, seconds % 60)
    Text(text, color = color, fontSize = fontSize, fontWeight = FontWeight.SemiBold,
        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
}

@Composable
private fun Bars(playing: Boolean) {
    val transition = rememberInfiniteTransition(label = "island-bars")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "phase")
    Canvas(Modifier.size(width = 20.dp, height = 14.dp)) {
        val w = size.width / 7
        for (i in 0 until 4) {
            val wave = if (playing) (kotlin.math.sin(phase * 2 * Math.PI + i * 1.3).toFloat() + 1f) / 2f else .15f
            val h = size.height * (.3f + .7f * wave)
            drawLine(Green, Offset(w * (1 + i * 1.7f), size.height / 2 + h / 2), Offset(w * (1 + i * 1.7f), size.height / 2 - h / 2),
                strokeWidth = w, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun Ring(fraction: Float?, size: Dp) {
    Canvas(Modifier.size(size)) {
        val stroke = this.size.width * .14f
        val arc = Size(this.size.width - stroke, this.size.height - stroke)
        val o = Offset(stroke / 2, stroke / 2)
        drawArc(Color.White.copy(alpha = .2f), 0f, 360f, false, o, arc, style = Stroke(stroke))
        drawArc(Green, -90f, 360f * (fraction ?: .25f), false, o, arc, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

private val Green = Color(0xFF30D158)
private val Orange = Color(0xFFFF9F0A)
private val Red = Color(0xFFFF453A)
private val Purple = Color(0xFF5E5CE6)
private val Blue = Color(0xFF0A84FF)
