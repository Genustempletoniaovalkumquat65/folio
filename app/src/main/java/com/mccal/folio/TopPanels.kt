@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
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
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
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
    onSystemPanel: (ShadePanel) -> Unit, showClock: Boolean = true, grouped: Boolean = true,
    ccControls: List<String> = CcControl.DEFAULTS, onCcControls: (List<String>) -> Unit = {},
    ccSize: PanelSize = PanelSize.STANDARD, ccCentered: Boolean = false, ncSplit: Boolean = true) {
    val open = panel == ShadePanel.NOTIFICATIONS || panel == ShadePanel.QUICK_SETTINGS
    BackHandler(open) { onClose() }
    var shown by remember { mutableStateOf<ShadePanel?>(null) }
    if (open) shown = panel
    val current = shown ?: return
    if (!open && progress() <= 0.001f) { shown = null; return }
    val lift = with(LocalDensity.current) { 28.dp.toPx() }

    // Scrim: tap or swipe up anywhere to close.
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress() }.background(FolioGlass.scrim)
        .then(if (open) Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onClose)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(onDragStart = { total = 0f }) { _, drag -> total += drag; if (total < -48.dp.toPx()) onClose() }
            } else Modifier)
        .testTag("top-panel-scrim"))

    val wide = LocalConfiguration.current.screenWidthDp >= 600
    // Unfolded, Notification Center can be iPad-style: big clock on the left, notifications on the right.
    val split = wide && ncSplit && current == ShadePanel.NOTIFICATIONS
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.folioSafeTop).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp),
        contentAlignment = when {
            split -> Alignment.TopEnd
            current == ShadePanel.NOTIFICATIONS -> Alignment.TopStart
            wide && ccCentered -> Alignment.TopCenter
            else -> Alignment.TopEnd
        }) {
        // Notification Center uses the width; Control Center is a compact fixed grid (like iPad) so it isn't oversized.
        val panelModifier = Modifier.widthIn(max = if (split) 540.dp else if (wide) 460.dp else 560.dp)
            .then(if (current == ShadePanel.NOTIFICATIONS) Modifier.fillMaxWidth() else Modifier)
            .then(if (split) Modifier.fillMaxHeight() else Modifier)
            // Taps on gaps inside the panel must not fall through to the scrim and close it.
            .pointerInput(Unit) { detectTapGestures() }
            .graphicsLayer {
                val p = progress()
                alpha = p
                translationY = -lift * (1f - p)
                scaleX = .96f + .04f * p; scaleY = scaleX
                transformOrigin = TransformOrigin(if (current == ShadePanel.NOTIFICATIONS) 0f else 1f, 0f)
            }
        if (split) {
            val clockLift = lift
            SplitClock(Modifier.align(Alignment.CenterStart).fillMaxWidth(.4f).graphicsLayer {
                val p = progress(); alpha = p; translationY = -clockLift * (1f - p)
            })
        }
        if (current == ShadePanel.NOTIFICATIONS) NotificationCenter(panelModifier, showClock && !split, grouped, tall = split, onClose = onClose) { onSystemPanel(ShadePanel.NOTIFICATIONS) }
        else ControlCenter(panelModifier, status, ccControls, onCcControls, ccSize, wide, onClose) { onSystemPanel(ShadePanel.QUICK_SETTINGS) }
    }
}

// ---------------------------------------------------------------------------------------------
// Notification Center

