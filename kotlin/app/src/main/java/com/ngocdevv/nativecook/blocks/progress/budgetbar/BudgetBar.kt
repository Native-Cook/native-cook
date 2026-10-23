package com.ngocdevv.nativecook.blocks.progress.budgetbar

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val WIDTH = 320f
private const val HEIGHT = 237f
private const val RADIUS = 25f

// The card's canvas overflows it by this much on every side to fit its shadow.
private val BLEED = 40.dp

private const val BAR_X = 21.9f
private const val BAR_Y = 109.7f
private const val BAR_W = 276.2f
private const val BAR_H = 6.4f

// Room around the bar's own canvas for its glow.
private val BAR_BLEED = 12.dp

private const val BUTTON_X = 21f
private const val BUTTON_Y = 165.7f
private const val BUTTON_W = 278f
private const val BUTTON_H = 50.3f
private val BUTTON_BLEED = 16.dp

private const val CHIP_X = 240.5f
private const val CHIP_Y = 21.9f
private const val CHIP_W = 57.6f
private const val CHIP_H = 22f
private val CHIP_BLEED = 12.dp

// Rolling digits: the size of one cell, and how fast a column may turn (digits per second).
private const val DIGIT_W = 8f
private const val DIGIT_H = 15f
private const val ROLL_SPEED = 16f

// The reference shows $2,886 of $8,000 spent; the autoPlay loop built around it (seconds).
private const val REST = 2886f / 8000f
private const val LOW = 0.08f
private const val ENTER = 0.9f
private const val FILL = 2.6f
private const val HOLD = 3.4f
private const val EXIT = 0.5f
private const val LOOP = ENTER + FILL + HOLD + EXIT

// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
private const val REST_T = ENTER + FILL + 1.4f

private val Ink = Color(0xFF2B2B2B)
private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun budgetText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val CaptionStyle = budgetText(10.8f, 16f, FontWeight.Normal, Color(0xFFA1A1A1), -0.2f)
private val AmountStyle = budgetText(28f, 34f, FontWeight.SemiBold, Ink, -0.4f)
private val SubtitleStyle = budgetText(13f, 18f, FontWeight.Normal, Color(0xFFA3A3A3), -0.2f)
private val MonthStyle = budgetText(10.4f, 16f, FontWeight.Medium, Color.White)
private val ButtonStyle = budgetText(17f, 22f, FontWeight.Medium, Color.White, -0.3f)
private val DigitStyle = budgetText(13.2f, DIGIT_H, FontWeight.Normal, Ink).copy(fontFeatureSettings = "tnum")

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

private fun easeOutCubic(t: Float) = 1f - (1f - clamp01(t)).pow(3)

/** Share of the budget spent at [t] seconds into the autoPlay loop. */
private fun loopValue(t: Float) = when {
    t < ENTER -> LOW
    t < ENTER + FILL -> LOW + (REST - LOW) * easeInOutCubic((t - ENTER) / FILL)
    else -> REST
}

/** 0…1: how far part [index] of the card has come in at [t] seconds into the loop. */
private fun entered(t: Float, index: Int) = easeOutCubic((t - 0.08f - 0.075f * index) / 0.55f)

/** The card's parts fade out together just before the loop starts over. */
private fun leaving(t: Float) = if (t > LOOP - EXIT) clamp01((LOOP - t) / (EXIT * 0.8f)) else 1f

private fun pow10(place: Int): Int {
    var result = 1
    repeat(place) { result *= 10 }
    return result
}

private fun digitOf(amount: Int, place: Int) = (amount / pow10(place)) % 10

/** Leading columns collapse while the amount is too small to need them. */
private fun needed(amount: Int, place: Int) = if (place == 0 || amount >= pow10(place)) 1f else 0f

private fun wrap10(position: Float): Float {
    val r = position % 10f
    return if (r < 0f) r + 10f else r
}

private fun money(amount: Int) = "$" + "%,d".format(java.util.Locale.US, amount)

/** One rolling amount: per column (index 0 is the ones) where it has turned to and how far it is unfolded. */
private class Odometer(places: Int, amount: Int) {
    val position = FloatArray(places) { digitOf(amount, it).toFloat() }
    val shown = FloatArray(places) { needed(amount, it) }

