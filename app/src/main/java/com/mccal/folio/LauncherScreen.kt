@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.mccal.folio

import androidx.compose.ui.res.stringResource
import android.appwidget.AppWidgetProviderInfo
import android.os.UserManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal val Ink: Color
    @Composable get() = LocalDuoPalette.current.ink
internal val Glass: Color
    @Composable get() = LocalDuoPalette.current.glass

private fun findFreeWidgetIndex(layout: HomeLayout, page: Int, spanX: Int, spanY: Int): Int? {
    val blocked = layout.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }
    for (row in 0..GRID_ROWS - spanY) for (column in 0..GRID_COLUMNS - spanX) {
        val cells = buildList {
            repeat(spanY) { y -> repeat(spanX) { x -> add(homeCellIndex(page, (row + y) * GRID_COLUMNS + column + x)) } }
        }
        if (cells.none { it in blocked || layout.slotAt(it) != null }) return cells.first()
    }
    return null
}

@Composable
fun DuoTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    val palette = if (dark) DarkDuoPalette else LightDuoPalette
    CompositionLocalProvider(LocalDuoPalette provides palette) {
        MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF9BC5D7), onPrimary = Color(0xFF12303D),
            surface = Color(0xFF17272E), onSurface = palette.ink, secondary = Color(0xFFD1BE98),
            secondaryContainer = Color(0xFF314852), onSecondaryContainer = palette.ink)
        else lightColorScheme(primary = Color(0xFF30596D), onPrimary = Color.White,
            surface = Color(0xFFF4F7F8), onSurface = palette.ink, secondary = Color(0xFF84775F),
            secondaryContainer = Color(0xFFDCE8ED), onSecondaryContainer = palette.ink), content = content)
    }
}


