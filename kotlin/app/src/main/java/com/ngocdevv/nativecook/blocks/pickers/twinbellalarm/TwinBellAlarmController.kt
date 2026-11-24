package com.ngocdevv.nativecook.blocks.pickers.twinbellalarm

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

private val EASE_IN_OUT = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

/**
 * One animated number as a closed form of the frame time (ms): still, up to two eased legs (a delay or hold between
 * them), or a spring. Reading it never advances anything, so the Compose layers and the stage agree for a frame.
 */
private class AlarmTrack(initial: Double, stiffness: Double, damping: Double) {
    private val z = damping / 2
    private val w = sqrt(stiffness - z * z)
    private var still = initial
    private var sprung = false
    private var springTo = initial
    private var springFrom = initial
    private var springV = 0.0
    private var springStart = 0.0
    private var legs = 0
    private val from = DoubleArray(2)
    private val to = DoubleArray(2)
    private val start = DoubleArray(2)
    private val ms = DoubleArray(2)

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
                val k = ((now - start[i]) / ms[i]).toFloat()
                return from[i] + (to[i] - from[i]) * EASE_IN_OUT.transform(k)
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

    fun timing(now: Double, target: Double, duration: Double, delay: Double = 0.0) {
        val current = value(now)
        sprung = false
        legs = 1
        from[0] = current
        to[0] = target
        start[0] = now + delay
        ms[0] = duration
    }

    /** To `peak` over `inMs`, a hold, then back to `end` over `outMs`. */
    fun there(now: Double, peak: Double, inMs: Double, holdMs: Double, end: Double, outMs: Double) {
        timing(now, peak, inMs)
        legs = 2
        from[1] = peak
        to[1] = end
        start[1] = now + inMs + holdMs
        ms[1] = outMs
    }
}

/**
 * The block's state for one frame time (TwinBellAlarm.tsx's frame callback, the hands' springs, a finger's ring and
 * the orbit drag). Main thread only. `frame(now)` may be called several times for one display frame: it advances once.
 */
internal class TwinBellAlarmController(autoPlay: Boolean, private val reduced: Boolean) {
    private val film = !reduced && autoPlay
    private val initialMinutes = if (reduced) AlarmMotion.FILM_MIN else AlarmMotion.REST_MIN

    private val hour = AlarmTrack(AlarmMotion.hourDeg(initialMinutes), HAND_K, HAND_C)
    private val minute = AlarmTrack(AlarmMotion.minuteDeg(initialMinutes), HAND_K, HAND_C)
    private val push = AlarmTrack(0.0, ORBIT_K, ORBIT_C)
    private val orbit = AlarmTrack(0.0, ORBIT_K, ORBIT_C)

    private var mode = if (film) MODE_AUTO else MODE_MANUAL
    private var loopStart = -1.0
    private var lastTouch = -1.0
    private var returnAt = -1.0
    private var dragging = false
    private var holdingWheel = false
    private var dragFrom = 0.0
    private var ringStart = -1.0
    private var now = -1.0
    private var readyAt = -1.0
    private var ringKey = -1

    var t = 0.0
        private set
    var hourNow = AlarmMotion.hourDeg(initialMinutes)
        private set
    var minuteNow = AlarmMotion.minuteDeg(initialMinutes)
        private set
    var pushNow = 0.0
        private set
    var orbitNow = 0.0
        private set

    /** Seconds into a ring (negative: not ringing). */
    var ring = -1.0
        private set

    /** What the controls show: minutes of the day, and the switch. */
    var wheel = initialMinutes
        private set
    var armed = reduced
        private set

    var shown = 0.0
        private set
    val filming: Boolean get() = mode == MODE_AUTO

    /** The ring's haptics: one firm tap as it starts, then a light one every 0.3 s. */
    var buzzes = 0
        private set
    var ticks = 0
        private set

    fun markReady() {
        if (readyAt < 0) readyAt = max(now, 0.0)
    }

    fun frame(nowMs: Double) {
        if (nowMs > now) {
            val gap = if (now < 0) 0.0 else nowMs - now
            now = nowMs
            step(gap)
            haptics()
        }
        hourNow = hour.value(now)
        minuteNow = minute.value(now)
        pushNow = push.value(now)
        orbitNow = orbit.value(now)
        shown = if (readyAt < 0) 0.0 else easeInOutQuad(AlarmMotion.clamp01((now - readyAt) / FADE_MS))
    }

