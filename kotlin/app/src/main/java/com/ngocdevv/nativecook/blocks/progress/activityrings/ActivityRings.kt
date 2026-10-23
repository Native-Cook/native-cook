package com.ngocdevv.nativecook.blocks.progress.activityrings

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val WIDTH = 344f
private const val HEIGHT = 148f
private const val RADIUS = 34f

// The canvas overflows the card by this much on every side to fit its shadow.
private val BLEED = 40.dp

// Centre of the rings and of the white disc in the middle of them.
private const val CX = 306.8f
private const val CY = 111.6f
private const val DISC_R = 35.9f
private const val RING_W = 19.6f
private const val BADGE_R = 8.5f

/**
 * One ring: radius, degrees swept at full value (clockwise from 6 o'clock), colours, and where in
 * the overall progress it starts drawing.
 */
private class RingSpec(
    val r: Float,
    val sweep: Float,
    val color: Color,
    val badge: Color,
    val ink: Color,
    val delay: Float,
)

// Outside in.
private val Rings = listOf(
    RingSpec(91.6f, 119.7f, Color(0xFF48A8E2), Color(0xFF80D2F8), Color(0xFF0B3A66), 0f),
    RingSpec(68.2f, 183.3f, Color(0xFF5EE24A), Color(0xFF92F37F), Color(0xFF0D4A0A), 0.15f),
    RingSpec(44.5f, 153.1f, Color(0xFFE84A66), Color(0xFFF27C90), Color(0xFF5A0A16), 0.3f),
)

// Share of the overall progress one ring takes to draw.
private const val RING_SPAN = 0.7f

// The reference shows every ring at its full value; the autoPlay loop built around it (seconds).
private const val REST = 1f
private const val START = 0.4f
private const val DRAW = 2.8f
private const val HOLD = 3.8f
private const val RESET = 0.7f
private const val LOOP = START + DRAW + HOLD + RESET

// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
private const val REST_T = START + DRAW + 1.6f

private const val DEG = (PI / 180.0).toFloat()

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun ringsText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = ringsText(19.6f, 24f, FontWeight.Bold, Color.White, -0.2f)
private val FigureStyle = ringsText(16f, 22f, FontWeight.Medium, Color.White)
private val ChangeStyle = ringsText(10.6f, 16f, FontWeight.Medium, Color(0xFF4CD964))
private val CaptionStyle = ringsText(11.8f, 16f, FontWeight.Medium, Color(0xFF707070), 0.2f)
private val BadgeStyle = ringsText(9.6f, 16f, FontWeight.ExtraBold, Color.Black).copy(fontFeatureSettings = "tnum")
private val ScoreStyle = ringsText(22f, 27f, FontWeight.Bold, Color(0xFF0A0A0A)).copy(fontFeatureSettings = "tnum")
private val ScoreLabelStyle = ringsText(13.2f, 18f, FontWeight.SemiBold, Color(0xFF0A0A0A))

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

private fun easeOutCubic(t: Float) = 1f - (1f - clamp01(t)).pow(3)

/** Overall progress at [t] seconds into the autoPlay loop. */
private fun loopValue(t: Float) = when {
    t < START -> 0f
    t < START + DRAW -> (t - START) / DRAW
    t < START + DRAW + HOLD -> 1f
    else -> 1f - easeInOutCubic((t - START - DRAW - HOLD) / RESET)
}

/** 0…1: how far ring [index] is drawn at overall progress [p]. The rings start one after another. */
private fun ringAt(p: Float, index: Int) = easeOutCubic((clamp01(p) - Rings[index].delay) / RING_SPAN)

/** One heartbeat, 0…1, at [phase] (0…1) of the beat: a strong thump and a softer one after it. */
private fun heartbeat(phase: Float) = when {
    phase < 0.16f -> sin(PI.toFloat() * phase / 0.16f)
    phase > 0.24f && phase < 0.4f -> 0.55f * sin(PI.toFloat() * (phase - 0.24f) / 0.16f)
    else -> 0f
}

/** Scale of a badge after its ring lands: a quick overshoot. */
private fun pop(since: Float, ambient: Float): Float {
    val u = clamp01(since / 0.5f)
    return 1f + ambient * 0.55f * sin(PI.toFloat() * u) * (1f - u)
}

/** Places the element by its leading edge and vertical centre inside the card. */
private fun Modifier.leadingAt(x: Dp, centerY: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToPx(), centerY.roundToPx() - placeable.height / 2)
    }
}

