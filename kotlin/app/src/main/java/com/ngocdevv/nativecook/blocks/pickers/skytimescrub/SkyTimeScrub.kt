package com.ngocdevv.nativecook.blocks.pickers.skytimescrub

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.pickers.skytimescrub.SkyTimeScrubMotion as M
import com.ngocdevv.nativecook.blocks.pickers.skytimescrub.SkyTimeScrubTheme as Theme
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import java.util.Calendar
import kotlin.math.min

/**
 * Sky Time Scrub: a time-of-day picker that is a scene. A real sky (single-scatter atmosphere, a bloom that flares while the eye's iris
 * lags, a sun disc reddened by air mass) behind a ruler; long press and slide to time travel, or grab the sun itself and drag it along
 * its dotted arch. The sky, the ruler, the time chip, the three solar-time rows and the controls' rims all follow.
 *
 * LOOP = 10 s ([SkyTimeScrubMotion.LOOP]): it opens on golden hour at 18:06, presses, scrubs through sunset into the night, runs
 * back to a dawn, coasts home and rests. A touch interrupts it; two seconds after the last touch it returns to now and restarts.
 */
@Composable
fun SkyTimeScrub(
    autoPlay: Boolean = false,
    now: Double? = null,
    hint: String? = Theme.HINT,
    haptics: Boolean = true,
    sound: Boolean = false,
    previewTime: Double? = null,
) {
    val context = LocalContext.current
    val densityOwner = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberLiveReducedMotion()
    val vibe = remember(context) { SkyTimeScrubHaptics.create(context) }
    val audio = remember(context, sound) { if (sound) SkyTimeScrubAudio(context) else null }
    DisposableEffect(audio) { onDispose { audio?.release() } }
    val tick = remember { mutableIntStateOf(0) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var selection by remember { mutableStateOf("") }
    var scheduleOpen by remember { mutableStateOf(false) }
    var chipVisible by remember { mutableStateOf(false) }
    var hintDismissed by remember { mutableStateOf(false) }
    BlockSystemBars(darkIcons = false)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val deviceNow = remember {
        val cal = Calendar.getInstance()
        (cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)).toDouble()
    }
    val rest = M.wrapMinutes(now ?: deviceNow)
    val fontScale = densityOwner.fontScale.toDouble()
    val renderer = remember(context, hint, fontScale) { SkyTimeScrubRenderer(context.assets, hint, fontScale) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(Theme.PAGE))) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val insetTop = with(densityOwner) { WindowInsets.statusBars.getTop(densityOwner).toDp().value }.toDouble()
        val insetBottom = with(densityOwner) { WindowInsets.navigationBars.getBottom(densityOwner).toDp().value }.toDouble()
        val layout = remember(w, h, insetTop, insetBottom, fontScale) { Theme.makeLayout(w, h, insetTop, insetBottom, fontScale) }
        val day = remember { M.makeDay(M.DEMO_SOLAR) }
        val projection = layout.arcAmplitude / layout.s / 87
        val detents = remember(day, rest) { doubleArrayOf(day.sunrise, day.noon, day.sunset, rest).map { M.wrapMinutes(it) }.toDoubleArray() }
        val timeline = remember(day, rest) { M.createTimeline(day, rest, true, M.EXPOSURE_UP, M.EXPOSURE_DOWN) }
        val engine = remember(layout, autoPlay, reduced, rest) {
            SkyTimeScrubEngine(autoPlay, reduced, day, rest, layout.s, projection, detents, timeline, true, M.EXPOSURE_UP, M.EXPOSURE_DOWN)
        }
        SideEffect {
            engine.cue = { kind, sfx ->
                if (haptics) vibe.emit(kind)
                if (sound && sfx.isNotEmpty()) audio?.play(sfx)
            }
            engine.hintDismissed = hintDismissed
            if (engine.scheduleOpen != scheduleOpen) engine.openSchedule(scheduleOpen)
        }
        val dens = densityOwner.density

        LaunchedEffect(engine, active, previewTime) {
            if (!active) return@LaunchedEffect
            var previous = 0L
            var lastSelection = -1.0
            var lastSelectionAt = 0.0
            if (previewTime != null) engine.pin(previewTime)
            while (true) {
                withFrameNanos { nanos ->
                    val dt = if (previous == 0L) 1.0 / 60 else min((nanos - previous) / 1e9, 0.05)
                    previous = nanos
                    engine.frame(nanos / 1e9, dt)
                    if (engine.chipVisible != chipVisible) chipVisible = engine.chipVisible
                    val m = Math.round(M.wrapMinutes(engine.minutes)).toDouble()
                    if (m != lastSelection && engine.clock - lastSelectionAt >= 0.25) {
                        lastSelection = m
                        lastSelectionAt = engine.clock
                        selection = engine.selectionLabel()
                    }
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
            renderer.draw(c, scale, layout, engine, reduced)
            c.restore()
        }

        // one pointer handler for everything: a control or the chip is a press, the ruler and the sun are pans
        Box(
            Modifier.fillMaxSize().pointerInput(engine, layout, dens) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val x0 = (down.position.x / dens).toDouble()
                    val y0 = (down.position.y / dens).toDouble()
                    val hit = renderer.hit(x0, y0, layout, engine)
                    if (hit == SkyTimeScrubHit.NONE) return@awaitEachGesture
                    down.consume()
                    when (hit) {
                        SkyTimeScrubHit.SUN_BUTTON, SkyTimeScrubHit.CITY_BUTTON, SkyTimeScrubHit.GRID_BUTTON,
                        SkyTimeScrubHit.CHIP, SkyTimeScrubHit.HINT_CLOSE -> {
                            val index = when (hit) {
                                SkyTimeScrubHit.SUN_BUTTON -> 0
                                SkyTimeScrubHit.CITY_BUTTON -> 1
                                SkyTimeScrubHit.GRID_BUTTON -> 2
                                else -> -1
                            }
                            if (index >= 0) engine.pressControl(index, true)
                            var inside = true
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                inside = renderer.rect(hit).contains(change.position.x / dens, change.position.y / dens)
                                change.consume()
                                if (!change.pressed) break
                            }
                            if (index >= 0) engine.pressControl(index, false)
                            if (inside) when (hit) {
                                SkyTimeScrubHit.SUN_BUTTON -> engine.travelTo(day.sunset)
                                SkyTimeScrubHit.CITY_BUTTON, SkyTimeScrubHit.CHIP -> engine.travelTo(rest)
                                SkyTimeScrubHit.GRID_BUTTON -> {
                                    scheduleOpen = !scheduleOpen
                                    engine.openSchedule(scheduleOpen)
                                    engine.cue(SkyTimeScrubCue.ONSET, "")
                                }
                                else -> {
                                    hintDismissed = true
                                    engine.hintDismissed = true
                                }
                            }
                        }
                        else -> {
                            val sun = hit == SkyTimeScrubHit.SUN_GRAB
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            if (sun) engine.sunBegin(x0) else engine.panBegin()
                            var finished = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    if (!change.pressed) {
                                        change.consume()
                                        finished = true
                                        val vx = (tracker.calculateVelocity().x / dens).toDouble()
                                        if (sun) engine.sunEnd(vx) else engine.panEnd(vx)
                                        break
                                    }
                                    if (sun) engine.sunUpdate((change.position.x / dens).toDouble())
                                    else engine.panUpdate((change.position.x / dens - x0))
                                    change.consume()
                                }
                            } finally {
                                if (!finished) {
                                    // a cancelled gesture never coasts
                                }
                                if (sun) engine.sunFinalize() else engine.panFinalize()
                            }
                        }
                    }
                }
            },
        )

        // accessibility: the ruler as one adjustable control, and the buttons
        Box(
            Modifier
                .offset((20 * layout.s).dp, (layout.rulerY - 40 * layout.s).dp)
                .size((layout.width - 40 * layout.s).dp, (88 * layout.s).dp)
                .semantics {
                    contentDescription = "${Theme.RULER}. ${Theme.RULER_HINT}"
                    stateDescription = selection
                    customActions = listOf(
                        CustomAccessibilityAction("Increase") { engine.nudge(1); true },
                        CustomAccessibilityAction("Decrease") { engine.nudge(-1); true },
                    )
                },
        )
        if (chipVisible) {
            Box(
                Modifier
                    .offset(((layout.width - Theme.CHIP_MAX_W * layout.s) / 2).dp, layout.chipY.dp)
                    .size((Theme.CHIP_MAX_W * layout.s).dp, (Theme.CHIP_TARGET * layout.s).dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = Theme.RETURN
                        onClick { engine.travelTo(rest); true }
                    },
            )
        }
        val controls = listOf(
            Triple("Preview sunset", Theme.CONTROLS_LEFT, Theme.CONTROLS_H) to { engine.travelTo(day.sunset) },
            Triple("${Theme.CITY}. ${Theme.RETURN}", Theme.CONTROLS_CITY_X, Theme.CONTROLS_CITY_W) to { engine.travelTo(rest) },
            Triple(if (scheduleOpen) "Hide solar times" else "Show solar times", Theme.CONTROLS_RIGHT_X, Theme.CONTROLS_H) to {
                scheduleOpen = !scheduleOpen
                engine.openSchedule(scheduleOpen)
            },
        )
        for ((spec, action) in controls) {
            Box(
                Modifier
                    .offset((spec.second * layout.s).dp, layout.controlsY.dp)
                    .size((spec.third * layout.s).dp, (Theme.CONTROLS_H * layout.s).dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = spec.first
                        onClick { action(); true }
                    },
            )
        }
    }
}

