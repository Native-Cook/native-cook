package com.ngocdevv.nativecook.blocks.modals.gelcubeunbox

import com.ngocdevv.nativecook.blocks.modals.gelcubeunbox.GelCubeUnboxAnim.Step as AnimStep
import com.ngocdevv.nativecook.blocks.modals.gelcubeunbox.GelCubeUnboxMotion as M
import kotlin.math.abs
import kotlin.math.max

/** Cues the host turns into haptics and sound (touch only; the loop is silent). */
internal object GelCubeUnboxBeat {
    const val GRAB = 0 // touch down: soft; the core 1 -> 1.25
    const val SEAL = 1 // first 6 dp up: selection; the hairline leak (air sound)
    const val THRESHOLD = 2 // first crossing of p = .45: light
    const val CLOSE = 3 // closed contact after the two-frame +20 % flash: medium (contact sound)
}

/**
 * The film's controller (RN `gcu-reveal.tsx` + `gcu-playback.ts`): the autoplay clock, the drag that lifts the lid (rubber band, seal leak, threshold,
 * flick), the action button's press, the resume timer, the close flash and the cues. One value `p` (0 sealed .. 1 open) plus `sealOffset` (the seal's
 * leak, a spring back to 0) drives the picture. Main thread, one `frame` per vsync.
 *   mode 0  the timeline plays: p = progressAt(t)
 *   mode 1  a finger (or nothing) moves p
 * Two seconds after the last touch the lid closes in 0.6 s and the timeline restarts at 0.
 */
