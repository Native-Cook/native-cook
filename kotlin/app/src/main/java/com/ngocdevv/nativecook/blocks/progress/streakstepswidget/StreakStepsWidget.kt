package com.ngocdevv.nativecook.blocks.progress.streakstepswidget

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
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

private const val SIZE = 312f
private const val RADIUS = 62f

// The canvas overflows the widget by this much on every side to fit its shadow.
private val BLEED = 40.dp

private const val FLAME_X = 54f
private const val FLAME_Y = 56f
private const val FOOT_X = 265.5f
private const val FOOT_Y = 55.5f
private const val DIVIDER_Y = 100.5f
private const val INSET = 30f
private const val DAY_X = 45.8f
private const val DAY_GAP = 38.9f
private const val DAY_Y = 136.5f
private const val DAY_R = 16.3f
private const val RING_R = 15.1f
private const val RING_STROKE = 2.4f
private const val BAR_Y = 272f
private const val BAR_W = SIZE - INSET * 2
private const val BAR_H = 11.5f

// Width of the highlight that travels along the filled bar.
private const val SHIMMER_W = 70f
private const val SHIMMER_PERIOD = 3.2f
private const val EMBERS = 4
private const val EMBER_SPEED = 0.55f
private const val EMBER_RISE = 26f

private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

// The value the reference shows: 6825 of 10,000 steps.
private const val REST = 0.6825f

// The autoPlay run is one clock in seconds; every part below is a window on it.
private const val T_CHECKS = 0.3f
private const val CHECK_STAGGER = 0.2f
private const val CHECK_LENGTH = 0.45f
private val T_FILL = 0.9f to 3.1f
private val T_RESET = 5.7f to 6.2f
private const val TOTAL = 6.6f
private const val HEAT_GAIN = 2.2f

private val Green = Color(0xFF47BD29)
private val Card = Color(0xFF101010)
private val EmptyDay = Color(0xFF1D1D1D)
private val FootColor = Color(0xFFCFCFCF)

private const val FLAME =
    "M3 -22C12 -16 19 -6 19 5C19 15 11 22 0 22C-11 22 -19 15 -19 5C-19 -1 -17 -6 -13 -10C-11 -6 -9 -3 -6 -3C-2 -3 -1 -8 -2 -13C-2.5 -17 0 -21 3 -22Z"
private const val FLAME_CORE =
    "M-1.5 2C0 5 1.5 7 3 7.5C4 7.8 5 7 5.5 6C7 8.5 7.5 11 7.5 13.5C7.5 18 4 21 0 21C-4 21 -7.5 18 -7.5 13.5C-7.5 9 -4 6 -1.5 2Z"
private const val FOOT =
    "M-6.2 -1.5C-6.2 -5.5 -3 -7.3 0.5 -7.3C5 -7.3 7.8 -3.5 7.8 1C7.8 6 5.5 12 1.2 12C-1.5 12 -3 10 -2.6 7.5C-2.3 5.5 -1.6 4.6 -3.2 3.2C-5 1.6 -6.2 0.5 -6.2 -1.5Z"
private val TOES = listOf(
    Triple(-9.5f, -9.1f, 2.75f),
    Triple(-3.3f, -11.8f, 2.1f),
    Triple(3.5f, -11.8f, 2.1f),
    Triple(9f, -9f, 1.4f),
)

private fun svgPath(data: String): Path = PathParser().parsePathString(data).toPath()

private val CenterLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

private fun label(
    color: Color,
    size: Float,
    lineHeight: Float,
    weight: FontWeight = FontWeight.Normal,
    tracking: Float = 0f,
    mono: Boolean = false,
) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
    fontFeatureSettings = "tnum",
    lineHeightStyle = CenterLine,
    platformStyle = NoFontPadding,
)

