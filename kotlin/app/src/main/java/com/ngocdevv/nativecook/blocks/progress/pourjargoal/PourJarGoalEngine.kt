package com.ngocdevv.nativecook.blocks.progress.pourjargoal

import com.ngocdevv.nativecook.blocks.progress.pourjargoal.PourJarGoalMotion as M
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

internal object PourJarGoalCue {
    const val ONSET = "onset"
    const val DETENT = "detent"
    const val IMPACT = "impact"
    const val SUCCESS = "success"
    const val LIMIT = "limit"
    const val UNDO = "undo"
}

/**
 * The RN block's `useLiquid` state machine on one thread: mode 0 plays the deterministic timeline, mode 1 is live (touch, pour, undo),
 * mode 2 a pinned time. Time is seconds; [frame] is called once per rendered frame.
 */
internal class PourJarGoalEngine(
    private val autoPlay: Boolean,
    private val reduced: Boolean,
    val vessel: PourJarGoalVessel,
    private val scaleX: Double,
    private val scaleY: Double,
    val min: Double,
    val max: Double,
    private val step: Double,
    defaultValue: Double,
    private val undoSeconds: Double = 4.0,
    private val viscosity: Double = M.VISCOSITY,
    private val startRate: Double = 40.0,
    private val maxRate: Double = 400.0,
    private val rampSeconds: Double = 1.5,
    private val ticks: DoubleArray = doubleArrayOf(0.25, 0.5, 0.75, 1.0),
) {
    var cue: (String) -> Unit = {}
    var sound: (String, Double, Double) -> Unit = { _, _, _ -> }

    private val range = max(1.0, max - min)
    private val initial = M.clamp(defaultValue, min, max)
    var base = initial
    var amount = initial
    var target = initial
    var displayAmount = initial
    var statAmount = initial
    var percentAmount = initial
    private val field = M.WaveField()
    val heights = DoubleArray(64)
    var mode = if (autoPlay && !reduced) 0 else 1
    var time = 0.0
    private var cycleStart = -1.0
    private var pinTime = -1.0
    var touching = 0
    var finger = vessel.width / 2
    var fingerY = vessel.volumeToY((initial - min) / range)
    private var secondFingerY = 0.0
    private var secondFinger = -1000.0
    var age = 10.0
    private var liveTime = 0.0
    var toast = 0.0
    private var toastAge = 100.0
    var delta = 0.0
    private var undoValue = initial
    var grip = 0.0
    var stream = 0.0
    var impact = 0.0
    var bubbleTime = 0.0
    private var dropOriginY = 0.0
    var dropX = vessel.width / 2
    var dropY = -100.0
    var dropRadius = 0.0
    private var dropAge = 100.0
    private var dropSpeed = 0.0
    private var pouring = false
    private var pourAge = 0.0
    private var accumulator = 0.0
    private var lastNotify = -10.0
    private var lastTick = floor(((initial - min) / range) * 4).toInt()
    private var lastStep = Math.round(initial / step)
    private var limited = false
    private var cancelled = false
    private var committed = initial
    private var returnFrom = initial
    private var userCommitTime = -100.0
    private var undoUntil = 0.0
    var clock = 0.0
    var accessibleValue = initial
        private set
    val hasUndo get() = clock < undoUntil
    var autoEnabled = false

    init {
        System.arraycopy(M.LOOP_FIELDS[0], 0, heights, 0, 64)
    }

    // --- touch -------------------------------------------------------------------------------------------------------------

    private fun begin() {
        if (mode != 1) {
            field.n = 64
            for (i in 0 until 64) {
                field.h[i] = heights[i]
                field.output[i] = heights[i]
                field.v[i] = 0.0
            }
        }
        mode = 1
        target = amount
        committed = base
        age = 0.0
        cancelled = false
        limited = false
        pinTime = -1.0
        toastAge = 100.0
        cue(PourJarGoalCue.ONSET)
    }

    private fun commit() {
        val previous = committed
        val saved = M.clamp(min + Math.round((amount - min) / step) * step, previous, max)
        userCommitTime = clock
        target = saved
        delta = saved - previous
        undoValue = previous
        touching = 0
        pouring = false
        age = 0.0
        toastAge = if (saved != previous) 0.0 else 100.0
        base = saved
        accessibleValue = saved
        if (saved - previous != 0.0) undoUntil = clock + undoSeconds
    }

    private fun cancel() {
        target = committed
        cancelled = true
        touching = 0
        pouring = false
        age = 0.0
        secondFinger = -1000.0
        sound("stop", 0.0, 0.6)
    }

    private fun updateFinger(x: Double, y: Double) {
        finger = M.clamp(x / scaleX, 0.0, vessel.width)
        fingerY = M.rubber(y / scaleY, 8.0, vessel.height - 8)
        val v = min + vessel.yToVolume(fingerY) * range
        target = M.rubber(v, committed, max)
    }

    /** Jar drag, coordinates local to the jar's view in points. */
    fun dragBegin(x: Double, y: Double) {
        begin()
        touching = 1
        updateFinger(x, y)
    }

    fun dragMove(x: Double, y: Double, secondX: Double?, secondY: Double?, jarWidthPx: Double) {
        if (cancelled) return
        if (secondX != null && secondY != null) {
            secondFinger = M.clamp(secondX / scaleX, 0.0, vessel.width)
            secondFingerY = secondY / scaleY
            updateFinger(x, y)
            return
        }
        secondFinger = -1000.0
        if (x < -24 || x > jarWidthPx + 24) {
            cancel()
            return
        }
        updateFinger(x, y)
    }

    fun dragEnd(velocityY: Double) {
        if (cancelled) return
        val v = -velocityY / scaleY
        if (!reduced) impulse(finger, v, vessel.width - 6)
        if (v > M.SKIP_THRESHOLD && !reduced) {
            dropOriginY = fingerY
            dropX = finger
            dropAge = 0.0
            dropSpeed = min(440.0, sqrt(4 * 1200 * max(0.0, min(40.0, fingerY - 10)))) * M.clamp(v / 2200, 0.5, 1.0)
        }
        secondFinger = -1000.0
        commit()
    }

    fun dragCancel() {
        if (!cancelled && touching == 1) cancel()
    }

    private fun impulse(x: Double, velocity: Double, width: Double) = M.impulse(field, x, velocity, width)

    fun pourBegin() {
        begin()
        touching = 2
        pouring = true
        pourAge = 0.0
        sound("pour", M.clamp((amount - min) / range), 0.6)
    }

    fun pourEnd() {
        val held = pourAge
        pouring = false
        if (held < 0.3) {
            sound("tail", M.clamp((amount - min) / range), 0.6)
            target = committed
            pourAge = -0.001
            touching = 3
        } else {
            sound("stop", M.clamp((amount - min) / range), 0.6)
            commit()
        }
    }

    fun pourCancel() {
        if (touching == 2) cancel()
    }

    fun undo() {
        if (undoUntil < clock || undoSeconds == 0.0) return
        val previous = undoValue
        mode = 1
        userCommitTime = -100.0
        undoUntil = 0.0
        target = previous
        toastAge = 100.0
        age = 0.0
        base = previous
        accessibleValue = previous
        cue(PourJarGoalCue.UNDO)
    }

    /** Accessibility increment / decrement / activate. */
    fun adjust(direction: Int, confirm: Boolean) {
        mode = 1
        pinTime = -1.0
        age = 0.0
        committed = base
        val next = M.clamp(target + direction * step, base, max)
        target = next
        if (confirm) {
            val d = next - base
            base = next
            delta = d
            undoValue = next - d
            toastAge = 0.0
            userCommitTime = clock
            accessibleValue = next
            if (d != 0.0) undoUntil = clock + undoSeconds
        } else {
            accessibleValue = next
            cue(PourJarGoalCue.DETENT)
        }
    }

    fun pin(t: Double) {
        pinTime = M.clamp(t, 0.0, M.LOOP)
        mode = 2
    }

    // --- frame -------------------------------------------------------------------------------------------------------------

    fun frame(now: Double, dtIn: Double) {
        clock = now
        val dt = min(dtIn, 1.0 / 30)
        if (mode != 1) {
            if (cycleStart < 0) cycleStart = now - time
            val t = if (reduced) 0.0 else if (mode == 2) pinTime else (now - cycleStart) % M.LOOP
            val k = M.timeline(t)
            time = t
            val b = base
            val add = min(step * 10, max - b)
            amount = b + add * k.lift
            displayAmount = b + add * M.timeline(t - 0.02).lift
            statAmount = b + add * M.timeline(t - 0.04).lift
            percentAmount = b + add * M.timeline(t - 0.08).lift
            val src = if (reduced) M.LOOP_FIELDS[0] else M.LOOP_FIELDS[min(960, Math.round(k.t * 120).toInt())]
            System.arraycopy(src, 0, heights, 0, 64)
            grip = k.grip
            val userAge = if (mode == 2) 100.0 else now - userCommitTime
            toast = if (userAge < undoSeconds) M.clamp(M.springAt(userAge - 0.35)) else k.toast
            if (userAge >= undoSeconds) delta = add
            impact = k.impact
            bubbleTime = k.bubbleTime
            return
        }
        if (touching == 3) {
            val previous = pourAge
            pourAge = previous + dt
            val t = pourAge
            val initialTarget = min(max, committed + step * 10)
            target = committed + (initialTarget - committed) * M.segment(t, 0.3, 1.0)
            finger = vessel.width / 2
            dropX = vessel.width / 2
            fingerY = vessel.volumeToY((amount - min) / range)
            dropY = 10 + (fingerY - 10) * M.clamp((t - 0.06) / 0.24).pow(2)
            dropRadius = if (t > 0.06 && t < 0.3 && !reduced) 4.0 else 0.0
            if (t >= 0.3 && previous < 0.3) {
                if (!reduced) impulse(vessel.width / 2, 620.0, vessel.width - 6)
                impact = 1.0
                cue(PourJarGoalCue.IMPACT)
                sound("land", M.clamp((amount - min) / range), 0.6)
            }
            if (t >= (if (reduced) 0.3 else 1.1)) {
                amount = initialTarget
                commit()
            }
        }
        val isTouched = touching > 0
        if (isTouched) age = 0.0 else age += dt
        liveTime += dt
        if (pouring) {
            pourAge += dt
            if (pourAge > 0.3) {
                val rate = startRate + (maxRate - startRate) * M.segment(pourAge - 0.3, 0.0, max(rampSeconds, 0.01))
                target = M.clamp(target + rate * dt, min, max)
                stream = if (reduced) 0.0 else 3 + 6 * M.clamp(rate / max(1.0, maxRate))
            }
        } else stream *= exp(-dt * 24)
        val desired = target
        amount += (desired - amount) * (1 - exp(-dt / (if (reduced) 0.07 else M.LAG)))
        displayAmount = if (reduced) amount else displayAmount + (amount - displayAmount) * (1 - exp(-dt / 0.02))
        statAmount += (amount - statAmount) * (1 - exp(-dt / 0.04))
        percentAmount += (amount - percentAmount) * (1 - exp(-dt / 0.08))
        grip += ((if (isTouched && touching == 1) 1.0 else 0.0) - grip) * (1 - exp(-dt * 32))
        accumulator = min(1.0 / 15, accumulator + dt)
        if (!reduced) {
            val centerY = vessel.volumeToY((amount - min) / range)
            val mound = if (touching == 1) M.clamp(centerY - fingerY, -48.0, 48.0) else 0.0
            val mound2 = if (touching == 1 && secondFinger > -100) M.clamp(centerY - secondFingerY, -48.0, 48.0) else 0.0
            val row = M.clamp(floor((centerY / vessel.height) * 128), 0.0, 127.0).toInt()
            val left = vessel.left[row]
            val surfaceWidth = max(8.0, vessel.right[row] - left - 6)
            val columns = Math.round(32 + M.clamp(surfaceWidth / max(1.0, vessel.width - 6)) * 32).toInt()
            var steps = floor(accumulator * 120).toInt()
            while (steps-- > 0) {
                M.stepField(field, 1.0 / 120, surfaceWidth, viscosity, finger - left, mound, secondFinger - left, columns, mound2)
            }
            accumulator %= (1.0 / 120)
            System.arraycopy(field.output, 0, heights, 0, 64)
            if (field.energy > 0.5 || isTouched || pouring) bubbleTime += dt
        } else System.arraycopy(M.LOOP_FIELDS[0], 0, heights, 0, 64)
        toastAge += dt
        val ta = toastAge
        val successTime = if (reduced) 0.01 else 0.35
        if (ta >= successTime && ta - dt < successTime && delta != 0.0) cue(PourJarGoalCue.SUCCESS)
        toast = M.clamp(if (reduced) ta / 0.15 else M.springAt(ta - 0.35)) *
            (1 - M.segment(ta, if (undoSeconds != 0.0) undoSeconds else 2.2, (if (undoSeconds != 0.0) undoSeconds else 2.2) + 0.34))
        dropAge += dt
        val da = dropAge
        if (touching == 3) {
            // the tap path above owns the drop
        } else if (da < 0.7 && !reduced) {
            val y = dropOriginY - dropSpeed * da + 1200 * da * da
            val col = M.clamp(Math.round((dropX / vessel.width) * 63).toDouble(), 0.0, 63.0).toInt()
            val surface = vessel.volumeToY((amount - min) / range) - heights[col]
            dropY = M.clamp(y, 10.0, vessel.height - 10)
            dropRadius = if (y < surface) 4.0 else 0.0
            if (da > 0.05 && y >= surface && dropRadius == 0.0 && dropSpeed > 0) {
                impulse(dropX, 240.0, vessel.width - 6)
                impact = 1.0
                dropSpeed = 0.0
                sound("land", M.clamp((amount - min) / range), 0.6)
            }
        } else dropRadius = 0.0
        impact *= exp(-dt * 9)
        if ((isTouched || pouring) && liveTime - lastNotify >= M.CALLBACK_INTERVAL) {
            lastNotify = liveTime
            val snapped = M.clamp(min + Math.round((amount - min) / step) * step, min, max)
            if (pouring) sound("flow", M.clamp((amount - min) / range), M.clamp(stream / 9) * 0.6)
            if (Math.round(snapped / step) != lastStep) {
                lastStep = Math.round(snapped / step)
                cue(PourJarGoalCue.DETENT)
            }
            val fraction = (snapped - min) / range
            for (i in ticks.indices) {
                if (fraction >= ticks[i] && lastTick < i) cue(PourJarGoalCue.DETENT)
            }
            var newTick = -1
            for (i in ticks.indices) if (fraction >= ticks[i]) newTick = i
            lastTick = newTick
            if (desired >= max && !limited) {
                limited = true
                cue(PourJarGoalCue.LIMIT)
            }
        }
        if (!isTouched && !pouring && age > M.RESUME_DELAY && autoPlay && !reduced) {
            if (age - dt <= M.RESUME_DELAY) returnFrom = amount
            val r = M.segment(age, M.RESUME_DELAY, M.RESUME_DELAY + M.RETURN_SECONDS)
            amount = returnFrom + (base - returnFrom) * r
            if (r >= 1) {
                mode = 0
                time = 0.0
                cycleStart = -1.0
                toast = 0.0
                bubbleTime = 0.0
            }
        }
    }

    /** Called on lifecycle resume: the timeline restarts its phase. */
    fun resume() {
        cycleStart = -1.0
    }

}
