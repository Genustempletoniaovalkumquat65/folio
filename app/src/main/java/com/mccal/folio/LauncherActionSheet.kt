package com.mccal.folio

import androidx.compose.ui.res.stringResource
import androidx.activity.OnBackPressedCallback
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.add_to_home), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close empty space options") }
        }
        Text(stringResource(R.string.choose_what_belongs_in_this_space), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        ActionRow(Icons.Rounded.Widgets, "Widgets", onWidgets, Modifier.testTag("empty-space-widgets"))
        ActionRow(Icons.Rounded.Wallpaper, "Wallpaper", onWallpaper, Modifier.testTag("empty-space-wallpaper"))
        onAddPage?.let { ActionRow(Icons.Rounded.AddToPhotos, "Add page", it, Modifier.testTag("empty-space-add-page")) }
        onRemovePage?.let { ActionRow(Icons.Rounded.DeleteOutline, "Remove this empty page", it, Modifier.testTag("empty-space-remove-page")) }
        ActionRow(Icons.Rounded.Tune, "Customize launcher", onCustomize, Modifier.testTag("empty-space-customize"))
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
