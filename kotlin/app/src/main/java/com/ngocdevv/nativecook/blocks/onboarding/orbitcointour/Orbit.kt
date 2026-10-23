package com.ngocdevv.nativecook.blocks.onboarding.orbitcointour

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Everything is laid out on a stage of 393 × 852 dp that is scaled to fit the screen, so the same
// numbers place the orbit and the cards on every phone. The orbit is drawn in one pass.

/** autoPlay: the orbit, the three scenes in turn, and back, every LOOP seconds. */
internal const val LOOP = 10.5f
internal const val OPEN_AT = 2f
internal const val SECOND_AT = 4.2f
internal const val THIRD_AT = 6.8f
internal const val CLOSE_AT = 9f

/** Seconds for the push from the orbit into a scene, and for the pull back out. */
internal const val PUSH = 1f

/** Seconds for one scene to slide to the next, and for a swipe to settle. */
internal const val SLIDE = 0.8f
internal const val SETTLE = 0.35f

/** A scene's contents come in over this long, starting REVEAL_AFTER into the slide. */
internal const val REVEAL = 0.9f
internal const val REVEAL_AFTER = 0.5f

/** Reduced motion: the orbit and the scenes cross-fade in this long. */
internal const val FADE = 0.3f

/** After a touch autoPlay waits this long, then goes back to the orbit and starts over. */
internal const val USER_HOLD = 3f

/** The orbit's clock starts again at the pull back, so it is this far on when a loop begins. */
internal const val PHASE_AT_START = LOOP - CLOSE_AT

/** Rings spun by hand coast for this long, ever slower: the curve starts 6.25 times as steep as it is long. */
internal const val COAST = 2f
internal const val COAST_REACH = COAST / 6.25f
internal val CoastCurve = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
internal val InOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
internal const val UNSPIN = 0.6f

internal const val STAGE_WIDTH = 393f
internal const val STAGE_HEIGHT = 852f
internal const val CX = 196.5f
internal const val CY = 355f
internal const val PI_F = PI.toFloat()

internal val Sand = Color(0xFFEDE4D9)
internal val Ink = Color(0xFF37342E)
private val Coral = Color(0xFFFA776B)
private val Grey = Color(0xFFB5B3B6)

/** The background of each scene. */
internal val Grounds = listOf(Sand, Color(0xFF367168), Color(0xFFC9BEB2))

private val Rings = floatArrayOf(120f, 185f, 260f, 350f, 450f)
private const val RING_STROKE = 1.5f
private const val RING_ALPHA = 0.13f

/** Degrees a second along a ring of this radius: the inner rings are faster. */
private fun speedOf(ring: Float) = 1300f / ring

private const val DISC = 184f

// The letter on the disc, a polygon in a 60 × 72 box, drawn 68 dp high.
internal val Letter = Path().apply {
    moveTo(0f, 72f); lineTo(0f, 0f); lineTo(17f, 0f); lineTo(43f, 44f); lineTo(43f, 0f); lineTo(60f, 0f)
    lineTo(60f, 72f); lineTo(43f, 72f); lineTo(17f, 28f); lineTo(17f, 72f); close()
}
private const val LETTER_HEIGHT = 68f

/** The coin of a scene: its icon, the ring it rides and where on it (degrees, at phase 0). */
internal class Coin(val icon: Int, val ring: Float, val angle: Float)

internal val Coins = listOf(Coin(0, 185f, -64f), Coin(1, 260f, 98f), Coin(2, 260f, 238f))
internal const val COIN = 46f

/** Where each scene's coin stops, above its card. */
internal val CoinTop = floatArrayOf(152f, 152f, 127f)

// Small coins that keep flipping (ring, angle, turn), and plain dots (ring, angle, radius).
private val Moons = listOf(floatArrayOf(185f, 172f, 0f), floatArrayOf(260f, 48f, 2f))
private const val MOON = 17f
private const val MOON_FLIP = 1.6f
private val Dots = listOf(floatArrayOf(350f, -62f, 6f), floatArrayOf(185f, 30f, 9f), floatArrayOf(185f, 140f, 9f))
private val DotColors = listOf(Ink, Ink, Grey)

