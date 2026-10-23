package com.ngocdevv.nativecook.blocks.progress.missionglowprogress

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import android.graphics.BlurMaskFilter
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val WIDTH = 330f
private const val HEIGHT = 139f
private const val RADIUS = 26f

// The canvas overflows the card by this much on every side to fit the backdrop glow.
private val BLEED = 90.dp

private const val BADGE_X = 38.6f
private const val BADGE_Y = 40.5f
private const val BADGE_R = 24f
private const val BAR_X = 15f
private const val BAR_Y = 83.5f
private const val BAR_W = 298f
private const val BAR_H = 20f
private const val BAR_CY = BAR_Y + BAR_H / 2

// The last three missions sit this far apart, counted back from the bar's right end.
private const val DOT_PITCH = 49f
private const val DOT_END = 16f
private const val DOT_R = 2.2f
private const val SPREAD = 3
private const val GRID = 4f
private const val SPARKS = 6

// The reference rests at 4 of 7 missions.
private const val REST_DONE = 4

// The autoPlay run is one clock in seconds: one hop per mission, a hold, a slide back.
private const val T_FIRST = 0.5f
private const val HOP_EVERY = 0.85f
private const val HOP_LENGTH = 0.6f

// The head lands (and rings) a little before its overshoot has settled.
private const val LAND_AFTER = 0.3f
private const val RING_LENGTH = 0.55f
private const val HOLD = 2.6f
private const val RESET = 0.5f
private const val TAIL = 0.4f
private const val HEAT_GAIN = 4f

private val Lime = Color(0xFFC4F84A)

private val CenterLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)
private val MessageStyle = TextStyle(
    color = Color(0xFFDBD9DA),
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.1.sp,
    lineHeightStyle = CenterLine,
    platformStyle = NoFontPadding,
)
private val CaptionStyle = TextStyle(
    color = Color(0xFF51565A),
    fontSize = 11.5.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.1.sp,
    fontFeatureSettings = "tnum",
    lineHeightStyle = CenterLine,
    platformStyle = NoFontPadding,
)

private fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun easeInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

private fun easeOutBack(x: Float): Float {
    val c = 1.7f
    return 1f + (c + 1f) * (x - 1f).pow(3) + c * (x - 1f).pow(2)
}

/**
 * Where the head sits (from the bar's left end) with [missions] done. The last three missions are
 * spread out at the reference's spacing; the earlier ones share what is left of the bar.
 */
private fun headX(missions: Float, total: Int): Float {
    val knee = max(total - SPREAD, 0).toFloat()
    val kneeX = BAR_W - DOT_END - min(SPREAD, total) * DOT_PITCH
    if (missions <= knee) return if (knee > 0f) kneeX * missions / knee else 0f
    return BAR_W - DOT_END - (total - missions) * DOT_PITCH
}

/** A solid-colour paint with a soft edge. Hardware canvases only honour mask filters from API 28. */
private fun softPaint(color: Color, sigma: Float, stroke: Float = 0f): Paint? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Paint().apply {
            this.color = color
            if (stroke > 0f) {
                style = PaintingStyle.Stroke
                strokeWidth = stroke
            }
            asFrameworkPaint().maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        }
    } else {
        null
    }

private class MissionMotion {
    /** Seconds into the autoPlay run. */
    var t by mutableFloatStateOf(0f)

    /** Free-running seconds for the breathing glow, the beacon and the sparks. */
    var time by mutableFloatStateOf(0f)

    /** 0…1, how fast the head is moving: stretches the comet tail and throws sparks. */
    var heat by mutableFloatStateOf(0f)

    /** Missions done while controlled. */
    val target = Animatable(0f)

    /** 0…1 after a controlled value lands on a mission. */
    val landed = Animatable(0f)

    val track = Path()
    val fill = Path()
    val marks = Path().apply {
        for (x in listOf(-7f, -4.4f, -1.8f)) {
            moveTo(x, -7.5f)
            lineTo(x + 1.6f, -11.1f)
        }
    }

