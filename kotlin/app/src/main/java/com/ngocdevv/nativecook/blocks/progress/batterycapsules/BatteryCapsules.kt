package com.ngocdevv.nativecook.blocks.progress.batterycapsules

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
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

enum class BatteryVariant { Light, Dark }

private const val SIZE = 164f
private const val RADIUS = 26f

// The canvas overflows the widget by this much on every side to fit its shadow.
private val BLEED = 24.dp

private const val CAP_X = 8.5f
private const val CAP_STEP = 50.85f
private const val CAP_W = 44.8f
private const val CAP_Y = 78.8f
private const val CAP_H = 76.2f
private const val CAP_R = 9.4f
private const val DISC_Y = 132.8f
private const val DISC_R = 16.25f
private const val CAST_X = 140.7f
private const val CAST_Y = 23.3f

// Where in the overall progress each capsule starts charging, and the share it takes.
private val DELAY = floatArrayOf(0f, 0.14f, 0.28f)
private const val SPAN = 0.72f

// At or below this charge a capsule is "low" and its fill breathes.
private const val LOW = 0.1f

// The reference shows every capsule at its level; the autoPlay loop built around it (seconds).
private const val REST = 1f
private const val START = 0.4f
private const val DRAW = 2f
private const val HOLD = 4.4f
private const val RESET = 0.6f
private const val LOOP = START + DRAW + HOLD + RESET

// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
private const val REST_T = START + DRAW + 1.6f

private class BatteryPalette(
    val cardTop: Color,
    val cardBottom: Color,
    val shadow: Color,
    val track: Color,
    val ink: Color,
    val dim: Color,
    val fills: List<Color>,
    val discs: List<Color>,
    val glyph: Color,
    val glow: Float,
    val sheen: Float,
)

private val LightPalette = BatteryPalette(
    cardTop = Color(0xFFD8DEE8), cardBottom = Color(0xFFC9D1DD), shadow = rgba(20, 30, 50, 0.22f),
    track = Color(0xFFBCC4D0), ink = Color(0xFF0B0B0C), dim = Color(0xFF5F7894),
    fills = listOf(Color(0xFF00D455), Color(0xFFFFCB05), Color(0xFFFF4245)),
    discs = listOf(Color(0xFFE8F8EE), Color(0xFFFBF8EA), Color(0xFFFBEFEF)),
    glyph = Color(0xFF0B0B0C), glow = 0.28f, sheen = 0.07f,
)

