// Full-screen gallery entry, native accessibility targets, and one lifecycle-bound frame clock.
package com.ngocdevv.nativecook.blocks.progress.horizonupload

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive

@Composable
fun HorizonUploadDemo() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val view = LocalView.current
    val window = LocalActivity.current?.window
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = horizonReducedMotion()
    val strings = remember(configuration) { HorizonStrings(context) }
    val assets = remember { HorizonAssets(context) }
    val state = remember { HorizonState(HorizonHaptics(context), autoPlay = true) }
    SideEffect { state.setReduced(reduced) }
    LaunchedEffect(state, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            state.setActive(true)
            try { while (isActive) withFrameNanos { state.frame(it) } }
            finally { state.setActive(false) }
        }
    }
    DisposableEffect(window, view) {
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val status = bars?.isAppearanceLightStatusBars
        val navigation = bars?.isAppearanceLightNavigationBars
        bars?.isAppearanceLightStatusBars = false; bars?.isAppearanceLightNavigationBars = false
        onDispose {
            if (status != null) bars.isAppearanceLightStatusBars = status
            if (navigation != null) bars.isAppearanceLightNavigationBars = navigation
        }
    }
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val top = insets.getTop(density) / density.density
    val bottom = insets.getBottom(density) / density.density
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val geometry = remember(maxWidth, maxHeight, top, bottom, density) {
            HorizonGeometry(maxWidth.value, maxHeight.value, top, bottom, density.density, density.fontScale)
        }
        val renderer = remember(assets, geometry, strings) { HorizonRenderer(assets, geometry, strings) }
        Box(Modifier.fillMaxSize().horizonGestures(state, geometry).drawWithCache {
            onDrawBehind {
                // Reading the invalidation token here schedules drawing, never per-frame composition.
                state.frame
                renderer.draw(drawContext.canvas.nativeCanvas, state)
            }
        }) {
            Box(Modifier.offset(geometry.x.dp, geometry.y.dp).size(geometry.cardWidth.dp, 240.dp).graphicsLayer {
                state.frame
                translationX = state.shiftX.value * density.density
                translationY = (state.shiftY.value - 46 * state.lift) * density.density
                scaleX = state.scale; scaleY = state.scale
            }) {
                Box(Modifier.offset(geometry.copyLeft.dp, 68.dp).size((geometry.cardWidth - 24 - geometry.copyLeft).dp, 44.dp)
                    .semantics { contentDescription = "${strings.name}, ${strings.size}" })
                Box(Modifier.offset(24.dp, 158.dp).size(geometry.panelWidth.dp, 58.dp).semantics {
                    role = Role.Button
                    contentDescription = strings.action(state.phase)
                    stateDescription = "${strings.labels[state.label.ordinal]}, ${state.percent} %"
                    progressBarRangeInfo = ProgressBarRangeInfo(state.percent.toFloat(), 0f..100f, 100)
                    onClick(label = strings.action(state.phase)) { state.toggle(); true }
                    if (!state.panelAvailable) disabled()
                })
                Box(Modifier.offset((geometry.cardWidth - 48).dp, 4.dp).size(44.dp).semantics {
                    role = Role.Button
                    contentDescription = if (state.retryAvailable) strings.again else strings.cancel
                    onClick(label = if (state.retryAvailable) strings.again else strings.cancel) { state.close(); true }
                    if (!state.closeAvailable) disabled()
                })
            }
            Box(Modifier.offset(24.dp, (geometry.height - bottom - 62).dp).size((geometry.width - 48).dp, 34.dp).semantics {
                contentDescription = strings.hint
            })
            // Live region announces completion/cancel, without announcing every percentage frame.
            Box(Modifier.size(1.dp).semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = when (state.phase) {
                    HorizonPhase.Done -> strings.complete
                    HorizonPhase.Stopped -> strings.canceled
                    else -> ""
                }
            })
        }
    }
}
