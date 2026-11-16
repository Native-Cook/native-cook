package com.ngocdevv.nativecook.blocks.progress.auroraslatsync

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

const val LOOP = 8.0f

/**
 * A sync hero: an aurora seen through eleven slats of reeded glass. Drag to push the light and
 * scrub the sync; two seconds after the last touch it settles and the eight-second loop resumes.
 */
@Composable
fun AuroraSlatSync(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reducedMotion = rememberLiveReducedMotion()
    val reduced by rememberUpdatedState(reducedMotion)
    val playing by rememberUpdatedState(autoPlay)
    val haptic by rememberUpdatedState(LocalHapticFeedback.current)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    val playback = remember { AuroraSlatSyncPlayback() }
    // The draw lambda is the only reader of the clock, so a frame redraws without recomposing.
    var clock by remember { mutableLongStateOf(0L) }
    val measurer = rememberTextMeasurer()
    val text = remember(measurer) { AuroraSlatSyncText(measurer) }
    BlockSystemBars(darkIcons = false)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(autoPlay, reducedMotion, active) {
        playback.reset()
        clock = 0L
        if (reducedMotion || !active) return@LaunchedEffect
        while (true) {
            withFrameNanos { now ->
                if (playback.origin == 0L) playback.origin = now
                playback.now = now
                if (playback.step(now, autoPlay)) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (autoPlay || playback.mode != AuroraSlatSyncPlayback.PLAYING) clock = now
            }
        }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .semantics {
                contentDescription =
                    "Halo Vault is syncing 1,284 files across 3 devices. Drag to push the light and scrub the sync."
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (reduced) return@awaitEachGesture
                    playback.begin(playing)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val travel = (change.position - down.position) / density
                        if (playback.drag(travel.x, travel.y)) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        change.consume()
                    }
                    playback.end()
                }
            },
    ) {
        val frame = if (reduced) AuroraSlatSyncFrame.finished() else playback.frame(clock, playing)
        drawAuroraSlatSync(frame, text)
    }
}

/** Everything one frame of the scene needs. */
private class AuroraSlatSyncFrame(
    var p: Float = 0f,
    var drift: Float = 0f,
    var pushX: Float = 0f,
    var pushY: Float = 0f,
    var sweep: Float = -1f,
    var percent: Int = 0,
    var numberAlpha: Float = 1f,
    var syncingAlpha: Float = 1f,
    var syncedAlpha: Float = 0f,
) {
    companion object {
        fun finished() = AuroraSlatSyncFrame(p = 1f, percent = 100, syncingAlpha = 0f, syncedAlpha = 1f)
    }
}

/** The timeline, shared number for number with the SwiftUI reference. */
private object AuroraSlatSyncMotion {
    const val BUILD_START = 0.8f
    const val BUILD_END = 4.4f
    const val SWEEP_START = 4.5f
    const val SWEEP_LENGTH = 1.0f
    const val RETURN_START = 6.0f
    const val RETURN_END = 7.2f

    fun seg(t: Float, a: Float, b: Float) = min(max((t - a) / (b - a), 0f), 1f)
    fun inOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
    fun smooth(x: Float): Float { val u = min(max(x, 0f), 1f); return u * u * (3f - 2f * u) }
    fun wrap(t: Float): Float { val m = t % LOOP; return if (m < 0f) m + LOOP else m }

    /** Finger travel with a rubber band: follows at first, then falls off. */
    fun rubber(x: Float) = x / (1f + abs(x) / 140f)

    /** Unit step response of the release spring (response 0.45 s, damping ratio 0.78), from 1 to 0. */
    fun spring(tau: Float): Float {
        if (tau <= 0f) return 1f
        val omega = 2f * PI.toFloat() / 0.45f
        val zeta = 0.78f
        val damped = omega * sqrt(1f - zeta * zeta)
        return exp(-zeta * omega * tau) * (cos(damped * tau) + zeta * omega / damped * sin(damped * tau))
    }

