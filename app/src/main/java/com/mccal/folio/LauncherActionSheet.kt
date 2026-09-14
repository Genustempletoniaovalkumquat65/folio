package com.mccal.folio

import androidx.compose.ui.res.stringResource
import androidx.activity.OnBackPressedCallback
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Handles Back on the ComponentDialog which owns a Material modal sheet. A regular Compose
 * BackHandler sees the activity owner inherited by the sheet composition, while platform Back is
 * dispatched to the dialog first.
 */
@Composable
internal fun ModalDialogBackHandler(onBack: () -> Unit) {
    val localView = androidx.compose.ui.platform.LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBack by rememberUpdatedState(onBack)
    val dispatcherOwner = remember(localView) {
        val dialogWindow = (localView.parent as? DialogWindowProvider)?.window
        dialogWindow?.decorView?.findViewTreeOnBackPressedDispatcherOwner()
    }
    DisposableEffect(dispatcherOwner, lifecycleOwner) {
        val callback = object : OnBackPressedCallback(dispatcherOwner != null) {
            override fun handleOnBackPressed() = currentOnBack()
        }
        dispatcherOwner?.onBackPressedDispatcher?.addCallback(lifecycleOwner, callback)
        onDispose { callback.remove() }
    }
}

@Composable
internal fun EmptySpaceActionSheet(onWidgets: () -> Unit, onWallpaper: () -> Unit,
    onCustomize: () -> Unit, onClose: () -> Unit, onAddPage: (() -> Unit)? = null, onRemovePage: (() -> Unit)? = null) {
    val maxHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * .75f).toDp() }
    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
        // iOS action sheet: a small centered title, grouped rows, then a separate Cancel.
        Column(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.add_to_home), color = Color.White.copy(alpha = .6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.choose_what_belongs_in_this_space), color = Color.White.copy(alpha = .5f), fontSize = 13.sp)
        }
        SheetGroup {
            Box(Modifier.testTag("empty-space-widgets")) { MenuRow("Add Widget", Icons.Rounded.Widgets, onClick = onWidgets) }
            MenuDivider()
            onAddPage?.let { Box(Modifier.testTag("empty-space-add-page")) { MenuRow("Add Page", Icons.Rounded.AddToPhotos, onClick = it) }; MenuDivider() }
            Box(Modifier.testTag("empty-space-wallpaper")) { MenuRow("Wallpaper", Icons.Rounded.Wallpaper, onClick = onWallpaper) }
            MenuDivider()
            Box(Modifier.testTag("empty-space-customize")) { MenuRow("Customize Folio", Icons.Rounded.Tune, onClick = onCustomize) }
            onRemovePage?.let { MenuDivider(); Box(Modifier.testTag("empty-space-remove-page")) { MenuRow("Remove This Empty Page", Icons.Rounded.DeleteOutline, destructive = true, onClick = it) } }
        }
        Spacer(Modifier.height(10.dp))
        SheetGroup { Text(stringResource(R.string.cancel), color = Color(0xFF0A84FF), fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Close empty space options", onClick = onClose).padding(vertical = 14.dp)) }
    }
}

@Composable
internal fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 52.dp), color = androidx.compose.ui.graphics.Color.Transparent,
        shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(tint.copy(alpha = .12f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = tint)
            }
            Spacer(Modifier.width(14.dp)); Text(label, style = MaterialTheme.typography.bodyLarge, color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface)
        }
    }
}
