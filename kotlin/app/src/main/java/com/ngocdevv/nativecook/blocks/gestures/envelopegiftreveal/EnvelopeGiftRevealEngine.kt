package com.ngocdevv.nativecook.blocks.gestures.envelopegiftreveal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

internal object EnvelopePhase {
    const val sealed = 0
    const val dragging = 1
    const val revealing = 2
    const val revealed = 3
    const val resetting = 4
}

internal enum class EnvelopeHaptic { Flap, Commit, Success, Light, Crack }

/** The scripted finger starts on the envelope's lower half. */
private const val ENVELOPE_FINGER_Y = 170f

/** Floats per petal: x, y, vx, vy, angle, spin, life, colour. */
private const val PETAL_STRIDE = 8

/** Product override: autoplay resumes two seconds after the last real touch ends. */
private const val AUTOPLAY_RESUME_AFTER_LAST_TOUCH_MS = 2_000f

/** Seeded noise for the petals, so every loop throws the same ones (use-envelope.ts hash). */
private fun envelopeHash(n: Int, salt: Int): Double {
    val x = sin(n * 91.7 + salt * 47.3) * 43758.5453
    return x - floor(x)
}

/**
 * The envelope's state (use-envelope.ts). Before the reveal commits, the finger owns `p`
 * (0 sealed to 1 card out). After it commits, every pose is a function of one clock and the commit
 * time, so the loop repeats. `width` and `centreY` place the confetti, `envTop` the scripted finger;
 * the last two are read each time because the safe area can arrive after the first frame.
 */
