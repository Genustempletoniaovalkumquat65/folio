package com.mccal.folio

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * An iOS-style Focus. Turning one on can silence notifications (an Android Do Not Disturb rule Folio owns),
 * change how the phone looks (dim wallpaper, grayscale, dark theme: Android 15+ device effects), and bring
 * Home to a chosen page. Only one Focus is on at a time.
 */
data class FocusMode(
    val id: String,
    val name: String,
    val color: Long,
    val silence: Boolean = true,
    /** Home page to show while this Focus is on; null keeps whatever page you were on. */
    val homePage: Int? = null,
    val dimWallpaper: Boolean = false,
    val grayscale: Boolean = false,
    val darkTheme: Boolean = false,
)

internal val DEFAULT_FOCUS_MODES = listOf(
    FocusMode("dnd", "Do Not Disturb", 0xFF5E5CE6),
    FocusMode("sleep", "Sleep", 0xFF30B0C7, dimWallpaper = true, darkTheme = true),
    FocusMode("personal", "Personal", 0xFFBF5AF2, silence = false),
    FocusMode("work", "Work", 0xFF32ADE6),
)

internal fun FocusMode.icon(): ImageVector = when (id) {
    "sleep" -> Icons.Rounded.Bed
    "personal" -> Icons.Rounded.Person
    "work" -> Icons.Rounded.Work
    else -> Icons.Rounded.DarkMode
}

/** Pure edits to the Focus list, unit-tested. */
internal object FocusModes {
    /** The saved list, with any built-in Focus that's missing added back (older saves, future defaults). */
    fun withDefaults(saved: List<FocusMode>): List<FocusMode> =
        saved.filter { s -> DEFAULT_FOCUS_MODES.any { it.id == s.id } } + DEFAULT_FOCUS_MODES.filter { d -> saved.none { it.id == d.id } }

    fun update(list: List<FocusMode>, mode: FocusMode) = list.map { if (it.id == mode.id) mode else it }

    /** The Home page a Focus asks for, kept inside the pages that exist. */
    fun homePage(mode: FocusMode?, pages: Int): Int? = mode?.homePage?.takeIf { pages > 0 }?.coerceIn(0, pages - 1)
}

/**
 * Applies a Focus to Android: one automatic Do Not Disturb rule per Focus, owned by Folio (so turning it off
 * never touches a Do Not Disturb the user set elsewhere). Without Do Not Disturb access, Folio's own Focus
 * effects (the page, the rail icon) still work and nothing is silenced.
 */
internal object FocusController {
    private const val PREFS = "focus_rules"

    fun hasAccess(context: Context) = context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    private fun conditionId(mode: FocusMode) = Uri.parse("folio://focus/${mode.id}")

    /** Turns [active] on (updating its rule to the current settings) and every other Folio Focus rule off. */
    fun apply(context: Context, modes: List<FocusMode>, active: FocusMode?) {
        if (!hasAccess(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, 0)
        modes.forEach { mode ->
            val ruleId = prefs.getString(mode.id, null)?.takeIf { runCatching { manager.getAutomaticZenRule(it) }.getOrNull() != null }
            if (mode.id != active?.id) {
                ruleId?.let { id -> runCatching { manager.setAutomaticZenRuleState(id, Condition(conditionId(mode), mode.name, Condition.STATE_FALSE)) } }
                return@forEach
            }
            val rule = buildRule(context, mode)
            val id = if (ruleId != null) ruleId.also { runCatching { manager.updateAutomaticZenRule(it, rule) } }
                else runCatching { manager.addAutomaticZenRule(rule) }.getOrNull()?.also { prefs.edit().putString(mode.id, it).apply() }
            id?.let { runCatching { manager.setAutomaticZenRuleState(it, Condition(conditionId(mode), mode.name, Condition.STATE_TRUE)) } }
        }
    }

    /** Whether Android still has [mode]'s rule on (someone may have turned it off in Android's own settings). */
    fun isOnInAndroid(context: Context, mode: FocusMode): Boolean? {
        if (!hasAccess(context) || Build.VERSION.SDK_INT < 35) return null
        val id = context.getSharedPreferences(PREFS, 0).getString(mode.id, null) ?: return null
        return runCatching { context.getSystemService(NotificationManager::class.java).getAutomaticZenRuleState(id) == Condition.STATE_TRUE }.getOrNull()
    }

    private fun buildRule(context: Context, mode: FocusMode): AutomaticZenRule {
        val settings = ComponentName(context, FolioSettingsActivity::class.java)
        val filter = if (mode.silence) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL
        if (Build.VERSION.SDK_INT >= 35) {
            return AutomaticZenRule.Builder(mode.name, conditionId(mode))
                .setConfigurationActivity(settings)
                .setInterruptionFilter(filter)
                .setType(if (mode.id == "sleep") AutomaticZenRule.TYPE_BEDTIME else AutomaticZenRule.TYPE_OTHER)
                .setDeviceEffects(android.service.notification.ZenDeviceEffects.Builder()
                    .setShouldDimWallpaper(mode.dimWallpaper)
                    .setShouldDisplayGrayscale(mode.grayscale)
                    .setShouldUseNightMode(mode.darkTheme)
                    .build())
                .setManualInvocationAllowed(true)
                .build()
        }
        @Suppress("DEPRECATION")
        return AutomaticZenRule(mode.name, null, settings, conditionId(mode), null, filter, true)
    }
}