internal class GelCubeUnboxEngine(
    val autoPlay: Boolean,
    val reduce: Boolean,
    val controlled: Boolean,
    initialOpen: Boolean,
    val lidWeight: Double,
    val s: Double,
) {
    var cue: (Int) -> Unit = {}
    var onCommit: (Boolean) -> Unit = {}
    var onProgress: ((Double) -> Unit)? = null
    var onSemanticOpen: (Boolean) -> Unit = {}

    /** the host's `open` when controlled */
    var controlledOpen = initialOpen

    private val filmEnabled = autoPlay && !controlled && !reduce
    val p = GelCubeUnboxAnim(if (initialOpen) 1.0 else 0.0)
    val sealOffset = GelCubeUnboxAnim(0.0)
    val manualCore = GelCubeUnboxAnim(1.0)
    val closeLead = GelCubeUnboxAnim(0.0)
    var t = 0.0
        private set
    var mode = if (filmEnabled && !initialOpen) 0 else 1
        private set
    var flash = 0.0
        private set
    var pressed = false
        private set

    private var filmActive = false
    private var origin = -1.0
    private var start = 0.0
    private var dragging = false
    private var sealBroken = false
    private var crossed = false
    private var closing = false
    private var lastCallback = -1e9
    private var lastHaptic = -1e9
    private var lastValue: Double? = null
    private var flashReset = -1.0
    private var deadline = -1.0
    private var clock = 0.0

    init {
        if (filmEnabled && !initialOpen) restart()
    }

    val animating: Boolean
        get() = p.running || sealOffset.running || manualCore.running || closeLead.running || (filmActive && mode == 0)

    /** The visible progress: p plus the seal's leak. */
    val progress: Double get() = p.value + sealOffset.value

    // ---- playback (gcu-playback.ts) ----

    private fun stopFilm() {
        deadline = -1.0
        filmActive = false
    }

    private fun restart() {
        if (!filmEnabled) return
        t = 0.0
        origin = -1.0
        mode = 0
        filmActive = true
    }

    /** The clock was suspended (the app left the foreground): continue the timeline from where it stopped. */
    fun resumeClock() {
        if (mode == 0 && filmActive) origin = -1.0
    }

    private fun afterTouch() {
        if (!filmEnabled) return
        deadline = clock + M.RESUME_MS
    }

    private fun resumeFire() {
        closing = true
        closeLead.set(M.FAN_LEAD)
        p.run(
            AnimStep.Timing(
                0.0, M.RESET,
                done = {
                    closing = false
                    restart()
                },
            ),
        )
    }

    /** One frame; `nowMs` is the frame time, `dt` the seconds since the last frame. Returns true while the picture is moving. */
    fun frame(nowMs: Double, dt: Double): Boolean {
        clock = nowMs
        p.step(dt)
        sealOffset.step(dt)
        manualCore.step(dt)
        closeLead.step(dt)
        if (filmActive && mode == 0) {
            if (origin < 0) origin = nowMs - t * 1000
            val time = ((nowMs - origin) / 1000) % M.LOOP
            t = time
            p.set(M.progressAt(time))
        }
        if (deadline > 0 && nowMs >= deadline) {
            deadline = -1.0
            resumeFire()
        }
        react()
        if (flashReset > 0 && nowMs >= flashReset) {
            flash = 0.0
            flashReset = -1.0
        }
        return animating
    }

    /** RN's `useAnimatedReaction` on p + sealOffset. */
    private fun react() {
        val value = progress
        val prev = lastValue
        if (prev != null && value == prev) return
        lastValue = value
        val now = clock
        val isOpen = value >= 0.8
        if (prev == null || isOpen != (prev >= 0.8)) onSemanticOpen(isOpen)
        val report = onProgress
        if (report != null && now - lastCallback >= M.CALLBACK_MS) {
            lastCallback = now
            report(M.clamp01(value))
        }
        if (mode == 1 && closing && prev != null && prev > M.HAIRLINE && value <= M.HAIRLINE) {
            // the two-frame flash as the lid meets the body
            flash = M.FLASH_GAIN
            flashReset = now + M.FLASH * 1000
        }
        if (mode == 1 && closing && prev != null && value <= M.CONTACT && prev > M.CONTACT) {
            closing = false
            if (now - lastHaptic >= M.CUE_INTERVAL_MS) {
                lastHaptic = now
                cue(GelCubeUnboxBeat.CLOSE)
            }
        }
    }

    private fun lid(to: Double, mass: Double = lidWeight, velocity: Double? = null) =
        AnimStep.Spring(to, GelCubeUnboxSpring(M.SPRING_LID.damping, M.SPRING_LID.stiffness, mass), velocity)

    private fun close(to: Double, mass: Double = M.SPRING_CLOSE.mass, velocity: Double? = null) =
        AnimStep.Spring(to, GelCubeUnboxSpring(M.SPRING_CLOSE.damping, M.SPRING_CLOSE.stiffness, mass), velocity)

    /** A change of the host's `open`: the lid follows (a spring, or a fade with Reduce Motion). */
    fun onControlledChange(open: Boolean) {
        controlledOpen = open
        if (!controlled) return
        mode = 1
        p.cancel()
        p.run(if (reduce) AnimStep.Timing(if (open) 1.0 else 0.0, M.FADE) else lid(if (open) 1.0 else 0.0))
    }

    /** The action button or TalkBack: open when sealed, close when open. */
    fun toggle() {
        stopFilm()
        val current = progress
        val next = current < M.THRESHOLD
        p.cancel()
        mode = 1
        sealOffset.set(0.0)
        manualCore.set(1.0)
        closing = !next
        closeLead.set(if (next) 0.0 else M.FAN_LEAD)
        cue(if (next) GelCubeUnboxBeat.SEAL else GelCubeUnboxBeat.GRAB)
        val target = if (controlled) (if (controlledOpen) 1.0 else 0.0) else if (next) 1.0 else 0.0
        p.run(if (reduce) AnimStep.Timing(target, M.FADE) else lid(target))
        onCommit(next)
        afterTouch()
    }

    // ---- the drag on the cube ----

    fun panBegin() {
        val wasFilm = mode != 1
        p.cancel()
        sealOffset.cancel()
        val current = progress
        p.set(current)
        sealOffset.set(0.0)
        start = current
        mode = 1
        dragging = false
        closing = false
        closeLead.set(0.0)
        flash = 0.0
        flashReset = -1.0
        sealBroken = current > M.HAIRLINE
        crossed = current > M.THRESHOLD
        if (wasFilm) manualCore.set(M.coreAt(t))
        manualCore.run(AnimStep.Spring(M.PRESS_CORE, M.SPRING_CORE))
        lastHaptic = clock
        stopFilm()
        cue(GelCubeUnboxBeat.GRAB)
    }

    /** `translationY` in dp from where the finger landed. */
    fun panUpdate(translationY: Double) {
        val distance = -translationY / s
        dragging = abs(distance) >= M.SEAL
        if (!sealBroken && distance < M.SEAL && start < M.HAIRLINE) {
            p.set(M.rubber(start + (distance * M.RESISTANCE) / M.DRAG))
            return
        }
        if (!sealBroken && distance >= M.SEAL) {
            sealBroken = true
            sealOffset.set((-M.SEAL * (1 - M.RESISTANCE)) / M.DRAG)
            sealOffset.run(AnimStep.Spring(0.0, M.SPRING_SEAL))
            if (clock - lastHaptic >= M.CUE_INTERVAL_MS) {
                lastHaptic = clock
                cue(GelCubeUnboxBeat.SEAL)
            }
        }
        val next = M.rubber(start + distance / M.DRAG)
        p.set(next)
        if (!crossed && next >= M.THRESHOLD) {
            crossed = true
            if (clock - lastHaptic >= M.CUE_INTERVAL_MS) {
                lastHaptic = clock
                cue(GelCubeUnboxBeat.THRESHOLD)
            }
        }
    }

    /** `velocityY` in dp per second. */
    fun panEnd(velocityY: Double) {
        manualCore.run(AnimStep.Spring(1.0, M.SPRING_CORE))
        if (!dragging) {
            p.run(close(if (controlled) (if (controlledOpen) 1.0 else 0.0) else start))
            afterTouch()
            return
        }
        val velocity = -velocityY / s / M.DRAG
        val next = p.value + velocity * M.PROJECTION > M.THRESHOLD
        val target = if (controlled) (if (controlledOpen) 1.0 else 0.0) else if (next) 1.0 else 0.0
        closing = target == 0.0
        closeLead.set(if (target == 0.0) M.FAN_LEAD * max(1.0, abs(velocity)) else 0.0)
        closeLead.run(AnimStep.Timing(0.0, M.RESET))
        if (target == 1.0 && -velocityY / s > M.FLICK) {
            val flick = GelCubeUnboxSpring(M.SPRING_FLICK.damping, M.SPRING_FLICK.stiffness, lidWeight)
            p.run(AnimStep.Spring(M.OVERSHOOT, flick, velocity, true), AnimStep.Spring(1.0, flick, null, true))
        } else {
            p.run(if (target == 1.0) lid(target, velocity = velocity) else close(target, lidWeight, velocity))
        }
        onCommit(next)
        afterTouch()
    }

    /** The pointer ended (`success`) or was cancelled. */
    fun panFinalize(success: Boolean) {
        manualCore.run(AnimStep.Spring(1.0, M.SPRING_CORE))
        if (!success) {
            p.run(close(if (controlled) (if (controlledOpen) 1.0 else 0.0) else start))
            afterTouch()
        }
    }

    // ---- the action button ----

    fun pressIn() {
        pressed = true
        stopFilm()
        p.cancel()
        val current = progress
        if (mode != 1) manualCore.set(M.coreAt(t))
        p.set(current)
        sealOffset.set(0.0)
        mode = 1
    }

    fun pressOut() {
        pressed = false
        afterTouch()
    }

    /** The action's press, after `pressOut`. `open` is whether the sheet shows the opened copy. */
    fun act(open: Boolean) {
        if (!open) toggle() else afterTouch()
    }
}
