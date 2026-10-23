package com.ngocdevv.nativecook.blocks.progress.profittrackprogress

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val WIDTH = 340f
private const val HEIGHT = 212f
private const val RADIUS = 18f
private const val TOP_H = 92f

// The canvas overflows the card by this much on every side to fit its plate and shadow.
private val BLEED = 40.dp

private const val TRACK_X = 22f
private const val TRACK_Y = 179.6f
private const val TRACK_W = 285.5f
private const val TRACK_H = 6.9f
private const val KNOB_R = 7.2f
private const val BADGE_X = TRACK_X + TRACK_W
private const val BADGE_R = 13.8f
private const val LOOP_X = 310.2f
private const val LOOP_Y = 146f

// Resting position in the reference (135% of a 180% track), and the autoPlay loop around it (seconds).
private const val REST = 0.75f
private const val START = 0.4f
private const val FILL = 2.6f
private const val HOLD = 3.6f
private const val RESET = 0.6f
private const val LOOP = START + FILL + HOLD + RESET

// A moment in the loop where everything is at rest: used when nothing is playing.
private const val REST_T = START + FILL + 1f

private val Blue = Color(0xFF4F70E4)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun profitText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val StatusStyle = profitText(9f, 12f, FontWeight.Medium, Color(0xFF5FA468))
private val NameStyle = profitText(16f, 20f, FontWeight.Bold, Color(0xFF12141A))
private val PriceStyle = profitText(13.5f, 18f, FontWeight.Bold, Blue)
    .copy(fontStyle = FontStyle.Italic, fontFeatureSettings = "tnum")
private val ApyStyle = profitText(13.5f, 18f, FontWeight.Bold, Blue).copy(fontFeatureSettings = "tnum")
private val UnitsStyle = profitText(9f, 12f, FontWeight.Normal, Color(0xFF9AA0A6))
private val HeadingStyle = profitText(9.5f, 12f, FontWeight.Normal, Color(0xFF8B8E96)).copy(letterSpacing = 3.2.sp)
private val PercentStyle = profitText(18f, 24f, FontWeight.Bold, Color(0xFFEEF0F6)).copy(fontFeatureSettings = "tnum")
private val CaptionStyle = profitText(11.5f, 18f, FontWeight.Medium, Color(0xFF9C9EA5))

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

private fun easeOutCubic(t: Float) = 1f - (1f - clamp01(t)).pow(3)

// Runs a little past the target and springs back: the knob lands with an overshoot.
private fun easeOutBack(t: Float): Float {
    val c = 1.15f
    return 1f + (c + 1f) * (t - 1f).pow(3) + c * (t - 1f).pow(2)
}

/** Position on the track at [t] seconds into the autoPlay loop. */
private fun loopValue(t: Float) = when {
    t < START -> 0f
    t < START + FILL -> REST * easeOutBack((t - START) / FILL)
    t < LOOP - RESET -> REST
    else -> REST * (1f - easeInOutCubic((t - (LOOP - RESET)) / RESET))
}

private fun knobX(p: Float) = KNOB_R + (TRACK_W - KNOB_R - BADGE_R) * clamp01(p)

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

/** Places the element by its centre inside the card. */
private fun Modifier.centerAt(x: Dp, y: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToPx() - placeable.width / 2, y.roundToPx() - placeable.height / 2)
    }
}

private fun svg(path: String) = PathParser().parsePathString(path).toPath()

/** An ember drifting up from the flame. */
private class ProfitEmber(val phase: Float, val period: Float, val reach: Float, val rise: Float, val radius: Float)

private class ProfitTrackPaints {
    val shadow = SoftRoundRect(rgba(20, 20, 45, 0.3f), sigma = 14f)
    val knobShadow = SoftRoundRect(rgba(0, 0, 0, 0.35f), sigma = 3f)
    val card = Path()
    private var cardX = Float.NaN

    // Flame in a 20 × 24 box, its base at the bottom centre.
    val flame = svg("M10 0C11 5 17 8 17 15C17 20 14 24 10 24C6 24 3 20 3 15C3 12 4.5 10 6 8.5C6.5 11 7.5 12 8.5 12C8 8 8.5 3 10 0Z")
    val flameInner = svg("M10 11C11 14 13.5 15.5 13.5 19C13.5 22 12 24 10 24C8 24 6.5 22 6.5 19C6.5 16 9 14 10 11Z")
    val infinity = svg("M0 0C-3 -5.5 -11 -5.5 -11 0C-11 5.5 -3 5.5 0 0C3 -5.5 11 -5.5 11 0C11 5.5 3 5.5 0 0")
    val infinityMeasure = PathMeasure().apply { setPath(infinity, false) }
    val infinityDrawn = Path()