private val CaptionStyle = label(Color(0xFF6A6A6A), 14f, 18f, FontWeight.Medium, tracking = 0.7f)
private val StreakStyle = label(Color(0xFFDBDBDB), 24f, 29f, FontWeight.Medium)
private val DaysStyle = label(Color(0xFFD6D6D6), 14.5f, 18f, FontWeight.Medium, tracking = 0.3f)
private val DayStyle = label(Color(0xFF8A8A8A), 13f, 18f, mono = true).copy(textAlign = TextAlign.Center)
private val StepsCaptionStyle = CaptionStyle.copy(fontSize = 13.5.sp)
private val StepsStyle = label(Color.White, 22f, 28f, FontWeight.SemiBold)
private val GoalStyle = label(Color(0xFF5C5C5C), 14f, 18f, mono = true)
private val PercentStyle = GoalStyle.copy(textAlign = TextAlign.End)

private fun seg(t: Float, window: Pair<Float, Float>) =
    ((t - window.first) / (window.second - window.first)).coerceIn(0f, 1f)

private fun easeInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

private fun easeOutBack(x: Float): Float {
    val c = 1.7f
    return 1f + (c + 1f) * (x - 1f).pow(3) + c * (x - 1f).pow(2)
}

/** A solid-colour paint with a soft edge. Hardware canvases only honour mask filters from API 28. */
private fun softPaint(color: Color, sigma: Float): Paint? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Paint().apply {
            this.color = color
            asFrameworkPaint().maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        }
    } else {
        null
    }

private class StreakMotion {
    /** Seconds into the autoPlay run. */
    var t by mutableFloatStateOf(0f)

    /** Free-running seconds for the flame and the shimmer. */
    var time by mutableFloatStateOf(0f)

    /** 0…1, how fast the value is moving right now: lights the ring's leading cap. */
    var heat by mutableFloatStateOf(0f)
    val target = Animatable(REST)

    val flame = svgPath(FLAME)
    val flameCore = svgPath(FLAME_CORE)
    val foot = svgPath(FOOT)
    val check = Path().apply {
        moveTo(-7.5f, 0f)
        lineTo(-2.5f, 5f)
        lineTo(7.5f, -5.5f)
    }
    val checkMeasure = PathMeasure().apply { setPath(check, false) }
    val drawnCheck = Path()
    val track = Path()
    val fill = Path()

    val shadow = SoftRoundRect(rgba(0, 0, 0, 0.5f), sigma = 16f)
    val flameGlow = softPaint(Color(0xFFFF3D0D), 12f)
    val barGlow = softPaint(rgba(71, 189, 41, 0.6f), 7f)
    val capGlow = softPaint(rgba(120, 255, 80, 0.7f), 4f)

    /** Share of the goal on show. */
    fun shown(controlled: Boolean, playing: Boolean): Float = when {
        controlled -> target.value
        playing -> REST * easeInOut(seg(t, T_FILL)) * (1f - easeInOut(seg(t, T_RESET)))
        else -> REST
    }
}

/**
 * Dark "streak + steps" widget: a flickering flame with the streak length, the week as a row of
 * ticked discs with today as a ring, and the step count with its percentage over a glowing bar.
 * The count, the percentage, the bar and today's ring all follow [progress].
 *
 * @param progress Share of the step goal reached, 0…1. Leave null to show the reference's 6825 steps.
 * @param autoPlay Plays by itself: the week's checks pop in, the steps count up, hold, and start over.
 * @param completedDays How many days of the week are already ticked; the next one is today and shows a ring.
 */
