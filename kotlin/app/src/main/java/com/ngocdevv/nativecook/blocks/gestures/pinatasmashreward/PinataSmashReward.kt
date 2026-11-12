package com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * A glossy daily piñata: hop, weighted jolts, progressive cracks, slow-motion burst, bank and reset.
 * LOOP = 8.4 s. Tap twelve times, drag past 8 pt to swing, and release with momentum capped at
 * 520 pt/s. The source's two-angle reactions, 12 × 12 yaw mesh and atlas are preserved. Two seconds
 * after touch, the round returns and autoPlay resumes. Reduced motion keeps the rest frame; taps
 * step the bar, and tap twelve crossfades to the burst still with six sweets.
 */
@Composable
fun PinataSmashReward(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = remember(context) { PinataSmashRewardHaptics.create(context) }
    val reduced = rememberLiveReducedMotion()
    val auto = autoPlay && !reduced
    val runtime = remember { PinataSmashRewardInteraction() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var resources by remember(context.assets) { mutableStateOf<PinataSmashRewardResources?>(null) }
    val renderer = remember(resources) { resources?.let { PinataSmashRewardCanvas(it) } }
    var size by remember { mutableStateOf(IntSize(402, 874)) }
    val density = LocalDensity.current
    val insetTop = WindowInsets.statusBars.getTop(density).toDouble()
    val scale = size.width / 402.0
    val geometry = remember(size, insetTop) { PinataSmashRewardResources.PinataSmashRewardView(scale, (size.height / scale - 874) / 2, insetTop / scale - 62, size.width.toDouble(), size.height.toDouble()) }
    val geo = remember(resources, geometry) { resources?.geo(geometry) }
    BlockSystemBars(darkIcons = true)
    LaunchedEffect(auto) { runtime.resetDriver() }
    LaunchedEffect(reduced) { runtime.awake.value = true }
    LaunchedEffect(context.assets) { resources = withContext(Dispatchers.IO) { PinataSmashRewardResources.load(context) } }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val buzz by rememberUpdatedState<(Int) -> Unit> { kind ->
        if (kind > 0) {
            haptics.emit(kind)
            if (kind == 3) scope.launch { delay(120); haptics.emitSuccess() }
        }
    }
    // The gesture coroutine and accessibility action always see current resources and geometry.
    val currentGeo by rememberUpdatedState(geo)
    val currentGeometry by rememberUpdatedState(geometry)
    LaunchedEffect(active, runtime, auto, reduced, resources, runtime.awake.value) {
        if (!active || resources == null || !runtime.awake.value) return@LaunchedEffect
        var previous = 0L
        while (true) {
            val keepGoing = withFrameNanos { frame ->
                val dt = if (previous == 0L) 0.016 else min((frame - previous) / 1e9, 0.05)
                previous = frame
                runtime.step(dt, auto, reduced)
            }
            if (!keepGoing) { runtime.awake.value = false; break }
        }
    }
    Canvas(modifier.fillMaxSize().background(Color.White).onSizeChanged { if (it.width > 0 && it.height > 0) size = it }
        .semantics {
            role = Role.Button
            contentDescription = "Daily piñata. Keep tapping the piñata to break it open."
            onClick("Hits the piñata") {
                if (reduced) false else currentGeo?.let { g ->
                    PinataSmashRewardDriverMotion.tapAt(runtime.driver, g.body.x, g.body.y, g)
                    runtime.awake.value = true; runtime.tick.intValue++; true
                } ?: false
            }
        }
        .pointerInput(runtime, reduced) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val g = currentGeo ?: return@awaitEachGesture
                val transform = currentGeometry
                fun x(px: Float) = px / transform.s
                fun y(py: Float) = py / transform.s - transform.dy
                runtime.begin(x(down.position.x), y(down.position.y), g, reduced)
                val tracker = VelocityTracker(); tracker.addPosition(down.uptimeMillis, down.position)
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (!change.pressed) break
                        runtime.move(x(change.position.x), y(change.position.y), reduced)
                        change.consume()
                    }
                } finally {
                    val velocity = tracker.calculateVelocity()
                    buzz(runtime.end(velocity.x / transform.s, velocity.y / transform.s, g, reduced))
                }
            }
        },
    ) {
        runtime.tick.intValue
        val g = geo
        if (g != null && renderer != null) renderer.draw(drawContext.canvas.nativeCanvas, runtime.frame(g, reduced), geometry)
    }
}
