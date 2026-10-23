package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Seconds, matching React Native's `LOOP_MS / 1000`. */
const val LOOP = 10.0

internal enum class CassetteDeckMode {
    Stop,
    Play,
    FastForward,
    Rewind,
    Pause,
}

private data class CassetteDeckSpring(val duration: Double, val dampingRatio: Double)

/** The deterministic transport, key springs, counter, and meters from the React Native model. */
internal class CassetteDeckModel(val reducedMotion: Boolean) {
    var tapePosition = TAPE_START
    var tapeSpeed = 0.0
    var mode = CassetteDeckMode.Stop
    var heldKey = -1
    val keyDepth = DoubleArray(KEY_COUNT)
    private val keyVelocity = DoubleArray(KEY_COUNT)
    var musicTime = 0.0
    var leftLevel = 0.0
    var rightLevel = 0.0
    var memory = false
    var ghostX = 0.0
    var ghostY = 0.0
    var ghostOpacity = 0.0
    var ghostTargetOpacity = 0.0
    var silent = false
    val events = mutableListOf<CassetteDeckHaptic>()
    var version = 0
    var moving = true

    fun pressKey(index: Int) {
        if (index !in 0 until KEY_COUNT) return
        heldKey = index
        val previous = mode
        when (index) {
            KEY_PLAY -> if (tapePosition < TAPE_END) mode = CassetteDeckMode.Play
            KEY_FAST_FORWARD -> {
                if (tapePosition < TAPE_END) mode = CassetteDeckMode.FastForward
                memory = false
            }
            KEY_REWIND -> {
                mode = CassetteDeckMode.Rewind
                memory = tapePosition > TAPE_START + 0.0005
            }
            KEY_STOP -> {
                mode = CassetteDeckMode.Stop
                memory = false
            }
            KEY_PAUSE -> {
                mode = when (mode) {
                    CassetteDeckMode.Play -> CassetteDeckMode.Pause
                    CassetteDeckMode.Pause -> CassetteDeckMode.Play
                    else -> mode
                }
            }
        }
        val latching = mode != previous &&
            (mode == CassetteDeckMode.Play || mode == CassetteDeckMode.FastForward || mode == CassetteDeckMode.Rewind)
        emit(if (latching) CassetteDeckHaptic.Latch else CassetteDeckHaptic.Key)
        version++
        moving = true
    }

    fun releaseKey() {
        heldKey = -1
        moving = true
    }

    fun advance(dtSeconds: Double) {
        val dt = min(dtSeconds, 0.05)
        var busy = false
        val target = when (mode) {
            CassetteDeckMode.Play -> TAPE_PLAY_SPEED
            CassetteDeckMode.FastForward -> TAPE_WIND_SPEED
            CassetteDeckMode.Rewind -> -TAPE_WIND_SPEED
            CassetteDeckMode.Stop,
            CassetteDeckMode.Pause,
            -> 0.0
        }
        val motor = if (reducedMotion) 1.0 else 1.0 - exp(-dt / TAPE_MOTOR_TIME)
        tapeSpeed += (target - tapeSpeed) * motor
        if (abs(tapeSpeed - target) < 1e-5) tapeSpeed = target
        tapePosition += tapeSpeed * dt

        if (memory && tapePosition <= TAPE_START) {
            tapePosition = TAPE_START
            tapeSpeed = 0.0
            mode = CassetteDeckMode.Stop
            memory = false
            emit(CassetteDeckHaptic.Memory)
            version++
        }
        if (tapePosition <= 0.02 && tapeSpeed < 0.0) {
            tapePosition = 0.02
            tapeSpeed = 0.0
            mode = CassetteDeckMode.Stop
            version++
        }
        if (tapePosition >= TAPE_END && tapeSpeed > 0.0) {
            tapePosition = TAPE_END
            tapeSpeed = 0.0
            mode = CassetteDeckMode.Stop
            version++
        }
        if (tapeSpeed != 0.0 || tapeSpeed != target) busy = true

        for (index in 0 until KEY_COUNT) {
            val goal = when {
                index == heldKey -> 1.0
                isLatched(index) -> TAPE_LATCH_DEPTH
                else -> 0.0
            }
            val spring = when {
                reducedMotion -> REDUCED_SPRING
                goal > keyDepth[index] -> DOWN_SPRING
                else -> UP_SPRING
            }
            val stepped = springStep(keyDepth[index], keyVelocity[index], goal, dt, spring)
            keyDepth[index] = stepped.first.coerceIn(0.0, 1.02)
            keyVelocity[index] = stepped.second
            if (stepped.second != 0.0) busy = true
        }

        val playing = mode == CassetteDeckMode.Play && abs(tapeSpeed - TAPE_PLAY_SPEED) < TAPE_PLAY_SPEED * 0.5
        if (playing) musicTime += dt
        val targetLeft = if (playing) level(musicTime, 0) else 0.0
        val targetRight = if (playing) level(musicTime, 1) else 0.0
        leftLevel = followLevel(leftLevel, targetLeft, dt)
        rightLevel = followLevel(rightLevel, targetRight, dt)
        if (leftLevel < 0.002 && !playing) leftLevel = 0.0
        if (rightLevel < 0.002 && !playing) rightLevel = 0.0
        if (leftLevel > 0.0 || rightLevel > 0.0) busy = true

        ghostOpacity += (ghostTargetOpacity - ghostOpacity) * min(1.0, dt / 0.08)
        if (abs(ghostOpacity - ghostTargetOpacity) < 0.01) ghostOpacity = ghostTargetOpacity
        moving = busy || heldKey >= 0 || ghostOpacity != ghostTargetOpacity
    }

