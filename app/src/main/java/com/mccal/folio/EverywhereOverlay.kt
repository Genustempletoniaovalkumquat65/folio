package com.mccal.folio

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Process
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Set by MainActivity: while Folio's Home is showing, the everywhere overlays step aside. */
internal object FolioForeground { val visible = MutableStateFlow(false) }

/** Lifecycle for ComposeViews that live in accessibility overlay windows. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    fun start() { saved.performRestore(null); registry.currentState = Lifecycle.State.RESUMED }
    fun stop() { registry.currentState = Lifecycle.State.DESTROYED }
}

/**
 * Dock handle and Dynamic Island over every app, drawn from the shade-gesture accessibility service.
 * Windows are sized to their visible parts so the rest of the screen keeps working normally.
 */
internal class EverywhereOverlay(private val service: AccessibilityService) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val prefs = service.getSharedPreferences("launcher", 0)
    private val owner = OverlayOwner()
    private val density get() = service.resources.displayMetrics.density

    private var handle: View? = null
    private var dock: ComposeView? = null
    private var island: ComposeView? = null
    private val dockOpen = MutableStateFlow(false)

    private data class Settings(val dockEverywhere: Boolean, val islandEverywhere: Boolean, val leftHanded: Boolean, val dock: List<String>)
    private val settings = MutableStateFlow(readSettings())
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "state") { settings.value = readSettings(); sync() } }
    private var foregroundJob: kotlinx.coroutines.Job? = null
    private val scopeJob = kotlinx.coroutines.SupervisorJob()

    fun start() {
        owner.start()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        foregroundJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate + scopeJob)
            .launch { FolioForeground.visible.collect { sync() } }
        sync()
    }

    fun stop() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        scopeJob.cancel()
        removeHandle(); removeDock(); removeIsland()
        owner.stop()
    }

    fun onConfigurationChanged() { removeHandle(); removeIsland(); sync() }

    private fun readSettings(): Settings = runCatching {
        val j = JSONObject(prefs.getString("state", "{}") ?: "{}")
        val dockIds = j.optJSONArray("dock")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() && s != "null" } } }.orEmpty()
        Settings(j.optBoolean("dockEverywhere", false), j.optBoolean("islandEverywhere", false), j.optBoolean("leftHanded", false), dockIds)
    }.getOrDefault(Settings(false, false, false, emptyList()))

    private fun sync() {
        val s = settings.value
        val home = FolioForeground.visible.value
        if (s.dockEverywhere && !home) addHandle(s.leftHanded) else { removeHandle(); removeDock() }
        if (s.islandEverywhere && !home) addIsland() else removeIsland()
    }

    // ---- Dock handle -------------------------------------------------------------------------

    private fun addHandle(leftHanded: Boolean) {
        if (handle != null) return
        val view = object : View(service) {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0x8CFFFFFF.toInt() }
            private var downX = 0f
            override fun onDraw(canvas: android.graphics.Canvas) {
                val w = 5 * density; val h = 64 * density
                val x = if (leftHanded) 4 * density else width - 4 * density - w
                canvas.drawRoundRect(x, (height - h) / 2, x + w, (height + h) / 2, w / 2, w / 2, paint)
            }
            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> downX = event.rawX
                    MotionEvent.ACTION_MOVE -> if (kotlin.math.abs(event.rawX - downX) > 18 * density) { openDock(); return true }
                    MotionEvent.ACTION_UP -> { performClick(); openDock() }
                }
                return true
            }
            override fun performClick(): Boolean { super.performClick(); return true }
        }
        val params = WindowManager.LayoutParams((22 * density).toInt(), (120 * density).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity = (if (leftHanded) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
            y = (-40 * density).toInt()
        }
        runCatching { wm.addView(view, params); handle = view }
    }

    private fun removeHandle() { handle?.let { runCatching { wm.removeView(it) } }; handle = null }

    private fun openDock() {
        if (dock != null) return
        val s = settings.value
        val apps = s.dock.mapNotNull { id -> resolveApp(id) }
        val view = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner); setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val open by dockOpen.collectAsState()
                LaunchedEffect(Unit) { dockOpen.value = true }
                Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { closeDock() },
                    contentAlignment = if (s.leftHanded) Alignment.CenterStart else Alignment.CenterEnd) {
                    AnimatedVisibility(open, enter = fadeIn() + slideInHorizontally(spring(dampingRatio = .8f, stiffness = Spring.StiffnessMediumLow)) { if (s.leftHanded) -it else it },
                        exit = fadeOut() + slideOutHorizontally { if (s.leftHanded) -it else it }) {
                        Column(Modifier.padding(horizontal = 12.dp).width(72.dp).clip(RoundedCornerShape(30.dp))
                            .background(Color(0xFF1C1C1E).copy(alpha = .72f)).border(1.dp, Color.White.copy(alpha = .16f), RoundedCornerShape(30.dp))
                            .padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            apps.forEach { (component, icon) ->
                                Image(icon.asImageBitmap(), null, Modifier.size(50.dp).clip(RoundedCornerShape(13.dp)).clickable {
                                    closeDock(); launch(component)
                                })
                            }
                            if (apps.isNotEmpty()) HorizontalDivider(Modifier.width(40.dp), color = Color.White.copy(alpha = .2f))
                            Box(Modifier.size(50.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = .16f)).clickable {
                                closeDock(); service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                            }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Home, "Home", tint = Color.White) }
                        }
                    }
                }
            }
        }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT).apply { dimAmount = .18f }
        runCatching { wm.addView(view, params); dock = view }
    }

    private fun closeDock() {
        dockOpen.value = false
        dock?.postDelayed({ removeDock() }, 220)
    }

    private fun removeDock() { dock?.let { runCatching { wm.removeView(it) } }; dock = null; dockOpen.value = false }

    private fun resolveApp(id: String): Pair<ComponentName, Bitmap>? {
        val component = ComponentName.unflattenFromString(parseProfileAppId(id)?.component ?: id) ?: return null
        val icon = runCatching { service.packageManager.getActivityIcon(component).toBitmap(144, 144) }.getOrNull() ?: return null
        return component to icon
    }

    private fun launch(component: ComponentName) {
        runCatching {
            service.getSystemService(android.content.pm.LauncherApps::class.java)
                .startMainActivity(component, Process.myUserHandle(), null, null)
        }
    }

    // ---- Island ------------------------------------------------------------------------------

    private fun addIsland() {
        if (island != null) return
        val metrics = wm.currentWindowMetrics
        val bounds = metrics.bounds
        val cutout: Rect? = metrics.windowInsets.displayCutout?.boundingRects?.minByOrNull { it.top }
        val d = density
        val camW = (cutout?.width() ?: 0) / d
        val camH = (cutout?.height() ?: 0) / d
        val centerX = cutout?.exactCenterX() ?: (bounds.width() / 2f)
        val centerYdp = (cutout?.exactCenterY() ?: (18 * d)) / d
        val pillHdp = maxOf(camH + 8f, 30f).coerceAtMost(maxOf(camH + 4f, (centerYdp - 2f) * 2))
        val roomDp = minOf(centerX, bounds.width() - centerX) / d - 8f
        val maxWdp = minOf(camW + 190f, roomDp * 2)
        val windowW = (maxWdp * d).toInt()
        val view = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner); setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val activity by IslandListenerService.activity.collectAsState()
                val eventPair by IslandEvents.latest.collectAsState()
                var event by remember { mutableStateOf<IslandEvent?>(null) }
                LaunchedEffect(eventPair) {
                    val (e, at) = eventPair ?: return@LaunchedEffect
                    val left = IslandEvents.SHOW_MS - (System.currentTimeMillis() - at)
                    if (left > 0) { event = e; kotlinx.coroutines.delay(left); event = null }
                }
                val content: IslandContent? = event?.let { IslandContent.Event(it) } ?: activity?.let { IslandContent.Live(it) }
                val want = content?.let { minOf(islandWantWidth(it, camW.dp), maxWdp.dp) } ?: 0.dp
                val w by animateDpAsState(want, spring(dampingRatio = .72f, stiffness = Spring.StiffnessMediumLow), label = "overlay-island")
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (content != null && w > 1.dp) Box(Modifier.size(w, pillHdp.dp).clip(RoundedCornerShape((pillHdp / 2).dp)).background(Color.Black)
                        .clickable { (content as? IslandContent.Live)?.let { IslandListenerService.open(service, it.activity) } }) {
                        IslandPillContent(content, camW.dp, pillHdp.dp)
                    }
                }
            }
        }
        val params = WindowManager.LayoutParams(windowW, (pillHdp * d).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (centerX - windowW / 2f).toInt()
            y = ((centerYdp - pillHdp / 2) * d).toInt()
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        runCatching { wm.addView(view, params); island = view }
    }

    private fun removeIsland() { island?.let { runCatching { wm.removeView(it) } }; island = null }
}