    /** Rest 0–0.8 s, build 0.8–4.4 s, peak 4.4–6 s (sweep 4.5–5.5 s), return 6–7.2 s, rest to 8 s. */
    fun loopFrame(t: Float): AuroraSlatSyncFrame {
        val build = inOut(seg(t, BUILD_START, BUILD_END))
        val back = inOut(seg(t, RETURN_START, RETURN_END))
        return AuroraSlatSyncFrame(
            p = build * (1f - back),
            drift = t,
            sweep = if (t >= SWEEP_START && t <= SWEEP_START + SWEEP_LENGTH) (t - SWEEP_START) / SWEEP_LENGTH else -1f,
            percent = if (t < 6.55f) (build * 100f).roundToInt() else 0,
            numberAlpha = if (t < 6.55f) 1f - seg(t, 6.0f, 6.35f) else seg(t, 6.75f, 7.1f),
            syncedAlpha = seg(t, 4.45f, 4.7f) * (1f - seg(t, 6.0f, 6.35f)),
            syncingAlpha = if (t < 5.5f) 1f - seg(t, 4.4f, 4.6f) else seg(t, 6.75f, 7.1f),
        )
    }

    /** The state under the finger: the label follows the energy directly. */
    fun heldFrame(p: Float, drift: Float, pushX: Float, pushY: Float, sweep: Float) = AuroraSlatSyncFrame(
        p = p, drift = drift, pushX = pushX, pushY = pushY, sweep = sweep,
        percent = (p * 100f).roundToInt(),
        syncedAlpha = seg(p, 0.97f, 1.0f),
        syncingAlpha = 1f - seg(p, 0.94f, 0.98f),
    )
}

/** Touch and clock state. Plain fields: only the frame loop and the gesture write them. */
private class AuroraSlatSyncPlayback {
    var mode = PLAYING
    var origin = 0L
    var now = 0L
    private var dragging = false
    private var p = 0f
    private var startP = 0f
    private var holdDrift = 0f
    private var holdStart = 0L
    private var pushX = 0f
    private var pushY = 0f
    private var releaseAt = 0L
    private var sweepAt = Long.MIN_VALUE / 2
    private var fromP = 0f
    private var fromDrift = 0f
    private var toDrift = 0f
    private var returnStart = 0L
    private var tick = -1L

    fun reset() {
        mode = PLAYING
        origin = 0L
        dragging = false
        pushX = 0f
        pushY = 0f
        tick = -1L
    }

    private fun seconds(from: Long, to: Long) = (to - from) / 1e9f

    fun frame(at: Long, autoPlay: Boolean): AuroraSlatSyncFrame = when (mode) {
        HELD -> {
            val settle = if (dragging) 1f else AuroraSlatSyncMotion.spring(seconds(releaseAt, at))
            val sweep = seconds(sweepAt, at) / AuroraSlatSyncMotion.SWEEP_LENGTH
            AuroraSlatSyncMotion.heldFrame(
                p, holdDrift + seconds(holdStart, at), pushX * settle, pushY * settle,
                if (sweep in 0f..1f) sweep else -1f,
            )
        }
        RETURNING -> {
            val e = AuroraSlatSyncMotion.inOut(AuroraSlatSyncMotion.seg(seconds(returnStart, at), 0f, RETURN))
            AuroraSlatSyncMotion.heldFrame(fromP * (1f - e), fromDrift + (toDrift - fromDrift) * e, 0f, 0f, -1f)
        }
        else -> AuroraSlatSyncMotion.loopFrame(
            if (autoPlay && origin != 0L && at != 0L) AuroraSlatSyncMotion.wrap(max(0f, seconds(origin, at))) else 0f,
        )
    }

