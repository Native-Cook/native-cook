package com.ngocdevv.nativecook.blocks.playground.clothflagmesh

import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

internal const val MESH_COLS = 20
internal const val MESH_ROWS = 14
internal const val MESH_COUNT = MESH_COLS * MESH_ROWS
// Rest distance between neighbouring points, in dp: the banner is 285 × 195 dp.
internal const val SPACING = 15.0
internal const val FLAG_W = (MESH_COLS - 1) * SPACING
internal const val FLAG_H = (MESH_ROWS - 1) * SPACING

// dp/s². A little lighter than the real thing, so the fabric floats.
private const val GRAVITY = 1400.0
// Wind at full strength, and what a gust of 1 adds to it, calm or not (dp/s along +x).
private const val WIND_SPEED = 1300.0
private const val GUST_SPEED = 950.0
// Swirl across the cloth (dp/s along z): waves that travel along the flag at WAVE_SPEED.
private const val SWIRL = 600.0
private const val WAVE_LENGTH = 180.0
private const val WAVE_SPEED = 240.0
// The readout calls full wind 22 knots.
private const val KNOTS_PER_SPEED = 22 / WIND_SPEED
// Drag along the cloth towards the air's velocity (1/s) — also what slows a limp flag in still air.
private const val SKIN = 2.2
// Pressure across a triangle's face, per dp² and dp/s of wind into it.
private const val PRESSURE = 0.03
// Velocity kept per step.
private const val DAMPING = 0.994
// Passes per step: the straight links, then the diagonals and the bending links.
private const val STRETCH_PASSES = 3
private const val SOFT_PASSES = 1
// How strongly each kind of link holds: straight neighbours, diagonals, and two apart (bending).
private const val STRETCH = 1.0
private const val SHEAR = 0.5
private const val BEND = 0.3
// No point may be further from its neighbour toward the pole than the spacing times this.
private const val TETHER = 1.03
// A grabbed point follows the finger by this share of the gap each step, and loses depth.
private const val GRAB_FOLLOW = 0.55
private const val GRAB_FLATTEN = 0.85

/** Wind speed for the readout, in knots: its strength along the flag, with a little swirl. */
internal fun windKnots(level: Double, gust: Double, time: Double): Double {
    val speed = level * WIND_SPEED + GUST_SPEED * gust
    val knots = speed * KNOTS_PER_SPEED
    return knots * (1 + 0.05 * sin(time * 1.7) + 0.03 * sin(time * 4.1))
}

/**
 * Unit normals at every point of a mesh given as x, y, z arrays, from central differences across
 * the grid, turned to face the viewer (+z): the back of the cloth is lit like the front.
 */
internal fun computeNormals(x: DoubleArray, y: DoubleArray, z: DoubleArray, nx: DoubleArray, ny: DoubleArray, nz: DoubleArray) {
    for (r in 0 until MESH_ROWS) {
        for (c in 0 until MESH_COLS) {
            val i = r * MESH_COLS + c
            val l = if (c > 0) i - 1 else i
            val rt = if (c < MESH_COLS - 1) i + 1 else i
            val u = if (r > 0) i - MESH_COLS else i
            val d = if (r < MESH_ROWS - 1) i + MESH_COLS else i
            val tx = x[rt] - x[l]
            val ty = y[rt] - y[l]
            val tz = z[rt] - z[l]
            val bx = x[d] - x[u]
            val by = y[d] - y[u]
            val bz = z[d] - z[u]
            val cx = ty * bz - tz * by
            val cy = tz * bx - tx * bz
            val cz = tx * by - ty * bx
            val len = sqrt(cx * cx + cy * cy + cz * cz)
            if (len < 1e-9) {
                nx[i] = 0.0
                ny[i] = 0.0
                nz[i] = 1.0
                continue
            }
            val s = if (cz < 0) -1 / len else 1 / len
            nx[i] = cx * s
            ny[i] = cy * s
            nz[i] = cz * s
        }
    }
}

/**
 * The banner: a verlet cloth of MESH_COLS × MESH_ROWS points in 3D (x right, y down, z towards the
 * viewer, all in dp), its first column pinned to the pole. Each tick adds gravity and wind, moves
 * every free point by its own velocity (verlet: position minus last position), then pulls the links
 * back to length. Wind acts twice: as drag along the cloth towards the wind's velocity, and as
 * pressure on each triangle across its face, which is what makes a flag fly and flap. It stays
 * cheap: a modest grid, the straight links solved a few times and the diagonal and bending links
 * once per step, and tethers that stop the cloth stretching however few passes it gets. A
 * step-for-step port of the React Native simulation, on arrays allocated once: nothing per step.
 */