private val DarkPalette = BatteryPalette(
    cardTop = Color(0xFF1D232B), cardBottom = Color(0xFF11151A), shadow = rgba(0, 0, 0, 0.4f),
    track = Color(0xFF343A43), ink = Color.White, dim = Color(0xFF7D858F),
    fills = listOf(Color(0xFF06E23F), Color(0xFFFFD60A), Color(0xFFFF3B3B)),
    discs = listOf(Color(0xFF06350F), Color(0xFF2A2609), Color(0xFF2B1719)),
    glyph = Color.White, glow = 0.55f, sheen = 0.1f,
)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun batteryText(size: Float, lineHeight: Float, weight: FontWeight, spacing: Float = 0f) = TextStyle(
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val NameStyle = batteryText(13.6f, 19.3f, FontWeight.SemiBold, -0.1f)
private val ModeStyle = batteryText(12.8f, 17f, FontWeight.Normal, -0.1f)
private val LabelStyle = batteryText(12.6f, 16f, FontWeight.Medium).copy(fontFeatureSettings = "tnum", textAlign = TextAlign.Center)

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

/** 0…1: how far capsule [index] has charged at overall progress [p]. They start one after another. */
private fun chargeAt(p: Float, index: Int) = easeOutCubic((clamp01(p) - DELAY[index]) / SPAN)

/** Share of the capsule's height the fill covers for a charge [v] (0…1), as measured on the reference. */
private fun fillShare(v: Float) = clamp01(v * 20f) * (0.08f + 0.8f * v)

/** Scale of a disc after its capsule lands: a quick overshoot. */
private fun pop(since: Float, ambient: Float): Float {
    val u = clamp01(since / 0.5f)
    return 1f + ambient * 0.22f * sin(PI.toFloat() * u) * (1f - u)
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

private class BatteryPaints(palette: BatteryPalette) {
    val shadow = SoftRoundRect(palette.shadow, sigma = 12f)
    val glow = softPaint(7f)
    val soft = softPaint(3.2f)
    val band = softPaint(5f)
    val capsule = Path()

    // Glyphs, drawn around the centre of their disc.
    val budStem = PathParser().parsePathString("M-0.9 -2L1.5 -2L1.5 6.3A1.2 1.2 0 0 1 -0.9 6.3Z").toPath()
    val caseBody = Path().apply { addRoundRect(RoundRect(Rect(-7f, -5.3f, 7f, 5.3f), CornerRadius(3.4f))) }
    val castTriangle = PathParser().parsePathString("M0 2.6L5.9 8.6L-5.9 8.6Z").toPath()
}

/**
 * Earbuds battery widget, light or dark: the device name and mode over three capsules for the left
 * bud, the case and the right bud. The capsules charge one after another with a soft glowing
 * edge, a band of light keeps climbing through each fill, the low one breathes like a warning
 * light, the icon discs pop as their capsule lands and the arcs of the output glyph ripple.
 *
 * @param progress Controlled share of the charge shown in the reference, 0…1: at 1 the capsules show [levels].
 * @param autoPlay Charges the three capsules one after another up to [levels], holds, and starts over.
 * @param levels Charge of the left bud, the case and the right bud, 0…1.
 */
@Composable
fun BatteryCapsules(
    progress: Float? = null,
    autoPlay: Boolean = false,
    variant: BatteryVariant = BatteryVariant.Light,
    owner: String = "Alex’s",
    device: String = "Buds Pro",
    mode: String = "Noise Cancellation",
    levels: List<Float> = listOf(0.8f, 0.2f, 0.1f),
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val controlled = progress != null
    val target = (progress ?: REST).coerceIn(0f, 1f)
    val auto = autoPlay && !controlled && !reducedMotion
    val looping by rememberUpdatedState(auto)
    val isControlled by rememberUpdatedState(controlled)
    val colors = if (variant == BatteryVariant.Dark) DarkPalette else LightPalette

    // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
    val time = remember { mutableFloatStateOf(0f) }
    val clock = remember { mutableFloatStateOf(if (auto) 0f else REST_T) }
    var playing by remember { mutableStateOf(false) }
    val held = remember { Animatable(target) }
    // When each capsule last reached its level, on the `time` axis.
    val landed = remember { FloatArray(3) { -10f } }

    LaunchedEffect(auto) {
        playing = auto
        clock.floatValue = if (auto) 0f else REST_T
    }

    LaunchedEffect(controlled, target) {
        if (!controlled || held.targetValue == target) return@LaunchedEffect
        held.animateTo(target, tween(if (reducedMotion) 200 else 1400, easing = EaseOutStrong))
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
            // A capsule reaching its level sets off its disc's pop.
            for (index in 0 until 3) {
                if (chargeAt(before, index) < 0.985f && chargeAt(current, index) >= 0.985f) landed[index] = time.floatValue + dt
            }
            before = current
            // Written last: reading it is what redraws the widget each frame.
            time.floatValue += dt
        }
    }

    // Read inside the draw lambda so a frame only redraws, it does not recompose.
    val value = { if (controlled) held.value else loopValue(clock.floatValue) }
    val ambient = if (reducedMotion) 0f else 1f
    val paints = remember(colors) { BatteryPaints(colors) }

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
            .clearAndSetSemantics {
                contentDescription = "$owner $device, $mode"
                stateDescription = "Left, case, right: " + levels.joinToString { "${(it * target * 100).roundToInt()}%" }
                progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    val t = time.floatValue
                    val p = value()

                    drawIntoCanvas { paints.shadow.draw(it.nativeCanvas, x + 8, y + 14, SIZE - 16, SIZE - 10, RADIUS) }
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(colors.cardTop, colors.cardBottom),
                            start = Offset(x, y),
                            end = Offset(x + SIZE, y + SIZE),
                        ),
                        topLeft = Offset(x, y),
                        size = Size(SIZE, SIZE),
                        cornerRadius = CornerRadius(RADIUS),
                    )

                    for (index in 0 until 3) {
                        val level = levels[index]
                        val cx0 = x + CAP_X + CAP_STEP * index
                        val cy0 = y + CAP_Y
                        val tone = colors.fills[index]

                        val charge = level * chargeAt(p, index)
                        val height = CAP_H * fillShare(charge)
                        val top = cy0 + CAP_H - height
                        // A low capsule breathes like a warning light.
                        val breath = if (level <= LOW) 1f - ambient * 0.28f * (0.5f + 0.5f * sin(t * 4.2f)) else 1f
                        val fillOpacity = clamp01(charge * 20f) * breath
                        val glowOpacity = colors.glow * fillOpacity * (0.8f + ambient * 0.2f * sin(t * 2f + index))

                        if (CanBlur && glowOpacity > 0.005f) {
                            val r = min(CAP_R, (height + 2f) / 2f)
                            drawIntoCanvas {
                                it.nativeCanvas.drawRoundRect(
                                    cx0 - 1f, top, cx0 + CAP_W + 1f, top + height + 2f, r, r, paints.glow.tinted(tone, glowOpacity),
                                )
                            }
                        }
                        drawRoundRect(colors.track, Offset(cx0, cy0), Size(CAP_W, CAP_H), CornerRadius(CAP_R))

                        paints.capsule.rewind()
                        paints.capsule.addRoundRect(RoundRect(Rect(cx0, cy0, cx0 + CAP_W, cy0 + CAP_H), CornerRadius(CAP_R)))
                        clipPath(paints.capsule) {
                            if (fillOpacity > 0.005f) {
                                // Blurred so the top edge of the charge is soft, as in the reference.
                                drawIntoCanvas {
                                    it.nativeCanvas.drawRect(
                                        cx0 - 10f, top, cx0 + CAP_W + 10f, top + CAP_H + 12f, paints.soft.tinted(tone, fillOpacity),
                                    )
                                }
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0f to rgba(255, 255, 255, 0f),
                                        0.16f to rgba(255, 255, 255, colors.sheen),
                                        1f to rgba(0, 0, 0, 0.1f),
                                        startY = top,
                                        endY = cy0 + CAP_H,
                                    ),
                                    topLeft = Offset(cx0, top),
                                    size = Size(CAP_W, CAP_H),
                                    alpha = fillOpacity,
                                )
                            }
                            // A band of light climbs through the fill, as if charge were flowing in.
                            val pulse = ((t + index * 0.45f) % 2.6f) / 1.1f
                            if (CanBlur && ambient > 0f && pulse < 1f) {
                                val bandY = cy0 + CAP_H + 6f - (height + 12f) * pulse
                                drawIntoCanvas {
                                    it.nativeCanvas.drawRect(
                                        cx0 - 6f, bandY, cx0 + CAP_W + 6f, bandY + 9f,
                                        paints.band.tinted(Color.White, 0.5f * sin(PI.toFloat() * pulse) * clamp01(height / 26f)),
                                    )
                                }
                            }
                        }

                        val scale = pop(if (ambient > 0f) t - landed[index] else 10f, ambient)
                        withTransform({
                            translate(cx0 + CAP_W / 2f, y + DISC_Y)
                            scale(scale, scale, pivot = Offset.Zero)
                        }) {
                            drawCircle(colors.discs[index], DISC_R, Offset.Zero)
                            if (index == 1) {
                                drawPath(paints.caseBody, colors.glyph, style = Stroke(1.5f, join = StrokeJoin.Round))
                                drawLine(colors.glyph, Offset(-7f, -1.3f), Offset(7f, -1.3f), 1.3f)
                                drawCircle(colors.glyph, 0.95f, Offset(0f, 1.9f))
                            } else {
                                withTransform({ scale(if (index == 0) 1f else -1f, 1f, pivot = Offset.Zero) }) {
                                    drawCircle(colors.glyph, 3.7f, Offset(-1.8f, -3.6f))
                                    drawPath(paints.budStem, colors.glyph)
                                    // The ear tip, with its opening.
                                    drawCircle(colors.glyph, 2.2f, Offset(2.7f, -1.5f))
                                    drawCircle(colors.discs[index], 1f, Offset(2.9f, -1.4f))
                                }
                            }
                        }
                    }

                    // The "playing to" glyph: its arcs light one after another, inside out, like sound
                    // leaving the triangle.
                    withTransform({ translate(x + CAST_X, y + CAST_Y) }) {
                        for (index in 0 until 3) {
                            val r = 3.5f + 2.5f * index
                            val dim = ambient * 0.62f * (0.5f + 0.5f * sin(t * 3.2f - index * 1.25f + 2.4f)).pow(2)
                            drawArc(
                                color = colors.ink,
                                startAngle = 140f,
                                sweepAngle = 260f,
                                useCenter = false,
                                topLeft = Offset(-r, 1.6f - r),
                                size = Size(r * 2, r * 2),
                                alpha = 1f - dim,
                                style = Stroke(1.25f, cap = StrokeCap.Round),
                            )
                        }
                        drawPath(paints.castTriangle, colors.cardTop, style = Stroke(3.4f, join = StrokeJoin.Round))
                        drawPath(paints.castTriangle, colors.ink)
                        drawPath(paints.castTriangle, colors.ink, style = Stroke(1f, join = StrokeJoin.Round))
                    }
                }
            }
        }

        Column(Modifier.offset(13.dp, 10.4.dp).width(112.dp)) {
            BasicText(owner, style = NameStyle.copy(color = colors.ink), maxLines = 1)
            BasicText(device, style = NameStyle.copy(color = colors.ink), maxLines = 1)
        }
        BasicText(mode, Modifier.offset(13.dp, 52.1.dp).width(146.dp), ModeStyle.copy(color = colors.dim), maxLines = 1)
        for (index in 0 until 3) {
            // The percentage above a capsule: counts with its charge.
            val shown by remember(controlled, levels) {
                derivedStateOf {
                    (levels[index] * 100f * chargeAt(if (controlled) held.value else loopValue(clock.floatValue), index)).roundToInt()
                }
            }
            BasicText(
                text = "$shown%",
                style = LabelStyle.copy(color = colors.ink),
                maxLines = 1,
                modifier = Modifier.offset((CAP_X + CAP_STEP * index).dp, 84.2.dp).width(CAP_W.dp),
            )
        }
    }
}