    // Side view of a saloon in a 40 × 17 box.
    val carBody = svg(
        "M1 12C1 9.5 2.5 8.5 5 8L11 7.2C13 4 15.5 2.2 19 2L26 2C29 2 31 3.5 33.5 7L37 7.8C38.6 8.2 39.2 9.5 39.2 11" +
            "L39.2 12.4C39.2 13 38.8 13.4 38.2 13.4L2 13.4C1.4 13.4 1 13 1 12.4Z",
    )
    val carGlass = svg("M13.5 7C15 4.6 16.8 3.4 19.3 3.4L22 3.4L22 7ZM23.4 3.4L26 3.4C28.2 3.4 29.8 4.6 31.6 7L23.4 7Z")
    val chevron = svg("M-3.6 1.8L0 -1.8L3.6 1.8")

    val embers = listOf(
        ProfitEmber(0.0f, 1.7f, 5f, 30f, 1.3f),
        ProfitEmber(0.23f, 2.1f, -6f, 36f, 1.0f),
        ProfitEmber(0.47f, 1.5f, 3f, 26f, 1.5f),
        ProfitEmber(0.66f, 1.9f, -3f, 33f, 1.1f),
        ProfitEmber(0.85f, 2.3f, 7f, 38f, 0.9f),
    )
    val sparks = floatArrayOf(-2.6f, -2.05f, -1.5f, -0.95f, -0.4f, 0.3f, 2.85f, 3.5f)

    fun update(x: Float, y: Float) {
        if (x == cardX) return
        cardX = x
        card.reset()
        card.addRoundRect(RoundRect(x, y, x + WIDTH, y + HEIGHT, CornerRadius(RADIUS)))
    }
}

private fun DrawScope.glow(color: Color, center: Offset, radius: Float) = drawCircle(
    brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = center, radius = radius),
    radius = radius,
    center = center,
)

/**
 * Two-layer asset card: a white row describing the asset and a dark panel with its profit
 * progress on a blue track that ends in a flame. The flame flickers and sheds embers, the knob
 * lands with a springy overshoot, and crossing 100% ignites the badge (flash, ring, sparks) while
 * the caption lights up and the infinity sign draws itself.
 *
 * @param progress Controlled position on the track, 0…1 (1 is [maxPercent]). Leave null for the reference's 135%.
 * @param autoPlay Climbs from 0 to 135% by itself, holds, drops back and starts over.
 * @param maxPercent Profit shown when the knob reaches the flame.
 * @param label Caption shown once profit passes 100%; [pendingLabel] is shown before that.
 */
