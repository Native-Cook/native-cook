package com.ngocdevv.nativecook.blocks.gestures.raffledrumspin

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

// Kept as Float for the existing Compose call sites. Motion calculations below use Double.
const val LOOP = 10f
internal const val LOOP_D = 10.0

internal object T {
    val select = doubleArrayOf(.6, .92)
    val fly = doubleArrayOf(.75, 1.35)
    const val flyStagger = .1
    const val landBounce = .3
    val press = doubleArrayOf(1.6, 1.75)
    val spin = doubleArrayOf(1.7, 3.6)
    val eject = doubleArrayOf(3.45, 4.0)
    const val handoff = .18
    val focus = doubleArrayOf(3.5, 3.9)
    val antic = doubleArrayOf(4.0, 4.75)
    val stitches = doubleArrayOf(4.05, 4.55)
    val morph = doubleArrayOf(4.75, 5.7)
    const val burst = 5.5
    val number = doubleArrayOf(5.6, 6.2)
    val sweep = doubleArrayOf(6.2, 6.9)
    val claim = doubleArrayOf(6.35, 6.95)
    const val hold = 7.0
    val claimPress = doubleArrayOf(7.85, 8.0)
    const val drain = .45
    val tear = doubleArrayOf(8.0, 8.6)
    val burn = doubleArrayOf(8.15, 8.6)
    const val dust = .42
    val focusOut = doubleArrayOf(8.6, 9.0)
    val back = doubleArrayOf(8.75, 9.25)
    const val backStagger = .1
    val entered = doubleArrayOf(8.6, 8.8)
    val iconBack = doubleArrayOf(8.55, 8.85)
    val reselect = doubleArrayOf(9.4, 9.72)
}

private object TICKET {
    const val x = 201.0
    const val y = 432.0
    const val w = 300.0
    const val h = 111.0
    const val body = .729
}

private const val IDLE_RATE = .35
private const val TURNS = 3
private const val SPIN_EXTRA = 2.0 * PI * TURNS - IDLE_RATE * LOOP_D
private object CAM {
    const val yaw = .2
    const val pitch = -.27
    const val dist = 6.0
}
private const val BALL_R = .15
private const val INNER = 1.0 - .03 - BALL_R

private object L {
    const val drumX = 205.0
    const val drumY = 178.0
    const val drumR = 70.0
    const val cardY = 561.0
    const val cardW = 167.0
    const val cardH = 202.0
    const val cardGap = 11.0
    const val cardX0 = 17.0
    const val ballDY = 60.0
    const val playW = 368.0
}

private val ICON_BALLS = arrayOf(
    arrayOf(doubleArrayOf(84.0, 28.0)),
    arrayOf(doubleArrayOf(55.0, 24.0), doubleArrayOf(107.0, 24.0)),
    arrayOf(doubleArrayOf(42.0, 20.0), doubleArrayOf(83.0, 20.0), doubleArrayOf(124.0, 20.0)),
)

internal data class Projection(val x: Double, val y: Double, val z: Double, val f: Double)

internal data class Ball(
    var x: Double,
    var y: Double,
    var r: Double,
    var z: Double,
    var rx: Double,
    var ry: Double,
    var rz: Double,
    var a: Double,
    var glow: Double,
    var sq: Double,
)

internal data class Frame(
    val t: Double,
    val theta: Double,
    val omega: Double,
    val speed: Double,
    val from: Int,
    val to: Int,
    val selK: Double,
    val sweep: Double,
    val check: Double,
    val strip: Double,
    val iconA: Array<DoubleArray>,
    val balls: List<Ball>,
    val hero: Ball?,
    val press: Double,
    val cardPress: DoubleArray,
    val shimmer: Double,
    val dim: Double,
    val focus: Double,
    val stitches: Double,
    val stitchT: Double,
    val morph: Double,
    val ticketA: Double,
    val number: Double,
    val foil: Double,
    val claimK: Double,
    val claimGlint: Double,
    val claimPress: Double,
    val tear: Double,
    val burn: Double,
    val absorb: Double,
    val land: Double,
    val tilt: DoubleArray,
    val lit: Double,
    val streak: Double,
    val streakAt: DoubleArray,
    val rays: Double,
    val ring: Double,
    val ringR: Double,
    val aurora: Double,
    val lens: DoubleArray,
    val fill: Double,
    val drain: Double,
    val drainAt: Double,
    val idleT: Double,
    val entered: DoubleArray,
    val burst: Double,
    val glow: Double,
    val vortex: Double,
)

internal data class Story(
    val from: Int = 0,
    val to: Int = 1,
    val reselect: Boolean = true,
    val tap: Double = 0.0,
    val tapOn: Int = -1,
    val touch: Boolean = false,
    val strip: Double? = null,
    val tiltX: Double? = null,
    val tiltY: Double? = null,
    val pressX: Double? = null,
)

private val AUTO = Story()

private const val SIM_HZ = 480
private const val BAKE_HZ = 120
private const val GRAVITY = 62.0
private const val WALL_E = .32
private const val BALL_E = .55
private const val GRIP = 12.0
private const val WIRES = 6
private const val KICK = .3

internal data class Baked(val t0: Double, val count: Int, val data: DoubleArray)

internal fun clamp01(x: Double): Double = if (x < 0.0) 0.0 else if (x > 1.0) 1.0 else x

internal fun seg(t: Double, a: Double, b: Double): Double = clamp01((t - a) / (b - a))

internal fun mix(a: Double, b: Double, k: Double): Double = a + (b - a) * k

