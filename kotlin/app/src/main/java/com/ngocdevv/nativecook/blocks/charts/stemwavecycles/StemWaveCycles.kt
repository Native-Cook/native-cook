package com.ngocdevv.nativecook.blocks.charts.stemwavecycles

import android.app.Activity
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.blocks.charts.stemwavecycles.StemWaveCyclesWave.drawWave
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

private typealias W = StemWaveCyclesWave

private val BACKGROUND = Color(0xFFE7E6F0)
private val INK = Color(0xFF1B1B1F)
private val CURVE_INK = Color(0xFF6E6E78)
private val LINE_INK = Color(0xFF8A8A92)

private const val PITCH = 17f
private const val STRIP = 14f
private const val ROW = 8f
private const val OVERLAP = 2f
private const val DOT = 2.5f
private const val AMPLITUDE = 120f
// The strip's centre, as a share of the screen's height.
private const val AXIS_AT = 0.46f
private val CAPTION_GAP = 40.dp
// The pinch's reach: the zoom may go this far past one period, or below the stems' period.
private const val ZOOM_MIN = -0.6
private const val ZOOM_MAX = 1.6
private val DRAG_SLOP = 8.dp
private const val TAP_MAX_MS = 500L

private const val USER_HOLD = 3.0
private const val SETTLE = 0.6

/** Which picture a tap switched to. */
enum class StemWaveCyclesMode { Stems, Curve }

private fun now() = System.nanoTime() / 1e9

/**
 * A number moving from one value to another over a time span (seconds of [now]): linear for the
 * loop's clock, cubic in-out for the hand's turn and stretch easing away.
 */
private class Ramp(val from: Double, val to: Double = from, val start: Double = 0.0, val duration: Double = 0.0, val cubic: Boolean = false) {
    fun value(at: Double): Double {
        if (duration <= 0) return to
        val u = ((at - start) / duration).coerceIn(0.0, 1.0)
        val eased = if (!cubic) u else if (u < 0.5) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2
        return from + (to - from) * eased
    }
}

private class ToggleEnd(val at: Double, val toStems: Boolean)

/**
 * The block's moving parts, kept in one remembered object so gesture handlers and the driving
 * coroutine always read the current values.
 */
private class StemWaveCyclesState(autoPlay: Boolean) {
    // The loop's clock: running from `origin` while automatic, else `clock`. A hand freezes it and
    // turns (`phase`) or stretches (`zoom`) the wave on top.
    var automatic by mutableStateOf(autoPlay)
    var origin by mutableDoubleStateOf(now())
    var clock by mutableStateOf(Ramp(0.0))
    var phase by mutableStateOf(Ramp(0.0))
    var zoom by mutableStateOf(Ramp(0.0))
    /** The end of the tap's play to the curve or back. */
    var toggleEnd by mutableStateOf<ToggleEnd?>(null)
    /** When the last hand let go; null while a finger is down or a tap's play runs. */
    var releasedAt by mutableStateOf<Double?>(null)
    var generation by mutableIntStateOf(0)
    var dragFrom: Double? = null
    var pinchFrom: Double? = null
    var quarter = 0
    // The frame clock runs only while something moves; otherwise the picture is drawn at `sampleAt`.
    var autoMoving by mutableStateOf(false)
    var handMoving by mutableStateOf(false)
    var sampleAt by mutableDoubleStateOf(now())
    var frame by mutableDoubleStateOf(now())

    var still = false
    var width = 1f
    var periodStems = 12f
    var tick: () -> Unit = {}
    var onModeChange: ((StemWaveCyclesMode) -> Unit)? = null

    val live get() = if (automatic) autoMoving else handMoving

    fun time(at: Double) = if (automatic) ((at - origin).coerceAtLeast(0.0) % W.LOOP) else clock.value(at)

    /** The zoom on screen, where a drag measures one period. */
    private fun zoomNow(at: Double) = (W.timeline(time(at), still).zoom + zoom.value(at)).coerceIn(ZOOM_MIN, ZOOM_MAX)

    private fun basePeriod() = (PITCH * periodStems).toDouble()