// A coin seen edge-on is this thick.
private const val EDGE = 9f
private const val RIM = 3f

// The push: the rings grow past the camera and turn, and everything on them fades.
private const val PUSH_GROW = 1.5f
private const val PUSH_TURN = PI_F / 2f

/** What the disc becomes in a scene: the block at the top of its card. */
internal class Hero(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float, val color: Color)

internal val Heroes = listOf(
    Hero(CX, 286f, 304f, 260f, 14f, Color(0xFF136F63)),
    Hero(CX, 345f, 240f, 360f, 16f, Color(0xFFE86262)),
    Hero(CX, 248f, 304f, 220f, 14f, Color(0xFF363B42)),
)

internal fun clamp01(t: Float) = min(1f, max(0f, t))

internal fun smooth(t: Float, from: Float, to: Float): Float {
    val u = clamp01((t - from) / (to - from))
    return u * u * (3f - 2f * u)
}

internal fun ease(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

// Mixed in sRGB, channel by channel, as the other two versions do.
private fun tint(a: Color, b: Color, t: Float) = Color(mix(a.red, b.red, t), mix(a.green, b.green, t), mix(a.blue, b.blue, t))

/** Where coin [index] is on its ring, in stage dp. */
internal fun coinPlace(index: Int, phase: Float, spin: Float): Offset {
    val coin = Coins[index]
    val angle = (coin.angle + speedOf(coin.ring) * phase) * PI_F / 180f + spin
    return Offset(CX + cos(angle) * coin.ring, CY + sin(angle) * coin.ring)
}

private val HouseIcon = Path().apply {
    moveTo(6f, 19f); lineTo(20f, 8f); lineTo(34f, 19f)
    moveTo(10f, 18f); lineTo(10f, 32f); lineTo(30f, 32f); lineTo(30f, 18f)
}
private val CardIcon = Path().apply {
    addRoundRect(RoundRect(Rect(5f, 10f, 35f, 31f), CornerRadius(4f)))
    moveTo(5f, 17f); lineTo(35f, 17f)
    arcTo(Rect(24f, 20f, 46f, 42f), 180f, 90f, forceMoveTo = true)
    arcTo(Rect(29f, 25f, 41f, 37f), 180f, 90f, forceMoveTo = true)
}
private val IconLine = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/**
 * A coin at ([cx], [cy]): [flip] is the cosine of its turn about its vertical axis, so 1 is
 * face-on, 0 edge-on and below 0 its blank back. [icon] is 0 the house, 1 the card, 2 the rings,
 * -1 none.
 */
internal fun DrawScope.drawCoin(cx: Float, cy: Float, radius: Float, flip: Float, icon: Int, alpha: Float) {
    if (alpha <= 0f) return
    val k = radius / COIN
    val wide = abs(flip)
    val thick = EDGE * k * sqrt(1f - wide * wide)
    // The back and the band between the two faces, which is what shows when the coin is edge-on.
    drawOval(Ink, Offset(cx - thick / 2f - radius * wide, cy - radius), Size(radius * wide * 2f, radius * 2f), alpha = alpha)
    drawRect(Ink, Offset(cx - thick / 2f, cy - radius), Size(thick, radius * 2f), alpha = alpha)
    withTransform({
        translate(cx + thick / 2f, cy)
        scale(max(wide, 0.001f), 1f, pivot = Offset.Zero)
    }) {
        drawCircle(Ink, radius, Offset.Zero, alpha = alpha)
        drawCircle(Sand, radius - RIM * k, Offset.Zero, alpha = alpha)
        drawCircle(Ink, radius * 0.8f, Offset.Zero, alpha = alpha * 0.3f, style = Stroke(1f))
        if (flip > 0f && icon >= 0) {
            translate(-20f, -20f) {
                when (icon) {
                    0 -> {
                        drawPath(HouseIcon, Ink, alpha = alpha, style = IconLine)
                        drawCircle(Coral, 2.6f, Offset(20f, 23f), alpha = alpha)
                    }
                    1 -> {
                        drawPath(CardIcon, Ink, alpha = alpha, style = IconLine)
                        drawCircle(Coral, 2.6f, Offset(11.5f, 25f), alpha = alpha)
                    }
                    else -> {
                        drawCircle(Ink, 13f, Offset(20f, 20f), alpha = alpha, style = IconLine)
                        drawCircle(Ink, 7f, Offset(20f, 20f), alpha = alpha, style = IconLine)
                        drawCircle(Coral, 2.6f, Offset(20f, 20f), alpha = alpha)
                    }
                }
            }
        }
    }
}

/**
 * The orbit in a canvas [width] × [height] dp, with the stage scaled by [scale] in its middle.
 * [tour] is how far the push into scene [scene] (1 to 3) has gone, [phase] the orbit's clock in
 * seconds and [spin] what the hand added, in radians.
 */
internal fun DrawScope.drawOrbit(width: Float, height: Float, scale: Float, tour: Float, scene: Int, phase: Float, spin: Float) {
    val e = ease(tour)
    val hero = scene - 1
    val target = Heroes[hero]
    // Everything on the rings is gone before the push ends.
    val away = 1f - smooth(e, 0.3f, 0.9f)
    val grow = 1f + PUSH_GROW * e
    val turn = spin + PUSH_TURN * e

    drawRect(tint(Sand, Grounds[hero], e), Offset.Zero, Size(width, height))
    withTransform({
        translate((width - STAGE_WIDTH * scale) / 2f, (height - STAGE_HEIGHT * scale) / 2f)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        // The rings keep their thin line while they grow: the radius is scaled, not the layer.
        for (ring in Rings) drawCircle(Ink, ring * grow, Offset(CX, CY), alpha = RING_ALPHA * away, style = Stroke(RING_STROKE))
        Dots.forEachIndexed { i, dot ->
            val angle = (dot[1] + speedOf(dot[0]) * phase) * PI_F / 180f + turn
            if (away > 0f) drawCircle(DotColors[i], dot[2], Offset(CX + cos(angle) * dot[0] * grow, CY + sin(angle) * dot[0] * grow), alpha = away)
        }
        for (moon in Moons) {
            val angle = (moon[1] + speedOf(moon[0]) * phase) * PI_F / 180f + turn
            drawCoin(CX + cos(angle) * moon[0] * grow, CY + sin(angle) * moon[0] * grow, MOON, cos(MOON_FLIP * phase + moon[2]), -1, away)
        }

        // The disc, on its way to being the top of the scene's card.
        val w = mix(DISC, target.w, e)
        val h = mix(DISC, target.h, e)
        val r = mix(DISC / 2f, target.r, e)
        val x = mix(CX, target.x, e)
        val y = mix(CY, target.y, e)
        drawRoundRect(tint(Coral, target.color, e), Offset(x - w / 2f, y - h / 2f), Size(w, h), CornerRadius(r))
        val letter = 1f - smooth(e, 0f, 0.35f)
        if (letter > 0f) {
            val k = LETTER_HEIGHT / 72f * (w / DISC)
            withTransform({
                translate(x - 30f * k, y - 36f * k)
                scale(k, k, pivot = Offset.Zero)
            }) {
                drawPath(Letter, Sand, alpha = letter)
            }
        }

        Coins.forEachIndexed { i, coin ->
            val angle = (coin.angle + speedOf(coin.ring) * phase) * PI_F / 180f + spin
            if (i == hero) {
                // The scene's coin rides round to the top of the screen, flipping once on the way.
                var arc = -PI_F / 2f - angle
                arc -= 2f * PI_F * (arc / (2f * PI_F)).roundToInt()
                val at = angle + arc * e
                val reach = mix(coin.ring, CY - CoinTop[i], e)
                drawCoin(CX + cos(at) * reach, CY + sin(at) * reach, COIN, cos(2f * PI_F * e), coin.icon, 1f)
            } else {
                val at = angle + PUSH_TURN * e
                drawCoin(CX + cos(at) * coin.ring * grow, CY + sin(at) * coin.ring * grow, COIN, 1f, coin.icon, away)
            }
        }
    }
}
