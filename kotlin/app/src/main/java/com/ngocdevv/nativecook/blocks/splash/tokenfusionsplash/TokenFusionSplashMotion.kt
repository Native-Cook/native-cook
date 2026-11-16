package com.ngocdevv.nativecook.blocks.splash.tokenfusionsplash

import com.ngocdevv.nativecook.blocks.splash.tokenfusionsplash.TokenFusionSplashTheme as Theme
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/** One token coin of a frame: position, radius (22 at rest, 0 once fused), reach of its neck, dye, lean (rad), heat. */
internal class TokenFusionCoin(val x: Double, val y: Double, val r: Double, val reach: Double, val dye: Double, val lean: Double, val heat: Double)

/** One frame of the story (RN `Scene`): everything the shader and the wordmark read. */
internal class TokenFusionScene(
    val x: Double,
    val y: Double,
    val r: Double,
    val sphere: Double,
    val stretch: Double,
    val axis: Double,
    val ringScale: Double,
    val mark: Double,
    val markSize: Double,
    val cut: Double,
    val word: Double,
    val wordY: Double,
    val flood: Double,
    val coins: List<TokenFusionCoin>,
)

internal class TokenFusionLiveCoin(
    var x: Double, var y: Double, var r: Double, var reach: Double, var dye: Double, var lean: Double, var heat: Double,
    var state: Int, var vx: Double, var vy: Double, var age: Double, var speed: Double, var fromX: Double, var fromY: Double,
    var pointer: Int, var ox: Double, var oy: Double, var lastX: Double, var lastY: Double, var lastT: Double,
    var snapped: Boolean, var neckFormed: Boolean,
) {
    fun copy() = TokenFusionLiveCoin(
        x, y, r, reach, dye, lean, heat, state, vx, vy, age, speed, fromX, fromY, pointer, ox, oy, lastX, lastY, lastT, snapped, neckFormed,
    )
}

internal class TokenFusionLive(
    var coins: List<TokenFusionLiveCoin>,
    var now: Double = 0.0,
    var angle: Double = 0.0,
    var gather: Double = 0.0,
    var mass: Double = 0.0,
    var massV: Double = 0.0,
    var x: Double = 0.0,
    var y: Double = 0.0,
    var vx: Double = 0.0,
    var vy: Double = 0.0,
    var corePointer: Int = -1,
    var coreOX: Double = 0.0,
    var coreOY: Double = 0.0,
    var lastTouch: Double = 0.0,
    var coreLastT: Double = 0.0,
    var corePull: Double = 0.0,
    var mark: Double = 0.0,
    var flood: Double = 0.0,
    var floodV: Double = 0.0,
    var completed: Boolean = false,
    var recoilX: Double = 0.0,
    var recoilY: Double = 0.0,
    var recoilVX: Double = 0.0,
    var recoilVY: Double = 0.0,
    var ringTime: Double = 10.0,
    var ringSpeed: Double = 0.0,
    var ringAxis: Double = 0.0,
    var mergedMask: Int = 0,
    var mergeEvents: Int = 0,
    var releaseEvents: Int = 0,
    var cue: Int = 0,
    var stretch: Double = 1.0,
    var snapTime: Double = 10.0,
    var carryStretch: Double = 0.0,
    var carryStretchV: Double = 0.0,
    var vMarkSize: Double = 60.0,
    var vCut: Double = 0.0,
    var vWord: Double = 0.0,
    var vWordY: Double = Theme.WORDMARK_Y,
) {
    fun copy(): TokenFusionLive = TokenFusionLive(
        coins.map { it.copy() }, now, angle, gather, mass, massV, x, y, vx, vy, corePointer, coreOX, coreOY, lastTouch, coreLastT, corePull,
        mark, flood, floodV, completed, recoilX, recoilY, recoilVX, recoilVY, ringTime, ringSpeed, ringAxis, mergedMask, mergeEvents,
        releaseEvents, cue, stretch, snapTime, carryStretch, carryStretchV, vMarkSize, vCut, vWord, vWordY,
    )
}

/**
 * The 7.5 s story and the live physics of the RN block (`tfs-motion.ts`), value for value. [sceneAt] is a pure function of the loop
 * time; a finger takes over through [TokenFusionLive], stepped at fixed 120 Hz sub-steps so throws are stable at any refresh rate.
 */