@Composable
fun StreakStepsWidget(
    progress: Float? = null,
    autoPlay: Boolean = false,
    streakDays: Int = 32,
    goal: Int = 10_000,
    completedDays: Int = 3,
    label: String = "Steps",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    var run by remember { mutableIntStateOf(0) }
    val motion = remember { StreakMotion() }
    val controlled = progress != null
    val playing = autoPlay && !controlled && !still

    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            when (it) {
                is PressInteraction.Press -> {
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
            }
        }
    }

    LaunchedEffect(progress) {
        if (progress != null) {
            motion.target.animateTo(progress.coerceIn(0f, 1f), tween(if (still) 200 else 700, easing = EaseOutStrong))
        }
    }

    LaunchedEffect(still, playing, controlled, run) {
        if (still) return@LaunchedEffect
        val start = withFrameNanos { it }
        var last = start
        var previous = motion.shown(controlled, playing)
        while (true) {
            val now = withFrameNanos { it }
            val dt = min((now - last) / 1e9f, 0.064f)
            last = now
            motion.time += dt
            if (playing) motion.t = ((now - start) / 1e9f) % TOTAL
            // Speed of the value in shares per second, eased so the cap fades in and out.
            val p = motion.shown(controlled, playing)
            val speed = abs(p - previous) / max(dt, 0.001f)
            previous = p
            motion.heat += (min(1f, speed * HEAT_GAIN) - motion.heat) * 0.18f
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    Box(
        modifier = modifier
            .size(SIZE.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            // A tap restarts the demo run.
            .clickable(interactionSource = interaction, indication = null) {
                onClick()
                run++
            }
            .semantics(mergeDescendants = true) {
                contentDescription = "$label, $streakDays day streak"
                progressBarRangeInfo = ProgressBarRangeInfo((progress ?: REST).coerceIn(0f, 1f), 0f..1f)
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    val p = motion.shown(controlled, playing)
                    val time = motion.time

                    drawIntoCanvas { motion.shadow.draw(it.nativeCanvas, x + 6, y + 14, SIZE - 12, SIZE - 12, RADIUS) }
                    drawRoundRect(Card, Offset(x, y), Size(SIZE, SIZE), CornerRadius(RADIUS))
                    // A hairline that catches the light along the top edge.
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to rgba(255, 255, 255, 0.3f),
                            0.3f to rgba(255, 255, 255, 0.035f),
                            0.8f to rgba(255, 255, 255, 0.035f),
                            1f to rgba(255, 255, 255, 0.1f),
                            startY = y,
                            endY = y + SIZE,
                        ),
                        topLeft = Offset(x + 0.5f, y + 0.5f),
                        size = Size(SIZE - 1, SIZE - 1),
                        cornerRadius = CornerRadius(RADIUS - 0.5f),
                        style = Stroke(1f),
                    )

                    drawFlame(motion, x + FLAME_X, y + FLAME_Y, time, still)

                    withTransform({ translate(x + FOOT_X, y + FOOT_Y) }) {
                        drawPath(motion.foot, FootColor, style = Stroke(2.6f, join = StrokeJoin.Round))
                        for ((tx, ty, r) in TOES) drawCircle(FootColor, r, Offset(tx, ty))
                    }

                    drawRect(rgba(255, 255, 255, 0.13f), Offset(x + INSET + 1, y + DIVIDER_Y), Size(BAR_W - 2, 1f))

                    for (index in DAYS.indices) {
                        drawDay(motion, index, completedDays, x, y, p, playing)
                    }
                    drawBar(motion, x + INSET, y + BAR_Y, p, time, still)
                }
            }
        }

        // The widget speaks as one progress bar; its texts stay out of the accessibility tree.
        Box(Modifier.matchParentSize().clearAndSetSemantics {}) {
            BasicText("STREAK", Modifier.offset(88.dp, 32.dp), style = CaptionStyle)
            BasicText(streakDays.toString(), Modifier.offset(88.dp, 50.dp), style = StreakStyle)
            BasicText("DAYS", Modifier.offset(124.5.dp, 58.dp), style = DaysStyle)
            DAYS.forEachIndexed { index, day ->
                BasicText(
                    day,
                    Modifier.offset((DAY_X + DAY_GAP * index - DAY_GAP / 2).dp, 162.5.dp).width(DAY_GAP.dp),
                    style = DayStyle,
                )
            }
            BasicText("STEPS", Modifier.offset(INSET.dp, 217.5.dp), style = StepsCaptionStyle)
            Row(Modifier.offset(INSET.dp, 236.dp)) {
                // Reads the animated value here, so only this number recomposes while it counts.
                StepsCount(motion, controlled, playing, goal, Modifier.alignByBaseline())
                BasicText(
                    "/" + String.format(Locale.US, "%,d", goal),
                    Modifier.alignByBaseline().padding(start = 4.dp),
                    style = GoalStyle,
                )
            }
            Percent(motion, controlled, playing, Modifier.offset((SIZE - INSET - 70f).dp, 244.dp).width(70.dp))
        }
    }
}

