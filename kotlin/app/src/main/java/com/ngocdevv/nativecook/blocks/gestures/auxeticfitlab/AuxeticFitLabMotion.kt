package com.ngocdevv.nativecook.blocks.gestures.auxeticfitlab

import kotlin.math.*

internal const val AUXETIC_FIT_LAB_LOOP = 8.0
internal enum class AuxeticFitLabSide { Left, Right }
internal enum class AuxeticFitLabDetent(val angle: Double) { Closed(4.0), Middle(23.0), Open(42.0) }
internal data class AuxeticFitLabPoint(val x: Double, val y: Double)
internal data class AuxeticFitLabRect(val x: Double, val y: Double, val width: Double, val height: Double) {
    fun contains(px: Double, py: Double) = px >= x && px <= x + width && py >= y && py <= y + height
}
internal data class AuxeticFitLabHousing(val offsetX: Double = 0.0, val offsetVelocityX: Double = 0.0,
    val scale: Double = 1.0, val scaleVelocity: Double = 0.0)
internal data class AuxeticFitLabFrame(val angle: Double, val angularVelocity: Double = 0.0,
    val left: AuxeticFitLabHousing = AuxeticFitLabHousing(), val right: AuxeticFitLabHousing = AuxeticFitLabHousing(),
    val arrowGain: Double = 1.0, val arrowGainVelocity: Double = 0.0) {
    val width get() = AuxeticFitLabMath.width(angle)
    val widthVelocity get() = AuxeticFitLabMath.jacobian(angle) * angularVelocity
    fun frozen() = copy(angularVelocity = 0.0, left = left.copy(offsetVelocityX = 0.0, scaleVelocity = 0.0),
        right = right.copy(offsetVelocityX = 0.0, scaleVelocity = 0.0), arrowGainVelocity = 0.0)
}
internal data class AuxeticFitLabMetrics(val extensionPercent: Double, val openAreaPercent: Double)
internal data class AuxeticFitLabInputRates(var leftOffset: Double = 0.0, var rightOffset: Double = 0.0,
    var leftScale: Double = 0.0, var rightScale: Double = 0.0, var arrows: Double = 0.0)
internal data class AuxeticFitLabInput(val frame: AuxeticFitLabFrame, val requestedWidth: Double,
    val requestedWidthVelocity: Double = 0.0, val actualAngularVelocity: Double? = null,
    val rates: AuxeticFitLabInputRates = AuxeticFitLabInputRates())
internal sealed interface AuxeticFitLabReleaseVelocity {
    data class Inherited(val frame: AuxeticFitLabFrame, val heldElapsed: Double) : AuxeticFitLabReleaseVelocity
    data class User(val width: Double, val angular: Double?, val sampleAge: Double) : AuxeticFitLabReleaseVelocity
}
internal data class AuxeticFitLabRelease(val target: AuxeticFitLabDetent?, val acceptedAngularVelocity: Double,
    val from: AuxeticFitLabFrame, val angle: AuxeticFitLabSpring, val leftOffset: AuxeticFitLabSpring,
    val rightOffset: AuxeticFitLabSpring, val leftScale: AuxeticFitLabSpring, val rightScale: AuxeticFitLabSpring,
    val arrows: AuxeticFitLabSpring)
internal data class AuxeticFitLabHandles(val leftCenter: AuxeticFitLabPoint, val rightCenter: AuxeticFitLabPoint,
    val leftHit: AuxeticFitLabRect, val rightHit: AuxeticFitLabRect, val pinchHit: AuxeticFitLabRect)
internal data class AuxeticFitLabSample(val value: Double, val velocity: Double)

