package com.ngocdevv.nativecook.blocks.progress.rulerdial

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import android.graphics.BlurMaskFilter
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WIDTH = 330f
private const val HEIGHT = 124f
private const val RADIUS = 18f

// The canvas overflows the panel by this much on every side to fit its shadow.
private val BLEED = 40.dp

private const val CX = WIDTH / 2

// The ticks fan out from a point well below the panel.
private const val PIVOT_Y = 211f
private const val TICK_OUTER = 150.6f
private const val TICK_INNER = 123.4f
private const val TICK_W = 1.7f

// One unit of value turns the fan by this much, which is one tick.
private const val STEP = 3.55f * Math.PI.toFloat() / 180f

// Ticks further than this from the needle fall outside the panel.
private const val TICKS_EACH_SIDE = 15

// How far a finger travels per unit: the tick spacing under the needle.
private const val UNIT_DP = TICK_OUTER * STEP
private const val NEEDLE_TOP = 50f
private const val NEEDLE_W = 2.9f
private const val VALUE_Y = 27.5f
private const val DIGIT_W = 10.2f
private const val DIGIT_H = 22f

private val AUTO_STOPS = listOf(95f, 68f, 49f, 0f)
private const val AUTO_HOLD_MS = 1300L
private const val AUTO_RESUME_MS = 3000L

private val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

private val DigitStyle = TextStyle(
    color = Color.White,
    fontSize = 16.sp,
    lineHeight = DIGIT_H.sp,
    textAlign = TextAlign.Center,
    fontFeatureSettings = "tnum",
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

private class DialMotion {
    /** The value under the needle; a fraction while the dial turns. */
    var value by mutableFloatStateOf(0f)

    /** 0…1, how fast the dial is turning: swells the ticks passing the needle and lights its tip. */
    var heat by mutableFloatStateOf(0f)

    /** Radians the needle is bent by the passing ticks; springs back when they stop. */
    var lean by mutableFloatStateOf(0f)
    var leanSpeed = 0f

    /** The move in progress: a coast after a flick, a step, or a controlled change. */
    var glide: Job? = null
    var resume: Job? = null

    /** The whole number last clicked past by the user. */
    var tick = 0

    val ticks = Path()
    val panel = Path()

    // Hardware canvases only honour mask filters from API 28; below that the needle has no glow.
    val glow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Paint().apply {
            color = Color.White
            asFrameworkPaint().maskFilter = BlurMaskFilter(sigmaToRadius(7f), BlurMaskFilter.Blur.NORMAL)
        }
    } else {
        null
    }
    val shadow = SoftRoundRect(rgba(0, 0, 0, 0.3f), sigma = 14f)

    suspend fun glideTo(target: Float, millis: Int, easing: Easing, onStep: (Float) -> Unit = {}) {
        animate(value, target, animationSpec = tween(millis, easing = easing)) { now, _ ->
            value = now
            onStep(now)
        }
    }
}

/**
 * Black panel with a fan of ruler ticks under a white needle and the value above it. Drag sideways
 * to turn the fan: ticks swell and light up as they pass the needle, the needle bends with them and
 * springs back, the digits roll like an odometer, and every tick is a light haptic click.
 *
 * @param progress Position on the dial, 0…1 of [max]. Leave null to let the dial keep its own value.
 * @param autoPlay Sweeps the dial by itself (0 → 95 → 68 → 49 → 0) until it is dragged, and again a while after.
 * @param onValueChange Called with the whole number under the needle while the user turns the dial.
 */