/** A CSS cubic-bezier(x1, y1, x2, y2) at progress x (Newton, then bisection if needed). */
internal fun bezier(x: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
    if (x <= 0.0) return 0.0
    if (x >= 1.0) return 1.0
    val cx = 3.0 * x1
    val bx = 3.0 * (x2 - x1) - cx
    val ax = 1.0 - cx - bx
    val cy = 3.0 * y1
    val by = 3.0 * (y2 - y1) - cy
    val ay = 1.0 - cy - by
    var t = x
    for (i in 0 until 6) {
        val e = ((ax * t + bx) * t + cx) * t - x
        val d = (3.0 * ax * t + 2.0 * bx) * t + cx
        if (abs(e) < 1e-5) break
        if (abs(d) < 1e-6) {
            var lo = 0.0
            var hi = 1.0
            t = x
            for (j in 0 until 20) {
                val v = ((ax * t + bx) * t + cx) * t
                if (abs(v - x) < 1e-5) break
                if (v < x) lo = t else hi = t
                t = (lo + hi) / 2.0
            }
            break
        }
        t -= e / d
    }
    return ((ay * t + by) * t + cy) * t
}

internal fun easeOut(x: Double) = bezier(x, .23, 1.0, .32, 1.0)
internal fun easeLiquid(x: Double) = bezier(x, .45, 0.0, .3, 1.0)
internal fun easeInOut(x: Double) = bezier(x, .77, 0.0, .175, 1.0)

internal fun rand(i: Double, salt: Double): Double {
    val x = sin(i * 127.1 + salt * 311.7) * 43758.5453
    return x - floor(x)
}

internal fun project(x: Double, y: Double, z: Double, sx: Double): Projection {
    val cy = cos(CAM.yaw)
    val sy = sin(CAM.yaw)
    val x1 = cy * x + sy * z
    val z1 = -sy * x + cy * z
    val cp = cos(CAM.pitch)
    val sp = sin(CAM.pitch)
    val y2 = cp * y - sp * z1
    val z2 = sp * y + cp * z1
    val depth = CAM.dist + z2
    val f = L.drumR * CAM.dist * sx
    return Projection(L.drumX * sx + (f * x1) / depth, L.drumY * sx - (f * y2) / depth, depth, f)
}

private fun spinCurve(u: Double): Double {
    if (u <= 0.0) return 0.0
    if (u >= 1.0) return 1.0
    val k = .4
    val a = 3.0 / (2.0 * k + k * k)
    if (u < k) return a * u * u
    val v = (u - k) / (1.0 - k)
    return a * k * k + (1.0 - a * k * k) * (1.0 - (1.0 - v).pow(3.0))
}

internal fun cageAngle(t: Double): Double =
    IDLE_RATE * t + SPIN_EXTRA * spinCurve(seg(t, T.spin[0], T.spin[1]))

internal fun iconCentre(card: Int, k: Int, strip: Double, sx: Double, sy: Double): Ball {
    val icon = ICON_BALLS[card][k]
    return Ball(
        x = (L.cardX0 + card * (L.cardW + L.cardGap) + icon[0] + strip) * sx,
        y = (L.cardY + L.ballDY) * sy,
        r = icon[1] * sx,
        z = 3.2,
        rx = 0.0,
        ry = 0.0,
        rz = 0.0,
        a = 0.0,
        glow = 0.0,
        sq = 1.0,
    )
}

private fun spinSign(value: Double): Double = if (value < 0.0) -1.0 else if (value > 0.0) 1.0 else value
private fun hypot3(x: Double, y: Double, z: Double): Double = hypot(hypot(x, y), z)

private fun bake(n: Int): Baked {
    val t0 = T.fly[1]
    val t1 = T.back[1] + 2.0 * T.backStagger
    val count = kotlin.math.ceil((t1 - t0) * BAKE_HZ).toInt() + 2
    val data = DoubleArray(count * n * 4)
    val p = Array(n) { DoubleArray(3) }
    val v = Array(n) { DoubleArray(3) }
    val roll = DoubleArray(n)
    val spinRate = DoubleArray(n)
    for (k in 0 until n) {
        val phi = .16 * sin(1.3 * (T.fly[1] + k * T.flyStagger) + 2.0 * k)
        p[k][0] = (k - (n - 1) / 2.0) * .42
        p[k][1] = -INNER * cos(phi)
        p[k][2] = INNER * sin(phi)
        v[k][1] = -1.6
        roll[k] = k * 1.7
        spinRate[k] = 0.0
    }
    fun live(k: Int, t: Double): Boolean {
        if (t < T.fly[1] + k * T.flyStagger) return false
        if (k == 0) return t < T.eject[0]
        return t < T.back[0] + (k - 1) * T.backStagger
    }
    val h = 1.0 / SIM_HZ
    val steps = SIM_HZ / BAKE_HZ
    var t = t0
    val wirePrev = IntArray(n)
    for (s in 0 until count) {
        for (k in 0 until n) {
            val o = (s * n + k) * 4
            data[o] = p[k][0]
            data[o + 1] = p[k][1]
            data[o + 2] = p[k][2]
            data[o + 3] = roll[k]
        }
        for (j in 0 until steps) {
            val w = (cageAngle(t + h) - cageAngle(t - h)) / (2.0 * h)
            val ang = cageAngle(t)
            for (k in 0 until n) {
                if (!live(k, t)) continue
                val q = p[k]
                val u = v[k]
                u[1] -= GRAVITY * h
                q[0] += u[0] * h
                q[1] += u[1] * h
                q[2] += u[2] * h
                val d = hypot3(q[0], q[1], q[2])
                if (d > INNER) {
                    val nx = q[0] / d
                    val ny = q[1] / d
                    val nz = q[2] / d
                    q[0] = nx * INNER
                    q[1] = ny * INNER
                    q[2] = nz * INNER
                    val vn = u[0] * nx + u[1] * ny + u[2] * nz
                    if (vn > 0.0) {
                        u[0] -= (1.0 + WALL_E) * vn * nx
                        u[1] -= (1.0 + WALL_E) * vn * ny
                        u[2] -= (1.0 + WALL_E) * vn * nz
                    }
                    val wy = -w * q[2]
                    val wz = w * q[1]
                    val g = min(1.0, GRIP * h)
                    u[1] += (wy - u[1]) * g
                    u[2] += (wz - u[2]) * g
                    u[0] *= 1.0 - 2.5 * h
                    val phase = floor(((ang - atan2(q[2], -q[1])) * WIRES) / PI).toInt()
                    if (phase != wirePrev[k]) {
                        val sy = wy - u[1]
                        val sz = wz - u[2]
                        val bump = hypot(sy, sz) * .35 + abs(w) * INNER * .05
                        u[1] += sy * KICK - ny * bump
                        u[2] += sz * KICK - nz * bump
                        u[0] += .25 * bump * sin(phase * 2.39 + k * 1.3)
                        wirePrev[k] = phase
                    }
                    spinRate[k] = hypot(u[1] - wy, u[2] - wz) / BALL_R * spinSign(if (w != 0.0) w else 1.0)
                }
                roll[k] += spinRate[k] * h
                spinRate[k] *= 1.0 - .6 * h
            }
            for (a in 0 until n) {
                for (b in a + 1 until n) {
                    if (!live(a, t) || !live(b, t)) continue
                    val dx = p[b][0] - p[a][0]
                    val dy = p[b][1] - p[a][1]
                    val dz = p[b][2] - p[a][2]
                    val dd = hypot3(dx, dy, dz)
                    if (dd >= 2.0 * BALL_R || dd < 1e-6) continue
                    val nx = dx / dd
                    val ny = dy / dd
                    val nz = dz / dd
                    val push = (2.0 * BALL_R - dd) / 2.0
                    p[a][0] -= nx * push
                    p[a][1] -= ny * push
                    p[a][2] -= nz * push
                    p[b][0] += nx * push
                    p[b][1] += ny * push
                    p[b][2] += nz * push
                    val rel = (v[b][0] - v[a][0]) * nx + (v[b][1] - v[a][1]) * ny + (v[b][2] - v[a][2]) * nz
                    if (rel < 0.0) {
                        val jn = (-(1.0 + BALL_E) * rel) / 2.0
                        v[a][0] -= jn * nx
                        v[a][1] -= jn * ny
                        v[a][2] -= jn * nz
                        v[b][0] += jn * nx
                        v[b][1] += jn * ny
                        v[b][2] += jn * nz
                    }
                }
            }
            t += h
        }
    }
    return Baked(t0, count, data)
}

