package com.mccal.folio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** How the side-rail status capsule looks. Saved with the launcher state. */
data class StatusStyle(
    val showTime: Boolean = true,
    val showDate: Boolean = true,
    val showBatteryPercent: Boolean = true,
    val glyph: StatusGlyph = StatusGlyph.RING,
    val colorfulBattery: Boolean = true,
    /** Frost strength shared by the status, dock and island capsules (0 = clear, 1 = solid). */
    val railGlass: Float = .26f,
) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject().put("showTime", showTime).put("showDate", showDate)
        .put("showBatteryPercent", showBatteryPercent).put("glyph", glyph.name).put("colorfulBattery", colorfulBattery)
        .put("railGlass", railGlass.toDouble())

    companion object {
        fun fromJson(j: org.json.JSONObject?): StatusStyle = if (j == null) StatusStyle() else StatusStyle(
            showTime = j.optBoolean("showTime", true), showDate = j.optBoolean("showDate", true),
            showBatteryPercent = j.optBoolean("showBatteryPercent", true),
            glyph = runCatching { StatusGlyph.valueOf(j.optString("glyph")) }.getOrDefault(StatusGlyph.RING),
            colorfulBattery = j.optBoolean("colorfulBattery", true),
            railGlass = j.optDouble("railGlass", .26).toFloat().coerceIn(0f, 1f))
    }
}

enum class StatusGlyph(val label: String) { RING("Ring"), ICONS("Icons"), MINIMAL("Battery only"), NONE("Hidden") }

/** Shared capsule look for the side rail (status, dock, island). */
internal val RailBorder = Color.White.copy(alpha = .16f)