@Composable
fun ProfitTrackProgress(
    progress: Float? = null,
    autoPlay: Boolean = false,
    maxPercent: Float = 180f,
    status: String = "Active",
    name: String = "Vela GT 2025",
    price: String = "270,675",
    currency: String = "USDT",
    apy: String = "178.12%",
    units: Int = 1,
    label: String = "Explosive profit",
    pendingLabel: String = "Building profit",
    onToggleClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val controlled = progress != null
    val target = (progress ?: REST).coerceIn(0f, 1f)
    val threshold = 100f / maxPercent
    val auto = autoPlay && !controlled && !reducedMotion
    val looping by rememberUpdatedState(auto)
    val isControlled by rememberUpdatedState(controlled)
    val limit by rememberUpdatedState(threshold)

    // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
    val time = remember { mutableFloatStateOf(0f) }
    val clock = remember { mutableFloatStateOf(if (auto) 0f else REST_T) }
    var playing by remember { mutableStateOf(false) }
    val held = remember { Animatable(target) }
    // When profit last crossed 100%, on the `time` axis.
    val ignitedAt = remember { mutableFloatStateOf(-10f) }

    LaunchedEffect(auto) {
        playing = auto
        clock.floatValue = if (auto) 0f else REST_T
    }

    LaunchedEffect(controlled, target) {
        if (!controlled || held.targetValue == target) return@LaunchedEffect
        held.animateTo(target, tween(if (reducedMotion) 200 else 700, easing = EaseOutStrong))
    }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        var before = target
        while (true) {
            val now = withFrameNanos { it }
            val dt = min((now - last) / 1e9f, 0.064f)
            last = now
            time.floatValue += dt
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
            // Crossing 100% on the way up sets off the ignition.
            val current = if (isControlled) held.value else loopValue(clock.floatValue)
            if (before < limit && current >= limit) ignitedAt.floatValue = time.floatValue
            before = current
        }
    }

    // Read inside draw / layer lambdas so a frame only redraws, it does not recompose.
    val value = { if (controlled) held.value else loopValue(clock.floatValue) }
    val lit = { clamp01((value() - threshold) / 0.03f) }
    // The number is real text: recompose only when the whole percent changes.
    val percent by remember(controlled, maxPercent) {
        derivedStateOf { ((if (controlled) held.value else loopValue(clock.floatValue)) * maxPercent).roundToInt() }
    }

    val ambient = if (reducedMotion) 0f else 1f
    val paints = remember { ProfitTrackPaints() }

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
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "$name, $status, $price $currency, estimated APY $apy, $units unit. Profit progress"
                stateDescription = "$percent%, ${if (percent >= 100) label else pendingLabel}"
                progressBarRangeInfo = ProgressBarRangeInfo(percent / maxPercent, 0f..1f)
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    paints.update(x, y)
                    val t = time.floatValue
                    val p = value()
                    val hot = lit()
                    // Seconds since the ignition; large when there is none to show.
                    val burst = if (ambient > 0f) t - ignitedAt.floatValue else 10f
                    val tx = x + TRACK_X
                    val ty = y + TRACK_Y
                    val bx = x + BADGE_X

                    // The pale plate the card sits on in the reference, then its shadow.
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to rgba(0, 0, 0, 0.075f),
                            0.6f to rgba(0, 0, 0, 0.05f),
                            1f to rgba(0, 0, 0, 0f),
                            startY = y - 13f,
                            endY = y + HEIGHT + 47f,
                        ),
                        topLeft = Offset(x - 13f, y - 13f),
                        size = Size(WIDTH + 26f, HEIGHT + 60f),
                        cornerRadius = CornerRadius(30f),
                    )
                    drawIntoCanvas { paints.shadow.draw(it.nativeCanvas, x + 8, y + 16, WIDTH - 16, HEIGHT - 8, RADIUS) }

                    clipPath(paints.card) {
                        drawRect(Color(0xFF17181D), Offset(x, y), Size(WIDTH, HEIGHT))
                        glow(rgba(46, 58, 130, 0.5f), Offset(x, y + 110f), 150f)
                        glow(rgba(66, 38, 120, 0.5f), Offset(x + 170f, y + HEIGHT + 40f), 190f)
                        glow(rgba(130, 80, 24, 0.42f), Offset(bx, ty - 10f), 130f)
                    }
                    drawRoundRect(Color.White, Offset(x, y), Size(WIDTH, TOP_H), CornerRadius(RADIUS))

                    // Thumbnail with a drawn car.
                    drawRoundRect(Color(0xFFE5E6F4), Offset(x + 18.2f, y + 20.7f), Size(51f, 51f), CornerRadius(8f))
                    translate(x + 18.2f + 5.5f, y + 20.7f + 19f) {
                        drawRoundRect(rgba(30, 40, 80, 0.18f), Offset(3f, 14.6f), Size(34f, 3f), CornerRadius(1.5f))
                        drawPath(paints.carBody, Color(0xFF263352))
                        drawPath(paints.carGlass, Color(0xFFAEBBD6))
                        for (wheel in floatArrayOf(9.5f, 30.5f)) {
                            drawCircle(Color(0xFF151A26), 3.4f, Offset(wheel, 13.4f))
                            drawCircle(Color(0xFFC9CFDB), 1.4f, Offset(wheel, 13.4f))
                        }
                    }

                    // Track: what is still ahead warms up towards the flame, what is behind is blue.
                    val knob = knobX(p)
                    val pill = CornerRadius(TRACK_H / 2)
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            0f to rgba(215, 118, 27, 0.22f),
                            0.62f to rgba(215, 118, 27, 0.36f),
                            0.77f to Color(0xFFD7761B),
                            0.88f to Color(0xFFDCAB44),
                            1f to Color(0xFFFEEF91),
                            startX = tx,
                            endX = tx + TRACK_W,
                        ),
                        topLeft = Offset(tx + knob, ty - TRACK_H / 2),
                        size = Size(TRACK_W - knob, TRACK_H),
                        cornerRadius = pill,
                    )
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            0f to Color(0xFF18D5DA),
                            0.19f to Color(0xFF4F8AE2),
                            0.38f to Blue,
                            1f to Color(0xFF4667D9),
                            startX = tx,
                            endX = tx + TRACK_W,
                        ),
                        topLeft = Offset(tx, ty - TRACK_H / 2),
                        size = Size(knob, TRACK_H),
                        cornerRadius = pill,
                    )

                    // Flame badge with its two halos; the ring and sparks are the ignition at 100%.
                    translate(bx, ty) {
                        scale(1f + ambient * 0.05f * sin(t * 2.2f), pivot = Offset.Zero) {
                            val haloAlpha = 0.6f + 0.4f * hot
                            drawCircle(rgba(255, 214, 120, 0.07f), 42f, Offset.Zero, alpha = haloAlpha)
                            drawCircle(rgba(255, 214, 120, 0.14f), 21f, Offset.Zero, alpha = haloAlpha)
                        }
                        if (burst < 0.75f) {
                            drawCircle(
                                color = Color(0xFFFFD978),
                                radius = BADGE_R + 34f * easeOutCubic(burst / 0.75f),
                                center = Offset.Zero,
                                alpha = (0.7f * (1f - burst / 0.75f)).coerceIn(0f, 1f),
                                style = Stroke(1.5f),
                            )
                        }
                        if (burst < 0.6f) {
                            val distance = BADGE_R + 4f + 24f * easeOutCubic(burst / 0.6f)
                            for (angle in paints.sparks) {
                                drawCircle(
                                    color = Color(0xFFFFE27A),
                                    radius = 1.6f,
                                    center = Offset(distance * cos(angle), distance * sin(angle)),
                                    alpha = (1f - burst / 0.6f).coerceIn(0f, 1f),
                                )
                            }
                        }
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to Color(0xFFFFF6AD),
                                0.6f to Color(0xFFF8E182),
                                1f to Color(0xFFF0CF63),
                                center = Offset(0f, -2f),
                                radius = BADGE_R,
                            ),
                            radius = BADGE_R,
                            center = Offset.Zero,
                        )
                        // Embers drift up from the flame.
                        val heat = ambient * (0.3f + 0.7f * hot)
                        for (ember in paints.embers) {
                            val life = (t / ember.period + ember.phase) % 1f
                            drawCircle(
                                color = Color(0xFFFFC24A),
                                radius = ember.radius,
                                center = Offset(
                                    ember.reach * sin(life * 5f + ember.phase * 9f) * life,
                                    -BADGE_R * 0.4f - ember.rise * life,
                                ),
                                alpha = (heat * clamp01(life / 0.15f) * (1f - life) * 0.9f).coerceIn(0f, 1f),
                            )
                        }
                        // The flame leans and stretches like a real one; it is anchored at its base.
                        val size = 0.82f + 0.18f * hot
                        translate(0f, 8.2f) {
                            rotateRad(ambient * 0.07f * sin(t * 7.3f), pivot = Offset.Zero) {
                                scale(
                                    scaleX = 0.7f * size * (1f - ambient * 0.04f * sin(t * 13.1f)),
                                    scaleY = 0.7f * size *
                                        (1f + ambient * (0.07f * sin(t * 13.1f) + 0.04f * sin(t * 23.7f + 1f))),
                                    pivot = Offset.Zero,
                                ) {
                                    translate(-10f, -24f) {
                                        drawPath(
                                            paints.flame,
                                            Brush.verticalGradient(
                                                listOf(Color(0xFFF2401D), Color(0xFFFB6A1C), Color(0xFFFF9A26)),
                                                startY = 0f,
                                                endY = 24f,
                                            ),
                                        )
                                        scale(1f, 1f + ambient * 0.12f * sin(t * 17.3f + 2f), pivot = Offset(10f, 24f)) {
                                            drawPath(
                                                paints.flameInner,
                                                Brush.verticalGradient(
                                                    listOf(Color(0xFFFFB52E), Color(0xFFFFE45E), Color(0xFFFFF7C4)),
                                                    startY = 11f,
                                                    endY = 24f,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (burst < 1f) drawCircle(Color(0xFFFFFBE0), BADGE_R, Offset.Zero, alpha = exp(-6f * burst).coerceIn(0f, 1f))
                    }

                    val kx = tx + knob
                    drawIntoCanvas {
                        paints.knobShadow.draw(
                            it.nativeCanvas, kx - KNOB_R - 1, ty - KNOB_R, KNOB_R * 2 + 2, KNOB_R * 2 + 2, KNOB_R + 1,
                        )
                    }
                    drawCircle(Blue, KNOB_R, Offset(kx, ty))
                    drawCircle(Color.White, 2f + ambient * 0.35f * sin(t * 3.1f), Offset(kx, ty))

                    // The infinity sign draws itself as profit passes 100%.
                    translate(x + LOOP_X, y + LOOP_Y) {
                        val stroke = Stroke(2.3f, cap = StrokeCap.Round)
                        drawPath(paints.infinity, rgba(255, 255, 255, 0.16f), style = stroke)
                        val drawn = clamp01((p - threshold) / 0.07f)
                        if (drawn > 0f) {
                            paints.infinityDrawn.reset()
                            paints.infinityMeasure.getSegment(
                                0f, paints.infinityMeasure.length * drawn, paints.infinityDrawn, true,
                            )
                            drawPath(paints.infinityDrawn, Color.White, style = stroke)
                        }
                    }
                }
            }
        }

        BasicText(
            text = status,
            style = StatusStyle,
            maxLines = 1,
            modifier = Modifier
                .leadingAt(79.3.dp, 26.dp)
                .height(14.dp)
                .background(Color(0xFFE8F6E6), RoundedCornerShape(3.5.dp))
                .padding(horizontal = 5.dp)
                .wrapContentHeight()
                .clearAndSetSemantics {},
        )
        BasicText(
            text = name,
            style = NameStyle,
            maxLines = 1,
            modifier = Modifier.leadingAt(79.3.dp, 46.3.dp).clearAndSetSemantics {},
        )
        BasicText(
            text = buildAnnotatedString {
                append(price)
                withStyle(SpanStyle(color = Color(0xFF5B7BE6), fontSize = 9.5.sp, fontWeight = FontWeight.Normal)) {
                    append(" $currency")
                }
            },
            style = PriceStyle,
            maxLines = 1,
            modifier = Modifier.leadingAt(79.3.dp, 66.dp).clearAndSetSemantics {},
        )
        BasicText(
            text = buildAnnotatedString {
                val small = SpanStyle(fontSize = 9.5.sp, fontWeight = FontWeight.Normal)
                withStyle(small.copy(color = Color(0xFF7D828A), fontStyle = FontStyle.Italic)) { append("est. ") }
                withStyle(small.copy(color = Color(0xFF9AA0A6))) { append("APY  ") }
                append(apy)
            },
            style = ApyStyle,
            maxLines = 1,
            modifier = Modifier.trailingAt(292.dp, 27.dp).clearAndSetSemantics {},
        )
        BasicText(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFF1D1F24), fontWeight = FontWeight.Bold)) { append(units.toString()) }
                append(" UNIT")
            },
            style = UnitsStyle,
            maxLines = 1,
            modifier = Modifier
                .trailingAt(321.2.dp, 66.35.dp)
                .height(14.3.dp)
                .border(1.dp, Color(0xFFDCDEE2), RoundedCornerShape(3.5.dp))
                .padding(horizontal = 5.dp)
                .wrapContentHeight()
                .clearAndSetSemantics {},
        )

        BasicText(
            text = "PROFIT PROGRESS",
            style = HeadingStyle,
            maxLines = 1,
            // The tracking adds a gap after the last letter; the offset keeps the word centred.
            modifier = Modifier.centerAt(171.6.dp, 118.2.dp).clearAndSetSemantics {},
        )
        BasicText(
            text = "$percent%",
            style = PercentStyle,
            maxLines = 1,
            modifier = Modifier.leadingAt(18.7.dp, 145.7.dp).clearAndSetSemantics {},
        )
        // The two captions cross-fade around 100%.
        BasicText(
            text = pendingLabel,
            style = CaptionStyle,
            maxLines = 1,
            modifier = Modifier
                .trailingAt(289.6.dp, 145.7.dp)
                .graphicsLayer { alpha = 1f - lit() }
                .clearAndSetSemantics {},
        )
        BasicText(
            text = label,
            style = CaptionStyle,
            maxLines = 1,
            modifier = Modifier
                .trailingAt(289.6.dp, 145.7.dp)
                .graphicsLayer { alpha = lit() }
                .clearAndSetSemantics {},
        )

        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        LaunchedEffect(pressed) {
            if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        val press by animateFloatAsState(
            targetValue = if (pressed) PRESS_SCALE else 1f,
            animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
            label = "press",
        )
        Canvas(
            Modifier
                .offset(298.3.dp, 18.6.dp)
                .size(22.8.dp)
                .graphicsLayer {
                    scaleX = press
                    scaleY = press
                }
                .background(Blue, CircleShape)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onToggleClick)
                .semantics { contentDescription = "Collapse" },
        ) {
            // The chevron is authored in dp around the disc's centre.
            scale(density, density, pivot = Offset.Zero) {
                translate(11.4f, 11.4f) {
                    drawPath(paints.chevron, Color.White, style = Stroke(1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}
