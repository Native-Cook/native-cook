package com.ngocdevv.nativecook.blocks.onboarding.breathingtidewelcome

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// The whole screen is one function of a scene time `s`, in seconds. 0 is the splash at rest. The
// logo fades and the tide rises to AT_LOW, where it waits for a breath; holding runs s to AT_TOP
// (the inhale), and from there it runs by itself through the exhale to AT_C, the welcome at rest.
// Going back runs it on to END, which is the splash again (s jumps to 0).

/** The splash rests this long before the tide comes. */
internal const val SPLASH = 0.5f
internal const val LOGO_OUT = 0.4f
internal const val RISE = 0.4f
internal const val AT_LOW = LOGO_OUT + RISE

/** The inhale: how long the hold lasts. The exhale is slower, or the breath feels mechanical. */
internal const val INHALE = 2f
internal const val AT_TOP = AT_LOW + INHALE
internal const val HOLD = 0.6f
internal const val EXHALE = 3.3f
internal const val AT_OUT = AT_TOP + HOLD + EXHALE

/** The welcome fades in over the tide, which sinks out of view. */
internal const val ARRIVE = 0.6f
internal const val AT_C = AT_OUT + ARRIVE

/** Back to the splash: a cross-fade. */
internal const val BACK = 0.5f
internal const val END = AT_C + BACK

/** An instruction fades in or out in this long. */
internal const val WORD = 0.3f

/** The yellow panel comes down this far as it fades in. */
internal const val PANEL_SLIDE = 30f

// Where the tide's crest is, as fractions of the screen's height, and how much lower its ends are
// at the screen's edges, in dp: the body swells as it rises.
internal const val LOW = 0.751f
internal const val TOP = 0.317f
internal const val DROP_LOW = 34f
internal const val DROP_TOP = 50f

/** The crest is this far under the screen's bottom edge when the tide is away. */
internal const val AWAY = 70f

/** The smile is this much wider at the top of the breath. */
internal const val SMILE_GROW = 0.06f

/** The ornaments float this far up and down, each on its own time. */
internal const val FLOAT = 4f
internal val FLOAT_PERIODS = floatArrayOf(2.5f, 2f, 10f / 3f)

/** The checkbox lands on a spring (mass 1). */
internal const val TICK_STIFFNESS = 300f
internal const val TICK_DAMPING = 14f

/**
 * autoPlay: the tide comes at ENTER_AT of every LOOP seconds, the breath starts at PRESS_AT, the
 * box is ticked at TICK_AT and the splash returns from BACK_AT.
 */
internal const val LOOP = 10f
internal const val ENTER_AT = SPLASH
internal const val PRESS_AT = ENTER_AT + AT_LOW
internal const val TICK_AT = ENTER_AT + AT_C + 0.7f
internal const val BACK_AT = ENTER_AT + AT_C + 1.5f

/** After a touch autoPlay waits this long before it starts over from the splash. */
internal const val USER_HOLD = 3f

internal fun clamp01(t: Float) = t.coerceIn(0f, 1f)

/** Slow at both ends, like a breath. */
internal fun sine(t: Float) = 0.5f - 0.5f * cos(PI.toFloat() * clamp01(t))

internal fun easeOut(t: Float): Float {
    val u = 1f - clamp01(t)
    return 1f - u * u * u
}

/** How full the breath is: 0 at the low tide, 1 at the top. */
internal fun breath(s: Float) = if (s <= AT_TOP) sine((s - AT_LOW) / INHALE) else 1f - sine((s - AT_TOP - HOLD) / EXHALE)

/** How much of the tide is on the screen: it rises from under it, and sinks away as the welcome comes. */
internal fun ashore(s: Float): Float {
    if (s <= AT_LOW) return easeOut((s - LOGO_OUT) / RISE)
    if (s <= AT_OUT) return 1f
    val u = clamp01((s - AT_OUT) / ARRIVE)
    return if (s <= AT_C) 1f - u * u else 0f
}

/** The crest's y on a screen [height] tall. Under reduced motion the tide stays half way. */
internal fun crest(s: Float, height: Float, calm: Boolean): Float {
    if (calm) return height * (LOW + (TOP - LOW) * 0.5f)
    val there = ashore(s)
    val low = height * LOW
    return height + AWAY + (low - height - AWAY) * there + (height * TOP - low) * breath(s)
}

internal fun drop(s: Float, calm: Boolean) = DROP_LOW + (DROP_TOP - DROP_LOW) * if (calm) 0.5f else breath(s)

