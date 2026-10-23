package com.ngocdevv.nativecook.blocks.progress.habitring

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.TAU
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
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

/** One stat tile: the number it counts up to, an optional unit after it, and its caption. */
data class HabitStat(val value: Int, val label: String, val suffix: String = "")

private const val WIDTH = 340f
private const val CARD_H = 285f
private const val CARD_R = 28.5f
private const val TILE_Y = 308.7f
private const val TILE_W = 106.5f
private const val TILE_H = 68f
private const val TILE_STEP = 116.75f
private const val TILE_R = 15f
private const val HEIGHT = TILE_Y + TILE_H

// The canvas overflows the component by this much on every side to fit the soft shadows.
private val BLEED = 30.dp

private const val CX = 170f
private const val CY = 115.7f
private const val RING_R = 81.4f
private const val RING_W = 13.7f
private val SMALL_X = floatArrayOf(44.7f, 294.7f)
private const val SMALL_Y = 107.7f
private const val SMALL_R = 22.5f
private const val SMALL_W = 4.8f

private val Orange = Color(0xFFF0742D)
private val Ink = Color(0xFFF0F0F2)

// autoPlay loop (seconds).
private const val START = 0.5f
private const val DRAW = 2.4f
private const val HOLD = 4.6f
private const val RESET = 0.7f
private const val LOOP = START + DRAW + HOLD + RESET

// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
private const val REST_T = START + DRAW + 1.8f

private const val PI_F = PI.toFloat()

/** A spark thrown off the head of the big ring: which side it drifts to (−1…1), its phase and size. */
private class HabitEmber(val side: Float, val phase: Float, val r: Float)

private val Embers = listOf(
    HabitEmber(-0.9f, 0.0f, 1.9f), HabitEmber(0.5f, 0.14f, 1.5f),
    HabitEmber(-0.2f, 0.29f, 2.1f), HabitEmber(0.95f, 0.43f, 1.4f),
    HabitEmber(-0.6f, 0.57f, 1.7f), HabitEmber(0.15f, 0.71f, 2.0f),
    HabitEmber(0.7f, 0.86f, 1.5f),
)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun habitText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    fontFeatureSettings = "tnum",
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun habitMono(size: Float, color: Color, spacing: Float) =
    habitText(size, 12f, FontWeight.Normal, color, spacing).copy(fontFamily = FontFamily.Monospace)

private val PercentStyle = habitText(58f, 64f, FontWeight.ExtraBold, Ink, -1.5f)
private val SignStyle = habitText(24f, 28f, FontWeight.ExtraBold, Ink)
private val LabelStyle = habitMono(9f, Color(0xFF9A9DA4), 3.1f)
private val SmallValueStyle = habitText(11.8f, 16f, FontWeight.Bold, Ink)
private val SmallLabelStyle = habitMono(8.2f, Color(0xFF8B8F97), 2.2f)
private val CaptionStyle = habitText(12.6f, 18.5f, FontWeight.Normal, Color(0xFF9B9EA6), -0.2f).copy(textAlign = TextAlign.Center)
private val StatValueStyle = habitText(20.8f, 26f, FontWeight.ExtraBold, Ink, -0.3f)
private val StatLabelStyle = habitMono(8.2f, Color(0xFF8B8F97), 1.3f)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

private fun easeOutCubic(t: Float) = 1f - (1f - clamp01(t)).pow(3)

/** Constancy at [t] seconds into the autoPlay loop, where [rest] is the reference value. */
private fun loopValue(t: Float, rest: Float) = when {
    t < START -> 0f
    t < START + DRAW -> rest * easeOutCubic((t - START) / DRAW)
    t < START + DRAW + HOLD -> rest
    else -> rest * (1f - easeInOutCubic((t - START - DRAW - HOLD) / RESET))
}

/** 0…1: how far small ring [index] is drawn when the big ring is at [share] of [rest]. The second starts later. */
private fun smallAt(share: Float, rest: Float, index: Int) =
    easeOutCubic(clamp01(share / max(rest, 0.001f)) * 1.25f - 0.25f * index)

/** 0…1: how far stat tile [index] has risen into place at [t] seconds into the loop. */
private fun tileAt(t: Float, index: Int): Float {
    val out = START + DRAW + HOLD
    if (t >= out) return 1f - clamp01((t - out) / (RESET * 0.6f))
    return easeOutCubic((t - 0.15f - 0.14f * index) / 0.55f)
}

