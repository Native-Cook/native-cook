package com.ngocdevv.nativecook.blocks.charts.sparkspherescore

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal class SparkSphereScoreSpring(val damping: Double, val stiffness: Double, val mass: Double)

internal class SparkSphereScorePalette(val at: DoubleArray, val lab: DoubleArray)

/** Every state `discUniforms` hands the disc shader (RN `SssFrame`). */
internal class SparkSphereScoreFrame {
    var vRim = 0.8
    var vHol = 0.8
    var vFilm = 0.8
    var cyc = 0.0
    var swirl = 0.0
    var agit = 0.0
    var probeX = 0.0
    var probeY = 0.0
    var probeHeat = 0.0
    var dimple = 0.0
    var dimpleAngle = 0.0
    var pulseAge = 99.0
    var tapAge = 99.0
    var reduceMotion = false
    var beats = 8
    var cx = 0.0
    var cy = 0.0
    var scale = 1.0
    var px = 3.0
    var p3 = 0.0
}

/** The uniforms of the disc shader, packed as in `sss-shaders.ts`. */
internal class SparkSphereScoreDiscUniforms {
    val geo = FloatArray(4)
    val memA = FloatArray(4)
    val memB = FloatArray(4)
    val dim = FloatArray(4)
    val s1 = FloatArray(4)
    val s2 = FloatArray(4)
    val look = FloatArray(4)
    val clock = FloatArray(4)
    val coreO = FloatArray(3)
    val coreI = FloatArray(3)
    val hazeC = FloatArray(3)
    val rimC = FloatArray(4)
    val shape = FloatArray(4)
    val misc = FloatArray(4)
    val base = FloatArray(3)
    val probe = FloatArray(4)
    val band = FloatArray(4)
    val ring = FloatArray(4)
}

/** The RN block's motion table (`sss-motion.ts`): every number, curve and the 8 s script. */
internal object SparkSphereScoreMotion {
    const val LOOP = 8.0
    const val V_MIN = -1.0
    const val V_MAX = 3.0
    const val V_REST = 0.8
    const val V_STEP = 0.05
    const val R0 = 163.6

    const val X_PER_PT = 0.05 / 4.174
    const val OVER_PT = 18.0
    const val OVER_K = 0.3
    const val RELEASE_PROJECT = 0.12
    const val IDLE_RESUME = 2.0
    const val HINT_AT = 1.4
    const val HINT_TICKS = 3
    const val PROBE_RADIUS = 70.0
    const val DIMPLE_REACH = 24.0
    const val DIMPLE = 6.0
    const val STIR_PER_PT = 0.012
    const val STIR_MAX = 4.0
    const val KEY_STEP = 0.1

    fun rubberPt(over: Double): Double = if (over <= 0) 0.0 else OVER_PT * (1 - exp(-(over * OVER_K) / OVER_PT))

    val SNAP = SparkSphereScoreSpring(30.5, 322.3, 1.0)
    val GLIDE = SparkSphereScoreSpring(22.1, 157.9, 1.0)
    val COOL = SparkSphereScoreSpring(14.0, 60.0, 1.0)
    val DIMPLE_SPRING = SparkSphereScoreSpring(14.0, 220.0, 1.0)
    val CURSOR = SparkSphereScoreSpring(16.0, 320.0, 1.0)
    val COOL_RESPONSE = (2 * PI) / sqrt(60.0)
    val COOL_ZETA = 14 / (2 * sqrt(60.0))

    const val LAG_RIM = 0.15
    const val LAG_HOLLOW = 0.4
    const val LAG_FILM = 0.27
    const val LAG_CARD = 0.2
    const val LAG_SCORE = 0.06

    const val T_TAP = 4.05
    const val SCRIPT_HI = 2.4
    const val SCRIPT_LO = -0.6