internal class ClothFlagMeshCloth(val originX: Double, val originY: Double) {
    val x = DoubleArray(MESH_COUNT)
    val y = DoubleArray(MESH_COUNT)
    val z = DoubleArray(MESH_COUNT)
    val px = DoubleArray(MESH_COUNT)
    val py = DoubleArray(MESH_COUNT)
    val pz = DoubleArray(MESH_COUNT)
    /** Where every point was a step ago, so a frame between steps can be drawn in between. */
    val ox = DoubleArray(MESH_COUNT)
    val oy = DoubleArray(MESH_COUNT)
    val oz = DoubleArray(MESH_COUNT)
    private val ax = DoubleArray(MESH_COUNT)
    private val ay = DoubleArray(MESH_COUNT)
    private val az = DoubleArray(MESH_COUNT)
    /** The air's velocity at each point this step. */
    private val wx = DoubleArray(MESH_COUNT)
    private val wy = DoubleArray(MESH_COUNT)
    private val wz = DoubleArray(MESH_COUNT)
    /** 0 for a pinned point, 1 otherwise. */
    private val free = DoubleArray(MESH_COUNT)
    /** Links as pairs of point indices, their rest lengths and stiffness: the straight ones first. */
    private val linkA = IntArray(MESH_COUNT * 6)
    private val linkB = IntArray(MESH_COUNT * 6)
    private val rest = DoubleArray(MESH_COUNT * 6)
    private val stiff = DoubleArray(MESH_COUNT * 6)
    private var straight = 0
    private var links = 0
    /** Seconds of wind so far, for the travelling swirl. */
    var time = 0.0
        private set

    init {
        for (r in 0 until MESH_ROWS) {
            for (c in 0 until MESH_COLS) {
                val i = r * MESH_COLS + c
                x[i] = originX + c * SPACING
                y[i] = originY + r * SPACING
                // A faint, fixed ripple across the face: a perfectly flat sheet could never buckle into folds.
                z[i] = if (c == 0) 0.0 else 1.5 * sin(c * 1.7 + r * 0.9) * sin(r * 2.3 + 0.4)
                pz[i] = z[i]
                px[i] = x[i]
                py[i] = y[i]
                free[i] = if (c == 0) 0.0 else 1.0
            }
        }
        x.copyInto(ox)
        y.copyInto(oy)
        z.copyInto(oz)
        fun link(a: Int, b: Int, s: Double) {
            val k = links
            linkA[k] = a
            linkB[k] = b
            rest[k] = hypot(x[b] - x[a], y[b] - y[a])
            stiff[k] = s
            links = k + 1
        }
        // Straight neighbours first, row by row from the pole outwards, then the softer links.
        for (r in 0 until MESH_ROWS) {
            for (c in 0 until MESH_COLS) {
                val i = r * MESH_COLS + c
                if (c + 1 < MESH_COLS) link(i, i + 1, STRETCH)
                if (r + 1 < MESH_ROWS) link(i, i + MESH_COLS, STRETCH)
            }
        }
        straight = links
        for (r in 0 until MESH_ROWS) {
            for (c in 0 until MESH_COLS) {
                val i = r * MESH_COLS + c
                if (c + 1 < MESH_COLS && r + 1 < MESH_ROWS) {
                    link(i, i + MESH_COLS + 1, SHEAR)
                    link(i + 1, i + MESH_COLS, SHEAR)
                }
                if (c + 2 < MESH_COLS) link(i, i + 2, BEND)
                if (r + 2 < MESH_ROWS) link(i, i + 2 * MESH_COLS, BEND)
            }
        }
    }

    /** Copies every point (position, velocity and last drawn position) of `from` into this cloth. */
    fun copyFrom(from: ClothFlagMeshCloth) {
        from.x.copyInto(x)
        from.y.copyInto(y)
        from.z.copyInto(z)
        from.px.copyInto(px)
        from.py.copyInto(py)
        from.pz.copyInto(pz)
        from.ox.copyInto(ox)
        from.oy.copyInto(oy)
        from.oz.copyInto(oz)
        time = from.time
    }

