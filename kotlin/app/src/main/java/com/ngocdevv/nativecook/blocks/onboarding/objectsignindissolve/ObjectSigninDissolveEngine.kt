package com.ngocdevv.nativecook.blocks.onboarding.objectsignindissolve

import com.ngocdevv.nativecook.blocks.onboarding.objectsignindissolve.ObjectSigninDissolveMotion as M
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The page answering the shell: lagged copies of the cut, and the seconds since the skin last closed (-1 none). */
internal class ObjectSigninDissolveGlow {
    var pool = 0.0
    var word = 0.0
    var pill = 0.0
    var zip = -1.0
}

/** The haptic beats (touch only; the autoplay loop is silent). */
internal object ObjectSigninDissolveBeat {
    const val ONSET = 0
    const val TICK = 1
    const val CLOSE = 2
    const val SPIN = 3
    const val TOGGLE = 4
    const val PRESS = 5
}

/**
 * Timeline + finger -> one [look] the canvas reads (RN `useEngine`). All state lives on the main thread: the frame loop and the
 * pointer events both run there, so nothing is shared across threads.
 *
 * Reduce Motion: no autoplay loop, no coast, no bank; the theme change cross-fades instead of wiping.
 */
internal class ObjectSigninDissolveEngine(
    private val autoPlay: Boolean,
    val reduce: Boolean,
    defaultLight: Boolean,
) {
    private class Grip {
        var on = false
        var x0 = 0.0
        var y0 = 0.0
        var yaw0 = 0.0
        var t0 = 0.0
        var lastX = 0.0
        var lastT = 0.0
        var lastEvent = 0.0
        var v = 0.0
        var moved = 0.0
        var tickC = 0.0
        var lastTick = 0.0
        var spun = 0
    }

    var haptic: (Int) -> Unit = {}
    var onTheme: ((Boolean) -> Unit)? = null
    var onBarDark: ((Boolean) -> Unit)? = null

    var clock = 0.0
        private set
    private var loopT = 0.0
    private var playing = autoPlay && !reduce

    var look: ObjectSigninDissolveLook = M.lookAt(0.0).let { if (defaultLight) it.copy(wipeP = 1.0) else it }
        private set
    private var live: ObjectSigninDissolveLive = M.liveFrom(look)
    val glow = ObjectSigninDissolveGlow()
    var lightMix = if (defaultLight) 1.0 else 0.0
        private set
    private var held = false
    private var touchedAt = -1e9
    private var fingerC = 0.0
    private val grip = Grip()
    private var hint = -1.0
    private var wasOver = false
    private var resuming = false

    /** Which of the three buttons is down (the 0.97 press scale), -1 none. */
    var pressed = -1

    // layout (set by the host)
    private var boxX = 0.0
    private var boxY = 0.0
    private var boxW = 0.0
    private var boxH = 0.0
    private var cy = 0.0
    private var s = 1.0
    private var reach = 0.0
    private var dBar = 0.0
    private var bar = if (defaultLight) 1 else 0
    private var prevR: Double? = null

    fun layout(w: Double, h: Double, insetTop: Double, pearCy: Double, scale: Double) {
        s = scale
        cy = pearCy
        boxX = w / 2 - 120 * scale
        boxY = pearCy - 220 * scale
        boxW = 240 * scale
        boxH = 400 * scale
        reach = M.wipeReach(w, h)
        dBar = hypot(w / 2 + 80, h + 80 - insetTop * 0.6)
    }

    /** The disc's radius this frame (the light page is inside it). */
    fun wipeRadius(): Double {
        val k = look
        return if (reduce) (if (k.wipeP > 0.5 || lightMix > 0.01) reach else 0.0) else M.wipeRadius(k.wipeP, reach)
    }

    private fun cOfY(y: Double) = (y - cy - M.PLANE_TOP * s) / ((M.PLANE_BOTTOM - M.PLANE_TOP) * s)

    private fun takeOver() {
        resuming = false
        if (playing) {
            live = M.liveFrom(look)
            playing = false
        }
    }

    /** Frame time pinned at t seconds of the loop (previews and checks). */
    fun frameAt(t: Double, dt: Double) {
        clock += dt
        val k = M.lookAt(t)
        val tt = ((t % M.LOOP) + M.LOOP) % M.LOOP
        val lag = { sec: Double -> M.lookAt(tt - sec).c }
        glow.pool = lag(M.LAG_POOL)
        glow.word = lag(M.LAG_WORD)
        glow.pill = lag(M.LAG_PILL)
        glow.zip = M.zipSince(tt)
        look = k
        lightMix = if (k.wipeP > 0.5) 1.0 else 0.0
        reactBar()
    }

    /** One frame. `rawDt` in seconds. */
    fun frame(rawDt: Double) {
        val dt = min(rawDt, 0.05)
        val now = clock + dt
        clock = now
        var k: ObjectSigninDissolveLook
        if (playing) {
            val t = (loopT + dt) % M.LOOP
            loopT = t
            k = M.lookAt(t)
        } else {
            var st = live
            val isHeld = held
            if (!isHeld && autoPlay && !reduce && now - touchedAt > M.RESUME_AFTER) {
                // 2 s after the last touch: ease to the rest pose, then start the loop again at t = 0
                if (!resuming) {
                    resuming = true
                    val target = M.YAW0 + M.jsRound((st.yaw - M.YAW0) / 360) * 360
                    live = st.copy(yawTarget = target, qTarget = 0.0)
                    st = live
                }
                val done = abs(st.yaw - st.yawTarget) < 0.4 && abs(st.yawV) < 4 && st.c < 0.004 && st.q <= 0.0005 &&
                    abs(st.scale - 1) < 0.001
                if (done) {
                    resuming = false
                    loopT = 0.0
                    playing = true
                    look = M.lookAt(0.0)
                    reactBar()
                    return
                }
            }
            val h = hint
            var breathe = 0.0
            if (isHeld && !reduce) {
                if (now - grip.lastT > M.BREATHE_AFTER) {
                    breathe = M.BREATHE * sin(2 * PI * M.BREATHE_HZ * (now - grip.lastT - M.BREATHE_AFTER))
                }
            }
            st = M.stepLive(st, dt, isHeld, fingerC, reduce, breathe)
            if (h >= 0) {
                // the hint: the pear turns 18 deg and back while a scan line dips 30 pt from the stalk
                val u = h / 1.1
                if (u >= 1) {
                    hint = -1.0
                } else {
                    hint = h + dt
                    val b = sin(PI * u)
                    st = st.copy(c = max(st.c, 0.073 * b * b))
                }
            }
            live = st
            k = M.lookOfLive(st, now)
            if (h >= 0 && h / 1.1 < 1) {
                k = k.copy(yaw = k.yaw + 18 * sin(PI * (h / 1.1)) * (if (reduce) 0.0 else 1.0))
            }
            k = k.copy(seam = M.seamOf(k.c))
            // haptics: the skin closing at the stalk, the spin passing the half-way
            val over = st.c > 0.15
            if (wasOver && st.c < 0.02) {
                wasOver = false
                haptic(ObjectSigninDissolveBeat.CLOSE)
            } else if (over) {
                wasOver = true
            }
            val spinNow = if (abs(st.yawV) > (M.SPIN_LO + M.SPIN_HI) / 2) 1 else 0
            if (spinNow != grip.spun) {
                grip.spun = spinNow
                if (!reduce) haptic(ObjectSigninDissolveBeat.SPIN)
            }
        }
        // the page answers the shell: one-pole lags of 0 / 60 / 120 ms, and one pulse when the skin closes
        val c = k.c
        fun lag(cur: Double, tau: Double) = if (tau <= 0) c else cur + (c - cur) * (1 - exp(-dt / tau))
        var zip = if (glow.zip >= 0) glow.zip + dt else -1.0
        if (zip > 1) zip = -1.0
        if (playing) zip = M.zipSince(loopT)
        else if (glow.pool > 0.2 && c < 0.02 && glow.zip < 0) zip = 0.0
        glow.pool = lag(glow.pool, M.LAG_POOL)
        glow.word = lag(glow.word, M.LAG_WORD)
        glow.pill = lag(glow.pill, M.LAG_PILL)
        glow.zip = zip
        look = k
        // Reduce Motion: the light layer cross-fades (70 ms constant) instead of wiping
        val target = if (k.wipeP > 0.5) 1.0 else 0.0
        lightMix += (target - lightMix) * (1 - exp(-dt / 0.07))
        reactBar()
    }

    /** The status bar's ink follows the page: it flips just before the disc reaches the bar, and back just before it leaves. */
    private fun reactBar() {
        val r = if (reduce) lightMix * reach else wipeRadius()
        val prev = prevR
        prevR = r
        if (prev == null || r == prev) return
        val lead = if (reduce) 0.0 else 260.0
        var next = bar
        if (r > prev && r > dBar - lead) next = 1
        else if (r < prev && r < dBar + lead) next = 0
        if (reduce) next = if (r > reach * 0.5) 1 else 0
        if (next != bar) {
            bar = next
            onBarDark?.invoke(next == 1)
        }
    }

    // ---- the finger (dp, in the canvas's own space) ----

    fun pointerDown(x: Double, y: Double, eventTime: Double) {
        val inBox = x >= boxX && x <= boxX + boxW && y >= boxY && y <= boxY + boxH
        if (!inBox) {
            grip.on = false
            return
        }
        takeOver()
        val st = live
        held = true
        touchedAt = clock
        fingerC = cOfY(y)
        grip.on = true
        grip.x0 = x
        grip.y0 = y
        grip.yaw0 = st.yaw
        grip.t0 = clock
        grip.lastX = x
        grip.lastT = clock
        grip.lastEvent = eventTime
        grip.v = 0.0
        grip.moved = 0.0
        grip.tickC = fingerC
        grip.lastTick = clock
        grip.spun = 0
        live = st.copy(yawV = 0.0)
        if (!reduce) haptic(ObjectSigninDissolveBeat.ONSET)
    }

    fun pointerMove(x: Double, y: Double, eventTime: Double) {
        val g = grip
        if (!g.on) return
        val now = clock
        val st = live
        val yaw = g.yaw0 + (x - g.x0) * M.DEG_PER_PT
        // the finger's rate comes from the events' own timestamps, so two events in one frame do not spike it
        val dtt = max(eventTime - g.lastEvent, 1e-3)
        val v = ((x - g.lastX) * M.DEG_PER_PT) / dtt
        val vf = g.v + (v - g.v) * 0.35 // a smoothed finger rate
        live = st.copy(yaw = yaw, yawV = vf)
        fingerC = cOfY(y)
        g.moved = max(g.moved, hypot(x - g.x0, y - g.y0))
        val travelled = abs(fingerC - g.tickC) * (M.PLANE_BOTTOM - M.PLANE_TOP) * s
        if (travelled >= M.TICK_EVERY * s && now - g.lastTick >= M.TICK_GAP) {
            g.tickC = fingerC
            g.lastTick = now
            haptic(ObjectSigninDissolveBeat.TICK)
        }
        touchedAt = now
        g.lastX = x
        g.lastT = now
        g.lastEvent = eventTime
        g.v = vf
    }

    /** The finger lifted; `velocityX` in dp per second. */
    fun pointerUp(velocityX: Double) {
        val g = grip
        if (!g.on) return
        val st = live
        val tap = g.moved < M.TAP_SLOP && clock - g.t0 < M.TAP_TIME
        val maxFling = M.MAX_TURNS * 360 * 6.02
        val fling = if (reduce) 0.0 else M.clamp(velocityX * M.DEG_PER_PT, -maxFling, maxFling)
        val v = if (tap) 0.0 else fling
        live = st.copy(yawV = v, yawTarget = if (reduce) M.heroNear(st.yaw, 0.0) else M.heroNear(st.yaw, v))
        if (tap && !reduce) hint = 0.0
    }

    fun pointerFinalize() {
        held = false
        touchedAt = clock
        grip.on = false
    }

    fun setTheme(light: Boolean) {
        takeOver()
        val st = live
        live = st.copy(qTarget = if (light) 1.0 else 0.0, q = if (reduce) (if (light) 1.0 else 0.0) else st.q)
        touchedAt = clock
        onTheme?.invoke(light)
    }

    fun a11yBump(dir: Int) {
        takeOver()
        val st = live
        live = st.copy(yawTarget = st.yaw + dir * 180, yawV = 0.0)
        touchedAt = clock
    }

    /** First-open hint (a screen that is not on autoPlay): the pear turns 18 deg and back while a scan line dips. */
    fun showHint() {
        if (autoPlay || reduce) return
        hint = 0.0
    }
}
