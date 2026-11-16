package com.ngocdevv.nativecook.blocks.progress.printlinescrub

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
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.progress.printlinescrub.PrintLineScrubMotion as M
import com.ngocdevv.nativecook.blocks.progress.printlinescrub.PrintLineScrubTheme as Theme
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Print Line Scrub: a slicer's layer preview on a phone. One figurine, printed in matte red up to a line and an unprinted mesh above
 * it; the frosted glass capsule that says "Printing Progress" is the layer slider: drag it and the cut plane moves 1:1 through the
 * figure, exposing a silhouette-derived section (strands round the wall, honeycomb inside, one hot layer line).
 *
 * Drag vertically: the line follows your finger, the capsule ducks under it so the cut shows; drag sideways: the figure tilts up to 4
 * deg; let go: the line springs back to the live layer carrying your velocity, and the layers you left hot cool.
 *
 * LOOP = 7.6 s ([PrintLineScrubMotion.LOOP]): press, drag down to the hips, hold and tilt, drag up to the shoulders, release and
 * catch up, the dots flash, cool, rest. A touch interrupts it; two seconds after the last touch it settles and the loop restarts.
 * `previewTime` pins the loop at that second (checks, previews).
 */
@Composable
fun PrintLineScrub(autoPlay: Boolean = false, previewTime: Double? = null) {
    val context = LocalContext.current
    val densityOwner = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberLiveReducedMotion()
    val vibe = remember(context) { PrintLineScrubHaptics.create(context) }
    val tick = remember { mutableIntStateOf(0) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var a11y by remember { mutableStateOf(PrintLineScrubA11y(M.PCT_LIVE.toInt(), M.layerAt(M.H_LIVE))) }
    BlockSystemBars(darkIcons = true)

    // the two renders decode off the main thread; nothing draws until they are in
    val renderer by produceState<PrintLineScrubRenderer?>(null, context.assets) {
        value = withContext(Dispatchers.IO) { PrintLineScrubRenderer(context.assets) }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(Theme.ground))) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val insetTop = with(densityOwner) { WindowInsets.statusBars.getTop(densityOwner).toDp().value }.toDouble()
        val insetBottom = with(densityOwner) { WindowInsets.navigationBars.getBottom(densityOwner).toDp().value }.toDouble()
        val g = remember(w, h, insetTop, insetBottom) { PrintLineScrubGeo(Theme.Layout(w, h, insetTop, insetBottom)) }
        val engine = remember(g, autoPlay, reduced) { PrintLineScrubEngine(autoPlay, reduced, g) }
        SideEffect {
            engine.haptic = { beat -> vibe.emit(beat) }
            engine.onArrive = { a11y = PrintLineScrubA11y(M.PCT_LIVE.toInt(), M.layerAt(M.H_LIVE)) }
        }
        val dens = densityOwner.density.toDouble()

        LaunchedEffect(engine, active, previewTime) {
            if (!active) return@LaunchedEffect
            var previous = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val dtMs = if (previous == 0L) 16.0 else min((nanos - previous) / 1e6, 50.0)
                    previous = nanos
                    if (previewTime != null) engine.frameAt(previewTime, dtMs / 1000) else engine.frame(dtMs)
                    // Read only inside the Canvas below: invalidates drawing, not the screen's composition.
                    tick.intValue++
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            tick.intValue
            val r = renderer ?: return@Canvas
            if (!r.ready) return@Canvas
            val c = drawContext.canvas.nativeCanvas
            val scale = drawContext.density.density
            c.save()
            c.scale(scale, scale)
            r.draw(c, g, engine, scale)
            c.restore()
        }

        // the handle: only the capsule (where it is now) takes the drag
        Box(
            Modifier.fillMaxSize().pointerInput(engine, dens) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val x0 = down.position.x / dens
                    val y0 = down.position.y / dens
                    if (!engine.inHandle(x0, y0)) return@awaitEachGesture
                    down.consume()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    engine.panBegin()
                    var finished = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!change.pressed) {
                                change.consume()
                                finished = true
                                engine.panFinalize(tracker.calculateVelocity().y / dens)
                                break
                            }
                            engine.panUpdate(change.position.x / dens - x0, change.position.y / dens - y0)
                            change.consume()
                        }
                    } finally {
                        if (!finished) engine.panFinalize(0.0)
                    }
                }
            },
        )

        // the scrubber as one adjustable control, for screen readers
        val restCy = g.capsuleCy(M.H_LIVE, 0.0)
        val step = { dir: Int ->
            a11y = engine.a11yStep(dir)
            true
        }
        Box(
            Modifier
                .offset(g.capL.dp, (restCy - g.capH / 2).dp)
                .size(g.capW.dp, g.capH.dp)
                .semantics {
                    contentDescription = Theme.A11Y_LABEL
                    stateDescription = "${a11y.pct} percent, layer ${a11y.layer} of ${Theme.LAYERS}"
                    progressBarRangeInfo = ProgressBarRangeInfo(a11y.pct.toFloat(), 0f..100f, 19)
                    setProgress { target -> step(if (target > a11y.pct) 1 else -1) }
                    customActions = listOf(
                        CustomAccessibilityAction("Increase") { step(1) },
                        CustomAccessibilityAction("Decrease") { step(-1) },
                    )
                },
        )
    }
}

