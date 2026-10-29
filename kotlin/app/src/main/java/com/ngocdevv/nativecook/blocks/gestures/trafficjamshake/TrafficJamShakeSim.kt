package com.ngocdevv.nativecook.blocks.gestures.trafficjamshake

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// The jam's physics, a port of TrafficJamShakeSim.swift number for number: every car is a spring
// to a shared ground, leans when it lags, stretches along its width when it moves fast (and grows
// tall in the air), and pushes its neighbours apart through contact springs. A hard bump makes the
// hit car honk, a harder one makes it hop. Fixed step 1/240 s, double precision, so the loop
// (stepped from rest at t = 0 with the scripted input of TrafficJamShakeMotion) is the same on every
// run and in every stack.

/** One car of the pile, in design points: anchor = the front face's bottom centre. */
internal class TrafficJamShakeCar(
    val x: Double,
    val y: Double,
    /** Front face width, body height, cabin height, length (depth of the extrusion). */
    val w: Double,
    val hb: Double,
    val hc: Double,
    val l: Double,
    val color: Int,
    /** Rest angle, radians (positive turns clockwise on screen). */
    val angle: Double,
)

internal object TrafficJamShakeMotion {
    const val LOOP = 8.0
    const val DT = 1.0 / 240
    /** The scripted tap on the front car. */
    const val TAP_TIME = 0.95
    const val TAP_CAR = 7
    const val SHAKE_START = 1.95
    const val SHAKE_END = 5.0
    /** One left-right swing of the ghost finger, seconds. */
    const val SWING = 0.66
    const val AMPLITUDE = 10.0
    /** The residual is faded out over this span so the loop ends exactly at rest. */
    const val FADE_START = 6.4
    const val FADE_END = 7.2
    const val USER_HOLD = 2.0
    const val SETTLE = 0.6
    const val HONK_LIFE = 0.5

    const val GROUND_OMEGA = 30.0
    const val GROUND_ZETA = 0.9
    const val CAR_ZETA = 0.2
    const val CONTACT_K = 1600.0
    const val CONTACT_C = 10.0
    const val HONK_SPEED = 70.0
    const val HOP_SPEED = 100.0
    const val GRAVITY = 2600.0
    const val TAP_LIFT = 380.0
    const val LEAN_PER_POINT = 0.007
    const val LEAN_OMEGA = 13.0
    const val LEAN_ZETA = 0.2
    const val STRETCH_OMEGA = 18.0
    const val STRETCH_ZETA = 0.25

    /** The pile, back to front (paint order). Colours index the body palette. */
    val cars = listOf(
        TrafficJamShakeCar(62.0, 150.0, 80.0, 66.0, 30.0, 54.0, 2, 0.0),
        TrafficJamShakeCar(150.0, 146.0, 78.0, 74.0, 28.0, 52.0, 1, 0.0),
        TrafficJamShakeCar(240.0, 156.0, 84.0, 82.0, 30.0, 58.0, 0, 0.0),
        TrafficJamShakeCar(70.0, 228.0, 104.0, 36.0, 28.0, 66.0, 3, 0.2),
        TrafficJamShakeCar(196.0, 220.0, 120.0, 30.0, 26.0, 72.0, 2, 0.0),
        TrafficJamShakeCar(92.0, 288.0, 132.0, 34.0, 26.0, 70.0, 0, 0.0),
        TrafficJamShakeCar(236.0, 290.0, 110.0, 36.0, 28.0, 66.0, 1, -0.12),
        TrafficJamShakeCar(196.0, 344.0, 112.0, 28.0, 24.0, 60.0, 4, 0.0),
    )

    /** Neighbours that touch in the pile. */
    val pairs = listOf(
        0 to 1, 1 to 2, 0 to 3, 1 to 3, 1 to 4, 2 to 4, 3 to 4, 3 to 5,
        4 to 5, 4 to 6, 2 to 6, 5 to 6, 5 to 7, 6 to 7,
    )