@Composable
fun LauncherScreen(
    state: LauncherState, model: LauncherModel, widgets: WidgetController, homeRequests: Int,
    onLaunch: (AppEntry) -> Unit, onMakeDefault: () -> Unit, onAppInfo: (AppEntry) -> Unit,
    isDefaultHome: Boolean, deviceStatus: DeviceStatus, onStatusMode: (Boolean) -> Unit, onWallpaperPreview: () -> Unit,
    onDiscover: () -> Unit = {}, searchRequests: Int = 0, settingsRequests: Int = 0,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit = { app, _ -> onLaunch(app) },
    onGoogleSearch: (android.graphics.Rect?) -> Boolean = { false },
    appearance: AppearanceState = AppearanceState(),
    onAppearanceMode: (AppearanceMode) -> Unit = {},
    onAppearanceManual: (String, Double, Double) -> Unit = { _, _, _ -> },
    onAppearanceDeviceLocation: () -> Unit = {},
    onAppearanceClear: () -> Unit = {},
    showFirstRun: Boolean = false,
    onFinishFirstRun: () -> Unit = {},
    onShadeSetup: () -> Unit = {},
    onShowWelcome: () -> Unit = {},
) {
    var sheet by rememberSaveable { mutableStateOf("") }
    var dockSlot by rememberSaveable { mutableIntStateOf(0) }
    var widgetSlot by rememberSaveable { mutableIntStateOf(0) }
    var widgetTargetIndex by rememberSaveable { mutableIntStateOf(Int.MIN_VALUE) }
    var widgetExactTarget by rememberSaveable { mutableStateOf(false) }
    /** Set while the widget picker is adding to the Smart Stack at this placement slot. */
    var stackTargetSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    /** Set while the widget picker is adding to the Today View. */
    var todayAdd by rememberSaveable { mutableStateOf(false) }
    var widgetPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var widgetProfileSerial by rememberSaveable { mutableStateOf<Long?>(null) }
    var widgetSession by remember { mutableStateOf<WidgetPickerSession?>(null) }
    var widgetPlacementMessage by remember { mutableStateOf<String?>(null) }
    var emptyCellIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var resizeSlot by remember { mutableStateOf<Int?>(null) }
    var resizeWidth by rememberSaveable { mutableIntStateOf(1) }
    var resizeHeight by rememberSaveable { mutableIntStateOf(1) }
    var resizeConstraints by remember { mutableStateOf<WidgetSpanConstraints?>(null) }
    var resizePitchX by remember { mutableFloatStateOf(1f) }
    var resizePitchY by remember { mutableFloatStateOf(1f) }
    var resizeTopPitch by remember { mutableFloatStateOf(1f) }
    var resizeAppPitch by remember { mutableFloatStateOf(1f) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var panelAppId by rememberSaveable { mutableStateOf<String?>(null) }
    var customizationPage by rememberSaveable { mutableStateOf(CustomizationPage.OVERVIEW) }
    LaunchedEffect(sheet) {
        if (sheet.isEmpty()) customizationPage = CustomizationPage.OVERVIEW
        if (sheet != "widgets") { stackTargetSlot = null; todayAdd = false }
    }
    var openFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var createFolderFirstId by rememberSaveable { mutableStateOf<String?>(null) }
    var savedPage by rememberSaveable { mutableIntStateOf(0) }
    var lastHomePage by rememberSaveable { mutableIntStateOf(0) }
    var libraryQuery by rememberSaveable { mutableStateOf("") }
    var pinQuery by rememberSaveable { mutableStateOf("") }
    val launcherActivity = androidx.activity.compose.LocalActivity.current as MainActivity
    val launcherRootView = LocalView.current.rootView
    DisposableEffect(sheet == "widgets") {
        val active = sheet == "widgets"
        if (active) LiveDiscover.setExternalResultPending(launcherActivity, "main", "widget-picker", true)
        onDispose { if (active) LiveDiscover.setExternalResultPending(launcherActivity, "main", "widget-picker", false) }
    }
    val appsById = remember(state.apps) { state.apps.associateBy { it.id } }
    val drag = remember { HomeDragState() }
    val folderOwnsInput = openFolderId != null || drag.source?.folderId != null
    DisposableEffect(folderOwnsInput) {
        if (folderOwnsInput) LiveDiscover.setExternalResultPending(launcherActivity, "main", "folder-panel", true)
        onDispose { if (folderOwnsInput) LiveDiscover.setExternalResultPending(launcherActivity, "main", "folder-panel", false) }
    }
    val haptic = LocalHapticFeedback.current
    val homeEdit = remember { HomeEditMode() }
    homeEdit.onRemove = { target -> if (target is DropTarget.Widget) widgets.remove(target.index) else model.removePlacement(target) }
    // Long-press on empty Home starts jiggle mode (iPhone); a second long-press opens the Home options.
    val onEmptyLongPress: (Int) -> Unit = { index ->
        if (homeEdit.active) emptyCellIndex = index
        else { homeEdit.lastEmptyIndex = index; haptic.performHapticFeedback(HapticFeedbackType.LongPress); homeEdit.start() }
    }
    val homePages = state.homePages
    val pendingNewPage = widgets.pendingPlacement?.page == homePages
    val visibleHomePages = homePages + if (drag.active || widgetSession != null || pendingNewPage) 1 else 0
    var expandedWorkspace by remember { mutableStateOf(false) }
    // The page left of Home is Folio's Today View, or Google Discover when chosen and available.
    val todayMode = state.leftPage == "TODAY"
    val currentTodayMode by rememberUpdatedState(todayMode)
    val firstHome = if (todayMode || DiscoverBounds.available) 1 else 0
    DisposableEffect(todayMode) {
        // Today mode never starts Google's hidden feed window (and closes one that's running).
        if (todayMode) LiveDiscover.setExternalResultPending(launcherActivity, "main", "today-view", true)
        onDispose { if (todayMode) LiveDiscover.setExternalResultPending(launcherActivity, "main", "today-view", false) }
    }
    val pageCount = visibleHomePages + 1
    val nativePager = rememberPagerState(initialPage = savedPage.coerceIn(-firstHome, pageCount - 1) + firstHome, pageCount = { pageCount + firstHome })
    val pager = remember(nativePager) { LauncherPager(nativePager, firstHome) }
    fun leaveTemporaryWidgetPage() {
        val persistedPages = model.state.value.homePages
        if (pager.currentPage >= persistedPages)
            pager.requestScrollToPage((persistedPages - 1).coerceAtLeast(0))
    }
    var priorPendingPlacement by remember { mutableStateOf<WidgetPlacement?>(null) }
    LaunchedEffect(widgets.pendingPlacement, state.layout) {
        val pending = widgets.pendingPlacement
        if (pending != null) priorPendingPlacement = pending
        else priorPendingPlacement?.let { prior ->
            if (model.placement(prior.slot) == null && prior.page >= homePages) leaveTemporaryWidgetPage()
            priorPendingPlacement = null
        }
    }
    val pageGestures = remember(nativePager) { PageGestureLimits(nativePager) }
    SideEffect { pageGestures.editing = drag.active || widgetSession != null || resizeSlot != null; LiveDiscover.allowNativeOpen = pager.currentPage == 0 && !drag.active && widgetSession == null && resizeSlot == null }
    val pageFling = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(nativePager, pagerSnapDistance = pageGestures)
    var nativeMotion by remember { mutableStateOf(false) }
    DisposableEffect(nativePager) {
        val callback: (Float) -> Unit = { progress ->
            val scrolling = nativePager.isScrollInProgress
            if (DuoMotionTrace.enabled) DuoMotionTrace.event("native_callback_received",
                "progress=$progress scrolling=$scrolling nativeMotion=$nativeMotion current=${nativePager.currentPage} offset=${nativePager.currentPageOffsetFraction}")
            if (!scrolling || nativeMotion) {
                val priorNativeMotion = nativeMotion
                nativeMotion = progress > 0f && progress < 1f
                val position = 1f - progress
                val page = position.roundToInt()
                if (DuoMotionTrace.enabled) DuoMotionTrace.event("native_callback_accepted",
                    "progress=$progress nativeMotion=$priorNativeMotion->$nativeMotion requestPage=$page requestOffset=${position - page}")
                nativePager.requestScrollToPage(page, position - page)
            } else if (DuoMotionTrace.enabled) DuoMotionTrace.event("native_callback_rejected",
                "progress=$progress reason=compose_scrolling nativeMotion=$nativeMotion")
        }
        LiveDiscover.onNativeProgress = callback
        onDispose { if (LiveDiscover.onNativeProgress === callback) LiveDiscover.onNativeProgress = null }
    }
    LaunchedEffect(nativePager) {
        snapshotFlow { Triple((1f - nativePager.currentPage - nativePager.currentPageOffsetFraction).coerceIn(0f, 1f), nativePager.isScrollInProgress, nativeMotion) to (nativePager.targetPage < firstHome) }
            .collect { (motion, towardFeed) ->
                val (progress, scrolling, native) = motion
                if (firstHome > 0 && !currentTodayMode) {
                    if (DuoMotionTrace.enabled) DuoMotionTrace.event("pager_observer",
                        "progress=$progress scrolling=$scrolling nativeMotion=$native towardFeed=$towardFeed")
                    if (scrolling) {
                        if (nativeMotion && DuoMotionTrace.enabled) DuoMotionTrace.event("native_owner_cleared",
                            "reason=compose_scrolling progress=$progress")
                        nativeMotion = false
                        LiveDiscover.page(progress, true, towardFeed)
                    } else if (!native) LiveDiscover.page(progress, false)
                }
            }
    }
    val scope = rememberCoroutineScope()
    DisposableEffect(pager) {
        val callback = { scope.launch { pager.animateScrollToPage(0) }; Unit }
        LiveDiscover.onHomeRequest = callback
        onDispose { if (LiveDiscover.onHomeRequest === callback) LiveDiscover.onHomeRequest = null }
    }
    var previousHomePages by remember { mutableIntStateOf(homePages) }
    var previousEditRevision by remember { mutableIntStateOf(state.editRevision) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(pager, homePages) {
        snapshotFlow { pager.settledPage to drag.active }.distinctUntilChanged().collect { (page, moving) ->
            if (!moving) { savedPage = page; if (page in 0 until homePages) lastHomePage = page }
        }
    }
    LaunchedEffect(homePages, state.editRevision) {
        if (homePages != previousHomePages && !drag.active) {
            // Pin edits in the library keep the library selected; a completed drop stays on home.
            if (state.editRevision == previousEditRevision) {
                if (pager.currentPage == previousHomePages) pager.scrollToPage(homePages)
                else if (pager.currentPage >= pageCount) pager.scrollToPage(homePages - 1)
            } else if (pager.currentPage >= homePages) pager.scrollToPage(homePages - 1)
        }
        previousHomePages = homePages
        previousEditRevision = state.editRevision
    }
    LaunchedEffect(pager.settledPage) { if (pager.settledPage != homePages) focus.clearFocus() }
    LaunchedEffect(pager.settledPage, visibleHomePages) { if (pager.settledPage !in 0 until visibleHomePages) homeEdit.stop() }
    LaunchedEffect(state.verticalStatus) { onStatusMode(state.verticalStatus) }
    LaunchedEffect(homeRequests) { if (homeRequests > 0) {
        // An app can pause Home after the destination is visible but before its settle completes.
        val page = pager.currentPage.takeIf { it in 0 until homePages }
            ?: lastHomePage.coerceIn(0, homePages - 1)
        drag.clear(); widgetSession = null; resizeSlot = null; sheet = ""; widgetPackage = null
        widgetExactTarget = false; widgetPlacementMessage = null; selectedId = null
        openFolderId = null; createFolderFirstId = null; emptyCellIndex = null; homeEdit.stop()
        focus.clearFocus(); keyboard?.hide()
        pager.animateScrollToPage(page)
    } }
    LaunchedEffect(settingsRequests) { if (settingsRequests > 0) {
        drag.clear(); widgetSession = null; resizeSlot = null; selectedId = null; homeEdit.stop(); sheet = "settings"
    } }
    LaunchedEffect(searchRequests) { if (searchRequests > 0) { drag.clear(); widgetSession = null; resizeSlot = null; sheet = ""; widgetPackage = null; widgetExactTarget = false; selectedId = null
        if (!state.googleSearch || !onGoogleSearch(null)) pager.animateScrollToPage(homePages)
    } }
    val widgetPickerBack = {
        if (widgetSession != null) {
            leaveTemporaryWidgetPage(); widgetSession = null; widgetPlacementMessage = null
        } else {
            sheet = ""; widgetPackage = null; widgetExactTarget = false; widgetPlacementMessage = null
        }
    }
    BackHandler(enabled = sheet == "widgets") { widgetPickerBack() }
    BackHandler(enabled = sheet.isEmpty()) { if (resizeSlot != null) resizeSlot = null else if (drag.active) {
        val destination = if (drag.source?.target is DropTarget.Library) homePages else drag.originPage.coerceAtMost(homePages - 1)
        drag.clear(); scope.launch { pager.scrollToPage(destination) }
    } else if (selectedId != null) selectedId = null else if (homeEdit.active) homeEdit.stop() else { focus.clearFocus(); scope.launch { pager.animateScrollToPage(0) } } }
    val openDiscover = { if (firstHome > 0) scope.launch { pager.animateScrollToPage(-1) } else onDiscover(); Unit }
    val openLibrary = { scope.launch { pager.animateScrollToPage(homePages) }; Unit }
    val todayContent: @Composable (Modifier) -> Unit = { pageModifier ->
        TodayView(state, widgets, pageModifier,
            onSearch = { launcherActivity.openSpotlight() }, onLaunch = onLaunch,
            onAddWidget = { todayAdd = true; widgetPackage = null; widgetProfileSerial = null; widgetExactTarget = false; sheet = "widgets" },
            onRemove = model::removeTodayWidget, onMove = model::moveTodayWidget)
    }
    val leftPageContent: @Composable (Modifier) -> Unit = { pageModifier ->
        when {
            !todayMode -> DiscoverContent(pageModifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp))
            expandedWorkspace && state.todayUnfolded != "PAGE" -> Box(pageModifier)
            else -> todayContent(pageModifier)
        }
    }

    val dragWindowPage = if (expandedWorkspace && (drag.active || widgetSession != null)) pager.settledPage else pager.currentPage
    val eligibleDragPages = remember(expandedWorkspace, dragWindowPage, visibleHomePages) {
        if (expandedWorkspace && dragWindowPage in 0 until visibleHomePages) {
            setOfNotNull((dragWindowPage - 1).takeIf { it >= -1 }, dragWindowPage)
        } else setOf(dragWindowPage)
    }
    val rawTarget = if (drag.active) drag.destination(drag.pointer, eligibleDragPages)?.target else null
    val target = if (rawTarget is DropTarget.Home && drag.source?.target is DropTarget.Widget) {
        val slot = (drag.source!!.target as DropTarget.Widget).index
        model.placement(slot)?.let {
            DropTarget.Home(adjustedWidgetDropIndex(rawTarget.index, it, drag.source!!.bounds, drag.origin))
        } ?: rawTarget
    } else rawTarget
    val blockedDock = drag.moved && target is DropTarget.Dock &&
        if (drag.source?.folderId != null) state.dock.none { it == null }
        else drag.source?.appId?.let { !canPlaceInDock(state.layout, it) } == true
    val insertionTarget = target.takeIf { drag.moved && !blockedDock }
    LaunchedEffect(drag.active, drag.moved) {
        val source = drag.source
        val id = source?.appId
        if (drag.active && drag.moved && id != null && selectedId == id) { selectedId = null; homeEdit.start() }
        // Dragging out of the App Library heads to Home only once the app actually moves (holding just shows the menu).
        if (drag.active && drag.moved && source?.target is DropTarget.Library) {
            withFrameNanos { }
            pager.scrollToPage(lastHomePage.coerceIn(0, homePages - 1))
        }
    }
    // A light tick each time the dragged item snaps to a new spot.
    LaunchedEffect(insertionTarget) { if (insertionTarget != null && drag.moved) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    val widgetRawTarget = widgetSession?.let { session -> drag.regions.values.firstOrNull {
        it.target is DropTarget.Home && it.page in eligibleDragPages && it.bounds.contains(session.pointer)
    }?.target as? DropTarget.Home }
    val widgetDraft = widgetSession?.let { session -> session.candidate ?: widgetRawTarget?.let { cell ->
        widgetCandidate(state.layout, session.slot, session.targetIndex ?: cell.index, session.span.width, session.span.height)
    } ?: session.targetIndex?.let { widgetCandidate(state.layout, session.slot, it, session.span.width, session.span.height) } }
    val dropHomePage = if (pager.currentPage >= visibleHomePages)
        lastHomePage.coerceIn(0, homePages - 1) else pager.currentPage.coerceIn(0, homePages)
    val previewLayout = remember(state.layout, drag.source, insertionTarget, drag.moved) {
        val id = drag.source?.appId
        when {
            id != null && insertionTarget is DropTarget.Home -> dropApp(state.layout, id, insertionTarget)
            id != null && insertionTarget is DropTarget.Dock -> dropApp(state.layout, id, insertionTarget)
            drag.source?.target is DropTarget.Widget && insertionTarget is DropTarget.Home ->
                moveWidget(state.layout, (drag.source!!.target as DropTarget.Widget).index, insertionTarget.index)
            else -> state.layout
        }
    }
    val edgeWidth = with(LocalDensity.current) { 30.dp.toPx() }
    val edgePointer = widgetSession?.takeIf { it.dragging }?.pointer ?: drag.pointer
    val edgeActive = (drag.active && drag.moved) || widgetSession?.dragging == true
    val edge = if (!edgeActive) 0 else dragEdgeDirection(edgePointer, drag.rootBounds, edgeWidth)
    LaunchedEffect(edgeActive, edge) {
        if (edge != 0) while (drag.active || widgetSession?.dragging == true) {
            delay(650)
            val next = (pager.currentPage + edge).coerceIn(0, homePages)
            if ((!drag.active && widgetSession?.dragging != true) || next == pager.currentPage) break
            // Do not key this effect on currentPage: it changes halfway through the
            // animation and would cancel the turn before the inner grid is visible.
            // Once the hold commits a turn, finish its animation while the finger moves
            // into the incoming page. Leaving the edge cancels only the next hold timer.
            scope.launch { pager.animateScrollToPage(next) }.join()
        }
    }
    fun finishDrag(cancelled: Boolean) {
        val source = drag.source ?: return
        val moved = drag.moved
        val rawDestination = if (moved && !cancelled) drag.destination(drag.pointer, eligibleDragPages)?.target else null
        val destination = if (rawDestination is DropTarget.Home && source.target is DropTarget.Widget) {
            model.placement(source.target.index)?.let {
                DropTarget.Home(adjustedWidgetDropIndex(rawDestination.index, it, source.bounds, drag.origin))
            }
                ?: rawDestination
        } else rawDestination
        val changed = when {
            source.folderId != null && destination is DropTarget.Folder ->
                model.addAppToFolder(destination.id, source.appId ?: "")
            source.folderId != null && destination != null && source.appId != null ->
                model.removeAppFromFolder(source.folderId, source.appId, destination)
            destination == DropTarget.Remove -> model.removePlacement(source.target)
            destination is DropTarget.Home && source.target is DropTarget.Widget -> model.moveWidgetTo(source.target.index, destination.index)
            destination != null && source.appId != null -> model.applyDrop(source.appId, destination)
            else -> false
        }
        if (moved && !cancelled && changed) haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
        // Like iPhone, dragging something on Home leaves Home in jiggle mode.
        if (moved && !cancelled && source.target !is DropTarget.Library && source.folderId == null) homeEdit.start()
        val returnToLibrary = source.target is DropTarget.Library && source.folderId == null && !changed
        val destinationHomePage = (destination as? DropTarget.Home)?.index?.let(::homeCellPage)
        val currentWindow = pager.settledPage.coerceIn(0, visibleHomePages - 1)
        val page = when (destination) {
            is DropTarget.Home -> if (expandedWorkspace && homeCellPage(destination.index) in eligibleDragPages) currentWindow else destinationHomePage!!
            is DropTarget.Dock -> dropHomePage
            is DropTarget.Widget -> 0
            else -> if (source.target is DropTarget.Library) pager.currentPage else drag.originPage
        }
        scope.launch {
            // Let a new home page compose before removing the temporary drop page.
            withFrameNanos { }
            drag.clear()
            withFrameNanos { }
            pager.scrollToPage(if (returnToLibrary) model.state.value.homePages else page.coerceIn(0, model.state.value.homePages - 1))
            if (!moved && !cancelled) {
                // A held dock app already shows its menu; only an empty slot opens the app chooser.
                if (source.target is DropTarget.Dock) { if (source.appId == null) { dockSlot = source.target.index; sheet = "dock" } else selectedId = source.appId }
                else if (source.target is DropTarget.Widget) { widgetSlot = source.target.index; sheet = "widgetActions" }
                else if (source.appId?.let(::isFolderId) == true) openFolderId = source.appId
                else if (source.folderId == null) selectedId = source.appId
            }
        }
    }

    val homeLayer = rememberGraphicsLayer()
    DisposableEffect(homeLayer) {
        homeLayer.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
        LiveDiscover.homeLayer = homeLayer
        onDispose { if (LiveDiscover.homeLayer === homeLayer) LiveDiscover.homeLayer = null }
    }
    Box(Modifier.fillMaxSize().graphicsLayer {
        // The feed frame reuses the pager's render nodes in another window. Give Main
        // a complete render target so cross-window damage cannot erase stationary controls.
        // Only Google Discover's hosted feed needs it; with Today View an extra offscreen pass just costs frames.
        compositingStrategy = if (todayMode) androidx.compose.ui.graphics.CompositingStrategy.Auto
            else androidx.compose.ui.graphics.CompositingStrategy.Offscreen
    }.onSizeChanged { LiveDiscover.fullSize = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }.testTag("launcher-root").homeDragInput(drag,
        enabled = sheet.isEmpty() && !showFirstRun && selectedId == null && resizeSlot == null && pager.currentPage >= 0,
        page = pager.currentPage, eligiblePages = eligibleDragPages, onStart = {
            focus.clearFocus(); keyboard?.hide(); haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            // iPhone: holding an app shows its menu right away (no Android-style pick-up). Moving while still
            // holding dismisses the menu, picks the app up and starts jiggle mode (see the effect below).
            drag.source?.let { src ->
                if (!homeEdit.active && src.appId != null && !isFolderId(src.appId) && src.folderId == null && src.target !is DropTarget.Widget)
                    selectedId = src.appId
            }
            if (drag.source?.folderId != null) openFolderId = null
        },
        onFinish = { cancelled -> finishDrag(cancelled) }, immediate = homeEdit.active)
        .twoFingerSwipeDown(FolioAction.entries.firstOrNull { it.name == state.triggerActions[FolioTrigger.TWO_FINGER_DOWN.name] }
            ?.takeIf { it != FolioAction.NONE && sheet.isEmpty() && !homeEdit.active }) { FolioActions.run(launcherActivity, it) }) { ProvideJiggle(homeEdit) {
        val panelWide = androidx.compose.ui.platform.LocalConfiguration.current.let { isRegularSize(it.screenWidthDp.toFloat(), it.screenHeightDp.toFloat()) }
        val tone = LocalWallpaperTone.current
        val homeInk = homeInkFor(state.homeInk, tone.prefersDarkText)
        val basePalette = LocalDuoPalette.current
        val palette = if (state.tintedGlass) remember(basePalette, tone.primary) { basePalette.copy(glass = tintedGlass(basePalette.glass, tone.primary)) } else basePalette
        CompositionLocalProvider(LocalWidgetStacks provides state.widgetStacks, LocalStackRotate provides state.stackRotate,
            LocalHomeInk provides homeInk, LocalDuoPalette provides palette,
            // Remembered so every icon isn't recomposed each time Home recomposes (a new lambda changes the local).
            LocalAppPanel provides remember(state.appPanels, state.featureScopes, homeEdit.active, haptic, panelWide) {
                val panelsOn = FeatureScopes.on(state.featureScopes, "appPanels", state.appPanels, screenFor(panelWide))
                if (panelsOn && !homeEdit.active) { app: AppEntry -> haptic.performHapticFeedback(HapticFeedbackType.ContextClick); panelAppId = app.id } else null
            }) {
        if (!state.systemWallpaper) DuneWallpaper()
        else if (state.wallpaperMotion) SystemWallpaperParallax(nativePager)
        // iOS "dark appearance dims wallpaper".
        val dim by androidx.compose.animation.core.animateFloatAsState(if (state.dimWallpaperDark && appearance.dark) .3f else 0f, label = "wallpaper dim")
        if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        // Home never moves for the keyboard: including IME insets here re-measured the whole grid on every
        // frame of the keyboard animation (Spotlight/search jank). Sheets that need it use imePadding themselves.
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets.ime).union(rememberHiddenCameraInsets()))) {
            val wide = maxWidth.value >= 650f && maxHeight.value >= REGULAR_MIN_HEIGHT_DP
            val preset = if (wide) state.expanded else state.compact
            val density = LocalDensity.current
            val inLibrary = pager.currentPage == visibleHomePages
            var statusHeight by remember { mutableFloatStateOf(0f) }
            val geometry = homeGeometry(maxWidth.value, maxHeight.value, preset, state.labels,
                statusHeight = if (state.verticalStatus) statusHeight + 22f else 0f,
                labelHeight = with(density) { 14.sp.toDp().value } + 6f, inLibrary = inLibrary,
                homeBottomSpace = if (isDefaultHome) 44f else 88f,
                // The rail's round search/back controls only show without the search pill or on Discover.
                railControls = !state.searchPill || pager.currentPage < 0)
            SideEffect {
                resizePitchX = with(density) { geometry.cellWidth.dp.toPx() }
                resizePitchY = with(density) { minOf((geometry.widgetHeight + 18f) / 2f, geometry.rowHeight).dp.toPx() }
                resizeTopPitch = with(density) { ((geometry.widgetHeight + 18f) / 2f).dp.toPx() }
                resizeAppPitch = with(density) { geometry.rowHeight.dp.toPx() }
            }
            LaunchedEffect(geometry.gridWidth, geometry.widgetHeight, geometry.rowHeight) { resizeSlot = null }
            SideEffect { expandedWorkspace = geometry.expanded }
            // Unfolded with Today View beside Home (or off), there's nothing to the left of Home: spring back.
            val noLeftPageUnfolded = todayMode && geometry.expanded && state.todayUnfolded != "PAGE"
            // Stop the swipe itself (not just spring back): the Today View is already on screen beside Home.
            SideEffect { pageGestures.minPage = if (noLeftPageUnfolded) firstHome else 0 }
            LaunchedEffect(noLeftPageUnfolded, pager.settledPage) {
                if (noLeftPageUnfolded && pager.settledPage < 0) pager.animateScrollToPage(0)
            }
            LaunchedEffect(geometry.expanded) {
                if (!geometry.expanded) {
                    val sessionTargetsLeading = widgetSession?.let { session ->
                        session.candidate?.page == -1 || session.targetIndex?.let(::homeCellPage) == -1
                    } == true
                    val savedTargetLeading = widgetTargetIndex != Int.MIN_VALUE && homeCellPage(widgetTargetIndex) == -1
                    if (sessionTargetsLeading || savedTargetLeading) {
                        widgetSession = null
                        widgetTargetIndex = Int.MIN_VALUE
                        widgetExactTarget = false
                        widgetPackage = null
                        widgetProfileSerial = null
                        widgetPlacementMessage = null
                        sheet = ""
                    }
                    val dragTouchesLeading = drag.source?.page == -1 ||
                        ((target as? DropTarget.Home)?.index?.let(::homeCellPage) == -1)
                    if (dragTouchesLeading) {
                        drag.clear()
                    }
                }
            }
            val contentHeight = maxHeight
            val panelWidth = maxWidth - geometry.homeWidth.dp
            val pagerWidth = maxWidth - preset.dockWidth.dp - 28.dp
            val leftColumnOrigin = (maxWidth / 2f - geometry.gridWidth.dp) / 2f - 16.dp
            val homeStride = panelWidth - leftColumnOrigin
            val bottomSpace = if (isDefaultHome) 44.dp else 88.dp
            val workspaceMotion = if (geometry.expanded) remember(firstHome, visibleHomePages, pagerWidth, homeStride, density) {
                WorkspacePageMotion(firstHome, visibleHomePages, with(density) { pagerWidth.toPx() }, with(density) { homeStride.toPx() })
            } else null
            val dockScroll = rememberScrollState()
            var gestureOriginInRoot by remember { mutableStateOf(Offset.Zero) }
            var gestureOriginInWindow by remember { mutableStateOf(Offset.Zero) }
            var scrubberBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
            val pagerInputEnabled = pager.currentPage in -firstHome..visibleHomePages && !drag.active &&
                widgetSession == null && resizeSlot == null && sheet.isEmpty() && !showFirstRun && selectedId == null &&
                openFolderId == null && emptyCellIndex == null && createFolderFirstId == null &&
                launcherActivity.backups.preview == null && !launcherActivity.backups.pickerPending &&
                !launcherActivity.backgrounds.pickerPending && widgets.setupStatus == null &&
                widgets.reconfigureWidgetId == null
            Box(Modifier.fillMaxSize().onGloballyPositioned {
                gestureOriginInRoot = it.boundsInRoot().topLeft
                gestureOriginInWindow = it.boundsInWindow().topLeft
            }.onePageGestures(
                nativePager,
                pageGestures,
                motion = workspaceMotion,
                enabled = pagerInputEnabled,
                // Positive IDs are provider-owned Android views. Leave their vertical
                // stream untouched so scrollable widgets retain native gesture handling.
                // A dock that is already scrolled also gets first use of a downward drag.
                canStartDownwardSwipe = { point ->
                    if (pager.currentPage !in 0 until visibleHomePages) false else {
                        val region = drag.hit(point + gestureOriginInRoot, eligibleDragPages)
                        val rootOnScreen = IntArray(2).also(launcherRootView::getLocationOnScreen)
                        val screenPoint = point + gestureOriginInWindow +
                            Offset(rootOnScreen[0].toFloat(), rootOnScreen[1].toFloat())
                        !(region?.target is DropTarget.Dock && dockScroll.value > 0) &&
                            !((region?.target as? DropTarget.Widget)?.index?.let { state.widgetStacks[it]?.isNotEmpty() } == true) &&
                            !nativeWidgetConsumesVerticalGesture(launcherRootView, screenPoint)
                    }
                },
                canStartGesture = { point -> geometry.expanded || homePages < 2 || !state.pageScrub || !scrubberBounds.contains(point + gestureOriginInRoot) },
                onDownwardSwipe = { panel ->
                    if (panel == ShadePanel.SEARCH) { if (state.swipeDownSearch) launcherActivity.openSpotlight() }
                    else launcherActivity.openSystemShade(panel)
                },
                onLeadingOverscroll = if (firstHome == 0) onDiscover else null,
            )) {
            val pagerModifier = Modifier.align(if (state.leftHanded) Alignment.TopEnd else Alignment.TopStart)
                .fillMaxHeight().width(pagerWidth)
                .drawWithContent {
                    // The recorded Home layer only feeds Google Discover's frame; Today View draws directly.
                    if (todayMode) drawContent()
                    else {
                        homeLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(homeLayer)
                    }
                    LiveDiscover.host.get()?.invalidateFrame()
                }.testTag("app-pager")
                .discoverSwipe(firstHome == 0 && pager.currentPage == 0 && !drag.active && sheet.isEmpty() &&
                    !showFirstRun && selectedId == null, onDiscover)
                .onGloballyPositioned {
                    if (firstHome > 0 && !todayMode) {
                        val bounds = it.boundsInWindow()
                        LiveDiscover.pagerOrigin = bounds.topLeft
                        val padding = 32 * density.density
                        LiveDiscover.prepare(launcherActivity,
                            android.graphics.Rect((bounds.left + padding).toInt(), (bounds.top + padding).toInt(),
                                (bounds.right - 16 * density.density).toInt(), (bounds.bottom - padding).toInt()), bounds.width)
                    }
                }
                .semantics { stateDescription = if (pager.currentPage == -1) "Discover" else if (pager.currentPage == visibleHomePages) "All apps" else "Home page ${pager.currentPage + 1} of $visibleHomePages" }
            if (geometry.expanded) {
                Box(pagerModifier) {
                    // PagerState remains the source of truth for native Discover progress,
                    // snapping, accessibility state, and programmatic page requests.
                    HorizontalPager(nativePager, Modifier.fillMaxSize(), userScrollEnabled = false,
                        key = { if (it < firstHome) "discover" else if (it - firstHome == visibleHomePages) "library" else "home-${it - firstHome}" }) { }
                    ExpandedWorkspace(
                        nativePager = nativePager, motion = workspaceMotion!!, firstHome = firstHome,
                        visibleHomePages = visibleHomePages, panelWidth = panelWidth,
                        contentHeight = contentHeight, bottomSpace = bottomSpace, geometry = geometry,
                        state = state, previewSlots = previewLayout.slots, previewLeadingSlots = previewLayout.leadingSlots,
                        previewWidgetPlacements = previewLayout.widgetPlacements, appsById = appsById,
                        widgets = widgets, drag = drag, target = target, insertionTarget = insertionTarget,
                        libraryQuery = libraryQuery, onLibraryQuery = { libraryQuery = it },
                        onLaunch = onLaunch, onLaunchFrom = onLaunchFrom, onPinned = model::setPinned,
                        onTurnOnWork = { model.turnOnWork(it) },
                        onActions = { selectedId = it.id }, onWidget = { widgetSlot = it; sheet = "widgetActions" },
                        onFolder = { openFolderId = it },
                        onEmptyWidget = onEmptyLongPress,
                        onRefresh = model::refresh,
                        leftPageContent = leftPageContent,
                        besideContent = if (todayMode && state.todayUnfolded == "BESIDE") todayContent else null,
                    )
                }
            } else {
                HorizontalPager(nativePager, pagerModifier,
                    // Keep adjacent Home panes attached so ordinary back-and-forth paging does
                    // not synchronously inflate provider RemoteViews inside the gesture frame.
                    // Discover is two physical positions before Home 2. Retain both Home
                    // neighbors to avoid reinflating Home 2's RemoteViews during native exit.
                    beyondViewportPageCount = if (firstHome > 0) 2 else 1,
                    userScrollEnabled = !drag.active && resizeSlot == null, flingBehavior = pageFling,
                    key = { if (it < firstHome) "discover" else if (it - firstHome == visibleHomePages) "library" else "home-${it - firstHome}" }) { physicalPage ->
                    val page = physicalPage - firstHome
                    if (page == -1) {
                        leftPageContent(Modifier.fillMaxSize())
                    } else if (page == visibleHomePages) {
                        AppLibrary(state, libraryQuery, { libraryQuery = it }, onLaunch, model::setPinned,
                            onActions = { selectedId = it.id }, modifier = Modifier.fillMaxSize().padding(start = 16.dp, top = 16.dp, bottom = bottomSpace).testTag("library-page"),
                            drag = drag, page = visibleHomePages, onLaunchFrom = onLaunchFrom, onTurnOnWork = { model.turnOnWork(it) })
                    } else {
                        // Centered beside the rail when the grid is narrower than the space (short, wide windows).
                        Row(Modifier.fillMaxSize().testTag("home-surface"), horizontalArrangement = Arrangement.Center) {
                            HomePagePane(page, state, previewLayout.slots, previewLayout.leadingSlots, previewLayout.widgetPlacements, appsById, geometry, contentHeight,
                                bottomSpace, widgets, drag, target, insertionTarget, showLargeWidget = false,
                                onLaunch = onLaunchFrom, onActions = { selectedId = it.id },
                                onWidget = { widgetSlot = it; sheet = "widgetActions" },
                                onFolder = { openFolderId = it },
                                onEmptyWidget = onEmptyLongPress,
                                onRefresh = model::refresh)
                        }
                    }
                }
            }
            if (state.verticalStatus) StatusRail(deviceStatus,
                Modifier.align(railTop(state.leftHanded)).railEdge(state.leftHanded, 12.dp).offset(y = geometry.contentTop.dp)
                    .width(preset.dockWidth.dp).onSizeChanged {
                        // The normal rail's 20dp location slot and 3dp gap do not move the dock.
                        statusHeight = (with(density) { it.height.toDp().value } -
                            if (contentHeight < 500.dp) 0f else 23f).coerceAtLeast(0f)
                    },
                compact = contentHeight < 500.dp, iconSize = dockIconSize(geometry.iconSize).dp, style = state.statusStyle,
                island = null)
            // Background and border without clipping, so Harbor-style magnified icons can grow past the rail.
            Box(Modifier.align(railTop(state.leftHanded)).railEdge(state.leftHanded, 12.dp).offset(y = geometry.dockTop.dp)
                .width(preset.dockWidth.dp).height(geometry.dockHeight.dp).graphicsLayer {
                    // Composite the stationary dock independently of the shared pager layer (not while magnifying: it would clip).
                    compositingStrategy = if (state.dockMagnify) androidx.compose.ui.graphics.CompositingStrategy.Auto
                        else androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                }.background(Glass.copy(alpha = state.statusStyle.railGlass), RoundedCornerShape(30.dp))
                .border(1.dp, RailBorder, RoundedCornerShape(30.dp)).testTag("dock")) {
                Column(Modifier.padding(vertical = 8.dp).verticalScroll(dockScroll)) {
                    DockAppColumn(state.dock, previewLayout.dock, appsById, geometry.dockRowHeight,
                        dockIconSize(geometry.iconSize), drag, insertionTarget,
                        onLaunch = onLaunchFrom, onChoose = { dockSlot = it; sheet = "dock" },
                        magnify = FeatureScopes.on(state.featureScopes, "dockMagnify", state.dockMagnify, screenFor(geometry.expanded)) &&
                            !LocalReduceMotion.current, leftHanded = state.leftHanded)
                }
            }
            Column(Modifier.align(if (state.leftHanded) Alignment.BottomEnd else Alignment.BottomStart).width(pagerWidth)
                .padding(start = if (state.leftHanded) 0.dp else 16.dp, end = if (state.leftHanded) 16.dp else 0.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!isDefaultHome) FilledTonalButton(onClick = { sheet = ""; onMakeDefault() }, Modifier.heightIn(min = 48.dp).testTag("home-setup")) {
                    Icon(Icons.Rounded.Home, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.set_as_home_app))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    if (!drag.active) IconButton(onClick = openDiscover, Modifier.size(32.dp).testTag("discover-page-link")) {
                        Icon(Icons.Rounded.Explore, "Discover", tint = Color.White.copy(alpha = .65f), modifier = Modifier.size(17.dp))
                    }
                    // iOS: a "Search" capsule where the page dots are; the dots come back while paging or editing.
                    // iOS: drag sideways along the Search pill or the dots to scrub through Home pages, a tick per page.
                    var scrubbing by remember { mutableStateOf(false) }
                    val scrubStep = with(density) { 34.dp.toPx() }
                    val showSearchPill = state.searchPill && !homeEdit.active && !drag.active && !scrubbing &&
                        !nativePager.isScrollInProgress && pager.currentPage in 0 until homePages
                    androidx.compose.animation.AnimatedContent(showSearchPill, label = "search pill",
                        // Cover screen only: unfolded, Home already shows two pages side by side.
                        modifier = if (geometry.expanded || homePages < 2 || !state.pageScrub) Modifier else Modifier.onGloballyPositioned { scrubberBounds = it.boundsInRoot() }.pointerInput(homePages) {
                            var startPage = 0
                            var travel = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { scrubbing = true; travel = 0f; startPage = pager.currentPage.coerceIn(0, homePages - 1) },
                                onDragEnd = { scrubbing = false }, onDragCancel = { scrubbing = false },
                            ) { change, amount ->
                                change.consume()
                                travel += amount
                                val page = (startPage + (travel / scrubStep).roundToInt()).coerceIn(0, homePages - 1)
                                if (page != pager.currentPage) {
                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    scope.launch { pager.scrollToPage(page) }
                                }
                            }
                        }.semantics { contentDescription = "Page scrubber" },
                        transitionSpec = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) togetherWith
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) },
                        contentAlignment = Alignment.Center) { pill ->
                        if (pill) HomeSearchPill { if (!state.googleSearch || !onGoogleSearch(null)) launcherActivity.openSpotlight() }
                        else Row(Modifier.height(30.dp).background(if (scrubbing) Color.White.copy(alpha = .18f) else Color.Transparent, CircleShape)
                            .padding(horizontal = if (scrubbing) 6.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (visibleHomePages <= 6) repeat(visibleHomePages) { index ->
                            Box(Modifier.size(28.dp).clip(CircleShape).clickable { scope.launch { pager.animateScrollToPage(index) } }
                                .semantics { contentDescription = if (index == homePages) "New home page" else "Home page ${index + 1}" }, contentAlignment = Alignment.Center) {
                                if (index == homePages) Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                else Box(Modifier.size(if (index == pager.currentPage) 6.dp else 4.dp).background(if (index == pager.currentPage) LocalHomeInk.current.primary else LocalHomeInk.current.faint, CircleShape))
                            }
                        } else Text("${minOf(pager.currentPage + 1, homePages)} / $homePages", color = Color.White, fontSize = 12.sp)
                        }
                    }
                    IconButton(onClick = openLibrary, Modifier.size(32.dp).testTag("library-page-link")) {
                        Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, "All apps page", tint = Color.White.copy(alpha = if (pager.currentPage == homePages) 1f else .6f), modifier = Modifier.size(17.dp))
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(homeEdit.active && sheet.isEmpty(),
                Modifier.align(if (state.leftHanded) Alignment.TopEnd else Alignment.TopStart).width(pagerWidth),
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it / 2 },
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it / 2 }) {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 2.dp).testTag("jiggle-bar"),
                    horizontalArrangement = if (geometry.expanded) Arrangement.spacedBy(8.dp, Alignment.End) else Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically) {
                    val editPage = pager.currentPage.coerceIn(0, homePages - 1)
                    JigglePill("", Icons.Rounded.Add, description = "Add widget") {
                        widgetSlot = model.nextWidgetSlot(); widgetTargetIndex = homeCellIndex(editPage, 0); widgetExactTarget = false
                        widgetPackage = null; widgetProfileSerial = null; sheet = "widgets"
                    }
                    JigglePill(stringResource(R.string.edit)) {
                        emptyCellIndex = homeEdit.lastEmptyIndex?.takeIf { homeCellPage(it) == editPage } ?: homeCellIndex(editPage, 0)
                    }
                    // Unfolded, keep all three together at the top right instead of spread across two pages.
                    if (!geometry.expanded) Spacer(Modifier.weight(1f))
                    JigglePill(stringResource(R.string.done), emphasized = true) { haptic.performHapticFeedback(HapticFeedbackType.Confirm); homeEdit.stop() }
                }
            }
            if (!inLibrary && !drag.active) Column(Modifier.align(railBottom(state.leftHanded)).railEdge(state.leftHanded, 12.dp).padding(bottom = 6.dp)
                .width(preset.dockWidth.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val controlSize = dockIconSize(geometry.iconSize).dp
                if (pager.currentPage == -1) CircleControl(Icons.Rounded.ArrowForward, "Back to home", "discover-home", controlSize) { scope.launch { pager.animateScrollToPage(0) } }
                val searchBounds = remember { android.graphics.Rect() }
                if (!state.searchPill || pager.currentPage !in 0 until homePages) Box(Modifier.onGloballyPositioned { searchBounds.set(it.boundsInWindow().toAndroidBounds()) }) {
                    CircleControl(Icons.Rounded.Search, if (state.googleSearch) "Search Google" else "Search apps", "search", controlSize) {
                        if (!state.googleSearch || !onGoogleSearch(searchBounds)) launcherActivity.openSpotlight()
                    }
                }
            }
            if (sheet.isNotEmpty() && sheet != "widgets") {
                val activeCustomizationPage = if (sheet == "settings:wallpaper") CustomizationPage.WALLPAPER else customizationPage
                ModalBottomSheet(onDismissRequest = {
                    customizationPage = CustomizationPage.OVERVIEW
                    sheet = ""; widgetPackage = null; widgetExactTarget = false
                }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
                    containerColor = MaterialTheme.colorScheme.surface, fullScreen = sheet.startsWith("settings")) {
                    ModalDialogBackHandler {
                        if ((sheet == "settings" || sheet == "settings:wallpaper") &&
                            activeCustomizationPage != CustomizationPage.OVERVIEW) {
                            customizationPage = CustomizationPage.OVERVIEW
                            sheet = "settings"
                        } else {
                            customizationPage = CustomizationPage.OVERVIEW
                            sheet = ""; widgetPackage = null; widgetExactTarget = false
                        }
                    }
                    when (sheet) {
                        "dock" -> AppPicker(state.apps, dockSlot,
                            onSelect = {
                                if (canPlaceInDock(state.layout, it.id)) {
                                    model.applyDrop(it.id, DropTarget.Dock(dockSlot)); sheet = ""
                                }
                            },
                            onClear = { model.removePlacement(DropTarget.Dock(dockSlot)) },
                            onLongClick = { selectedId = it.id; sheet = "" },
                            canSelect = { canPlaceInDock(state.layout, it.id) },
                            blockedHint = if (state.dock.none { it == null }) "Dock full • Move an app out first" else null)
                        "pins" -> Column(Modifier.fillMaxHeight(.9f).imePadding()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { sheet = "" }) { Text(stringResource(R.string.done)) }
                            }
                            AppLibrary(state, pinQuery, { pinQuery = it }, onLaunch, model::setPinned,
                                onActions = { selectedId = it.id; sheet = "" }, editing = true, modifier = Modifier.weight(1f).fillMaxWidth(),
                                onTurnOnWork = { model.turnOnWork(it) })
                        }
                        "settings", "settings:wallpaper" -> CustomizationSheet(state, wide, model, isDefaultHome,
                            page = activeCustomizationPage, onPage = { customizationPage = it; sheet = "settings" },
                            onMakeDefault = { sheet = ""; onMakeDefault() },
                            onClose = { customizationPage = CustomizationPage.OVERVIEW; sheet = "" }, onEditPins = { sheet = "pins" },
                            onWidget = { widgetSlot = it; widgetPackage = null; widgetProfileSerial = null; widgetExactTarget = false; sheet = "widgets" },
                            onAddWidget = { page -> widgetSlot = model.nextWidgetSlot(); widgetTargetIndex = page * HOME_CELLS; widgetPackage = null; widgetProfileSerial = null; widgetExactTarget = false; sheet = "widgets" },
                            onRemoveWidget = widgets::remove,
                            onExportLayout = { sheet = ""; launcherActivity.backups.startExport() },
                            onImportLayout = { sheet = ""; launcherActivity.backups.startImport() },
                            appearance = appearance, onAppearanceMode = onAppearanceMode,
                            onAppearanceManual = onAppearanceManual, onAppearanceDeviceLocation = onAppearanceDeviceLocation,
                            onAppearanceClear = onAppearanceClear,
                            onShadeSetup = { sheet = ""; onShadeSetup() },
                            onShowWelcome = { sheet = ""; onShowWelcome() },
                            backgrounds = launcherActivity.backgrounds,
                            onWallpaperPreview = { sheet = ""; onWallpaperPreview() }, homePage = pager.currentPage.coerceIn(0, homePages - 1))
                        "widgetActions" -> model.placement(widgetSlot)?.let { placement ->
                            val topPitch = (geometry.widgetHeight + 18f) / 2f
                            val gridSizing = WidgetGridSizing(GRID_COLUMNS, GRID_ROWS, geometry.cellWidth,
                                minOf(topPitch, geometry.rowHeight), maxOf(topPitch, geometry.rowHeight), 10f, 18f,
                                topRowHeightDp = topPitch, appRowHeightDp = geometry.rowHeight)
                            val constraints = widgets.manager.getAppWidgetInfo(placement.id)?.let { widgets.sizing(it, gridSizing) }
                            WidgetActions(placement, constraints,
                                stackCards = model.stackCards(placement.slot), stackLabel = { widgetLabel(it, widgets) },
                                stackRotate = state.stackRotate, onStackRotate = model::setStackRotate,
                                onAddToStack = {
                                    stackTargetSlot = placement.slot; widgetSlot = placement.slot
                                    widgetPackage = null; widgetProfileSerial = null; widgetExactTarget = false; sheet = "widgets"
                                },
                                onRemoveFromStack = { model.removeFromStack(placement.slot, it) },
                                onShowFirstInStack = { model.showFirstInStack(placement.slot, it) },
                                canConfigure = widgets.canReconfigure(placement.id),
                                onConfigure = { widgets.reconfigure(placement.id); sheet = "" },
                                isValid = { x, y -> (x == placement.spanX && y == placement.spanY) || resizeWidget(state.layout, widgetSlot, x, y) != state.layout },
                                onResize = { x, y -> model.resizeWidget(widgetSlot, x, y) },
                                onStartResize = { x, y ->
                                    resizeSlot = widgetSlot; resizeWidth = x; resizeHeight = y
                                    resizeConstraints = constraints; sheet = ""
                                },
                                onMoveToPage = { page ->
                                    (0 until HOME_CELLS).firstOrNull { local ->
                                        widgetCandidate(state.layout, placement.slot, page * HOME_CELLS + local,
                                            placement.spanX, placement.spanY) != null
                                    }?.let { model.moveWidgetTo(placement.slot, page * HOME_CELLS + it) } == true
                                }, homePages = homePages,
                                onReplace = {
                                    widgetPackage = null
                                    widgetProfileSerial = widgets.manager.getAppWidgetInfo(placement.id)?.profile?.let {
                                        launcherActivity.getSystemService(UserManager::class.java).getSerialNumberForUser(it)
                                    }?.takeIf { it >= 0 }
                                    widgetExactTarget = false; sheet = "widgets"
                                },
                                onRemove = { widgets.remove(widgetSlot); sheet = "" },
                                onClose = { sheet = "" })
                        }
                    }
                }
            }
            if (showFirstRun) {
                // Full-screen, iOS Setup Assistant style; Back steps back, Skip Setup or Get Started finishes.
                ModalBottomSheet(onDismissRequest = onFinishFirstRun, modifier = Modifier.testTag("first-run-setup"), fullScreen = true) {
                    Onboarding(isDefaultHome, onMakeDefault, onShadeSetup,
                        systemWallpaper = state.systemWallpaper,
                        onWallpaper = { value ->
                            if (value != state.systemWallpaper) { model.setSystemWallpaper(value); launcherActivity.recreate() }
                        },
                        onFinish = onFinishFirstRun, state = state, model = model)
                }
            }
            if (sheet == "widgets") {
                val catalogProfiles = remember(state.profiles) { state.profiles.filter { it.isPersonal || it.isWork } }
                val selectedProfile = catalogProfiles.firstOrNull { it.userSerial == widgetProfileSerial }
                    ?: catalogProfiles.firstOrNull { it.isPersonal } ?: AppProfile(0, "Personal", true, false, false, true, true)
                val userManager = remember(launcherActivity) { launcherActivity.getSystemService(UserManager::class.java) }
                val providers = remember(widgetPackage, selectedProfile, sheet, state.apps) {
                    val user = userManager.getUserForSerialNumber(selectedProfile.userSerial)
                    if (user == null || !selectedProfile.available || !selectedProfile.unlocked || selectedProfile.quiet) emptyList()
                    else runCatching { widgetPackage?.let { widgets.providersForPackage(it, user) }
                        ?: widgets.providers(user) }.getOrDefault(emptyList()).filter { provider ->
                        provider.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 &&
                            provider.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER == 0
                    }
                }
                val catalog by produceState<List<WidgetCatalogEntry>?>(null, providers, selectedProfile.userSerial, sheet) {
                    value = withContext(Dispatchers.IO) { widgetCatalog(launcherActivity, providers, selectedProfile) }
                }
                val topPitch = (geometry.widgetHeight + 18f) / 2f
                val pickerSizing = remember(geometry) { WidgetGridSizing(GRID_COLUMNS, GRID_ROWS,
                    geometry.cellWidth, minOf(topPitch, geometry.rowHeight),
                    maxOf(topPitch, geometry.rowHeight), 10f, 18f,
                    topRowHeightDp = topPitch, appRowHeightDp = geometry.rowHeight) }
                val footprint: (AppWidgetProviderInfo) -> WidgetSpan? = { provider ->
                    widgets.sizing(provider, pickerSizing)?.takeIf { it.minimumFitsGrid }?.preferred
                }
                VisualWidgetPicker(catalog, catalogProfiles.ifEmpty { listOf(selectedProfile) }, selectedProfile,
                    onSelectProfile = { widgetProfileSerial = it.userSerial; widgetPlacementMessage = null },
                    onTurnOnWork = { model.turnOnWork(it) }, hiddenForDrag = widgetSession != null,
                    footprint = footprint,
                    onBack = widgetPickerBack,
                    onTap = tap@{ provider ->
                        if (todayAdd) {
                            val span = widgets.sizing(provider, pickerSizing)?.preferred
                            widgets.addToToday(provider, span?.let { TodaySize.forSpan(it.width, it.height) } ?: TodaySize.MEDIUM, pickerSizing)
                            todayAdd = false; sheet = ""; widgetPackage = null
                            return@tap
                        }
                        stackTargetSlot?.let { stackSlot ->
                            val placement = model.placement(stackSlot)
                            val min = widgets.sizing(provider, pickerSizing)?.minimum
                            if (placement == null || (min != null && (min.width > placement.spanX || min.height > placement.spanY))) {
                                widgetPlacementMessage = "This widget needs a bigger space than this stack. Resize the stack first."
                            } else {
                                widgets.addToStack(stackSlot, provider, pickerSizing)
                                stackTargetSlot = null; sheet = ""; widgetPackage = null; widgetPlacementMessage = null
                            }
                            return@tap
                        }
                        footprint(provider)?.let { preferredSpan ->
                            val existing = model.placement(widgetSlot)
                            val constraints = widgets.sizing(provider, pickerSizing)
                            val span = existing?.let { placement ->
                                WidgetSpan(placement.spanX, placement.spanY).takeIf {
                                    constraints != null && it.width in constraints.minimum.width..constraints.maximum.width &&
                                        it.height in constraints.minimum.height..constraints.maximum.height
                                }
                            } ?: preferredSpan
                            val special = existing?.takeIf { it.row + it.spanY > GRID_ROWS }
                            if (special != null) {
                                widgetSession = WidgetPickerSession(provider, widgetSlot,
                                    WidgetSpan(special.spanX, special.spanY), Offset.Zero,
                                    dragging = false, candidate = special)
                                widgetPlacementMessage = null
                                scope.launch { pager.scrollToPage(special.page.coerceAtLeast(0).coerceAtMost(homePages - 1)) }
                                return@let
                            }
                            val requestedIndex = existing?.let { homeCellIndex(it.page, it.row * GRID_COLUMNS + it.column) }
                                ?: widgetTargetIndex.takeUnless { it == Int.MIN_VALUE } ?: 0
                            val requestedPage = homeCellPage(requestedIndex).coerceIn(if (expandedWorkspace) -1 else 0, homePages)
                            val availablePages = (if (expandedWorkspace) -1 else 0)..homePages
                            val autoPages = (listOf(requestedPage) + availablePages.filter { it != requestedPage })
                            val freeIndex = if (existing != null || widgetExactTarget) requestedIndex.takeIf {
                                widgetCandidate(state.layout, widgetSlot, it, span.width, span.height) != null
                            } else autoPages.asSequence().flatMap { page ->
                                (0 until HOME_CELLS).asSequence().map { homeCellIndex(page, it) }
                            }.firstOrNull { widgetCandidate(state.layout, widgetSlot, it, span.width, span.height) != null }
                            val targetIndex = freeIndex ?: requestedIndex
                            widgetSession = WidgetPickerSession(provider, widgetSlot, span, Offset.Zero,
                                dragging = false, targetIndex = targetIndex)
                            widgetPlacementMessage = if (freeIndex == null)
                                "There isn’t room for this size. Choose another page or move an item first." else null
                            scope.launch { pager.scrollToPage(homeCellPage(targetIndex).coerceIn(0, homePages)) }
                        }
                    },
                    onBuiltin = builtin@{ builtinId ->
                        if (todayAdd) { model.addTodayWidget(builtinId, TodaySize.SMALL); todayAdd = false; sheet = ""; return@builtin }
                        stackTargetSlot?.let { stackSlot ->
                            model.addToStack(stackSlot, builtinId); stackTargetSlot = null; sheet = ""; widgetPackage = null
                            return@builtin
                        }
                        val existing = model.placement(widgetSlot)
                        val special = existing?.takeIf { it.row + it.spanY > GRID_ROWS }
                        val span = existing?.let { WidgetSpan(it.spanX, it.spanY) } ?: WidgetSpan(2, 2)
                        if (special != null) {
                            widgetSession = WidgetPickerSession(null, widgetSlot, span, Offset.Zero,
                                dragging = false, candidate = special, builtinId = builtinId)
                            widgetPlacementMessage = null
                            scope.launch { pager.scrollToPage(special.page.coerceAtLeast(0).coerceAtMost(homePages - 1)) }
                            return@builtin
                        }
                        val requested = existing?.let {
                            homeCellIndex(it.page, it.row * GRID_COLUMNS + it.column)
                        } ?: widgetTargetIndex.takeUnless { it == Int.MIN_VALUE } ?: 0
                        val requestedPage = homeCellPage(requested).coerceIn(if (expandedWorkspace) -1 else 0, homePages)
                        val availablePages = (if (expandedWorkspace) -1 else 0)..homePages
                        val candidates = if (model.placement(widgetSlot) != null || widgetExactTarget) sequenceOf(requested)
                            else (listOf(requestedPage) + availablePages.filter { it != requestedPage }).asSequence()
                                .flatMap { page -> (0 until HOME_CELLS).asSequence().map { homeCellIndex(page, it) } }
                        val free = candidates.firstOrNull {
                            widgetCandidate(state.layout, widgetSlot, it, span.width, span.height) != null
                        }
                        widgetSession = WidgetPickerSession(null, widgetSlot, span, Offset.Zero,
                            dragging = false, targetIndex = free ?: requested, builtinId = builtinId)
                        widgetPlacementMessage = if (free == null)
                            "There isn’t room for this card. Choose another page or move an item first." else null
                        scope.launch { pager.scrollToPage(homeCellPage(free ?: requested).coerceIn(0, homePages)) }
                    },
                    onDragStart = { provider, point ->
                        if (stackTargetSlot == null && !todayAdd) footprint(provider)?.let { span ->
                            widgetSession = WidgetPickerSession(provider, widgetSlot, span, point, dragging = true)
                            widgetPlacementMessage = null
                            scope.launch { pager.scrollToPage(lastHomePage.coerceIn(0, homePages - 1)) }
                        }
                    },
                    onDrag = { point -> widgetSession = widgetSession?.copy(pointer = point) },
                    onDrop = {
                        val session = widgetSession
                        if (session != null && widgetDraft != null) {
                            session.provider?.let { widgets.add(widgetDraft, it, pickerSizing) }
                                ?: session.builtinId?.let { widgets.setBuiltin(widgetDraft.copy(id = it)) }
                            widgetSession = null; sheet = ""; widgetPackage = null
                        } else {
                            leaveTemporaryWidgetPage(); widgetSession = null
                            widgetPlacementMessage = "There isn’t room there. Try another space or page."
                        }
                    },
                    onCancelDrag = {
                        if (widgetSession != null) {
                            leaveTemporaryWidgetPage(); widgetSession = null
                        }
                    })
                widgetSession?.let { session ->
                    val placementDensity = LocalDensity.current
                    val sessionEntry = session.provider?.let { selected -> catalog?.firstOrNull {
                        it.provider.provider == selected.provider && it.provider.profile == selected.profile } }
                    // Legacy overflow replacements are locked to their existing view
                    // bounds and may begin below the canonical six-row grid. They have
                    // no Home-cell address; specialAnchor below is their visual anchor.
                    val candidateIndex = widgetDraft?.takeIf { session.candidate == null }
                        ?.let { homeCellIndex(it.page, it.row * GRID_COLUMNS + it.column) }
                    val visualIndex = candidateIndex ?: widgetRawTarget?.index ?: session.targetIndex
                    val specialAnchor = session.candidate?.let { drag.regions[DropTarget.Widget(session.slot)]?.bounds }
                    val anchor = specialAnchor ?: visualIndex?.let { drag.regions[DropTarget.Home(it)]?.bounds }
                    Box(Modifier.fillMaxSize().testTag("widget-placement-mode")
                        .then(if (!session.dragging && session.candidate == null) Modifier.pointerInput(session.slot, session.span) {
                            detectTapGestures { local ->
                                val point = local + drag.rootOrigin
                                val cell = drag.regions.values.firstOrNull {
                                    it.target is DropTarget.Home && it.page in eligibleDragPages && it.bounds.contains(point)
                                }?.target as? DropTarget.Home
                                cell?.let { widgetSession = session.copy(pointer = point, targetIndex = it.index) }
                            }
                        } else Modifier)) {
                        Row(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.folioSafeTop).padding(top = 8.dp)
                            .background(Glass.copy(alpha = .97f), RoundedCornerShape(22.dp))
                            .testTag("widget-placement-toolbar"), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = widgetPickerBack) { Text(stringResource(R.string.back_to_widgets)) }
                            if (session.candidate != null) Text(stringResource(R.string.replace_here), color = Ink,
                                modifier = Modifier.testTag("widget-replacement-locked"))
                            val targetPage = homeCellPage(session.targetIndex ?: 0)
                            if (!session.dragging && session.candidate == null) IconButton(
                                enabled = targetPage > if (expandedWorkspace) -1 else 0, onClick = {
                                val local = homeCellLocal(session.targetIndex ?: 0)
                                val page = targetPage - 1
                                widgetSession = session.copy(targetIndex = homeCellIndex(page, local))
                                scope.launch { pager.animateScrollToPage(page.coerceAtLeast(0)) }
                            }) { Icon(Icons.Rounded.ChevronLeft, "Previous home page") }
                            Text("${session.span.width} × ${session.span.height}", color = Ink)
                            if (!session.dragging && session.candidate == null) IconButton(enabled = targetPage < homePages, onClick = {
                                val local = homeCellLocal(session.targetIndex ?: 0)
                                val page = (targetPage + 1).coerceAtMost(homePages)
                                widgetSession = session.copy(targetIndex = homeCellIndex(page, local))
                                scope.launch { pager.animateScrollToPage(page.coerceAtLeast(0)) }
                            }) { Icon(Icons.Rounded.ChevronRight, "Next home page") }
                            if (!session.dragging) TextButton(enabled = widgetDraft != null, onClick = {
                                widgetDraft?.let { draft ->
                                    val contentSize = specialAnchor?.let { bounds -> with(placementDensity) {
                                        WidgetContentSize(bounds.width.toDp().value, bounds.height.toDp().value)
                                    } }
                                    session.provider?.let { widgets.add(draft, it, pickerSizing, contentSize) }
                                        ?: session.builtinId?.let { widgets.setBuiltin(draft.copy(id = it)) }
                                    widgetSession = null; sheet = ""; widgetPackage = null
                                }
                            }, modifier = Modifier.testTag("widget-placement-apply")) { Text(stringResource(R.string.place)) }
                            TextButton(onClick = { leaveTemporaryWidgetPage(); widgetSession = null; sheet = ""; widgetPackage = null },
                                modifier = Modifier.testTag("widget-placement-cancel")) { Text(stringResource(R.string.cancel)) }
                        }
                        if (anchor != null) {
                            val density = LocalDensity.current
                            val cellWidthPx = with(density) { geometry.cellWidth.dp.toPx() }
                            fun pickerRowTop(row: Int): Float = if (row <= 2) row * with(density) { topPitch.dp.toPx() }
                                else with(density) { (geometry.widgetHeight + 18f + (row - 2) * geometry.rowHeight).dp.toPx() }
                            val candidateRow = homeCellLocal(visualIndex ?: 0) / GRID_COLUMNS
                            val previewWidth = specialAnchor?.let { with(density) { it.width.toDp() } }
                                ?: with(density) { (cellWidthPx * session.span.width - 10.dp.toPx()).toDp() }
                            val previewHeight = specialAnchor?.let { with(density) { it.height.toDp() } }
                                ?: with(density) { (pickerRowTop(candidateRow + session.span.height) -
                                    pickerRowTop(candidateRow) - 18.dp.toPx()).coerceAtLeast(48.dp.toPx()).toDp() }
                            val previewX = if (specialAnchor != null) anchor.left
                                else anchor.left + with(density) { 5.dp.toPx() }
                            Surface(Modifier.offset { IntOffset(previewX.roundToInt(), anchor.top.roundToInt()) }
                                .size(previewWidth, previewHeight).testTag("widget-placement-preview")
                                .semantics { stateDescription = if (widgetDraft != null) "Ready to place" else "No room here" },
                                color = if (widgetDraft != null) Glass.copy(alpha = .82f) else Color(0xFFE7B6B6).copy(alpha = .9f),
                                shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(3.dp,
                                    if (widgetDraft != null) Color.White else Color(0xFFFF6B6B))) {
                                Box(Modifier.fillMaxSize()) {
                                    if (sessionEntry != null) WidgetProviderPreview(sessionEntry, session.span,
                                        Modifier.fillMaxSize().padding(5.dp).clip(RoundedCornerShape(18.dp)))
                                    else Column(Modifier.align(Alignment.Center).padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(session.provider?.loadLabel(launcherActivity.packageManager)?.toString()
                                            ?: when (session.builtinId) {
                                                CLOCK_WIDGET -> "Clock"
                                                DATE_WIDGET -> "Date"
                                                else -> "Widget panel"
                                            }, color = Ink,
                                            textAlign = TextAlign.Center)
                                        Text("${session.span.width} × ${session.span.height}", color = Ink)
                                    }
                                    if (widgetDraft == null) Box(Modifier.matchParentSize()
                                        .background(Color(0xFFB83B3B).copy(alpha = .34f)), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.no_room_here), color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        } else if (session.dragging) {
                            Surface(Modifier.offset { IntOffset((session.pointer.x - 90.dp.toPx()).roundToInt(),
                                (session.pointer.y - 60.dp.toPx()).roundToInt()) }.size(180.dp, 120.dp)
                                .testTag("widget-placement-preview").semantics { stateDescription = "No room here" },
                                color = Color(0xFFE7B6B6).copy(alpha = .9f), shape = RoundedCornerShape(24.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (sessionEntry != null) WidgetProviderPreview(sessionEntry, session.span,
                                        Modifier.fillMaxSize().padding(5.dp).clip(RoundedCornerShape(18.dp)))
                                    Box(Modifier.matchParentSize().background(Color(0xFFB83B3B).copy(alpha = .34f)),
                                        contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_room_here), color = Color.White) }
                                }
                            }
                        }
                    }
                }
                widgetPlacementMessage?.let { message ->
                    Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp),
                        color = Glass, shape = RoundedCornerShape(18.dp)) { Text(message, Modifier.padding(16.dp), color = Ink) }
                }
            }
        }
        if (drag.active) {
            if (drag.moved) {
                if (pager.currentPage > 0) Box(Modifier.align(Alignment.CenterStart).width(6.dp).height(112.dp)
                    .background(Color.White.copy(alpha = if (edge < 0) .9f else .3f), RoundedCornerShape(6.dp)).testTag("drag-edge-left"))
                if (pager.currentPage < homePages) Box(Modifier.align(Alignment.CenterEnd).width(6.dp).height(112.dp)
                    .background(Color.White.copy(alpha = if (edge > 0) .9f else .3f), RoundedCornerShape(6.dp)).testTag("drag-edge-right"))
            }
            appsById[drag.source?.appId]?.let { app ->
                val size = 66.dp
                val px = with(LocalDensity.current) { size.toPx() }
                AppIcon(app, "Moving ${app.label}", Modifier
                    .offset { IntOffset((drag.pointer.x - drag.rootOrigin.x - px / 2).roundToInt(), (drag.pointer.y - drag.rootOrigin.y - px * .65f).roundToInt()) }
                    .size(size).shadow(16.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).testTag("drag-ghost"))
            }
            drag.source?.appId?.let { state.layout.folder(it) }?.let { folder ->
                Surface(Modifier.offset { IntOffset((drag.pointer.x - drag.rootOrigin.x - 42.dp.toPx()).roundToInt(),
                    (drag.pointer.y - drag.rootOrigin.y - 52.dp.toPx()).roundToInt()) }.size(84.dp)
                    .shadow(16.dp, RoundedCornerShape(20.dp)).testTag("folder-drag-ghost"),
                    color = Glass.copy(alpha = .96f), shape = RoundedCornerShape(20.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(folder.title, color = Ink, textAlign = TextAlign.Center) }
                }
            }
            drag.source?.widgetId?.let { id ->
                val width = 144.dp; val height = 108.dp
                val x = with(LocalDensity.current) { width.toPx() }
                val y = with(LocalDensity.current) { height.toPx() }
                Surface(Modifier.offset { IntOffset((drag.pointer.x - x / 2).roundToInt(), (drag.pointer.y - y * .65f).roundToInt()) }
                    .size(width, height).shadow(16.dp, RoundedCornerShape(24.dp)).testTag("drag-ghost"),
                    color = Glass.copy(alpha = .95f), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Widgets, null, tint = Ink)
                        Spacer(Modifier.height(8.dp))
                        Text(remember(id, widgets) { widgetLabel(id, widgets) }, color = Ink, maxLines = 2, textAlign = TextAlign.Center)
                    }
                }
            }
            if (blockedDock) Surface(
                Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.folioSafeTop)
                    .padding(top = 10.dp, start = 20.dp, end = 100.dp),
                color = Glass.copy(alpha = .96f), shape = RoundedCornerShape(18.dp)
            ) {
                Text(stringResource(R.string.dock_full_move_an_app_out_first),
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = Ink, fontSize = 13.sp)
            }
            if (drag.moved && drag.source?.target !is DropTarget.Library &&
                drag.source?.appId?.let(::isFolderId) != true) Surface(
                // Keep removal in the right-side control area that is vacated during a drag.
                // A centered target overlaps the expanded workspace's right-hand first cell.
                Modifier.align(railBottom(state.leftHanded)).navigationBarsPadding().railEdge(state.leftHanded, 12.dp).padding(bottom = 12.dp)
                    .width((if (expandedWorkspace) state.expanded else state.compact).dockWidth.dp).height(64.dp)
                    .dropRegion(drag, DropTarget.Remove).testTag("remove-drop-target"),
                color = if (target == DropTarget.Remove) Color(0xFFB33B3B) else Glass.copy(alpha = .96f), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxSize().padding(vertical = 6.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.DeleteOutline, null)
                    Text(stringResource(R.string.remove), fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        resizeSlot?.let { slot ->
            val placement = model.placement(slot)
            val bounds = drag.regions[DropTarget.Widget(slot)]?.bounds
            if (placement != null && bounds != null) {
                val minW = resizeConstraints?.minimum?.width ?: 2
                val minH = resizeConstraints?.minimum?.height ?: 2
                val maxW = minOf(GRID_COLUMNS - placement.column, resizeConstraints?.maximum?.width ?: GRID_COLUMNS)
                val maxH = minOf(GRID_ROWS - placement.row, resizeConstraints?.maximum?.height ?: GRID_ROWS)
                val feasible = placement.page >= -1 && placement.row in 0 until GRID_ROWS &&
                    !(placement.id >= 0 && resizeConstraints == null) && minW <= maxW && minH <= maxH
                val candidate = resizeWidget(state.layout, slot, resizeWidth, resizeHeight)
                val valid = feasible && ((resizeWidth == placement.spanX && resizeHeight == placement.spanY) || candidate != state.layout)
                val widthPx = (bounds.width + (resizeWidth - placement.spanX) * resizePitchX).coerceAtLeast(resizePitchX)
                val density = LocalDensity.current
                fun resizeRowTop(row: Int) = if (row <= 2) row * resizeTopPitch else 2 * resizeTopPitch + (row - 2) * resizeAppPitch
                val heightPx = (resizeRowTop(placement.row + resizeHeight) - resizeRowTop(placement.row) -
                    with(density) { 18.dp.toPx() }).coerceAtLeast(resizePitchY)
                Box(Modifier.offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                    .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
                    .border(3.dp, if (valid) Color.White else Color(0xFFFF6B6B), RoundedCornerShape(24.dp))
                    .testTag("widget-resize-preview-$slot")) {
                    Box(Modifier.align(Alignment.BottomEnd).offset(12.dp, 12.dp).size(44.dp)
                        .background(if (valid) Color.White else Color(0xFFFF6B6B), CircleShape)
                        .testTag("widget-resize-handle-$slot")
                        .pointerInput(slot, resizeConstraints) {
                            var dx = 0f; var dy = 0f; var startWidth = resizeWidth; var startHeight = resizeHeight
                            detectDragGestures(onDragStart = {
                                dx = 0f; dy = 0f; startWidth = resizeWidth; startHeight = resizeHeight
                            }, onDrag = { change, amount ->
                                change.consume(); dx += amount.x; dy += amount.y
                                if (feasible && resizeConstraints?.canResizeHorizontally != false)
                                    resizeWidth = (startWidth + (dx / resizePitchX).roundToInt()).coerceIn(minW, maxW)
                                if (feasible && resizeConstraints?.canResizeVertically != false)
                                    resizeHeight = (startHeight + (dy / resizePitchY).roundToInt()).coerceIn(minH, maxH)
                            })
                        }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.OpenInFull, "Drag to resize widget", tint = Ink, modifier = Modifier.size(22.dp))
                    }
                    Row(Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                        .background(Glass.copy(alpha = .96f), RoundedCornerShape(20.dp))) {
                        TextButton(onClick = { resizeSlot = null }) { Text(stringResource(R.string.cancel)) }
                        TextButton(enabled = valid, onClick = {
                            model.resizeWidget(slot, resizeWidth, resizeHeight); resizeSlot = null
                        }) { Text(stringResource(R.string.apply)) }
                    }
                    if (!feasible) Text(stringResource(R.string.move_this_widget_into_the_six_row_grid_b),
                        color = Color.White, modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .65f)).padding(8.dp))
                }
            }
        }
        appsById[panelAppId]?.let { app ->
            AppPanel(app, onDismiss = { panelAppId = null }, onOpen = { panelAppId = null; onLaunchFrom(app, IconBounds.of(app.id)) })
        }
        appsById[selectedId]?.let { app ->
            val pinned = state.layout.indexOfShortcut(app.id) != null
            val packageName = app.packageName
            val hasWidgets = packageName.isNotEmpty() && runCatching {
                widgets.providersForPackage(packageName, app.user)
            }.getOrDefault(emptyList()).isNotEmpty()
            val openWidgetsFor: (() -> Unit)? = if (hasWidgets) {{
                val page = lastHomePage.coerceIn(0, homePages - 1)
                widgetTargetIndex = homeCellIndex(page, 0); widgetExactTarget = false
                widgetSlot = model.nextWidgetSlot(); widgetPackage = packageName
                widgetProfileSerial = app.userSerial; selectedId = null; sheet = "widgets"
            }} else null
            // iPhone-style menu next to the icon; "Edit Home Screen" starts jiggle mode for moving.
            AppContextMenu(app, onHome = pinned, hidden = app.id in state.hiddenApps,
                onDismiss = { selectedId = null }, onMove = { selectedId = null; homeEdit.start() },
                onAddOrRemove = { model.setPinned(app.id, !pinned); selectedId = null },
                onCreateFolder = { createFolderFirstId = app.id; selectedId = null },
                onWidgets = openWidgetsFor,
                onToggleHidden = { model.setHidden(app.id, app.id !in state.hiddenApps); selectedId = null },
                onInfo = { onAppInfo(app); selectedId = null })
        }
        emptyCellIndex?.let { index ->
            ModalBottomSheet(onDismissRequest = { emptyCellIndex = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), formWidth = 400.dp) {
                EmptySpaceActionSheet(onWidgets = {
                        widgetTargetIndex = index; widgetExactTarget = true; widgetSlot = model.nextWidgetSlot(); widgetPackage = null; widgetProfileSerial = null
                        emptyCellIndex = null; sheet = "widgets"
                    }, onWallpaper = { emptyCellIndex = null; sheet = "settings:wallpaper" },
                    onCustomize = { emptyCellIndex = null; sheet = "settings" }, onClose = { emptyCellIndex = null },
                    onAddPage = { emptyCellIndex = null; val page = model.addPage(); if (page >= 0) scope.launch { pager.animateScrollToPage(page) } },
                    onRemovePage = if (homeCellPage(index) == homePages - 1 && homePages > 1 && state.layout.contentPageCount < homePages) {{
                        emptyCellIndex = null; if (model.removeLastEmptyPage()) scope.launch { pager.animateScrollToPage(homePages - 2) }
                    }} else null)
            }
        }
        createFolderFirstId?.let { firstId ->
            val first = appsById[firstId]
            AlertDialog(onDismissRequest = { createFolderFirstId = null }, title = { Text("Create folder with ${first?.label ?: "app"}") },
                text = { LazyColumn(Modifier.heightIn(max = 420.dp).testTag("folder-app-picker")) {
                    items(state.apps.filter { it.id != firstId && it.available }, key = { it.id }) { second ->
                        TextButton(onClick = {
                            val preferredPage = state.layout.indexOfShortcut(firstId)?.let(::homeCellPage)
                                ?.takeIf { it >= 0 || expandedWorkspace } ?: lastHomePage.coerceIn(0, homePages - 1)
                            val blocked = state.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }
                            val targetIndex = (0 until HOME_CELLS).map { homeCellIndex(preferredPage, it) }
                                .firstOrNull { it !in blocked && state.layout.slotAt(it) in listOf(null, firstId, second.id) }
                            if (targetIndex != null) model.createFolder(firstId, second.id, targetIndex)
                            createFolderFirstId = null
                        }, modifier = Modifier.fillMaxWidth().testTag("folder-app-${second.id}")) {
                            Text(second.label, Modifier.fillMaxWidth())
                        }
                    }
                } }, confirmButton = { TextButton(onClick = { createFolderFirstId = null }) { Text(stringResource(R.string.cancel)) } })
        }
        openFolderId?.let { id ->
            state.folders.firstOrNull { it.id == id }?.let { folder ->
                val blocked = state.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }
                val destinationPages = (if (expandedWorkspace) listOf(-1) else emptyList()) + (0 until homePages)
                val homeDestinations = destinationPages.mapNotNull { destinationPage ->
                    (0 until HOME_CELLS).map { homeCellIndex(destinationPage, it) }
                        .firstOrNull { it !in blocked && state.layout.slotAt(it) == null }
                }
                FolderPanel(folder, appsById, drag, pager.currentPage, homeDestinations,
                    dockVacancies = state.dock.indices.filter { state.dock[it] == null },
                    onDismiss = { openFolderId = null }, onRename = { model.renameFolder(id, it) },
                    color = state.folderColors[id], onColor = { model.setFolderColor(id, it) },
                    onLaunch = onLaunchFrom,
                    onMoveOut = { appId, destination ->
                        if (model.removeAppFromFolder(id, appId, destination)) openFolderId = model.folder(id)?.id
                    })
            } ?: LaunchedEffect(id) { openFolderId = null }
        }
        launcherActivity.backups.preview?.let { preview ->
            LayoutRestorePreview(preview, onRestore = {
                launcherActivity.backups.applyImport(); sheet = ""
            }, onCancel = launcherActivity.backups::cancelImport)
        }
        if (launcherActivity.backups.pickerPending) AlertDialog(onDismissRequest = {},
            title = { Text(stringResource(R.string.layout_document)) },
            text = { Text(stringResource(R.string.the_system_document_picker_is_still_open)) },
            confirmButton = { TextButton(onClick = { launcherActivity.backups.resumePendingPicker() },
                modifier = Modifier.testTag("backup-picker-resume")) { Text(stringResource(R.string.resume)) } },
            dismissButton = { TextButton(onClick = launcherActivity.backups::cancelImport,
                modifier = Modifier.testTag("backup-picker-cancel")) { Text(stringResource(R.string.cancel)) } })
        if (launcherActivity.backgrounds.pickerPending && !launcherActivity.backgrounds.loading) AlertDialog(
            onDismissRequest = {}, title = { Text(stringResource(R.string.background_photo)) },
            text = { Text(stringResource(R.string.the_photo_picker_was_interrupted_resume)) },
            confirmButton = { TextButton(onClick = launcherActivity.backgrounds::choosePhoto,
                modifier = Modifier.testTag("background-picker-resume")) { Text(stringResource(R.string.resume)) } },
            dismissButton = { TextButton(onClick = launcherActivity.backgrounds::cancelPendingSelection,
                modifier = Modifier.testTag("background-picker-cancel")) { Text(stringResource(R.string.cancel)) } })
        (launcherActivity.backups.errorMessage ?: launcherActivity.backups.successMessage)?.let { message ->
            AlertDialog(onDismissRequest = launcherActivity.backups::clearMessage,
                title = { Text(if (launcherActivity.backups.errorMessage != null) "Layout backup problem" else "Layout backup") },
                text = { Text(message) }, confirmButton = { TextButton(onClick = launcherActivity.backups::clearMessage) { Text(stringResource(R.string.ok)) } })
        }
        widgets.failureMessage?.let { message ->
            AlertDialog(onDismissRequest = widgets::clearFailure, title = { Text(stringResource(R.string.widget_not_added)) },
                text = { Text(message, Modifier.testTag("widget-bind-error")) },
                confirmButton = { TextButton(onClick = widgets::clearFailure) { Text(stringResource(R.string.ok)) } })
        }
        if (widgets.pendingPlacement != null && widgets.setupStatus != null) {
            AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.finish_widget_setup)) },
                text = { Text(stringResource(R.string.the_widget_is_waiting_at_its_chosen_spot)) },
                confirmButton = { TextButton(onClick = widgets::finishPendingSetup,
                    modifier = Modifier.semantics { contentDescription = "Continue widget setup" }) { Text(stringResource(R.string.finish_setup)) } },
                dismissButton = { TextButton(onClick = { leaveTemporaryWidgetPage(); widgets.cancelPendingSetup() },
                    modifier = Modifier.semantics { contentDescription = "Cancel widget setup" }) { Text(stringResource(R.string.cancel)) } })
        }
        widgets.reconfigureWidgetId?.let {
            AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.widget_settings)) },
                text = { Text(stringResource(R.string.widget_settings_were_interrupted_resume)) },
                confirmButton = { TextButton(onClick = widgets::finishPendingReconfigure,
                    modifier = Modifier.testTag("widget-reconfigure-resume")) { Text(stringResource(R.string.resume)) } },
                dismissButton = { TextButton(onClick = widgets::cancelPendingReconfigure,
                    modifier = Modifier.testTag("widget-reconfigure-cancel")) { Text(stringResource(R.string.cancel)) } })
        }
        }
    } } }
}