    fun resetToRest() {
        mode = CassetteDeckMode.Stop
        heldKey = -1
        memory = false
        tapeSpeed = 0.0
        tapePosition = TAPE_START
        musicTime = 0.0
        version++
        moving = true
    }

    fun runScript(previousTime: Double, currentTime: Double) {
        for (press in DEMO_PRESSES) {
            val index = press[0].toInt()
            val down = press[1]
            val up = press[2]
            val centerX = CassetteDeckTheme.keyX(index) + CassetteDeckTheme.KEY_WIDTH / 2.0
            val centerY = CassetteDeckTheme.KEYS_Y + CassetteDeckTheme.KEY_TOP + 40.0
            if (crossed(previousTime, currentTime, down - 0.18)) {
                ghostX = centerX
                ghostY = centerY
                ghostTargetOpacity = 1.0
            }
            if (crossed(previousTime, currentTime, down)) pressKey(index)
            if (crossed(previousTime, currentTime, up)) {
                releaseKey()
                ghostTargetOpacity = 0.0
            }
        }
    }

    private fun isLatched(index: Int): Boolean {
        if (index == KEY_PLAY) return mode == CassetteDeckMode.Play || mode == CassetteDeckMode.Pause
        if (index == KEY_PAUSE) return mode == CassetteDeckMode.Pause
        if (index == KEY_FAST_FORWARD) return mode == CassetteDeckMode.FastForward
        if (index == KEY_REWIND) return mode == CassetteDeckMode.Rewind
        return false
    }

    private fun emit(haptic: CassetteDeckHaptic) {
        if (!silent) events += haptic
    }

    private fun followLevel(value: Double, target: Double, dt: Double): Double {
        val time = if (target > value) METER_ATTACK else METER_RELEASE
        return value + (target - value) * (1.0 - exp(-dt / time))
    }

