package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import com.ngocdevv.nativecook.blocks.gestures.cassettedeck.CassetteDeckMotion.Scrub
import com.ngocdevv.nativecook.blocks.gestures.cassettedeck.CassetteDeckSim.Companion.KEYS
import com.ngocdevv.nativecook.blocks.gestures.cassettedeck.CassetteDeckSim.Companion.SLOTS
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The frame loop and the touch hooks around the simulation (RN `useFrameCallback`, `touch`, `press`, `nudge`
 * and the cassette's pan gesture). Everything runs on the main thread, so nothing needs a lock.
 */
internal class CassetteDeckController(
    private val autoPlay: Boolean,
    private val onHaptic: (CassetteDeckHaptic) -> Unit,
) {
    val sim = CassetteDeckSim().also { it.k = CassetteDeckScript.loopScales }

    // The clock: fixed steps are taken out of the frame time, and the autoplay counts its own steps.
    private var mode = if (autoPlay) CassetteDeckMode.AUTOPLAY else CassetteDeckMode.FINGERS
    private var idle = 0.0
    private var accumulator = 0.0
    private var step = 0
    private var qaFrozenAt = Double.NaN

    // The cassette's pan: 0 slides the cassettes, 1 and 2 turn the left and right reel.
    private var grab = 0
    private var pageAtStart = 0.0
    private var lastOmega = 0.0

    /** True while something can still change what is drawn: the autoplay, a recent touch, or turning reels. */
    val isBusy: Boolean
        get() {
            if (autoPlay || idle < BUSY_SECONDS || abs(sim.v) > 0.001) return true
            for (j in 0 until SLOTS) if (abs(sim.w[j]) > 0.001) return true
            return false
        }

    /** One animation frame. `dtSeconds` is the time since the previous frame (RN default 16 ms for the first). */
    fun frame(dtSeconds: Double, reduced: Boolean) {
        sim.reduced = reduced
        if (CassetteDeckMotion.QA_HOLD != null) {
            qaHold()
            return
        }
        val qaT = CassetteDeckMotion.QA_T
        if (qaT != null) {
            qaFreeze(qaT)
            return
        }
        accumulator += min(0.05, dtSeconds)
        while (accumulator >= CassetteDeckMotion.STEP) {
            accumulator -= CassetteDeckMotion.STEP
            if (mode == CassetteDeckMode.AUTOPLAY) {
                stepAuto()
                continue
            }
            idle += CassetteDeckMotion.STEP
            var free = !sim.dragging && !sim.scrubbing
            for (i in 0 until KEYS) if (sim.held[i] != 0) free = false
            if (mode == CassetteDeckMode.FINGERS && autoPlay && free && idle * 1000.0 > INTERACTION_RESUME_MS) {
                mode = CassetteDeckMode.RETURNING
            }
            if (mode == CassetteDeckMode.RETURNING) {
                // Back to rest before the script resumes: the play key is released and the first cassette comes home.
                sim.latched = false
                sim.target = SLOTS * cassetteDeckRound(sim.p / SLOTS)
                var still = abs(sim.p - sim.target) < 0.002 && abs(sim.pv) < 0.01 && sim.v == 0.0
                for (i in 0 until KEYS) if (sim.d[i] > 0.002) still = false
                for (j in 0 until SLOTS) if (abs(sim.w[j]) > 0.003) still = false
                if (still) {
                    sim.resetToRest()
                    mode = CassetteDeckMode.AUTOPLAY
                    step = 0
                    continue
                }
            }
            sim.stepSim(CassetteDeckMotion.STEP, true)
        }
        if (mode != CassetteDeckMode.AUTOPLAY) {
            for (event in sim.events) onHaptic(event)
        }
        sim.events.clear()
    }

    /** Advances the autoplay one fixed step; wraps at the loop's end (RN `stepAuto`). */
    private fun stepAuto() {
        if (step >= CassetteDeckScript.loopSteps) {
            sim.resetToRest()
            step = 0
        }
        CassetteDeckScript.input(sim, step * CassetteDeckMotion.STEP)
        sim.stepSim(CassetteDeckMotion.STEP, false)
        step += 1
    }

    /** Holds a frozen state for the replica's screenshots (RN `qaHold`). */
    private fun qaHold() {
        sim.resetToRest()
        val hold = CassetteDeckMotion.QA_HOLD
        if (hold == "a" || hold == "b") {
            sim.latched = true
            sim.d[0] = 0.86
            sim.lit[0] = 1.0
            sim.led = 1.0
        }
        if (hold == "b") {
            sim.p = CassetteDeckMotion.QA_B_PAGE
            sim.d[2] = 1.0
            sim.lit[2] = 1.0
        }
    }

    /** Freezes the autoplay at a script time for proof frames (RN `QA_T`). */
    private fun qaFreeze(seconds: Double) {
        if (qaFrozenAt == seconds) return
        sim.resetToRest()
        step = 0
        val steps = cassetteDeckRound(seconds / CassetteDeckMotion.STEP).toInt()
        for (i in 0 until steps) stepAuto()
        qaFrozenAt = seconds
    }

    /** Any touch: the script lets go of the keys and the cassette, and the real fingers own the deck (RN `touch`). */
    fun touch() {
        if (mode == CassetteDeckMode.AUTOPLAY) {
            for (i in 0 until KEYS) sim.held[i] = 0
            if (sim.dragging) sim.dragTo(sim.p, sim.pv, true)
        }
        mode = CassetteDeckMode.FINGERS
        idle = 0.0
    }

    /** A finger goes down on (or lifts from) key `index` (RN `press`). */
    fun press(index: Int, down: Boolean) {
        touch()
        sim.pressKey(index, down)
    }

    /** The accessibility actions: one cassette forward or back (RN `nudge`). */
    fun nudge(direction: Int) {
        touch()
        sim.target = cassetteDeckRound(sim.target) + direction
        if (sim.reduced) {
            sim.p = sim.target
            sim.fade = 0.0
        }
    }

    /** The cassette's pan: finger down (RN `onBegin`). `x`, `y` are in the touch area's own coordinates. */
    fun cassetteBegin(x: Double, y: Double, geometry: CassetteDeckGeometry) {
        touch()
        grab = 0
        if (sim.loadedSlot() < 0 || sim.reduced) return
        for (reel in 0 until 2) {
            if (hypot(x - geometry.reelX(reel), y - geometry.reelY(reel)) < Scrub.grabRadius * geometry.s) grab = reel + 1
        }
    }

    /** The pan has moved far enough to count (RN `onStart`). */
    fun cassetteStart() {
        pageAtStart = sim.p
    }

    /**
     * The pan moved (RN `onUpdate`). `translationX` is since the pan started, `velocityX` and `velocityY` are in dp/s.
     * Grabbed on a reel, the finger's angular velocity turns the reel: omega = (r x v) / |r|^2.
     */
    fun cassetteUpdate(
        x: Double,
        y: Double,
        translationX: Double,
        velocityX: Double,
        velocityY: Double,
        geometry: CassetteDeckGeometry,
    ) {
        if (grab == 0) {
            sim.dragTo(pageAtStart - translationX / geometry.width, -velocityX / geometry.width, false)
            return
        }
        val rx = x - geometry.reelX(grab - 1)
        val ry = y - geometry.reelY(grab - 1)
        val r2 = max(rx * rx + ry * ry, 100.0 * geometry.s * geometry.s)
        val omega = (rx * velocityY - ry * velocityX) / r2
        lastOmega = omega
        sim.scrubTo(omega, packRadius(grab - 1), true)
    }

    /** The pan ended or was cancelled (RN `onFinalize`). */
    fun cassetteEnd(velocityX: Double, geometry: CassetteDeckGeometry) {
        if (sim.dragging) sim.dragTo(sim.p, -velocityX / geometry.width, true)
        if (sim.scrubbing) sim.scrubTo(lastOmega, packRadius(grab - 1), false)
        idle = 0.0
    }

    // The loaded cassette's tape pack under the finger. A reel is only grabbed while a cassette is loaded.
    private fun packRadius(reel: Int): Double {
        val slot = max(sim.loadedSlot(), 0)
        return CassetteDeckSim.tapeRadius(if (reel == 0) 1.0 - sim.prog[slot] else sim.prog[slot])
    }

    private companion object {
        // Product rule for this port: restart autoplay two seconds after the last touch. Keep the approved RN
        // RESUME_MS = 3500 literal in CassetteDeckMotion for source fidelity; this interaction policy overrides it.
        const val INTERACTION_RESUME_MS = 2000.0

        // Springs settle within a few seconds; after this long without a touch a still deck stops redrawing.
        const val BUSY_SECONDS = 10.0
    }
}
