package com.ngocdevv.nativecook.blocks.inputs.paperplanesend

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The block's clock and the send button's touch state for one frame time (PaperPlaneSend.tsx's frame callback, its
 * haptic reaction and `sendNow`). Main thread only. `frame(now)` may be called several times for one display frame
 * (the Compose layers and the Filament stage): it advances once.
 */
internal class PaperPlaneSendController(autoPlay: Boolean, val reduced: Boolean) {
    private val film = !reduced && autoPlay

    /** The film's time (s). */
    var t = if (reduced) PlaneMotion.REDUCED_T else 0.0
        private set

    /** The screen's geometry (PlaneMotion.G_*); the last one is the throw's size. Empty until the first layout. */
    val geo = DoubleArray(8)
    var hasGeometry = false
        private set

    /** The 3D stage has loaded the sheet: the film may start. */
    var ready = false

    /** Count the haptics: the send, and the message landing. */
    var sendTaps = 0
        private set
    var landTaps = 0
        private set

    private var loopStart = -1.0
    private var running = film
    private var now = -1.0

    // a finger on the send button: pressed 0..1 (a timing), and how far it is pulled up (dp, negative; a spring back)
    private var pressFrom = 0.0
    private var pressTo = 0.0
    private var pressStart = 0.0
    private var pressMs = 1.0
    private var pullValue = 0.0
    private var pullSpring = false
    private var pullFrom = 0.0
    private var pullV = 0.0
    private var pullStart = 0.0

    val pressed: Double
        get() {
            val k = PlaneMotion.clamp01((now - pressStart) / pressMs)
            return pressFrom + (pressTo - pressFrom) * PlaneMotion.easeInOutQuad(k)
        }

    val pull: Double
        get() {
            if (!pullSpring) return pullValue
            val s = max(0.0, (now - pullStart) / 1000)
            return exp(-PULL_Z * s) * (pullFrom * cos(PULL_W * s) + ((pullV + PULL_Z * pullFrom) / PULL_W) * sin(PULL_W * s))
        }

    fun setGeometry(width: Double, height: Double, sheetCx: Double, sheetCy: Double, slotCx: Double, slotCy: Double, sheetPt: Double) {
        geo[PlaneMotion.G_W] = width
        geo[PlaneMotion.G_H] = height
        geo[PlaneMotion.G_SX] = sheetCx
        geo[PlaneMotion.G_SY] = sheetCy
        geo[PlaneMotion.G_TX] = slotCx
        geo[PlaneMotion.G_TY] = slotCy
        geo[PlaneMotion.G_PT] = sheetPt
        if (!hasGeometry) geo[PlaneMotion.G_THROW] = 1.0
        hasGeometry = true
    }

    fun frame(nowMs: Double) {
        if (nowMs <= now) return
        val gap = if (now < 0) 0.0 else nowMs - now
        now = nowMs
        val prev = t
        step(gap)
        // haptics: the send, and the message landing
        if (reduced) return
        if (prev < PlaneMotion.TAP && t >= PlaneMotion.TAP && t < PlaneMotion.LIFT) sendTaps++
        if (prev < PlaneMotion.POP && t >= PlaneMotion.POP) landTaps++
    }

    private fun step(gap: Double) {
        if (reduced) {
            // the message delivered, still
            t = PlaneMotion.REDUCED_T
            return
        }
        if (!ready || !running) {
            t = 0.0
            loopStart = -1.0
            return
        }
        if (loopStart < 0) {
            loopStart = now
        } else if (gap > STALL_MS) {
            // the main thread was held up (the first haptic, a model still loading, a debugger): the film picks up
            // where it was instead of skipping what the stall covered
            loopStart += gap - FRAME_MS
        }
        var el = (now - loopStart) / 1000
        if (el >= PlaneMotion.LOOP) {
            // the loop's seam: a throw's bigger arc is over; without autoplay the film stops here
            loopStart += PlaneMotion.LOOP * 1000
            el -= PlaneMotion.LOOP
            geo[PlaneMotion.G_THROW] = 1.0
            if (!film) {
                running = false
                t = 0.0
                loopStart = -1.0
                return
            }
        }
        t = el
    }

    /** A finger sends: the clock jumps to the moment the message leaves the field. `amp` is the throw's size. */
    private fun sendNow(amp: Double) {
        if (reduced || !ready || now < 0) return
        // already on its way (or the receipt is still up): let it finish
        if (running && t >= PlaneMotion.LIFT && t < PlaneMotion.RESET_END) return
        geo[PlaneMotion.G_THROW] = amp
        loopStart = now - PlaneMotion.LIFT * 1000
        running = true
        sendTaps++
    }

    private fun animatePress(target: Double, ms: Double) {
        pressFrom = pressed
        pressTo = target
        pressStart = now
        pressMs = ms
    }

    /** A finger lands on the send button. */
    fun touchDown() = animatePress(1.0, 90.0)

    /** A drag (8 dp or more): the button follows the finger up, rubber-banded. */
    fun dragged(translationY: Double) {
        val up = if (translationY < 0) -translationY else 0.0
        pullSpring = false
        pullValue = -PULL_MAX * (1 - 1 / (up / PULL_MAX + 1))
    }

    /** The drag ends: far or fast enough upwards is a throw; the harder the flick, the bigger (and faster) the flight. */
    fun dragEnded(translationY: Double, velocityY: Double) {
        if (translationY < -THROW_PT || velocityY < -THROW_V) {
            val k = min(1.0, max(0.0, (-velocityY - THROW_V) / (THROW_V_FULL - THROW_V)))
            sendNow(1 + (PlaneMotion.THROW_MAX - 1) * k)
        }
        pullFrom = pull
        pullV = velocityY
        pullStart = now
        pullSpring = true
    }

    /** A tap that ended in time, without a drag. */
    fun tapped() = sendNow(1.0)

    /** The finger is gone (after a tap, a drag or a cancel). */
    fun touchUp() = animatePress(0.0, 160.0)

    private companion object {
        /** A drag up on the send button: how far it follows (dp), and what counts as a throw. */
        const val PULL_MAX = 84.0
        const val THROW_PT = 28.0
        const val THROW_V = 500.0
        const val THROW_V_FULL = 3000.0

        /** The button's spring back: stiffness 260, damping 15, mass 1. */
        const val PULL_Z = 7.5
        val PULL_W = sqrt(260 - PULL_Z * PULL_Z)

        /** A frame that arrives later than this (ms) is a stall: the film's clock does not count it. */
        const val STALL_MS = 100.0
        const val FRAME_MS = 1000.0 / 60
    }
}
