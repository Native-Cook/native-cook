package com.ngocdevv.nativecook.blocks.gestures.flipfocusbadge

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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.gestures.flipfocusbadge.FlipFocusBadgeTheme as Theme
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.min

/**
 * Flip Focus Badge: a focus timer whose reward is an object. Flip the phone (here: drag the badge) past 90 degrees and the black-glass badge shows its
 * back, which is the running session's clock; flip back to finish. If the session reached an hour, the periwinkle numeral presses into the glass behind
 * the plate and punches out as the new number, and at the same moment the sheet's total rolls (10h 24m -> 11h 00m) and the progress line starts the next
 * hour. The badge is a small real 3D scene ray-marched live by one AGSL shader on API 33+ (a drawing below); the page around it is a function of the
 * same state.
 *
 * LOOP = 8.1 s ([FlipFocusBadgeMotion.LOOP]): rest "10" 0 - 0.5; the badge presses and turns to 70 degrees under a finger 0.5 - 1.5 (the gasp frame at
 * 1.7); it lands on its back 1.9 - 2.4 and the session runs shown fast 2.4 - 4.0 (the clock 00:00 -> 36:00, the line fills); flip back 4.0 - 5.0 with the
 * numeral exchange (10 -> 11) and the hour's landing 4.05 - 4.7; hold 4.8 - 5.8; rewind 5.8 - 7.0; rest from 7.0. A touch takes the badge from wherever
 * the loop has it (a session runs 24x fast so it can be watched); two seconds after the last touch it eases back and plays again. Reduce Motion: no loop,
 * no ring lag or sway or glints, the rest frame.
 * Not ported from the RN props: the device-turn source (the rotation sensor), controlled `running`, `defaultRunning`, `source`.
 */