@Composable
private fun StepsCount(motion: StreakMotion, controlled: Boolean, playing: Boolean, goal: Int, modifier: Modifier) {
    BasicText((motion.shown(controlled, playing) * goal).roundToInt().toString(), modifier, style = StepsStyle)
}

@Composable
private fun Percent(motion: StreakMotion, controlled: Boolean, playing: Boolean, modifier: Modifier) {
    BasicText("${(motion.shown(controlled, playing) * 100f).roundToInt()}%", modifier, style = PercentStyle)
}

/** The flame leans and stretches from its base; the white core flickers faster; sparks rise. */
private fun DrawScope.drawFlame(motion: StreakMotion, cx: Float, cy: Float, time: Float, still: Boolean) {
    withTransform({ translate(cx, cy) }) {
        motion.flameGlow?.let { glow ->
            glow.alpha = if (still) 0.72f else 0.72f + 0.14f * sin(time * 2.1f)
            withTransform({ scale(1.15f, 1.15f, pivot = Offset.Zero) }) {
                drawIntoCanvas { it.drawPath(motion.flame, glow) }
            }
        }

        val stretch = 0.035f * sin(time * 5.1f) + 0.02f * sin(time * 8.7f + 1f)
        val lean = tan(0.05f * sin(time * 3.3f))
        withTransform({
            // Skew about the base, then stretch: the same order as the React Native transform list.
            translate(0f, 22f)
            transform(androidx.compose.ui.graphics.Matrix().apply { values[4] = lean })
            scale(1f - stretch * 0.5f, 1f + stretch, pivot = Offset.Zero)
            translate(0f, -22f)
        }) {
            drawPath(motion.flame, Color(0xFFFA3D0E))
        }

        val flicker = 0.09f * sin(time * 7.3f + 2f) + 0.05f * sin(time * 11.9f)
        withTransform({ scale(1f - flicker * 0.4f, 1f + flicker, pivot = Offset(0f, 21f)) }) {
            drawPath(motion.flameCore, Color(0xFFFFFAF8))
        }

        if (still) return@withTransform
        for (index in 0 until EMBERS) {
            val life = (time * EMBER_SPEED + index.toFloat() / EMBERS) % 1f
            val side = if (index % 2 == 1) -1f else 1f
            val ex = 2f + side * (3f + index * 1.7f) * life + 2.5f * sin(time * 3f + index * 2f)
            val ey = -16f - EMBER_RISE * life
            drawCircle(
                Color(0xFFFF8A3D),
                radius = (1.5f - 0.15f * index) * (1f - 0.6f * life),
                center = Offset(ex, ey),
                alpha = 0.95f * seg(life, 0f to 0.15f) * (1f - seg(life, 0.45f to 1f)),
            )
        }
    }
}