    companion object {
        const val KEY_COUNT = 5
        const val KEY_REWIND = 0
        const val KEY_PLAY = 1
        const val KEY_FAST_FORWARD = 2
        const val KEY_STOP = 3
        const val KEY_PAUSE = 4
        const val TAPE_START = 0.18
        const val TAPE_END = 0.97
        const val TAPE_PLAY_SPEED = 0.01
        const val TAPE_WIND_SPEED = 0.12
        const val TAPE_MOTOR_TIME = 0.22
        const val TAPE_LENGTH = 13_200.0
        const val TAPE_LATCH_DEPTH = 0.78
        const val METER_SEGMENTS = 12
        private const val METER_ATTACK = 0.06
        private const val METER_RELEASE = 0.35
        private val DOWN_SPRING = CassetteDeckSpring(0.110, 0.9)
        private val UP_SPRING = CassetteDeckSpring(0.220, 0.55)
        private val REDUCED_SPRING = CassetteDeckSpring(0.160, 1.0)
        private val DEMO_PRESSES = arrayOf(
            doubleArrayOf(KEY_PLAY.toDouble(), 0.8, 1.0),
            doubleArrayOf(KEY_FAST_FORWARD.toDouble(), 4.0, 4.2),
            doubleArrayOf(KEY_REWIND.toDouble(), 5.3, 5.5),
        )
        private val PACK_K = CassetteDeckTheme.PACK_MAX.pow(2) - CassetteDeckTheme.PACK_MIN.pow(2)

        fun packRight(position: Double): Double {
            return sqrt(CassetteDeckTheme.PACK_MIN.pow(2) + position * PACK_K)
        }

        fun packLeft(position: Double): Double {
            return sqrt(CassetteDeckTheme.PACK_MIN.pow(2) + (1.0 - position) * PACK_K)
        }

        fun reelAngles(position: Double): Pair<Double, Double> {
            val right = 2.0 * TAPE_LENGTH * (packRight(position) - packRight(0.0)) / PACK_K
            val left = 2.0 * TAPE_LENGTH * (packLeft(0.0) - packLeft(position)) / PACK_K
            return left to right
        }

        fun counterAt(position: Double): Double {
            return (reelAngles(position).second - reelAngles(TAPE_START).second) / (PI * 2.0)
        }

        fun keyAt(x: Double, y: Double): Int {
            if (y < CassetteDeckTheme.KEYS_Y - 8.0 || y > CassetteDeckTheme.KEYS_Y + CassetteDeckTheme.KEYS_HEIGHT + 8.0) {
                return -1
            }
            for (index in 0 until KEY_COUNT) {
                val left = CassetteDeckTheme.keyX(index)
                if (x >= left - 4.0 && x <= left + CassetteDeckTheme.KEY_WIDTH + 4.0) return index
            }
            return -1
        }

        private fun crossed(previous: Double, current: Double, at: Double): Boolean {
            return previous < at && current >= at
        }

        private fun level(time: Double, channel: Int): Double {
            val beat = max(0.0, sin(time * PI * 2.0 * 1.9 + channel * 0.4)).pow(6)
            val phrase = 0.5 + 0.22 * sin(time * 0.9 + channel * 1.3) + 0.12 * sin(time * 2.7 + channel)
            val grain = 0.08 * sin(time * 23.0 + channel * 2.1) * sin(time * 7.3)
            return (phrase + 0.32 * beat + grain).coerceIn(0.0, 1.0)
        }

        private fun springStep(
            position: Double,
            velocity: Double,
            target: Double,
            dt: Double,
            spring: CassetteDeckSpring,
        ): Pair<Double, Double> {
            val omega = 2.0 * PI / spring.duration
            val stiffness = omega * omega
            val damping = 2.0 * spring.dampingRatio * omega
            val steps = max(1, ceil(dt / 0.004).toInt())
            val h = dt / steps
            var x = position
            var v = velocity
            repeat(steps) {
                v += (-stiffness * (x - target) - damping * v) * h
                x += v * h
            }
            if (abs(x - target) < 0.0008 && abs(v) < 0.01) return target to 0.0
            return x to v
        }
    }
}

/** Owns autoplay pause/resume state; both the demo and the finger drive the same model. */
internal class CassetteDeckSession(
    reducedMotion: Boolean,
    private val autoPlay: Boolean,
) {
    val model = CassetteDeckModel(reducedMotion)
    private var paused = !autoPlay
    private var touching = false
    private var quietTime = 0.0
    private var clock = 0.0

    fun advance(dtSeconds: Double): Boolean {
        val dt = min(dtSeconds, 0.05)
        var changed = model.moving
        if (autoPlay) {
            if (paused) {
                if (!touching) quietTime += dt
                if (quietTime >= RESUME_SECONDS) {
                    model.silent = true
                    model.resetToRest()
                    clock = 0.0
                    paused = false
                    changed = true
                }
            } else {
                model.silent = true
                val previous = clock
                val current = previous + dt
                model.runScript(previous, current)
                clock = if (current >= LOOP) current - LOOP else current
                changed = changed || model.moving
            }
        }
        if (model.moving) {
            model.advance(dt)
            changed = true
        }
        return changed
    }

    fun needsFrames(): Boolean = autoPlay || model.moving

    fun touchStart(stageX: Double, stageY: Double) {
        touching = true
        pauseDemo()
        model.pressKey(CassetteDeckModel.keyAt(stageX, stageY))
    }

    fun touchEnd() {
        touching = false
        quietTime = 0.0
        model.releaseKey()
    }

    fun accessibilityPress(index: Int) {
        pauseDemo()
        model.pressKey(index)
        model.releaseKey()
    }

    private fun pauseDemo() {
        quietTime = 0.0
        model.silent = false
        if (paused) return
        paused = true
        model.ghostTargetOpacity = 0.0
        model.releaseKey()
    }

    private companion object {
        const val RESUME_SECONDS = 2.0
    }
}
