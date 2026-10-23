package com.ngocdevv.nativecook.blocks.progress.timerborderprogress

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val WIDTH = 330f
private const val HEIGHT = 200f

// The canvas overflows the widget by this much on every side to fit the panel's shadow.
private val BLEED = 30.dp

private const val PILL_X = 67.3f
private const val PILL_W = 195.4f
private const val PILL_H = 35.3f
private const val RING_X = 86.2f
private const val RING_Y = PILL_H / 2f
private const val RING_R = 10.6f

private const val PANEL_Y = 57.4f
private const val PANEL_H = HEIGHT - PANEL_Y
private const val PANEL_R = 36f

// The progress stroke runs round the panel, leaving a gap at the top centre.
private const val STROKE = 7f
private const val EDGE = 10.5f
private const val GAP_FROM = 107.5f
private const val GAP_TO = 222.5f

private const val BUTTON_Y = 128.6f
private const val BUTTON_R = 32.1f
private const val PAUSE_X = 67.2f
private const val CANCEL_X = 262.8f
private const val DIGIT_W = 24.4f
private const val DIGIT_H = 48f

// The reference's frame: 13:12 left of a 16:15 timer.
private const val DURATION = 975f
private const val REST_S = 792f

// The autoPlay loop (seconds): wind up to the full duration, race down to the reference, tick in real time.
private const val WIND = 0.7f
private const val RACE = 2.6f
private const val HOLD = 5.2f
private const val LOOP = WIND + RACE + HOLD
private const val LANDED = WIND + RACE

private val Orange = Color(0xFFFF9F0A)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

private fun timerText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    fontFeatureSettings = "tnum",
    lineHeightStyle = Centered,
    platformStyle = NoFontPadding,
)

private val CompactStyle = timerText(14.6f, 20f, FontWeight.SemiBold, Orange)
private val AlarmStyle = timerText(14.8f, 20f, FontWeight.Medium, Color(0xFF5A5A5E))
private val TimeStyle = timerText(40.5f, DIGIT_H, FontWeight.Bold, Color.White)
private val LabelStyle = timerText(15.6f, 21f, FontWeight.Medium, Color(0xFF5A5A5E))

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

/** Share of the reference timer left at [t] seconds into the autoPlay loop. */
private fun loopValue(t: Float) = when {
    t < WIND -> (REST_S - HOLD + (DURATION - REST_S + HOLD) * easeInOutCubic(t / WIND)) / DURATION
    t < LANDED -> (DURATION - (DURATION - REST_S) * easeInOutCubic((t - WIND) / RACE)) / DURATION
    else -> (REST_S - (t - LANDED)) / DURATION
}

/** 0…1: how fast the time is being wound at [t] seconds into the loop; 0 while it ticks in real time. */
private fun loopActivity(t: Float) = when {
    t < WIND -> sin(PI.toFloat() * (t / WIND))
    t < LANDED -> sin(PI.toFloat() * ((t - WIND) / RACE))
    else -> 0f
}

/** A cheap repeatable random number in 0…1. */
private fun noise(seed: Float): Float {
    val v = sin(seed * 127.1 + 31.7) * 43758.5453
    return (v - floor(v)).toFloat()
}

private fun timecode(seconds: Int) = String.format(Locale.US, "%02d:%02d", max(0, seconds) / 60, max(0, seconds) % 60)

/** Places the element by its leading edge and vertical centre inside the widget. */
private fun Modifier.leadingAt(x: Dp, centerY: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToPx(), centerY.roundToPx() - placeable.height / 2)
    }
}

/** Places the element by its trailing edge and vertical centre inside the widget. */
private fun Modifier.trailingAt(x: Dp, centerY: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToPx() - placeable.width, centerY.roundToPx() - placeable.height / 2)
    }
}

/** Places the element by its centre inside the widget. */
private fun Modifier.centerAt(x: Dp, y: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToPx() - placeable.width / 2, y.roundToPx() - placeable.height / 2)
    }
}

