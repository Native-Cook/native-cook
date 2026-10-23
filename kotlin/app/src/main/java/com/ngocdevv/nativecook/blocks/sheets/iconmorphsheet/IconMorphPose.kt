package com.ngocdevv.nativecook.blocks.sheets.iconmorphsheet

import kotlin.math.*

/** Pure model-space poses and support bounds, matching icon-timeline.ts. */
internal object IconMorphPose {
    private val loops = doubleArrayOf(3.2, 3.4, 3.4, 2.8)
    private val pace = doubleArrayOf(1.6, 3.4 / 2.3, 3.4 / 2.4, 1.4)
    private val hero = doubleArrayOf(1.3, 1.4, 1.5, 1.15)
    private fun ease(a: Double, b: Double, v: Double): Double {
        val x = ((v - a) / (b - a)).coerceIn(0.0, 1.0)
        return x * x * (3 - 2 * x)
    }
    private fun mix(a: Double, b: Double, v: Double) = a + (b - a) * v
    private fun pop(a: Double, b: Double, t: Double, overshoot: Double = 1.5): Double {
        val tail = ((t - a) / (b - a)).coerceIn(0.0, 1.0) - 1
        return 1 + (1 + overshoot) * tail * tail * tail + overshoot * tail * tail
    }

    fun advance(times: DoubleArray, position: Double, previous: Double, delta: Double,
                running: Double, wasRunning: Double, reduced: Boolean): DoubleArray {
        val rest = round(position).toInt()
        val atRest = abs(position - rest) < 0.00001
        return DoubleArray(4) { i ->
            when {
                reduced -> hero[i]
                running < 0.5 || (atRest && i != rest) -> 0.0
                atRest && abs(previous - rest) < 0.00001 && wasRunning > 0.5 ->
                    (times[i] + max(0.0, delta) * pace[i]) % loops[i]
                else -> times[i]
            }
        }
    }

    private fun coin(t: Double, release: Double, which: Int): DoubleArray {
        val big = which == 0
        val phase = (t - 1.3) * (if (big) 3.0 else 3.8) + which * 0.35
        val hx = if (big) 0.0 else if (which == 1) -0.37 else 0.37
        val hy = if (big) 0.61 else -0.08
        val x = if (big) -0.08 + 0.46 * sin(phase) else (if (which == 1) -1.03 else 1.12) + 0.12 * sin(phase)
        val y = if (big) 0.52 + 0.18 * sin(phase) else -0.60 + 0.26 * sin(phase + which)
        val z = if (big) 0.12 + 0.54 * cos(phase) else 0.20 + 0.43 * sin(phase)
        return doubleArrayOf(mix(hx, x, release), mix(hy, y, release), mix(0.0, z, release),
            (if (big) 0.63 else 0.26) * mix(0.16, 1.0, ease(0.0, if (t > 2.6) 0.24 else 0.6, release)),
            phase * (if (big) 1.65 else 2.2) - (if (big) 0.38 else 0.12),
            if (big) -0.18 else 0.60 * cos(phase))
    }
    private fun turnX(v: DoubleArray, a: Double) = doubleArrayOf(v[0], cos(a) * v[1] + sin(a) * v[2], -sin(a) * v[1] + cos(a) * v[2])
    private fun turnY(v: DoubleArray, a: Double) = doubleArrayOf(cos(a) * v[0] - sin(a) * v[2], v[1], sin(a) * v[0] + cos(a) * v[2])
    private fun turnZ(v: DoubleArray, a: Double) = doubleArrayOf(cos(a) * v[0] + sin(a) * v[1], -sin(a) * v[0] + cos(a) * v[1], v[2])
    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun support(n: DoubleArray, x: Double, y: Double, r: Double, depth: Double, bevel: Double): Double {
        return (x - r) * abs(n[0]) + (y - r) * abs(n[1]) +
            (r - bevel) * hypot(n[0], n[1]) + (depth - bevel) * abs(n[2]) + bevel
    }