    fun snap(amount: Int) {
        for (place in position.indices) {
            position[place] = digitOf(amount, place).toFloat()
            shown[place] = needed(amount, place)
        }
    }

    /** [trend] is 1 while the amount is rising, -1 while it falls, 0 at rest: the way the columns turn. */
    fun turn(amount: Int, trend: Int, dt: Float) {
        for (place in position.indices) {
            shown[place] += (needed(amount, place) - shown[place]) * min(1f, dt * 12f)
            val ahead = wrap10(digitOf(amount, place) - position[place])
            // Turn the way the amount is going; at rest take the short way round.
            var delta = ahead
            if (if (trend < 0) ahead > 0.5f else if (trend == 0) ahead > 5f else ahead > 9.5f) delta = ahead - 10f
            if (abs(delta) < 0.004f) {
                position[place] = wrap10(position[place] + delta)
                continue
            }
            // Follow with a speed limit, so a fast count reads as a spinning column that then settles.
            val limit = ROLL_SPEED * dt
            position[place] = wrap10(position[place] + (delta * min(1f, dt * 16f)).coerceIn(-limit, limit))
        }
    }
}

/** Places the element by its leading edge and vertical centre inside the card. */
private fun Modifier.leadingAt(x: Dp, centerY: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToPx(), centerY.roundToPx() - placeable.height / 2)
    }
}

/** Places the element by its trailing edge and vertical centre inside the card. */
private fun Modifier.trailingAt(x: Dp, centerY: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToPx() - placeable.width, centerY.roundToPx() - placeable.height / 2)
    }
}

// Hardware canvases only honour mask filters from API 28: soft layers are skipped below that.
private val CanBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

private fun softPaint(sigma: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    if (CanBlur) maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
}

private fun Paint.tinted(color: Color, alpha: Float = 1f): Paint {
    this.color = color.copy(alpha = (color.alpha * alpha).coerceIn(0f, 1f)).toArgb()
    return this
}

private class BudgetPaints {
    val cardShadow = SoftRoundRect(rgba(0, 0, 0, 0.07f), sigma = 16f)
    val chipShadow = SoftRoundRect(rgba(0, 0, 0, 0.3f), sigma = 4.5f)
    val buttonShadow = SoftRoundRect(rgba(0, 0, 0, 0.3f), sigma = 6f)
    val glow = softPaint(4.5f)
    val bead = softPaint(2f)
    val fill = Path()
    val chevron = Path().apply {
        moveTo(-6.3f, -3.2f)
        lineTo(0f, 3.3f)
        lineTo(6.3f, -3.2f)
    }
}

private fun DrawScope.pill(color: Color, x: Float, y: Float, w: Float, h: Float) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(h / 2))

/**
 * White budget card: the month chip, the monthly limit, a thin aqua-to-ink bar, the amounts spent
 * and left, and a dark "View More" pill. The card's parts come in one after another, the amounts
 * roll like odometers while the bar grows, and a bead of light runs along the filled part.
 *
 * @param progress Controlled share of the budget spent, 0…1.
 * @param autoPlay Lays the card out part by part, counts up to the amount shown in the reference,
 *   holds, and starts over.
 * @param budget The monthly limit in whole dollars: the spent and remaining amounts are derived from it.
 */
