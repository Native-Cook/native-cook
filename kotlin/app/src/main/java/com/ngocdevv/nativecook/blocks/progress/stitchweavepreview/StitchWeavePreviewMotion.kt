package com.ngocdevv.nativecook.blocks.progress.stitchweavepreview

import kotlin.math.*

internal const val STITCH_WEAVE_PREVIEW_LOOP = 8.0

internal data class StitchWeavePreviewVector(val x: Double, val y: Double, val z: Double) {
    operator fun plus(b: StitchWeavePreviewVector) = StitchWeavePreviewVector(x + b.x, y + b.y, z + b.z)
    operator fun minus(b: StitchWeavePreviewVector) = StitchWeavePreviewVector(x - b.x, y - b.y, z - b.z)
    operator fun times(s: Double) = StitchWeavePreviewVector(x * s, y * s, z * s)
}

internal data class StitchWeavePreviewPose(
    val p: Double,
    val rotation: StitchWeavePreviewVector,
    val lagRotation: StitchWeavePreviewVector,
    val lift: Double,
    val hem: Double = 0.0,
    val anticipation: Double = 0.0,
    val light: Double = 0.0,
    val lightPosition: Double = 8.3,
    val crownElapsed: Double = 0.0,
    val topologySpeed: Double = 1.0,
) {
    fun moved(progress: Double): StitchWeavePreviewPose {
        val before = StitchWeavePreviewMath.base(p)
        val after = StitchWeavePreviewMath.base(progress)
        return after.copy(rotation = after.rotation + rotation - before.rotation,
            lagRotation = after.lagRotation + lagRotation - before.lagRotation,
            lift = after.lift + lift - before.lift, hem = hem, anticipation = anticipation,
            light = light, lightPosition = lightPosition, crownElapsed = crownElapsed, topologySpeed = topologySpeed)
    }
    companion object {
        val Rest = StitchWeavePreviewPose(0.0, StitchWeavePreviewVector(30.0, -10.0, -7.0), StitchWeavePreviewVector(30.0, -10.0, -7.0), 0.0)
        val Finished = StitchWeavePreviewPose(1.0, StitchWeavePreviewVector(8.0, 6.0, -3.0), StitchWeavePreviewVector(8.0, 6.0, -3.0), 0.0, crownElapsed = 10.0)
    }
}

internal data class StitchWeavePreviewAction(val stamp: Double, val from: StitchWeavePreviewPose,
    val target: Double, val velocity: Double, val kick: Double, val saving: Boolean) {
    fun pose(now: Double): StitchWeavePreviewPose {
        val age = max(0.0, now - stamp)
        val m = StitchWeavePreviewMath
        if (age >= 2.65) return StitchWeavePreviewPose.Rest
        if (age >= 2.0) return m.base(target * (1 - m.io((age - 2) / .65)))
        val p = if (saving) from.p + (target - from.p) * m.entrance(age / .48)
            else m.spring(from.p, target, velocity, age, .36, .82)
        val result = m.base(m.clamp(p)); val base = m.base(from.p)
        val decay = m.decay(age, .44, .7)
        return result.copy(rotation = result.rotation + (from.rotation - base.rotation) * decay,
            lagRotation = result.lagRotation + (from.lagRotation - base.lagRotation) * decay,
            lift = result.lift + (from.lift - base.lift) * decay + kick * exp(-10 * age) * sin(16 * age),
            hem = from.hem * m.decay(age, .25, .82), anticipation = from.anticipation * exp(-14 * age),
            light = from.light * exp(-8 * age), lightPosition = from.lightPosition,
            crownElapsed = from.crownElapsed + age, topologySpeed = from.topologySpeed)
    }
}