    val badgeGlow = softPaint(Color.White, 6f, stroke = 5f)?.apply {
        asFrameworkPaint().shader = android.graphics.LinearGradient(
            -BADGE_R, BADGE_R * 0.6f, BADGE_R, -BADGE_R * 0.6f,
            intArrayOf(0xE6FFA028.toInt(), 0xE6E61E32.toInt()), null, android.graphics.Shader.TileMode.CLAMP,
        )
    }
    val haze = softPaint(rgba(160, 235, 30, 0.32f), 18f)
    val halo = softPaint(rgba(215, 255, 60, 1f), 7f)
    val shine = softPaint(rgba(255, 255, 255, 0.22f), 2.5f)
    val shade = softPaint(rgba(40, 70, 0, 0.3f), 3f)
    val core = softPaint(rgba(255, 255, 185, 1f), 6f)
    val spot = softPaint(Color(0xFFFFFFF0), 4f)
    val beacon = softPaint(rgba(170, 255, 60, 0.9f), 4f)

    fun hops(rest: Int) = T_FIRST + (rest - 1) * HOP_EVERY + HOP_LENGTH

    fun loop(rest: Int) = hops(rest) + HOLD + RESET + TAIL

    /** Missions done, a fraction while the head is between two. */
    fun missions(controlled: Boolean, playing: Boolean, rest: Int): Float {
        if (controlled) return target.value
        if (!playing) return rest.toFloat()
        var m = 0f
        for (k in 0 until rest) {
            val begin = T_FIRST + k * HOP_EVERY
            m += easeOutBack(seg(t, begin, begin + HOP_LENGTH))
        }
        val hops = hops(rest)
        return m * (1f - easeInOut(seg(t, hops + HOLD, hops + HOLD + RESET)))
    }

    /** 0…1 after the head lands on a mission: spreads a ring from it. */
    fun ring(controlled: Boolean, playing: Boolean, rest: Int): Float {
        if (controlled) return landed.value
        if (!playing) return 0f
        var ring = 0f
        for (k in 0 until rest) {
            val begin = T_FIRST + k * HOP_EVERY + LAND_AFTER
            val a = seg(t, begin, begin + RING_LENGTH)
            if (a > 0f && a < 1f) ring = a
        }
        return ring
    }
}

private val GridPoints: List<Offset> = buildList {
    var gy = GRID / 2
    while (gy < HEIGHT * 0.8f) {
        var gx = GRID / 2
        while (gx < WIDTH) {
            add(Offset(gx, gy))
            gx += GRID
        }
        gy += GRID
    }
}

/**
 * Dark glass "next level" card: a mission badge, a line of encouragement, and a lime bar whose
 * white-hot head rides from mission to mission, with dots for the missions still to do and
 * captions that count them. The head trails a comet tail and sparks while it moves and rings
 * when it lands.
 *
 * @param progress Share of the missions completed, 0…1. Leave null to show the reference's 4 of 7.
 * @param autoPlay Plays by itself: the bar advances one mission at a time up to 4 of 7, holds, and starts over.
 * @param total Missions in the level.
 * @param backdrop Draws the reference's orange and blue backdrop glow around the card; the parent clips it.
 */
