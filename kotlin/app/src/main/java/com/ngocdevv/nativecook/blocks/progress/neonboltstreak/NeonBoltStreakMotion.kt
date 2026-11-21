package com.ngocdevv.nativecook.blocks.progress.neonboltstreak

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

internal object NeonBoltStreakMotion {
    const val LOOP = 9.5
    const val TODAY = 3
    val DAYS = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private fun segment(t: Double, a: Double, b: Double): Float = ((t - a) / (b - a)).coerceIn(0.0, 1.0).toFloat()
    private fun out(k: Float) = 1f - (1f - k).pow(3)
    private fun inOut(k: Float) = if (k < .5f) 4f * k * k * k else 1f - (-2f * k + 2f).pow(3) / 2f
    private fun bump(t: Double, a: Double, width: Double): Float = if (t <= a || t >= a + width) 0f else sin((t - a) / width * PI).toFloat()

    private fun rest(): NeonBoltStreakFrame = NeonBoltStreakFrame(
        lit = floatArrayOf(1f, 1f, 1f, 0f, 0f, 0f, 0f),
        fill = floatArrayOf(1f, 1f, 1f, 0f, 0f, 0f, 0f),
    )

    private fun ignite(elapsed: Double, day: Int, long: Boolean, charge: Float, base: NeonBoltStreakFrame): NeonBoltStreakFrame {
        if (elapsed < -.05) return base
        val lit = base.lit.copyOf(); val fill = base.fill.copyOf(); val head = base.head.copyOf()
        val contact = if (long) .25 else .15
        val catchAt = contact + if (long) .24 else .08
        val duration = if (long) .4 else .3
        if (elapsed < contact) return base.copy(strikeDay = day, strikeDrop = segment(elapsed, 0.0, contact))
        val jolt = (if (long) 1.5f else .8f) * (1f - out(segment(elapsed, contact, contact + .12)))
        if (elapsed < catchAt) {
            val spark = elapsed - contact
            lit[day] = if (long) when {
                spark < .04 -> .35f
                spark < .09 -> 0f
                spark < .12 -> .15f
                spark < .16 -> 0f
                spark < .21 -> .6f
                else -> 0f
            } else if (spark < .04) .35f else 0f
            fill[day] = if (long) when { spark < .09 -> .07f; spark < .16 -> .05f; else -> .2f } else .08f
            head[day] = if (lit[day] > 0f) 1f else 0f
            return base.copy(lit = lit, fill = fill, head = head, strikeDay = day, strikeDrop = 1f, jolt = jolt)
        }
        val k = segment(elapsed, catchAt, catchAt + duration)
        val progress = if (long) 1f - (1f - k).pow(2) else out(k)
        val initial = if (long) .2f else .15f
        fill[day] = initial + (1.02f - initial) * progress
        head[day] = 1f - segment(elapsed, catchAt + duration, catchAt + duration + .1)
        val overshoot = 1.15f + .2f * charge
        val g = elapsed - catchAt - duration
        lit[day] = when {
            g < 0 -> .6f + .3f * k
            g < .06 -> .9f + (overshoot - .9f) * out((g / .06).toFloat())
            else -> overshoot + (1f - overshoot) * inOut(segment(g, .06, .32))
        }
        lit[day] *= 1f - .15f * (bump(elapsed, catchAt + duration + .30, .07) + bump(elapsed, catchAt + duration + .46, .07))
        return base.copy(lit = lit, fill = fill, head = head, strikeDay = day, strikeDrop = 1f, jolt = jolt)
    }

    fun autoplay(raw: Double): NeonBoltStreakFrame {
        val t = raw % LOOP
        var frame = rest().copy(time = t, camera = if (t < 2.25) inOut(segment(t, .3, 1.0)) else 1f - inOut(segment(t, 2.25, 2.85)))
        val strikes = arrayOf(Triple(3, 1.15, true), Triple(4, 3.6, false), Triple(5, 3.75, false), Triple(6, 3.9, false))
        if (t < 7.4) strikes.forEach { (day, start, long) -> frame = ignite(t - start, day, long, 0f, frame) }
        else {
            val reset = inOut(segment(t, 7.48, 8.12)); val lit = frame.lit.copyOf(); val fill = frame.fill.copyOf()
            for (day in 3..6) { lit[day] = 1f - reset; fill[day] = 1f - reset }
            frame = frame.copy(lit = lit, fill = fill)
        }
        var count = 3f + out(segment(t, 2.75, 3.05))
        arrayOf(3.6, 3.75, 3.9).forEach { count += out(segment(t, it + .3, it + .6)) }
        count -= 4f * inOut(segment(t, 7.5, 8.12))
        var flash = 0f
        arrayOf(2.75 to 1f, 3.93 to .3f, 4.08 to .3f, 4.23 to 1f).forEach { (start, strength) ->
            val age = t - start; if (age in 0.0..<.6) flash = max(flash, strength * (1f - (age / .6).toFloat()).pow(2))
        }
        frame = frame.copy(count = count, flash = flash)
        if (t in 4.55..<7.4) {
            val c = t - 4.55; val lit = frame.lit.copyOf()
            for (day in 0..6) lit[day] += .3f * bump(c, .1 * day, .25) + .18f * bump(c, 1.1 + .06 * day, .22)
            frame = frame.copy(lit = lit, border = 1.01f * inOut(segment(c, .1, 1.1)), borderLit = c >= .1, flood = inOut(segment(c, 1.05, 1.65)), ghost = .12f + .18f * (1f - out(segment(c, 1.08, 1.55))))
        } else frame = frame.copy(ghost = .07f + .05f * flash)
        return frame
    }

    fun manual(elapsed: Double, day: Int, charge: Float, celebration: Boolean, flicker: Boolean): NeonBoltStreakFrame {
        if (celebration) return NeonBoltStreakFrame(time = elapsed, lit = FloatArray(7) { 1f }, fill = FloatArray(7) { 1f }, count = 7f, flood = inOut(segment(elapsed, .15, .7)), border = 1.01f * inOut(segment(elapsed, .05, .75)), borderLit = true, ghost = .2f)
        if (flicker) {
            val glow = when { elapsed < .05 -> .12f; elapsed < .11 -> .65f; elapsed < .18 -> .08f; else -> 1f + .15f * (1f - out(segment(elapsed, .2, .5))) }
            val base = rest(); val lit = base.lit.copyOf(); val fill = base.fill.copyOf(); lit[day] = glow; fill[day] = 1f
            return base.copy(time = elapsed, lit = lit, fill = fill)
        }
        var frame = ignite(elapsed, day, true, charge, rest()).copy(time = elapsed, charge = charge)
        if (elapsed > .72) { val settled = out(segment(elapsed, .72, 1.05)); frame = frame.copy(count = 3f + settled, flash = (1f - settled) * (.65f + .35f * charge)) }
        return frame
    }

    fun flicker(elapsed: Double): Float = when {
        elapsed < .05 -> .12f
        elapsed < .11 -> .65f
        elapsed < .18 -> .08f
        else -> 1f + .15f * (1f - out(segment(elapsed, .2, .5)))
    }
}
