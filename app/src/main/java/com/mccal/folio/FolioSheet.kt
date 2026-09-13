package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    content: @Composable ColumnScope.() -> Unit,
) {
    DisposableEffect(Unit) { LauncherSheetsOpen.intValue++; onDispose { LauncherSheetsOpen.intValue-- } }
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