    /** Extrusion direction: 34° up and to the right. */
    val dirX = cos(34 * PI / 180)
    val dirY = -sin(34 * PI / 180)

    fun hash(i: Double): Double {
        val s = sin(i * 12.9898) * 43758.5453
        return s - floor(s)
    }
    fun omega(i: Int): Double = 2 * PI * (1.4 + 1.1 * hash(i + 1.0))
    fun spinSign(i: Int): Double = if (hash(i + 7.0) < 0.5) -1.0 else 1.0

    fun seg(t: Double, a: Double, b: Double): Double = ((t - a) / (b - a)).coerceIn(0.0, 1.0)
    fun smooth(t: Double, a: Double, b: Double): Double {
        val x = seg(t, a, b)
        return x * x * (3 - 2 * x)
    }
    fun easeOutCubic(x: Double): Double = 1 - (1 - x).pow(3)
    fun easeInCubic(x: Double): Double = x * x * x

    /** The ground target of the ghost finger's shake, design points (x, y). */
    fun targetX(t: Double): Double {
        if (t <= SHAKE_START || t >= SHAKE_END) return 0.0
        return envelope(t) * sin(2 * PI * (t - SHAKE_START) / SWING)
    }
    fun targetY(t: Double): Double {
        if (t <= SHAKE_START || t >= SHAKE_END) return 0.0
        return 0.25 * envelope(t) * sin(4 * PI * (t - SHAKE_START) / SWING)
    }
    private fun envelope(t: Double): Double =
        AMPLITUDE * smooth(t, SHAKE_START, 2.6) * (1 - smooth(t, SHAKE_END - 0.55, SHAKE_END))

    /** 1 for most of the loop, easing to 0 before it ends so the last frame is the rest frame. */
    fun window(t: Double): Double = 1 - smooth(t, FADE_START, FADE_END)

    /** The ghost finger: opacity, scale, and whether it is on the tapped car (else the pile's centre). */
    class Finger(val opacity: Double, val scale: Double, val onTap: Boolean)

    fun finger(t: Double): Finger {
        if (t < 1.5) {
            val i = easeOutCubic(seg(t, 0.75, 0.95))
            val o = easeInCubic(seg(t, 1.0, 1.25))
            return Finger(i * (1 - o), 0.8 + 0.2 * i - 0.12 * seg(t, 0.92, 0.98) * (1 - seg(t, 0.98, 1.08)), true)
        }
        val i = easeOutCubic(seg(t, 1.7, 1.95))
        val o = easeInCubic(seg(t, SHAKE_END, SHAKE_END + 0.25))
        return Finger(i * (1 - o), 0.8 + 0.2 * i, false)
    }

    /** Engine idle: a tiny vertical tremble that repeats 36 times per loop. */
    fun tremble(i: Int, t: Double): Double = 0.35 * sin(2 * PI * 36 * t / LOOP + 2 * PI * hash(i + 3.0))

    /** The pile's bounds in design points (with the hard shadow): left, top, right, bottom. */
    val bounds: DoubleArray = run {
        var l = Double.MAX_VALUE
        var t = Double.MAX_VALUE
        var r = -Double.MAX_VALUE
        var b = -Double.MAX_VALUE
        for (c in cars) {
            val top = c.y - c.hb - c.hc + 0.82 * c.l * dirY
            l = min(l, c.x - c.w / 2)
            r = max(r, c.x + c.w / 2 + c.l * dirX)
            t = min(t, top)
            b = max(b, c.y + 6)
        }
        doubleArrayOf(l, t, r, b)
    }
}

internal class TrafficJamShakeSim {
    private val m = TrafficJamShakeMotion
    private val count = m.cars.size

