package com.ngocdevv.nativecook.blocks.pickers.mixingdeskfader

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Reanimated's Easing.bezier(0.65, 0, 0.35, 1). */
private val EASE_IN_OUT = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

private fun easeInOutQuad(x: Double): Double = if (x < 0.5) 2 * x * x else 1 - (2 - 2 * x) * (2 - 2 * x) / 2

/**
 * One animated number as a closed form of the frame time (ms): still, one eased leg after an optional delay
 * (withDelay + withTiming; Reanimated's default ease-in-out quad unless a bezier is given) or a spring (withSpring,
 * mass 1). Reading it never advances anything, so the Compose layers and the stage agree for a frame.
 */
private class MixTrack(initial: Double, stiffness: Double = 240.0, damping: Double = 26.0) {
    private val z = damping / 2
    private val w = sqrt(stiffness - z * z)
    private var still = initial
    private var sprung = false
    private var springTo = initial
    private var springFrom = initial
    private var springV = 0.0
    private var springStart = 0.0
    private var timed = false
    private var from = 0.0
    private var to = 0.0
    private var start = 0.0
    private var ms = 0.0
    private var ease: CubicBezierEasing? = null

    fun value(now: Double): Double {
        if (sprung) {
            val s = max(0.0, (now - springStart) / 1000)
            val d = springFrom - springTo
            return springTo + exp(-z * s) * (d * cos(w * s) + (springV + z * d) / w * sin(w * s))
        }
        if (!timed) return still
        if (now <= start) return from
        if (now >= start + ms) return to
        val k = (now - start) / ms
        val e = ease
        return from + (to - from) * (e?.transform(k.toFloat())?.toDouble() ?: easeInOutQuad(k))
    }

    fun set(v: Double) {
        sprung = false
        timed = false
        still = v
    }

    /** cancelAnimation: it stays where it is. */
    fun cancel(now: Double) = set(value(now))

    fun spring(now: Double, target: Double, velocity: Double) {
        springFrom = value(now)
        springTo = target
        springV = velocity
        springStart = now
        sprung = true
        timed = false
    }

    /** withDelay(delay, withTiming(target, duration, easing)): it holds where it is until the delay has passed. */
    fun timing(now: Double, target: Double, duration: Double, easing: CubicBezierEasing?, delay: Double = 0.0) {
        from = value(now)
        to = target
        start = now + delay
        ms = duration
        ease = easing
        sprung = false
        timed = true
    }
}

/**
 * The block's state for one frame time (MixingDeskFader.tsx's frame callback: the film, a finger's faders, mutes and
 * presets, and the return). Main thread only. `frame(now)` may be called several times for one display frame: it
 * advances once.
 */
internal class MixingDeskFaderController(autoPlay: Boolean, private val reduced: Boolean) {
    private val film = !reduced && autoPlay
    private val initial = MixerMotion.PRESETS[if (reduced) REDUCED_PRESET else 0]

    private val faders = Array(MixerMotion.CHANNELS) { MixTrack(initial[it], FADER_K, FADER_C) }
    private val knobs = Array(MixerMotion.CHANNELS) { MixTrack(initial[4 + it]) }
    private val mutes = Array(MixerMotion.CHANNELS) { MixTrack(initial[8 + it]) }
    private val lean = MixTrack(0.0)

    private var mode = if (film) MODE_AUTO else MODE_MANUAL
    private var loopStart = -1.0
    private var now = -1.0
    private var readyAt = -1.0
    private var lastTouch = -1.0
    private var returnAt = -1.0
    private var dragging = false

    /** The fader a finger holds (-1: none), its value at the touch, and its track on screen (dx, dy for the travel). */
    private var grab = -1
    private var grabFrom = 0.0
    private var axisX = 0.0
    private var axisY = 1.0
    private var inDetent = false
    private val a = DoubleArray(2)
    private val b = DoubleArray(2)

    /** The film's time (s, 0..LOOP); outside the film it keeps running for the meters and the REC light. */
    var t = 0.0
        private set

    /** The preset the segmented control shows. */
    var preset = if (reduced) REDUCED_PRESET else 0
        private set

    /** The pose (faders, knobs, mutes), the camera's lean and the meters' signal time at the last frame. */
    val pose = initial.copyOf()
    var leanNow = 0.0
        private set
    var meterT = if (reduced) STILL_T else 0.0
        private set

