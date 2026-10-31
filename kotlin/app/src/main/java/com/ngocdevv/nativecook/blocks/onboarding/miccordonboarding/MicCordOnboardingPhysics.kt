package com.ngocdevv.nativecook.blocks.onboarding.miccordonboarding

import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** The deterministic Verlet rope and the little notes puffed from its end. */
internal object MicCordOnboardingPhysics {
    const val N = 18
    const val SEG = 10.0
    const val GRAVITY = 1500.0
    const val LOOP = 8.0
    const val FIXED_STEP = 1.0 / 120.0
    const val NOTES = 14

    const val GRAB_AT = 1.0
    const val GRAB_RELEASE = 1.8
    const val GRAB_DX = 40.0
    const val GRAB_DY = -18.0

    data class Rope(
        val x: DoubleArray,
        val y: DoubleArray,
        val px: DoubleArray,
        val py: DoubleArray,
        var held: Int = 0,
        var hx: Double = 0.0,
        var hy: Double = 0.0,
        var speed: Double = 0.0,
        val notes: DoubleArray = DoubleArray(NOTES * 6),
        var spawn: Double = 0.0,
        var burst: Int = 0,
        var was: Int = 0,
        var seed: Int = 0,
    )

    fun makeRope(ax: Double, ay: Double): Rope {
        val x = DoubleArray(N) { ax }
        val y = DoubleArray(N) { ay + it * SEG }
        val notes = DoubleArray(NOTES * 6) { if (it % 6 == 4) 99.0 else 0.0 }
        return Rope(x, y, x.copyOf(), y.copyOf(), notes = notes)
    }

    fun copyRope(source: Rope): Rope = Rope(
        x = source.x.copyOf(),
        y = source.y.copyOf(),
        px = source.px.copyOf(),
        py = source.py.copyOf(),
        held = source.held,
        hx = source.hx,
        hy = source.hy,
        speed = source.speed,
        notes = source.notes.copyOf(),
        spawn = source.spawn,
        burst = source.burst,
        was = source.was,
        seed = source.seed,
    )

    fun hash(value: Double): Double {
        val s = sin(value * 12.9898 + 4.1414) * 43758.5453
        return s - floor(s)
    }

    /** One fixed Verlet step, matching the React Native worklet implementation. */
    fun stepRope(r: Rope, ax: Double, ay: Double, h: Double, damping: Double) {
        val ex = r.x[N - 1]
        val ey = r.y[N - 1]
        for (i in 1 until N) {
            val vx = (r.x[i] - r.px[i]) * damping
            val vy = (r.y[i] - r.py[i]) * damping
            r.px[i] = r.x[i]
            r.py[i] = r.y[i]
            val g = if (i == N - 1) GRAVITY * 1.6 else GRAVITY
            r.x[i] += vx
            r.y[i] += vy + g * h * h
        }
        r.x[0] = ax
        r.y[0] = ay
        r.px[0] = ax
        r.py[0] = ay

        repeat(14) {
            for (i in 0 until N - 1) {
                val dx = r.x[i + 1] - r.x[i]
                val dy = r.y[i + 1] - r.y[i]
                val d = max(0.0001, hypot(dx, dy))
                val diff = (d - SEG) / d
                val k = if (d > SEG) 0.5 else 0.25
                val mx = dx * diff * k
                val my = dy * diff * k
                if (i > 0) {
                    r.x[i] += mx
                    r.y[i] += my
                }
                r.x[i + 1] -= mx
                r.y[i + 1] -= my
            }
            if (r.held == 1) {
                r.x[N - 1] = r.hx
                r.y[N - 1] = r.hy
            }
        }
        r.speed = hypot(r.x[N - 1] - ex, r.y[N - 1] - ey) / h
    }

    /** Spawn and integrate notes. `quiet` is the reduced-motion path. */
    fun stepNotes(r: Rope, h: Double, quiet: Boolean) {
        r.spawn += h
        if (r.was == 1 && r.held == 0) r.burst = 4
        r.was = r.held
        if (!quiet && r.held == 0 && (r.speed > 300.0 || r.burst > 0) && r.spawn > 0.08) {
            if (r.burst > 0) r.burst -= 1
            r.spawn = 0.0
            var best = 0
            for (i in 1 until NOTES) {
                if (r.notes[i * 6 + 4] > r.notes[best * 6 + 4]) best = i
            }
            r.seed += 1
            val o = best * 6
            val dx = r.x[N - 1] - r.x[N - 3]
            val dy = r.y[N - 1] - r.y[N - 3]
            val dl = max(0.001, hypot(dx, dy))
            r.notes[o] = r.x[N - 1] + (dx / dl) * 200.0 + (hash(r.seed + 11.0) - 0.5) * 40.0
            r.notes[o + 1] = r.y[N - 1] + (dy / dl) * 200.0
            r.notes[o + 2] = (hash(r.seed.toDouble()) - 0.5) * 160.0 + (r.x[N - 1] - r.px[N - 1]) / h * 0.15
            r.notes[o + 3] = -120.0 - hash(r.seed + 7.0) * 120.0
            r.notes[o + 4] = 0.0
            r.notes[o + 5] = floor(hash(r.seed + 3.0) * 3.0)
        }
        for (i in 0 until NOTES) {
            val o = i * 6
            if (r.notes[o + 4] > 1.6) continue
            r.notes[o + 4] += h
            r.notes[o] += r.notes[o + 2] * h
            r.notes[o + 1] += r.notes[o + 3] * h
            r.notes[o + 2] *= 1.0 - h * 1.5
            r.notes[o + 3] *= 1.0 - h * 0.6
        }
    }
}