/** The tide's opacity: under reduced motion it fades in and out instead of travelling, and the breath shows as a glow. */
internal fun tideAlpha(s: Float, calm: Boolean) = if (calm) ashore(s) * (0.6f + 0.4f * breath(s)) else 1f

internal fun logoAlpha(s: Float) = if (s <= AT_C) 1f - clamp01(s / LOGO_OUT) else clamp01((s - AT_C) / BACK)

/** "Breathe in": there from the rise to the top of the breath. */
internal fun inAlpha(s: Float) = clamp01((s - LOGO_OUT) / RISE) * (1f - clamp01((s - AT_TOP) / WORD))

/** "Breathe out": there from the hold to the end of the breath. */
internal fun outAlpha(s: Float) = clamp01((s - AT_TOP - WORD) / WORD) * (1f - clamp01((s - AT_OUT) / WORD))

/** How much of the welcome is there. */
internal fun welcome(s: Float): Float {
    if (s <= AT_C) {
        val u = clamp01((s - AT_OUT) / ARRIVE)
        return u * u * (3f - 2f * u)
    }
    return 1f - clamp01((s - AT_C) / BACK)
}

/** How far up an ornament has floated, [clock] seconds after the block appeared. */
internal fun floated(index: Int, clock: Float) = FLOAT * sin(2f * PI.toFloat() * clock / FLOAT_PERIODS[index % 3] + index * 1.9f)

// The picture on the welcome, in dp from its centre. A part is a shape, a colour (0xRRGGBB) and
// numbers: a polygon (0, then its points), a rounded rectangle (1: x, y, width, height, radius)
// or a circle (2: x, y, radius).
internal val Panel = Color(0xFFFDB703)
internal val Tide = Color(0xFFF47D35)
private val Face = Color(0xFF3D3531)
private val FACE_DISC = 0xfd7203.toFloat()
private val WHITE = 0xffffff.toFloat()
private val SKY = 0x5aa9f8.toFloat()
private val PINK = 0xf9a8c4.toFloat()
private val PLUM = 0x6b4fd8.toFloat()
private val NIGHT = 0x3b2a8c.toFloat()
private val NOTE = 0x2f6bff.toFloat()
private val RED = 0xf0503c.toFloat()

/** A quarter of a ring from the left round to the top: a band of a rainbow. */
private fun band(color: Float, radius: Float, width: Float): FloatArray {
    val points = mutableListOf(0f, color)
    for (i in 0..6) {
        val angle = PI.toFloat() + i / 6f * (PI.toFloat() / 2f)
        points += (radius + width / 2f) * cos(angle)
        points += (radius + width / 2f) * sin(angle)
    }
    for (i in 6 downTo 0) {
        val angle = PI.toFloat() + i / 6f * (PI.toFloat() / 2f)
        points += (radius - width / 2f) * cos(angle)
        points += (radius - width / 2f) * sin(angle)
    }
    return points.toFloatArray()
}

/** A star with four thin points. */
private fun star(color: Float, size: Float): FloatArray {
    val waist = size * 0.2f
    return floatArrayOf(0f, color, 0f, -size, waist, -waist, size, 0f, waist, waist, 0f, size, -waist, waist, -size, 0f, -waist, -waist)
}

private class Ornament(val x: Float, val y: Float, vararg val parts: FloatArray)

/** The ornaments round the phone: where each is, and its parts. */
private val Ornaments = listOf(
    // A cloud.
    Ornament(
        -98f, -34f,
        floatArrayOf(2f, SKY, -8f, 2f, 8f), floatArrayOf(2f, SKY, 2f, -3f, 10f), floatArrayOf(2f, SKY, 12f, 3f, 7f), floatArrayOf(1f, SKY, -16f, 2f, 35f, 9f, 4.5f),
    ),
    // A flower.
    Ornament(
        -76f, -92f,
        floatArrayOf(2f, WHITE, 0f, -8f, 6f), floatArrayOf(2f, WHITE, 7.6f, -2.5f, 6f), floatArrayOf(2f, WHITE, 4.7f, 6.5f, 6f),
        floatArrayOf(2f, WHITE, -4.7f, 6.5f, 6f), floatArrayOf(2f, WHITE, -7.6f, -2.5f, 6f), floatArrayOf(2f, PINK, 0f, 0f, 4.5f),
    ),
    // A moon.
    Ornament(-84f, 52f, floatArrayOf(2f, PLUM, 0f, 0f, 15f), floatArrayOf(2f, NIGHT, 5f, -4f, 11f)),
    // A music note.
    Ornament(
        86f, 84f,
        floatArrayOf(2f, NOTE, -5f, 8f, 5.5f), floatArrayOf(1f, NOTE, -1.5f, -10f, 3f, 19f, 1.5f), floatArrayOf(0f, NOTE, 1.5f, -10f, 11f, -6f, 11f, -1f, 1.5f, -5f),
    ),
    // A rainbow.
    Ornament(112f, -10f, band(RED, 16f, 5f), band(PINK, 9f, 5f)),
    // A play button.
    Ornament(88f, -94f, floatArrayOf(2f, FACE_DISC, 0f, 0f, 14f), floatArrayOf(0f, WHITE, -4f, -6.5f, 7f, 0f, -4f, 6.5f)),
    // Two sparkles.
    Ornament(112f, 36f, star(WHITE, 9f)),
    Ornament(-112f, 6f, star(WHITE, 6f)),
)

