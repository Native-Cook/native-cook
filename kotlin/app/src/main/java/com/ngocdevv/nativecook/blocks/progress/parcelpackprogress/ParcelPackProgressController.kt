package com.ngocdevv.nativecook.blocks.progress.parcelpackprogress

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** The orbit's swing back as a closed form of the frame time: stiffness 140, damping 17, mass 1. */
private class OrbitSpring {
    private var sprung = false
    private var from = 0.0
    private var velocity = 0.0
    private var start = 0.0
    private var still = 0.0

    fun value(now: Double): Double {
        if (!sprung) return still
        val s = max(0.0, (now - start) / 1000)
        return exp(-Z * s) * (from * cos(W * s) + (velocity + Z * from) / W * sin(W * s))
    }

    fun set(v: Double) {
        sprung = false
        still = v
    }

    fun release(now: Double, v: Double) {
        from = value(now)
        velocity = v
        start = now
        sprung = true
    }

    private companion object {
        const val Z = 8.5
        val W = sqrt(140.0 - Z * Z)
    }
}

/** A tap's run of the film's clock: one or two legs, each from a time to a time. */
private class ClockLeg(val from: Double, val to: Double, val start: Double, val ms: Double, val outQuad: Boolean) {
    val end: Double get() = start + ms

    fun at(now: Double): Double {
        val k = ParcelMotion.clamp01((now - start) / ms)
        val e = if (outQuad) ParcelMotion.easeOutQuad(k) else ParcelMotion.easeInOutQuad(k)
        return ParcelMotion.lerp(from, to, e)
    }
}

/**
 * The block's state for one frame time (ParcelPackProgress.tsx's frame callback, jumps and drag). Main thread only.
 * `frame(now)` may be called several times for one display frame (Compose layers and the Filament stage): it advances
 * once, so both show the same t.
 */
internal class ParcelPackProgressController(autoPlay: Boolean, private val reduced: Boolean) {
    private val film = !reduced && autoPlay
    private val orbit = OrbitSpring()
    private var mode = if (film) MODE_AUTO else MODE_MANUAL
    private var loopStart = -1.0
    private var lastTouch = -1.0
    private var dragging = false
    private var dragFrom = 0.0
    private var now = -1.0
    private var first: ClockLeg? = null
    private var second: ClockLeg? = null
    private var readyAt = -1.0
    private var previousT: Double

    var t = if (reduced) ParcelMotion.REDUCED_T else 0.0
        private set
    var orbitNow = 0.0
        private set

    /** The stage's fade-in over the backdrop, 0..1. */
    var shown = 0.0
        private set

    /** The film's finger is drawn only while the film plays. */
    val filming: Boolean get() = mode == MODE_AUTO

    /** Count a light tick as a step is done, and a soft one as the label lands. */
    var ticks = 0
        private set
    var thuds = 0
        private set

    init {
        previousT = t
    }

    /** The 3D stage has drawn the carton: the film may start. */
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
        orbitNow = orbit.value(now)
        shown = if (readyAt < 0) 0.0 else ParcelMotion.easeInOutQuad(ParcelMotion.clamp01((now - readyAt) / FADE_MS))
    }

    private fun step(gap: Double) {
        if (mode == MODE_AUTO) {
            if (readyAt < 0) {
                t = 0.0
                loopStart = -1.0
                return
            }
            if (loopStart < 0) {
                loopStart = now - t * 1000
            } else if (gap > STALL_MS) {
                // the thread was held up: the film picks up where it was instead of skipping what the stall covered
                loopStart += gap - FRAME_MS
            }
            var el = (now - loopStart) / 1000
            if (el >= ParcelMotion.LOOP) {
                loopStart += ParcelMotion.LOOP * 1000
                el -= ParcelMotion.LOOP
            }
            t = el
            return
        }
        runClock()
        if (lastTouch < 0 || dragging) {
            lastTouch = now
            return
        }
        if (!film || now - lastTouch < IDLE_MS) return
        // 2 s after the last touch the film carries on from the step on show
        first = null
        second = null
        t = ((t % ParcelMotion.LOOP) + ParcelMotion.LOOP) % ParcelMotion.LOOP
        loopStart = -1.0
        mode = MODE_AUTO
    }

    private fun runClock() {
        val a = first ?: return
        if (now < a.end) {
            t = a.at(now)
            return
        }
        val b = second
        if (b == null) {
            t = a.to
            first = null
            return
        }
        if (now < b.end) {
            t = b.at(now)
            return
        }
        t = b.to
        first = null
        second = null
    }

    private fun haptics() {
        val previous = previousT
        val current = t
        previousT = current
        if (current <= previous || current - previous > 1) return
        if (previous < ParcelMotion.LABEL_LAND && current >= ParcelMotion.LABEL_LAND) {
            thuds++
            return
        }
        for (done in ParcelMotion.DONE) {
            if (previous < done && current >= done) {
                ticks++
                return
            }
        }
    }

    private fun pause() {
        if (now < 0) now = System.nanoTime() / 1e6
        if (mode == MODE_AUTO) mode = MODE_MANUAL
        lastTouch = -1.0
    }

    /** A tap on step i: the clock runs, forward or back, to the moment that step is done. */
    fun jumpTo(i: Int) {
        pause()
        first = null
        second = null
        val to = ParcelMotion.JUMP[i]
        if (reduced) {
            t = to
            return
        }
        var from = ((t % ParcelMotion.LOOP) + ParcelMotion.LOOP) % ParcelMotion.LOOP
        if (from >= ParcelMotion.REST) {
            // the rest at the loop's end is the same frame as t = 0
            from = 0.0
            t = 0.0
        }
        if (from >= ParcelMotion.SWAP) {
            // a new carton is on its way in: let it arrive, then run forward from the start
            val arrive = ClockLeg(from, ParcelMotion.REST, now, jumpMs(ParcelMotion.REST - from), true)
            first = arrive
            second = ClockLeg(0.0, to, arrive.end, jumpMs(to), false)
            return
        }
        first = ClockLeg(from, to, now, jumpMs(to - from), false)
    }

    fun dragBegan(width: Double) {
        pause()
        dragging = true
        val at = orbit.value(now)
        orbit.set(at)
        dragFrom = -at / ParcelMotion.ORBIT_SPAN * width
    }

    fun dragMoved(dx: Double, width: Double) {
        if (dragging) orbit.set(ParcelMotion.orbitFromDrag(dragFrom + dx, width))
    }

    fun dragEnded(velocityX: Double, width: Double) {
        if (!dragging) return
        orbit.release(now, -velocityX / width * ParcelMotion.ORBIT_SPAN)
        dragging = false
    }

    /** The gesture is over; a cancelled drag still swings back. */
    fun dragFinalized() {
        if (dragging) orbit.release(now, 0.0)
        dragging = false
    }

    private fun jumpMs(span: Double): Double = min(JUMP_MAX_MS, max(JUMP_MIN_MS, abs(span) * JUMP_MS_PER_S))

    private companion object {
        const val MODE_AUTO = 0
        const val MODE_MANUAL = 1
        const val IDLE_MS = 2000.0
        const val JUMP_MS_PER_S = 280.0
        const val JUMP_MIN_MS = 300.0
        const val JUMP_MAX_MS = 1400.0
        const val STALL_MS = 100.0
        const val FRAME_MS = 1000.0 / 60
        const val FADE_MS = 320.0
    }
}
