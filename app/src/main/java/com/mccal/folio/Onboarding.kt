package com.mccal.folio

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/** One onboarding page. [done] is re-read whenever Folio comes back from a settings screen. */
private data class OnboardingPage(
    val key: String, val icon: ImageVector, val color: Long, val title: String, val body: String,
    val uses: List<String> = emptyList(), val action: String? = null, val done: () -> Boolean = { false },
    val onAction: (() -> Unit)? = null, val optional: Boolean = true,
)

/**
 * iOS Setup Assistant-style onboarding: one clear page per thing Folio needs, each explaining why (and what it
 * doesn't do), every step skippable, progress saved so it resumes where you left off, and Home is usable the
 * whole time. Pages for things already allowed are left out.
 */
@Composable
internal fun Onboarding(isDefaultHome: Boolean, onMakeDefault: () -> Unit, onShadeSetup: () -> Unit,
    systemWallpaper: Boolean, onWallpaper: (Boolean) -> Unit, onFinish: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("setup_experience", Context.MODE_PRIVATE) }
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { tick++ } }
    fun open(intent: Intent?) { intent?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } }

    val all = remember {
        listOf(
            OnboardingPage("welcome", Icons.Rounded.Home, 0xFF5E5CE6, "Welcome to Something New",
                "Folio isn’t a copy of Apple’s work. It takes inspiration from iOS and the iOS jailbreak community, then builds on it for your Fold. If you’re coming from iPhone but want to customize everything, this is your canvas. Have fun, and reach out if you run into any issues.",
                action = "Continue", optional = false),
            OnboardingPage("home", Icons.Rounded.Home, 0xFF0A84FF, "Make Folio your Home",
                "So the Home gesture, folding and the side key always come back to Folio. You can switch back anytime.",
                action = "Choose Home App", done = { isDefaultHome }, onAction = onMakeDefault),
            OnboardingPage("notifications", Icons.Rounded.Notifications, 0xFFFF3B30, "Notifications",
                "Folio reads notifications only to show them in its own UI.",
                uses = listOf("Dynamic Island: music, calls, timers, messages", "Notification Center and quick reply", "App icon badges"),
                action = "Allow Access", done = { IslandListenerService.hasAccess(context) },
                onAction = { open(IslandListenerService.accessSettingsIntent(context)) }),
            OnboardingPage("gestures", Icons.Rounded.Accessibility, 0xFF30D158, "Gestures",
                "Folio’s gestures service lets pull-downs open panels and puts the island and dock over other apps. It can’t read your screen.",
                uses = listOf("Pull down for Notification and Control Center", "Island and dock in every app", "Lock Screen and Screenshot actions"),
                action = "Turn On", done = { SystemShadeAccessibilityService.isConnected() }, onAction = onShadeSetup),
            OnboardingPage("fold", Icons.Rounded.Devices, 0xFFFF375F, "Folding from Home",
                "Samsung locks the phone when you fold from Home. Set Continue apps on cover screen to Always so the cover picks up where you were.",
                action = "Open Display Settings", done = { foldStaysAwake(context) },
                onAction = { open(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }),
            OnboardingPage("sidekey", Icons.Rounded.TouchApp, 0xFFFF9F0A, "Side Key",
                "Hold the side key for Folio’s picker: ChatGPT, Claude, Perplexity, Gemini or Google without AI. Settings › Side Key walks through it.",
                action = "Choose Folio as Assistant", done = { AssistPickerActivity.isDefaultAssistant(context) },
                onAction = { open(AssistPickerActivity.settingsIntent()) }),
            OnboardingPage("look", Icons.Rounded.Wallpaper, 0xFF32ADE6, "Your Wallpaper",
                "Keep the wallpaper you already use, or use Folio’s dunes. Text on Home adjusts for light and dark wallpapers.",
                optional = false),
            OnboardingPage("done", Icons.Rounded.CheckCircle, 0xFF30D158, "You’re All Set",
                "Hold an app for its menu, swipe down on Home for Spotlight, and pull down from the top corners for notifications and Control Center. Everything else is in Settings.",
                action = "Get Started", optional = false),
        ).filter { page -> page.key in setOf("welcome", "look", "done") || !page.done() }
    }
    var index by rememberSaveable { mutableIntStateOf(prefs.getInt(STEP, 0).coerceIn(0, all.lastIndex)) }
    fun go(to: Int) { index = to.coerceIn(0, all.lastIndex); prefs.edit().putInt(STEP, index).apply() }
    fun finish() { prefs.edit().remove(STEP).apply(); onFinish() }
    BackHandler(index > 0) { go(index - 1) }
    val page = all[index]
    val done = remember(tick, page) { page.done() }

    Box(Modifier.fillMaxSize().testTag("onboarding")) {
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 24.dp)) {
            // Top bar: back and skip
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable { go(index - 1) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ChevronLeft, null, tint = IosBlue, modifier = Modifier.size(26.dp))
                    Text("Back", color = IosBlue, fontSize = 17.sp)
                }
                Spacer(Modifier.weight(1f))
                if (page.key != "done") Text("Skip Setup", color = IosBlue, fontSize = 17.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { finish() }.padding(8.dp).testTag("onboarding-skip"))
            }
            AnimatedContent(index, Modifier.weight(1f), label = "onboarding page",
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
                }) { i ->
                val p = all[i]
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)).background(Color(p.color)), contentAlignment = Alignment.Center) {
                        Icon(p.icon, null, tint = Color.White, modifier = Modifier.size(56.dp))
                    }
                    Text(p.title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        lineHeight = 40.sp, modifier = Modifier.padding(top = 24.dp))
                    Text(p.body, color = Color.White.copy(alpha = .7f), fontSize = 17.sp, textAlign = TextAlign.Center, lineHeight = 23.sp,
                        modifier = Modifier.padding(top = 12.dp))
                    if (p.uses.isNotEmpty()) SheetGroup(Modifier.padding(top = 24.dp)) {
                        p.uses.forEachIndexed { n, use ->
                            if (n > 0) MenuDivider()
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Check, null, tint = Color(p.color), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(use, color = Color.White, fontSize = 15.sp)
                            }
                        }
                    }
                    if (p.key == "look") Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IosChip(selected = systemWallpaper, onClick = { onWallpaper(true) }, label = { Text("My Wallpaper") }, modifier = Modifier.weight(1f))
                        IosChip(selected = !systemWallpaper, onClick = { onWallpaper(false) }, label = { Text("Folio Dunes") }, modifier = Modifier.weight(1f))
                    }
                    if (done && p.onAction != null) Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF30D158))
                        Spacer(Modifier.width(6.dp))
                        Text("All set", color = Color(0xFF30D158), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            // Bottom: primary action (or Continue once done), Not Now, and progress dots.
            val primary = when {
                page.onAction != null && !done -> page.action ?: "Continue"
                page.key == "done" -> page.action ?: "Get Started"
                else -> "Continue"
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(IosBlue)
                .clickable {
                    when {
                        page.key == "done" -> finish()
                        page.onAction != null && !done -> page.onAction.invoke()
                        else -> go(index + 1)
                    }
                }.semantics { contentDescription = primary }.testTag("onboarding-primary"), contentAlignment = Alignment.Center) {
                Text(primary, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                if (page.optional && !done) Text("Not Now", color = IosBlue, fontSize = 17.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { go(index + 1) }.padding(10.dp).testTag("onboarding-not-now"))
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.Center) {
                all.indices.forEach { n ->
                    Box(Modifier.padding(3.dp).size(if (n == index) 8.dp else 6.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = if (n == index) 1f else .3f)))
                }
            }
        }
    }
}

private val IosBlue = Color(0xFF0A84FF)
private const val STEP = "onboardingStep"
