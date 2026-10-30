package com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingTheme.bars
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingTheme.fits
import com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding.CareerWebOnboardingTheme.nodes

/** Pure timeline and touch driver ported value-for-value from cwo-motion.ts. */
internal object CareerWebOnboardingMotion {
    const val LOOP = 9.0
    const val RESUME = 2.0
    private const val TAU = PI * 2.0

    object T {
        val markOut = doubleArrayOf(1.0, 1.3)
        val markIn = doubleArrayOf(1.3, 1.85)
        val arrowOut = doubleArrayOf(1.7, 2.0)
        val arrowIn = doubleArrayOf(2.0, 2.6)
        const val pops = 2.6
        const val pick = 2.85
        val grow = doubleArrayOf(2.95, 3.45)
        val tug = doubleArrayOf(3.9, 4.5)
        val ungrow = doubleArrayOf(6.2, 6.7)
        val nudge = doubleArrayOf(7.2, 7.8)
    }

    data class Point(val x: Double, val y: Double)

    fun clamp01(value: Double): Double = when {
        value < 0.0 -> 0.0
        value > 1.0 -> 1.0
        else -> value
    }

    fun seg(t: Double, a: Double, b: Double): Double = clamp01((t - a) / (b - a))

    fun mix(a: Double, b: Double, p: Double): Double = a + (b - a) * p

    fun easeOut(value: Double): Double {
        val u = 1.0 - clamp01(value)
        return 1.0 - u * u * u * u * u
    }

    fun easeIn(value: Double): Double {
        val p = clamp01(value)
        return p * p * p
    }

    fun easeInOut(value: Double): Double {
        val p = clamp01(value)
        return if (p < 0.5) 16.0 * p * p * p * p * p else 1.0 - (-2.0 * p + 2.0).let { it * it * it * it * it } / 2.0
    }

    fun smooth(value: Double): Double {
        val p = clamp01(value)
        return p * p * (3.0 - 2.0 * p)
    }

    fun wobble(tau: Double, period: Double, decay: Double): Double {
        if (tau <= 0.0) return 0.0
        return exp(-tau / decay) * cos(TAU * tau / period)
    }

    private fun tugCurve(t: Double): Double = when {
        t < T.tug[0] -> 0.0
        t < T.tug[1] -> smooth(seg(t, T.tug[0], T.tug[1]))
        else -> wobble(t - T.tug[1], 0.5, 0.32)
    }

    private const val TUG_X = 26.0
    private const val TUG_Y = 30.0
    private val follow = arrayOf(
        doubleArrayOf(0.06, 0.2),
        doubleArrayOf(1.0, 0.0),
        doubleArrayOf(0.06, 0.2),
        doubleArrayOf(0.2, 0.16),
        doubleArrayOf(0.45, 0.08),
        doubleArrayOf(0.45, 0.08),
        doubleArrayOf(0.2, 0.16),
    )

    fun nodeHome(index: Int, t: Double, amb: Double): Point {
        val node = nodes[index]
        val phase = amb / LOOP
        val f = follow[index]
        val g = tugCurve(t - f[1]) * f[0]
        return Point(
            node.x + 2.2 * sin(TAU * ((1 + (index % 2)) * phase + index * 0.17)) + TUG_X * g,
            node.y + 2.8 * sin(TAU * ((2 - (index % 2)) * phase + index * 0.31)) + TUG_Y * g,
        )
    }

    data class Frame(
        val t: Double,
        val mark: Double,
        val arrow: Double,
        val head: Double,
        val pops: Double,
        val grow: DoubleArray,
        val pick: Double,
        val nudge: Double,
        val tugVib: Double,
    )

    fun frameAt(t: Double): Frame {
        var mark = 1.0
        if (t >= T.markOut[0] && t < T.markOut[1]) mark = 1.0 - easeIn(seg(t, T.markOut[0], T.markOut[1]))
        else if (t >= T.markIn[0] && t < T.markIn[1]) mark = easeOut(seg(t, T.markIn[0], T.markIn[1]))
        var arrow = 1.0
        if (t >= T.arrowOut[0] && t < T.arrowOut[1]) arrow = 1.0 - easeIn(seg(t, T.arrowOut[0], T.arrowOut[1]))
        else if (t >= T.arrowIn[0] && t < T.arrowIn[1]) arrow = easeInOut(seg(t, T.arrowIn[0], T.arrowIn[1]))
        val g1 = if (t < T.ungrow[0]) easeOut(seg(t, T.grow[0], T.grow[1])) else 1.0 - easeInOut(seg(t, T.ungrow[0], T.ungrow[1]))
        val pk = t - T.pick
        val np = seg(t, T.nudge[0], T.nudge[1])
        return Frame(
            t = t,
            mark = mark,
            arrow = arrow,
            head = wobble(t - T.arrowIn[1] + 0.05, 0.22, 0.12),
            pops = wobble(t - T.pops, 0.2, 0.12),
            grow = doubleArrayOf(0.0, g1, 0.0),
            pick = if (pk >= 0.0 && pk < 0.7) pk / 0.7 else -1.0,
            nudge = if (np > 0.0 && np < 1.0) sin(PI * np) else 0.0,
            tugVib = if (t >= T.tug[1]) exp(-(t - T.tug[1]) / 0.45) * sin(TAU * (t - T.tug[1]) * 7.0) else 0.0,
        )
    }

    const val N = 7
    val links: Array<IntArray> = buildList {
        bars.forEach { add(it) }
        for (person in fits.indices) fits[person].forEach { role -> add(intArrayOf(person, role)) }
    }.toTypedArray()