    /** Eases every point a share `k` (0…1) of the way to `to`'s, velocity included. */
    fun blendTo(to: ClothFlagMeshCloth, k: Double) {
        for (i in 0 until MESH_COUNT) {
            x[i] += (to.x[i] - x[i]) * k
            y[i] += (to.y[i] - y[i]) * k
            z[i] += (to.z[i] - z[i]) * k
            px[i] += (to.px[i] - px[i]) * k
            py[i] += (to.py[i] - py[i]) * k
            pz[i] += (to.pz[i] - pz[i]) * k
        }
    }

    /** Stops every point where it is. */
    fun still() {
        x.copyInto(px)
        y.copyInto(py)
        z.copyInto(pz)
        x.copyInto(ox)
        y.copyInto(oy)
        z.copyInto(oz)
    }

    /** Pulls point `i` back to within `max` of point `j`, if it is further. */
    private fun tetherTo(i: Int, j: Int, max: Double) {
        val dx = x[i] - x[j]
        val dy = y[i] - y[j]
        val dz = z[i] - z[j]
        val d2 = dx * dx + dy * dy + dz * dz
        if (d2 <= max * max) return
        val k = max / sqrt(d2)
        x[i] = x[j] + dx * k
        y[i] = y[j] + dy * k
        z[i] = z[j] + dz * k
    }