@Composable
fun FlipFocusBadge(
    autoPlay: Boolean = true,
    totalSeconds: Double = 37440.0,
    timeScale: Double = 24.0,
    minSessionSeconds: Double = FlipFocusBadgeMotion.MIN_SESSION,
    haptics: Boolean = true,
    onStart: (() -> Unit)? = null,
    onStop: ((Int) -> Unit)? = null,
    onHour: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val densityOwner = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberLiveReducedMotion()
    val vibe = remember(context) { FlipFocusBadgeHaptics.create(context) }
    val tick = remember { mutableIntStateOf(0) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var a11yRunning by remember { mutableStateOf(false) }
    var a11yHours by remember { mutableIntStateOf(Math.floor(totalSeconds / 3600).toInt()) }
    var a11yRange by remember { mutableIntStateOf(0) }
    var a11yTotal by remember { mutableStateOf(FlipFocusBadgeEngine.fmt(totalSeconds)) }
    BlockSystemBars(darkIcons = false)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(Theme.pageMid))) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val insetTop = with(densityOwner) { WindowInsets.statusBars.getTop(densityOwner).toDp().value }.toDouble()
        val insetBottom = with(densityOwner) { WindowInsets.navigationBars.getBottom(densityOwner).toDp().value }.toDouble()
        val layout = remember(w, h, insetTop, insetBottom) { Theme.Layout(w, h, insetTop, insetBottom) }
        val s = layout.s
        val dens = densityOwner.density
        val renderer = remember { FlipFocusBadgeRenderer(context.assets) }
        val engine = remember(autoPlay, reduced, timeScale, minSessionSeconds, totalSeconds, s) {
            FlipFocusBadgeEngine(autoPlay, reduced, timeScale, minSessionSeconds, totalSeconds, s)
        }
        val cbStart by rememberUpdatedState(onStart)
        val cbStop by rememberUpdatedState(onStop)
        val cbHour by rememberUpdatedState(onHour)
        SideEffect {
            engine.haptics = if (haptics) vibe else null
            engine.onStart = { cbStart?.invoke() }
            engine.onStop = { seconds ->
                a11yTotal = engine.rollTo
                cbStop?.invoke(seconds)
            }
            engine.onHour = { hours ->
                a11yHours = hours
                cbHour?.invoke(hours)
            }
            engine.onRunning = { r ->
                a11yRunning = r
                a11yHours = engine.valueN
                a11yTotal = engine.rollTo
            }
        }

        LaunchedEffect(engine, active) {
            if (!active) return@LaunchedEffect
            var previous = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val dt = if (previous == 0L) 0.016 else min((nanos - previous) / 1e9, 0.05)
                    previous = nanos
                    // Read only inside the Canvas below: invalidates drawing, not the screen's composition.
                    engine.frame(dt)
                    tick.intValue++
                }
            }
        }

        // hit areas, dp
        val badgeTop = layout.yc(Theme.CENTRE_Y) - 150 * s
        val badgeH = 300 * s
        val itemW = (Theme.SEG_W - 2 * Theme.SEG_PAD - 3 * Theme.SEG_GAP) / 4
        val segTop = layout.sheetTop + (Theme.SEG_Y - Theme.SHEET_Y) * s
        fun segLeft(i: Int) = layout.x(Theme.SEG_X) + (Theme.SEG_PAD + i * (itemW + Theme.SEG_GAP)) * s
        fun inDisc(i: Int, x: Double, y: Double): Boolean {
            val b = if (i == 0) Theme.discHelp else Theme.discGear
            val left = b.x * s - 4
            val top = layout.yt(b.y) - 4
            return x >= left && x <= left + b.w * s + 8 && y >= top && y <= top + b.h * s + 8
        }
        fun inSeg(i: Int, x: Double, y: Double): Boolean {
            val left = segLeft(i)
            return x >= left && x <= left + itemW * s && y >= segTop && y <= segTop + Theme.SEG_H * s
        }
        fun choose(i: Int) {
            engine.selectRange(i)
            a11yRange = i
            a11yTotal = if (i == 0) engine.rollTo else Theme.RANGES[i].total
            tick.intValue++
        }

        Canvas(
            Modifier.fillMaxSize().pointerInput(engine, dens, layout) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val x = down.position.x / dens.toDouble()
                    val y = down.position.y / dens.toDouble()
                    // 1 the badge, 2 a disc, 3 a segment
                    var mode = 0
                    var index = 0
                    if (y >= badgeTop && y <= badgeTop + badgeH) mode = 1
                    else if (inDisc(0, x, y)) { mode = 2; index = 0 }
                    else if (inDisc(1, x, y)) { mode = 2; index = 1 }
                    else {
                        for (i in 0..3) if (inSeg(i, x, y)) { mode = 3; index = i }
                    }
                    if (mode == 0) return@awaitEachGesture
                    down.consume()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    if (mode == 1) engine.badgeDown()
                    if (mode == 2) engine.discPressed = index
                    tick.intValue++
                    var finished = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                            val px = change.position.x / dens.toDouble()
                            val py = change.position.y / dens.toDouble()
                            when (mode) {
                                1 -> {
                                    val tx = (change.position.x - down.position.x) / dens.toDouble()
                                    val ty = (change.position.y - down.position.y) / dens.toDouble()
                                    engine.badgeMove(tx, ty)
                                    if (!change.pressed) {
                                        val v = tracker.calculateVelocity()
                                        engine.badgeUp(v.x / dens.toDouble(), v.y / dens.toDouble(), false)
                                        finished = true
                                    }
                                }
                                2 -> if (!change.pressed) {
                                    engine.discPressed = -1
                                    finished = true
                                }
                                3 -> if (!change.pressed) {
                                    if (inSeg(index, px, py)) choose(index)
                                    finished = true
                                }
                            }
                            if (finished) break
                            tick.intValue++
                        }
                    } finally {
                        if (!finished) {
                            when (mode) {
                                1 -> engine.badgeUp(0.0, 0.0, true)
                                2 -> engine.discPressed = -1
                            }
                        }
                        tick.intValue++
                    }
                }
            },
        ) {
            tick.intValue
            val c = drawContext.canvas.nativeCanvas
            val scale = drawContext.density.density
            c.save()
            c.scale(scale, scale)
            renderer.draw(c, this.size.width / scale, this.size.height / scale, scale, layout, engine, reduced)
            c.restore()
        }

        // static nodes over the drawing, so the controls are focusable and labelled
        fun spot(x: Double, y: Double, bw: Double, bh: Double) = Modifier.offset(x.dp, y.dp).size(bw.dp, bh.dp)
        Box(
            spot(0.0, badgeTop, w, badgeH).semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = if (a11yRunning) Theme.badgeA11yRunning("") else Theme.badgeA11y(a11yHours)
                onClick(label = if (a11yRunning) Theme.STOP_A11Y else Theme.START_A11Y) {
                    engine.a11yFlip()
                    tick.intValue++
                    true
                }
            },
        )
        for (cb in 0..1) {
            val b = if (cb == 0) Theme.chipFlame else Theme.chipTrophy
            Box(
                spot(b.x * s, layout.yt(b.y), b.w * s, b.h * s).semantics(mergeDescendants = true) {
                    contentDescription = if (cb == 0) "${Theme.STREAK} day streak" else "${Theme.TROPHIES} trophies"
                },
            )
        }
        for (d in 0..1) {
            val b = if (d == 0) Theme.discHelp else Theme.discGear
            Box(
                spot(b.x * s, layout.yt(b.y), b.w * s, b.h * s).semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = if (d == 0) Theme.HELP_A11Y else Theme.SETTINGS_A11Y
                },
            )
        }
        Box(
            spot(layout.x(Theme.SHEET_X), layout.sheetTop, Theme.SHEET_W * s, (Theme.SEG_Y - Theme.SHEET_Y - 4) * s).semantics(mergeDescendants = true) {
                val total = a11yTotal.replace("h", " hours ").replace("m", " minutes")
                contentDescription = "${Theme.RANGES[a11yRange].date}. ${if (a11yRange == 0) total else Theme.RANGES[a11yRange].total}. ${Theme.CAPTION}"
            },
        )
        for (i in 0..3) {
            Box(
                spot(segLeft(i), segTop, itemW * s, Theme.SEG_H * s).semantics(mergeDescendants = true) {
                    role = Role.Tab
                    selected = i == a11yRange
                    contentDescription = Theme.SEGMENT_LABELS[i]
                    onClick { choose(i); true }
                },
            )
        }
    }
}
