package com.ngocdevv.nativecook.blocks.charts.sparkspherescore

import com.ngocdevv.nativecook.blocks.charts.sparkspherescore.SparkSphereScoreMotion as M
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal object SparkSphereScoreCue {
    const val ONSET = 0
    const val DETENT = 1
    const val CROSS = 2
    const val LIMIT = 3
    const val SETTLE = 4
    const val PROBE = 5
    const val TAP = 6
}

internal object SparkSphereScorePhase {
    const val AUTO = 0
    const val DRAG = 1
    const val RETURN = 2
    const val REST = 3
}

/** One step of an animation: wait, ease (Reanimated's default in-out quad) or spring. */
internal class SparkSphereScoreSeg(
    val kind: Int, val to: Double = 0.0, val duration: Double = 0.0,
    val spring: SparkSphereScoreSpring? = null, val velocity: Double = 0.0, val done: (() -> Unit)? = null,
) {
    companion object {
        const val WAIT = 0
        const val TIMING = 1
        const val SPRING = 2
    }
}

/** A value that follows a queue of waits, timings and springs (Reanimated's `withDelay` / `withSequence` / `withTiming` / `withSpring`). */
internal class SparkSphereScoreAnim(var value: Double) {
    private val queue = ArrayDeque<SparkSphereScoreSeg>()
    private var cur: SparkSphereScoreSeg? = null
    private var elapsed = 0.0
    private var from = value
    private var velocity = 0.0
    val active get() = cur != null || queue.isNotEmpty()

    fun set(v: Double) {
        cancel()
        value = v
    }

    fun cancel() {
        queue.clear()
        cur = null
        velocity = 0.0
    }

    fun spring(to: Double, cfg: SparkSphereScoreSpring, velocity: Double = 0.0, done: (() -> Unit)? = null) =
        run(listOf(SparkSphereScoreSeg(SparkSphereScoreSeg.SPRING, to, 0.0, cfg, velocity, done)))

    fun timing(to: Double, duration: Double, done: (() -> Unit)? = null) =
        run(listOf(SparkSphereScoreSeg(SparkSphereScoreSeg.TIMING, to, duration, done = done)))

    fun run(segs: List<SparkSphereScoreSeg>) {
        cancel()
        queue.addAll(segs)
    }

    private fun begin(s: SparkSphereScoreSeg) {
        cur = s
        elapsed = 0.0
        from = value
        velocity = s.velocity
    }

    fun step(dt: Double) {
        var left = dt
        var guard = 0
        while (guard++ < 8) {
            if (cur == null) {
                val n = queue.removeFirstOrNull() ?: return
                begin(n)
            }
            val s = cur ?: return
            when (s.kind) {
                SparkSphereScoreSeg.WAIT -> {
                    elapsed += left
                    if (elapsed < s.duration) return
                    left = elapsed - s.duration
                    finish(s)
                }
                SparkSphereScoreSeg.TIMING -> {
                    elapsed += left
                    val p = min(elapsed / s.duration, 1.0)
                    val e = if (p < 0.5) 2 * p * p else 1 - 2 * (1 - p) * (1 - p)
                    value = from + (s.to - from) * e
                    if (p < 1.0) return
                    left = elapsed - s.duration
                    value = s.to
                    finish(s)
                }
                else -> {
                    val cfg = s.spring ?: return
                    val n = max(1, Math.ceil(left / (1.0 / 240)).toInt())
                    val h = left / n
                    var x = value
                    var v = velocity
                    for (i in 0 until n) {
                        val nv = v + (-cfg.stiffness * (x - s.to) - cfg.damping * v) / cfg.mass * h
                        x += nv * h
                        v = nv
                    }
                    value = x
                    velocity = v
                    if (abs(v) < 2.0 && abs(x - s.to) < 0.01) {
                        value = s.to
                        velocity = 0.0
                        finish(s)
                    }
                    return
                }
            }
        }
    }

    private fun finish(s: SparkSphereScoreSeg) {
        cur = null
        s.done?.invoke()
    }
}

/**
 * The block's state machine (RN `useSphereEngine`, the two gestures and the card / hint reactions). Time in seconds, positions in dp.
 * AUTO: every channel is a closed-form function of the loop time; DRAG / RETURN / REST: `v` is written by a finger or a spring and the
 * lagged paces, swirl and agitation are integrated with the same step function the script's table was built with.
 */
