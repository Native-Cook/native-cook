package com.ngocdevv.nativecook.blocks.progress.breathvolumelungs

import com.ngocdevv.nativecook.blocks.progress.breathvolumelungs.BreathVolumeLungsMotion as M
import com.ngocdevv.nativecook.blocks.progress.breathvolumelungs.BreathVolumeLungsTheme as Theme
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The block's state machine (port of the frame callback and gestures in bvl-scene.tsx).
 * phase: 0 autoplay/idle, 1 hold, 2 scrub, 3 coast, 4 result, 5 refill, 7 accessible single test.
 */
internal class BreathVolumeLungsEngine(val autoPlay: Boolean, val reduced: Boolean, val layout: Theme.Layout) {
    val maxVolume = Theme.CAPACITY
    private val crossings = M.litreTimes(maxVolume)

    var p = 0.0
    var flow = 0.0
    var elapsed = 0.0
    var clock = 0.0
    var phase = 0
    var anchor = 0.0
    var initialP = 0.0
    var releaseVelocity = 0.0
    var releaseOver = 0.0
    var dragOffset = 0.0
    var touched = false
    var lastPack = -10.0
    var airPeak = -10.0
    var fontT = 0.0
    private var originReset = false
    private var resultAlpha = 0.0
    private var prevDone: Boolean? = null
    private var prevLitre = Int.MIN_VALUE
    private var prevNear = false
    private var overshootReturn = false

    val pSpring = BreathVolumeLungsSpring()
    val pressed = BreathVolumeLungsSpring()
    val overshoot = BreathVolumeLungsSpring()
    val resetPress = BreathVolumeLungsSpring()
    val finishPress = BreathVolumeLungsSpring()

    var cue: (Int) -> Unit = {}
    var announce: (Double) -> Unit = {}
    var dirty = true

    // layout derived
    private val s = layout.s
    val k = layout.k
    val knobY = layout.cardY + (Theme.KNOB_Y - Theme.CARD_Y) * s * k
    val knobX0 = Theme.KNOB_X0 * s
    val knobX1 = Theme.KNOB_X1 * s
    val travel = knobX1 - knobX0
    val buttonY = min(layout.cardY + (Theme.PILL_Y - Theme.CARD_Y) * s * k, layout.h - layout.safeBottom - Theme.PILL_H * s * k)
    val buttonH = Theme.PILL_H * s * k
    val resetX = (Theme.REFRESH_CX - Theme.REFRESH_R) * s
    val resetW = 60 * s
    val finishX = resetX + resetW + Theme.CONTROLS_GAP * s
    val finishW = 194 * s

    val a11yReading: Double get() = M.clamp(p) * maxVolume
    fun fontAlpha(): Double = ease(fontT)
    fun resultEased(): Double = ease(resultAlpha)
    private fun ease(x: Double): Double { val a = M.clamp(x); return if (a < 0.5) 2 * a * a else 1 - 2 * (1 - a) * (1 - a) }

    fun captionAlpha(): Double = if (phase == 0 || phase == 6) M.resultAt(clock) else resultEased()

    fun wake() { originReset = true; dirty = true }

    fun needsFrame(): Boolean =
        (autoPlay && !reduced) || (phase in 1..5) || phase == 7 || pSpring.moving || pressed.moving || overshoot.moving ||
            resetPress.moving || finishPress.moving || fontT < 1.0 || resultAlpha != (if (phase == 4) 1.0 else 0.0)

    fun frame(dt0: Double) {
        val dt = min(0.05, dt0)
        val old = M.clamp(p)
        if (originReset) originReset = false else clock += dt
        val now = clock
        val ph = phase
        fontT = min(1.0, fontT + dt / 0.18)
        if (pSpring.moving) {
            pSpring.step(dt)
            p = pSpring.x
            if (pSpring.justFinished && phase == 5 && pSpring.target == 0.0) rest()
        }
        if (ph == 0 && autoPlay && !reduced) {
            val t = now % M.LOOP
            p = M.progressAt(t)
            flow = M.flowAt(t, maxVolume)
            elapsed = M.elapsedAt(t)
        } else if (ph == 1 || ph == 7) {
            p = M.heldAt(initialP, now - anchor)
            elapsed += dt
            if (ph == 7 && p >= 1) {
                phase = 4
                anchor = now
                announce(maxVolume)
            }
        } else if (ph == 3) {
            val age = now - anchor
            p = M.coastAt(initialP, releaseVelocity, age) + releaseOver * (1 - M.springAt(age, 0.35, 0.85))
            if (age > M.COAST) {
                p = M.clamp(p)
                phase = 4
            }
        } else if (ph == 4 && now - anchor > M.IDLE) {
            phase = 5
            pSpring.x = p
            pSpring.to(0.0, M.GLIDE)
        }
        if (ph == 1 || ph == 3 || ph == 5 || ph == 7) {
            flow = if (dt > 0) M.clamp((M.clamp(p) - old) / dt * maxVolume, -M.FLOW_PEAK, M.FLOW_PEAK) else 0.0
        } else if (ph == 4) flow = 0.0

        pressed.step(dt); resetPress.step(dt); finishPress.step(dt)
        overshoot.step(dt)
        if (overshoot.justFinished && overshootReturn) { overshootReturn = false; overshoot.to(0.0, M.INHALE) }

        // reactions
        val done = phase == 4
        if (done != prevDone) {
            if (done && prevDone == false && touched) cue(BreathVolumeLungsCue.RESULT)
            prevDone = done
        }
        val target = if (done) 1.0 else 0.0
        resultAlpha = if (resultAlpha < target) min(target, resultAlpha + dt / 0.2) else max(target, resultAlpha - dt / 0.2)
        val litre = floor(M.clamp(p) * maxVolume).toInt()
        if (prevLitre != Int.MIN_VALUE && litre > prevLitre && touched) {
            lastPack = clock
            cue(BreathVolumeLungsCue.LITRE)
        }
        prevLitre = litre
        val near = M.clamp(p) >= 0.98
        if (near && !prevNear) airPeak = clock
        prevNear = near
        dirty = true
    }

