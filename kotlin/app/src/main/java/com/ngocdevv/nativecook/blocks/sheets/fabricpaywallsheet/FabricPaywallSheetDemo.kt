package com.ngocdevv.nativecook.blocks.sheets.fabricpaywallsheet

import android.view.ContextThemeWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ngocdevv.nativecook.R

/**
 * The original app's content view, `PurchaseScreen`, hosted as it is: the white screen with the
 * Purchase button, and the sheet it opens. What its Activity did around the views is done here:
 * the theme, the light system bars, back closing the sheet, pausing the fabric with the lifecycle,
 * and keeping the plan and the open sheet across a saved state.
 */
@Composable
fun FabricPaywallSheetDemo() {
    val context = LocalContext.current
    val holder = remember { PurchaseScreenHolder() }
    // onSaveInstanceState: "plan" and "sheet", read from the view when the state is saved.
    val restored = rememberSaveable(
        saver = Saver<Pair<String, Boolean>, Pair<String, Boolean>>(
            save = { initial -> holder.screen?.let { it.selected.name to it.isSheetShown } ?: initial },
            restore = { it },
        ),
    ) { Plan.YEARLY.name to false }
    var sheetShown by remember { mutableStateOf(false) }
    val screen = remember(context) {
        // The views take their defaults from the original app's light theme, not the gallery's.
        PurchaseScreen(
            context = ContextThemeWrapper(context, R.style.fabricpaywallsheet_Theme_Purchase),
            initialPlan = runCatching { Plan.valueOf(restored.first) }.getOrNull() ?: Plan.YEARLY,
        ).also { holder.screen = it }
    }
    DisposableEffect(screen) {
        screen.onSheetShownChanged = { sheetShown = it }
        sheetShown = screen.isSheetShown
        if (restored.second && !screen.isSheetShown) screen.post { screen.present() }
        onDispose { screen.onSheetShownChanged = null }
    }

    FabricPaywallSheetSystemBars()

    // Back closes the sheet first; with no sheet open it is the gallery's (the original finished).
    BackHandler(enabled = sheetShown) { screen.dismiss() }

    // onResume / onPause
    LifecycleResumeEffect(screen) {
        screen.resumeFabric()
        onPauseOrDispose { screen.pauseFabric() }
    }

    AndroidView(factory = { screen }, modifier = Modifier.fillMaxSize())
}

private class PurchaseScreenHolder {
    var screen: PurchaseScreen? = null
}

/**
 * The screen is white, so the original theme asked for dark icons on both system bars. The
 * gallery's activity uses light icons, so they are switched while the block is open and put back
 * when it closes. The rest of the original window setup (transparent bars, drawing under them, no
 * navigation bar contrast scrim) is what the gallery's edge-to-edge activity already does.
 */
@Composable
private fun FabricPaywallSheetSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val lightStatusBars = controller.isAppearanceLightStatusBars
        val lightNavigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatusBars
            controller.isAppearanceLightNavigationBars = lightNavigationBars
        }
    }
}