    fun clamp(x: Double, a: Double, b: Double) = if (x < a) a else if (x > b) b else x
    fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t
    fun smoothstep(a: Double, b: Double, x: Double): Double {
        val t = clamp((x - a) / (b - a), 0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    /** cubic-bezier(0.65, 0, 0.35, 1) by Newton on x. */
    fun inOut(p: Double): Double {
        val x1 = 0.65; val y1 = 0.0; val x2 = 0.35; val y2 = 1.0
        val cx = 3 * x1; val bx = 3 * (x2 - x1) - cx; val ax = 1 - cx - bx
        val cy = 3 * y1; val by = 3 * (y2 - y1) - cy; val ay = 1 - cy - by
        var u = p
        for (i in 0 until 6) {
            val x = ((ax * u + bx) * u + cx) * u - p
            val dx = (3 * ax * u + 2 * bx) * u + cx
            if (abs(dx) < 1e-6) break
            u -= x / dx
        }
        u = clamp(u, 0.0, 1.0)
        return ((ay * u + by) * u + cy) * u
    }

    /** 0 to 1 step response of a damped spring (mass 1), analytic. */
    fun springAt(t: Double, response: Double, zeta: Double): Double {
        if (t <= 0) return 0.0
        val w = (2 * PI) / response
        if (zeta >= 1) return 1 - exp(-w * t) * (1 + w * t)
        val wd = w * sqrt(1 - zeta * zeta)
        return 1 - exp(-zeta * w * t) * (cos(wd * t) + ((zeta * w) / wd) * sin(wd * t))
    }

    private fun wrapLoop(t0: Double) = ((t0 % LOOP) + LOOP) % LOOP

    fun scriptV(t0: Double): Double {
        val t = wrapLoop(t0)
        if (t < 0.6) return V_REST
        if (t < 0.72) return V_REST - 0.04 * sin((PI * (t - 0.6)) / 0.12)
        if (t < 2.0) return mix(V_REST, SCRIPT_HI, inOut((t - 0.72) / 1.28))
        if (t < 2.6) return SCRIPT_HI
        if (t < 3.8) return mix(SCRIPT_HI, SCRIPT_LO, inOut((t - 2.6) / 1.2))
        if (t < 4.6) return SCRIPT_LO
        return mix(SCRIPT_LO, V_REST, springAt(t - 4.6, COOL_RESPONSE, COOL_ZETA))
    }

    fun scriptGrab(t0: Double): Double {
        val t = wrapLoop(t0)
        if (t < 0.6) return 0.0
        if (t < 0.72) return smoothstep(0.6, 0.72, t)
        if (t < 4.6) return 1.0
        return 1 - smoothstep(4.6, 4.9, t)
    }

    fun heatOf(v: Double) = clamp((v - V_REST) / (V_MAX - V_REST), 0.0, 1.0)
    fun coldOf(v: Double) = smoothstep(0.35, -0.55, v)
    fun fillOf(v: Double) = 0.85 * smoothstep(0.88, 2.3, v)

    // ---- OKLab palette ----
    private fun srgbToLin(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun linToSrgb(c: Double): Double {
        val x = if (c <= 0) 0.0 else c
        return if (x <= 0.0031308) x * 12.92 else 1.055 * x.pow(1 / 2.4) - 0.055
    }
    private fun toOklab(r: Double, g: Double, b: Double, out: DoubleArray, o: Int) {
        val lr = srgbToLin(r); val lg = srgbToLin(g); val lb = srgbToLin(b)
        val l = cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb)
        val m = cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb)
        val s = cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb)
        out[o] = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s
        out[o + 1] = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s
        out[o + 2] = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s
    }
    private fun fromOklab(L: Double, a: Double, b: Double, out: DoubleArray) {
        val l = (L + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val m = (L - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val s = (L - 0.0894841775 * a - 1.291485548 * b).pow(3)
        out[0] = linToSrgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s)
        out[1] = linToSrgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s)
        out[2] = linToSrgb(-0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s)
    }

