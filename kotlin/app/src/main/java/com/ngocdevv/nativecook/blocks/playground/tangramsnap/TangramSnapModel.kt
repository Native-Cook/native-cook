package com.ngocdevv.nativecook.blocks.playground.tangramsnap

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// The tangram: the seven classic pieces cut from one square, four silhouettes made of them, and the
// springs that move the pieces. A step-for-step port of the React Native `tangramModel.ts`.
//
// Lengths are in units of the small triangle's leg (u). Cut from a square of side 2√2: two large
// triangles (legs 2), one medium (legs √2), two small (legs 1), a square (side 1) and a
// parallelogram (sides 1 and √2, angles 45° and 135°). Areas 2+2+1+1+1+½+½ = 8.
//
// A pose is a centre (the mean of the corners), a turn in steps of 45° and a mirror flag (mirrored
// across the local y axis before turning). Screen y points down, so a positive turn is clockwise.
// Each silhouette is written as the corners of its seven pieces; its slots (one pose per piece)
// are solved from those corners, which also proves the corners are an exact tangram.

internal enum class Kind { LARGE, MEDIUM, SMALL, SQUARE, PARALLELOGRAM }

/** `Math.round`: the nearest integer, halves towards +∞. */
internal fun jsRound(v: Double): Double {
    val r = floor(v)
    return if (v - r >= 0.5) r + 1 else r
}

internal class Slot(
    /** Centre in units, relative to the silhouette's centre. */
    val x: Double,
    val y: Double,
    /** Every (turn step 0…7, flip) that lays the piece exactly on this slot. */
    val turns: IntArray,
    val flips: IntArray,
)

internal class Silhouette(
    val name: String,
    /** The solid colour it fills with when solved (rgb 0…255). */
    val color: IntArray,
    val slots: List<Slot>,
    /** Half its width and height (units), for layout. */
    val halfW: Double,
    val halfH: Double,
)

private val S2 = sqrt(2.0)
private val H2 = S2 / 2

/** The pieces in a fixed order; slots of every silhouette follow the same order. */
internal val PIECES = listOf(Kind.LARGE, Kind.LARGE, Kind.MEDIUM, Kind.SQUARE, Kind.PARALLELOGRAM, Kind.SMALL, Kind.SMALL)

/** Each kind's corners, unit = small triangle's leg, y down. */
private fun raw(kind: Kind) = when (kind) {
    Kind.LARGE -> doubleArrayOf(0.0, 0.0, 2.0, 0.0, 0.0, -2.0)
    Kind.MEDIUM -> doubleArrayOf(0.0, 0.0, 2.0, 0.0, 1.0, -1.0)
    Kind.SMALL -> doubleArrayOf(0.0, 0.0, 1.0, 0.0, 0.0, -1.0)
    Kind.SQUARE -> doubleArrayOf(0.0, 0.0, 1.0, 0.0, 1.0, -1.0, 0.0, -1.0)
    Kind.PARALLELOGRAM -> doubleArrayOf(0.0, 0.0, 1.0, 0.0, 2.0, -1.0, 1.0, -1.0)
}

private fun centre(points: DoubleArray): Pair<Double, Double> {
    var x = 0.0
    var y = 0.0
    val n = points.size / 2
    for (i in 0 until n) {
        x += points[i * 2]
        y += points[i * 2 + 1]
    }
    return x / n to y / n
}

private fun centred(points: DoubleArray): DoubleArray {
    val (cx, cy) = centre(points)
    return DoubleArray(points.size) { i -> if (i % 2 == 0) points[i] - cx else points[i] - cy }
}

/** Each piece's corners, centred on their mean. */
internal val SHAPES: List<DoubleArray> = PIECES.map { centred(raw(it)) }

/** A shape's corners at a pose (turn in radians, flip 0/1), around (x, y), scaled by [unit]. */
private fun placed(shape: DoubleArray, x: Double, y: Double, angle: Double, flip: Double, unit: Double): DoubleArray {
    val c = cos(angle)
    val s = sin(angle)
    val out = DoubleArray(shape.size)
    for (i in shape.indices step 2) {
        val lx = (if (flip > 0.5) -shape[i] else shape[i]) * unit
        val ly = shape[i + 1] * unit
        out[i] = x + lx * c - ly * s
        out[i + 1] = y + lx * s + ly * c
    }
    return out
}