@Composable
fun StatusRail(
    status: DeviceStatus,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    iconSize: Dp = 40.dp,
    locationInUse: Boolean = false,
    island: (@Composable () -> Unit)? = null,
    style: StatusStyle = StatusStyle(),
) {
    val ink = LocalHomeInk.current.primary
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_050L - (System.currentTimeMillis() % 60_000L))
        }
    }
    val format = if (android.text.format.DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm"
    val timeFormatter = remember(format) { DateTimeFormatter.ofPattern(format) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d") }
    val description = listOfNotNull(
        if (locationInUse) "Location in use" else null,
        now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, $format")),
        status.battery?.let { "Battery $it percent${if (status.charging) ", charging" else ""}" } ?: "Battery unavailable",
        if (status.wifiConnected) "Wi-Fi connected${status.wifiLevel?.let { ", signal $it of 4" } ?: ""}" else "Wi-Fi disconnected",
        if (status.airplane) "Airplane mode" else status.cellularLevel?.let { "Cellular signal $it of 4" } ?: "Cellular signal unavailable",
    ).joinToString(". ")
    val fontScale = LocalDensity.current.fontScale
    val wifiVisual = wifiSignalVisual(status.wifiConnected, status.wifiLevel)
    val cellularVisual = cellularSignalVisual(status.cellularLevel, status.airplane)
    val activeDots = (cellularVisual as? CellularSignalVisual.Available)?.activeDots ?: 0
    val batteryColor = when {
        !style.colorfulBattery -> ink
        status.charging -> BatteryCharging
        (status.battery ?: 100) <= 20 -> BatteryLow
        else -> ink
    }
    val capsule = RoundedCornerShape(30.dp)
    BoxWithConstraints(modifier.testTag("status-rail").semantics(mergeDescendants = true) { contentDescription = description }) {
        val availableWidth = (maxWidth - 16.dp).coerceAtLeast(28.dp)
        val visualSize = minOf(iconSize * .9f, availableWidth, if (compact) 34.dp else 44.dp)
        val timeSize = minOf(17f, availableWidth.value / (2.5f * fontScale)).sp
        val detailSize = minOf(11f, availableWidth.value / (3.3f * fontScale)).sp
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // Compact windows omit the reserve so status stays clear of the fixed dock.
            if (!compact) Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
                // Callers currently leave this false; the slot waits for a truthful activity signal.
                if (locationInUse) Icon(Icons.Rounded.LocationOn, null, tint = ink,
                    modifier = Modifier.size(18.dp))
            }
            val hasContent = style.showTime || (!compact && style.showDate) || style.glyph != StatusGlyph.NONE ||
                (!compact && style.showBatteryPercent)
            // Same frosted capsule as the dock so status and dock read as one side rail.
            if (hasContent) Column(Modifier.fillMaxWidth().background(Glass.copy(alpha = style.railGlass), capsule)
                .border(1.dp, RailBorder, capsule)
                .padding(vertical = if (compact) 8.dp else 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (style.showTime) Text(now.format(timeFormatter), color = ink, fontSize = timeSize, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                if (!compact && style.showDate) Text(now.format(dateFormatter), color = ink.copy(alpha = .7f), fontSize = detailSize,
                    fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                when (style.glyph) {
                    StatusGlyph.RING, StatusGlyph.MINIMAL -> Canvas(Modifier.padding(top = 2.dp).size(visualSize)) {
                        val w = size.width
                        val center = Offset(w / 2, w / 2)
                        // Battery: one thin full ring, filled clockwise from the top.
                        val radius = w * .44f
                        drawCircle(ink.copy(alpha = .22f), radius, center, style = Stroke(w * .06f))
                        status.battery?.let { level ->
                            drawArc(batteryColor, -90f, 360f * level / 100, false, Offset(center.x - radius, center.y - radius),
                                Size(radius * 2, radius * 2), style = Stroke(width = w * .06f, cap = StrokeCap.Round))
                        }
                        if (style.glyph == StatusGlyph.RING) {
                            drawWifiFan(w, wifiVisual, ink = ink)
                            // Cellular: a short row of dots under the fan.
                            for (i in 0..4) drawCircle(ink.copy(alpha = if (i < activeDots) 1f else .28f), w * .026f,
                                Offset(center.x + (i - 2) * w * .085f, w * .74f))
                        } else status.battery?.let { level ->
                            // Minimal: nothing inside the ring but a small charge dot when charging.
                            if (status.charging) drawCircle(batteryColor, w * .06f, center)
                        }
                    }
                    StatusGlyph.ICONS -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)) {
                        Canvas(Modifier.size(width = 22.dp, height = 16.dp)) { drawWifiFan(size.width, wifiVisual, centered = true) }
                        Canvas(Modifier.size(width = 22.dp, height = 14.dp)) {
                            val bar = size.width / 7
                            for (i in 0 until 5) {
                                val h = size.height * (.3f + .7f * i / 4)
                                drawRoundRect(ink.copy(alpha = if (i < activeDots) 1f else .28f),
                                    Offset(i * bar * 1.5f, size.height - h), Size(bar, h),
                                    androidx.compose.ui.geometry.CornerRadius(bar / 2))
                            }
                        }
                        Canvas(Modifier.size(width = 26.dp, height = 12.dp)) {
                            val body = Size(size.width * .86f, size.height)
                            drawRoundRect(ink.copy(alpha = .55f), Offset.Zero, body, androidx.compose.ui.geometry.CornerRadius(size.height * .3f),
                                style = Stroke(size.height * .1f))
                            drawRoundRect(ink.copy(alpha = .55f), Offset(body.width + size.width * .03f, size.height * .3f),
                                Size(size.width * .08f, size.height * .4f), androidx.compose.ui.geometry.CornerRadius(size.height * .1f))
                            status.battery?.let { level ->
                                val inset = size.height * .18f
                                drawRoundRect(batteryColor, Offset(inset, inset), Size((body.width - inset * 2) * level / 100, size.height - inset * 2),
                                    androidx.compose.ui.geometry.CornerRadius(size.height * .15f))
                            }
                        }
                    }
                    StatusGlyph.NONE -> Unit
                }
                if (!compact && style.showBatteryPercent) Text(if (status.airplane) "Airplane" else status.battery?.let { "$it%" } ?: "—",
                    color = if (status.charging && style.colorfulBattery) BatteryCharging else ink.copy(alpha = .85f),
                    fontSize = detailSize, fontWeight = FontWeight.Medium,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
            }
            if (island != null) { Spacer(Modifier.height(8.dp)); island() }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWifiFan(w: Float, wifiVisual: WifiSignalVisual, centered: Boolean = false, ink: Color = Color.White) {
    val cx = size.width / 2
    val fanY = if (centered) size.height * .95f else w * .56f
    val scale = if (centered) size.height / (w * .325f + w * .04f) * .9f else 1f
    if (wifiVisual is WifiSignalVisual.Connected) {
        for (i in 1..3) {
            val r = w * (.07f + i * .075f) * scale
            drawArc(ink.copy(alpha = signalAlpha(wifiVisual.elements[i])), 225f, 90f, false,
                Offset(cx - r, fanY - r), Size(r * 2, r * 2), style = Stroke(w * .05f * scale, cap = StrokeCap.Round))
        }
        drawCircle(ink.copy(alpha = signalAlpha(wifiVisual.elements[0])), w * .04f * scale, Offset(cx, fanY))
    } else {
        drawLine(ink.copy(alpha = .7f), Offset(cx - w * .1f, fanY - w * .2f), Offset(cx + w * .1f, fanY), w * .05f, StrokeCap.Round)
    }
}

private val BatteryCharging = Color(0xFF6EE39A)
private val BatteryLow = Color(0xFFFFB35C)

private fun signalAlpha(emphasis: SignalElementEmphasis): Float = when (emphasis) {
    SignalElementEmphasis.DIM -> .3f
    SignalElementEmphasis.NEUTRAL -> .62f
    SignalElementEmphasis.LIT -> 1f
}
