package com.ngocdevv.nativecook.blocks.progress.milestonestepper

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.TAU
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private const val WIDTH = 330f
private const val HEIGHT = 254f
private const val RADIUS = 16f

// The canvas overflows the card by this much on every side to fit its shadow.
private val BLEED = 44.dp

private const val BUBBLE_X = 19.7f
private const val BUBBLE_Y = 56f
private const val BUBBLE_W = 289.5f
private const val BUBBLE_H = 63.3f
private const val BUBBLE_R = 13f
private const val TRACK_X = 21.3f
private const val TRACK_Y = 140f
private const val TRACK_W = 288f
private const val TRACK_H = 33f
private const val DOT_Y = TRACK_Y + TRACK_H / 2
private const val DOT_R = 9.6f

// Distance of the first and last milestone from the pill's ends.
private const val DOT_INSET = 19f

// How far past a milestone's centre the fill reaches once it is done: up to the next circle.
private const val FILL_LEAD = 57f
private const val TIP_X = 70.8f
private const val TIP_Y = 214.4f
private const val TIP_W = 186.7f
private const val TIP_H = 22.3f
private const val SPARKS = 7

// The reference rests with three of five milestones reached.
private const val REST_DONE = 3

// The autoPlay run is one clock in seconds: one spring per milestone, a hold, a drain.
private const val STEP_FIRST = 0.5f
private const val STEP_EVERY = 0.95f
private const val STEP_LENGTH = 0.6f
private const val HOLD = 2.6f
private const val RESET = 0.5f
private const val TAIL = 0.4f

private val Green = Color(0xFF2FAA77)
private val Ink = Color(0xFF111111)
private val Muted = Color(0xFF8D8D92)

private val CenterLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

private fun label(
    color: Color,
    size: Float,
    lineHeight: Float,
    weight: FontWeight = FontWeight.Normal,
    tracking: Float = 0f,
) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    fontFeatureSettings = "tnum",
    lineHeightStyle = CenterLine,
    platformStyle = NoFontPadding,
)

private val TitleStyle = label(Color(0xFF0B0B0B), 22f, 28f, FontWeight.ExtraBold, tracking = -0.6f)
private val StrongStyle = label(Ink, 10.5f, 14f, FontWeight.SemiBold)
private val StretchStyle = label(Ink, 9.3f, 14f, FontWeight.SemiBold, tracking = -0.3f)
private val PlaceholderStyle = label(Color(0xFF8E9096), 10f, 14f)
private val NumberStyle = label(Muted, 7.5f, 10f)
private val NumberOnStyle = label(Color(0xFF2A9D6E), 7.5f, 10f, FontWeight.Bold)
private val NameStyle = label(Muted, 8.5f, 12f, tracking = -0.1f)
private val NameOnStyle = label(Color(0xFF0D0D0D), 8.5f, 12f, FontWeight.Bold, tracking = -0.1f)
private val TipStyle = label(Color(0xFF222222), 8f, 12f, FontWeight.Medium, tracking = -0.3f)

private fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun easeOut(x: Float) = 1f - (1f - x).pow(3)

private fun easeInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

private fun easeOutBack(x: Float): Float {
    val c = 1.7f
    return 1f + (c + 1f) * (x - 1f).pow(3) + c * (x - 1f).pow(2)
}

/** A colour three stops along a milestone's age: newest, the one before, older. */
private fun aged(age: Float, newest: Color, before: Color, older: Color) =
    if (age <= 2f) lerp(newest, before, seg(age, 1.2f, 2f)) else lerp(before, older, seg(age, 2f, 3f))

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

private fun polyline(vararg points: Float) = Path().apply {
    moveTo(points[0], points[1])
    for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
}

private class MilestoneMotion {
    /** Seconds into the autoPlay run. */
    var t by mutableFloatStateOf(0f)

    /** Free-running seconds for the breathing haze and the floating bubble. */
    var time by mutableFloatStateOf(0f)

    /** Milestones reached while controlled. */
    val target = Animatable(0f)

    val shadow = SoftRoundRect(rgba(86, 103, 119, 0.5f), sigma = 16f)
    val bubbleShadow = SoftRoundRect(rgba(60, 70, 90, 0.16f), sigma = 9f)
    val dotShade = softPaint(rgba(0, 40, 20, 0.25f), 2.5f)

