package com.ngocdevv.nativecook.blocks.onboarding.goalstaircaseglow

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// The whole scene is one drawing on a stage of 393 × 852 dp, scaled to fit the screen. What it
// shows at a moment follows from that moment and a short list of times: when the scene appeared,
// when the stairs were called, when each hop began and when it all began to fade.

/** autoPlay: the stairs at STAIRS_AT, the climb at CLIMB_AT, the fade at RESET_AT, every LOOP seconds. */
internal const val LOOP = 8.5f
internal const val STAIRS_AT = 2.5f
internal const val CLIMB_AT = 4.4f
internal const val RESET_AT = 7.4f

/** After a touch autoPlay waits this long, then fades out and starts over. */
internal const val USER_HOLD = 3f

/** A time that has not come. */
internal const val NONE = 1e9f

// The scene appears: the ball, then the goal, whose ring swells once.
private const val BALL_IN = 0.4f
private const val GOAL_AFTER = 0.2f

// A dashed line is drawn from the ball towards the goal, and three question marks pop up.
private const val LINE_AT = 0.6f
private const val LINE_FOR = 0.5f
private const val MARKS_AT = 1.3f
private const val MARK_GAP = 0.12f
private const val MARK_POP = 0.25f
private const val SHRUG_FOR = 0.7f

// They leave by themselves, or as soon as the stairs are called.
private const val ASK_OUT = 2f
private const val ASK_FADE = 0.5f

/** The labels arrive one after the other, each sliding up into place. */
internal const val STAIR_GAP = 0.3f
internal const val STAIR_IN = 0.3f
private const val STAIR_RISE = 10f

/** A hop from step to step, and the last one onto the goal. */
internal const val HOP = 0.32f
internal const val GOAL_HOP = 0.4f

// The top of a hop is this far above the straight line between its ends.
private const val ARC = 22f

// A label lights up when the ball lands, and dips like a spring: 3 dp down and back.
private const val LIT = 0.12f
private const val DIP = 6.34f
private const val DIP_DECAY = 11f
private const val DIP_SWING = 19.05f
private const val SQUASH = 0.08f

/** The goal bursts, glows on, and then everything fades to black and stays dark a moment. */
internal const val BURST = 0.4f
internal const val FADE = 0.5f
internal const val DARK = 0.6f

/** How long the scene takes to appear and ask its question. */
internal const val INTRO = 3f

internal const val STAGE_WIDTH = 393f
internal const val STAGE_HEIGHT = 852f
private const val BALL = 19f
private val BallAt = Offset(81f, 484f)
private val GoalAt = Offset(288f, 172f)
private const val GOAL = 11f
private const val RING = 17f
private const val GOAL_LABEL = 213f
private val FirstStep = Offset(158f, 457f)
private val LastStep = Offset(291f, 280f)
private const val LABEL_WIDTH = 78f
private const val LABEL_HEIGHT = 22f
private const val LABEL_PAD = 10f
private const val LABEL_RADIUS = 4f
private val LineFrom = Offset(112f, 455f)
private val LineTo = Offset(210f, 320f)

// The question marks: where each is from the ball's centre, and its tilt in degrees.
private val Marks = listOf(floatArrayOf(-15f, -38f, -12f), floatArrayOf(1f, -49f, 4f), floatArrayOf(17f, -37f, 14f))

// Everything is mirrored on the floor, which lies this far under the ball's spot: a dim, flattened
// copy straight below it, fainter the higher the thing is. The goal's copy is far down the screen.
private const val FLOOR = 484f + 52f
private const val FLOOR_FADE = 300f
private const val GOAL_MIRROR = 800f
private const val HALO = 120f
private const val STREAKS = 14
private const val PI_F = PI.toFloat()

internal val Gold = Color(0xFFFED01B)
internal val LabelInk = Color(0xFF111111)

/**
 * The times the scene is drawn from.
 *
 * @param born When the scene began to appear.
 * @param stairs When the stairs were called.
 * @param hops When each hop began: one per step, and one onto the goal.
 * @param fade When everything began to fade to black.
 */
internal class Plot(val born: Float, val stairs: Float, val hops: FloatArray, val fade: Float) {
    constructor(born: Float, steps: Int) : this(born, NONE, FloatArray(steps + 1) { NONE }, NONE)

