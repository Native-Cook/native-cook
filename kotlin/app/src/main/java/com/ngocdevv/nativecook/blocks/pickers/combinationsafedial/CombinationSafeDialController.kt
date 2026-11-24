package com.ngocdevv.nativecook.blocks.pickers.combinationsafedial

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

private val BEZIER_IN_OUT = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

/** Reanimated's easings that this block uses. */
private val LINEAR: (Double) -> Double = { it }
private val EASE_IN_OUT: (Double) -> Double = { BEZIER_IN_OUT.transform(it.toFloat()).toDouble() }

/** withTiming's default, Easing.inOut(Easing.quad). */
private val QUAD_IN_OUT: (Double) -> Double = { x -> if (x < 0.5) 2 * x * x else 1 - 2 * (1 - x) * (1 - x) }

/**
 * One animated number as a closed form of the frame time (ms): still, a chain of eased legs (withTiming /
 * withSequence), or a spring (withSpring, mass 1). Reading it never advances anything, so the Compose layers and the
 * stage agree for a frame.
 */
private class SafeTrack(initial: Double, stiffness: Double = 170.0, damping: Double = 22.0) {
    private val z = damping / 2
    private val w = sqrt(stiffness - z * z)
    private var still = initial
    private var sprung = false
    private var springTo = initial
    private var springFrom = initial
    private var springV = 0.0
    private var springStart = 0.0
    private var legs = 0
    private val from = DoubleArray(MAX_LEGS)
    private val to = DoubleArray(MAX_LEGS)
    private val start = DoubleArray(MAX_LEGS)
    private val ms = DoubleArray(MAX_LEGS)
    private val ease = arrayOfNulls<(Double) -> Double>(MAX_LEGS)

    fun value(now: Double): Double {
        if (sprung) {
            val s = max(0.0, (now - springStart) / 1000)
            val d = springFrom - springTo
            return springTo + exp(-z * s) * (d * cos(w * s) + (springV + z * d) / w * sin(w * s))
        }
        if (legs == 0) return still
        if (now <= start[0]) return from[0]
        for (i in 0 until legs) {
            if (now < start[i]) return to[i - 1]
            if (now < start[i] + ms[i]) {
                val k = (now - start[i]) / ms[i]
                return from[i] + (to[i] - from[i]) * ease[i]!!(k)
            }
        }
        return to[legs - 1]
    }

    /** Units a second, so a spring that replaces a spring keeps its speed. */
    private fun velocity(now: Double): Double {
        if (!sprung) return 0.0
        val s = max(0.0, (now - springStart) / 1000)
        val d = springFrom - springTo
        val b = (springV + z * d) / w
        return exp(-z * s) * ((b * w - z * d) * cos(w * s) - (d * w + z * b) * sin(w * s))
    }

    fun set(v: Double) {
        sprung = false
        legs = 0
        still = v
    }

    /** cancelAnimation: it stays where it is. */
    fun cancel(now: Double) = set(value(now))

    fun spring(now: Double, target: Double, velocity: Double? = null) {
        val v = velocity ?: velocity(now)
        springFrom = value(now)
        springTo = target
        springV = v
        springStart = now
        sprung = true
        legs = 0
    }

    fun timing(now: Double, target: Double, duration: Double, easing: (Double) -> Double) {
        if (duration <= 0) {
            set(target)
            return
        }
        val current = value(now)
        sprung = false
        legs = 1
        from[0] = current
        to[0] = target
        start[0] = now
        ms[0] = duration
        ease[0] = easing
    }

    /** withSequence of timings, each from where the last one ends. */
    fun sequence(now: Double, targets: DoubleArray, durations: DoubleArray, easing: (Double) -> Double) {
        var at = now
        var v = value(now)
        sprung = false
        legs = targets.size
        for (i in targets.indices) {
            from[i] = v
            to[i] = targets[i]
            start[i] = at
            ms[i] = durations[i]
            ease[i] = easing
            v = targets[i]
            at += durations[i]
        }
    }

    private companion object {
        const val MAX_LEGS = 5
    }
}

/**
 * The block's state for one frame time (CombinationSafeDial.tsx's frame callback, the dial's landing spring, the
 * entries, the unlock timeline and the wrong code's shake). Main thread only. `frame(now)` may be called several times
 * for one display frame: it advances once.
 */
internal class CombinationSafeDialController(autoPlay: Boolean, private val reduced: Boolean, code: IntArray) {
    private val film = !reduced && autoPlay
    private val c0 = code[0]
    private val c1 = code[1]
    private val c2 = code[2]

    // reduced motion rests on the film's result: the right numbers in, the door open
    private val initialDial = if (reduced) (if (c2 == 0) 0.0 else (c2 - SafeMotion.DIAL_N).toDouble()) else 0.0
    private val dial = SafeTrack(initialDial, DIAL_K, DIAL_C)
    private val unlock = SafeTrack(if (reduced) SafeMotion.UNLOCK else 0.0)
    private val shake = SafeTrack(0.0)
    private val wrong = SafeTrack(0.0)

