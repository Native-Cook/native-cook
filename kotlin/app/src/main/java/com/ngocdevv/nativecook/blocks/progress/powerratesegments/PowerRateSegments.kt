package com.ngocdevv.nativecook.blocks.progress.powerratesegments

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.TAU
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.ClipOp
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val WIDTH = 308f
private const val HEIGHT = 300f
private const val RADIUS = 16f

// The canvas overflows the menu by this much on every side: the light streaks carry on outside it.
// (10dp more than the other platforms, so the canvas still spans a 411-420dp wide Android screen.)
private val BLEED = 56.dp

private const val INSET = 17.5f
private const val ROW_Y = 12f
private const val ROW_H = 48f
private const val DIVIDER_Y = 82f
private const val PANEL_Y = 109f
private const val PANEL_W = WIDTH - INSET * 2
private const val PANEL_H = 115f
private const val PANEL_R = 10f
private const val SETTINGS_Y = 262f
private const val ICON_X = 37f

private const val SEGMENTS = 25
private const val SEG_X = 30f
private const val SEG_Y = 194f
private const val SEG_W = 6f
private const val SEG_H = 17.5f
private const val SEG_PITCH = 9f

// The lit run ends inside a segment, so the leading one shows as a sliver until it is passed.
private const val SEG_LEAD = 0.75f

private const val DIGIT_W = 13.4f
private const val DIGIT_H = 28f

// Value shown in the reference, and the autoPlay loop built around it (seconds).
private const val REST = 0.2431f
private const val START = 0.4f
private const val FILL = 2.2f
private const val HOLD = 3.8f
private const val RESET = 0.6f
private const val LOOP = START + FILL + HOLD + RESET

// A moment in the loop where everything is at rest: used when nothing is playing.
private const val REST_T = START + FILL + 1f
private const val WAVE_PERIOD = 2.6f

// The streaks rise to the right at this angle and fan out from the menu's bottom right corner.
private const val TILT_DEGREES = -22f

/**
 * A light streak in the tilted frame: [a] along the streak from the corner (negative = left),
 * [d] above the corner, half length [l], half thickness [t], then how far and how fast it slides.
 */
private class PowerStreak(
    val a: Float,
    val d: Float,
    val l: Float,
    val t: Float,
    val alpha: Float,
    val amp: Float,
    val speed: Float,
    val phase: Float,
    val mint: Boolean,
)

private val PowerStreaks = listOf(
    PowerStreak(-120f, 150f, 210f, 5f, 0.3f, 30f, 0.23f, 0.0f, false),
    PowerStreak(-60f, 128f, 190f, 2.2f, 0.42f, 40f, 0.31f, 1.3f, true),
    PowerStreak(-150f, 112f, 240f, 7f, 0.34f, 26f, 0.19f, 2.1f, false),
    PowerStreak(-40f, 96f, 170f, 1.6f, 0.55f, 46f, 0.37f, 0.7f, true),
    PowerStreak(-100f, 80f, 230f, 6f, 0.42f, 30f, 0.21f, 3.0f, false),
    PowerStreak(-30f, 66f, 180f, 2.4f, 0.6f, 50f, 0.33f, 4.2f, true),
    PowerStreak(-140f, 52f, 250f, 8f, 0.45f, 24f, 0.17f, 5.0f, false),
    PowerStreak(-50f, 40f, 200f, 1.8f, 0.7f, 44f, 0.41f, 2.6f, true),
    PowerStreak(-90f, 26f, 240f, 7f, 0.5f, 28f, 0.2f, 1.1f, false),
    PowerStreak(-20f, 14f, 190f, 2.2f, 0.75f, 52f, 0.36f, 5.6f, true),
    PowerStreak(-120f, 0f, 260f, 9f, 0.5f, 22f, 0.16f, 3.7f, false),
    PowerStreak(-60f, -14f, 220f, 2f, 0.6f, 40f, 0.3f, 0.3f, true),
    PowerStreak(-100f, -30f, 260f, 8f, 0.4f, 26f, 0.18f, 4.6f, false),
    PowerStreak(-180f, 176f, 200f, 3f, 0.2f, 30f, 0.25f, 2.9f, true),
)

