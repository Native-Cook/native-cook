package com.ngocdevv.nativecook.blocks.modals.pitstopreview

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Closed-form mirror of the accepted React Native motion.ts. */
internal object PitStopReviewMotion {
    const val LOOP = 10.0

    object T {
        const val press = 0.8
        const val tap = 0.9
        const val whipEnd = 1.3
        const val carGone = 1.22
        const val enter = 1.38
        const val stop = 1.92
        const val lightsOn = 2.05
        const val lightStep = 0.1
        const val panelOpen = 2.55
        const val panelDuration = 0.28
        const val flyStart = 2.62
        const val flyStagger = 0.045
        const val flyDuration = 0.46
        const val hold = 3.3
        const val close = 6.0
        const val backStagger = 0.04
        const val backDuration = 0.34
        const val panelClose = 6.08
        const val lightsOut = 6.72
        const val rest = 7.8
    }

    object M {
        const val whipCarTravel = 480.0
        const val entryDistance = 440.0
        const val stopLurch = 3.2
        const val launchLead = 34.0
        const val pitchBrake = 3.0
        const val pitchSquat = -1.5
        const val blurRest = 220.0
        const val blurWhip = 300.0
        const val whipDistance = 3000.0
        const val worldSpeed = 2545.0
        const val carBlurPerVelocity = 0.0055
        const val carBlurMax = 22.0
        const val carRestBlur = 2.0
        const val carStreaks = 0.75
        const val discEmber = 0.28
        const val manualRate = 1.5
        const val rush = 4.0
        const val closeRush = 4.0
        const val panelRise = 18.0
        const val panelScale = 0.96
        const val backdrop = 0.4
        const val ctaPressed = 0.97
    }

    data class Puff(val x: Double, val y: Double, val radius: Double, val alpha: Double)
    data class PackedPuffs(val data: DoubleArray, val count: Int, val box: DoubleArray)
    data class Tyre(val x: Double, val y: Double, val seed: Double, val rear: Boolean, val side: Double)
    private data class SmokeSpec(
        val count: Int,
        val from: Double,
        val to: Double,
        val life: Double,
        val alpha: Double,
        val drag: Double,
        val grow: Double,
        val spread: Double,
    )

    private val frontSmoke = SmokeSpec(16, 1.66, 1.92, 1.3, 0.42, 5.0, 40.0, 44.0)
    private val rearSmoke = SmokeSpec(16, 6.72, 7.22, 1.0, 0.42, 3.0, 46.0, 40.0)
    const val PUFF_SLOTS = 32

    fun clamp(value: Double, low: Double = 0.0, high: Double = 1.0): Double =
        min(high, max(low, value))

    fun smooth(value: Double): Double {
        val t = clamp(value)
        return t * t * (3.0 - 2.0 * t)
    }

    fun span(scene: Double, start: Double, end: Double): Double =
        clamp((scene - start) / max(0.0001, end - start))

    fun easeOutCubic(value: Double): Double {
        val t = 1.0 - clamp(value)
        return 1.0 - t * t * t
    }

    fun easeInOutCubic(value: Double): Double {
        val t = clamp(value)
        return if (t < 0.5) 4.0 * t * t * t else 1.0 - (-2.0 * t + 2.0).pow(3.0) / 2.0
    }

    fun cubicBezier(value: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val x = clamp(value)
        fun coordinate(t: Double, a: Double, b: Double): Double {
            val inverse = 1.0 - t
            return 3.0 * inverse * inverse * t * a + 3.0 * inverse * t * t * b + t * t * t
        }
        var low = 0.0
        var high = 1.0
        var t = x
        repeat(18) {
            val current = coordinate(t, x1, x2)
            if (abs(current - x) < 0.00001) return coordinate(t, y1, y2)
            if (current < x) low = t else high = t
            t = (low + high) * 0.5
        }
        return coordinate(t, y1, y2)
    }

    fun ring(tau: Double, frequency: Double, dampingRatio: Double): Double {
        if (tau <= 0.0) return 1.0
        val omega = 2.0 * PI * frequency
        val damped = omega * sqrt(1.0 - dampingRatio * dampingRatio)
        return exp(-dampingRatio * omega * tau) * cos(damped * tau)
    }

    fun kick(tau: Double, frequency: Double, dampingRatio: Double): Double {
        if (tau <= 0.0) return 0.0
        val omega = 2.0 * PI * frequency
        val damped = omega * sqrt(1.0 - dampingRatio * dampingRatio)
        return exp(-dampingRatio * omega * tau) * sin(damped * tau)
    }