    private fun rest() {
        phase = 0; clock = 0.0; originReset = true; elapsed = 0.0; flow = 0.0; touched = false
    }

    // ---- hold (press and hold anywhere above the card) ----
    fun holdBegin() {
        pSpring.moving = false
        initialP = M.clamp(p)
        anchor = clock
        touched = true
        phase = 1
        pressed.to(1.0, M.SNAP)
        wake(); cue(BreathVolumeLungsCue.ONSET)
    }

    fun holdEnd() {
        if (phase == 1) {
            initialP = M.clamp(p)
            releaseVelocity = flow / maxVolume
            releaseOver = 0.0
            anchor = clock
            phase = if (reduced) 4 else 3
            announce(M.clamp(p) * maxVolume)
        }
        pressed.to(0.0, M.SNAP)
        dirty = true
    }

    // ---- drag on the knob ----
    fun dragBegin() {
        pSpring.moving = false
        initialP = p
        dragOffset = 0.0
        anchor = clock
        touched = true
        phase = 1
        pressed.to(1.0, M.SNAP)
        flow = 0.0
        wake(); cue(BreathVolumeLungsCue.ONSET)
    }

    fun dragUpdate(translationX: Double, velocityX: Double) {
        if (phase == 1) {
            if (kotlin.math.abs(translationX) < 1) return
            initialP = p
            dragOffset = translationX
            phase = 2
        }
        p = M.rubberAt(initialP + (translationX - dragOffset) / travel, travel)
        flow = M.clamp(velocityX / travel * maxVolume, -M.FLOW_PEAK, M.FLOW_PEAK)
        dirty = true
    }

    fun dragEnd(velocityX: Double) {
        val v = if (phase == 1) flow / maxVolume else velocityX / travel
        val value = M.clamp(p)
        releaseOver = p - value
        if (reduced) p = value
        initialP = value
        releaseVelocity = v
        anchor = clock
        phase = if (reduced) 4 else 3
        if (v < -1.2 && !reduced) { overshootReturn = true; overshoot.to(0.03, M.INHALE) }
        announce(value * maxVolume)
        dirty = true
    }

    fun dragFinalize() {
        pressed.to(0.0, M.SNAP)
        if (phase == 2 || phase == 1) { phase = 4; anchor = clock }
        dirty = true
    }

    // ---- buttons ----
    fun finish() {
        phase = 4; anchor = clock; touched = true; flow = 0.0
        pressed.set(0.0)
        cue(BreathVolumeLungsCue.RESULT)
        announce(M.clamp(p) * maxVolume)
        wake()
    }

    fun reset() {
        pSpring.moving = false
        phase = 5
        touched = true
        cue(BreathVolumeLungsCue.ONSET)
        pSpring.x = p
        pSpring.to(0.0, M.GLIDE)
        wake()
    }

    /** Accessible single test (double tap on the screen's button). */
    fun a11yTest() {
        touched = true
        cue(BreathVolumeLungsCue.ONSET)
        if (reduced) {
            phase = 4; anchor = clock
            pSpring.x = p
            pSpring.to(1.0, M.SpringSpec(26.0, 400.0, 1.0))
            announce(maxVolume)
        } else {
            pSpring.moving = false
            initialP = M.clamp(p)
            anchor = clock
            phase = 7
        }
        wake()
    }

    fun nudge(direction: Int) {
        val value = M.clamp(p + direction * 0.5 / maxVolume)
        phase = 4; anchor = clock; touched = true
        pSpring.x = p
        pSpring.to(value, M.SNAP)
        cue(BreathVolumeLungsCue.ONSET)
        announce(value * maxVolume)
        wake()
    }

    // ---- hit zones (dp) ----
    enum class Hit { NONE, HOLD, KNOB, RESET, FINISH }

    fun hit(x: Double, y: Double): Hit {
        if (y >= buttonY && y <= buttonY + buttonH) {
            if (x >= resetX && x <= resetX + resetW) return Hit.RESET
            if (x >= finishX && x <= finishX + finishW) return Hit.FINISH
        }
        val kl = max(20.0, knobX0 - 24 * s)
        if (x >= kl && x <= layout.w - 20 && y >= knobY - 28 * s && y <= knobY + 28 * s) return Hit.KNOB
        if (x >= 20 && x <= layout.w - 20 && y >= layout.safeTop + 80 * s && y <= knobY - 30 * s) return Hit.HOLD
        return Hit.NONE
    }
}