    /** The ramp (v, sRGB 0..1): mint-white below zero, the pin's green at rest, lime, amber at 2.0x, amber-orange at 3.0x. */
    private val PALETTE = arrayOf(
        doubleArrayOf(-1.0, 0.812, 1.0, 0.941),
        doubleArrayOf(-0.35, 0.49, 0.96, 0.84),
        doubleArrayOf(0.8, 0.18, 0.941, 0.627),
        doubleArrayOf(1.5, 0.784, 0.941, 0.227),
        doubleArrayOf(2.0, 1.0, 0.69, 0.18),
        doubleArrayOf(3.0, 1.0, 0.541, 0.18),
    )

    val DEFAULT_PALETTE: SparkSphereScorePalette = run {
        val at = DoubleArray(PALETTE.size)
        val lab = DoubleArray(PALETTE.size * 3)
        for ((i, p) in PALETTE.withIndex()) {
            at[i] = p[0]
            toOklab(p[1], p[2], p[3], lab, i * 3)
        }
        SparkSphereScorePalette(at, lab)
    }

    /** sRGB 0..1 of the spark colour at pace v, written into `out`. */
    fun colourAt(v: Double, out: DoubleArray, tab: SparkSphereScorePalette = DEFAULT_PALETTE) {
        val n = tab.at.size
        var i = 0
        while (i < n - 2 && v > tab.at[i + 1]) i++
        val t = clamp((v - tab.at[i]) / (tab.at[i + 1] - tab.at[i]), 0.0, 1.0)
        val a = i * 3
        val b = a + 3
        val L = tab.lab
        fromOklab(mix(L[a], L[b], t), mix(L[a + 1], L[b + 1], t), mix(L[a + 2], L[b + 2], t), out)
        out[0] = clamp(out[0], 0.0, 1.0)
        out[1] = clamp(out[1], 0.0, 1.0)
        out[2] = clamp(out[2], 0.0, 1.0)
    }

    // ---- the score ----
    const val SCORE_CHRONO = 32.2
    const val SCORE_PER_UNIT = 11.5
    const val SCORE_PIVOT = 1.0
    fun scoreOf(v: Double) = SCORE_CHRONO + SCORE_PER_UNIT * (v - SCORE_PIVOT)
    val BAND_FROM = doubleArrayOf(-1000.0, 0.0, 1.0, 2.0)
    fun bandOf(v: Double, from: DoubleArray): Int {
        var b = 0
        for (i in 1 until from.size) if (v >= from[i]) b = i
        return b
    }

    // ---- membrane ----
    private val REST_MEM = doubleArrayOf(0.0257, -2.0402, 0.01437, 0.43109, 0.0061, 0.53582, 0.0, 0.0)
    private val LIVE_W = doubleArrayOf(1.0, 0.7, 0.5, 0.35)
    private val LIVE_PSI = doubleArrayOf(0.6, 2.1, -1.2, 0.4)
    private val LIVE_N = doubleArrayOf(1.0, -2.0, 3.0, -1.0)

    fun membrane(cyc: Double, heat: Double, cold: Double, ringAmp: Double, ringPhase: Double, out: DoubleArray) {
        val live = (0.5 + 4.5 * heat) * (1 - cold)
        for (k in 0 until 4) {
            val e0 = REST_MEM[2 * k] * (1 - 0.6 * cold)
            val p0 = REST_MEM[2 * k + 1]
            var a = (live * LIVE_W[k]) / R0
            var p = LIVE_PSI[k] + 2 * PI * LIVE_N[k] * cyc
            if (k == 0) {
                a += ringAmp
                p += ringPhase
            }
            val re = e0 * cos(p0) + a * cos(p)
            val im = e0 * sin(p0) + a * sin(p)
            out[2 * k] = sqrt(re * re + im * im)
            out[2 * k + 1] = atan2(im, re)
        }
    }

    fun beat(cyc: Double, beats: Int = 8): Double {
        val f = (cyc * beats) % 1.0
        return sin(PI * f).pow(6)
    }

    // ---- pulse at 1.0x ----
    const val PULSE_DURATION = 0.42
    const val PULSE_BAND_WIDTH = 30 / 163.6
    const val PULSE_BAND_AMP = 0.75
    const val PULSE_RING_AMP = 4 / 163.6
    const val PULSE_RING_TAU = 0.3
    const val PULSE_RING_PERIOD = 0.32
    const val RIPPLE_SPEED = 600.0
    const val RIPPLE_AMOUNT = 4.0

