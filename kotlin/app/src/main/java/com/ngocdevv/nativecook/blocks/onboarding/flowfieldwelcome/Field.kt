package com.ngocdevv.nativecook.blocks.onboarding.flowfieldwelcome

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// The field is a square lattice of short strokes. Each one's place and angle are computed every
// frame from how far the field has opened (0 packed, 1 the open vortex), so nothing is tweened
// between two stored pictures.

/** autoPlay: the field opens and closes twice a LOOP. */
internal const val LOOP = 8.6f
internal const val CYCLE = 4.3f
internal const val OPEN_AT = 0.3f
internal const val CLOSE_AT = 2.7f

/** Seconds from packed to the open vortex, and back. */
internal const val MORPH = 1.4f

/** Reduced motion: the two figures cross-fade in this long. */
internal const val FADE = 0.3f

/** After a touch autoPlay waits this long, then closes the field and starts over. */
internal const val USER_HOLD = 3f

/** The canvas: the open lattice plus room for the strokes that are flung out. */
internal const val FIELD = 360f
private const val GRID = 18
private const val COUNT = GRID * GRID
private const val OPEN_SIZE = 260f
private const val PITCH = OPEN_SIZE / (GRID - 1)

// The packed lattice is this much of the open one: 150 dp.
private const val PACKED = 0.58f

// Before it opens, and as the last thing when it closes, the block squeezes to 140 dp.
private const val SQUEEZE = 0.042f
private const val SQUEEZE_FOR = 0.3f

private const val PI_F = PI.toFloat()

// Half-way the lattice is twisted, the outside more than the inside, which tears its corners out.
private const val TWIST = 50f * PI_F / 180f
private const val TWIST_INSIDE = 0.25f

// The outside starts this much of the way later than the inside: the motion ripples outwards.
private const val RIPPLE = 0.06f

// Half-way a few strokes are flung further out, and all of them lean off their line.
private const val FLING = 26f
private const val STRAY = 40f * PI_F / 180f

// Packed, a stroke points along the swirl give or take SPREAD, and twitches by JITTER a few times a second.
private const val SPREAD = 70f * PI_F / 180f
private const val JITTER = 10f * PI_F / 180f
private const val JITTER_HZ = 5f
private val JitterSteps = (LOOP * JITTER_HZ).roundToInt()
private const val JITTER_BLEND = 0.5f

// Open, the strokes circle an eye that itself circles the centre.
private const val EYE = 24f
internal const val EYE_FROM = -PI_F / 2f

/** Radians a second. */
internal const val EYE_SPEED = 1.2f
private const val DASH = 5f
private const val THICK = 1.2f

// A finger pushes the strokes within REPEL_REACH away by up to REPEL, and turns those within TURN_REACH towards itself.
private const val REPEL = 14f
private const val REPEL_REACH = 60f
private const val TURN_REACH = 120f

/** A number from 0 to 1 that depends on [n] alone, the same on every platform. */
private fun hash(n: Int): Float {
    var h = (n xor 0x9e3779b9.toInt()) * 0x85ebca6b.toInt()
    h = h xor (h ushr 13)
    h *= 0xc2b2ae35.toInt()
    h = h xor (h ushr 16)
    return ((h.toLong() and 0xFFFFFFFFL) / 4294967296.0).toFloat()
}

// Per stroke: its home on the open lattice, how far that is from the centre (0 to 1), and its own
// share of the spread, the fling and the stray.
private val HomeX = FloatArray(COUNT) { ((it % GRID) - (GRID - 1) / 2f) * PITCH }
private val HomeY = FloatArray(COUNT) { ((it / GRID) - (GRID - 1) / 2f) * PITCH }
private val Far = FloatArray(COUNT) { hypot(HomeX[it], HomeY[it]) / (OPEN_SIZE / 2f * sqrt(2f)) }
private val SpreadOf = FloatArray(COUNT) { (hash(it * 4 + 1) * 2f - 1f) * SPREAD }
private val FlingOf = FloatArray(COUNT) { hash(it * 4 + 2).pow(3) * FLING }
private val StrayOf = FloatArray(COUNT) { (hash(it * 4 + 3) * 2f - 1f) * STRAY }

private fun clamp01(t: Float) = min(1f, max(0f, t))

private fun ease(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

/**
 * Draws every stroke of the field in a [FIELD] × [FIELD] box, in the current units. [progress] is
 * how far the field has opened and [opening] which way it is going; [clock] is seconds, for the
 * twitch; [eye] is the angle of the vortex's eye round the centre; [fingerX], [fingerY] (from the
 * centre) and [pull] (0 to 1) are the touch.
 */
internal fun DrawScope.drawField(
    progress: Float,
    opening: Boolean,
    clock: Float,
    eye: Float,
    fingerX: Float,
    fingerY: Float,
    pull: Float,
    color: Color,
    alpha: Float = 1f,
) {
    val ticks = (clock % LOOP) * JITTER_HZ
    val step = floor(ticks).toInt() % JitterSteps
    val before = (step + JitterSteps - 1) % JitterSteps
    val u = clamp01((ticks - floor(ticks)) / JITTER_BLEND)
    val blend = u * u * (3f - 2f * u)
    val eyeOut = EYE * ease(progress)
    val eyeX = eyeOut * cos(eye)
    val eyeY = eyeOut * sin(eye)
    val half = FIELD / 2f
    for (i in 0 until COUNT) {
        val far = Far[i]
        // The inside leads both ways.
        val own = clamp01(progress * (1f + RIPPLE) - RIPPLE * (if (opening) far else 1f - far))
        val q = ease(own)
        val tear = sin(PI_F * q)
        val squeeze = sin(PI_F * min(1f, own / SQUEEZE_FOR))
        val scale = PACKED + (1f - PACKED) * q - SQUEEZE * squeeze * squeeze
        val twist = TWIST * tear * (TWIST_INSIDE + (1f - TWIST_INSIDE) * far * far)
        val cosT = cos(twist)
        val sinT = sin(twist)
        var x = (HomeX[i] * cosT - HomeY[i] * sinT) * scale
        var y = (HomeX[i] * sinT + HomeY[i] * cosT) * scale
        val reach = max(hypot(x, y), 0.0001f)
        val fling = FlingOf[i] * tear
        x += x / reach * fling
        y += y / reach * fling
        val twitch = hash(i * 64 + 1000 + before) * (1f - blend) + hash(i * 64 + 1000 + step) * blend
        var angle = atan2(y - eyeY, x - eyeX) + PI_F / 2f + twist + (1f - q) * (SpreadOf[i] + JITTER * (twitch * 2f - 1f)) + tear * StrayOf[i]
        if (pull > 0f) {
            val dx = x - fingerX
            val dy = y - fingerY
            val gap = max(hypot(dx, dy), 0.0001f)
            if (gap < REPEL_REACH) {
                val k = 1f - gap / REPEL_REACH
                x += dx / gap * REPEL * pull * k * k
                y += dy / gap * REPEL * pull * k * k
            }
            if (gap < TURN_REACH) {
                val k = 1f - gap / TURN_REACH
                // A stroke has no head: it turns the short way to the line through the finger.
                var turn = atan2(dy, dx) - angle
                turn -= PI_F * (turn / PI_F).roundToInt()
                angle += turn * pull * k * k
            }
        }
        val hx = cos(angle) * DASH / 2f
        val hy = sin(angle) * DASH / 2f
        drawLine(color, Offset(half + x - hx, half + y - hy), Offset(half + x + hx, half + y + hy), strokeWidth = THICK, cap = StrokeCap.Round, alpha = alpha)
    }
}
