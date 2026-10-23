package com.ngocdevv.nativecook.blocks.progress.spendingorbit

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SIZE = 290f

// The canvas overflows the widget by this much on every side to fit the orbit's bloom.
private val BLEED = 24.dp

private const val TILE_W = 250f
private const val TILE_H = 239f
private const val TILE_X = (SIZE - TILE_W) / 2f
private const val TILE_Y = (SIZE - TILE_H) / 2f
private const val TILE_R = 26f
private const val INSET = 29.9f

private const val C = SIZE / 2f
private const val ORBIT_R = 141.8f

private const val TICKS = 27
private const val TICK_X = TILE_X + 29.4f
private const val TICK_Y = TILE_Y + 101.2f
private const val TICK_W = 2.9f
private const val TICK_H = 14f
private const val TICK_PITCH = 7.25f

private const val ROW_Y = TILE_Y + 143.7f
private const val ROW_PITCH = 20.85f

private class SpendingCategory(val label: String, val amount: Float, val ticks: Int, val color: Color)

// The day's categories as in the reference: their amounts and how many ticks of the bar they take.
private val CATEGORIES = listOf(
    SpendingCategory("Groceries", 78.46f, 11, Color(0xFFFB6A2C)),
    SpendingCategory("Entertainment", 56.2f, 6, Color(0xFF8B5CF6)),
    SpendingCategory("Transportation", 33.58f, 4, Color(0xFF3AA5F5)),
    SpendingCategory("Utilities", 24.21f, 2, Color(0xFF27D267)),
)
private const val SPENT = 192.45f
private const val SPENT_TICKS = 23

// Value shown in the reference, and the autoPlay loop built around it (seconds).
private const val REST = 0.78f
private const val BUDGET = SPENT / REST
private const val START = 0.45f
private const val FILL = 2.4f
private const val HOLD = 4.4f
private const val RESET = 0.8f
private const val LOOP = START + FILL + HOLD + RESET
private const val LANDED = START + FILL

// A moment in the loop where everything is at rest: used when nothing is playing.
private const val REST_T = LANDED + 1.6f

// Each of the two orbiting arcs spans a quarter turn at the reference's 78%.
private const val ARC_AT_FULL = 90f / REST
private const val GLINT_PERIOD = 3.4f

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

private fun orbitText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = NoFontPadding,
)

private val TitleStyle = orbitText(12.6f, 18f, FontWeight.Bold, Color(0xFF8C8C8C))
    .copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp)
private val SpentStyle = orbitText(29f, 38f, FontWeight.Medium, Color(0xFFEEEEEE))
    .copy(letterSpacing = (-1.3).sp, fontFeatureSettings = "tnum")
private val SpentCents = SpanStyle(color = Color(0xFF808080), fontSize = 16.sp, letterSpacing = (-0.2).sp)
private val PercentStyle = orbitText(16f, 21f, FontWeight.Medium, Color(0xFF808080))
    .copy(letterSpacing = (-0.2).sp, fontFeatureSettings = "tnum")
private val LabelStyle = orbitText(10.3f, 16f, FontWeight.Medium, Color(0xFFECECEC))
    .copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.35.sp)
private val AmountStyle = LabelStyle.copy(color = Color(0xFFF2F2F2), fontWeight = FontWeight.SemiBold)
private val AmountCents = SpanStyle(color = Color(0xFF858585), fontWeight = FontWeight.Medium)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

/** Budget share at [t] seconds into the autoPlay loop. */
private fun loopValue(t: Float) = when {
    t < START -> 0f
    t < LANDED -> REST * easeInOutCubic((t - START) / FILL)
    t < LOOP - RESET -> REST
    else -> REST * (1f - easeInOutCubic((t - (LOOP - RESET)) / RESET))
}

/** 0…1: how fast the value is moving at [t] seconds into the loop. */
private fun loopActivity(t: Float): Float {
    val u = (t - START) / FILL
    return if (u <= 0f || u >= 1f) 0f else sin(PI.toFloat() * u)
}