    /** Advances the idle return; true when the autoplay sweep starts (one light haptic). */
    fun step(at: Long, autoPlay: Boolean): Boolean {
        when (mode) {
            HELD -> if (!dragging && seconds(releaseAt, at) >= 2f) {
                val drift = AuroraSlatSyncMotion.wrap(holdDrift + seconds(holdStart, at))
                fromP = p
                fromDrift = drift
                toDrift = if (drift < LOOP / 2f) 0f else LOOP
                returnStart = at
                mode = RETURNING
            }
            RETURNING -> if (seconds(returnStart, at) >= RETURN) {
                origin = at
                tick = -1L
                mode = PLAYING
            }
            else -> if (autoPlay) {
                val next = floor((seconds(origin, at) + LOOP - AuroraSlatSyncMotion.SWEEP_START) / LOOP).toLong()
                val fire = tick >= 0L && next > tick
                tick = next
                return fire
            }
        }
        return false
    }

    fun begin(autoPlay: Boolean) {
        val frozen = frame(now, autoPlay)
        dragging = true
        startP = frozen.p
        p = frozen.p
        holdDrift = frozen.drift
        holdStart = now
        pushX = 0f
        pushY = 0f
        mode = HELD
    }

    /** Finger travel in dp; true when the sync has just been pushed to full. */
    fun drag(dx: Float, dy: Float): Boolean {
        if (!dragging) return false
        pushX = AuroraSlatSyncMotion.rubber(dx)
        pushY = AuroraSlatSyncMotion.rubber(dy)
        val next = min(1f, max(0f, startP + dx / 300f - dy / 460f))
        val full = next >= 1f && p < 1f
        if (full) sweepAt = now
        p = next
        return full
    }

    fun end() {
        if (!dragging) return
        dragging = false
        releaseAt = now
    }

    companion object {
        const val PLAYING = 0
        const val HELD = 1
        const val RETURNING = 2
        const val RETURN = 0.9f
    }
}

// Scene, in a 402 × 874 design space scaled to the width it is given.

private const val DESIGN_WIDTH = 402f
private const val DESIGN_HEIGHT = 874f
private const val CENTER_X = 201f
private const val STACK_Y = 366f
private const val SLATS = 11
private const val PITCH = 44f
private const val SLAT_HEIGHT = 36f

// Per-slat refraction: sideways displacement (× 22) and blur amount (0 sharp … 1 soft).
private val OFFSETS = floatArrayOf(-0.62f, 0.38f, -0.21f, 0.74f, -0.48f, 0.12f, 0.57f, -0.33f, 0.81f, -0.70f, 0.26f)
private val BLURS = floatArrayOf(0.95f, 0.55f, 0.80f, 0.30f, 0.62f, 0.18f, 0.45f, 0.70f, 0.35f, 0.85f, 1.0f)

// The aurora's three bodies: cobalt, teal, lime.
private val COLORS = arrayOf(Color(0.110f, 0.220f, 1.0f), Color(0.200f, 0.740f, 0.760f), Color(0.640f, 0.980f, 0.300f))
private val RISE_Y = floatArrayOf(-0.52f, 0.04f, 0.56f)
private val WANDER_Y = floatArrayOf(1f, 2f, 1f)
private val PHASE_Y = floatArrayOf(0.0f, 2.1f, 4.0f)
private val SWAY_X = floatArrayOf(0.46f, 0.34f, 0.50f)
private val WANDER_X = floatArrayOf(2f, 1f, 3f)
private val PHASE_X = floatArrayOf(0.6f, 2.9f, 4.4f)
private val RADIUS_X = floatArrayOf(0.86f, 0.66f, 0.62f)

private val BACKGROUND = Color(0.071f, 0.039f, 0.149f)
private val VIGNETTE_CORE = Color(0.106f, 0.063f, 0.227f)
private val VIGNETTE_EDGE = Color(0.043f, 0.024f, 0.094f)
private val FLASH = Color(0.90f, 0.98f, 1.0f)
private val SYNCED = Color(0.72f, 0.98f, 0.44f)