// Motes drifting up the light: height above the corner, radius, speed (laps per second), phase.
private val PowerMotes = arrayOf(
    floatArrayOf(34f, 1.1f, 0.07f, 0.1f),
    floatArrayOf(58f, 0.8f, 0.05f, 0.45f),
    floatArrayOf(12f, 1.3f, 0.06f, 0.7f),
    floatArrayOf(84f, 0.7f, 0.045f, 0.25f),
    floatArrayOf(-8f, 1f, 0.08f, 0.9f),
)

private val PowerGreen = Color(0xFF2FD46D)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

private fun menuText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = NoFontPadding,
)

private val RowStyle = menuText(15.5f, 22f, FontWeight.Normal, rgba(255, 255, 255, 0.56f)).copy(letterSpacing = 0.2.sp)
private val SelectedRowStyle = RowStyle.copy(color = Color.White, fontWeight = FontWeight.Medium)
private val DigitStyle = menuText(22f, DIGIT_H, FontWeight.Medium, Color.White)
    .copy(fontFeatureSettings = "tnum", textAlign = TextAlign.Center)
private val ChangeStyle = menuText(11.5f, 16f, FontWeight.Normal, Color(0xFF3BD583)).copy(fontFeatureSettings = "tnum")
private val LabelStyle = menuText(15f, 20f, FontWeight.Normal, rgba(232, 244, 238, 0.5f)).copy(letterSpacing = 0.2.sp)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

// Long tail, so the last digits of the counter are seen rolling into place.
private fun easeOutQuart(t: Float) = 1f - (1f - t).pow(4)

/** Power rate at [t] seconds into the autoPlay loop. */
private fun loopValue(t: Float) = when {
    t < START -> 0f
    t < START + FILL -> REST * easeOutQuart((t - START) / FILL)
    t < LOOP - RESET -> REST
    else -> REST * (1f - easeInOutCubic((t - (LOOP - RESET)) / RESET))
}

