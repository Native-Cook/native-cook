package com.ngocdevv.nativecook.blocks.onboarding.pricetagwelcome

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

internal object PriceTagWelcomeMotion {
    const val LOOP = 8.0
    const val N = 5
    const val RESUME_AFTER = 2.0

    object Script {
        const val pressIn = 0.8
        const val freeze = 0.9
        const val tapItem = 1
        const val thawPress = 4.3
        const val thaw = 4.4
        val seam = doubleArrayOf(6.6, 7.4)
    }

    object Freeze {
        const val grow = 1.25
        const val lockAt = 0.19
        const val unlockAt = 0.3
        const val melt = 1.4
        const val kick = 0.9
        const val drag = 0.55
    }

    object Etch {
        val priceOut = doubleArrayOf(0.05, 0.4)
        val wait = doubleArrayOf(0.4, 0.8)
        val timer = doubleArrayOf(0.85, 1.3)
        const val firstTick = 1.85
        val out = doubleArrayOf(0.0, 0.4)
        val priceIn = doubleArrayOf(0.35, 0.7)
        const val priceGhost = 0.22
        /** The frozen price's blur radius (pt at 402 pt): a real Gaussian blur, bounded by the tag. */
        const val priceBlur = 3.5
    }

    object Tick {
        const val omega = 22.0
        const val zeta = 0.62
    }

    const val STILL_FADE = 0.2

    object Swing {
        const val hz = 1.35
        const val zeta = 0.13
        val tune = doubleArrayOf(1.0, 0.94, 1.06, 0.97, 1.03)
        const val gain = 0.00095
        const val maxDeg = 24.0
    }

    object Press {
        const val scale = 0.97
        const val pressIn = 0.1
        const val pressOut = 0.2
    }

    const val DECAY_TAU = 0.36

    object Home {
        const val base = 0.45
        const val perPx = 0.0005
        const val max = 1.1
        const val thawStagger = 0.06
    }

    object Glint {
        const val fullSpeed = 1500.0
        const val alpha = 0.5
    }

    const val TAP_SLOP = 30.0
    const val PAN_SLOP = 8.0
    const val PAN_FAIL_Y = 16.0

    private const val TABLE_HZ = 120
    internal const val SIM_HZ = 240

    data class SpringFrame(
        val x: Double,
        val velocity: Double,
    )

    data class SwingFrame(
        val theta: Double,
        val velocity: Double,
    )

    data class FrostClock(
        val freezeTime: Double,
        val meltTime: Double,
    )

    data class FrostLook(
        val on: Boolean,
        val grow: Double,
        val melt: Double,
        val fade: Double,
        val priceAlpha: Double,
        /** 0...1: how far the price has gone under the ice (its blur and the cross-fade to the blurred copy). */
        val lost: Double,
        val wait: Double,
        val timer: Double,
        val out: Double,
    )

    data class Countdown(
        val left: Int,
        val previous: Int,
        val roll: Double,
    )

    data class Frame(
        val x: Double,
        val velocity: Double,
        val thetaDegrees: DoubleArray,
        val freezeTime: DoubleArray,
        val meltTime: DoubleArray,
        val press: DoubleArray,
        val clock: Double,
        val glint: Double,
        val glintAlpha: Double,
    )

    val REST_FRAME = Frame(
        x = 0.0,
        velocity = 0.0,
        thetaDegrees = DoubleArray(N),
        freezeTime = DoubleArray(N) { -1.0 },
        meltTime = DoubleArray(N) { -1.0 },
        press = DoubleArray(N),
        clock = 0.0,
        glint = 0.0,
        glintAlpha = 0.0,
    )

    fun clamp01(value: Double): Double = value.coerceIn(0.0, 1.0)

    fun segment(time: Double, start: Double, end: Double): Double {
        return clamp01((time - start) / (end - start))
    }

    fun lerp(start: Double, end: Double, progress: Double): Double {
        return start + (end - start) * progress
    }

    fun mod(value: Double, period: Double): Double {
        return ((value % period) + period) % period
    }

    fun smoothstep(progress: Double): Double {
        val x = clamp01(progress)
        return x * x * (3.0 - 2.0 * x)
    }

    fun easeInOut(progress: Double): Double {
        val x = clamp01(progress)
        return if (x < 0.5) {
            4.0 * x * x * x
        } else {
            1.0 - (-2.0 * x + 2.0).pow(3.0) / 2.0
        }
    }

    /** An item's frost layers are composed only while it is on screen, plus this lead (ref px). */
    const val MOUNT_LEAD = 60.0

