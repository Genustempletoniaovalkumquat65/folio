@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.mccal.folio

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow

/** Incremented to focus the All apps search field (e.g. after a middle swipe-down on Home). */
internal val librarySearchFocusRequests = mutableIntStateOf(0)

@Composable
internal fun AppLibrary(
    state: LauncherState, query: String, onQuery: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit, onPin: (String, Boolean) -> Unit, onActions: (AppEntry) -> Unit,
    modifier: Modifier = Modifier, editing: Boolean = false,
    drag: HomeDragState? = null, page: Int? = null,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit = { app, _ -> onLaunch(app) },
    onTurnOnWork: (Long) -> Unit = {},
) {
    val glass = !editing
    val palette = LocalDuoPalette.current
    val ink = if (glass) Ink else MaterialTheme.colorScheme.onSurface
    val pinned = remember(state.homeSlots, state.leadingSlots) {
        (state.homeSlots.asSequence() + state.leadingSlots.asSequence()).filterNotNull().toSet()
    }
    val hasWork = state.profiles.any { it.isWork } || state.apps.any { it.isWork }
    var showWork by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    val hiddenCount = if (editing) 0 else state.apps.count { it.id in state.hiddenApps }
    val context = androidx.compose.ui.platform.LocalContext.current
    val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusRequest = librarySearchFocusRequests.intValue
    LaunchedEffect(focusRequest) {
        if (focusRequest > 0 && !editing) {
            kotlinx.coroutines.delay(280) // let the page settle first
            runCatching { searchFocus.requestFocus(); keyboard?.show() }
        }
    }
    val listState = rememberLazyListState()
    val selectedProfile = if (showWork) state.profiles.firstOrNull { it.isWork } else state.profiles.firstOrNull { it.isPersonal }
    LaunchedEffect(showWork, selectedProfile?.available, selectedProfile?.quiet) {
        listState.scrollToItem(0)
    }
    val visibleApps = remember(state.apps, query, showWork, hasWork, showHidden, state.hiddenApps, editing) {
        state.apps.filter { (!hasWork || it.isWork == showWork) && it.label.contains(query.trim(), true) &&
            (editing || (it.id in state.hiddenApps) == showHidden) }
    }
    val groups = remember(visibleApps) {
        visibleApps.groupBy {
            it.label.firstOrNull()?.takeIf(Char::isLetter)?.uppercaseChar()?.toString() ?: "#"
        }
    }
    Surface(modifier, shape = RoundedCornerShape(24.dp),
        color = if (glass) Glass.copy(alpha = .48f) else MaterialTheme.colorScheme.surface,
        contentColor = ink,
        border = if (glass) BorderStroke(1.dp, Color.White.copy(alpha = .38f)) else null) {
        Column(Modifier.background(Brush.verticalGradient(if (glass)
            listOf(Color.White.copy(alpha = .09f), Color.Transparent) else listOf(Color.Transparent, Color.Transparent)))
            .padding(horizontal = 16.dp).padding(top = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing) "Choose home apps" else "All apps", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                Text(if (editing) "${pinned.size} pinned" else "${visibleApps.size}", color = ink, fontSize = 12.sp)
            }
            if (hasWork || hiddenCount > 0 || showHidden) Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasWork) {
                    FilterChip(selected = !showWork, onClick = { showWork = false }, label = { Text("Personal") })
                    FilterChip(selected = showWork, onClick = { showWork = true }, label = { Text("Work") })
                }
                FilterChip(selected = showHidden, onClick = { showHidden = !showHidden }, label = { Text("Hidden ($hiddenCount)") },
                    leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null, Modifier.size(16.dp)) }, modifier = Modifier.testTag("hidden-apps-chip"))
            }
            OutlinedTextField(query, onQuery, Modifier.fillMaxWidth().padding(vertical = 12.dp).then(if (editing) Modifier else Modifier.focusRequester(searchFocus)).testTag(if (editing) "pin-search" else "library-search"),
                placeholder = { Text(if (editing) "Search apps" else "Search apps, web or ask AI") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                    if (!editing && query.isNotBlank()) openWebSearch(context, WebSearchTarget.entries.first(), query)
                }),
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear search") } },
                colors = if (glass) OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ink, unfocusedTextColor = ink, cursorColor = ink,
                    focusedContainerColor = Color.White.copy(alpha = .18f), unfocusedContainerColor = Color.White.copy(alpha = .12f),
                    focusedBorderColor = Color.White.copy(alpha = .8f), unfocusedBorderColor = Color.White.copy(alpha = .45f),
                    focusedPlaceholderColor = ink, unfocusedPlaceholderColor = ink,
                    focusedLeadingIconColor = ink, unfocusedLeadingIconColor = ink,
                    focusedTrailingIconColor = ink, unfocusedTrailingIconColor = ink,
                ) else OutlinedTextFieldDefaults.colors())
            LazyColumn(Modifier.weight(1f).testTag("all-apps-list"), state = listState,
                contentPadding = PaddingValues(bottom = 12.dp)) {
                if (showWork && selectedProfile?.available == false) item("work-paused") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (selectedProfile.quiet) "Work apps are paused" else "Work profile is unavailable")
                        if (selectedProfile.quiet) Button(onClick = { onTurnOnWork(selectedProfile.userSerial) },
                            Modifier.padding(top = 10.dp).testTag("turn-on-work")) { Text("Turn on work apps") }
                    }
                }
                if (!editing && query.isNotBlank()) item("web-search") {
                    WebSearchRow(query) { openWebSearch(context, it, query) }
                }
                if (groups.isEmpty()) item { Text(if (state.loading) "Loading apps…" else "No apps found", Modifier.padding(vertical = 20.dp)) }
                groups.forEach { (letter, entries) ->
                    stickyHeader(key = "heading-$letter") {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            // An opaque small chip prevents text from showing through the sticky letter.
                            Box(Modifier.size(width = 32.dp, height = 28.dp).background(
                                if (glass) (if (palette.dark) Color(0xFF314852) else Color(0xFFB7CBD3))
                                else MaterialTheme.colorScheme.surfaceContainer,
                                RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Text(letter, color = ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                            if (glass) HorizontalDivider(Modifier.weight(1f).padding(start = 10.dp), color = Color.White.copy(alpha = .24f))
                        }
                    }
                    items(entries, key = { it.id }) { app ->
                        val isPinned = app.id in pinned
                        val launchBounds = remember { android.graphics.Rect() }
                        val dragModifier = if (drag != null) Modifier.dropRegion(drag, DropTarget.Library(app.id), app.id, page) else Modifier
                        val click = { if (editing) onPin(app.id, !isPinned) else onLaunchFrom(app, launchBounds) }
                        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).then(dragModifier).clip(RoundedCornerShape(14.dp)).testTag("library-app-${app.id}")
                            .then(if (drag == null) Modifier.combinedClickable(onClick = click, onLongClick = { onActions(app) })
                                else Modifier.clickable(onClick = click).semantics { onLongClick("App options") { onActions(app); true } })
                            .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(app, null, Modifier.size(40.dp)
                                .onGloballyPositioned { launchBounds.set(it.boundsInWindow().toAndroidBounds()) }.clip(RoundedCornerShape(10.dp)))
                            Text(app.label, Modifier.weight(1f).padding(start = 12.dp), maxLines = 2, fontSize = 14.sp)
                            if (editing) IconButton(onClick = { onPin(app.id, !isPinned) }, Modifier.testTag("pin-${app.id}")) {
                                Icon(if (isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                                    if (isPinned) "Remove ${app.label} from home" else "Pin ${app.label} to home",
                                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Where a typed query can go. Google uses the "Web" filter (udm=14), which omits AI Overviews. */
internal enum class WebSearchTarget(val label: String, private val prefix: String) {
    GOOGLE("Google", "https://www.google.com/search?udm=14&q="),
    DUCKDUCKGO("DuckDuckGo", "https://noai.duckduckgo.com/?q="),
    CHATGPT("Ask ChatGPT", "https://chatgpt.com/?q="),
    CLAUDE("Ask Claude", "https://claude.ai/new?q="),
    PERPLEXITY("Perplexity", "https://www.perplexity.ai/search?q=");

    fun uri(query: String): android.net.Uri = android.net.Uri.parse(prefix + android.net.Uri.encode(query.trim()))
}

internal fun openWebSearch(context: android.content.Context, target: WebSearchTarget, query: String) {
    // A plain https link: the matching app opens it if installed and verified, otherwise the browser.
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, target.uri(query))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun WebSearchRow(query: String, onSearch: (WebSearchTarget) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text("Search \u201c${query.trim()}\u201d with", fontSize = 12.sp, color = Ink.copy(alpha = .75f),
            modifier = Modifier.padding(bottom = 6.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WebSearchTarget.entries.forEach { target ->
                AssistChip(onClick = { onSearch(target) }, label = { Text(target.label) },
                    leadingIcon = { Icon(if (target.label.startsWith("Ask")) Icons.Rounded.AutoAwesome else Icons.Rounded.Public,
                        null, Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("web-search-${target.name.lowercase()}"))
            }
        }
    }
}