    private fun step(gap: Double) {
        if (mode == MODE_AUTO) {
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
            if (el >= AlarmMotion.LOOP) {
                loopStart += AlarmMotion.LOOP * 1000
                el -= AlarmMotion.LOOP
            }
            t = el
            hour.set(AlarmMotion.filmHour(el))
            minute.set(AlarmMotion.filmMinute(el))
            push.set(AlarmMotion.filmPush(el))
            ring = AlarmMotion.filmRing(el)
            wheel = AlarmMotion.filmWheel(el)
            armed = AlarmMotion.filmArmed(el)
            return
        }
        // a finger's ring, on its own clock
        if (ringStart >= 0) {
            val tau = (now - ringStart) / 1000
            if (tau <= AlarmMotion.RING) {
                ring = tau
            } else {
                ring = -1.0
                ringStart = -1.0
            }
        } else {
            ring = -1.0
        }
        if (mode == MODE_RETURN) {
            // the alarm is back at 7:00 and off, the camera home: the film starts over
            if (now - returnAt >= RETURN_MS) {
                t = 0.0
                loopStart = -1.0
                mode = MODE_AUTO
            }
            return
        }
        if (lastTouch < 0 || dragging || holdingWheel) {
            lastTouch = now
            return
        }
        if (!film || now - lastTouch < IDLE_MS) return
        // 2 s after the last touch: the wheel and the hands go back to 7:00, the switch off, the camera home
        mode = MODE_RETURN
        returnAt = now
        wheel = AlarmMotion.REST_MIN
        armed = false
        sweepTo(AlarmMotion.REST_MIN)
        quiet()
        orbit.timing(now, 0.0, 700.0)
    }

    private fun haptics() {
        val key = if (ring < 0) -1 else floor(ring / RING_TICK).toInt()
        if (key == ringKey) return
        ringKey = key
        if (key < 0 || reduced) return
        if (key == 0) buzzes++ else ticks++
    }

    /** The hands go to a time by the shortest way round, each on its own spring. */
    private fun sweepTo(min: Int) {
        val hNow = hour.value(now)
        val mNow = minute.value(now)
        val h = hNow + AlarmMotion.shortest(AlarmMotion.hourDeg(min) - hNow)
        val m = mNow + AlarmMotion.shortest(AlarmMotion.minuteDeg(min) - mNow)
        if (reduced) {
            hour.set(h)
            minute.set(m)
            return
        }
        hour.spring(now, h)
        minute.spring(now, m)
    }

    /** The ring stops: what is left of it is its dying away, and the camera eases back. */
    private fun quiet() {
        if (ringStart >= 0) {
            val tau = (now - ringStart) / 1000
            if (tau < AlarmMotion.RING - AlarmMotion.RING_OUT) ringStart = now - (AlarmMotion.RING - AlarmMotion.RING_OUT) * 1000
        }
        push.timing(now, 0.0, PUSH_BACK_MS)
    }

    /** A touch takes over from wherever the film (or the return) is. */
    private fun pause() {
        if (now < 0) now = System.nanoTime() / 1e6
        if (mode == MODE_AUTO) {
            // a ring in progress carries on, on its own clock; the camera eases back after it
            if (ring >= 0) {
                ringStart = now - ring * 1000
                push.timing(now, 0.0, PUSH_BACK_MS, max(0.0, (AlarmMotion.RING - ring) * 1000))
            } else if (push.value(now) > 0) {
                push.timing(now, 0.0, PUSH_BACK_MS)
            }
            // the hands may be mid-sweep: they finish at the time the wheel shows
            sweepTo(wheel)
        }
        if (mode == MODE_RETURN) orbit.cancel(now)
        mode = MODE_MANUAL
        lastTouch = -1.0
    }

    /** A finger is on the wheel: the film stops, and the idle clock waits until it lets go. */
    fun wheelHeld(held: Boolean) {
        if (held) pause()
        holdingWheel = held
    }

    fun userMinutes(min: Int) {
        pause()
        wheel = min
        sweepTo(min)
    }

    /** On rings (the camera moves in, holds, comes back); off stops it. */
    fun userToggle(on: Boolean) {
        pause()
        armed = on
        if (on && !reduced) {
            ringStart = now
            push.there(now, 1.0, PUSH_MS, PUSH_HOLD_MS, 0.0, PUSH_BACK_MS)
            return
        }
        quiet()
    }

    fun dragBegan(width: Double) {
        pause()
        dragging = true
        orbit.cancel(now)
        dragFrom = -orbit.value(now) / AlarmMotion.ORBIT_SPAN * width
    }

    fun dragMoved(dx: Double, width: Double) {
        if (dragging) orbit.set(AlarmMotion.orbitFromDrag(dragFrom + dx, width))
    }

    fun dragEnded(velocityX: Double, width: Double) {
        if (!dragging) return
        orbit.spring(now, 0.0, -velocityX / width * AlarmMotion.ORBIT_SPAN)
        dragging = false
    }

    /** The gesture is over; a cancelled drag still swings back. */
    fun dragFinalized() {
        if (dragging) orbit.spring(now, 0.0, 0.0)
        dragging = false
    }

    private fun easeInOutQuad(x: Double): Double = if (x < 0.5) 2 * x * x else 1 - (2 - 2 * x) * (2 - 2 * x) / 2

    private companion object {
        const val MODE_AUTO = 0
        const val MODE_MANUAL = 1
        const val MODE_RETURN = 2
        const val IDLE_MS = 2000.0
        const val RETURN_MS = 1000.0
        const val STALL_MS = 100.0
        const val FRAME_MS = 1000.0 / 60
        const val RING_TICK = 0.3
        const val HAND_K = 150.0
        const val HAND_C = 15.0
        const val ORBIT_K = 140.0
        const val ORBIT_C = 17.0
        const val PUSH_MS = 650.0
        const val PUSH_HOLD_MS = 900.0
        const val PUSH_BACK_MS = 1100.0
        const val FADE_MS = 320.0
    }
}