    fun wrap(scene: Double): Double = ((scene % LOOP) + LOOP) % LOOP

    private fun activeScene(raw: Double): Double = if (raw >= T.rest) 0.0 else raw

    fun carX(raw: Double): Double {
        val scene = activeScene(raw)
        if (scene < T.tap) return 0.0
        if (scene < T.carGone) {
            val u = span(scene, T.tap, T.carGone)
            return M.whipCarTravel * u * u * u
        }
        if (scene < T.enter) return M.whipCarTravel
        if (scene < T.stop) {
            val u = span(scene, T.enter, T.stop)
            return -M.entryDistance * (1.0 - u) * (1.0 - u)
        }
        if (scene < T.lightsOut) {
            return M.stopLurch * kick(scene - T.stop, 1.8, 0.45) *
                (1.0 - span(scene, T.stop + 0.6, T.stop + 0.9))
        }
        val tau = scene - T.lightsOut
        return if (tau < 0.4) {
            M.launchLead * easeOutCubic(tau / 0.4)
        } else {
            M.launchLead * (1.0 - smooth((tau - 0.4) / 0.66))
        }
    }

    fun carVelocity(raw: Double): Double {
        val scene = activeScene(raw)
        if (scene >= T.tap && scene < T.carGone) {
            val duration = T.carGone - T.tap
            val u = span(scene, T.tap, T.carGone)
            return 3.0 * M.whipCarTravel * u * u / duration
        }
        if (scene >= T.enter && scene < T.stop) {
            val duration = T.stop - T.enter
            val u = span(scene, T.enter, T.stop)
            return 2.0 * M.entryDistance * (1.0 - u) / duration
        }
        if (scene >= T.lightsOut && scene < T.lightsOut + 0.4) {
            val u = (scene - T.lightsOut) / 0.4
            return 3.0 * M.launchLead * (1.0 - u) * (1.0 - u) / 0.4
        }
        return 0.0
    }

    fun pitch(raw: Double): Double {
        val scene = activeScene(raw)
        if (scene >= T.enter && scene < T.stop) {
            return M.pitchBrake * smooth(span(scene, T.enter + 0.04, T.enter + 0.2))
        }
        if (scene >= T.stop && scene < T.hold) {
            return clamp(
                M.pitchBrake * ring(scene - T.stop, 2.0, 0.32),
                M.pitchSquat,
                M.pitchBrake,
            ) * (1.0 - span(scene, T.hold - 0.35, T.hold))
        }
        if (scene >= T.lightsOut) {
            val tau = scene - T.lightsOut
            val squat = M.pitchSquat * smooth(tau / 0.12)
            val release = if (tau < 0.33) 1.0 else ring(tau - 0.33, 1.6, 0.45)
            return squat * release * (1.0 - span(scene, T.rest - 0.2, T.rest))
        }
        return 0.0
    }

    fun disc(raw: Double): Double {
        val scene = activeScene(raw)
        if (scene < T.enter + 0.12) return 0.0
        if (scene < T.stop) return smooth(span(scene, T.enter + 0.12, T.stop))
        if (scene < T.lightsOut) {
            return M.discEmber + (1.0 - M.discEmber) * exp(-3.0 * (scene - T.stop)) *
                (1.0 - span(scene, T.stop + 1.1, T.hold))
        }
        return M.discEmber * (1.0 - smooth(span(scene, T.lightsOut, T.rest - 0.2)))
    }

    fun speed(raw: Double): Double {
        val scene = activeScene(raw)
        if (scene < T.tap + 0.05) return 1.0
        if (scene < T.lightsOut + 0.13) {
            return 1.0 - smooth(span(scene, T.tap + 0.05, T.whipEnd - 0.05))
        }
        return smooth(span(scene, T.lightsOut + 0.13, T.rest - 0.15))
    }

    fun carBlur(scene: Double): Double = min(
        M.carBlurMax,
        abs(carVelocity(scene)) * M.carBlurPerVelocity + M.carRestBlur * speed(scene),
    )

    fun carStreaks(scene: Double): Double = M.carStreaks * speed(scene)

    fun whip(raw: Double): Double {
        val scene = activeScene(raw)
        if (scene <= T.tap || scene >= T.whipEnd) return 0.0
        val value = sin(PI * span(scene, T.tap, T.whipEnd))
        return value * value
    }

    fun worldBlur(scene: Double): Double = M.blurRest * speed(scene) + M.blurWhip * whip(scene)

