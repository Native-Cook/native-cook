package com.ngocdevv.nativecook.blocks.pickers.skytimescrub

import com.ngocdevv.nativecook.blocks.pickers.skytimescrub.SkyTimeScrubMotion as M
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal object SkyTimeScrubCue {
    const val ONSET = 0
    const val STEP = 1
    const val HOUR = 2
    const val LANDING = 3
    const val LOWER_LIMB = 4
    const val UPPER_LIMB = 5
}

/** A damped spring that can start with a velocity and report when it comes to rest (Reanimated's `withSpring`). */
internal class SkyTimeScrubSpringAnim(var value: Double) {
    var target = value
    var velocity = 0.0
    var active = false
    var spring = M.SNAP
    var restDisplacement = 0.01
    var restSpeed = 2.0
    var onDone: (() -> Unit)? = null

    fun start(to: Double, cfg: SkyTimeScrubSpring, v: Double = 0.0, displacement: Double = 0.01, speed: Double = 2.0, done: (() -> Unit)? = null) {
        target = to
        spring = cfg
        velocity = v
        active = true
        restDisplacement = displacement
        restSpeed = speed
        onDone = done
    }

    fun cancel() {
        active = false
        onDone = null
    }

    fun set(v: Double) {
        active = false
        onDone = null
        velocity = 0.0
        value = v
        target = v
    }

    /** Returns true while it moves. */
    fun step(dt: Double): Boolean {
        if (!active) return false
        val n = max(1, Math.ceil(dt / (1.0 / 240)).toInt())
        val h = dt / n
        var x = value
        var v = velocity
        for (i in 0 until n) {
            val nv = v + (-spring.stiffness * (x - target) - spring.damping * v) / spring.mass * h
            x += nv * h
            v = nv
        }
        value = x
        velocity = v
        if (abs(v) < restSpeed && abs(x - target) < restDisplacement) {
            value = target
            velocity = 0.0
            active = false
            val d = onDone
            onDone = null
            d?.invoke()
        }
        return true
    }
}

/**
 * The block's state machine (RN `SkyTimeScrubber`'s frame callback, gestures and reactions): autoplay loop, press and scrub, coast with
 * detent snap, return to now, exposure iris, schedule wake, the hint's fold, and the haptic / sound cues. Time is in seconds; angles in
 * the units the RN block uses; positions in dp.
 */
