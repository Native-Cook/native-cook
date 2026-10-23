package com.ngocdevv.nativecook.blocks.gestures.pullcordtheme

import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeMotion.BulbMotion
import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeMotion.Feel
import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeMotion.Haptic
import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeMotion.Ignite
import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeMotion.Level
import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeMotion.Reveal
import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeMotion.Rope
import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeMotion.STEP
import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeMotion.Switch
import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeMotion.Tap
import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeTheme.Cord
import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeTheme.ReplicaArt
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
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One fixed-step simulation: a verlet cord on a spring switch, the hanging bulb's pendulum, the theme
 * reveal and the light inside the glass. A real finger and the autoplay's scripted finger both go
 * through [down], [move] and [up], so the demo shows exactly what a touch does. Port of cord-sim.ts.
 * Everything runs on the main thread, in the frame loop and in the pointer handler.
 */
internal class PullCordThemeSim(
    private val g: PullCordThemeGeometry,
    var reduced: Boolean,
    autoPlay: Boolean,
) {
    private val n = Cord.points

    var acc = 0.0
    var clock = 0.0
    val px = DoubleArray(n) { g.anchorX }
    val py = DoubleArray(n) { g.anchorY + g.seg * it }
    private val ox = px.copyOf()
    private val oy = py.copyOf()

    /** The switch's travel (e), its velocity and whether it can toggle again. */
    var e = 0.0
    private var ev = 0.0
    var armed = true

    /** 0 nothing, 1 the bead, 2 the bulb. */
    var grab = 0
    private var scripted = false
    private var fx = 0.0
    private var fy = 0.0
    private var gdx = 0.0
    private var gdy = 0.0
    private var downX = 0.0
    private var downY = 0.0
    private var downT = 0.0
    private var moved = 0.0

    /** The committed theme, 0 dark and 1 light, and the reveal tween between them. */
    var light = 0
    var rv = 0.0
    private var rFrom = 0.0
    private var rTo = 0.0
    private var rT = 1.0
    private var rDur = 1.0
    var level = 0.0
    var ignT = -1.0
    var commitT = 99.0
    private var tickAt = 0

    /** The bulb's pendulum on top of the idle swing. */
    private var th = 0.0
    private var thv = 0.0
    private var grabAngle = 0.0

    private var demoOn = autoPlay
    private var demoT = 0.0
    private var quiet = 0.0
    private var tapOn = false
    private var tapT = 0.0

    /** Haptic kinds waiting for the frame loop to hand them to the device. */
    val events = ArrayList<Int>()
    private val restX: DoubleArray
    private val restY: DoubleArray

    init {
        // Let the cord settle under gravity once, and keep that pose as the rest the loop returns to.
        for (i in 0 until Rope.settleSteps) ropeStep()
        restX = px.copyOf()
        restY = py.copyOf()
        px.copyInto(ox)
        py.copyInto(oy)
    }

    private fun solveRope() {
        for (pass in 0 until Rope.passes) {
            for (i in 0 until n - 1) {
                val j = i + 1
                val dx = px[j] - px[i]
                val dy = py[j] - py[i]
                val d = sqrt(dx * dx + dy * dy).takeIf { it != 0.0 } ?: 1e-6
                // A cord only resists stretching.
                if (d <= g.seg) continue
                val wi = if (i == 0) 0.0 else 1.0
                val wj = if (j == n - 1) (if (grab == 1) 0.0 else Rope.beadWeight) else 1.0
                val sum = wi + wj
                if (sum == 0.0) continue
                val corr = (d - g.seg) / d / sum
                px[i] += dx * corr * wi
                py[i] += dy * corr * wi
                px[j] -= dx * corr * wj
                py[j] -= dy * corr * wj
            }
            // A little bending stiffness: two links apart never closer than 1.6 links, so the cord never kinks.
            for (i in 1 until n - 2) {
                val j = i + 2
                val dx = px[j] - px[i]
                val dy = py[j] - py[i]
                val d = sqrt(dx * dx + dy * dy).takeIf { it != 0.0 } ?: 1e-6
                val minimum = 1.6 * g.seg
                if (d >= minimum) continue
                val wj = if (j == n - 1) (if (grab == 1) 0.0 else Rope.beadWeight) else 1.0
                val corr = ((d - minimum) / d / (1 + wj)) * 0.5
                px[i] += dx * corr
                py[i] += dy * corr
                px[j] -= dx * corr * wj
                py[j] -= dy * corr * wj
            }
        }
    }

    /** Verlet: the anchor rides the switch, everything else falls; then the cord's constraints. */
    private fun ropeStep() {
        px[0] = g.anchorX
        py[0] = g.anchorY + e
        ox[0] = px[0]
        oy[0] = py[0]
        val gdt = Rope.gravity * g.s * STEP * STEP
        val last = if (grab == 1) n - 1 else n
        for (i in 1 until last) {
            val vx = (px[i] - ox[i]) * Rope.damping
            val vy = (py[i] - oy[i]) * Rope.damping
            ox[i] = px[i]
            oy[i] = py[i]
            px[i] += vx
            py[i] += vy + gdt
        }
        solveRope()
    }

    /** The bulb's angle about its pivot: the source's slow swing plus whatever a finger gave it. */
    fun bulbAngle(): Double {
        val amp = if (reduced) 0.0 else ReplicaArt.swingSrc * PullCordThemeTheme.FIT_K * g.s
        return (amp / g.glassArm) *
            sin((2 * PI * clock) / BulbMotion.period + PullCordThemeMotion.REPLICA_SWING_PHASE) + th
    }

    /** World x of the glass centre, where the light (and the reveal) comes from. */
    fun glassCenterX(): Double = g.pivotX + sin(bulbAngle()) * g.glassArm

    /** World y of the glass centre. */
    fun glassCenterY(): Double = g.pivotY + cos(bulbAngle()) * g.glassArm

    private fun startReveal(to: Int) {
        rFrom = rv
        rTo = to.toDouble()
        rT = 0.0
        val full = if (reduced) Reveal.reducedFade else if (to == 1) Reveal.toLight else Reveal.toDark
        rDur = max(0.05, full * abs(to - rv))
    }

    private fun toggle() {
        light = 1 - light
        armed = false
        // The scrub preview already showed part of the reveal: continue from it, no jump.
        if (!reduced) rv = if (light == 1) max(rv, Feel.preview) else min(rv, 1 - Feel.preview)
        startReveal(light)
        if (light == 0 && !reduced) ignT = 0.0
        commitT = 0.0
        // The switch's snap tugs the bulb into a swing (the cord hangs right of it).
        if (!reduced) thv += Feel.kick
        if (!scripted) events.add(Haptic.DETENT)
    }

    /** An accessibility activate: plays one scripted pull through the same kernel. */
    fun requestToggle() {
        demoOn = false
        quiet = 0.0
        tapOn = true
        tapT = 0.0
    }

    fun down(x: Double, y: Double, script: Boolean = false) {
        scripted = script
        if (!scripted) {
            demoOn = false
            tapOn = false
            quiet = 0.0
        }
        val last = n - 1
        val bx = px[last]
        val by = py[last] + (Cord.beadHeight * g.s) / 2
        val hit = (Cord.hit * g.s) / 2 + 8
        val cx = glassCenterX()
        val cy = glassCenterY()
        if (abs(x - bx) < hit && abs(y - by) < hit + (Cord.beadHeight * g.s) / 2) {
            grab = 1
            gdx = px[last] - x
            gdy = py[last] - y
            if (!scripted) events.add(Haptic.GRAB)
        } else if (hypot(x - cx, y - cy) < ReplicaArt.grabRadius * g.s) {
            grab = 2
            grabAngle = atan2(x - g.pivotX, y - g.pivotY) - bulbAngle()
        } else {
            grab = 0
        }
        fx = x + gdx
        fy = y + gdy
        downX = x
        downY = y
        downT = clock
        moved = 0.0
    }

    fun move(x: Double, y: Double) {
        if (grab == 0) return
        moved = max(moved, hypot(x - downX, y - downY))
        fx = x + gdx
        fy = y + gdy
    }

    fun up(vx: Double, vy: Double, cancelled: Boolean = false) {
        if (grab == 1) {
            val last = n - 1
            val isTap = !scripted && !cancelled && moved < Tap.slop && clock - downT < Tap.time
            val limit = Rope.releaseSpeed
            val releaseVx = vx.coerceIn(-limit, limit)
            val releaseVy = vy.coerceIn(-limit, limit)
            ox[last] = px[last] - releaseVx * STEP
            oy[last] = py[last] - releaseVy * STEP
            grab = 0
            // A fast downward flick commits even short of the detent.
            if (armed && releaseVy > Feel.flickSpeed * g.s && e > Feel.flickMin * g.s) toggle()
            if (isTap) {
                tapOn = true
                tapT = 0.0
            }
        } else if (grab == 2) {
            thv = (vx / g.glassArm).coerceIn(-BulbMotion.maxThrow, BulbMotion.maxThrow)
            grab = 0
        }
        scripted = false
    }

    /** Runs one scripted finger track; offsets are from the bead's rest position. */
    private fun runScript(track: Array<DoubleArray>, t0: Double, t1: Double) {
        val bx = g.anchorX
        val by = g.restY
        for (segment in track) {
            val a = segment[0]
            val b = segment[1]
            if (segment[6] == 1.0 && t0 <= a && a < t1) {
                down(bx + segment[2] * g.s, by + segment[3] * g.s, script = true)
            }
            if (t1 > a && t0 < b) {
                val u = easeInOutSine(((min(t1, b) - a) / (b - a)).coerceIn(0.0, 1.0))
                val x = bx + (segment[2] + (segment[4] - segment[2]) * u) * g.s
                val y = by + (segment[3] + (segment[5] - segment[3]) * u) * g.s
                if (scripted && grab != 0) move(x, y)
            }
            if (segment[7] == 1.0 && t0 < b && b <= t1 && scripted) up(0.0, 0.0)
        }
    }

    /** At the loop seam the rope has settled; snapping the last fraction of a point keeps the video loop exact. */
    private fun snapRestIfStill() {
        var worst = abs(e)
        for (i in 0 until n) {
            worst = max(worst, max(abs(px[i] - restX[i]), abs(py[i] - restY[i])))
        }
        if (worst > Rope.snapRest * g.s || grab != 0) return
        for (i in 0 until n) {
            px[i] = restX[i]
            py[i] = restY[i]
            ox[i] = px[i]
            oy[i] = py[i]
        }
        e = 0.0
        ev = 0.0
        th = 0.0
        thv = 0.0
        level = light.toDouble()
    }

    private fun fixedStep(autoPlay: Boolean) {
        val dt = STEP
        val loop = PullCordThemeMotion.LOOP

        // Scripted fingers first, through the same kernel as a touch.
        if (demoOn) {
            val t0 = demoT
            var t1 = t0 + dt
            runScript(PullCordThemeMotion.SCRIPT, t0, t1)
            if (t1 >= loop) {
                t1 -= loop
                snapRestIfStill()
            }
            demoT = t1
        } else if (tapOn) {
            val t0 = tapT
            val t1 = t0 + dt
            runScript(PullCordThemeMotion.TAP_PULL, t0, t1)
            tapT = t1
            if (t1 > PullCordThemeMotion.TAP_PULL.last()[1] + dt) tapOn = false
        } else if (autoPlay && grab == 0) {
            quiet += dt
            if (quiet * 1000 >= PullCordThemeMotion.RESUME_MS) {
                demoOn = true
                demoT = if (light == 1) PullCordThemeMotion.LIGHT_REST_AT else 0.0
            }
        }

        // The bead follows the finger; the cord drags the switch down with a rubber band.
        val prevE = e
        var pullTarget = 0.0
        if (grab == 1) {
            var dx = fx - g.anchorX
            val side = Rope.sideReach * g.s
            if (abs(dx) > side) dx = sign(dx) * (side + rubber(abs(dx) - side, side * 0.5))
            var dy = fy - g.anchorY
            val lift = g.length - Rope.liftReach * g.s
            if (dy < lift) dy = lift - rubber(lift - dy, 24 * g.s)
            val dist = sqrt(dx * dx + dy * dy).takeIf { it != 0.0 } ?: 1e-6
            val over = dist - g.length
            var reach = dist
            if (over > 0) {
                // Free travel first, then the switch's spring stiffens into a rubber band.
                val lin = Switch.linear * g.s
                pullTarget = if (over < lin) over else lin + rubber(over - lin, (Switch.travel - Switch.linear) * g.s)
                reach = g.length + pullTarget
            }
            ox[n - 1] = px[n - 1]
            oy[n - 1] = py[n - 1]
            px[n - 1] = g.anchorX + (dx / dist) * reach
            py[n - 1] = g.anchorY + (dy / dist) * reach
        }
        if (grab == 1 && pullTarget >= e) {
            e = pullTarget
            ev = (e - prevE) / dt
        } else {
            val damping = if (reduced) Switch.reducedDamping else Switch.damping
            ev += (-Switch.stiffness * e - damping * ev) * dt
            e += ev * dt
            if (e < pullTarget) {
                e = pullTarget
                ev = 0.0
            }
            if (e < 0) {
                if (ev < -260 && !scripted && grab == 0 && !demoOn && !tapOn) events.add(Haptic.HOME)
                e = 0.0
                ev = 0.0
            }
        }

        ropeStep()

        // The detent.
        if (e < Switch.rearm * g.s) armed = true
        if (armed && e >= Switch.detent * g.s) toggle()
        // A tick every few points of pull while the finger holds the cord.
        if (grab == 1) {
            val k = floor(e / (Feel.tick * g.s)).toInt()
            if (k != tickAt) {
                if (k > tickAt && !scripted) events.add(Haptic.TICK)
                tickAt = k
            }
        } else {
            tickAt = 0
        }
        commitT += dt

        // Reveal, liquid light and ignition.
        if (rT < rDur) {
            rT = min(rDur, rT + dt)
            val u = rT / rDur
            val eased = if (reduced) u else revealEase(u, rTo == 1.0)
            rv = rFrom + (rTo - rFrom) * eased
        }
        val pull = (e / (Switch.detent * g.s)).coerceIn(0.0, 1.0)
        val target = if (armed) (if (light == 1) 1 - pull else pull) else light.toDouble()
        val rate = if (armed) Level.follow else Level.fill
        level += (target - level) * (1 - exp(-rate * dt))
        if (ignT >= 0) {
            ignT += dt
            if (ignT > Ignite.times.last()) ignT = -1.0
        }

        // The bulb: a damped pendulum on top of the idle swing; a finger can hold it.
        val w0 = (2 * PI) / BulbMotion.period
        if (grab == 2) {
            val want = atan2(fx - gdx - g.pivotX, fy - gdy - g.pivotY) - grabAngle
            val idle = bulbAngle() - th
            var wanted = want - idle
            val m = BulbMotion.maxAngle
            if (abs(wanted) > m) wanted = sign(wanted) * (m + rubber(abs(wanted) - m, m * 0.4))
            thv = (wanted - th) / dt
            th = wanted
        } else {
            thv += (-w0 * w0 * th - 2 * BulbMotion.damping * w0 * thv) * dt
            th += thv * dt
        }
        clock += dt
    }

    /** Advances by [frameDt] seconds in fixed steps. */
    fun step(frameDt: Double, autoPlay: Boolean) {
        acc = min(acc + frameDt, STEP * PullCordThemeMotion.MAX_STEPS)
        while (acc >= STEP) {
            fixedStep(autoPlay)
            acc -= STEP
        }
    }

    /** The neon filament's brightness while it re-ignites, 1 when it is steady. */
    fun igniteValue(): Double {
        if (ignT < 0) return 1.0
        val ts = Ignite.times
        val vs = Ignite.values
        for (i in 0 until ts.size - 1) {
            if (ignT <= ts[i + 1]) {
                val u = (ignT - ts[i]) / (ts[i + 1] - ts[i])
                return vs[i] + (vs[i + 1] - vs[i]) * u
            }
        }
        return 1.0
    }

    /** Radius that covers the whole screen from the glass centre. */
    fun revealReach(cx: Double, cy: Double): Double {
        val dx = max(cx, g.w - cx)
        val dy = max(cy, g.h - cy)
        return sqrt(dx * dx + dy * dy) + Reveal.margin * g.s
    }

    private fun rubber(over: Double, limit: Double) = limit * (1 - exp(-over / limit))

    private fun easeInOutSine(u: Double) = (1 - cos(PI * u)) / 2

    private fun revealEase(u: Double, toLight: Boolean): Double {
        if (toLight) return 1 - (1 - u).pow(5)
        return if (u < 0.5) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2
    }
}