    private var mode = if (film) MODE_AUTO else MODE_MANUAL
    private var loopStart = -1.0
    private var now = -1.0
    private var readyAt = -1.0
    private var lastTouch = -1.0
    private var returnAt = -1.0
    private var dragging = false
    private var lastAngle = 0.0
    private var centreX = 0.0
    private var centreY = 0.0

    /** The number a released dial is landing on (NaN: none), and whether the finger really turned it. */
    private var landing = Double.NaN
    private var turned = false
    private var clearAt = -1.0
    private var lastNumber = SafeMotion.dialNumber(initialDial)

    /** The film's time (s, 0..LOOP). */
    var t = 0.0
        private set

    /** How many numbers are in (0..3), and the three entries. */
    var count = if (reduced) 3 else 0
        private set
    val entries = if (reduced) intArrayOf(c0, c1, c2) else intArrayOf(0, 0, 0)

    /** True while a finger may lock the vault again. */
    var canLock = reduced
        private set

    var dialNow = initialDial
        private set
    var unlockNow = if (reduced) SafeMotion.UNLOCK else 0.0
        private set
    var openNow = SafeMotion.doorAt(unlockNow)
        private set
    var shakeNow = 0.0
        private set
    var wrongNow = 0.0
        private set
    var shown = 0.0
        private set
    val filming: Boolean get() = mode == MODE_AUTO

    /** Haptics and the unlock callback, as counters the composable turns into calls. */
    var ticks = 0
        private set
    var rights = 0
        private set
    var wrongs = 0
        private set

    fun markReady() {
        if (readyAt < 0) readyAt = max(now, 0.0)
    }

    fun frame(nowMs: Double) {
        if (nowMs > now) {
            val gap = if (now < 0) 0.0 else nowMs - now
            now = nowMs
            step(gap)
        }
        dialNow = dial.value(now)
        unlockNow = unlock.value(now)
        openNow = SafeMotion.doorAt(unlockNow)
        shakeNow = shake.value(now)
        wrongNow = wrong.value(now)
        shown = if (readyAt < 0) 0.0 else QUAD_IN_OUT(SafeMotion.clamp01((now - readyAt) / FADE_MS))
        // a tick as a finger's dial passes each number (the film stays silent)
        val n = SafeMotion.dialNumber(dialNow)
        if (n != lastNumber) {
            lastNumber = n
            if (!reduced && mode == MODE_MANUAL) ticks++
        }
    }

    private fun step(gap: Double) {
        if (mode == MODE_AUTO) {
            if (!film) {
                mode = MODE_MANUAL
                return
            }
            if (readyAt < 0) {
                t = 0.0
                loopStart = -1.0
                return
            }
            if (loopStart < 0) {
                loopStart = now
            } else if (gap > STALL_MS) {
                // the thread was held up: the film picks up where it was instead of skipping what the stall covered
                loopStart += gap - FRAME_MS
            }
            var el = (now - loopStart) / 1000
            if (el >= SafeMotion.LOOP) {
                loopStart += SafeMotion.LOOP * 1000
                el -= SafeMotion.LOOP
            }
            t = el
            dial.set(SafeMotion.filmDial(el, c0, c1, c2))
            unlock.set(SafeMotion.filmUnlock(el))
            val n = SafeMotion.filmEntered(el)
            if (count != n) {
                entries[0] = c0
                entries[1] = c1
                entries[2] = c2
                count = n
            }
            return
        }
        // a released dial has landed on its number
        val target = landing
        if (!target.isNaN() && !dragging && abs(dial.value(now) - target) < SETTLED) {
            landing = Double.NaN
            if (turned) {
                turned = false
                commit(SafeMotion.dialNumber(target))
            }
        }
        if (clearAt >= 0 && now >= clearAt) clearEntries()
        if (mode == MODE_RETURN) {
            if (now - returnAt >= RETURN_MS) {
                dial.set(0.0)
                t = 0.0
                loopStart = -1.0
                mode = MODE_AUTO
            }
            return
        }
        if (lastTouch < 0 || dragging || !landing.isNaN()) {
            lastTouch = now
            return
        }
        if (!film || now - lastTouch < IDLE_MS) return
        // 2 s after the last touch: the door shuts, the entries clear and the dial goes home by the short way
        mode = MODE_RETURN
        returnAt = now
        canLock = false
        clearEntries()
        val home = SafeMotion.roundJs(dial.value(now) / SafeMotion.DIAL_N) * SafeMotion.DIAL_N
        dial.timing(now, home, 900.0, EASE_IN_OUT)
        unlock.timing(now, 0.0, if (unlock.value(now) > 0) RETURN_MS - 80 else 1.0, LINEAR)
    }

    private fun clearEntries() {
        count = 0
        wrong.timing(now, 0.0, 180.0, QUAD_IN_OUT)
        clearAt = -1.0
    }

