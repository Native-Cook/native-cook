package com.ngocdevv.nativecook.blocks.onboarding.shapeshufflewelcome

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// The still life: eleven pieces that keep their identity while they are rebuilt into four
// buildings. A composition gives every piece a place, a size, a turn, a shape and a colour; a
// piece it has no use for shrinks to nothing behind a neighbour. Lengths are in dp.

/** A composition rests for REST seconds and takes CHANGE to become the next; four of them make LOOP. */
internal const val REST = 1.4
internal const val CHANGE = 0.8
internal const val STEP = REST + CHANGE
internal const val LOOP = STEP * 4

/** After a touch autoPlay waits this long before it goes back to the first composition and starts over. */
internal const val USER_HOLD = 3.0

// Each piece sets off a little after the one on its left, up to DELAY late, and lands on a spring
// that carries it a few dp past its place.
private const val DELAY = 0.12
private val Land = Spring(stiffness = 150.0, damping = 17.0)

// Shapes and colours do not overshoot: they blend over BLEND seconds.
private const val BLEND = 0.3

// A piece that is thrown follows an arc that takes this long.
private const val ARC = 0.55

// A piece that was dragged away springs back.
private val Return = Spring(stiffness = 220.0, damping = 16.0)
private const val RETURN_FOR = 0.8

// The scene is this wide; pieces are placed from its middle, and from the ground up.
private const val SCENE = 220.0

private val Lightest = Color(0xFFF4F7FC)
private val Light = Color(0xFFD4D8DB)
private val Mid = Color(0xFFADB1B4)
private val Dark = Color(0xFF858B93)
private val Indigo = Color(0xFF4F46E5)
private val Amber = Color(0xFFF59E0B)
private val Teal = Color(0xFF14B8A6)
private val Coral = Color(0xFFF9735B)

private class Spring(val stiffness: Double, val damping: Double)

/**
 * A piece in one composition: centre x from the scene's middle, centre y above the ground, width,
 * height, turn in degrees, radius of the top corners and of the bottom ones, taper (1 brings the
 * top edge to a point: a roof), cut (1 cuts a block into four steps), how high it is thrown on
 * its way here, colour.
 */
internal class Spot(
    val x: Double, val y: Double, val w: Double, val h: Double, val turn: Double,
    val top: Double, val bottom: Double, val taper: Double, val cut: Double, val lift: Double, val color: Color,
)

/**
 * @param steps A block that can be cut into steps; the others can be rounded and tapered.
 * @param turn What a whole loop adds to the piece's rotation.
 */
internal class Piece(val name: String, val steps: Boolean, val turn: Double, val spots: List<Spot>)

