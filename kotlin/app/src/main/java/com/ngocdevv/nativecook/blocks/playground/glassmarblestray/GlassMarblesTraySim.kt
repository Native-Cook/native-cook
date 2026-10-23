package com.ngocdevv.nativecook.blocks.playground.glassmarblestray

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Glass marbles on a wooden tray. A step-for-step port of the React Native `marblesSim.ts`, in
// Double, so all three stacks roll the same marbles. Everything lives in the tray's own frame
// (u right, v down, w up out of the floor, dp, origin at the tray's centre), so the physics never
// sees the perspective.
//
// Each tick: the tray's tilt and its small slide follow their targets on springs; gravity along
// the tilted floor, the slide's inertia and the carved cups (shallow bowls that pull a marble to
// their centre) push the marbles; rolling friction slows them; then a few passes of circle-circle
// collisions (with restitution and mass) and walls. Marbles can hop off the floor when the tray
// is shaken, and each one carries a 3D orientation that turns as it rolls, so its swirl rolls too.

internal const val TICK = 1.0 / 60
internal const val MAX_TICKS = 3
// Collisions run on SUBSTEPS slices of each tick.
private const val SUBSTEPS = 2

// autoPlay period (s).
internal const val LOOP = 12.0

// Gravity along the floor (dp/s²), already scaled for a rolling ball (5/7 of free fall).
private const val GRAVITY = 4300.0
// A hop: gravity on a marble in the air, and how much of its fall speed a landing keeps.
private const val AIR_GRAVITY = 5200.0
private const val LANDING = 0.32
// Rolling: speed kept per second, plus a small constant deceleration (dp/s²).
private const val ROLL_KEEP = 0.7
private const val ROLL_FRICTION = 16.0
// Cups: radius (dp), depth (dp) and the extra damping (1/s) of a marble rocking in one.
internal const val CUP_R = 19.0
private const val CUP_DEPTH = 1.4
private const val CUP_DAMP = 2.4
// Restitution of glass on glass, and of glass on the wooden wall.
private const val BOUNCE = 0.82
private const val WALL_BOUNCE = 0.45
// A collision faster than this (dp/s) clicks: a glint, and a haptic when the user is playing.
private const val CLICK_SPEED = 170.0
// Tray springs: the tilt (1/s², 1/s) and the slide.
private const val TILT_K = 150.0
private const val TILT_D = 17.0
private const val SLIDE_K = 260.0
private const val SLIDE_D = 24.0
// How much of the slide's acceleration the marbles feel (they stay put while the tray moves).
private const val INERTIA = 1.0
// The guided return at the end of an autoPlay loop: spring (1/s²) and damping (1/s) to home.
private const val GUIDE_K = 16.0
private const val GUIDE_D = 5.0

internal const val FLASHES = 10
internal const val FLASH_LIFE = 0.22

// autoPlay script (s): at rest, then the tilt traces a circle (one and a quarter turns), then a
// shake, then the marbles settle and are led home.
private const val CIRCLE_FROM = 1.0
private const val CIRCLE_TO = 7.0
private const val CIRCLE_TURNS = 1.25
private const val CIRCLE_TILT = 0.2
internal const val SHAKE_AT = 7.5
private const val SHAKE_TIME = 0.7
private const val SHAKE_SLIDE = 16.0
private const val SHAKE_RATE = 5.0
private const val GUIDE_FROM = 8.7
private const val GUIDE_FULL = 10.4

/** `Math.sign`. */
private fun sign(v: Double) = if (v > 0) 1.0 else if (v < 0) -1.0 else v

internal class Marble(
    val r: Double,
    /** Glass tint, swirl colours (rgb 0…255) and how many vanes the swirl has. */
    val glass: IntArray,
    val vane: IntArray,
    val vane2: IntArray,
    val vanes: Int,
)

/** The cups: a honeycomb of rows of five and four, centred on the floor. */
internal class Tray(val halfW: Double, val halfH: Double, marbles: Int) {
    /** Radius of the floor's corners. */
    val corner = 20.0
    val cupX: DoubleArray
    val cupY: DoubleArray
    /** The cup each marble starts in. */
    val home: IntArray