    /** Bit i: item i's sticker, rime halo and lead overlap the screen at row offset `offset`. */
    fun visibleMask(offset: Double): Int {
        val reach = PriceTagWelcomeTheme.Space.frostMargin + MOUNT_LEAD
        var mask = 0
        for (index in PriceTagWelcomeTheme.items.indices) {
            val item = PriceTagWelcomeTheme.items[index]
            val x = wrapX(item.x, offset)
            if (x + item.w + reach > 0.0 && x - reach < PriceTagWelcomeTheme.REF_W) {
                mask = mask or (1 shl index)
            }
        }
        return mask
    }

    fun wrapX(restX: Double, offset: Double): Double {
        return PriceTagWelcomeTheme.WRAP_LEFT + mod(
            restX + offset - PriceTagWelcomeTheme.WRAP_LEFT,
            PriceTagWelcomeTheme.PERIOD,
        )
    }

    fun springStep(tau: Double, omega: Double, zeta: Double): SpringFrame {
        if (tau <= 0.0) {
            return SpringFrame(0.0, 0.0)
        }
        val dampedOmega = omega * sqrt(1.0 - zeta * zeta)
        val decay = exp(-zeta * omega * tau)
        val cosine = cos(dampedOmega * tau)
        val sine = sin(dampedOmega * tau)
        return SpringFrame(
            x = 1.0 - decay * (cosine + zeta * omega / dampedOmega * sine),
            velocity = decay * omega * omega / dampedOmega * sine,
        )
    }

    fun frostLook(freezeTime: Double, meltTime: Double, still: Boolean): FrostLook {
        if (freezeTime < 0.0) {
            return FrostLook(false, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0)
        }
        val melt = if (meltTime < 0.0) 0.0 else clamp01(meltTime / Freeze.melt)
        if (still) {
            val alpha = clamp01(freezeTime / STILL_FADE) *
                if (meltTime < 0.0) 1.0 else 1.0 - clamp01(meltTime / STILL_FADE)
            return FrostLook(
                on = true,
                grow = 1.0,
                melt = 0.0,
                fade = alpha,
                priceAlpha = lerp(1.0, Etch.priceGhost, alpha),
                lost = alpha,
                wait = alpha,
                timer = alpha,
                out = 0.0,
            )
        }
        val growth = clamp01(freezeTime / Freeze.grow)
        val grow = 1.0 - (1.0 - growth).pow(3.0)
        val gone = smoothstep(segment(freezeTime, Etch.priceOut[0], Etch.priceOut[1]))
        val back = if (meltTime < 0.0) {
            0.0
        } else {
            smoothstep(segment(meltTime, Etch.priceIn[0], Etch.priceIn[1]))
        }
        val lost = gone * (1.0 - back)
        return FrostLook(
            on = true,
            grow = grow,
            melt = melt,
            fade = 1.0,
            priceAlpha = lerp(1.0, Etch.priceGhost, lost),
            lost = lost,
            wait = segment(freezeTime, Etch.wait[0], Etch.wait[1]),
            timer = segment(freezeTime, Etch.timer[0], Etch.timer[1]),
            out = if (meltTime < 0.0) 0.0 else smoothstep(segment(meltTime, Etch.out[0], Etch.out[1])),
        )
    }

    fun countdownAt(freezeTime: Double, still: Boolean): Countdown {
        if (freezeTime < Etch.firstTick) {
            return Countdown(
                left = PriceTagWelcomeTheme.Copy.countdown,
                previous = PriceTagWelcomeTheme.Copy.countdown,
                roll = 1.0,
            )
        }
        val ticks = floor(freezeTime - Etch.firstTick).toInt() + 1
        val since = freezeTime - Etch.firstTick - (ticks - 1)
        return Countdown(
            left = PriceTagWelcomeTheme.Copy.countdown - ticks,
            previous = PriceTagWelcomeTheme.Copy.countdown - ticks + 1,
            roll = if (still) 1.0 else springStep(since, Tick.omega, Tick.zeta).x,
        )
    }

    fun digitOf(seconds: Int, column: Int): Int {
        val hours = seconds / 3600
        val minutes = seconds / 60 % 60
        val secs = seconds % 60
        val value = if (column < 2) hours else if (column < 4) minutes else secs
        return if (column % 2 == 0) value / 10 % 10 else value % 10
    }

    fun stripLine(from: Int, to: Int, roll: Double): Double {
        if (from == to) {
            return to.toDouble()
        }
        val start = if (from == 0) 10 else from
        return start + (to - start) * roll
    }