@Composable
fun RulerDial(
    progress: Float? = null,
    autoPlay: Boolean = false,
    max: Float = 100f,
    label: String = "Dial",
    onValueChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val motion = remember { DialMotion() }
    val onChange by rememberUpdatedState(onValueChange)
    val controlled = progress != null
    var paused by remember { mutableStateOf(false) }
    var autoRun by remember { mutableIntStateOf(0) }
    val playing = autoPlay && !controlled && !still && !paused

    // One click per tick while the user turns the dial; the demo sweep stays silent.
    fun click(now: Float) {
        val whole = now.roundToInt()
        if (whole == motion.tick) return
        motion.tick = whole
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        onChange(whole)
    }

    var primed by remember { mutableIntStateOf(0) }
    LaunchedEffect(progress, max) {
        if (progress == null) return@LaunchedEffect
        val next = progress.coerceIn(0f, 1f) * max
        // The first value is shown without a run-up.
        if (primed++ == 0) motion.value = next else motion.glideTo(next, if (still) 200 else 700, EaseOutStrong)
    }

    // The demo sweep: from wherever the dial is to each stop in turn, with a rest at each.
    LaunchedEffect(playing, autoRun, max) {
        if (!playing) return@LaunchedEffect
        while (true) {
            for (stop in AUTO_STOPS) {
                delay(AUTO_HOLD_MS)
                val to = min(max, stop)
                motion.glideTo(to, (700 + abs(to - motion.value) * 22).toInt(), EaseInOut)
            }
        }
    }

    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        var last = withFrameNanos { it }
        var previous = motion.value
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceIn(0.001f, 0.05f)
            last = now
            val speed = (motion.value - previous) / dt
            previous = motion.value
            motion.heat += (min(1f, abs(speed) / 24f) - motion.heat) * 0.2f
            // The passing ticks push the needle over; a spring pulls it back and lets it wobble.
            val pushed = (-speed * 0.0022f).coerceIn(-0.07f, 0.07f)
            motion.leanSpeed += ((pushed - motion.lean) * 260f - motion.leanSpeed * 13f) * dt
            motion.lean += motion.leanSpeed * dt
        }
    }

    // Accessibility hears about whole numbers only, not every frame of a turn.
    val whole by remember { derivedStateOf { motion.value.roundToInt() } }

    // A sideways drag turns the dial; lifting lets it coast to the nearest tick.
    val drag = rememberDraggableState { delta ->
        // Dragging left brings higher numbers under the needle, like sliding a ruler.
        motion.value = (motion.value - delta / density / UNIT_DP).coerceIn(0f, max)
        click(motion.value)
    }

    Box(
        modifier = modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .draggable(
                state = drag,
                orientation = Orientation.Horizontal,
                enabled = !controlled,
                onDragStarted = {
                    // Take over from the demo sweep, wherever it was.
                    motion.glide?.cancel()
                    motion.resume?.cancel()
                    paused = true
                    motion.tick = motion.value.roundToInt()
                },
                onDragStopped = { velocity ->
                    val coast = (-velocity / density / UNIT_DP * 0.22f).coerceIn(-40f, 40f)
                    val to = (motion.value + coast).roundToInt().toFloat().coerceIn(0f, max)
                    motion.glide = scope.launch {
                        motion.glideTo(to, (320 + abs(coast) * 14).toInt(), EaseOutStrong) { click(it) }
                    }
                    // The demo picks up again a while after the dial was let go.
                    motion.resume = scope.launch {
                        delay(AUTO_RESUME_MS)
                        paused = false
                        autoRun++
                    }
                },
            )
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(whole.toFloat(), 0f..max, steps = max.toInt() - 1)
                setProgress { target ->
                    if (controlled) return@setProgress false
                    motion.glide?.cancel()
                    motion.glide = scope.launch { motion.glideTo(target.coerceIn(0f, max), 200, EaseOutStrong) { click(it) } }
                    true
                }
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    val value = motion.value
                    val heat = if (still) 0f else motion.heat
                    val cx = x + CX
                    val pivot = y + PIVOT_Y

                    drawIntoCanvas { motion.shadow.draw(it.nativeCanvas, x + 8, y + 14, WIDTH - 16, HEIGHT - 8, RADIUS) }
                    drawRoundRect(Color.Black, Offset(x, y), Size(WIDTH, HEIGHT), CornerRadius(RADIUS))
                    motion.panel.reset()
                    motion.panel.addRoundRect(RoundRect(x, y, x + WIDTH, y + HEIGHT, CornerRadius(RADIUS)))

                    // Every tick in view, as one path; the ones passing the needle stand a little taller.
                    motion.ticks.reset()
                    val nearest = value.roundToInt()
                    for (k in nearest - TICKS_EACH_SIDE..nearest + TICKS_EACH_SIDE) {
                        val steps = k - value
                        val a = steps * STEP
                        val outer = TICK_OUTER + 5f * heat * exp(-steps * steps * 1.2f)
                        motion.ticks.moveTo(cx + TICK_INNER * sin(a), pivot - TICK_INNER * cos(a))
                        motion.ticks.lineTo(cx + outer * sin(a), pivot - outer * cos(a))
                    }

                    clipPath(motion.panel) {
                        // Blue on the left, grey under the needle, gold on the right; the colours stay put
                        // while the ticks pass.
                        drawPath(
                            motion.ticks,
                            brush = Brush.horizontalGradient(
                                0f to rgba(110, 128, 196, 0f),
                                0.14f to rgba(118, 136, 200, 0.7f),
                                0.34f to Color(0xFF96A4D2),
                                0.5f to Color(0xFFB4B6BC),
                                0.66f to Color(0xFFC0B996),
                                0.86f to rgba(196, 174, 96, 0.7f),
                                1f to rgba(190, 166, 84, 0f),
                                startX = cx - 124f,
                                endX = cx + 124f,
                            ),
                            style = Stroke(TICK_W),
                        )
                        // The ticks nearest the needle catch its light while the dial turns.
                        if (heat > 0.01f) {
                            drawPath(
                                motion.ticks,
                                brush = Brush.horizontalGradient(
                                    0f to rgba(255, 255, 255, 0f),
                                    0.5f to rgba(255, 255, 255, 0.85f),
                                    1f to rgba(255, 255, 255, 0f),
                                    startX = cx - 22f,
                                    endX = cx + 22f,
                                ),
                                alpha = heat,
                                style = Stroke(TICK_W),
                            )
                        }

                        // The needle is bent by the passing ticks about a point below the panel.
                        val top = y + NEEDLE_TOP
                        withTransform({
                            rotate(motion.lean * 180f / Math.PI.toFloat(), pivot = Offset(cx, y + HEIGHT + 30f))
                        }) {
                            if (heat > 0.01f) {
                                motion.glow?.let { glow ->
                                    glow.alpha = 0.75f * heat
                                    drawIntoCanvas { it.drawRoundRect(cx - 5, top - 3, cx + 5, top + 37, 5f, 5f, glow) }
                                }
                            }
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.White,
                                    0.62f to Color.White,
                                    1f to rgba(255, 255, 255, 0.12f),
                                    startY = top,
                                    endY = y + HEIGHT,
                                ),
                                topLeft = Offset(cx - NEEDLE_W / 2, top),
                                size = Size(NEEDLE_W, HEIGHT - NEEDLE_TOP + 4f),
                                cornerRadius = CornerRadius(NEEDLE_W / 2),
                            )
                        }
                    }
                }
            }
        }

        // The read-out: each wheel is centred on its origin and slid to its place.
        Box(Modifier.offset(CX.dp, (VALUE_Y - DIGIT_H / 2).dp).clearAndSetSemantics {}) {
            for (place in 0..2) Wheel(motion, place, max)
        }
    }
}

