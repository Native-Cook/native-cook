package com.ngocdevv.nativecook.blocks.progress.breathvolumelungs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.progress.breathvolumelungs.BreathVolumeLungsMotion as M
import com.ngocdevv.nativecook.blocks.progress.breathvolumelungs.BreathVolumeLungsTheme as Theme
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.min

/**
 * Breath Volume Lungs: a live halftone instrument. The user breathes out and the lungs empty, each lobe deflating towards its outlet
 * while two streams carry its dots into the pile, the litres count up to 5.5, "Well done" lands, then Reset breathes the dots back up.
 * Drag the knob or press and hold anywhere above the card to exhale; Reset and Finish Test are the two controls.
 *
 * LOOP = 8.5 s ([BreathVolumeLungsMotion.LOOP]): rest, exhale (0.7-3.9 s), result (4.3 s), refill (6.3-7.9 s), rest. A touch interrupts it;
 * two seconds after the result it breathes back to rest and restarts. Reduce Motion: the rest frame, no loop.
 */
@Composable
fun BreathVolumeLungs(autoPlay: Boolean = false, haptics: Boolean = true) {
    val context = LocalContext.current
    val densityOwner = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberLiveReducedMotion()
    val vibe = remember(context) { BreathVolumeLungsHaptics.create(context) }
    val tick = remember { mutableIntStateOf(0) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var reading by remember { mutableDoubleStateOf(0.0) }
    var announcement by remember { mutableStateOf("") }
    BlockSystemBars(darkIcons = false)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val dens = densityOwner.density
    val fontScale = densityOwner.fontScale.toDouble()

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(Theme.GROUND))) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val insetTop = with(densityOwner) { WindowInsets.statusBars.getTop(densityOwner).toDp().value }.toDouble()
        val insetBottom = with(densityOwner) { WindowInsets.navigationBars.getBottom(densityOwner).toDp().value }.toDouble()
        val layout = remember(w, h, insetTop, insetBottom) { Theme.makeLayout(w, h, insetTop, insetBottom) }
        val renderer = remember(context, layout, reduced, fontScale) { BreathVolumeLungsRenderer(context.assets, layout, reduced, fontScale) }
        val engine = remember(layout, autoPlay, reduced) { BreathVolumeLungsEngine(autoPlay, reduced, layout) }
        SideEffect {
            engine.cue = { kind -> if (haptics) vibe.emit(kind) }
            engine.announce = { v -> announcement = String.format(Locale.US, "%.1f %s", v, Theme.UNIT) }
        }

        LaunchedEffect(engine, active) {
            if (!active) return@LaunchedEffect
            engine.wake()
            var previous = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val dt = if (previous == 0L) 1.0 / 60 else min((nanos - previous) / 1e9, 0.05)
                    previous = nanos
                    if (engine.needsFrame()) engine.frame(dt)
                    if (engine.dirty) {
                        engine.dirty = false
                        if (engine.a11yReading != reading) reading = engine.a11yReading
                        // Read only inside the Canvas below: invalidates drawing, not the screen's composition.
                        tick.intValue++
                    }
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            tick.intValue
            val c = drawContext.canvas.nativeCanvas
            val scale = drawContext.density.density
            c.save()
            c.scale(scale, scale)
            renderer.draw(c, engine)
            c.restore()
        }

        // one pointer handler: the two buttons are presses, the knob is a drag, the area above is press-and-hold
        Box(
            Modifier.fillMaxSize().pointerInput(engine, dens) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val x0 = (down.position.x / dens).toDouble()
                    val y0 = (down.position.y / dens).toDouble()
                    val zone = engine.hit(x0, y0)
                    if (zone == BreathVolumeLungsEngine.Hit.NONE) return@awaitEachGesture
                    down.consume()
                    when (zone) {
                        BreathVolumeLungsEngine.Hit.RESET, BreathVolumeLungsEngine.Hit.FINISH -> {
                            val press = if (zone == BreathVolumeLungsEngine.Hit.RESET) engine.resetPress else engine.finishPress
                            press.to(1.0, M.SNAP); engine.dirty = true
                            var inside = true
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                inside = engine.hit(change.position.x / dens.toDouble(), change.position.y / dens.toDouble()) == zone
                                change.consume()
                                if (!change.pressed) break
                            }
                            press.to(0.0, M.SNAP); engine.dirty = true
                            if (inside) if (zone == BreathVolumeLungsEngine.Hit.RESET) engine.reset() else engine.finish()
                        }
                        BreathVolumeLungsEngine.Hit.KNOB -> {
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            engine.dragBegin()
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    val vel = tracker.calculateVelocity()
                                    engine.dragUpdate((change.position.x / dens).toDouble() - x0, (vel.x / dens).toDouble())
                                    change.consume()
                                    if (!change.pressed) break
                                }
                            } finally {
                                val vel = tracker.calculateVelocity()
                                engine.dragEnd((vel.x / dens).toDouble())
                                engine.dragFinalize()
                            }
                        }
                        else -> {
                            engine.holdBegin()
                            var released = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    // LongPress maxDistance 32: moving further ends the hold like a release
                                    val away = hypot((change.position.x / dens).toDouble() - x0, (change.position.y / dens).toDouble() - y0)
                                    if (!released && away > 32) { released = true; engine.holdEnd() }
                                    if (!change.pressed) break
                                }
                            } finally {
                                if (!released) engine.holdEnd()
                            }
                        }
                    }
                }
            },
        )

        // accessibility: the whole screen as a hold button, the knob as one adjustable control, the two buttons
        val s = layout.s
        Box(
            Modifier
                .offset(20.dp, (insetTop + 80 * s).dp)
                .size((w - 40).dp, (engine.knobY - 30 * s - insetTop - 80 * s).coerceAtLeast(1.0).dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "${Theme.A11Y_LABEL}. ${Theme.A11Y_HINT}"
                    stateDescription = String.format(Locale.US, "%.1f %s", reading, Theme.UNIT)
                    onClick(label = "Start a full exhale") { engine.a11yTest(); true }
                },
        )
        Box(
            Modifier
                .offset(maxOf(20.0, engine.knobX0 - 24 * s).dp, (engine.knobY - 28 * s).dp)
                .size((w - 20 - maxOf(20.0, engine.knobX0 - 24 * s)).dp, (56 * s).dp)
                .semantics {
                    contentDescription = "${Theme.A11Y_LABEL}. Swipe up or down to adjust by half a litre."
                    stateDescription = String.format(Locale.US, "%.1f %s", reading, Theme.UNIT)
                    customActions = listOf(
                        CustomAccessibilityAction("Increase") { engine.nudge(1); true },
                        CustomAccessibilityAction("Decrease") { engine.nudge(-1); true },
                        CustomAccessibilityAction("Start a full exhale") { engine.a11yTest(); true },
                    )
                },
        )
        Box(
            Modifier
                .offset(engine.resetX.dp, engine.buttonY.dp)
                .size(engine.resetW.dp, engine.buttonH.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Reset breath test"
                    onClick { engine.reset(); true }
                },
        )
        Box(
            Modifier
                .offset(engine.finishX.dp, engine.buttonY.dp)
                .size(engine.finishW.dp, engine.buttonH.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Finish breath test"
                    onClick { engine.finish(); true }
                },
        )
        Box(Modifier.size(1.dp).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = announcement })
    }
}
