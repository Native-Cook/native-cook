package com.ngocdevv.nativecook.blocks.progress.recruitersteps

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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private const val WIDTH = 340f
private const val HEIGHT = 139.2f
private const val RADIUS = 46f

// The canvas overflows the panel by this much on every side to fit its shadow.
private val BLEED = 40.dp

private const val STEPS = 5
private const val STEP_X = 36.4f
private const val STEP_GAP = 66.6f
private const val STEP_Y = 71.3f
private const val DOT_R = 12.8f
private const val RING_R = 12.4f
private const val RING_W = 2.9f

// A dash starts and ends this far from the centres of the circles it joins.
private const val DASH_FROM = 19f
private const val DASH_TO = 47.6f
private const val DASH_W = 4.1f
private val ICON_X = floatArrayOf(279.8f, 295.3f, 306.7f)
private const val ICON_Y = 34.3f
private const val ICON_R = 10.9f

private val Blue = Color(0xFF1E9BFF)
private val Dim = Color(0xFF1C5C96)
private val Grey = Color(0xFF8E8E93)

// The reference shows three steps done and the fourth well under way.
private const val REST = 0.754f

// autoPlay loop (seconds): steps per second while working.
private const val START = 0.4f
private const val RATE = 0.8f
private const val RUN = REST * STEPS / RATE
private const val HOLD = 3.6f
private const val RESET = 0.7f
private const val LOOP = START + RUN + HOLD + RESET

// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
private const val REST_T = START + RUN + 1.5f

private const val PI_F = PI.toFloat()

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun stepsText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = (-0.1f).sp,
    fontFeatureSettings = "tnum",
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = stepsText(15.2f, 20f, FontWeight.Bold, Color.White)
private val StatusStyle = stepsText(16.4f, 21f, FontWeight.Normal, Grey)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

private fun easeOutCubic(t: Float) = 1f - (1f - clamp01(t)).pow(3)

/** Overall progress at [t] seconds into the autoPlay loop. */
private fun loopValue(t: Float) = when {
    t < START -> 0f
    t < START + RUN -> (t - START) * RATE / STEPS
    t < START + RUN + HOLD -> REST
    else -> REST * (1f - easeInOutCubic((t - START - RUN - HOLD) / RESET))
}

/** How far past its completion step [index] is, in steps: everything about a dot follows from it. */
private fun pastStep(p: Float, index: Int) = clamp01(p) * STEPS - (index + 1)

/** The step being worked on at progress [p] (0…5; 5 when everything is done). */
private fun activeStep(p: Float) = min(STEPS, floor(clamp01(p) * STEPS + 0.0001f).toInt())

// Hardware canvases only honour mask filters from API 28: soft layers are skipped below that.
private val CanBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

private fun softPaint(sigma: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    if (CanBlur) maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
}

private fun Paint.tinted(color: Color, alpha: Float = 1f): Paint {
    this.color = color.copy(alpha = (color.alpha * alpha).coerceIn(0f, 1f)).toArgb()
    return this
}

private class StepsPaints {
    val shadow = SoftRoundRect(rgba(6, 16, 40, 0.5f), sigma = 16f)
    val pulse = softPaint(1.6f)
    val check = PathParser().parsePathString("M-5.4 -0.6L-1.2 4.8L5.8 -5").toPath()
    val measure = PathMeasure().apply { setPath(check, false) }
    val drawnCheck = Path()

    // Glyphs of the three tools, drawn around the centre of their disc.
    val envelope = PathParser().parsePathString("M-5.6 -3.8L5.6 -3.8L5.6 3.8L-5.6 3.8Z").toPath()
    val flap = PathParser().parsePathString("M-5.6 -3.6L0 1L5.6 -3.6").toPath()
    val meridian = PathParser()
        .parsePathString("M0 -7.6C-4.6 -3.6 -4.6 3.6 0 7.6C4.6 3.6 4.6 -3.6 0 -7.6ZM-7.6 0L7.6 0").toPath()
    val page = PathParser().parsePathString("M-3.9 -5.2L1.6 -5.2L3.9 -2.9L3.9 5.2L-3.9 5.2Z").toPath()
    val lines = PathParser().parsePathString("M-1.8 -0.6L1.8 -0.6M-1.8 2.2L1.8 2.2").toPath()
}

/**
 * Agent live activity: a title with the tools in use, five steps joined by dashes, and what the
 * agent is doing right now. Each step's dash fills with a pulse of light running along it, the
 * step being worked on carries a spinning arc, a finished step pops in with a ripple and draws its
 * check, the status line and the count slide over to the next step, and the dots after the status
 * keep typing.
 *
 * @param progress Controlled progress through the steps, 0…1: each step is a fifth.
 * @param autoPlay Works through the steps up to the state shown in the reference, holds, and starts over.
 * @param statuses What the agent is doing during each of the five steps (the dots after it are animated).
 */