/** The pieces, back to front. */
internal val Pieces = listOf(
    Piece("base", steps = true, turn = 360.0, spots = listOf(
        Spot(-80.0, 29.0, 58.0, 58.0, 0.0, 0.0, 0.0, 0.0, 0.0, 40.0, Mid),
        Spot(-82.0, 29.0, 58.0, 58.0, 90.0, 0.0, 0.0, 0.0, 0.0, 0.0, Mid),
        Spot(-61.5, 29.0, 87.0, 58.0, 180.0, 0.0, 0.0, 0.0, 0.0, 0.0, Mid),
        Spot(-76.0, 29.0, 58.0, 58.0, 270.0, 0.0, 0.0, 0.0, 1.0, 0.0, Mid),
    )),
    Piece("backing", steps = false, turn = 360.0, spots = listOf(
        Spot(-22.0, 29.0, 58.0, 58.0, 0.0, 0.0, 0.0, 0.0, 0.0, 50.0, Lightest),
        Spot(-24.0, 29.0, 0.0, 0.0, 90.0, 0.0, 0.0, 0.0, 0.0, 0.0, Lightest),
        Spot(33.0, 58.0, 0.0, 0.0, 90.0, 0.0, 0.0, 0.0, 0.0, 0.0, Lightest),
        Spot(-18.0, 58.0, 0.0, 0.0, 90.0, 0.0, 0.0, 0.0, 0.0, 0.0, Lightest),
    )),
    Piece("stairs", steps = true, turn = 360.0, spots = listOf(
        Spot(-22.0, 29.0, 58.0, 58.0, 0.0, 0.0, 0.0, 0.0, 1.0, 60.0, Indigo),
        Spot(-24.0, 29.0, 58.0, 58.0, 90.0, 0.0, 0.0, 0.0, 0.0, 0.0, Light),
        Spot(33.0, 58.0, 58.0, 116.0, 180.0, 0.0, 0.0, 0.0, 0.0, 0.0, Light),
        Spot(-18.0, 58.0, 58.0, 116.0, 180.0, 0.0, 0.0, 0.0, 0.0, 0.0, Light),
    )),
    Piece("upper left", steps = false, turn = 360.0, spots = listOf(
        Spot(-22.0, 87.0, 58.0, 58.0, 0.0, 0.0, 0.0, 0.0, 0.0, 70.0, Light),
        Spot(-24.0, 29.0, 0.0, 0.0, 90.0, 0.0, 0.0, 0.0, 0.0, 0.0, Light),
        Spot(33.0, 58.0, 0.0, 0.0, 90.0, 0.0, 0.0, 0.0, 0.0, 0.0, Light),
        Spot(-18.0, 58.0, 0.0, 0.0, 90.0, 0.0, 0.0, 0.0, 0.0, 0.0, Light),
    )),
    Piece("upper right", steps = false, turn = 360.0, spots = listOf(
        Spot(36.0, 87.0, 58.0, 58.0, 0.0, 0.0, 0.0, 0.0, 0.0, 80.0, Lightest),
        Spot(86.0, 29.0, 0.0, 0.0, 90.0, 0.0, 0.0, 0.0, 0.0, 0.0, Light),
        Spot(33.0, 58.0, 0.0, 0.0, 90.0, 0.0, 0.0, 0.0, 0.0, 0.0, Light),
        Spot(40.0, 29.0, 0.0, 0.0, 90.0, 0.0, 0.0, 0.0, 0.0, 0.0, Dark),
    )),
    Piece("front", steps = false, turn = 0.0, spots = listOf(
        Spot(36.0, 29.0, 58.0, 58.0, 0.0, 0.0, 0.0, 0.0, 0.0, 50.0, Light),
        Spot(86.0, 29.0, 58.0, 58.0, 90.0, 0.0, 0.0, 0.0, 0.0, 0.0, Light),
        Spot(-11.0, 43.5, 58.0, 87.0, 0.0, 29.0, 0.0, 0.0, 0.0, 0.0, Dark),
        Spot(40.0, 29.0, 58.0, 58.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Dark),
    )),
    Piece("window", steps = false, turn = 0.0, spots = listOf(
        Spot(36.0, 87.0, 30.0, 30.0, 0.0, 15.0, 15.0, 0.0, 0.0, 60.0, Mid),
        Spot(86.0, 22.0, 0.0, 0.0, 0.0, 15.0, 15.0, 0.0, 0.0, 0.0, Mid),
        Spot(33.0, 92.0, 0.0, 0.0, 0.0, 15.0, 15.0, 0.0, 0.0, 0.0, Lightest),
        Spot(-18.0, 92.0, 30.0, 30.0, 0.0, 15.0, 15.0, 0.0, 0.0, 0.0, Lightest),
    )),
    Piece("roof", steps = false, turn = 360.0, spots = listOf(
        Spot(7.0, 137.0, 116.0, 42.0, 0.0, 0.0, 0.0, 1.0, 0.0, 80.0, Mid),
        Spot(-53.0, 79.0, 116.0, 42.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, Dark),
        Spot(33.0, 130.5, 58.0, 29.0, 0.0, 29.0, 0.0, 0.0, 0.0, 0.0, Mid),
        Spot(66.0, 65.0, 110.0, 14.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Mid),
    )),
    Piece("accent", steps = false, turn = 0.0, spots = listOf(
        Spot(91.0, 11.0, 44.0, 22.0, 0.0, 22.0, 0.0, 0.0, 0.0, 40.0, Amber),
        Spot(31.0, 59.0, 30.0, 30.0, 0.0, 15.0, 15.0, 0.0, 0.0, 0.0, Teal),
        Spot(-40.0, 100.0, 30.0, 30.0, 0.0, 15.0, 15.0, 0.0, 0.0, 40.0, Indigo),
        Spot(95.0, 11.0, 44.0, 22.0, 0.0, 22.0, 0.0, 0.0, 0.0, 0.0, Coral),
    )),
    Piece("ball", steps = false, turn = 0.0, spots = listOf(
        Spot(91.0, 44.0, 44.0, 44.0, 0.0, 22.0, 22.0, 0.0, 0.0, 60.0, Amber),
        Spot(31.0, 22.0, 44.0, 44.0, 0.0, 22.0, 22.0, 0.0, 0.0, 0.0, Teal),
        Spot(89.0, 22.0, 44.0, 44.0, 0.0, 22.0, 22.0, 0.0, 0.0, 0.0, Mid),
        Spot(95.0, 11.0, 0.0, 0.0, 0.0, 22.0, 22.0, 0.0, 0.0, 0.0, Coral),
    )),
    Piece("bowl", steps = false, turn = 360.0, spots = listOf(
        Spot(-80.0, 69.0, 44.0, 22.0, 0.0, 0.0, 22.0, 0.0, 0.0, 50.0, Light),
        Spot(86.0, 22.0, 29.0, 44.0, 180.0, 0.0, 14.5, 0.0, 0.0, 0.0, Mid),
        Spot(-11.0, 30.0, 0.0, 0.0, 180.0, 0.0, 14.5, 0.0, 0.0, 0.0, Dark),
        Spot(40.0, 29.0, 0.0, 0.0, 180.0, 0.0, 14.5, 0.0, 0.0, 0.0, Dark),
    )),
)

internal const val COUNT = 4

/** A piece at one moment. */
internal class PieceAt(
    val x: Double, val y: Double, val w: Double, val h: Double, val rotation: Double,
    val top: Double, val bottom: Double, val taper: Double, val cut: Double, val color: Color,
)

