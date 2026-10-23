package com.ngocdevv.nativecook.blocks.progress.geodefocusreward

import kotlin.math.*

internal const val GEODE_FOCUS_REWARD_LOOP = 8.0
internal data class GeodeFocusRewardVector(val x: Double, val y: Double, val z: Double) {
    operator fun plus(v: GeodeFocusRewardVector) = GeodeFocusRewardVector(x + v.x, y + v.y, z + v.z)
    operator fun minus(v: GeodeFocusRewardVector) = GeodeFocusRewardVector(x - v.x, y - v.y, z - v.z)
    operator fun times(q: Double) = GeodeFocusRewardVector(x * q, y * q, z * q)
    fun dot(v: GeodeFocusRewardVector) = x * v.x + y * v.y + z * v.z
    fun cross(v: GeodeFocusRewardVector) = GeodeFocusRewardVector(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x)
    fun normalized(): GeodeFocusRewardVector { val n = sqrt(dot(this)); return if (n > 0) this * (1 / n) else Zero }
    companion object { val Zero = GeodeFocusRewardVector(0.0, 0.0, 0.0) }
}
internal data class GeodeFocusRewardPiecePose(var center: GeodeFocusRewardVector, var rotation: GeodeFocusRewardVector, var alpha: Double)
internal data class GeodeFocusRewardHeroPose(var p: Double, var globalScale: Double, var globalY: Double, var glowGain: Double,
    var coreYaw: Double, var coreScale: Double, var manualYaw: Double, var crack: Double, var pieces: List<GeodeFocusRewardPiecePose>, var crumbAmount: Double)