/** Coefficients are captured once; drawing samples an analytic, finite response. */
internal data class AuxeticFitLabSpring(val start: Double, val target: Double, val velocity: Double, val critical: Boolean) {
    fun raw(time: Double): AuxeticFitLabSample {
        val t = max(0.0, time); val d = start - target
        if (critical) {
            val c = velocity + omega * d; val e = exp(-omega * t)
            return AuxeticFitLabSample(target + e * (d + c * t), e * (velocity - omega * c * t))
        }
        val b = (velocity + alpha * d) / beta
        val e = exp(-alpha * t); val c = cos(beta * t); val s = sin(beta * t)
        return AuxeticFitLabSample(target + e * (d * c + b * s), e * (velocity * c + (-alpha * b - beta * d) * s))
    }
    fun sample(time: Double): AuxeticFitLabSample {
        if (time <= 0) return AuxeticFitLabSample(start, velocity)
        if (time >= 1.20) return AuxeticFitLabSample(target, 0.0)
        val s = raw(time)
        if (time <= .80) return s
        val u = (time - .80) / .40; val envelope = 1 - u * u * (3 - 2 * u)
        val derivative = -6 * u * (1 - u) / .40
        return AuxeticFitLabSample(target + envelope * (s.value - target), envelope * s.velocity + derivative * (s.value - target))
    }
    fun feasible(lower: Double, upper: Double): Boolean {
        val d = start - target; val b = (velocity + alpha * d) / beta
        val c = -alpha * b - beta * d; val phase = atan2(-velocity, c)
        val times = mutableListOf(0.0, 1.20)
        for (k in -2..8) {
            val t = (phase + k * PI) / beta
            if (t > 0 && t < 1.20) times.add(t)
        }
        return times.all { val v = raw(it).value; v >= lower - 1e-12 && v <= upper + 1e-12 }
    }
    companion object {
        private val omega = 2 * PI / .40
        private val alpha = .8 * omega
        private val beta = .6 * omega
        fun bounded(start: Double, target: Double, velocity: Double, lower: Double, upper: Double): AuxeticFitLabSpring {
            val candidate = AuxeticFitLabSpring(start, target, velocity, false)
            if (candidate.feasible(lower, upper)) return candidate
            var lo = 0.0; var hi = 1.0
            repeat(40) {
                val mid = (lo + hi) / 2
                if (AuxeticFitLabSpring(start, target, velocity * mid, false).feasible(lower, upper)) lo = mid else hi = mid
            }
            return AuxeticFitLabSpring(start, target, velocity * lo, false)
        }
    }
}