@Composable
private fun ExpandedWorkspace(
    nativePager: androidx.compose.foundation.pager.PagerState,
    motion: WorkspacePageMotion,
    firstHome: Int,
    leftPageContent: @Composable (Modifier) -> Unit,
    /** iPad-style Today View kept beside Home in place of the unfolded-only page. */
    besideContent: (@Composable (Modifier) -> Unit)? = null,
    visibleHomePages: Int,
    panelWidth: Dp,
    contentHeight: Dp,
    bottomSpace: Dp,
    geometry: HomeGeometry,
    state: LauncherState,
    previewSlots: List<String?>,
    previewLeadingSlots: List<String?>,
    previewWidgetPlacements: List<WidgetPlacement>,
    appsById: Map<String, AppEntry>,
    widgets: WidgetController,
    drag: HomeDragState,
    target: DropTarget?,
    insertionTarget: DropTarget?,
    libraryQuery: String,
    onLibraryQuery: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit,
    onPinned: (String, Boolean) -> Unit,
    onTurnOnWork: (Long) -> Unit,
    onActions: (AppEntry) -> Unit,
    onWidget: (Int) -> Unit,
    onFolder: (String) -> Unit,
    onEmptyWidget: (Int) -> Unit,
    onRefresh: () -> Unit,
) {
    val density = LocalDensity.current
    val viewportWidth = motion.pageWidth
    val stride = motion.homeStride
    val initialHomeOrigin = with(density) { panelWidth.toPx() }
    val homePaneWidth = with(density) { (geometry.gridWidth + 16f).dp.toPx() }
    val stateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val visibleHomes by remember(nativePager, motion, firstHome, visibleHomePages, initialHomeOrigin, homePaneWidth) {
        derivedStateOf(structuralEqualityPolicy()) {
            val physicalPosition = nativePager.currentPage + nativePager.currentPageOffsetFraction
            val scroll = motion.offset(physicalPosition)
            val intersectingHomes = (0 until visibleHomePages).filter { page ->
                val start = initialHomeOrigin + page * stride
                start + homePaneWidth > scroll && start < scroll + viewportWidth
            }
            val nearestLogicalPage = nativePager.currentPage - firstHome
            // While Discover is current, keep the initial Home pair cached. Otherwise Home 2
            // is recreated midway through the first native exit and provider inflation can
            // block the gesture frame even though that pane began offscreen.
            val retentionAnchor = nearestLogicalPage.coerceAtLeast(0)
            (intersectingHomes + (retentionAnchor - 1..retentionAnchor + 1))
                .filter { it in 0 until visibleHomePages }.distinct().sorted()
        }
    }
    val place: Modifier.(Float) -> Modifier = { x ->
        offset {
            val physicalPosition = nativePager.currentPage + nativePager.currentPageOffsetFraction
            IntOffset((x - motion.offset(physicalPosition)).roundToInt(), 0)
        }
    }
    val showDiscover by remember(nativePager, firstHome) {
        derivedStateOf(structuralEqualityPolicy()) {
            firstHome > 0 && nativePager.currentPage + nativePager.currentPageOffsetFraction <= firstHome + .25f
        }
    }
    val leadingX = initialHomeOrigin - stride
    val showLeading by remember(nativePager, motion, firstHome, panelWidth, leadingX, homePaneWidth) {
        derivedStateOf(structuralEqualityPolicy()) {
            val physicalPosition = nativePager.currentPage + nativePager.currentPageOffsetFraction
            val scroll = motion.offset(physicalPosition)
            panelWidth.value > 0f && physicalPosition - firstHome < 1f &&
                leadingX - scroll + homePaneWidth > 0f
        }
    }
    val libraryPhysicalPage = firstHome + visibleHomePages
    val showLibrary by remember(nativePager, libraryPhysicalPage) {
        derivedStateOf(structuralEqualityPolicy()) {
            nativePager.currentPage + nativePager.currentPageOffsetFraction >= libraryPhysicalPage - 1.25f
        }
    }

    Box(Modifier.fillMaxSize().clipToBounds().testTag("expanded-workspace")) {
        if (showDiscover) {
            key("discover-pane") {
                Box(Modifier.place(-viewportWidth).fillMaxSize()) { leftPageContent(Modifier.fillMaxSize()) }
            }
        }

        if (showLeading) {
            key("expanded-leading-home") {
                Box(Modifier.place(leadingX).width((geometry.gridWidth + 16f).dp).fillMaxHeight()
                    .testTag("expanded-leading-home")) {
                    if (besideContent != null) besideContent(Modifier.fillMaxSize()) else HomePagePane(
                        -1, state, previewSlots, previewLeadingSlots, previewWidgetPlacements, appsById, geometry, contentHeight, bottomSpace,
                        widgets, drag, target, insertionTarget, showLargeWidget = true,
                        onLaunch = onLaunchFrom, onActions = onActions, onWidget = onWidget,
                        onFolder = onFolder, onEmptyWidget = onEmptyWidget, onRefresh = onRefresh,
                        modifier = Modifier,
                    )
                }
            }
        }

        visibleHomes.forEach { page ->
            key("expanded-home-$page") {
                stateHolder.SaveableStateProvider("expanded-home-$page") {
                    Box(Modifier.place(initialHomeOrigin + page * stride)
                        .width((geometry.gridWidth + 16f).dp).fillMaxHeight()) {
                        HomePagePane(
                            page, state, previewSlots, previewLeadingSlots, previewWidgetPlacements, appsById, geometry, contentHeight, bottomSpace,
                            widgets, drag, target, insertionTarget, showLargeWidget = page > 0,
                            onLaunch = onLaunchFrom, onActions = onActions, onWidget = onWidget,
                            onFolder = onFolder,
                            onEmptyWidget = onEmptyWidget,
                            onRefresh = onRefresh,
                        )
                    }
                }
            }
        }

        if (showLibrary) {
            key("library-pane") {
                Box(Modifier.place((visibleHomePages - 1) * stride + viewportWidth).fillMaxSize()) {
                    AppLibrary(state, libraryQuery, onLibraryQuery, onLaunch, onPinned,
                        onActions = onActions,
                        modifier = Modifier.fillMaxSize().padding(start = 16.dp, top = 16.dp, bottom = bottomSpace)
                            .testTag("library-page"),
                        drag = drag, page = visibleHomePages, onLaunchFrom = onLaunchFrom, onTurnOnWork = onTurnOnWork)
                }
            }
        }
    }
}

