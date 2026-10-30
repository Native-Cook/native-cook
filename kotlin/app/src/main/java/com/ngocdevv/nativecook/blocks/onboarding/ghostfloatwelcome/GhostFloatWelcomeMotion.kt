package com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome

import kotlin.math.*
import com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome.GhostFloatWelcomeTheme.bodies
import com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome.GhostFloatWelcomeTheme.stars

/** Analytic choreography. Double preserves the reference's JavaScript arithmetic. */
internal object GhostFloatWelcomeMotion {
    const val LOOP = 9.0
    const val RESUME = 2.0
    const val TAU = PI * 2
    object T {
        val blinks = doubleArrayOf(0.9, 3.95, 7.6)
        val inhale = doubleArrayOf(1.4, 1.75)
        const val puff = 1.75
        val ring = doubleArrayOf(1.75, 2.4)
        val tickPress = doubleArrayOf(4.45, 4.56)
        val tick = doubleArrayOf(4.56, 4.86)
        val button = doubleArrayOf(4.66, 5.0)
        val sheen = doubleArrayOf(5.0, 5.55)
        val happy = doubleArrayOf(4.7, 6.75)
        val roll = doubleArrayOf(4.8, 5.55)
        val lean = doubleArrayOf(5.0, 5.9)
        val untick = doubleArrayOf(6.95, 7.25)
        val buttonOff = doubleArrayOf(7.0, 7.3)
    }

    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)

    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))

    fun mix(a: Double, b: Double, p: Double) = a + (b - a) * p

    fun easeOut(x: Double): Double {
        val u = 1 - clamp01(x)
        return 1 - u * u * u * u * u
    }

    fun easeInOut(x: Double): Double {
        val p = clamp01(x)
        return if (p < 0.5) 16 * p * p * p * p * p else 1 - (-2 * p + 2).pow(5) / 2
    }

    fun smooth(x: Double): Double {
        val p = clamp01(x)
        return p * p * (3 - 2 * p)
    }

    fun kick(tau: Double, period: Double, decay: Double) = if (tau <= 0) 0.0 else 1.9 * exp(-tau / decay) * sin(TAU * tau / period)

    fun wobble(tau: Double, period: Double, decay: Double) = if (tau <= 0) 0.0 else exp(-tau / decay) * cos(TAU * tau / period)
    private data class FloatSpec(
        val nx: Int,
        val ny: Int,
        val px: Double,
        val py: Double,
        val ax: Double,
        val ay: Double,
        val rot: Double,
    )
    private val floats = arrayOf(
        FloatSpec(1, 2, 0.1, 0.3, 4.0, 5.5, 4.0),
        FloatSpec(2, 1, 0.55, 0.05, 5.0, 6.0, 7.0),
        FloatSpec(1, 2, 0.3, 0.8, 6.0, 5.0, 9.0),
        FloatSpec(2, 1, 0.85, 0.4, 4.5, 6.5, 10.0),
        FloatSpec(1, 1, 0.2, 0.65, 5.5, 7.0, 8.0),
        FloatSpec(2, 2, 0.7, 0.15, 4.0, 5.0, 9.0),
        FloatSpec(1, 2, 0.45, 0.95, 6.0, 5.0, 6.0),
    )

    data class Pose(val x: Double, val y: Double, val rot: Double = 0.0)

    data class BooOffset(val x: Double, val y: Double, val k: Double)

    fun booOffset(x: Double, y: Double, t: Double, gain: Double): BooOffset {
        val dx = x - bodies[0].x
        val dy = y - bodies[0].y
        val dist = hypot(dx, dy).let {
            if (it == 0.0) 1.0 else it
        }
        if (dist < 1) return BooOffset(0.0, 0.0, 0.0)
        val tau = t - (T.puff + 0.03 + dist / 700)
        val amp = gain * max(18.0, 74 - dist * 0.16)
        val k = kick(tau, 1.3, 0.55)
        return BooOffset(dx / dist * amp * k, dy / dist * amp * k, k)
    }

    fun leanOffset(x: Double, y: Double, t: Double): Pose {
        val lean = sin(PI * seg(t, T.lean[0], T.lean[1]))
        if (lean <= 0) return Pose(0.0, 0.0)
        val dx = GhostFloatWelcomeTheme.UI.buttonX + GhostFloatWelcomeTheme.UI.buttonW / 2 - x
        val dy = GhostFloatWelcomeTheme.UI.buttonY - y
        val dist = hypot(dx, dy).let {
            if (it == 0.0) 1.0 else it
        }
        val amp = 9 * lean * (1 - min(0.6, dist / 900))
        return Pose(dx / dist * amp, dy / dist * amp)
    }

    fun bodyPose(i: Int, t: Double, amb: Double): Pose {
        val b = bodies[i]
        val f = floats[i]
        val ph = amb / LOOP
        var x = b.x + f.ax * sin(TAU * (f.nx * ph + f.px))
        var y = b.y + f.ay * sin(TAU * (f.ny * ph + f.py))
        var rot = f.rot * sin(TAU * (ph + f.px + f.py))
        if (i > 0) {
            val o = booOffset(b.x, b.y, t, 1.0)
            val lean = leanOffset(b.x, b.y, t)
            x += o.x
            y += o.y
            x += lean.x
            y += lean.y
            rot += o.k * 26 * (if (i % 2 == 0) 1 else -1)
        }
        return Pose(x, y, rot)
    }

    fun starPose(i: Int, t: Double, amb: Double): Pose {
        val s = stars[i]
        val ph = amb / LOOP
        val o = booOffset(s.x, s.y, t, 0.55)
        return Pose(s.x + 3 * sin(TAU * (ph + i * 0.17)) + o.x, s.y + 4 * sin(TAU * (ph * 2 + i * 0.29)) + o.y, 18 * sin(TAU * (ph + i * 0.13)) + o.k * 40)
    }

    data class Frame(
        val t: Double,
        val inhale: Double,
        val puff: Double,
        val ring: Double,
        val wide: Double,
        val blink: Double,
        val happy: Double,
        val roll: Double,
        val hop: Double,
        val press: Double,
        val tick: Double,
        val button: Double,
        val sheen: Double,
    )

    fun frameAt(t: Double): Frame {
        var blink = 0.0
        for (at in T.blinks) {
            val p = seg(t, at, at + 0.16)
            if (p > 0 && p < 1) blink = sin(PI * p)
        }
        val inhale = if (t >= T.inhale[0] && t < T.puff) easeInOut(seg(t, T.inhale[0], T.puff)) else 0.0
        val puff = if (t >= T.puff) wobble(t - T.puff, 0.34, 0.24) else 0.0
        val rp = seg(t, T.ring[0], T.ring[1])
        val wide = if (t >= T.puff) max(0.0, 1 - seg(t, T.puff + 0.3, T.puff + 0.9)) else 0.0
        val happy = if (t < T.happy[1]) smooth(seg(t, T.happy[0], T.happy[0] + 0.18)) else 1 - smooth(seg(t, T.happy[1], T.happy[1] + 0.2))
        val rr = seg(t, T.roll[0], T.roll[1])
        val pp = seg(t, T.tickPress[0], T.tick[0] + 0.12)
        val sp = seg(t, T.sheen[0], T.sheen[1])
        return Frame(
            t = t,
            inhale = inhale,
            puff = puff,
            ring = if (rp > 0 && rp < 1) rp else -1.0,
            wide = wide,
            blink = blink,
            happy = happy,
            roll = 360 * easeInOut(rr),
            hop = 22 * sin(PI * rr),
            press = if (pp > 0 && pp < 1) sin(PI * pp) else 0.0,
            tick = if (t < T.untick[0]) easeOut(seg(t, T.tick[0], T.tick[1]))
                else 1 - easeOut(seg(t, T.untick[0], T.untick[1])),
            button = if (t < T.buttonOff[0]) easeInOut(seg(t, T.button[0], T.button[1]))
                else 1 - easeInOut(seg(t, T.buttonOff[0], T.buttonOff[1])),
            sheen = if (sp > 0 && sp < 1) sp else -1.0,
        )
    }

    data class Home(val x: Double, val y: Double, val r: Double, val mass: Double)

    fun liveHome(j: Int, t: Double, amb: Double): Home {
        if (j < bodies.size) {
            val p = bodyPose(j, t, amb)
            return Home(p.x, p.y, bodies[j].r, 1.0)
        }
        val i = j - bodies.size
        val p = starPose(i, t, amb)
        return Home(p.x, p.y, stars[i].s, 0.4)
    }

    fun follow(cur: Double, aim: Double, k: Double, dt: Double) = cur + (aim - cur) * (1 - exp(-dt * k))
}