    /** The stage's fade-in, 0..1. */
    var shown = 0.0
        private set
    val filming: Boolean get() = mode == MODE_AUTO

    fun markReady() {
        if (readyAt < 0) readyAt = max(now, 0.0)
    }

    fun frame(nowMs: Double) {
        if (nowMs > now) {
            val gap = if (now < 0) 0.0 else nowMs - now
            now = nowMs
            step(gap)
        }
        if (mode == MODE_AUTO) {
            MixerMotion.filmPose(t, pose)
        } else {
            for (c in 0 until MixerMotion.CHANNELS) {
                pose[c] = faders[c].value(now)
                pose[4 + c] = knobs[c].value(now)
                pose[8 + c] = mutes[c].value(now)
            }
        }
        leanNow = lean.value(now)
        // the meters never stop: the film's time while it plays, the stage's own clock otherwise
        meterT = when {
            mode == MODE_AUTO && readyAt >= 0 -> t
            reduced -> STILL_T
            else -> (now / 1000) % MixerMotion.LOOP
        }
        shown = if (readyAt < 0) 0.0 else easeInOutQuad(MixerMotion.clamp01((now - readyAt) / FADE_MS))
    }

    private fun step(gap: Double) {
        if (mode == MODE_AUTO) {
            if (!film) {
                mode = MODE_MANUAL
                return
            }
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
            if (el >= MixerMotion.LOOP) {
                loopStart += MixerMotion.LOOP * 1000
                el -= MixerMotion.LOOP
            }
            t = el
            lean.set(MixerMotion.leanAt(el))
            preset = MixerMotion.filmPreset(el)
            return
        }
        // the meters and the REC light keep their own time
        if (!reduced) t = (t + min(0.05, gap / 1000)) % MixerMotion.LOOP
        if (mode == MODE_RETURN) {
            if (now - returnAt >= RETURN_MS) {
                t = 0.0
                loopStart = -1.0
                mode = MODE_AUTO
            }
            return
        }
        if (lastTouch < 0 || dragging) {
            lastTouch = now
            return
        }
        if (!film || now - lastTouch < IDLE_MS) return
        // 2 s after the last touch: Intro is recalled, the camera comes home, and the film restarts from t = 0
        mode = MODE_RETURN
        returnAt = now
        preset = 0
        recall(0)
        lean.timing(now, 0.0, RETURN_MS - 100, EASE_IN_OUT)
    }

    /** Glide every fader, knob and mute to preset p, as a recall does. */
    private fun recall(p: Int) {
        val target = MixerMotion.PRESETS[p]
        for (c in 0 until MixerMotion.CHANNELS) {
            if (reduced) {
                faders[c].set(target[c])
                knobs[c].set(target[4 + c])
                mutes[c].set(target[8 + c])
                continue
            }
            val delay = c * MixerMotion.STAGGER * 1000
            faders[c].timing(now, target[c], MixerMotion.GLIDE * 1000, EASE_IN_OUT, delay)
            knobs[c].timing(now, target[4 + c], MixerMotion.GLIDE * 1000, EASE_IN_OUT, delay)
            mutes[c].timing(now, target[8 + c], MixerMotion.MUTE_MOVE * 1000, null, 100.0)
        }
    }

    /** A touch takes over from wherever the film (or the return) is: the desk keeps the pose it had. */
    private fun pause() {
        if (now < 0) now = System.nanoTime() / 1e6
        if (mode == MODE_AUTO) {
            MixerMotion.filmPose(t, pose)
            for (c in 0 until MixerMotion.CHANNELS) {
                faders[c].set(pose[c])
                knobs[c].set(pose[4 + c])
                mutes[c].set(MixerMotion.jsRound(pose[8 + c]))
            }
        }
        if (mode == MODE_RETURN) lean.cancel(now)
        mode = MODE_MANUAL
        lastTouch = -1.0
    }

    /** A finger picked a preset. */
    fun userPreset(p: Int) {
        pause()
        preset = p
        recall(p)
    }