internal val BAKED = listOf(bake(1), bake(2), bake(3))

private data class InsideBall(val x: Double, val y: Double, val z: Double, val roll: Double)

private fun insideBall(k: Int, n: Int, t: Double, manualSpin: Double, manualSpeed: Double): InsideBall {
    val b = BAKED[n - 1]
    val f = max(0.0, min(b.count - 1.001, (t - b.t0) * BAKE_HZ))
    val i = floor(f).toInt()
    val u = f - i
    val o0 = (i * n + k) * 4
    val o1 = ((i + 1) * n + k) * 4
    val d = b.data
    var x = d[o0] + (d[o1] - d[o0]) * u
    var y = d[o0 + 1] + (d[o1 + 1] - d[o0 + 1]) * u
    var z = d[o0 + 2] + (d[o1 + 2] - d[o0 + 2]) * u
    val roll = d[o0 + 3] + (d[o1 + 3] - d[o0 + 3]) * u
    if (manualSpeed > .001) {
        val a = .9 * manualSpeed * spinSign(if (manualSpin != 0.0) manualSpin else 1.0)
        val c = cos(a)
        val s = sin(a)
        val y2 = c * y - s * z
        z = s * y + c * z
        y = y2
        x += .04 * manualSpeed * sin(t * 9.0 + k)
    }
    return InsideBall(x, y, z, roll + manualSpin * .5)
}

private fun quad(a: Double, b: Double, c: Double, u: Double): Double {
    val v = 1.0 - u
    return v * v * a + 2.0 * v * u * b + u * u * c
}

internal fun focusAt(t: Double): Double =
    easeInOut(seg(t, T.focus[0], T.focus[1])) * (1.0 - easeInOut(seg(t, T.focusOut[0], T.focusOut[1])))

private fun stripOf(c: Int): Double = if (c == 2) STRIP_MIN else 0.0

internal val STRIP_MIN = -(L.cardX0 * 2.0 + 3.0 * L.cardW + 2.0 * L.cardGap - 402.0)

internal fun rubber(x: Double): Double {
    val over = if (x > 0.0) x else if (x < STRIP_MIN) x - STRIP_MIN else 0.0
    if (over == 0.0) return x
    val d = 402.0 * .55
    val r = (1.0 - 1.0 / (abs(over) * .55 / d + 1.0)) * d
    return (if (x > 0.0) 0.0 else STRIP_MIN) + spinSign(over) * r
}

private fun stepStrip(d: Driver, h: Double, goal: Double) {
    if (d.drag) return
    var target = goal
    if (target.isNaN()) {
        if (d.strip > 0.0) target = 0.0
        else if (d.strip < STRIP_MIN) target = STRIP_MIN
    }
    if (target.isNaN()) {
        d.strip += d.stripV * h
        d.stripV *= .998.pow(h * 1000.0)
        if (abs(d.stripV) < 4.0) d.stripV = 0.0
        return
    }
    val w = 16.0
    val a = -w * w * (d.strip - target) - 2.0 * w * d.stripV
    d.stripV += a * h
    d.strip += d.stripV * h
    if (abs(d.strip - target) < .2 && abs(d.stripV) < 2.0) {
        d.strip = target
        d.stripV = 0.0
    }
}

internal fun stripAt(t: Double, story: Story): Double {
    val reselect = story.reselect && t >= T.reselect[0]
    val from = if (reselect) story.to else story.from
    val to = if (reselect) story.from else story.to
    val win = if (reselect) T.reselect else T.select
    return mix(stripOf(from), stripOf(to), easeOut(seg(t, win[0], win[1])))
}

