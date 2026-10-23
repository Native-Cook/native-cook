package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A cassette deck: five keys that bottom out and latch, reels driven by the tape, cassettes you slide, and a reel
 * you can turn by hand. A Compose port of the React Native block; the whole screen is drawn in one canvas from a
 * fixed-step simulation, so no frame recomposes anything.
 */
@Composable
fun CassetteDeck(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = LocalActivity.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val reduced by rememberUpdatedState(rememberReducedMotion())
    val assets = remember(context) { CassetteDeckAssets(context) }
    val controller = remember(autoPlay, view) {
        CassetteDeckController(autoPlay) { haptic -> view.performCassetteDeckHaptic(haptic) }
    }
    var frameTick by remember { mutableLongStateOf(0L) }

    // The block is dark: light status bar icons, as the RN demo's `StatusBar style="light"`.
    DisposableEffect(activity, view) {
        val controls = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controls?.isAppearanceLightStatusBars
        controls?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controls.isAppearanceLightStatusBars = previous }
    }

    // RN `useFrameCallback`: the first frame counts as 16 ms.
    LaunchedEffect(controller) {
        var previousNanos = 0L
        while (isActive) {
            val nowNanos = withFrameNanos { it }
            val dtSeconds = if (previousNanos == 0L) 0.016 else (nowNanos - previousNanos) / 1_000_000_000.0
            previousNanos = nowNanos
            controller.frame(dtSeconds, reduced)
            if (controller.isBusy) frameTick++
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(CassetteDeckTheme.bg))
            .cassetteDeckTouch(controller),
    ) {
        val geometry = remember(maxWidth, maxHeight) { CassetteDeckGeometry(maxWidth.value.toDouble(), maxHeight.value.toDouble()) }
        val renderer = remember(geometry, assets, density) { CassetteDeckRenderer(geometry, assets, density) }

        // The tick is read only while drawing, so a frame redraws this canvas and recomposes nothing.
        Canvas(Modifier.fillMaxSize()) {
            frameTick
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val saved = native.save()
                native.scale(density, density)
                renderer.draw(native, controller.sim)
                native.restoreToCount(saved)
            }
        }

        // Accessibility only (RN :197-203, :504-509): the touches are routed by `cassetteDeckTouch`.
        Box(
            Modifier
                .offset(0.dp, geometry.swipeTop.dp)
                .size(geometry.width.dp, geometry.swipeHeight.dp)
                .semantics {
                    contentDescription = "Cassette. Swipe left or right to change the cassette"
                    customActions = listOf(
                        CustomAccessibilityAction("Next cassette") {
                            controller.nudge(1)
                            true
                        },
                        CustomAccessibilityAction("Previous cassette") {
                            controller.nudge(-1)
                            true
                        },
                    )
                },
        )
        CassetteDeckTheme.keyLabels.forEachIndexed { index, label ->
            Box(
                Modifier
                    .offset(geometry.keyLeft(index).dp, geometry.keyTop.dp)
                    .size(geometry.keyWidth.dp, geometry.keyHeight.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = label
                        // RN `onAccessibilityTap`: press, then let go 160 ms later.
                        onClick(label) {
                            scope.launch {
                                controller.press(index, true)
                                delay(160)
                                controller.press(index, false)
                            }
                            true
                        }
                    },
            )
        }
    }
}