/** Money spent in category [index] once [total] has been spent in all: the categories fill one after the other. */
private fun categorySpend(total: Float, index: Int): Float {
    var before = 0f
    for (i in 0 until index) before += CATEGORIES[i].amount
    val own = max(0f, total - before)
    // Anything over the reference's total lands in the last category.
    return if (index == CATEGORIES.lastIndex) own else min(CATEGORIES[index].amount, own)
}

/** How many ticks of the bar [total] lights, fractional: each category fills its own run of ticks. */
private fun litTicks(total: Float): Float {
    var lit = 0f
    CATEGORIES.forEachIndexed { index, category ->
        lit += category.ticks * clamp01(categorySpend(total, index) / category.amount)
    }
    return lit + (TICKS - SPENT_TICKS) * clamp01((total - SPENT) / (BUDGET - SPENT))
}

private fun tickColor(index: Int): Color {
    var end = 0
    for (category in CATEGORIES) {
        end += category.ticks
        if (index < end) return category.color
    }
    return CATEGORIES.last().color
}

/** Whole part and cents of an amount, styled apart. */
private fun moneyText(cents: Int, fraction: SpanStyle) = buildAnnotatedString {
    append("$${cents / 100}")
    withStyle(fraction) { append(String.format(Locale.US, ".%02d", cents % 100)) }
}

/** Places the element by its leading edge and first baseline inside the widget. */
private fun Modifier.leadingBaseline(x: Dp, baseline: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToPx(), baseline.roundToPx() - placeable[FirstBaseline])
    }
}

/** Places the element by its trailing edge and first baseline inside the widget. */
private fun Modifier.trailingBaseline(x: Dp, baseline: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToPx() - placeable.width, baseline.roundToPx() - placeable[FirstBaseline])
    }
}

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

private class OrbitPaints {
    val tile = Path().apply {
        addRoundRect(RoundRect(Rect(TILE_X, TILE_Y, TILE_X + TILE_W, TILE_Y + TILE_H), CornerRadius(TILE_R)))
    }
    val restingTicks = Path().apply {
        for (i in 0 until TICKS) {
            val x = TICK_X + i * TICK_PITCH
            addRoundRect(RoundRect(Rect(x, TICK_Y, x + TICK_W, TICK_Y + TICK_H), CornerRadius(TICK_W / 2f)))
        }
    }
    val arcs = Path()
    val glint = Path()

    val glass = Brush.linearGradient(
        0f to Color(0xFF3D3D3D), 0.34f to Color(0xFF232323), 0.68f to Color(0xFF1D1D1D), 1f to Color(0xFF434343),
        start = Offset(TILE_X + TILE_W, TILE_Y), end = Offset(TILE_X, TILE_Y + TILE_H),
    )
    val edge = Brush.linearGradient(
        listOf(rgba(255, 255, 255, 0.3f), rgba(255, 255, 255, 0.12f), rgba(255, 255, 255, 0.26f)),
        start = Offset(TILE_X + TILE_W, TILE_Y), end = Offset(TILE_X, TILE_Y + TILE_H),
    )

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

    val bloom = soft(rgba(255, 255, 255, 0.9f), 7f, 7f)
    val shine = soft(Color.White, 2.2f, 6.5f)
    val shadow = SoftRoundRect(rgba(0, 0, 0, 0.4f), sigma = 12f)
    val orange = soft(rgba(214, 92, 36, 0.13f), 30f)
    val blue = soft(rgba(48, 84, 196, 0.14f), 28f)
    val green = soft(rgba(30, 170, 96, 0.13f), 30f)

    /** Adds an arc of the orbit, in degrees clockwise from 12 o'clock. */
    fun orbit(path: Path, from: Float, sweep: Float) {
        path.addArc(Rect(Offset(C, C), ORBIT_R), from - 90f, sweep.coerceIn(0.01f, 359f))
    }
}