    val track = Path().apply {
        addRoundRect(RoundRect(TRACK_X, TRACK_Y, TRACK_X + TRACK_W, TRACK_Y + TRACK_H, CornerRadius(TRACK_H / 2)))
    }
    val tail = Path().apply {
        moveTo(BUBBLE_X + 19f, BUBBLE_Y + BUBBLE_H - 1f)
        lineTo(BUBBLE_X + 41.5f, BUBBLE_Y + BUBBLE_H - 1f)
        lineTo(BUBBLE_X + 31.5f, BUBBLE_Y + BUBBLE_H + 7.4f)
        quadraticTo(BUBBLE_X + 29.8f, BUBBLE_Y + BUBBLE_H + 8.8f, BUBBLE_X + 28.1f, BUBBLE_Y + BUBBLE_H + 7.4f)
        close()
    }
    val check = polyline(-4f, 0.3f, -1.3f, 3f, 4.2f, -2.8f)
    val plus = Path().apply {
        moveTo(-3.6f, 0f)
        lineTo(3.6f, 0f)
        moveTo(0f, -3.6f)
        lineTo(0f, 3.6f)
    }
    val checkMeasure = PathMeasure().apply { setPath(check, false) }
    val drawnCheck = Path()
    val smallCheck = polyline(-2.7f, 0.2f, -0.9f, 2f, 2.9f, -1.9f)
    val waveTop = polyline(-4.4f, -2.8f, -1.2f, -0.8f, 4.2f, -3.2f)
    val waveBottom = polyline(-4.4f, 1.4f, -1.2f, 3.4f, 4.2f, 1f)

    fun drainAt(rest: Int) = STEP_FIRST + (rest - 1) * STEP_EVERY + STEP_LENGTH + HOLD

    /** Milestones reached, a fraction while the fill is between two. */
    fun done(controlled: Boolean, playing: Boolean, rest: Int): Float {
        if (controlled) return target.value
        if (!playing) return rest.toFloat()
        var d = 0f
        for (k in 0 until rest) {
            val begin = STEP_FIRST + k * STEP_EVERY
            d += easeOutBack(seg(t, begin, begin + STEP_LENGTH))
        }
        val drain = drainAt(rest)
        return d * (1f - easeInOut(seg(t, drain, drain + RESET)))
    }
}

/**
 * Light "You're almost there!" card: a funding-goal speech bubble over a pill-shaped stepper whose
 * dark-to-mint fill springs from milestone to milestone. Each milestone pops with a ring and a few
 * sparks when the fill lands on it, then ages from mint to green to white as the fill moves on.
 *
 * @param progress Share of the milestones reached, 0…1. Leave null to show the reference's 3 of 5.
 * @param autoPlay Plays by itself: the fill springs from milestone to milestone up to 3 of 5, holds, and drains.
 */