/** One day of the week: an empty disc, a ticked green one, or today's ring. */
private fun DrawScope.drawDay(
    motion: StreakMotion,
    index: Int,
    completed: Int,
    x: Float,
    y: Float,
    p: Float,
    playing: Boolean,
) {
    val c = Offset(x + DAY_X + DAY_GAP * index, y + DAY_Y)

    if (index == completed) {
        drawCircle(Color(0xFF2E2E2E), RING_R, c, style = Stroke(RING_STROKE))
        val turn = min(1f, p)
        if (turn > 0.001f) {
            drawArc(
                color = Green,
                startAngle = -90f,
                sweepAngle = min(360f * turn, 359.9f),
                useCenter = false,
                topLeft = Offset(c.x - RING_R, c.y - RING_R),
                size = Size(RING_R * 2, RING_R * 2),
                style = Stroke(RING_STROKE, cap = StrokeCap.Round),
            )
        }
        // The ring is drawn by a bright cap that cools down once the value settles.
        val heat = motion.heat
        if (heat > 0.01f) {
            val a = (-90f + 360f * turn) * (Math.PI.toFloat() / 180f)
            val head = Offset(c.x + RING_R * cos(a), c.y + RING_R * sin(a))
            motion.capGlow?.let { glow ->
                glow.alpha = 0.7f * heat
                drawIntoCanvas { it.drawCircle(head, 5f, glow) }
            }
            drawCircle(Color(0xFFEAFFDF), 2f, head, alpha = heat)
        }
        return
    }

    drawCircle(EmptyDay, DAY_R, c)
    if (index > completed) return

    // 0 = empty, 1 = ticked. While playing, the ticks pop in one after another and leave together.
    val begin = T_CHECKS + CHECK_STAGGER * index
    val landing = if (playing) seg(motion.t, begin to begin + CHECK_LENGTH) else 1f
    val on = landing * (if (playing) 1f - seg(motion.t, T_RESET) else 1f)
    // A green ring spreads from the disc as its tick lands.
    if (landing > 0f && landing < 1f) {
        drawCircle(
            Green,
            radius = DAY_R + 9f * (1f - (1f - landing).pow(3)),
            center = c,
            alpha = 0.7f * (1f - landing),
            style = Stroke(1.5f),
        )
    }
    if (on <= 0f) return

    val pop = 0.5f + 0.5f * easeOutBack(on)
    val alpha = min(1f, on * 3f)
    withTransform({ scale(pop, pop, pivot = c) }) {
        // Lighter towards the rim, like a lit bead.
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0xFF46BC2B),
                0.62f to Color(0xFF4FC435),
                1f to Color(0xFFA5F493),
                center = Offset(c.x, c.y - 2f),
                radius = DAY_R + 2f,
            ),
            radius = DAY_R,
            center = c,
            alpha = alpha,
        )
        val drawn = seg(on, 0.35f to 1f)
        if (drawn > 0f) {
            motion.drawnCheck.reset()
            motion.checkMeasure.getSegment(0f, motion.checkMeasure.length * drawn, motion.drawnCheck, true)
            withTransform({ translate(c.x, c.y) }) {
                drawPath(
                    motion.drawnCheck,
                    Color(0xFF0D1A09),
                    alpha = alpha,
                    style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

private fun DrawScope.drawBar(motion: StreakMotion, x: Float, y: Float, p: Float, time: Float, still: Boolean) {
    val r = CornerRadius(BAR_H / 2)
    drawRoundRect(Color(0xFF162015), Offset(x, y), Size(BAR_W, BAR_H), r)

    val fillW = BAR_W * p.coerceIn(0f, 1f)
    if (fillW <= 0.01f) return
    motion.barGlow?.let { glow ->
        glow.alpha = 0.6f * min(1f, fillW / 24f)
        drawIntoCanvas { it.drawRoundRect(x, y, x + fillW, y + BAR_H, BAR_H / 2, BAR_H / 2, glow) }
    }

    // The fill keeps its round end while it grows out of the track's left edge.
    val fillX = x + min(0f, fillW - BAR_H)
    val fillDraw = max(fillW, BAR_H)
    motion.track.reset()
    motion.track.addRoundRect(RoundRect(x, y, x + BAR_W, y + BAR_H, r))
    motion.fill.reset()
    motion.fill.addRoundRect(RoundRect(fillX, y, fillX + fillDraw, y + BAR_H, r))
    clipPath(motion.track) {
        drawPath(motion.fill, Green)
        if (still) return@clipPath
        clipPath(motion.fill) {
            val travel = seg(time % SHIMMER_PERIOD, 0f to 1.5f)
            val sx = x - SHIMMER_W + (fillW + SHIMMER_W) * travel
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to rgba(255, 255, 255, 0f),
                    0.5f to rgba(220, 255, 200, 0.55f),
                    1f to rgba(255, 255, 255, 0f),
                    startX = sx,
                    endX = sx + SHIMMER_W,
                ),
                topLeft = Offset(sx, y),
                size = Size(SHIMMER_W, BAR_H),
            )
        }
    }
}