    fun worldX(raw: Double): Double {
        val scene = activeScene(raw)
        if (scene < T.tap) return 0.0
        if (scene < T.lightsOut + 0.13) {
            return -M.whipDistance * smooth(span(scene, T.tap, T.whipEnd))
        }
        val start = T.lightsOut + 0.13
        val end = T.rest - 0.15
        val duration = end - start
        val u = span(scene, start, end)
        return -M.whipDistance + M.worldSpeed *
            (duration * (u * u * u - u * u * u * u / 2.0) + max(0.0, scene - end))
    }

    fun haze(raw: Double): Double {
        val scene = activeScene(raw)
        if (scene >= T.enter && scene < T.lightsOut) {
            return smooth(span(scene, T.enter, T.enter + 0.1)) *
                (1.0 - 0.45 * span(scene, T.stop - 0.1, T.stop)) *
                (1.0 - smooth(span(scene, T.stop + 0.3, T.hold - 0.1)))
        }
        if (scene >= T.lightsOut) {
            return smooth(span(scene, T.lightsOut, T.lightsOut + 0.15)) *
                (1.0 - smooth(span(scene, T.lightsOut + 0.5, T.rest - 0.1)))
        }
        return 0.0
    }

    fun trail(raw: Double): Double {
        val scene = activeScene(raw)
        if (scene < T.enter) return 1.0
        if (scene < T.lightsOut) {
            val u = span(scene, T.enter, T.stop)
            return (0.45 + 0.55 * (1.0 - u)) *
                (1.0 - smooth(span(scene, T.stop - 0.08, T.stop + 0.3)))
        }
        return smooth(span(scene, T.lightsOut + 0.1, T.rest - 0.2))
    }

    fun speedLines(raw: Double): Double {
        val scene = activeScene(raw)
        if (scene < T.tap) return 0.0
        if (scene < T.whipEnd) return whip(scene)
        if (scene < T.stop) {
            return smooth(span(scene, T.enter - 0.02, T.enter + 0.08)) *
                (1.0 - smooth(span(scene, T.enter + 0.12, T.stop - 0.12)))
        }
        if (scene >= T.lightsOut) {
            val value = sin(PI * span(scene, T.lightsOut + 0.15, T.rest - 0.25))
            return 0.6 * value * value
        }
        return 0.0
    }

    fun gantry(raw: Double): Double {
        val scene = activeScene(raw)
        return smooth(span(scene, T.stop + 0.03, T.stop + 0.16)) *
            (1.0 - smooth(span(scene, T.lightsOut + 0.06, T.lightsOut + 0.24)))
    }

    fun lightOn(raw: Double, index: Int): Double {
        val scene = activeScene(raw)
        val on = T.lightsOn + index * T.lightStep
        return span(scene, on, on + 0.05) *
            (1.0 - span(scene, T.lightsOut, T.lightsOut + 0.03))
    }

    fun flyIn(raw: Double, index: Int): Double {
        val scene = activeScene(raw)
        val start = T.flyStart + index * T.flyStagger
        return easeInOutCubic(span(scene, start, start + T.flyDuration))
    }

    fun flyBack(raw: Double, index: Int): Double {
        val scene = activeScene(raw)
        val start = T.close + (4 - index) * T.backStagger
        return easeInOutCubic(span(scene, start, start + T.backDuration))
    }

    fun lightAway(raw: Double, index: Int): Double =
        flyIn(raw, index) * (1.0 - flyBack(raw, index))

    fun panel(raw: Double): Double {
        val scene = activeScene(raw)
        if (scene < T.panelOpen) return 0.0
        if (scene < T.panelClose) {
            return cubicBezier(
                (scene - T.panelOpen) / T.panelDuration,
                0.32,
                0.72,
                0.0,
                1.0,
            )
        }
        return 1.0 - cubicBezier(
            (scene - T.panelClose) / T.panelDuration,
            0.23,
            1.0,
            0.32,
            1.0,
        )
    }

    fun ctaScale(raw: Double): Double {
        val scene = activeScene(raw)
        if (scene < T.press || scene > T.tap + 0.15) return 1.0
        if (scene < T.tap) {
            return 1.0 - (1.0 - M.ctaPressed) * cubicBezier(
                (scene - T.press) / (T.tap - T.press),
                0.23,
                1.0,
                0.32,
                1.0,
            )
        }
        return M.ctaPressed + (1.0 - M.ctaPressed) * cubicBezier(
            (scene - T.tap) / 0.15,
            0.23,
            1.0,
            0.32,
            1.0,
        )
    }

    fun starLanded(raw: Double, index: Int): Double =
        if (flyIn(raw, index) >= 1.0 && flyBack(raw, index) <= 0.0) 1.0 else 0.0

