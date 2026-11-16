package com.ngocdevv.nativecook.blocks.pickers.beamshapetorch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.pickers.beamshapetorch.BeamShapeTorchTheme as Theme
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Beam Control: a black glass capsule, grown out of the Dynamic Island, holding a light whose beam obeys one law: the same flux
 * through a narrower cone burns hotter and throws further. One finger sets both axes: drag up/down for brightness, toward/away from
 * the beam's axis (or pinch) to focus it, or take the small sun at the end of the arc and drag it; tap to switch the light on/off;
 * tap outside to close the capsule into the island, tap the island to open it.
 *
 * Mount it over a full-screen page: it draws on a transparent canvas the size of its parent so the glass can cast its shadow and spill
 * its light onto the page, and only the capsule (or the island), and the page while the capsule is open, take touches.
 * `previewTime` pins the autoplay loop at that second (checks, previews).
 */
@Composable
internal fun BeamShapeTorchControl(
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    defaultBrightness: Double = 1.0,
    defaultSpread: Double = 1.0,
    on: Boolean? = null,
    onToggle: ((Boolean) -> Unit)? = null,
    onChange: ((brightness: Double, spread: Double, beamAngle: Double) -> Unit)? = null,
    onChangeEnd: ((brightness: Double, spread: Double, beamAngle: Double) -> Unit)? = null,
    angleMin: Double = 4.0,
    angleMax: Double = 40.0,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    anchor: Theme.Anchor = Theme.Anchor.Island,
    glyph: BeamShapeTorchGlyph = BeamShapeTorchGlyph.Torch,
    theme: Theme.Theme? = null,
    haptics: Boolean = true,
    closeOnOutsideTap: Boolean = true,
    accessibilityLabel: String? = null,
) {
    val context = LocalContext.current
    val densityOwner = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberLiveReducedMotion()
    val vibe = remember(context) { BeamShapeTorchHaptics.create(context) }
    val renderer = remember { BeamShapeTorchRenderer() }
    val th = remember(theme) { Theme.resolve(theme) }
    val aMin = min(max(angleMin, 1.2), 39.0)
    val aMax = min(max(angleMax, aMin + 1), 40.0)
    val startOpen = expanded ?: true
    val tick = remember { mutableIntStateOf(0) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var a11y by remember {
        mutableStateOf(BeamShapeTorchA11y(if (on == false) 0.0 else defaultBrightness.coerceIn(0.0, 1.0), defaultSpread.coerceIn(0.0, 1.0), startOpen))
    }
    val engine = remember(autoPlay, reduced, aMin, aMax) {
        BeamShapeTorchEngine(autoPlay, reduced, aMin, aMax, defaultBrightness, defaultSpread, startOpen, on)
    }
    SideEffect {
        engine.on = on
        engine.expanded = expanded
        engine.onToggle = onToggle
        engine.onChange = onChange
        engine.onChangeEnd = onChangeEnd
        engine.onExpandedChange = onExpandedChange
        engine.onReport = { a11y = it }
        engine.cue = { beat -> if (haptics) vibe.emit(beat) }
    }
    LaunchedEffect(engine, on, expanded) { engine.onPropChanged() }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(engine, active, previewTime) {
        if (!active) return@LaunchedEffect
        var previous = 0L
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (previous == 0L) 0.016 else min((nanos - previous) / 1e9, 0.05)
                previous = nanos
                if (previewTime != null) engine.frameAt(previewTime, dt) else engine.frame(dt)
                // Read only inside the Canvas below: invalidates drawing, not the screen's composition.
                if (engine.consumeRedraw()) tick.intValue++
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val insetTop = with(densityOwner) { WindowInsets.statusBars.getTop(densityOwner).toDp().value }.toDouble()
        val l = remember(w, h, insetTop, anchor) { Theme.layout(w, h, insetTop, anchor) }
        SideEffect { engine.layout(l) }
        val dens = densityOwner.density.toDouble()
        val isOpen = expanded ?: a11y.open

        Canvas(Modifier.fillMaxSize()) {
            tick.intValue
            val c = drawContext.canvas.nativeCanvas
            val scale = drawContext.density.density
            c.save()
            c.scale(scale, scale)
            renderer.draw(c, l, engine, th, glyph, scale)
            c.restore()
        }

        // Touch: the capsule (or the island) with a little slop is the control; while it is open a tap anywhere else closes it. A touch
        // that is neither is left alone.
        Box(
            Modifier.fillMaxSize().pointerInput(engine, closeOnOutsideTap, dens) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val x0 = down.position.x / dens
                    val y0 = down.position.y / dens
                    val onControl = engine.inHitArea(x0, y0)
                    val closing = !onControl && closeOnOutsideTap && engine.isOpenNow
                    if (!onControl && !closing) return@awaitEachGesture
                    down.consume()
                    if (closing) {
                        val tapped = withTimeoutOrNull(400L) {
                            var good = true
                            var done = false
                            while (!done) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                ch.consume()
                                if (hypot(ch.position.x - down.position.x, ch.position.y - down.position.y) / dens > 20) good = false
                                if (!ch.pressed) done = true
                            }
                            good && done
                        }
                        if (tapped == true) engine.closeTap()
                        return@awaitEachGesture
                    }
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    engine.panBegin(x0, y0)
                    // the finger is the centroid of the pressed pointers; a pointer joining or leaving does not make it jump
                    var baseX = x0
                    var baseY = y0
                    var cX = x0
                    var cY = y0
                    var count = 1
                    var pinchActive = false
                    var pinchDone = false
                    var span0 = 1.0
                    var scale = 1.0
                    var scaleV = 0.0
                    var lastT = down.uptimeMillis
                    try {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                if (pinchActive) engine.pinchEnd(scaleV)
                                val v = tracker.calculateVelocity()
                                engine.panEnd(cX, v.x / dens, v.y / dens)
                                ev.changes.forEach { it.consume() }
                                break
                            }
                            val nx = pressed.sumOf { it.position.x.toDouble() } / pressed.size / dens
                            val ny = pressed.sumOf { it.position.y.toDouble() } / pressed.size / dens
                            if (pressed.size != count) {
                                baseX += nx - cX
                                baseY += ny - cY
                                count = pressed.size
                                tracker.resetTracking()
                            }
                            cX = nx
                            cY = ny
                            val now = pressed[0].uptimeMillis
                            tracker.addPosition(now, Offset((nx * dens).toFloat(), (ny * dens).toFloat()))
                            if (pressed.size >= 2) {
                                val a = pressed[0].position
                                val b = pressed[1].position
                                val span = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()) / dens
                                if (!pinchActive && !pinchDone) {
                                    pinchActive = true
                                    span0 = max(span, 1.0)
                                    scale = 1.0
                                    scaleV = 0.0
                                    engine.pinchBegin()
                                } else if (pinchActive) {
                                    val ns = span / span0
                                    val dt = max((now - lastT) / 1000.0, 0.001)
                                    scaleV = scaleV * 0.6 + 0.4 * ((ns - scale) / dt)
                                    scale = ns
                                    engine.pinchUpdate(ns)
                                }
                            } else if (pinchActive) {
                                pinchActive = false
                                pinchDone = true
                                engine.pinchEnd(scaleV)
                            }
                            lastT = now
                            engine.panUpdate(cX, cY, cX - baseX, cY - baseY)
                            ev.changes.forEach { it.consume() }
                        }
                    } finally {
                        engine.panFinalize()
                    }
                }
            },
        )

        // Screen readers: one adjustable control, with actions for focus and on/off.
        val box = if (isOpen) l.cap else l.isl
        val percent = { v: Double -> "${(v * 100).roundToInt()} percent" }
        val value = if (!isOpen) "Closed"
        else if (a11y.b < 0.01) "Off"
        else "Brightness ${percent(a11y.b)}, beam ${if (a11y.w < 0.15) "focused" else "width ${percent(a11y.w)}"}"
        val act = { name: String ->
            a11y = engine.a11yAction(name, a11y)
            true
        }
        Box(
            Modifier
                .offset(box.x.dp, box.y.dp)
                .size(box.w.dp, box.h.dp)
                .semantics {
                    contentDescription = (accessibilityLabel ?: Theme.LABEL) + ". " + if (isOpen) Theme.HINT_OPEN else Theme.HINT_CLOSED
                    stateDescription = value
                    if (isOpen) {
                        progressBarRangeInfo = ProgressBarRangeInfo(a11y.b.toFloat(), 0f..1f, 9)
                        setProgress { target -> act(if (target > a11y.b) "increment" else "decrement") }
                    }
                    onClick(label = if (isOpen) "Switch the light on or off" else Theme.HINT_CLOSED) { act("activate") }
                    customActions = listOf(
                        CustomAccessibilityAction(Theme.NARROW) { act("narrow") },
                        CustomAccessibilityAction(Theme.WIDEN) { act("widen") },
                        CustomAccessibilityAction(Theme.CLOSE) { act("escape") },
                    )
                },
        )
    }
}
