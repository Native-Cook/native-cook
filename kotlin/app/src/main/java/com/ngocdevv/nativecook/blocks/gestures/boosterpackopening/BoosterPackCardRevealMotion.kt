package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

internal const val CARD_COUNT = 5

// At ~30° yaw, the reference video exposes ~2.5% of the projected card width
// between successive edges (about 19 px on a 755 px face).
internal const val CARD_STACK_DEPTH = 0.048
internal const val CARD_EXIT_MS = 180.0
internal const val RARE_REVEAL_MS = 2600.0
internal const val RARE_INTERACTION_MS = 1650.0

/** How many marks sit under each card once it has arrived. */
internal val CARD_MARKS = intArrayOf(1, 1, 3, 2, 2)
internal val CARD_HOLO = doubleArrayOf(0.0, 0.0, 0.3, 1.0, 1.0)
internal val PACK_CARD_RARITIES =
    listOf(CardRarity.Common, CardRarity.Common, CardRarity.Rare, CardRarity.Epic, CardRarity.Epic)

internal class CardPose(
    @JvmField val x: Double,
    @JvmField val y: Double,
    @JvmField val yaw: Double,
    @JvmField val pitch: Double,
    @JvmField val roll: Double,
    @JvmField val scale: Double,
)

/** The cards that turn through their back when they arrive. */
internal fun isRareCard(index: Int): Boolean = CARD_HOLO[index] == 1.0

/** Slow movement inspects the stack. Distance OR an intentional flick advances. */
internal fun shouldAdvanceCard(dx: Double, dy: Double, vx: Double, cardWidth: Double): Boolean {
    if (abs(dx) < abs(dy) * 1.25) return false
    val flick = abs(dx) > cardWidth * 0.065 && abs(vx) > 650 && dx * vx > 0
    return abs(dx) >= cardWidth * 0.38 || flick
}

internal class CardTilt(
    @JvmField val yaw: Double,
    @JvmField val pitch: Double,
)

internal fun dragCardTilt(dx: Double, dy: Double, cardWidth: Double): CardTilt =
    // The reference exposes ~30° of yaw and much less pitch. Soft resistance
    // lets the finger continue moving without flattening the card against an edge.
    CardTilt(
        yaw = 0.56 * tanh(dx / (cardWidth * 0.24)),
        pitch = -0.16 * tanh(dy / (cardWidth * 0.38)),
    )

// Measured at 30 fps in the reference video. This is the automatic
// rare-card turn AFTER the previous card slides away, not a drag.
private val TURN_TIMES = doubleArrayOf(0.0, 500.0, 566.0, 633.0, 733.0, 833.0, 933.0, 1033.0, 1200.0, 1450.0, 1650.0)
private val TURN_ANGLES = doubleArrayOf(0.0, 0.0, 0.06, 0.52, 2.2, 3.75, 5.05, 5.72, 6.0, 6.2, PI * 2)

private fun turnSlope(i: Int): Double {
    if (i == 0 || i == TURN_TIMES.size - 1) return 0.0
    val a = (TURN_ANGLES[i] - TURN_ANGLES[i - 1]) / (TURN_TIMES[i] - TURN_TIMES[i - 1])
    val b = (TURN_ANGLES[i + 1] - TURN_ANGLES[i]) / (TURN_TIMES[i + 1] - TURN_TIMES[i])
    return if (a * b <= 0) 0.0 else 2 * a * b / (a + b)
}

/** Monotone Hermite interpolation keeps angular velocity continuous at samples. */
private fun turnAtTime(ms: Double): Double {
    if (ms <= TURN_TIMES[0]) return 0.0
    if (ms >= RARE_INTERACTION_MS) return PI * 2
    for (i in 1 until TURN_TIMES.size) {
        if (ms > TURN_TIMES[i]) continue
        val duration = TURN_TIMES[i] - TURN_TIMES[i - 1]
        val t = (ms - TURN_TIMES[i - 1]) / duration
        val t2 = t * t
        val t3 = t2 * t
        return (2 * t3 - 3 * t2 + 1) * TURN_ANGLES[i - 1] +
            (t3 - 2 * t2 + t) * duration * turnSlope(i - 1) +
            (-2 * t3 + 3 * t2) * TURN_ANGLES[i] +
            (t3 - t2) * duration * turnSlope(i)
    }
    return PI * 2
}