    fun starScale(raw: Double, index: Int): Double {
        val scene = activeScene(raw)
        val land = T.flyStart + index * T.flyStagger + T.flyDuration
        if (scene < land || scene > land + 0.18) return 1.0
        return 1.0 + 0.06 * (1.0 - cubicBezier(
            (scene - land) / 0.18,
            0.23,
            1.0,
            0.32,
            1.0,
        ))
    }

    fun hash(value: Double): Double {
        val x = sin(value * 127.1 + 311.7) * 43758.5453
        return x - floor(x)
    }

    fun puff(raw: Double, tyre: Tyre, index: Int): Puff? {
        val scene = activeScene(raw)
        val spec = if (tyre.rear) rearSmoke else frontSmoke
        if (index >= spec.count) return null
        val born = spec.from + (spec.to - spec.from) *
            ((index + hash(tyre.seed + index * 7.3)) / spec.count)
        val age = scene - born
        if (age <= 0.0 || age >= spec.life) return null
        val jitterX = (hash(tyre.seed + index * 3.1) - 0.5) * 8.0
        val jitterY = (hash(tyre.seed + index * 5.7) - 0.5) * 10.0
        val startX = tyre.x + carX(born) + jitterX
        val startY = tyre.y + jitterY
        val spread = tyre.side * (0.25 + 0.75 * hash(tyre.seed + index * 9.9)) * spec.spread
        val ease = 1.0 - exp(-spec.drag * age)
        val x = if (!tyre.rear) {
            startX + carVelocity(born) * ease / spec.drag
        } else {
            startX - 70.0 * (1.0 - exp(-3.0 * age)) - (worldX(scene) - worldX(born))
        }
        val y = startY + spread * (1.0 - exp(-2.4 * age))
        val radius = 6.0 + spec.grow * (1.0 - exp(-1.8 * age)) *
            (0.7 + 0.6 * hash(tyre.seed + index * 1.7))
        val life = age / spec.life
        val rearFade = if (tyre.rear) 1.0 - span(scene, T.rest - 0.35, T.rest) else 1.0
        val alpha = spec.alpha * smooth(age / 0.06) * (1.0 - life) * (1.0 - life) * rearFade
        return Puff(x, y, radius, alpha)
    }

    fun packPuffs(raw: Double, tyres: List<Tyre>): PackedPuffs {
        val values = DoubleArray(PUFF_SLOTS * 4)
        var count = 0
        var x0 = 1e9
        var y0 = 1e9
        var x1 = -1e9
        var y1 = -1e9
        tyres.forEach { tyre ->
            for (index in 0 until 16) {
                if (count >= PUFF_SLOTS) break
                val puff = puff(raw, tyre, index) ?: continue
                if (puff.alpha <= 0.003) continue
                val offset = count * 4
                values[offset] = puff.x
                values[offset + 1] = puff.y
                values[offset + 2] = puff.radius
                values[offset + 3] = puff.alpha
                count += 1
                x0 = min(x0, puff.x - puff.radius)
                y0 = min(y0, puff.y - puff.radius)
                x1 = max(x1, puff.x + puff.radius)
                y1 = max(y1, puff.y + puff.radius)
            }
        }
        val box = if (count > 0) {
            doubleArrayOf(x0, y0, x1, y1)
        } else {
            doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        }
        return PackedPuffs(values, count, box)
    }

    fun moving(raw: Double): Boolean {
        val scene = wrap(raw)
        return (scene >= T.press && scene < T.hold) ||
            (scene >= T.close && scene < T.rest)
    }

    fun morphPoint(
        index: Int,
        progress: Double,
        centerX: Double,
        centerY: Double,
        radius: Double,
    ): Pair<Double, Double> {
        val point = ((index % 20) + 20) % 20
        val angle = -PI / 2.0 + point * 2.0 * PI / 20.0
        val amount = clamp(progress)
        val segment = floor(point / 2.0).toInt()
        val fraction = if (point % 2 == 0) 0.0 else 0.5
        fun star(value: Int): Pair<Double, Double> {
            val item = ((value % 10) + 10) % 10
            val starAngle = -PI / 2.0 + item * PI / 5.0
            val starRadius = if (item % 2 == 0) radius else radius * 0.46
            return cos(starAngle) * starRadius to sin(starAngle) * starRadius
        }
        val start = star(segment)
        val end = star(segment + 1)
        val circleX = cos(angle) * radius
        val circleY = sin(angle) * radius
        return centerX + circleX +
            (start.first + (end.first - start.first) * fraction - circleX) * amount to
            centerY + circleY +
            (start.second + (end.second - start.second) * fraction - circleY) * amount
    }
}