internal fun frameAt(
    t: Double,
    sx: Double,
    sy: Double,
    story: Story = AUTO,
    manualSpin: Double = 0.0,
    manualSpeed: Double = 0.0,
    idle: Double = 0.0,
): Frame {
    val reselect = story.reselect && t >= T.reselect[0]
    val from = if (reselect) story.to else story.from
    val to = if (reselect) story.from else story.to
    val selWin = if (reselect) T.reselect else T.select
    val selK = easeOut(seg(t, selWin[0], selWin[1]))
    val sweep = seg(t, selWin[0], selWin[0] + .5)
    val check = easeOut(seg(t, selWin[0] + .02, selWin[0] + .2))
    val strip = story.strip ?: stripAt(t, story)

    val theta = cageAngle(t) + manualSpin
    val omega = (cageAngle(t + .008) - cageAngle(t - .008)) / .016
    val speed = min(1.0, max(0.0, (omega - IDLE_RATE) / 14.0))
    val card = story.to
    val n = card + 1
    val iconA = arrayOf(doubleArrayOf(1.0), doubleArrayOf(1.0, 1.0), doubleArrayOf(1.0, 1.0, 1.0))
    val balls = ArrayList<Ball>()
    var hero: Ball? = null
    val rx = TICKET.x * sx
    val ry = TICKET.y * sy
    val rr = 56.0 * sx
    for (k in 0 until n) {
        val home = iconCentre(card, k, strip, sx, sy)
        val inside = insideBall(k, n, t, manualSpin, manualSpeed)
        val p = project(inside.x, inside.y, inside.z, sx)
        val pr = p.f * BALL_R / p.z
        val fs = T.fly[0] + k * T.flyStagger
        val fe = T.fly[1] + k * T.flyStagger
        val winner = k == 0
        val bs = T.back[0] + (k - 1) * T.backStagger
        val be = T.back[1] + (k - 1) * T.backStagger
        var b = Ball(home.x, home.y, home.r, 3.2, inside.roll, .6 * sin(t * 1.7 + k), .3 * k, 0.0, 0.0, 1.0)
        val inPlay = if (winner) t >= fs && t < T.morph[1] else t >= fs && t < be
        if (inPlay) {
            iconA[card][k] = 0.0
            b.a = 1.0
            if (t < fe) {
                val u = easeInOut(seg(t, fs, fe))
                b.x = quad(home.x, mix(home.x, p.x, .6), p.x, u)
                b.y = quad(home.y, L.drumY * sy - 150.0 * sy, p.y, u)
                b.r = mix(home.r, pr, easeOut(u))
                b.z = if (u < .55) 3.2 else mix(3.2, p.z, seg(u, .55, 1.0))
            } else {
                b.x = p.x
                b.y = p.y
                b.r = pr
                b.z = p.z
                val lt = t - fe
                if (lt < T.landBounce) b.sq = 1.0 - .22 * exp(-lt * 14.0) * cos(lt * 30.0)
            }
            if (winner && t >= T.eject[0]) {
                val u = seg(t, T.eject[0], T.eject[1])
                val e = easeInOut(u)
                b.x = quad(p.x, L.drumX * sx, rx, e)
                b.y = quad(p.y, L.drumY * sy - 170.0 * sy, ry, e)
                b.r = mix(pr, rr, easeOut(u))
                b.z = mix(p.z, 3.1, seg(u, 0.0, .18))
                b.sq = if (u < .12) 1.0 - .25 * sin(u / .12 * PI) else 1.0 + .1 * sin(seg(u, .12, .5) * PI)
                b.glow = seg(u, .4, 1.0)
                b.rx += u * 4.0
                if (u >= T.handoff) {
                    hero = b.copy()
                    b.a = 0.0
                }
            }
            if (!winner && t >= bs) {
                val u = easeInOut(seg(t, bs, be))
                b.x = quad(p.x, mix(p.x, home.x, .5), home.x, u)
                b.y = quad(p.y, min(p.y, home.y) - 110.0 * sy, home.y, u)
                b.r = mix(pr, home.r, u)
                b.z = mix(p.z, 3.2, seg(u, 0.0, .3))
            }
        }
        if (winner && t >= T.iconBack[0] && t < LOOP_D) iconA[card][k] = easeOut(seg(t, T.iconBack[0], T.iconBack[1]))
        else if (winner && t >= T.morph[1]) iconA[card][k] = 0.0
        balls += b
    }
    if (t >= T.eject[1] && t < T.morph[1] && hero == null) {
        hero = Ball(rx, ry, rr, 3.1, 4.0 + (t - T.eject[1]) * 1.2, .5, 0.0, 1.0, 1.0, 1.0)
    }
    if (hero != null && t >= T.eject[1]) {
        val h = hero
        val tt = min(t, T.morph[0])
        val u = seg(tt, T.antic[0], T.morph[0])
        val near = easeInOut(seg(t, T.antic[0], T.morph[0]))
        h.r = rr * (1.0 + .08 * near)
        h.glow = 1.0 + .5 * near
        h.rx = 4.0 + (tt - T.eject[1]) * 1.2 + 6.5 * (u - sin(2.0 * PI * u) / (2.0 * PI))
        h.ry += .35 * near
        val shake = seg(t, T.morph[0] - .18, T.morph[0]) * (1.0 - seg(t, T.morph[0], T.morph[0] + .1))
        h.x = rx + sin(t * 97.0) * 1.6 * sx * shake
        h.y = ry + sin((t - T.eject[1]) * 3.0) * 3.0 * sy * (1.0 - near) + cos(t * 83.0) * 1.2 * sy * shake
    }

    val pulse = story.tap
    val press = max(if (story.touch) 0.0 else sin(PI * seg(t, T.press[0], T.press[1])), if (story.tapOn == 3) pulse else 0.0)
    val cardPress = doubleArrayOf(0.0, 0.0, 0.0)
    if (story.tapOn >= 0 && story.tapOn < 3) cardPress[story.tapOn] = pulse
    val shimmer = if (t > T.spin[0] && t < T.spin[0] + .9) seg(t, T.spin[0], T.spin[0] + .9) else 0.0
    val focus = easeInOut(seg(t, T.focus[0], T.focus[1])) * (1.0 - easeInOut(seg(t, T.focusOut[0], T.focusOut[1])))
    val dim = .35 * focus
    val stitches = seg(t, T.stitches[0], T.stitches[1])
    val stitchT = if (t >= T.stitches[0] && t < T.morph[1] + .4) t - T.stitches[0] else -1.0
    val morph = seg(t, T.morph[0], T.morph[1])
    val burn = seg(t, T.burn[0], T.burn[1])
    val ticketA = if (t < T.morph[1]) if (t >= T.morph[0]) 1.0 else 0.0 else 1.0 - burn
    val absorb = sin(PI * seg(t, T.burn[0] + T.dust * .8, T.burn[1] + T.dust + .15))
    val land = if (t >= T.tear[1] && t < T.tear[1] + .6) t - T.tear[1] else -1.0
    val lit = easeOut(seg(t, T.morph[1] - .05, T.morph[1] + .35))
    val sway = seg(t, T.morph[1], T.morph[1] + .4) * (1.0 - seg(t, T.claimPress[0] - .25, T.claimPress[0] + .1))
    val ht = t - T.morph[1]
    val tilt = doubleArrayOf(
        .26 * sin(ht * 2.2) * sway + (story.tiltX ?: 0.0),
        .14 * sin(ht * 1.5 + .8) * sway + (story.tiltY ?: 0.0),
    )
    val number = easeOut(seg(t, T.number[0], T.number[1]))
    val since = t + idle - T.sweep[0]
    val sweepLen = T.sweep[1] - T.sweep[0]
    val ph = if (since >= 0.0 && t < T.claimPress[0]) since % 1.6 else -1.0
    val foil = if (ph >= 0.0 && ph < sweepLen) ph / sweepLen else 0.0
    val gph = if (t >= T.claim[1] + .3 && t < T.claimPress[0]) (t + idle - T.claim[1] - .3) % 1.6 else -1.0
    val claimGlint = if (gph >= 0.0 && gph < .6) gph / .6 else 0.0
    val drainAt0 = T.claimPress[0] + .05
    val claimK = easeOut(seg(t, T.claim[0], T.claim[1])) * (1.0 - easeLiquid(seg(t, drainAt0, drainAt0 + T.drain)))
    val fill = easeLiquid(seg(t, T.claim[0] - .05, T.claim[1] + .05))
    val drain = easeLiquid(seg(t, drainAt0, drainAt0 + T.drain))
    val drainAt = if (story.pressX != null && !story.pressX.isNaN()) story.pressX else L.playW / 2.0
    val wb = hero ?: balls[0]
    val se = seg(t, T.eject[0] + .02, T.eject[0] + .35)
    val streak = if (se > 0.0 && se < 1.0) sin(PI * se).pow(1.3) else 0.0
    val streakAt = doubleArrayOf(wb.x, wb.y)
    val rays = easeOut(seg(t, T.morph[0] + .02, T.morph[0] + .22)) * (1.0 - easeInOut(seg(t, T.burst - .05, T.burst + .35)))
    val rAge = t - T.burst
    val ring = if (rAge >= 0.0 && rAge < .5) (1.0 - rAge / .5).pow(2.0) else 0.0
    val ringR = mix(40.0, 300.0, easeOut(max(0.0, min(1.0, rAge / .5)))) * sx
    val aurora = easeInOut(seg(t, T.number[0], T.number[0] + .65)) * (1.0 - easeOut(seg(t, T.burn[0], T.burn[1] - .1)))
    val lensFrom = L.cardX0 + from * (L.cardW + L.cardGap) + strip
    val lensTo = L.cardX0 + to * (L.cardW + L.cardGap) + strip
    val lu = seg(t, selWin[0], selWin[1])
    val lead = easeOut(min(1.0, lu / .72))
    val trail = easeInOut(seg(lu, .18, 1.0))
    val right = to > from
    val lx0 = if (right) mix(lensFrom, lensTo, trail) else mix(lensFrom, lensTo, lead)
    val lx1 = (if (right) mix(lensFrom, lensTo, lead) else mix(lensFrom, lensTo, trail)) + L.cardW
    val lk = if (from != to && lu > 0.0 && lu < 1.0) sin(PI * lu).pow(.55) else 0.0
    val squeeze = 1.0 - .06 * sin(PI * lu) * (lx1 - lx0 - L.cardW) / (L.cardW + L.cardGap)
    val lh = L.cardH * squeeze
    val lens = doubleArrayOf(lx0, L.cardY + (L.cardH - lh) / 2.0, lx1 - lx0, lh, lk)
    val claimPress = if (story.touch) 0.0 else sin(PI * seg(t, T.claimPress[0], T.claimPress[1]))
    val tear = if (t >= T.tear[0] && t < T.reselect[0]) seg(t, T.tear[0], T.tear[1]) else 0.0
    val entered = doubleArrayOf(0.0, 0.0, 0.0)
    entered[card] = easeOut(seg(t, T.entered[0], T.entered[1])) * (1.0 - seg(t, T.reselect[0], T.reselect[0] + .3))
    val bt = t + if (t >= T.burst) idle else 0.0
    val burst = if (bt >= T.burst && bt < T.burst + 2.4) bt - T.burst else -1.0
    val glow = .35 * min(1.0, speed * 1.4) + .4 * focus
    return Frame(
        t, theta, omega, min(1.0, speed + manualSpeed), from, to, selK, sweep, check, strip, iconA, balls, hero,
        press, cardPress, shimmer, dim, focus, stitches, stitchT, morph, ticketA, number, foil, claimK, claimGlint,
        claimPress, tear, burn, absorb, land, tilt, lit, streak, streakAt, rays, ring, ringR, aurora, lens, fill,
        drain, drainAt, idle, entered, burst, glow, speed,
    )
}

