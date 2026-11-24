package com.ngocdevv.nativecook.blocks.progress.espressobrewprogress

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One animated number as a closed form of the frame time (ms): still, one eased leg (withTiming with Reanimated's
 * default ease-in-out quad) or a spring (withSpring, mass 1). Reading it never advances anything, so the Compose
 * layers and the stage agree for a frame.
 */
private class EspressoTrack(initial: Double, stiffness: Double = 140.0, damping: Double = 18.0) {
    private val z = damping / 2
    private val w = sqrt(stiffness - z * z)
    private var still = initial
    private var sprung = false
    private var springTo = initial
    private var springFrom = initial
    private var springV = 0.0
    private var springStart = 0.0
    private var timed = false
    private var from = 0.0
    private var to = 0.0
    private var start = 0.0
    private var ms = 0.0

    fun value(now: Double): Double {
        if (sprung) {
            val s = max(0.0, (now - springStart) / 1000)
            val d = springFrom - springTo
            return springTo + exp(-z * s) * (d * cos(w * s) + (springV + z * d) / w * sin(w * s))
        }
        if (!timed) return still
        if (now <= start) return from
        if (now >= start + ms) return to
        return from + (to - from) * easeInOutQuad((now - start) / ms)
    }

    /** The spring's speed (units a second), 0 when it is not a spring. */
    private fun velocity(now: Double): Double {
        if (!sprung) return 0.0
        val s = max(0.0, (now - springStart) / 1000)
        val d = springFrom - springTo
        val a = d
        val b = (springV + z * d) / w
        return exp(-z * s) * ((b * w - z * a) * cos(w * s) - (a * w + z * b) * sin(w * s))
    }

    fun set(v: Double) {
        sprung = false
        timed = false
        still = v
    }

    /** cancelAnimation: it stays where it is. */
    fun cancel(now: Double) = set(value(now))

    /** A spring to target; a spring already moving hands on its speed unless one is given. */
    fun spring(now: Double, target: Double, velocity: Double? = null) {
        val v = velocity ?: velocity(now)
        springFrom = value(now)
        springTo = target
        springV = v
        springStart = now
        sprung = true
        timed = false
    }

    fun timing(now: Double, target: Double, duration: Double) {
        from = value(now)
        to = target
        start = now
        ms = duration
        sprung = false
        timed = true
    }
}

internal fun easeInOutQuad(x: Double): Double = if (x < 0.5) 2 * x * x else 1 - (2 - 2 * x) * (2 - 2 * x) / 2

/**
 * The block's state for one frame time (EspressoBrewProgress.tsx's frame callback: the film, a finger's shot that plays
 * the film's timeline in real time, the cup change and the return). Main thread only. `frame(now)` may be called
 * several times for one display frame: it advances once.
 */
internal class EspressoBrewProgressController(autoPlay: Boolean, private val reduced: Boolean) {
    private val film = !reduced && autoPlay

    // reduced motion rests on the film's result: a double, poured, the cup full
    private val dose = EspressoTrack(if (reduced) 1.0 else 0.0)
    private val orbit = EspressoTrack(0.0, ORBIT_K, ORBIT_C)

    private var mode = if (film) MODE_AUTO else MODE_MANUAL

    /** A finger's shot runs t forward to runTo (-1: holds); `chain` starts a shot when it gets there. */
    private var runTo = -1.0
    private var chain = false
    private var loopStart = -1.0
    private var now = -1.0
    private var readyAt = -1.0
    private var lastTouch = -1.0
    private var returnAt = -1.0
    private var dragging = false
    private var dragFrom = 0.0

    /** The place on the film's timeline (s): the film's t, or a finger's shot. */
    var t = if (reduced) EspressoMotion.PHASE_FULL else 0.0
        private set

    /** The selected dose: 0 = Single, 1 = Double (the film and a finger write it). */
    var doseSel = if (reduced) 1 else 0
        private set

    /** The liquid's dose (eased, 0..1) and the finger's orbit (deg), at the last frame. */
    var doseNow = dose.value(0.0)
        private set
    var orbitNow = 0.0
        private set

    /** The stage's fade-in, 0..1. */
    var shown = 0.0
        private set
    val filming: Boolean get() = mode == MODE_AUTO

    fun markReady() {
        if (readyAt < 0) readyAt = max(now, 0.0)
    }