internal object TokenFusionSplashMotion {
    const val LOOP = 7.5

    // timeline (s)
    const val T_GATHER0 = 0.4
    const val T_FLOOD0 = 3.75
    const val T_FLOOD1 = 4.6
    const val T_MARK0 = 4.18
    const val T_MARK1 = 4.92
    const val T_WORD0 = 4.96
    const val T_WORD1 = 5.2
    const val T_RECEDE0 = 5.7
    const val T_RECEDE1 = 7.0
    val CAPTURES = doubleArrayOf(2.0, 2.55, 2.95, 3.28)
    const val CAPTURE_DURATION = 0.32

    // physics
    const val DECAY_PER_MS = 0.996
    const val GRAVITY = 9000.0
    const val GRAVITY_MIN_GAP = 20.0
    const val PULL_RADIUS = 160.0
    const val CAPTURE_GAP = 40.0
    const val CONTACT_GAP = 6.0
    const val HOLD_GAP = 44.0
    const val REACH_GAP = 96.0
    const val SNAP_GAP = 110.0
    const val PULL_BACK_SECONDS = 0.16
    const val MIN_GRAB_RADIUS = 9.0
    const val RING_HZ = 3.2
    const val RING_DECAY = 0.26
    const val RING_MIN = 0.09
    const val RING_BOOST = 0.09
    const val SPEED_MAX = 1500.0
    const val RECOIL = 14.0
    const val RESUME_AFTER = 2.0
    const val RESUME_DURATION = 0.6
    const val MAX_STRETCH = 1.25
    val ORBIT_SPEEDS = doubleArrayOf(6.0, 9.0, 13.0, 19.0, 19.0, 19.0, 19.0)
    const val ORBIT_TIGHTEN = 0.93
    const val MASS_LAG = 0.12
    val FIRST_RING_PEAK = atan(2 * PI * RING_HZ * RING_DECAY) / (2 * PI * RING_HZ)
    const val DYE_SECONDS = 2.4
    const val FLIGHT_TIMEOUT = 0.8

    fun clamp(x: Double, lo: Double = 0.0, hi: Double = 1.0): Double = min(hi, max(lo, x))
    fun mix(a: Double, b: Double, t: Double): Double = a + (b - a) * t
    fun smooth(x: Double): Double {
        val t = clamp(x)
        return t * t * (3 - 2 * t)
    }
    fun seg(t: Double, a: Double, b: Double): Double = smooth((t - a) / max(b - a, 0.0001))

    /** Analytic damped spring, preserving exact time sampling. */
    fun springAt(t: Double, damping: Double, stiffness: Double, mass: Double = 1.0): Double {
        if (t <= 0) return 0.0
        val w = sqrt(stiffness / mass)
        val z = damping / (2 * sqrt(stiffness * mass))
        val wd = w * sqrt(max(0.0001, 1 - z * z))
        return 1 - exp(-z * w * t) * (cos(wd * t) + ((z * w) / wd) * sin(wd * t))
    }

    fun ringAt(t: Double, speed: Double = 0.0): Double {
        if (t < 0) return 0.0
        return (RING_MIN + RING_BOOST * clamp(speed / SPEED_MAX)) * sin(2 * PI * RING_HZ * t) * exp(-t / RING_DECAY)
    }

    class XY(val x: Double, val y: Double)

    fun orbitAt(i: Int, count: Int, angle: Double, gather: Double, mass: Double): XY {
        val base = if (count == 4) Theme.ORBIT_ANGLES[i] else -125 + (i * 360.0) / count
        val a = ((base + angle) * PI) / 180
        val tilt = (Theme.ORBIT_TILT * PI) / 180
        val gain = ORBIT_TIGHTEN.pow(mass)
        val x = mix(Theme.ORBIT_WIDE_X, Theme.ORBIT_TIGHT_X, gather) * gain * cos(a)
        val y = mix(Theme.ORBIT_WIDE_Y, Theme.ORBIT_TIGHT_Y, gather) * gain * sin(a)
        return XY(Theme.CORE_X + x * cos(tilt) - y * sin(tilt), Theme.CORE_Y + x * sin(tilt) + y * cos(tilt))
    }

    fun coinReach(x: Double, y: Double, r: Double, coreX: Double, coreY: Double, coreR: Double): Double =
        1 - seg(hypot(x - coreX, y - coreY) - r - coreR, 30.0, REACH_GAP)