/** Whether two corner lists are the same set of points, within [eps]. */
private fun sameCorners(a: DoubleArray, b: DoubleArray, eps: Double): Boolean {
    if (a.size != b.size) return false
    for (i in a.indices step 2) {
        var found = false
        for (j in b.indices step 2) {
            if (abs(a[i] - b[j]) < eps && abs(a[i + 1] - b[j + 1]) < eps) {
                found = true
                break
            }
        }
        if (!found) return false
    }
    return true
}

/** Solves the slots of a silhouette given as corners (y up, as drawn on squared paper). */
private fun solve(name: String, color: Int, pieces: List<DoubleArray>): Silhouette {
    // To screen space: y down.
    val down = pieces.map { p -> DoubleArray(p.size) { i -> if (i % 2 == 1) -p[i] else p[i] } }
    var minX = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    for (p in down) {
        for (i in p.indices step 2) {
            minX = min(minX, p[i])
            maxX = max(maxX, p[i])
            minY = min(minY, p[i + 1])
            maxY = max(maxY, p[i + 1])
        }
    }
    val mx = (minX + maxX) / 2
    val my = (minY + maxY) / 2
    val slots = down.mapIndexed { k, corners ->
        val (cx, cy) = centre(corners)
        val turns = ArrayList<Int>()
        val flips = ArrayList<Int>()
        for (flip in 0 until 2) {
            for (turn in 0 until 8) {
                val at = placed(SHAPES[k], cx, cy, (turn * PI) / 4, flip.toDouble(), 1.0)
                if (sameCorners(at, corners, 1e-6)) {
                    turns.add(turn)
                    flips.add(flip)
                }
            }
        }
        check(turns.isNotEmpty()) { "Tangram $name: piece $k is not a ${PIECES[k]}" }
        Slot(cx - mx, cy - my, turns.toIntArray(), flips.toIntArray())
    }
    val rgb = intArrayOf((color shr 16) and 255, (color shr 8) and 255, color and 255)
    return Silhouette(name, rgb, slots, (maxX - minX) / 2, (maxY - minY) / 2)
}

private fun d(vararg v: Double) = v

