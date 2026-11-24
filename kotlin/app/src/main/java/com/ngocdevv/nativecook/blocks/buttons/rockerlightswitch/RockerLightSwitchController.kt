package com.ngocdevv.nativecook.blocks.buttons.rockerlightswitch

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val EASE_IN_OUT_BEZIER = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

internal enum class SwitchEase {
    InQuad, OutCubic, InOutQuad, InOutBezier;

    fun at(x: Double): Double = when (this) {
        InQuad -> SwitchMotion.easeInQuad(x)
        OutCubic -> SwitchMotion.easeOutCubic(x)
        InOutQuad -> SwitchMotion.easeInOutQuad(x)
        InOutBezier -> EASE_IN_OUT_BEZIER.transform(x.toFloat()).toDouble()
    }
}

/**
 * One animated number as a closed form of the frame time (ms): still, a timing (with a delay), a spring, or a timing
 * that hands over to a spring (Reanimated's withTiming / withDelay / withSpring / withSequence). Reading it never
 * advances anything, so the Compose layers and the Filament stage see the same value for the same frame.
 */
internal class SwitchTrack(initial: Double, private val z: Double = SwitchMotion.SNAP_Z, private val w: Double = SwitchMotion.SNAP_W) {
    private var timed = false
    private var sprung = false
    private var from = initial
    private var to = initial
    private var start = 0.0
    private var duration = 0.0
    private var ease = SwitchEase.InOutQuad
    private var springFrom = initial
    private var springTo = initial
    private var springV = 0.0
    private var springStart = 0.0

    fun value(now: Double): Double {
        if (timed) {
            val tau = now - start
            if (tau <= 0) return from
            if (tau < duration) return from + (to - from) * ease.at(tau / duration)
            if (!sprung) return to
        } else if (!sprung) {
            return to
        }
        val s = max(0.0, (now - springStart) / 1000)
        val d = springFrom - springTo
        return springTo + exp(-z * s) * (d * cos(w * s) + ((springV + z * d) / w) * sin(w * s))
    }

    fun set(v: Double) {
        timed = false
        sprung = false
        to = v
    }

    fun cancel(now: Double) = set(value(now))

    fun timing(now: Double, target: Double, ms: Double, easing: SwitchEase, delayMs: Double = 0.0) {
        from = value(now)
        timed = true
        sprung = false
        to = target
        start = now + delayMs
        duration = ms
        ease = easing
    }

    /** `velocity` in units a second. */
    fun spring(now: Double, target: Double, velocity: Double = 0.0) {
        springFrom = value(now)
        timed = false
        sprung = true
        springTo = target
        springV = velocity
        springStart = now
    }

    /** The press, then the snap: a timing to `tip`, then the spring from there to `target`. */
    fun timingThenSpring(now: Double, tip: Double, ms: Double, easing: SwitchEase, target: Double) {
        timing(now, tip, ms, easing)
        sprung = true
        springFrom = tip
        springTo = target
        springV = 0.0
        springStart = now + ms
    }
}

/**
 * The block's state for one frame time: the film while it plays, a finger's values otherwise, and the way back to the
 * film (RockerLightSwitch.tsx's frame callback, latch reaction and gesture). Main thread only. `frame(now)` may be
 * called several times for one display frame (the Compose layers and the Filament stage): it advances once.
 */
internal class RockerLightSwitchController(autoPlay: Boolean, val reduced: Boolean) {
    private val film = !reduced && autoPlay

    private val press = SwitchTrack(if (reduced) 1.0 else 0.0)
    private val level = SwitchTrack(if (reduced) 1.0 else 0.0)
    private val bright = SwitchTrack(1.0)
    private val orbit = SwitchTrack(0.0)

    private var mode = if (film) MODE_AUTO else MODE_MANUAL
    private var loopStart = -1.0
    private var lastActivity = -1.0
    private var returnAt = -1.0
    private var dragging = false
    private var dragMode = false
    private var fromX = 0.0
    private var fromY = 0.0
    private var now = -1.0

    /** The 3D stage has drawn the wall: the film may start. */
    var ready = false

    /** A finger is on the slider. */
    var sliding = false
        private set

    // ------------------------------------------------------------ this frame
    var t = 0.0
        private set
    var pressNow = if (reduced) 1.0 else 0.0
        private set
    var levelNow = if (reduced) 1.0 else 0.0
        private set
    var brightNow = 1.0
        private set
    var orbitNow = 0.0
        private set

    /** The film's finger: [opacity, half, pressed]. A real finger's dot is not drawn. */
    val ghost = doubleArrayOf(0.0, 1.0, 0.0)

    /** The latch: the light is on from press 0.5. */
    var lit = reduced
        private set

    /** Counts the latch's clicks (for the haptic). */
    var clicks = 0
        private set

    val light: Double get() = SwitchMotion.lightOut(levelNow, brightNow)

    fun frame(nowMs: Double) {
        if (nowMs > now) {
            now = nowMs
            step()
        }
        sample()
    }

    private fun sample() {
        pressNow = press.value(now)
        levelNow = level.value(now)
        brightNow = bright.value(now)
        orbitNow = orbit.value(now)
        // the latch: a click, and (outside the film, which has its own curve) the filament warming up or dying
        val cur = pressNow >= 0.5
        if (cur == lit) return
        lit = cur
        clicks++
        if (mode == MODE_AUTO) return
        if (reduced) {
            level.timing(now, if (cur) 1.0 else 0.0, 200.0, SwitchEase.InOutQuad)
        } else if (cur) {
            level.timing(now, 1.0, SwitchMotion.WARM * 1000, SwitchEase.OutCubic, SwitchMotion.LAG * 1000)
        } else {
            level.timing(now, 0.0, SwitchMotion.COOL * 1000, SwitchEase.OutCubic, SwitchMotion.LAG_OFF * 1000)
        }
    }