@Composable
fun MilestoneStepper(
    progress: Float? = null,
    autoPlay: Boolean = false,
    title: String = "You’re almost there!",
    steps: List<String> = listOf("Create Project", "Add Media", "Set Goals", "Team", "Launch"),
    tip: String = "Tip: Use round numbers to attract backers.",
    label: String = "Project setup",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    var run by remember { mutableIntStateOf(0) }
    val motion = remember { MilestoneMotion() }
    val count = steps.size
    val controlled = progress != null
    val playing = autoPlay && !controlled && !still
    val rest = min(REST_DONE, count)
    val pitch = (TRACK_W - DOT_INSET * 2) / max(count - 1, 1)

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

    var primed by remember { mutableIntStateOf(0) }
    LaunchedEffect(progress, count) {
        if (progress == null) return@LaunchedEffect
        val next = progress.coerceIn(0f, 1f) * count
        // The first value is shown without a run-up.
        if (primed++ == 0) motion.target.snapTo(next) else motion.target.animateTo(next, tween(if (still) 200 else 700, easing = EaseOutStrong))
    }

    LaunchedEffect(still, playing, run, rest) {
        if (still) return@LaunchedEffect
        val start = withFrameNanos { it }
        val loop = motion.drainAt(rest) + RESET + TAIL
        var last = start
        while (true) {
            val now = withFrameNanos { it }
            motion.time += min((now - last) / 1e9f, 0.064f)
            last = now
            if (playing) motion.t = ((now - start) / 1e9f) % loop
        }
    }

    // Whole milestones the fill has covered: switches the labels.
    val reached by remember(controlled, playing, rest, count) {
        derivedStateOf { (motion.done(controlled, playing, rest) + 0.75f).toInt().coerceIn(0, count) }
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    Box(
        modifier = modifier
            .size(WIDTH.dp, HEIGHT.dp)
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
                contentDescription = label
                stateDescription = "$reached of $count steps done"
                progressBarRangeInfo = ProgressBarRangeInfo((progress ?: (rest.toFloat() / max(count, 1))).coerceIn(0f, 1f), 0f..1f)
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    val done = motion.done(controlled, playing, rest)
                    // The reference's heavy slate shadow, thrown down and to the right.
                    drawIntoCanvas { motion.shadow.draw(it.nativeCanvas, x + 14, y + 22, WIDTH - 6, HEIGHT - 8, RADIUS + 4) }
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFFF7F6F7), Color(0xFFEFEEF0), Color(0xFFE9E8EB)),
                            start = Offset(x, y),
                            end = Offset(x + WIDTH, y + HEIGHT),
                        ),
                        topLeft = Offset(x, y),
                        size = Size(WIDTH, HEIGHT),
                        cornerRadius = CornerRadius(RADIUS),
                    )
                    drawRoundRect(
                        rgba(255, 255, 255, 0.95f), Offset(x + 0.6f, y + 0.6f), Size(WIDTH - 1.2f, HEIGHT - 1.2f),
                        CornerRadius(RADIUS - 0.6f), style = Stroke(1.2f),
                    )

                    withTransform({ translate(x, y) }) {
                        withTransform({ translate(0f, if (still) 0f else 1.4f * sin(motion.time * 1.7f)) }) {
                            drawBubble(motion)
                        }
                        drawTrack(motion, done, count, pitch, still)
                        for (index in 0 until count) drawMilestone(motion, index, count, pitch, done)

                        drawRoundRect(Color(0xFFF0F0F1), Offset(TIP_X, TIP_Y), Size(TIP_W, TIP_H), CornerRadius(TIP_H / 2))
                        drawRoundRect(
                            Color(0xFFDCDCDF), Offset(TIP_X + 0.4f, TIP_Y + 0.4f), Size(TIP_W - 0.8f, TIP_H - 0.8f),
                            CornerRadius(TIP_H / 2), style = Stroke(0.8f),
                        )
                        val plus = Offset(TIP_X + 12.5f, TIP_Y + TIP_H / 2)
                        drawLine(Green, plus + Offset(-3f, 0f), plus + Offset(3f, 0f), 1.9f, StrokeCap.Round)
                        drawLine(Green, plus + Offset(0f, -3f), plus + Offset(0f, 3f), 1.9f, StrokeCap.Round)
                    }
                }
            }
        }

        // The card speaks as one progress bar; its texts stay out of the accessibility tree.
        Box(Modifier.matchParentSize().clearAndSetSemantics {}) {
            BasicText(title, Modifier.offset(20.dp, 16.dp), style = TitleStyle, maxLines = 1)
            Box(
                Modifier.matchParentSize().graphicsLayer {
                    translationY = (if (still) 0f else 1.4f * sin(motion.time * 1.7f)) * density
                },
            ) {
                BasicText("Funding goal", Modifier.offset(29.3.dp, 65.4.dp), style = StrongStyle)
                BasicText("Funding target", Modifier.offset(50.5.dp, 93.dp), style = PlaceholderStyle)
                BasicText("Stretch goals.", Modifier.offset(219.5.dp, 93.dp), style = StretchStyle)
            }

            steps.forEachIndexed { index, step ->
                val on = index < reached
                val selected = index == 0 && on
                // The first and last labels are nudged inwards so they stay inside the card.
                val center = (TRACK_X + DOT_INSET + pitch * index).coerceIn(42f, WIDTH - 38f)
                Column(
                    Modifier.offset((center - 40f).dp, 175.5.dp).width(80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(11.dp).background(if (selected) Green else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            "${index + 1}",
                            style = if (selected) NumberOnStyle.copy(color = Color.White) else if (on) NumberOnStyle else NumberStyle,
                        )
                    }
                    BasicText(
                        if (index == reached - 1) "($step)" else step,
                        Modifier.padding(top = 0.5.dp),
                        style = if (on) NameOnStyle else NameStyle,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            BasicText(tip, Modifier.offset((TIP_X + 20f).dp, (TIP_Y + TIP_H / 2 - 6f).dp), style = TipStyle, maxLines = 1)
        }
    }
}

/** The funding-goal speech bubble. */
private fun DrawScope.drawBubble(motion: MilestoneMotion) {
    drawIntoCanvas { motion.bubbleShadow.draw(it.nativeCanvas, BUBBLE_X, BUBBLE_Y + 5, BUBBLE_W, BUBBLE_H, BUBBLE_R) }
    drawPath(motion.tail, Color.White)
    drawRoundRect(Color.White, Offset(BUBBLE_X, BUBBLE_Y), Size(BUBBLE_W, BUBBLE_H), CornerRadius(BUBBLE_R))
    drawRoundRect(Color(0xFFF1F2F4), Offset(28.2f, 87.2f), Size(165f, 25.6f), CornerRadius(12.8f))

    val round = Stroke(1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    withTransform({ translate(40f, 100f) }) {
        drawCircle(Green, 6.2f, Offset.Zero)
        drawPath(motion.smallCheck, Color.White, style = round)
    }
    withTransform({ translate(208f, 100f) }) {
        drawCircle(Green, 6.2f, Offset.Zero)
        drawLine(Color.White, Offset(-2.3f, -2.3f), Offset(2.3f, 2.3f), 1.3f, StrokeCap.Round)
        drawLine(Color.White, Offset(2.3f, -2.3f), Offset(-2.3f, 2.3f), 1.3f, StrokeCap.Round)
    }
    withTransform({ translate(292f, 72.4f) }) {
        drawPath(motion.waveTop, Ink, style = round)
        drawPath(motion.waveBottom, Ink, style = round)
    }
    withTransform({ translate(291.5f, 100.5f) }) {
        val grey = Color(0xFF7C7C82)
        drawRoundRect(grey, Offset(-4.4f, -3.8f), Size(8.8f, 8.2f), CornerRadius(1.6f), style = Stroke(1f))
        drawLine(grey, Offset(0.6f, -5.6f), Offset(0.6f, -3.8f), 1f, StrokeCap.Round)
        drawLine(grey, Offset(-1.6f, 1.6f), Offset(1.2f, -1f), 1f, StrokeCap.Round)
    }
}

/** The pill, its dark-to-mint fill and the haze that runs ahead of it. */
private fun DrawScope.drawTrack(motion: MilestoneMotion, done: Float, count: Int, pitch: Float, still: Boolean) {
    val r = CornerRadius(TRACK_H / 2)
    drawRoundRect(Color(0xFFE9E9EC), Offset(TRACK_X, TRACK_Y), Size(TRACK_W, TRACK_H), r)

    // The fill ends just short of the next milestone, and takes the whole pill at the last one.
    val full = seg(done, count - 1f, count.toFloat())
    val end = min(
        TRACK_W,
        DOT_INSET + pitch * (done - 1f) + FILL_LEAD + (TRACK_W - DOT_INSET - pitch * (count - 2) - FILL_LEAD) * full,
    )
    val shown = seg(done, 0.05f, 0.35f)
    if (shown > 0f) {
        clipPath(motion.track) {
            // A mint haze runs ahead of the fill and breathes.
            val hazeX = TRACK_X + end - TRACK_H / 2
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to rgba(125, 228, 189, 0.85f),
                    0.5f to rgba(150, 235, 205, 0.4f),
                    1f to rgba(170, 240, 215, 0f),
                    startX = hazeX,
                    endX = TRACK_X + end + 58f,
                ),
                topLeft = Offset(hazeX, TRACK_Y),
                size = Size(76f, TRACK_H),
                alpha = shown * (if (still) 0.8f else 0.72f + 0.2f * sin(motion.time * 2.2f)),
            )
            val fillW = max(TRACK_H, end)
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    0f to Color(0xFF03170C),
                    0.25f to Color(0xFF06281A),
                    0.6f to Color(0xFF1D885C),
                    0.9f to Color(0xFF5EDCAC),
                    1f to Color(0xFF7DE4BD),
                    startX = TRACK_X,
                    endX = TRACK_X + fillW,
                ),
                topLeft = Offset(TRACK_X, TRACK_Y),
                size = Size(fillW, TRACK_H),
                cornerRadius = r,
                alpha = shown,
            )
        }
    }
    drawRoundRect(
        rgba(255, 255, 255, 0.7f), Offset(TRACK_X + 0.5f, TRACK_Y + 0.5f), Size(TRACK_W - 1, TRACK_H - 1), r,
        style = Stroke(1f),
    )
    drawRoundRect(
        rgba(120, 125, 135, 0.22f), Offset(TRACK_X - 0.3f, TRACK_Y - 0.3f), Size(TRACK_W + 0.6f, TRACK_H + 0.6f), r,
        style = Stroke(0.6f),
    )
}