/** Places the element by its centre inside the card. */
private fun Modifier.centerAt(x: Dp, y: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToPx() - placeable.width / 2, y.roundToPx() - placeable.height / 2)
    }
}

// Hardware canvases only honour mask filters from API 28: soft layers are skipped below that.
private val CanBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

private fun softPaint(sigma: Float, stroke: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    if (stroke > 0f) {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    if (CanBlur) maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
}

private fun Paint.tinted(color: Color, alpha: Float = 1f): Paint {
    this.color = color.copy(alpha = (color.alpha * alpha).coerceIn(0f, 1f)).toArgb()
    return this
}

private class RingsPaints {
    val shadow = SoftRoundRect(rgba(0, 0, 0, 0.5f), sigma = 16f)
    val sheen = softPaint(3f, stroke = RING_W - 7f)
    val lead = softPaint(4f)
    val discShadow = softPaint(5f)
    val moonGlow = softPaint(3.5f, stroke = 3f)
    val heartGlow = softPaint(3.5f, stroke = 3.4f)
    val arcBounds = RectF()

    val card = Path().apply { addRoundRect(RoundRect(Rect(0f, 0f, WIDTH, HEIGHT), CornerRadius(RADIUS))) }
    val moon = PathParser().parsePathString(
        "M3.6 -8.3C-2.6 -10 -9.2 -5.4 -9.2 0.6C-9.2 5.8 -5 9.4 0 9.4C4.4 9.4 7.8 6.8 8.9 3C5.8 3.9 2.6 2.2 1.7 -1C1 -3.6 1.8 -6.6 3.6 -8.3Z",
    ).toPath()
    val heart = PathParser().parsePathString(
        "M0 7.6C-5.4 3.6 -8.6 0.8 -8.6 -2.8C-8.6 -5.6 -6.6 -7.4 -4.2 -7.4C-2.4 -7.4 -0.9 -6.4 0 -4.9C0.9 -6.4 2.4 -7.4 4.2 -7.4C6.6 -7.4 8.6 -5.6 8.6 -2.8C8.6 0.8 5.4 3.6 0 7.6Z",
    ).toPath()
}

/** Part of a ring, in degrees clockwise from 6 o'clock. */
private fun DrawScope.ringArc(color: Color, r: Float, from: Float, to: Float, width: Float) = drawArc(
    color = color,
    startAngle = 90f + from,
    sweepAngle = to - from,
    useCenter = false,
    topLeft = Offset(CX - r, CY - r),
    size = Size(r * 2, r * 2),
    style = Stroke(width, cap = StrokeCap.Round),
)

/**
 * Black "today" widget: sleep and heart-rate rows on the left, three thick concentric rings on the
 * right, each ending in a badge with its number, around a white disc with the day's score. The
 * rings draw one after another with a bright head, their badges ride along counting up and pop
 * when they land, a sheen keeps running along the finished rings, and the heart beats at its rate.
 *
 * @param progress Controlled share of the day's figures reached, 0…1: at 1 the rings show their full values.
 * @param autoPlay Draws the rings one after another up to the values shown in the reference, holds, and starts over.
 * @param sleepChange Change against yesterday, shown in green with an up arrow.
 * @param bpm Beats per minute the heart glyph pulses at.
 * @param values The numbers the blue, green and red rings count up to.
 */
@Composable
fun ActivityRings(
    progress: Float? = null,
    autoPlay: Boolean = false,
    title: String = "Today · 21 Feb",
    sleep: String = "7h 2m",
    sleepChange: String = "12%",
    sleepLabel: String = "Sleep time",
    heartRate: String = "56 bpm",
    heartLabel: String = "Avg. heart rate",
    bpm: Float = 56f,
    values: List<Int> = listOf(31, 84, 53),
    score: Int = 16,
    scoreLabel: String = "PAI",
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val controlled = progress != null
    val target = (progress ?: REST).coerceIn(0f, 1f)
    val auto = autoPlay && !controlled && !reducedMotion
    val looping by rememberUpdatedState(auto)
    val isControlled by rememberUpdatedState(controlled)

    // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
    val time = remember { mutableFloatStateOf(0f) }
    val clock = remember { mutableFloatStateOf(if (auto) 0f else REST_T) }
    var playing by remember { mutableStateOf(false) }
    val held = remember { Animatable(target) }
    // When each ring last reached its full value, on the `time` axis.
    val landed = remember { FloatArray(3) { -10f } }

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
        var before = target
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
            val current = if (isControlled) held.value else loopValue(clock.floatValue)
            // A ring reaching its full value sets off its badge's pop.
            for (index in 0 until 3) {
                if (ringAt(before, index) < 0.995f && ringAt(current, index) >= 0.995f) landed[index] = time.floatValue + dt
            }
            before = current
            // Written last: reading it is what redraws the card each frame.
            time.floatValue += dt
        }
    }

    // Read inside draw / layer lambdas so a frame only redraws, it does not recompose.
    val value = { if (controlled) held.value else loopValue(clock.floatValue) }
    // The score follows the rings: the average of how far they are drawn.
    val scoreShown by remember(controlled, score) {
        derivedStateOf {
            val p = if (controlled) held.value else loopValue(clock.floatValue)
            (score * (ringAt(p, 0) + ringAt(p, 1) + ringAt(p, 2)) / 3f).roundToInt()
        }
    }

    val ambient = if (reducedMotion) 0f else 1f
    val paints = remember { RingsPaints() }

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
                contentDescription = title
                stateDescription = "$scoreShown $scoreLabel, $sleepLabel $sleep, $heartLabel $heartRate"
                progressBarRangeInfo = ProgressBarRangeInfo(scoreShown.toFloat(), 0f..score.toFloat())
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    val t = time.floatValue
                    val p = value()

                    drawIntoCanvas { paints.shadow.draw(it.nativeCanvas, x + 12, y + 20, WIDTH - 24, HEIGHT - 12, RADIUS) }
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF1B1B1B), Color(0xFF0E0E0E)),
                            start = Offset(x, y),
                            end = Offset(x + WIDTH * 0.3f, y + HEIGHT),
                        ),
                        topLeft = Offset(x, y),
                        size = Size(WIDTH, HEIGHT),
                        cornerRadius = CornerRadius(RADIUS),
                    )

                    translate(x, y) {
                        clipPath(paints.card) {
                            val centre = Offset(CX, CY)
                            // Faint outer tracks behind the rings.
                            drawCircle(rgba(255, 255, 255, 0.018f), 139f, centre, style = Stroke(RING_W))
                            drawCircle(rgba(255, 255, 255, 0.03f), 115.5f, centre, style = Stroke(RING_W))

                            for (index in 0 until 3) {
                                val ring = Rings[index]
                                val drawn = ringAt(p, index)
                                val angle = (90f + ring.sweep * drawn) * DEG
                                val head = Offset(CX + ring.r * cos(angle), CY + ring.r * sin(angle))
                                val since = if (ambient > 0f) t - landed[index] else 10f
                                drawCircle(Color(0xFF0A0A0A), ring.r, centre, style = Stroke(RING_W + 7f))
                                ringArc(ring.color, ring.r, 0f, ring.sweep * drawn, RING_W)
                                // A sheen runs along the finished ring every few seconds.
                                val sheenAt = ((t + index * 0.5f) % 3.4f) / 1.3f
                                if (CanBlur && ambient > 0f && sheenAt < 1f && drawn >= 0.999f) {
                                    val end = ring.sweep * drawn * sheenAt
                                    val from = max(0f, end - 16f)
                                    paints.arcBounds.set(CX - ring.r, CY - ring.r, CX + ring.r, CY + ring.r)
                                    drawIntoCanvas {
                                        it.nativeCanvas.drawArc(
                                            paints.arcBounds, 90f + from, end - from, false,
                                            paints.sheen.tinted(Color.White, 0.34f * sin(PI.toFloat() * sheenAt)),
                                        )
                                    }
                                }
                                // The head of the ring is bright while it is still drawing.
                                val lead = clamp01(drawn * 10f) * clamp01((1f - drawn) * 4f) * 0.75f
                                if (CanBlur && lead > 0.01f) {
                                    drawIntoCanvas {
                                        it.nativeCanvas.drawCircle(head.x, head.y, RING_W / 2, paints.lead.tinted(Color.White, lead))
                                    }
                                }
                                drawCircle(ring.badge, BADGE_R * pop(since, ambient), head)
                            }

                            // White disc in the middle; when the last ring lands a ripple leaves it.
                            val ripple = (if (ambient > 0f) t - landed[2] else 10f) / 0.9f
                            if (ripple > 0f && ripple < 1f) {
                                drawCircle(
                                    Color.White, DISC_R + 16f * easeOutCubic(ripple), centre,
                                    alpha = 0.5f * (1f - ripple), style = Stroke(1.6f),
                                )
                            }
                            if (CanBlur) {
                                drawIntoCanvas {
                                    it.nativeCanvas.drawCircle(CX, CY + 1f, DISC_R + 2f, paints.discShadow.tinted(Color.Black, 0.5f))
                                }
                            }
                            drawCircle(
                                brush = Brush.radialGradient(
                                    0f to Color.White,
                                    0.82f to Color.White,
                                    1f to Color(0xFFE6E6E6),
                                    center = centre,
                                    radius = DISC_R,
                                ),
                                radius = DISC_R,
                                center = centre,
                            )
                        }
                    }

                    // Moon and heart glyphs with their glow: the moon's breathes slowly, the heart beats
                    // at the rate it reports.
                    translate(x + 29.6f, y + 71.4f) {
                        if (CanBlur) {
                            drawIntoCanvas {
                                it.nativeCanvas.drawPath(
                                    paints.moon.asAndroidPath(),
                                    paints.moonGlow.tinted(Color(0xFF6F8CFF), 0.6f + ambient * 0.3f * sin(t * 1.2f)),
                                )
                            }
                        }
                        drawPath(paints.moon, Color(0xFF3A478C))
                        drawPath(paints.moon, Color(0xFF7592FF), style = Stroke(1.6f, join = StrokeJoin.Round))
                    }
                    val beat = ambient * heartbeat((t * bpm / 60f) % 1f)
                    withTransform({
                        translate(x + 29.6f, y + 115.4f)
                        scale(1f + 0.16f * beat, 1f + 0.16f * beat, pivot = Offset.Zero)
                    }) {
                        if (CanBlur) {
                            drawIntoCanvas {
                                it.nativeCanvas.drawPath(
                                    paints.heart.asAndroidPath(),
                                    paints.heartGlow.tinted(Color(0xFFFF1F1F), 0.55f + 0.45f * beat),
                                )
                            }
                        }
                        drawPath(paints.heart, Color(0xFF7A0C0C))
                        drawPath(paints.heart, Color(0xFFFF2222), style = Stroke(1.8f, join = StrokeJoin.Round))
                    }
                }
            }
        }

        // The labels sit in their own layer, clipped to the card: the badge numbers start below its edge.
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(RADIUS.dp))) {
            BasicText(title, Modifier.leadingAt(18.8.dp, 26.dp), TitleStyle, maxLines = 1)
            Row(Modifier.leadingAt(48.7.dp, 64.5.dp), verticalAlignment = Alignment.Top) {
                BasicText(sleep, style = FigureStyle, maxLines = 1)
                BasicText("$sleepChange↑", Modifier.padding(start = 4.dp, top = 0.6.dp), ChangeStyle, maxLines = 1)
            }
            BasicText(sleepLabel, Modifier.leadingAt(48.7.dp, 81.6.dp), CaptionStyle, maxLines = 1)
            BasicText(heartRate, Modifier.leadingAt(48.7.dp, 108.dp), FigureStyle, maxLines = 1)
            BasicText(heartLabel, Modifier.leadingAt(48.7.dp, 125.4.dp), CaptionStyle, maxLines = 1)

            for (index in 0 until 3) {
                val ring = Rings[index]
                // The number in a ring's badge: rides the head of the ring and counts up with it.
                val shown by remember(controlled, values) {
                    derivedStateOf {
                        (values[index] * ringAt(if (controlled) held.value else loopValue(clock.floatValue), index)).roundToInt()
                    }
                }
                BasicText(
                    text = shown.toString(),
                    style = BadgeStyle.copy(color = ring.ink),
                    maxLines = 1,
                    modifier = Modifier
                        .centerAt(0.dp, 0.dp)
                        .graphicsLayer {
                            val drawn = ringAt(value(), index)
                            val angle = (90f + ring.sweep * drawn) * DEG
                            translationX = (CX + ring.r * cos(angle)).dp.toPx()
                            translationY = (CY + ring.r * sin(angle)).dp.toPx()
                            alpha = clamp01(drawn * 8f)
                            val scale = pop(if (ambient > 0f) time.floatValue - landed[index] else 10f, ambient)
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
            BasicText(scoreShown.toString(), Modifier.centerAt((CX + 0.6f).dp, 103.9.dp), ScoreStyle, maxLines = 1)
            BasicText(scoreLabel, Modifier.centerAt((CX + 0.6f).dp, 125.8.dp), ScoreLabelStyle, maxLines = 1)
        }
    }
}
