package com.mccal.folio

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stable names for settings. */
internal val IslandEvent.kind: String get() = when (this) {
    is IslandEvent.Charging -> "CHARGING"
    is IslandEvent.Silent -> "SILENT"
    is IslandEvent.Focus -> "FOCUS"
    is IslandEvent.Bluetooth -> "BLUETOOTH"
    is IslandEvent.Message -> "MESSAGE"
}

/** Listens for brief system moments (charging, silent, focus, Bluetooth) and publishes them for the island. */
class IslandEvents private constructor(private val context: Context) {
    private var registered = false
    private var lastCharging: Boolean? = null
    private var lastRinger: Int? = null
    private var lastFocus: Boolean? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1).takeIf { it >= 0 }
                        ?.let { it * 100 / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) }
                    if (lastCharging == false && charging) { emit(IslandEvent.Charging(level)); FolioActions.onTrigger(c, FolioTrigger.CHARGING) }
                    lastCharging = charging
                }
                AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                    val mode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
                    if (lastRinger != null && mode != lastRinger) emit(IslandEvent.Silent(mode != AudioManager.RINGER_MODE_NORMAL))
                    lastRinger = mode
                }
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                    val on = c.getSystemService(NotificationManager::class.java).currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
                    if (lastFocus != null && on != lastFocus) emit(IslandEvent.Focus(on))
                    lastFocus = on
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val name = if (ContextCompat.checkSelfPermission(c, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
                        runCatching { androidx.core.content.IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)?.name }.getOrNull() else null
                    emit(IslandEvent.Bluetooth(name))
                    FolioActions.onTrigger(c, FolioTrigger.BLUETOOTH)
                }
                AudioManager.ACTION_HEADSET_PLUG -> {
                    // The sticky state delivered on registration isn't a new plug-in.
                    if (!isInitialStickyBroadcast && intent.getIntExtra("state", 0) == 1) FolioActions.onTrigger(c, FolioTrigger.HEADPHONES)
                }
            }
        }
    }

    private fun register() {
        if (registered) return
        lastRinger = context.getSystemService(AudioManager::class.java).ringerMode
        lastFocus = context.getSystemService(NotificationManager::class.java).currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED); addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED); addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
        }, ContextCompat.RECEIVER_NOT_EXPORTED) // all four are protected system broadcasts
        registered = true
    }

    private fun unregister() {
        if (registered) runCatching { context.unregisterReceiver(receiver) }
        registered = false
        lastCharging = null
    }

    private fun emit(event: IslandEvent) { mutable.value = event to System.currentTimeMillis() }

    companion object {
        private val mutable = MutableStateFlow<Pair<IslandEvent, Long>?>(null)
        val latest: StateFlow<Pair<IslandEvent, Long>?> = mutable.asStateFlow()
        const val SHOW_MS = 2_600L
        const val MESSAGE_SHOW_MS = 6_000L
        fun showMs(event: IslandEvent) = if (event is IslandEvent.Message) MESSAGE_SHOW_MS else SHOW_MS
        /** Posted by the notification listener for new messages. */
        internal fun post(event: IslandEvent) { mutable.value = event to System.currentTimeMillis() }
        @android.annotation.SuppressLint("StaticFieldLeak") // holds only the application context
        private var shared: IslandEvents? = null
        private var users = 0

        /** Reference-counted so Home and the everywhere overlay share one registration. */
        @Synchronized fun acquire(context: Context) {
            if (users++ == 0) shared = IslandEvents(context.applicationContext).also { it.register() }
        }
        @Synchronized fun release() {
            if (users > 0 && --users == 0) { shared?.unregister(); shared = null }
        }
    }

    /** Lifecycle-bound acquire/release for an activity. */
    class Observer(private val context: Context) : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = acquire(context)
        override fun onStop(owner: LifecycleOwner) = release()
    }
}
