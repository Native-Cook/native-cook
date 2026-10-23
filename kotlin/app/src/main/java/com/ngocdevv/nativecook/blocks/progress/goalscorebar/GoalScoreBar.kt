package com.ngocdevv.nativecook.blocks.progress.goalscorebar

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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
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

/**
 * A friend in the panel: an avatar drawn as a coloured disc with initials.
 *
 * @param share Share of their own goal reached, 0…1: the ring around the avatar.
 */
data class GoalFriend(val initials: String, val color: Color, val ink: Color, val share: Float)

private const val WIDTH = 340f

// The compact pill, then the expanded panel under it.
private const val PILL_W = 200.5f
private const val PILL_H = 36f
private const val PILL_X = (WIDTH - PILL_W) / 2f
private const val PANEL_Y = 59.3f
private const val PANEL_H = 146.3f
private const val PANEL_R = 43f
private const val HEIGHT = PANEL_Y + PANEL_H

// The canvas overflows the component by this much on every side to fit the shadow and the device frame.
private val BLEED = 44.dp

private const val BAR_X = 21.5f
private const val BAR_W = 296.2f
private const val BAR_Y = PANEL_Y + 64f
private const val BAR_H = 3.4f
private val AVATAR_X = floatArrayOf(43f, 98.5f, 153.5f)
private const val AVATAR_Y = PANEL_Y + 102.2f
private const val AVATAR_R = 17f
private const val RING_R = 20.3f
private const val RING_W = 3.2f
private const val TROPHY_X = 278.6f
private const val TROPHY_Y = PANEL_Y + 41.6f

// The rolling number: four columns, each a window one digit tall.
private const val COL_W = 21.9f
private const val DIGIT_H = 44f

// A column never rolls faster than this (digits per second), so fast counting reads as a blur of rolling
// digits instead of flicker; it eases into its digit over the last turn.
private const val ROLL_MAX = 22f
private const val ROLL_MIN = 2.5f

private val Orange = Color(0xFFF2A23E)

// autoPlay loop (seconds).
private const val START = 0.5f
private const val COUNT = 2.8f
private const val HOLD = 4.2f
private const val RESET = 0.7f
private const val LOOP = START + COUNT + HOLD + RESET

// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
private const val REST_T = START + COUNT + 1.8f

private val TEN = intArrayOf(1, 10, 100, 1000)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun goalText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    fontFeatureSettings = "tnum",
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val PillScoreStyle = goalText(13.6f, 18f, FontWeight.SemiBold, Color.White)
private val PillRankStyle = goalText(13.6f, 18f, FontWeight.Medium, Orange).copy(textAlign = TextAlign.End)
private val DigitStyle = goalText(37f, DIGIT_H, FontWeight.Bold, Color.White, -0.5f)
private val GoalStyle = goalText(16f, 20f, FontWeight.Medium, Color(0xFF6C6C6E))
private val RankStyle = goalText(16f, 20f, FontWeight.Medium, Orange).copy(textAlign = TextAlign.End)
private val InitialsStyle = goalText(11.5f, 16f, FontWeight.Bold, Color.White, 0.2f).copy(textAlign = TextAlign.Center)
private val ButtonLabelStyle = goalText(16f, 21f, FontWeight.Medium, Color.White)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

private fun easeOutCubic(t: Float) = 1f - (1f - clamp01(t)).pow(3)

/** Share of the goal reached at [t] seconds into the autoPlay loop, where [rest] is the reference share. */
private fun loopValue(t: Float, rest: Float) = when {
    t < START -> 0f
    t < START + COUNT -> rest * easeOutCubic((t - START) / COUNT)
    t < START + COUNT + HOLD -> rest
    else -> rest * (1f - easeInOutCubic((t - START - COUNT - HOLD) / RESET))
}

/** How far friend [index]'s ring is drawn: they sweep in one after another early in the count. */
private fun friendAt(share: Float, rest: Float, index: Int) = easeOutCubic(clamp01(share / max(rest, 0.001f) * 4f - 0.5f * index))