/** Scale after the ring lands: a quick overshoot. */
private fun pop(since: Float, amount: Float): Float {
    val u = clamp01(since / 0.55f)
    return 1f + amount * sin(PI_F * u) * (1f - u)
}

/** Places the element by its centre inside the component. */
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
    }
    if (CanBlur) maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
}

private fun Paint.tinted(color: Color, alpha: Float = 1f): Paint {
    this.color = color.copy(alpha = (color.alpha * alpha).coerceIn(0f, 1f)).toArgb()
    return this
}

private class HabitPaints {
    val cardShadow = SoftRoundRect(rgba(0, 0, 0, 0.38f), sigma = 13f)
    val cardLight = SoftRoundRect(rgba(255, 255, 255, 0.03f), sigma = 9f)
    val tileShadow = SoftRoundRect(rgba(0, 0, 0, 0.34f), sigma = 8f)
    val glow = softPaint(9f, stroke = RING_W + 4f)
    val hot = softPaint(5f)
    val bounds = RectF()
}

/** Per-frame state: how fast the ring is moving and when it last came to rest. */
private class HabitPace {
    /** 0…1, smoothed so the sparks fade out instead of cutting off. */
    var moving = 0f

    /** On the `time` axis. */
    var landed = -10f
}

/** Part of a ring, clockwise from 12 o'clock. */
private fun DrawScope.ring(color: Color, cx: Float, cy: Float, r: Float, share: Float, width: Float, alpha: Float = 1f) = drawArc(
    color = color,
    startAngle = -90f,
    sweepAngle = 360f * share,
    useCenter = false,
    topLeft = Offset(cx - r, cy - r),
    size = Size(r * 2, r * 2),
    alpha = alpha,
    style = Stroke(width, cap = StrokeCap.Round),
)

/** A raised plate: slanted gradient and a hairline border (its shadow is drawn by the caller). */
private fun DrawScope.plate(x: Float, y: Float, w: Float, h: Float, r: Float, top: Color, bottom: Color, slant: Float, alpha: Float = 1f) {
    drawRoundRect(
        brush = Brush.linearGradient(listOf(top, bottom), start = Offset(x, y), end = Offset(x + w * slant, y + h)),
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = CornerRadius(r),
        alpha = alpha,
    )
    drawRoundRect(
        color = rgba(255, 255, 255, 0.075f),
        topLeft = Offset(x + 0.5f, y + 0.5f),
        size = Size(w - 1f, h - 1f),
        cornerRadius = CornerRadius(r - 0.5f),
        alpha = alpha,
        style = Stroke(1f),
    )
}

/**
 * Habit tracker summary: a big constancy ring between two small ones on a soft raised card, with
 * three stat tiles under it. The big ring draws behind a white-hot head that throws sparks, its
 * glow breathes and a ripple keeps leaving the head once it has landed; the percentage counts up
 * and settles with a small bounce, the small rings follow, and the tiles rise in one by one.
 *
 * @param progress Controlled constancy, 0…1: the big ring and its percentage.
 * @param autoPlay Draws the rings up to the values shown in the reference, holds, and starts over.
 * @param constancy Constancy at the resting frame, 0…1.
 * @param week Value of the left small ring, out of 100.
 * @param day Value of the right small ring, out of 100.
 */