/** Labels measured once at 1 px per design unit; the canvas scale sizes them. */
private class AuroraSlatSyncText(private val measurer: TextMeasurer) {
    private val unit = Density(1f, 1f)
    private val numbers = arrayOfNulls<TextLayoutResult>(101)

    private fun layout(text: String, size: Int, weight: FontWeight, color: Color) = measurer.measure(
        text = text,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = weight,
            fontFeatureSettings = "tnum",
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
        softWrap = false,
        maxLines = 1,
        density = unit,
    )

    val title = layout("Halo Vault", 17, FontWeight.SemiBold, Color.White.copy(alpha = 0.94f))
    val subtitle = layout("Studio library", 13, FontWeight.Normal, Color.White.copy(alpha = 0.5f))
    val syncing = layout("Syncing…", 17, FontWeight.SemiBold, Color.White.copy(alpha = 0.9f))
    val synced = layout("All synced", 17, FontWeight.SemiBold, SYNCED)
    val detail = layout("1,284 files · 3 devices", 13, FontWeight.Normal, Color.White.copy(alpha = 0.46f))

    fun number(percent: Int): TextLayoutResult {
        val index = percent.coerceIn(0, 100)
        return numbers[index] ?: layout("$index%", 46, FontWeight.Light, Color.White.copy(alpha = 0.96f))
            .also { numbers[index] = it }
    }
}