    /** A hand stops the loop where it is and the hand's own easing away. */
    private fun touch(at: Double) {
        if (automatic) {
            clock = Ramp(time(at))
            automatic = false
            autoMoving = false
        }
        phase = Ramp(phase.value(at))
        zoom = Ramp(zoom.value(at))
        handMoving = toggleEnd != null
        sampleAt = at
        releasedAt = null
        generation++
    }

    private fun release() {
        if (dragFrom != null || pinchFrom != null) return
        releasedAt = if (toggleEnd == null) now() else null
        generation++
    }

    /** Plays on to the zoomed curve, or from it back to the stems. */
    fun toggle() {
        val at = now()
        touch(at)
        val t = clock.value(at)
        val curve = t >= W.RETRACT_START && t < W.STEMS_BACK
        val from = if (curve) max(t, W.RETURN_START) else W.RETRACT_START
        val to = if (curve) W.STEMS_BACK else W.CURVE_HELD
        clock = Ramp(from, to, at, to - from)
        toggleEnd = ToggleEnd(at + (to - from), curve)
        handMoving = true
        tick()
        onModeChange?.invoke(if (curve) StemWaveCyclesMode.Stems else StemWaveCyclesMode.Curve)
    }

    fun dragStart() {
        val at = now()
        touch(at)
        dragFrom = phase.value(at)
        quarter = floor(phase.value(at) / (PI / 2)).toInt()
    }

    fun drag(translationX: Float) {
        // The wave follows the finger: a drag of one period turns it one whole cycle.
        val period = basePeriod() * exp(ln(width / basePeriod()) * zoomNow(now()))
        val turned = (dragFrom ?: 0.0) - translationX / period * PI * 2
        phase = Ramp(turned)
        // A selection tick each quarter turn.
        val q = floor(turned / (PI / 2)).toInt()
        if (q != quarter) {
            quarter = q
            tick()
        }
    }

    fun dragEnd() {
        dragFrom = null
        release()
    }

    fun pinchStart() {
        val at = now()
        touch(at)
        pinchFrom = zoom.value(at)
    }

    fun pinch(scale: Float) {
        zoom = Ramp((pinchFrom ?: 0.0) + ln(max(0.05f, scale).toDouble()) / ln(width / basePeriod()))
    }

    fun pinchEnd() {
        pinchFrom = null
        release()
    }

    private suspend fun waitUntil(at: Double) {
        while (now() < at) delay(((at - now()) * 1000).roundToInt().coerceAtLeast(1).toLong())
    }

    /** Finishes a tap's play, waits out the hand, eases its turn and stretch away and runs the loop. */
    suspend fun drive(autoPlay: Boolean) {
        val end = toggleEnd
        if (!automatic && end != null) {
            waitUntil(end.at)
            if (end.toStems) clock = Ramp(0.0)
            toggleEnd = null
            handMoving = false
            sampleAt = end.at
            if (dragFrom == null && pinchFrom == null) releasedAt = end.at
        }
        if (!autoPlay) return
        if (!automatic) {
            val released = releasedAt ?: return
            // The hand's turn and stretch ease away (the turn to the nearest whole cycle), then the
            // loop plays on from where it was stopped and comes round to rest.
            val settleAt = released + USER_HOLD
            waitUntil(settleAt)
            val turned = phase.value(settleAt)
            val whole = Math.round(turned / (PI * 2)) * PI * 2
            phase = Ramp(turned, whole, settleAt, SETTLE, cubic = true)
            zoom = Ramp(zoom.value(settleAt), 0.0, settleAt, SETTLE, cubic = true)
            handMoving = true
            val resume = settleAt + SETTLE
            waitUntil(resume)
            phase = Ramp(0.0)
            zoom = Ramp(0.0)
            handMoving = false
            origin = resume - clock.value(resume)
            automatic = true
        }
        // The picture is still from the loop's end to the start of the roll.
        while (true) {
            val start = origin + floor((now() - origin) / W.LOOP) * W.LOOP
            val rollAt = start + W.ROLL_START
            if (now() < rollAt) {
                autoMoving = false
                sampleAt = start + W.ROLL_START / 2
                waitUntil(rollAt)
            }
            autoMoving = true
            waitUntil(start + W.LOOP)
        }
    }
}