internal class SkyTimeScrubEngine(
    val autoPlay: Boolean,
    val reduce: Boolean,
    val day: SkyTimeScrubDay,
    val rest: Double,
    val s: Double,
    val projection: Double,
    val detents: DoubleArray,
    val timeline: SkyTimeScrubTimeline,
    private val adapt: Boolean,
    private val expoUp: Double,
    private val expoDown: Double,
) {
    var cue: (kind: Int, sfx: String) -> Unit = { _, _ -> }
    /** Called when the UI that depends on chip visibility / selection text needs a refresh (rare). */
    var onSelection: ((String) -> Unit)? = null

    // state shared with the renderer
    val minutesAnim = SkyTimeScrubSpringAnim(rest)
    val minutes: Double get() = minutesAnim.value
    var ev = M.targetEV(rest, day)
    var held = 0.0
    var wake = 0.0
    var hintOn = 1.0
    val tickAt = doubleArrayOf(-100.0, -100.0, -100.0)
    var clock = 0.0
    var chipVisible = false
    var hintDismissed = false
    var scheduleOpen = false
    var down = false
    var sunGrab = false

    private var mode = if (autoPlay) 0 else 1 // 0 autoplay, 1 touch/idle, 2 coast, 3 snap, 4 returning
    private var loopTime = 0.0
    private var pinned = -1.0
    private var lastTouch = -1.0
    private var prevMinutes = -1e9

    private var tDown = false
    private var tActive = false
    private var tAt = 0.0
    private var tBase = 0.0
    private var tLastCue = -1.0
    private var tOffset = 0.0

    private var coastFrom = 0.0
    private var coastVelocity = 0.0
    private var coastAt = 0.0
    private var returnFrom = 0.0
    private var returnAt = 0.0

    // reveal: a 120 ms ease-in-out timing
    private var reveal = 0.0
    private var revealFrom = 0.0
    private var revealTo = 0.0
    private var revealT = 1.0

    private val rowT = M.rowTimes(day)

    // reaction memory
    private var stepPrev = Int.MIN_VALUE
    private var limbPrev = -1
    private var firstFrame = true

    // control press springs (sun, city, grid)
    val press = Array(3) { SkyTimeScrubSpringAnim(1.0) }

    val revealValue: Double get() = reveal
    val pinnedTime: Double get() = pinned

    fun pin(t: Double) {
        minutesAnim.cancel()
        pinned = max(0.0, t)
    }

    fun openSchedule(open: Boolean) {
        if (scheduleOpen == open) return
        scheduleOpen = open
        revealFrom = reveal
        revealTo = if (open) 1.0 else 0.0
        revealT = 0.0
    }

    // ---- frame ----------------------------------------------------------------------------------------------------------------

    fun frame(nowT: Double, rawDt: Double) {
        val dt = min(max(rawDt, 0.0), M.MAX_FRAME_DELTA)
        clock = nowT
        // reveal timing (default easing: in-out quad)
        if (revealT < 1.0) {
            revealT = min(1.0, revealT + dt / (SkyTimeScrubTheme.PRESS_MS / 1000.0))
            val e = if (revealT < 0.5) 2 * revealT * revealT else 1 - (-2 * revealT + 2) * (-2 * revealT + 2) / 2
            reveal = revealFrom + (revealTo - revealFrom) * e
        }
        for (p in press) p.step(dt)
        minutesAnim.step(dt)
        if (pinned >= 0) {
            val pose = M.sampleTimeline(if (reduce) 0.0 else pinned, timeline)
            minutesAnim.set(pose.minutes)
            ev = pose.ev
            held = pose.held
            wake = pose.wake
            hintOn = if (reduce) 1.0 else M.hintAt(pinned)
            tickAt[0] = nowT - pose.tick0
            tickAt[1] = nowT - pose.tick1
            tickAt[2] = nowT - pose.tick2
            prevMinutes = -1e9
            updateVisibility()
            return
        }
        if (mode == 0 && autoPlay && !reduce) {
            loopTime = (loopTime + dt) % M.LOOP
            val pose = M.sampleTimeline(loopTime, timeline)
            minutesAnim.set(pose.minutes)
            ev = pose.ev
            held = pose.held
            wake = pose.wake
            // follows the film, but never jumps up: after a touch the loop restarts and the hint unfolds at its own pace
            hintOn = min(M.hintAt(loopTime), hintOn + dt / M.HINT_ARRIVE)
            tickAt[0] = nowT - pose.tick0
            tickAt[1] = nowT - pose.tick1
            tickAt[2] = nowT - pose.tick2
            prevMinutes = pose.minutes
            updateVisibility()
            return
        }
        if (tDown) {
            if (!tActive && nowT - tAt >= M.LONG_PRESS) {
                tActive = true
                tLastCue = nowT
                cue(SkyTimeScrubCue.ONSET, "")
            }
            val target = if (tActive || nowT - tAt >= M.LONG_PRESS) 1.0 else 0.0
            held = if (reduce) target else M.mix(held, target, 1 - exp(-dt / M.HOLD_RISE))
        } else {
            held = if (reduce) 0.0 else M.mix(held, 0.0, 1 - exp(-dt / M.HOLD_FALL))
        }
        if (mode == 2) {
            val elapsed = max(0.0, nowT - coastAt)
            val speed = coastVelocity * exp(-M.DECAY * elapsed)
            val m = coastFrom + (coastVelocity / M.DECAY) * (1 - exp(-M.DECAY * elapsed))
            minutesAnim.set(m)
            val detent = M.nearestDetent(m, detents)
            if (abs(speed) < M.CAPTURE_SPEED && detent.distance < M.DETENT_DISTANCE) {
                mode = 3
                cue(SkyTimeScrubCue.LANDING, "clack")
                minutesAnim.start(detent.value, M.DETENT, speed) { mode = 1 }
            } else if (abs(speed) < M.STOP_SPEED) {
                mode = 1
            }
        }
        if (!tDown && lastTouch >= 0 && nowT - lastTouch >= M.RESUME_AFTER && mode != 4) {
            minutesAnim.cancel()
            returnFrom = minutes
            returnAt = nowT
            mode = 4
        }
        if (mode == 4) {
            val p = M.clamp((nowT - returnAt) / M.RETURN_DURATION)
            val target = rest + Math.round((returnFrom - rest) / 1440.0) * 1440.0
            minutesAnim.set(M.mix(returnFrom, target, if (reduce) 1.0 else M.smooth(p)))
            if (p >= 1 || reduce) {
                minutesAnim.set(rest)
                ev = M.targetEV(rest, day)
                mode = if (autoPlay && !reduce) 0 else 1
                loopTime = 0.0
                lastTouch = -1.0
            }
        }
        val cur = minutes
        val target = M.targetEV(cur, day)
        val tau = if (target < ev) expoUp else expoDown
        ev = if (reduce || !adapt) target else M.clamp(M.mix(ev, target, 1 - exp(-dt / max(tau, 0.001))), target - 0.8, target + 1.2)
        // the schedule rows wake while a scrub session lasts and sleep again when the sky returns to now
        val session = held > 0.5 || (lastTouch >= 0 && mode != 4)
        wake = if (reduce) (if (session) M.ROWS_MAX else 0.0)
        else M.clamp(wake + (if (session) dt * M.ROWS_RISE else -dt * M.ROWS_FALL), 0.0, M.ROWS_MAX)
        // the hint stays folded away until the sky is back at now and the touch is over
        val away = held > 0.02 || lastTouch >= 0
        hintOn = if (reduce) (if (away) 0.0 else 1.0) else M.clamp(hintOn + (if (away) -dt / M.HINT_LEAVE else dt / M.HINT_ARRIVE))
        // a row ticks the instant the scrub crosses its time (any day), in step with the detent cue
        val prev = prevMinutes
        if (prev > -1e8) {
            for (r in 0 until 3) {
                if (floor((prev - rowT[r]) / 1440) != floor((cur - rowT[r]) / 1440)) {
                    tickAt[r] = nowT
                    if (tActive && nowT - tLastCue >= M.HAPTIC_GAP) {
                        tLastCue = nowT
                        cue(SkyTimeScrubCue.STEP, "")
                    }
                }
            }
        }
        prevMinutes = cur
        updateVisibility()
        reactions()
    }

    private fun updateVisibility() {
        chipVisible = held > M.HIDDEN_THRESHOLD || M.circularDiff(M.wrapMinutes(minutes), rest) > 1
    }

    /** The two `useAnimatedReaction`s: a step cue every 15 minutes, the horizon cues at the sun's limbs. */
    private fun reactions() {
        val step = floor(M.wrapMinutes(minutes) / M.STEP).toInt()
        val sun = M.solarAt(minutes, day)
        val h = M.sunHeight(sun.elevation, day, projection)
        val semi = M.sunSemiHeight(sun.elevation)
        val limb = if (h - semi > 0) 2 else if (h + semi < 0) 0 else 1
        if (firstFrame) {
            firstFrame = false
            stepPrev = step
            limbPrev = limb
            return
        }
        if (step != stepPrev) {
            val driven = tActive || mode == 2
            if (mode != 0 && pinned < 0 && driven && clock - tLastCue >= M.HAPTIC_GAP) {
                tLastCue = clock
                val night = sun.elevation < 0
                cue(if (step % 4 == 0) SkyTimeScrubCue.HOUR else SkyTimeScrubCue.STEP, if (night) "tickNight" else "tick")
            }
            stepPrev = step
        }
        if (limb != limbPrev) {
            if (mode != 0 && pinned < 0) {
                val lower = (limb == 1 && limbPrev == 2) || (limb == 2 && limbPrev == 1)
                if (lower) cue(SkyTimeScrubCue.LOWER_LIMB, "swell") else cue(SkyTimeScrubCue.UPPER_LIMB, "")
            }
            limbPrev = limb
        }
    }

    // ---- gestures -------------------------------------------------------------------------------------------------------------

    /** The ruler pan: a long press or a slide scrubs. [x] is the pointer's x (dp). */
    fun panBegin() {
        minutesAnim.cancel()
        pinned = -1.0
        mode = 1
        lastTouch = clock
        tDown = true; tActive = false; tAt = clock; tBase = minutes; tLastCue = -1.0; tOffset = 0.0
        down = true
        sunGrab = false
    }

    fun panUpdate(translationX: Double) {
        val moved = abs(translationX / s) > M.SLOP
        if (!tActive && moved) {
            tActive = true
            tLastCue = clock
            cue(SkyTimeScrubCue.ONSET, "")
        }
        if (tActive || moved) minutesAnim.set(tBase - (translationX / s) * M.MINUTES_PER_POINT)
        lastTouch = clock
    }

    fun panEnd(velocityX: Double) {
        if (!tActive) return
        val speed = M.clamp((-velocityX / s) * M.MINUTES_PER_POINT, -M.MAX_SPEED, M.MAX_SPEED)
        release(speed)
    }

    fun panFinalize() {
        tDown = false
        tActive = false
        down = false
        lastTouch = clock
    }

    fun sunBegin(absX: Double) {
        minutesAnim.cancel()
        pinned = -1.0
        mode = 1
        lastTouch = clock
        val sunX = M.solarAt(minutes, day).x * s
        tDown = true; tActive = true; tAt = clock; tBase = minutes; tLastCue = clock; tOffset = sunX - absX
        down = true
        sunGrab = true
        cue(SkyTimeScrubCue.ONSET, "")
    }

    fun sunUpdate(absX: Double) {
        val d = ((absX + tOffset) / s - 86) / 230
        val base = M.wrapMinutes(tBase)
        minutesAnim.set(tBase - base + M.minutesFromDayParam(d, day))
        lastTouch = clock
    }

    fun sunEnd(velocityX: Double) {
        val cur = minutes
        val d = (M.solarAt(cur, day).x - 86) / 230
        val speed = M.clamp(((velocityX / s) * M.minutesPerDay(d, day)) / 230, -M.MAX_SPEED, M.MAX_SPEED)
        release(speed)
    }

    fun sunFinalize() {
        tDown = false
        tActive = false
        down = false
        sunGrab = false
        lastTouch = clock
    }

    private fun release(speed: Double) {
        if (reduce) {
            val d = M.nearestDetent(minutes, detents)
            if (d.distance < M.DETENT_DISTANCE) minutesAnim.set(d.value)
        } else {
            coastFrom = minutes
            coastVelocity = speed
            coastAt = clock
            mode = 2
        }
    }

    fun travelTo(rawTarget: Double) {
        val target = rawTarget + Math.round((minutes - rawTarget) / 1440.0) * 1440.0
        minutesAnim.cancel()
        pinned = -1.0
        mode = 3
        lastTouch = clock
        cue(SkyTimeScrubCue.ONSET, "")
        if (reduce) {
            minutesAnim.set(target)
            mode = 1
            return
        }
        minutesAnim.start(target, M.GLIDE) { mode = 1 }
    }

    fun pressControl(i: Int, pressed: Boolean) {
        press[i].start(if (pressed && !reduce) SkyTimeScrubTheme.PRESSED else 1.0, M.SNAP, press[i].velocity, 0.0005, 0.05)
    }

    /** Accessibility: step the ruler by 15 minutes. */
    fun nudge(direction: Int) = travelTo(minutes + direction * M.STEP)

    fun selectionLabel(): String = M.timeLabel(minutes, day, rest)
}