@Composable
private fun HomePagePane(
    page: Int,
    state: LauncherState,
    previewSlots: List<String?>,
    previewLeadingSlots: List<String?>,
    previewWidgetPlacements: List<WidgetPlacement>,
    appsById: Map<String, AppEntry>,
    geometry: HomeGeometry,
    contentHeight: Dp,
    bottomSpace: Dp,
    widgets: WidgetController,
    drag: HomeDragState,
    target: DropTarget?,
    insertionTarget: DropTarget?,
    showLargeWidget: Boolean,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onActions: (AppEntry) -> Unit,
    onWidget: (Int) -> Unit,
    onFolder: (String) -> Unit,
    onEmptyWidget: (Int) -> Unit = {},
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val homeScroll = rememberScrollState()
    var paneBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val pageStart = homeCellIndex(page, 0)
    val backgroundTarget = (pageStart until pageStart + HOME_CELLS).firstOrNull { index ->
        state.layout.slotAt(index) == null && state.widgetPlacements.none { index in it.coveredIndices() }
    } ?: pageStart
    val verticalEdge = with(LocalDensity.current) { 42.dp.toPx() }
    LaunchedEffect(drag.active, page, paneBounds) {
        while (drag.active) {
            val pointer = drag.pointer
            val amount = when {
                !paneBounds.contains(pointer) -> 0f
                pointer.y < paneBounds.top + verticalEdge && homeScroll.canScrollBackward -> -18f
                pointer.y > paneBounds.bottom - verticalEdge && homeScroll.canScrollForward -> 18f
                else -> 0f
            }
            if (amount != 0f) homeScroll.scrollBy(amount)
            delay(16)
        }
    }
    val edit = LocalHomeEdit.current
    val context = LocalContext.current
    val doubleTapAction = FolioAction.entries.firstOrNull { it.name == state.triggerActions[FolioTrigger.DOUBLE_TAP.name] } ?: FolioAction.NONE
    Box(modifier.testTag("home-page-$page")
        // Jiggle mode: a tap on empty space (not on an icon, which handles its own taps) finishes editing.
        .pointerInput(edit.active, doubleTapAction, backgroundTarget, drag.active) {
            // Like iPhone, a long-press anywhere on empty Home (below the grid too) starts jiggle mode,
            // and a second one opens the Home options.
            val longPress: (Offset) -> Unit = { if (!drag.active) onEmptyWidget(backgroundTarget) }
            when {
                edit.active -> detectTapGestures(onTap = { edit.stop() }, onLongPress = longPress)
                // Activator-style double-tap on empty Home.
                doubleTapAction != FolioAction.NONE -> detectTapGestures(onDoubleTap = { FolioActions.run(context, doubleTapAction) }, onLongPress = longPress)
                else -> detectTapGestures(onLongPress = longPress)
            }
        }
        .semantics {
            onLongClick("Home options") {
                if (!drag.active) onEmptyWidget(backgroundTarget)
                !drag.active
            }
        }
        .onGloballyPositioned { paneBounds = it.boundsInRoot() }
        .width((geometry.gridWidth + 16f).dp)
        .height((contentHeight - bottomSpace).coerceAtLeast(0.dp))) {
        Box(Modifier.width(16.dp).fillMaxHeight().testTag("home-options-margin-$page")
            .pointerInput(backgroundTarget, drag.active) {
                detectTapGestures(onLongPress = {
                    if (!drag.active) onEmptyWidget(backgroundTarget)
                })
            })
        // Jiggle mode: room under the + / Edit / Done bar so the top row's remove buttons never crowd it.
        // Frozen while something is held: sliding the grid under a finger would change where it drops.
        var roomWanted by remember { mutableStateOf(edit.active) }
        if (!drag.active) roomWanted = edit.active
        val editRoom by animateDpAsState(if (roomWanted) 44.dp else 0.dp, label = "jiggle room")
        Column(Modifier.offset(x = 16.dp).width(geometry.gridWidth.dp).fillMaxHeight()
            .verticalScroll(homeScroll).padding(top = geometry.contentTop.dp + editRoom, bottom = 8.dp)) {
            SharedHomeGrid(page, state.homeSlots, state.leadingSlots, previewSlots, previewLeadingSlots, previewWidgetPlacements,
                appsById, geometry, state.labels, widgets, drag, target,
                folders = state.folders, onLaunch = onLaunch, onActions = onActions, onWidget = onWidget,
                onFolder = onFolder, onEmptyWidget = onEmptyWidget)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            if (state.error != null) Text(state.error, color = Color.White,
                modifier = Modifier.clickable(onClick = onRefresh).padding(12.dp))
        }
    }
}