private class TimerPaints {
    // Starts right of the gap and runs clockwise back to its left.
    val border = Path().apply {
        val left = EDGE
        val right = WIDTH - EDGE
        val top = PANEL_Y + EDGE
        val bottom = HEIGHT - EDGE
        val r = PANEL_R - EDGE
        moveTo(GAP_TO, top)
        lineTo(right - r, top)
        arcTo(Rect(Offset(right - r, top + r), r), -90f, 90f, false)
        lineTo(right, bottom - r)
        arcTo(Rect(Offset(right - r, bottom - r), r), 0f, 90f, false)
        lineTo(left + r, bottom)
        arcTo(Rect(Offset(left + r, bottom - r), r), 90f, 90f, false)
        lineTo(left, top + r)
        arcTo(Rect(Offset(left + r, top + r), r), 180f, 90f, false)
        lineTo(GAP_FROM, top)
    }
    val measure = PathMeasure().apply { setPath(border, false) }
    val left = Path()
    val sparks = Path()
    val bell = Path().apply {
        moveTo(-4.7f, 3.1f)
        cubicTo(-3.5f, 2.1f, -3.4f, 0.6f, -3.4f, -1f)
        cubicTo(-3.4f, -3.3f, -1.9f, -4.9f, 0f, -4.9f)
        cubicTo(1.9f, -4.9f, 3.4f, -3.3f, 3.4f, -1f)
        cubicTo(3.4f, 0.6f, 3.5f, 2.1f, 4.7f, 3.1f)
        close()
        addArc(Rect(Offset(0f, 4.1f), 1.3f), 0f, 180f)
        close()
    }
    val sheet = Brush.verticalGradient(
        listOf(rgba(246, 246, 246, 1f), rgba(246, 246, 246, 0f)),
        startY = PANEL_Y + PANEL_H - 50f,
        endY = PANEL_Y + PANEL_H + 44f,
    )
    val shadow = SoftRoundRect(rgba(0, 0, 0, 0.3f), sigma = 14f)

    private fun soft(color: Color, sigma: Float, stroke: Float? = null) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        if (stroke != null) {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        }
    }

    val bloom = soft(rgba(255, 159, 10, 0.35f), 5f, STROKE + 2f)
    val glow = soft(rgba(255, 176, 46, 0.9f), 5f)
    val core = soft(Color(0xFFFFF3D6), 1.1f)
}

/**
 * Timer live activity: a compact black pill with a small ring and the time, and an expanded
 * black panel whose border is an orange stroke that shrinks as the time runs out, with pause and
 * cancel buttons around the big time. The tip of the stroke burns: it glows, beats with every
 * second and throws sparks (a shower while the time is being wound), the digits of the big
 * time roll like an odometer on each tick, and a paused timer breathes.
 *
 * @param progress Controlled share of the timer that is left, 0…1. Leave null to show the reference's 13:12 (or the autoPlay loop).
 * @param autoPlay Runs by itself: winds up to the full duration, races down to 13:12, ticks there in real time, and starts over.
 * @param duration Length of the timer in seconds.
 * @param alarm Time of day the timer goes off.
 */