// The silhouettes, piece by piece in PIECES order: large, large, medium, square, parallelogram,
// small, small. Corners in units, y up.
internal val SILHOUETTES = listOf(
    solve(
        "The Cat", 0xE2603A,
        listOf(
            // Body: two large triangles make one big right triangle, chest upright, back sloping to the tail.
            d(0.0, 0.0, 0.0, 2 * S2, S2, S2),
            d(0.0, 0.0, 2 * S2, 0.0, S2, S2),
            // Front paws, against the chest.
            d(0.0, 0.0, -S2, 0.0, 0.0, S2),
            // The head: a diamond on the shoulder.
            d(0.5, 2 * S2 - 0.5, 0.5 + H2, 2 * S2 - 0.5 + H2, 0.5, 2 * S2 - 0.5 + S2, 0.5 - H2, 2 * S2 - 0.5 + H2),
            // The tail, along the ground.
            d(2 * S2, 0.0, 2 * S2 + 1, 0.0, 2 * S2 + 2, 1.0, 2 * S2 + 1, 1.0),
            // Ears.
            d(0.5 - H2, 2 * S2 - 0.5 + H2, 0.5, 2 * S2 - 0.5 + S2, 0.5 - H2, 2 * S2 - 0.5 + H2 + S2),
            d(0.5 + H2, 2 * S2 - 0.5 + H2, 0.5, 2 * S2 - 0.5 + S2, 0.5 + H2, 2 * S2 - 0.5 + H2 + S2),
        ),
    ),
    solve(
        "The Boat", 0x2F6DB5,
        listOf(
            // Mainsail, then the jib before the mast.
            d(1.0, 1.0, 1.0, 3.0, 3.0, 1.0),
            d(1.0, 1.0, 1.0, 1 + 2 * S2, 1 - S2, 1 + S2),
            // A pennant at the masthead.
            d(1.0, 1 + 2 * S2, 1 + S2, 1 + 2 * S2, 1.0, 1 + 3 * S2),
            // The hull: a square between two small triangles.
            d(1.0, 0.0, 2.0, 0.0, 2.0, 1.0, 1.0, 1.0),
            // The keel, raked aft.
            d(1.0, 0.0, 2.0, 0.0, 3.0, -1.0, 2.0, -1.0),
            d(0.0, 1.0, 1.0, 1.0, 1.0, 0.0),
            d(2.0, 0.0, 2.0, 1.0, 3.0, 1.0),
        ),
    ),
    solve(
        "The Runner", 0xE3A52C,
        listOf(
            // Torso, shoulders up.
            d(0.0, 4.0, 2 * S2, 4.0, S2, 4 - S2),
            // The back leg, kicked out behind.
            d(S2, 4 - S2, -S2, 4 - S2, 0.0, 4 - 2 * S2),
            // The front thigh.
            d(S2, 4 - S2, 2 * S2, 4 - 2 * S2, 2 * S2, 4 - S2),
            // The head.
            d(S2 + 0.5, 4.0, S2 + 0.5 + H2, 4 + H2, S2 + 0.5, 4 + S2, S2 + 0.5 - H2, 4 + H2),
            // The front arm, reaching up.
            d(2 * S2, 4.0, 2 * S2 + 1, 4.0, 2 * S2 + 2, 5.0, 2 * S2 + 1, 5.0),
            // The front foot, and the back arm.
            d(2 * S2, 4 - 2 * S2, 2 * S2, 3 - 2 * S2, 2 * S2 + 1, 3 - 2 * S2),
            d(0.0, 4.0, -1.0, 4.0, 0.0, 3.0),
        ),
    ),
    solve(
        "The Swan", 0xEFE6D6,
        listOf(
            // The body: a large triangle on the water, the wing raised over its back.
            d(0.0, 0.0, 2 * S2, 0.0, S2, S2),
            d(2 * S2, S2, 2 * S2, S2 + 2, 2 * S2 - 2, S2),
            // The tail end of the body.
            d(2 * S2, 0.0, 2 * S2, S2, S2, S2),
            // The head, a diamond on the neck.
            d(0.0, 2 * S2, H2, 2 * S2 + H2, 0.0, 3 * S2, -H2, 2 * S2 + H2),
            // The neck, upright.
            d(0.0, S2, H2, S2 + H2, H2, 2 * S2 + H2, 0.0, 2 * S2),
            // The breast.
            d(0.0, 0.0, 0.0, S2, H2, H2),
            d(0.0, S2, S2, S2, H2, H2),
        ),
    ),
)

/** The widest and tallest any silhouette gets (units), for sizing the board. */
internal val MAX_HALF_W = SILHOUETTES.maxOf { it.halfW }
internal val MAX_HALF_H = SILHOUETTES.maxOf { it.halfH }

/**
 * Where each piece rests off the board, around the centre of the loose area: centre (units), turn
 * (steps of 45°) and flip. Spread so none overlap; together 7.3 × 4.3 units.
 */
internal const val LOOSE_HALF_W = 3.7
internal const val LOOSE_HALF_H = 2.2
internal val LOOSE = arrayOf(
    doubleArrayOf(-2.3, -0.45, 3.0, 0.0),
    doubleArrayOf(2.2, 0.6, 7.0, 0.0),
    doubleArrayOf(0.0, -1.05, 4.0, 0.0),
    doubleArrayOf(-0.45, 1.35, 1.0, 0.0),
    doubleArrayOf(1.45, -1.2, 2.0, 1.0),
    doubleArrayOf(-2.0, 1.6, 6.0, 0.0),
    doubleArrayOf(2.85, -1.5, 5.0, 0.0),
)

// Springs (1/s², 1/s): a free piece, a piece held by the finger, the snap into a slot (a little
// overshoot: the magnet), the turn, the flip and the lift.
private val SPRING_FREE = doubleArrayOf(210.0, 26.0)
private val SPRING_HELD = doubleArrayOf(900.0, 52.0)
private val SPRING_SNAP = doubleArrayOf(420.0, 24.0)
private val SPRING_TURN = doubleArrayOf(260.0, 28.0)
private val SPRING_FLIP = doubleArrayOf(180.0, 26.8)
private val SPRING_LIFT = doubleArrayOf(380.0, 36.0)