/** Where digit [place] of the counter sits (0…10), rolling over like a mechanical odometer. */
private fun digitPosition(value: Float, place: Int): Float {
    // Hundredths of a percent, snapped so 24.31 does not come out as 24.3099….
    val y = Math.round(value.toDouble() * 1e6) / 100.0 / 10.0.pow(place)
    val base = floor(y)
    val frac = y - base
    // The last wheel turns freely; the others only move while the wheel below passes from 9 to 0.
    val roll = if (place == 0) frac else max(0.0, (frac - 0.9) * 10.0)
    return (base % 10.0 + roll).toFloat()
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

private class PowerRatePaints {
    private fun fade(r: Int, g: Int, b: Int) =
        Brush.radialGradient(listOf(rgba(r, g, b, 1f), rgba(r, g, b, 0f)), center = Offset.Zero, radius = 1f)

    val mint = fade(150, 255, 205)
    val green = fade(26, 224, 116)
    val wash = Brush.radialGradient(
        listOf(rgba(22, 214, 110, 0.85f), rgba(22, 214, 110, 0.34f), rgba(22, 214, 110, 0f)),
        center = Offset.Zero,
        radius = 1f,
    )
    val core = Brush.radialGradient(
        listOf(rgba(44, 246, 172, 0.95f), rgba(44, 246, 172, 0.32f), rgba(44, 246, 172, 0f)),
        center = Offset.Zero,
        radius = 1f,
    )

    // Menu glass: open at the top, where the rest of the menu would continue.
    val menu = Path().apply {
        moveTo(0f, 0f)
        lineTo(0f, HEIGHT - RADIUS)
        arcTo(Rect(0f, HEIGHT - RADIUS * 2, RADIUS * 2, HEIGHT), 180f, -90f, false)
        lineTo(WIDTH - RADIUS, HEIGHT)
        arcTo(Rect(WIDTH - RADIUS * 2, HEIGHT - RADIUS * 2, WIDTH, HEIGHT), 90f, -90f, false)
        lineTo(WIDTH, 0f)
        close()
    }
    val edge = Path().apply {
        val r = RADIUS - 0.5f
        moveTo(0.5f, 0f)
        lineTo(0.5f, HEIGHT - RADIUS)
        arcTo(Rect(0.5f, HEIGHT - RADIUS - r, 0.5f + r * 2, HEIGHT - 0.5f), 180f, -90f, false)
        lineTo(WIDTH - RADIUS, HEIGHT - 0.5f)
        arcTo(Rect(WIDTH - 0.5f - r * 2, HEIGHT - RADIUS - r, WIDTH - 0.5f, HEIGHT - 0.5f), 90f, -90f, false)
        lineTo(WIDTH - 0.5f, 0f)
    }

    // Plug in a ring, a cog and the pins of a generic chip, each drawn around its own centre.
    val plug = Path().apply {
        moveTo(-2.2f, -4.6f)
        lineTo(-2.2f, -2f)
        moveTo(2.2f, -4.6f)
        lineTo(2.2f, -2f)
        moveTo(-3.7f, -2f)
        lineTo(3.7f, -2f)
        lineTo(3.7f, 0.2f)
        arcTo(Rect(-3.7f, -3.5f, 3.7f, 3.9f), 0f, 180f, false)
        close()
        moveTo(0f, 3.9f)
        lineTo(0f, 6.2f)
    }
    val cog = Path().apply {
        val teeth = 8
        val step = TAU / teeth
        for (i in 0 until teeth) {
            val a = i * step
            val angles = floatArrayOf(a - step * 0.14f, a + step * 0.14f, a + step * 0.3f, a + step * 0.7f)
            val radii = floatArrayOf(8.2f, 8.2f, 6.2f, 6.2f)
            for (k in angles.indices) {
                val x = radii[k] * cos(angles[k])
                val y = radii[k] * sin(angles[k])
                if (i == 0 && k == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        close()
    }
    val pins = Path().apply {
        for (side in floatArrayOf(-1f, 1f)) {
            for (y in floatArrayOf(-4f, 0f, 4f)) {
                moveTo(side * 10f, y)
                lineTo(side * 7f, y)
            }
        }
    }

    val dim = Path().apply {
        for (i in 0 until SEGMENTS) {
            val x = SEG_X + i * SEG_PITCH
            addRoundRect(RoundRect(x, SEG_Y, x + SEG_W, SEG_Y + SEG_H, CornerRadius(1.6f)))
        }
    }
    val lit = Path()
    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color(0xFF2FF48A).toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(3.6f), BlurMaskFilter.Blur.NORMAL)
        }
    }
}

/** A soft-edged ellipse: a radial fade squashed to the ellipse's proportions. */
private fun DrawScope.softEllipse(brush: Brush, cx: Float, cy: Float, rx: Float, ry: Float, alpha: Float) {
    withTransform({
        translate(cx, cy)
        scale(rx, ry, pivot = Offset.Zero)
    }) {
        drawCircle(brush, radius = 1f, center = Offset.Zero, alpha = alpha.coerceIn(0f, 1f))
    }
}

/** One wheel of the counter. Hundreds and tens leave the row while they would only show a leading zero. */
@Composable
private fun PowerDigit(place: Int, value: () -> Float, optional: Boolean = false) {
    val shown = {
        if (optional) {
            val unit = 10f.pow(place - 2)
            clamp01((value() * 100f - unit * 0.96f) / (unit * 0.04f))
        } else {
            1f
        }
    }
    Column(
        modifier = Modifier
            .graphicsLayer { alpha = shown() }
            .clipToBounds()
            .layout { measurable, _ ->
                val cellWidth = DIGIT_W.dp.roundToPx()
                val cellHeight = DIGIT_H.dp.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(cellWidth, cellHeight * 11))
                layout((cellWidth * shown()).roundToInt(), cellHeight) { placeable.place(0, 0) }
            }
            .graphicsLayer { translationY = -DIGIT_H.dp.toPx() * digitPosition(clamp01(value()), place) },
    ) {
        for (i in 0..10) {
            Box(Modifier.size(DIGIT_W.dp, DIGIT_H.dp), contentAlignment = Alignment.Center) {
                BasicText(text = (i % 10).toString(), style = DigitStyle)
            }
        }
    }
}