    private fun sceneSupport(id: Int, sign: Double, pose: Map<String, DoubleArray>): Double {
        val n = doubleArrayOf(sqrt(0.5) * sign, sqrt(0.5) * sign, 0.0)
        val zero = pose.getValue("uZero")
        val safe = pose.getValue("uSafe")
        val env = pose.getValue("uEnvelope")
        val note = pose.getValue("uNote")
        val book = pose.getValue("uBook")
        val turn = pose.getValue("uBookTurn")
        if (id == 0) {
            val q = turnY(n, -0.18)
            q[1] *= zero[1]
            var bound = support(q, 0.51, 0.85, 0.43, 0.205, 0.065)
            if (zero[0] > 0) for (i in 0..2) {
                val c = pose.getValue("uCoin$i")
                val t = pose.getValue("uCoinTurn$i")
                val v = doubleArrayOf(n[0], n[1] * t[2], -n[1] * t[3])
                val local = doubleArrayOf(v[0] * t[0] - v[2] * t[1], v[1], v[0] * t[1] + v[2] * t[0])
                val depth = c[3] * mix(0.80, 0.105, max(zero[2], ease(0.0, 0.82, zero[0]))) + 0.028
                bound = max(bound, dot(n, c) + c[3] * hypot(local[0], local[1]) + depth * abs(local[2]))
            }
            return bound
        }
        if (id == 1) {
            val k = safe[0]
            val q = turnY(n, -PI * k - 0.14 * k)
            var bound = support(q, 0.88, 0.88, 0.88, 0.11 - 0.02 * k, 0.044)
            if (k > 0) {
                val f = turnX(doubleArrayOf(-q[0], q[1], -q[2]), -0.16 * k)
                val bud = safe[2]
                if (bud > 0) {
                    val centre = doubleArrayOf(mix(0.42, 0.65, bud), mix(0.44, 0.66, bud), mix(-0.065, 0.20, bud))
                    val size = mix(0.18, 1.0, ease(0.0, 0.65, bud))
                    val rim = maxOf(0.285 * f[1], 0.285 * abs(f[0]) + 0.2394 * f[1], -0.325 * f[1])
                    bound = max(bound, dot(f, centre) + size * (rim + 0.025 + 0.095 * abs(f[2])))
                }
                bound = max(bound, dot(f, doubleArrayOf(0.0, 0.015, 0.32 * k)) +
                    (if (f[1] > 0) 0.12 else 0.28) * k * abs(f[1]) + mix(0.065, 0.35, k))
            }
            return bound
        }
        if (id == 2) {
            val q = turnZ(turnX(turnY(n, env[3] - 0.10), -0.16), -0.10)
            val offset = q[1] * note[1]
            var bound = support(q, 0.91, 0.57, 0.13, 0.28, 0.06) - 0.10 * q[1] + 0.085 * q[2]
            val flapN = turnX(q, -env[0] * 2.82)
            val flap = max(0.80 * abs(flapN[0]), -0.66 * flapN[1]) + 0.06 * hypot(flapN[0], flapN[1]) + 0.065 * abs(flapN[2])
            bound = max(bound, dot(q, doubleArrayOf(0.0, 0.42, 0.02)) + flap + 0.14 * flapN[2])
            val rise = note[0]
            if (rise > 0) bound = max(bound, support(q, 0.76, 0.44 + 0.12 * rise, 0.14, 0.045, 0.028) +
                dot(q, doubleArrayOf(0.0, -0.08 + 0.37 * rise, -0.03 + 0.10 * rise)))
            for (i in 0..1) {
                val bud = env[i + 1]
                if (bud <= 0) continue
                val h = i == 0
                val at = doubleArrayOf(mix(if (h) 0.40 else -0.50, if (h) 1.04 else -0.96, bud),
                    mix(if (h) 0.02 else -0.20, if (h) 0.78 else -0.54, bud) +
                        (if (h) 0.025 else 0.023) * sin(note[3] * 3 + (if (h) 0 else 2)) * min(bud, 1.0),
                    mix(if (h) -0.06 else 0.02, if (h) 0.20 else 0.36, bud))
                bound = max(bound, dot(q, at) + (if (h) 0.265 else 0.34) * mix(0.16, 1.0, ease(0.0, 0.72, bud)))
            }
            return bound + offset
        }
        val q = turnX(turnY(turnZ(n, turn[1]), turn[2]), turn[0])
        var bound = support(q, 0.93, 0.75, 0.18, 0.22, 0.027)
        bound = max(bound, 0.75 * q[0] - 0.57 * q[1] + (0.17 + 0.24 * book[3]) * q[2] + 0.18)
        if (book[1] > 0) {
            val b = book[1]
            bound = max(bound, dot(q, doubleArrayOf(mix(0.63, 0.86, b), mix(0.54, 0.68, b), mix(0.06, 0.37, b))) +
                0.22 * mix(0.16, 1.0, ease(0.0, 0.70, b)))
        }
        return bound
    }

