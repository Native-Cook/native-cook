package com.ngocdevv.nativecook.blocks.navigation.tasteprofile

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

const val LOOP = 10.0
internal const val RESUME_AFTER = 2.0
internal const val SETTLE = 1.6

internal data class TasteProfileScriptEntry(val press: Double, val commit: Double, val city: Int)

internal val SCRIPT = listOf(
    TasteProfileScriptEntry(0.7, 0.82, 2),
    TasteProfileScriptEntry(6.6, 6.72, 0),
)

/** The autoPlay swipe: finger down, drag, release with velocity, snap to the third photo, flat again. */
internal object TasteProfileStripScript {
    const val down = 2.55
    const val release = 2.9
    const val dragTo = 0.55
    const val to = 2
    const val flatAt = 3.55
}

internal val CITIES = listOf("all", "new york", "london", "paris")
internal data class TasteProfileRank(val face: Int, val word: Int, val back: Int)
internal val RANK = listOf(
    TasteProfileRank(0, 0, 0),
    TasteProfileRank(0, 0, 0),
    TasteProfileRank(1, 1, 0),
    TasteProfileRank(1, 2, 1),
)
internal val RANK_WORDS = listOf("new york", "london", "paris")
internal val STATS = listOf(
    listOf("125", "764", "183", "17"),
    listOf("92", "518", "120", "11"),
    listOf("21", "146", "40", "4"),
    listOf("12", "100", "23", "2"),
)

internal object TasteProfilePress {
    const val scale = 0.97
    const val `in` = 0.12
    const val out = 0.16
}

internal data class TasteProfileSpring(val omega: Double, val zeta: Double)

internal object TasteProfilePill {
    val lead = TasteProfileSpring(21.0, 0.84)
    val trail = TasteProfileSpring(12.5, 0.92)
}

internal object TasteProfileCoinMotion {
    const val delay = 0.06
    const val flip = 0.56
    const val lift = 12.0
    const val grow = 0.08
    const val shadow = 0.16
    const val step = 10.0
    const val frames = 19
}

internal object TasteProfileLabelMotion {
    const val delay = 0.18
    const val duration = 0.36
    const val travel = 14.0
}

internal object TasteProfileFollowMotion {
    const val durationMilliseconds = 420.0
    const val dampingRatio = 0.82
    const val grow = 54.0
}

internal object TasteProfileReduced {
    const val fade = 0.2
    const val dip = 0.45
}

internal fun clamp01(value: Double): Double = value.coerceIn(0.0, 1.0)

internal fun segment(value: Double, from: Double, to: Double): Double = clamp01((value - from) / (to - from))

internal fun interpolate(from: Double, to: Double, amount: Double): Double = from + (to - from) * amount

/** CSS cubic-bezier y sampled after solving x with the same 18 bisection steps as the reference. */
internal fun cubicBezier(value: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
    val target = clamp01(value)
    if (target == 0.0 || target == 1.0) return target
    var low = 0.0
    var high = 1.0
    repeat(18) {
        val middle = (low + high) / 2.0
        val inverse = 1.0 - middle
        val sampledX = 3.0 * inverse * inverse * middle * x1 + 3.0 * inverse * middle * middle * x2 + middle * middle * middle
        if (sampledX < target) low = middle else high = middle
    }
    val middle = (low + high) / 2.0
    val inverse = 1.0 - middle
    return 3.0 * inverse * inverse * middle * y1 + 3.0 * inverse * middle * middle * y2 + middle * middle * middle
}

internal fun easeOut(value: Double): Double = cubicBezier(value, 0.23, 1.0, 0.32, 1.0)

internal fun easeInOut(value: Double): Double = cubicBezier(value, 0.77, 0.0, 0.175, 1.0)

/** Closed-form damped spring copied value-for-value from motion.ts. */
internal fun tasteProfileSpring(seconds: Double, spec: TasteProfileSpring): Double {
    if (seconds <= 0.0) return 0.0
    val damped = spec.omega * sqrt(1.0 - spec.zeta * spec.zeta)
    val envelope = exp(-spec.zeta * spec.omega * seconds)
    val value = 1.0 - envelope * (cos(damped * seconds) + spec.zeta * spec.omega / damped * sin(damped * seconds))
    return if (envelope < 1e-4) 1.0 else value
}

internal data class TasteProfileChange(
    val from: Int,
    val to: Int,
    val tau: Double,
    val leftStart: Double,
    val rightStart: Double,
    val angleStart: Double,
    val backStart: Int,
    val photoStart: Double,
    val pressedChip: Int,
    val press: Double,
    val reduced: Boolean,
)

internal data class TasteProfileCoin(val angle: Double, val back: Int, val lift: Double)

internal fun pressAt(time: Double, downAt: Double, upAt: Double): Double {
    if (time < downAt) return 0.0
    if (time < upAt) return easeOut(segment(time, downAt, downAt + TasteProfilePress.`in`))
    val held = easeOut(segment(upAt, downAt, downAt + TasteProfilePress.`in`))
    return held * (1.0 - easeOut(segment(time, upAt, upAt + TasteProfilePress.out)))
}

internal fun chipEdges(index: Int): Pair<Double, Double> {
    val chip = TasteProfileBoxes.chips[index]
    return chip.x.toDouble() to (chip.x + chip.w).toDouble()
}