/**
 * An abstract chart of a cycle: a lollipop chart of stems on a strip of colour blocks, the stems
 * rolling as a wave. It dissolves into a single sine curve through the dots, zooms to one period
 * with its crest and trough marked, and returns to stems. Drag to turn the wave, pinch to change
 * its period, tap to switch between the stems and the curve.
 *
 * @param amplitude Peak height of the wave in points.
 * @param periodStems Stems per period while the stems stand.
 * @param autoPlay Plays stems → curve → one period → stems by itself, every [StemWaveCyclesWave.LOOP] seconds.
 * @param onModeChange Called when a tap switches between the stems and the curve.
 */
@Composable
fun StemWaveCycles(
    modifier: Modifier = Modifier,
    caption: String = "Economic cycles",
    amplitude: Float = AMPLITUDE,
    periodStems: Float = 12f,
    background: Color = BACKGROUND,
    ink: Color = INK,
    autoPlay: Boolean = false,
    onModeChange: ((StemWaveCyclesMode) -> Unit)? = null,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    val screen = LocalDensity.current
    val state = remember { StemWaveCyclesState(autoPlay) }
    val modeChange by rememberUpdatedState(onModeChange)
    state.still = still
    state.periodStems = periodStems
    state.onModeChange = { modeChange?.invoke(it) }
    state.tick = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }

    // Dark status bar icons on the pale screen.
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
    }
    LaunchedEffect(state.generation, autoPlay) { state.drive(autoPlay) }
    LaunchedEffect(state.live) {
        while (state.live) withFrameNanos { state.frame = it / 1e9 }
    }

    val bottom = max(WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding(), 16.dp) + CAPTION_GAP
    Box(modifier.fillMaxSize().background(background)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { state.width = it.width / screen.density }
                .semantics {
                    role = Role.Button
                    contentDescription = caption
                    onClick(label = "Switches between the stems and the curve") {
                        state.toggle()
                        true
                    }
                }
                .pointerInput(state) {
                    val slop = DRAG_SLOP.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var panX = 0f
                        var moved = 0f
                        var dragging = false
                        var pinching = false
                        var scale = 1f
                        var multi = false
                        var upAt = down.uptimeMillis
                        do {
                            val event = awaitPointerEvent()
                            upAt = event.changes.first().uptimeMillis
                            val pressed = event.changes.count { it.pressed }
                            if (pressed >= 2) {
                                multi = true
                                if (!pinching) {
                                    pinching = true
                                    state.pinchStart()
                                }
                                scale *= event.calculateZoom()
                                state.pinch(scale)
                            }
                            val pan = event.calculatePan()
                            panX += pan.x
                            moved += pan.getDistance()
                            if (!dragging && abs(panX) > slop) {
                                dragging = true
                                state.dragStart()
                            }
                            if (dragging) state.drag(panX / screen.density)
                            if (dragging || pinching) event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        if (dragging) state.dragEnd()
                        if (pinching) state.pinchEnd()
                        val quick = upAt - down.uptimeMillis <= TAP_MAX_MS
                        if (!dragging && !pinching && !multi && moved < slop && quick) state.toggle()
                    }
                },
        ) {
            val date = if (state.live) state.frame else state.sampleAt
            val w = size.width / density
            val h = size.height / density
            val look = StemWaveCyclesWave.Look(
                width = w, height = h, axis = h * AXIS_AT, amplitude = minOf(amplitude, h * 0.16f), pitch = PITCH,
                periodStems = periodStems, strip = STRIP, row = ROW, overlap = OVERLAP, dot = DOT,
                ink = ink, curveInk = CURVE_INK, lineInk = LINE_INK,
            )
            val t = state.time(date)
            val scene = W.timeline(t, still)
            val zoom = (scene.zoom + state.zoom.value(date)).coerceIn(ZOOM_MIN, ZOOM_MAX)
            scale(density, density, pivot = Offset.Zero) {
                drawWave(t, scene.phase + state.phase.value(date), zoom, still, look)
            }
        }
        BasicText(
            caption,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = bottom),
            style = TextStyle(color = ink, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp, textAlign = TextAlign.Center),
        )
    }
}