/** A milestone: a grey circle that pops to mint when the fill reaches it, then ages to green and white. */
private fun DrawScope.drawMilestone(motion: MilestoneMotion, index: Int, count: Int, pitch: Float, done: Float) {
    val c = Offset(TRACK_X + DOT_INSET + pitch * index, DOT_Y)
    val last = index == count - 1
    // How long ago, in milestones, the fill got here; negative while it is still on its way.
    val age = done - index
    val reached = seg(age, 0.15f, 0.6f)
    val next = seg(age, -1.1f, -0.9f) * (1f - reached)

    // A ring and a handful of sparks celebrate the landing.
    val burst = seg(age, 0.3f, 1f)
    if (burst > 0f && burst < 1f) {
        drawCircle(Color(0xFFBDFBE4), DOT_R + 12f * easeOut(burst), c, alpha = 0.8f * (1f - burst), style = Stroke(1.5f))
        for (spark in 0 until SPARKS) {
            val angle = spark.toFloat() / SPARKS * TAU + 0.4f
            val reach = DOT_R + (15f + 9f * ((spark * 0.618f) % 1f)) * easeOut(burst)
            val color = when (spark % 3) {
                0 -> Color.White
                1 -> Color(0xFF5EDCAC)
                else -> Color(0xFF1D885C)
            }
            drawCircle(
                color,
                radius = (if (spark % 2 == 1) 1.5f else 2.1f) * (1f - 0.7f * burst),
                center = Offset(c.x + cos(angle) * reach, c.y + sin(angle) * reach + 5f * burst * burst),
                alpha = 1f - burst * burst,
            )
        }
    }

    val pop = 1f + 0.32f * sin(Math.PI.toFloat() * reached) * (1f - reached * 0.4f)
    val stroke = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    withTransform({ scale(pop, pop, pivot = c) }) {
        if (reached < 1f) {
            val waiting = 1f - reached
            drawCircle(Color(0xFFCFCFD3), DOT_R, c, alpha = waiting)
            drawCircle(Color(0xFFB4E6D4), DOT_R, c, alpha = waiting * next)
            withTransform({ translate(c.x, c.y) }) {
                val glyph = if (last) motion.plus else motion.check
                drawPath(glyph, Color(0xFFA3A3A8), alpha = waiting, style = stroke)
                drawPath(glyph, Color(0xFF8FC7B2), alpha = waiting * next, style = stroke)
            }
        }
        if (reached <= 0f) return@withTransform
        motion.dotShade?.let { shade ->
            shade.alpha = 0.25f * reached
            drawIntoCanvas { it.drawCircle(Offset(c.x, c.y + 1f), DOT_R, shade) }
        }
        // Newest is mint with a dark tick, the one before deep green with a white tick, older ones white.
        drawCircle(aged(age, Color(0xFF8DF6CF), Color(0xFF1F7D54), Color.White), DOT_R, c, alpha = reached)
        val drawn = seg(age, 0.35f, 0.8f)
        if (drawn > 0f) {
            motion.drawnCheck.reset()
            motion.checkMeasure.getSegment(0f, motion.checkMeasure.length * drawn, motion.drawnCheck, true)
            withTransform({ translate(c.x, c.y) }) {
                drawPath(
                    motion.drawnCheck,
                    aged(age, Color(0xFF0D5A3A), Color.White, Color(0xFF0B3D24)),
                    alpha = reached,
                    style = Stroke(1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}
