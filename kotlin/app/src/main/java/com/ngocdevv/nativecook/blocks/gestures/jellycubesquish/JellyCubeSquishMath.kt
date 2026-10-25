package com.ngocdevv.nativecook.blocks.gestures.jellycubesquish

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Jelly Cube Squish — the cube's geometry, soft-body deformation and camera.

 World: x right, y away, z up; the plate is the plane z = 0 and the cube stands on it, half size
 HALF, edges rounded by ROUND (each face is an N × N grid pushed onto a rounded box). The camera
 turns YAW about z and looks down ELEV, so the front (−y), left (−x) and top faces show.

 A deformation is a few numbers, applied to every rest point p (h = z / 2·HALF, 0 at the plate):
   squash sq   z shrinks by sq, the sides bulge to keep the volume (most at mid height);
   lean lx, ly the top shears sideways by h²·(lx, ly) — the cube's wobble modes;
   dent        a Gaussian push (radius DENT_R) at point (px, py, pz) along the face normal;
   stretch     (sx, sy, sz) a pull at the same point, falling off over GRAB_R and with height.
 */

internal object JellyMath {
    const val HALF = 72.0
    const val ROUND = 20.0
    const val N = 12
    private const val DENT_R = 34.0
    private const val GRAB_R = 92.0
    private val YAW = 38 * PI / 180
    private val ELEV = 24 * PI / 180
    private val CY = cos(YAW)
    private val SY = sin(YAW)
    private val CE = cos(ELEV)
    private val SE = sin(ELEV)
    private const val CAM_D = 900.0
    private const val ZOOM = 1.45
    /** The camera looks at this height (the cube's middle). */
    private const val LOOK_Z = HALF

    /** Unit vector from the cube toward the camera, in world space. */
    val TO_EYE = doubleArrayOf(-SY * CE, -CY * CE, SE)
    /** Camera right and up in world space (for mapping a drag to a pull). */
    val RIGHT = doubleArrayOf(CY, -SY, 0.0)
    val UP = doubleArrayOf(SY * SE, CY * SE, CE)
    /** Unit vector toward the light (up, left, front). */
    val TO_LIGHT = doubleArrayOf(-0.45, -0.55, 0.7)

    const val FACE_COUNT = 6
    const val PER = (N + 1) * (N + 1)

    /** The six faces: origin corner, u and v axes (each 2·HALF long), outward normal. */
    private val FACES = arrayOf(
        // front (−y), left (−x), top (+z), back (+y), right (+x), bottom (−z)
        intArrayOf(-1, -1, 0, 1, 0, 0, 0, 0, 1, 0, -1, 0),
        intArrayOf(-1, 1, 0, 0, -1, 0, 0, 0, 1, -1, 0, 0),
        intArrayOf(-1, -1, 2, 1, 0, 0, 0, 1, 0, 0, 0, 1),
        intArrayOf(1, 1, 0, -1, 0, 0, 0, 0, 1, 0, 1, 0),
        intArrayOf(1, -1, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0),
        intArrayOf(-1, 1, 0, 1, 0, 0, 0, -1, 0, 0, 0, -1),
    )

    fun clamp(v: Double, a: Double, b: Double) = min(max(v, a), b)

    /** Screen point and scale of a world point, the cube's foot centred at (cx, cy), into out. */
    fun project(cx: Double, cy: Double, x: Double, y: Double, z0: Double, out: DoubleArray) {
        val x1 = x * CY - y * SY
        val y1 = x * SY + y * CY
        val z = z0 - LOOK_Z
        val yc = y1 * SE + z * CE
        val s = ZOOM * CAM_D / (CAM_D + y1 * CE - z * SE)
        out[0] = cx + x1 * s
        out[1] = cy - yc * s
        out[2] = s
    }

    /** Rest points of every face grid (rounded box), face-major, row-major: [x, y, z] each. */
    fun restPoints(): DoubleArray {
        val out = DoubleArray(PER * FACE_COUNT * 3)
        val inner = HALF - ROUND
        var n = 0
        for (f in 0 until FACE_COUNT) {
            val fc = FACES[f]
            for (j in 0..N) for (i in 0..N) {
                val a = i.toDouble() / N * 2
                val b = j.toDouble() / N * 2
                val x = (fc[0] + fc[3] * a + fc[6] * b) * HALF
                val y = (fc[1] + fc[4] * a + fc[7] * b) * HALF
                val z = (fc[2] + fc[5] * a + fc[8] * b) * HALF
                // Push onto the rounded box: nearest point of the inner box plus ROUND outward.
                val cx = clamp(x, -inner, inner)
                val cy = clamp(y, -inner, inner)
                val cz = clamp(z, ROUND, 2 * HALF - ROUND)
                val dx = x - cx
                val dy = y - cy
                val dz = z - cz
                val l = sqrt(dx * dx + dy * dy + dz * dz).takeIf { it != 0.0 } ?: 1.0
                out[n++] = cx + dx / l * ROUND
                out[n++] = cy + dy / l * ROUND
                out[n++] = max(0.0, cz + dz / l * ROUND)
            }
        }
        return out
    }

    /** A deformed point into out. */
    fun deform(d: JellyDeform, x: Double, y: Double, z: Double, out: DoubleArray) {
        val h = clamp(z / (2 * HALF), 0.0, 1.0)
        val sq = clamp(d.sq, -0.25, 0.4)
        val bulge = (1 / sqrt(1 - sq) - 1) * (0.55 + 0.45 * sin(PI * h))
        var px = x * (1 + bulge)
        var py = y * (1 + bulge)
        var pz = z * (1 - sq)
        px += d.lx * h * h * HALF * 0.01
        py += d.ly * h * h * HALF * 0.01
        val ex = x - d.px
        val ey = y - d.py
        val ez = z - d.pz
        val r2 = ex * ex + ey * ey + ez * ez
        if (d.dent != 0.0) {
            val w = exp(-r2 / (2 * DENT_R * DENT_R))
            // The dent, and a soft rim pushed out around it.
            val rr = DENT_R * 1.9
            val rim = exp(-r2 / (2 * rr * rr)) - w
            val k = d.dent * (w - 0.35 * rim)
            px += d.nx * k
            py += d.ny * k
            pz += d.nz * k * (if (z > 2) 1 else 0)
        }
        if (d.sx != 0.0 || d.sy != 0.0 || d.sz != 0.0) {
            val w = exp(-r2 / (2 * GRAB_R * GRAB_R)) * (0.25 + 0.75 * h)
            px += d.sx * w
            py += d.sy * w
            pz += d.sz * w
        }
        out[0] = px
        out[1] = py
        out[2] = max(0.0, pz)
    }
}

/** A deformation (mutable, reused per frame). Dent / grab point and the dent's direction (unit, into the cube). */
internal class JellyDeform {
    var sq = 0.0
    var lx = 0.0
    var ly = 0.0
    var dent = 0.0
    var px = 0.0
    var py = -JellyMath.HALF
    var pz = JellyMath.HALF
    var nx = 0.0
    var ny = 1.0
    var nz = 0.0
    var sx = 0.0
    var sy = 0.0
    var sz = 0.0

    fun rest(): JellyDeform {
        sq = 0.0; lx = 0.0; ly = 0.0; dent = 0.0
        px = 0.0; py = -JellyMath.HALF; pz = JellyMath.HALF
        nx = 0.0; ny = 1.0; nz = 0.0
        sx = 0.0; sy = 0.0; sz = 0.0
        return this
    }
}