    /** The plot as it stands at [t]: once a fade and the dark after it are over, the scene is new. */
    fun at(t: Float) = if (fade < NONE && t >= fade + FADE + DARK) Plot(fade + FADE + DARK, hops.size - 1) else this
}

private fun clamp01(t: Float) = min(1f, max(0f, t))

private fun smooth(t: Float, from: Float, to: Float): Float {
    // A time that has not come is too large to add a duration to: nothing has started.
    if (from >= NONE) return 0f
    val u = clamp01((t - from) / (to - from))
    return u * u * (3f - 2f * u)
}

private fun easeOut(t: Float): Float {
    val u = 1f - clamp01(t)
    return 1f - u * u * u
}

private fun dipAt(since: Float) = if (since <= 0f) 0f else DIP * exp(-DIP_DECAY * since) * sin(DIP_SWING * since)

/** A number from 0 to 1 that depends on [n] alone, the same on every platform. */
private fun hash(n: Int): Float {
    var h = (n xor 0x9e3779b9.toInt()) * 0x85ebca6b.toInt()
    h = h xor (h ushr 13)
    h *= 0xc2b2ae35.toInt()
    h = h xor (h ushr 16)
    return ((h.toLong() and 0xFFFFFFFFL) / 4294967296.0).toFloat()
}

/** The centre of step [index] (0 is the lowest) of [count]. */
private fun stepAt(index: Int, count: Int): Offset {
    val u = if (count > 1) index / (count - 1f) else 0f
    return Offset(FirstStep.x + (LastStep.x - FirstStep.x) * u, FirstStep.y + (LastStep.y - FirstStep.y) * u)
}

// Soft discs of one colour that fade from the centre to nothing (cos², so there is no rim), in a
// unit circle: lights, glows and the copies on the floor are these, stretched.
private val FalloffAt = floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f)
private val Falloff = floatArrayOf(1f, 0.905f, 0.655f, 0.345f, 0.095f, 0f)

private fun soft(color: Color) = Brush.radialGradient(
    colorStops = Array(Falloff.size) { FalloffAt[it] to color.copy(alpha = Falloff[it]) },
    center = Offset.Zero,
    radius = 1f,
)

private val HaloLight = soft(Color(0xFF241F1E))
private val BallLight = soft(Color(0xFFFFE2A8))
private val GoldLight = soft(Gold)
private val WhiteLight = soft(Color.White)
private val BallFace = Brush.linearGradient(
    listOf(Color(0xFFFFF4DC), Color(0xFFFFE2A8)),
    start = Offset(-BALL * 0.7f, -BALL * 0.7f),
    end = Offset(BALL * 0.7f, BALL * 0.7f),
)
private val Dashes = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))

// A question mark 13 dp high, drawn round its middle.
private val Mark = Path().apply {
    arcTo(Rect(-3.3f, -6.3f, 3.3f, 0.3f), 180f, 235f, forceMoveTo = true)
    lineTo(0.2f, 1f)
    lineTo(0f, 2.6f)
}

/** A word in its two looks, laid out once at one pixel to the dp. */
internal class Words(val dim: TextLayoutResult, val lit: TextLayoutResult)

private fun DrawScope.light(brush: Brush, x: Float, y: Float, rx: Float, ry: Float, alpha: Float) {
    if (alpha <= 0f) return
    withTransform({
        translate(x, y)
        scale(rx, ry, pivot = Offset.Zero)
    }) {
        drawCircle(brush, radius = 1f, center = Offset.Zero, alpha = min(1f, alpha))
    }
}

/** Draws a word centred on ([x], [y]). */
private fun DrawScope.write(word: TextLayoutResult, x: Float, y: Float, alpha: Float) {
    if (alpha <= 0f) return
    drawText(word, topLeft = Offset(x - word.size.width / 2f, y - word.size.height / 2f), alpha = alpha)
}

/**
 * The scene at [t] seconds in a canvas [width] × [height] dp, with the stage scaled to fit in
 * its middle. [still] is reduced motion: the ball does not arc or squash and nothing wobbles.
 */
