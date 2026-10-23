package com.ngocdevv.nativecook.blocks.navigation.tasteprofile

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Departure board, cover-flow and chart motion, ported value for value from motion.ts. */

internal const val DRUM = " 0123456789"

internal object TasteProfileBoardMotion {
    const val perspDigit = 140.0
    const val perspPhoto = 520.0
    const val digitDelay = 0.06
    const val digitTileStagger = 0.07
    const val digitSlotStagger = 0.03
    const val digitStep = 0.075
    const val digitCardIn = 0.09
    const val digitCardOut = 0.24
    val digitCard = TasteProfileCard(42.0, 10.0, 7.0, 13.0)
    const val countDelay = 0.32
    const val countSlotStagger = 0.03
    const val countStep = 0.075
    val countCard = TasteProfileCard(25.0, 6.0, 4.0, 0.0)
    const val photoDelay = 0.1
    const val photoCellStagger = 0.08
    const val photoStep = 0.17
    const val photoSteps = 3
    const val bounceAmp = 16.0
    const val bounceOmega = 34.0
    const val bounceZeta = 0.34
    const val bounceDur = 0.28
    const val shutter = 1.0 / 60.0
    val ghostAlpha = listOf(0.34, 0.2, 0.09)
    const val flapDark = 0.42
    const val flapLight = 0.16
    const val glint = 0.22
    const val uncover = 0.32
    const val cover = 0.36
    const val seam = 0.38
}

internal data class TasteProfileCard(val w: Double, val padY: Double, val r: Double, val spread: Double)

internal val STRIP_PHOTOS: List<List<Int>> = listOf(
    listOf(0, 1, 2, 3, 4, 10, 16, 5),
    listOf(4, 5, 6, 7, 8, 9),
    listOf(10, 11, 12, 13, 14, 15),
    listOf(16, 17, 18, 19, 20, 21),
)
internal val PHOTO_COUNT = listOf("1470", "958", "312", "200")

internal object TasteProfileCoverMotion {
    const val rotate = 52.0
    const val span = 1.6
    const val squeeze = 0.2
    const val shrink = 0.1
    const val dim = 0.3
    const val radius = 0.7
    const val inset = 0.05
    const val grow = 60.0
    const val inDuration = 0.22
    const val outDuration = 0.30
    const val project = 0.22
    const val rubber = 0.35
    const val snapDuration = 0.52
    const val snapZeta = 0.82
    const val takeoverDuration = 0.40
    val snap = TasteProfileSpring(10.0, 0.82)
}

internal object TasteProfileExpandMotion {
    const val hold = 0.35
    val spring = TasteProfileSpring(17.0, 0.84)
    const val out = 0.24
    const val barDelay = 0.1
    const val barStagger = 0.045
    val barSpring = TasteProfileSpring(19.0, 0.72)
    const val shadowFrom = 0.05
    const val shadowTo = 0.16
    const val hlDuration = 0.12
}

internal val MONTHS = listOf("may", "jun", "jul", "aug", "sep", "oct")
private val SHARE = listOf(
    listOf(0.11, 0.14, 0.12, 0.18, 0.2, 0.25),
    listOf(0.13, 0.12, 0.17, 0.16, 0.19, 0.23),
    listOf(0.2, 0.18, 0.15, 0.16, 0.14, 0.17),
    listOf(0.24, 0.12, 0.2, 0.14, 0.18, 0.12),
)

internal fun monthBars(city: Int, tile: Int): List<Int> {
    val total = STATS[city][tile].toInt()
    val out = mutableListOf<Int>()
    var used = 0
    for (i in 0 until 6) {
        // JavaScript's Math.round rounds halves up.
        val value = if (i == 5) total - used else floor(total * SHARE[tile][i] + 0.5).toInt()
        out.add(value)
        used += value
    }
    return out
}