    fun scriptFrost(index: Int, time: Double): FrostClock {
        if (index != Script.tapItem || time < Script.freeze) {
            return FrostClock(-1.0, -1.0)
        }
        val meltTime = if (time >= Script.thaw) time - Script.thaw else -1.0
        if (meltTime >= Freeze.melt) {
            return FrostClock(-1.0, -1.0)
        }
        return FrostClock(time - Script.freeze, meltTime)
    }

    fun scriptPress(index: Int, time: Double): Double {
        if (index != Script.tapItem) {
            return 0.0
        }
        fun hold(start: Double, end: Double): Double {
            return smoothstep(segment(time, start, start + Press.pressIn)) *
                (1.0 - smoothstep(segment(time, end, end + Press.pressOut)))
        }
        return max(
            hold(Script.pressIn, Script.freeze),
            hold(Script.thawPress, Script.thaw),
        )
    }

    private fun omegaOf(index: Int): Double {
        return 2.0 * PI * Swing.hz * Swing.tune[index]
    }

    fun swingStep(
        theta: Double,
        velocity: Double,
        index: Int,
        acceleration: Double,
        delta: Double,
    ): SwingFrame {
        val omega = omegaOf(index)
        val angularAcceleration = -omega * omega * theta -
            2.0 * Swing.zeta * omega * velocity + Swing.gain * acceleration
        val nextVelocity = velocity + angularAcceleration * delta
        return SwingFrame(theta + nextVelocity * delta, nextVelocity)
    }

    fun lockedBy(freezeTime: Double, meltTime: Double): Boolean {
        return freezeTime >= Freeze.lockAt &&
            (meltTime < 0.0 || meltTime < Freeze.unlockAt)
    }

    private val swingTable: Array<DoubleArray> by lazy { buildSwingTable() }

    private fun buildSwingTable(): Array<DoubleArray> {
        val steps = (LOOP * SIM_HZ).roundToInt()
        val every = SIM_HZ / TABLE_HZ
        return Array(N) { index ->
            var theta = 0.0
            var velocity = 0.0
            val row = DoubleArray(steps / every + 1)
            var outputIndex = 1
            for (step in 1..steps) {
                val time = step.toDouble() / SIM_HZ
                if (index == Script.tapItem &&
                    time - 1.0 / SIM_HZ < Script.freeze &&
                    time >= Script.freeze
                ) {
                    velocity += Freeze.kick
                }
                val frost = scriptFrost(index, time)
                if (lockedBy(frost.freezeTime, frost.meltTime)) {
                    velocity = 0.0
                } else {
                    val next = swingStep(theta, velocity, index, 0.0, 1.0 / SIM_HZ)
                    theta = next.theta
                    velocity = next.velocity
                }
                if (step % every == 0) {
                    row[outputIndex] = theta
                    outputIndex++
                }
            }
            row
        }
    }

    fun scriptSwing(index: Int, time: Double): SwingFrame {
        val table = swingTable[index]
        val frame = time * TABLE_HZ
        val left = min(floor(frame).toInt(), table.size - 2)
        val progress = frame - left
        val seam = 1.0 - smoothstep(segment(time, Script.seam[0], Script.seam[1]))
        return SwingFrame(
            theta = lerp(table[left], table[left + 1], progress) * seam,
            velocity = (table[left + 1] - table[left]) * TABLE_HZ * seam,
        )
    }

    fun softDegrees(radians: Double): Double {
        val degrees = radians * 180.0 / PI
        return Swing.maxDeg * tanh(degrees / Swing.maxDeg)
    }

    fun glintOf(x: Double, velocity: Double): Pair<Double, Double> {
        return mod(-x, PriceTagWelcomeTheme.PERIOD) / PriceTagWelcomeTheme.PERIOD to
            smoothstep(kotlin.math.abs(velocity) / Glint.fullSpeed)
    }

    fun frameAt(time: Double): Frame {
        val theta = DoubleArray(N)
        val freezeTime = DoubleArray(N)
        val meltTime = DoubleArray(N)
        val press = DoubleArray(N)
        for (index in 0 until N) {
            theta[index] = softDegrees(scriptSwing(index, time).theta)
            val frost = scriptFrost(index, time)
            freezeTime[index] = frost.freezeTime
            meltTime[index] = frost.meltTime
            press[index] = scriptPress(index, time)
        }
        return Frame(
            x = 0.0,
            velocity = 0.0,
            thetaDegrees = theta,
            freezeTime = freezeTime,
            meltTime = meltTime,
            press = press,
            clock = time,
            glint = 0.0,
            glintAlpha = 0.0,
        )
    }

    fun substepCount(delta: Double): Int {
        return max(1, ceil(delta * SIM_HZ).toInt())
    }
}