// autoPlay (s): pieces are set one after another, the figure fills, turns into the swan with its
// pieces, holds, and comes apart back to the cat's empty outline.
internal const val LOOP = 13.0
internal const val PLACE_FROM = 0.8
internal const val PLACE_EVERY = 0.74
internal const val PLACE_TIME = 0.58
internal const val MORPH_AT = 7.9
internal const val RESET_AT = 10.3
internal const val MORPH_TIME = 1.2
/** The silhouette the loop morphs into once the cat is done. */
internal const val AUTO_SECOND = 3

/** `a` moved by whole turns to the nearest of `b`. */
internal fun nearestTurn(a: Double, b: Double): Double {
    val full = PI * 2
    return a + full * jsRound((b - a) / full)
}

/**
 * The pose that lays a piece on [slot] with the least turning from its angle, keeping its flip
 * unless [anyFlip]. Null when the slot needs the other face up. Returns (angle, flip).
 */
internal fun slotPose(slot: Slot, angle: Double, flip: Int, anyFlip: Boolean): Pair<Double, Int>? {
    var best = -1
    var bestTurn = 0.0
    var bestCost = Double.POSITIVE_INFINITY
    for (k in slot.turns.indices) {
        if (!anyFlip && slot.flips[k] != flip) continue
        val turn = nearestTurn((slot.turns[k] * PI) / 4, angle)
        val cost = abs(turn - angle) + (if (slot.flips[k] != flip) 1.2 else 0.0)
        if (cost < bestCost) {
            bestCost = cost
            best = k
            bestTurn = turn
        }
    }
    if (best < 0) return null
    return bestTurn to slot.flips[best]
}

/** Whether a piece turned to [angle] (any real angle) matches the slot's turn within [slack] rad. */
internal fun turnMatches(slot: Slot, angle: Double, flip: Int, slack: Double): Boolean {
    for (k in slot.turns.indices) {
        if (slot.flips[k] != flip) continue
        val turn = nearestTurn((slot.turns[k] * PI) / 4, angle)
        if (abs(turn - angle) <= slack) return true
    }
    return false
}

/** A point is inside a convex polygon given as corners (either winding). */
internal fun insideConvex(points: DoubleArray, x: Double, y: Double): Boolean {
    var sign = 0
    val n = points.size / 2
    for (i in 0 until n) {
        val j = (i + 1) % n
        val cross = (points[j * 2] - points[i * 2]) * (y - points[i * 2 + 1]) - (points[j * 2 + 1] - points[i * 2 + 1]) * (x - points[i * 2])
        if (cross != 0.0) {
            val s = if (cross > 0) 1 else -1
            if (sign == 0) sign = s else if (s != sign) return false
        }
    }
    return true
}

/** A convex polygon pulled in by [d] along every edge (corners where the moved edges meet). */
internal fun inset(points: DoubleArray, d: Double): DoubleArray {
    val n = points.size / 2
    // Winding: positive area means the inside is to the left of each edge (y down).
    var area = 0.0
    for (i in 0 until n) {
        val j = (i + 1) % n
        area += points[i * 2] * points[j * 2 + 1] - points[j * 2] * points[i * 2 + 1]
    }
    val side = if (area > 0) 1.0 else -1.0
    val out = DoubleArray(points.size)
    for (i in 0 until n) {
        val h = (i + n - 1) % n
        val j = (i + 1) % n
        // Inward normals of the edges before and after corner i.
        var ax = points[i * 2] - points[h * 2]
        var ay = points[i * 2 + 1] - points[h * 2 + 1]
        var la = hypot(ax, ay)
        var bx = points[j * 2] - points[i * 2]
        var by = points[j * 2 + 1] - points[i * 2 + 1]
        val lb = hypot(bx, by)
        // A degenerate corner (a piece squeezed edge-on): keep the corner rather than divide by zero.
        if (!(la > 1e-6) || !(lb > 1e-6)) {
            out[i * 2] = points[i * 2]
            out[i * 2 + 1] = points[i * 2 + 1]
            continue
        }
        ax /= la
        ay /= la
        bx /= lb
        by /= lb
        val nax = -ay * side
        val nay = ax * side
        val nbx = -by * side
        val nby = bx * side
        // Corner of the two moved edges: along the bisector by d / cos(half the turn).
        val mx = nax + nbx
        val my = nay + nby
        la = mx * nax + my * nay
        if (!(la > 1e-3)) {
            out[i * 2] = points[i * 2]
            out[i * 2 + 1] = points[i * 2 + 1]
            continue
        }
        out[i * 2] = points[i * 2] + (mx * d) / la
        out[i * 2 + 1] = points[i * 2 + 1] + (my * d) / la
    }
    return out
}