@Composable
private fun CircleControl(icon: ImageVector, label: String, tag: String, visualSize: Dp, action: () -> Unit) {
    IconButton(onClick = action, modifier = Modifier.size(visualSize.coerceAtLeast(48.dp)).testTag(tag)) {
        Box(Modifier.size(visualSize).testTag("$tag-visual").background(Glass.copy(alpha = .22f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = .25f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SharedHomeGrid(
    page: Int,
    savedSlots: List<String?>,
    savedLeadingSlots: List<String?>,
    previewSlots: List<String?>,
    previewLeadingSlots: List<String?>,
    widgetPlacements: List<WidgetPlacement>,
    appsById: Map<String, AppEntry>,
    geometry: HomeGeometry,
    labels: Boolean,
    widgets: WidgetController,
    drag: HomeDragState,
    target: DropTarget?,
    folders: List<FolderEntry>,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onActions: (AppEntry) -> Unit,
    onWidget: (Int) -> Unit,
    onFolder: (String) -> Unit,
    onEmptyWidget: (Int) -> Unit,
) {
    val rowHeight = geometry.rowHeight
    val iconSize = geometry.iconSize
    val edit = LocalHomeEdit.current
    val pageStart = homeCellIndex(page, 0)
    val pageRange = pageStart until pageStart + HOME_CELLS
    fun savedAt(index: Int) = if (page == -1) savedLeadingSlots.getOrNull(homeCellLocal(index)) else savedSlots.getOrNull(index)
    fun previewAt(index: Int) = if (page == -1) previewLeadingSlots.getOrNull(homeCellLocal(index)) else previewSlots.getOrNull(index)
    fun savedIndexOf(id: String) = if (page == -1) savedLeadingSlots.indexOf(id).takeIf { it >= 0 }?.let { homeCellIndex(-1, it) }
        else savedSlots.indexOf(id).takeIf { it >= 0 }
    fun previewIndexOf(id: String) = if (page == -1) previewLeadingSlots.indexOf(id).takeIf { it >= 0 }?.let { homeCellIndex(-1, it) }
        else previewSlots.indexOf(id).takeIf { it >= 0 }
    val draggedId = drag.source?.appId
    val homeTarget = (target as? DropTarget.Home)?.index
    val source = drag.source?.target as? DropTarget.Home
    val draggedPreviewIndex = draggedId?.let(::previewIndexOf) ?: -1
    val hiddenIndex = when {
        !drag.active || !drag.moved -> null
        homeTarget != null -> draggedPreviewIndex.takeIf { it >= 0 }
        source != null && target !is DropTarget.Dock -> draggedPreviewIndex.takeIf { it >= 0 }
        else -> null
    }
    val dimDragged = drag.active && !drag.moved && source != null
    val pending = widgets.pendingPlacement?.takeIf { it.page == page }
    val pendingIsReplacement = pending != null && widgetPlacements.any { it.slot == pending.slot }
    val pageWidgets = widgetPlacements.filter { it.page == page } + listOfNotNull(pending?.takeUnless { pendingIsReplacement })
    val renderedRows = maxOf(GRID_ROWS, pageWidgets.maxOfOrNull { it.row + it.spanY } ?: GRID_ROWS)
    // Stacked, or two columns side by side in a short, wide window (see HomeCellLayout).
    val cells = remember(geometry, pageWidgets.map { it.row to it.spanY }) { HomeCellLayout.forPage(geometry, pageWidgets.map { it.row to it.spanY }) }
    fun rowTop(row: Int) = cells.y(row)
    BoxWithConstraints(Modifier.fillMaxWidth().height(cells.height(renderedRows).dp)) {
        val density = LocalDensity.current
        val cellWidth = cells.cellWidth.dp
        fun cellX(column: Int, row: Int) = cells.x(column, row).dp

        repeat(HOME_CELLS) { localIndex ->
            val globalIndex = pageStart + localIndex
            val cell = DropTarget.Home(globalIndex)
            val savedId = savedAt(globalIndex)
            val savedApp = appsById[savedId]
            val savedFolder = folders.firstOrNull { it.id == savedId }
            val previewId = previewAt(globalIndex)
            val highlighted = drag.active && target == cell
            val gap = hiddenIndex == globalIndex
            val row = localIndex / GRID_COLUMNS
            val cellHeight = cells.spanHeight(row, 1)
            Box(Modifier.offset(x = cellX(localIndex % GRID_COLUMNS, row), y = rowTop(row).dp)
                .width(cellWidth).height(cellHeight.dp).testTag("home-cell-$globalIndex")
                .dropRegion(drag, cell, savedApp?.id ?: savedFolder?.id, page)
                .combinedClickable(onClick = { if (savedFolder != null) onFolder(savedFolder.id) else if (edit.active) edit.stop() },
                    onLongClick = { if (savedId == null && !drag.active) onEmptyWidget(globalIndex) })
                .background(if (highlighted) Glass.copy(alpha = .25f) else Color.Transparent, RoundedCornerShape(16.dp))
                .border(if (highlighted) 2.dp else 0.dp,
                    if (highlighted) Color.White.copy(alpha = .8f) else Color.Transparent, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.TopCenter) {
                if (drag.active && drag.source?.appId != null && (gap || previewId == null)) Box(
                    Modifier.size(iconSize.dp).testTag(if (gap) "drag-gap-home-$globalIndex" else "empty-home-slot-$globalIndex")
                        .background(Glass.copy(alpha = if (gap) .16f else .08f), RoundedCornerShape(18.dp))
                        .border(if (gap) 2.dp else 1.dp, Color.White.copy(alpha = if (gap) .55f else .3f), RoundedCornerShape(18.dp)))
            }
        }

        val ids = (if (page == -1) savedLeadingSlots + previewLeadingSlots
            else savedSlots.slicePage(pageRange) + previewSlots.slicePage(pageRange)).filterNotNull().distinct()
        ids.forEach { id ->
            val savedIndex = savedIndexOf(id) ?: -1
            val previewIndex = previewIndexOf(id) ?: -1
            val renderIndex = previewIndex.takeIf { it in pageRange } ?: savedIndex.takeIf { it in pageRange } ?: return@forEach
            val app = appsById[id] ?: return@forEach
            key(id) {
                val localIndex = renderIndex - pageStart
                val row = localIndex / GRID_COLUMNS
                // Slide only while rearranging; a new screen size (folding) must place icons immediately.
                val animatedOffset by animateIntOffsetAsState(
                    with(density) { IntOffset(cellX(localIndex % GRID_COLUMNS, row).toPx().roundToInt(), rowTop(row).dp.toPx().roundToInt()) },
                    animationSpec = if (drag.active || edit.active) androidx.compose.animation.core.spring(visibilityThreshold = IntOffset(1, 1))
                        else androidx.compose.animation.core.snap(),
                    label = "home insertion $id",
                )
                val visible = previewIndex in pageRange && renderIndex != hiddenIndex
                val opacity by animateFloatAsState(
                    if (dimDragged && id == draggedId) .28f else 1f,
                    label = "home insertion visibility $id",
                )
                Box(Modifier.offset { animatedOffset }.width(cellWidth).height(rowHeight.dp)
                    .alpha(opacity).testTag("home-app-$id"), contentAlignment = Alignment.TopCenter) {
                    if (visible) AppTile(app, iconSize, labels,
                        onClick = { if (!edit.active) onLaunch(app, it) }, onLongClick = { onActions(app) },
                        onRemove = if (edit.active && savedIndex != -1) {{ edit.onRemove(DropTarget.Home(savedIndex)) }} else null)
                }
            }
        }
        folders.forEach { folder ->
            val savedIndex = savedIndexOf(folder.id) ?: -1
            val previewIndex = previewIndexOf(folder.id) ?: -1
            val renderIndex = previewIndex.takeIf { it in pageRange } ?: savedIndex.takeIf { it in pageRange } ?: return@forEach
            val localIndex = renderIndex - pageStart
            val row = localIndex / GRID_COLUMNS
            val x = cellX(localIndex % GRID_COLUMNS, row)
            val y = rowTop(row).dp
            FolderTile(folder, appsById, iconSize, labels, drag, page,
                Modifier.offset(x = x, y = y).width(cellWidth).height(rowHeight.dp)
                    .testTag("home-folder-${folder.id}"), onClick = { onFolder(folder.id) })
        }
        pageWidgets.forEach { placement ->
            key("widget-${placement.slot}") {
                val x = cellX(placement.column, placement.row) + 5.dp
                val width = (cellWidth * placement.spanX - 10.dp).coerceAtLeast(1.dp)
                val y = rowTop(placement.row)
                val height = (cells.spanHeight(placement.row, placement.spanY) - 18f).coerceAtLeast(48f)
                if (placement == pending) Surface(Modifier.offset(x = x, y = y.dp).width(width).height(height.dp)
                    .testTag("widget-pending-${placement.slot}").semantics(mergeDescendants = true) {
                        contentDescription = "Pending ${widgets.pendingProvider?.shortClassName ?: "widget"}"
                    }, color = Glass.copy(alpha = .72f),
                    shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(2.dp, Color.White)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.finish_widget_setup), color = Ink)
                    }
                } else MovableWidget(placement.id, placement.slot, widgets, drag, target,
                    Modifier.offset(x = x, y = y.dp).width(width).height(height.dp), page = page) { onWidget(placement.slot) }
            }
        }
    }
}

@Composable
private fun DockAppColumn(
    savedDock: List<String?>,
    previewDock: List<String?>,
    appsById: Map<String, AppEntry>,
    rowHeight: Float,
    iconSize: Float,
    drag: HomeDragState,
    target: DropTarget?,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onChoose: (Int) -> Unit,
    magnify: Boolean = false,
    leftHanded: Boolean = false,
) {
    val draggedId = drag.source?.appId
    val edit = LocalHomeEdit.current
    val dockTarget = (target as? DropTarget.Dock)?.index
    val source = drag.source?.target as? DropTarget.Dock
    val draggedPreviewIndex = previewDock.indexOf(draggedId)
    val hiddenIndex = when {
        !drag.active || !drag.moved -> null
        dockTarget != null -> draggedPreviewIndex.takeIf { it >= 0 }
        source != null && target !is DropTarget.Home -> draggedPreviewIndex.takeIf { it >= 0 }
        else -> null
    }
    val dimDragged = drag.active && !drag.moved && source != null
    val launchBounds = remember(savedDock.size) { List(savedDock.size) { android.graphics.Rect() } }
    val interactions = remember(savedDock.size) { List(savedDock.size) { MutableInteractionSource() } }
    val slotScales = savedDock.indices.map { index ->
        val pressed by interactions[index].collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) .92f else 1f, label = "dock press $index")
        scale
    }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { rowHeight.dp.toPx() }
    // Harbor-style magnification: icons swell under the finger as it slides along the dock (touches pass through).
    var touchY by remember { mutableStateOf<Float?>(null) }
    val haptic = LocalHapticFeedback.current
    Box(Modifier.fillMaxWidth().height((rowHeight * savedDock.size).dp).then(if (!magnify) Modifier else Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            touchY = down.position.y
            var lastRow = (down.position.y / rowHeightPx).toInt()
            while (true) {
                val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                touchY = change.position.y
                val row = (change.position.y / rowHeightPx).toInt()
                if (row != lastRow) { lastRow = row; haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) }
            }
            touchY = null
        }
    })) {
        savedDock.indices.forEach { index ->
            val cell = DropTarget.Dock(index)
            val savedApp = appsById[savedDock[index]]
            val previewId = previewDock.getOrNull(index)
            val highlighted = drag.active && target == cell
            val gap = hiddenIndex == index
            Box(Modifier.fillMaxWidth().height(rowHeight.dp).offset(y = (rowHeight * index).dp)
                .background(if (highlighted) Color.White.copy(alpha = .3f) else Color.Transparent, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center) {
                when {
                    gap -> Box(Modifier.size(iconSize.dp).testTag("drag-gap-dock-$index")
                        .background(Glass.copy(alpha = .16f), RoundedCornerShape(14.dp))
                        .border(2.dp, Color.White.copy(alpha = .55f), RoundedCornerShape(14.dp)))
                    previewId == null -> Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(rowHeight.dp).offset(y = (rowHeight * index).dp)
                .testTag("dock-slot-$index").dropRegion(drag, cell, savedApp?.id)
                .semantics(mergeDescendants = true) { contentDescription = savedApp?.label ?: "Choose dock app ${index + 1}" }
                .combinedClickable(interactionSource = interactions[index], indication = LocalIndication.current, role = Role.Button, onClick = {
                    if (savedApp != null) { if (!edit.active) onLaunch(savedApp, launchBounds[index]) } else onChoose(index)
                }, onLongClick = null)
                .semantics { onLongClick("Choose dock app") { onChoose(index); true } })
        }

        val ids = (savedDock + previewDock).filterNotNull().distinct()
        ids.forEach { id ->
            val savedIndex = savedDock.indexOf(id)
            val previewIndex = previewDock.indexOf(id)
            val renderIndex = previewIndex.takeIf { it >= 0 } ?: savedIndex.takeIf { it >= 0 } ?: return@forEach
            val app = appsById[id] ?: return@forEach
            key(id) {
                val animatedOffset by animateIntOffsetAsState(
                    IntOffset(0, (renderIndex * rowHeightPx).roundToInt()),
                    animationSpec = if (drag.active) androidx.compose.animation.core.spring(visibilityThreshold = IntOffset(1, 1))
                        else androidx.compose.animation.core.snap(), label = "dock insertion $id")
                val visible = previewIndex >= 0 && renderIndex != hiddenIndex
                val opacity by animateFloatAsState(
                    if (!visible) 0f else if (dimDragged && id == draggedId) .28f else 1f,
                    label = "dock insertion visibility $id",
                )
                Box(Modifier.offset { animatedOffset }.fillMaxWidth().height(rowHeight.dp).alpha(opacity)
                    .testTag("dock-app-$id"), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(iconSize.dp).testTag("dock-icon-$id")
                        .onGloballyPositioned { if (savedIndex >= 0) { launchBounds[savedIndex].set(it.boundsInWindow().toAndroidBounds()); IconBounds.update(id, launchBounds[savedIndex]) } }
                        .jiggle(id)) {
                        val magnification by animateFloatAsState(touchY?.let { y ->
                            val center = (renderIndex + .5f) * rowHeightPx
                            1f + .38f * (1f - kotlin.math.abs(center - y) / (rowHeightPx * 1.5f)).coerceAtLeast(0f)
                        } ?: 1f, androidx.compose.animation.core.spring(dampingRatio = .75f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium), label = "dock magnify $id")
                        AppIcon(app, null, Modifier.fillMaxSize().graphicsLayer {
                            val s = slotScales[renderIndex] * magnification; scaleX = s; scaleY = s
                            // Grow toward the screen, away from the edge the dock sits on.
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (leftHanded) 0f else 1f, .5f)
                        }, shape = RoundedCornerShape(11.dp))
                        if (edit.active && savedIndex >= 0) JiggleRemoveButton("Remove ${app.label} from dock") { edit.onRemove(DropTarget.Dock(savedIndex)) }
                    }
                }
            }
        }
    }
}

