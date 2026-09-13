package com.mccal.folio

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Remembers the last apps launched from Folio, for Spotlight suggestions. Stays on the device. */
internal object RecentApps {
    private const val KEY = "recent_apps"
    fun record(context: Context, id: String) {
        val prefs = context.getSharedPreferences("folio", 0)
        val list = (listOf(id) + (prefs.getString(KEY, "") ?: "").split('\n').filter { it.isNotBlank() && it != id }).take(12)
        prefs.edit().putString(KEY, list.joinToString("\n")).apply()
    }
    fun load(context: Context): List<String> =
        (context.getSharedPreferences("folio", 0).getString(KEY, "") ?: "").split('\n').filter { it.isNotBlank() }
}

/** iOS-style Spotlight over Home: suggestions, apps, contacts, settings, calculator, web and AI. */
@Composable
internal fun SpotlightOverlay(visible: Boolean, state: LauncherState, onClose: () -> Unit,
    onLaunch: (AppEntry) -> Unit) {
    BackHandler(visible) { onClose() }
    AnimatedVisibility(visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .62f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClose))
    }
    AnimatedVisibility(visible,
        enter = fadeIn(tween(200)) + slideInVertically(spring(dampingRatio = .82f, stiffness = Spring.StiffnessMediumLow)) { -it / 12 } +
            scaleIn(spring(dampingRatio = .82f, stiffness = Spring.StiffnessMediumLow), initialScale = .96f),
        exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { -it / 16 } + scaleOut(tween(160), targetScale = .97f)) {
        SpotlightContent(state, onClose, onLaunch)
    }
}