    init {
        val pitch = max(8.0, min(62.0, ((halfW - 28) * 2) / 4))
        val rowGap = pitch * 0.866
        val rows = max(3, min(9, floor(((halfH - 28) * 2) / rowGap).toInt() + 1))
        val xs = ArrayList<Double>()
        val ys = ArrayList<Double>()
        for (row in 0 until rows) {
            val count = if (row % 2 == 0) 5 else 4
            val y = (row - (rows - 1) / 2.0) * rowGap
            for (c in 0 until count) {
                xs.add((c - (count - 1) / 2.0) * pitch)
                ys.add(y)
            }
        }
        cupX = xs.toDoubleArray()
        cupY = ys.toDoubleArray()
        // Homes: spread over the board, every other cup or so, in a fixed order.
        val step = xs.size.toDouble() / marbles
        home = IntArray(marbles) { i -> min(xs.size - 1, floor(i * step + step * 0.35).toInt()) }
    }
}

internal class TrayInput {
    var tiltX = 0.0
    var tiltY = 0.0
    var slideX = 0.0
    var slideY = 0.0
    /** 0…1: how strongly each marble is led back to its home cup. */
    var guide = 0.0

    fun clear() {
        tiltX = 0.0
        tiltY = 0.0
        slideX = 0.0
        slideY = 0.0
        guide = 0.0
    }
}

internal class World(tray: Tray, set: List<Marble>) {
    val n = set.size
    val x = DoubleArray(n)
    val y = DoubleArray(n)
    val vx = DoubleArray(n)
    val vy = DoubleArray(n)
    /** Height above the floor and its speed: non-zero only in a hop. */
    val z = DoubleArray(n)
    val vz = DoubleArray(n)
    val r = DoubleArray(n)
    val mass = DoubleArray(n)
    /** Orientation of each marble: a 3×3 rotation, row-major, 9 numbers each. */
    val rot = DoubleArray(n * 9)
    /** The tray: tilt about its x and y axes (rad) and their speeds; its slide (dp) and speeds. */
    var tiltX = 0.0
    var tiltY = 0.0
    var tiltVX = 0.0
    var tiltVY = 0.0
    var slideX = 0.0
    var slideY = 0.0
    var slideVX = 0.0
    var slideVY = 0.0
    /** Glints where marbles clicked: position, age (s) and strength. */
    val flashX = DoubleArray(FLASHES)
    val flashY = DoubleArray(FLASHES)
    val flashAge = DoubleArray(FLASHES) { FLASH_LIFE }
    val flashPower = DoubleArray(FLASHES)
    var nextFlash = 0
    /** Clicks this tick (for the haptic), and the hardest one. */
    var clicks = 0
    var hardest = 0.0
    /** Seed of the generator used by shakes and flicks. */
    var seed = 7041

    init {
        for (i in 0 until n) {
            val cup = tray.home[i]
            x[i] = tray.cupX[cup]
            y[i] = tray.cupY[cup]
            r[i] = set[i].r
            mass[i] = set[i].r * set[i].r * set[i].r
            // A different resting turn for each, so the swirls do not line up.
            val a = i * 2.39996
            val b = i * 1.1
            val ca = cos(a)
            val sa = sin(a)
            val cb = cos(b)
            val sb = sin(b)
            // Rz(a)·Rx(b)
            val o = i * 9
            rot[o] = ca
            rot[o + 1] = -sa * cb
            rot[o + 2] = sa * sb
            rot[o + 3] = sa
            rot[o + 4] = ca * cb
            rot[o + 5] = -ca * sb
            rot[o + 6] = 0.0
            rot[o + 7] = sb
            rot[o + 8] = cb
        }
    }

    fun copyFrom(from: World) {
        from.x.copyInto(x)
        from.y.copyInto(y)
        from.vx.copyInto(vx)
        from.vy.copyInto(vy)
        from.z.copyInto(z)
        from.vz.copyInto(vz)
        from.rot.copyInto(rot)
        tiltX = from.tiltX
        tiltY = from.tiltY
        tiltVX = from.tiltVX
        tiltVY = from.tiltVY
        slideX = from.slideX
        slideY = from.slideY
        slideVX = from.slideVX
        slideVY = from.slideVY
        seed = from.seed
    }

    /** mulberry32: the same sequence on every platform. */
    fun random(): Double {
        seed += 0x6D2B79F5
        var t = seed
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return (t xor (t ushr 14)).toUInt().toDouble() / 4294967296.0
    }