internal data class Driver(
    var mode: Int = 0,
    var t: Double = 0.0,
    var stop: Double = LOOP_D,
    var last: Double = -100.0,
    var from: Int = 0,
    var to: Int = 1,
    var spin: Double = 0.0,
    var spinV: Double = 0.0,
    var idle: Double = 0.0,
    var tap: Double = 0.0,
    var tapOn: Int = -1,
    var held: Int = -1,
    var strip: Double = 0.0,
    var stripV: Double = 0.0,
    var drag: Boolean = false,
    var free: Boolean = false,
    var goal: Double = Double.NaN,
    var tiltX: Double = 0.0,
    var tiltY: Double = 0.0,
    var tiltVX: Double = 0.0,
    var tiltVY: Double = 0.0,
    var tilting: Boolean = false,
    var pressX: Double = Double.NaN,
)

internal const val RESUME = 2.0

internal fun makeDriver() = Driver()

internal fun stepDriver(d: Driver, dt: Double, now: Double, auto: Boolean) {
    val step = min(dt, .05)
    val down = d.held >= 0
    d.tap += ((if (down) 1.0 else 0.0) - d.tap) * (1.0 - exp(-step / if (down) .035 else .065))
    d.spin += d.spinV * step
    d.spinV *= exp(-step * 1.6)
    if (abs(d.spinV) < .01) d.spinV = 0.0
    if (d.mode == 0) {
        d.t = if (auto) (d.t + step) % LOOP_D else 0.0
        if (auto && d.t < step) {
            d.from = 0
            d.to = 1
        }
    } else if (d.mode == 1) {
        val next = d.t + step
        if (next >= d.stop) {
            d.t = d.stop
            d.mode = 2
            d.last = now
        } else d.t = next
    } else if (d.mode == 2) {
        d.idle = now - d.last
        val waitingForClaim = d.t >= T.hold - .01 && d.t < T.claimPress[0]
        if (auto && d.idle > RESUME && !waitingForClaim) {
            d.mode = 3
            d.from = 0
            d.idle = 0.0
        }
    } else if (d.mode == 3) {
        d.t += step
        if (d.t >= LOOP_D) {
            d.mode = 0
            d.t = 0.0
            d.from = 0
            d.to = 1
        }
    }
    if (d.mode != 2) d.idle = 0.0
    val autoStrip = d.mode == 0 || d.mode == 3
    val goal = if (autoStrip) stripAt(d.t, Story(d.from, d.to, true, 0.0, -1, false)) else d.goal
    for (i in 0 until 4) stepStrip(d, step / 4.0, goal)
    if (autoStrip && d.free && !d.drag && d.strip == goal) d.free = false
    if (!d.tilting) {
        val w = 11.0
        val z = .6
        for (i in 0 until 4) {
            val hh = step / 4.0
            d.tiltVX += (-w * w * d.tiltX - 2.0 * z * w * d.tiltVX) * hh
            d.tiltVY += (-w * w * d.tiltY - 2.0 * z * w * d.tiltVY) * hh
            d.tiltX += d.tiltVX * hh
            d.tiltY += d.tiltVY * hh
        }
        if (abs(d.tiltX) + abs(d.tiltY) < 1e-4 && abs(d.tiltVX) + abs(d.tiltVY) < 1e-3) {
            d.tiltX = 0.0
            d.tiltY = 0.0
            d.tiltVX = 0.0
            d.tiltVY = 0.0
        }
    }
    if (!d.free) d.strip = if (autoStrip) goal else d.strip
}