@Composable
private fun SpotlightContent(state: LauncherState, onClose: () -> Unit, onLaunch: (AppEntry) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(120); runCatching { focus.requestFocus(); keyboard?.show() } }
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    val apps = remember(state.apps, state.hiddenApps) { state.apps.filter { it.id !in state.hiddenApps } }
    val recent = remember(apps) {
        val byId = apps.associateBy { it.id }
        (RecentApps.load(context).mapNotNull(byId::get) + state.dock.mapNotNull { it?.let(byId::get) }).distinctBy { it.id }.take(8)
    }
    val q = query.trim()
    val appHits = remember(q, apps) { if (q.isEmpty()) emptyList() else rankApps(apps, q) }
    val settingHits = remember(q) { if (q.length < 2) emptyList() else SettingShortcuts.filter { it.matches(q) }.take(4) }
    val math = remember(q) { evaluateMath(q) }
    var contactsGranted by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { contactsGranted = it }
    val contacts by produceState(emptyList<ContactHit>(), q, contactsGranted) {
        value = if (contactsGranted && q.length >= 2) withContext(Dispatchers.IO) { queryContacts(context, q) } else emptyList()
    }
    val launch = { app: AppEntry -> RecentApps.record(context, app.id); onClose(); onLaunch(app) }
    val start = { intent: Intent -> onClose(); runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; Unit }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 16.dp).padding(top = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = if (wide) 640.dp else 720.dp).fillMaxWidth().testTag("spotlight"),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Search field
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SpotGlass).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = .75f), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search", color = Color.White.copy(alpha = .55f), fontSize = 18.sp)
                    BasicTextField(query, { query = it }, Modifier.fillMaxWidth().focusRequester(focus).testTag("spotlight-field"),
                        singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 18.sp), cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            when {
                                appHits.isNotEmpty() -> launch(appHits.first())
                                q.isNotEmpty() -> { onClose(); openWebSearch(context, WebSearchTarget.GOOGLE, q) }
                            }
                        }))
                }
                if (query.isNotEmpty()) Icon(Icons.Rounded.Cancel, "Clear", tint = Color.White.copy(alpha = .6f),
                    modifier = Modifier.size(20.dp).clip(CircleShape).clickable { query = "" })
            }

            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp)) {
                if (q.isEmpty()) {
                    if (recent.isNotEmpty()) item("suggestions") {
                        Section("Suggestions") { AppGrid(recent, launch) }
                    }
                } else {
                    math?.let { result -> item("math") {
                        Section("Calculator") {
                            ResultRow(Icons.Rounded.Calculate, "= $result", q) {
                                val clip = context.getSystemService(android.content.ClipboardManager::class.java)
                                clip.setPrimaryClip(android.content.ClipData.newPlainText("Result", result))
                            }
                        }
                    } }
                    appHits.firstOrNull()?.let { top -> item("top") {
                        Section("Top Hit") { TopHit(top) { launch(top) } }
                    } }
                    if (appHits.size > 1) item("apps") { Section("Apps") { AppGrid(appHits.drop(1).take(8), launch) } }
                    if (contacts.isNotEmpty()) item("contacts") {
                        Section("Contacts") { contacts.forEach { c ->
                            ResultRow(Icons.Rounded.Person, c.name, c.detail) { start(Intent(Intent.ACTION_VIEW, c.uri)) }
                        } }
                    } else if (!contactsGranted && q.length >= 2) item("contacts-permission") {
                        Section("Contacts") { ResultRow(Icons.Rounded.PersonSearch, "Search your contacts", "Allow contacts access") {
                            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                        } }
                    }
                    if (settingHits.isNotEmpty()) item("settings") {
                        Section("Settings") { settingHits.forEach { s -> ResultRow(Icons.Rounded.Settings, s.title, "Settings") { start(Intent(s.action)) } } }
                    }
                    item("web") {
                        Section("Search the Web & Ask AI") {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                WebSearchTarget.entries.forEach { target ->
                                    Row(Modifier.clip(RoundedCornerShape(50)).background(SpotGlass)
                                        .clickable { onClose(); openWebSearch(context, target, q) }
                                        .padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (target.label.startsWith("Ask")) Icons.Rounded.AutoAwesome else Icons.Rounded.Public, null,
                                            tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(target.label, color = Color.White, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                    if (math == null && appHits.isEmpty() && contacts.isEmpty() && settingHits.isEmpty()) item("none") {
                        Text("No results on this phone", color = Color.White.copy(alpha = .6f), fontSize = 13.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color.White.copy(alpha = .7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SpotGlass).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

@Composable
private fun AppGrid(apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth > 560.dp) 8 else 4
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            apps.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { app ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { onLaunch(app) }.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            AppIcon(app, app.label, Modifier.size(52.dp).clip(RoundedCornerShape(13.dp)))
                            Text(app.label, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp))
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun TopHit(app: AppEntry, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        AppIcon(app, null, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(if (app.profileLabel == "Personal") "Application" else "${app.profileLabel} app", color = Color.White.copy(alpha = .6f), fontSize = 13.sp)
        }
        Text("Open", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .18f)).padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

@Composable
private fun ResultRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, color = Color.White.copy(alpha = .6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Search logic

/** Prefix beats word-start beats substring beats initials ("gm" → Google Maps). */
internal fun rankApps(apps: List<AppEntry>, query: String): List<AppEntry> {
    val q = query.lowercase()
    return apps.mapNotNull { app ->
        val label = app.label.lowercase()
        val words = label.split(' ', '-', '.', '_').filter { it.isNotEmpty() }
        val score = when {
            label == q -> 0
            label.startsWith(q) -> 1
            words.any { it.startsWith(q) } -> 2
            label.contains(q) -> 3
            words.joinToString("") { it.take(1) }.startsWith(q) -> 4
            else -> null
        }
        score?.let { it to app }
    }.sortedWith(compareBy({ it.first }, { it.second.label.length })).map { it.second }
}

/** Evaluates simple arithmetic like "12*(3+4)/2". Returns null when the query isn't math. */
internal fun evaluateMath(input: String): String? {
    val text = input.replace('×', '*').replace('÷', '/').replace(",", "").replace(" ", "")
    if (text.length < 3 || !text.any { it in "+-*/^%" } || !text.all { it.isDigit() || it in "+-*/^%.()" }) return null
    return runCatching {
        val parser = MathParser(text)
        val value = parser.expression()
        if (!parser.done || value.isNaN() || value.isInfinite()) null
        else if (value == Math.rint(value) && kotlin.math.abs(value) < 1e15) value.toLong().toString()
        else "%.6f".format(value).trimEnd('0').trimEnd('.')
    }.getOrNull()
}

private class MathParser(private val text: String) {
    private var i = 0
    val done get() = i == text.length
    private fun peek() = text.getOrNull(i)
    fun expression(): Double {
        var v = term()
        while (peek() == '+' || peek() == '-') { val op = text[i++]; val r = term(); v = if (op == '+') v + r else v - r }
        return v
    }
    private fun term(): Double {
        var v = power()
        while (peek() == '*' || peek() == '/' || peek() == '%') {
            val op = text[i++]; val r = power()
            v = when (op) { '*' -> v * r; '/' -> v / r; else -> v % r }
        }
        return v
    }
    private fun power(): Double { val b = unary(); return if (peek() == '^') { i++; Math.pow(b, power()) } else b }
    private fun unary(): Double {
        if (peek() == '-') { i++; return -unary() }
        if (peek() == '(') { i++; val v = expression(); require(peek() == ')'); i++; return v }
        val start = i
        while (peek()?.let { it.isDigit() || it == '.' } == true) i++
        return text.substring(start, i).toDouble()
    }
}

private data class ContactHit(val name: String, val detail: String?, val uri: Uri)

private fun queryContacts(context: Context, query: String): List<ContactHit> = runCatching {
    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query))
    context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY,
        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY), null, null, null)?.use { c ->
        buildList {
            while (c.moveToNext() && size < 5) {
                val id = c.getLong(0)
                add(ContactHit(c.getString(2) ?: continue, null,
                    ContactsContract.Contacts.getLookupUri(id, c.getString(1)) ?: ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)))
            }
        }
    } ?: emptyList()
}.getOrDefault(emptyList())

private data class SettingShortcut(val title: String, val action: String, val keywords: List<String>) {
    fun matches(q: String) = title.contains(q, true) || keywords.any { it.startsWith(q, true) }
}

private val SettingShortcuts = listOf(
    SettingShortcut("Wi-Fi", Settings.ACTION_WIFI_SETTINGS, listOf("wifi", "wireless", "internet", "network")),
    SettingShortcut("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, listOf("bluetooth", "pair", "headphones")),
    SettingShortcut("Mobile network", Settings.ACTION_NETWORK_OPERATOR_SETTINGS, listOf("cellular", "mobile", "data", "sim")),
    SettingShortcut("Airplane mode", Settings.ACTION_AIRPLANE_MODE_SETTINGS, listOf("airplane", "flight")),
    SettingShortcut("Display & brightness", Settings.ACTION_DISPLAY_SETTINGS, listOf("display", "brightness", "screen", "dark")),
    SettingShortcut("Sounds & vibration", Settings.ACTION_SOUND_SETTINGS, listOf("sound", "volume", "ringtone", "vibration")),
    SettingShortcut("Notifications", Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS, listOf("notification", "alerts")),
    SettingShortcut("Battery", Intent.ACTION_POWER_USAGE_SUMMARY, listOf("battery", "power", "charging")),
    SettingShortcut("Apps", Settings.ACTION_APPLICATION_SETTINGS, listOf("apps", "applications", "uninstall")),
    SettingShortcut("Default apps", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, listOf("default", "home app", "browser", "assistant")),
    SettingShortcut("Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, listOf("storage", "space")),
    SettingShortcut("Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS, listOf("location", "gps")),
    SettingShortcut("Security & privacy", Settings.ACTION_SECURITY_SETTINGS, listOf("security", "privacy", "lock", "password", "fingerprint")),
    SettingShortcut("Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS, listOf("accessibility")),
    SettingShortcut("Date & time", Settings.ACTION_DATE_SETTINGS, listOf("date", "time", "clock")),
    SettingShortcut("Language & keyboard", Settings.ACTION_LOCALE_SETTINGS, listOf("language", "keyboard")),
    SettingShortcut("Do Not Disturb", Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS, listOf("dnd", "do not disturb", "focus")),
    SettingShortcut("NFC & payments", Settings.ACTION_NFC_SETTINGS, listOf("nfc", "pay", "wallet", "contactless")),
    SettingShortcut("Developer options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, listOf("developer", "usb debugging")),
    SettingShortcut("About phone", Settings.ACTION_DEVICE_INFO_SETTINGS, listOf("about", "phone", "software", "version")),
    SettingShortcut("Settings", Settings.ACTION_SETTINGS, listOf("settings", "preferences")),
)

private val SpotGlass = Color(0xFF2C2C2E).copy(alpha = .78f)