internal object StitchWeavePreviewMath {
    val yarns = intArrayOf(0xDEDCD3, 0x889A8C, 0x7D8C92)
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun smooth(x: Double): Double { val u = clamp(x); return u * u * (3 - 2 * u) }
    fun hash(i: Int): Double { val n = sin(i * 12.9898 + 4.1414) * 43758.5453; return n - floor(n) }
    fun bezier(x: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        if (x <= 0) return 0.0; if (x >= 1) return 1.0
        var lo = 0.0; var hi = 1.0
        repeat(18) { val u = (lo + hi) / 2; val a = 1 - u
            val v = 3 * a * a * u * x1 + 3 * a * u * u * x2 + u * u * u
            if (v < x) lo = u else hi = u }
        val u = (lo + hi) / 2; val a = 1 - u
        return 3 * a * a * u * y1 + 3 * a * u * u * y2 + u * u * u
    }
    fun io(x: Double) = bezier(x, .65, 0.0, .35, 1.0)
    fun entrance(x: Double) = bezier(x, .2, .8, .2, 1.0)
    fun progress(t: Double): Double = when { t < 1.23 -> 0.0; t < 3.95 -> io((t - 1.23) / 2.72); t < 5.2 -> 1.0; else -> 1 - io((t - 5.2) / 1.9) }
    fun orientation(t: Double): StitchWeavePreviewVector {
        val q = if (t < 5.2) io((t - 1.23) / 2.82) else 1 - io((t - 5.2) / 1.9)
        return StitchWeavePreviewVector(30 - 22 * q, -10 + 16 * q, -7 + 4 * q)
    }
    fun base(progress: Double): StitchWeavePreviewPose {
        val p = clamp(progress); val r = StitchWeavePreviewVector(30 - 22 * p, -10 + 16 * p, -7 + 4 * p)
        return StitchWeavePreviewPose(p, r, r, 18 * sin(PI * p))
    }
    fun authored(time: Double): StitchWeavePreviewPose {
        val t = max(0.0, time) % STITCH_WEAVE_PREVIEW_LOOP
        val b = base(progress(t)); var lift = 0.0; var anticipation = 0.0; var hem = 0.0; var light = 0.0; var position = 8.3
        if (t >= 1.23 && t < 3.95) lift = 18 * entrance((t - 1.23) / .32)
        if (t >= 3.95 && t < 4.45) lift = 18 * decay(t - 3.95, .45, .76) * (1 - smooth((t - 4.30) / .15))
        if (t >= 5.2 && t < 7.1) lift = 18 * sin(PI * b.p)
        if (t >= 1.1 && t < 1.23) { anticipation = sin(PI * smooth((t - 1.1) / .13)); hem = -4 * anticipation }
        if (t >= 6.85 && t < 7.1) hem = 2 * io((t - 6.85) / .25)
        if (t >= 7.1 && t < 7.3) hem = 2 * decay(t - 7.1, .25, .82) * (1 - smooth((t - 7.2) / .1))
        if (t >= 4.15 && t < 4.9) { val q = (t - 4.15) / .75; light = .14 * sin(PI * q); position = 8.3 + 376.4 * smooth(q) }
        return b.copy(rotation = orientation(t), lagRotation = orientation(max(0.0, t - .07)), lift = lift,
            hem = hem, anticipation = anticipation, light = light, lightPosition = position,
            crownElapsed = if (t >= 3.95 && t < 5.2) t - 3.95 else 0.0, topologySpeed = if (t < 5.2) 1.9 / 2.72 else 1.0)
    }
    fun canonicalTime(p: Double): Double {
        if (p <= 0) return 0.0; if (p >= 1) return 1.9
        var lo = 0.0; var hi = 1.0
        repeat(18) { val u = (lo + hi) / 2; if (u * u * (3 - 2 * u) < p) lo = u else hi = u }
        val u = (lo + hi) / 2; val a = 1 - u
        return 1.9 * (3 * a * a * u * .65 + 3 * a * u * u * .35 + u * u * u)
    }
    fun delay(column: Int) = .0025 * (column % 4) + .004 * (2 * hash(column) - 1)
    fun columnRows(p: Double, canonical: Double, column: Int) = when { p <= 0 -> 3.0; p >= 1 -> 48.0; else -> 3 + 45 * io((canonical - delay(column)) / 1.9) }
    fun spring(from: Double, to: Double, velocity: Double, time: Double, response: Double, damping: Double): Double {
        val w = 2 * PI / response; val a = damping * w; val b = w * sqrt(1 - damping * damping); val delta = from - to
        return to + exp(-a * time) * (delta * cos(b * time) + (velocity + a * delta) / b * sin(b * time))
    }
    fun decay(time: Double, response: Double, damping: Double) = spring(1.0, 0.0, 0.0, time, response, damping)
    fun solidMaterial(pattern: Int, yarn: Int) = FloatArray(48 * 6).also { a -> repeat(48) { row -> a[row * 6 + pattern] = 1f; a[row * 6 + 3 + yarn] = 1f } }
    fun materialMix(row: Int, age: Double, rows: Double, reduced: Boolean): Double {
        if (age < 0) return 0.0; if (reduced) return smooth(age / .18)
        val lag = clamp((rows - 1 - row) / max(1.0, rows - 1)) * .22
        return smooth((age - lag) / .16)
    }
    fun rotationMatrix(d: StitchWeavePreviewVector): DoubleArray {
        val x = d.x * PI / 180; val y = d.y * PI / 180; val z = d.z * PI / 180
        val cx = cos(x); val sx = sin(x); val cy = cos(y); val sy = sin(y); val cz = cos(z); val sz = sin(z)
        return doubleArrayOf(cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx,
            sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx, -sy, cy * sx, cy * cx)
    }
}