    private fun capAt(i: Int, count: Int) = if (count == 4) CAPTURES[i] else 2 + i * (1.28 / max(1, count - 1))

    fun sceneAt(time: Double, count: Int = 4): TokenFusionScene {
        val t = ((time % LOOP) + LOOP) % LOOP
        val returning = t > T_RECEDE0
        val back = seg(t, T_RECEDE0, T_RECEDE1)
        val gather = if (returning) 1 - back else clamp(springAt(t - T_GATHER0, 14.0, 120.0))
        var area = 0.0
        var mass = 0.0
        var angle = 6 * min(t, 5.7)
        var ringing = 0.0
        for (i in 0 until count) {
            val cap = capAt(i, count)
            val p = if (returning) 1 - seg(t, 5.84 + i * 0.07, 6.85 + i * 0.04) else seg(t, cap, cap + CAPTURE_DURATION)
            area += 22 * 22 * p
            mass += clamp(springAt(t - cap - 0.18 - FIRST_RING_PEAK - MASS_LAG, 18.0, 90.0)) * (1 - back)
            if (t > cap + 0.2) {
                angle += max(0.0, t - cap - 0.32) * (ORBIT_SPEEDS[min(i + 1, 6)] - ORBIT_SPEEDS[min(i, 6)])
            }
            ringing += ringAt(t - cap - 0.18) * (1 - back)
        }
        // An orbit turns forward, then retraces only while droplets split off. The seam has an exact resting interval.
        angle *= 1 - back
        val base = sqrt(mix(120.0 * 120, 55.0 * 55, gather) + area)
        val flood = seg(t, T_FLOOD0, T_FLOOD1) * (1 - back)
        val r = mix(base, 980.0, flood)
        val coins = ArrayList<TokenFusionCoin>(count)
        for (i in 0 until count) {
            val cap = capAt(i, count)
            val slot = orbitAt(i, count, angle, gather, mass)
            val approach = if (i == 0) seg(t, 1.0, 1.7) else seg(t, cap - 0.7, cap - 0.08)
            val swallowed = seg(t, cap, cap + CAPTURE_DURATION)
            val release = seg(t, 5.84 + i * 0.07, 6.85 + i * 0.04)
            val travel = if (returning) 1 - release else approach
            val progress = if (returning) 1 - release else swallowed
            val dx = slot.x - Theme.CORE_X
            val dy = slot.y - Theme.CORE_Y
            val len = max(1.0, hypot(dx, dy))
            val rr = 22 * sqrt(max(0.0, 1 - progress))
            val destination = 55 + HOLD_GAP + 22
            val radial = mix(len, mix(destination, 20.0, swallowed), travel)
            val x = Theme.CORE_X + (dx / len) * radial
            val y = Theme.CORE_Y + (dy / len) * radial
            coins.add(
                TokenFusionCoin(
                    x, y, rr,
                    coinReach(x, y, rr, Theme.CORE_X, Theme.CORE_Y, base) * travel,
                    max(approach * (1 - swallowed), 1 - seg(t, cap, cap + DYE_SECONDS)) * (if (approach > 0) 1.0 else 0.0) * (1 - back),
                    (sign(Theme.CORE_X - x) * min(1.0, mass) * 6 * PI) / 180,
                    approach * (1 - progress) * (1 - back),
                ),
            )
        }
        val mark = if (returning) back else 1 - seg(t, 1.12, 1.35) + seg(t, T_MARK0, T_MARK1)
        return TokenFusionScene(
            x = Theme.CORE_X,
            y = Theme.CORE_Y,
            r = r,
            sphere = 1 - gather * 0.6,
            stretch = 1 + ringing,
            axis = -2.1,
            ringScale = 1 - gather * 0.6 * 0.54,
            mark = clamp(mark),
            markSize = if (returning) mix(60.0, 60.0, back) else mix(mix(60.0, 40.0, gather), 60.0, seg(t, 4.18, 4.92)),
            cut = seg(t, 4.18, 4.42) * (1 - seg(t, 4.42, 4.92)) * (1 - back),
            word = seg(t, T_WORD0, T_WORD1) * (1 - seg(t, 5.7, 5.94)),
            wordY = Theme.WORDMARK_Y + Theme.WORDMARK_RISE * clamp(1 - seg(t, T_WORD0, T_WORD1) + back),
            flood = flood,
            coins = coins,
        )
    }