internal class RareCardPose(
    @JvmField val yaw: Double,
    @JvmField val pitch: Double,
    @JvmField val roll: Double,
    @JvmField val scale: Double,
    @JvmField val light: Double,
    @JvmField val flash: Double,
    @JvmField val sweep: Double,
)

private val RARE_LIGHT_TIMES = doubleArrayOf(0.0, 250.0, 600.0, 1800.0, 2200.0, 2600.0)
private val RARE_LIGHT = doubleArrayOf(0.0, 0.0, 1.0, 1.0, 0.65, 0.0)
private val RARE_FLASH_TIMES = doubleArrayOf(0.0, 1120.0, 1250.0, 1340.0, 1560.0, 1950.0, 2330.0)
private val RARE_FLASH = doubleArrayOf(0.0, 0.0, 0.65, 1.0, 0.48, 0.35, 0.0)

internal fun rareCardPose(ms: Double, reducedMotion: Boolean = false): RareCardPose {
    val yaw = if (reducedMotion) 0.0 else turnAtTime(ms)
    val light = if (reducedMotion) 0.0 else interpolateKeyframes(ms, RARE_LIGHT_TIMES, RARE_LIGHT)
    val flash = if (reducedMotion) 0.0 else interpolateKeyframes(ms, RARE_FLASH_TIMES, RARE_FLASH)
    return RareCardPose(
        yaw = yaw,
        pitch = if (reducedMotion) 0.0 else sin(yaw) * 0.065,
        roll = if (reducedMotion) 0.0 else sin(yaw) * 0.018,
        scale = if (reducedMotion) 1.0 else 1 + sin(clamp((ms - 550) / 1100, 0.0, 1.0) * PI) * 0.045,
        light = light,
        flash = flash,
        sweep = if (reducedMotion) 0.0 else clamp((ms - 1120) / 450, 0.0, 1.0),
    )
}

internal const val CARD_ARRIVAL_MS = 520.0

internal class CardArrival(
    @JvmField val pop: Double,
    @JvmField val sweep: Double,
    @JvmField val flash: Double,
)

private val ARRIVAL_FLASH_TIMES = doubleArrayOf(0.0, 60.0, 200.0, 380.0, CARD_ARRIVAL_MS)
private val ARRIVAL_FLASH = doubleArrayOf(0.0, 0.0, 0.85, 0.6, 0.0)
private val NO_ARRIVAL = CardArrival(0.0, 0.0, 0.0)

/** A plain card arriving on top of the stack: a small pop and one glint. */
internal fun commonCardArrival(ms: Double, reducedMotion: Boolean = false): CardArrival {
    if (reducedMotion || ms >= CARD_ARRIVAL_MS) return NO_ARRIVAL
    val t = clamp(ms / 360, 0.0, 1.0)
    return CardArrival(
        // Peaks early, then settles without crossing below the resting scale.
        pop = sin(t * PI) * (1 - t * 0.55),
        sweep = clamp((ms - 60) / 420, 0.0, 1.0),
        flash = interpolateKeyframes(ms, ARRIVAL_FLASH_TIMES, ARRIVAL_FLASH),
    )
}

/**
 * True perspective projection of a rigid card, in local centered coordinates, as the nine values
 * of a row-major 3×3 matrix. A homography avoids affine triangle seams.
 * Reverse the back's U direction so its lettering is never mirrored.
 */
internal fun cardProjection(pose: CardPose, cardWidth: Double, back: Boolean = false): FloatArray {
    val cy = cos(pose.yaw)
    val sy = sin(pose.yaw)
    val cx = cos(pose.pitch)
    val sx = sin(pose.pitch)
    val cz = cos(pose.roll)
    val sz = sin(pose.roll)
    val flip = if (back) -1.0 else 1.0
    val scale = pose.scale
    val r00 = cy * cz * scale * flip
    val r01 = (sx * sy * cz - cx * sz) * scale
    val r10 = cy * sz * scale * flip
    val r11 = (sx * sy * sz + cx * cz) * scale
    val p0 = sy * scale * flip / (cardWidth * 2.7)
    val p1 = -cy * sx * scale / (cardWidth * 2.7)
    return floatArrayOf(
        (r00 + pose.x * p0).toFloat(), (r01 + pose.x * p1).toFloat(), pose.x.toFloat(),
        (r10 + pose.y * p0).toFloat(), (r11 + pose.y * p1).toFloat(), pose.y.toFloat(),
        p0.toFloat(), p1.toFloat(), 1f,
    )
}
