package com.mccal.folio

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Which installed apps get a live icon: the phone's calendar apps and clock apps. */
internal object LiveIcons {
    private var calendar: Set<String>? = null
    private var clock: Set<String>? = null

    enum class Kind { CALENDAR, CLOCK }

    fun kind(context: Context, packageName: String): Kind? {
        if (calendar == null) load(context)
        return when (packageName) {
            in calendar.orEmpty() -> Kind.CALENDAR
            in clock.orEmpty() -> Kind.CLOCK
            else -> null
        }
    }

    private fun load(context: Context) {
        val pm = context.packageManager
        fun packages(intent: Intent) = runCatching {
            pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
        }.getOrDefault(emptySet())
        calendar = packages(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)) +
            setOf("com.samsung.android.calendar", "com.google.android.calendar")
        clock = packages(Intent(AlarmClock.ACTION_SHOW_ALARMS)) +
            setOf("com.sec.android.app.clockpackage", "com.google.android.deskclock")
    }
}

enum class IconStyle(val label: String) { DEFAULT("Default"), DARK("Dark"), TINTED("Tinted") }

enum class IconShape(val label: String) { DEFAULT("Default"), SQUIRCLE("Squircle"), CIRCLE("Circle"), ROUNDED("Rounded square") }
enum class BadgeStyle(val label: String) { OFF("Off"), DOT("Dot"), COUNT("Count") }
enum class BadgeColor(val label: String) { RED("Red"), APP("Match icon") }

/** Icon look for the whole launcher, provided from the saved settings. */
internal data class IconLook(val style: IconStyle = IconStyle.DEFAULT, val tint: Color = Color(0xFFFFB340),
    val shape: IconShape = IconShape.DEFAULT, val pack: String? = null, val badges: BadgeStyle = BadgeStyle.DOT,
    val badgeColor: BadgeColor = BadgeColor.RED)

/** Unread notification counts per package, for icon badges. */
internal val LocalBadgeCounts = androidx.compose.runtime.compositionLocalOf { emptyMap<String, Int>() }

private val SquircleShape = androidx.compose.foundation.shape.GenericShape { size, _ ->
    // Superellipse (n = 4), the iOS-style continuous-corner icon shape.
    val a = size.width / 2; val b = size.height / 2
    val steps = 64
    for (i in 0..steps) {
        val t = 2 * Math.PI * i / steps
        val c = kotlin.math.cos(t); val s = kotlin.math.sin(t)
        val x = a + a * Math.signum(c) * Math.pow(kotlin.math.abs(c), 0.5)
        val y = b + b * Math.signum(s) * Math.pow(kotlin.math.abs(s), 0.5)
        if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
    }
    close()
}

private fun IconShape.toShape(): androidx.compose.ui.graphics.Shape? = when (this) {
    IconShape.DEFAULT -> null
    IconShape.SQUIRCLE -> SquircleShape
    IconShape.CIRCLE -> androidx.compose.foundation.shape.CircleShape
    IconShape.ROUNDED -> RoundedCornerShape(22)
}
internal val LocalIconLook = androidx.compose.runtime.staticCompositionLocalOf { IconLook() }

private fun filterFor(look: IconLook): androidx.compose.ui.graphics.ColorFilter? = when (look.style) {
    IconStyle.DEFAULT -> null
    // Dimmer and a little less saturated, so bright icons sit calmly on a dark Home.
    IconStyle.DARK -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
        .62f, .08f, .05f, 0f, -6f,
        .05f, .65f, .05f, 0f, -6f,
        .05f, .08f, .62f, 0f, -6f,
        0f, 0f, 0f, 1f, 0f)))
    // Luminance mapped onto one tint color, like iOS tinted icons.
    IconStyle.TINTED -> {
        val r = look.tint.red; val g = look.tint.green; val b = look.tint.blue
        androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
            .299f * r, .587f * r, .114f * r, 0f, 0f,
            .299f * g, .587f * g, .114f * g, 0f, 0f,
            .299f * b, .587f * b, .114f * b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f)))
    }
}

/**
 * Drop-in replacement for an app's icon Image: live Clock and Calendar icons, like iPhone.
 * Pass [shape] instead of clipping the modifier so the notification badge can sit over the corner;
 * badges only show where a shape is passed (Home, dock, folders, App Library).
 */