@Composable
fun BudgetBar(
    progress: Float? = null,
    autoPlay: Boolean = false,
    budget: Int = 8000,
    title: String = "Monthly Budget",
    subtitle: String = "Monthly Spending Limit",
    month: String = "SEP",
    spentLabel: String = "Spent",
    remainingLabel: String = "Budget Remaining",
    buttonLabel: String = "View More",
    onMonthClick: () -> Unit = {},
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val controlled = progress != null
    val target = (progress ?: REST).coerceIn(0f, 1f)
    val auto = autoPlay && !controlled && !reducedMotion
    val looping by rememberUpdatedState(auto)
    val isControlled by rememberUpdatedState(controlled)
    val limit by rememberUpdatedState(budget)
    val places = budget.toString().length

    // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
    val time = remember { mutableFloatStateOf(0f) }
    val clock = remember { mutableFloatStateOf(if (auto) 0f else REST_T) }
    var playing by remember { mutableStateOf(false) }
    val held = remember { Animatable(target) }
    // An autoPlay run starts with its amounts in place instead of rolling to them.
    val spentDigits = remember(places) { Odometer(places, (budget * (if (auto) LOW else target)).roundToInt()) }
    val leftDigits = remember(places) { Odometer(places, budget - (budget * (if (auto) LOW else target)).roundToInt()) }

    LaunchedEffect(auto) {
        playing = auto
        clock.floatValue = if (auto) 0f else REST_T
    }

    LaunchedEffect(controlled, target) {
        if (!controlled || held.targetValue == target) return@LaunchedEffect
        held.animateTo(target, tween(if (reducedMotion) 200 else 1400, easing = EaseOutStrong))
    }

    LaunchedEffect(reducedMotion, places) {
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
            val step = current - before
            before = current
            val trend = if (step > 0.00002f) 1 else if (step < -0.00002f) -1 else 0
            val spent = (limit * clamp01(current)).roundToInt()
            if (abs(step) > 0.1f) {
                // A jump (a run starting over) shows the amounts in place instead of rolling to them.
                spentDigits.snap(spent)
                leftDigits.snap(limit - spent)
            } else {
                spentDigits.turn(spent, trend, dt)
                leftDigits.turn(limit - spent, -trend, dt)
            }
            // Written last: reading it is what redraws the bar and the digits each frame.
            time.floatValue += dt
        }
    }

    // Read inside draw / layer lambdas so a frame only redraws, it does not recompose.
    val value = { if (controlled) held.value else loopValue(clock.floatValue) }
    // The amounts as read out: they follow the value, which only recomposes the semantics.
    val spentNow by remember(controlled, budget) {
        derivedStateOf { (budget * clamp01(if (controlled) held.value else loopValue(clock.floatValue))).roundToInt() }
    }

    val ambient = if (reducedMotion) 0f else 1f
    val paints = remember { BudgetPaints() }
    val measurer = rememberTextMeasurer()
    val digits = remember(measurer) { List(10) { measurer.measure(it.toString(), DigitStyle) } }
    val sign = remember(measurer) { measurer.measure("$", DigitStyle) }
    val comma = remember(measurer) { measurer.measure(",", DigitStyle) }

    fun Modifier.part(index: Int) = graphicsLayer {
        val t = clock.floatValue
        val e = entered(t, index)
        alpha = e * leaving(t)
        translationY = 9.dp.toPx() * (1f - e)
    }

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
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    drawIntoCanvas { paints.cardShadow.draw(it.nativeCanvas, x + 6, y + 14, WIDTH - 12, HEIGHT - 10, RADIUS) }
                    drawRoundRect(Color.White, Offset(x, y), Size(WIDTH, HEIGHT), CornerRadius(RADIUS))
                }
            }
        }

        Box(Modifier.matchParentSize().part(0)) {
            BasicText(title, Modifier.leadingAt(22.4.dp, 33.4.dp), CaptionStyle, maxLines = 1)
            BudgetPressable(
                x = CHIP_X, y = CHIP_Y, width = CHIP_W, height = CHIP_H, pressedScale = 0.95f,
                label = "Month, $month", onClick = onMonthClick,
            ) {
                Box(Modifier.matchParentSize().bleed(CHIP_BLEED)) {
                    Canvas(Modifier.fillMaxSize()) {
                        surface(CHIP_BLEED) { x, y, _, _ ->
                            drawIntoCanvas { paints.chipShadow.draw(it.nativeCanvas, x + 2, y + 5, CHIP_W - 4, CHIP_H - 3, 6f) }
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    listOf(Color(0xFF282828), Color(0xFF3D3D3D)), startY = y, endY = y + CHIP_H,
                                ),
                                topLeft = Offset(x, y),
                                size = Size(CHIP_W, CHIP_H),
                                cornerRadius = CornerRadius(6f),
                            )
                            drawContext.transform.translate(x + 43.4f, y + 11.2f)
                            drawPath(
                                paints.chevron, Color.White,
                                style = Stroke(1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                            )
                            drawContext.transform.translate(-(x + 43.4f), -(y + 11.2f))
                        }
                    }
                }
                BasicText(month, Modifier.leadingAt(8.7.dp, 11.dp).clearAndSetSemantics {}, MonthStyle, maxLines = 1)
            }
        }

        BasicText(
            text = money(budget).replace("$", "$ "),
            style = AmountStyle,
            maxLines = 1,
            modifier = Modifier
                .part(1)
                .leadingAt(22.4.dp, 62.9.dp)
                .semantics {
                    contentDescription = "$title, ${money(budget)}"
                    stateDescription = "$spentLabel ${money(spentNow)}, $remainingLabel ${money(budget - spentNow)}"
                    progressBarRangeInfo = ProgressBarRangeInfo(spentNow.toFloat(), 0f..budget.toFloat())
                },
        )

        BasicText(subtitle, Modifier.part(2).leadingAt(22.4.dp, 95.1.dp), SubtitleStyle, maxLines = 1)

        Box(
            Modifier
                .part(3)
                .offset(BAR_X.dp, BAR_Y.dp)
                .size(BAR_W.dp, BAR_H.dp)
                .bleed(BAR_BLEED),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BAR_BLEED) { x, y, _, _ ->
                    val t = time.floatValue
                    val width = max(BAR_H, BAR_W * clamp01(value()))
                    pill(Color(0xFFD1D1D1), x, y, BAR_W, BAR_H)
                    if (CanBlur) {
                        // The aqua half of the bar has a faint glow that breathes.
                        drawIntoCanvas {
                            it.nativeCanvas.drawRoundRect(
                                x, y + 1f, x + width * 0.62f, y + 1f + BAR_H, BAR_H / 2, BAR_H / 2,
                                paints.glow.tinted(Color(0xFF19E9FF), 0.3f + ambient * 0.12f * sin(t * 1.7f)),
                            )
                        }
                    }
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            0f to Color(0xFF00F6FF),
                            0.3f to Color(0xFF2EE9FF),
                            0.48f to Color(0xFF79D3FB),
                            0.74f to Color(0xFF4A82A6),
                            1f to Color(0xFF23262B),
                            startX = x,
                            endX = x + width,
                        ),
                        topLeft = Offset(x, y),
                        size = Size(width, BAR_H),
                        cornerRadius = CornerRadius(BAR_H / 2),
                    )
                    // A bead of light runs along the filled part every few seconds.
                    val beadAt = (t % 3.4f) / 1.2f
                    if (CanBlur && ambient > 0f && beadAt < 1f) {
                        paints.fill.rewind()
                        paints.fill.addRoundRect(RoundRect(Rect(x, y, x + width, y + BAR_H), CornerRadius(BAR_H / 2)))
                        clipPath(paints.fill) {
                            val bx = x - 16f + (width + 16f) * beadAt
                            drawIntoCanvas {
                                it.nativeCanvas.drawRoundRect(
                                    bx, y + 0.8f, bx + 16f, y + BAR_H - 0.8f, 2.4f, 2.4f,
                                    paints.bead.tinted(Color.White, 0.85f * sin(PI.toFloat() * beadAt)),
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(Modifier.matchParentSize().part(4).clearAndSetSemantics {}) {
            BasicText(spentLabel, Modifier.leadingAt(22.4.dp, 134.4.dp), CaptionStyle, maxLines = 1)
            BasicText(remainingLabel, Modifier.trailingAt(297.6.dp, 134.4.dp), CaptionStyle, maxLines = 1)
            // The amounts: digits that roll like an odometer, drawn straight from the measured glyphs.
            Canvas(Modifier.matchParentSize().clipToBounds()) {
                // Read so every frame of the loop redraws the columns.
                time.floatValue
                if (ambient == 0f) {
                    val spent = (budget * clamp01(value())).roundToInt()
                    spentDigits.snap(spent)
                    leftDigits.snap(budget - spent)
                }
                val top = (149f - DIGIT_H / 2).dp.toPx()
                val cell = DIGIT_W.dp.toPx()
                val gap = 0.6.dp.toPx()
                val commaWidth = 3.6.dp.toPx()
                fun width(meter: Odometer): Float {
                    var total = sign.size.width + gap
                    for (place in meter.position.indices) {
                        total += cell * meter.shown[place]
                        if (place > 0 && place % 3 == 0) total += commaWidth * meter.shown[place]
                    }
                    return total
                }
                fun draw(meter: Odometer, start: Float) {
                    var x = start
                    drawText(sign, topLeft = Offset(x, top))
                    x += sign.size.width + gap
                    for (place in meter.position.indices.reversed()) {
                        val shown = meter.shown[place]
                        if (shown > 0.01f) {
                            val whole = floor(meter.position[place]).toInt()
                            val part = meter.position[place] - whole
                            clipRect(x, top, x + cell * shown, top + DIGIT_H.dp.toPx()) {
                                drawRolling(digits[whole % 10], x, top - DIGIT_H.dp.toPx() * part, cell, shown)
                                drawRolling(digits[(whole + 1) % 10], x, top + DIGIT_H.dp.toPx() * (1f - part), cell, shown)
                            }
                        }
                        x += cell * shown
                        if (place > 0 && place % 3 == 0) {
                            if (shown > 0.01f) {
                                clipRect(x, top, x + commaWidth * shown, top + DIGIT_H.dp.toPx()) {
                                    drawRolling(comma, x, top, commaWidth, shown)
                                }
                            }
                            x += commaWidth * shown
                        }
                    }
                }
                draw(spentDigits, 22.4.dp.toPx())
                draw(leftDigits, 297.6.dp.toPx() - width(leftDigits))
            }
        }

        Box(Modifier.matchParentSize().part(5)) {
            BudgetPressable(
                x = BUTTON_X, y = BUTTON_Y, width = BUTTON_W, height = BUTTON_H, pressedScale = PRESS_SCALE,
                label = buttonLabel, onClick = onClick,
            ) {
                Box(Modifier.matchParentSize().bleed(BUTTON_BLEED)) {
                    Canvas(Modifier.fillMaxSize()) {
                        surface(BUTTON_BLEED) { x, y, _, _ ->
                            drawIntoCanvas {
                                paints.buttonShadow.draw(it.nativeCanvas, x + 8, y + 9, BUTTON_W - 16, BUTTON_H - 4, BUTTON_H / 2)
                            }
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    listOf(Color(0xFF242424), Color(0xFF434343)), startY = y, endY = y + BUTTON_H,
                                ),
                                topLeft = Offset(x, y),
                                size = Size(BUTTON_W, BUTTON_H),
                                cornerRadius = CornerRadius(BUTTON_H / 2),
                            )
                            // A hairline that is dark along the top and light along the bottom, like a pressed rim.
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    0f to rgba(120, 120, 120, 0.55f),
                                    0.5f to rgba(120, 120, 120, 0.15f),
                                    1f to rgba(170, 170, 170, 0.7f),
                                    startY = y,
                                    endY = y + BUTTON_H,
                                ),
                                topLeft = Offset(x + 0.6f, y + 0.6f),
                                size = Size(BUTTON_W - 1.2f, BUTTON_H - 1.2f),
                                cornerRadius = CornerRadius(BUTTON_H / 2 - 0.6f),
                                style = Stroke(1.2f),
                            )
                        }
                    }
                }
                BasicText(
                    buttonLabel, Modifier.align(Alignment.Center).clearAndSetSemantics {}, ButtonStyle, maxLines = 1,
                )
            }
        }
    }
}

/** Draws one glyph of a rolling amount centred in its cell, fading with the cell as it folds away. */
private fun DrawScope.drawRolling(glyph: TextLayoutResult, x: Float, y: Float, cell: Float, alpha: Float) {
    drawText(glyph, topLeft = Offset(x + (cell - glyph.size.width) / 2f, y), alpha = alpha)
}

/** A tappable part of the card: scales down while pressed, with a light haptic. */
@Composable
private fun BudgetPressable(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    pressedScale: Float,
    label: String,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    LaunchedEffect(pressed) {
        if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )
    Box(
        modifier = Modifier
            .offset(x.dp, y.dp)
            .size(width.dp, height.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        content = content,
    )
}
