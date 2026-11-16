package com.ngocdevv.nativecook.blocks.pickers.neoncontourmood

import com.ngocdevv.nativecook.blocks.pickers.neoncontourmood.NeonContourMoodMotion as M
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.math.tanh

/** Config the frame needs that does not change from frame to frame. */
internal class NeonContourMoodCfg(
    val n: Int, val echoMs: Double, val rings: Double, val spacing: Double, val breathing: Boolean, val reduced: Boolean,
    val cx: Double, val cy: Double, val heroS: Double, val cutoff: Double, val fade: Double, val ground: FloatArray, val core: FloatArray,
    val pack: NeonContourMoodPack, val widths: DoubleArray, val lens: IntArray, val table: DoubleArray,
) {
    val last: Int get() = n - 1
}

/** One animated scalar: a plain write cancels, a spring (with an initial velocity) or a timing runs from the value as it is. */
internal class NeonContourMoodAnim(initial: Double) {
    var value = initial
        private set
    private var target = initial
    private var velocity = 0.0
    private var spring: NeonContourMoodSpring? = null
    private var restDisp = 0.01
    private var restSpeed = 2.0
    // timing
    private var timingFrom = 0.0
    private var timingTo = 0.0
    private var timingDur = 0.0
    private var timingT = 0.0
    private var timing = false
    val running get() = spring != null || timing

    fun set(v: Double) {
        spring = null; timing = false; velocity = 0.0; value = v; target = v
    }

    fun cancel() {
        spring = null; timing = false
    }

    fun spring(to: Double, s: NeonContourMoodSpring, v: Double = 0.0, disp: Double = 0.01, speed: Double = 2.0) {
        timing = false
        spring = s; target = to; velocity = v; restDisp = disp; restSpeed = speed
    }

    /** A default-eased (in-out quad) timing of [seconds]. */
    fun timing(to: Double, seconds: Double) {
        spring = null
        timing = true; timingFrom = value; timingTo = to; timingDur = seconds; timingT = 0.0
    }

    fun step(dt: Double) {
        val s = spring
        if (s != null) {
            val n = max(1, ceil(dt / (1.0 / 240)).toInt())
            val h = dt / n
            var x = value
            var v = velocity
            for (i in 0 until n) {
                val nv = v + (-s.stiffness * (x - target) - s.damping * v) / s.mass * h
                x += nv * h
                v = nv
            }
            value = x; velocity = v
            if (abs(v) < restSpeed && abs(x - target) < restDisp) {
                value = target; velocity = 0.0; spring = null
            }
        } else if (timing) {
            timingT = min(1.0, timingT + dt / timingDur)
            val e = if (timingT < 0.5) 2 * timingT * timingT else 1 - (-2 * timingT + 2) * (-2 * timingT + 2) / 2
            value = timingFrom + (timingTo - timingFrom) * e
            if (timingT >= 1.0) { value = timingTo; timing = false }
        }
    }
}

/**
 * The block's per-frame state machine (RN `ContourMoodPicker`'s frame callback, gestures and `ncm-engine.ts`): autoplay as a pure
 * function of loop time, the finger-driven picker with its ring history, nested holding, label one-shots, release-to-stop, the return to
 * the loop two seconds after the last touch. Time in seconds, positions in dp.
 */