@Composable
private fun NotificationCenter(modifier: Modifier, showClock: Boolean, grouped: Boolean, tall: Boolean = false, onClose: () -> Unit, onSystem: () -> Unit) {
    val context = LocalContext.current
    val items by IslandListenerService.notifications.collectAsState()
    val hasAccess = remember { IslandListenerService.hasAccess(context) }
    val tick by rememberMinuteTick()
    val now = remember(tick) { LocalDateTime.now() }
    val clock = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    var expandedGroup by remember { mutableStateOf<String?>(null) }
    val groups = remember(items) { items.groupBy { it.packageName }.values.sortedByDescending { g -> g.maxOf { it.postTime } } }

    // Keyboard for quick reply pushes the list up instead of covering it.
    Column(modifier.windowInsetsPadding(WindowInsets.imeAnimationTarget).testTag("notification-center"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showClock) Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), color = Color.White.copy(alpha = .9f), fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold)
            Text(now.format(DateTimeFormatter.ofPattern(clock)), color = Color.White, fontSize = 76.sp, fontWeight = FontWeight.Bold,
                lineHeight = 80.sp)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Notification Center", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            GlassIconButton(Icons.Rounded.Tune, "Android notifications") { onClose(); onSystem() }
            if (items.any { it.clearable }) {
                Spacer(Modifier.width(8.dp))
                // Like iPhone: the × turns into "Clear" and a second tap clears everything.
                var confirmClear by remember { mutableStateOf(false) }
                LaunchedEffect(confirmClear) { if (confirmClear) { kotlinx.coroutines.delay(3_000); confirmClear = false } }
                androidx.compose.animation.AnimatedContent(confirmClear, label = "clear-all") { confirm ->
                    if (confirm) PanelPill("Clear") { confirmClear = false; IslandListenerService.dismissAll() }
                    else GlassIconButton(Icons.Rounded.Close, "Clear all") { confirmClear = true }
                }
            }
        }
        when {
            !hasAccess -> EmptyNote("Allow notification access to see notifications here.", "Allow") {
                onClose(); runCatching { context.startActivity(IslandListenerService.accessSettingsIntent(context)) }
            }
            items.isEmpty() -> Text("No Notifications", color = Color.White.copy(alpha = .6f), fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            else -> LazyColumn(Modifier.fillMaxWidth().then(if (tall) Modifier.weight(1f, fill = false) else Modifier.heightIn(max = 620.dp)),
                verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
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
        Box(Modifier.matchParentSize().padding(horizontal = 24.dp).offset(y = 14.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF242428).copy(alpha = .45f)))
        Box(Modifier.matchParentSize().padding(horizontal = 12.dp).offset(y = 7.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF242428).copy(alpha = .66f)))
        key(group.first().key) { NotificationCard(group.first(), Modifier, extraCount = group.size - 1, onOpen = onExpand) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationCard(item: NotificationItem, modifier: Modifier, extraCount: Int = 0, onOpen: () -> Unit) {
    // iOS: swipe left to reveal Options and Clear; a long swipe clears; tapping the card closes the buttons.
    val scope = rememberCoroutineScope()
    val swipe = remember(item.key) { androidx.compose.animation.core.Animatable(0f) }
    val density = LocalDensity.current
    val reveal = with(density) { (if (item.clearable) 176.dp else 92.dp).toPx() }
    var options by remember(item.key) { mutableStateOf(false) }
    fun settle(to: Float) = scope.launch { swipe.animateTo(to, spring(dampingRatio = .85f, stiffness = Spring.StiffnessMediumLow)) }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(22.dp))) {
        val widthPx = constraints.maxWidth.toFloat()
        if (swipe.value < -1f) Row(Modifier.matchParentSize().padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically) {
            SwipeAction("Options") { settle(0f); options = true }
            if (item.clearable) SwipeAction("Clear") { scope.launch { swipe.animateTo(-widthPx); IslandListenerService.dismiss(item.key) } }
        }
        Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(swipe.value.roundToInt(), 0) }
            .pointerInput(item.key, widthPx) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            item.clearable && swipe.value < -widthPx * .55f -> scope.launch { swipe.animateTo(-widthPx); IslandListenerService.dismiss(item.key) }
                            swipe.value < -reveal / 2 -> settle(-reveal)
                            else -> settle(0f)
                        }
                    },
                    onDragCancel = { settle(0f) },
                ) { change, amount -> change.consume(); scope.launch { swipe.snapTo((swipe.value + amount).coerceIn(-widthPx, 0f)) } }
            }) {
        val context = LocalContext.current
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        var cardBounds by remember { mutableStateOf(android.graphics.Rect()) }
        Box(Modifier.onGloballyPositioned { cardBounds = it.boundsInWindow().let { b -> android.graphics.Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt()) } }) {
        if (options) NotificationOptions(item, cardBounds) { options = false }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(NotifGlass)
            .border(FolioGlass.edge, RoundedCornerShape(22.dp))
            .combinedClickable(onClick = { if (swipe.value < -1f) settle(0f) else onOpen() }, onLongClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); options = true
            }, onLongClickLabel = "Notification options")
            .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            item.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(38.dp).clip(RoundedCornerShape(9.dp))) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title ?: item.appLabel, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(relativeTime(item.postTime), color = FolioGlass.secondary, fontSize = 13.sp)
                }
                item.text?.let { Text(it.lines().filter(String::isNotBlank).joinToString(" "), color = Color.White.copy(alpha = .88f),
                    fontSize = 14.sp, maxLines = if (extraCount > 0) 2 else 4, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp) }
                if (extraCount > 0) Text("$extraCount more from ${item.appLabel}",
                    color = FolioGlass.secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                if (extraCount == 0 && (item.canReply || item.canMarkRead)) {
                    var replying by remember(item.key) { mutableStateOf(false) }
                    if (replying) QuickReplyField(item.title, Modifier.padding(top = 8.dp),
                        onSend = { IslandListenerService.reply(context, item.key, it) }, onDone = { replying = false })
                    else Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (item.canReply) MessageActionPill("Reply") { replying = true }
                        if (item.canMarkRead) MessageActionPill("Mark as Read") { IslandListenerService.markRead(item.key) }
                    }
                }
            }
        }
        }
    }
}
}