@Composable
internal fun AppIcon(app: AppEntry, contentDescription: String?, modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape? = null, badge: Boolean = shape != null) {
    val context = LocalContext.current
    val kind = remember(app.component.packageName) { LiveIcons.kind(context, app.component.packageName) }
    val look = LocalIconLook.current
    val accent = if (look.style == IconStyle.TINTED) look.tint else null
    val lookShape = remember(look.shape) { look.shape.toShape() }
    val clipShape = lookShape ?: shape
    val packIcon by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, look.pack, app.id) {
        value = look.pack?.let { pack ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { IconPacks.icon(context, pack, app.component, 192) }
        }
    }
    val badgeCount = if (!badge || look.badges == BadgeStyle.OFF) 0 else LocalBadgeCounts.current[app.component.packageName] ?: 0
    Box(modifier.semantics { contentDescription?.let { this.contentDescription = it } }) {
        val fill = Modifier.fillMaxSize().then(if (clipShape != null) Modifier.clip(clipShape) else Modifier)
        when {
            kind == LiveIcons.Kind.CALENDAR -> CalendarIcon(fill, accent)
            kind == LiveIcons.Kind.CLOCK -> ClockIcon(fill, accent)
            else -> {
                val source = packIcon ?: app.icon
                val bitmap = remember(source) { source.asImageBitmap() }
                val filter = remember(look.style, look.tint) { filterFor(look) }
                Image(bitmap, null, fill, colorFilter = filter)
            }
        }
        if (badgeCount > 0) {
            val color = when {
                look.badgeColor == BadgeColor.RED -> BadgeRed
                look.style == IconStyle.TINTED -> look.tint
                kind == LiveIcons.Kind.CALENDAR -> IconRed
                kind == LiveIcons.Kind.CLOCK -> IconOrange
                else -> {
                    val source = packIcon ?: app.icon
                    val accent by produceState(BadgeAccents.cached(source), source) {
                        if (value == null) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { BadgeAccents.of(source) }
                    }
                    accent?.let { Color(it) } ?: BadgeRed
                }
            }
            IconBadge(badgeCount, look.badges, color)
        }
    }
}

@Composable
private fun CalendarIcon(modifier: Modifier, accent: Color?) {
    val tick by rememberMinuteTick()
    val today = remember(tick) { LocalDate.now() }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(22)).background(IconDark), contentAlignment = Alignment.Center) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val small = with(density) { (maxWidth * .17f).toSp() }
        val big = with(density) { (maxWidth * .46f).toSp() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
                color = accent ?: IconRed, fontSize = small, fontWeight = FontWeight.SemiBold, lineHeight = small * 1.15f)
            Text(today.dayOfMonth.toString(), color = accent ?: Color.White, fontSize = big,
                fontWeight = FontWeight.Light, lineHeight = big * 1.08f)
        }
    }
}

@Composable
private fun ClockIcon(modifier: Modifier, accent: Color?) {
    val tick by rememberSecondTick()
    val now = remember(tick) { LocalTime.now() }
    Box(modifier.clip(RoundedCornerShape(22)).background(IconDark)) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension * .42f
            drawCircle(IconFace, r, c)
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0 - 90).toFloat()
                val inner = if (i % 3 == 0) r * .78f else r * .84f
                drawLine(Color.White.copy(alpha = if (i % 3 == 0) 1f else .6f),
                    Offset(c.x + inner * cos(a), c.y + inner * sin(a)), Offset(c.x + r * .93f * cos(a), c.y + r * .93f * sin(a)),
                    strokeWidth = r * (if (i % 3 == 0) .05f else .03f), cap = StrokeCap.Round)
            }
            fun hand(fraction: Float, length: Float, width: Float, color: Color, tail: Float = .12f) {
                val a = (fraction * 2 * Math.PI - Math.PI / 2).toFloat()
                drawLine(color, Offset(c.x - r * tail * cos(a), c.y - r * tail * sin(a)),
                    Offset(c.x + r * length * cos(a), c.y + r * length * sin(a)), strokeWidth = r * width, cap = StrokeCap.Round)
            }
            val seconds = now.second.toFloat()
            val minutes = now.minute + seconds / 60f
            val hours = (now.hour % 12) + minutes / 60f
            hand(hours / 12f, .5f, .085f, accent ?: Color.White)
            hand(minutes / 60f, .74f, .06f, accent ?: Color.White)
            hand(seconds / 60f, .82f, .025f, accent?.copy(alpha = .7f) ?: IconOrange, tail = .2f)
            drawCircle(accent ?: IconOrange, r * .06f, c)
        }
    }
}