    fun frame(position: Double, inputTimes: DoubleArray, bounce: Double, lean: Double,
              press: Double, reduced: Boolean): Map<String, DoubleArray> {
        val pos = position.coerceIn(0.0, 3.0)
        val lower = floor(pos).toInt()
        val upper = min(3, lower + 1)
        val reverse = !reduced && pos > lower && inputTimes[lower] == 0.0 && inputTimes[upper] > 0
        val a = if (reverse) upper else lower
        val b = if (reverse) lower else upper
        val s = if (reverse) upper - pos else pos - lower
        val w = ((s - 0.06) / 0.88).coerceIn(0.0, 1.0)
        val activity = ease(0.06, 0.15, s) * (1 - ease(0.85, 0.94, s))
        val mid = sin(PI * s) * activity
        val times = DoubleArray(4) { if (reduced) hero[it] else if (it == a) inputTimes[it] % loops[it] else 0.0 }
        val (zt, st, et, bt) = times
        val zr = ease(0.38, 0.82, zt) * (1 - ease(2.68, 2.96, zt))
        val coins = Array(3) { coin(zt, zr, it) }
        val safe = ease(0.30, 0.70, st) * (1 - ease(2.75, 3.10, st))
        val opened = ease(0.25, 0.70, et) * (1 - ease(2.40, 2.90, et))
        val heart = pop(0.32, 0.75, et) * (1 - ease(2.40, 2.80, et))
        val grin = pop(0.40, 0.83, et) * (1 - ease(2.48, 2.88, et))
        val flop = ease(1.35, 1.90, bt) * (1 - ease(2.20, 2.80, bt))
        val badge = pop(0.50, 0.86, bt) * (1 - ease(1.35, 1.62, bt))
        var radius = max(1.72, coins.maxOf { sqrt(it[0] * it[0] + it[1] * it[1] + it[2] * it[2]) + it[3] + 0.06 })
        if (a == 0) radius = mix(radius, 1.72, ease(0.85, 0.94, s))
        val frame = linkedMapOf(
            "uScene" to doubleArrayOf(a.toDouble(), b.toDouble(), w, s),
            "uRadius" to doubleArrayOf(radius),
            "uShape" to doubleArrayOf(0.24, 1.0, if (reduced) 0.0 else 0.16 * sin(2 * PI * s) * activity, if (reverse) -1.0 else 1.0),
            "uTurn" to doubleArrayOf(if (reduced) 0.0 else (if (reverse) 0.7 else -0.7) * mid + 0.35 * bounce + 0.45 * lean.coerceIn(-1.0, 1.0)),
            "uScale" to doubleArrayOf(if (reduced) 1.0 else (1 - 0.05 * mid) * (1 + 0.08 * bounce.coerceIn(-1.0, 1.0))),
            "uPress" to doubleArrayOf(if (reduced) 0.0 else press.coerceIn(0.0, 1.0)),
            "uWet" to doubleArrayOf(mid * mid),
            "uZero" to doubleArrayOf(zr, 1 - 0.025 * sin(PI * zr), if (zt > 2.6) 1.0 else 0.0, 0.0),
            "uSafe" to doubleArrayOf(safe, sin((st - 0.7) * PI * 2 / 1.025), pop(0.50, 0.82, st, 1.9) * (1 - ease(2.75, 2.93, st)), 1.0),
            "uEnvelope" to doubleArrayOf(opened, heart, grin, if (reduced) 0.0 else PI * 2 * ease(2.90, 3.40, et)),
            "uNote" to doubleArrayOf(pop(0.28, 0.78, et) * (1 - ease(2.40, 2.88, et)), sin(et * 2.5) * 0.025 * opened, sin(et * 3.5) * 0.10, et),
            "uBook" to doubleArrayOf(flop, badge, ease(0.80, 1.0, bt), sin(PI * flop)),
            "uBookTurn" to doubleArrayOf(-0.83 + 0.35 * flop, -0.10 + 0.28 * flop, -0.15 + 0.12 * flop, 0.0),
        )
        for (i in 0..2) {
            val c = coins[i]
            frame["uCoin$i"] = c.copyOf(4)
            frame["uCoinTurn$i"] = doubleArrayOf(cos(c[4]), sin(c[4]), cos(c[5]), sin(c[5]))
        }
        val lo = min(a, b)
        val hi = max(a, b)
        val band = if (lo == 1 && hi == 2) 0.60 else if (hi == 3) 0.55 else 0.46
        val direction = if (reverse) -1.0 else 1.0
        frame["uFront"] = doubleArrayOf(-max(sceneSupport(a, -direction, frame), sceneSupport(b, -direction, frame)) - band,
            max(sceneSupport(a, direction, frame), sceneSupport(b, direction, frame)) + band, band, activity)
        return frame
    }
}