    /** A number is in: the third one decides. */
    private fun commit(n: Int) {
        val i = count
        if (i >= 3 || canLock || clearAt >= 0) return
        entries[i] = n
        count = i + 1
        if (i < 2) return
        val right = entries[0] == c0 && entries[1] == c1 && n == c2
        if (right) {
            canLock = true
            if (reduced) unlock.set(SafeMotion.UNLOCK) else unlock.timing(now, SafeMotion.UNLOCK, SafeMotion.UNLOCK * 1000, LINEAR)
            rights++
            return
        }
        wrongs++
        wrong.timing(now, 1.0, 120.0, QUAD_IN_OUT)
        clearAt = now + WRONG_HOLD_MS
        if (!reduced) {
            shake.sequence(
                now,
                doubleArrayOf(SHAKE_M, -SHAKE_M, SHAKE_M * 0.6, -SHAKE_M * 0.3, 0.0),
                doubleArrayOf(SHAKE_STEP_MS, SHAKE_STEP_MS * 2, SHAKE_STEP_MS * 2, SHAKE_STEP_MS * 2, SHAKE_STEP_MS),
                QUAD_IN_OUT,
            )
        }
    }

    /** A touch takes over from wherever the film (or the return) is. */
    private fun pause() {
        if (now < 0) now = System.nanoTime() / 1e6
        if (mode == MODE_AUTO) {
            // the film's open door finishes the way it was going, and stays unlocked until the finger locks it
            val u = unlock.value(now)
            if (u > 0.01) {
                val opening = t < SafeMotion.SHUT
                canLock = opening
                val to = if (opening) SafeMotion.UNLOCK else 0.0
                unlock.timing(now, to, abs(to - u) * 1000, LINEAR)
                if (!opening) count = 0
            } else if (count >= 3) {
                count = 0
            }
            // a dial caught between numbers lands on the nearest one, without entering it
            val near = SafeMotion.roundJs(dial.value(now))
            dial.spring(now, near)
            landing = near
            turned = false
        }
        if (mode == MODE_RETURN) {
            dial.cancel(now)
            val near = SafeMotion.roundJs(dial.value(now))
            landing = near
            dial.spring(now, near)
            turned = false
        }
        mode = MODE_MANUAL
        lastTouch = -1.0
    }

    /** The Lock button: the door shuts and the entries clear. */
    fun relock() {
        pause()
        canLock = false
        count = 0
        if (reduced) unlock.set(0.0) else unlock.timing(now, 0.0, RELOCK_MS, LINEAR)
        lastTouch = -1.0
    }

    /** A finger starts turning the dial, at (x, y) dp in a stage of width x height dp. */
    fun panStart(x: Double, y: Double, width: Double, height: Double) {
        pause()
        if (canLock) return
        dragging = true
        dial.cancel(now)
        landing = Double.NaN
        val c = DoubleArray(2)
        SafeMotion.project(0.0, DIAL_WORLD_Y, SafeMotion.DIAL_FACE_Z, SafeMotion.doorAt(unlock.value(now)), width, height, c)
        centreX = c[0]
        centreY = c[1]
        lastAngle = SafeMotion.angle(x - c[0], y - c[1])
    }

    fun panMove(x: Double, y: Double) {
        if (!dragging) return
        val a = SafeMotion.angle(x - centreX, y - centreY)
        val d = SafeMotion.angleDelta(lastAngle, a)
        lastAngle = a
        // on screen a positive angle is clockwise, and a clockwise dial counts down
        dial.set(dial.value(now) - d * 180 / Math.PI / SafeMotion.DEG_PER_NUMBER)
        if (abs(d) > 0.001) turned = true
    }

    /** Released at (x, y) with the finger's velocity (dp/s): its speed carries it on and a spring lands it on a number. */
    fun panEnd(x: Double, y: Double, vx: Double, vy: Double) {
        if (!dragging) return
        val rx = x - centreX
        val ry = y - centreY
        val r2 = max(400.0, rx * rx + ry * ry)
        val omega = (rx * vy - ry * vx) / r2
        val v = -omega * 180 / Math.PI / SafeMotion.DEG_PER_NUMBER
        val target = SafeMotion.roundJs(dial.value(now) + v * FLICK_S)
        landing = target
        if (reduced) dial.set(target) else dial.spring(now, target, v)
    }

    fun panFinalize() {
        dragging = false
    }

    private companion object {
        const val MODE_AUTO = 0
        const val MODE_MANUAL = 1
        const val MODE_RETURN = 2
        const val IDLE_MS = 2000.0
        const val RETURN_MS = 1250.0
        const val STALL_MS = 100.0
        const val FRAME_MS = 1000.0 / 60
        const val FLICK_S = 0.16
        const val DIAL_K = 170.0
        const val DIAL_C = 22.0
        const val SETTLED = 0.012
        const val SHAKE_M = 0.007
        const val SHAKE_STEP_MS = 55.0
        const val WRONG_HOLD_MS = 520.0
        const val RELOCK_MS = 1200.0
        const val FADE_MS = 320.0
        val DIAL_WORLD_Y = (SafeMotion.DOOR_HINGE[1] + SafeMotion.DIAL_PIVOT[1]).toDouble()
    }
}