internal data class TasteProfileHold(val tile: Int, val t0: Double, val t1: Double, val t2: Double) {
    companion object {
        val NONE = TasteProfileHold(-1, -1.0, -1.0, -1.0)
        val SCRIPT = TasteProfileHold(0, 4.35, 4.7, 6.0)
    }
}

internal data class TasteProfileHoldView(
    val tile: Int = -1,
    val press: Double = 0.0,
    val e: Double = 0.0,
    val since: Double = 0.0,
    val hl: Double = 5.0,
)

internal fun springFrom(seconds: Double, from: Double, to: Double, v0: Double, spec: TasteProfileSpring): Double {
    if (seconds <= 0.0) return from
    val damped = spec.omega * sqrt(1.0 - spec.zeta * spec.zeta)
    val a = from - to
    val b = (v0 + spec.zeta * spec.omega * a) / damped
    val envelope = exp(-spec.zeta * spec.omega * seconds)
    if (envelope < 1e-4) return to
    return to + envelope * (a * cos(damped * seconds) + b * sin(damped * seconds))
}

/** Reanimated's duration-based spring: natural frequency such that the energy decays under a threshold. */
internal fun durationOmega(duration: Double, zeta: Double): Double {
    val mass = 4.0
    val threshold = 6e-9
    val settling = duration * 1.5
    fun difference(stiffness: Double): Double {
        val x0 = -1.0
        val dampedOmega = sqrt(stiffness / mass) * zeta
        val position = (x0 + x0 * dampedOmega * settling) * exp(-dampedOmega * settling)
        val nextVelocity = position * -dampedOmega + x0 * dampedOmega * exp(-dampedOmega * settling)
        val initial = 0.5 * stiffness * x0 * x0
        val energy = 0.5 * stiffness * position * position + 0.5 * mass * nextVelocity * nextVelocity
        return energy / initial - threshold
    }
    var low = 2.220446049250313e-16
    var high = 8000.0
    val direction = if (difference(high) >= difference(low)) 1.0 else -1.0
    repeat(100) {
        val middle = (low + high) / 2.0
        if (abs(difference(middle)) <= threshold * 1e-3) return sqrt(middle / mass)
        if (difference(middle) * direction < 0.0) low = middle else high = middle
    }
    return sqrt(((low + high) / 2.0) / mass)
}

/** Value of a duration-based spring started at `startedAt` with `velocity` (units/s). */
internal fun durationSpringValue(
    now: Double,
    startedAt: Double,
    from: Double,
    to: Double,
    velocity: Double,
    omega: Double,
    zeta: Double,
    settle: Double,
): Double {
    val elapsed = now - startedAt
    if (elapsed <= 0.0) return from
    if (elapsed >= settle) return to
    val x0 = from - to
    val damped = omega * sqrt(1.0 - zeta * zeta)
    val envelope = exp(-zeta * omega * elapsed)
    return to + envelope * (sin(damped * elapsed) * ((velocity + zeta * omega * x0) / damped) + x0 * cos(damped * elapsed))
}

/** The strip in the loop: offset (photos) and separation k (0 flat … 1 cover-flow). */
internal fun stripAt(t: Double): Pair<Double, Double> {
    val s = TasteProfileStripScript
    if (t < s.down || t >= SCRIPT[1].commit) return 0.0 to 0.0
    val k = if (t < s.flatAt) {
        easeOut(segment(t, s.down, s.down + TasteProfileCoverMotion.inDuration))
    } else {
        1.0 - easeInOut(segment(t, s.flatAt, s.flatAt + TasteProfileCoverMotion.outDuration))
    }
    if (t < s.release) {
        val p = segment(t, s.down, s.release)
        return s.dragTo * p * p to k
    }
    val v0 = 2.0 * s.dragTo / (s.release - s.down)
    return springFrom(t - s.release, s.dragTo, s.to.toDouble(), v0, TasteProfileCoverMotion.snap) to k
}