private val IconDark = Color(0xFF1C1C1E)
private val IconFace = Color(0xFF2C2C2E)
private val IconRed = Color(0xFFFF453A)
private val IconOrange = Color(0xFFFF9F0A)

private val BadgeRed = Color(0xFFFF3B30)

/** iOS-style badge: sits over the icon's top-right corner, a dot or a pill that widens for 2+ digits. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.IconBadge(count: Int, style: BadgeStyle, color: Color) {
    BoxWithConstraints(Modifier.matchParentSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val h = if (style == BadgeStyle.COUNT) maxWidth * .34f else maxWidth * .22f
        val pill = androidx.compose.foundation.shape.CircleShape
        val readable = if (color.luminance() > .62f) Color.Black.copy(alpha = .85f) else Color.White
        Box(Modifier.align(Alignment.TopEnd).offset(h * .32f, -h * .32f)
            .heightIn(min = h).widthIn(min = h)
            .shadow(with(density) { 2.dp }, pill, ambientColor = Color.Black, spotColor = Color.Black)
            .background(color, pill)
            .padding(horizontal = if (style == BadgeStyle.COUNT && count > 9) h * .22f else 0.dp),
            contentAlignment = Alignment.Center) {
            if (style == BadgeStyle.COUNT) {
                val size = with(density) { (h * .6f).toSp() }
                Text(if (count > 99) "99+" else count.toString(), color = readable, fontWeight = FontWeight.SemiBold,
                    fontSize = size, maxLines = 1, softWrap = false,
                    style = androidx.compose.ui.text.TextStyle(lineHeight = size,
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                            androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                            androidx.compose.ui.text.style.LineHeightStyle.Trim.Both)))
            }
        }
    }
}

/** Main color of an icon for "Match icon" badges, cached per bitmap. */
internal object BadgeAccents {
    private val cache = android.util.LruCache<android.graphics.Bitmap, Int>(256)
    private const val NONE = 0 // grayscale icons fall back to red

    fun cached(bitmap: android.graphics.Bitmap): Int? = cache.get(bitmap)?.takeIf { it != NONE }

    fun of(bitmap: android.graphics.Bitmap): Int? {
        cache.get(bitmap)?.let { return it.takeIf { c -> c != NONE } }
        val accent = runCatching {
            val soft = if (bitmap.config == android.graphics.Bitmap.Config.HARDWARE) bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else bitmap
            val small = android.graphics.Bitmap.createScaledBitmap(soft, 24, 24, true)
            val pixels = IntArray(24 * 24).also { small.getPixels(it, 0, 24, 0, 0, 24, 24) }
            dominantAccent(pixels)
        }.getOrNull()
        cache.put(bitmap, accent ?: NONE)
        return accent
    }
}

/**
 * The most prominent saturated color in ARGB [pixels], or null when the icon is mostly
 * transparent, gray, black or white. Hues are bucketed and weighted by saturation × brightness.
 */
internal fun dominantAccent(pixels: IntArray): Int? {
    val weight = FloatArray(12); val rs = FloatArray(12); val gs = FloatArray(12); val bs = FloatArray(12)
    var opaque = 0
    for (p in pixels) {
        if ((p ushr 24) < 160) continue
        opaque++
        val r = (p shr 16 and 255) / 255f; val g = (p shr 8 and 255) / 255f; val b = (p and 255) / 255f
        val max = maxOf(r, g, b); val delta = max - minOf(r, g, b)
        val sat = if (max == 0f) 0f else delta / max
        if (sat < .35f || max < .25f) continue
        val hue = when (max) { r -> ((g - b) / delta).mod(6f); g -> (b - r) / delta + 2f; else -> (r - g) / delta + 4f }
        val bucket = (hue * 2f).toInt().coerceIn(0, 11)
        val w = sat * max
        weight[bucket] += w; rs[bucket] += r * w; gs[bucket] += g * w; bs[bucket] += b * w
    }
    val best = weight.indices.maxBy { weight[it] }
    if (opaque == 0 || weight[best] < opaque * .06f) return null
    fun channel(sum: Float) = kotlin.math.round(sum / weight[best] * 255f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (channel(rs[best]) shl 16) or (channel(gs[best]) shl 8) or channel(bs[best])
}