// The phone in the picture, and the face disc that overlaps its right half.
private const val PHONE_WIDTH = 118f
private const val PHONE_HEIGHT = 215f
private const val PHONE_RADIUS = 18f
private const val DISC_X = 22f
private const val DISC_Y = 6f
private const val DISC_SIZE = 150f

private fun rgb(color: Float) = Color(0xFF000000.toInt() or color.toInt())

/**
 * The face: two closed eyes and a smile under them, as three arcs. (x, y) is the middle of the
 * line the eyes hang from; at [scale] 1 an eye is 34 wide and the smile 130. [path] is reused.
 */
private fun DrawScope.face(path: Path, x: Float, y: Float, scale: Float, stroke: Float, wide: Float, alpha: Float) {
    val half = 65f * scale * wide
    path.rewind()
    path.moveTo(x - 41f * scale, y)
    path.quadraticTo(x - 24f * scale, y + 24f * scale, x - 7f * scale, y)
    path.moveTo(x + 7f * scale, y)
    path.quadraticTo(x + 24f * scale, y + 24f * scale, x + 41f * scale, y)
    path.moveTo(x - half, y + 16f * scale)
    path.quadraticTo(x, y + 72f * scale, x + half, y + 16f * scale)
    drawPath(path, Face, alpha, Stroke(stroke, cap = StrokeCap.Round))
}

/** The tide at scene time [s], in dp: a fill under an arched edge, and the face that rides on it. */
internal fun DrawScope.paintTide(path: Path, s: Float, calm: Boolean, width: Float, height: Float, color: Color) {
    val alpha = tideAlpha(s, calm).coerceIn(0f, 1f)
    val y = crest(s, height, calm)
    if (alpha <= 0.003f || y >= height) return
    val fall = drop(s, calm)
    path.rewind()
    path.moveTo(0f, y + fall)
    // The edge is an arc through the crest: the curve's middle is half way to its control point.
    path.quadraticTo(width / 2f, y - fall, width, y + fall)
    path.lineTo(width, height)
    path.lineTo(0f, height)
    path.close()
    drawPath(path, color, alpha)
    val full = if (calm) 0.5f else breath(s)
    face(path, width / 2f, y + 36f, 1f, 5f, 1f + SMILE_GROW * full * full, alpha)
}

/** The picture on the welcome round ([cx], [cy]), in dp: a phone, the face on a disc, and the ornaments, which float. */
internal fun DrawScope.paintPicture(path: Path, clock: Float?, cx: Float, cy: Float) {
    drawRoundRect(Color.White, Offset(cx - PHONE_WIDTH / 2f, cy - PHONE_HEIGHT / 2f), Size(PHONE_WIDTH, PHONE_HEIGHT), CornerRadius(PHONE_RADIUS, PHONE_RADIUS))
    drawCircle(rgb(FACE_DISC), DISC_SIZE / 2f, Offset(cx + DISC_X, cy + DISC_Y))
    face(path, cx + DISC_X, cy + DISC_Y - 14f, 0.6f, 3f, 1f, 1f)

    Ornaments.forEachIndexed { n, ornament ->
        val x = cx + ornament.x
        val y = cy + ornament.y - if (clock == null) 0f else floated(n, clock)
        for (part in ornament.parts) {
            val color = rgb(part[1])
            when (part[0]) {
                2f -> drawCircle(color, part[4], Offset(x + part[2], y + part[3]))
                1f -> drawRoundRect(color, Offset(x + part[2], y + part[3]), Size(part[4], part[5]), CornerRadius(part[6], part[6]))
                else -> {
                    path.rewind()
                    var i = 2
                    while (i + 1 < part.size) {
                        if (i == 2) path.moveTo(x + part[i], y + part[i + 1]) else path.lineTo(x + part[i], y + part[i + 1])
                        i += 2
                    }
                    path.close()
                    drawPath(path, color)
                }
            }
        }
    }
}