    // Per-car state, struct-of-arrays.
    val px = DoubleArray(count)
    val py = DoubleArray(count)
    val vx = DoubleArray(count)
    val vy = DoubleArray(count)
    val z = DoubleArray(count)
    val vz = DoubleArray(count)
    val a = DoubleArray(count)
    val va = DoubleArray(count)
    val e = DoubleArray(count)
    val ve = DoubleArray(count)
    /** Seconds since the last honk. */
    val honk = DoubleArray(count) { 9.0 }
    private val honkCool = DoubleArray(count)
    private val hopCool = DoubleArray(count)
    private val ax = DoubleArray(count)
    private val ay = DoubleArray(count)

    var gx = 0.0
        private set
    var gy = 0.0
        private set
    private var gvx = 0.0
    private var gvy = 0.0
    /** Where the ground is pulled to: the script's shake, or the finger. */
    var tx = 0.0
    var ty = 0.0
    /** Steps done since the last reset. */
    var steps = 0
        private set
    /** True while the loop's script drives the ground (false under the finger). */
    var scripted = true
    /** Counts hard bumps, for haptics. */
    var bumps = 0
        private set
    private var tapped = false

    private val pi = IntArray(m.pairs.size) { m.pairs[it].first }
    private val pj = IntArray(m.pairs.size) { m.pairs[it].second }
    private val pnx = DoubleArray(m.pairs.size)
    private val pny = DoubleArray(m.pairs.size)
    private val omegas = DoubleArray(count) { m.omega(it) }

    init {
        for (k in m.pairs.indices) {
            val dx = m.cars[pj[k]].x - m.cars[pi[k]].x
            val dy = m.cars[pj[k]].y - m.cars[pi[k]].y
            val len = max(hypot(dx, dy), 1.0)
            pnx[k] = dx / len
            pny[k] = dy / len
        }
    }

    val time: Double get() = steps * m.DT

    fun reset() {
        for (i in 0 until count) {
            px[i] = 0.0; py[i] = 0.0; vx[i] = 0.0; vy[i] = 0.0; z[i] = 0.0; vz[i] = 0.0
            a[i] = 0.0; va[i] = 0.0; e[i] = 0.0; ve[i] = 0.0
            honk[i] = 9.0; honkCool[i] = 0.0; hopCool[i] = 0.0
        }
        gx = 0.0; gy = 0.0; gvx = 0.0; gvy = 0.0; tx = 0.0; ty = 0.0
        steps = 0
        tapped = false
        scripted = true
    }

    /** Multiplies every deviation from rest by [k] (used when a touch picks up a fading loop). */
    fun scale(k: Double) {
        for (i in 0 until count) {
            px[i] *= k; py[i] *= k; vx[i] *= k; vy[i] *= k; z[i] *= k; vz[i] *= k
            a[i] *= k; va[i] *= k; e[i] *= k; ve[i] *= k
        }
        gx *= k; gy *= k; gvx *= k; gvy *= k
    }

    /** Steps until [t] seconds after the last reset; a gap longer than [maxGap] is skipped. */
    fun run(t: Double, maxGap: Double = Double.POSITIVE_INFINITY) {
        val target = floor(t / m.DT).toInt()
        if ((target - steps) * m.DT > maxGap) steps = target - (maxGap / m.DT).toInt()
        while (steps < target) step()
    }

    /** Makes car [i] hop and honk. */
    fun tap(i: Int) {
        if (z[i] < 6) vz[i] = m.TAP_LIFT
        va[i] += 2 * m.spinSign(i)
        honk[i] = 0.0
        honkCool[i] = 0.8
        hopCool[i] = 0.6
    }

    private fun bump(k: Int, speed: Double) {
        if (speed > m.HONK_SPEED && honkCool[k] <= 0) {
            honk[k] = 0.0
            honkCool[k] = 0.8
            bumps++
        }
        if (speed > m.HOP_SPEED && hopCool[k] <= 0 && z[k] < 1) {
            vz[k] = min(300.0, 2.5 * speed)
            va[k] += 1.2 * m.spinSign(k)
            hopCool[k] = 0.6
        }
    }