private fun <T> List<T>.slicePage(range: IntRange): List<T> =
    if (isEmpty() || range.first >= size) emptyList() else subList(range.first, minOf(range.last + 1, size))

@Composable
private fun FolderTile(folder: FolderEntry, apps: Map<String, AppEntry>, size: Float, labels: Boolean,
    drag: HomeDragState, page: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).semantics(mergeDescendants = true) {
        contentDescription = "Folder ${folder.title}, ${folder.appIds.size} apps"
    }, horizontalAlignment = Alignment.CenterHorizontally) {
        val tint = LocalFolderColors.current[folder.id]?.let { Color(it) }
        val bounds = remember { android.graphics.Rect() }
        Box(Modifier.size(size.dp)
            .dropRegion(drag, DropTarget.Folder(folder.id), page = page, folderId = folder.id)
            .onGloballyPositioned { bounds.set(it.boundsInWindow().toAndroidBounds()); IconBounds.update(folder.id, bounds) }
            .jiggle(folder.id).clip(RoundedCornerShape((size * .24f).dp))
            .background(tint?.copy(alpha = .78f) ?: Glass.copy(alpha = .72f)).border(1.dp, Color.White.copy(alpha = .55f), RoundedCornerShape((size * .24f).dp))
            .testTag("folder-drop-${folder.id}")) {
            folder.appIds.take(4).forEachIndexed { index, id ->
                apps[id]?.let { app ->
                    AppIcon(app, null, Modifier.align(when (index) {
                        0 -> Alignment.TopStart; 1 -> Alignment.TopEnd; 2 -> Alignment.BottomStart; else -> Alignment.BottomEnd
                    }).padding(5.dp).size((size * .38f).dp).clip(RoundedCornerShape(6.dp)))
                }
            }
        }
        if (labels) Text(folder.title, color = LocalHomeInk.current.primary, fontSize = 11.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun AppTile(app: AppEntry, size: Float, labels: Boolean, modifier: Modifier = Modifier, onClick: (android.graphics.Rect) -> Unit, onLongClick: () -> Unit,
    onRemove: (() -> Unit)? = null) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .88f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = .55f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium), label = "app press")
    // No size animation: after folding, the cover's icons must appear at their own size on the first frame.
    val iconSize = size.dp
    val bounds = remember { android.graphics.Rect() }
    val openPanel = LocalAppPanel.current
    Column(modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) { contentDescription = app.label }
        .swipeUpForPanel(openPanel?.let { { it(app) } })
        .clickable(interactionSource = interaction, indication = null,
            role = Role.Button, onClick = { onClick(bounds) })
        .semantics { onLongClick("App options") { onLongClick(); true } }.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        // Bounds are read outside the wiggle layer so jiggling doesn't report a new position every frame.
        Box(Modifier.size(iconSize).onGloballyPositioned { bounds.set(it.boundsInWindow().toAndroidBounds()); IconBounds.update(app.id, bounds) }
            .jiggle(app.id)) {
            AppIcon(app, null, Modifier.fillMaxSize()
                .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (pressed) .82f else 1f }, shape = RoundedCornerShape((size * .24f).dp))
            if (onRemove != null) JiggleRemoveButton("Remove ${app.label} from Home", onRemove)
        }
        val ink = LocalHomeInk.current
        if (labels) Text(app.label, color = ink.primary, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = TextStyle(shadow = ink.labelShadow), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
        color = Glass.copy(alpha = .26f), shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(1.dp, RailBorder)) {
        // Like iOS widgets, the whole card scales with its size, so a narrower column (the Today View beside
        // Home in portrait) shrinks the text instead of clipping it.
        BoxWithConstraints {
            val scale = (minOf(maxWidth, maxHeight) / 150.dp).coerceIn(.6f, 1f)
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(density.density * scale, density.fontScale)) {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween, content = content)
            }
        }
    }
}

