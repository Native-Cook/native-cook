package com.ngocdevv.nativecook.blocks.charts.thermalglasstiles

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.charts.thermalglasstiles.ThermalGlassTilesData as D
import com.ngocdevv.nativecook.blocks.charts.thermalglasstiles.ThermalGlassTilesMotion as M
import com.ngocdevv.nativecook.blocks.charts.thermalglasstiles.ThermalGlassTilesTheme as Theme
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Thermal Glass Tiles: a run summary over a thermal image. Three frosted glass tiles lens a heat field in which a runner's heat follows
 * the effort; drag along the route (or the timeline) and the run replays: distance, time, heart rate, pace, contact time and vertical
 * oscillation all change together, the big figures rolling like an odometer. Tap a lower tile to jump to its extreme.
 *
 * LOOP = 8 s ([ThermalGlassTilesMotion.LOOP]): rest, scrub to the start, climb to the hill and hold it white-hot, continue, reverse once,
 * coast, park, return to the summary. A touch interrupts it; three seconds after the last touch it springs home and restarts.
 */
@Composable
fun ThermalGlassTiles(autoPlay: Boolean = false, units: String = "km", previewTime: Double? = null) {
    val context = LocalContext.current
    val densityOwner = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberLiveReducedMotion()
    val vibe = remember(context) { ThermalGlassTilesHaptics.create(context) }
    val tick = remember { mutableIntStateOf(0) }
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var a11y by remember { mutableStateOf("") }
    BlockSystemBars(darkIcons = true)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val fontScale = densityOwner.fontScale.toDouble()
    val route = remember { D.resampleRoute(D.ROUTE, M.ROUTE_SAMPLES) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(Theme.PAGE))) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val insetTop = with(densityOwner) { WindowInsets.statusBars.getTop(densityOwner).toDp().value }.toDouble()
        val insetBottom = with(densityOwner) { WindowInsets.navigationBars.getBottom(densityOwner).toDp().value }.toDouble()
        val layout = remember(w, h, insetTop, insetBottom) { Theme.makeLayout(w, h, insetTop, insetBottom) }
        val engine = remember(layout, autoPlay, reduced, units) {
            ThermalGlassTilesEngine(autoPlay, reduced, D.RUN, route.first, layout.s, units)
        }
        val renderer = remember(layout, engine, fontScale) { ThermalGlassTilesRenderer(context.assets, layout, fontScale, engine, route.second) }
        SideEffect {
            engine.onBuzz = { kind -> vibe.emit(kind) }
            engine.foreground = foreground
        }
        val dens = densityOwner.density

        LaunchedEffect(engine, renderer.ready, previewTime) {
            if (!renderer.ready) return@LaunchedEffect
            var previous = 0L
            engine.start()
            if (previewTime != null) engine.pin(previewTime)
            while (true) {
                withFrameNanos { nanos ->
                    val dt = if (previous == 0L) 1.0 / 60 else min((nanos - previous) / 1e9, 0.1)
                    previous = nanos
                    engine.frame(nanos / 1e9, dt)
                    if (engine.dirty) {
                        engine.dirty = false
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
            renderer.draw(c, scale)
            c.restore()
        }

        // one pointer handler: a drag on the large tile scrubs the run, a tap on a lower tile jumps to its extreme
        Box(
            Modifier.fillMaxSize().pointerInput(engine, renderer, dens) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val x0 = (down.position.x / dens).toDouble()
                    val y0 = (down.position.y / dens).toDouble()
                    fun inside(r: ThermalGlassTilesRect, x: Double, y: Double) = x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h
                    val large = renderer.large
                    when {
                        inside(large, x0, y0) -> {
                            down.consume()
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            engine.panBegin(x0 - large.x, y0 - large.y)
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    if (!change.pressed) {
                                        change.consume()
                                        engine.panEnd()
                                        break
                                    }
                                    val v = tracker.calculateVelocity()
                                    engine.panUpdate(
                                        change.position.x / dens - large.x, change.position.y / dens - large.y,
                                        (v.x / dens).toDouble(), (v.y / dens).toDouble(),
                                    )
                                    change.consume()
                                }
                            } finally {
                                engine.panFinalize()
                            }
                        }
                        inside(renderer.contact, x0, y0) || inside(renderer.vertical, x0, y0) -> {
                            down.consume()
                            val isContact = inside(renderer.contact, x0, y0)
                            val tile = if (isContact) renderer.contact else renderer.vertical
                            engine.tapBegin(if (isContact) 122.0 else 280.0, 583.0)
                            var ok = true
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!inside(tile, (change.position.x / dens).toDouble(), (change.position.y / dens).toDouble())) ok = false
                                change.consume()
                                if (!change.pressed) break
                            }
                            if (ok) if (isContact) engine.contactTap() else engine.verticalTap()
                        }
                    }
                }
            },
        )

        // accessibility: the replay as one adjustable control, and the two tiles as buttons
        LaunchedEffect(engine) {
            while (true) {
                withFrameNanos { }
                val s = engine.sample
                val mi = units == "mi"
                val text = "${(s.distance * (if (mi) 0.621371 else 1.0)).let { "%.1f".format(it) }} ${if (mi) "miles" else "kilometres"}, " +
                    "${s.bpm.roundToInt()} beats per minute, pace ${D.timeText(s.pace * (if (mi) 1.609344 else 1.0))} per ${if (mi) "mile" else "kilometre"}"
                if (text != a11y) a11y = text
                kotlinx.coroutines.delay(250)
            }
        }
        val large = renderer.large
        Box(
            Modifier
                .offset(large.x.dp, large.y.dp)
                .size(large.w.dp, large.h.dp)
                .semantics {
                    contentDescription = "Run replay. Drag along the route to replay your run. Swipe up or down to step one kilometre."
                    stateDescription = a11y
                    customActions = listOf(
                        CustomAccessibilityAction("Next kilometre") { engine.nudge(1); true },
                        CustomAccessibilityAction("Previous kilometre") { engine.nudge(-1); true },
                    )
                },
        )
        Box(
            Modifier
                .offset(renderer.contact.x.dp, renderer.contact.y.dp)
                .size(renderer.contact.w.dp, renderer.contact.h.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Contact time. Replay the longest ground contact. ${engine.sample.contact.roundToInt()} milliseconds"
                    onClick { engine.contactTap(); true }
                },
        )
        Box(
            Modifier
                .offset(renderer.vertical.x.dp, renderer.vertical.y.dp)
                .size(renderer.vertical.w.dp, renderer.vertical.h.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Vertical oscillation. Replay the highest bounce. ${"%.1f".format(engine.sample.oscillation)} centimetres"
                    onClick { engine.verticalTap(); true }
                },
        )
    }
}

