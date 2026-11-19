package com.ngocdevv.nativecook.blocks.onboarding.visorbootonboarding

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A value animated from media time t0 (seconds): held, eased, a spring or a decay. */
private sealed interface VisorBootAnim {
    fun at(now: Double): Double
    /** Media time at which it has visibly come to rest. */
    val end: Double

    class Still(val v: Double) : VisorBootAnim {
        override fun at(now: Double) = v
        override val end = 0.0
    }

    class Timing(val from: Double, val to: Double, val t0: Double, val dur: Double, val curve: (Double) -> Double) : VisorBootAnim {
        override fun at(now: Double) =
            if (dur <= 0) to else from + (to - from) * curve(VisorBootMotion.clamp01((now - t0) / dur))
        override val end get() = t0 + dur
    }

    class Spring(val from: Double, val to: Double, val v0: Double, val t0: Double, val omega: Double, val zeta: Double) : VisorBootAnim {
        override fun at(now: Double) = VisorBootMotion.springAt(from, to, v0, max(0.0, now - t0), omega, zeta)
        override val end get() = t0 + 5.5 / (max(zeta, 0.3) * omega)
    }

    class Decay(val x0: Double, val v0: Double, val t0: Double) : VisorBootAnim {
        override fun at(now: Double) = x0 + v0 / DECAY_K * (1 - exp(-DECAY_K * max(0.0, now - t0)))
        override val end get() = t0 + max(0.0, ln(max(abs(v0), 1e-6) / 2) / DECAY_K)
    }

    companion object {
        /** Reanimated's withDecay deceleration 0.995 per ms. */
        val DECAY_K = -ln(0.995) * 1000
    }
}

/**
 * The block's one source of truth: the film's clock and whatever a finger set. `state(now)` is a pure function of the
 * stored fields and a frame time (seconds on the System.nanoTime base, which Choreographer and withFrameNanos share),
 * read by the Filament stage's frame callback and by the Compose draw lambdas, so the layers never disagree and
 * nothing recomposes per frame. Everything runs on the main thread.
 *
 * React Native's three modes: the film (auto), the pages under a finger (manual), and the ease back to the poster
 * page 2 s after the last touch (back), after which the film starts again from 0.
 */
internal class VisorBootController(autoPlay: Boolean, val reduced: Boolean) {
    private enum class Mode { AUTO, MANUAL, BACK }

    val film = autoPlay && !reduced
    private var mode = if (film) Mode.AUTO else Mode.MANUAL
    private var start: Double? = null
    private var page: VisorBootAnim = VisorBootAnim.Still(0.0)
    private var m: VisorBootAnim = VisorBootAnim.Still(if (film) 0.0 else 1.0)
    private var yaw: VisorBootAnim = VisorBootAnim.Still(0.0)
    private var pitch: VisorBootAnim = VisorBootAnim.Still(0.0)
    private var runBase = 0.0
    private var runFrom = 0.0
    private var lastActivity = 0.0
    private var frozenT = 0.0
    var dragging = false
        private set

    /** The film holds the poster until the helmet has loaded, so the first frame shown is the poster. */
    var ready = false

    val isManual get() = mode == Mode.MANUAL

    /** Whether the finger is turning the helmet (for the 45 deg detent haptic). */
    fun isOrbiting(now: Double): Boolean {
        if (dragging) return true
        val y = yaw
        return y is VisorBootAnim.Decay && now < y.end
    }

