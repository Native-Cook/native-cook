package com.ngocdevv.nativecook.blocks.playground.spirographdraw

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The geometry of a spirograph set, in model units (dp at a scale of 1) around the ring's centre,
 * plus the pens and the autoPlay script.
 *
 * A gear of `teeth` teeth rolls inside a ring of RING_TEETH teeth. Its centre sits at angle `phi`
 * on a circle of radius R − r; rolling without slipping turns the gear by −(R − r)/r for every
 * radian of `phi`, which is kept as its own running angle `spin` so it stays continuous when the
 * gear is changed. A pen in a hole at distance d and angle ψ on the gear traces a hypotrochoid.
 */

// The ring's pitch radius (where the teeth mesh) and its tooth count; the ring's body is RING_BODY wide.
internal const val RING_R = 150.0
internal const val RING_TEETH = 96
internal const val RING_BODY = 26.0
// Teeth stand TOOTH_DEPTH proud of the pitch circle on both ring and gears.
private const val TOOTH_DEPTH = 3.2
// Everything the pen can reach, and the ring, fits in a square of this half-size.
internal const val BOUNDS = RING_R + RING_BODY + 8

internal class SpiroGear(val teeth: Int, val tint: Int)

/** The gears in the set, smallest first: tinted, translucent plastic. */
internal val Gears = listOf(
    SpiroGear(30, 0xFFE3A13B.toInt()),
    SpiroGear(40, 0xFF3F9C9E.toInt()),
    SpiroGear(56, 0xFFD4604F.toInt()),
    SpiroGear(72, 0xFF6A80C8.toInt()),
)

internal class SpiroInk(val color: Int, val name: String)

/** The pens: real ink colours, laid on the paper with a multiply blend. */
internal val Inks = listOf(
    SpiroInk(0xFF2541A8.toInt(), "Ultramarine"),
    SpiroInk(0xFFD3402A.toInt(), "Vermilion"),
    SpiroInk(0xFF11806C.toInt(), "Viridian"),
    SpiroInk(0xFF2A2830.toInt(), "Graphite"),
)

// Pen holes: distance from the gear's centre as a share of its radius, laid along a spiral.
internal val Holes = doubleArrayOf(0.24, 0.4, 0.56, 0.71, 0.85)
private const val HOLE_TURN = 1.25

/** Pitch radius of a gear. */
internal fun gearRadius(teeth: Int) = RING_R * teeth / RING_TEETH

private fun gcd(a: Int, b: Int): Int {
    var x = a
    var y = b
    while (y != 0) {
        val t = y
        y = x % y
        x = t
    }
    return x
}

/** Turns of the gear round the ring before the pattern closes. */
internal fun patternTurns(teeth: Int) = teeth / gcd(RING_TEETH, teeth)

/** Loops the pattern makes. */
internal fun patternPetals(teeth: Int) = RING_TEETH / gcd(RING_TEETH, teeth)

/** Distance of a pen hole from the gear's centre; `hole` may be fractional while it morphs. */
internal fun holeDistance(hole: Double, radius: Double): Double {
    val i = max(0.0, min((Holes.size - 1).toDouble(), hole))
    val lo = floor(i).toInt()
    val hi = min(Holes.size - 1, lo + 1)
    val f = i - lo
    return radius * (Holes[lo] + (Holes[hi] - Holes[lo]) * f)
}

/** Angle of a pen hole on the gear. */
internal fun holeAngle(hole: Double) = HOLE_TURN * max(0.0, min((Holes.size - 1).toDouble(), hole))

internal fun penX(phi: Double, spin: Double, radius: Double, d: Double, psi: Double) =
    (RING_R - radius) * cos(phi) + d * cos(spin + psi)

internal fun penY(phi: Double, spin: Double, radius: Double, d: Double, psi: Double) =
    (RING_R - radius) * sin(phi) + d * sin(spin + psi)

/** How far the gear turns on itself per radian it rolls round the ring. */
internal fun spinRate(radius: Double) = -(RING_R - radius) / radius

/**
 * The outline of a toothed wheel of `teeth` teeth on a pitch circle of `radius`, teeth pointing
 * outwards (`outward` = 1, a gear) or inwards (−1, the ring's inner edge), as flat x, y pairs.
 */
internal fun toothOutline(teeth: Int, radius: Double, outward: Double, depth: Double = TOOTH_DEPTH): FloatArray {
    // Root, flank up, flat tip, flank down: a trapezoid tooth on each pitch step.
    val ts = doubleArrayOf(0.0, 0.18, 0.32, 0.68, 0.82)
    val hs = doubleArrayOf(-1.0, -1.0, 1.0, 1.0, -1.0)
    val pts = FloatArray(teeth * 5 * 2)
    val step = 2 * PI / teeth
    var n = 0
    for (k in 0 until teeth) {
        val a = k * step
        for (j in 0 until 5) {
            val r = radius + outward * hs[j] * depth * 0.5
            pts[n++] = (r * cos(a + ts[j] * step)).toFloat()
            pts[n++] = (r * sin(a + ts[j] * step)).toFloat()
        }
    }
    return pts
}

internal class SpiroCue(
    val at: Double,
    val gear: Int = -1,
    val hole: Int = -1,
    val ink: Int = -1,
    val roll: Double = 0.0,
    val time: Double = 1.0,
    val clear: Boolean = false,
)

// autoPlay (seconds). It starts and ends at rest on a clean sheet with the 40-tooth gear, hole 3 and
// ultramarine ink: rolls the gear five times round (a closed twelve-loop rosette), switches to
// vermilion ink and the 72-tooth gear, moves the pen out to hole 5, rolls three times round (four
// wide loops over the first pattern), tears the sheet off and sets the first gear, hole and ink
// back. Every roll is a whole number of the pattern's turns, so the gear ends where it began.
internal const val LOOP = 16.0
internal const val START_GEAR = 1
internal const val START_HOLE = 2
internal const val START_INK = 0
internal val Script = listOf(
    SpiroCue(0.9, roll = 5.0, time = 5.6),
    SpiroCue(7.0, ink = 1),
    SpiroCue(7.4, gear = 3),
    SpiroCue(7.9, hole = 4),
    SpiroCue(8.4, roll = 3.0, time = 3.6),
    SpiroCue(12.7, clear = true),
    SpiroCue(14.2, gear = 1, hole = 2, ink = 0),
)
