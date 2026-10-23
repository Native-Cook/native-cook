package com.ngocdevv.nativecook.blocks.playground.pendulumwave

import android.graphics.Paint
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

// The camera's focal length, as a share of the smaller of the width and half the height, and where
// the scene's middle sits on the screen (a share of the height).
private const val FOCAL = 1.75
private const val CENTRE_AT = 0.5
// The beam fades for the view from above between these pitches (degrees).
private const val TOP_FROM = 40.0
private const val TOP_TO = 72.0
// After a touch the loop waits this long, then starts over.
private const val USER_HOLD = 4.0
private const val SIDE = 24f
// A drag becomes the comb after this much sideways travel (dp); a shorter touch is a tap.
private const val PAN_SLOP = 10f
private const val TAP_MS = 350L

private val Ink = Color(0xFFEDE3D2)
private val InkSoft = Ink.copy(alpha = 0.5f)
private val InkFaint = Ink.copy(alpha = 0.28f)
internal val PendulumBackground = Color(0xFF0D0E10)

private fun smooth01(p: Double): Double {
    val t = p.coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t)
}

/** The pendulums, the script's clock and the camera, shared by the frame loop and the gestures. */
private class PendulumState {
    val sim = PendulumSim()
    var clock = 0.0
    var hold = 0.0
    // The camera: where the last morph started, the view it goes to, how far along (1 = there),
    // and the clock that sways the 3/4 view.
    var camFrom = viewCamera(VIEW_THREE_QUARTER, 0.0)
    var viewTo = VIEW_THREE_QUARTER
    var morph = 1.0
    var orbit = 0.0
    // Screen dp per world unit along the swing, for turning a drag into the comb's travel.
    var axisX = 1.0
    var axisY = 0.0
    var dragFrom = 0.0
    var dragging = false
    val view = View3()
    val frame = mutableIntStateOf(0)

    /** The camera right now. */
    fun camera(): Camera {
        val target = viewCamera(viewTo, orbit)
        return if (morph < 1) blendCamera(camFrom, target, morph) else target
    }
}

/**
 * Fifteen brass-bob pendulums on a walnut frame, tuned to drift in and out of step through one 30 s
 * cycle: travelling waves, a snake, interleaved rows, apparent chaos, then one line again. Drag
 * sideways and a brass comb gathers the bobs and pulls them aside; let go to release them. A tap
 * turns the camera to the next view (3/4, top, side) with a morph.
 *
 * @param autoPlay pulls the bobs aside with the comb, lets go, tours the views through one cycle, catches them.
 */