internal fun storyOf(d: Driver): Story = Story(
    from = d.from,
    to = d.to,
    reselect = d.mode == 0 || d.mode == 3,
    tap = d.tap,
    tapOn = d.tapOn,
    touch = d.mode == 1 || d.mode == 2,
    strip = if (d.free) d.strip else null,
    tiltX = d.tiltX,
    tiltY = d.tiltY,
    pressX = if (d.mode == 1 || d.mode == 2) d.pressX else Double.NaN,
)

internal fun pressIn(d: Driver, on: Int, x: Double = Double.NaN) {
    d.held = on
    d.tapOn = on
    if (on == 3) d.pressX = x
}

internal fun pressOut(d: Driver) {
    d.held = -1
}

internal fun selectCard(d: Driver, c: Int, now: Double): Boolean {
    if (d.mode != 0 && d.t > T.press[0] && d.t < T.back[1]) return false
    val before = if (d.free) d.strip else stripAt(d.t, storyOf(d))
    val current = if (d.mode == 0 || d.mode == 3) {
        if (d.t >= T.select[0] && d.t < T.reselect[0]) d.to else d.from
    } else d.to
    if (current == c && d.mode != 0) return false
    d.from = current
    d.to = c
    d.t = T.select[0]
    d.mode = 1
    d.stop = T.press[0] - .05
    d.last = now
    d.free = true
    d.strip = before
    val left = L.cardX0 + c * (L.cardW + L.cardGap)
    val lo = 12.0 - left
    val hi = 402.0 - 12.0 - L.cardW - left
    d.goal = max(STRIP_MIN, min(0.0, min(hi, max(lo, d.strip))))
    return true
}

internal fun pressPlay(d: Driver, now: Double): Boolean {
    d.last = now
    if (d.mode == 0 || d.mode == 3) {
        val chosen = if (d.t >= T.select[0] && d.t < T.reselect[0]) d.to else d.from
        d.from = chosen
        d.to = chosen
        if (d.t >= T.reselect[0]) d.t = 0.0
    }
    if (d.t >= T.hold - .01 && d.t < T.claimPress[0]) {
        d.t = T.claimPress[0]
        d.mode = 1
        d.stop = T.reselect[0] - .05
        return true
    }
    if (d.t < T.press[0]) {
        if (d.t < T.fly[0]) d.t = T.fly[0] - .02
        else if (d.t > T.fly[1] + .35) d.t = max(d.t, T.press[0] - .02)
        d.mode = 1
        d.stop = T.hold
        return true
    }
    if (d.t < T.hold) {
        d.mode = 1
        d.stop = T.hold
        return true
    }
    return false
}

