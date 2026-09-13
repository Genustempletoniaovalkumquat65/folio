package com.mccal.folio

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * iOS-style legibility for things drawn straight on the wallpaper (labels, status, page dots, cards' text):
 * dark ink over light wallpapers, white ink over dark ones.
 */
@Immutable
internal data class HomeInk(val dark: Boolean) {
    val primary get() = if (dark) Color(0xFF1C1C1E) else Color.White
    val secondary get() = primary.copy(alpha = if (dark) .65f else .75f)
    val faint get() = primary.copy(alpha = if (dark) .3f else .4f)
    /** Soft shadow for labels: dark under white text, light under dark text. */
    val labelShadow get() = if (dark) Shadow(Color.White.copy(alpha = .45f), Offset(0f, 1f), 3f)
        else Shadow(Color.Black.copy(alpha = .55f), Offset(0f, 1f), 3f)
}

internal val LocalHomeInk = staticCompositionLocalOf { HomeInk(dark = false) }

/** "AUTO", "LIGHT" (white text) or "DARK" (dark text). */
internal fun homeInkFor(setting: String, wallpaperPrefersDarkText: Boolean) = HomeInk(when (setting) {
    "DARK" -> true
    "LIGHT" -> false
    else -> wallpaperPrefersDarkText
})

internal fun WallpaperColors?.prefersDarkText(): Boolean =
    this != null && colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0

/**
 * Whether the wallpaper behind Home suits dark text. Android's wallpaper: the system's own color hint (no
 * permission needed, updates when the wallpaper changes). Folio's photo: the same hint computed from it.
 * Folio's dunes: white text.
 */
@Composable
internal fun rememberWallpaperPrefersDarkText(systemWallpaper: Boolean): Boolean {
    val context = LocalContext.current
    if (systemWallpaper) {
        val manager = remember(context) { WallpaperManager.getInstance(context) }
        var dark by remember { mutableStateOf(runCatching { manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull().prefersDarkText()) }
        DisposableEffect(manager) {
            val listener = WallpaperManager.OnColorsChangedListener { colors, which ->
                if (which and WallpaperManager.FLAG_SYSTEM != 0) dark = colors.prefersDarkText()
            }
            runCatching { manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper())) }
            onDispose { runCatching { manager.removeOnColorsChangedListener(listener) } }
        }
        return dark
    }
    val revision = LauncherBackgroundCache.revision.intValue
    val dark by produceState(false, revision) {
        value = withContext(Dispatchers.Default) {
            val photo = loadLauncherBackground(context)?.takeUnless { it.isRecycled } ?: return@withContext false
            runCatching { WallpaperColors.fromBitmap(photo).prefersDarkText() }.getOrDefault(false)
        }
    }
    return dark
}