@Composable
fun RecruiterSteps(
    progress: Float? = null,
    autoPlay: Boolean = false,
    title: String = "Recruiter",
    statuses: List<String> = listOf("Reading inbox", "Searching profiles", "Screening candidates", "Writing summary", "Sending shortlist"),
    doneStatus: String = "Shortlist sent",
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val controlled = progress != null
    val target = (progress ?: REST).coerceIn(0f, 1f)
    val auto = autoPlay && !controlled && !reducedMotion
    val looping by rememberUpdatedState(auto)
    val ambient = if (reducedMotion) 0f else 1f

    // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
    val time = remember { mutableFloatStateOf(0f) }
    val clock = remember { mutableFloatStateOf(if (auto) 0f else REST_T) }
    var playing by remember { mutableStateOf(false) }
    val held = remember { Animatable(target) }

    LaunchedEffect(auto) {
        playing = auto
        clock.floatValue = if (auto) 0f else REST_T
    }

    LaunchedEffect(controlled, target) {
        if (!controlled || held.targetValue == target) return@LaunchedEffect
        held.animateTo(target, tween(if (reducedMotion) 200 else 1600, easing = EaseOutStrong))
    }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = min((now - last) / 1e9f, 0.064f)
            last = now
            if (playing) {
                var t = clock.floatValue + dt
                if (looping) {
                    if (t >= LOOP) t -= LOOP
                } else if (t >= REST_T) {
                    // A tapped, one-off run stops at the resting frame.
                    t = REST_T
                    playing = false
                }
                clock.floatValue = t
            }
            // Written last: reading it is what redraws the panel each frame.
            time.floatValue += dt
        }
    }

    // Read inside draw / layer lambdas so a frame only redraws, it does not recompose.
    val value = { if (controlled) held.value else loopValue(clock.floatValue) }
    // The step being worked on, and the one before it for the hand-over of the status line.
    val step by remember(controlled) { derivedStateOf { activeStep(value()) } }
    var shownStep by remember { mutableIntStateOf(step) }
    var before by remember { mutableIntStateOf(step) }
    // When the status line last changed, on the `time` axis.
    val swappedAt = remember { mutableFloatStateOf(-10f) }
    LaunchedEffect(step) {
        if (step == shownStep) return@LaunchedEffect
        before = shownStep
        shownStep = step
        swappedAt.floatValue = time.floatValue
    }

    fun label(index: Int) = if (index >= STEPS || index >= statuses.size) doneStatus else statuses[index]
    val incoming = { if (ambient > 0f) easeOutCubic((time.floatValue - swappedAt.floatValue) / 0.32f) else 1f }
    val outgoing = { if (ambient > 0f) easeOutCubic((time.floatValue - swappedAt.floatValue) / 0.24f) else 1f }

    val paints = remember { StepsPaints() }

    Box(
        modifier = modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .pointerInput(controlled, reducedMotion) {
                detectTapGestures {
                    if (!controlled && !reducedMotion) {
                        clock.floatValue = 0f
                        playing = true
                    }
                }
            }
            .clearAndSetSemantics {
                val done = activeStep(target)
                contentDescription = title
                stateDescription = "${label(done)}, $done of $STEPS"
                progressBarRangeInfo = ProgressBarRangeInfo(done.toFloat(), 0f..STEPS.toFloat(), STEPS - 1)
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    val t = time.floatValue
                    val p = clamp01(value())

                    drawIntoCanvas { paints.shadow.draw(it.nativeCanvas, x + 10, y + 16, WIDTH - 20, HEIGHT - 8, RADIUS) }
                    drawRoundRect(Color.Black, Offset(x, y), Size(WIDTH, HEIGHT), CornerRadius(RADIUS))

                    for (index in 0 until STEPS) {
                        val cx = x + STEP_X + STEP_GAP * index
                        val cy = y + STEP_Y
                        val centre = Offset(cx, cy)
                        val past = pastStep(p, index)
                        val filled = clamp01(past / 0.1f)

                        if (index < STEPS - 1) {
                            // The dash to the next dot fills, with a pulse of light running along it.
                            val from = cx + DASH_FROM
                            val span = DASH_TO - DASH_FROM
                            drawLine(Dim, Offset(from, cy), Offset(from + span, cy), DASH_W, StrokeCap.Round)
                            val fill = clamp01(past)
                            if (fill > 0.001f) {
                                drawLine(
                                    Blue, Offset(from, cy), Offset(from + span * fill, cy), DASH_W, StrokeCap.Round,
                                    alpha = clamp01(past * 40f),
                                )
                            }
                            if (ambient > 0f && past > 0.04f && past < 1f) {
                                val phase = (t * 1.3f) % 1f
                                drawIntoCanvas {
                                    it.nativeCanvas.drawCircle(
                                        from + span * fill * phase, cy, 2.6f,
                                        paints.pulse.tinted(Color(0xFFD9EFFF), 0.9f * sin(PI_F * phase)),
                                    )
                                }
                            }
                        }

                        if (filled < 1f) drawCircle(Dim, RING_R, centre, alpha = 1f - filled, style = Stroke(RING_W))
                        // The step being worked on: a short arc runs around its ring.
                        if (ambient > 0f && activeStep(p) == index && p > 0.0005f) {
                            drawArc(
                                color = Blue,
                                startAngle = Math.toDegrees((t * 5.2f).toDouble()).toFloat() - 90f,
                                sweepAngle = 90f,
                                useCenter = false,
                                topLeft = Offset(cx - RING_R, cy - RING_R),
                                size = Size(RING_R * 2, RING_R * 2),
                                alpha = clamp01((p * STEPS - index) * 8f) * (1f - filled),
                                style = Stroke(RING_W, cap = StrokeCap.Round),
                            )
                        }
                        // A finished step lands with a springy overshoot, sends out a ripple and draws its check.
                        val ripple = past / 0.5f
                        if (ambient > 0f && ripple > 0f && ripple < 1f) {
                            drawCircle(Blue, DOT_R + 10f * easeOutCubic(ripple), centre, alpha = 0.7f * (1f - ripple), style = Stroke(1.5f))
                        }
                        if (filled > 0f) {
                            val u = clamp01(past / 0.36f)
                            drawCircle(
                                Blue, DOT_R * (0.7f + 0.3f * filled + ambient * 0.24f * sin(PI_F * u) * (1f - u)), centre, alpha = filled,
                            )
                            val drawn = if (ambient > 0f) easeOutCubic((past - 0.06f) / 0.26f) else filled
                            if (drawn > 0.001f) {
                                paints.drawnCheck.rewind()
                                paints.measure.getSegment(0f, paints.measure.length * drawn, paints.drawnCheck, true)
                                translate(cx, cy) {
                                    drawPath(
                                        paints.drawnCheck, Color(0xFF04070C),
                                        style = Stroke(2.1f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                    )
                                }
                            }
                        }
                    }

                    // The tools the agent uses, back to front so the first overlaps the others: they bob
                    // one after another.
                    for (index in 2 downTo 0) {
                        val lift = ambient * 2.2f * max(0f, sin(t * 2.4f - index * 1.1f)).pow(3)
                        translate(x + ICON_X[index], y + ICON_Y - lift) {
                            drawCircle(Color.Black, ICON_R + 1.5f, Offset.Zero)
                            when (index) {
                                0 -> {
                                    drawCircle(
                                        Brush.verticalGradient(listOf(Color(0xFF45C6FF), Color(0xFF1477F5)), -ICON_R, ICON_R),
                                        ICON_R, Offset.Zero,
                                    )
                                    drawPath(paints.envelope, Color.White)
                                    drawPath(paints.flap, Color(0xFF2A8CF2), style = Stroke(1.1f, join = StrokeJoin.Round))
                                }
                                1 -> {
                                    drawCircle(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF7FE0FF), Color(0xFF0B5FD6)),
                                            start = Offset(-ICON_R, -ICON_R),
                                            end = Offset(ICON_R, ICON_R),
                                        ),
                                        ICON_R, Offset.Zero,
                                    )
                                    drawCircle(rgba(255, 255, 255, 0.85f), 7.6f, Offset.Zero, style = Stroke(1f))
                                    drawPath(paints.meridian, rgba(255, 255, 255, 0.85f), style = Stroke(1f))
                                }
                                else -> {
                                    drawCircle(Color(0xFFF4F4F2), ICON_R, Offset.Zero)
                                    drawPath(paints.page, Color(0xFF16171A), style = Stroke(1.3f, join = StrokeJoin.Round))
                                    drawPath(paints.lines, Color(0xFF16171A), style = Stroke(1.1f, cap = StrokeCap.Round))
                                }
                            }
                        }
                    }
                }
            }
        }

        BasicText(title, Modifier.offset(24.dp, 22.3.dp), TitleStyle, maxLines = 1)

        // The status line hands over to the next step: the old one leaves upwards, the new one rises in.
        BasicText(
            text = label(before) + if (before < STEPS) "..." else "",
            style = StatusStyle,
            maxLines = 1,
            modifier = Modifier.offset(24.dp, 96.6.dp).graphicsLayer {
                val u = outgoing()
                alpha = 1f - u
                translationY = (-7f * u).dp.toPx()
            },
        )
        Row(
            Modifier.offset(24.dp, 96.6.dp).graphicsLayer {
                val u = incoming()
                alpha = u
                translationY = (7f * (1f - u)).dp.toPx()
            },
        ) {
            BasicText(label(shownStep), style = StatusStyle, maxLines = 1)
            if (shownStep < STEPS) {
                // The dots light one after another while the agent works.
                for (index in 0 until 3) {
                    BasicText(
                        ".",
                        Modifier.graphicsLayer { alpha = 1f - ambient * 0.72f * (0.5f - 0.5f * sin(time.floatValue * 5f - index * 1.1f)) },
                        StatusStyle,
                    )
                }
            }
        }
        BasicText(
            text = "$shownStep of $STEPS",
            style = StatusStyle.copy(textAlign = TextAlign.End),
            maxLines = 1,
            modifier = Modifier.offset((WIDTH - 24.4f - 80f).dp, 96.6.dp).width(80.dp).graphicsLayer {
                val u = incoming()
                alpha = u
                translationY = (7f * (1f - u)).dp.toPx()
            },
        )
    }
}