internal fun touched(d: Driver, now: Double) {
    d.last = now
    if (d.mode == 0 || d.mode == 3) {
        d.mode = 2
        d.to = if (d.t >= T.select[0] && d.t < T.reselect[0]) d.to else d.from
        d.from = d.to
    }
}

internal fun grabStrip(d: Driver, now: Double) {
    if (!d.free) d.strip = stripAt(d.t, storyOf(d))
    touched(d, now)
    d.free = true
    d.drag = true
    d.stripV = 0.0
    d.goal = Double.NaN
}

internal fun releaseStrip(d: Driver, velocity: Double, now: Double) {
    d.drag = false
    d.stripV = velocity
    d.last = now
}

internal fun canTilt(t: Double): Boolean = t >= T.morph[1] && t < T.claimPress[0]

internal fun grabTicket(d: Driver, now: Double) {
    touched(d, now)
    d.tilting = true
    d.tiltVX = 0.0
    d.tiltVY = 0.0
}

internal fun tiltTicket(d: Driver, dx: Double, dy: Double, now: Double) {
    fun soft(v: Double) = .55 * tanh(v / .55)
    d.tiltX = soft(dx / 170.0)
    d.tiltY = soft(-dy / 140.0)
    d.last = now
}

internal fun releaseTicket(d: Driver, vx: Double, vy: Double, now: Double) {
    d.tilting = false
    d.tiltVX = max(-8.0, min(8.0, vx / 170.0))
    d.tiltVY = max(-8.0, min(8.0, -vy / 140.0))
    d.last = now
}

// Float-shaped compatibility API for the current Compose screen.
internal object RdsTime {
    const val select0 = .6f
    const val select1 = .92f
    const val fly0 = .75f
    const val fly1 = 1.35f
    const val flyStagger = .1f
    const val press0 = 1.6f
    const val press1 = 1.75f
    const val spin0 = 1.7f
    const val spin1 = 3.6f
    const val eject0 = 3.45f
    const val eject1 = 4f
    const val handoff = .18f
    const val focus0 = 3.5f
    const val focus1 = 3.9f
    const val stitches0 = 4.05f
    const val stitches1 = 4.55f
    const val morph0 = 4.75f
    const val morph1 = 5.7f
    const val burst = 5.5f
    const val number0 = 5.6f
    const val number1 = 6.2f
    const val sweep0 = 6.2f
    const val sweep1 = 6.9f
    const val claim0 = 6.35f
    const val claim1 = 6.95f
    const val hold = 7f
    const val claimPress0 = 7.85f
    const val claimPress1 = 8f
    const val drain = .45f
    const val tear0 = 8f
    const val tear1 = 8.6f
    const val burn0 = 8.15f
    const val burn1 = 8.6f
    const val dust = .42f
    const val focusOut0 = 8.6f
    const val focusOut1 = 9f
    const val back0 = 8.75f
    const val back1 = 9.25f
    const val backStagger = .1f
    const val entered0 = 8.6f
    const val entered1 = 8.8f
    const val iconBack0 = 8.55f
    const val iconBack1 = 8.85f
    const val reselect0 = 9.4f
    const val reselect1 = 9.72f
}

internal data class RdsBall(
    var x: Float,
    var y: Float,
    var radius: Float,
    var depth: Float = 3.2f,
    var roll: Float = 0f,
    var alpha: Float = 1f,
    var squash: Float = 1f,
    var glow: Float = 0f,
    var rx: Float = 0f,
    var ry: Float = 0f,
    var rz: Float = 0f,
    var rotX: Float = 0f,
    var rotY: Float = 0f,
    var rotZ: Float = 0f,
)

internal data class RdsFrame(
    val t: Float,
    val theta: Float,
    val speed: Float,
    val from: Int,
    val to: Int,
    val selection: Float,
    val strip: Float,
    val iconAlpha: Array<FloatArray>,
    val balls: List<RdsBall>,
    val hero: RdsBall?,
    val playPress: Float,
    val cardPress: FloatArray,
    val focus: Float,
    val stitches: Float,
    val morph: Float,
    val ticketAlpha: Float,
    val number: Float,
    val foil: Float,
    val claim: Float,
    val claimPress: Float,
    val tear: Float,
    val burn: Float,
    val absorb: Float,
    val entered: Float,
    val burstAge: Float,
    val ring: Float,
    val rays: Float,
    val glow: Float,
    val fill: Float,
    val drain: Float,
    val tiltX: Float,
    val tiltY: Float,
    val omega: Float = 0f,
    val sweep: Float = 0f,
    val check: Float = 0f,
    val dim: Float = 0f,
    val stitchT: Float = -1f,
    val claimGlint: Float = 0f,
    val land: Float = -1f,
    val lit: Float = 0f,
    val streak: Float = 0f,
    val streakAt: FloatArray = floatArrayOf(0f, 0f),
    val ringR: Float = 0f,
    val aurora: Float = 0f,
    val lens: FloatArray = floatArrayOf(0f, 0f, 0f, 0f, 0f),
    val drainAt: Float = 0f,
    val idleT: Float = 0f,
    val enteredCards: FloatArray = floatArrayOf(0f, 0f, 0f),
    val burst: Float = -1f,
    val vortex: Float = 0f,
    val shimmer: Float = 0f,
)

internal class RdsDriver {
    internal val state = makeDriver()
    enum class Mode { Auto, Playing, Waiting, Resuming }