internal class NeonContourMoodEngine(
    val autoPlay: Boolean,
    val reduced: Boolean,
    val c: NeonContourMoodCfg,
    val startMood: Double,
    val restMood: Double,
    val s: Double,
    val layout: NeonContourMoodTheme.Layout,
) {
    var onSelect: (index: Int, live: Boolean) -> Unit = { _, _ -> }
    var onBuzz: (cue: Int, weight: Int) -> Unit = { _, _ -> }
    var onEnd: (Int) -> Unit = {}

    companion object {
        const val CUE_ONSET = 0
        const val CUE_DETENT = 1
        const val CUE_RELEASE = 2
        const val CUE_LIMIT = 3
        const val CUE_NEXT = 4
        const val CUE_PULSE = 5

        // scalar state indices
        const val EV_N = 0
        const val IDX = 1
        const val EV_T = 2
        const val LAB_SETTLED = 3
        const val H_FROM = 4
        const val H_TARGET = 5
        const val H_T0 = 6
        const val DIR = 7
        const val SPEED = 8
        const val HELD_PREV = 9
        const val HAVE_HIST = 10
        const val LAST_TT = 11
        const val LAST_PLAY = 12
        const val END_IDX = 13
        const val LAST_LEAN_X = 14
        const val LAST_LEAN_Y = 15
        const val LAST_SQUEEZE = 16
        const val LAST_PULSE = 17
        const val ST_SIZE = 18
    }

    // rig
    val mood = NeonContourMoodAnim(startMood)
    val look = NeonContourMoodLook(startMood)
    val lab = M.newLab()
    val field = NeonContourMoodField()
    private val ev = M.newEvents()
    private val st = DoubleArray(ST_SIZE)
    private val hist = DoubleArray(7) { startMood }
    private val snap = DoubleArray(M.MAXL)
    private var endTarget = -1.0

    // state
    var clock = 0.0
    private var epoch = -1.0
    private var inited = false
    var playing = autoPlay && !reduced
    private var held = false
    private var touchedAt = -1e6
    private var returnStarted = false
    private var grabX = 0.0
    private var grabY = 0.0
    private var grabMood = startMood
    val press = NeonContourMoodAnim(0.0)
    val buttonPress = NeonContourMoodAnim(0.0)
    val closePress = NeonContourMoodAnim(0.0)
    val leanX = NeonContourMoodAnim(0.0)
    val leanY = NeonContourMoodAnim(0.0)
    val squeeze = NeonContourMoodAnim(0.0)
    private var pulseT0 = -1.0
    private var pinned = -1.0
    private var explicit = -1.0
    val reduceFade = NeonContourMoodAnim(1.0)
    private var lastLab = Math.round(startMood).toInt()

    init {
        val start = Math.round(startMood).toInt()
        M.labelSettled(lab, start, DoubleArray(c.n), c.lens)
        st[IDX] = start.toDouble()
        st[LAST_PULSE] = -1.0
    }

    fun pin(t: Double) { explicit = -1.0; pinned = max(0.0, t) }

    // ---- field fill -----------------------------------------------------------------------------------------------------------

    private fun fillField(look: NeonContourMoodLook, lx: Double, ly: Double, squeezeV: Double, pulse: Double) {
        val u = field
        u.center[0] = c.cx.toFloat(); u.center[1] = c.cy.toFloat()
        u.scale = c.heroS.toFloat()
        u.cutoff = c.cutoff.toFloat()
        u.fade = c.fade.toFloat()
        u.rings = c.rings.toFloat()
        for (i in 0 until 3) { u.ground[i] = c.ground[i]; u.coreCol[i] = c.core[i] }
        u.wake = (if (c.reduced) 0.0 else look.wake).toFloat()
        u.breath = (if (c.breathing && !c.reduced) look.breath else 0.0).toFloat()
        u.radiate = (look.radiate * max(0.0, look.time - M.T_FLICK_END) * 26).toFloat()
        u.spacing = (c.spacing + squeezeV).toFloat()
        u.lean[0] = lx.toFloat(); u.lean[1] = ly.toFloat()
        u.pulse = pulse.toFloat()
        NeonContourMoodShaderMath.packField(u, look.echoes, c.pack)
    }

    private fun labelBusy(now: Double) = now - st[EV_T] < M.LABEL_SETTLE

    private fun playFrame(tt: Double) {
        M.fillTimeline(look, tt, c.echoMs, c.n)
        M.labelFromTable(lab, c.table, look.time, c.widths, c.lens, snap, st[IDX].toInt())
        st[LAST_TT] = look.time
        st[LAST_PLAY] = 1.0
        st[HAVE_HIST] = 0.0
        st[IDX] = lab[M.L_TO]
        fillField(look, 0.0, 0.0, 0.0, -1.0)
    }

    private fun explicitFrame(m: Double) {
        look.mood = m
        for (k in 0 until 7) look.echoes[k] = m
        look.wake = 0.0; look.breath = 0.0; look.radiate = 0.0; look.time = 0.0
        val idx = round(M.clamp(m, 0.0, c.last.toDouble())).toInt()
        st[IDX] = idx.toDouble(); st[LAST_PLAY] = 0.0; st[HAVE_HIST] = 0.0
        M.labelSettled(lab, idx, c.widths, c.lens)
        fillField(look, 0.0, 0.0, 0.0, -1.0)
    }

    /** Takes over from autoplay without a jump: the displayed rings become the history, running switches carry over. */
    private fun handoff(now: Double) {
        for (k in 0 until 7) hist[k] = look.echoes[k]
        st[H_FROM] = 0.0; st[H_TARGET] = 0.0; st[H_T0] = now; st[DIR] = 0.0; st[SPEED] = 0.0; st[HAVE_HIST] = 1.0
        if (st[LAST_PLAY] == 1.0) {
            val tt = st[LAST_TT]
            val table = c.table
            var count = 0
            while (count < table.size / 3 && table[count * 3] <= tt) count++
            val from = max(0, count - 4)
            var n = 0
            var e = from
            while (e < count && n < M.MAXEV) {
                ev[n * 3] = now - (tt - table[e * 3])
                ev[n * 3 + 1] = table[e * 3 + 1]
                ev[n * 3 + 2] = table[e * 3 + 2]
                n++; e++
            }
            st[EV_N] = n.toDouble()
            st[EV_T] = if (n > 0) ev[(n - 1) * 3] else -99.0
            st[IDX] = lab[M.L_TO]
            st[LAB_SETTLED] = 0.0
            st[LAST_PLAY] = 0.0
        }
    }

    /** One frame of the finger-driven picker. Sets [END_IDX] and calls [onEnd] when a release has come to rest. */
    private fun interactiveFrame(now: Double, dt: Double) {
        if (st[HAVE_HIST] != 1.0) handoff(now)
        val prevM = look.mood
        val last = c.last.toDouble()
        val m = M.clamp(mood.value, 0.0, last)
        val safeDt = if (dt < 0.001) 0.001 else dt
        var changed = false

        if (held && st[HELD_PREV] != 1.0) st[DIR] = 0.0
        st[HELD_PREV] = if (held) 1.0 else 0.0
        val inst = abs(m - prevM) / safeDt
        st[SPEED] = M.mix(st[SPEED], inst, 1 - exp(-safeDt / M.SPEED_TAU))
        if (inst > M.HOLD_SLOW && m != prevM) st[DIR] = if (m > prevM) 1.0 else -1.0
        val wasHolding = st[H_TARGET] == 1.0
        val wantHold = !c.reduced && held && st[DIR] != 0.0 && (if (wasHolding) st[SPEED] < M.HOLD_SLOW * 1.5 else st[SPEED] < M.HOLD_SLOW)
        if (wantHold != wasHolding) {
            st[H_FROM] = M.holdProfile(st[H_FROM], st[H_TARGET], now - st[H_T0])
            st[H_TARGET] = if (wantHold) 1.0 else 0.0
            st[H_T0] = now
        }
        val hFrom = st[H_FROM]
        val hTarget = st[H_TARGET]
        val hAge = now - st[H_T0]
        val dir = st[DIR]
        val lo = max(0.0, m - 1.6)
        val hi = min(last, m + 1.6)
        val echoes = look.echoes
        for (k in 0 until 7) {
            var h = m
            if (k > 0 && !c.reduced) {
                val tau = if (returnStarted) 0.08 else (k * c.echoMs) / 1000
                val v = hist[k] + (m - hist[k]) * (1 - exp(-safeDt / (if (tau < 0.001) 0.001 else tau)))
                h = if (v < lo) lo else if (v > hi) hi else v
            }
            hist[k] = h
            var e = h
            if (k > 0 && !c.reduced) {
                val nested = M.clamp(m - dir * k * M.HOLD_GAP, 0.0, last)
                e = h + (nested - h) * M.holdProfile(hFrom, hTarget, hAge - k * M.HOLD_RING_DELAY)
            }
            if (abs(e - echoes[k]) > 0.0001) changed = true
            echoes[k] = e
        }
        val holdNow = M.holdProfile(hFrom, hTarget, hAge)
        val speed = abs(m - prevM) / safeDt
        val wake = if (c.reduced) 0.0 else max(M.clamp(speed / 10), look.wake * exp(-safeDt / M.WAKE_TAU))
        val breath = if (c.breathing && !c.reduced) 1.2 * kotlin.math.sin(now * Math.PI) * holdNow else 0.0
        val pressV = press.value
        if (abs(m - prevM) > 0.00001 || abs(wake - look.wake) > 0.0001 || abs(pressV - look.press) > 0.0001 || abs(breath - look.breath) > 0.0001) changed = true
        look.mood = m; look.wake = wake; look.press = pressV; look.breath = breath; look.radiate = 0.0; look.time = 0.0
        val pulse = if (pulseT0 >= 0 && now - pulseT0 < 0.5) now - pulseT0 else -1.0
        val lx = leanX.value
        val ly = leanY.value
        val sq = squeeze.value
        if (lx != st[LAST_LEAN_X] || ly != st[LAST_LEAN_Y] || sq != st[LAST_SQUEEZE] || pulse != st[LAST_PULSE]) changed = true
        st[LAST_LEAN_X] = lx; st[LAST_LEAN_Y] = ly; st[LAST_SQUEEZE] = sq; st[LAST_PULSE] = pulse
        if (holdNow > 0.0001 && holdNow < 0.9999) changed = true

        // one label, one word, one tag
        val idx = M.settleLabel(st[IDX].toInt(), m, c.last)
        if (idx != st[IDX].toInt()) {
            if (c.reduced) {
                M.labelSettled(lab, idx, c.widths, c.lens)
            } else {
                st[EV_N] = M.pushEvent(ev, st[EV_N].toInt(), now, st[IDX].toInt(), idx).toDouble()
                st[EV_T] = now
                st[LAB_SETTLED] = 0.0
            }
            st[IDX] = idx.toDouble()
        }
        if (!c.reduced) {
            if (labelBusy(now)) {
                M.computeLabel(lab, ev, st[EV_N].toInt(), now, st[IDX].toInt(), c.widths, c.lens, snap)
            } else if (st[LAB_SETTLED] != 1.0) {
                M.computeLabel(lab, ev, st[EV_N].toInt(), st[EV_T] + 99, st[IDX].toInt(), c.widths, c.lens, snap)
                st[LAB_SETTLED] = 1.0
            }
        }

        // a release that has come to rest
        val target = endTarget
        if (target >= 0 && !held && abs(m - target) < 0.01 && st[SPEED] < 0.05) {
            st[END_IDX] = target
            endTarget = -1.0
            onEnd(target.toInt())
        }
        if (changed) fillField(look, lx, ly, sq, pulse)
    }

    private fun nearAll(a: DoubleArray, v: Double, tol: Double): Boolean {
        for (x in a) if (abs(x - v) > tol) return false
        return true
    }

    // ---- frame ----------------------------------------------------------------------------------------------------------------

    fun frame(now: Double, rawDt: Double) {
        val dt = min(max(rawDt, 0.0), 0.05)
        clock = now
        mood.step(dt); press.step(dt); buttonPress.step(dt); closePress.step(dt)
        leanX.step(dt); leanY.step(dt); squeeze.step(dt); reduceFade.step(dt)
        val last = c.last.toDouble()
        if (!inited) {
            explicitFrame(M.clamp(mood.value, 0.0, last))
            inited = true
        }
        var handled = false
        if (pinned >= 0 && !reduced) {
            playFrame(pinned); handled = true
        } else if (explicit >= 0) {
            explicitFrame(explicit); handled = true
        } else if (playing && !reduced) {
            if (epoch < 0) epoch = now
            playFrame(now - epoch); handled = true
        }
        if (!handled) {
            interactiveFrame(now, dt)
            val m = M.clamp(mood.value, 0.0, last)
            if (autoPlay && !held && now - touchedAt >= M.RESUME_AFTER && pinned < 0) {
                if (!returnStarted) {
                    returnStarted = true
                    if (reduced) {
                        reduceFade.set(0.0)
                        mood.set(restMood)
                        reduceFade.timing(1.0, M.REDUCED_DIP)
                    } else mood.spring(restMood, M.GLIDE)
                }
                if (!reduced && now - touchedAt >= M.RESUME_AFTER + M.RETURN_DURATION && abs(m - restMood) < 0.002 && nearAll(look.echoes, restMood, 0.004)) {
                    mood.set(restMood)
                    epoch = -1.0
                    playing = true
                    returnStarted = false
                }
            }
        }
        // the label (one integer, one word, one tag) is the only thing that says "a stop was crossed"
        val to = lab[M.L_TO].toInt()
        if (to != lastLab) {
            lastLab = to
            onSelect(to, !playing && !returnStarted && pinned < 0 && explicit < 0)
        }
    }

    // ---- gestures -------------------------------------------------------------------------------------------------------------

    private fun takeOver() {
        mood.cancel()
        mood.set(look.mood)
        playing = false
        pinned = -1.0
        explicit = -1.0
        touchedAt = clock
        returnStarted = false
    }

    fun sliderBegin(x: Double, y: Double) {
        takeOver()
        held = true
        endTarget = -1.0
        grabX = x; grabY = y; grabMood = look.mood
        if (!reduced) press.spring(1.0, M.PRESS, 0.0, 0.001, 0.05)
        onBuzz(CUE_ONSET, 1)
    }

    fun sliderUpdate(x: Double) {
        if (!reduced) mood.set(M.rubberMood(grabMood + (x - grabX) / ((layout.lastX - layout.firstX) / c.last), c.last.toDouble()))
    }

    fun sliderEnd(x: Double, velocityX: Double) {
        val moved = abs(x - grabX) > 5 * s
        val v = velocityX / ((layout.lastX - layout.firstX) / c.last)
        val last = c.last.toDouble()
        val target = if (moved && !reduced) M.projectedStop(mood.value, v, last)
        else round(M.clamp(((x + layout.sliderX - layout.firstX) / (layout.lastX - layout.firstX)) * last, 0.0, last))
        release(target, if (moved) v else 0.0)
    }

    private fun release(target: Double, velocity: Double) {
        endTarget = target
        if (reduced) {
            reduceFade.set(0.0)
            mood.set(target)
            reduceFade.timing(1.0, M.REDUCED_DIP)
        } else mood.spring(target, M.SNAP, velocity)
        onBuzz(if (target == 0.0 || target == c.last.toDouble()) CUE_LIMIT else CUE_RELEASE, 1)
    }

    fun sliderFinalize() {
        held = false
        touchedAt = clock
        press.spring(0.0, M.PRESS, 0.0, 0.001, 0.05)
    }

    fun heroBegin(x: Double, y: Double) {
        takeOver()
        held = true
        endTarget = -1.0
        grabX = x; grabY = y; grabMood = look.mood
        if (!reduced) press.spring(1.0, M.PRESS, 0.0, 0.001, 0.05)
        onBuzz(CUE_ONSET, 1)
    }

    fun heroUpdate(x: Double, y: Double) {
        if (reduced) return
        mood.set(M.rubberMood(grabMood + (x - grabX) / (NeonContourMoodTheme.POINTS_PER_STOP * s), c.last.toDouble()))
        squeeze.set(M.squeezeFor(y - grabY, NeonContourMoodTheme.SQUEEZE_MAX * c.spacing))
        val dx = (x - layout.w / 2) / s
        val dy = (y - (layout.heroY - layout.heroTouchTop)) / s
        val r = sqrt(dx * dx + dy * dy)
        val k = if (r > 0.000001) (NeonContourMoodTheme.LEAN_MAX * tanh(r / NeonContourMoodTheme.LEAN_REACH)) / r else 0.0
        leanX.set(dx * k)
        leanY.set(dy * k)
    }

    fun heroEnd(x: Double, velocityX: Double) {
        val moved = abs(x - grabX) > 5 * s
        val v = velocityX / (NeonContourMoodTheme.POINTS_PER_STOP * s)
        val last = c.last.toDouble()
        val target = if (moved && !reduced) M.projectedStop(mood.value, v, last) else round(M.clamp(mood.value, 0.0, last))
        release(target, if (moved) v else 0.0)
        if (!reduced && abs(squeeze.value) > NeonContourMoodTheme.SQUEEZE_MAX * c.spacing * 0.3) pulseT0 = clock
    }

    fun heroFinalize() {
        held = false
        touchedAt = clock
        press.spring(0.0, M.PRESS, 0.0, 0.001, 0.05)
        squeeze.spring(0.0, M.SQUEEZE, squeeze.value * 0.0, 0.001, 0.05)
        leanX.spring(0.0, M.SQUEEZE, 0.0, 0.001, 0.05)
        leanY.spring(0.0, M.SQUEEZE, 0.0, 0.001, 0.05)
    }

    fun heroTapBegin() = takeOver()

    fun heroTapEnd() {
        if (reduced) return
        pulseT0 = clock
        onBuzz(CUE_PULSE, 1)
    }

    fun selectStop(target: Int) {
        takeOver()
        endTarget = target.toDouble()
        if (reduced) {
            reduceFade.set(0.0)
            mood.set(target.toDouble())
            reduceFade.timing(1.0, M.REDUCED_DIP)
        } else mood.spring(target.toDouble(), M.SNAP)
    }

    fun next(): Int {
        val index = round(M.clamp(look.mood, 0.0, c.last.toDouble())).toInt()
        selectStop(index)
        onBuzz(CUE_NEXT, 1)
        return index
    }

    fun close() {
        onBuzz(CUE_RELEASE, 1)
        selectStop(round(restMood).toInt())
    }

    fun buttonDown() = buttonPress.set(if (reduced) 0.0 else 1.0)
    fun buttonUp() = buttonPress.spring(0.0, M.PRESS, 0.0, 0.001, 0.05)
    fun closeDown() = closePress.set(if (reduced) 0.0 else 1.0)
    fun closeUp() = closePress.spring(0.0, M.PRESS, 0.0, 0.001, 0.05)

    /** Overshoot past the ends is drawn as a displacement of the thumb (the rubber band), not while autoplay runs. */
    fun edgeOffset(): Double {
        if (reduced || playing) return 0.0
        val last = c.last.toDouble()
        return ((mood.value - M.clamp(mood.value, 0.0, last)) * (layout.lastX - layout.firstX)) / last
    }
}