@Composable
fun TimerBorderProgress(
    progress: Float? = null,
    autoPlay: Boolean = false,
    duration: Int = 975,
    alarm: String = "14:44",
    label: String = "Timer",
    onPauseChange: (Boolean) -> Unit = {},
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val controlled = progress != null
    val target = (progress ?: (REST_S / DURATION)).coerceIn(0f, 1f)
    val auto = autoPlay && !controlled && !reducedMotion
    val looping by rememberUpdatedState(auto)
    var paused by remember { mutableStateOf(false) }

    // Seconds: `time` never stops (sparks, breathing), `clock` is the position in the autoPlay loop.
    val time = remember { mutableFloatStateOf(0f) }
    val clock = remember { mutableFloatStateOf(if (auto) 0f else LANDED) }
    var playing by remember { mutableStateOf(false) }
    val held = remember { Animatable(target) }
    // When a controlled value last changed, on the `time` axis.
    val movedAt = remember { mutableFloatStateOf(-10f) }

    LaunchedEffect(auto) {
        playing = auto
        clock.floatValue = if (auto) 0f else LANDED
    }

    LaunchedEffect(controlled, target) {
        if (!controlled || held.targetValue == target) return@LaunchedEffect
        movedAt.floatValue = time.floatValue
        held.animateTo(target, tween(if (reducedMotion) 200 else 800, easing = EaseOutStrong))
    }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = min((now - last) / 1e9f, 0.064f)
            last = now
            time.floatValue += dt
            // Pause really pauses: the loop's clock stands still.
            if (!playing || paused) continue
            var t = clock.floatValue + dt
            if (looping) {
                if (t >= LOOP) t -= LOOP
            } else if (t >= LANDED) {
                // A tapped, one-off run stops at the resting frame.
                t = LANDED
                playing = false
            }
            clock.floatValue = t
        }
    }

    // Read inside draw / layer lambdas so a frame only redraws, it does not recompose.
    val value = { clamp01(if (controlled) held.value else loopValue(clock.floatValue)) }
    val seconds = { value() * (if (controlled) duration.toFloat() else DURATION) }
    val activity = {
        when {
            reducedMotion -> 0f
            !controlled -> if (playing) loopActivity(clock.floatValue) else 0f
            else -> {
                val u = (time.floatValue - movedAt.floatValue) / 0.7f
                if (u <= 0f || u >= 1f) 0f else sin(PI.toFloat() * u)
            }
        }
    }
    val running = { playing && !paused }
    // The read-outs are real text: recompose only when a whole second changes.
    val shown by remember(controlled, duration) { derivedStateOf { ceil(seconds() - 1e-4f).toInt() } }
    // Seconds since the digits last changed, while the timer ticks: a new digit rolls up like an odometer.
    val sinceTick = { if (running() && activity() == 0f) shown - seconds() else 1f }

    val restart = {
        if (!controlled && !reducedMotion) {
            clock.floatValue = 0f
            playing = true
        }
    }
    val paints = remember { TimerPaints() }
    val pauseBlend by animateFloatAsState(if (paused) 1f else 0f, tween(200), label = "paused")

    Box(
        modifier = modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .pointerInput(controlled, reducedMotion) { detectTapGestures { restart() } }
            .semantics {
                contentDescription = "$label, alarm at $alarm" + if (paused) ", paused" else ""
                stateDescription = "${timecode(shown)} left"
                progressBarRangeInfo = ProgressBarRangeInfo(shown / (if (controlled) duration.toFloat() else DURATION), 0f..1f)
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    translate(x, y) {
                        drawTimer(paints, value(), seconds(), time.floatValue, activity(), running(), pauseBlend)
                    }
                }
            }
        }

        val now = timecode(shown)
        val before = timecode(shown + 1)
        BasicText(now, style = CompactStyle, maxLines = 1, modifier = Modifier.trailingAt(255.6.dp, RING_Y.dp).clearAndSetSemantics {})
        BasicText(alarm, style = AlarmStyle, maxLines = 1, modifier = Modifier.leadingAt(153.4.dp, 95.4.dp).clearAndSetSemantics {})
        Row(Modifier.centerAt((WIDTH / 2f).dp, 128.4.dp).clearAndSetSemantics {}) {
            for (index in 0 until 5) {
                if (index == 2) {
                    Box(Modifier.size(12.dp, DIGIT_H.dp).offset(y = (-3).dp), contentAlignment = Alignment.Center) {
                        BasicText(":", style = TimeStyle)
                    }
                    continue
                }
                val rolls = now[index] != before[index]
                Box(Modifier.size(DIGIT_W.dp, DIGIT_H.dp).clipToBounds(), contentAlignment = Alignment.Center) {
                    if (rolls) {
                        // The digit that just went: slides up and out.
                        BasicText(
                            text = before[index].toString(),
                            style = TimeStyle,
                            modifier = Modifier.graphicsLayer {
                                val since = sinceTick()
                                translationY = -DIGIT_H * 0.55f * EaseOutStrong.transform(clamp01(since / 0.32f)) * density
                                alpha = 1f - clamp01(since / 0.2f)
                            },
                        )
                    }
                    BasicText(
                        text = now[index].toString(),
                        style = TimeStyle,
                        modifier = Modifier.graphicsLayer {
                            if (rolls) {
                                val since = sinceTick()
                                translationY = DIGIT_H * 0.55f * (1f - EaseOutStrong.transform(clamp01(since / 0.32f))) * density
                                alpha = clamp01(since / 0.2f)
                            }
                        },
                    )
                }
            }
        }
        BasicText(label, style = LabelStyle, maxLines = 1, modifier = Modifier.centerAt((WIDTH / 2f).dp, 159.8.dp).clearAndSetSemantics {})

        RoundButton(PAUSE_X, Color(0xFF262628), if (paused) "Resume" else "Pause", onClick = {
            paused = !paused
            onPauseChange(paused)
        }) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val unit = size.width / (BUTTON_R * 2f)
            // Two bars while the timer runs, a triangle while it is paused.
            for (dx in floatArrayOf(-7.3f, 7.3f)) {
                drawLine(
                    Color.White,
                    Offset(c.x + dx * unit, c.y - 8.6f * unit),
                    Offset(c.x + dx * unit, c.y + 8.6f * unit),
                    6.6f * unit,
                    StrokeCap.Round,
                    alpha = 1f - pauseBlend,
                )
            }
            val triangle = Path().apply {
                moveTo(c.x - 6.5f * unit, c.y - 10.5f * unit)
                lineTo(c.x + 11f * unit, c.y)
                lineTo(c.x - 6.5f * unit, c.y + 10.5f * unit)
                close()
            }
            drawPath(triangle, Color.White, alpha = pauseBlend)
            drawPath(triangle, Color.White, alpha = pauseBlend, style = Stroke(5f * unit, join = StrokeJoin.Round))
        }
        RoundButton(CANCEL_X, Color(0xFF4A1010), "Cancel", onClick = {
            paused = false
            restart()
            onCancel()
        }) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val reach = 8.3f * size.width / (BUTTON_R * 2f)
            val width = 7f * size.width / (BUTTON_R * 2f)
            drawLine(Color(0xFFFF3B3B), Offset(c.x - reach, c.y - reach), Offset(c.x + reach, c.y + reach), width, StrokeCap.Round)
            drawLine(Color(0xFFFF3B3B), Offset(c.x + reach, c.y - reach), Offset(c.x - reach, c.y + reach), width, StrokeCap.Round)
        }
    }
}