    var time: Float
        get() = state.t.toFloat()
        set(value) { state.t = value.toDouble() }
    var selected: Int
        get() = state.to
        set(value) { state.to = value }
    var from: Int
        get() = state.from
        set(value) { state.from = value }
    var mode: Mode
        get() = when (state.mode) {
            1 -> Mode.Playing
            2 -> Mode.Waiting
            3 -> Mode.Resuming
            else -> Mode.Auto
        }
        set(value) { state.mode = when (value) {
            Mode.Auto -> 0
            Mode.Playing -> 1
            Mode.Waiting -> 2
            Mode.Resuming -> 3
        } }
    var stop: Float
        get() = state.stop.toFloat()
        set(value) { state.stop = value.toDouble() }
    var lastTouchSeconds: Float
        get() = state.last.toFloat()
        set(value) { state.last = value.toDouble() }
    var strip: Float
        get() = state.strip.toFloat()
        set(value) { state.strip = value.toDouble() }
    var stripVelocity: Float
        get() = state.stripV.toFloat()
        set(value) { state.stripV = value.toDouble() }
    var draggingStrip: Boolean
        get() = state.drag
        set(value) { state.drag = value }
    var free: Boolean
        get() = state.free
        set(value) { state.free = value }
    var goal: Float
        get() = state.goal.toFloat()
        set(value) { state.goal = value.toDouble() }
    var manualSpin: Float
        get() = state.spin.toFloat()
        set(value) { state.spin = value.toDouble() }
    var manualSpinVelocity: Float
        get() = state.spinV.toFloat()
        set(value) { state.spinV = value.toDouble() }
    var tiltX: Float
        get() = state.tiltX.toFloat()
        set(value) { state.tiltX = value.toDouble() }
    var tiltY: Float
        get() = state.tiltY.toFloat()
        set(value) { state.tiltY = value.toDouble() }
    var tilting: Boolean
        get() = state.tilting
        set(value) { state.tilting = value }
    var press: Float
        get() = state.tap.toFloat()
        set(value) { state.tap = value.toDouble() }
    var pressing: Boolean
        get() = state.held >= 0
        set(value) {
            if (value) {
                if (state.held < 0) {
                    state.held = if (state.tapOn >= 0) state.tapOn else 3
                    state.tapOn = state.held
                }
            } else state.held = -1
        }

    fun step(dtSeconds: Float, nowSeconds: Float, autoPlay: Boolean) =
        stepDriver(state, dtSeconds.toDouble(), nowSeconds.toDouble(), autoPlay)

    fun select(card: Int, nowSeconds: Float) {
        selectCard(state, card, nowSeconds.toDouble())
    }

    fun play(nowSeconds: Float) {
        pressPlay(state, nowSeconds.toDouble())
    }
}

internal fun rubberBand(x: Float): Float = rubber(x.toDouble()).toFloat()

internal fun raffleFrameAt(rawTime: Float, sx: Float, sy: Float, driver: RdsDriver): RdsFrame {
    val d = driver.state
    val f = frameAt(
        d.t,
        sx.toDouble(),
        sy.toDouble(),
        storyOf(d),
        d.spin,
        min(1.0, abs(d.spinV) / 14.0),
        d.idle,
    )
    fun legacyBall(b: Ball): RdsBall = RdsBall(
        x = b.x.toFloat(),
        y = b.y.toFloat(),
        radius = b.r.toFloat(),
        depth = b.z.toFloat(),
        roll = b.rx.toFloat(),
        alpha = b.a.toFloat(),
        squash = b.sq.toFloat(),
        glow = b.glow.toFloat(),
        rx = b.rx.toFloat(),
        ry = b.ry.toFloat(),
        rz = b.rz.toFloat(),
        rotX = b.rx.toFloat(),
        rotY = b.ry.toFloat(),
        rotZ = b.rz.toFloat(),
    )
    return RdsFrame(
        t = f.t.toFloat(),
        theta = f.theta.toFloat(),
        speed = f.speed.toFloat(),
        from = f.from,
        to = f.to,
        selection = f.selK.toFloat(),
        strip = f.strip.toFloat(),
        iconAlpha = Array(f.iconA.size) { i -> FloatArray(f.iconA[i].size) { j -> f.iconA[i][j].toFloat() } },
        balls = f.balls.map(::legacyBall),
        hero = f.hero?.let(::legacyBall),
        playPress = f.press.toFloat(),
        cardPress = FloatArray(f.cardPress.size) { f.cardPress[it].toFloat() },
        focus = f.focus.toFloat(),
        stitches = f.stitches.toFloat(),
        morph = f.morph.toFloat(),
        ticketAlpha = f.ticketA.toFloat(),
        number = f.number.toFloat(),
        foil = f.foil.toFloat(),
        claim = f.claimK.toFloat(),
        claimPress = f.claimPress.toFloat(),
        tear = f.tear.toFloat(),
        burn = f.burn.toFloat(),
        absorb = f.absorb.toFloat(),
        entered = f.entered[f.to].toFloat(),
        burstAge = f.burst.toFloat(),
        ring = f.ring.toFloat(),
        rays = f.rays.toFloat(),
        glow = f.glow.toFloat(),
        fill = f.fill.toFloat(),
        drain = f.drain.toFloat(),
        tiltX = f.tilt[0].toFloat(),
        tiltY = f.tilt[1].toFloat(),
        omega = f.omega.toFloat(),
        sweep = f.sweep.toFloat(),
        check = f.check.toFloat(),
        dim = f.dim.toFloat(),
        stitchT = f.stitchT.toFloat(),
        claimGlint = f.claimGlint.toFloat(),
        land = f.land.toFloat(),
        lit = f.lit.toFloat(),
        streak = f.streak.toFloat(),
        streakAt = floatArrayOf(f.streakAt[0].toFloat(), f.streakAt[1].toFloat()),
        ringR = f.ringR.toFloat(),
        aurora = f.aurora.toFloat(),
        lens = FloatArray(f.lens.size) { f.lens[it].toFloat() },
        drainAt = f.drainAt.toFloat(),
        idleT = f.idleT.toFloat(),
        enteredCards = FloatArray(f.entered.size) { f.entered[it].toFloat() },
        burst = f.burst.toFloat(),
        vortex = f.vortex.toFloat(),
        shimmer = f.shimmer.toFloat(),
    )
}
