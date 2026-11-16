package com.ngocdevv.nativecook.blocks.pickers.beamshapetorch

import com.ngocdevv.nativecook.blocks.pickers.beamshapetorch.BeamShapeTorchMotion as M
import com.ngocdevv.nativecook.blocks.pickers.beamshapetorch.BeamShapeTorchTheme as Theme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/** The haptic beats (touch only; the autoplay loop is silent). */
internal object BeamShapeTorchBeat {
    const val ONSET = 0
    const val TICK = 1
    const val CLICK = 2
    const val FOCUS_LOCK = 3
    const val CEILING = 4
    const val REVEAL = 5
}

/** What a VoiceOver / TalkBack reader says about the control. */
internal data class BeamShapeTorchA11y(val b: Double, val w: Double, val open: Boolean)

/**
 * Timeline + finger -> one [look] the canvas reads (RN `BeamControl`'s frame callback and gestures), all on the main thread: the
 * frame loop and the pointer events both run there, so nothing is shared across threads.
 *
 * State: three animated numbers and the live values. The timeline plays `look` while the autoplay loop runs; a finger hands it to
 * the live values (brightness b, spread w, capsule m / mw, press), which springs and timings then drive.
 */
internal class BeamShapeTorchEngine(
    val autoPlay: Boolean,
    val reduce: Boolean,
    val aMin: Double,
    val aMax: Double,
    defaultB: Double,
    defaultW: Double,
    startOpen: Boolean,
    startOn: Boolean? = null,
) {
    private class Grip {
        var region = 0 // 0 none, 1 capsule, 2 island, 3 outside
        var x0 = 0.0
        var y0 = 0.0
        var t0 = 0.0
        var b0 = 1.0
        var w0 = 1.0
        var d0 = 0.0
        var axis = 0 // 0 undecided, 1 horizontal leads, 2 vertical leads
        var moved = 0.0
        var rawB = 1.0
        var pinch = false
        var wp0 = 1.0
        var tickB = 10
        var tickT = 8
        var tickAt = -1.0
        var wasOff = false
        var handle = false // the finger took the sun
        var glide = false // ... and landed far from it: both values glide to it first
        var degPrev = 0.0
        var degAt = -1.0
        var degSpeed = 0.0

        fun reset(region: Int, x: Double, y: Double, t: Double) {
            this.region = region
            x0 = x
            y0 = y
            t0 = t
            b0 = 1.0
            w0 = 1.0
            d0 = 0.0
            axis = 0
            moved = 0.0
            rawB = 1.0
            pinch = false
            wp0 = 1.0
            tickB = 10
            tickT = 8
            tickAt = -1.0
            wasOff = false
            handle = false
            glide = false
            degPrev = 0.0
            degAt = -1.0
            degSpeed = 0.0
        }
    }

    // ---- host hooks ----
    var cue: (Int) -> Unit = {}
    var onChange: ((Double, Double, Double) -> Unit)? = null
    var onChangeEnd: ((Double, Double, Double) -> Unit)? = null
    var onToggle: ((Boolean) -> Unit)? = null
    var onExpandedChange: ((Boolean) -> Unit)? = null
    var onReport: ((BeamShapeTorchA11y) -> Unit)? = null

    /** Controlled: the light is on (brightness above 0). Null = the component's own. */
    var on: Boolean? = startOn
    /** Controlled: the capsule is open. Null = the component's own. */
    var expanded: Boolean? = null

    // ---- animated values ----
    private val initialB = if (on == false) 0.0 else M.clamp01(defaultB)
    val bL = BeamShapeTorchAnim(initialB)
    val wL = BeamShapeTorchAnim(M.clamp01(defaultW))
    val mL = BeamShapeTorchAnim(if (startOpen) 1.0 else 0.0)
    val mwL = BeamShapeTorchAnim(if (startOpen) 1.0 else 0.0)
    val pressL = BeamShapeTorchAnim(1.0)
    val afterL = BeamShapeTorchAnim(0.0)
    val reducedFade = BeamShapeTorchAnim(1.0)
    val sunGrab = BeamShapeTorchAnim(0.0)

    var look: BeamShapeTorchLook = BeamShapeTorchLook(
        initialB, M.clamp01(defaultW), if (startOpen) 1.0 else 0.0, if (startOpen) 1.0 else 0.0, 1.0, 1.0, 0.0, 0.0,
    )
        private set
    var clock = 0.0
        private set
    private var loopT = 0.0
    private var playing = autoPlay && !reduce
    private var openT = startOpen
    private var hotL = initialB
    private var lastOn = M.clamp01(defaultB).let { if (it == 0.0) 1.0 else it }
    private var touching = false
    private var touchedAt = -1e9
    private var resuming = false
    private val grip = Grip()
    private var handleOn = false
    private var fx = 0.0
    private var fy = 0.0
    private var prevB = initialB
    private var prevW = M.clamp01(defaultW)
    private var vB = 0.0
    private var vW = 0.0
    private var dwell = 0.0
    private var focusAt = -1.0
    private var ceilOn = M.opticsOf(initialB, defaultW, aMin, aMax).reach >= M.CEILING_ON
    private var lastB = initialB
    private var lastW = M.clamp01(defaultW)
    private var lastEmit = -1.0
    private var moveAt = -1.0
    private var pend = false
    private var endPending = false
    private var lastOnState = initialB > 0.01
    private var redrawFlag = true

    // ---- layout (set by the host, dp) ----
    private var s = 1.0
    private var cx = 0.0
    private var top = 0.0
    private var lensY = 0.0
    private var cap = Theme.Box(0.0, 0.0, 0.0, 0.0, 0.0)
    private var isl = Theme.Box(0.0, 0.0, 0.0, 0.0, 0.0)

    fun layout(l: Theme.Layout) {
        s = l.s
        cap = l.cap
        isl = l.isl
        cx = l.cap.x + l.cap.w / 2
        top = l.cap.y
        lensY = l.iy(Theme.LENS_Y)
        redrawFlag = true
    }

    fun consumeRedraw(): Boolean {
        val r = redrawFlag
        redrawFlag = false
        return r
    }

    /** The capsule's rect from the look: height leads, width follows, both scale with the press. */
    class CapRect(val x: Double, val y: Double, val w: Double, val h: Double, val r: Double)

    fun capRect(k: BeamShapeTorchLook = look): CapRect {
        val h = M.mix(isl.h, cap.h, k.m) * k.press
        val w = M.mix(isl.w, cap.w, k.mw) * k.press
        return CapRect(cx - w / 2, top, w, h, M.mix(isl.r, cap.r, min(k.m, k.mw)))
    }

    /** The content shrinks toward the island with the capsule, and fades before it reaches it: (scale, opacity). */
    fun innerScale(k: BeamShapeTorchLook = look): Double = M.mix(0.55, 1.0, min(k.m, k.mw)) * k.press
    fun innerShow(k: BeamShapeTorchLook = look): Double = M.smooth(0.4, 0.92, min(k.m, k.mw))

    /** Only the capsule (or the island) takes touches, with a little slop; the rest of the screen is the host's. */
    fun inHitArea(x: Double, y: Double): Boolean {
        val c = capRect()
        val slop = if (look.m > 0.9) 6 * s else 14.0
        return x >= c.x - slop && x <= c.x + c.w + slop && y >= c.y - slop && y <= c.y + c.h + slop
    }

    val isOpenNow: Boolean get() = look.m > 0.9

    // ---- the frame ----

    private fun stepAnims(dt: Double) {
        bL.step(dt)
        wL.step(dt)
        mL.step(dt)
        mwL.step(dt)
        pressL.step(dt)
        afterL.step(dt)
        reducedFade.step(dt)
        sunGrab.step(dt)
    }

    /** Frame time pinned at t seconds of the loop (previews and checks). */
    fun frameAt(t: Double, dt: Double) {
        clock += dt
        look = M.lookAt(t)
        redrawFlag = true
    }

    /** One frame; `rawDt` in seconds. Returns true when the picture changed. */
    fun frame(rawDt: Double): Boolean {
        val dt = min(rawDt, 0.05)
        val now = clock + dt
        clock = now
        stepAnims(dt)
        if (playing) {
            val t = (loopT + dt) % M.LOOP
            loopT = t
            var k = M.lookAt(t)
            if (on == false) k = k.copy(b = 0.0, hot = 0.0)
            val ex = expanded
            if (ex != null) {
                val m = if (ex) 1.0 else 0.0
                k = k.copy(m = m, mw = m)
            }
            look = k
            redrawFlag = true
            return true
        }

        // the sun as the handle: the finger is the clock, both values follow its position (after a short glide when it landed far)
        if (handleOn) {
            val g = grip
            val sc = M.mix(0.55, 1.0, min(mL.value, mwL.value)) * pressL.value
            val lensPx = top + (lensY - top) * sc
            val sol = M.solveSun(abs(fx - cx) / (s * sc), (lensPx - fy) / (s * sc), g.b0, g.w0, aMin, aMax, Theme.SUN_ALONG)
            val x = if (g.glide) min((now - g.t0) / M.SUN_GLIDE, 1.0) else 1.0
            val e = if (x >= 1) 1.0
            else M.springAt(x * M.SUN_GLIDE, M.SPRING_PRESS.response, M.SPRING_PRESS.zeta) /
                M.springAt(M.SUN_GLIDE, M.SPRING_PRESS.response, M.SPRING_PRESS.zeta)
            val rawB = M.mix(g.b0, sol.b, e)
            val rawW = M.mix(g.w0, sol.w, e)
            bL.set(M.rubber(rawB))
            wL.set(M.rubber(rawW))
            g.rawB = rawB
            tickCheck(M.rubber(rawB), M.rubber(rawW))
            touchedAt = now
        }

        val b = if (on == false) 0.0 else bL.value
        val w = wL.value
        val bC = M.clamp01(b)
        val wC = M.clamp01(w)
        var hot = hotL
        val tau = if (b > hot) 0.06 else 0.14 // incandescent lag: quick to heat, slow to cool
        hot += (b - hot) * (1 - exp(-dt / tau))
        hotL = hot
        // the speed of both axes, for the release of the sun
        vB = vB * 0.6 + 0.4 * ((b - prevB) / max(dt, 0.001))
        vW = vW * 0.6 + 0.4 * ((w - prevW) / max(dt, 0.001))
        prevB = b
        prevW = w

        if (focusAt >= 0 && now >= focusAt) {
            focusAt = -1.0
            if (!touching && !reduce) cue(BeamShapeTorchBeat.FOCUS_LOCK)
        }

        val o = M.opticsOf(look.b, look.w, aMin, aMax)
        // a held pencil leaves an afterimage in the fan as it opens
        if (!reduce) {
            val held = wC < 0.12 && bC > 0.5
            if (held) {
                dwell += dt
            } else {
                if (dwell >= M.DWELL && bC > 0.3) {
                    afterL.run(
                        BeamShapeTorchAnim.Step.Timing(1.0, 0.08),
                        BeamShapeTorchAnim.Step.Timing(0.0, M.AFTER_FADE, ease = { 1 - (1 - it) * (1 - it) }),
                    )
                }
                dwell = 0.0
            }
            // the pencil lands on the ceiling: one Rigid beat, re-armed below 190 pt
            val reach = o.reach
            if (!ceilOn && reach >= M.CEILING_ON && bC > 0.9 && wC < 0.1) {
                ceilOn = true
                cue(BeamShapeTorchBeat.CEILING)
            } else if (ceilOn && (reach < M.CEILING_OFF || wC > 0.15)) {
                ceilOn = false
            }
        }

        // what the host hears: at most every 60 ms while either axis moves, once more when they settle
        if (abs(bC - lastB) + abs(wC - lastW) > 0.0008) {
            lastB = bC
            lastW = wC
            moveAt = now
            pend = true
        }
        val deg = (o.theta * 180) / PI
        if (pend && now - lastEmit >= 0.06) {
            pend = false
            lastEmit = now
            onChange?.invoke(bC, wC, deg)
        }
        val isOn = bC > 0.01
        if (on == null && isOn != lastOnState && !touching) {
            lastOnState = isOn
            onToggle?.invoke(isOn)
        }
        if (endPending && !touching && !pend && now - moveAt > 0.15) {
            endPending = false
            onChangeEnd?.invoke(bC, wC, deg)
        }

        // the look is only rewritten (and the canvas only redrawn) when a value changed
        if (abs(hot - b) < 1e-4) hot = b
        hotL = hot
        val prev = look
        val shimmers = !reduce && bC > 0.01 && 1 - wC > 0.02 && (touching || now - touchedAt < M.RESUME_AFTER)
        val next = BeamShapeTorchLook(
            b = b,
            w = w,
            m = mL.value,
            mw = mwL.value,
            press = pressL.value,
            hot = if (reduce) b else hot,
            phase = if (shimmers) (now / M.LOOP) % 1.0 else prev.phase,
            after = if (reduce) 0.0 else afterL.value,
        )
        var changed = false
        if (shimmers || next != prev) {
            look = next
            changed = true
        }
        if (reducedFade.running) changed = true
        if (changed) redrawFlag = true
        if (!autoPlay || reduce || on != null || expanded != null) return changed
        if (touching || now - touchedAt < M.RESUME_AFTER) return changed
        if (!resuming) {
            // ease back to the rest frame, then the loop starts again at t = 0
            resuming = true
            for (a in arrayOf(bL, wL, mL, mwL, pressL)) {
                a.run(BeamShapeTorchAnim.Step.Timing(1.0, 0.6, ease = { M.bezier(0.65, 0.0, 0.35, 1.0, it) }))
            }
            openT = true
            return changed
        }
        val settled = abs(b - 1) < 0.002 && abs(wL.value - 1) < 0.002 && abs(mL.value - 1) < 0.002 &&
            abs(mwL.value - 1) < 0.002 && abs(hot - 1) < 0.01
        if (settled) {
            resuming = false
            loopT = 0.0
            playing = true
        }
        return changed
    }

    /** ticks: every 10 % of brightness, every 5 deg of half-angle (denser above 60 deg/s); a click when the lamp comes on from off */
    private fun tickCheck(b: Double, w: Double) {
        val g = grip
        val now = clock
        val deg = (M.opticsOf(b, w, aMin, aMax).theta * 180) / PI
        val tb = floor(M.clamp01(b) * 10 + 1e-4).toInt()
        val tt = floor(deg / M.TICK_THETA).toInt()
        val inst = if (g.degAt >= 0) abs(deg - g.degPrev) / max(now - g.degAt, 0.001) else 0.0
        val speed = if (g.degAt >= 0) g.degSpeed * 0.7 + inst * 0.3 else 0.0
        val gap = if (speed > M.TICK_FAST_DEG) M.TICK_GAP_FAST else M.TICK_GAP
        if ((tb != g.tickB || tt != g.tickT) && now - g.tickAt > gap) {
            g.tickAt = now
            if (g.wasOff && b > 0.02) {
                g.wasOff = false
                cue(BeamShapeTorchBeat.CLICK)
            } else {
                cue(BeamShapeTorchBeat.TICK)
            }
        }
        g.tickB = tb
        g.tickT = tt
        g.degPrev = deg
        g.degAt = now
        g.degSpeed = speed
    }

    // ---- the finger ----

    private fun takeOver() {
        resuming = false
        val k = look
        // Cancel every in-flight spring before reading it: re-grabbing or reversing never fights a previous release.
        bL.cancel()
        wL.cancel()
        mL.cancel()
        mwL.cancel()
        pressL.cancel()
        if (!playing) return
        playing = false
        bL.set(k.b)
        wL.set(k.w)
        mL.set(k.m)
        mwL.set(k.mw)
        pressL.set(k.press)
        hotL = k.hot
        openT = k.m > 0.5
        prevB = k.b
        prevW = k.w
        lastB = M.clamp01(k.b)
        lastW = M.clamp01(k.w)
        lastOnState = k.b > 0.01
    }

    private fun report() {
        endPending = true
        onReport?.invoke(BeamShapeTorchA11y(M.clamp01(bL.value), M.clamp01(wL.value), openT))
    }

    private fun toggle() {
        if (on != null) {
            onToggle?.invoke(on == false)
            cue(BeamShapeTorchBeat.CLICK)
            return
        }
        if (bL.value > 0.01) {
            lastOn = max(bL.value, 0.35)
            bL.run(BeamShapeTorchAnim.Step.Timing(0.0, if (reduce) 0.2 else 0.22, ease = { M.bezier(0.32, 0.0, 0.67, 1.0, it) }))
        } else {
            bL.run(
                if (reduce) BeamShapeTorchAnim.Step.Timing(lastOn, 0.2)
                else BeamShapeTorchAnim.Step.Spring(lastOn, M.springCfg(0.45, 1.0)),
            )
        }
        cue(BeamShapeTorchBeat.CLICK)
    }

    private fun setOpen(open: Boolean, user: Boolean) {
        if (expanded != null && user) {
            onExpandedChange?.invoke(open)
            return
        }
        val cfg = if (open) M.springCfg(M.SPRING_EXPAND.response, M.SPRING_EXPAND.zeta)
        else M.springCfg(M.SPRING_COLLAPSE.response, M.SPRING_COLLAPSE.zeta)
        openT = open
        if (reduce) {
            // Fade out, replace the geometry while invisible, fade in: no island travel under Reduce Motion.
            reducedFade.run(
                BeamShapeTorchAnim.Step.Timing(0.0, 0.1, done = {
                    mL.set(if (open) 1.0 else 0.0)
                    mwL.set(if (open) 1.0 else 0.0)
                    reducedFade.run(BeamShapeTorchAnim.Step.Timing(1.0, 0.1))
                }),
            )
        } else {
            mL.run(BeamShapeTorchAnim.Step.Spring(if (open) 1.0 else 0.0, cfg))
            mwL.run(
                BeamShapeTorchAnim.Step.Delay(if (open) 0.0 else 0.05),
                BeamShapeTorchAnim.Step.Spring(if (open) 1.0 else 0.0, cfg),
            )
        }
        if (open && bL.value < 0.01) {
            bL.run(BeamShapeTorchAnim.Step.Delay(0.25), BeamShapeTorchAnim.Step.Spring(lastOn, M.springCfg(0.9, 1.0)))
        }
        cue(BeamShapeTorchBeat.REVEAL)
        if (user) onExpandedChange?.invoke(open)
    }

    /** Controlled props: the host flips them, the capsule follows from where it is. */
    fun onPropChanged() {
        val o = on
        if (o != null) {
            val isOn = bL.value > 0.01
            if (o != isOn) {
                takeOver()
                if (o) {
                    bL.run(
                        if (reduce) BeamShapeTorchAnim.Step.Timing(lastOn, 0.2)
                        else BeamShapeTorchAnim.Step.Spring(lastOn, M.springCfg(0.45, 1.0)),
                    )
                } else {
                    lastOn = max(bL.value, 0.35)
                    bL.run(BeamShapeTorchAnim.Step.Timing(0.0, 0.22, ease = { M.bezier(0.32, 0.0, 0.67, 1.0, it) }))
                }
            }
        }
        val e = expanded
        if (e != null && e != openT) {
            takeOver()
            setOpen(e, false)
        }
    }

    fun panBegin(px: Double, py: Double) {
        focusAt = -1.0
        touching = true
        touchedAt = clock
        val k = look
        val c = capRect(k)
        val open = k.m > 0.9
        val slop = 6 * s
        val inCap = px > c.x - slop && px < c.x + c.w + slop && py > c.y - slop && py < c.y + c.h + slop
        val inIsland = px > isl.x - 14 && px < isl.x + isl.w + 14 && py > isl.y - 14 && py < isl.y + isl.h + 14
        val region = if (open && inCap) 1 else if (!open && inIsland) 2 else 3
        val g = grip
        g.reset(region, px, py, clock)
        if (region == 1) {
            takeOver()
            val b = bL.value
            val w = wL.value
            val deg = (M.opticsOf(b, w, aMin, aMax).theta * 180) / PI
            g.b0 = b
            g.w0 = w
            g.rawB = b
            g.d0 = abs(px - cx)
            g.tickB = floor(M.clamp01(b) * 10 + 1e-4).toInt()
            g.tickT = floor(deg / M.TICK_THETA).toInt()
            g.wasOff = b < 0.01
            g.degPrev = deg
            // the sun is the handle: the hit test at touch-down decides, so the relative drag keeps everything else
            val sc = innerScale(k)
            val so = M.sunOffset(M.clamp01(b), M.clamp01(w), aMin, aMax, Theme.SUN_ALONG)
            val sunX = cx + so.x * s * sc
            val sunY = top + (lensY - top) * sc - so.y * s * sc
            val away = hypot(px - sunX, py - sunY)
            if (away <= M.SUN_HIT * s) {
                g.handle = true
                g.glide = away > M.SUN_NEAR * s
                handleOn = true
                fx = px
                fy = py
                if (!reduce) sunGrab.run(BeamShapeTorchAnim.Step.Spring(1.0, M.SNAP))
            }
            if (!reduce) pressL.run(BeamShapeTorchAnim.Step.Spring(M.PRESS_SCALE, M.SNAP))
            cue(BeamShapeTorchBeat.ONSET)
        }
    }

    /** `tx`, `ty`: the finger's travel since touch-down; `ax`, `ay`: where it is now. */
    fun panUpdate(ax: Double, ay: Double, tx: Double, ty: Double) {
        val g = grip
        val dist = hypot(tx, ty)
        g.moved = max(g.moved, dist)
        if (g.region != 1) return
        if (g.handle) {
            fx = ax
            fy = ay
            return
        }
        if (g.axis == 0 && dist > M.AXIS_LEAD * s) g.axis = if (abs(tx) > abs(ty)) 1 else 2
        val gy = if (g.axis == 1) M.FOLLOW else 1.0
        val gx = if (g.axis == 2) M.FOLLOW else 1.0
        g.rawB = g.b0 - (ty * gy) / (M.PT_PER_BRIGHTNESS * s)
        val b = M.rubber(g.rawB)
        bL.set(b)
        if (!g.pinch) {
            val rawW = g.w0 + ((abs(ax - cx) - g.d0) * gx) / (M.PT_PER_SPREAD * s)
            wL.set(M.rubber(rawW))
        }
        touchedAt = clock
        tickCheck(b, wL.value)
    }

    /** The finger lifted; velocities in dp per second. */
    fun panEnd(ax: Double, vx: Double, vy: Double) {
        val g = grip
        val tap = g.moved < M.TAP_SLOP * s && clock - g.t0 < M.TAP_TIME
        val ease = { x: Double -> M.bezier(0.23, 1.0, 0.32, 1.0, x) }
        if (g.region == 1 && g.handle) {
            handleOn = false
            sunGrab.run(BeamShapeTorchAnim.Step.Spring(0.0, M.SNAP))
            if (g.rawB < M.OFF_BELOW) {
                lastOn = max(g.b0, 0.35)
                bL.run(BeamShapeTorchAnim.Step.Timing(0.0, 0.18, ease))
                cue(BeamShapeTorchBeat.CLICK)
            } else {
                val b = M.clamp01(bL.value)
                val w = M.clamp01(wL.value)
                val bT = M.clamp01(b + vB * M.PROJECT_B)
                val wT = M.clamp01(w + vW * M.PROJECT_W)
                bL.run(BeamShapeTorchAnim.Step.Timing(bT, 0.14, ease))
                if (reduce) wL.set(wT) else wL.run(BeamShapeTorchAnim.Step.Spring(wT, M.POP, vW))
                if (abs(wT - g.w0) > 0.08 && !reduce) focusAt = clock + M.FOCUS_LOCK_DELAY
            }
        } else if (g.region == 1) {
            if (tap) {
                toggle()
            } else if (g.rawB < M.OFF_BELOW) {
                lastOn = max(g.b0, 0.35)
                bL.run(BeamShapeTorchAnim.Step.Timing(0.0, 0.18, ease))
                cue(BeamShapeTorchBeat.CLICK)
            } else {
                val gy = if (g.axis == 1) M.FOLLOW else 1.0
                val gx = if (g.axis == 2) M.FOLLOW else 1.0
                val vb = (-vy * gy) / (M.PT_PER_BRIGHTNESS * s)
                val bT = M.clamp01(M.clamp01(bL.value) + vb * M.PROJECT_B)
                bL.run(BeamShapeTorchAnim.Step.Timing(bT, 0.14, ease))
                if (!g.pinch) {
                    val side = if (ax >= cx) 1.0 else -1.0
                    val vw = (side * vx * gx) / (M.PT_PER_SPREAD * s)
                    val w = wL.value
                    val wT = M.clamp01(M.clamp01(w) + vw * M.PROJECT_W)
                    if (reduce) wL.set(wT) else wL.run(BeamShapeTorchAnim.Step.Spring(wT, M.POP, vw))
                    if (abs(wT - g.w0) > 0.08 && !reduce) focusAt = clock + M.FOCUS_LOCK_DELAY
                }
            }
        } else if (tap && g.region == 2) {
            takeOver()
            setOpen(true, true)
        }
    }

    fun panFinalize() {
        val g = grip
        handleOn = false
        if (g.handle) sunGrab.run(BeamShapeTorchAnim.Step.Spring(0.0, M.SNAP))
        if (g.region == 1 && !reduce) pressL.run(BeamShapeTorchAnim.Step.Spring(1.0, M.SNAP))
        touching = false
        touchedAt = clock
        val had = g.region != 0
        g.region = 0
        g.handle = false
        if (had) report()
    }

    fun pinchBegin() {
        val g = grip
        if (g.region != 1 || g.handle) return
        g.pinch = true
        g.wp0 = wL.value
    }

    fun pinchUpdate(scale: Double) {
        val g = grip
        if (!g.pinch) return
        wL.set(M.rubber(g.wp0 + (scale - 1) * M.PINCH_GAIN))
    }

    fun pinchEnd(velocity: Double) {
        val g = grip
        if (!g.pinch) return
        val vw = velocity * M.PINCH_GAIN
        val wT = M.clamp01(M.clamp01(wL.value) + vw * M.PROJECT_W)
        if (reduce) wL.set(wT) else wL.run(BeamShapeTorchAnim.Step.Spring(wT, M.POP, vw))
        if (!reduce) focusAt = clock + M.FOCUS_LOCK_DELAY
    }

    /** A tap on the page while the capsule is open closes it into the island. */
    fun closeTap() {
        if (look.m < 0.9) return
        touchedAt = clock
        takeOver()
        setOpen(false, true)
        report()
    }

    // ---- screen reader: one adjustable control, with actions for focus and on/off ----

    fun a11yAction(name: String, state: BeamShapeTorchA11y): BeamShapeTorchA11y {
        takeOver()
        playing = false
        resuming = false
        touchedAt = clock
        endPending = true
        val b = M.clamp01(bL.value)
        val w = M.clamp01(wL.value)
        var next = state
        val fade = { to: Double -> BeamShapeTorchAnim.Step.Timing(to, 0.2) }
        when (name) {
            "increment" -> {
                val nb = min(1.0, round(b * 10 + 1) / 10)
                next = next.copy(b = nb)
                bL.run(fade(nb))
            }
            "decrement" -> {
                val nb = max(0.0, round(b * 10 - 1) / 10)
                next = next.copy(b = nb)
                bL.run(fade(nb))
            }
            "narrow" -> {
                val nw = max(0.0, w - 0.25)
                next = next.copy(w = nw)
                wL.run(fade(nw))
            }
            "widen" -> {
                val nw = min(1.0, w + 0.25)
                next = next.copy(w = nw)
                wL.run(fade(nw))
            }
            "activate" -> {
                val isOpen = expanded ?: state.open
                if (!isOpen) {
                    if (expanded == null) {
                        setOpen(true, false)
                        next = next.copy(open = true)
                    }
                    onExpandedChange?.invoke(true)
                } else {
                    val o = on
                    if (o != null) {
                        onToggle?.invoke(!o)
                    } else {
                        val nb = if (b > 0.01) 0.0 else lastOn
                        next = next.copy(b = nb)
                        if (b > 0.01) lastOn = max(b, 0.35)
                        bL.run(fade(nb))
                    }
                }
            }
            "escape" -> {
                if (expanded == null) {
                    setOpen(false, false)
                    next = next.copy(open = false)
                }
                onExpandedChange?.invoke(false)
            }
        }
        return next
    }
}