    fun liveFrom(scene: TokenFusionScene, next: TokenFusionScene? = null): TokenFusionLive {
        var mask = 0
        val coins = scene.coins.mapIndexed { i, c ->
            val fused = c.r < 1
            if (fused) mask = mask or (1 shl i)
            val shrinking = !fused && c.r < 21.9 && (next == null || next.coins[i].r < c.r)
            var low = 0.0
            var high = 1.0
            val progress = clamp(1 - (c.r * c.r) / (22 * 22))
            for (j in 0 until 12) {
                val mid = (low + high) / 2
                if (smooth(mid) < progress) low = mid else high = mid
            }
            val age = if (shrinking) ((low + high) / 2) * 0.32 else 0.0
            val left = max(0.001, 1 - clamp(springAt(age, 16.0, 200.0)))
            TokenFusionLiveCoin(
                c.x, c.y, c.r, c.reach, c.dye, c.lean, c.heat,
                if (fused) 4 else if (shrinking) 3 else 0,
                if (next != null) (next.coins[i].x - c.x) * 120 else 0.0,
                if (next != null) (next.coins[i].y - c.y) * 120 else 0.0,
                age, 0.0,
                scene.x + (c.x - scene.x) / left,
                scene.y + (c.y - scene.y) / left,
                -1, 0.0, 0.0, c.x, c.y, 0.0, false, false,
            )
        }
        return TokenFusionLive(
            coins = coins,
            gather = clamp((1 - scene.sphere) / 0.6),
            x = scene.x,
            y = scene.y,
            vx = if (next != null) (next.x - scene.x) * 120 else 0.0,
            vy = if (next != null) (next.y - scene.y) * 120 else 0.0,
            mark = scene.mark,
            flood = scene.flood,
            ringAxis = scene.axis,
            mergedMask = mask,
            stretch = scene.stretch,
            carryStretch = scene.stretch - 1,
            carryStretchV = if (next != null) (next.stretch - scene.stretch) * 120 else 0.0,
            vMarkSize = scene.markSize,
            vCut = scene.cut,
            vWord = scene.word,
            vWordY = scene.wordY,
        )
    }

    fun liveRadius(m: TokenFusionLive): Double {
        var area = mix(120.0 * 120, 55.0 * 55, m.gather)
        for (c in m.coins) area += max(0.0, 22.0 * 22 - c.r * c.r)
        return sqrt(max(55.0 * 55, area))
    }

    fun capture(m: TokenFusionLive, index: Int, speed: Double = 0.0) {
        val c = m.coins.getOrNull(index) ?: return
        if (c.state == 3 || c.state == 4) return
        c.state = 3
        c.age = 0.0
        c.speed = speed
        c.fromX = c.x
        c.fromY = c.y
        c.pointer = -1
        m.mergeEvents = m.mergeEvents or (1 shl index)
        m.mergedMask = m.mergedMask or (1 shl index)
        m.ringTime = 0.0
        m.ringSpeed = speed
        m.ringAxis = atan2(c.y - m.y, c.x - m.x)
        m.recoilVX -= cos(m.ringAxis) * RECOIL * 25
        m.recoilVY -= sin(m.ringAxis) * RECOIL * 25
        m.cue = if (speed > SPEED_MAX) 4 else 3
    }

    fun grab(m: TokenFusionLive, pointer: Int, x: Double, y: Double, touchCore: Boolean): Boolean {
        var best = -1
        var dist = 36.0
        for (i in m.coins.indices) {
            val c = m.coins[i]
            if (c.pointer >= 0 || c.state == 4 || (c.state == 3 && (c.age > PULL_BACK_SECONDS || c.r < MIN_GRAB_RADIUS))) continue
            val d = hypot(x - c.x, y - c.y)
            if (d < dist) {
                best = i
                dist = d
            }
        }
        if (best >= 0) {
            val c = m.coins[best]
            if (c.state == 3) {
                m.mergedMask = m.mergedMask and (1 shl best).inv()
                m.releaseEvents = m.releaseEvents or (1 shl best)
            }
            c.state = 1
            if (c.r >= 21.9) c.r = 22 * 1.04 // Immediate press response; the live spring continues to 1.12.
            c.pointer = pointer
            c.ox = x - c.x
            c.oy = y - c.y
            c.lastT = m.now
            c.snapped = false
            c.neckFormed = c.reach > 0.2
            c.lastX = c.x
            c.lastY = c.y
            c.age = 0.0
            m.cue = 1
            m.lastTouch = m.now
            return true
        }
        if (touchCore && m.corePointer < 0 && hypot(x - m.x, y - m.y) < min(liveRadius(m), 120.0)) {
            m.corePointer = pointer
            m.coreOX = x - m.x
            m.coreOY = y - m.y
            m.lastTouch = m.now
            m.coreLastT = m.now
            m.cue = 1
            return true
        }
        return false
    }