@Composable
fun MissionGlowProgress(
    progress: Float? = null,
    autoPlay: Boolean = false,
    total: Int = 7,
    title: String = "Good job!",
    backdrop: Boolean = true,
    label: String = "Missions",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    var run by remember { mutableIntStateOf(0) }
    val motion = remember { MissionMotion() }
    val controlled = progress != null
    val playing = autoPlay && !controlled && !still
    val rest = min(REST_DONE, total)

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

    var primed by remember { mutableStateOf(false) }
    LaunchedEffect(progress, total) {
        if (progress == null) return@LaunchedEffect
        val next = progress.coerceIn(0f, 1f) * total
        if (!primed) {
            // The first value is shown without a run-up.
            primed = true
            motion.target.snapTo(next)
            return@LaunchedEffect
        }
        val forwards = next > motion.target.value
        motion.target.animateTo(next, tween(if (still) 200 else 700, easing = EaseOutStrong))
        if (forwards && !still) {
            motion.landed.snapTo(0f)
            motion.landed.animateTo(1f, tween((RING_LENGTH * 1000).toInt(), easing = LinearEasing))
        }
    }

    LaunchedEffect(still, playing, controlled, run, rest) {
        if (still) return@LaunchedEffect
        val start = withFrameNanos { it }
        val loop = motion.loop(rest)
        var last = start
        var previous = motion.missions(controlled, playing, rest)
        while (true) {
            val now = withFrameNanos { it }
            val dt = min((now - last) / 1e9f, 0.064f)
            last = now
            motion.time += dt
            if (playing) motion.t = ((now - start) / 1e9f) % loop
            // Speed of the head in shares of the bar per second, eased so the tail grows and fades.
            val m = motion.missions(controlled, playing, rest)
            val speed = abs(m - previous) / total / max(dt, 0.001f)
            previous = m
            motion.heat += (min(1f, speed * HEAT_GAIN) - motion.heat) * 0.18f
        }
    }

    // The captions only change when a whole mission is reached.
    val done by remember(controlled, playing, rest, total) {
        derivedStateOf { motion.missions(controlled, playing, rest).roundToInt().coerceIn(0, total) }
    }
    val left = total - done

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
                stateDescription = "$done of $total missions completed"
                progressBarRangeInfo = ProgressBarRangeInfo(done.toFloat(), 0f..total.toFloat())
            },
    ) {
        // The card, its grain and the backdrop never change, so they get a layer that is drawn once.
        Box(Modifier.matchParentSize().bleed(BLEED).graphicsLayer()) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ -> drawGlass(x, y, backdrop) }
            }
        }
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    drawBar(
                        motion, x, y, total,
                        missions = motion.missions(controlled, playing, rest),
                        ring = motion.ring(controlled, playing, rest),
                        heat = if (still) 0f else motion.heat,
                        time = motion.time,
                        still = still,
                    )
                }
            }
        }

        // The card speaks as one progress bar; its texts stay out of the accessibility tree.
        Box(Modifier.matchParentSize().clearAndSetSemantics {}) {
            BasicText(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$title ") }
                    append(
                        if (left > 0) {
                            "Just complete $left more ${plural(left).lowercase()} to advance to the next level."
                        } else {
                            "Every mission is complete. On to the next level."
                        },
                    )
                },
                Modifier.offset(75.dp, 22.5.dp).width(240.dp),
                style = MessageStyle,
            )
            BasicText("$done ${plural(done)} Completed", Modifier.offset(14.dp, 109.5.dp), style = CaptionStyle, maxLines = 1)
            BasicText(
                "$left ${plural(left)} Remaining",
                Modifier.offset(14.dp, 109.5.dp).width((WIDTH - 31f).dp),
                style = CaptionStyle.copy(textAlign = TextAlign.End),
                maxLines = 1,
            )
        }
    }
}

private fun plural(count: Int) = if (count == 1) "Mission" else "Missions"

private fun DrawScope.drawGlass(x: Float, y: Float, backdrop: Boolean) {
    if (backdrop) {
        val orange = Offset(x + WIDTH + 30f, y - 120f)
        drawCircle(
            brush = Brush.radialGradient(
                0f to rgba(225, 75, 5, 0.95f),
                0.35f to rgba(150, 45, 0, 0.55f),
                0.7f to rgba(70, 22, 0, 0.25f),
                1f to rgba(40, 12, 0, 0f),
                center = orange,
                radius = 300f,
            ),
            radius = 300f,
            center = orange,
        )
        val blue = Offset(x + WIDTH / 2 - 30f, y + HEIGHT + 170f)
        drawCircle(
            brush = Brush.radialGradient(
                0f to rgba(24, 34, 170, 0.95f),
                0.5f to rgba(14, 20, 100, 0.55f),
                1f to rgba(6, 8, 40, 0f),
                center = blue,
                radius = 280f,
            ),
            radius = 280f,
            center = blue,
        )
    }

    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(rgba(52, 40, 38, 0.62f), rgba(10, 10, 18, 0.55f)),
            start = Offset(x, y),
            end = Offset(x + WIDTH * 0.3f, y + HEIGHT),
        ),
        topLeft = Offset(x, y),
        size = Size(WIDTH, HEIGHT),
        cornerRadius = CornerRadius(RADIUS),
    )
    // The fine halftone grain of the glass, fading out towards the bottom.
    val card = Path().apply { addRoundRect(RoundRect(x, y, x + WIDTH, y + HEIGHT, CornerRadius(RADIUS))) }
    clipPath(card) {
        withTransform({ translate(x, y) }) {
            drawPoints(
                points = GridPoints,
                pointMode = PointMode.Points,
                brush = Brush.verticalGradient(
                    0f to rgba(255, 255, 255, 0.13f),
                    0.5f to rgba(255, 255, 255, 0.07f),
                    1f to rgba(255, 255, 255, 0f),
                    startY = 0f,
                    endY = HEIGHT * 0.8f,
                ),
                strokeWidth = 1f,
                cap = StrokeCap.Round,
            )
        }
    }
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to rgba(255, 255, 255, 0.2f),
            0.5f to rgba(255, 255, 255, 0.07f),
            1f to rgba(255, 255, 255, 0.12f),
            startY = y,
            endY = y + HEIGHT,
        ),
        topLeft = Offset(x + 0.5f, y + 0.5f),
        size = Size(WIDTH - 1, HEIGHT - 1),
        cornerRadius = CornerRadius(RADIUS - 0.5f),
        style = Stroke(1f),
    )
}

