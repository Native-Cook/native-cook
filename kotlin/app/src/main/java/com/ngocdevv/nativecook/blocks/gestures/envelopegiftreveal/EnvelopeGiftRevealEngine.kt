package com.ngocdevv.nativecook.blocks.gestures.envelopegiftreveal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

internal object EnvelopeGiftRevealPhase {
    const val sealed = 0
    const val dragging = 1
    const val revealing = 2
    const val revealed = 3
    const val resetting = 4
}

internal enum class EnvelopeGiftRevealHaptic { Flap, Commit, Success, Light, Crack }

private const val ENVELOPE_FINGER_Y = 170f
private const val AUTOPLAY_RESUME_AFTER_LAST_TOUCH_MS = 2_000f

internal class EnvelopeGiftRevealEngine(
    private val width: Float,
    private val envTop: () -> Float,
    private val autoPlay: Boolean,
    private val reduced: Boolean,
    private val onPhase: (Int) -> Unit,
    private val onHaptic: (EnvelopeGiftRevealHaptic) -> Unit,
) {
    /** Only drawing and graphics-layer lambdas observe this per-frame state. */
    var tick by mutableLongStateOf(0L)
        private set

    val frame = EnvelopeGiftRevealFrame()
    var clock = 0.0
        private set
    val p = EnvelopeGiftRevealAnimatedFloat()
    val tilt = EnvelopeGiftRevealAnimatedFloat()
    var phase = EnvelopeGiftRevealPhase.sealed
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
    private var successFired = false

    private fun setPhase(next: Int) {
        if (phase == next) return
        phase = next
        onPhase(next)
    }

    private fun haptic(kind: EnvelopeGiftRevealHaptic) {
        if (!touchScripted) onHaptic(kind)
    }

    private fun commit() {
        commitAt = clock
        commitP0 = p.value
        commitTilt0 = tilt.value
        successFired = false
        setPhase(EnvelopeGiftRevealPhase.revealing)
        haptic(EnvelopeGiftRevealHaptic.Commit)
    }

    private fun cancelReveal(): Boolean {
        val elapsed = (clock - commitAt).toFloat()
        val finish = envelopeGiftRevealFinishMs(commitP0, reduced)
        if (commitAt < 0.0 || elapsed >= finish) return false
        val progress = envelopeGiftRevealClamp01(elapsed / finish)
        val current = commitP0 + (1f - commitP0) * (1f - (1f - progress).pow(3))
        commitAt = -1.0
        commitP0 = 0f
        commitTilt0 = 0f
        p.set(current)
        closeFromCurrent()
        setPhase(EnvelopeGiftRevealPhase.sealed)
        haptic(EnvelopeGiftRevealHaptic.Light)
        return true
    }

    fun down(x: Float, y: Float, scripted: Boolean) {
        if (phase == EnvelopeGiftRevealPhase.revealing && !scripted && cancelReveal()) return
        if (phase != EnvelopeGiftRevealPhase.sealed) return
        touchActive = true
        touchScripted = scripted
        touchX0 = x
        touchY0 = y
        touchP0 = p.value
        touchFlapped = false
        touchCracked = false
        p.set(p.value)
        setPhase(EnvelopeGiftRevealPhase.dragging)
    }

    fun move(x: Float, y: Float) {
        if (!touchActive || phase != EnvelopeGiftRevealPhase.dragging) return
        var next = touchP0 + (touchY0 - y) / EnvelopeGiftRevealOpen.travel
        if (next < 0f) next = 0f
        if (next > 1f) next = 1f + (next - 1f) * 0.2f
        p.set(next)
        val cardOut = max(
            0f,
            (next - EnvelopeGiftRevealOpen.flapShare) / (1f - EnvelopeGiftRevealOpen.flapShare),
        )
        val horizontal = ((x - touchX0) * 0.08f).coerceIn(
            -EnvelopeGiftRevealOpen.tiltMax,
            EnvelopeGiftRevealOpen.tiltMax,
        )
        tilt.set(if (reduced) 0f else horizontal * min(1f, cardOut * 2f))
        if (!touchCracked && next >= EnvelopeGiftRevealOpen.flapShare * 0.22f) {
            touchCracked = true
            haptic(EnvelopeGiftRevealHaptic.Crack)
        }
        if (!touchFlapped && next >= EnvelopeGiftRevealOpen.flapShare) {
            touchFlapped = true
            haptic(EnvelopeGiftRevealHaptic.Flap)
        }
    }

    fun up(velocityY: Float) {
        if (!touchActive) return
        touchActive = false
        if (phase != EnvelopeGiftRevealPhase.dragging) return
        if (p.value >= EnvelopeGiftRevealOpen.commit ||
            (velocityY < -900f && p.value > 0.15f)
        ) {
            commit()
            return
        }
        setPhase(EnvelopeGiftRevealPhase.sealed)
        if (p.value > 0.08f) haptic(EnvelopeGiftRevealHaptic.Light)
        closeFromCurrent(velocity = -velocityY / EnvelopeGiftRevealOpen.travel)
    }

    fun open() {
        if (phase != EnvelopeGiftRevealPhase.sealed) return
        touchScripted = false
        commit()
    }

    fun reset() {
        if (phase != EnvelopeGiftRevealPhase.revealed && phase != EnvelopeGiftRevealPhase.revealing) return
        resetAt = clock
        setPhase(EnvelopeGiftRevealPhase.resetting)
    }

    private fun closeFromCurrent(velocity: Float = 0f) {
        if (reduced) {
            p.set(0f)
            tilt.set(0f)
            return
        }
        p.springTo(0f, EnvelopeGiftRevealSprings.close, velocity)
        tilt.springTo(0f, EnvelopeGiftRevealSprings.close)
    }

    fun takeOver() {
        if (scriptOn && touchScripted && touchActive) {
            touchActive = false
            setPhase(EnvelopeGiftRevealPhase.sealed)
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

    fun onFrame(deltaMs: Float) {
        val now = clock + deltaMs
        clock = now
        p.step(deltaMs / 1_000f)
        tilt.step(deltaMs / 1_000f)
        if (commitAt >= 0.0) {
            val finish = envelopeGiftRevealFinishMs(commitP0, reduced)
            val sinceCommit = (now - commitAt).toFloat()
            val done = finish + if (reduced) 0f else EnvelopeGiftRevealReveal.flyMs
            if (!successFired && sinceCommit >= finish + EnvelopeGiftRevealReveal.flyMs * 0.45f) {
                successFired = true
                haptic(EnvelopeGiftRevealHaptic.Success)
            }
            if (phase == EnvelopeGiftRevealPhase.revealing && sinceCommit >= done) {
                setPhase(EnvelopeGiftRevealPhase.revealed)
            }
        }
        val resetDuration = if (reduced) REDUCED_MS else EnvelopeGiftRevealReset.fadeMs
        if (resetAt >= 0.0 && (now - resetAt).toFloat() >= resetDuration) {
            resetAt = -1.0
            commitAt = -1.0
            commitP0 = 0f
            commitTilt0 = 0f
            p.set(0f)
            tilt.set(0f)
            setPhase(EnvelopeGiftRevealPhase.sealed)
        }
        val breathClock = if (scriptOn) scriptT else now.toFloat()
        breath = (breathClock + EnvelopeGiftRevealOpen.breathLead) % EnvelopeGiftRevealOpen.breathMs
        if (autoPlay) stepScript(deltaMs)
        frame.update(this, reduced)
        tick += 1L
    }

    private fun stepScript(deltaMs: Float) {
        if (!scriptOn) {
            val quiet = if (!scriptTouching && !touchActive) scriptQuiet + deltaMs else 0f
            if (quiet >= AUTOPLAY_RESUME_AFTER_LAST_TOUCH_MS && phase == EnvelopeGiftRevealPhase.revealed) {
                reset()
                scriptQuiet = quiet
                return
            }
            scriptQuiet = quiet
            scriptOn = quiet >= AUTOPLAY_RESUME_AFTER_LAST_TOUCH_MS &&
                phase == EnvelopeGiftRevealPhase.sealed &&
                p.value < 0.01f
            scriptT = 0f
            return
        }
        val previous = scriptT
        val hold = QA_HOLD_AT
        val time = if (hold != null && previous >= hold) previous else previous + deltaMs
        val x0 = width / 2f + 10f
        val y0 = envTop() + ENVELOPE_FINGER_Y
        if (previous < EnvelopeGiftRevealScript.down && time >= EnvelopeGiftRevealScript.down) {
            down(x0, y0, scripted = true)
        }
        if (time > EnvelopeGiftRevealScript.down && previous < EnvelopeGiftRevealScript.up) {
            val progress = envelopeGiftRevealClamp01(
                (time - EnvelopeGiftRevealScript.down - 80f) /
                    (EnvelopeGiftRevealScript.up - EnvelopeGiftRevealScript.down - 150f),
            )
            val eased = envelopeGiftRevealEaseInOut(progress)
            move(
                x0 + 34f * sin(PI.toFloat() * eased),
                y0 - EnvelopeGiftRevealScript.rise * eased,
            )
        }
        if (previous < EnvelopeGiftRevealScript.up && time >= EnvelopeGiftRevealScript.up) up(0f)
        if (previous < EnvelopeGiftRevealScript.reset && time >= EnvelopeGiftRevealScript.reset) reset()
        scriptT = if (time >= LOOP_MS) time - LOOP_MS else time
    }
}