internal class SparkSphereScoreEngine(
    val autoPlay: Boolean,
    val reduce: Boolean,
    val layout: SparkSphereScoreTheme.Layout,
    initial: Double,
    val snapBack: Boolean,
    val touchable: Boolean,
    private val px: Double,
    val bandFrom: DoubleArray,
) {
    var cue: (Int) -> Unit = {}
    var onChange: ((Double) -> Unit)? = null

    val v = SparkSphereScoreAnim(initial)
    var reading = initial
    var phase = if (autoPlay && !reduce) SparkSphereScorePhase.AUTO else SparkSphereScorePhase.REST
    var time = 0.0
    var cyc = 0.0
    var touchedAt = -99.0
    var pinT = -1.0
    var pinV = -99.0
    private var prevV = initial
    var vRim = initial
    var vHol = initial
    var vFilm = initial
    var vCard = initial
    var vScore = initial
    val grab = SparkSphereScoreAnim(0.0)
    private var stir = 0.0
    private var swirl = 0.0
    private var agit = 0.0
    private var stirIn = 0.0
    var probeX = 0.0
    var probeY = 0.0
    val probeHeat = SparkSphereScoreAnim(0.0)
    val dimple = SparkSphereScoreAnim(0.0)
    private var dimpleAngle = 0.0
    private var pulseT0 = -99.0
    private var tapT0 = -99.0
    var pulseAge = 99.0
    private var tapAge = 99.0
    private val fingerOffset = SparkSphereScoreAnim(0.0)
    private var lastDetent = 0.0
    private var edgeHit = false
    private var scrubTick = 0.0
    private var downAt = 0.0

    val bandAlpha = Array(max(bandFrom.size, 1)) { SparkSphereScoreAnim(0.0) }
    private var lastBand = -1
    var a11yReading = initial

    private val frameRec = SparkSphereScoreFrame()
    val disc = SparkSphereScoreDiscUniforms()
    private val st = DoubleArray(3)
    private val pal = M.DEFAULT_PALETTE

    private val x0 = layout.rulerX0
    private val pitch = layout.rulerPitch
    private val xMax = x0 + ((M.V_MAX - M.V_MIN) / M.V_STEP) * pitch

    init {
        frameRec.reduceMotion = reduce
        frameRec.cx = layout.discCx
        frameRec.cy = layout.discCy
        frameRec.scale = layout.discScale
        frameRec.px = px
        frameRec.p3 = 0.0
        frameRec.vRim = initial; frameRec.vHol = initial; frameRec.vFilm = initial
        M.discUniforms(frameRec, pal, disc)
        val b = M.bandOf(initial, bandFrom)
        for (i in bandAlpha.indices) bandAlpha[i].value = if (i == b) 1.0 else 0.0
        lastBand = b
        // the discovery nudge: 1.4 s after mount the cursor slides three ticks right and back (only when nothing plays)
        if (!autoPlay && !reduce && touchable) {
            v.run(
                listOf(
                    SparkSphereScoreSeg(SparkSphereScoreSeg.WAIT, duration = M.HINT_AT),
                    SparkSphereScoreSeg(SparkSphereScoreSeg.TIMING, initial + M.HINT_TICKS * 0.05, 0.42),
                    SparkSphereScoreSeg(SparkSphereScoreSeg.TIMING, initial, 0.62),
                ),
            )
        }
    }

    fun cursorX(): Double = x0 + ((v.value - M.V_MIN) / M.V_STEP) * pitch

    // ---- dev / preview hook ----
    fun pin(t: Double) { pinV = -99.0; pinT = t }

    private fun toPace(xf: Double): Double {
        var xc = xf
        if (xf < x0) xc = x0 - M.rubberPt(x0 - xf) else if (xf > xMax) xc = xMax + M.rubberPt(xf - xMax)
        return M.V_MIN + ((xc - x0) / pitch) * M.V_STEP
    }

    /** One frame: advance the animations, then the engine (RN `useFrameCallback`). */
    fun frame(dtIn: Double) {
        val dt = M.clamp(dtIn, 0.001, 0.05)
        v.step(dt); grab.step(dt); probeHeat.step(dt); dimple.step(dt); fingerOffset.step(dt)
        for (a in bandAlpha) a.step(dt)
        val now = time + dt
        time = now
        var c = cyc + dt
        var wrapped = false
        if (c >= M.LOOP) { c -= M.LOOP; wrapped = true }
        cyc = c
        val pinned = pinT >= 0
        val pinnedV = pinV > -50
        var ph = phase
        if (reduce && ph == SparkSphereScorePhase.AUTO) ph = SparkSphereScorePhase.REST

        if (pinned || (ph == SparkSphereScorePhase.AUTO && autoPlay)) {
            val tl = if (pinned) pinT else c
            val vv = M.scriptV(tl)
            v.set(vv)
            vRim = M.scriptV(tl - M.LAG_RIM)
            vHol = M.scriptV(tl - M.LAG_HOLLOW)
            vFilm = M.scriptV(tl - M.LAG_FILM)
            vCard = M.scriptV(tl - M.LAG_CARD)
            vScore = M.scriptV(tl - M.LAG_SCORE)
            grab.set(M.scriptGrab(tl))
            swirl = M.scriptSwirl(tl)
            agit = M.scriptAgit(tl)
            stir = 0.0
            prevV = vv
            pulseAge = M.scriptPulseAge(tl)
            tapAge = if (tl >= M.T_TAP) tl - M.T_TAP else 99.0
            probeHeat.set(0.0)
            dimple.set(0.0)
        } else if (pinnedV) {
            val vv = pinV
            v.set(vv)
            vRim = vv; vHol = vv; vFilm = vv; vCard = vv; vScore = vv
            prevV = vv
        } else {
            val vv = v.value
            val pv = prevV
            val vd = (vv - pv) / dt
            prevV = vv
            vRim += (vv - vRim) * (1 - exp(-dt / M.LAG_RIM))
            vHol += (vv - vHol) * (1 - exp(-dt / M.LAG_HOLLOW))
            vFilm += (vv - vFilm) * (1 - exp(-dt / M.LAG_FILM))
            vCard += (vv - vCard) * (1 - exp(-dt / M.LAG_CARD))
            vScore += (vv - vScore) * (1 - exp(-dt / M.LAG_SCORE))
            val extra = stirIn
            stirIn = extra * exp(-dt / 0.9)
            st[0] = stir; st[1] = swirl; st[2] = agit
            M.stepStir(st, vv, if (reduce) 0.0 else vd, dt, if (reduce) 0.0 else extra)
            stir = st[0]
            swirl = if (reduce) 0.0 else st[1]
            agit = if (reduce) 0.0 else st[2]
            if ((pv - 1) * (vv - 1) < 0) pulseT0 = now
            pulseAge = now - pulseT0
            tapAge = now - tapT0
            if (autoPlay && !reduce && ph != SparkSphereScorePhase.DRAG && ph != SparkSphereScorePhase.AUTO && wrapped) {
                val idle = now - touchedAt
                val settled = abs(vv - reading) < 0.004 && abs(vd) < 0.05 && abs(swirl) < 0.03 && probeHeat.value < 0.02 && grab.value < 0.02
                if (idle > M.IDLE_RESUME && settled) {
                    phase = SparkSphereScorePhase.AUTO
                    cyc = 0.0; swirl = 0.0; agit = 0.0; stir = 0.0; stirIn = 0.0
                    c = 0.0
                }
            } else if (ph == SparkSphereScorePhase.RETURN && abs(vv - reading) < 0.004 && abs(vd) < 0.05) {
                phase = SparkSphereScorePhase.REST
            }
        }

        val tf = if (pinned) pinT else c
        val f = frameRec
        f.vRim = vRim; f.vHol = vHol; f.vFilm = vFilm
        f.cyc = tf / M.LOOP
        f.swirl = swirl; f.agit = agit
        f.probeX = probeX; f.probeY = probeY; f.probeHeat = probeHeat.value
        f.dimple = dimple.value; f.dimpleAngle = dimpleAngle
        f.pulseAge = pulseAge; f.tapAge = tapAge
        M.discUniforms(f, pal, disc)

        // the card's copy: a time-based cross-fade at the band boundaries
        val b = M.bandOf(vCard, bandFrom)
        if (b != lastBand) {
            val inst = lastBand < 0 || pinned || pinnedV || reduce
            for (i in bandAlpha.indices) {
                val target = if (i == b) 1.0 else 0.0
                if (inst) bandAlpha[i].set(target) else bandAlpha[i].timing(target, 0.16)
            }
            lastBand = b
        }
        // the label for assistive tech settles with the sphere
        if (phase == SparkSphereScorePhase.REST) {
            val r = Math.round(v.value * 20) / 20.0
            if (r != a11yReading) a11yReading = r
        }
    }

    // ---- the sphere: a warm probe ----
    fun probeBegin(lx: Double, ly: Double) {
        val t = time
        downAt = t
        touchedAt = t
        if (phase == SparkSphereScorePhase.AUTO) {
            phase = SparkSphereScorePhase.RETURN
            pulseT0 = t - pulseAge
            v.spring(reading, M.COOL)
        }
        probeX = lx / layout.discScale
        probeY = ly / layout.discScale
        probeHeat.spring(1.0, M.GLIDE)
        cue(SparkSphereScoreCue.PROBE)
    }

    fun probeUpdate(lx: Double, ly: Double, vx: Double, vy: Double) {
        probeX = lx / layout.discScale
        probeY = ly / layout.discScale
        val r = sqrt(lx * lx + ly * ly)
        val rim = layout.discR - r
        val near = rim < M.DIMPLE_REACH * layout.discScale && r > layout.discR * 0.5
        dimple.spring(if (near) M.DIMPLE / 163.6 else 0.0, M.DIMPLE_SPRING)
        if (near) dimpleAngle = atan2(ly, lx)
        val vt = if (r > 8) (lx * vy - ly * vx) / r else 0.0
        stirIn = M.clamp(M.STIR_PER_PT * vt, -M.STIR_MAX, M.STIR_MAX)
    }

    fun probeEnd(dx: Double, dy: Double) {
        val t = time
        touchedAt = t
        probeHeat.timing(0.0, 0.6)
        dimple.spring(0.0, M.DIMPLE_SPRING)
        val still = abs(dx) < 8 && abs(dy) < 8 && t - downAt < 0.35
        if (still && M.coldOf(vRim) > 0.5) {
            tapT0 = t
            cue(SparkSphereScoreCue.TAP)
        }
    }

    // ---- the ruler ----
    fun rulerBegin(x: Double) {
        v.cancel()
        fingerOffset.cancel()
        val t = time
        phase = SparkSphereScorePhase.DRAG
        touchedAt = t
        pinT = -1.0
        pinV = -99.0
        val vf = toPace(x)
        fingerOffset.set(v.value - vf)
        fingerOffset.spring(0.0, M.SNAP)
        grab.spring(1.0, M.CURSOR)
        lastDetent = floor(v.value / M.KEY_STEP + 1e-6)
        edgeHit = false
        scrubTick = t
        cue(SparkSphereScoreCue.ONSET)
    }

    fun rulerUpdate(x: Double) {
        val vf = toPace(x)
        val nv = vf + fingerOffset.value
        val pv = v.value
        v.set(nv)
        val t = time
        val d = floor(nv / M.KEY_STEP + 1e-6)
        val crossed = (pv - 1) * (nv - 1) < 0 || (pv - 0) * (nv - 0) < 0
        if (crossed) cue(SparkSphereScoreCue.CROSS)
        else if (d != lastDetent && t - scrubTick > 0.06) {
            scrubTick = t
            cue(SparkSphereScoreCue.DETENT)
        }
        lastDetent = d
        val out = nv < M.V_MIN || nv > M.V_MAX
        if (out && !edgeHit) {
            edgeHit = true
            cue(SparkSphereScoreCue.LIMIT)
        } else if (!out) edgeHit = false
        if (t - touchedAt > 0.1) touchedAt = t
    }

    fun rulerEnd(velocityX: Double) {
        val t = time
        touchedAt = t
        grab.spring(0.0, M.CURSOR)
        val vel = velocityX * M.X_PER_PT
        phase = SparkSphereScorePhase.RETURN
        val nv = v.value
        if (snapBack) {
            val tgt = reading
            if (reduce) v.timing(tgt, 0.2) { cue(SparkSphereScoreCue.SETTLE) }
            else v.spring(tgt, M.COOL, vel) { cue(SparkSphereScoreCue.SETTLE) }
        } else {
            val proj = M.clamp(Math.round((nv + vel * M.RELEASE_PROJECT) / M.V_STEP) * M.V_STEP, M.V_MIN, M.V_MAX)
            reading = proj
            v.spring(proj, M.GLIDE, vel)
            onChange?.invoke(proj)
        }
    }

    /** Assistive tech: the ruler as an adjustable control, a tenth per action. */
    fun nudge(dir: Int) {
        val nv = M.clamp(Math.round((a11yReading + dir * M.KEY_STEP) / M.V_STEP) * M.V_STEP, M.V_MIN, M.V_MAX)
        a11yReading = nv
        phase = SparkSphereScorePhase.RETURN
        touchedAt = time
        if (snapBack) {
            v.run(
                listOf(
                    SparkSphereScoreSeg(SparkSphereScoreSeg.TIMING, nv, 0.2),
                    SparkSphereScoreSeg(SparkSphereScoreSeg.WAIT, duration = 1.8),
                    SparkSphereScoreSeg(SparkSphereScoreSeg.SPRING, reading, spring = M.COOL),
                ),
            )
        } else {
            reading = nv
            v.spring(nv, M.GLIDE)
            onChange?.invoke(nv)
        }
    }
}