    /** Re-squares a marble's rotation (Gram–Schmidt on its rows) so rounding never skews it. */
    private fun orthonormalize(o: Int) {
        val m = rot
        var l = sqrt(m[o] * m[o] + m[o + 1] * m[o + 1] + m[o + 2] * m[o + 2])
        val l2 = sqrt(m[o + 3] * m[o + 3] + m[o + 4] * m[o + 4] + m[o + 5] * m[o + 5])
        // A collapsed or non-finite matrix (never expected): start the marble's turn over.
        if (!(l > 1e-9) || !(l2 > 1e-9) || !(l + l2).isFinite()) {
            for (k in 0 until 9) m[o + k] = if (k % 4 == 0) 1.0 else 0.0
            return
        }
        m[o] /= l
        m[o + 1] /= l
        m[o + 2] /= l
        val d = m[o] * m[o + 3] + m[o + 1] * m[o + 4] + m[o + 2] * m[o + 5]
        m[o + 3] -= d * m[o]
        m[o + 4] -= d * m[o + 1]
        m[o + 5] -= d * m[o + 2]
        l = sqrt(m[o + 3] * m[o + 3] + m[o + 4] * m[o + 4] + m[o + 5] * m[o + 5])
        m[o + 3] /= l
        m[o + 4] /= l
        m[o + 5] /= l
        m[o + 6] = m[o + 1] * m[o + 5] - m[o + 2] * m[o + 4]
        m[o + 7] = m[o + 2] * m[o + 3] - m[o] * m[o + 5]
        m[o + 8] = m[o] * m[o + 4] - m[o + 1] * m[o + 3]
    }

    /** Turns marble [i] by [angle] about the unit axis (ax, ay, az): R ← Rot·R. */
    private fun turn(i: Int, ax: Double, ay: Double, az: Double, angle: Double) {
        if (angle == 0.0) return
        val c = cos(angle)
        val s = sin(angle)
        val t = 1 - c
        val r00 = t * ax * ax + c
        val r01 = t * ax * ay - s * az
        val r02 = t * ax * az + s * ay
        val r10 = t * ax * ay + s * az
        val r11 = t * ay * ay + c
        val r12 = t * ay * az - s * ax
        val r20 = t * ax * az - s * ay
        val r21 = t * ay * az + s * ax
        val r22 = t * az * az + c
        val o = i * 9
        for (col in 0 until 3) {
            val a = rot[o + col]
            val b = rot[o + 3 + col]
            val d = rot[o + 6 + col]
            rot[o + col] = r00 * a + r01 * b + r02 * d
            rot[o + 3 + col] = r10 * a + r11 * b + r12 * d
            rot[o + 6 + col] = r20 * a + r21 * b + r22 * d
        }
    }

    /** Eases every marble's rotation a share [k] towards [from]'s, then squares it again. */
    fun blendRotations(from: World, k: Double) {
        for (i in rot.indices) rot[i] += (from.rot[i] - rot[i]) * k
        for (i in 0 until n) orthonormalize(i * 9)
    }

    /** Eases every marble a share [k] towards [from]'s position and speed. */
    fun blendPositions(from: World, k: Double) {
        for (i in 0 until n) {
            x[i] += (from.x[i] - x[i]) * k
            y[i] += (from.y[i] - y[i]) * k
            vx[i] += (from.vx[i] - vx[i]) * k
            vy[i] += (from.vy[i] - vy[i]) * k
            z[i] += (from.z[i] - z[i]) * k
            vz[i] += (from.vz[i] - vz[i]) * k
        }
    }

    private fun addFlash(fx: Double, fy: Double, power: Double) {
        val f = nextFlash
        flashX[f] = fx
        flashY[f] = fy
        flashAge[f] = 0.0
        flashPower[f] = power
        nextFlash = (f + 1) % FLASHES
    }

    /** A shake: every marble is knocked in a random direction and hops. */
    fun scatter(strength: Double) {
        for (i in 0 until n) {
            val a = random() * PI * 2
            val speed = strength * (220 + 260 * random())
            vx[i] += cos(a) * speed
            vy[i] += sin(a) * speed
            vz[i] = max(vz[i], strength * (160 + 180 * random()))
        }
    }

    /** A flick of marble [i] along (dx, dy) at [speed] dp/s, with a little hop. */
    fun flick(i: Int, dx: Double, dy: Double, speed: Double) {
        var l = hypot(dx, dy)
        if (l == 0.0 || l.isNaN()) l = 1.0
        vx[i] += (dx / l) * speed
        vy[i] += (dy / l) * speed
        vz[i] = max(vz[i], 90.0)
    }