@Composable
private fun currentTime(): LocalDateTime {
    val tick by rememberMinuteTick()
    return remember(tick) { LocalDateTime.now() }
}

@Composable
private fun ClockCard(onClick: () -> Unit) {
    val time = currentTime()
    val format = if (android.text.format.DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm"
    GlassCard(onClick = onClick) {
        Text(stringResource(R.string.local_time), color = LocalHomeInk.current.secondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .6.sp,
            modifier = Modifier.semantics { contentDescription = "Clock widget; tap to replace" })
        Text(time.format(DateTimeFormatter.ofPattern(format)), color = LocalHomeInk.current.primary, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
        Text(time.format(DateTimeFormatter.ofPattern(if (format == "HH:mm") "EEE" else "a · EEE")), color = LocalHomeInk.current.secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DateCard(onClick: () -> Unit) {
    val date = currentTime()
    GlassCard(onClick = onClick) {
        Text(date.format(DateTimeFormatter.ofPattern("EEEE")).uppercase(), color = LocalHomeInk.current.secondary, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = .6.sp, maxLines = 1)
        Text(date.dayOfMonth.toString(), color = LocalHomeInk.current.primary, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, lineHeight = 46.sp)
        Text(date.format(DateTimeFormatter.ofPattern("MMMM")), color = LocalHomeInk.current.secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ExpandedCard(onClick: () -> Unit) {
    val date = currentTime()
    GlassCard(onClick = onClick) {
        Column {
            Text(date.format(DateTimeFormatter.ofPattern("EEEE")), color = LocalHomeInk.current.primary, fontSize = 22.sp)
            Text(date.format(DateTimeFormatter.ofPattern("MMMM d")), color = LocalHomeInk.current.secondary, fontSize = 16.sp)
        }
        Column {
            Icon(Icons.Rounded.Widgets, null, tint = LocalHomeInk.current.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.a_little_more_room), color = LocalHomeInk.current.primary, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.add_a_calendar_photos_or_another_widget), color = LocalHomeInk.current.secondary, fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onClick) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.add_widget)) }
        }
    }
}

@Composable
private fun WidgetSlot(id: Int, slot: Int, controller: WidgetController, modifier: Modifier, onAdd: () -> Unit, fallback: @Composable () -> Unit) {
    var restoreMessage by remember(slot) { mutableStateOf<String?>(null) }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(24.dp)).testTag("widget-slot-$slot")) {
        val displayedContentSize = WidgetContentSize(maxWidth.value, maxHeight.value)
        if (id == NEEDS_BINDING_WIDGET) {
            val restore = controller.restoreDescriptor(slot)
            Surface(Modifier.fillMaxSize().testTag("widget-restore-$slot"), color = Glass.copy(alpha = .88f),
                shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = .7f))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(restore?.title ?: "Saved widget", color = Ink, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Text(restore?.profileLabel ?: "Unavailable profile", color = Ink.copy(alpha = .72f),
                        style = MaterialTheme.typography.bodySmall)
                    restoreMessage?.let { Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
                    Row {
                        TextButton(onClick = {
                            if (!controller.rebindRestoredWidget(slot, contentSize = displayedContentSize))
                                restoreMessage = "That provider or profile isn’t available. Choose a replacement."
                        },
                            modifier = Modifier.testTag("widget-restore-reconnect-$slot")) { Text(stringResource(R.string.reconnect)) }
                        TextButton(onClick = onAdd, modifier = Modifier.testTag("widget-restore-replace-$slot")) { Text(stringResource(R.string.replace)) }
                    }
                }
            }
            return@BoxWithConstraints
        }
        val info = remember(id) { if (id >= 0) controller.manager.getAppWidgetInfo(id) else null }
        if (info == null) fallback()
        else {
            key(id) {
                AndroidView(factory = { context -> controller.host.createView(context, id, info) },
                    modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private fun widgetLabel(id: Int, controller: WidgetController) = when (id) {
    CLOCK_WIDGET -> "Clock"
    DATE_WIDGET -> "Date"
    INFO_WIDGET -> "Widget panel"
    EMPTY_WIDGET -> "Add widget"
    else -> controller.label(id)
}

@Composable
private fun MovableWidget(id: Int, slot: Int, controller: WidgetController, drag: HomeDragState,
    target: DropTarget?, modifier: Modifier, page: Int, onAdd: () -> Unit) {
    val cell = DropTarget.Widget(slot)
    val edit = LocalHomeEdit.current
    // Built-in cards wiggle; provider widgets (Android views) only get the remove button, since moving
    // a hosted view every frame would re-lay it out constantly.
    val cards = WidgetStacks.cards(id, LocalWidgetStacks.current[slot])
    Box(modifier.dropRegion(drag, cell, page = page, widgetId = id)) {
        val chrome = Modifier.fillMaxSize().then(if (id < 0) Modifier.jiggle("widget-$slot", .35f) else Modifier)
            .alpha(if (drag.source?.target == cell) .3f else 1f)
            .border(if (drag.active && target == cell) 2.dp else 0.dp,
                if (drag.active && target == cell) Color.White else Color.Transparent, RoundedCornerShape(24.dp))
            .semantics { onLongClick("Move or replace widget") { onAdd(); true } }
        if (cards.size > 1) SmartStack(cards, slot, controller, chrome, onAdd)
        else WidgetSlot(id, slot, controller, chrome, onAdd) { BuiltinWidgetCard(id, slot, onAdd) }
    if (edit.active && id != EMPTY_WIDGET && id != INFO_WIDGET) JiggleRemoveButton("Remove widget") { edit.onRemove(cell) }
    }
}

@Composable
internal fun BuiltinWidgetCard(id: Int, slot: Int, onAdd: () -> Unit) {
    when (id) {
        CLOCK_WIDGET -> ClockCard(onAdd)
        DATE_WIDGET -> DateCard(onAdd)
        INFO_WIDGET -> if (slot % 3 == 2) ExpandedCard(onAdd) else GlassCard(onClick = onAdd) {
            Icon(Icons.Rounded.Widgets, null, tint = Color.White, modifier = Modifier.size(28.dp))
            Text(stringResource(R.string.your_widgets), color = Color.White, fontSize = 15.sp, maxLines = 1)
            Text(stringResource(R.string.tap_to_choose), color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
        }
        else -> Surface(Modifier.fillMaxSize().clickable(onClick = onAdd), color = Glass.copy(alpha = .18f),
            shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .25f))) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Add, null, tint = Color.White)
                Text(if (id >= 0) "Widget unavailable" else "Add widget", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

/**
 * iOS Smart Stack: swipe up or down between the widgets in one spot. Dots on the side show while flipping;
 * with Smart Rotate on, the stack moves to its next widget every 30 minutes while Home is open.
 */
@Composable
private fun SmartStack(cards: List<Int>, slot: Int, controller: WidgetController, modifier: Modifier, onAdd: () -> Unit) {
    val pager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { cards.size })
    val rotate = LocalStackRotate.current
    val haptic = LocalHapticFeedback.current
    var lastSettled by remember { mutableIntStateOf(0) }
    LaunchedEffect(pager.settledPage) {
        if (pager.settledPage != lastSettled) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        lastSettled = pager.settledPage
    }
    LaunchedEffect(rotate, cards.size) {
        if (rotate) while (true) {
            delay(30 * 60_000L)
            if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % cards.size)
        }
    }
    var dotsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(pager.isScrollInProgress) { if (pager.isScrollInProgress) dotsVisible = true else { delay(1_200); dotsVisible = false } }
    Box(modifier) {
        androidx.compose.foundation.pager.VerticalPager(pager, Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)),
            key = { cards[it] }, beyondViewportPageCount = 0) { page ->
            val card = cards[page]
            WidgetSlot(card, slot, controller, Modifier.fillMaxSize(), onAdd) { BuiltinWidgetCard(card, slot, onAdd) }
        }
        val dotsAlpha by animateFloatAsState(if (dotsVisible) 1f else 0f, label = "stack dots")
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 5.dp).alpha(dotsAlpha)
            .background(Color.Black.copy(alpha = .28f), RoundedCornerShape(50)).padding(horizontal = 3.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(cards.size) { index ->
                Box(Modifier.size(5.dp).background(Color.White.copy(alpha = if (index == pager.currentPage) 1f else .4f), CircleShape))
            }
        }
    }
}

@Composable
private fun AppPicker(apps: List<AppEntry>, dockSlot: Int?, onSelect: (AppEntry) -> Unit, onClear: () -> Unit,
    onLongClick: (AppEntry) -> Unit, canSelect: (AppEntry) -> Boolean = { true }, blockedHint: String? = null) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) { apps.filter { it.label.contains(query.trim(), ignoreCase = true) } }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).padding(horizontal = 20.dp).imePadding()) {
        Text(if (dockSlot == null) "Your Apps" else "Dock Position ${dockSlot + 1}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        IosSearchField(query, { query = it }, stringResource(R.string.search_apps), Modifier.padding(vertical = 12.dp), fieldModifier = Modifier.testTag("search-field"))
        if (dockSlot != null) SheetGroup(Modifier.padding(bottom = 8.dp)) { IosActionRow(stringResource(R.string.leave_this_position_empty), destructive = true, onClick = onClear) }
        if (blockedHint != null) Text(blockedHint, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp).testTag("dock-full-guidance"))
        LazyColumn(Modifier.weight(1f)) {
            if (filtered.isEmpty()) item { Text(stringResource(R.string.no_apps_found), Modifier.padding(vertical = 24.dp)) }
            items(filtered, key = { it.id }) { app ->
                val enabled = canSelect(app)
                Row(Modifier.fillMaxWidth().testTag("picker-app-${app.id}")
                    .combinedClickable(enabled = enabled, onClick = { onSelect(app) }, onLongClick = { onLongClick(app) })
                    .alpha(if (enabled) 1f else .45f)
                    .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app, null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
                    Text(app.label, Modifier.padding(start = 16.dp).weight(1f), maxLines = 2)
                    if (dockSlot != null && enabled) Icon(Icons.Rounded.AddCircle, "Choose ${app.label}", tint = Color(0xFF0A84FF))
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(state: LauncherState, initiallyWide: Boolean, model: LauncherModel, isDefaultHome: Boolean,
    onMakeDefault: () -> Unit, onClose: () -> Unit, onEditPins: () -> Unit, onWidget: (Int) -> Unit,
    onAddWidget: (Int) -> Unit, onRemoveWidget: (Int) -> Unit, onWallpaperPreview: () -> Unit,
    onExportLayout: () -> Unit, onImportLayout: () -> Unit,
    appearance: AppearanceState, onAppearanceMode: (AppearanceMode) -> Unit,
    onAppearanceManual: (String, Double, Double) -> Unit, onAppearanceDeviceLocation: () -> Unit,
    onAppearanceClear: () -> Unit,
    backgrounds: LauncherBackgroundController,
    homePage: Int = 0) {
    var wide by rememberSaveable { mutableStateOf(initiallyWide) }
    val p = if (wide) state.expanded else state.compact
    Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.make_it_yours), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close customization") }
        }
        Button(onClick = onMakeDefault, modifier = Modifier.fillMaxWidth().testTag("default-home-settings")) {
            Text(if (isDefaultHome) "Change home app" else "Set as home app")
        }
        TextButton(onClick = onEditPins, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.choose_home_apps_2)) }
        if (state.canUndoEdit) TextButton(onClick = { model.undoEdit(); onClose() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.undo_last_layout_change))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!wide, { wide = false }, label = { Text(stringResource(R.string.cover_compact)) })
            FilterChip(wide, { wide = true }, label = { Text(stringResource(R.string.inner_expanded)) })
        }
        SettingSlider("App icon size", "${p.iconSize.toInt()} dp", p.iconSize, 40f..68f) { model.setPreset(wide, p.copy(iconSize = it)) }
        SettingSlider("Space between rows", "${p.rowGap.toInt()} dp", p.rowGap, 0f..28f) { model.setPreset(wide, p.copy(rowGap = it)) }
        SettingSlider("Dock width", "${p.dockWidth.toInt()} dp", p.dockWidth, 56f..84f) { model.setPreset(wide, p.copy(dockWidth = it)) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.align_dock_with_app_rows), Modifier.weight(1f))
            Switch(p.dockAlignToGrid, { model.setPreset(wide, p.copy(dockAlignToGrid = it)) })
        }
        if (!p.dockAlignToGrid) SettingSlider("Dock height on screen", "${(p.dockPosition * 100).toInt()}%", p.dockPosition, .25f.. .75f) { model.setPreset(wide, p.copy(dockPosition = it)) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.show_app_names), Modifier.weight(1f)); Switch(state.labels, model::setLabels, Modifier.testTag("label-switch"))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.status_at_upper_right), Modifier.weight(1f)); Switch(state.verticalStatus, model::setVerticalStatus, Modifier.testTag("status-switch"))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.search_button_opens_google), Modifier.weight(1f))
            Switch(state.googleSearch, model::setGoogleSearch, Modifier.testTag("google-search-switch"))
        }
        Text(stringResource(R.string.opens_google_s_search_screen_all_apps_ke),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { model.setPreset(wide, LayoutPreset()) }) { Text(stringResource(R.string.reset_this_layout)) }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        TextButton(onClick = onWallpaperPreview, modifier = Modifier.fillMaxWidth().testTag("wallpaper-preview")) {
            Icon(Icons.Rounded.Wallpaper, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.apply_matching_wallpaper))
        }
        Text(stringResource(R.string.preview_the_current_launcher_background), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.launcher_background), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Button(onClick = backgrounds::choosePhoto, enabled = !backgrounds.loading,
            modifier = Modifier.fillMaxWidth().testTag("background-choose")) { Text(stringResource(R.string.choose_background_photo)) }
        if (backgrounds.photoSelected) OutlinedButton(onClick = backgrounds::reset,
            modifier = Modifier.fillMaxWidth().testTag("background-reset")) { Text(stringResource(R.string.reset_to_folio_dunes)) }
        if (backgrounds.loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("background-loading"))
        (backgrounds.errorMessage ?: backgrounds.successMessage)?.let { message ->
            TextButton(onClick = backgrounds::clearMessage, Modifier.fillMaxWidth().testTag("background-message")) { Text(message) }
        }
        Text(stringResource(R.string.the_selected_photo_stays_on_this_device),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        AppearanceSettings(appearance, onAppearanceMode, onAppearanceManual, onAppearanceDeviceLocation, onAppearanceClear)
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text(stringResource(R.string.layout_backup), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExportLayout, modifier = Modifier.weight(1f).testTag("layout-export")) { Text(stringResource(R.string.save)) }
            OutlinedButton(onClick = onImportLayout, modifier = Modifier.weight(1f).testTag("layout-import")) { Text(stringResource(R.string.restore)) }
        }
        Text(stringResource(R.string.restore_always_shows_a_review_before_cha), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text("Widgets · Page ${homePage + 1}", style = MaterialTheme.typography.titleMedium)
        state.widgetPlacements.filter { it.page == homePage }.forEach { placement ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${placement.spanX} × ${placement.spanY} widget · row ${placement.row + 1}", Modifier.weight(1f))
                IconButton(onClick = { onRemoveWidget(placement.slot) },
                    modifier = Modifier.semantics { contentDescription = "Remove widget" }) {
                    Icon(Icons.Rounded.DeleteOutline, null)
                }
                TextButton(onClick = { onWidget(placement.slot) }) { Text(stringResource(R.string.replace)) }
            }
        }
        if (wide) state.widgetPlacements.filter { it.page == -1 }.forEach { placement ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.unfolded_only_page), Modifier.weight(1f))
                IconButton(onClick = { onRemoveWidget(placement.slot) },
                    modifier = Modifier.semantics { contentDescription = "Remove widget from Unfolded-only page" }) {
                    Icon(Icons.Rounded.DeleteOutline, null)
                }
                TextButton(onClick = { onWidget(placement.slot) }) { Text(stringResource(R.string.replace)) }
            }
        }
        TextButton(onClick = { onAddWidget(homePage) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_widget_to_this_page)) }
        Text(stringResource(R.string.hold_and_drag_an_app_to_move_it_pause_at), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp))
        }
    }
}

