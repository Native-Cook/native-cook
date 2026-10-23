package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// Live-only embellishments around the measured opening. Each is a pure function
// of the shared clocks, so a held pose holds them too.
internal const val IDLE_CYCLE_MS = 4_600.0
internal const val BURST_GLINT_COUNT = 26
internal const val BURST_DURATION_MS = 980.0
internal const val MAX_TEAR_SPARKS = 34

private const val TWO_PI = PI * 2

/** Deterministic 0..1 value, so particles need no stored random state. */
internal fun hash01(index: Int, salt: Int): Double {
    val value = sin(index * 127.1 + salt * 311.7) * 43758.5453
    return value - floor(value)
}

private fun smooth(value: Double): Double {
    val t = clamp(value, 0.0, 1.0)
    return t * t * (3 - 2 * t)
}

/** Slow breath of the glow behind an untouched pack; 0 at the cycle boundary. */
internal fun idleAuraBreath(phase: Double): Double = sin(phase * TWO_PI)

internal class IdleSheen(
    @JvmField val start: Point,
    @JvmField val end: Point,
    @JvmField val opacity: Double,
)

/** A diagonal sheen crosses the intact pack once per idle cycle. */
internal fun idleSheen(phase: Double, pack: Rect): IdleSheen {
    val travel = clamp((phase - 0.04) / 0.3, 0.0, 1.0)
    val centerX = pack.x + pack.width * (-0.3 + 1.6 * smooth(travel))
    val centerY = pack.y + pack.height * 0.5
    val band = pack.width * 0.2
    return IdleSheen(
        start = Point(centerX - band, centerY - band * 0.55),
        end = Point(centerX + band, centerY + band * 0.55),
        opacity = sin(travel * PI),
    )
}

internal class IdleSeamHint(
    @JvmField val head: Double,
    @JvmField val tail: Double,
    @JvmField val opacity: Double,
)

/** A light runs along the tear line between sheens, showing where to swipe. */
internal fun idleSeamHint(phase: Double, pack: Rect): IdleSeamHint {
    val travel = clamp((phase - 0.46) / 0.36, 0.0, 1.0)
    val strength = sin(travel * PI)
    val head = pack.x + pack.width * (0.03 + 0.94 * smooth(travel))
    val tail = max(pack.x, head - pack.width * 0.26 * strength)
    return IdleSeamHint(head, tail, strength.pow(0.7))
}

internal class TearSpark(
    @JvmField val x: Double,
    @JvmField val y: Double,
    @JvmField val vx: Double,
    @JvmField val vy: Double,
    @JvmField val age: Double,
    @JvmField val life: Double,
    @JvmField val size: Double,
)

/** A spark thrown backward from the tear frontier. */
internal fun spawnTearSpark(origin: Point, seed: Int, direction: Int, width: Double): TearSpark {
    val speed = width * (0.22 + hash01(seed, 1) * 0.78)
    val spread = (hash01(seed, 2) - 0.5) * 2.5
    val angle = (if (direction == 1) PI else 0.0) + direction * spread
    return TearSpark(
        x = origin.x,
        y = origin.y,
        vx = cos(angle) * speed,
        vy = sin(angle) * speed - width * 0.14,
        age = 0.0,
        life = 230 + hash01(seed, 3) * 330,
        size = width * (0.006 + hash01(seed, 4) * 0.008),
    )
}

internal fun stepTearSpark(spark: TearSpark, seconds: Double, width: Double): TearSpark? {
    val age = spark.age + seconds * 1000
    if (age >= spark.life) return null
    val drag = max(0.0, 1 - 2.4 * seconds)
    return TearSpark(
        x = spark.x + spark.vx * seconds,
        y = spark.y + spark.vy * seconds,
        vx = spark.vx * drag,
        vy = spark.vy * drag + width * 1.5 * seconds,
        age = age,
        life = spark.life,
        size = spark.size,
    )
}

internal class BurstGlint(
    @JvmField val x: Double,
    @JvmField val y: Double,
    @JvmField val size: Double,
    @JvmField val rotation: Double,
)

/** Glints thrown out of the cut. Time is relative to the commit flash. */
internal fun burstGlintAt(
    index: Int,
    ms: Double,
    pack: Rect,
    cutY: Double,
    width: Double,
    height: Double,
): BurstGlint? {
    val age = ms - hash01(index, 1) * 80
    val life = 430 + hash01(index, 2) * 520
    if (age <= 0 || age >= life) return null

    val u = 0.06 + 0.88 * hash01(index, 3)
    // Glints near either side lean outward, giving the burst a fan silhouette.
    val angle = -PI / 2 + (u - 0.5) * 1.7 + (hash01(index, 4) - 0.5) * 0.8
    val speed = width * (0.6 + hash01(index, 5) * 1.25)
    val seconds = age / 1000
    val drag = 4.4
    val travel = speed * (1 - exp(-drag * seconds)) / drag
    val progress = age / life
    val twinkle = 0.72 + 0.28 * sin(age / 38 + index * 1.7)
    return BurstGlint(
        x = pack.x + pack.width * u + cos(angle) * travel,
        y = cutY + sin(angle) * travel + 0.5 * height * 0.34 * seconds * seconds,
        size =
            width * (0.011 + hash01(index, 6) * 0.021) *
                min(1.0, progress * 9) * (1 - progress).pow(0.75) * twinkle,
        rotation = hash01(index, 7) * PI + seconds * (hash01(index, 8) - 0.5) * 7,
    )
}

internal class Shockwave(
    @JvmField val radiusX: Double,
    @JvmField val radiusY: Double,
    @JvmField val strokeWidth: Double,
    @JvmField val opacity: Double,
)

private val SHOCKWAVE_TIMES = doubleArrayOf(0.0, 24.0, 130.0, 480.0)
private val SHOCKWAVE_OPACITY = doubleArrayOf(0.0, 0.62, 0.36, 0.0)

/** Soft ring that keeps expanding after the single-frame flash ring is gone. */
internal fun shockwaveAt(ms: Double, width: Double, height: Double): Shockwave {
    val eased = 1 - (1 - clamp(ms / 480, 0.0, 1.0)).pow(3)
    return Shockwave(
        radiusX = width * (0.4 + 0.52 * eased),
        radiusY = height * (0.05 + 0.105 * eased),
        strokeWidth = width * (0.034 - 0.027 * eased),
        opacity = interpolateKeyframes(ms, SHOCKWAVE_TIMES, SHOCKWAVE_OPACITY),
    )
}

/** Brief zoom of the whole scene on the commit frame. */
internal fun commitPunchScale(ms: Double): Double {
    if (ms <= 0 || ms >= 340) return 1.0
    return 1 + 0.03 * clamp(ms / 40, 0.0, 1.0) * exp(-ms / 78)
}

private val RISE_LIGHT_TIMES = doubleArrayOf(780.0, 880.0, 1040.0, 1380.0, 1640.0)
private val RISE_LIGHT = doubleArrayOf(0.0, 0.9, 0.78, 0.34, 0.0)

/** Light spilling from the mouth while the card rises and centers. */
internal fun cardRiseLightAt(ms: Double): Double = interpolateKeyframes(ms, RISE_LIGHT_TIMES, RISE_LIGHT)