    private fun step() {
        val dt = m.DT
        val t = time
        if (scripted) {
            tx = m.targetX(t)
            ty = m.targetY(t)
            if (!tapped && t >= m.TAP_TIME) {
                tapped = true
                tap(m.TAP_CAR)
            }
        }

        // The ground follows its target on a stiff spring.
        val w0 = m.GROUND_OMEGA
        gvx += (w0 * w0 * (tx - gx) - 2 * m.GROUND_ZETA * w0 * gvx) * dt
        gvy += (w0 * w0 * (ty - gy) - 2 * m.GROUND_ZETA * w0 * gvy) * dt
        gx += gvx * dt
        gy += gvy * dt

        for (i in 0 until count) {
            val w = omegas[i]
            ax[i] = -w * w * (px[i] - gx) - 2 * m.CAR_ZETA * w * (vx[i] - gvx)
            ay[i] = -w * w * (py[i] - gy) - 2 * m.CAR_ZETA * w * (vy[i] - gvy)
        }
        for (k in pi.indices) {
            val i = pi[k]
            val j = pj[k]
            val nx = pnx[k]
            val ny = pny[k]
            val d = (px[j] - px[i]) * nx + (py[j] - py[i]) * ny
            if (d >= 0) continue
            val vi = vx[i] * nx + vy[i] * ny
            val vj = vx[j] * nx + vy[j] * ny
            val approach = max(0.0, vi - vj)
            val f = m.CONTACT_K * -d + m.CONTACT_C * approach
            ax[i] -= f * nx
            ay[i] -= f * ny
            ax[j] += f * nx
            ay[j] += f * ny
            // The car being run into is the one that complains.
            bump(if (vi > -vj) j else i, approach)
        }

        for (i in 0 until count) {
            vx[i] += ax[i] * dt
            vy[i] += ay[i] * dt
            px[i] += vx[i] * dt
            py[i] += vy[i] * dt

            if (z[i] > 0 || vz[i] > 0) {
                vz[i] -= m.GRAVITY * dt
                z[i] += vz[i] * dt
                if (z[i] <= 0) {
                    val impact = -vz[i]
                    z[i] = 0.0
                    vz[i] = if (impact > 160) impact * 0.28 else 0.0
                    ve[i] += 0.0025 * impact
                    if (impact > 220) {
                        // Neighbours in pair order, as in the Swift list.
                        for (k in pi.indices) {
                            val j: Int
                            val nx: Double
                            val ny: Double
                            if (pi[k] == i) { j = pj[k]; nx = pnx[k]; ny = pny[k] } else if (pj[k] == i) { j = pi[k]; nx = -pnx[k]; ny = -pny[k] } else continue
                            if (z[j] < 1 && hopCool[j] <= 0) {
                                vz[j] += 0.32 * impact
                                vx[j] += 0.12 * impact * nx
                                vy[j] += 0.12 * impact * ny
                                hopCool[j] = 0.6
                            }
                        }
                    }
                }
            }

            val lean = m.LEAN_PER_POINT * (px[i] - gx)
            val wa = m.LEAN_OMEGA
            va[i] += (-wa * wa * (a[i] - lean) - 2 * m.LEAN_ZETA * wa * va[i]) * dt
            a[i] += va[i] * dt

            val stretch = min(0.4, 0.0024 * abs(vx[i] - gvx)) - min(0.18, 0.0006 * abs(vz[i]))
            val we = m.STRETCH_OMEGA
            ve[i] += (-we * we * (e[i] - stretch) - 2 * m.STRETCH_ZETA * we * ve[i]) * dt
            e[i] = (e[i] + ve[i] * dt).coerceIn(-0.35, 0.7)

            honk[i] += dt
            honkCool[i] -= dt
            hopCool[i] -= dt
        }
        steps++
    }
}
