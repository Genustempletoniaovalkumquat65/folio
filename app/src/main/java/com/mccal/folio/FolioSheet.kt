package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Folio's sheet: same API as Material's ModalBottomSheet (this package-level function shadows the
 * star-imported one in launcher files), but dark iOS-style glass, a slim handle, and Home blurred behind.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    @Suppress("UNUSED_PARAMETER") containerColor: Color = Color.Unspecified,
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    /** iOS Settings-style full-screen page that slides in, instead of a bottom sheet. */
    fullScreen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    DisposableEffect(Unit) { LauncherSheetsOpen.intValue++; onDispose { LauncherSheetsOpen.intValue-- } }
    if (fullScreen) { FullScreenPage(onDismissRequest, content); return }
    MaterialTheme(colorScheme = FolioSheetColors, typography = MaterialTheme.typography) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = onDismissRequest, modifier = modifier, sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = Color(0xFF1C1C1E).copy(alpha = .97f), contentColor = Color.White,
            scrimColor = Color.Black.copy(alpha = .35f),
            dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 5.dp)
                .background(Color.White.copy(alpha = .3f), RoundedCornerShape(3.dp))) },
            properties = properties, content = {
                // The sheet is its own window; hide the status bar there too so Home stays edge to edge.
                val view = androidx.compose.ui.platform.LocalView.current
                androidx.compose.runtime.LaunchedEffect(view) {
                    (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { w ->
                        androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).apply {
                            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                        }
                    }
                }
                content()
            },
        )
    }
}

@Composable
private fun FullScreenPage(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
            dismissOnBackPress = false)) {
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.LaunchedEffect(view) {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { w ->
                w.setDimAmount(0f)
                w.setWindowAnimations(0)
                androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).apply {
                    systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                }
            }
        }
        val slide = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(1f) }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            slide.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 500f))
        }
        MaterialTheme(colorScheme = FolioSheetColors, typography = MaterialTheme.typography) {
            androidx.compose.material3.Surface(Modifier.fillMaxSize().graphicsLayer {
                translationX = size.width * slide.value
            }, color = Color.Black, contentColor = Color.White) {
                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.folioSafeTop).navigationBarsPadding(), content = content)
            }
        }
    }
}
