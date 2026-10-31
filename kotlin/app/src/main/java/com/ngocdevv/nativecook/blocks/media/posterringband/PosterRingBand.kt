package com.ngocdevv.nativecook.blocks.media.posterringband

import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.rememberReducedMotion

/**
 * Twelve 92 × 128 dp posters on a radius-150 ring, focal length 820, roll 8 degrees.
 * The 9 s loop turns -360 degrees linearly; tilt = -16 + 3 sin(4πt/9) degrees.
 * A drag turns deltaX/150 radians, decays at 2.5/s for 2 s, then snaps over 0.6 s.
 */
@Composable
fun PosterRingBand(
    modifier: Modifier = Modifier,
    title: String = "ORBIT",
    dates: String = "Fest 26 · 12–14 Jun · Porto Vale",
    action: String = "Get festival passes",
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = LocalActivity.current?.window
    val density = LocalDensity.current.density
    val reduced = rememberReducedMotion()
    val auto by rememberUpdatedState(autoPlay)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val motion = remember { PosterRingBandMotion() }
    val revision = remember { mutableIntStateOf(0) }
    val art = remember(context) { PosterRingBandArt(context.assets) }
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val safeTop = insets.calculateTopPadding().value
    val safeBottom = insets.calculateBottomPadding().value

    DisposableEffect(window, view) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (oldStatus != null) controller.isAppearanceLightStatusBars = oldStatus
            if (oldNavigation != null) controller.isAppearanceLightNavigationBars = oldNavigation
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(active, reduced) {
        if (!active || reduced) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                motion.advance((now - previous) / 1e9, auto)
                previous = now
                revision.intValue++
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFEDEBE6))) {
        val footerTop = maxHeight.value - safeBottom - 72f
        Canvas(
            Modifier.fillMaxSize()
                .semantics { contentDescription = "$title. $dates. Festival line-up ring. Drag to spin." }
                .pointerInput(reduced, density, footerTop) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.position.y / density >= footerTop) return@awaitEachGesture
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        motion.begin(down.position.x / density.toDouble())
                        var panning = false
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (!panning && (change.position - down.position).getDistance() >= 4f * density) panning = true
                                if (panning) {
                                    change.consume()
                                    val velocity = tracker.calculateVelocity().x / density
                                    if (motion.drag(change.position.x / density.toDouble(), velocity.toDouble())) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                }
                            }
                        } finally {
                            motion.end()
                        }
                    }
                },
        ) {
            revision.intValue
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.scale(density, density)
                art.draw(native, size.width / density, size.height / density, safeTop, safeBottom, motion, title, dates, action)
                native.restore()
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = (safeBottom + 14).dp)
                .fillMaxWidth().height(58.dp)
                .semantics { role = Role.Button; contentDescription = action },
        )
    }
}
