package com.mccal.folio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp

internal enum class CustomizationPage { OVERVIEW, SETUP, WALLPAPER, HOME, STATUS, GESTURES, FOLD, BACKUP, HELP }

@Composable
internal fun CustomizationSheet(state: LauncherState, initiallyWide: Boolean, model: LauncherModel,
    isDefaultHome: Boolean, page: CustomizationPage, onPage: (CustomizationPage) -> Unit,
    onMakeDefault: () -> Unit, onClose: () -> Unit, onEditPins: () -> Unit, onWidget: (Int) -> Unit,
    onAddWidget: (Int) -> Unit, onRemoveWidget: (Int) -> Unit, onWallpaperPreview: () -> Unit,
    onExportLayout: () -> Unit, onImportLayout: () -> Unit,
    appearance: AppearanceState, onAppearanceMode: (AppearanceMode) -> Unit,
    onAppearanceManual: (String, Double, Double) -> Unit, onAppearanceDeviceLocation: () -> Unit,
    onAppearanceClear: () -> Unit, backgrounds: LauncherBackgroundController, homePage: Int = 0,
    onShadeSetup: () -> Unit = {},
) {
    var wide by rememberSaveable { mutableStateOf(initiallyWide) }
    val title = when (page) {
        CustomizationPage.OVERVIEW -> "Make it yours"
        CustomizationPage.SETUP -> "Set up Folio"
        CustomizationPage.WALLPAPER -> "Wallpaper & appearance"
        CustomizationPage.HOME -> "Home layout"
        CustomizationPage.STATUS -> "Status & side rail"
        CustomizationPage.GESTURES -> "Gestures & search"
        CustomizationPage.FOLD -> "Fold"
        CustomizationPage.BACKUP -> "Backup"
        CustomizationPage.HELP -> "Help & setup"
    }
    val bodyScroll = rememberScrollState()
    LaunchedEffect(page) { bodyScroll.scrollTo(0) }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (page != CustomizationPage.OVERVIEW) IconButton(onClick = { onPage(CustomizationPage.OVERVIEW) },
                Modifier.testTag("customization-back")) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close customization") }
        }
        Column(Modifier.weight(1f).verticalScroll(bodyScroll).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (page) {
                CustomizationPage.OVERVIEW -> {
                    if (!isDefaultHome) Button(onClick = onMakeDefault, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("default-home-settings")) { Text("Set as home app") }
                    if (state.canUndoEdit) OutlinedButton(onClick = { model.undoEdit(); onClose() },
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Undo last layout change") }
                    val setupSteps = rememberSetupSteps(isDefaultHome, onMakeDefault, onShadeSetup, state.messagesApp, model::setMessagesApp, state.systemWallpaper, model::setSystemWallpaper)
                    val setupLeft = setupSteps.count { it.required && !it.done }
                    if (setupLeft > 0) CustomizationDestination(Icons.Rounded.Checklist, "Finish setting up Folio",
                        "$setupLeft step${if (setupLeft > 1) "s" else ""} left for the full experience", "customization-setup") { onPage(CustomizationPage.SETUP) }
                    MiniHomePreview(backgrounds.previewBitmap, state, 176.dp)
                    CustomizationDestination(Icons.Rounded.Wallpaper, "Wallpaper & appearance",
                        if (backgrounds.previewPending) "Photo ready to review" else "Background, colors, and light",
                        "customization-wallpaper") { onPage(CustomizationPage.WALLPAPER) }
                    CustomizationDestination(Icons.Rounded.GridView, "Home layout",
                        "Icons, spacing, dock, and widgets", "customization-home") { onPage(CustomizationPage.HOME) }
                    CustomizationDestination(Icons.Rounded.ViewSidebar, "Status & side rail",
                        "Status icons, island, frost, left-handed", "customization-status") { onPage(CustomizationPage.STATUS) }
                    CustomizationDestination(Icons.Rounded.Search, "Gestures & search",
                        "Control Center, notifications, Spotlight", "customization-gestures") { onPage(CustomizationPage.GESTURES) }
                    CustomizationDestination(Icons.Rounded.Devices, "Fold",
                        "Unfold animation and staying awake", "customization-fold") { onPage(CustomizationPage.FOLD) }
                    CustomizationDestination(Icons.Rounded.Save, "Backup",
                        "Save or restore this layout", "customization-backup") { onPage(CustomizationPage.BACKUP) }
                    if (setupLeft == 0) CustomizationDestination(Icons.Rounded.Checklist, "Setup checklist",
                        "Permissions and Samsung settings Folio uses", "customization-setup-all") { onPage(CustomizationPage.SETUP) }
                    CustomizationDestination(Icons.Rounded.HelpOutline, "Help & setup",
                        "Home app, widgets, gestures, and Discover", "customization-help") {
                        onPage(CustomizationPage.HELP)
                    }
                    if (isDefaultHome) TextButton(onClick = onMakeDefault, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("default-home-settings")) { Text("Change home app") }
                }
                CustomizationPage.SETUP -> SetupChecklist(rememberSetupSteps(isDefaultHome, onMakeDefault, onShadeSetup, state.messagesApp, model::setMessagesApp, state.systemWallpaper, model::setSystemWallpaper))
                CustomizationPage.WALLPAPER -> {
                    val wallpaperContext = androidx.compose.ui.platform.LocalContext.current
                    SettingsCard("Background") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IosChip(selected = state.systemWallpaper, onClick = {
                                if (!state.systemWallpaper) { model.setSystemWallpaper(true); (wallpaperContext as? android.app.Activity)?.recreate() }
                            },
                                label = { Text("Android wallpaper") }, modifier = Modifier.weight(1f).testTag("background-system"))
                            IosChip(selected = !state.systemWallpaper, onClick = {
                                if (state.systemWallpaper) { model.setSystemWallpaper(false); (wallpaperContext as? android.app.Activity)?.recreate() }
                            },
                                label = { Text("Folio background") }, modifier = Modifier.weight(1f).testTag("background-folio"))
                        }
                        Text(if (state.systemWallpaper) "Uses the same wallpaper as your phone’s home screen (including live wallpapers), so it matches what you had in Samsung’s or another launcher."
                            else "Folio’s dunes or a photo you choose, only behind Folio.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.systemWallpaper) TextButton(onClick = {
                            runCatching { wallpaperContext.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SET_WALLPAPER), "Change wallpaper")
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }, modifier = Modifier.testTag("background-change-system")) { Text("Change Android wallpaper") }
                    }
                    if (!state.systemWallpaper) {
                    MiniHomePreview(backgrounds.previewBitmap, state, 228.dp)
                    Text("Launcher background", style = MaterialTheme.typography.titleMedium)
                    Text("Changes the image behind Folio’s Home screens.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = backgrounds::choosePhoto, enabled = !backgrounds.loading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("background-choose")) {
                        Text(if (backgrounds.previewPending) "Choose a different photo" else "Choose a photo")
                    }
                    if (backgrounds.previewPending) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = backgrounds::cancelPreview, Modifier.weight(1f).heightIn(min = 48.dp)
                            .testTag("background-preview-cancel")) { Text("Cancel") }
                        Button(onClick = backgrounds::applyPreview, enabled = backgrounds.previewBitmap != null,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("background-preview-apply")) { Text("Apply") }
                    }
                    if (backgrounds.photoSelected && !backgrounds.previewPending) OutlinedButton(onClick = backgrounds::reset,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("background-reset")) { Text("Reset to default dunes") }
                    if (backgrounds.loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("background-loading"))
                    (backgrounds.errorMessage ?: backgrounds.successMessage)?.let { message ->
                        TextButton(onClick = backgrounds::clearMessage, Modifier.fillMaxWidth().testTag("background-message")) { Text(message) }
                    }
                    }
                    if (!state.systemWallpaper) {
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text("Folio background as phone wallpaper", style = MaterialTheme.typography.titleMedium)
                        Text("Opens Android’s preview to use Folio’s background as your phone’s wallpaper too (a live wallpaper), so it matches outside Folio.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = onWallpaperPreview, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("wallpaper-preview")) { Icon(Icons.Rounded.Wallpaper, null); Spacer(Modifier.width(8.dp)); Text("Preview as phone wallpaper") }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    AppearanceSettings(appearance, onAppearanceMode, onAppearanceManual, onAppearanceDeviceLocation, onAppearanceClear)
                }
                CustomizationPage.HOME -> HomeLayoutSettings(state, wide, { wide = it }, model, homePage,
                    onEditPins, onWidget, onAddWidget, onRemoveWidget)
                CustomizationPage.GESTURES -> {
                    SettingsCard("Gestures") {
                        Text("Pull down from the top-left for notifications, top-right for Control Center. Swipe down lower on Home for Spotlight. Swipe sideways to change pages.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SettingsSwitch("iPhone-style Control Center and notifications", state.folioPanels, model::setFolioPanels, "folio-panels-switch")
                    }
                    if (state.folioPanels) SettingsCard("Panels") {
                        CustomizationSlider("Background blur", "${(state.panelBlur * 100).toInt()}%", state.panelBlur, 0f..1f) { model.setPanelBlur(it) }
                        SettingsSwitch("Big clock in Notification Center", state.notificationClock, model::setNotificationClock, "notification-clock-switch")
                        SettingsSwitch("Stack notifications by app", state.groupNotifications, model::setGroupNotifications, "notification-group-switch")
                        SettingsSwitch("Unfolded: clock beside notifications", state.ncSplit, model::setNcSplit, "notification-split-switch")
                        Text("Control Center size", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PanelSize.entries.forEach { size ->
                                IosChip(selected = state.ccSize == size, onClick = { model.setCcSize(size) }, label = { Text(size.label) })
                            }
                        }
                        SettingsSwitch("Unfolded: Control Center in the middle", state.ccCentered, model::setCcCentered, "cc-centered-switch")
                        Text("Tip: tap + at the top of Control Center to add or remove controls.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("To restyle Samsung\u2019s own pull-down (colors, transparency, layout), use Good Lock \u203a QuickStar and Theme Park.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SettingsCard("Side key") {
                        val assistContext = androidx.compose.ui.platform.LocalContext.current
                        val held = remember(page) { AssistPickerActivity.isDefaultAssistant(assistContext) }
                        Text(if (held) "Holding the side key opens Folio\u2019s picker: ChatGPT, Claude, Perplexity, Gemini or search without AI."
                            else "Make Folio the digital assistant, then set Settings \u203a Advanced features \u203a Side button \u203a Press and hold \u203a Digital assistant.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!held) FilledTonalButton(onClick = { runCatching { assistContext.startActivity(AssistPickerActivity.settingsIntent()) } }) {
                            Text("Choose Folio as assistant")
                        }
                    }
                    SettingsCard("Spotlight") {
                        Text("Search with Enter", style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(WebSearchTarget.GOOGLE to "Google (no AI)", WebSearchTarget.DUCKDUCKGO to "DuckDuckGo").forEach { (target, label) ->
                                IosChip(selected = state.searchEngine == target.name, onClick = { model.setSearchEngine(target.name) }, label = { Text(label) })
                            }
                        }
                        SpotlightSection.entries.forEach { section ->
                            SettingsSwitch(section.title, section.name !in state.spotlightHidden,
                                { model.setSpotlightSection(section.name, it) }, "spotlight-${section.name.lowercase()}")
                        }
                        val messageContext = androidx.compose.ui.platform.LocalContext.current
                        val iMessageApps = remember { Messaging.iMessageApps.filter { Messaging.installed(messageContext, it.first) } }
                        if (iMessageApps.isNotEmpty()) {
                            Text("Message contacts with", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IosChip(selected = state.messagesApp == null, onClick = { model.setMessagesApp(null) }, label = { Text("Texting app") })
                                iMessageApps.forEach { (pkg, label) ->
                                    IosChip(selected = state.messagesApp == pkg, onClick = { model.setMessagesApp(pkg) }, label = { Text(label) })
                                }
                            }
                        }
                    }
                    SettingsCard("Left of Home") {
                        val leftContext = androidx.compose.ui.platform.LocalContext.current
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("TODAY" to "Today View", "DISCOVER" to "Google Discover").forEach { (value, label) ->
                                IosChip(selected = state.leftPage == value, onClick = {
                                    if (state.leftPage != value) {
                                        model.setLeftPage(value)
                                        // The Home pager's page count changes; rebuild the screen once.
                                        (leftContext as? android.app.Activity)?.recreate()
                                    }
                                }, label = { Text(label) })
                            }
                        }
                        Text("Today View is iPhone's widget page: search, suggestions and your widgets. Google Discover needs the Google app.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.leftPage == "TODAY") {
                            Text("When unfolded", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("PAGE" to "Swipe to it", "BESIDE" to "Beside Home", "OFF" to "Off").forEach { (value, label) ->
                                    IosChip(selected = state.todayUnfolded == value, onClick = { model.setTodayUnfolded(value) }, label = { Text(label) })
                                }
                            }
                            Text(when (state.todayUnfolded) {
                                "BESIDE" -> "Like iPad: Today View stays on the left of the open screen, next to your first Home page. It takes the place of the unfolded-only page."
                                "OFF" -> "No Today View while unfolded; it's still there on the cover screen."
                                else -> "Swipe right from your first Home page to open it, folded or unfolded."
                            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    SettingsCard("App Library") {
                        SettingsSwitch("Group apps into categories", state.libraryCategories, model::setLibraryCategories, "library-categories-switch")
                    }
                    SettingsCard("Search") {
                        SettingsSwitch("Search button on Home", state.searchPill, model::setSearchPill, "search-pill-switch")
                        SettingsSwitch("Swipe down on Home for Spotlight", state.swipeDownSearch, model::setSwipeDownSearch, "swipe-search-switch")
                        SettingsSwitch("Search button opens the Google app", state.googleSearch, model::setGoogleSearch, "google-search-switch")
                        Text("When off, the search button opens Spotlight: apps, contacts, settings, a calculator, Google without AI, and ChatGPT, Claude or Perplexity.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                CustomizationPage.STATUS -> {
                    val st = state.statusStyle
                    SettingsCard("App icons") {
                        val iconContext = androidx.compose.ui.platform.LocalContext.current
                        val packs = remember { IconPacks.installed(iconContext) }
                        Text("Icon pack", style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IosChip(selected = state.iconPack == null, onClick = { model.setIconPack(null) }, label = { Text("App icons") })
                            packs.forEach { pack ->
                                IosChip(selected = state.iconPack == pack.packageName, onClick = { IconPacks.clear(); model.setIconPack(pack.packageName) },
                                    label = { Text(pack.label) })
                            }
                        }
                        if (packs.isEmpty()) Text("Install any icon pack made for Nova-style launchers to use it here.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Shape", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconShape.entries.forEach { shape ->
                                IosChip(selected = state.iconShape == shape, onClick = { model.setIconShape(shape) }, label = { Text(shape.label) })
                            }
                        }
                        Text("Notification badges", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BadgeStyle.entries.forEach { style ->
                                IosChip(selected = state.badgeStyle == style, onClick = { model.setBadgeStyle(style) }, label = { Text(style.label) })
                            }
                        }
                        if (state.badgeStyle != BadgeStyle.OFF) Text("Badge color", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                        if (state.badgeStyle != BadgeStyle.OFF) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BadgeColor.entries.forEach { color ->
                                IosChip(selected = state.badgeColor == color, onClick = { model.setBadgeColor(color) }, label = { Text(color.label) })
                            }
                        }
                        Text("Style", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconStyle.entries.forEach { style ->
                                IosChip(selected = state.iconStyle == style, onClick = { model.setIconStyle(style, state.iconTint) },
                                    label = { Text(style.label) }, modifier = Modifier.testTag("icon-style-${style.name.lowercase()}"))
                            }
                        }
                        if (state.iconStyle == IconStyle.TINTED) Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(0xFFFFB340, 0xFFFF6961, 0xFFFF7EB6, 0xFFBF8CFF, 0xFF64B5FF, 0xFF5EE0C4, 0xFF9BE15D, 0xFFE8E8E8).forEach { c ->
                                Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(androidx.compose.ui.graphics.Color(c))
                                    .then(if (state.iconTint == c) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, androidx.compose.foundation.shape.CircleShape) else Modifier)
                                    .clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { model.setIconStyle(IconStyle.TINTED, c) }
                                    .semantics { contentDescription = "Tint color"; selected = state.iconTint == c })
                            }
                        }
                    }
                    SettingsCard("Side rail") {
                        SettingsSwitch("Left-handed layout (rail on the left)", state.leftHanded, model::setLeftHanded, "left-handed-switch")
                        SettingsSwitch("Show app names", state.labels, model::setLabels, "label-switch")
                        CustomizationSlider("Frost", "${(st.railGlass * 100).toInt()}%", st.railGlass, 0f..0.8f) {
                            model.setStatusStyle(st.copy(railGlass = it))
                        }
                    }
                    SettingsCard("Status") {
                        SettingsSwitch("Show status in the rail", state.verticalStatus, model::setVerticalStatus, "status-switch")
                        if (state.verticalStatus) {
                            Text("Icon style", style = MaterialTheme.typography.labelLarge)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusGlyph.entries.forEach { g ->
                                    IosChip(selected = st.glyph == g, onClick = { model.setStatusStyle(st.copy(glyph = g)) }, label = { Text(g.label) },
                                        modifier = Modifier.testTag("status-glyph-${g.name.lowercase()}"))
                                }
                            }
                            SettingsSwitch("Time", st.showTime, { model.setStatusStyle(st.copy(showTime = it)) }, "status-time")
                            SettingsSwitch("Date", st.showDate, { model.setStatusStyle(st.copy(showDate = it)) }, "status-date")
                            SettingsSwitch("Battery percentage", st.showBatteryPercent, { model.setStatusStyle(st.copy(showBatteryPercent = it)) }, "status-percent")
                            SettingsSwitch("Color battery when charging or low", st.colorfulBattery, { model.setStatusStyle(st.copy(colorfulBattery = it)) }, "status-color")
                        }
                    }
                    SettingsCard("In every app") {
                        SettingsSwitch("Dock handle on the rail edge", state.dockEverywhere, { on ->
                            model.setDockEverywhere(on); if (on && !SystemShadeAccessibilityService.isConnected()) onShadeSetup()
                        }, "dock-everywhere-switch")
                        SettingsSwitch("Dynamic Island", state.islandEverywhere, { on ->
                            model.setIslandEverywhere(on); if (on && !SystemShadeAccessibilityService.isConnected()) onShadeSetup()
                        }, "island-everywhere-switch")
                        Text("Uses Folio\u2019s accessibility service (the same one as shade gestures). Tap or drag the handle to open your dock. If the handle fights the back gesture, lower the right-edge back sensitivity.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SettingsCard("Island") {
                        val islandContext = androidx.compose.ui.platform.LocalContext.current
                        SettingsSwitch("Music and live progress", state.island, { on ->
                            model.setIsland(on)
                            if (on && !IslandListenerService.hasAccess(islandContext))
                                runCatching { islandContext.startActivity(IslandListenerService.accessSettingsIntent(islandContext)) }
                        }, "island-switch")
                        if (state.island && !IslandListenerService.hasAccess(islandContext)) TextButton(onClick = {
                            runCatching { islandContext.startActivity(IslandListenerService.accessSettingsIntent(islandContext)) }
                        }) { Text("Allow notification access") }
                        if (state.island) {
                            Text("Long-press and drag the island to move it. On the inner screen the camera sits under the display, so drag it onto the camera once.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { IslandPosition.reset(islandContext) }) { Text("Put the island back at the camera") }
                            Text("Brief pop-ups", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                            listOf("CHARGING" to "Charging", "SILENT" to "Silent mode", "FOCUS" to "Do Not Disturb", "BLUETOOTH" to "Bluetooth devices", "MESSAGE" to "New messages (with quick reply)").forEach { (kind, label) ->
                                SettingsSwitch(label, kind !in state.islandEventsOff, { model.setIslandEvent(kind, it) }, "island-event-${kind.lowercase()}")
                            }
                            if ("MESSAGE" !in state.islandEventsOff) MessageBannerSettings(state.messagesAvoidDouble, model::setMessagesAvoidDouble)
                        }
                        Text("Reads only music, calls, timers, navigation and progress. Nothing leaves your phone.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                CustomizationPage.FOLD -> {
                    SettingsCard("Fold animation") {
                        SettingsSwitch("Fold animation", state.foldEffect, model::setFoldEffect, "fold-effect-switch")
                        if (state.foldEffect) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IosChip(selected = !state.foldSnapshot, onClick = { model.setFoldSnapshot(false) }, label = { Text("Duo blur") })
                            IosChip(selected = state.foldSnapshot, onClick = { model.setFoldSnapshot(true) }, label = { Text("Screenshot morph") })
                        }
                        if (state.foldEffect && state.foldSnapshot) Text("Takes a quick in-memory snapshot of Folio’s screen as the hinge starts moving and melts it into the other display. Nothing is saved.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.foldEffect) CustomizationSlider("Intensity", "${(state.foldIntensity * 100).toInt()}%",
                            state.foldIntensity, .3f..1.5f) { model.setFoldIntensity(it) }
                        Text("Your Fold reports only a few hinge positions, so Folio learns how fast you open and close and paces the effect to match.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SettingsCard("StandBy") {
                        SettingsSwitch("Show StandBy when set down half-open", state.standBy, model::setStandBy, "standby-switch")
                        Text("Big clock, date, next alarm, battery and music while the phone sits half-open on Home. Turns dim red at night. Tap to leave.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SettingsCard("Closing from Home") {
                        SettingsSwitch("Stay awake on the cover screen", state.stayAwakeOnFold, model::setStayAwakeOnFold, "fold-awake-switch")
                        Text("Samsung locks the phone when you fold on any home screen. Folio briefly steps aside while you close it so the cover screen stays on. Needs Settings › Display › Continue apps on cover screen › Always.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                CustomizationPage.BACKUP -> {
                    Text("Save the current Home layout, folders, widgets, and layout settings.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onExportLayout, Modifier.weight(1f).heightIn(min = 48.dp).testTag("layout-export")) { Text("Save") }
                        Button(onClick = onImportLayout, Modifier.weight(1f).heightIn(min = 48.dp).testTag("layout-import")) { Text("Restore") }
                    }
                    Text("Restore shows a review before changing Home.", style = MaterialTheme.typography.bodySmall)
                }
                CustomizationPage.HELP -> LauncherHelp(
                    isDefaultHome = isDefaultHome,
                    onHomeSettings = onMakeDefault,
                    onAddWidget = { onAddWidget(homePage) },
                    onShadeSetup = onShadeSetup,
                )
            }
        }
    }
}

@Composable
private fun LauncherHelp(
    isDefaultHome: Boolean,
    onHomeSettings: () -> Unit,
    onAddWidget: () -> Unit,
    onShadeSetup: () -> Unit,
) {
    HelpSection(Icons.Rounded.Home, "Home app",
        if (isDefaultHome) "Folio is your Home app. You can switch launchers in Android’s Home settings."
        else "Choose Folio in Android’s Home settings to use it when you press Home.")
    Button(onClick = onHomeSettings, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help-home-settings")) {
        Text(if (isDefaultHome) "Change home app" else "Set Folio as Home")
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    HelpSection(Icons.Rounded.TouchApp, "Customize any page",
        "Long-press empty space, then choose Customize launcher. If a page is full, long-press the slim area at its left edge.")
    HelpSection(Icons.Rounded.Widgets, "Widgets",
        "Add Android widgets to empty Home cells. Hold a widget to move or remove it.")
    OutlinedButton(onClick = onAddWidget, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help-add-widget")) {
        Text("Add widget to this page")
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    HelpSection(Icons.Rounded.SwipeDown, "Notifications and quick settings",
        "Pull down from the top of Home. The first time, Folio explains Android’s optional Accessibility setting, which opens the system panels and, if you turn them on, shows the dock handle and island over other apps.")
    TextButton(onClick = onShadeSetup, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help-shade-setup")) {
        Text("Set up shade gestures")
    }
    HelpSection(Icons.Rounded.Explore, "Discover",
        "Swipe right from the first Home page. If Google can’t provide the feed, Folio keeps a Home return and recovery actions available.")
}

@Composable
private fun HelpSection(icon: ImageVector, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 2.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun CustomizationDestination(icon: ImageVector, title: String, detail: String, tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(tag),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .52f), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

@Composable private fun MiniHomePreview(stagedBitmap: android.graphics.Bitmap?, state: LauncherState,
    previewHeight: androidx.compose.ui.unit.Dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val backgroundRevision = LauncherBackgroundCache.revision.intValue
    val committedBitmap = remember(backgroundRevision) { cachedLauncherBackground(context) }
    val bitmap = stagedBitmap ?: committedBitmap
    val apps = remember(state.apps) { state.apps.associateBy { it.id } }
    val homeIcons = state.homeSlots.mapNotNull { id -> id?.let(apps::get) }.take(8)
    val dockIcons = state.dock.mapNotNull { id -> id?.let(apps::get) }.take(5)
    val scale = previewHeight.value * .632f / 250f
    fun unit(value: Float) = (value * scale).dp
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.height(previewHeight).width(previewHeight * .632f).clip(RoundedCornerShape(unit(24f)))
            .testTag("customization-home-preview")) {
            DuneWallpaper()
            bitmap?.let { Image(it.asImageBitmap(), null, Modifier.matchParentSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
            Column(Modifier.fillMaxSize().padding(start = unit(16f), top = unit(18f), end = unit(54f)),
                verticalArrangement = Arrangement.spacedBy(unit(10f))) {
                Box(Modifier.fillMaxWidth().height(unit(42f)).background(MaterialTheme.colorScheme.surface.copy(alpha = .38f), RoundedCornerShape(unit(12f))))
                homeIcons.chunked(4).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { app -> AppIcon(app, null, Modifier.size(unit(24f)).clip(RoundedCornerShape(unit(7f)))) }
                } }
            }
            Column(Modifier.align(Alignment.CenterEnd).padding(end = unit(10f)).width(unit(36f))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = .42f), RoundedCornerShape(unit(18f)))
                .padding(vertical = unit(8f)), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(unit(8f))) {
                dockIcons.forEach { app -> AppIcon(app, null, Modifier.size(unit(22f)).clip(RoundedCornerShape(unit(7f)))) }
            }
        }
    }
}

@Composable private fun HomeLayoutSettings(state: LauncherState, wide: Boolean, onWide: (Boolean) -> Unit,
    model: LauncherModel, homePage: Int, onEditPins: () -> Unit, onWidget: (Int) -> Unit,
    onAddWidget: (Int) -> Unit, onRemoveWidget: (Int) -> Unit) {
    val p = if (wide) state.expanded else state.compact
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IosChip(!wide, { onWide(false) }, label = { Text("Cover") })
        IosChip(wide, { onWide(true) }, label = { Text("Inner") })
    }
    OutlinedButton(onClick = onEditPins, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Choose Home apps") }
    CustomizationSlider("App icon size", "${p.iconSize.toInt()} dp", p.iconSize, 40f..68f) { model.setPreset(wide, p.copy(iconSize = it)) }
    CustomizationSlider("Space between rows", "${p.rowGap.toInt()} dp", p.rowGap, 0f..28f) { model.setPreset(wide, p.copy(rowGap = it)) }
    CustomizationSlider("Dock width", "${p.dockWidth.toInt()} dp", p.dockWidth, 56f..84f) { model.setPreset(wide, p.copy(dockWidth = it)) }
    SettingsSwitch("Align dock with app rows", p.dockAlignToGrid, { model.setPreset(wide, p.copy(dockAlignToGrid = it)) })
    if (!p.dockAlignToGrid) CustomizationSlider("Dock height on screen", "${(p.dockPosition * 100).toInt()}%", p.dockPosition, .25f.. .75f) { model.setPreset(wide, p.copy(dockPosition = it)) }
    TextButton(onClick = { model.setPreset(wide, LayoutPreset()) }, Modifier.fillMaxWidth()) { Text("Reset this layout") }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Text("Widgets · Page ${homePage + 1}", style = MaterialTheme.typography.titleMedium)
    state.widgetPlacements.filter { it.page == homePage || (wide && it.page == -1) }.forEach { placement ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (placement.page == -1) "Unfolded-only page" else "${placement.spanX} × ${placement.spanY} widget · row ${placement.row + 1}", Modifier.weight(1f))
            IconButton(onClick = { onRemoveWidget(placement.slot) }, modifier = Modifier.semantics { contentDescription = if (placement.page == -1) "Remove widget from Unfolded-only page" else "Remove widget" }) { Icon(Icons.Rounded.DeleteOutline, null) }
            TextButton(onClick = { onWidget(placement.slot) }) { Text("Replace") }
        }
    }
    TextButton(onClick = { onAddWidget(homePage) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Add widget to this page") }
}

/** Keeps Android's pop-up and Folio's island message card from showing for the same message. */
@Composable private fun MessageBannerSettings(avoidDouble: Boolean, onAvoidDouble: (Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val channels by IslandListenerService.messageChannels.collectAsState()
    SettingsSwitch("Don\u2019t double up with Android pop-ups", avoidDouble, onAvoidDouble, "messages-avoid-double-switch")
    Text(if (avoidDouble) "Messages that Android already pops up are left to Android. Turn off Android\u2019s pop-up for an app below and its messages use the island instead (sound, badges and the notification list stay the same)."
        else "The island shows every new message, even when Android also shows its own pop-up.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (!avoidDouble) return
    val list = channels.values.sortedWith(compareBy({ !it.popsUp }, { it.appLabel }))
    if (list.isEmpty()) Text("Messaging apps appear here after they post a notification.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    list.forEach { channel ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(channel.appLabel, style = MaterialTheme.typography.bodyLarge)
                Text(listOfNotNull(channel.channelName, if (channel.popsUp) "Android pop-up" else "Island").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (channel.popsUp) TextButton(onClick = { runCatching { context.startActivity(channel.settingsIntent()) } }) { Text("Use island") }
            else Icon(Icons.Rounded.Check, "Uses the island", tint = androidx.compose.ui.graphics.Color(0xFF30D158))
        }
    }
}

@Composable private fun SettingsSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit, tag: String? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); IosSwitch(checked, onChecked, Modifier.then(if (tag != null) Modifier.testTag(tag) else Modifier))
    }
}

@Composable private fun CustomizationSlider(label: String, valueLabel: String, value: Float,
    range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column { Row { Text(label, Modifier.weight(1f)); Text(valueLabel, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .6f)) }
        IosSlider(value, onChange, valueRange = range, modifier = Modifier.semantics { contentDescription = label }) }
}

@Composable private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}