    fun pulseRing(age: Double) = if (age < 0) 0.0 else PULSE_RING_AMP * exp(-age / PULSE_RING_TAU)

    // ---- integrators ----
    const val STIR_GAIN = 0.9
    const val STIR_TAU = 0.9
    const val STIR_BASE = 0.9

    /** Pure step of the stir / swirl / agitation state: the live gesture and the script's table both call this. s = [stir, swirl, agit]. */
    fun stepStir(s: DoubleArray, v: Double, vdot: Double, dt: Double, extra: Double = 0.0) {
        val cold = coldOf(v)
        val heat = heatOf(v)
        val target = clamp(STIR_GAIN * vdot + extra, -STIR_MAX, STIR_MAX)
        s[0] += (target - s[0]) * (1 - exp(-dt / STIR_TAU))
        s[0] = clamp(s[0], -STIR_MAX, STIR_MAX)
        val omega = s[0] + STIR_BASE * smoothstep(V_REST, 2.4, v) - STIR_BASE * smoothstep(0.0, -0.6, v)
        val tauHome = mix(1.4, 0.5, cold)
        s[1] += (omega - s[1] / tauHome) * dt
        s[1] = clamp(s[1], -4.5, 4.5)
        s[2] += 0.9 * heat * dt + (1 - heat) * (s[2].roundToInt().toDouble() - s[2]) * (1 - exp(-dt * 1.6))
    }

    private const val TABLE_HZ = 240
    private val swirlTab: DoubleArray
    private val agitTab: DoubleArray
    private val cross: DoubleArray

    init {
        val n = (LOOP * TABLE_HZ).toInt()
        val swirl = DoubleArray(n + 1)
        val agit = DoubleArray(n + 1)
        val s = doubleArrayOf(0.0, 0.0, 0.0)
        val dt = 1.0 / TABLE_HZ
        var prev = scriptV(0.0)
        val crossings = ArrayList<Double>()
        for (i in 1..n) {
            val t = i * dt
            val v = scriptV(t)
            stepStir(s, v, (v - prev) / dt, dt)
            if ((prev - 1) * (v - 1) < 0) crossings.add(t - dt * 0.5)
            prev = v
            swirl[i] = s[1] * (1 - smoothstep(5.8, 7.4, t))
            agit[i] = s[2]
        }
        val total = agit[n]
        val whole = max(total.roundToInt(), 1)
        val k = whole / max(total, 1e-6)
        for (i in 0..n) agit[i] *= k
        swirlTab = swirl
        agitTab = agit
        cross = crossings.toDoubleArray()
    }

    private fun lut(tab: DoubleArray, t0: Double): Double {
        val t = wrapLoop(t0)
        val x = t * TABLE_HZ
        val i = min(floor(x).toInt(), tab.size - 2)
        val f = x - i
        return tab[i] + (tab[i + 1] - tab[i]) * f
    }

    fun scriptSwirl(t: Double) = lut(swirlTab, t)
    fun scriptAgit(t: Double) = lut(agitTab, t)

    fun scriptPulseAge(t0: Double): Double {
        val t = wrapLoop(t0)
        var age = 99.0
        for (c in cross) {
            val a = if (t >= c) t - c else t + LOOP - c
            if (a < age) age = a
        }
        return age
    }

    // ---- the frame: what the shaders get ----
    private val rs = DoubleArray(2)
    private val mem = DoubleArray(8)
    private val c0 = DoubleArray(3)
    private val c1 = DoubleArray(3)
    private val c2 = DoubleArray(3)