    private fun walls(tray: Tray, i: Int) {
        val rad = r[i]
        val lx = tray.halfW - rad
        val ly = tray.halfH - rad
        // The rounded corners: a marble beyond a corner's centre is held on its arc.
        val cr = max(0.0, tray.corner - rad)
        val cx = lx - cr
        val cy = ly - cr
        val ax = abs(x[i])
        val ay = abs(y[i])
        if (ax > cx && ay > cy && cr > 0) {
            val sx = sign(x[i])
            val sy = sign(y[i])
            val dx = ax - cx
            val dy = ay - cy
            val d = hypot(dx, dy)
            if (d > cr) {
                val nx = (dx / d) * sx
                val ny = (dy / d) * sy
                x[i] = sx * cx + nx * cr
                y[i] = sy * cy + ny * cr
                val vn = vx[i] * nx + vy[i] * ny
                if (vn > 0) {
                    vx[i] -= (1 + WALL_BOUNCE) * vn * nx
                    vy[i] -= (1 + WALL_BOUNCE) * vn * ny
                    if (vn > CLICK_SPEED) addFlash(x[i] + nx * rad, y[i] + ny * rad, min(1.0, vn / 600))
                }
            }
            return
        }
        if (x[i] > lx || x[i] < -lx) {
            val s = sign(x[i])
            x[i] = s * lx
            if (vx[i] * s > 0) {
                if (abs(vx[i]) > CLICK_SPEED) addFlash(s * (lx + rad), y[i], min(1.0, abs(vx[i]) / 600))
                vx[i] = -vx[i] * WALL_BOUNCE
            }
        }
        if (y[i] > ly || y[i] < -ly) {
            val s = sign(y[i])
            y[i] = s * ly
            if (vy[i] * s > 0) {
                if (abs(vy[i]) > CLICK_SPEED) addFlash(x[i], s * (ly + rad), min(1.0, abs(vy[i]) / 600))
                vy[i] = -vy[i] * WALL_BOUNCE
            }
        }
    }

