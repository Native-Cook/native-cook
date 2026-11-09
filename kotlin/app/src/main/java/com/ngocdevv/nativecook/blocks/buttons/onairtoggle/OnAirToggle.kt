package com.ngocdevv.nativecook.blocks.buttons.onairtoggle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.hypot
import kotlinx.coroutines.channels.Channel

/** Murmur's studio: the geometry, materials and six-second timeline of OnAirToggle.tsx. */
@Composable
fun OnAirToggle(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val reduced = rememberLiveReducedMotion()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // A live change to Remove animations starts in the same legible off state as mounting.
    val motion = remember(reduced) { OnAirToggleMotion() }
    val tick = remember(motion) { mutableIntStateOf(0) }
    val wake = remember(motion) { Channel<Unit>(Channel.CONFLATED) }
    var dark by remember(motion) { mutableStateOf(false) }
    BlockSystemBars(darkIcons = !dark)
    val art = remember(density.density, density.fontScale) {
        OnAirToggleArt(density.density, density.fontScale)
    }
    val safeTop = WindowInsets.safeDrawing.getTop(density) / density.density
    val safeBottom = WindowInsets.safeDrawing.getBottom(density) / density.density

    // Only Canvas observes tick. Composition changes at the room's midpoint, for system bars
    // and switch semantics; it never observes the continuously changing frame values.
    LaunchedEffect(active, reduced, autoPlay, motion) {
        if (!active) return@LaunchedEffect
        var previous = 0L
        while (true) {
            if (!motion.needsFrames(autoPlay, reduced)) {
                previous = 0L
                wake.receive()
                continue
            }
            withFrameNanos { nanos ->
                val dt = if (previous == 0L) 0.0 else (nanos - previous) / 1e9
                previous = nanos
                motion.advance(dt, autoPlay, reduced)
                dark = motion.frame.room > 0.5
                tick.intValue++
            }
        }
    }

    fun settle(to: Double) {
        motion.settle(to, reduced)
        tick.intValue++
        wake.trySend(Unit)
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = maxWidth.value
        val height = maxHeight.value
        val cy = height * 0.48f
        val text = remember(width, height, safeTop, safeBottom, density.density, density.fontScale) {
            OnAirToggleText(density.density, density.fontScale, width, height, safeTop, safeBottom)
        }
        Canvas(Modifier.fillMaxSize()) {
            tick.intValue
            withTransform({ scale(density.density, density.density, pivot = Offset.Zero) }) {
                art.draw(this, width, height, motion.frame, motion.cross, motion.meterTime, reduced)
                text.draw(drawContext.canvas.nativeCanvas, motion.frame)
            }
        }
        Box(
            Modifier.offset((width / 2 - 150 - 12).dp, (cy - 74 - 16).dp)
                .size(324.dp, 198.dp)
                .semantics {
                    contentDescription = "On air"
                    role = Role.Switch
                    toggleableState = if (dark) ToggleableState.On else ToggleableState.Off
                    onClick {
                        val position = if (reduced) motion.cross else motion.frame.pos
                        settle(if (position > 0.5) 0.0 else 1.0)
                        true
                    }
                }
                .pointerInput(motion, reduced) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragging = false
                        var cancelled = false
                        var movedTooFar = false
                        var end = down
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || change.isConsumed) {
                                cancelled = true
                                break
                            }
                            end = change
                            val delta = change.position - down.position
                            val distance = hypot(delta.x, delta.y) / this.density
                            if (distance > 10f) movedTooFar = true
                            if (!reduced && !dragging && change.pressed && distance >= 6f) {
                                // Capture the live knob after slop, then apply translation from down.
                                motion.beginDrag()
                                dragging = true
                                wake.trySend(Unit)
                            }
                            if (dragging) {
                                change.consume()
                                motion.drag((delta.x / this.density).toDouble())
                                tick.intValue++
                            }
                            if (!change.pressed) break
                        }
                        if (dragging) {
                            settle(if (motion.frame.pos > 0.5) 1.0 else 0.0)
                        } else if (!cancelled && !movedTooFar &&
                            end.uptimeMillis - down.uptimeMillis <= 500 &&
                            end.position.x in 0f..size.width.toFloat() &&
                            end.position.y in 0f..size.height.toFloat()
                        ) {
                            val position = if (reduced) motion.cross else motion.frame.pos
                            settle(if (position > 0.5) 0.0 else 1.0)
                        }
                    }
                },
        )
    }
}