    fun state(now: Double): VisorBootState {
        if (mode == Mode.AUTO) {
            if (!ready) return VisorBootState(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
            val s0 = start ?: now.also { start = it }
            val t = max(0.0, (now - s0) % LOOP)
            return VisorBootState(t, 0.0, 0.0, 0.0, 0.0, t)
        }
        if (mode == Mode.MANUAL && film && !dragging) {
            var idleFrom = max(lastActivity, page.end)
            if (yaw is VisorBootAnim.Decay) idleFrom = max(idleFrom, yaw.end)
            if (now >= idleFrom + VisorBootMotion.IDLE) {
                val r0 = idleFrom + VisorBootMotion.IDLE
                val y = yaw.at(r0)
                val p = pitch.at(r0)
                val pg = page.at(r0)
                val d = VisorBootMotion.RETURN_DURATION
                val ease: (Double) -> Double = VisorBootMotion::easeInOutTouch
                yaw = VisorBootAnim.Timing(y, (y / 360).roundToInt() * 360.0, r0, d, ease)
                pitch = VisorBootAnim.Timing(p, 0.0, r0, d, ease)
                page = VisorBootAnim.Timing(pg, 0.0, r0, d, ease)
                mode = Mode.BACK
            }
        }
        if (mode == Mode.BACK && now >= page.end) {
            mode = Mode.AUTO
            start = page.end
            yaw = VisorBootAnim.Still(0.0)
            pitch = VisorBootAnim.Still(0.0)
            page = VisorBootAnim.Still(0.0)
            m = VisorBootAnim.Still(0.0)
            return state(now)
        }
        return VisorBootState(frozenT, page.at(now), m.at(now), yaw.at(now), pitch.at(now), runBase + (now - runFrom))
    }

    /** Any touch hands the film over to the pages, at the page the film was on. */
    private fun pause(now: Double) {
        val s = state(now)
        if (mode == Mode.AUTO) {
            page = VisorBootAnim.Still(VisorBootMotion.pageOfFilm(s.t))
            runBase = s.run
            runFrom = now
            m = VisorBootAnim.Timing(0.0, 1.0, now, VisorBootMotion.TAKEOVER, VisorBootMotion::easeInOutTouch)
            frozenT = s.t
        } else {
            page = VisorBootAnim.Still(s.page)
            yaw = VisorBootAnim.Still(s.yaw)
            pitch = VisorBootAnim.Still(s.pitch)
        }
        mode = Mode.MANUAL
        lastActivity = now
    }

    private fun pageSpring(from: Double, to: Double, v0: Double, now: Double): VisorBootAnim =
        if (reduced) VisorBootAnim.Timing(from, to, now, 0.22) { it }
        // Reanimated { duration: 700, dampingRatio: 0.86 }
        else VisorBootAnim.Spring(from, to, v0, now, 2 * PI / 0.7 * 0.92, 0.86)

    fun goPage(i: Int, now: Double) {
        pause(now)
        val target = max(0, min(VisorBootMotion.PAGES - 1, i)).toDouble()
        page = pageSpring(page.at(now), target, 0.0, now)
    }

    fun next(now: Double) = goPage(state(now).page.roundToInt() + 1, now)
    fun skip(now: Double) = goPage(VisorBootMotion.PAGES - 1, now)
    fun start(now: Double) = pause(now)

    // ---- orbit
    private var yawFrom = 0.0
    private var pitchFrom = 0.0
    private var pageFrom = 0.0

    fun orbitBegan(now: Double) {
        pause(now)
        dragging = true
        yawFrom = yaw.at(now)
        pitchFrom = pitch.at(now)
    }

    fun orbitChanged(now: Double, dx: Double, dy: Double) {
        lastActivity = now
        yaw = VisorBootAnim.Still(yawFrom - dx * YAW_PER_PT)
        pitch = VisorBootAnim.Still(rubber(pitchFrom + dy * PITCH_PER_PT, PITCH_MIN, PITCH_MAX, 10.0))
    }

    fun orbitEnded(now: Double, vx: Double, vy: Double) {
        dragging = false
        lastActivity = now
        val p = pitch.at(now)
        val back = max(PITCH_MIN, min(PITCH_MAX, p))
        pitch = VisorBootAnim.Spring(p, back, vy * PITCH_PER_PT, now, 2 * PI / 0.4 * 0.95, 0.8)
        if (reduced) return
        yaw = VisorBootAnim.Decay(yaw.at(now), -vx * YAW_PER_PT, now)
    }

    /** The current yaw in degrees (for the detent haptic). */
    fun yawAt(now: Double) = yaw.at(now)

    // ---- pager
    fun pagerBegan(now: Double) {
        pause(now)
        dragging = true
        pageFrom = page.at(now)
    }

    fun pagerChanged(now: Double, dx: Double, width: Double) {
        lastActivity = now
        page = VisorBootAnim.Still(rubber(pageFrom - dx / (width * PAGE_SPAN), 0.0, VisorBootMotion.PAGES - 1.0, 0.35))
    }

    fun pagerEnded(now: Double, dx: Double, vx: Double, width: Double) {
        dragging = false
        lastActivity = now
        val base = pageFrom.roundToInt().toDouble()
        var target = base
        if (dx < -50 || vx < -500) target = base + 1 else if (dx > 50 || vx > 500) target = base - 1
        target = max(0.0, min(VisorBootMotion.PAGES - 1.0, target))
        page = pageSpring(page.at(now), target, -vx / (width * PAGE_SPAN), now)
    }

    /** A gesture that ended without a drag (a cancelled touch). */
    fun dragCancelled(now: Double) {
        dragging = false
        lastActivity = now
    }

    companion object {
        const val YAW_PER_PT = 0.36
        const val PITCH_PER_PT = 0.16
        const val PITCH_MIN = -10.0
        const val PITCH_MAX = 24.0
        const val DETENT_DEG = 45.0
        const val PAGE_SPAN = 0.8

        fun rubber(x: Double, lo: Double, hi: Double, k: Double): Double {
            if (x < lo) { val o = lo - x; return lo - o / (1 + o / k) }
            if (x > hi) { val o = x - hi; return hi + o / (1 + o / k) }
            return x
        }
    }
}
