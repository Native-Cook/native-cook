package com.ngocdevv.nativecook.blocks.cards.glasspaymorph

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.min
import kotlinx.coroutines.withContext

/**
 * Solune's frosted payment card, faithfully ported from the RN reference.
 * LOOP = 4.4 s: lights depart at .30; card condenses .60–1.30; pay pulse at 1.60;
 * pill holds to 2.80; lights whip out and the slab half-turns to its rest at 3.90.
 * Tap toggles payment, vertical drag scrubs, horizontal drag tilts, lights can be thrown.
 * Two seconds after touch the driver returns home and resumes the pure loop.
 * Reduced motion stops the clock; taps crossfade card/pill over 220 ms.
 */
@Composable
fun GlassPayMorph(autoPlay: Boolean = false, previewTime: Double? = null) {
    val context = LocalContext.current
    val haptics = remember(context) { GlassPayMorphHaptics.create(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberLiveReducedMotion()
    val auto = autoPlay && !reduced
    val driver = remember(auto, reduced) { GlassPayMorphDriver() }
    val touch = remember(driver) { GlassPayMorphTouch(driver) }
    val renderer = remember(context.assets) { GlassPayMorphRenderer(context.assets) }
    val density = LocalDensity.current
    val insetTop = WindowInsets.statusBars.getTop(density).toFloat()
    val tick = remember { mutableIntStateOf(0) }
    val clock = remember(driver) { doubleArrayOf(0.0) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var paidTarget by remember { mutableStateOf(false) }
    var paidRequest by remember { mutableIntStateOf(0) }
    val paid = remember { Animatable(0f) }
    BlockSystemBars(darkIcons = false)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(active, reduced, auto, driver, previewTime) {
        if (!active || reduced || previewTime != null) return@LaunchedEffect
        var previous = 0L
        while (true) {
            withFrameNanos { frame ->
                val dt = if (previous == 0L) 0.016 else min((frame - previous) / 1e9, 0.05)
                previous = frame
                clock[0] += dt
                val event = driver.step(dt, clock[0], auto)
                if (event != 0) haptics.emit(event)
                // Read only inside Canvas below: invalidates drawing, not screen composition.
                tick.intValue++
            }
        }
    }

    fun toggleReduced() {
        paidTarget = paid.value <= 0.5f
        paidRequest++
    }

    LaunchedEffect(paidRequest) {
        // RN explicitly uses ReduceMotion.Never for this gentle accessibility crossfade.
        withContext(object : MotionDurationScale { override val scaleFactor = 1f }) {
            paid.animateTo(if (paidTarget) 1f else 0f, tween(220, easing = Easing { x ->
                if (x < 0.5f) 2 * x * x else 1 - (-2 * x + 2) * (-2 * x + 2) / 2
            }))
        }
    }

    Canvas(Modifier.fillMaxSize().background(Color(0xFF1B2135))
        .semantics {
            role = Role.Button
            contentDescription = "Solune card ending 4417. Hold near reader, or tap the card to pay."
            onClick(label = "Pays with the card, or shows the card again after paying.") {
                if (reduced) toggleReduced()
                else {
                    driver.takeOver(clock[0])
                    driver.toggle()
                }
                true
            }
        }
        .pointerInput(driver, reduced) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val scale = size.width / 402.0
                val dy = (size.height - 874 * scale) / 2
                val start = down.position
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                down.consume()
                if (!reduced && touch.begin(start.x / scale, (start.y - dy) / scale, clock[0])) haptics.emit(0)
                var vx = 0.0
                var vy = 0.0
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val velocity = tracker.calculateVelocity()
                        vx = velocity.x / scale
                        vy = velocity.y / scale
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        if (!reduced) touch.move((change.position.x - start.x) / scale,
                            (change.position.y - start.y) / scale, vx, vy, clock[0])
                        change.consume()
                    }
                } finally {
                    if (reduced) {
                        toggleReduced()
                    } else touch.end(vx, vy, clock[0])
                }
            }
        }) {
        tick.intValue
        val canvas = drawContext.canvas.nativeCanvas
        if (reduced) renderer.crossfade(canvas, size.width, size.height, insetTop, paid.value.toDouble())
        else {
            val scene = if (previewTime != null) GlassPayMorphMotion.sceneAt(previewTime)
            else GlassPayMorphMotion.sceneAt(driver.time(), driver.mods())
            renderer.draw(canvas, size.width, size.height, insetTop, scene)
        }
    }
}