internal object AuxeticFitLabMath {
    const val LOOP = AUXETIC_FIT_LAB_LOOP
    const val edge = 49.5
    private const val radians = PI / 180
    val rest get() = staticFrame(4.0)
    val reduced get() = staticFrame(42.0)
    fun width(angle: Double): Double { val a = angle * radians; return 4 * edge * (cos(a) + sin(a)) }
    fun jacobian(angle: Double): Double { val a = angle * radians; return 4 * edge * (cos(a) - sin(a)) * radians }
    fun angleForWidth(value: Double): Double {
        val lower = width(4.0); val upper = width(44.0)
        if (value <= lower) return 4.0
        if (value >= upper) return 44.0
        return (PI / 4 - acos(clamp(value / (4 * edge * sqrt(2.0)), -1.0, 1.0))) / radians
    }
    fun metrics(angle: Double): AuxeticFitLabMetrics {
        val d = width(angle) / 4; val d0 = width(4.0) / 4
        return AuxeticFitLabMetrics(100 * (d / d0 - 1), 100 * (1 - edge * edge / (d * d)))
    }
    fun handles(frame: AuxeticFitLabFrame): AuxeticFitLabHandles {
        val d = frame.width / 4
        val left = AuxeticFitLabPoint(196.5 - (2 * d + 20) + frame.left.offsetX, 399.0)
        val right = AuxeticFitLabPoint(196.5 + (2 * d + 20) + frame.right.offsetX, 399.0)
        return AuxeticFitLabHandles(left, right,
            AuxeticFitLabRect(clamp(left.x - 22, 14.0, 335.0), 369.0, 44.0, 60.0),
            AuxeticFitLabRect(clamp(right.x - 22, 14.0, 335.0), 369.0, 44.0, 60.0),
            AuxeticFitLabRect(196.5 - 2 * d - 12, 399 - 1.5 * d - 12, 4 * d + 24, 3 * d + 24))
    }
    fun staticFrame(angle: Double) = AuxeticFitLabFrame(clamp(angle, 4.0, 42.0))
    fun authored(time: Double): AuxeticFitLabFrame {
        val t = max(0.0, time) % LOOP
        var frame = rest
        when {
            t >= 1.24 && t < 3.90 -> {
                val q = curve((t - 1.24) / 2.66, .65, 0.0, .35, 1.0)
                frame = frame.copy(angle = 4 + 38 * q.value, angularVelocity = 38 * q.velocity / 2.66)
            }
            t >= 3.90 && t < 4.25 -> {
                val u = (t - 3.90) / .35; val k = 2 * PI * .82
                val peak = atan(1 / .82) / PI
                val norm = sin(PI * peak).pow(2) * exp(-k * peak)
                val s = sin(PI * u); val e = exp(-k * u)
                frame = frame.copy(angle = 42 + s * s * e / norm,
                    angularVelocity = e * (PI * sin(2 * PI * u) - k * s * s) / (norm * .35))
            }
            t >= 4.25 && t < 5.15 -> frame = frame.copy(angle = 42.0)
            t >= 5.15 && t < 7.10 -> {
                val q = curve((t - 5.15) / 1.95, .65, 0.0, .35, 1.0)
                frame = frame.copy(angle = 42 - 38 * q.value, angularVelocity = -38 * q.velocity / 1.95)
            }
        }
        var offset = 0.0; var offsetVelocity = 0.0; var scale = 1.0; var scaleVelocity = 0.0
        when {
            t >= 1.10 && t < 1.24 -> {
                val q = curve((t - 1.10) / .14, .2, .8, .2, 1.0)
                offset = 2 * q.value; offsetVelocity = 2 * q.velocity / .14
                scale = 1 - .06 * q.value; scaleVelocity = -.06 * q.velocity / .14
            }
            t >= 1.24 && t < 1.46 -> {
                val q = smooth((t - 1.24) / .22)
                offset = 2 * (1 - q.value); offsetVelocity = -2 * q.velocity / .22
                scale = .94 + .06 * q.value; scaleVelocity = .06 * q.velocity / .22
            }
            t >= 7 && t < 7.10 -> {
                val q = smooth((t - 7) / .10)
                offset = q.value; offsetVelocity = q.velocity / .10
                scale = 1 - .02 * q.value; scaleVelocity = -.02 * q.velocity / .10
            }
            t >= 7.10 && t < 7.28 -> {
                val q = curve((t - 7.10) / .18, .2, .8, .2, 1.0)
                offset = 1 - q.value; offsetVelocity = -q.velocity / .18
                scale = .98 + .02 * q.value; scaleVelocity = .02 * q.velocity / .18
            }
        }
        val a = smooth((t - 1.24) / .22); val b = smooth((t - 3.90) / .35)
        return frame.copy(left = AuxeticFitLabHousing(offset, offsetVelocity, scale, scaleVelocity),
            right = AuxeticFitLabHousing(-offset, -offsetVelocity, scale, scaleVelocity),
            arrowGain = 1 + .12 * a.value * (1 - b.value),
            arrowGainVelocity = .12 * (a.velocity / .22 * (1 - b.value) - a.value * b.velocity / .35))
    }
    fun begin(frame: AuxeticFitLabFrame) = AuxeticFitLabInput(frame.frozen(), frame.width)
    fun drag(capture: AuxeticFitLabFrame, side: AuxeticFitLabSide, translationX: Double, velocityX: Double): AuxeticFitLabInput {
        val sign = if (side == AuxeticFitLabSide.Right) 1.0 else -1.0
        return input(capture, capture.width + sign * 2 * translationX, sign * 2 * velocityX,
            side == AuxeticFitLabSide.Left, side == AuxeticFitLabSide.Right)
    }
    fun pinch(capture: AuxeticFitLabFrame, scale: Double, scaleVelocity: Double) =
        input(capture, capture.width * scale, capture.width * scaleVelocity, true, true)
    private fun input(capture: AuxeticFitLabFrame, requested: Double, velocity: Double, leftRubber: Boolean, rightRubber: Boolean): AuxeticFitLabInput {
        val lower = width(4.0); val upper = width(44.0); val physical = clamp(requested, lower, upper)
        val angle = if (requested == capture.width) capture.angle else angleForWidth(physical)
        val widthIsMoving = requested >= lower && requested <= upper && !(requested == lower && velocity < 0) && !(requested == upper && velocity > 0)
        val physicalVelocity = if (widthIsMoving) velocity else 0.0
        val delta = requested - capture.width; val intent = abs(delta) / 2
        val q = smooth(intent / 12); val qWidth = q.velocity * sign(delta) / 24
        val physicalWidthSlope = if (widthIsMoving) 1.0 else 0.0
        val excess = (requested - physical) / 2; val excessWidth = (1 - physicalWidthSlope) / 2
        val rates = AuxeticFitLabInputRates()
        fun housing(from: AuxeticFitLabHousing, direction: Double, active: Boolean): AuxeticFitLabHousing {
            val residual = from.offsetX * (1 - q.value); val residualWidth = -from.offsetX * qWidth
            val over = if (active) direction * excess else 0.0; val overWidth = if (active) direction * excessWidth else 0.0
            val rubber = .45 * over / (1 + .45 * abs(over) / 16)
            val rubberWidth = .45 * overWidth / (1 + .45 * abs(over) / 16).pow(2)
            val room = 1 - abs(residual) / 16; val roomWidth = -sign(residual) * residualWidth / 16
            val slope = residualWidth + rubberWidth * room + rubber * roomWidth
            val scaleSlope = -(from.scale - 1) * qWidth
            if (direction < 0) { rates.leftOffset = slope; rates.leftScale = scaleSlope }
            else { rates.rightOffset = slope; rates.rightScale = scaleSlope }
            return AuxeticFitLabHousing(residual + rubber * room, slope * velocity,
                1 + (from.scale - 1) * (1 - q.value), scaleSlope * velocity)
        }
        val left = housing(capture.left, -1.0, leftRubber); val right = housing(capture.right, 1.0, rightRubber)
        rates.arrows = (1.12 - capture.arrowGain) * qWidth
        val angular = physicalVelocity / jacobian(angle)
        val frame = AuxeticFitLabFrame(angle, angular, left, right, capture.arrowGain + (1.12 - capture.arrowGain) * q.value, rates.arrows * velocity)
        return AuxeticFitLabInput(frame, requested, velocity, angular, rates)
    }
    fun nearestDetent(angle: Double, velocity: Double, previous: AuxeticFitLabDetent): AuxeticFitLabDetent {
        val detents = AuxeticFitLabDetent.entries
        val distance = detents.minOf { abs(it.angle - angle) }
        val ties = detents.filter { abs(abs(it.angle - angle) - distance) <= 1e-10 }
        if (velocity > 0) return ties.lastOrNull() ?: AuxeticFitLabDetent.Closed
        if (velocity < 0) return ties.firstOrNull() ?: AuxeticFitLabDetent.Closed
        return if (ties.contains(previous)) previous else ties.firstOrNull() ?: AuxeticFitLabDetent.Closed
    }
    fun makeRelease(input: AuxeticFitLabInput, velocity: AuxeticFitLabReleaseVelocity, previous: AuxeticFitLabDetent, cancelled: Boolean): AuxeticFitLabRelease {
        var from = input.frame; var widthVelocity: Double; var angularVelocity: Double
        when (velocity) {
            is AuxeticFitLabReleaseVelocity.Inherited -> {
                val a = 1 - smooth(velocity.heldElapsed / .12).value; val capture = velocity.frame
                angularVelocity = capture.angularVelocity * a; widthVelocity = jacobian(from.angle) * angularVelocity
                from = from.copy(left = from.left.copy(offsetVelocityX = capture.left.offsetVelocityX * a, scaleVelocity = capture.left.scaleVelocity * a),
                    right = from.right.copy(offsetVelocityX = capture.right.offsetVelocityX * a, scaleVelocity = capture.right.scaleVelocity * a), arrowGainVelocity = capture.arrowGainVelocity * a)
            }
            is AuxeticFitLabReleaseVelocity.User -> {
                val a = 1 - smooth(velocity.sampleAge / .12).value
                widthVelocity = clamp(velocity.width * a, -600.0, 600.0)
                angularVelocity = (velocity.angular ?: (velocity.width / jacobian(from.angle))) * a
                from = from.copy(left = from.left.copy(offsetVelocityX = input.rates.leftOffset * widthVelocity, scaleVelocity = input.rates.leftScale * widthVelocity),
                    right = from.right.copy(offsetVelocityX = input.rates.rightOffset * widthVelocity, scaleVelocity = input.rates.rightScale * widthVelocity), arrowGainVelocity = input.rates.arrows * widthVelocity)
            }
        }
        widthVelocity = clamp(widthVelocity, -600.0, 600.0); angularVelocity = clamp(angularVelocity, -50.0, 50.0)
        if ((from.angle <= 4 && angularVelocity < 0) || (from.angle >= 44 && angularVelocity > 0)) angularVelocity = 0.0
        if (cancelled) return releasePlan(from.copy(angularVelocity = 0.0), null, AuxeticFitLabSpring(from.angle, from.angle, 0.0, true))
        val predicted = angleForWidth(from.width + .12 * widthVelocity)
        val target = nearestDetent(predicted, widthVelocity, previous)
        val spring = if (target == AuxeticFitLabDetent.Closed) {
            val omega = 2 * PI / .40
            val v = clamp(angularVelocity, max(-50.0, -omega * (from.angle - 4)), min(50.0, omega * (44 - from.angle)))
            AuxeticFitLabSpring(from.angle, 4.0, v, true)
        } else AuxeticFitLabSpring.bounded(from.angle, target.angle, angularVelocity, 4.0, 44.0)
        from = from.copy(angularVelocity = spring.velocity)
        return releasePlan(from, target, spring)
    }
    private fun releasePlan(from: AuxeticFitLabFrame, target: AuxeticFitLabDetent?, angle: AuxeticFitLabSpring): AuxeticFitLabRelease {
        val leftOffset = AuxeticFitLabSpring.bounded(from.left.offsetX, 0.0, from.left.offsetVelocityX, -16.0, 16.0)
        val rightOffset = AuxeticFitLabSpring.bounded(from.right.offsetX, 0.0, from.right.offsetVelocityX, -16.0, 16.0)
        val leftScale = AuxeticFitLabSpring(from.left.scale, 1.0, from.left.scaleVelocity, false)
        val rightScale = AuxeticFitLabSpring(from.right.scale, 1.0, from.right.scaleVelocity, false)
        val omega = 2 * PI / .40
        val gainVelocity = clamp(from.arrowGainVelocity, -omega * (from.arrowGain - 1), omega * (1.12 - from.arrowGain))
        val arrows = AuxeticFitLabSpring(from.arrowGain, 1.0, gainVelocity, true)
        return AuxeticFitLabRelease(target, angle.velocity, from, angle, leftOffset, rightOffset, leftScale, rightScale, arrows)
    }
    fun released(release: AuxeticFitLabRelease, elapsed: Double): AuxeticFitLabFrame {
        if (release.target == null) {
            val q = smooth(elapsed / .22); val f = release.from
            return f.copy(angularVelocity = 0.0, left = relaxed(f.left, q.value, q.velocity / .22),
                right = relaxed(f.right, q.value, q.velocity / .22), arrowGain = f.arrowGain + (1 - f.arrowGain) * q.value,
                arrowGainVelocity = (1 - f.arrowGain) * q.velocity / .22)
        }
        val a = release.angle.sample(elapsed); val lo = release.leftOffset.sample(elapsed); val ro = release.rightOffset.sample(elapsed)
        val ls = release.leftScale.sample(elapsed); val rs = release.rightScale.sample(elapsed); val gain = release.arrows.sample(elapsed)
        return AuxeticFitLabFrame(a.value, a.velocity, AuxeticFitLabHousing(lo.value, lo.velocity, ls.value, ls.velocity),
            AuxeticFitLabHousing(ro.value, ro.velocity, rs.value, rs.velocity), gain.value, gain.velocity)
    }
    fun recovering(frame: AuxeticFitLabFrame, elapsed: Double): AuxeticFitLabFrame {
        if (elapsed <= 0) return frame
        if (elapsed >= .60) return rest
        val q = curve(elapsed / .60, .65, 0.0, .35, 1.0); val rate = q.velocity / .60
        return AuxeticFitLabFrame(frame.angle + (4 - frame.angle) * q.value, (4 - frame.angle) * rate,
            relaxed(frame.left, q.value, rate), relaxed(frame.right, q.value, rate),
            frame.arrowGain + (1 - frame.arrowGain) * q.value, (1 - frame.arrowGain) * rate)
    }
    private fun relaxed(from: AuxeticFitLabHousing, q: Double, rate: Double) = AuxeticFitLabHousing(
        from.offsetX * (1 - q), -from.offsetX * rate, from.scale + (1 - from.scale) * q, (1 - from.scale) * rate)
    fun clamp(value: Double, lower: Double, upper: Double) = min(upper, max(lower, value))
    private fun sign(value: Double) = if (value == 0.0) 0.0 else if (value > 0) 1.0 else -1.0
    private fun smooth(value: Double): AuxeticFitLabSample {
        val u = clamp(value, 0.0, 1.0)
        return AuxeticFitLabSample(u * u * (3 - 2 * u), if (value <= 0 || value >= 1) 0.0 else 6 * u * (1 - u))
    }
    private fun curve(value: Double, x1: Double, y1: Double, x2: Double, y2: Double): AuxeticFitLabSample {
        val x = clamp(value, 0.0, 1.0)
        fun coordinate(u: Double, a: Double, b: Double): Double { val v = 1 - u; return 3 * v * v * u * a + 3 * v * u * u * b + u * u * u }
        fun derivative(u: Double, a: Double, b: Double) = 3 * (1 - u) * (1 - u) * a + 6 * (1 - u) * u * (b - a) + 3 * u * u * (1 - b)
        if (x == 0.0) return AuxeticFitLabSample(0.0, y1 / x1)
        if (x == 1.0) return AuxeticFitLabSample(1.0, (1 - y2) / (1 - x2))
        var lo = 0.0; var hi = 1.0
        repeat(30) { val mid = (lo + hi) / 2; if (coordinate(mid, x1, x2) < x) lo = mid else hi = mid }
        val u = (lo + hi) / 2
        return AuxeticFitLabSample(coordinate(u, y1, y2), derivative(u, y1, y2) / derivative(u, x1, x2))
    }
}