    private fun collide() {
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val dx = x[j] - x[i]
                val dy = y[j] - y[i]
                val minD = r[i] + r[j]
                val d2 = dx * dx + dy * dy
                if (d2 >= minD * minD || d2 == 0.0) continue
                // Marbles at very different heights pass over each other.
                if (abs(z[i] - z[j]) > minD * 0.8) continue
                val d = sqrt(d2)
                val nx = dx / d
                val ny = dy / d
                val mi = mass[i]
                val mj = mass[j]
                val total = mi + mj
                // Push apart in proportion to the other's mass.
                val overlap = minD - d
                x[i] -= nx * overlap * (mj / total)
                y[i] -= ny * overlap * (mj / total)
                x[j] += nx * overlap * (mi / total)
                y[j] += ny * overlap * (mi / total)
                val vn = (vx[j] - vx[i]) * nx + (vy[j] - vy[i]) * ny
                if (vn >= 0) continue
                val impulse = (-(1 + BOUNCE) * vn) / (1 / mi + 1 / mj)
                vx[i] -= (impulse / mi) * nx
                vy[i] -= (impulse / mi) * ny
                vx[j] += (impulse / mj) * nx
                vy[j] += (impulse / mj) * ny
                if (-vn > CLICK_SPEED) {
                    clicks += 1
                    hardest = max(hardest, -vn)
                    addFlash(x[i] + nx * r[i], y[i] + ny * r[i], min(1.0, -vn / 600))
                }
            }
        }
    }

    /** One tick of the tray and its marbles. */
    fun step(tray: Tray, input: TrayInput, dt: Double) {
        clicks = 0
        hardest = 0.0
        // The tray follows its targets on springs; the marbles feel how it accelerates.
        val tax = TILT_K * (input.tiltX - tiltX) - TILT_D * tiltVX
        val tay = TILT_K * (input.tiltY - tiltY) - TILT_D * tiltVY
        tiltVX += tax * dt
        tiltVY += tay * dt
        tiltX += tiltVX * dt
        tiltY += tiltVY * dt
        val sax = SLIDE_K * (input.slideX - slideX) - SLIDE_D * slideVX
        val say = SLIDE_K * (input.slideY - slideY) - SLIDE_D * slideVY
        slideVX += sax * dt
        slideVY += say * dt
        slideX += slideVX * dt
        slideY += slideVY * dt

        // Gravity along the tilted floor: (sin ty, −cos ty · sin tx).
        val gx = GRAVITY * sin(tiltY) - INERTIA * sax
        val gy = GRAVITY * (-cos(tiltY) * sin(tiltX)) - INERTIA * say
        val h = dt / SUBSTEPS
        val keep = ROLL_KEEP.pow(h)
        val cupK = (GRAVITY * 2 * CUP_DEPTH) / (CUP_R * CUP_R)
        repeat(SUBSTEPS) {
            for (i in 0 until n) {
                var ax = 0.0
                var ay = 0.0
                val grounded = z[i] <= 0
                if (grounded) {
                    ax += gx
                    ay += gy
                    // The nearest cup pulls the marble to its centre; the home cup when it is being led home.
                    var best = -1
                    var bestD = CUP_R * CUP_R
                    for (c in tray.cupX.indices) {
                        val dx = tray.cupX[c] - x[i]
                        val dy = tray.cupY[c] - y[i]
                        val d2 = dx * dx + dy * dy
                        if (d2 < bestD) {
                            bestD = d2
                            best = c
                        }
                    }
                    if (best >= 0 && input.guide < 0.5) {
                        ax += cupK * (tray.cupX[best] - x[i]) - CUP_DAMP * vx[i]
                        ay += cupK * (tray.cupY[best] - y[i]) - CUP_DAMP * vy[i]
                    }
                } else {
                    // In the air: it keeps its speed and falls back.
                    vz[i] -= AIR_GRAVITY * h
                }
                if (input.guide > 0) {
                    val cup = tray.home[i]
                    ax += input.guide * (GUIDE_K * (tray.cupX[cup] - x[i]) - GUIDE_D * vx[i])
                    ay += input.guide * (GUIDE_K * (tray.cupY[cup] - y[i]) - GUIDE_D * vy[i])
                }
                vx[i] += ax * h
                vy[i] += ay * h
                if (grounded) {
                    vx[i] *= keep
                    vy[i] *= keep
                    val speed = hypot(vx[i], vy[i])
                    if (speed > 0) {
                        val slow = max(0.0, speed - ROLL_FRICTION * h) / speed
                        vx[i] *= slow
                        vy[i] *= slow
                    }
                }
                val ox = x[i]
                val oy = y[i]
                x[i] += vx[i] * h
                y[i] += vy[i] * h
                if (!grounded || vz[i] > 0) {
                    z[i] += vz[i] * h
                    if (z[i] <= 0) {
                        val hit = -vz[i]
                        z[i] = 0.0
                        vz[i] = if (hit * LANDING > 60) hit * LANDING else 0.0
                        if (hit > CLICK_SPEED * 1.4) {
                            clicks += 1
                            hardest = max(hardest, hit * 0.5)
                        }
                    }
                }
                walls(tray, i)
                // Rolling turns the marble about the axis across its path, by distance over radius.
                val mx = x[i] - ox
                val my = y[i] - oy
                val dist = hypot(mx, my)
                if (dist > 1e-6 && z[i] <= 0) turn(i, -my / dist, mx / dist, 0.0, dist / r[i])
            }
            collide()
        }
        for (i in 0 until n) orthonormalize(i * 9)
        for (f in 0 until FLASHES) if (flashAge[f] < FLASH_LIFE) flashAge[f] += dt
    }
}

/** The tray's tilt and slide that the script asks for at loop time [t], into [out]. */
internal fun scriptPose(t: Double, out: TrayInput) {
    out.clear()
    if (t > CIRCLE_FROM && t < CIRCLE_TO) {
        val u = (t - CIRCLE_FROM) / (CIRCLE_TO - CIRCLE_FROM)
        val bump = sin(PI * u)
        val amp = CIRCLE_TILT * (bump * bump)
        val a = PI * 2 * CIRCLE_TURNS * u - PI / 2
        out.tiltY = amp * cos(a)
        out.tiltX = amp * sin(a)
    }
    if (t > SHAKE_AT && t < SHAKE_AT + SHAKE_TIME) {
        val u = (t - SHAKE_AT) / SHAKE_TIME
        out.slideX = SHAKE_SLIDE * sin(PI * 2 * SHAKE_RATE * (t - SHAKE_AT)) * sin(PI * u)
    }
    if (t > GUIDE_FROM) out.guide = min(1.0, (t - GUIDE_FROM) / (GUIDE_FULL - GUIDE_FROM))
}