private fun DrawScope.drawBar(
    motion: MissionMotion,
    x: Float,
    y: Float,
    total: Int,
    missions: Float,
    ring: Float,
    heat: Float,
    time: Float,
    still: Boolean,
) {
    val top = y + BAR_Y
    val cy = y + BAR_CY
    val r = CornerRadius(BAR_H / 2)
    val breath = (if (still) 0.85f else 0.8f + 0.12f * sin(time * 1.9f)) + 0.2f * heat

    drawBadge(motion, x + BADGE_X, y + BADGE_Y, min(1f, breath))

    drawRoundRect(rgba(22, 22, 22, 0.9f), Offset(x + BAR_X, top), Size(BAR_W, BAR_H), r)
    for (index in 1..total) drawDot(motion, index, total, x, cy, missions, time, still)

    // Nothing is lit until the first mission is under way.
    val lit = seg(missions, 0.02f, 0.3f)
    if (lit <= 0f) return
    val head = Offset(x + BAR_X + max(0f, headX(missions, total)), cy)
    val glow = lit * min(1f, breath)

    // A wide lime haze and a tight halo around the head.
    drawIntoCanvas { canvas ->
        motion.haze?.let {
            it.alpha = 0.32f * glow
            canvas.drawCircle(head, 34f, it)
        }
        motion.halo?.let {
            it.alpha = glow
            canvas.drawCircle(head, 14f + 4f * heat, it)
        }
    }
    if (ring > 0f && ring < 1f) {
        drawCircle(
            Lime,
            radius = 9f + 20f * (1f - (1f - ring).pow(3)),
            center = head,
            alpha = 0.85f * (1f - ring) * lit,
            style = Stroke(1.6f),
        )
    }

    val right = head.x + BAR_H / 2
    motion.fill.reset()
    motion.fill.addRoundRect(RoundRect(x + BAR_X, top, right, top + BAR_H, r))
    drawPath(
        motion.fill,
        brush = Brush.horizontalGradient(
            0f to Color(0xFFD5E255),
            0.28f to Color(0xFFB3D038),
            0.66f to Color(0xFF80B520),
            1f to Color(0xFF9CCB28),
            startX = x + BAR_X,
            endX = right,
        ),
        alpha = lit,
    )
    clipPath(motion.fill) {
        drawIntoCanvas { canvas ->
            // Lit from above like a glass tube.
            motion.shine?.let {
                it.alpha = 0.22f * lit
                canvas.drawRoundRect(x + BAR_X + 4, top + 2, x + BAR_X + 4 + BAR_W, top + 7, 2.5f, 2.5f, it)
            }
            motion.shade?.let {
                it.alpha = 0.3f * lit
                canvas.drawRoundRect(x + BAR_X, top + BAR_H - 4, x + BAR_X + BAR_W, top + BAR_H + 2, 3f, 3f, it)
            }
            // The white-hot core stretches back into a tail while the head moves.
            val tail = 40f + 34f * heat
            motion.core?.let {
                it.alpha = lit
                canvas.drawOval(head.x + 7 - tail, cy - 7.5f, head.x + 7, cy + 7.5f, it)
            }
            motion.spot?.let {
                it.alpha = lit
                canvas.drawCircle(head, 7.5f, it)
            }
        }
        if (ring > 0f) drawPath(motion.fill, Color.White, alpha = 0.7f * (1f - seg(ring, 0f, 0.5f)) * lit)
    }

    // Sparks shed backwards from the head while it moves.
    if (heat <= 0.01f) return
    for (index in 0 until SPARKS) {
        val side = if (index % 2 == 1) 1f else -1f
        val reach = 0.5f + 0.5f * ((index * 0.37f) % 1f)
        val life = (time * 1.7f + index.toFloat() / SPARKS) % 1f
        drawCircle(
            Color(0xFFEAFF8A),
            radius = 1.4f * (1f - 0.6f * life),
            center = Offset(
                head.x - 4f - (10f + 30f * reach) * life,
                cy + side * (5f + 17f * reach * life) + 6f * life * life,
            ),
            alpha = heat * (1f - life) * lit,
        )
    }
}