private fun rankOf(points: Int, rivals: List<Int>) = 1 + rivals.count { it > points }

private fun ordinal(rank: Int): String {
    if (rank % 100 in 11..13) return "${rank}th"
    return rank.toString() + when (rank % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
}

private fun spaced(n: Int): String {
    var digits = n.toString()
    var out = ""
    while (digits.length > 3) {
        out = " " + digits.takeLast(3) + out
        digits = digits.dropLast(3)
    }
    return digits + out
}

/** Scale of the trophy after the rank changes: a quick overshoot. */
private fun pop(since: Float): Float {
    val u = clamp01(since / 0.55f)
    return 1f + 0.45f * sin(PI.toFloat() * u) * (1f - u)
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

/** Per-frame state of the rolling number and the trophy. */
private class GoalRoll(points: Int) {
    /** Where each column is (0…10), units first, and whether it is shown (leading zeros fold away). */
    val pos = FloatArray(4) { (points / TEN[it] % 10).toFloat() }
    val shown = FloatArray(4) { if (it == 0 || points >= TEN[it]) 1f else 0f }

    /** Change of the share per second: lights the head of the line. */
    var speed = 0f

    /** When the rank last changed, on the `time` axis. */
    var rankedAt = -10f
}

private class GoalPaints {
    val shadow = SoftRoundRect(rgba(0, 0, 0, 0.3f), sigma = 14f)
    val head = softPaint(5f)
    val shimmer = softPaint(3f)
    val flash = softPaint(6f)
    val fill = Path()

    // Trophy, drawn around its centre in a 12.4 × 12.6 box.
    val cup = PathParser().parsePathString("M-3.7 -6.3L3.7 -6.3L3.7 -2.4A3.7 3.7 0 0 1 -3.7 -2.4Z").toPath()
    val handles = PathParser()
        .parsePathString("M-3.9 -5L-5.6 -5A2.5 2.5 0 0 0 -3.5 -1.7M3.9 -5L5.6 -5A2.5 2.5 0 0 1 3.5 -1.7").toPath()
    val foot = PathParser().parsePathString("M-0.8 1L0.8 1L0.8 4.3L3 4.3L3 6.2L-3 6.2L-3 4.3L-0.8 4.3Z").toPath()
}

/** Climbing a place makes the trophy jump and tip. */
private fun DrawScope.drawTrophy(paints: GoalPaints, x: Float, y: Float, size: Float, since: Float, ambient: Float) {
    val u = clamp01(since / 0.55f)
    val scale = size * (if (ambient > 0f) pop(since) else 1f)
    withTransform({
        translate(x, y - ambient * 3f * sin(PI.toFloat() * u) * (1f - u))
        rotate(Math.toDegrees((ambient * 0.35f * sin(u * PI.toFloat() * 3f) * (1f - u)).toDouble()).toFloat(), pivot = Offset.Zero)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        val flashAt = clamp01(since / 0.7f)
        val flash = ambient * 0.8f * (1f - flashAt) * clamp01(flashAt * 8f)
        if (CanBlur && flash > 0.005f) {
            drawIntoCanvas { it.nativeCanvas.drawCircle(0f, 0f, 9f, paints.flash.tinted(Orange, flash)) }
        }
        drawPath(paints.cup, Orange)
        drawPath(paints.handles, Orange, style = Stroke(1.15f, cap = StrokeCap.Round))
        drawPath(paints.foot, Orange)
    }
}

/**
 * Leaderboard live activity: a compact pill with the score and place, and the expanded panel with
 * the score against the goal, a thin progress line, three friends and a button. The score rolls up
 * like an odometer, the line fills behind a glowing head with a highlight running along it, the
 * trophy jumps each time a rival is passed, and the friends' rings sweep in one after another.
 *
 * @param progress Controlled share of the goal reached, 0…1.
 * @param autoPlay Counts the score up to [score], climbing the ranking on the way, holds, and starts over.
 * @param score Score shown at the resting frame.
 * @param rivals Scores of the other people in the ranking: passing one moves the trophy up a place.
 */
@Composable
fun GoalScoreBar(
    progress: Float? = null,
    autoPlay: Boolean = false,
    score: Int = 8814,
    goal: Int = 10000,
    rivals: List<Int> = listOf(9480, 8120, 6730, 5210),
    friends: List<GoalFriend> = listOf(
        GoalFriend("MK", Color(0xFF33363D), Color.White, 0.75f),
        GoalFriend("JL", Color(0xFFF6B90F), Color(0xFF2A1C00), 0.66f),
        GoalFriend("TS", Color(0xFF90A78F), Color(0xFF16240F), 0.42f),
    ),
    buttonLabel: String = "All friends",
    onAllFriends: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val controlled = progress != null
    val rest = score.toFloat() / goal
    val target = (progress ?: rest).coerceIn(0f, 1f)
    val auto = autoPlay && !controlled && !reducedMotion
    val looping by rememberUpdatedState(auto)
    val isControlled by rememberUpdatedState(controlled)
    val currentRest by rememberUpdatedState(rest)
    val currentRivals by rememberUpdatedState(rivals)

    // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
    val time = remember { mutableFloatStateOf(0f) }
    val clock = remember { mutableFloatStateOf(if (auto) 0f else REST_T) }
    var playing by remember { mutableStateOf(false) }
    val held = remember { Animatable(target) }
    val roll = remember { GoalRoll(((if (auto) 0f else target) * goal).roundToInt()) }

    LaunchedEffect(auto) {
        playing = auto
        clock.floatValue = if (auto) 0f else REST_T
    }

    LaunchedEffect(controlled, target) {
        if (!controlled || held.targetValue == target) return@LaunchedEffect
        held.animateTo(target, tween(if (reducedMotion) 200 else 1600, easing = EaseOutStrong))
    }

    LaunchedEffect(reducedMotion, goal) {
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
            roll.speed = (share - before) / dt
            val points = (share * goal).roundToInt()
            // Passing a rival moves the trophy up a place.
            if (rankOf(points, currentRivals) < rankOf((before * goal).roundToInt(), currentRivals)) roll.rankedAt = now
            // Each column rolls towards its digit in the direction the score is moving.
            val dir = if (share >= before) 1f else -1f
            before = share
            for (k in 0 until 4) {
                val digit = (points / TEN[k] % 10).toFloat()
                var dist = ((digit - roll.pos[k]) * dir).mod(10f)
                if (dist > 9.99f) dist = 0f
                val step = max(ROLL_MIN, min(dist * 10f, ROLL_MAX)) * dt
                roll.pos[k] = if (step >= dist) digit else (roll.pos[k] + dir * step).mod(10f)
                if (k > 0) roll.shown[k] += ((if (points >= TEN[k]) 1f else 0f) - roll.shown[k]) * min(1f, dt * 14f)
            }
            // Written last: reading it is what redraws the component each frame.
            time.floatValue = now
        }
    }

    // Read inside draw / layer lambdas so a frame only redraws, it does not recompose.
    val value = { if (controlled) held.value else loopValue(clock.floatValue, rest) }
    val pointsShown by remember(controlled, rest, goal) { derivedStateOf { (value() * goal).roundToInt() } }
    val rank by remember(controlled, rest, goal, rivals) { derivedStateOf { rankOf((value() * goal).roundToInt(), rivals) } }

    val ambient = if (reducedMotion) 0f else 1f
    val paints = remember { GoalPaints() }
    val measurer = rememberTextMeasurer()
    val glyphs = remember(measurer) { List(10) { measurer.measure(it.toString(), DigitStyle) } }
    val goalLabel = remember(measurer, goal) { measurer.measure("/ ${spaced(goal)}", GoalStyle) }

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
                    val t = time.floatValue
                    val share = value()
                    val panelY = y + PANEL_Y

                    // The body of the device the panel sits on, fading out downwards.
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to rgba(246, 246, 246, 1f),
                            0.72f to rgba(246, 246, 246, 1f),
                            1f to rgba(246, 246, 246, 0f),
                            startY = panelY,
                            endY = y + HEIGHT + 40f,
                        ),
                        topLeft = Offset(x - 9f, panelY - 9f),
                        size = Size(WIDTH + 18f, PANEL_H + 120f),
                        cornerRadius = CornerRadius(PANEL_R + 9f),
                    )
                    drawIntoCanvas { paints.shadow.draw(it.nativeCanvas, x + 10, panelY + 16, WIDTH - 20, PANEL_H - 6, PANEL_R) }
                    drawRoundRect(Color.Black, Offset(x + PILL_X, y), Size(PILL_W, PILL_H), CornerRadius(PILL_H / 2f))
                    drawRoundRect(Color.Black, Offset(x, panelY), Size(WIDTH, PANEL_H), CornerRadius(PANEL_R))

                    // The line: track, glowing head, fill, and a highlight that runs along the filled part.
                    val barY = y + BAR_Y
                    val fillW = max(BAR_H, BAR_W * clamp01(share))
                    val fillOpacity = clamp01(share * 60f)
                    drawRoundRect(
                        Color(0xFF29292B), Offset(x + BAR_X, barY - BAR_H / 2f), Size(BAR_W, BAR_H), CornerRadius(BAR_H / 2f),
                    )
                    val headOpacity = fillOpacity * (0.35f + ambient * (0.12f * sin(t * 3f) + 0.5f * clamp01(abs(roll.speed) * 2.5f)))
                    if (CanBlur && headOpacity > 0.005f) {
                        drawIntoCanvas {
                            it.nativeCanvas.drawCircle(x + BAR_X + fillW - BAR_H / 2f, barY, 5f, paints.head.tinted(Orange, headOpacity))
                        }
                    }
                    drawRoundRect(
                        Orange, Offset(x + BAR_X, barY - BAR_H / 2f), Size(fillW, BAR_H), CornerRadius(BAR_H / 2f), alpha = fillOpacity,
                    )
                    val shimmer = (t % 3.2f) / 1.2f
                    if (CanBlur && ambient > 0f && shimmer < 1f) {
                        paints.fill.rewind()
                        paints.fill.addRoundRect(
                            RoundRect(Rect(x + BAR_X, barY - BAR_H / 2f, x + BAR_X + fillW, barY + BAR_H / 2f), CornerRadius(BAR_H / 2f)),
                        )
                        clipPath(paints.fill) {
                            val sx = x + BAR_X - 30f + (fillW + 30f) * shimmer
                            drawIntoCanvas {
                                it.nativeCanvas.drawRoundRect(
                                    sx, barY - 3f, sx + 22f, barY + 3f, 3f, 3f,
                                    paints.shimmer.tinted(Color(0xFFFFF4D6), 0.85f * sin(PI.toFloat() * shimmer)),
                                )
                            }
                        }
                    }

                    for (index in 0 until 3) {
                        val centre = Offset(x + AVATAR_X[index], y + AVATAR_Y)
                        val drawn = friendAt(share, rest, index)
                        drawCircle(Color(0xFF2A2A2C), RING_R, centre, style = Stroke(RING_W))
                        if (drawn > 0.001f) {
                            drawArc(
                                color = Orange,
                                startAngle = -90f,
                                sweepAngle = 360f * friends[index].share * drawn,
                                useCenter = false,
                                topLeft = Offset(centre.x - RING_R, centre.y - RING_R),
                                size = Size(RING_R * 2, RING_R * 2),
                                style = Stroke(RING_W, cap = StrokeCap.Round),
                            )
                        }
                        drawCircle(friends[index].color, AVATAR_R * (0.72f + 0.28f * drawn), centre, alpha = clamp01(drawn * 3f))
                    }

                    val sinceRank = if (ambient > 0f) t - roll.rankedAt else 10f
                    drawTrophy(paints, x + TROPHY_X, y + TROPHY_Y, 1.16f, sinceRank, ambient)
                    drawTrophy(paints, x + PILL_X + 173f, y + PILL_H / 2f, 0.96f, sinceRank, ambient)
                }
            }
        }

        Box(
            Modifier.matchParentSize().clearAndSetSemantics {
                contentDescription = "Score"
                stateDescription = "${(target * goal).roundToInt()} of $goal, ${ordinal(rank)} place"
                progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
            },
        ) {
            BasicText(pointsShown.toString(), Modifier.offset((PILL_X + 9f).dp, 9.dp), PillScoreStyle, maxLines = 1)
            BasicText(
                rank.toString(), Modifier.offset((PILL_X + PILL_W - 8.7f - 30f).dp, 9.dp).width(30.dp), PillRankStyle, maxLines = 1,
            )

            // The rolling number and the goal after it: each column shows the digit it is on and the next
            // one sliding in under it.
            Canvas(Modifier.offset(22.2.dp, (PANEL_Y + 12.7f).dp).size(290.dp, DIGIT_H.dp).clipToBounds()) {
                time.floatValue
                val points = (value() * goal).roundToInt()
                val column = COL_W.dp.toPx()
                val digitH = DIGIT_H.dp.toPx()
                var left = 0f
                for (k in 3 downTo 0) {
                    // Reduced motion: nothing rolls, the columns simply show the digits.
                    val pos = if (ambient > 0f) roll.pos[k] else (points / TEN[k] % 10).toFloat()
                    val shown = if (ambient > 0f) roll.shown[k] else if (k == 0 || points >= TEN[k]) 1f else 0f
                    if (shown > 0.01f) {
                        val digit = floor(pos).toInt() % 10
                        val frac = pos - floor(pos)
                        clipRect(left, 0f, left + column * shown, digitH) {
                            val glyph = glyphs[digit]
                            drawText(glyph, topLeft = Offset(left + (column - glyph.size.width) / 2f, -frac * digitH), alpha = shown)
                            if (frac > 0.001f) {
                                val next = glyphs[(digit + 1) % 10]
                                drawText(next, topLeft = Offset(left + (column - next.size.width) / 2f, (1f - frac) * digitH), alpha = shown)
                            }
                        }
                    }
                    left += column * shown
                }
                drawText(goalLabel, topLeft = Offset(left + 8.dp.toPx(), 19.5.dp.toPx()))
            }

            BasicText(
                ordinal(rank), Modifier.offset((WIDTH - 22.8f - 40f).dp, (PANEL_Y + 32.2f).dp).width(40.dp), RankStyle, maxLines = 1,
            )

            for (index in 0 until 3) {
                BasicText(
                    text = friends[index].initials,
                    style = InitialsStyle.copy(color = friends[index].ink),
                    maxLines = 1,
                    modifier = Modifier
                        .offset((AVATAR_X[index] - 20f).dp, (AVATAR_Y - 8f).dp)
                        .width(40.dp)
                        .graphicsLayer {
                            val drawn = friendAt(value(), rest, index)
                            alpha = clamp01(drawn * 3f - 1f)
                            scaleX = 0.72f + 0.28f * drawn
                            scaleY = scaleX
                        },
                )
            }
        }

        // The button: scales down while pressed, with a light haptic.
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val view = LocalView.current
        LaunchedEffect(pressed) {
            if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        val press by animateFloatAsState(
            targetValue = if (pressed) PRESS_SCALE else 1f,
            animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
            label = "press",
        )
        Box(
            modifier = Modifier
                .offset(190.dp, (PANEL_Y + 80.7f).dp)
                .size(127.7.dp, 43.5.dp)
                .graphicsLayer {
                    scaleX = press
                    scaleY = press
                }
                .background(Color(0xFF242426), CircleShape)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onAllFriends?.invoke() }
                .semantics { contentDescription = buttonLabel },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(buttonLabel, Modifier.clearAndSetSemantics {}, ButtonLabelStyle, maxLines = 1)
        }
    }
}