@Composable
private fun SettingSlider(label: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 14.dp)) {
        Row { Text(label, Modifier.weight(1f)); Text(valueLabel, color = MaterialTheme.colorScheme.secondary) }
        IosSlider(value, onChange, valueRange = range, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun WidgetActions(
    placement: WidgetPlacement,
    constraints: WidgetSpanConstraints?,
    canConfigure: Boolean,
    onConfigure: () -> Unit,
    isValid: (Int, Int) -> Boolean,
    onResize: (Int, Int) -> Unit,
    onStartResize: (Int, Int) -> Unit,
    onMoveToPage: (Int) -> Boolean,
    homePages: Int,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
    stackCards: List<Int> = emptyList(),
    stackLabel: (Int) -> String = { "Widget" },
    stackRotate: Boolean = true,
    onStackRotate: (Boolean) -> Unit = {},
    onAddToStack: () -> Unit = {},
    onRemoveFromStack: (Int) -> Unit = {},
    onShowFirstInStack: (Int) -> Unit = {},
) {
    val sheetMaxHeight = with(LocalDensity.current) {
        (LocalWindowInfo.current.containerSize.height * .88f).toDp()
    }
    var width by remember(placement.slot, placement.spanX) { mutableIntStateOf(placement.spanX) }
    var height by remember(placement.slot, placement.spanY) { mutableIntStateOf(placement.spanY) }
    var customSize by remember(placement.slot) { mutableStateOf(false) }
    val minWidth = constraints?.minimum?.width ?: 2
    val minHeight = constraints?.minimum?.height ?: 2
    val maxWidth = minOf(GRID_COLUMNS - placement.column, constraints?.maximum?.width ?: GRID_COLUMNS)
    val maxHeight = minOf(GRID_ROWS - placement.row, constraints?.maximum?.height ?: GRID_ROWS)
    val feasible = placement.page >= -1 && placement.row in 0 until GRID_ROWS &&
        !(placement.id >= 0 && constraints == null) && minWidth <= maxWidth && minHeight <= maxHeight
    val valid = feasible && isValid(width, height)
    fun fits(w: Int, h: Int) = feasible && w in minWidth..maxWidth && h in minHeight..maxHeight && isValid(w, h)
    val secondary = Color.White.copy(alpha = .6f)

    // iOS-style widget menu: quick sizes, widget actions, Smart Stack, and a red Remove at the bottom.
    Column(Modifier.fillMaxWidth().heightIn(max = sheetMaxHeight).verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (stackCards.size > 1) "Smart Stack" else "Widget", Modifier.weight(1f), color = Color.White,
                fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f)).clickable(onClickLabel = "Close widget options", onClick = onClose),
                contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, "Close widget options", tint = Color.White, modifier = Modifier.size(18.dp)) }
        }

        SheetGroupLabel(stringResource(R.string.size))
        SheetGroup {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple("Small", 2, 2), Triple("Medium", 4, 2), Triple("Large", 4, 4)).forEach { (label, w, h) ->
                    val ok = fits(w, h) || (placement.spanX == w && placement.spanY == h)
                    IosChip(selected = placement.spanX == w && placement.spanY == h,
                        onClick = { if (ok && (placement.spanX != w || placement.spanY != h)) { onResize(w, h); onClose() } },
                        label = { Text(label, color = if (ok) Color.Unspecified else Color.White.copy(alpha = .3f)) },
                        modifier = Modifier.weight(1f).testTag("widget-size-${label.lowercase()}-${placement.slot}"))
                }
            }
            MenuDivider()
            MenuRow("Resize on Home", Icons.Rounded.OpenInFull) { if (feasible) onStartResize(width, height) }
            MenuDivider()
            MenuRow(if (customSize) "Hide Custom Size" else "Custom Size", Icons.Rounded.Tune) { customSize = !customSize }
            if (customSize) Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!feasible) Text(stringResource(R.string.move_this_widget_into_the_six_row_grid_b), color = Color(0xFFFF453A), fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.width), Modifier.weight(1f), color = Color.White)
                    IconButton(enabled = constraints?.canResizeHorizontally != false,
                        onClick = { if (feasible) width = (width - 1).coerceAtLeast(minWidth) }) { Icon(Icons.Rounded.Remove, "Decrease widget width", tint = Color.White) }
                    Text("$width columns", Modifier.width(88.dp), textAlign = TextAlign.Center, color = Color.White)
                    IconButton(enabled = constraints?.canResizeHorizontally != false,
                        onClick = { if (feasible) width = (width + 1).coerceAtMost(maxWidth) }) { Icon(Icons.Rounded.Add, "Increase widget width", tint = Color.White) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.height), Modifier.weight(1f), color = Color.White)
                    IconButton(enabled = constraints?.canResizeVertically != false,
                        onClick = { if (feasible) height = (height - 1).coerceAtLeast(minHeight) }) { Icon(Icons.Rounded.Remove, "Decrease widget height", tint = Color.White) }
                    Text("$height rows", Modifier.width(88.dp), textAlign = TextAlign.Center, color = Color.White)
                    IconButton(enabled = constraints?.canResizeVertically != false,
                        onClick = { if (feasible) height = (height + 1).coerceAtMost(maxHeight) }) { Icon(Icons.Rounded.Add, "Increase widget height", tint = Color.White) }
                }
                if (!valid) Text(stringResource(R.string.that_size_overlaps_another_item_or_exten), color = secondary, fontSize = 13.sp)
                IosChip(selected = valid, onClick = { if (valid) { onResize(width, height); onClose() } }, label = { Text(stringResource(R.string.apply_size)) },
                    modifier = Modifier.fillMaxWidth())
            }
        }

        SheetGroupLabel(stringResource(R.string.widget))
        SheetGroup {
            if (canConfigure) { MenuRow("Edit Widget", Icons.Rounded.Settings) { onConfigure() }; MenuDivider() }
            MenuRow("Replace Widget", Icons.Rounded.FindReplace) { onReplace() }
            if (homePages > 1) {
                MenuDivider()
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(homePages) { page ->
                        IosChip(selected = placement.page == page, onClick = { onMoveToPage(page) },
                            label = { Text("Page ${page + 1}") }, modifier = Modifier.testTag("widget-move-${placement.slot}-page-$page"))
                    }
                }
            }
        }

        SheetGroupLabel(stringResource(R.string.smart_stack))
        SheetGroup {
            MenuRow(if (stackCards.size > 1) "Add Widget to Stack" else "Make a Stack", Icons.Rounded.Layers) { onAddToStack() }
            if (stackCards.size > 1) {
                stackCards.forEachIndexed { index, card ->
                    MenuDivider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}. ${stackLabel(card)}", Modifier.weight(1f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (index > 0) TextButton(onClick = { onShowFirstInStack(card) }) { Text(stringResource(R.string.show_first)) }
                        IconButton(onClick = { onRemoveFromStack(card) }) {
                            Icon(Icons.Rounded.RemoveCircleOutline, "Remove ${stackLabel(card)} from stack", tint = Color(0xFFFF453A))
                        }
                    }
                }
                MenuDivider()
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.smart_rotate), color = Color.White)
                        Text(stringResource(R.string.show_the_next_widget_every_30_minutes), color = secondary, fontSize = 13.sp)
                    }
                    IosSwitch(stackRotate, onStackRotate)
                }
            }
        }
        if (stackCards.size > 1) Text(stringResource(R.string.swipe_up_or_down_on_the_stack_to_flip_be), color = secondary, fontSize = 13.sp,
            modifier = Modifier.padding(start = 4.dp))

        SheetGroup(Modifier.padding(top = 8.dp)) {
            MenuRow(if (stackCards.size > 1) "Remove Stack" else "Remove Widget", Icons.Rounded.RemoveCircleOutline, destructive = true) { onRemove() }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** iOS grouped-list section header inside Folio's dark sheets. */
@Composable
internal fun SheetGroupLabel(text: String) {
    Text(text.uppercase(), color = Color.White.copy(alpha = .55f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = .4.sp, modifier = Modifier.padding(start = 16.dp, top = 10.dp))
}

/** iOS inset grouped list: rounded dark card holding rows separated by thin dividers. */
@Composable
internal fun SheetGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF2C2C2E)), content = content)
}

/** Side-rail placement; the rail sits on the right unless the left-handed layout is on. */
internal fun railTop(leftHanded: Boolean) = if (leftHanded) Alignment.TopStart else Alignment.TopEnd
internal fun railBottom(leftHanded: Boolean) = if (leftHanded) Alignment.BottomStart else Alignment.BottomEnd
internal fun Modifier.railEdge(leftHanded: Boolean, gap: androidx.compose.ui.unit.Dp) =
    padding(start = if (leftHanded) gap else 0.dp, end = if (leftHanded) 0.dp else gap)