internal fun loopChange(time: Double): TasteProfileChange {
    val first = SCRIPT[0]
    val second = SCRIPT[1]
    val returning = time >= second.commit
    val script = if (returning) second else first
    val fromCity = if (returning) first.city else 0
    val edges = chipEdges(fromCity)
    val rank = RANK[fromCity]
    val firstPress = pressAt(time, first.press, first.commit)
    val secondPress = pressAt(time, second.press, second.commit)
    val pressedChip: Int
    val press: Double
    when {
        secondPress > 0.0 -> {
            pressedChip = second.city
            press = secondPress
        }
        firstPress > 0.0 -> {
            pressedChip = first.city
            press = firstPress
        }
        else -> {
            pressedChip = -1
            press = 0.0
        }
    }
    return TasteProfileChange(
        from = fromCity,
        to = script.city,
        tau = time - script.commit,
        leftStart = edges.first,
        rightStart = edges.second,
        angleStart = rank.face * 180.0,
        backStart = rank.back,
        photoStart = if (returning) TasteProfileStripScript.to.toDouble() else 0.0,
        pressedChip = pressedChip,
        press = press,
        reduced = false,
    )
}

internal fun pillAt(change: TasteProfileChange): Pair<Double, Double> {
    val target = chipEdges(change.to)
    if (change.reduced) {
        return if (change.tau >= TasteProfileReduced.fade / 2.0) target else change.leftStart to change.rightStart
    }
    val movingRight = target.first + target.second >= change.leftStart + change.rightStart
    val lead = tasteProfileSpring(change.tau, TasteProfilePill.lead)
    val trail = tasteProfileSpring(change.tau, TasteProfilePill.trail)
    val left = interpolate(change.leftStart, target.first, if (movingRight) trail else lead)
    val right = interpolate(change.rightStart, target.second, if (movingRight) lead else trail)
    return left to max(right, left + 1.0)
}

internal fun coinAt(change: TasteProfileChange): TasteProfileCoin {
    val target = RANK[change.to]
    val targetAngle = target.face * 180.0
    if (change.reduced) {
        val swap = change.tau >= TasteProfileReduced.fade / 2.0
        return TasteProfileCoin(if (swap) targetAngle else change.angleStart, if (swap) target.back else change.backStart, 0.0)
    }
    val progress = easeInOut(segment(change.tau, TasteProfileCoinMotion.delay, TasteProfileCoinMotion.delay + TasteProfileCoinMotion.flip))
    val lift = sin(PI * segment(change.tau, TasteProfileCoinMotion.delay, TasteProfileCoinMotion.delay + TasteProfileCoinMotion.flip))
    if (change.angleStart == 180.0 && targetAngle == 180.0 && change.backStart != target.back) {
        val firstHalf = progress < 0.5
        val angle = if (firstHalf) interpolate(180.0, 90.0, progress * 2.0) else interpolate(90.0, 180.0, progress * 2.0 - 1.0)
        return TasteProfileCoin(angle, if (firstHalf) change.backStart else target.back, lift)
    }
    if (change.angleStart == targetAngle) return TasteProfileCoin(targetAngle, target.back, 0.0)
    return TasteProfileCoin(interpolate(change.angleStart, targetAngle, progress), if (targetAngle == 180.0) target.back else change.backStart, lift)
}

internal fun coinFrame(angle: Double, back: Int): Int {
    val frame = (angle / TasteProfileCoinMotion.step).roundToInt()
    return if (frame <= 9 || back == 0) frame else TasteProfileCoinMotion.frames + frame - 10
}

internal fun rollProgress(local: Double, slot: Int, duration: Double, stagger: Double): Double =
    easeOut(clamp01((local - slot * stagger) / duration))

internal fun reducedDip(tau: Double): Double {
    if (tau <= 0.0 || tau >= TasteProfileReduced.fade) return 1.0
    return 1.0 - (1.0 - TasteProfileReduced.dip) * sin(PI * tau / TasteProfileReduced.fade)
}

internal data class TasteProfileRollGroup(
    val layerOf: List<Int>,
    val values: List<Int>,
    val start: Double,
    val duration: Double,
    val stagger: Double,
    val travel: Double,
)

internal data class TasteProfileRollStyle(val y: Double, val opacity: Double)

internal fun rollStyle(change: TasteProfileChange, group: TasteProfileRollGroup, layer: Int, slot: Int): TasteProfileRollStyle {
    val fromLayer = group.layerOf[change.from]
    val toLayer = group.layerOf[change.to]
    if (change.reduced) {
        val shown = if (change.tau >= TasteProfileReduced.fade / 2.0) toLayer else fromLayer
        val opacity = if (layer == shown) if (fromLayer == toLayer) 1.0 else reducedDip(change.tau) else 0.0
        return TasteProfileRollStyle(0.0, opacity)
    }
    if (fromLayer == toLayer) return TasteProfileRollStyle(0.0, if (layer == toLayer) 1.0 else 0.0)
    if (layer != fromLayer && layer != toLayer) return TasteProfileRollStyle(0.0, 0.0)
    val direction = if (group.values[toLayer] >= group.values[fromLayer]) 1.0 else -1.0
    val progress = rollProgress(change.tau - group.start, slot, group.duration, group.stagger)
    return if (layer == fromLayer) {
        TasteProfileRollStyle(-direction * progress * group.travel, 1.0 - clamp01(progress / 0.6))
    } else {
        TasteProfileRollStyle(direction * (1.0 - progress) * group.travel, clamp01((progress - 0.3) / 0.7))
    }
}