    /**
     * One step of `dt` seconds under air of strength `level` (0…1) plus `gust`. A point being dragged
     * (`grab` ≥ 0) follows (grabX, grabY) and is held there; on release it keeps the finger's velocity.
     */
    fun step(level: Double, gust: Double, dt: Double, grab: Int, grabX: Double, grabY: Double) {
        x.copyInto(ox)
        y.copyInto(oy)
        z.copyInto(oz)
        time += dt
        val t = time

        // The air: a steady push along the flag and two travelling waves across it, at odd angles so
        // the ripples never line up into stripes; then gravity, and drag towards the air's velocity.
        val along = level * WIND_SPEED + GUST_SPEED * gust
        val swirlAmp = SWIRL * (level + 0.6 * gust)
        val k1 = (2 * PI) / WAVE_LENGTH
        val lift = along * 0.03
        for (i in 0 until MESH_COUNT) {
            val u = x[i] - originX
            val v = y[i] - originY
            var swirl = 0.0
            if (swirlAmp > 0) {
                swirl = sin(k1 * u - k1 * WAVE_SPEED * t + 0.6 * sin(0.013 * v + 0.9 * t)) +
                    0.45 * sin(1.7 * k1 * u + 0.021 * v - 1.6 * k1 * WAVE_SPEED * t + 1.3)
            }
            wx[i] = along
            wy[i] = if (lift == 0.0) 0.0 else lift * sin(0.8 * t + 0.01 * u)
            wz[i] = swirlAmp * swirl
            ax[i] = SKIN * (wx[i] - (x[i] - px[i]) / dt)
            ay[i] = GRAVITY + SKIN * (wy[i] - (y[i] - py[i]) / dt)
            az[i] = SKIN * (wz[i] - (z[i] - pz[i]) / dt)
        }

        // Pressure on each triangle, across its face, shared by its three corners.
        if (along > 1) {
            for (r in 0 until MESH_ROWS - 1) {
                for (c in 0 until MESH_COLS - 1) {
                    val i = r * MESH_COLS + c
                    for (half in 0 until 2) {
                        val a = if (half == 0) i else i + 1
                        val b = if (half == 0) i + 1 else i + MESH_COLS + 1
                        val d = i + MESH_COLS
                        val e1x = x[b] - x[a]
                        val e1y = y[b] - y[a]
                        val e1z = z[b] - z[a]
                        val e2x = x[d] - x[a]
                        val e2y = y[d] - y[a]
                        val e2z = z[d] - z[a]
                        // Normal scaled by twice the area.
                        val cx = e1y * e2z - e1z * e2y
                        val cy = e1z * e2x - e1x * e2z
                        val cz = e1x * e2y - e1y * e2x
                        val len = sqrt(cx * cx + cy * cy + cz * cz)
                        if (len < 1e-6) continue
                        // The air against the triangle: its corners' wind less their velocity, averaged.
                        val rvx = (wx[a] + wx[b] + wx[d] - (x[a] - px[a] + x[b] - px[b] + x[d] - px[d]) / dt) / 3
                        val rvy = (wy[a] + wy[b] + wy[d] - (y[a] - py[a] + y[b] - py[b] + y[d] - py[d]) / dt) / 3
                        val rvz = (wz[a] + wz[b] + wz[d] - (z[a] - pz[a] + z[b] - pz[b] + z[d] - pz[d]) / dt) / 3
                        // (n̂ · w) n̂ · area / 3 per corner = (n · w) n / (6 |n|).
                        val push = (PRESSURE * (cx * rvx + cy * rvy + cz * rvz)) / (6 * len)
                        val fx = push * cx
                        val fy = push * cy
                        val fz = push * cz
                        ax[a] += fx; ay[a] += fy; az[a] += fz
                        ax[b] += fx; ay[b] += fy; az[b] += fz
                        ax[d] += fx; ay[d] += fy; az[d] += fz
                    }
                }
            }
        }

        // Verlet: carry on at the last step's velocity, plus this step's push.
        val dt2 = dt * dt
        for (i in 0 until MESH_COUNT) {
            if (free[i] == 0.0) continue
            val nx = x[i] + (x[i] - px[i]) * DAMPING + ax[i] * dt2
            val ny = y[i] + (y[i] - py[i]) * DAMPING + ay[i] * dt2
            val nz = z[i] + (z[i] - pz[i]) * DAMPING + az[i] * dt2
            px[i] = x[i]; py[i] = y[i]; pz[i] = z[i]
            x[i] = nx; y[i] = ny; z[i] = nz
        }

        // The grabbed point eases to the finger; its last position stays, so it carries the finger's speed.
        val held = if (grab in 0 until MESH_COUNT && free[grab] == 1.0) grab else -1
        if (held >= 0) {
            px[held] = x[held]
            py[held] = y[held]
            pz[held] = z[held]
            x[held] += (grabX - x[held]) * GRAB_FOLLOW
            y[held] += (grabY - y[held]) * GRAB_FOLLOW
            z[held] *= GRAB_FLATTEN
        }

        // Pull the links back towards their length; a pinned or held end does not move. The straight
        // links every pass; the soft ones on the last pass only.
        val total = STRETCH_PASSES + SOFT_PASSES
        for (pass in 0 until total) {
            val end = if (pass < total - SOFT_PASSES) straight else links
            for (l in 0 until end) {
                val a = linkA[l]
                val b = linkB[l]
                val wa = if (a == held) 0.0 else free[a]
                val wb = if (b == held) 0.0 else free[b]
                val sum = wa + wb
                if (sum == 0.0) continue
                val dx = x[b] - x[a]
                val dy = y[b] - y[a]
                val dz = z[b] - z[a]
                val d = sqrt(dx * dx + dy * dy + dz * dz)
                if (d < 1e-6) continue
                val diff = ((d - rest[l]) / d) * stiff[l]
                val sa = (diff * wa) / sum
                val sb = (diff * wb) / sum
                x[a] += dx * sa; y[a] += dy * sa; z[a] += dz * sa
                x[b] -= dx * sb; y[b] -= dy * sb; z[b] -= dz * sb
            }
        }

        // A held point drags the cloth with it, as a pinch does: no point may be further from it than
        // they lie apart on the flat banner, so the whole cloth drapes from the finger in broad folds
        // instead of one point being drawn out into a spike.
        if (held >= 0) {
            val hc = held % MESH_COLS
            val hr = held / MESH_COLS
            for (i in 0 until MESH_COUNT) {
                val c = i % MESH_COLS
                if (c == 0 || i == held) continue
                val reach = SPACING * TETHER * hypot((c - hc).toDouble(), (i / MESH_COLS - hr).toDouble())
                tetherTo(i, held, reach)
            }
        }

        // Rows can not stretch: walking out from the pole, each point is kept within TETHER of the
        // one before it, and down each column within TETHER of the one above. Cheap, and it holds
        // the weight that a few passes of the links alone would not. The held point is kept too, so a
        // finger pulling past the cloth's reach draws it taut rather than tearing a point out of it.
        val max = SPACING * TETHER
        for (i in 0 until MESH_COUNT) {
            if (i % MESH_COLS == 0) continue
            tetherTo(i, i - 1, max)
            if (i >= MESH_COLS) tetherTo(i, i - MESH_COLS, max)
        }
    }
}