@Composable
private fun SwipeAction(label: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxHeight().width(80.dp).clip(RoundedCornerShape(22.dp)).background(NotifGlass)
        .border(FolioGlass.edge, RoundedCornerShape(22.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** Control Center size choice (the largest a small control can be). */
enum class PanelSize(val label: String, val cell: Dp) { COMPACT("Compact", 58.dp), STANDARD("Standard", 66.dp), LARGE("Large", 76.dp) }

/** iPad-style clock and date for the split (unfolded) Notification Center. */
@Composable
private fun SplitClock(modifier: Modifier) {
    val context = LocalContext.current
    val tick by rememberMinuteTick()
    val now = remember(tick) { LocalDateTime.now() }
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    Column(modifier.padding(start = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), color = Color.White.copy(alpha = .9f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(now.format(DateTimeFormatter.ofPattern(pattern)), color = Color.White, fontSize = 112.sp, fontWeight = FontWeight.Bold, lineHeight = 116.sp)
    }
}

/**
 * iPhone-style options for a long-pressed notification: the card lifts where it is over a blurred, dimmed
 * background and a compact menu appears under it (above it when there's no room).
 */
@Composable
private fun NotificationOptions(item: NotificationItem, bounds: android.graphics.Rect, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = .75f, stiffness = Spring.StiffnessMediumLow)) }
    fun act(action: () -> Unit) { onDismiss(); action() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = androidx.compose.ui.platform.LocalView.current
        LaunchedEffect(view) {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { w ->
                w.setDimAmount(0f)
                // Same full-screen window as the launcher, so the lifted card lines up exactly with the original.
                androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).apply {
                    systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                }
                w.attributes = w.attributes.apply { layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS }
                if (android.os.Build.VERSION.SDK_INT >= 31) { w.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND); w.attributes = w.attributes.apply { blurBehindRadius = 40 } }
            }
        }
        var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionOnScreen() }
            .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }.background(Color.Black.copy(alpha = .35f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)) {
            val screenH = with(density) { maxHeight.toPx() }
            val top = bounds.top - origin.y
            val left = bounds.left - origin.x
            // The card stays exactly where the notification is; the menu goes under it, or above when it wouldn't fit.
            val gap = with(density) { 10.dp.toPx() }
            var cardH by remember { mutableIntStateOf(bounds.height()) }
            var menuH by remember { mutableIntStateOf(with(density) { 230.dp.roundToPx() }) }
            val below = top + cardH + gap + menuH + with(density) { 16.dp.toPx() } < screenH
            Box(Modifier.fillMaxSize()) {
                val card: @Composable () -> Unit = {
                    Row(Modifier.offset { androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()) }
                        .width(with(density) { bounds.width().toDp() }).heightIn(min = with(density) { bounds.height().toDp() })
                        .onSizeChanged { cardH = it.height }
                        .graphicsLayer { val s = 1f + .03f * appear.value; scaleX = s; scaleY = s }
                        .clip(RoundedCornerShape(22.dp)).background(Color(0xFF2C2C30)).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        item.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(38.dp).clip(RoundedCornerShape(9.dp))) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title ?: item.appLabel, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            item.text?.let { Text(it, color = Color.White.copy(alpha = .85f), fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
                val menu: @Composable () -> Unit = {
                    Column(Modifier.offset { androidx.compose.ui.unit.IntOffset(left.toInt(),
                            (if (below) top + cardH + gap else top - gap - menuH).toInt().coerceAtLeast(0)) }
                        .width(minOf(280.dp, with(density) { bounds.width().toDp() })).onSizeChanged { menuH = it.height }
                        .graphicsLayer { val s = .8f + .2f * appear.value; scaleX = s; scaleY = s
                            transformOrigin = TransformOrigin(0f, if (below) 0f else 1f) }
                        .clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A2A2E).copy(alpha = .97f)).border(FolioGlass.edge, RoundedCornerShape(16.dp))
                        .clickable(remember { MutableInteractionSource() }, null) {}) {
                        MenuRow("Open", Icons.Rounded.OpenInNew) { act { IslandListenerService.openNotification(context, item) } }
                        if (item.clearable) {
                            MenuDivider()
                            MenuRow("Snooze for 1 Hour", Icons.Rounded.Snooze) { act { IslandListenerService.snooze(item.key, 60 * 60_000L) } }
                            MenuDivider()
                            MenuRow("Snooze Until Tomorrow", Icons.Rounded.Bedtime) { act {
                                val morning = java.time.LocalDate.now().plusDays(1).atTime(8, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                IslandListenerService.snooze(item.key, (morning - System.currentTimeMillis()).coerceAtLeast(60_000L))
                            } }
                        }
                        MenuDivider()
                        MenuRow("Notification Settings", Icons.Rounded.Tune) { act {
                            val intent = item.channelId?.let { MessageChannel(item.packageName, item.appLabel, it, null, 0).settingsIntent() }
                                ?: Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, item.packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        } }
                        if (item.clearable) { MenuDivider(); MenuRow("Clear", Icons.Rounded.Close, destructive = true) { act { IslandListenerService.dismiss(item.key) } } }
                    }
                }
                card(); menu()
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

/** Small (1×1) controls the user can add, remove and order. */
internal enum class CcControl(val label: String) {
    ROTATION("Rotation Lock"), DND("Do Not Disturb"), FLASHLIGHT("Flashlight"), CAMERA("Camera"), TIMER("Timer"),
    ALARM("Alarm"), CALCULATOR("Calculator"), SCREENSHOT("Screenshot"), LOCK("Lock Screen"), WALLET("Wallet"),
    NOTES("Notes"), HOTSPOT("Hotspot"), BATTERY_SAVER("Battery Saver"), SETTINGS("Settings"), SYSTEM("Android Quick Settings");

    companion object {
        val DEFAULTS = listOf(ROTATION, DND, FLASHLIGHT, CAMERA, TIMER, CALCULATOR, SCREENSHOT, SYSTEM).map { it.name }
        val WALLETS = listOf("com.google.android.apps.walletnfcrel", "com.samsung.android.spay")
        val NOTE_APPS = listOf("com.samsung.android.app.notes", "com.google.android.keep")
    }
}

private fun CcControl.icon(): ImageVector = when (this) {
    CcControl.ROTATION -> Icons.Rounded.ScreenLockRotation
    CcControl.DND -> Icons.Rounded.DarkMode
    CcControl.FLASHLIGHT -> Icons.Rounded.FlashlightOn
    CcControl.CAMERA -> Icons.Rounded.PhotoCamera
    CcControl.TIMER -> Icons.Rounded.Timer
    CcControl.ALARM -> Icons.Rounded.Alarm
    CcControl.CALCULATOR -> Icons.Rounded.Calculate
    CcControl.SCREENSHOT -> Icons.Rounded.Screenshot
    CcControl.LOCK -> Icons.Rounded.Lock
    CcControl.WALLET -> Icons.Rounded.Wallet
    CcControl.NOTES -> Icons.Rounded.EditNote
    CcControl.HOTSPOT -> Icons.Rounded.WifiTethering
    CcControl.BATTERY_SAVER -> Icons.Rounded.BatterySaver
    CcControl.SETTINGS -> Icons.Rounded.Settings
    CcControl.SYSTEM -> Icons.Rounded.Tune
}

@Composable
private fun ControlCenter(modifier: Modifier, status: DeviceStatus, controlNames: List<String>, onControls: (List<String>) -> Unit,
    size: PanelSize, wide: Boolean, onClose: () -> Unit, onSystem: () -> Unit) {
    val context = LocalContext.current
    val controls = remember { DeviceControls(context) }
    DisposableEffect(controls) { controls.start(); onDispose { controls.stop() } }
    val media = (IslandListenerService.activity.collectAsState().value as? IslandActivity.Media)
    val open = { intent: Intent -> onClose(); runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; Unit }
    fun launchFirst(packages: List<String>) = packages.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
    fun available(control: CcControl) = when (control) {
        CcControl.WALLET -> launchFirst(CcControl.WALLETS) != null
        CcControl.NOTES -> launchFirst(CcControl.NOTE_APPS) != null
        CcControl.FLASHLIGHT -> controls.hasTorch
        else -> true
    }
    val chosen = remember(controlNames) { controlNames.mapNotNull { n -> CcControl.entries.firstOrNull { it.name == n } } }.filter(::available)
    val edit = remember { HomeEditMode() }
    BackHandler(edit.active) { edit.stop() }

    fun isOn(control: CcControl) = when (control) {
        CcControl.ROTATION -> controls.rotationLocked
        CcControl.DND -> controls.dndOn
        CcControl.FLASHLIGHT -> controls.torchOn
        else -> false
    }
    fun accent(control: CcControl) = when (control) {
        CcControl.ROTATION -> AccentRed
        CcControl.DND -> AccentPurple
        else -> Color.White
    }
    fun run(control: CcControl) {
        when (control) {
            CcControl.ROTATION -> if (!controls.toggleRotationLock()) open(controls.writeSettingsIntent())
            CcControl.DND -> if (!controls.toggleDnd()) open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            CcControl.FLASHLIGHT -> controls.toggleTorch()
            CcControl.CAMERA -> open(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            CcControl.TIMER -> open(Intent(AlarmClock.ACTION_SHOW_TIMERS))
            CcControl.ALARM -> open(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            CcControl.CALCULATOR -> open(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))
            // Close first so the panel isn't in the screenshot.
            // (A main-thread post, not a composition scope: the panel leaves composition as it closes.)
            CcControl.SCREENSHOT -> { onClose(); android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                SystemShadeAccessibilityService.global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) }, 450) }
            CcControl.LOCK -> { onClose(); if (!SystemShadeAccessibilityService.global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) onSystem() }
            CcControl.WALLET -> launchFirst(CcControl.WALLETS)?.let(open)
            CcControl.NOTES -> launchFirst(CcControl.NOTE_APPS)?.let(open)
            CcControl.HOTSPOT -> open(Intent("android.settings.TETHER_SETTINGS").takeIf { it.resolveActivity(context.packageManager) != null }
                ?: Intent(Settings.ACTION_WIRELESS_SETTINGS))
            CcControl.BATTERY_SAVER -> open(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
            CcControl.SETTINGS -> open(Intent(Settings.ACTION_SETTINGS))
            CcControl.SYSTEM -> { onClose(); onSystem() }
        }
    }

    BoxWithConstraints(modifier.testTag("control-center")) {
        val gap = 12.dp
        // iPad-sized cells: a compact grid instead of stretching across the whole screen.
        val rows = 5 + (maxOf(0, chosen.size - 4) + 3) / 4
        val byHeight = if (maxHeight == Dp.Infinity) Dp.Infinity else (maxHeight - 60.dp - gap * (rows - 1)) / rows
        val cell = minOf(size.cell + if (wide) 8.dp else 0.dp, (maxWidth - gap * 3) / 4, byHeight).coerceAtLeast(44.dp)
        fun span(n: Int): Dp = cell * n + gap * (n - 1)
        val gridWidth = span(4)
        ProvideJiggle(edit) {
        Column(Modifier.width(gridWidth).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(gap)) {
            // iOS 18 header: edit on the left, power on the right.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (edit.active) PanelPill("Done") { edit.stop() }
                else GlassIconButton(Icons.Rounded.Add, "Edit controls") { edit.start() }
                Spacer(Modifier.weight(1f))
                GlassIconButton(Icons.Rounded.PowerSettingsNew, "Power menu") {
                    onClose(); if (!SystemShadeAccessibilityService.global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)) onSystem()
                }
            }
            // Row 1–2: connectivity (2×2) and now playing (2×2)
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Module(Modifier.size(span(2))) {
                    Column(Modifier.fillMaxSize().padding(cell * .16f), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            RoundToggle(Icons.Rounded.AirplanemodeActive, "Airplane mode", status.airplane, AccentOrange, cell * .7f) {
                                open(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) }
                            RoundToggle(Icons.Rounded.SignalCellularAlt, "Mobile data", !status.airplane && (status.cellularLevel ?: 0) > 0, AccentGreen, cell * .7f) {
                                open(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            RoundToggle(Icons.Rounded.Wifi, "Wi-Fi", status.wifiConnected, AccentBlue, cell * .7f) { open(Intent(Settings.Panel.ACTION_WIFI)) }
                            RoundToggle(Icons.Rounded.Bluetooth, "Bluetooth", controls.bluetoothOn, AccentBlue, cell * .7f) {
                                open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                        }
                    }
                }
                MediaModule(media, Modifier.size(span(2)), cell, onOpen = { media?.let { onClose(); IslandListenerService.open(context, it) } })
            }
            // Row 3–4: the first four small controls (2×2) and brightness + volume sliders
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Column(Modifier.width(span(2)), verticalArrangement = Arrangement.spacedBy(gap)) {
                    chosen.take(4).chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            pair.forEach { c -> EditableControl(c, isOn(c), accent(c), cell, edit, onRemove = { onControls(controlNames - c.name) }) { run(c) } }
                        }
                    }
                }
                TallSlider(Icons.Rounded.LightMode, "Brightness", controls.brightness, cell, span(2),
                    onStart = { if (!android.provider.Settings.System.canWrite(context)) open(controls.writeSettingsIntent()) }) { value ->
                    controls.changeBrightness(value) }
                TallSlider(Icons.AutoMirrored.Rounded.VolumeUp, "Volume", controls.volume, cell, span(2)) { controls.changeVolume(it) }
            }
            // The rest, four per row
            chosen.drop(4).chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { c -> EditableControl(c, isOn(c), accent(c), cell, edit, onRemove = { onControls(controlNames - c.name) }) { run(c) } }
                }
            }
            // Edit mode: gallery of controls that aren't in Control Center yet
            if (edit.active) {
                val unused = CcControl.entries.filter { it !in chosen && available(it) }
                Text(if (unused.isEmpty()) "All controls added" else "Add a Control", color = FolioGlass.secondary, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
                unused.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { c ->
                            Column(Modifier.width(cell), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box {
                                    SquareToggle(c.icon(), "Add ${c.label}", false, Color.White, cell) { onControls(controlNames + c.name) }
                                    Box(Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(20.dp).clip(CircleShape).background(AccentGreen),
                                        contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                                }
                                Text(c.label, color = Color.White, fontSize = 10.sp, maxLines = 2, lineHeight = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

/** A 1×1 control that wiggles with a "–" while editing. */
@Composable
private fun EditableControl(control: CcControl, on: Boolean, accent: Color, cell: Dp, edit: HomeEditMode, onRemove: () -> Unit, onRun: () -> Unit) {
    Box(Modifier.jiggle(control.name, .6f)) {
        SquareToggle(control.icon(), control.label, on, accent, cell) { if (!edit.active) onRun() }
        if (edit.active) JiggleRemoveButton("Remove ${control.label}", onRemove)
    }
}

/** Now Playing (2×2): album art, title and transport controls. */
@Composable
private fun MediaModule(media: IslandActivity.Media?, modifier: Modifier, cell: Dp, onOpen: () -> Unit) {
    Module(modifier.clickable(enabled = media != null, onClick = onOpen)) {
        Column(Modifier.fillMaxSize().padding(cell * .16f), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(cell * .62f).clip(RoundedCornerShape(cell * .14f)).background(FolioGlass.raised), contentAlignment = Alignment.Center) {
                    val image = media?.art ?: media?.icon
                    if (image != null) Image(image.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    else Icon(Icons.Rounded.MusicNote, null, tint = FolioGlass.secondary, modifier = Modifier.size(cell * .3f))
                }
                Spacer(Modifier.weight(1f))
                if (media?.art != null) media.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(cell * .26f).clip(RoundedCornerShape(cell * .07f))) }
            }
            Column {
                Text(media?.title ?: "Not Playing", color = if (media != null) Color.White else FolioGlass.secondary,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                media?.subtitle?.let { Text(it, color = FolioGlass.secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val t = media?.controller?.transportControls
                val tint = Color.White.copy(alpha = if (t != null) 1f else .35f)
                Icon(Icons.Rounded.FastRewind, "Previous", tint = tint, modifier = Modifier.size(cell * .34f).clip(CircleShape).clickable(t != null) { t?.skipToPrevious() })
                Icon(if (media?.playing == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", tint = tint,
                    modifier = Modifier.size(cell * .42f).clip(CircleShape).clickable(t != null) { if (media?.playing == true) t?.pause() else t?.play() })
                Icon(Icons.Rounded.FastForward, "Next", tint = tint, modifier = Modifier.size(cell * .34f).clip(CircleShape).clickable(t != null) { t?.skipToNext() })
            }
        }
    }
}

@Composable
private fun Module(modifier: Modifier, content: @Composable BoxScope.() -> Unit) =
    Box(modifier.clip(RoundedCornerShape(26.dp)).background(ModuleGlass)
        .border(FolioGlass.edge, RoundedCornerShape(26.dp)), content = content)

@Composable
private fun RoundToggle(icon: ImageVector, label: String, on: Boolean, accent: Color, size: Dp, onClick: () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(Modifier.size(size).clip(CircleShape).background(if (on) accent else Color.White.copy(alpha = .16f))
        .clickable { haptic.toggle(!on); onClick() }.semantics { contentDescription = "$label, ${if (on) "on" else "off"}" },
        contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * .46f))
    }
}

@Composable
private fun SquareToggle(icon: ImageVector, label: String, on: Boolean, accent: Color, size: Dp, onClick: () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(Modifier.size(size).clip(RoundedCornerShape(size * .3f)).background(if (on) accent else ModuleGlass)
        .clickable(role = androidx.compose.ui.semantics.Role.Button) { haptic.toggle(!on); onClick() }
        .semantics { contentDescription = label; stateDescription = if (on) "On" else "Off" }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (on && accent == Color.White) Color.Black else Color.White, modifier = Modifier.size(size * .4f))
    }
}

@Composable
private fun TallSlider(icon: ImageVector, label: String, value: Float, width: Dp, height: Dp,
    onStart: () -> Unit = {}, onValue: (Float) -> Unit) {
    val shown by androidx.compose.animation.core.animateFloatAsState(value.coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessMedium), label = label)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(Modifier.size(width, height).clip(RoundedCornerShape(width * .36f)).background(ModuleGlass)
        .pointerInput(Unit) {
            var last = -1f
            detectVerticalDragGestures(onDragStart = { onStart(); last = -1f }) { change, _ ->
                change.consume()
                val v = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                // Like iOS: a tick when the slider hits its top or bottom.
                if ((v == 0f || v == 1f) && v != last) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentFrequentTick)
                last = v
                onValue(v)
            }
        }
        .semantics {
            contentDescription = label
            progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(value, 0f..1f)
            setProgress { target -> onStart(); onValue(target.coerceIn(0f, 1f)); true } // TalkBack can adjust it
        }) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(shown).background(Color.White))
        Icon(icon, null, tint = if (shown > .18f) Color(0xFF3A3A3C) else Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = width * .22f).size(width * .36f))
    }
}

@Composable
private fun GlassIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.minimumInteractiveComponentSize().clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center) { Box(Modifier.size(34.dp).clip(CircleShape).background(NotifGlass), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
    } }
}

@Composable
private fun PanelPill(label: String, onClick: () -> Unit) {
    Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50)).background(NotifGlass).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp))
}

@Composable
private fun EmptyNote(text: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(NotifGlass).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text, color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
        if (action != null) PanelPill(action, onAction)
    }
}

private val ModuleGlass = FolioGlass.module
private val NotifGlass = FolioGlass.card
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

    val hasTorch get() = torchId != null
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
    private var manualModeSet = false
    fun changeBrightness(value: Float): Boolean {
        if (!Settings.System.canWrite(context)) return false
        val level = (value * 255).toInt().coerceIn(1, 255)
        if (level == (brightness * 255).toInt()) return true // skip identical writes while dragging
        brightness = value
        runCatching {
            if (!manualModeSet) {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                manualModeSet = true
            }
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, level)
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


private fun androidx.compose.ui.hapticfeedback.HapticFeedback.toggle(on: Boolean) = performHapticFeedback(
    if (on) androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOn else androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOff)