private fun clamp01(t: Double) = min(1.0, max(0.0, t))

private fun smooth(t: Double, from: Double, to: Double): Double {
    val u = clamp01((t - from) / (to - from))
    return u * u * (3 - 2 * u)
}

/** How far a spring of mass 1 has come from 0 to 1 after [t] seconds; it overshoots. */
private fun spring(t: Double, spring: Spring): Double {
    if (t <= 0) return 0.0
    val w = sqrt(spring.stiffness)
    val z = spring.damping / (2 * w)
    val wd = w * sqrt(1 - z * z)
    return 1 - exp(-z * w * t) * (cos(wd * t) + (z * w / wd) * sin(wd * t))
}

/** How much of a drag is left [t] seconds after the piece was let go. */
internal fun dragLeft(t: Double) = if (t < 0) 1.0 else if (t >= RETURN_FOR) 0.0 else 1 - spring(t, Return)

/** A piece [e] seconds into the change from composition [from] to composition [to]. */
internal fun pieceAt(index: Int, from: Int, to: Int, e: Double): PieceAt {
    val piece = Pieces[index]
    val a = piece.spots[from]
    val b = piece.spots[to]
    val since = e - DELAY * clamp01((b.x + SCENE / 2) / SCENE)
    // Places, sizes and turns land on the spring; shapes and colours blend.
    val p = if (from == to) 1.0 else spring(since, Land)
    val q = if (from == to) 1.0 else smooth(since, 0.0, BLEND)
    fun mix(from: Double, to: Double, by: Double) = from + (to - from) * by
    // A loop's last change brings the rotation round to where it started.
    val turned = b.turn + (if (to < from) piece.turn else 0.0)
    return PieceAt(
        x = mix(a.x, b.x, p),
        y = mix(a.y, b.y, p) + (if (from == to) 0.0 else b.lift * sin(PI * clamp01(since / ARC))),
        w = max(0.0, mix(a.w, b.w, p)),
        h = max(0.0, mix(a.h, b.h, p)),
        rotation = mix(a.turn, turned, p),
        top = mix(a.top, b.top, q),
        bottom = mix(a.bottom, b.bottom, q),
        taper = mix(a.taper, b.taper, q),
        cut = mix(a.cut, b.cut, q),
        color = lerp(a.color, b.color, q.toFloat()),
    )
}

// A quarter circle as one curve.
private const val K = 0.5523f

/**
 * The outline of a piece round its own centre. A block is a four-cornered shape whose top edge
 * narrows with the taper and whose corners are rounded as far as its sides allow; a stepped block
 * is a rectangle whose upper right is cut away in four steps, as deep as the cut.
 */
internal fun Path.outline(steps: Boolean, p: PieceAt) {
    val w = p.w.toFloat()
    val h = p.h.toFloat()
    val l = -w / 2f
    val t = -h / 2f
    rewind()
    if (steps) {
        val cut = p.cut.toFloat()
        moveTo(l, -t)
        lineTo(l, t)
        for (i in 1..4) {
            val x = l + w * i / 4f
            lineTo(x, t + h * (i - 1) / 4f * cut)
            if (i < 4) lineTo(x, t + h * i / 4f * cut)
        }
        lineTo(-l, -t)
        close()
        return
    }
    val inset = w / 2f * p.taper.toFloat()
    val xs = floatArrayOf(l + inset, -l - inset, -l, l)
    val ys = floatArrayOf(t, t, -t, -t)
    val radii = floatArrayOf(p.top.toFloat(), p.top.toFloat(), p.bottom.toFloat(), p.bottom.toFloat())
    for (i in 0 until 4) {
        val before = (i + 3) % 4
        val after = (i + 1) % 4
        val toBefore = hypot(xs[before] - xs[i], ys[before] - ys[i])
        val toAfter = hypot(xs[after] - xs[i], ys[after] - ys[i])
        // Two corners share a side in proportion to their radii when it is too short for both.
        val shareBefore = if (radii[i] + radii[before] > toBefore) toBefore / (radii[i] + radii[before]) else 1f
        val shareAfter = if (radii[i] + radii[after] > toAfter) toAfter / (radii[i] + radii[after]) else 1f
        val r = radii[i] * min(shareBefore, shareAfter)
        val fb = if (toBefore > 0f) r / toBefore else 0f
        val fa = if (toAfter > 0f) r / toAfter else 0f
        val ax = xs[i] + (xs[before] - xs[i]) * fb
        val ay = ys[i] + (ys[before] - ys[i]) * fb
        val bx = xs[i] + (xs[after] - xs[i]) * fa
        val by = ys[i] + (ys[after] - ys[i]) * fa
        if (i == 0) moveTo(ax, ay) else lineTo(ax, ay)
        cubicTo(ax + (xs[i] - ax) * K, ay + (ys[i] - ay) * K, bx + (xs[i] - bx) * K, by + (ys[i] - by) * K, bx, by)
    }
    close()
}
