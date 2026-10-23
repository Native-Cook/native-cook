package com.ngocdevv.nativecook.blocks.pickers.stampdiarycalendar

import com.ngocdevv.nativecook.blocks.pickers.stampdiarycalendar.StampDiaryCalendarMotion as M
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Stamp Diary Calendar controller: the frame callback and the touch handlers of
 * StampDiaryCalendar.tsx, on plain Kotlin types so the regression harness can run them.
 * All coordinates are reference points (402 wide) and `now` is seconds on any monotonic clock.
 */
internal class StampDiaryCalendarController {
    enum class Mode { AUTO, FINGER, SNAP, STORY, IDLE, QUIET }
    enum class Haptic { SELECTION, LIGHT, MEDIUM }

    class Ctl {
        var mode = Mode.IDLE
        var memory = AUTO_MEMORY
        var t0 = 0.0
        var tau0 = 0.0
        var stopAt = 0.0
        var prevTau = 0.0
        var lastTouch = -100.0
        var cornerX = 0.0
        var cornerY = 0.0
        var dx = 0.0
        var dy = 0.0
        var moved = false
        var lift = 1.0
        var release = M.detachCurl(AUTO_MEMORY)
        var quietShow = false
        var restSent = false

        /** Story speed: 1, or RETURN_RATE when a tap sends a stamp home. */
        var rate = 1.0
    }

    val ctl = Ctl()
    var pose: M.Pose = M.restPose(AUTO_MEMORY)
        private set

    /** autoPlay prop, with neither Reduce Motion nor a screen reader on. */
    var autoOn = false
        set(value) {
            field = value
            restartRest()
        }

    /** Reduce Motion: a crossfade at the landing spot. */
    var quiet = false
        set(value) {
            field = value
            restartRest()
        }

    var may = true
        set(value) {
            field = value
            restartRest()
        }

    /** The actor layer's memory changed (the view re-renders the postmark and the art). */
    var onActor: (Int) -> Unit = {}
    var onHaptic: (Haptic) -> Unit = {}

    /** An empty day was tapped (selection ring). */
    var onEmptyDay: (Int) -> Unit = {}

    var emptyStart = -100.0
        private set

    /** Ring over a tapped empty day: 120 ms in, 1.2 s hold, 300 ms out (ease in-out). */
    fun emptyAlpha(now: Double): Double {
        fun inOut(t: Double): Double {
            val x = M.clamp01(t)
            return if (x < 0.5) 2 * x * x else 1 - (-2 * x + 2).pow(2) / 2
        }
        val t = now - emptyStart
        if (t < 0) return 0.0
        if (t < 0.12) return inOut(t / 0.12)
        if (t < 1.32) return 1.0
        return 1 - inOut((t - 1.32) / 0.3)
    }

    private fun restartRest() {
        ctl.mode = Mode.IDLE
        ctl.memory = AUTO_MEMORY
        ctl.lastTouch = -100.0
        ctl.quietShow = false
        ctl.restSent = false
    }

    /** Does the controller need frames? False when it shows the pin and no loop is waiting. */
    fun isRunning(now: Double): Boolean {
        if (now - emptyStart < 1.7) return true
        if (ctl.mode == Mode.IDLE) return !ctl.restSent || (autoOn && may)
        return true
    }

    /** The app came back to the foreground: restart the loop clock so it does not jump. */
    fun resumeClock(now: Double) {
        if (ctl.mode == Mode.AUTO) ctl.t0 = now
    }

    // ---- frame