    fun movePointer(m: TokenFusionLive, pointer: Int, x: Double, y: Double, minX: Double, maxX: Double, minY: Double, maxY: Double) {
        if (pointer == m.corePointer) {
            var dx = x - m.coreOX - Theme.CORE_X
            var dy = y - m.coreOY - Theme.CORE_Y
            val d = hypot(dx, dy)
            val radius = Theme.TOUCH_CORE_LIMIT
            m.corePull = max(0.0, d - radius) / (120 + max(0.0, d - radius))
            m.ringAxis = atan2(dy, dx)
            if (d > radius) {
                dx *= radius / d
                dy *= radius / d
            }
            val dt = max(1.0 / 120, m.now - m.coreLastT)
            m.vx = (Theme.CORE_X + dx - m.x) / dt
            m.vy = (Theme.CORE_Y + dy - m.y) / dt
            m.x = Theme.CORE_X + dx
            m.y = Theme.CORE_Y + dy
            m.coreLastT = m.now
        }
        for (idx in m.coins.indices) {
            val c = m.coins[idx]
            if (c.pointer != pointer) continue
            val nx = clamp(x - c.ox, minX, maxX)
            val ny = clamp(y - c.oy, minY, maxY)
            val dt = max(1.0 / 120, m.now - c.lastT)
            c.vx = mix(c.vx, (nx - c.x) / dt, 0.7)
            c.vy = mix(c.vy, (ny - c.y) / dt, 0.7)
            c.x = nx
            c.y = ny
            c.lastT = m.now
            val gap = hypot(c.x - m.x, c.y - m.y) - liveRadius(m) - c.r
            if (gap > SNAP_GAP && c.neckFormed && !c.snapped) {
                c.snapped = true
                m.snapTime = 0.0
                m.cue = 2
            }
            if (gap < CONTACT_GAP) capture(m, idx, hypot(c.vx, c.vy))
        }
        m.lastTouch = m.now
    }

    fun releasePointer(m: TokenFusionLive, pointer: Int, cancel: Boolean = false) {
        if (m.corePointer == pointer) m.corePointer = -1
        for (i in m.coins.indices) {
            val c = m.coins[i]
            if (c.pointer != pointer) continue
            c.pointer = -1
            c.age = 0.0
            val gap = hypot(c.x - m.x, c.y - m.y) - liveRadius(m) - c.r
            if (!cancel && gap < CAPTURE_GAP) {
                capture(m, i, hypot(c.vx, c.vy))
            } else {
                c.state = if (cancel) 5 else 2
                c.fromX = c.x
                c.fromY = c.y
            }
        }
        m.lastTouch = m.now
    }

    /** JS `Math.round`: halves go up. */
    private fun jsRound(x: Double) = floor(x + 0.5)

