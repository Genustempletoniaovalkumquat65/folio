package com.mccal.folio

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** One spring for every Folio overlay so backdrop, scrim and panel always move together. */
internal val OverlaySpring = spring<Float>(dampingRatio = .86f, stiffness = Spring.StiffnessMediumLow)

/**
 * Folio's Notification Center (top-left pull) and Control Center (top-right pull). [progress] is the
 * shared overlay animation (0 closed … 1 open); the launcher behind blurs with the same value.
 */
@Composable
internal fun TopPanels(panel: ShadePanel?, progress: () -> Float, status: DeviceStatus, onClose: () -> Unit,
    onSystemPanel: (ShadePanel) -> Unit, showClock: Boolean = true, grouped: Boolean = true) {
    val open = panel == ShadePanel.NOTIFICATIONS || panel == ShadePanel.QUICK_SETTINGS
    BackHandler(open) { onClose() }
    var shown by remember { mutableStateOf<ShadePanel?>(null) }
    if (open) shown = panel
    val current = shown ?: return
    if (!open && progress() <= 0.001f) { shown = null; return }
    val lift = with(LocalDensity.current) { 28.dp.toPx() }

    // Scrim: tap or swipe up anywhere to close.
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress() }.background(Color.Black.copy(alpha = .28f))
        .then(if (open) Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onClose)
            .pointerInput(Unit) { detectVerticalDragGestures { _, drag -> if (drag < -18f) onClose() } } else Modifier)
        .testTag("top-panel-scrim"))

    val wide = LocalConfiguration.current.screenWidthDp >= 600
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = if (current == ShadePanel.NOTIFICATIONS) Alignment.TopStart else Alignment.TopEnd) {
        val panelModifier = Modifier.widthIn(max = if (wide) 460.dp else 560.dp).fillMaxWidth()
            .graphicsLayer {
                val p = progress()
                alpha = p
                translationY = -lift * (1f - p)
                scaleX = .96f + .04f * p; scaleY = scaleX
                transformOrigin = TransformOrigin(if (current == ShadePanel.NOTIFICATIONS) 0f else 1f, 0f)
            }
        if (current == ShadePanel.NOTIFICATIONS) NotificationCenter(panelModifier, showClock, grouped, onClose) { onSystemPanel(ShadePanel.NOTIFICATIONS) }
        else ControlCenter(panelModifier, status, onClose) { onSystemPanel(ShadePanel.QUICK_SETTINGS) }
    }
}

// ---------------------------------------------------------------------------------------------
// Notification Center

@Composable
private fun NotificationCenter(modifier: Modifier, showClock: Boolean, grouped: Boolean, onClose: () -> Unit, onSystem: () -> Unit) {
    val context = LocalContext.current
    val items by IslandListenerService.notifications.collectAsState()
    val hasAccess = remember { IslandListenerService.hasAccess(context) }
    val now by produceState(LocalDateTime.now()) { while (true) { kotlinx.coroutines.delay(10_000); value = LocalDateTime.now() } }
    val clock = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    var expandedGroup by remember { mutableStateOf<String?>(null) }
    val groups = remember(items) { items.groupBy { it.packageName }.values.sortedByDescending { g -> g.maxOf { it.postTime } } }

    Column(modifier.testTag("notification-center"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showClock) Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), color = Color.White.copy(alpha = .9f), fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold)
            Text(now.format(DateTimeFormatter.ofPattern(clock)), color = Color.White, fontSize = 76.sp, fontWeight = FontWeight.Bold,
                lineHeight = 80.sp)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Notification Center", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            GlassIconButton(Icons.Rounded.Tune, "Android notifications") { onClose(); onSystem() }
            if (items.any { it.clearable }) { Spacer(Modifier.width(8.dp)); GlassIconButton(Icons.Rounded.Close, "Clear all") { IslandListenerService.dismissAll() } }
        }
        when {
            !hasAccess -> EmptyNote("Allow notification access to see notifications here.", "Allow") {
                runCatching { context.startActivity(IslandListenerService.accessSettingsIntent(context)) }
            }
            items.isEmpty() -> Text("No Notifications", color = Color.White.copy(alpha = .6f), fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                groups.forEach { group ->
                    val pkg = group.first().packageName
                    val expanded = !grouped || expandedGroup == pkg || group.size == 1
                    if (expanded) {
                        if (group.size > 1) item("$pkg-header") {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(group.first().appLabel, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                PanelPill("Show less") { expandedGroup = null }
                            }
                        }
                        items(group, key = { it.key }) { item -> NotificationCard(item, Modifier.animateItem()) {
                            onClose(); IslandListenerService.openNotification(context, item)
                        } }
                    } else item("$pkg-stack") {
                        StackedNotification(group, Modifier.animateItem()) { expandedGroup = pkg }
                    }
                }
            }
        }
    }
}