/** Mission badge: a red card in a ring lit orange from the left and red from the right. */
private fun DrawScope.drawBadge(motion: MissionMotion, cx: Float, cy: Float, breath: Float) {
    val from = Offset(-BADGE_R, BADGE_R * 0.6f)
    val to = Offset(BADGE_R, -BADGE_R * 0.6f)
    withTransform({ translate(cx, cy) }) {
        motion.badgeGlow?.let { glow ->
            glow.alpha = breath
            drawIntoCanvas { it.drawCircle(Offset.Zero, BADGE_R, glow) }
        }
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF2A0D12), Color(0xFF0D0709)),
                center = Offset(4f, -4f),
                radius = BADGE_R * 1.2f,
            ),
            radius = BADGE_R,
            center = Offset.Zero,
        )
        drawCircle(
            brush = Brush.linearGradient(listOf(Color(0xFFFFB04A), Color(0xFFF0553A), Color(0xFFD8243C)), from, to),
            radius = BADGE_R - 0.75f,
            center = Offset.Zero,
            style = Stroke(1.5f),
        )
        withTransform({ rotate(0.2f * 180f / Math.PI.toFloat(), pivot = Offset.Zero) }) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(Color(0xFFFF8A5C), Color(0xFFE83038), Color(0xFFA5121F)),
                    start = Offset(-10f, -14f),
                    end = Offset(10f, 14f),
                ),
                topLeft = Offset(-10.5f, -14f),
                size = Size(21f, 28f),
                cornerRadius = CornerRadius(3.5f),
            )
            drawRoundRect(
                rgba(255, 200, 180, 0.55f), Offset(-10f, -13.5f), Size(20f, 27f), CornerRadius(3f),
                style = Stroke(0.8f),
            )
            drawCircle(rgba(255, 150, 140, 0.75f), 4.6f, Offset(-1.5f, 3f), style = Stroke(1.8f))
            drawCircle(rgba(120, 10, 20, 0.7f), 1.4f, Offset(-1.5f, 3f))
            drawPath(motion.marks, rgba(255, 255, 255, 0.85f), style = Stroke(1.1f))
            for (dy in listOf(-4f, -1.4f, 1.2f, 3.8f)) {
                drawCircle(rgba(255, 255, 255, 0.85f), 0.65f, Offset(6.6f, dy))
            }
        }
    }
}

/** A mission still to do: grey, or a lime beacon when it is the next one. */
private fun DrawScope.drawDot(
    motion: MissionMotion,
    index: Int,
    total: Int,
    x: Float,
    cy: Float,
    missions: Float,
    time: Float,
    still: Boolean,
) {
    val c = Offset(x + BAR_X + headX(index.toFloat(), total), cy)
    // How many missions ahead of the head this dot is.
    val ahead = index - missions
    val shown = seg(ahead, 0.2f, 0.45f)
    if (shown <= 0f) return
    val next = shown * (1f - seg(ahead, 1.05f, 1.3f))
    drawCircle(Color(0xFF434343), DOT_R, c, alpha = shown)
    if (next <= 0f) return

    motion.beacon?.let { glow ->
        glow.alpha = 0.9f * next * (if (still) 0.55f else 0.55f + 0.25f * sin(time * 3f))
        drawIntoCanvas { it.drawCircle(c, 6f, glow) }
    }
    // The beacon sends out a faint ring every so often.
    val beat = (time % 1.8f) / 1.1f
    if (!still && beat < 1f) {
        drawCircle(Lime, DOT_R + 7f * beat, c, alpha = 0.6f * next * (1f - beat), style = Stroke(1f))
    }
    drawCircle(Lime, DOT_R, c, alpha = next)
}