/**
 * Dark glass settings menu with a glowing green panel: a power rate counter, a change chip and a
 * row of small segments, over streaks of green light. The digits roll like an odometer, the
 * segments light one after another with a flickering glow and a pulse that runs along them, and
 * the light streaks slide, swell with the rate and carry a few drifting motes.
 *
 * @param progress Controlled power rate, 0…1. Leave null to show the reference's 24.31% (or the autoPlay loop).
 * @param autoPlay Ramps from 0 to 24.31% by itself, holds, drops and starts over.
 * @param change Text of the change chip.
 */
@Composable
fun PowerRateSegments(
    progress: Float? = null,
    autoPlay: Boolean = false,
    label: String = "GPU Actual Power Rate",
    change: String = "-14.83%",
    selectedRow: String = "Power Usage",
    settingsRow: String = "Settings",
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val controlled = progress != null
    val target = (progress ?: REST).coerceIn(0f, 1f)
    val auto = autoPlay && !controlled && !reducedMotion
    val looping by rememberUpdatedState(auto)

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

    // Read inside draw / layout / layer lambdas so a frame only redraws, it does not recompose.
    val value = { if (controlled) held.value else loopValue(clock.floatValue) }
    val ambient = if (reducedMotion) 0f else 1f
    val paints = remember { PowerRatePaints() }

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
        // Clipped like the Skia canvas: past it the light would escape the dimming drawn over it.
        Box(Modifier.matchParentSize().bleed(BLEED).clipToBounds()) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    val p = value()
                    val t = time.floatValue
                    val bleed = BLEED.value
                    translate(x, y) {
                        // Light fanning out from the bottom right corner: it swells with the
                        // power rate and breathes a little.
                        val gain = 0.5f + 0.42f * clamp01(p / REST) + ambient * 0.08f * sin(t * 1.3f)
                        withTransform({
                            translate(WIDTH, HEIGHT)
                            rotate(TILT_DEGREES, pivot = Offset.Zero)
                        }) {
                            softEllipse(paints.wash, -50f, -24f, 330f, 118f, gain)
                            softEllipse(paints.core, 10f, 0f, 190f, 74f, gain)
                            for (s in PowerStreaks) {
                                val alpha = min(1f, s.alpha * 1.3f) *
                                    (0.72f + ambient * 0.28f * sin(t * s.speed * 5.3f + s.phase * 1.7f))
                                softEllipse(
                                    brush = if (s.mint) paints.mint else paints.green,
                                    cx = s.a + ambient * s.amp * sin(t * s.speed * 2f + s.phase),
                                    cy = -s.d,
                                    rx = s.l,
                                    ry = s.t,
                                    alpha = alpha * gain,
                                )
                            }
                            if (ambient > 0f) {
                                PowerMotes.forEachIndexed { index, m ->
                                    val lap = (t * m[2] + m[3]) % 1f
                                    val alpha = sin(PI.toFloat() * lap) * (0.55f + 0.45f * sin(t * 3.1f + index * 1.9f))
                                    drawCircle(
                                        color = Color(0xFFC4FFE4),
                                        radius = m[1],
                                        center = Offset(-300f + 330f * lap, -m[0]),
                                        alpha = (alpha * gain).coerceIn(0f, 1f),
                                    )
                                }
                            }
                        }

                        // Outside the glass the light is much dimmer.
                        clipPath(paints.menu, clipOp = ClipOp.Difference) {
                            drawRect(
                                color = rgba(2, 5, 4, 0.74f),
                                topLeft = Offset(-bleed, -bleed),
                                size = Size(WIDTH + bleed * 2, HEIGHT + bleed * 2),
                            )
                        }
                        drawPath(
                            paints.menu,
                            Brush.verticalGradient(
                                0f to rgba(13, 15, 14, 0f),
                                0.07f to rgba(13, 15, 14, 0.5f),
                                1f to rgba(13, 15, 14, 0.14f),
                                startY = 0f,
                                endY = HEIGHT,
                            ),
                        )
                        drawPath(
                            paints.edge,
                            Brush.linearGradient(
                                0f to rgba(255, 255, 255, 0f),
                                0.12f to rgba(255, 255, 255, 0.11f),
                                0.6f to rgba(190, 255, 225, 0.2f),
                                1f to rgba(150, 255, 210, 0.75f),
                                start = Offset.Zero,
                                end = Offset(WIDTH, HEIGHT),
                            ),
                            style = Stroke(1f),
                        )

                        // Selected row and divider.
                        drawRoundRect(rgba(255, 255, 255, 0.055f), Offset(INSET, ROW_Y), Size(PANEL_W, ROW_H), CornerRadius(11f))
                        drawRect(rgba(255, 255, 255, 0.1f), Offset(INSET, DIVIDER_Y), Size(PANEL_W, 1f))
                        translate(ICON_X, ROW_Y + ROW_H / 2f) {
                            drawCircle(PowerGreen, radius = 8.6f, center = Offset.Zero, style = Stroke(1.5f))
                            drawPath(paints.plug, PowerGreen, style = Stroke(1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        translate(ICON_X, SETTINGS_Y) {
                            val ink = rgba(255, 255, 255, 0.62f)
                            drawPath(paints.cog, ink, style = Stroke(1.5f, join = StrokeJoin.Round))
                            drawCircle(ink, radius = 2.6f, center = Offset.Zero, style = Stroke(1.5f))
                        }

                        // Power panel: dark glass on the left, lit green on the right.
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                0f to rgba(27, 33, 31, 0.82f),
                                0.45f to rgba(24, 52, 39, 0.55f),
                                1f to rgba(40, 170, 105, 0.2f),
                                start = Offset(INSET, PANEL_Y),
                                end = Offset(INSET + PANEL_W, PANEL_Y + PANEL_H * 0.6f),
                            ),
                            topLeft = Offset(INSET, PANEL_Y),
                            size = Size(PANEL_W, PANEL_H),
                            cornerRadius = CornerRadius(PANEL_R),
                        )
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                0f to rgba(255, 255, 255, 0.14f),
                                0.55f to rgba(170, 255, 215, 0.22f),
                                1f to rgba(150, 255, 205, 0.7f),
                                start = Offset(INSET, PANEL_Y),
                                end = Offset(INSET + PANEL_W, PANEL_Y + PANEL_H),
                            ),
                            topLeft = Offset(INSET + 0.5f, PANEL_Y + 0.5f),
                            size = Size(PANEL_W - 1f, PANEL_H - 1f),
                            cornerRadius = CornerRadius(PANEL_R - 0.5f),
                            style = Stroke(1f),
                        )

                        // Chip glyph.
                        translate(41f, PANEL_Y + 28.5f) {
                            drawPath(paints.pins, PowerGreen, style = Stroke(1.5f, cap = StrokeCap.Round))
                            drawRoundRect(PowerGreen, Offset(-7f, -7f), Size(14f, 14f), CornerRadius(3f))
                            drawRoundRect(Color(0xFF143524), Offset(-2.8f, -2.8f), Size(5.6f, 5.6f), CornerRadius(1.2f))
                        }

                        // Change chip with its cursor bar.
                        drawRect(rgba(52, 214, 122, 0.16f), Offset(213f, PANEL_Y + 13.5f), Size(61f, 16f))
                        drawRect(
                            color = Color(0xFF3FE087),
                            topLeft = Offset(276.4f, PANEL_Y + 13.5f),
                            size = Size(1.5f, 16f),
                            alpha = 0.65f + ambient * 0.35f * sin(t * 4.2f),
                        )

                        // Segments: the dim row fades out to the right, the lit run glows.
                        val rowEnd = SEG_X + SEGMENTS * SEG_PITCH
                        drawPath(
                            paints.dim,
                            Brush.horizontalGradient(
                                0f to rgba(70, 190, 120, 0.4f),
                                0.5f to rgba(70, 190, 120, 0.2f),
                                0.95f to rgba(70, 190, 120, 0f),
                                startX = SEG_X,
                                endX = rowEnd,
                            ),
                        )
                        val litEdge = SEG_X + max(0f, clamp01(p) * SEGMENTS - SEG_LEAD) * SEG_PITCH
                        paints.lit.rewind()
                        for (i in 0 until SEGMENTS) {
                            val sx = SEG_X + i * SEG_PITCH
                            val shown = min(SEG_W, litEdge - sx)
                            if (shown <= 0f) break
                            paints.lit.addRoundRect(
                                RoundRect(sx, SEG_Y, sx + shown, SEG_Y + SEG_H, CornerRadius(min(1.6f, shown / 2f))),
                            )
                        }
                        // Mains flicker on the lit run, and a pulse that runs along it every few seconds.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val flicker = 0.82f + ambient * 0.18f * sin(t * 21f) * sin(t * 6.7f)
                            paints.glow.alpha = (255f * flicker).roundToInt().coerceIn(0, 255)
                            drawIntoCanvas { it.nativeCanvas.drawPath(paints.lit.asAndroidPath(), paints.glow) }
                        }
                        drawPath(
                            paints.lit,
                            Brush.verticalGradient(
                                listOf(Color(0xFF7DFFB6), Color(0xFFE2FFEF), Color(0xFF7DFFB6)),
                                startY = SEG_Y,
                                endY = SEG_Y + SEG_H,
                            ),
                        )
                        val phase = (t % WAVE_PERIOD) / 0.9f
                        if (ambient > 0f && phase < 1f) {
                            val wx = SEG_X - 30f + (SEGMENTS * SEG_PITCH + 30f) * phase
                            drawPath(
                                paints.lit,
                                Brush.horizontalGradient(
                                    0f to rgba(255, 255, 255, 0f),
                                    0.5f to rgba(255, 255, 255, 0.95f),
                                    1f to rgba(255, 255, 255, 0f),
                                    startX = wx,
                                    endX = wx + 30f,
                                ),
                            )
                        }
                    }
                }
            }
        }

        BasicText(
            text = selectedRow,
            style = SelectedRowStyle,
            maxLines = 1,
            modifier = Modifier.leadingAt(57.dp, (ROW_Y + ROW_H / 2f).dp),
        )
        BasicText(
            text = settingsRow,
            style = RowStyle,
            maxLines = 1,
            modifier = Modifier.leadingAt(57.dp, SETTINGS_Y.dp),
        )

        Row(
            modifier = Modifier
                .leadingAt(61.dp, (PANEL_Y + 28.5f).dp)
                .clearAndSetSemantics {
                    contentDescription = label
                    stateDescription = String.format(Locale.US, "%.2f%%", target * 100f)
                    progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PowerDigit(4, value, optional = true)
            PowerDigit(3, value, optional = true)
            PowerDigit(2, value)
            BasicText(text = ".", style = DigitStyle)
            PowerDigit(1, value)
            PowerDigit(0, value)
            BasicText(text = "%", style = DigitStyle)
        }
        BasicText(
            text = change,
            style = ChangeStyle,
            maxLines = 1,
            modifier = Modifier.centerAt(243.5.dp, (PANEL_Y + 21.5f).dp),
        )
        BasicText(
            text = label,
            style = LabelStyle,
            maxLines = 1,
            modifier = Modifier.leadingAt(30.5.dp, (PANEL_Y + 62.5f).dp),
        )
    }
}