    fun tick(now: Double): M.Pose {
        val c = ctl
        var next: M.Pose? = null
        when (c.mode) {
            Mode.IDLE -> {
                if (autoOn && may && now - c.lastTouch > M.Touch.RESUME_AFTER) {
                    val previous = c.memory
                    c.mode = Mode.AUTO
                    c.memory = AUTO_MEMORY
                    c.t0 = now
                    c.restSent = false
                    if (previous != AUTO_MEMORY) onActor(AUTO_MEMORY)
                    return pose
                }
                if (c.restSent) return pose
                c.restSent = true
                next = M.restPose(c.memory)
            }
            Mode.AUTO -> {
                val tau = (now - c.t0) % M.LOOP
                next = M.storyPose(c.memory, tau, M.detachCurl(c.memory))
            }
            Mode.FINGER -> {
                val p = M.fingerPose(c.memory, now - c.t0, c.cornerX, c.cornerY, c.cornerX + c.dx, c.cornerY + c.dy)
                next = if (c.moved) p else p.copy(curl = M.FLAT)
            }
            Mode.SNAP -> {
                val k = M.settle(now - c.t0)
                val p = M.fingerPose(c.memory, 1.0, c.cornerX, c.cornerY, c.cornerX + c.dx * k, c.cornerY + c.dy * k)
                val lift = M.mix(1.0, c.lift, k)
                val cellY = M.cellCenter(StampDiaryCalendarData.memories[c.memory].day, M.MAY_2026).y
                next = p.copy(lift = lift, cy = cellY - M.Peel.RISE * ((lift - 1) / (M.Peel.LIFT - 1)))
                if (now - c.t0 > M.Touch.SNAP_FOR) {
                    c.mode = Mode.IDLE
                    c.restSent = false
                    next = M.restPose(c.memory)
                }
            }
            Mode.STORY -> {
                var tau = c.tau0 + (now - c.t0) * c.rate
                if (tau >= c.stopAt) tau = c.stopAt
                if (c.prevTau < M.T.LAND && tau >= M.T.LAND) onHaptic(Haptic.MEDIUM)
                if (c.prevTau < M.T.REST - 0.12 && tau >= M.T.REST - 0.12) onHaptic(Haptic.LIGHT)
                next = M.storyPose(c.memory, tau, c.release)
                if (tau >= c.stopAt && c.stopAt >= M.T.REST) {
                    c.mode = Mode.IDLE
                    c.lastTouch = now
                    c.restSent = false
                    c.prevTau = tau
                } else if (tau >= c.stopAt && autoOn && now - c.lastTouch > M.Touch.RESUME_AFTER) {
                    // Held after a touch: two seconds later the stamp goes home and the loop restarts.
                    c.tau0 = M.T.HOLD_END
                    c.t0 = now
                    c.stopAt = M.T.REST
                    c.prevTau = tau
                    c.rate = 1.0
                } else {
                    c.prevTau = tau
                }
            }
            Mode.QUIET -> {
                val v = M.clamp01((now - c.t0) / M.Touch.FADE)
                next = M.quietPose(c.memory, if (c.quietShow) v else 1 - v)
                if (!c.quietShow && v >= 1) {
                    c.mode = Mode.IDLE
                    c.restSent = false
                }
            }
        }
        if (next != null) pose = next
        return pose
    }

    // ---- touch

    private fun startStory(memory: Int, tau0: Double, stopAt: Double, now: Double, release: M.Curl) {
        val previous = ctl.memory
        ctl.mode = Mode.STORY
        ctl.memory = memory
        ctl.tau0 = tau0
        ctl.stopAt = stopAt
        ctl.t0 = now
        ctl.prevTau = tau0
        ctl.lastTouch = now
        ctl.release = release
        ctl.restSent = false
        ctl.rate = 1.0
        if (memory != previous) onActor(memory)
    }

    fun tapMemory(memory: Int, now: Double) {
        val c = ctl
        if (quiet) {
            val showing = c.mode == Mode.QUIET && c.quietShow && c.memory == memory
            val previous = c.memory
            c.mode = Mode.QUIET
            c.memory = memory
            c.t0 = now
            c.quietShow = !showing
            c.lastTouch = now
            c.restSent = false
            if (memory != previous) onActor(memory)
            onHaptic(Haptic.SELECTION)
            return
        }
        startStory(memory, M.T.PEEL_START, M.T.HOLD_END, now, M.detachCurl(memory))
        onHaptic(Haptic.SELECTION)
    }

    fun sendHome(now: Double) {
        val c = ctl
        if (c.mode == Mode.QUIET && c.quietShow) {
            c.t0 = now
            c.quietShow = false
            c.lastTouch = now
            return
        }
        if (c.mode == Mode.STORY && c.stopAt == M.T.HOLD_END) {
            val tau = min(c.stopAt, c.tau0 + (now - c.t0) * c.rate)
            if (tau >= M.T.LAND) {
                c.tau0 = max(tau, M.T.HOLD_END)
                c.t0 = now
                c.stopAt = M.T.REST
                c.lastTouch = now
                c.rate = M.Touch.RETURN_RATE
                onHaptic(Haptic.LIGHT)
            }
        }
    }

    /** Is the controller showing the pin (nothing moving)? Touch on a running loop takes it over. */
    private fun takeOver(now: Double): Boolean {
        val c = ctl
        when (c.mode) {
            Mode.AUTO -> {
                val tau = (now - c.t0) % M.LOOP
                if (tau < M.T.PEEL_START - 0.05 || tau >= M.T.REST) {
                    c.mode = Mode.IDLE
                    c.lastTouch = now
                    c.restSent = false
                    return true
                }
                // Mid-story: keep playing to the hold, then wait for the user.
                c.mode = Mode.STORY
                c.tau0 = tau
                c.t0 = now
                c.stopAt = if (tau < M.T.HOLD_END) M.T.HOLD_END else M.T.REST
                c.prevTau = tau
                c.lastTouch = now
                c.release = M.detachCurl(c.memory)
                c.rate = 1.0
                return false
            }
            Mode.IDLE -> {
                c.lastTouch = now
                return true
            }
            Mode.QUIET -> return true
            Mode.SNAP -> {
                c.mode = Mode.IDLE
                c.lastTouch = now
                c.restSent = false
                return true
            }
            else -> {
                c.lastTouch = now
                return false
            }
        }
    }

