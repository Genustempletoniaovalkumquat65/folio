package com.mccal.folio

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A notification as shown in Folio's Notification Center. */
data class NotificationItem(val key: String, val packageName: String, val appLabel: String, val icon: Bitmap?,
    val title: String?, val text: String?, val postTime: Long, val clearable: Boolean,
    val contentIntent: android.app.PendingIntent?)

/** What the side-rail island shows: now playing wins over ongoing progress. */
sealed interface IslandActivity {
    val packageName: String
    val title: String
    val icon: Bitmap?

    data class Media(override val packageName: String, override val title: String, val subtitle: String?,
        override val icon: Bitmap?, val playing: Boolean, val controller: MediaController) : IslandActivity

    data class Progress(override val packageName: String, override val title: String, val subtitle: String?,
        override val icon: Bitmap?, val fraction: Float?, val key: String) : IslandActivity

    /** Ongoing phone or VoIP call; [since] is when it started (for the running timer). */
    data class Call(override val packageName: String, override val title: String, override val icon: Bitmap?,
        val since: Long?, val key: String) : IslandActivity

    /** Countdown timer or stopwatch from a chronometer notification. [base] is wall-clock millis. */
    data class Timer(override val packageName: String, override val title: String, override val icon: Bitmap?,
        val base: Long, val countDown: Boolean, val key: String) : IslandActivity

    /** Turn-by-turn navigation. */
    data class Navigation(override val packageName: String, override val title: String, val subtitle: String?,
        override val icon: Bitmap?, val key: String) : IslandActivity
}

/** Short-lived system moments the island briefly shows, like iPhone's Dynamic Island. */
sealed interface IslandEvent {
    data class Charging(val level: Int?) : IslandEvent
    data class Silent(val on: Boolean) : IslandEvent
    data class Focus(val on: Boolean) : IslandEvent
    data class Bluetooth(val name: String?) : IslandEvent
}

/**
 * Reads only ongoing progress notifications and the active media session. Nothing is stored or
 * sent anywhere; the island simply mirrors what the system already shows in the shade.
 */
class IslandListenerService : NotificationListenerService() {
    private var sessions: MediaSessionManager? = null
    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { publish() }
    private val controllerCallbacks = mutableMapOf<android.media.session.MediaSession.Token, Pair<MediaController, MediaController.Callback>>()

    override fun onListenerConnected() {
        connected.value = true
        sessions = getSystemService(MediaSessionManager::class.java)
        runCatching { sessions?.addOnActiveSessionsChangedListener(sessionListener, component(this)) }
        instance = this
        publish()
    }