/** Exact seventeen shared and fourteen boundary hinge identities. */
internal class AuxeticFitLabGeometry(angle: Double) {
    private val cosine = cos(angle * PI / 180)
    private val sine = sin(angle * PI / 180)
    val spacing = AuxeticFitLabMath.edge * (cosine + sine)
    val bounds = AuxeticFitLabRect(196.5 - 2 * spacing, 399 - 1.5 * spacing, 4 * spacing, 3 * spacing)
    fun center(column: Int, row: Int) = AuxeticFitLabPoint(196.5 + (column - 1.5) * spacing, 399 + (row - 1) * spacing)
    fun vertex(cell: Int, corner: Int): AuxeticFitLabPoint {
        val column = cell % 4; val row = cell / 4; val sign = if ((column + row) % 2 == 0) 1.0 else -1.0
        val half = AuxeticFitLabMath.edge / 2
        val x = if (corner == 0 || corner == 3) -half else half; val y = if (corner == 0 || corner == 1) -half else half
        val c = center(column, row)
        return AuxeticFitLabPoint(c.x + cosine * x - sign * sine * y, c.y + sign * sine * x + cosine * y)
    }
    fun pins(): List<AuxeticFitLabPoint> {
        val pins = ArrayList<AuxeticFitLabPoint>(31)
        for (row in 0..<3) for (column in 0..<3) pins.add(vertex(row * 4 + column, if ((column + row) % 2 == 0) 1 else 2))
        for (row in 0..<2) for (column in 0..<4) pins.add(vertex(row * 4 + column, if ((column + row) % 2 == 0) 2 else 3))
        for ((cell, corner) in boundary) pins.add(vertex(cell, corner))
        return pins
    }
    companion object {
        private val boundary = listOf(0 to 0, 0 to 3, 1 to 1, 2 to 0, 3 to 1, 3 to 2, 4 to 0,
            7 to 1, 8 to 2, 8 to 3, 9 to 3, 10 to 2, 11 to 2, 11 to 3)
    }
}