@Composable
private fun StackedNotification(group: List<NotificationItem>, modifier: Modifier, onExpand: () -> Unit) {
    Box(modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        // Two receding cards behind, like an iPhone notification stack.
        Box(Modifier.matchParentSize().padding(horizontal = 22.dp).offset(y = 12.dp).clip(RoundedCornerShape(22.dp)).background(NotifGlass.copy(alpha = .35f)))
        Box(Modifier.matchParentSize().padding(horizontal = 11.dp).offset(y = 6.dp).clip(RoundedCornerShape(22.dp)).background(NotifGlass.copy(alpha = .6f)))
        NotificationCard(group.first(), Modifier, extraCount = group.size - 1, onOpen = onExpand)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationCard(item: NotificationItem, modifier: Modifier, extraCount: Int = 0, onOpen: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it != SwipeToDismissBoxValue.Settled && item.clearable) { IslandListenerService.dismiss(item.key); true } else false
    })
    SwipeToDismissBox(dismissState, modifier = modifier, backgroundContent = {}, enableDismissFromStartToEnd = item.clearable,
        enableDismissFromEndToStart = item.clearable) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(NotifGlass).clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            item.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(38.dp).clip(RoundedCornerShape(9.dp))) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title ?: item.appLabel, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(relativeTime(item.postTime), color = Color.White.copy(alpha = .55f), fontSize = 13.sp)
                }
                item.text?.let { Text(it, color = Color.White.copy(alpha = .88f), fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp) }
                if (extraCount > 0) Text("$extraCount more notification${if (extraCount > 1) "s" else ""}",
                    color = Color.White.copy(alpha = .55f), fontSize = 13.sp)
            }
        }
    }
}

private fun relativeTime(time: Long): String {
    val minutes = (System.currentTimeMillis() - time) / 60_000
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}

// ---------------------------------------------------------------------------------------------
// Control Center: a strict 4-column grid; every module is a whole number of cells.