    private fun step() {
        if (mode == MODE_AUTO) {
            if (!film) {
                mode = MODE_MANUAL
                return
            }
            if (!ready) {
                t = 0.0
                return
            }
            if (loopStart < 0) loopStart = now
            val tt = ((now - loopStart) / 1000) % SwitchMotion.LOOP
            t = tt
            press.set(SwitchMotion.filmPress(tt))
            level.set(SwitchMotion.filmLevel(tt))
            bright.set(SwitchMotion.filmBright(tt))
            orbit.set(SwitchMotion.filmOrbit(tt))
            SwitchMotion.filmGhost(tt, ghost)
            return
        }
        if (mode == MODE_RETURN) {
            // the switch is off again and the camera home: the film starts over
            if (now - returnAt >= SwitchMotion.RETURN_MS) {
                t = 0.0
                loopStart = -1.0
                mode = MODE_AUTO
            }
            return
        }
        if (lastActivity < 0 || dragging || sliding) {
            lastActivity = now
            return
        }
        if (!film || now - lastActivity < SwitchMotion.IDLE_MS) return
        // 2 s after the last touch: the rocker snaps off, the slider and the camera ease home, then the film restarts
        mode = MODE_RETURN
        returnAt = now
        if (press.value(now) >= 0.5) {
            press.timingThenSpring(now, 1 - SwitchMotion.PRESS_DEPTH, TAP_PRESS_MS, SwitchEase.InQuad, 0.0)
        } else {
            press.spring(now, 0.0)
        }
        bright.timing(now, 1.0, 500.0, SwitchEase.InOutBezier)
        orbit.timing(now, 0.0, 800.0, SwitchEase.InOutBezier)
    }

    /** A touch takes over: the film stops where it is and the light settles to what the latch says. */
    private fun pause() {
        if (now < 0) now = System.nanoTime() / 1e6
        if (mode == MODE_AUTO) {
            level.timing(now, if (press.value(now) >= 0.5) 1.0 else 0.0, 300.0, SwitchEase.OutCubic)
            ghost[0] = 0.0
            ghost[1] = 1.0
            ghost[2] = 0.0
        }
        if (mode == MODE_RETURN) {
            bright.cancel(now)
            orbit.cancel(now)
        }
        mode = MODE_MANUAL
        lastActivity = -1.0
    }

    /** A finger lands on the plate, on the top half (`top`) or the bottom one. */
    fun pressBegan(top: Boolean) {
        pause()
        dragging = true
        dragMode = false
        // a press on the raised half tips the rocker; on the half already in, it only gives a little
        val target = if (top) 1.0 else 0.0
        val isOn = press.value(now) >= 0.5
        press.cancel(now)
        if (top != isOn) {
            val tip = if (top) SwitchMotion.PRESS_DEPTH else 1 - SwitchMotion.PRESS_DEPTH
            press.timingThenSpring(now, tip, if (reduced) 60.0 else TAP_PRESS_MS, SwitchEase.InQuad, target)
        } else {
            press.timingThenSpring(now, if (top) 1.05 else -0.05, 90.0, SwitchEase.OutCubic, target)
        }
    }

    /** The finger has moved `translationY` dp since it landed. */
    fun pressMoved(translationY: Double) {
        if (!dragging) return
        if (!dragMode) {
            if (abs(translationY) < 8) return
            // a drag takes the rocker over from wherever the press left it
            dragMode = true
            fromX = SwitchMotion.detentInverse(min(1.0, max(0.0, press.value(now))))
            fromY = translationY
        }
        // dragging up tips it on; rubber-banded past either rest
        var x = fromX - (translationY - fromY) / DRAG_PT
        if (x > 1) x = 1 + (x - 1) / (1 + (x - 1) * 6) * 0.25
        else if (x < 0) x = x / (1 - x * 6) * 0.25
        press.set(if (x > 1 || x < 0) x else SwitchMotion.detent(x))
    }

    /** The finger lifts with `velocityY` dp a second: a dragged rocker falls to the side it is on (a flick counts). */
    fun pressEnded(velocityY: Double) {
        if (!dragMode) return
        val v = -velocityY / DRAG_PT
        val target = if (press.value(now) + v * 0.08 >= 0.5) 1.0 else 0.0
        press.spring(now, target, v)
    }

    fun pressFinalized() {
        dragging = false
        dragMode = false
    }

    /** The slider's value from a finger. */
    fun setBrightness(v: Double) {
        pause()
        bright.set(min(1.0, max(SwitchMotion.BRIGHT_MIN, v)))
    }

    fun setSliding(active: Boolean) {
        if (active && !sliding) pause()
        sliding = active
    }

    private companion object {
        const val MODE_AUTO = 0
        const val MODE_MANUAL = 1
        const val MODE_RETURN = 2

        /** Points of vertical drag for the rocker's whole travel. */
        const val DRAG_PT = 84.0

        /** A finger's press reaches the tipping point in this long (ms); the film's finger is slower. */
        const val TAP_PRESS_MS = 130.0
    }
}