    /** Semi-implicit live physics; fixed substeps keep throws stable at 30, 60 and 120 Hz. */
    fun stepLive(m: TokenFusionLive, dt: Double, reduced: Boolean = false, allowAutoFlood: Boolean = true) {
        val steps = max(1, ceil(dt * 120).toInt())
        val h = min(dt, 0.05) / steps
        for (n in 0 until steps) {
            m.now += h
            m.ringTime += h
            m.snapTime += h
            m.carryStretchV += (-160 * m.carryStretch - 12 * m.carryStretchV) * h
            m.carryStretch += m.carryStretchV * h
            var captured = 0
            for (c in m.coins) if ((c.state == 3 && c.age > 0.32) || c.state == 4) captured++
            val massTarget = if (m.ringTime > 0.18 + FIRST_RING_PEAK + MASS_LAG) captured.toDouble() else m.mass
            m.massV += (90 * (massTarget - m.mass) - 18 * m.massV) * h
            m.mass += m.massV * h
            m.angle += ORBIT_SPEEDS[min(6, jsRound(clamp(m.mass, 0.0, 6.0)).toInt())] * h
            if (m.corePointer < 0) {
                m.corePull *= exp(-h * 10)
                m.vx += ((110 * (Theme.CORE_X - m.x) - 16 * m.vx) / 1.2) * h
                m.x += m.vx * h
                m.vy += ((110 * (Theme.CORE_Y - m.y) - 16 * m.vy) / 1.2) * h
                m.y += m.vy * h
            }
            m.recoilVX += (-160 * m.recoilX - 12 * m.recoilVX) * h
            m.recoilX += m.recoilVX * h
            m.recoilVY += (-160 * m.recoilY - 12 * m.recoilVY) * h
            m.recoilY += m.recoilVY * h
            val r = liveRadius(m)
            for (i in m.coins.indices) {
                val c = m.coins[i]
                c.age += h
                val slot = orbitAt(i, m.coins.size, m.angle, m.gather, m.mass)
                val dx = m.x - c.x
                val dy = m.y - c.y
                val d = max(1.0, hypot(dx, dy))
                val gap = d - r - c.r
                if (c.state == 0 || c.state == 5) {
                    c.vx += (120 * (slot.x - c.x) - 14 * c.vx) * h
                    c.vy += (120 * (slot.y - c.y) - 14 * c.vy) * h
                    c.x += c.vx * h
                    c.y += c.vy * h
                    c.r += (22 - c.r) * min(1.0, h * 18)
                    if (c.state == 5 && hypot(slot.x - c.x, slot.y - c.y) < 1) c.state = 0
                    if (m.corePointer >= 0 && gap < CONTACT_GAP) capture(m, i, hypot(m.vx, m.vy))
                } else if (c.state == 2) {
                    val pull = if (gap < PULL_RADIUS) GRAVITY / max(gap, GRAVITY_MIN_GAP) else 0.0
                    c.vx = (c.vx + (dx / d) * pull * h) * DECAY_PER_MS.pow(h * 1000)
                    c.vy = (c.vy + (dy / d) * pull * h) * DECAY_PER_MS.pow(h * 1000)
                    c.x += c.vx * h
                    c.y += c.vy * h
                    if (gap < CAPTURE_GAP) {
                        capture(m, i, hypot(c.vx, c.vy))
                    } else if (c.age > FLIGHT_TIMEOUT) {
                        c.state = 6
                        c.age = 0.0
                        c.fromX = c.x
                        c.fromY = c.y
                    }
                } else if (c.state == 6) {
                    val p = seg(c.age, 0.0, 0.8)
                    val startAngle = atan2(c.fromY - m.y, c.fromX - m.x)
                    val endAngle = atan2(slot.y - m.y, slot.x - m.x)
                    val turn = ((((endAngle - startAngle) % (2 * PI)) + 2 * PI) % (2 * PI)) + 2 * PI
                    val angle = startAngle + turn * p
                    val radius = mix(hypot(c.fromX - m.x, c.fromY - m.y), hypot(slot.x - m.x, slot.y - m.y), p)
                    val nx = m.x + cos(angle) * radius
                    val ny = m.y + sin(angle) * radius
                    c.vx = (nx - c.x) / h
                    c.vy = (ny - c.y) / h
                    c.x = nx
                    c.y = ny
                    if (p >= 1) {
                        c.state = 5
                        c.age = 0.0
                    }
                } else if (c.state == 3) {
                    val p = clamp(springAt(c.age, 16.0, 200.0))
                    c.x = mix(c.fromX, m.x, p)
                    c.y = mix(c.fromY, m.y, p)
                    c.r = 22 * sqrt(max(0.0, 1 - seg(c.age, 0.0, 0.32)))
                    if (c.age > 0.32) {
                        c.state = 4
                        c.r = 0.0
                    }
                } else if (c.state == 1) {
                    c.r += (22 * 1.12 - c.r) * min(1.0, h * 22)
                }
                c.reach = if (reduced || ((c.state == 0 || c.state == 5) && m.corePointer < 0)) 0.0 else coinReach(c.x, c.y, c.r, m.x, m.y, r)
                if (c.reach > 0.2 && !c.neckFormed) {
                    c.neckFormed = true
                    if (c.state == 1 || c.state == 2) m.cue = 2
                }
                c.dye = if (c.state == 3 || c.state == 4) 1 - seg(c.age, 0.0, DYE_SECONDS) else c.reach
                c.heat = c.reach
                c.lean = (sign(m.x - c.x) * (min(1.0, m.mass) * 6 * PI)) / 180
            }
            m.mark += ((if (m.flood > 0.65 || m.coins.none { it.reach > 0.2 }) 1.0 else 0.0) - m.mark) * min(1.0, h * 14)
            val all = m.coins.all { it.state == 4 }
            if ((all && allowAutoFlood) || m.flood > 0) {
                m.floodV += (48.7 * (1 - m.flood) - 14 * m.floodV) * h
                m.flood = clamp(m.flood + m.floodV * h)
            }
            val follow = min(1.0, h * 14)
            m.vMarkSize = mix(m.vMarkSize, mix(mix(60.0, 40.0, m.gather), 60.0, m.flood), follow)
            m.vCut = mix(m.vCut, seg(m.flood, 0.4, 0.65) * (1 - seg(m.flood, 0.65, 0.85)), follow)
            m.vWord = mix(m.vWord, seg(m.flood, 0.88, 0.98), follow)
            m.vWordY = mix(m.vWordY, Theme.WORDMARK_Y + Theme.WORDMARK_RISE * (1 - seg(m.flood, 0.88, 0.98)), follow)
            m.stretch = if (reduced) 1.0
            else min(MAX_STRETCH, 1 + hypot(m.vx, m.vy) / 6000 + 0.25 * m.corePull) + ringAt(m.ringTime - 0.18, m.ringSpeed) + m.carryStretch
        }
    }