/** Round button face with a drawn glyph; scales down while pressed. */
@Composable
private fun RoundButton(x: Float, color: Color, label: String, onClick: () -> Unit, glyph: DrawScope.() -> Unit) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed) {
        if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
    val press by animateFloatAsState(if (pressed) 1f else 0f, tween(150, easing = EaseOutStrong), label = "press")
    Canvas(
        Modifier
            .centerAt(x.dp, BUTTON_Y.dp)
            .size((BUTTON_R * 2f).dp)
            .graphicsLayer {
                val scale = 1f - 0.07f * press
                scaleX = scale
                scaleY = scale
            }
            .background(color, CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = label, onClick = onClick),
    ) { glyph() }
}

private fun DrawScope.drawTimer(
    paints: TimerPaints,
    value: Float,
    seconds: Float,
    time: Float,
    activity: Float,
    running: Boolean,
    paused: Float,
) {
    val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    // A paused timer breathes.
    val lit = 1f - paused * (0.3f + 0.2f * sin(time * 3.4f))

    // The lighter sheet the expanded panel sits on.
    drawRoundRect(paints.sheet, Offset(-9f, PANEL_Y - 11f), Size(WIDTH + 18f, PANEL_H + 60f), CornerRadius(46f))
    drawIntoCanvas { paints.shadow.draw(it.nativeCanvas, 10f, PANEL_Y + 16f, WIDTH - 20f, PANEL_H - 10f, PANEL_R) }

    // Compact pill; its ring mirrors the stroke.
    drawRoundRect(Color.Black, Offset(PILL_X, 0f), Size(PILL_W, PILL_H), CornerRadius(PILL_H / 2f))
    val ring = Offset(RING_X, RING_Y)
    drawCircle(Color(0xFF2C2C2E), RING_R, ring, style = Stroke(2.2f))
    drawArc(
        color = Orange,
        startAngle = -90f,
        sweepAngle = (value * 360f).coerceIn(0.01f, 359.9f),
        useCenter = false,
        topLeft = Offset(RING_X - RING_R, RING_Y - RING_R),
        size = Size(RING_R * 2f, RING_R * 2f),
        alpha = lit,
        style = Stroke(2.2f, cap = StrokeCap.Round),
    )

    // Expanded panel
    drawRoundRect(Color.Black, Offset(0f, PANEL_Y), Size(WIDTH, PANEL_H), CornerRadius(PANEL_R))
    val stroke = Stroke(STROKE, cap = StrokeCap.Round)
    drawPath(paints.border, Color(0xFF2A2A2C), style = stroke)
    val length = paints.measure.length
    val reach = max(0.01f, value * length)
    paints.left.rewind()
    paints.measure.getSegment(0f, reach, paints.left, true)
    // A faint bloom along the whole stroke, stronger while the time is being wound.
    if (running && blurs) {
        paints.bloom.alpha = (255f * 0.35f * (0.22f + 0.5f * activity)).roundToInt().coerceIn(0, 255)
        drawIntoCanvas { it.nativeCanvas.drawPath(paints.left.asAndroidPath(), paints.bloom) }
    }
    drawPath(paints.left, Orange, alpha = lit, style = stroke)

    // The burning tip of the stroke: it beats with every second and flares while the time is being wound.
    if (running) {
        val tip = paints.measure.getPosition(reach)
        val beat = (1f - activity) * exp(-5f * (ceil(seconds) - seconds))
        if (blurs) {
            drawIntoCanvas {
                val canvas = it.nativeCanvas
                paints.glow.alpha = (255f * 0.9f * (0.55f + 0.3f * beat + 0.15f * activity)).roundToInt().coerceIn(0, 255)
                canvas.drawCircle(tip.x, tip.y, 5.5f + 3f * beat + 3.5f * activity, paints.glow)
                paints.core.alpha = (255f * (0.5f + 0.5f * max(beat, activity))).roundToInt().coerceIn(0, 255)
                canvas.drawCircle(tip.x, tip.y, 2.7f, paints.core)
            }
        }
        // Sparks thrown off the tip: a couple while it ticks, a shower while the time is being wound.
        paints.sparks.rewind()
        for (i in 0 until (2f + 7f * activity).roundToInt()) {
            val cycle = time / 0.75f + i / 9f
            val age = cycle - floor(cycle)
            val seed = floor(cycle) * 9f + i
            val angle = noise(seed) * PI.toFloat() * 2f
            val throwReach = (9f + 14f * noise(seed + 0.5f)) * (1f - (1f - age).pow(2))
            val r = 1.35f * (1f - age)
            paints.sparks.addOval(Rect(Offset(tip.x + cos(angle) * throwReach, tip.y + sin(angle) * throwReach + 7f * age * age), r))
        }
        drawPath(paints.sparks, Color(0xFFFFD98A))
    }

    // Alarm bell in front of the alarm time.
    translate(142.9f, 95.4f) { drawPath(paints.bell, Color(0xFF5A5A5E)) }
}