/** One place of the read-out: a strip of digits that rolls like an odometer wheel. */
@Composable
private fun Wheel(motion: DialMotion, place: Int, max: Float) {
    val unit = 10f.pow(place)
    Box(
        Modifier
            .size(DIGIT_W.dp, DIGIT_H.dp)
            .graphicsLayer {
                val v = motion.value.coerceIn(0f, max)
                // Places come and go as the number gains or loses a digit, and the read-out stays centred.
                val places = 1f + seg(v, 9f, 10f) + seg(v, 99f, 100f)
                alpha = if (place == 0) 1f else seg(v, unit - 1f, unit - 0.4f)
                translationX = (places / 2f - place - 1f) * DIGIT_W * density
            }
            .clipToBounds(),
    ) {
        Column(
            Modifier
                // The strip keeps its full height inside the one-digit window that clips it.
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .graphicsLayer {
                    val v = motion.value.coerceIn(0f, max)
                    // A wheel only turns while the wheel to its right goes from 9 to 0.
                    val below = if (place == 0) 1f else seg((v % unit) - (unit - 1f), 0f, 1f)
                    val digit = if (place == 0) v % 10f else floor(v / unit) % 10f + below
                    translationY = -digit * DIGIT_H * density
                },
        ) {
            for (index in 0..10) {
                BasicText("${index % 10}", Modifier.size(DIGIT_W.dp, DIGIT_H.dp), style = DigitStyle)
            }
        }
    }
}
