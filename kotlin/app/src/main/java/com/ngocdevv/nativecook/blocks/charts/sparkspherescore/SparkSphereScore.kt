package com.ngocdevv.nativecook.blocks.charts.sparkspherescore

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
import com.ngocdevv.nativecook.blocks.charts.sparkspherescore.SparkSphereScoreMotion as M
import com.ngocdevv.nativecook.blocks.charts.sparkspherescore.SparkSphereScoreTheme as Theme
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * Spark Sphere Score: one value, the pace of aging, drives the whole screen. Drag the speed ruler and the sparks change state of matter
 * (liquid at the pin's 0.8x, a hot gas of amber sparks toward 3.0x, a frozen mint lattice below 0.3x), the membrane wobbles or sharpens,
 * the score rolls like an odometer and the card's copy cross-fades; on release a slow spring cools the cell back to its reading.
 * A finger on the sphere is a warm probe: it agitates the sparks, dents the rim and spins the shell; a tap on the crystal sends a ring
 * of light through the lattice.
 *
 * LOOP = 8 s ([SparkSphereScoreMotion.LOOP]): rest, the finger lands, 0.8x up to 2.4x (gas), down through liquid into crystal at -0.6x,
 * a tap on the crystal, release, relax. A touch interrupts it; two seconds after the last touch it returns to rest and restarts.
 */
@Composable
fun SparkSphereScore(
    autoPlay: Boolean = false,
    defaultValue: Double = M.V_REST,
    snapBack: Boolean = true,
    touchable: Boolean = true,
    haptics: Boolean = true,
    onChange: ((Double) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    onPreviousWeek: (() -> Unit)? = null,
    previewTime: Double? = null,
) {
    val context = LocalContext.current
    val densityOwner = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberLiveReducedMotion()
    val vibe = remember(context) { SparkSphereScoreHaptics.create(context) }
    val tick = remember { mutableIntStateOf(0) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var a11yReading by remember { mutableDoubleStateOf(defaultValue) }
    BlockSystemBars(darkIcons = false)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val fontScale = densityOwner.fontScale.toDouble()
    val dens = densityOwner.density

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(Theme.GROUND0))) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val insetTop = with(densityOwner) { WindowInsets.statusBars.getTop(densityOwner).toDp().value }.toDouble()
        val insetBottom = with(densityOwner) { WindowInsets.navigationBars.getBottom(densityOwner).toDp().value }.toDouble()
        val layout = remember(w, h, insetTop, insetBottom, fontScale) { Theme.makeLayout(w, h, insetTop, insetBottom, fontScale) }
        val renderer = remember(context, layout, reduced) { SparkSphereScoreRenderer(context.assets, layout, reduced) }
        val bandFrom = remember { doubleArrayOf(M.BAND_FROM[0], M.BAND_FROM[1], M.BAND_FROM[2], M.BAND_FROM[3]) }
        val engine = remember(layout, autoPlay, reduced, snapBack, touchable) {
            SparkSphereScoreEngine(autoPlay, reduced, layout, defaultValue, snapBack, touchable, dens.toDouble(), bandFrom)
        }
        SideEffect {
            engine.cue = { kind -> if (haptics) vibe.emit(kind) }
            engine.onChange = onChange
        }

        LaunchedEffect(engine, active, previewTime) {
            if (!active) return@LaunchedEffect
            var previous = 0L
            if (previewTime != null) engine.pin(previewTime)
            while (true) {
                withFrameNanos { nanos ->
                    val dt = if (previous == 0L) 1.0 / 60 else min((nanos - previous) / 1e9, 0.05)
                    previous = nanos
                    engine.frame(dt)
                    if (engine.a11yReading != a11yReading) a11yReading = engine.a11yReading
                    // Read only inside the Canvas below: invalidates drawing, not the screen's composition.
                    tick.intValue++
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

        // one pointer handler for everything: the three chrome controls are presses, the sphere and the ruler are pans
        Box(
            Modifier.fillMaxSize().pointerInput(engine, layout, dens, onBack, onInfo, onPreviousWeek) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val x0 = (down.position.x / dens).toDouble()
                    val y0 = (down.position.y / dens).toDouble()
                    val hit = renderer.hit(x0, y0, onBack != null, onInfo != null, onPreviousWeek != null)
                    if (hit == SparkSphereScoreHit.NONE) return@awaitEachGesture
                    if (hit == SparkSphereScoreHit.BACK || hit == SparkSphereScoreHit.INFO || hit == SparkSphereScoreHit.PREV) {
                        down.consume()
                        var inside = true
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            inside = renderer.hit(change.position.x / dens.toDouble(), change.position.y / dens.toDouble(), true, true, true) == hit
                            change.consume()
                            if (!change.pressed) break
                        }
                        if (inside) when (hit) {
                            SparkSphereScoreHit.BACK -> onBack?.invoke()
                            SparkSphereScoreHit.INFO -> onInfo?.invoke()
                            else -> onPreviousWeek?.invoke()
                        }
                        return@awaitEachGesture
                    }
                    if (!touchable) return@awaitEachGesture
                    down.consume()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val disc = hit == SparkSphereScoreHit.DISC
                    if (disc) engine.probeBegin(x0 - layout.discCx, y0 - layout.discCy) else engine.rulerBegin(x0)
                    var lastX = x0
                    var lastY = y0
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            lastX = (change.position.x / dens).toDouble()
                            lastY = (change.position.y / dens).toDouble()
                            val vel = tracker.calculateVelocity()
                            if (disc) engine.probeUpdate(lastX - layout.discCx, lastY - layout.discCy, (vel.x / dens).toDouble(), (vel.y / dens).toDouble())
                            else engine.rulerUpdate(lastX)
                            change.consume()
                            if (!change.pressed) break
                        }
                    } finally {
                        val vel = tracker.calculateVelocity()
                        if (disc) engine.probeEnd(lastX - x0, lastY - y0) else engine.rulerEnd((vel.x / dens).toDouble())
                    }
                }
            },
        )

        // accessibility: the sphere as an image, the ruler as one adjustable control, the chrome buttons
        val b = renderer.rulerBounds()
        val scoreText = M.scoreOf(a11yReading)
        val deltaNow = scoreText - M.SCORE_CHRONO
        Box(
            Modifier
                .offset((layout.discCx - layout.discR).dp, (layout.discCy - layout.discR).dp)
                .size((2 * layout.discR).dp)
                .semantics {
                    contentDescription = String.format(
                        Locale.US, "%s %.1f, %.1f %s", Theme.SCORE_LABEL_COPY, scoreText, abs(deltaNow),
                        if (deltaNow > 0.049) Theme.OLDER else Theme.YOUNGER,
                    )
                },
        )
        Box(
            Modifier
                .offset(0.dp, b[0].dp)
                .size(layout.width.dp, (b[1] - b[0]).dp)
                .semantics {
                    contentDescription = "${Theme.RULER_LABEL}. ${Theme.RULER_HINT}"
                    stateDescription = String.format(Locale.US, "%.1fx. %s, %.1f", a11yReading, Theme.SCORE_LABEL_COPY, scoreText)
                    customActions = listOf(
                        CustomAccessibilityAction("Increase") { engine.nudge(1); true },
                        CustomAccessibilityAction("Decrease") { engine.nudge(-1); true },
                    )
                },
        )
        val buttons = listOf(
            Triple("Back", onBack, doubleArrayOf(layout.x(Theme.BACK_X) - 14, layout.yA(Theme.BACK_CY) - 22)),
            Triple("About vital age", onInfo, doubleArrayOf(layout.x(Theme.INFO_CX) - 22, layout.yA(Theme.INFO_CY) - 22)),
            Triple("Previous week", onPreviousWeek, doubleArrayOf(layout.x(Theme.CHEV_L) - 18, layout.yA(Theme.CHEV_CY) - 22)),
        )
        for ((label, action, at) in buttons) {
            if (action == null) continue
            Box(
                Modifier
                    .offset(at[0].dp, at[1].dp)
                    .size(44.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = label
                        onClick { action(); true }
                    },
            )
        }
    }
}