    fun touchBegan(x: Double, y: Double, now: Double) {
        val free = takeOver(now)
        val c = ctl
        // While a stamp is out, any tap sends it home once it has landed.
        if (!free) {
            val landed = c.mode == Mode.STORY && c.stopAt == M.T.HOLD_END && c.tau0 + (now - c.t0) * c.rate >= M.T.LAND
            c.moved = false
            c.dx = 0.0
            c.dy = 0.0
            c.cornerX = if (landed) -1.0 else 0.0
            c.cornerY = 0.0
            return
        }
        if (c.mode == Mode.QUIET && c.quietShow) {
            c.cornerX = -1.0
            c.moved = false
            return
        }
        val day = if (may) M.dayAt(x, y, M.MAY_2026) else 0
        val memory = if (may && day > 0) M.memoryIndex(day, M.MAY_2026) else -1
        if (memory >= 0 && !quiet) {
            val previous = c.memory
            val mem = StampDiaryCalendarData.memories[memory]
            c.mode = Mode.FINGER
            c.memory = memory
            c.t0 = now
            c.moved = false
            c.dx = 0.0
            c.dy = 0.0
            c.cornerX = mem.w / 2
            c.cornerY = mem.h / 2
            c.lastTouch = now
            c.restSent = false
            if (memory != previous) onActor(memory)
            onHaptic(Haptic.SELECTION)
        } else {
            c.cornerX = if (memory >= 0) (-2 - memory).toDouble() else 0.0
            c.cornerY = day.toDouble()
            c.moved = false
        }
    }

    fun touchMoved(translationX: Double, translationY: Double, now: Double) {
        val c = ctl
        if (c.mode != Mode.FINGER) {
            if (hypot(translationX, translationY) > M.Touch.SLOP) c.moved = true
            return
        }
        if (!c.moved && hypot(translationX, translationY) > M.Touch.SLOP) {
            val mem = StampDiaryCalendarData.memories[c.memory]
            c.moved = true
            // The corner opposite the pull is the one that lifts.
            c.cornerX = if (translationX < 0) mem.w / 2 else -mem.w / 2
            c.cornerY = if (translationY < 0) mem.h / 2 else -mem.h / 2
        }
        val lift = pose.lift
        c.dx = translationX / lift
        c.dy = translationY / lift
        c.lastTouch = now
    }

    /**
     * Release (or cancel). A tap never activates a pan, so everything is decided here.
     * `velocityY` is the finger's vertical speed in reference points per second.
     */
    fun touchEnded(velocityY: Double, now: Double) {
        val c = ctl
        if (c.mode == Mode.FINGER) {
            if (!c.moved) {
                // A tap: play the peel from where the press already lifted it.
                val lifted = M.clamp01((now - c.t0) / M.Touch.LIFT_IN)
                startStory(c.memory, M.T.PEEL_START + 0.25 * lifted * (M.T.DETACH - M.T.PEEL_START), M.T.HOLD_END, now, M.detachCurl(c.memory))
                return
            }
            val share = M.peelShare(c.memory, c.dx, c.dy)
            if (share > M.Touch.COMMIT_SHARE || velocityY < M.Touch.COMMIT_VELOCITY) {
                startStory(c.memory, M.T.DETACH, M.T.HOLD_END, now, pose.curl)
                onHaptic(Haptic.LIGHT)
            } else {
                c.mode = Mode.SNAP
                c.t0 = now
                c.lift = pose.lift
                c.lastTouch = now
            }
            return
        }
        if (c.moved) return
        if (c.cornerX == -1.0) {
            sendHome(now)
            return
        }
        if (c.cornerX <= -2) {
            tapMemory((-2 - c.cornerX).toInt(), now)
            return
        }
        if (c.cornerY > 0) {
            emptyStart = now
            onEmptyDay(c.cornerY.toInt())
            onHaptic(Haptic.SELECTION)
        }
    }

    /** Accessibility activation of a day (TalkBack). */
    fun select(day: Int, month: M.Month, now: Double) {
        val memory = M.memoryIndex(day, month)
        if (memory >= 0) {
            tapMemory(memory, now)
        } else {
            emptyStart = now
            onEmptyDay(day)
        }
    }

    companion object {
        /** May 12, the wreath. */
        const val AUTO_MEMORY = 3
    }
}