    fun frame(nowMs: Double) {
        if (nowMs > now) {
            val gap = if (now < 0) 0.0 else nowMs - now
            now = nowMs
            step(gap)
        }
        doseNow = dose.value(now)
        // while the film plays the stage looks from straight on (RN's stage uses orbit 0 then)
        orbitNow = if (mode == MODE_AUTO) 0.0 else orbit.value(now)
        shown = if (readyAt < 0) 0.0 else easeInOutQuad(EspressoMotion.clamp01((now - readyAt) / FADE_MS))
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
            if (el >= EspressoMotion.LOOP) {
                loopStart += EspressoMotion.LOOP * 1000
                el -= EspressoMotion.LOOP
            }
            t = el
            val d = EspressoMotion.filmDose(el)
            if (doseSel != d) {
                doseSel = d
                dose.set(d.toDouble())
            }
            return
        }
        // a finger's shot (or a cup change) plays the film's timeline in real time
        if (runTo >= 0) {
            val dt = min(0.05, gap / 1000)
            val next = t + dt
            if (next >= runTo) {
                if (chain) {
                    chain = false
                    t = EspressoMotion.PHASE_BREW
                    runTo = EspressoMotion.PHASE_FULL
                } else {
                    t = runTo
                    runTo = -1.0
                }
            } else {
                t = next
            }
        }
        if (mode == MODE_RETURN) {
            if (runTo < 0 && now - returnAt >= RETURN_MS) {
                t = 0.0
                loopStart = -1.0
                mode = MODE_AUTO
            }
            return
        }
        if (lastTouch < 0 || dragging || runTo >= 0) {
            lastTouch = now
            return
        }
        if (!film || now - lastTouch < IDLE_MS) return
        // 2 s after the last touch: a full cup is changed for an empty one and the camera comes home; the film's restart
        // puts Single back (the cup is empty by then, so the dose changes nothing on screen)
        mode = MODE_RETURN
        returnAt = now
        val cur = t
        if (cur >= EspressoMotion.PHASE_BREW && cur < EspressoMotion.PHASE_CUP_IN_END) runTo = EspressoMotion.PHASE_CUP_IN_END
        orbit.spring(now, 0.0)
    }

    /** A touch takes over from wherever the film (or the return) is; a shot in progress keeps pouring. */
    private fun pause() {
        if (now < 0) now = System.nanoTime() / 1e6
        if (mode == MODE_RETURN) {
            orbit.cancel(now)
            dose.cancel(now)
            chain = false
        }
        if (mode == MODE_AUTO) {
            val cur = t
            chain = false
            runTo = when {
                cur >= EspressoMotion.PHASE_CUP_IN_END -> {
                    t = 0.0
                    -1.0
                }
                cur >= EspressoMotion.PHASE_BREW && cur < EspressoMotion.PHASE_FULL -> EspressoMotion.PHASE_FULL
                cur >= EspressoMotion.PHASE_CUP_OUT -> EspressoMotion.PHASE_CUP_IN_END
                else -> -1.0
            }
        }
        mode = MODE_MANUAL
        lastTouch = -1.0
    }

    /** The Brew button. */
    fun userBrew() {
        pause()
        val cur = t
        if (reduced) {
            // no pour: the shot is simply there
            runTo = -1.0
            t = EspressoMotion.PHASE_FULL
            return
        }
        if (runTo >= 0) {
            // already pouring, or changing the cup: brew when the fresh cup is in
            if (runTo == EspressoMotion.PHASE_CUP_IN_END) chain = true
            return
        }
        if (cur >= EspressoMotion.PHASE_BREW && cur < EspressoMotion.PHASE_CUP_IN_END) {
            // the cup is full: change it, then brew
            runTo = EspressoMotion.PHASE_CUP_IN_END
            chain = true
            return
        }
        t = EspressoMotion.PHASE_BREW
        runTo = EspressoMotion.PHASE_FULL
    }

    /** Single / Double. */
    fun userDose(v: Int) {
        pause()
        doseSel = v
        if (reduced) dose.set(v.toDouble()) else dose.timing(now, v.toDouble(), DOSE_MS)
    }

    fun dragBegan() {
        pause()
        dragging = true
        orbit.cancel(now)
        dragFrom = orbit.value(now)
    }

    /** dx: the finger's whole horizontal travel (dp) since it touched down. */
    fun dragMoved(dx: Double, width: Double) {
        if (dragging) orbit.set(EspressoMotion.orbitFromDrag(dragFrom, dx, width))
    }

    /** Let go: it lands inside its range on a spring with the finger's speed. */
    fun dragEnded(velocityX: Double, width: Double) {
        if (!dragging) return
        val v = velocityX / width * EspressoMotion.ORBIT_SPAN
        val to = max(-EspressoMotion.ORBIT_MAX, min(EspressoMotion.ORBIT_MAX, orbit.value(now) + v * 0.1))
        if (reduced) orbit.set(to) else orbit.spring(now, to, v)
    }

    fun dragFinalized() {
        dragging = false
    }

    private companion object {
        const val MODE_AUTO = 0
        const val MODE_MANUAL = 1
        const val MODE_RETURN = 2
        const val IDLE_MS = 2000.0
        const val RETURN_MS = 900.0
        const val STALL_MS = 100.0
        const val FRAME_MS = 1000.0 / 60
        const val DOSE_MS = 420.0
        const val ORBIT_K = 140.0
        const val ORBIT_C = 18.0
        const val FADE_MS = 320.0
    }
}
