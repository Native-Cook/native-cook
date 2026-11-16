package com.ngocdevv.nativecook.blocks.cards.reededglassreveal

import com.ngocdevv.nativecook.blocks.cards.reededglassreveal.ReededGlassRevealAnim.Step as AnimStep
import com.ngocdevv.nativecook.blocks.cards.reededglassreveal.ReededGlassRevealMotion as M
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Cues the host turns into haptics (touch only; the loop is silent). */
internal object ReededGlassRevealBeat {
    const val ONSET = 0 // the finger touches the glass: light
    const val TICK = 1 // the front crosses a flute under the finger (<= 16 a second): selection
    const val EDGE = 2 // the front reaches the last flute at either end: rigid
    const val REVEALED = 3 // the glass is flat and the number is shown: success
    const val MASKED = 4 // the flutes have risen again: soft
    const val PRESS = 5 // a button is pressed: light
}

/**
 * The glass and its screen state (RN `ReededGlass.tsx`'s frame loop and Pan gesture plus the swap / plus buttons' press values): the autoplay loop, the
 * live physics under a finger, the swap button's sweep, the peek window, the 2 s resume, the auto re-mask, the haptic cues and the render gate.
 * Main thread, one `frame` per vsync.
 */
internal class ReededGlassRevealEngine(
    val autoPlay: Boolean,
    val reduce: Boolean,
    val layout: ReededGlassRevealTheme.Layout,
    val autoMaskMs: Double = 8000.0,
    val haptics: Boolean = true,
) {
    var cue: (Int) -> Unit = {}
    /** the glass has been revealed (true) or masked again (false): fired once per completed change, not for the silent loop */
    var onRevealedChange: (Boolean) -> Unit = {}
    /** mirrors for the host's semantics: the glass is clear / the glass is going to be clear */
    var onRevealedState: (Boolean) -> Unit = {}
    var onTargetState: (Boolean) -> Unit = {}
    var onReady: () -> Unit = {}

    val geo: ReededGlassRevealGeo = M.makeGeo(layout.w, layout.ribW, layout.front0, layout.front1, layout.edges, layout.centres)
    var live: ReededGlassRevealLive = M.restLive(geo)
        private set
    var look: ReededGlassRevealLook = M.timelineLook(0.0, geo)
        private set
    private var playing = autoPlay && !reduce
    private var loopT = 0.0
    private var clock = 0.0
    private var touchedAt = -1e9
    private var revealedAt = -1e9
    private var cmd = 0
    private var cmdSeen = 0
    private var cmdTo = 0 // 1: clear, -1: mask, 0: none
    private var touchStartX = 0.0
    private var touchFrontStart = geo.front0
    private var touchDragged = false
    private var uiRevealed = false
    private var uiTargetRevealed = false
    private var asleep = false

    // the render gate: what waits for the glass shows after the images and 8 smooth frames
    private var gateArmed = false
    private var gateWaited = 0.0
    private var gateSmooth = 0
    private var gateDone = false
    val ready: Boolean get() = gateDone

    /** the swap button's and the plus button's press scale (RN `held` / `pressed` shared values) */
    val held = ReededGlassRevealAnim(1.0)
    val plusPressed = ReededGlassRevealAnim(1.0)

    fun armGate() {
        gateArmed = true
        asleep = false
    }

    fun wake() {
        asleep = false
    }

    val animating: Boolean get() = held.running || plusPressed.running

    // ---- commands (the swap button, a controlled value, TalkBack) ----

    fun toggle() {
        wake()
        cmd++
    }

    fun setTo(revealed: Boolean) {
        wake()
        cmdTo = if (revealed) 1 else -1
    }

    /** a button of ours is pressed: freeze the loop under it */
    fun hold() {
        wake()
        val m = if (playing) M.liveFromTimeline(loopT, geo) else live
        playing = false
        m.touching = true
        m.touchX = -1e9
        m.mode = M.HOLD
        m.fv = 0.0
        m.dragged = true // a button is not a long press: no peek window
        live = m
        val targetRevealed = m.target >= geo.front1 - 1
        uiTargetRevealed = targetRevealed
        onTargetState(targetRevealed)
        touchedAt = clock
    }

    fun release() {
        val m = live
        m.touching = false
        m.mode = M.SPRING
        m.dragged = false
        touchedAt = clock
    }

    // ---- the frame ----

    /** One frame (`ms` since the last one); returns true while the picture changes. */
    fun frame(ms: Double): Boolean {
        held.step(ms / 1000)
        plusPressed.step(ms / 1000)
        if (asleep) return animating
        if (gateArmed && !gateDone) {
            gateSmooth = if (ms < 40) gateSmooth + 1 else 0
            gateWaited += ms
            if (gateSmooth >= 8 && gateWaited > 300) {
                gateDone = true
                onReady()
            }
        }
        if (!gateDone) {
            // Start the story only when its first fully rendered frame is ready.
            loopT = 0.0
            if (uiTargetRevealed) {
                look = M.liveLook(live, 1.0)
            } else {
                live = M.restLive(geo)
                look = M.timelineLook(0.0, geo)
            }
            return true
        }
        val elapsed = max(0.0, ms / 1000)
        val dt = min(elapsed, 0.05)
        val now = clock + elapsed
        clock = now

        // the swap button (or toggle() / setTo()): sweep to the other end from wherever the glass is
        val wantTo = cmdTo
        if (cmd != cmdSeen || wantTo != 0) {
            val m: ReededGlassRevealLive
            if (playing) {
                playing = false
                m = M.liveFromTimeline(loopT, geo)
            } else {
                m = live
            }
            val toEnd = if (wantTo != 0) (if (wantTo > 0) geo.front1 else geo.front0) else if (m.target >= geo.front1 - 1) geo.front0 else geo.front1
            cmdSeen = cmd
            cmdTo = 0
            if (reduce) m.target = toEnd else if (abs(toEnd - m.fx) > 1) M.startSweep(m, geo, toEnd)
            live = m
            touchedAt = now
        }

        if (playing) {
            if (!autoPlay || reduce) {
                playing = false
                live = M.restLive(geo)
            } else {
                val t = (loopT + elapsed) % M.LOOP
                loopT = t
                look = M.timelineLook(t, geo)
                val r = t >= M.T_GLYPH1 && t < M.T_GLYPH2
                if (uiRevealed != r) {
                    uiRevealed = r
                    onRevealedState(r)
                }
                val targetRevealed = look.front >= (geo.front0 + geo.front1) / 2
                if (uiTargetRevealed != targetRevealed) {
                    uiTargetRevealed = targetRevealed
                    onTargetState(targetRevealed)
                }
                return true
            }
        }

        val m = live
        val ev = M.stepLive(m, dt, geo, reduce, now, true)
        if (haptics) {
            if (ev.edge) cue(ReededGlassRevealBeat.EDGE) else if (ev.tick) cue(ReededGlassRevealBeat.TICK)
        }
        if (ev.revealed) {
            revealedAt = now
            uiRevealed = true
            if (haptics) cue(ReededGlassRevealBeat.REVEALED)
            onRevealedState(true)
            onRevealedChange(true)
        }
        if (ev.masked) {
            uiRevealed = false
            if (haptics) cue(ReededGlassRevealBeat.MASKED)
            onRevealedState(false)
            onRevealedChange(false)
        }
        val idle = !m.touching && m.mode != M.DRAG && m.mode != M.SWEEP && m.mode != M.HOLD
        if (autoPlay && !reduce) {
            // 2 s after the last touch the glass eases back to rest, then the loop starts again at t = 0
            if (M.shouldResume(m, now, touchedAt)) {
                if (m.target != geo.front0) {
                    m.mode = M.SPRING
                    m.target = geo.front0
                }
                if (M.liveAtRest(m, geo)) {
                    live = M.restLive(geo)
                    loopT = 0.0
                    playing = true
                    look = M.timelineLook(0.0, geo)
                    if (uiRevealed) {
                        uiRevealed = false
                        onRevealedState(false)
                    }
                    return true
                }
            }
        } else if (idle && m.revealed && autoMaskMs > 0 && now - revealedAt > autoMaskMs / 1000 && now - touchedAt > M.RESUME_AFTER) {
            if (reduce) m.target = geo.front0 else if (m.target != geo.front0) M.startSweep(m, geo, geo.front0)
        }
        live = m
        look = M.liveLook(m, 1.0)
        val targetRevealed = m.target >= geo.front1 - 1
        if (uiTargetRevealed != targetRevealed) {
            uiTargetRevealed = targetRevealed
            onTargetState(targetRevealed)
        }
        if (idle && (!autoPlay || reduce) && M.liveAtRest(m, geo)) {
            // A static pane needs no per-frame work. A real gesture or button wakes it.
            asleep = true
        }
        return true
    }

    // ---- the finger on the glass ----

    private fun takeOver(): ReededGlassRevealLive {
        if (playing) {
            playing = false
            return M.liveFromTimeline(loopT, geo)
        }
        return live
    }

    fun touchBegin(x: Double) {
        wake()
        val m = takeOver()
        m.touching = true
        m.touchX = x
        m.mode = M.HOLD
        m.fv = 0.0
        m.dragged = false
        m.holdT = 0.0
        m.peekRel = -1.0
        live = m
        touchStartX = x
        touchFrontStart = m.fx
        touchDragged = false
        touchedAt = clock
        if (haptics) cue(ReededGlassRevealBeat.ONSET)
    }

    /** `translationX` / `velocityX` in dp (from where the finger landed / per second). */
    fun touchMove(x: Double, translationX: Double, velocityX: Double) {
        val m = live
        m.touchX = x
        val dx = x - touchStartX
        if (m.peek > 0.3) {
            // an open peek window follows the finger instead of moving the front
        } else if (!reduce) {
            val dead = if (m.fx <= geo.front0 + 1 && !m.revealed) M.DIRECTION_DECIDE else 6.0
            if (!touchDragged && abs(dx) > dead) {
                M.decideDirection(m, geo, touchStartX, dx)
                m.dragged = true
                m.peek = 0.0
                touchDragged = true
            }
            if (touchDragged || abs(dx) > dead) M.dragLive(m, geo, touchFrontStart, translationX, velocityX)
        } else if (!touchDragged && abs(dx) > 6) {
            m.dragged = true
            touchDragged = true
        }
        touchedAt = clock
    }

    fun touchEnd(velocityX: Double, success: Boolean) {
        val m = live
        M.finishTouch(m, geo, touchDragged, velocityX, reduce, success, 0.5)
        touchedAt = clock
    }

    // ---- the buttons' press values (RN: withTiming 100 ms bezier(.23, 1, .32, 1), spring back) ----

    private val pressEase = { x: Double -> M.bezier(doubleArrayOf(0.23, 1.0, 0.32, 1.0), x) }

    private fun pressIn(v: ReededGlassRevealAnim) {
        v.run(AnimStep.Timing(if (reduce) 0.82 else M.PRESS_FINGER, 0.1, pressEase))
    }

    private fun pressOut(v: ReededGlassRevealAnim) {
        if (reduce) v.run(AnimStep.Timing(1.0, 0.16)) else v.run(AnimStep.Spring(1.0, ReededGlassRevealSpring(30.5, 322.3, 1.0)))
    }

    /** The swap button is pressed. */
    fun swapDown() {
        hold()
        pressIn(held)
        if (haptics) cue(ReededGlassRevealBeat.PRESS)
    }

    fun swapUp(pressed: Boolean) {
        release()
        pressOut(held)
        if (pressed) toggle()
    }

    /** The plus button. */
    fun plusDown() {
        hold()
        pressIn(plusPressed)
        if (haptics) cue(ReededGlassRevealBeat.PRESS)
    }

    fun plusUp() {
        release()
        pressOut(plusPressed)
    }
}
