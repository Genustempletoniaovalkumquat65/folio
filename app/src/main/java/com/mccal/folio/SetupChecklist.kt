package com.mccal.folio

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/** One thing Folio needs (or recommends) and whether it's done. */
internal data class SetupStep(
    val icon: ImageVector, val title: String, val detail: String, val done: Boolean,
    val required: Boolean, val action: String, val onAction: () -> Unit,
)

@Composable
internal fun rememberSetupSteps(isDefaultHome: Boolean, onMakeDefault: () -> Unit, onShadeSetup: () -> Unit): List<SetupStep> {
    val context = LocalContext.current
    // Re-check every time Folio comes back from a settings screen.
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { tick++ } }
    val bluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val contacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    fun open(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    return remember(tick, isDefaultHome) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        listOf(
            SetupStep(Icons.Rounded.Home, "Make Folio your Home app", "So Home, gestures and the fold effect are always Folio.",
                isDefaultHome, true, "Set") { onMakeDefault() },
            SetupStep(Icons.Rounded.Notifications, "Notification access",
                "Powers the Dynamic Island and Notification Center. Nothing leaves your phone.",
                IslandListenerService.hasAccess(context), true, "Allow") { open(IslandListenerService.accessSettingsIntent(context)) },
            SetupStep(Icons.Rounded.Accessibility, "Folio gestures service",
                "Lets pull-downs open system panels, and shows the dock handle and island in other apps.",
                SystemShadeAccessibilityService.isConnected(), true, "Turn on") { onShadeSetup() },
            SetupStep(Icons.Rounded.Devices, "Continue apps on cover screen: Always",
                "Keeps the cover screen on when you fold from Home. Settings › Display › Continue apps on cover screen.",
                foldStaysAwake(context), true, "Open") { open(Intent(Settings.ACTION_DISPLAY_SETTINGS)) },
            SetupStep(Icons.Rounded.LightMode, "Modify system settings",
                "Lets Control Center change brightness and rotation lock.",
                Settings.System.canWrite(context), false, "Allow") {
                open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
            },
            SetupStep(Icons.Rounded.DarkMode, "Do Not Disturb access", "Lets Control Center turn Do Not Disturb on and off.",
                notifications.isNotificationPolicyAccessGranted, false, "Allow") { open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
            SetupStep(Icons.Rounded.Contacts, "Contacts in Spotlight", "Search your contacts from Spotlight.",
                granted(context, Manifest.permission.READ_CONTACTS), false, "Allow") { contacts.launch(Manifest.permission.READ_CONTACTS) },
            SetupStep(Icons.Rounded.Headphones, "Bluetooth device names", "Shows “Connected to Galaxy Buds” in the island.",
                granted(context, Manifest.permission.BLUETOOTH_CONNECT), false, "Allow") { bluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT) },
        )
    }
}

private fun granted(context: Context, permission: String) =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun foldStaysAwake(context: Context) =
    runCatching { Settings.System.getString(context.contentResolver, "fold_lock_behavior_setting") }.getOrNull() == "stay_awake_on_fold_key"

@Composable
internal fun SetupChecklist(steps: List<SetupStep>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val done = steps.count { it.done }
        LinearProgressIndicator({ done / steps.size.toFloat() }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
        Text("$done of ${steps.size} done", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(true to "Needed for the full experience", false to "Optional").forEach { (required, heading) ->
            Text(heading.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                steps.filter { it.required == required }.forEachIndexed { index, step ->
                    if (index > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                    Row(Modifier.fillMaxWidth().clickable(enabled = !step.done, onClick = step.onAction).padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("setup-${step.title.lowercase().replace(' ', '-')}"), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(
                            if (step.done) Color(0xFF30D158).copy(alpha = .18f) else MaterialTheme.colorScheme.primary.copy(alpha = .12f)),
                            contentAlignment = Alignment.Center) {
                            Icon(if (step.done) Icons.Rounded.Check else step.icon, null,
                                tint = if (step.done) Color(0xFF30D158) else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(step.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(step.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(8.dp))
                        if (step.done) Text("Done", style = MaterialTheme.typography.labelLarge, color = Color(0xFF30D158))
                        else FilledTonalButton(onClick = step.onAction, contentPadding = PaddingValues(horizontal = 14.dp)) { Text(step.action) }
                    }
                }
            }
        }
        Text("TIPS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "Turn off Samsung Wallet's swipe-up: Samsung Wallet › ⋮ › Settings › Quick access.",
                "Open Wallet like iPhone: Settings › Advanced features › Side button › Double press › Samsung Wallet.",
                "If the dock handle fights the back swipe, lower right-edge back sensitivity in Navigation bar settings.",
                "Use ChatGPT, Claude or Perplexity on the side key: Settings › Apps › Default apps › Digital assistant app.",
            ).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