@Composable
fun HabitRing(
    progress: Float? = null,
    autoPlay: Boolean = false,
    constancy: Float = 0.82f,
    label: String = "CONSTANCE",
    week: Int = 90,
    weekLabel: String = "SEMAINE",
    day: Int = 53,
    dayLabel: String = "JOUR",
    caption: String = "Average of 18 habits over 18 days\nelapsed.",
    stats: List<HabitStat> = listOf(
        HabitStat(202, "CHECK-INS"),
        HabitStat(18, "BEST STREAK", suffix = " j"),
        HabitStat(14, "PERFECT DAYS"),
    ),
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val controlled = progress != null
    val rest = constancy
    val target = (progress ?: rest).coerceIn(0f, 1f)
    val auto = autoPlay && !controlled && !reducedMotion
    val looping by rememberUpdatedState(auto)
    val isControlled by rememberUpdatedState(controlled)
    val currentRest by rememberUpdatedState(rest)

    // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
    val time = remember { mutableFloatStateOf(0f) }
    val clock = remember { mutableFloatStateOf(if (auto) 0f else REST_T) }
    var playing by remember { mutableStateOf(false) }
    val held = remember { Animatable(target) }
    val pace = remember { HabitPace() }

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
        var before = if (isControlled) held.value else loopValue(clock.floatValue, currentRest)
        while (true) {
            val frame = withFrameNanos { it }
            val dt = min((frame - last) / 1e9f, 0.064f)
            last = frame
            if (dt <= 0f) continue
            val now = time.floatValue + dt
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
            val share = if (isControlled) held.value else loopValue(clock.floatValue, currentRest)
            val speed = clamp01(max(0f, share - before) / dt * 4f)
            before = share
            val was = pace.moving
            pace.moving += (speed - was) * min(1f, dt * 10f)
            // The ring coming to rest sets off the bounce and the first ripple.
            if (was > 0.05f && pace.moving <= 0.05f && share > 0.01f) pace.landed = now
            // Written last: reading it is what redraws the component each frame.
            time.floatValue = now
        }
    }

    // Read inside draw / layer lambdas so a frame only redraws, it does not recompose.
    val value = { if (controlled) held.value else loopValue(clock.floatValue, rest) }
    val percent by remember(controlled, rest) { derivedStateOf { (value() * 100f).roundToInt() } }
    val weekShown by remember(controlled, rest, week) { derivedStateOf { (week * smallAt(value(), rest, 0)).roundToInt() } }
    val dayShown by remember(controlled, rest, day) { derivedStateOf { (day * smallAt(value(), rest, 1)).roundToInt() } }

    val ambient = if (reducedMotion) 0f else 1f
    val paints = remember { HabitPaints() }

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
                contentDescription = label
                stateDescription = "${(target * 100f).roundToInt()}%, $weekLabel $week, $dayLabel $day. ${caption.replace('\n', ' ')}"
                progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    val t = time.floatValue
                    val share = clamp01(value())
                    val at = clock.floatValue
                    val cx = x + CX
                    val cy = y + CY

                    // The card: raised from the page by a dark shadow below and a faint light above.
                    drawIntoCanvas {
                        paints.cardShadow.draw(it.nativeCanvas, x + 6, y + 9, WIDTH, CARD_H, CARD_R)
                        paints.cardLight.draw(it.nativeCanvas, x - 4, y - 4, WIDTH, CARD_H, CARD_R)
                    }
                    plate(x, y, WIDTH, CARD_H, CARD_R, Color(0xFF373940), Color(0xFF2F3137), 0.35f)

                    // The big ring: track, breathing glow, the ring itself.
                    val ringOpacity = clamp01(share * 80f)
                    drawCircle(Color(0xFF202125), RING_R, Offset(cx, cy), style = Stroke(RING_W))
                    val glow = ringOpacity * (0.3f + ambient * (0.13f * sin(t * 1.7f) + 0.25f * pace.moving))
                    if (CanBlur && glow > 0.005f) {
                        paints.bounds.set(cx - RING_R, cy - RING_R, cx + RING_R, cy + RING_R)
                        drawIntoCanvas {
                            it.nativeCanvas.drawArc(paints.bounds, -90f, 360f * share, false, paints.glow.tinted(Orange, glow))
                        }
                    }
                    if (ringOpacity > 0.005f) ring(Orange, cx, cy, RING_R, share, RING_W, ringOpacity)

                    val angle = -PI_F / 2f + TAU * share
                    val head = Offset(cx + RING_R * cos(angle), cy + RING_R * sin(angle))
                    // When the ring lands, and every few seconds after, a ripple leaves its head.
                    val ripple = (t - pace.landed).mod(3.4f) / 0.9f
                    if (ambient > 0f && ripple < 1f && pace.moving <= 0.05f && share >= 0.01f) {
                        drawCircle(
                            Color(0xFFFFB78A), RING_W / 2f + 12f * easeOutCubic(ripple), head,
                            alpha = 0.55f * (1f - ripple), style = Stroke(1.6f),
                        )
                    }
                    // Sparks thrown off the head while it draws: left behind it, drifting off the ring to either side.
                    val power = ambient * pace.moving
                    if (power >= 0.02f) {
                        for (ember in Embers) {
                            val u = (t * 1.5f + ember.phase) % 1f
                            val trail = angle - u * 0.42f
                            val r = RING_R + ember.side * (4f + 13f * u)
                            drawCircle(Color(0xFFFFC9A2), ember.r * (1f - u) * power, Offset(cx + r * cos(trail), cy + r * sin(trail)))
                        }
                        // The head burns white.
                        if (CanBlur) {
                            drawIntoCanvas {
                                it.nativeCanvas.drawCircle(
                                    head.x, head.y, RING_W / 2f + 2f, paints.hot.tinted(Color(0xFFFFF1E4), ringOpacity * power),
                                )
                            }
                        }
                    }

                    for (index in 0 until 2) {
                        val sx = x + SMALL_X[index]
                        val sy = y + SMALL_Y
                        val drawn = (if (index == 0) week else day) / 100f * smallAt(share, rest, index)
                        drawCircle(Color(0xFF222328), SMALL_R, Offset(sx, sy), style = Stroke(SMALL_W))
                        if (drawn > 0.001f) ring(Orange, sx, sy, SMALL_R, drawn, SMALL_W)
                    }

                    for (index in 0 until 3) {
                        val rise = tileAt(at, index)
                        if (rise <= 0.005f) continue
                        val tx = x + TILE_STEP * index
                        translate(0f, 10f * (1f - rise)) {
                            drawIntoCanvas { paints.tileShadow.draw(it.nativeCanvas, tx + 4, y + TILE_Y + 6, TILE_W, TILE_H, TILE_R, rise) }
                            plate(tx, y + TILE_Y, TILE_W, TILE_H, TILE_R, Color(0xFF37393F), Color(0xFF303238), 0.4f, rise)
                        }
                    }
                }
            }
        }

        Box(Modifier.matchParentSize()) {
            Row(
                Modifier
                    .centerAt((WIDTH / 2f + 1.7f).dp, (72.4f + 32f).dp)
                    .graphicsLayer {
                        val scale = if (ambient > 0f) pop(time.floatValue - pace.landed, 0.07f) else 1f
                        scaleX = scale
                        scaleY = scale
                    },
                verticalAlignment = Alignment.Top,
            ) {
                BasicText(percent.toString(), style = PercentStyle, maxLines = 1)
                BasicText("%", Modifier.padding(start = 1.dp, top = 1.5.dp), SignStyle, maxLines = 1)
            }
            BasicText(label, Modifier.centerAt((WIDTH / 2f + 1.55f).dp, 149.7.dp), LabelStyle, maxLines = 1)

            BasicText(weekShown.toString(), Modifier.centerAt(SMALL_X[0].dp, SMALL_Y.dp), SmallValueStyle, maxLines = 1)
            BasicText(weekLabel, Modifier.centerAt((SMALL_X[0] + 1.1f).dp, 143.8.dp), SmallLabelStyle, maxLines = 1)
            BasicText(dayShown.toString(), Modifier.centerAt(SMALL_X[1].dp, SMALL_Y.dp), SmallValueStyle, maxLines = 1)
            BasicText(dayLabel, Modifier.centerAt((SMALL_X[1] + 1.1f).dp, 143.8.dp), SmallLabelStyle, maxLines = 1)

            BasicText(caption, Modifier.offset(50.dp, 220.4.dp).width((WIDTH - 100f).dp), CaptionStyle, maxLines = 2)

            for (index in 0 until 3) {
                val stat = stats[index]
                // Counts with the big ring.
                val count by remember(controlled, rest, stat) {
                    derivedStateOf { (stat.value * easeOutCubic(value() / max(rest, 0.001f))).roundToInt() }
                }
                Box(
                    Modifier
                        .offset((TILE_STEP * index).dp, TILE_Y.dp)
                        .size(TILE_W.dp, TILE_H.dp)
                        .graphicsLayer {
                            val rise = tileAt(clock.floatValue, index)
                            alpha = rise
                            translationY = (10f * (1f - rise)).dp.toPx()
                        },
                ) {
                    BasicText("$count${stat.suffix}", Modifier.offset(14.9.dp, 11.7.dp), StatValueStyle, maxLines = 1)
                    BasicText(stat.label, Modifier.offset(14.9.dp, 41.7.dp), StatLabelStyle, maxLines = 1)
                }
            }
        }
    }
}