internal fun DrawScope.drawStage(width: Float, height: Float, t: Float, given: Plot, labels: List<Words>, goal: Words, still: Boolean) {
    val plot = given.at(t)
    // For the frame in which the list of steps has changed and the plot has not yet.
    val count = min(labels.size, plot.hops.size - 1)
    val life = t - plot.born
    val out = if (plot.fade < NONE) 1f - smooth(t, plot.fade, plot.fade + FADE) else 1f
    if (life < 0f || out <= 0f) return
    val scale = min(width / STAGE_WIDTH, height / STAGE_HEIGHT)
    withTransform({
        translate((width - STAGE_WIDTH * scale) / 2f, (height - STAGE_HEIGHT * scale) / 2f)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        val ballIn = smooth(life, 0f, BALL_IN) * out
        val goalIn = smooth(life, GOAL_AFTER, GOAL_AFTER + BALL_IN) * out
        val burst = if (plot.hops[count] < NONE) t - (plot.hops[count] + GOAL_HOP) else -NONE
        val glowing = smooth(burst, 0f, 0.15f)

        // Where the ball is: on its spot, on a step, or on the arc of a hop.
        var x = BallAt.x
        var y = BallAt.y
        var landed = -NONE
        for (k in 0..count) {
            val from = plot.hops[k]
            if (t < from) break
            val to = if (k < count) stepAt(k, count) else GoalAt
            val toY = if (k < count) to.y - LABEL_HEIGHT / 2f - BALL else to.y
            val duration = if (k < count) HOP else GOAL_HOP
            val u = (t - from) / duration
            if (u >= 1f || still) {
                x = to.x
                y = toY + if (k < count) dipAt(t - from - HOP) else 0f
                landed = from + duration
            } else {
                // One curve through a point above the middle, at a steady pace: up fast, over slowly, down fast.
                val midX = (x + to.x) / 2f
                val midY = (y + toY) / 2f - ARC * 2f
                x = (1f - u) * (1f - u) * x + 2f * u * (1f - u) * midX + u * u * to.x
                y = (1f - u) * (1f - u) * y + 2f * u * (1f - u) * midY + u * u * toY
                landed = -NONE
            }
        }
        val since = t - landed
        // It flattens for a moment where it lands, shrugs at its question, and gathers itself before the climb.
        var squash = if (since >= 0f && since < 0.2f) SQUASH * (1f - smooth(since, 0.06f, 0.2f)) else 0f
        val shrug = sin(PI_F * clamp01((life - MARKS_AT) / SHRUG_FOR))
        if (!still) squash += 0.06f * shrug * shrug
        val ballAlpha = ballIn * (1f - smooth(burst, 0f, LIT))

        light(HaloLight, x, y, HALO, HALO, ballAlpha)
        light(HaloLight, GoalAt.x, GoalAt.y, HALO * 1.1f, HALO * 1.1f, goalIn)
        light(BallLight, x, FLOOR, BALL * 1.5f, BALL * 0.9f, 0.25f * ballAlpha * (1f - 0.6f * clamp01((BallAt.y - y) / FLOOR_FADE)))
        light(GoldLight, GoalAt.x, GOAL_MIRROR, 30f, 12f, 0.25f * goalIn * (1f + glowing))

        // The question: a dashed line towards the goal, and three marks over the ball.
        val leave = min(plot.born + ASK_OUT, plot.stairs)
        val asking = (1f - smooth(t, leave, leave + ASK_FADE)) * out
        val drawn = clamp01((life - LINE_AT) / LINE_FOR) * (1f - smooth(t, leave, leave + ASK_FADE))
        if (drawn > 0f && asking > 0f) {
            drawLine(Color.White, LineFrom, LineFrom + (LineTo - LineFrom) * drawn, strokeWidth = 1f, pathEffect = Dashes, alpha = 0.55f * asking)
        }

        // The stairs, from the lowest label up.
        for (i in 0 until count) {
            if (plot.stairs >= NONE) break
            val at = stepAt(i, count)
            val arrived = if (still) smooth(t - plot.stairs, 0f, STAIR_IN) else easeOut((t - plot.stairs - i * STAIR_GAP) / STAIR_IN)
            if (arrived <= 0f) continue
            val landing = if (plot.hops[i] < NONE) plot.hops[i] + HOP else NONE
            val lit = smooth(t, landing, landing + LIT)
            val shown = arrived * out
            val w = max(LABEL_WIDTH, labels[i].dim.size.width + LABEL_PAD * 2f)
            val cy = at.y + if (still) 0f else STAIR_RISE * (1f - arrived) + dipAt(t - landing)
            light(WhiteLight, at.x, FLOOR + 4f, w * 0.6f, 7f, shown * (0.06f + 0.2f * lit))
            val corner = Offset(at.x - w / 2f, cy - LABEL_HEIGHT / 2f)
            if (lit > 0f) {
                // A soft glow: three wider, fainter copies.
                for (g in 3 downTo 1) {
                    val d = g * 3f
                    drawRoundRect(Color.White, corner - Offset(d, d), Size(w + d * 2f, LABEL_HEIGHT + d * 2f), CornerRadius(LABEL_RADIUS + d), alpha = shown * lit * (0.13f - g * 0.03f))
                }
                drawRoundRect(Color.White, corner, Size(w, LABEL_HEIGHT), CornerRadius(LABEL_RADIUS), alpha = shown * lit)
            }
            drawRoundRect(Color.White, corner, Size(w, LABEL_HEIGHT), CornerRadius(LABEL_RADIUS), alpha = 0.35f * shown * (1f - lit), style = Stroke(1f))
            write(if (lit > 0.5f) labels[i].lit else labels[i].dim, at.x, cy, shown)
        }

        // The goal: a gold disc in a thin ring, its glow, and its name.
        val swell = if (still) 1f else 1f + 0.3f * sin(PI_F * clamp01((life - GOAL_AFTER) / BALL_IN))
        val reach = (GOAL + 40f) * (1f + 0.3f * glowing)
        light(GoldLight, GoalAt.x, GoalAt.y, reach, reach, 0.35f * goalIn * (1f + glowing))
        drawCircle(Color.White, RING * swell, GoalAt, alpha = 0.6f * goalIn, style = Stroke(1f))
        drawCircle(Gold, GOAL, GoalAt, alpha = goalIn)
        write(if (glowing > 0.5f) goal.lit else goal.dim, GoalAt.x, GOAL_LABEL, goalIn)
        if (burst > 0f && burst < BURST && !still) {
            // Short streaks fly out of it and fade.
            val p = burst / BURST
            for (i in 0 until STREAKS) {
                val angle = (i + hash(i * 2 + 1) * 0.6f) / STREAKS * 2f * PI_F
                val from = RING + (30f + 20f * hash(i * 2 + 2)) * easeOut(p)
                val to = from + 12f * (1f - p)
                val direction = Offset(cos(angle), sin(angle))
                drawLine(Gold, GoalAt + direction * from, GoalAt + direction * to, strokeWidth = 3f, cap = StrokeCap.Round, alpha = (1f - p) * out)
            }
        }

        // The ball, with its glow.
        if (ballAlpha > 0f) {
            light(BallLight, x, y, BALL + 30f, BALL + 30f, 0.35f * ballAlpha)
            withTransform({
                translate(x, y + BALL * squash)
                scale(1f + squash * 0.5f, 1f - squash, pivot = Offset.Zero)
            }) {
                drawCircle(BallFace, BALL, Offset.Zero, alpha = ballAlpha)
            }
        }
        if (asking > 0f) {
            Marks.forEachIndexed { i, mark ->
                val u = clamp01((life - MARKS_AT - i * MARK_GAP) / MARK_POP) - 1f
                if (u > -1f) {
                    // Each pops up past its size and settles, then sways.
                    val pop = if (still) 1f else 1f + 2.7f * u * u * u + 1.7f * u * u
                    val sway = if (still) 0f else 8f * sin(life * 6f + i * 2f)
                    val alpha = asking * if (still) smooth(u, -1f, 0f) else 1f
                    withTransform({
                        translate(BallAt.x + mark[0], BallAt.y + mark[1])
                        rotate(mark[2] + sway, pivot = Offset.Zero)
                        scale(pop, pop, pivot = Offset.Zero)
                    }) {
                        drawPath(Mark, Color.White, alpha = alpha, style = Stroke(2f, cap = StrokeCap.Round))
                        drawCircle(Color.White, 1.3f, Offset(0f, 5.4f), alpha = alpha)
                    }
                }
            }
        }
    }
}
