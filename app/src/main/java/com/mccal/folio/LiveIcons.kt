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

/** Icon look for the whole launcher, provided from the saved settings. */
internal data class IconLook(val style: IconStyle = IconStyle.DEFAULT, val tint: Color = Color(0xFFFFB340))
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

/** Drop-in replacement for an app's icon Image: live Clock and Calendar icons, like iPhone. */
@Composable
internal fun AppIcon(app: AppEntry, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val kind = remember(app.component.packageName) { LiveIcons.kind(context, app.component.packageName) }
    val look = LocalIconLook.current
    val accent = if (look.style == IconStyle.TINTED) look.tint else null
    when (kind) {
        LiveIcons.Kind.CALENDAR -> CalendarIcon(modifier.semantics { contentDescription?.let { this.contentDescription = it } }, accent)
        LiveIcons.Kind.CLOCK -> ClockIcon(modifier.semantics { contentDescription?.let { this.contentDescription = it } }, accent)
        null -> {
            val bitmap = remember(app.icon) { app.icon.asImageBitmap() }
            val filter = remember(look) { filterFor(look) }
            Image(bitmap, contentDescription, modifier, colorFilter = filter)
        }
    }
}

@Composable
private fun CalendarIcon(modifier: Modifier, accent: Color?) {
    val today by produceState(LocalDate.now()) {
        while (true) { delay(60_000); value = LocalDate.now() }
    }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(22)).background(IconDark), contentAlignment = Alignment.Center) {
        val unit = maxWidth.value
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
                color = accent ?: IconRed, fontSize = (unit * .17f).sp, fontWeight = FontWeight.SemiBold, lineHeight = (unit * .2f).sp)
            Text(today.dayOfMonth.toString(), color = accent ?: Color.White, fontSize = (unit * .46f).sp,
                fontWeight = FontWeight.Light, lineHeight = (unit * .5f).sp)
        }
    }
}

@Composable
private fun ClockIcon(modifier: Modifier, accent: Color?) {
    val now by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(1_000L - (System.currentTimeMillis() % 1_000L))
        }
    }
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