    fun sceneOf(m: TokenFusionLive, reduced: Boolean = false): TokenFusionScene {
        val r = liveRadius(m)
        val flood = m.flood
        return TokenFusionScene(
            x = m.x + (if (reduced) 0.0 else m.recoilX),
            y = m.y + (if (reduced) 0.0 else m.recoilY),
            r = mix(r, 980.0, flood),
            sphere = 1 - m.gather * 0.6,
            stretch = if (reduced) 1.0 else m.stretch + (0.05 * ringAt(m.snapTime)) / 0.09,
            axis = if (m.corePointer >= 0 && hypot(m.vx, m.vy) > 1) atan2(m.vy, m.vx) else m.ringAxis,
            ringScale = 1 - m.gather * 0.324,
            mark = if (reduced && flood > 0) 1.0 else m.mark,
            markSize = if (reduced && flood > 0) 60.0 else m.vMarkSize,
            cut = if (reduced) 0.0 else m.vCut,
            word = if (reduced) (if (flood > 0) 1.0 else 0.0) else m.vWord,
            wordY = if (reduced) Theme.WORDMARK_Y else m.vWordY,
            flood = flood,
            coins = m.coins.map { c ->
                TokenFusionCoin(
                    c.x, c.y, c.r * (1 + (if (c.snapped && !reduced) (0.03 * ringAt(m.snapTime)) / 0.09 else 0.0)), c.reach, c.dye, c.lean, c.heat,
                )
            },
        )
    }

    /** Blend of two scenes (the resume ease). */
    fun blendScene(a: TokenFusionScene, b: TokenFusionScene, p: Double): TokenFusionScene = TokenFusionScene(
        x = mix(a.x, b.x, p), y = mix(a.y, b.y, p), r = mix(a.r, b.r, p), sphere = mix(a.sphere, b.sphere, p),
        stretch = mix(a.stretch, b.stretch, p), axis = mix(a.axis, b.axis, p), ringScale = mix(a.ringScale, b.ringScale, p),
        mark = mix(a.mark, b.mark, p), markSize = mix(a.markSize, b.markSize, p), cut = mix(a.cut, b.cut, p),
        word = mix(a.word, b.word, p), wordY = mix(a.wordY, b.wordY, p), flood = mix(a.flood, b.flood, p),
        coins = b.coins.mapIndexed { i, c ->
            val o = a.coins[i]
            TokenFusionCoin(mix(o.x, c.x, p), mix(o.y, c.y, p), mix(o.r, c.r, p), mix(o.reach, c.reach, p), mix(o.dye, c.dye, p), c.lean, c.heat)
        },
    )
}