    /**
     * A finger touched the stage at (x, y) (dp): the fader whose projected track is nearest, within FADER_REACH, is the
     * one a vertical drag will move.
     */
    fun dragDown(x: Double, y: Double, width: Double, height: Double) {
        val k = leanNow
        var best = -1
        var bestD = FADER_REACH
        for (c in 0 until MixerMotion.CHANNELS) {
            MixerMotion.capOnScreen(c, 0.0, k, width, height, a)
            MixerMotion.capOnScreen(c, 1.0, k, width, height, b)
            val dx = b[0] - a[0]
            val dy = b[1] - a[1]
            val l2 = dx * dx + dy * dy
            val u = max(-0.1, min(1.1, ((x - a[0]) * dx + (y - a[1]) * dy) / l2))
            val px = a[0] + dx * u - x
            val py = a[1] + dy * u - y
            val d = sqrt(px * px + py * py)
            if (d < bestD) {
                bestD = d
                best = c
                axisX = dx
                axisY = dy
            }
        }
        grab = best
        if (best >= 0) grabFrom = pose[best]
    }

    /** The drag became active. */
    fun dragBegan() {
        val c = grab
        if (c < 0) return
        pause()
        dragging = true
        val f = faders[c]
        f.cancel(now)
        grabFrom = f.value(now)
        inDetent = abs(grabFrom - MixerMotion.SNAP_V) < MixerMotion.SNAP_ZONE * 0.5
    }

    /** tx, ty: the finger's whole travel (dp) since it touched down. True when the cap just caught the 0 dB detent. */
    fun dragMoved(tx: Double, ty: Double): Boolean {
        val c = grab
        if (c < 0 || !dragging) return false
        val raw = grabFrom + (tx * axisX + ty * axisY) / (axisX * axisX + axisY * axisY)
        val v = MixerMotion.faderFromDrag(raw)
        faders[c].set(v)
        val caught = abs(v - MixerMotion.SNAP_V) < MixerMotion.SNAP_ZONE * 0.5
        val tick = caught && !inDetent
        inDetent = caught
        return tick
    }

    /** Let go: the finger's speed carries it a little, onto 0 dB when close, on a spring. Returns the channel or -1. */
    fun dragEnded(vx: Double, vy: Double): Int {
        val c = grab
        if (c < 0 || !dragging) return -1
        val vel = (vx * axisX + vy * axisY) / (axisX * axisX + axisY * axisY)
        val f = faders[c]
        var to = max(0.0, min(1.0, f.value(now) + vel * FLICK_S))
        if (abs(to - MixerMotion.SNAP_V) < SNAP_CATCH) to = MixerMotion.SNAP_V
        if (reduced) f.set(to) else f.spring(now, to, vel)
        releasedTo = to
        return c
    }

    /** Where the last released fader lands. */
    var releasedTo = 0.0
        private set

    fun dragFinalized() {
        dragging = false
        grab = -1
    }

    /** A tap at (x, y) (dp): the mute button within MUTE_REACH toggles. Returns channel x 2 + (1 if now muted), or -1. */
    fun tap(x: Double, y: Double, width: Double, height: Double): Int {
        val k = leanNow
        var best = -1
        var bestD = MUTE_REACH
        for (c in 0 until MixerMotion.CHANNELS) {
            MixerMotion.muteOnScreen(c, k, width, height, a)
            val d = sqrt((a[0] - x) * (a[0] - x) + (a[1] - y) * (a[1] - y))
            if (d < bestD) {
                bestD = d
                best = c
            }
        }
        if (best < 0) return -1
        pause()
        val m = mutes[best]
        val to = if (m.value(now) > 0.5) 0.0 else 1.0
        if (reduced) m.set(to) else m.timing(now, to, MixerMotion.MUTE_MOVE * 1000, null)
        return best * 2 + (if (to == 1.0) 1 else 0)
    }

    private companion object {
        const val MODE_AUTO = 0
        const val MODE_MANUAL = 1
        const val MODE_RETURN = 2
        const val IDLE_MS = 2000.0
        const val RETURN_MS = 1500.0
        const val STALL_MS = 100.0
        const val FRAME_MS = 1000.0 / 60
        const val FADE_MS = 320.0

        /** A finger finds a fader within this distance (dp) of its track, and a mute button within MUTE_REACH. */
        const val FADER_REACH = 34.0
        const val MUTE_REACH = 30.0

        /** A released fader: how far ahead its speed carries it (s), the spring it lands on, and the detent's catch. */
        const val FLICK_S = 0.08
        const val FADER_K = 240.0
        const val FADER_C = 26.0
        const val SNAP_CATCH = MixerMotion.SNAP_ZONE * 1.3

        /** Reduced motion's still: the Interview mix, and the meters at one moment of the signal. */
        const val REDUCED_PRESET = 1
        const val STILL_T = 2.6
    }
}