private fun DrawScope.drawAuroraSlatSync(f: AuroraSlatSyncFrame, text: AuroraSlatSyncText) {
    drawRect(BACKGROUND)
    val k = size.width / DESIGN_WIDTH
    if (k <= 0f) return
    withTransform({
        scale(k, k, pivot = Offset.Zero)
        translate(0f, (size.height / k - DESIGN_HEIGHT) / 2f)
    }) {
        // Vignette: a slightly lifted indigo behind the stack, darker towards the corners.
        drawCircle(
            brush = Brush.radialGradient(
                0f to VIGNETTE_CORE, 0.55f to BACKGROUND, 1f to VIGNETTE_EDGE,
                center = Offset(CENTER_X, STACK_Y), radius = 560f,
            ),
            radius = 1200f, center = Offset(CENTER_X, STACK_Y),
        )

        val p = f.p
        val eased = AuroraSlatSyncMotion.smooth(p)
        val energy = 0.86f + 0.14f * p
        val amps = floatArrayOf(0.80f + 0.20f * p, 0.88f, 0.34f + 0.66f * p)
        val turn = 2f * PI.toFloat() / LOOP
        val centersY = FloatArray(3) {
            RISE_Y[it] * p + 0.10f * sin(turn * WANDER_Y[it] * f.drift + PHASE_Y[it]) + f.pushY / 240f
        }

        // The light that escapes the glass: a wide, faint bloom behind the stack.
        for (b in 0 until 3) {
            blob(
                CENTER_X + f.pushX * 0.3f, STACK_Y + centersY[b] * 5f * PITCH,
                150f + 90f * p, 120f + 70f * p, COLORS[b], (0.05f + 0.10f * p) * amps[b], BlendMode.Plus,
            )
        }

        val sweepAt = if (f.sweep < 0f) -99f else -1.5f + 13f * AuroraSlatSyncMotion.smooth(f.sweep)
        val sweepAmp = if (f.sweep < 0f) 0f else sin(PI.toFloat() * min(f.sweep, 1f))
        val sigma = 0.42f + 0.30f * p
        val shear = Matrix()

        for (i in 0 until SLATS) {
            val di = (i - 5).toFloat()
            val u = di / 5f
            val half = 180f * sqrt(1f - 0.70f * u * u)
            val dim = 1f - 0.5f * u * u
            val top = STACK_Y + di * PITCH - SLAT_HEIGHT / 2f
            val slope = -3.5f * u / half
            val lit = AuroraSlatSyncMotion.smooth(1f - (abs(di) - (1.9f + 3.0f * p)) / 1.6f)
            val blur = BLURS[i]
            val band = half * (0.46f + 0.54f * eased) * (0.75f + 0.25f * lit)
            val flash = sweepAmp * exp(-(i - sweepAt) * (i - sweepAt) / 1.6f)
            val shift = OFFSETS[i] * 22f * (0.5f + 0.5f * p) + f.pushX * (0.55f + 0.6f * blur)

            // The slat: a strip of glass seen slightly from the side, so its edges fan out.
            val x0 = -half - 26f
            val x1 = half + 26f
            val quad = Path().apply {
                moveTo(CENTER_X + x0, top + slope * x0)
                lineTo(CENTER_X + x1, top + slope * x1)
                lineTo(CENTER_X + x1, top + SLAT_HEIGHT + 0.4f * slope * x1)
                lineTo(CENTER_X + x0, top + SLAT_HEIGHT + 0.4f * slope * x0)
                close()
            }
            drawPath(
                quad,
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.2f to Color.White.copy(alpha = 0.034f * dim),
                    0.8f to Color.White.copy(alpha = 0.034f * dim),
                    1f to Color.Transparent,
                    startX = CENTER_X + x0, endX = CENTER_X + x1,
                ),
                style = Fill,
            )

            // Inside it the aurora is displaced, stretched and blurred by this slat's own amount:
            // brightest under the sharp top edge, gone before the bottom one.
            val pane = Path().apply {
                moveTo(CENTER_X + x0 - 70f, top + slope * (x0 - 70f))
                lineTo(CENTER_X + x1 + 70f, top + slope * (x1 + 70f))
                lineTo(CENTER_X + x1 + 70f, top + SLAT_HEIGHT + 4f)
                lineTo(CENTER_X + x0 - 70f, top + SLAT_HEIGHT + 4f)
                close()
            }
            shear.reset()
            shear[0, 1] = slope
            clipPath(pane) {
                withTransform({
                    translate(CENTER_X, top)
                    transform(shear)
                }) {
                    for (b in 0 until 3) {
                        val d = (u - centersY[b]) / sigma
                        val alpha = energy * lit * dim * exp(-d * d) * amps[b] * (1f - 0.25f * blur)
                        val x = SWAY_X[b] * band * sin(turn * WANDER_X[b] * f.drift + PHASE_X[b] + i * 0.55f) + shift
                        blob(
                            x, SLAT_HEIGHT * 0.10f,
                            band * RADIUS_X[b] * (1f + 0.45f * blur), SLAT_HEIGHT * (1.9f + 0.5f * blur),
                            COLORS[b], min(1f, alpha * 1.7f),
                        )
                    }
                    if (flash > 0.003f) {
                        blob(
                            shift * 0.4f, SLAT_HEIGHT * 0.10f, half * 1.05f, SLAT_HEIGHT * 1.9f,
                            FLASH, 0.38f * flash * dim, BlendMode.Plus,
                        )
                    }
                }
            }

            // The soft bottom edge of reeded glass: the image falls away into the dark.
            drawPath(
                pane,
                Brush.verticalGradient(
                    0f to BACKGROUND.copy(alpha = 0f),
                    0.6f to BACKGROUND.copy(alpha = 0.42f + 0.2f * blur),
                    1f to BACKGROUND.copy(alpha = 0.92f),
                    startY = top + SLAT_HEIGHT * 0.5f, endY = top + SLAT_HEIGHT + 2f,
                ),
            )

            // Edges catch light: a hairline on the glass, brighter where the aurora sits under it.
            edgeLine(x0 + 20f, x1 - 20f, slope, top, 0.09f * dim)
            edgeLine(
                shift - band * 1.1f, shift + band * 1.1f, slope, top,
                min(1f, 0.55f * lit * energy * dim + 0.7f * flash * dim),
            )
        }

        ornaments()
        labels(f, text)
    }
}