    class Driver(t0: Double = 0.0) {
        var t = t0
        var mode = 0 // 0 autoplay, 2 touched/paused
        var last = -10.0
        var amb = t0
        val ox = DoubleArray(N)
        val oy = DoubleArray(N)
        val vx = DoubleArray(N)
        val vy = DoubleArray(N)
        val grow = DoubleArray(3) { -1.0 }
        val growAim = DoubleArray(3)
        val vib = DoubleArray(links.size)
        val vibAt = DoubleArray(links.size) { -10.0 }
        var hold = -1
        var fx = 0.0
        var fy = 0.0
        var gdx = 0.0
        var gdy = 0.0
        var press = 0.0
        var pressAim = 0.0
        var shootAt = -1.0
        val pickAt = DoubleArray(3) { -1.0 }
    }

    fun makeDriver(t: Double = 0.0): Driver = Driver(t)

    fun linkOn(link: Int, growth: DoubleArray): Double = if (link < bars.size) 1.0 else growth[(link - bars.size) / 2]

    private const val K_HOME = 30.0
    private const val K_LINK = 34.0

    data class StepResult(val t: Double, val grow: DoubleArray)

    fun stepDriver(d: Driver, dt: Double, now: Double, auto: Boolean, loopGrow: DoubleArray): StepResult {
        if (d.mode == 2) {
            if (auto && d.hold < 0 && now - d.last >= RESUME) {
                d.mode = 0
                for (person in 0 until 3) d.grow[person] = -1.0
            }
        } else if (auto) {
            d.t = (d.t + dt) % LOOP
        }
        d.amb = (d.amb + dt) % LOOP
        d.press += (d.pressAim - d.press) * (1.0 - exp(-dt * if (d.pressAim > 0.0) 40.0 else 16.0))
        for (person in 0 until 3) {
            if (d.grow[person] >= 0.0) d.grow[person] += (d.growAim[person] - d.grow[person]) * (1.0 - exp(-dt * 7.0))
        }
        val growth = DoubleArray(3) { person -> if (d.grow[person] >= 0.0) d.grow[person] else loopGrow[person] }

        val substeps = 3
        val h = dt / substeps
        repeat(substeps) {
            val px = DoubleArray(N)
            val py = DoubleArray(N)
            val hx = DoubleArray(N)
            val hy = DoubleArray(N)
            for (index in 0 until N) {
                val home = nodeHome(index, d.t, d.amb)
                hx[index] = home.x
                hy[index] = home.y
                px[index] = home.x + d.ox[index]
                py[index] = home.y + d.oy[index]
            }
            val ax = DoubleArray(N)
            val ay = DoubleArray(N)
            for (index in 0 until N) {
                ax[index] = -K_HOME * d.ox[index] - 2.0 * 0.42 * sqrt(K_HOME + 20.0) * d.vx[index]
                ay[index] = -K_HOME * d.oy[index] - 2.0 * 0.42 * sqrt(K_HOME + 20.0) * d.vy[index]
            }
            for (link in links.indices) {
                val on = linkOn(link, growth)
                if (on < 0.5) continue
                val a = links[link][0]
                val b = links[link][1]
                val dx = px[b] - px[a]
                val dy = py[b] - py[a]
                val length = hypot(dx, dy).takeUnless { it == 0.0 } ?: 1.0
                val rest = hypot(hx[b] - hx[a], hy[b] - hy[a])
                val force = K_LINK * (length - rest) * on
                ax[a] += dx / length * force
                ay[a] += dy / length * force
                ax[b] -= dx / length * force
                ay[b] -= dy / length * force
            }
            for (a in 0 until N) {
                for (b in a + 1 until N) {
                    val dx = px[b] - px[a]
                    val dy = py[b] - py[a]
                    val distance = hypot(dx, dy).takeUnless { it == 0.0 } ?: 1.0
                    val minimum = nodes[a].r + nodes[b].r - 6.0
                    if (distance >= minimum) continue
                    val force = (minimum - distance) * 380.0
                    ax[a] -= dx / distance * force
                    ay[a] -= dy / distance * force
                    ax[b] += dx / distance * force
                    ay[b] += dy / distance * force
                }
            }
            for (index in 0 until N) {
                if (index == d.hold) {
                    val targetX = d.fx - d.gdx - hx[index]
                    val targetY = d.fy - d.gdy - hy[index]
                    val nextX = d.ox[index] + (targetX - d.ox[index]) * 0.45
                    val nextY = d.oy[index] + (targetY - d.oy[index]) * 0.45
                    d.vx[index] = (nextX - d.ox[index]) / h
                    d.vy[index] = (nextY - d.oy[index]) / h
                    d.ox[index] = nextX
                    d.oy[index] = nextY
                    continue
                }
                d.vx[index] += ax[index] * h
                d.vy[index] += ay[index] * h
                d.ox[index] += d.vx[index] * h
                d.oy[index] += d.vy[index] * h
            }
        }
        return StepResult(d.t, growth)
    }

    fun touched(d: Driver, now: Double) {
        d.last = now
        if (d.mode == 0) {
            val frame = frameAt(d.t)
            for (person in 0 until 3) {
                d.grow[person] = frame.grow[person]
                d.growAim[person] = if (frame.grow[person] > 0.5) 1.0 else 0.0
            }
        }
        d.mode = 2
    }

    fun pluck(d: Driver, link: Int, amplitude: Double, now: Double) {
        d.vib[link] = amplitude
        d.vibAt[link] = now
    }

    fun vibration(d: Driver, link: Int, now: Double): Double {
        val tau = now - d.vibAt[link]
        if (tau < 0.0 || tau > 1.6) return 0.0
        return d.vib[link] * exp(-tau / 0.4) * sin(TAU * 8.0 * tau)
    }
}