@Composable
private fun ControlCenter(modifier: Modifier, status: DeviceStatus, onClose: () -> Unit, onSystem: () -> Unit) {
    val context = LocalContext.current
    val controls = remember { DeviceControls(context) }
    DisposableEffect(controls) { controls.start(); onDispose { controls.stop() } }
    val media = (IslandListenerService.activity.collectAsState().value as? IslandActivity.Media)
    val open = { intent: Intent -> onClose(); runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; Unit }

    BoxWithConstraints(modifier.testTag("control-center")) {
        val gap = 14.dp
        val cell = (maxWidth - gap * 3) / 4
        fun span(n: Int): Dp = cell * n + gap * (n - 1)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            // Row 1–2: connectivity (2×2) and now playing (2×2)
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Module(Modifier.size(span(2))) {
                    Column(Modifier.fillMaxSize().padding(cell * .14f), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            RoundToggle(Icons.Rounded.AirplanemodeActive, "Airplane mode", status.airplane, AccentOrange, cell * .72f) {
                                open(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) }
                            RoundToggle(Icons.Rounded.SignalCellularAlt, "Mobile data", !status.airplane && (status.cellularLevel ?: 0) > 0, AccentGreen, cell * .72f) {
                                open(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            RoundToggle(Icons.Rounded.Wifi, "Wi-Fi", status.wifiConnected, AccentBlue, cell * .72f) { open(Intent(Settings.Panel.ACTION_WIFI)) }
                            RoundToggle(Icons.Rounded.Bluetooth, "Bluetooth", controls.bluetoothOn, AccentBlue, cell * .72f) {
                                open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                        }
                    }
                }
                Module(Modifier.size(span(2)).clickable(enabled = media != null) { media?.let { onClose(); IslandListenerService.open(context, it) } }) {
                    Column(Modifier.fillMaxSize().padding(cell * .16f), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            media?.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(cell * .42f).clip(RoundedCornerShape(8.dp))) }
                                ?: Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = .5f), modifier = Modifier.size(cell * .36f))
                        }
                        Column {
                            Text(media?.title ?: "Not Playing", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(media?.subtitle ?: " ", color = Color.White.copy(alpha = .6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            val t = media?.controller?.transportControls
                            val tint = Color.White.copy(alpha = if (t != null) 1f else .35f)
                            Icon(Icons.Rounded.FastRewind, "Previous", tint = tint, modifier = Modifier.size(cell * .34f).clip(CircleShape).clickable(t != null) { t?.skipToPrevious() })
                            Icon(if (media?.playing == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", tint = tint,
                                modifier = Modifier.size(cell * .44f).clip(CircleShape).clickable(t != null) { if (media?.playing == true) t?.pause() else t?.play() })
                            Icon(Icons.Rounded.FastForward, "Next", tint = tint, modifier = Modifier.size(cell * .34f).clip(CircleShape).clickable(t != null) { t?.skipToNext() })
                        }
                    }
                }
            }
            // Row 3–4: four 1×1 toggles on the left; brightness and volume sliders (1×2) on the right
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        SquareToggle(Icons.Rounded.ScreenLockRotation, "Rotation lock", controls.rotationLocked, AccentRed, cell) {
                            if (!controls.toggleRotationLock()) open(controls.writeSettingsIntent()) }
                        SquareToggle(Icons.Rounded.DarkMode, "Do Not Disturb", controls.dndOn, AccentPurple, cell) {
                            if (!controls.toggleDnd()) open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        SquareToggle(Icons.Rounded.FlashlightOn, "Flashlight", controls.torchOn, Color.White, cell) { controls.toggleTorch() }
                        SquareToggle(Icons.Rounded.PhotoCamera, "Camera", false, Color.White, cell) { open(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }
                    }
                }
                TallSlider(Icons.Rounded.LightMode, "Brightness", controls.brightness, cell, span(2)) { value ->
                    if (!controls.changeBrightness(value)) open(controls.writeSettingsIntent()) }
                TallSlider(Icons.AutoMirrored.Rounded.VolumeUp, "Volume", controls.volume, cell, span(2)) { controls.changeVolume(it) }
            }
            // Row 5: shortcuts
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                SquareToggle(Icons.Rounded.Timer, "Timer", false, Color.White, cell) { open(Intent(AlarmClock.ACTION_SHOW_TIMERS)) }
                SquareToggle(Icons.Rounded.Calculate, "Calculator", false, Color.White, cell) {
                    open(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)) }
                SquareToggle(Icons.Rounded.Settings, "Settings", false, Color.White, cell) { open(Intent(Settings.ACTION_SETTINGS)) }
                SquareToggle(Icons.Rounded.Tune, "Android quick settings", false, Color.White, cell) { onClose(); onSystem() }
            }
        }
    }
}

@Composable
private fun Module(modifier: Modifier, content: @Composable BoxScope.() -> Unit) =
    Box(modifier.clip(RoundedCornerShape(26.dp)).background(ModuleGlass), content = content)

@Composable
private fun RoundToggle(icon: ImageVector, label: String, on: Boolean, accent: Color, size: Dp, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(if (on) accent else Color.White.copy(alpha = .16f))
        .clickable(onClick = onClick).semantics { contentDescription = "$label, ${if (on) "on" else "off"}" },
        contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * .46f))
    }
}