@Composable
fun PendulumWave(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val hostView = LocalView.current
    val screen = LocalDensity.current
    val state = remember { PendulumState() }
    var viewName by remember { mutableStateOf(ViewNames[VIEW_THREE_QUARTER]) }
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val painter = remember { PendulumPainter() }
    val mono = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 12f
            color = InkSoft.toArgb()
        }
    }
    val track = remember { Paint().apply { color = Ink.copy(alpha = 0.14f).toArgb() } }
    val filled = remember { Paint().apply { color = Color(1f, 214f / 255f, 150f / 255f, 0.85f).toArgb() } }

    fun setView(index: Int, byUser: Boolean) {
        val s = state
        if (index == s.viewTo) return
        s.camFrom = s.camera()
        s.viewTo = index
        s.morph = if (reducedMotion) 1.0 else 0.0
        viewName = ViewNames[index]
        if (byUser) hostView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Pendulum wave, $viewName view. Drag sideways to pull the pendulums aside and release them; tap to change the view."
            },
    ) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val focal = min(width, height * 0.5) * FOCAL
        val centreY = height * CENTRE_AT
        val readoutY = (safeTop + 70).toFloat()

        LaunchedEffect(state, auto) {
            state.clock = 0.0
            state.orbit = 0.0
            state.hold = 0.0
        }

        LaunchedEffect(state, auto, reducedMotion, width, height) {
            var last = -1L
            val s = state.sim
            while (true) {
                withFrameNanos { nanos ->
                    // The script, the pendulums and the camera share one clock, capped so a slow
                    // frame cannot skip ahead.
                    val raw = if (last < 0) 1.0 / 60 else (nanos - last) / 1e9
                    last = nanos
                    val dt = min(raw, MAX_FRAME)
                    if (dt <= 0) return@withFrameNanos
                    // A comb in the user's hand keeps the script away.
                    if (state.dragging) state.hold = USER_HOLD
                    if (state.hold > 0) {
                        state.hold -= dt
                        if (state.hold <= 0) {
                            state.clock = 0.0
                            state.orbit = 0.0
                        }
                    } else if (auto) {
                        val before = state.clock
                        val now = (before + dt) % LOOP
                        state.clock = now
                        setView(s.script(before, now), false)
                    }
                    if (!reducedMotion) state.orbit = (state.orbit + dt) % LOOP
                    if (state.morph < 1) state.morph = min(1.0, state.morph + dt / MORPH)
                    s.step(dt)
                    if (s.events != 0) {
                        if (s.eventsByUser) {
                            if (s.events and EVENT_RELEASE != 0) {
                                hostView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            } else if (s.events and EVENT_GRAB != 0) {
                                hostView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                        s.events = 0
                        s.eventsByUser = false
                    }
                    if (width > 0) {
                        val v = state.view
                        v.set(state.camera(), width, focal, centreY)
                        v.project(0.0, 6.0, 0.0)
                        val ax = v.px
                        val ay = v.py
                        v.project(1.0, 6.0, 0.0)
                        state.axisX = v.px - ax
                        state.axisY = v.py - ay
                    }
                    state.frame.intValue += 1
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state) {
                    // One gesture stands in for the race of a pan (after 10 dp of sideways travel)
                    // and a tap (a touch lifted within 350 ms that never became a pan).
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val s = state.sim
                        var panning = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val tx = (change.position.x - down.position.x) / density
                                val ty = (change.position.y - down.position.y) / density
                                if (!change.pressed) {
                                    if (panning) {
                                        s.release(byUser = true, timed = true)
                                    } else if (change.uptimeMillis - down.uptimeMillis <= TAP_MS) {
                                        // Tap: the next view.
                                        state.hold = USER_HOLD
                                        setView((state.viewTo + 1) % ViewNames.size, true)
                                    }
                                    break
                                }
                                if (!panning && abs(tx) > PAN_SLOP) {
                                    // Drag sideways: the comb takes the bobs where they are on average, so
                                    // a moving row is caught, not yanked, and follows the finger.
                                    panning = true
                                    state.hold = USER_HOLD
                                    var mean = 0.0
                                    for (x in s.x) mean += x / s.x.size
                                    state.dragFrom = mean
                                    state.dragging = true
                                    s.grab(mean, true)
                                }
                                if (panning) {
                                    change.consume()
                                    state.hold = USER_HOLD
                                    val per = state.axisX * state.axisX + state.axisY * state.axisY
                                    val along = if (per > 0) (tx * state.axisX + ty * state.axisY) / per else 0.0
                                    s.move(state.dragFrom + along)
                                }
                            }
                        } finally {
                            // Also when the gesture is cut off (cancelled pointer input): the comb lets
                            // go, so the script is not held off for ever.
                            if (panning && s.held) s.release(byUser = true, timed = true)
                            state.dragging = false
                        }
                    }
                },
        ) {
            state.frame.intValue
            val s = state.sim
            val w = width.toFloat()
            // Nothing to draw until the size is real (0 before layout, or an unbounded parent).
            if (w <= 0f || height <= 0.0 || !height.isFinite()) return@Canvas
            drawIntoCanvas { c ->
                val n = c.nativeCanvas
                n.save()
                n.scale(density, density)
                val cam = state.camera()
                val v = state.view
                v.set(cam, width, focal, centreY)
                painter.setDensity(density)
                with(painter) { n.drawScene(v, s, w, height.toFloat(), smooth01((cam.pitch - TOP_FROM) / (TOP_TO - TOP_FROM))) }
                // The cycle readout: a hairline that fills over the cycle, and the seconds since release.
                val running = s.sinceRelease >= 0
                val t = if (running) s.sinceRelease % CYCLE else 0.0
                n.drawRect(SIDE, readoutY, w - SIDE, readoutY + 1, track)
                n.drawRect(SIDE, readoutY - 0.5f, SIDE + ((w - SIDE * 2) * t / CYCLE).toFloat(), readoutY + 1.5f, filled)
                val label = if (running) String.format(Locale.US, "%.1f s / %d s", t, CYCLE.toInt()) else "— / ${CYCLE.toInt()} s"
                n.drawText(label, w - SIDE - mono.measureText(label), readoutY - 10, mono)
                n.restore()
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = SIDE.dp, end = SIDE.dp, top = (safeTop + 18).dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            BasicText("PENDULUM WAVE", style = TextStyle(color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp))
            BasicText("Fifteen bobs, one ${CYCLE.toInt()} s cycle", style = TextStyle(color = InkSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = SIDE.dp, end = SIDE.dp, bottom = (safeBottom + 22).dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    for (name in ViewNames) {
                        BasicText(
                            name.uppercase(Locale.US),
                            style = TextStyle(
                                color = if (name == viewName) Ink else InkFaint,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.6.sp,
                            ),
                        )
                    }
                }
                BasicText(
                    "Drag sideways to release · Tap to change view",
                    style = TextStyle(color = InkSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
                )
            }
        }
    }
}