/**
 * Dark glass "today spending" tile wrapped by a thin white orbit: the amount spent, its share of
 * the budget, a bar of coloured ticks split by category and the category list. The ticks light
 * in a wave, each one stretching as the fill reaches it, while the amounts count up row by row
 * and each category bleeds its colour into the glass; the two orbiting arcs grow and turn once
 * around the tile, overshoot as they land with a flash of their bloom, and at rest a glint keeps
 * running along the arcs and through the lit ticks.
 *
 * @param progress Controlled share of the daily budget spent, 0…1. Leave null to show the reference's 78% (or the autoPlay loop).
 * @param autoPlay Fills by itself from nothing to 78%, holds, empties and starts over.
 */
@Composable
fun SpendingOrbit(
    progress: Float? = null,
    autoPlay: Boolean = false,
    title: String = "TODAY SPENDING",
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val controlled = progress != null
    val target = (progress ?: REST).coerceIn(0f, 1f)
    val auto = autoPlay && !controlled && !reducedMotion
    val looping by rememberUpdatedState(auto)

    // Seconds: `time` never stops (glints), `clock` is the position in the autoPlay loop.
    val time = remember { mutableFloatStateOf(0f) }
    val clock = remember { mutableFloatStateOf(if (auto) 0f else REST_T) }
    var playing by remember { mutableStateOf(false) }
    val held = remember { Animatable(target) }
    // When a controlled value last changed, on the `time` axis.
    val movedAt = remember { mutableFloatStateOf(-10f) }

    LaunchedEffect(auto) {
        playing = auto
        clock.floatValue = if (auto) 0f else REST_T
    }

    LaunchedEffect(controlled, target) {
        if (!controlled || held.targetValue == target) return@LaunchedEffect
        movedAt.floatValue = time.floatValue
        held.animateTo(target, tween(if (reducedMotion) 200 else 900, easing = EaseOutStrong))
    }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = min((now - last) / 1e9f, 0.064f)
            last = now
            time.floatValue += dt
            if (!playing) continue
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
    }

    // Read inside draw / layer lambdas so a frame only redraws, it does not recompose.
    val value = { clamp01(if (controlled) held.value else loopValue(clock.floatValue)) }
    val activity = {
        when {
            reducedMotion -> 0f
            !controlled -> loopActivity(clock.floatValue)
            else -> {
                val u = (time.floatValue - movedAt.floatValue) / 0.8f
                if (u <= 0f || u >= 1f) 0f else sin(PI.toFloat() * u)
            }
        }
    }
    // Seconds since the value landed; large when nothing happened.
    val landed = {
        when {
            reducedMotion -> 10f
            !controlled -> if (clock.floatValue < LOOP - RESET) clock.floatValue - LANDED else 10f
            else -> time.floatValue - movedAt.floatValue - 0.7f
        }
    }
    // The figures are real text: each recomposes only when its own cents change.
    val percent by remember(controlled) { derivedStateOf { (value() * 100f).roundToInt() } }
    val totalCents = remember(controlled) { derivedStateOf { (value() * BUDGET * 100f).roundToInt() } }
    val rowCents = remember(controlled) {
        CATEGORIES.indices.map { index -> derivedStateOf { (categorySpend(value() * BUDGET, index) * 100f).roundToInt() } }
    }

    val ambient = if (reducedMotion) 0f else 1f
    val paints = remember { OrbitPaints() }

    Box(
        modifier = modifier
            .size(SIZE.dp)
            .pointerInput(controlled, reducedMotion) {
                detectTapGestures {
                    if (!controlled && !reducedMotion) {
                        clock.floatValue = 0f
                        playing = true
                    }
                }
            }
            .semantics {
                contentDescription = "$title, ${String.format(Locale.US, "$%.2f", percent / 100f * BUDGET)} spent"
                progressBarRangeInfo = ProgressBarRangeInfo(percent / 100f, 0f..1f)
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    val p = value()
                    val total = p * BUDGET
                    val t = time.floatValue
                    val busy = activity()
                    val since = landed()
                    val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    translate(x, y) {
                        // Orbit: two opposite arcs that grow with the value while the pair turns once per 78%.
                        drawCircle(rgba(255, 255, 255, 0.055f), ORBIT_R, Offset(C, C), style = Stroke(2f))
                        val spin = p / REST * 360f
                        val wobble = if (since > 0f && since < 1.2f) 7f * exp(-4.5f * since) * sin(13f * since) else 0f
                        val sweep = ARC_AT_FULL * p + wobble
                        val arcsAlpha = clamp01(p / 0.02f)
                        paints.arcs.rewind()
                        paints.orbit(paints.arcs, spin, sweep)
                        paints.orbit(paints.arcs, spin + 180f, sweep)
                        drawIntoCanvas {
                            val canvas = it.nativeCanvas
                            if (blurs) {
                                // Their bloom breathes, and flashes as the value lands.
                                val u = since / 0.8f
                                val flash = if (u > 0f && u < 1f) 0.5f * (1f - u) else 0f
                                val bloom = arcsAlpha * min(1f, 0.5f + 0.12f * ambient * sin(t * 1.4f) + 0.3f * busy + flash)
                                paints.bloom.alpha = (255f * 0.9f * bloom).roundToInt().coerceIn(0, 255)
                                canvas.drawPath(paints.arcs.asAndroidPath(), paints.bloom)
                            }
                        }
                        drawPath(paints.arcs, Color.White, alpha = arcsAlpha, style = Stroke(3.7f, cap = StrokeCap.Round))
                        // At rest a glint runs along each arc from its tail to its head.
                        val glintPhase = (t % GLINT_PERIOD) / 1.3f
                        val glintAlpha = ambient * (1f - busy) * arcsAlpha * (if (glintPhase < 1f) sin(PI.toFloat() * glintPhase) else 0f)
                        if (glintAlpha > 0f && blurs) {
                            val at = spin + (sweep + 16f) * min(1f, glintPhase) - 8f
                            val from = max(spin, at - 9f)
                            val to = min(spin + sweep, at + 9f)
                            if (to > from) {
                                paints.glint.rewind()
                                paints.orbit(paints.glint, from, to - from)
                                paints.orbit(paints.glint, from + 180f, to - from)
                                paints.shine.alpha = (255f * glintAlpha).roundToInt().coerceIn(0, 255)
                                drawIntoCanvas { it.nativeCanvas.drawPath(paints.glint.asAndroidPath(), paints.shine) }
                            }
                        }

                        // Glass tile
                        drawIntoCanvas { paints.shadow.draw(it.nativeCanvas, TILE_X, TILE_Y + 6f, TILE_W, TILE_H, TILE_R) }
                        drawPath(paints.tile, paints.glass)
                        // Colour that the lit categories bleed into the glass.
                        if (blurs) {
                            clipPath(paints.tile) {
                                drawIntoCanvas {
                                    val canvas = it.nativeCanvas
                                    fun glow(paint: Paint, base: Float, index: Int, gx: Float, gy: Float, r: Float) {
                                        val share = clamp01(categorySpend(total, index) / CATEGORIES[index].amount)
                                        paint.alpha = (255f * base * share).roundToInt()
                                        canvas.drawCircle(TILE_X + gx, TILE_Y + gy, r, paint)
                                    }
                                    glow(paints.orange, 0.13f, 0, 38f, 132f, 62f)
                                    glow(paints.blue, 0.14f, 2, 138f, 116f, 50f)
                                    glow(paints.green, 0.13f, 3, 206f, 150f, 58f)
                                }
                            }
                        }
                        drawRoundRect(
                            brush = paints.edge,
                            topLeft = Offset(TILE_X + 0.5f, TILE_Y + 0.5f),
                            size = Size(TILE_W - 1f, TILE_H - 1f),
                            cornerRadius = CornerRadius(TILE_R - 0.5f),
                            style = Stroke(1f),
                        )

                        // Ticks: the ones around the moving head of the fill stretch, and each
                        // settles in its category's colour.
                        val lit = litTicks(total)
                        for (index in 0 until TICKS) {
                            val near = clamp01(1f - abs(lit - index - 0.5f) / 2.2f)
                            val h = TICK_H * (1f + 0.45f * busy * near * near * (3f - 2f * near))
                            val topLeft = Offset(TICK_X + index * TICK_PITCH, TICK_Y + (TICK_H - h) / 2f)
                            val size = Size(TICK_W, h)
                            val corner = CornerRadius(TICK_W / 2f)
                            drawRoundRect(rgba(255, 255, 255, 0.2f), topLeft, size, corner)
                            val on = clamp01(lit - index)
                            if (on > 0f) drawRoundRect(tickColor(index), topLeft, size, corner, alpha = on)
                        }
                        // A soft highlight that keeps travelling through the lit ticks.
                        val shineAlpha = ambient * (1f - busy)
                        if (shineAlpha > 0f) {
                            val phase = min(1f, ((t + 1.2f) % GLINT_PERIOD) / 1.5f)
                            val sx = TICK_X - 24f + (TICKS * TICK_PITCH + 48f) * phase
                            clipPath(paints.restingTicks) {
                                clipRect(TICK_X - 1f, TICK_Y - 4f, TICK_X - 1f + max(0f, lit * TICK_PITCH - 1f), TICK_Y + TICK_H + 4f) {
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            listOf(rgba(255, 255, 255, 0f), rgba(255, 255, 255, 0.75f), rgba(255, 255, 255, 0f)),
                                            startX = sx - 12f,
                                            endX = sx + 12f,
                                        ),
                                        topLeft = Offset(sx - 12f, TICK_Y - 2f),
                                        size = Size(24f, TICK_H + 4f),
                                        alpha = shineAlpha,
                                    )
                                }
                            }
                        }

                        // Row markers: each pops as its category starts to fill.
                        CATEGORIES.forEachIndexed { index, category ->
                            val share = clamp01(categorySpend(total, index) / category.amount)
                            val h = 11.6f * (1f + 0.5f * sin(PI.toFloat() * clamp01(share * 2.5f)))
                            val cy = ROW_Y + index * ROW_PITCH
                            drawRoundRect(
                                color = category.color,
                                topLeft = Offset(TILE_X + 29.4f, cy - h / 2f),
                                size = Size(2.9f, h),
                                cornerRadius = CornerRadius(1.45f),
                                alpha = 0.3f + 0.7f * clamp01(share * 5f),
                            )
                        }
                    }
                }
            }
        }

        BasicText(
            text = title,
            style = TitleStyle,
            maxLines = 1,
            modifier = Modifier.leadingAt((TILE_X + INSET).dp, (TILE_Y + 39.6f).dp).clearAndSetSemantics {},
        )
        BasicText(
            text = moneyText(totalCents.value, SpentCents),
            style = SpentStyle,
            maxLines = 1,
            modifier = Modifier.leadingBaseline((TILE_X + INSET - 1f).dp, (TILE_Y + 83.2f).dp).clearAndSetSemantics {},
        )
        BasicText(
            text = "$percent%",
            style = PercentStyle,
            maxLines = 1,
            modifier = Modifier.trailingBaseline((TILE_X + TILE_W - INSET).dp, (TILE_Y + 83.2f).dp).clearAndSetSemantics {},
        )
        CATEGORIES.forEachIndexed { index, category ->
            val cy = (ROW_Y + index * ROW_PITCH).dp
            // A row is dim until its category starts to fill.
            val dim = Modifier.graphicsLayer { alpha = 0.38f + 0.62f * clamp01(rowCents[index].value / (category.amount * 100f) * 5f) }
            BasicText(
                text = category.label,
                style = LabelStyle,
                maxLines = 1,
                modifier = Modifier.leadingAt((TILE_X + 41.5f).dp, cy).then(dim).clearAndSetSemantics {},
            )
            BasicText(
                text = moneyText(rowCents[index].value, AmountCents),
                style = AmountStyle,
                maxLines = 1,
                modifier = Modifier.trailingAt((TILE_X + TILE_W - INSET).dp, cy).then(dim).clearAndSetSemantics {},
            )
        }
    }
}