internal data class TasteProfileHoldSample(val tile: Int, val press: Double, val e: Double, val since: Double)

internal fun holdAt(t: Double, h: TasteProfileHold): TasteProfileHoldSample {
    if (h.tile < 0 || t < h.t0) return TasteProfileHoldSample(-1, 0.0, 0.0, 0.0)
    val commit = if (h.t1 >= 0.0) h.t1 else h.t2
    val press = pressAt(t, h.t0, commit)
    if (h.t1 < 0.0 || t < h.t1) return TasteProfileHoldSample(if (press > 0.0) h.tile else -1, press, 0.0, 0.0)
    var e = tasteProfileSpring(minOf(t, h.t2) - h.t1, TasteProfileExpandMotion.spring)
    if (t > h.t2) e *= 1.0 - easeOut(segment(t, h.t2, h.t2 + TasteProfileExpandMotion.out))
    return TasteProfileHoldSample(
        if (e > 0.0005 || press > 0.0) h.tile else -1,
        press,
        if (t >= h.t2 + TasteProfileExpandMotion.out) 0.0 else e,
        t - h.t1,
    )
}

/** The autoPlay finger on the bar chart: slides to September, then August. */
internal fun scriptHl(since: Double): Double = 5.0 - easeOut(segment(since, 0.85, 0.97)) - easeOut(segment(since, 1.1, 1.22))

internal fun holdView(now: Double, h: TasteProfileHold, hl: Double, reduced: Boolean): TasteProfileHoldView {
    val v = holdAt(now, h)
    if (reduced && h.t1 >= 0.0 && now >= h.t1) {
        val e = if (now < h.t2) clamp01((now - h.t1) / 0.2) else 1.0 - clamp01((now - h.t2) / 0.2)
        return TasteProfileHoldView(if (e > 0.0) h.tile else -1, 0.0, e, 10.0, hl)
    }
    return TasteProfileHoldView(v.tile, if (reduced) 0.0 else v.press, v.e, v.since, hl)
}

internal fun bounceAt(t: Double): Double {
    if (t <= 0.0 || t >= TasteProfileBoardMotion.bounceDur) return 0.0
    val damped = TasteProfileBoardMotion.bounceOmega * sqrt(1.0 - TasteProfileBoardMotion.bounceZeta * TasteProfileBoardMotion.bounceZeta)
    return TasteProfileBoardMotion.bounceAmp * exp(-TasteProfileBoardMotion.bounceZeta * TasteProfileBoardMotion.bounceOmega * t) * abs(sin(damped * t))
}

internal data class TasteProfileFlap(val k: Int, val angle: Double, val speed: Double, val bounce: Double)

internal fun flapAt(local: Double, n: Int, step: Double): TasteProfileFlap {
    if (n <= 0 || local < 0.0) return TasteProfileFlap(0, -1.0, 0.0, 0.0)
    val k = floor(local / step).toInt()
    if (k >= n) return TasteProfileFlap(n, -1.0, 0.0, bounceAt(local - n * step))
    val u = local / step - k
    return TasteProfileFlap(k, 180.0 * u * u, 360.0 * u / step, 0.0)
}

internal fun drumSteps(a: Char, b: Char): Int {
    val i = DRUM.indexOf(a)
    val j = DRUM.indexOf(b)
    return (j - i + DRUM.length) % DRUM.length
}

internal fun drumChar(a: Char, k: Int): Char = DRUM[(DRUM.indexOf(a) + k) % DRUM.length]

internal fun barUnder(xRef: Double): Int {
    val gap = (TasteProfileChart.w - 2 * TasteProfileChart.pad - 6 * TasteProfileChart.barW) / 5
    val i = ((xRef - TasteProfileChart.pad - TasteProfileChart.barW / 2) / (TasteProfileChart.barW + gap)).roundToInt()
    return i.coerceIn(0, 5)
}

internal fun jsRound(value: Double): Double = floor(value + 0.5)