internal data class GeodeFocusRewardCopyPose(var restAlpha: Double, var resultAlpha: DoubleArray, var resultY: DoubleArray)
internal data class GeodeFocusRewardFrame(var hero: GeodeFocusRewardHeroPose, var copy: GeodeFocusRewardCopyPose) {
    fun duplicate() = GeodeFocusRewardFrame(hero.copy(pieces = hero.pieces.map { it.copy() }), copy.copy(resultAlpha = copy.resultAlpha.copyOf(), resultY = copy.resultY.copyOf()))
}
internal object GeodeFocusRewardMath {
    fun clamp(x: Double, lo: Double = 0.0, hi: Double = 1.0) = x.coerceIn(lo, hi)
    fun smooth(x: Double): Double { val q = clamp(x); return q * q * (3 - 2 * q) }
    fun segment(x: Double, a: Double, b: Double) = clamp((x - a) / (b - a))
    fun sm(x: Double, a: Double, b: Double) = smooth(segment(x, a, b))
    fun mix(a: Double, b: Double, q: Double) = a + (b - a) * q
    fun mix(a: GeodeFocusRewardVector, b: GeodeFocusRewardVector, q: Double) = a + (b - a) * q
    fun bezier(x: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        if (x <= 0) return 0.0; if (x >= 1) return 1.0
        var lo = 0.0; var hi = 1.0
        repeat(18) { val u = (lo + hi) * .5; val a = 1 - u; val px = 3 * a * a * u * x1 + 3 * a * u * u * x2 + u * u * u; if (px < x) lo = u else hi = u }
        val u = (lo + hi) * .5; val a = 1 - u
        return 3 * a * a * u * y1 + 3 * a * u * u * y2 + u * u * u
    }
    fun e(x: Double) = bezier(x, .2, .8, .2, 1.0)
    fun io(x: Double) = bezier(x, .65, 0.0, .35, 1.0)
    fun spring(from: Double, to: Double, velocity: Double, time: Double, response: Double, damping: Double): Double {
        val w = 2 * PI / response; val a = damping * w; val b = w * sqrt(1 - damping * damping); val d = from - to
        return to + exp(-a * time) * (d * cos(b * time) + (velocity + a * d) * sin(b * time) / b)
    }
    fun decay(age: Double, response: Double, damping: Double) = spring(1.0, 0.0, 0.0, max(0.0, age), response, damping)
    fun rotate(p: GeodeFocusRewardVector, angles: GeodeFocusRewardVector): GeodeFocusRewardVector {
        val y = angles.x * PI / 180; val x = angles.y * PI / 180; val z = angles.z * PI / 180
        val a = GeodeFocusRewardVector(p.x * cos(y) + p.z * sin(y), p.y, -p.x * sin(y) + p.z * cos(y))
        val b = GeodeFocusRewardVector(a.x, a.y * cos(x) - a.z * sin(x), a.y * sin(x) + a.z * cos(x))
        return GeodeFocusRewardVector(b.x * cos(z) - b.y * sin(z), b.x * sin(z) + b.y * cos(z), b.z)
    }
    val rest get() = steady(0.0)
    val reduced get() = steady(1.0).also { it.hero.glowGain = .72; it.hero.crumbAmount = 0.0 }
    private fun steady(progress: Double): GeodeFocusRewardFrame {
        val p = clamp(progress); val alpha = 1 - sm(p, .88, 1.0)
        val pieces = GeodeFocusRewardGeometry.pieces.map { piece ->
            val delay = piece.delay / 1.6; val q = smooth((segment(p, .18, .94) - delay) / (1 - delay))
            val rotation = piece.finalRotation * q; val pivot = piece.pivot - piece.center
            val hinge = pivot - rotate(pivot, rotation); val peel = 1 - sm(q, .60, 1.0); val end = sm(q, .60, 1.0)
            val radial = (GeodeFocusRewardVector(piece.center.x, piece.center.y, 0.0) + GeodeFocusRewardVector(.001, 0.0, 0.0)).normalized()
            val overshoot = 5 * sin(PI * end) * (1 - end)
            GeodeFocusRewardPiecePose(mix(piece.center, piece.finalCenter, q) + hinge * peel + radial * overshoot, rotation, alpha)
        }
        val result = sm(p, .78, .96)
        return GeodeFocusRewardFrame(GeodeFocusRewardHeroPose(p, 1.0, 0.0, .35 + .37 * sm(p, .18, 1.0), -8 + 14 * sm(p, .18, 1.0), .94 + .06 * p, 0.0, sm(p, 0.0, .18), pieces,
            sin(PI * sm(p, .55, .98)) * (1 - sm(p, .88, 1.0))), GeodeFocusRewardCopyPose(1 - sm(p, .12, .25), DoubleArray(4) { result }, DoubleArray(4) { 10 * (1 - result) }))
    }
    fun authored(time: Double): GeodeFocusRewardFrame {
        val t = max(0.0, time) % GEODE_FOCUS_REWARD_LOOP
        val p = when { t < 1.23 -> 0.0; t < 2.60 -> .18 * io(segment(t, 1.23, 2.60)); t < 3.50 -> mix(.18, .65, e(segment(t, 2.60, 3.50))); t < 4.20 -> mix(.65, 1.0, e(segment(t, 3.50, 4.20))); t < 5.50 -> 1.0; else -> 1 - io(segment(t, 5.50, 7.10)) }
        val f = steady(p)
        when {
            t >= 1.10 && t < 1.23 -> { val q = e(segment(t, 1.10, 1.23)); f.hero.globalScale = 1 - .035 * q; f.hero.globalY = 2 * q }
            t >= 1.23 && t < 2.60 -> { val q = sm(t, 1.23, 2.60); f.hero.globalScale = mix(.965, 1.0, q); f.hero.globalY = 2 * (1 - q) }
            t >= 5.50 && t < 7.10 -> { val q = sm(t, 5.50, 7.10); f.hero.globalScale = 1 - .015 * q; f.hero.globalY = q }
            t >= 7.10 && t < 7.30 -> { val u = t - 7.10; val r = decay(u, .25, .82) * (1 - smooth(u / .20)); f.hero.globalScale = 1 - .015 * r; f.hero.globalY = r }
        }
        f.hero.glowGain = when { t < 3.80 -> .35; t < 4.075 -> mix(.35, 1.0, sm(t, 3.80, 4.075)); t < 4.35 -> mix(1.0, .72, sm(t, 4.075, 4.35)); t < 5.50 -> .72; else -> mix(.72, .35, sm(t, 5.50, 7.10)) }
        f.copy.restAlpha = (1 - sm(t, 2.85, 3.25)) + sm(t, 6.75, 7.12)
        repeat(4) { i -> val q = e(segment(t, 4.05 + i * .04, 4.36 + i * .04)) * (1 - sm(t, 5.50, 5.77)); f.copy.resultAlpha[i] = q; f.copy.resultY[i] = 10 * (1 - q) }
        return f
    }
    private fun moved(from: GeodeFocusRewardFrame, progress: Double, residual: Double = 1.0, copyResidual: Double = 1.0): GeodeFocusRewardFrame {
        val old = steady(from.hero.p); val next = steady(progress); val h = next.hero; val a = from.hero; val o = old.hero
        h.globalScale = 1 + (a.globalScale - 1) * residual; h.globalY = a.globalY * residual
        h.glowGain = clamp(h.glowGain + (a.glowGain - o.glowGain) * residual, .35, 1.0)
        h.coreYaw += (a.coreYaw - o.coreYaw) * residual; h.coreScale += (a.coreScale - o.coreScale) * residual; h.manualYaw = a.manualYaw
        h.crack = clamp(h.crack + (a.crack - o.crack) * residual); h.crumbAmount = clamp(h.crumbAmount + (a.crumbAmount - o.crumbAmount) * residual)
        repeat(12) { i -> h.pieces[i].center += (a.pieces[i].center - o.pieces[i].center) * residual; h.pieces[i].rotation += (a.pieces[i].rotation - o.pieces[i].rotation) * residual; h.pieces[i].alpha = clamp(h.pieces[i].alpha + (a.pieces[i].alpha - o.pieces[i].alpha) * residual) }
        // Preserve the captured copy at Down, then hand its residual to the destination.
        val distanceToEdge = if (progress >= a.p) 1 - a.p else a.p
        val copyTravel = if (distanceToEdge > 0) abs(progress - a.p) / min(.18, distanceToEdge) else 0.0
        val copyWeight = residual * copyResidual * (1 - smooth(copyTravel))
        next.copy.restAlpha = clamp(next.copy.restAlpha + (from.copy.restAlpha - old.copy.restAlpha) * copyWeight)
        repeat(4) { i -> next.copy.resultAlpha[i] = clamp(next.copy.resultAlpha[i] + (from.copy.resultAlpha[i] - old.copy.resultAlpha[i]) * copyWeight); next.copy.resultY[i] += (from.copy.resultY[i] - old.copy.resultY[i]) * copyWeight }
        return next
    }
    fun held(from: GeodeFocusRewardFrame, elapsed: Double, dx: Double, dy: Double, yawDeflection: Double): GeodeFocusRewardFrame {
        if (elapsed <= .12 && dx == 0.0 && dy == 0.0 && yawDeflection == 0.0) return from
        val p = clamp(from.hero.p + .62 * max(0.0, elapsed - .12) - dy / 210); val f = moved(from, p, copyResidual = 1 - sm(elapsed, .12, .43))
        val base = clamp(from.hero.manualYaw + .09 * dx, -12.0, 12.0)
        val capturedTransient = from.hero.manualYaw - clamp(from.hero.manualYaw, -12.0, 12.0)
        f.hero.manualYaw = clamp(base + capturedTransient * decay(max(0.0, elapsed - .12), .4, .8) + yawDeflection, -17.0, 17.0)
        return f
    }
    fun released(from: GeodeFocusRewardFrame, opens: Boolean, velocity: Double, elapsed: Double, cancelled: Boolean, yawTarget: Double? = null): GeodeFocusRewardFrame {
        val age = max(0.0, elapsed); if (age == 0.0) return from
        val target = if (opens && !cancelled) 1.0 else 0.0; val response = if (target == 1.0) .45 else .38; val damping = if (target == 1.0) .78 else .85
        val p = clamp(spring(from.hero.p, target, if (cancelled) 0.0 else clamp(velocity, -1.6, 1.6), age, response, damping))
        val f = moved(from, p, decay(age, response, damping))
        if (cancelled) f.hero.manualYaw = from.hero.manualYaw * (1 - sm(age, 0.0, .24))
        else if (yawTarget != null) f.hero.manualYaw = yawTarget + (from.hero.manualYaw - yawTarget) * decay(age, .4, .8)
        return f
    }
    fun replay(from: GeodeFocusRewardFrame, elapsed: Double) = steady(e(segment(max(0.0, elapsed), 0.0, 1.25))).also { it.hero.manualYaw = from.hero.manualYaw }
    fun recovering(from: GeodeFocusRewardFrame, elapsed: Double): GeodeFocusRewardFrame {
        if (elapsed <= 0) return from; if (elapsed >= .65) return rest
        val q = io(elapsed / .65); val r = rest; val f = from.duplicate(); val h = f.hero; val a = from.hero
        h.p = mix(a.p, 0.0, q); h.globalScale = mix(a.globalScale, 1.0, q); h.globalY = a.globalY * (1 - q)
        h.glowGain = mix(a.glowGain, .35, q); h.coreYaw = mix(a.coreYaw, -8.0, q); h.coreScale = mix(a.coreScale, .94, q)
        h.manualYaw = a.manualYaw * (1 - q); h.crack = a.crack * (1 - q); h.crumbAmount = a.crumbAmount * (1 - q)
        repeat(12) { i -> h.pieces[i].center = mix(a.pieces[i].center, r.hero.pieces[i].center, q); h.pieces[i].rotation = mix(a.pieces[i].rotation, GeodeFocusRewardVector.Zero, q); h.pieces[i].alpha = mix(a.pieces[i].alpha, 1.0, q) }
        f.copy.restAlpha = mix(from.copy.restAlpha, 1.0, q)
        repeat(4) { i -> f.copy.resultAlpha[i] = from.copy.resultAlpha[i] * (1 - q); f.copy.resultY[i] = mix(from.copy.resultY[i], 10.0, q) }
        return f
    }
}

internal class GeodeFocusRewardYaw {
    private var position = 0.0; private var velocity = 0.0; private var target = 0.0; private var stamp: Double? = null
    fun follow(target: Double, now: Double) { val current = sample(now); position = current.first; velocity = current.second; this.target = target; stamp = now }
    fun sample(now: Double): Pair<Double, Double> {
        val start = stamp ?: return 0.0 to 0.0; val age = max(0.0, now - start)
        if (age <= .05) return spring(position, velocity, target, age)
        val released = spring(position, velocity, target, .05); return spring(released.first, released.second, 0.0, age - .05)
    }
    private fun spring(from: Double, speed: Double, to: Double, time: Double): Pair<Double, Double> {
        if (time <= 0) return from to speed
        val omega = 2 * PI / .4; val damping = .8 * omega; val frequency = omega * sqrt(1 - .8 * .8)
        val displacement = from - to; val sine = (speed + damping * displacement) / frequency
        val c = cos(frequency * time); val s = sin(frequency * time); val envelope = exp(-damping * time); val offset = displacement * c + sine * s
        return to + envelope * offset to envelope * ((-displacement * frequency * s + sine * frequency * c) - damping * offset)
    }
}