/** The seven pieces' poses, speeds, targets, spring modes, slots and drawing order. */
internal class Pieces {
    val x = DoubleArray(7)
    val y = DoubleArray(7)
    val angle = DoubleArray(7)
    /** 0…1: 0 face up, 1 mirrored; drawn as a horizontal squeeze through zero. */
    val flip = DoubleArray(7)
    val lift = DoubleArray(7)
    val vx = DoubleArray(7)
    val vy = DoubleArray(7)
    val va = DoubleArray(7)
    val vf = DoubleArray(7)
    val vl = DoubleArray(7)
    val tx = DoubleArray(7)
    val ty = DoubleArray(7)
    val ta = DoubleArray(7)
    val tf = DoubleArray(7)
    val tl = DoubleArray(7)
    /** Which spring moves the piece: 0 free, 1 held, 2 snapping. */
    val mode = IntArray(7)
    /** The slot it sits in, or -1. */
    val slot = IntArray(7) { -1 }
    /** Drawing order, bottom first. */
    val order = IntArray(7) { it }

    fun copyFrom(from: Pieces) {
        from.x.copyInto(x)
        from.y.copyInto(y)
        from.angle.copyInto(angle)
        from.flip.copyInto(flip)
        from.lift.copyInto(lift)
        from.vx.copyInto(vx)
        from.vy.copyInto(vy)
        from.va.copyInto(va)
        from.vf.copyInto(vf)
        from.vl.copyInto(vl)
        from.tx.copyInto(tx)
        from.ty.copyInto(ty)
        from.ta.copyInto(ta)
        from.tf.copyInto(tf)
        from.tl.copyInto(tl)
        from.mode.copyInto(mode)
        from.slot.copyInto(slot)
        from.order.copyInto(order)
    }

    /** Moves piece [i] to the top of the drawing order. */
    fun raise(i: Int) {
        var at = 0
        for (k in order.indices) if (order[k] == i) at = k
        for (k in at until order.size - 1) order[k] = order[k + 1]
        order[order.size - 1] = i
    }

    /** One tick of every piece's springs. */
    fun step(dt: Double, calm: Boolean) {
        for (i in 0 until 7) {
            val m = mode[i]
            val pos = if (m == 1) SPRING_HELD else if (m == 2) SPRING_SNAP else SPRING_FREE
            // Reduced motion: the snap settles without overshoot.
            val damp = if (calm && m == 2) 2 * sqrt(pos[0]) else pos[1]
            var v = vx[i] + (pos[0] * (tx[i] - x[i]) - damp * vx[i]) * dt
            x[i] += v * dt
            vx[i] = v
            v = vy[i] + (pos[0] * (ty[i] - y[i]) - damp * vy[i]) * dt
            y[i] += v * dt
            vy[i] = v
            v = va[i] + (SPRING_TURN[0] * (ta[i] - angle[i]) - SPRING_TURN[1] * va[i]) * dt
            angle[i] += v * dt
            va[i] = v
            v = vf[i] + (SPRING_FLIP[0] * (tf[i] - flip[i]) - SPRING_FLIP[1] * vf[i]) * dt
            flip[i] += v * dt
            vf[i] = v
            v = vl[i] + (SPRING_LIFT[0] * (tl[i] - lift[i]) - SPRING_LIFT[1] * vl[i]) * dt
            lift[i] += v * dt
            vl[i] = v
        }
    }
}