internal class EnvelopeGiftRevealEngine(
    private val width: Float,
    private val centreY: () -> Float,
    private val envTop: () -> Float,
    private val autoPlay: Boolean,
    private val reduced: Boolean,
    private val onPhase: (Int) -> Unit,
    private val onHaptic: (EnvelopeHaptic) -> Unit,
) {
    /** Bumped once per frame; drawing and layer lambdas read it so only they refresh. */
    var tick by mutableLongStateOf(0L)
        private set

    val frame = EnvelopeFrame()

    var clock = 0.0
        private set
    val p = EnvelopeAnimatedFloat()
    val tilt = EnvelopeAnimatedFloat()
    var phase = EnvelopePhase.sealed
        private set
    var commitAt = -1.0
        private set
    var commitP0 = 0f
        private set
    var commitTilt0 = 0f
        private set
    var resetAt = -1.0
        private set
    var breath = 0f
        private set

    /**
     * Ambient effects use the deterministic script clock during autoplay. Once reset starts, the
     * returning envelope is already rendered at the canonical rest sample, so frame 8_000 equals
     * frame 0 even though the ambient source periods do not divide the loop.
     */
    val visualClock: Double
        get() = if (autoPlay && scriptOn) {
            if (scriptT >= EnvelopeScript.reset) 0.0 else scriptT.toDouble()
        } else {
            clock
        }

    /** Petals: [PETAL_STRIDE] numbers each. */
    val confetti = DoubleArray(EnvelopeReveal.confetti * PETAL_STRIDE)

    private var touchActive = false
    private var touchScripted = false
    private var touchX0 = 0f
    private var touchY0 = 0f
    private var touchP0 = 0f
    private var touchFlapped = false
    private var touchCracked = false

    private var scriptOn = autoPlay
    private var scriptT = 0f
    private var scriptQuiet = 0f
    private var scriptTouching = false
    private var fired = false

    private fun setPhase(next: Int) {
        if (phase == next) return
        phase = next
        onPhase(next)
    }

    /** Scripted fingers feel nothing; only a real touch gets haptics. */
    private fun haptic(kind: EnvelopeHaptic) {
        if (!touchScripted) onHaptic(kind)
    }

    private fun doCommit() {
        commitAt = clock
        commitP0 = p.value
        commitTilt0 = tilt.value
        fired = false
        setPhase(EnvelopePhase.revealing)
        haptic(EnvelopeHaptic.Commit)
    }

    /** Interruption: a touch while the envelope is still opening (before the card leaves) reverses it. */
    private fun cancelReveal(): Boolean {
        val elapsed = (clock - commitAt).toFloat()
        val fin = envelopeFinishMs(commitP0, reduced)
        if (commitAt < 0.0 || elapsed >= fin) return false
        val u = envelopeClamp01(elapsed / fin)
        val now = commitP0 + (1f - commitP0) * (1f - (1f - u).pow(3))
        commitAt = -1.0
        commitP0 = 0f
        commitTilt0 = 0f
        p.set(now)
        closeFromCurrent()
        setPhase(EnvelopePhase.sealed)
        haptic(EnvelopeHaptic.Light)
        return true
    }

    fun down(x: Float, y: Float, scripted: Boolean) {
        if (phase == EnvelopePhase.revealing && !scripted && cancelReveal()) return
        if (phase != EnvelopePhase.sealed) return
        touchActive = true
        touchScripted = scripted
        touchX0 = x
        touchY0 = y
        touchP0 = p.value
        touchFlapped = false
        touchCracked = false
        // Freeze p where it is: a closing spring must not fight the finger.
        p.set(p.value)
        setPhase(EnvelopePhase.dragging)
    }

    fun move(x: Float, y: Float) {
        if (!touchActive || phase != EnvelopePhase.dragging) return
        var next = touchP0 + (touchY0 - y) / EnvelopeOpen.travel
        if (next < 0f) next = 0f
        if (next > 1f) next = 1f + (next - 1f) * 0.2f
        p.set(next)
        val out = max(0f, (next - EnvelopeOpen.flapShare) / (1f - EnvelopeOpen.flapShare))
        tilt.set(
            if (reduced) {
                0f
            } else {
                max(-EnvelopeOpen.tiltMax, min(EnvelopeOpen.tiltMax, (x - touchX0) * 0.08f)) * min(1f, out * 2f)
            },
        )
        // The seal cracks early in the lift (rigid tap), the flap clears at the fold (light tap).
        if (!touchCracked && next >= EnvelopeOpen.flapShare * 0.22f) {
            touchCracked = true
            haptic(EnvelopeHaptic.Crack)
        }
        if (!touchFlapped && next >= EnvelopeOpen.flapShare) {
            touchFlapped = true
            haptic(EnvelopeHaptic.Flap)
        }
    }

    /** [vy] is the finger's vertical velocity in dp per second (negative is up). */
    fun up(vy: Float) {
        if (!touchActive) return
        touchActive = false
        if (phase != EnvelopePhase.dragging) return
        if (p.value >= EnvelopeOpen.commit || (vy < -900f && p.value > 0.15f)) {
            doCommit()
            return
        }
        setPhase(EnvelopePhase.sealed)
        if (p.value > 0.08f) haptic(EnvelopeHaptic.Light)
        closeFromCurrent(velocity = -vy / EnvelopeOpen.travel)
    }

    /** The button, and the accessible alternative: open without dragging. */
    fun open() {
        if (phase != EnvelopePhase.sealed) return
        touchScripted = false
        doCommit()
    }

    fun reset() {
        if (phase != EnvelopePhase.revealed && phase != EnvelopePhase.revealing) return
        resetAt = clock
        setPhase(EnvelopePhase.resetting)
    }

    /** Reanimated's system Reduce Motion setting makes `withSpring` finish immediately. */
    private fun closeFromCurrent(velocity: Float = 0f) {
        if (reduced) {
            p.set(0f)
            tilt.set(0f)
            return
        }
        p.springTo(0f, EnvelopeSprings.close, velocity)
        tilt.springTo(0f, EnvelopeSprings.close)
    }

    private fun spawn() {
        if (reduced) return
        for (n in 0 until EnvelopeReveal.confetti) {
            val o = n * PETAL_STRIDE
            val angle = -PI / 2 + (envelopeHash(n, 1) - 0.5) * PI * 1.5
            val speed = 260 + envelopeHash(n, 2) * 620
            confetti[o] = width / 2.0 + (envelopeHash(n, 3) - 0.5) * 120
            confetti[o + 1] = centreY() + (envelopeHash(n, 4) - 0.5) * 60
            confetti[o + 2] = cos(angle) * speed
            confetti[o + 3] = sin(angle) * speed
            confetti[o + 4] = envelopeHash(n, 5) * PI
            confetti[o + 5] = (envelopeHash(n, 6) - 0.5) * 16
            confetti[o + 6] = EnvelopeReveal.confettiLife * (0.6 + 0.4 * envelopeHash(n, 7))
            confetti[o + 7] = floor(envelopeHash(n, 8) * 6)
        }
    }

    /** A touch starts: the script stops, and its own finger lets the flap fall closed. */
    fun takeOver() {
        if (scriptOn && touchScripted && touchActive) {
            touchActive = false
            setPhase(EnvelopePhase.sealed)
            closeFromCurrent()
        }
        scriptOn = false
        scriptQuiet = 0f
        scriptTouching = true
    }

    fun letGo() {
        scriptTouching = false
        scriptQuiet = 0f
    }

    /** One frame (the useFrameCallback body). [dtMs] is capped at 64 by the caller. */
    fun onFrame(dtMs: Float) {
        val dt = dtMs
        val now = clock + dt
        clock = now
        p.step(dt / 1000f)
        tilt.step(dt / 1000f)

        if (commitAt >= 0.0) {
            val fin = envelopeFinishMs(commitP0, reduced)
            val done = fin + (if (reduced) 0f else EnvelopeReveal.flyMs)
            val sinceCommit = (now - commitAt).toFloat()
            if (!fired && sinceCommit >= fin + EnvelopeReveal.flyMs * 0.45f) {
                fired = true
                spawn()
                haptic(EnvelopeHaptic.Success)
            }
            if (phase == EnvelopePhase.revealing && sinceCommit >= done) setPhase(EnvelopePhase.revealed)
        }
        if (resetAt >= 0.0 && (now - resetAt).toFloat() >= (if (reduced) REDUCED_MS else EnvelopeReset.fadeMs)) {
            resetAt = -1.0
            commitAt = -1.0
            commitP0 = 0f
            commitTilt0 = 0f
            p.set(0f)
            tilt.set(0f)
            setPhase(EnvelopePhase.sealed)
        }
        if (!reduced) stepConfetti(dt / 1000f)

        // The flap lifts a little now and then while sealed, to say it opens.
        breath = (if (scriptOn) scriptT else (now % EnvelopeOpen.breathMs.toDouble()).toFloat()) % EnvelopeOpen.breathMs
        if (autoPlay) stepScript(dt)
        frame.update(this, reduced)
        tick += 1
    }

    private fun stepConfetti(k: Float) {
        for (n in 0 until EnvelopeReveal.confetti) {
            val o = n * PETAL_STRIDE
            if (confetti[o + 6] <= 0.0) continue
            confetti[o + 2] *= EnvelopeReveal.drag
            confetti[o + 3] = confetti[o + 3] * EnvelopeReveal.drag + EnvelopeReveal.gravity * k
            confetti[o] += confetti[o + 2] * k + sin(confetti[o + 4] * 2) * 0.4
            confetti[o + 1] += confetti[o + 3] * k
            confetti[o + 4] += confetti[o + 5] * k
            confetti[o + 6] -= k
        }
    }

    /** The scripted finger: one clock, looping every [LOOP_MS]. */
    private fun stepScript(dt: Float) {
        if (!scriptOn) {
            val idle = !scriptTouching && !touchActive
            val quiet = if (idle) scriptQuiet + dt else 0f
            if (quiet >= AUTOPLAY_RESUME_AFTER_LAST_TOUCH_MS && phase == EnvelopePhase.revealed) {
                reset()
                // Keep the elapsed quiet time: restart as soon as the reset reaches sealed.
                scriptQuiet = quiet
                return
            }
            scriptQuiet = quiet
            scriptOn = quiet >= AUTOPLAY_RESUME_AFTER_LAST_TOUCH_MS &&
                phase == EnvelopePhase.sealed &&
                p.value < 0.01f
            scriptT = 0f
            return
        }
        val prev = scriptT
        val holdAt = QA_HOLD_AT
        val t = if (holdAt != null && prev >= holdAt) prev else prev + dt
        val x0 = width / 2f + 10f
        val y0 = envTop() + ENVELOPE_FINGER_Y
        if (prev < EnvelopeScript.down && t >= EnvelopeScript.down) down(x0, y0, true)
        if (t > EnvelopeScript.down && prev < EnvelopeScript.up) {
            val u = envelopeClamp01((t - EnvelopeScript.down - 80f) / (EnvelopeScript.up - EnvelopeScript.down - 150f))
            val e = envelopeEaseInOut(u)
            // Up with a slight drift to the right and back, so the card swings as it comes out.
            move(x0 + 34f * sin(PI.toFloat() * e), y0 - EnvelopeScript.rise * e)
        }
        if (prev < EnvelopeScript.up && t >= EnvelopeScript.up) up(0f)
        if (prev < EnvelopeScript.reset && t >= EnvelopeScript.reset) reset()
        scriptT = if (t >= LOOP_MS) t - LOOP_MS else t
    }
}