    override fun onListenerDisconnected() {
        connected.value = false
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionListener) }
        clearControllerCallbacks()
        if (instance === this) instance = null
        mutable.value = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = publish()
    override fun onNotificationRemoved(sbn: StatusBarNotification) = publish()

    private fun publish() {
        mutable.value = runCatching { currentOngoing() ?: currentMedia() ?: currentProgress() }.getOrNull()
        notificationsMutable.value = runCatching { currentNotifications() }.getOrDefault(emptyList())
    }

    private fun currentNotifications(): List<NotificationItem> = activeNotifications.orEmpty()
        .filter { sbn ->
            val n = sbn.notification
            sbn.packageName != packageName && n.flags and Notification.FLAG_GROUP_SUMMARY == 0 &&
                (n.extras.getCharSequence(Notification.EXTRA_TITLE) != null || n.extras.getCharSequence(Notification.EXTRA_TEXT) != null)
        }
        .sortedByDescending { it.postTime }
        .map { sbn ->
            val extras = sbn.notification.extras
            NotificationItem(sbn.key, sbn.packageName, appLabel(sbn.packageName), appIcon(sbn.packageName),
                extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString(),
                sbn.postTime, sbn.isClearable, sbn.notification.contentIntent)
        }

    /** Calls, navigation and timers outrank media, as on iPhone. */
    private fun currentOngoing(): IslandActivity? {
        val ongoing = runCatching { activeNotifications }.getOrNull().orEmpty()
            .filter { it.isOngoing && it.packageName != packageName }.sortedByDescending { it.postTime }
        fun title(sbn: StatusBarNotification) = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        ongoing.firstOrNull { it.notification.category == Notification.CATEGORY_CALL }?.let { sbn ->
            val n = sbn.notification
            val since = n.`when`.takeIf { n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) || it > 0 }
            return IslandActivity.Call(sbn.packageName, title(sbn) ?: "Call", appIcon(sbn.packageName), since, sbn.key)
        }
        ongoing.firstOrNull { it.notification.category == Notification.CATEGORY_NAVIGATION }?.let { sbn ->
            val extras = sbn.notification.extras
            return IslandActivity.Navigation(sbn.packageName, title(sbn) ?: "Navigating",
                extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(), appIcon(sbn.packageName), sbn.key)
        }
        ongoing.firstOrNull { it.notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) }?.let { sbn ->
            val n = sbn.notification
            return IslandActivity.Timer(sbn.packageName, title(sbn) ?: appLabel(sbn.packageName), appIcon(sbn.packageName),
                n.`when`, n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN), sbn.key)
        }
        return null
    }

    private fun currentMedia(): IslandActivity.Media? {
        val controllers = runCatching { sessions?.getActiveSessions(component(this)) }.getOrNull().orEmpty()
        watch(controllers)
        val active = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
            ?: return null
        val meta = active.metadata ?: return null
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return null
        return IslandActivity.Media(active.packageName, title, meta.getString(MediaMetadata.METADATA_KEY_ARTIST),
            appIcon(active.packageName), active.playbackState?.state == PlaybackState.STATE_PLAYING, active)
    }

    private fun currentProgress(): IslandActivity.Progress? {
        val sbn = runCatching { activeNotifications }.getOrNull().orEmpty()
            .filter { it.isOngoing && it.packageName != packageName && hasProgress(it.notification) }
            .maxByOrNull { it.postTime } ?: return null
        val extras = sbn.notification.extras
        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val progress = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: appLabel(sbn.packageName)
        return IslandActivity.Progress(sbn.packageName, title, extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            appIcon(sbn.packageName), if (!indeterminate && max > 0) (progress.toFloat() / max).coerceIn(0f, 1f) else null, sbn.key)
    }

    private fun hasProgress(n: Notification): Boolean {
        val extras = n.extras
        return extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 ||
            extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false) ||
            extras.getString(Notification.EXTRA_TEMPLATE)?.endsWith("ProgressStyle") == true
    }

    private fun watch(controllers: List<MediaController>) {
        val tokens = controllers.map { it.sessionToken }.toSet()
        controllerCallbacks.keys.filter { it !in tokens }.forEach { token ->
            controllerCallbacks.remove(token)?.let { (controller, callback) -> controller.unregisterCallback(callback) }
        }
        controllers.filter { it.sessionToken !in controllerCallbacks }.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
                override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
            }
            controller.registerCallback(callback)
            controllerCallbacks[controller.sessionToken] = controller to callback
        }
    }

    private fun clearControllerCallbacks() {
        controllerCallbacks.values.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        controllerCallbacks.clear()
    }

    private fun appIcon(pkg: String): Bitmap? = iconCache.getOrPut(pkg) {
        runCatching { packageManager.getApplicationIcon(pkg).toBitmap(96, 96) }.getOrNull()
    }

    private fun appLabel(pkg: String): String =
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    companion object {
        private val notificationsMutable = MutableStateFlow<List<NotificationItem>>(emptyList())
        val notifications: StateFlow<List<NotificationItem>> = notificationsMutable.asStateFlow()

        fun dismiss(key: String) { runCatching { instance?.cancelNotification(key) } }
        fun dismissAll() { runCatching { instance?.cancelAllNotifications() } }
        fun openNotification(context: Context, item: NotificationItem) {
            val sent = runCatching { item.contentIntent?.send(); item.contentIntent != null }.getOrDefault(false)
            if (!sent) context.packageManager.getLaunchIntentForPackage(item.packageName)
                ?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
        private val mutable = MutableStateFlow<IslandActivity?>(null)
        val activity: StateFlow<IslandActivity?> = mutable.asStateFlow()
        val connected = MutableStateFlow(false)
        private val iconCache = mutableMapOf<String, Bitmap?>()
        private var instance: IslandListenerService? = null

        fun component(context: Context) = ComponentName(context, IslandListenerService::class.java)

        fun hasAccess(context: Context): Boolean =
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?.contains(component(context).flattenToString()) == true

        fun accessSettingsIntent(context: Context): Intent =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Opens whatever the island is showing (the notification's own tap action, or the media app). */
        fun open(context: Context, activity: IslandActivity) {
            val service = instance
            val key = when (activity) {
                is IslandActivity.Progress -> activity.key
                is IslandActivity.Call -> activity.key
                is IslandActivity.Timer -> activity.key
                is IslandActivity.Navigation -> activity.key
                is IslandActivity.Media -> null
            }
            val pending = key?.let { k ->
                runCatching { service?.activeNotifications?.firstOrNull { it.key == k }?.notification?.contentIntent }.getOrNull()
            } ?: (activity as? IslandActivity.Media)?.controller?.sessionActivity
            val sent = runCatching { pending?.send(); pending != null }.getOrDefault(false)
            if (!sent) context.packageManager.getLaunchIntentForPackage(activity.packageName)
                ?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
    }
}

private val IslandAccent = Color(0xFF6EE39A)
