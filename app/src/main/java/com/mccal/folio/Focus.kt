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
    /** Turns on and off by itself at these times, like an iOS Focus schedule; null for manual only. */
    val schedule: FocusSchedule? = null,
)

/** Daily window in minutes after midnight (the end may be past midnight, e.g. 22:00–07:00), on ISO days 1 = Monday … 7 = Sunday. */
data class FocusSchedule(val startMinute: Int, val endMinute: Int, val days: Set<Int> = (1..7).toSet()) {
    /** Whether the window covers [time]. A window that crosses midnight belongs to the day it starts. */
    fun covers(time: java.time.LocalDateTime): Boolean {
        val minute = time.hour * 60 + time.minute
        val today = time.dayOfWeek.value
        val yesterday = if (today == 1) 7 else today - 1
        return if (startMinute <= endMinute) today in days && minute in startMinute until endMinute
        else (today in days && minute >= startMinute) || (yesterday in days && minute < endMinute)
    }
}

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

/** Focus schedules: which Focus a schedule wants now, and when to look again. Pure; unit-tested. */
internal object FocusSchedules {
    fun scheduledNow(modes: List<FocusMode>, now: java.time.LocalDateTime): FocusMode? = modes.firstOrNull { it.schedule?.covers(now) == true }

    /** The next time any schedule starts or ends after [now], within the coming week. */
    fun nextBoundary(modes: List<FocusMode>, now: java.time.LocalDateTime): java.time.LocalDateTime? {
        val minutes = modes.mapNotNull { it.schedule }.flatMap { listOf(it.startMinute, it.endMinute) }.distinct()
        if (minutes.isEmpty()) return null
        return (0..7).flatMap { day -> minutes.map { m -> now.toLocalDate().plusDays(day.toLong()).atStartOfDay().plusMinutes(m.toLong()) } }
            .filter { it.isAfter(now) }.minOrNull()
    }

    /**
     * What the active Focus should be at a boundary: a scheduled Focus turns on; a Focus whose schedule just
     * ended turns off; a Focus turned on by hand (no schedule, or outside it) is left alone.
     */
    fun activeAt(modes: List<FocusMode>, active: String?, now: java.time.LocalDateTime, previous: java.time.LocalDateTime): String? {
        scheduledNow(modes, now)?.let { return it.id }
        val current = modes.firstOrNull { it.id == active } ?: return active
        val schedule = current.schedule ?: return active
        return if (schedule.covers(previous) && !schedule.covers(now)) null else active
    }
}

internal fun focusModesToJson(modes: List<FocusMode>) = org.json.JSONArray().apply {
    modes.forEach { m -> put(org.json.JSONObject().put("id", m.id).put("name", m.name).put("color", m.color)
        .put("silence", m.silence).put("homePage", m.homePage ?: -1).put("dim", m.dimWallpaper).put("gray", m.grayscale).put("dark", m.darkTheme)
        .apply { m.schedule?.let { put("schedule", org.json.JSONObject().put("start", it.startMinute).put("end", it.endMinute)
            .put("days", org.json.JSONArray(it.days.sorted()))) } }) }
}

internal fun focusModesFromJson(array: org.json.JSONArray?): List<FocusMode> = FocusModes.withDefaults(array?.let { a -> (0 until a.length()).mapNotNull { i ->
    a.optJSONObject(i)?.let { o -> DEFAULT_FOCUS_MODES.firstOrNull { it.id == o.optString("id") }?.copy(
        silence = o.optBoolean("silence", true), homePage = o.optInt("homePage", -1).takeIf { it >= 0 },
        dimWallpaper = o.optBoolean("dim"), grayscale = o.optBoolean("gray"), darkTheme = o.optBoolean("dark"),
        schedule = o.optJSONObject("schedule")?.let { s -> FocusSchedule(s.optInt("start").coerceIn(0, 1439), s.optInt("end").coerceIn(0, 1439),
            s.optJSONArray("days")?.let { d -> (0 until d.length()).map { d.optInt(it) }.filter { it in 1..7 }.toSet() } ?: (1..7).toSet()) }) }
} }.orEmpty())

/** Turns scheduled Focuses on and off: an inexact alarm at each boundary (Folio doesn't need to be open). */
class FocusScheduleReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
        FocusScheduler.run(context)
    }
}

internal object FocusScheduler {
    private fun local(instant: java.time.Instant) = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())

    /** Applies what the schedules want now, then sets the alarm for the next boundary. */
    fun run(context: android.content.Context, previous: java.time.Instant? = null) {
        val now = java.time.Instant.now()
        // Measured from the last check, so a boundary missed while the phone was off still takes effect.
        val checks = context.getSharedPreferences("focus_rules", 0)
        val since = previous ?: java.time.Instant.ofEpochMilli(checks.getLong("last_check", now.toEpochMilli() - 60_000))
        checks.edit().putLong("last_check", now.toEpochMilli()).apply()
        val live = FolioSettingsBridge.liveModel?.get()
        val prefs = context.getSharedPreferences(SettingKeys.PREFS, 0)
        val json = runCatching { org.json.JSONObject(prefs.getString(SettingKeys.STATE, null) ?: return) }.getOrNull() ?: return
        val modes = live?.state?.value?.focusModes ?: focusModesFromJson(json.optJSONArray("focusModes"))
        val active = live?.state?.value?.activeFocus ?: json.optString("activeFocus").takeIf { it.isNotEmpty() }
        val wanted = FocusSchedules.activeAt(modes, active, local(now), local(since))
        if (wanted != active) setActive(context, wanted)
        schedule(context, modes, now)
    }

    /** Turns a Focus on (or all off) from anywhere: through Home when it's running, otherwise straight to the saved state. */
    fun setActive(context: android.content.Context, id: String?) {
        FolioSettingsBridge.liveModel?.get()?.let { it.setFocus(id); return }
        val prefs = context.getSharedPreferences(SettingKeys.PREFS, 0)
        val json = runCatching { org.json.JSONObject(prefs.getString(SettingKeys.STATE, null) ?: return) }.getOrNull() ?: return
        val modes = focusModesFromJson(json.optJSONArray("focusModes"))
        prefs.edit().putString(SettingKeys.STATE, json.put("activeFocus", id ?: "").toString()).apply()
        FocusController.apply(context, modes, modes.firstOrNull { it.id == id })
    }

    fun toggle(context: android.content.Context, id: String) {
        val active = FolioSettingsBridge.liveModel?.get()?.state?.value?.activeFocus
            ?: runCatching { org.json.JSONObject(context.getSharedPreferences(SettingKeys.PREFS, 0).getString(SettingKeys.STATE, "{}") ?: "{}")
                .optString("activeFocus").takeIf { it.isNotEmpty() } }.getOrNull()
        setActive(context, if (active == id) null else id)
    }

    fun schedule(context: android.content.Context, modes: List<FocusMode>, now: java.time.Instant = java.time.Instant.now()) {
        val alarms = context.getSystemService(android.app.AlarmManager::class.java) ?: return
        val intent = android.content.Intent(context, FocusScheduleReceiver::class.java)
        val pending = android.app.PendingIntent.getBroadcast(context, 7101, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        val next = FocusSchedules.nextBoundary(modes, local(now))
        if (next == null) { alarms.cancel(pending); return }
        val at = next.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Inexact within a minute: no exact-alarm permission needed, and a Focus a few seconds late is fine.
        alarms.setWindow(android.app.AlarmManager.RTC_WAKEUP, at, 60_000, pending)
    }
}