@Composable
private fun SquareToggle(icon: ImageVector, label: String, on: Boolean, accent: Color, size: Dp, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * .3f)).background(if (on) accent else ModuleGlass)
        .clickable(onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (on && accent == Color.White) Color.Black else Color.White, modifier = Modifier.size(size * .4f))
    }
}

@Composable
private fun TallSlider(icon: ImageVector, label: String, value: Float, width: Dp, height: Dp, onValue: (Float) -> Unit) {
    val shown by androidx.compose.animation.core.animateFloatAsState(value.coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessMedium), label = label)
    Box(Modifier.size(width, height).clip(RoundedCornerShape(width * .36f)).background(ModuleGlass)
        .pointerInput(Unit) {
            detectVerticalDragGestures { change, _ -> onValue((1f - change.position.y / size.height).coerceIn(0f, 1f)) }
        }
        .semantics { contentDescription = "$label ${(value * 100).toInt()} percent" }) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(shown).background(Color.White))
        Icon(icon, null, tint = if (shown > .18f) Color(0xFF3A3A3C) else Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = width * .22f).size(width * .36f))
    }
}

@Composable
private fun GlassIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(NotifGlass).clickable(onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PanelPill(label: String, onClick: () -> Unit) {
    Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(NotifGlass).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
private fun EmptyNote(text: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(NotifGlass).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text, color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
        if (action != null) PanelPill(action, onAction)
    }
}

private val ModuleGlass = Color(0xFF1C1C1E).copy(alpha = .55f)
private val NotifGlass = Color(0xFF2C2C2E).copy(alpha = .62f)
private val AccentBlue = Color(0xFF0A84FF)
private val AccentGreen = Color(0xFF30D158)
private val AccentOrange = Color(0xFFFF9F0A)
private val AccentPurple = Color(0xFF5E5CE6)
private val AccentRed = Color(0xFFFF453A)

private class DeviceControls(private val context: Context) {
    private val camera = context.getSystemService(CameraManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val torchId = runCatching {
        camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()

    var torchOn by mutableStateOf(false); private set
    var volume by mutableFloatStateOf(0f); private set
    var brightness by mutableFloatStateOf(.5f); private set
    var rotationLocked by mutableStateOf(false); private set
    var dndOn by mutableStateOf(false); private set
    var bluetoothOn by mutableStateOf(false); private set

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { if (cameraId == torchId) torchOn = enabled }
    }

    fun start() {
        runCatching { camera.registerTorchCallback(torchCallback, null) }
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
        brightness = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(.5f)
        rotationLocked = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 0 }.getOrDefault(false)
        dndOn = notifications.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
        bluetoothOn = runCatching { context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.isEnabled == true }.getOrDefault(false)
    }

    fun stop() { runCatching { camera.unregisterTorchCallback(torchCallback) } }

    fun toggleTorch() { torchId?.let { id -> runCatching { camera.setTorchMode(id, !torchOn) } } }

    fun changeVolume(value: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (value * max).toInt(), 0); volume = value }
    }

    /** Returns false when "Modify system settings" hasn't been granted yet. */
    fun changeBrightness(value: Float): Boolean {
        if (!Settings.System.canWrite(context)) return false
        runCatching {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (value * 255).toInt().coerceIn(1, 255))
            brightness = value
        }
        return true
    }

    fun toggleRotationLock(): Boolean {
        if (!Settings.System.canWrite(context)) return false
        runCatching {
            Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (rotationLocked) 1 else 0)
            rotationLocked = !rotationLocked
        }
        return true
    }

    /** Returns false when Do Not Disturb access hasn't been granted yet. */
    fun toggleDnd(): Boolean {
        if (!notifications.isNotificationPolicyAccessGranted) return false
        notifications.setInterruptionFilter(if (dndOn) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        dndOn = !dndOn
        return true
    }

    fun writeSettingsIntent() = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
}