/** A soft elliptical body of light: a radial falloff squashed to rx × ry. */
private fun DrawScope.blob(
    x: Float, y: Float, rx: Float, ry: Float, color: Color, alpha: Float, blend: BlendMode = BlendMode.SrcOver,
) {
    if (alpha <= 0.002f || rx <= 0.5f) return
    withTransform({
        translate(x, y)
        scale(rx, ry, pivot = Offset.Zero)
    }) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to color.copy(alpha = alpha),
                0.5f to color.copy(alpha = alpha * 0.86f),
                0.8f to color.copy(alpha = alpha * 0.30f),
                1f to color.copy(alpha = 0f),
                center = Offset.Zero, radius = 1f,
            ),
            radius = 1f, center = Offset.Zero, blendMode = blend,
        )
    }
}

private fun DrawScope.edgeLine(a: Float, b: Float, slope: Float, top: Float, alpha: Float) {
    if (alpha <= 0.004f || b <= a) return
    val start = Offset(CENTER_X + a, top + slope * a)
    val end = Offset(CENTER_X + b, top + slope * b)
    drawLine(
        Brush.linearGradient(
            0f to Color.Transparent, 0.5f to Color.White.copy(alpha = alpha), 1f to Color.Transparent,
            start = start, end = end,
        ),
        start, end, strokeWidth = 1f,
    )
}

private fun DrawScope.ornaments() {
    // Crosshair on the stack's centre.
    val white = Color.White.copy(alpha = 0.9f)
    drawLine(white, Offset(CENTER_X - 11f, STACK_Y), Offset(CENTER_X + 11f, STACK_Y), strokeWidth = 1f)
    drawLine(white, Offset(CENTER_X, STACK_Y - 11f), Offset(CENTER_X, STACK_Y + 11f), strokeWidth = 1f)

    // Viewfinder corners, top right.
    val l = 346f
    val t = 62f
    val s = 30f
    val a = 8f
    val corners = Path()
    for ((cx, cy, dx, dy) in listOf(
        floatArrayOf(l, t, 1f, 1f), floatArrayOf(l + s, t, -1f, 1f),
        floatArrayOf(l, t + s, 1f, -1f), floatArrayOf(l + s, t + s, -1f, -1f),
    )) {
        corners.moveTo(cx + dx * a, cy)
        corners.lineTo(cx, cy)
        corners.lineTo(cx, cy + dy * a)
    }
    drawPath(corners, Color.White.copy(alpha = 0.55f), style = Stroke(width = 1f))

    // Dot grids in two corners: a staircase of small dots.
    val dot = Color.White.copy(alpha = 0.42f)
    for (row in 0 until 4) {
        for (col in 0 until 4 - row) {
            drawCircle(dot, 1f, Offset(26f + col * 6f, 122f + row * 6f))
            drawCircle(dot, 1f, Offset(376f - col * 6f, 626f - row * 6f))
        }
    }
}

private fun DrawScope.labels(f: AuroraSlatSyncFrame, text: AuroraSlatSyncText) {
    leading(text.title, 24f, 77f, 1f)
    leading(text.subtitle, 24f, 98f, 1f)
    centered(text.number(f.percent), 672f, f.numberAlpha)
    if (f.syncingAlpha > 0.003f) centered(text.syncing, 716f - 6f * (1f - f.syncingAlpha), f.syncingAlpha)
    if (f.syncedAlpha > 0.003f) centered(text.synced, 716f + 6f * (1f - f.syncedAlpha), f.syncedAlpha)
    centered(text.detail, 742f, 1f)
}

private fun DrawScope.centered(layout: TextLayoutResult, y: Float, alpha: Float) {
    if (alpha <= 0.003f) return
    drawText(
        layout,
        topLeft = Offset(CENTER_X - layout.size.width / 2f, y - layout.size.height / 2f),
        alpha = min(1f, alpha),
    )
}

private fun DrawScope.leading(layout: TextLayoutResult, x: Float, y: Float, alpha: Float) {
    drawText(layout, topLeft = Offset(x, y - layout.size.height / 2f), alpha = alpha)
}