    /** Writes the disc shader's uniforms for a frame (single-threaded: the UI thread). */
    fun discUniforms(f: SparkSphereScoreFrame, pal: SparkSphereScorePalette, u: SparkSphereScoreDiscUniforms) {
        val reduce = f.reduceMotion
        var heat = heatOf(f.vRim)
        var cold = coldOf(f.vRim)
        var fill = fillOf(f.vHol)
        if (reduce) {
            // density stays in three states (crystal, shell, spread) cross-faded at the band boundaries
            rs[0] = 1 - smoothstep(-0.08, 0.12, f.vHol)
            rs[1] = 0.45 * smoothstep(0.88, 1.12, f.vHol) + 0.4 * smoothstep(1.88, 2.12, f.vHol)
            cold = rs[0]
            fill = rs[1]
            heat = 0.0
        }
        val cyc = if (reduce) 0.0 else f.cyc
        colourAt(f.vRim, c0, pal)
        colourAt(f.vHol, c1, pal)
        colourAt(f.vFilm, c2, pal)
        val pulseAge = if (reduce) 99.0 else f.pulseAge
        val uu = pulseAge / PULSE_DURATION
        val bandQ = 1.05 - 1.05 * clamp(uu, 0.0, 1.0)
        val bandA = if (uu < 0 || uu > 1) 0.0 else PULSE_BAND_AMP * sin(PI * uu)
        val ringA = if (reduce) 0.0 else pulseRing(pulseAge)
        val ringPh = (2 * PI * pulseAge) / PULSE_RING_PERIOD
        membrane(cyc, if (reduce) 0.0 else heat, cold, ringA, ringPh, mem)
        val breath = 1 + (if (reduce) 0.0 else 1.2 / 163.6) * beat(cyc, f.beats)
        val tapQ = (f.tapAge * 300) / 163.6
        val tapOn = f.tapAge < 1.2 && cold > 0.5
        val hot = heat
        fun put(a: FloatArray, vararg v: Double) { for (i in v.indices) a[i] = v[i].toFloat() }
        put(u.geo, f.cx, f.cy, f.scale, f.px)
        put(u.memA, mem[0], mem[1], mem[2], mem[3])
        put(u.memB, mem[4], mem[5], mem[6], mem[7])
        put(u.dim, f.dimple, f.dimpleAngle, 0.55, breath)
        put(u.s1, heat, cold, fill, if (reduce) 0.0 else f.swirl)
        put(
            u.s2,
            if (reduce) 0.0 else f.agit,
            if (reduce) 0.0 else 0.016 * (1 - cold),
            if (reduce) 0.0 else 0.04 * smoothstep(0.1, 0.8, hot) * (1 - cold),
            if (reduce) 0.0 else 3.0 + 1.6 * hot,
        )
        put(
            u.look,
            1 + 0.3 * hot,
            (if (reduce) 0.0 else 0.45) * (1 - hot) * (1 - 0.5 * cold),
            if (reduce) 0.0 else -2.62 + 2 * PI * cyc,
            if (reduce) 0.0 else 0.55 * hot,
        )
        put(u.clock, cyc, if (reduce) 0.0 else 2 * PI * 7 * cyc, 0.9 * cold, max(0.65 * hot, 0.88 * cold))
        put(u.coreO, c0[0], c0[1], c0[2])
        put(u.coreI, c1[0], c1[1], c1[2])
        put(u.hazeC, max(c1[0] * 0.375 - 0.063, 0.0), c1[1] * 0.375, c1[2] * 0.4)
        put(u.rimC, c2[0] * 0.78, c2[1] * 0.78, c2[2] * 0.78, 1.0)
        put(u.shape, 1.3 - 0.3 * cold, 0.6 * (1 - cold), 14.0, 0.07)
        put(u.misc, 0.8, 0.3 + 0.2 * cold, 0.86, f.p3)
        put(u.base, 0.008, 0.016, 0.02)
        put(u.probe, f.probeX, f.probeY, PROBE_RADIUS, f.probeHeat)
        put(u.band, bandQ, bandA, PULSE_BAND_WIDTH, if (tapOn) tapQ else 2.0)
        put(u.ring, if (tapOn) 0.9 * exp(-f.tapAge / 0.9) else 0.0, 0.06, 0.3, 0.56)
    }
}
