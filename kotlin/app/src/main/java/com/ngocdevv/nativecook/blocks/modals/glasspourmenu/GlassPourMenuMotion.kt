package com.ngocdevv.nativecook.blocks.modals.glasspourmenu

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A `{ damping, stiffness, mass }` spring. */
internal class GlassPourMenuSpring(val damping: Double, val stiffness: Double, val mass: Double)

/** The shape of the glass at one instant (RN `Glass`): a bead, a panel, the neck between them and how lit it is. */
internal class GlassPourMenuGlass(
    val beadX: Double,
    val beadY: Double,
    val beadR: Double,
    val boxX: Double,
    val boxY: Double,
    val boxW: Double,
    val boxH: Double,
    val boxR: Double,
    val k: Double,
    val thick: Double,
    val hot: Double,
    val frost: Double,
    val open: Double,
    val glyphK: Double,
    val rows: DoubleArray,
    val panelBottom: Double,
    val rim: Double,
    val neckA: Double,
    val neckB: Double,
    val neckR: Double,
)

internal class GlassPourMenuLife(val sag: Double = 0.0, val breath: Double = 0.0, val bob: Double = 0.0, val recoil: Double = 0.0, val dimple: Double = 0.0, val dip: Double = 0.0)

internal class GlassPourMenuFrame(val p: Double, val press: Double, val lensY: Double, val lensA: Double, val flash: Double)

/**
 * The motion of the RN block (`gpm-motion.ts`). One progress value p (0 closed .. 1 open, rubber-banded past both ends) drives the glass's
 * whole shape and light through fixed curves ([glassAt]), never time: the finger scrubs exactly what the loop plays. The loop
 * ([timeline], LOOP = 7.2 s) is a pure function of t: the press, a slow pull down to the neck, a hold there (the gasp), the release into
 * an analytic spring, the lens bar touring the rows, a pick, the pour back.
 */
internal object GlassPourMenuMotion {
    const val LOOP = 7.2

    const val T_PRESS = 0.5
    const val T_PULL = 0.55
    const val T_PULL_DUR = 0.9
    const val T_RELEASE = 2.45
    const val T_PINCH = 2.510094355743
    const val T_MERGE = 5.539456060903
    const val T_LENS_IN = 3.15
    const val T_LENS_IN_DUR = 0.22
    val T_LENS_STEPS = doubleArrayOf(3.5, 3.85, 4.2, 4.6)
    val T_LENS_ROWS = intArrayOf(1, 2, 3, 1)
    const val T_PICK = 5.1
    const val T_PICK_DUR = 0.35
    const val T_CLOSE = 5.45

    const val P_HOLD = 0.62
    const val P_PINCH = 0.7
    const val P_FROSTED = 0.95

    val SNAP = GlassPourMenuSpring(30.5, 322.3, 1.0)
    val GLIDE = GlassPourMenuSpring(22.1, 157.9, 1.0)
    val OPEN = GlassPourMenuSpring(18.0, 170.0, 1.0)
    val CLOSE = GlassPourMenuSpring(18.3, 130.5, 1.0)
    val RECOIL = GlassPourMenuSpring(12.0, 300.0, 0.6)
    val DIMPLE = GlassPourMenuSpring(14.0, 260.0, 0.8)
    val TWIST = GlassPourMenuSpring(12.0, 120.0, 1.0)
    val SATELLITE = GlassPourMenuSpring(16.0, 240.0, 0.5)
    val LENS = GlassPourMenuSpring(26.0, 420.0, 0.6)

    class Shape(val response: Double, val zeta: Double)

    fun springShape(s: GlassPourMenuSpring): Shape {
        val w = sqrt(s.stiffness / s.mass)
        return Shape((2 * PI) / w, s.damping / (2 * sqrt(s.stiffness * s.mass)))
    }

    private val OPEN_SHAPE = springShape(OPEN)
    private val CLOSE_SHAPE = springShape(CLOSE)
    private val LENS_SHAPE = springShape(LENS)
    private val SNAP_SHAPE = springShape(SNAP)

    const val PT_PER_P = 175.0
    const val PROJECTION = 0.12
    const val TAP_SLOP = 8.0
    const val RESUME_AFTER = 2.0

    fun clamp01(x: Double): Double = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    fun seg(t: Double, a: Double, b: Double): Double = clamp01((t - a) / (b - a))
    fun smooth(a: Double, b: Double, x: Double): Double {
        val t = clamp01((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }
    fun easeInOut(x: Double): Double {
        val t = clamp01(x)
        return t * t * t * (t * (t * 6 - 15) + 10)
    }
    fun easeOut(x: Double): Double {
        val t = clamp01(x)
        return 1 - (1 - t) * (1 - t) * (1 - t)
    }
    fun mix(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    /** 0 -> 1 step response of a spring, analytic so the loop never accumulates state. */
    fun springAt(t: Double, response: Double, zeta: Double): Double {
        if (t <= 0) return 0.0
        val w = (2 * PI) / response
        if (zeta >= 1) return 1 - exp(-w * t) * (1 + w * t)
        val wd = w * sqrt(1 - zeta * zeta)
        return 1 - exp(-zeta * w * t) * (cos(wd * t) + ((zeta * w) / wd) * sin(wd * t))
    }

    /** A third SDF bridges two smooth bodies. No per-part optical centre is used. */
    fun glassAt(pIn: Double, press: Double, g: GlassPourMenuTheme.Geometry = GlassPourMenuTheme.DEFAULT, life: GlassPourMenuLife = GlassPourMenuLife()): GlassPourMenuGlass {
        val pc = clamp01(pIn)
        val r = g.buttonR
        val scale = r / 39
        val lift = easeInOut(seg(pc, 0.0, 0.62))
        val sag = life.sag * (1 - smooth(0.05, 0.35, pc))
        val beadR = mix(r, g.beadR, lift) * (1 - 0.06 * press * (1 - lift))
        val earlyBead = -6 * scale - beadR
        val beadBaseY = if (pc < 0.35) earlyBead * easeInOut(seg(pc, 0.0, 0.35)) else mix(earlyBead, g.beadY, easeOut(seg(pc, 0.35, 0.62)))
        val beadY = beadBaseY + sag + life.bob + life.recoil
        // 0-.35: a sagging round volume, r39 -> 52 at y46. Then sides level before corners.
        val gather = easeInOut(seg(pc, 0.0, 0.35))
        val level = easeInOut(seg(pc, 0.35, 1.0))
        val sagR = mix(r, 52 * scale, gather)
        val sagY = 46 * scale * gather + sag
        val bottom = mix(sagY + sagR, g.panelBottom, level) + max(pIn - 1, 0.0) * 175 * 0.35 + life.dip
        val gap = scale * (28 * easeOut(seg(pc, 0.35, 0.62)) + 6 * smooth(0.62, 0.7, pc))
        val linkedTop = beadY + beadR + gap
        val top = mix(sagY - sagR, linkedTop, smooth(0.18, 0.35, pc)) * (1 - smooth(0.7, 1.0, pc)) +
            g.panelTop * smooth(0.7, 1.0, pc) + life.dimple
        val boxY = (top + bottom) / 2
        val boxH = max(1.0, (bottom - top) / 2)
        val boxW = mix(sagR, g.panelHalf, easeOut(seg(pc, 0.35, 0.94)))
        val boxR = mix(min(boxW, boxH), g.panelR, smooth(0.7, 1.0, pc))
        val neckR = (mix(22 * scale, 4.5, easeOut(seg(pc, 0.35, 0.62))) + life.breath) * smooth(0.18, 0.35, pc) * (1 - smooth(0.68, 0.7, pc))
        val area = 4 * boxW * boxH - (4 - PI) * boxR * boxR + PI * beadR * beadR * lift
        val thick = min((PI * r * r) / max(area, 1.0), 1.0)
        val hot = 0.55 * smooth(0.04, 0.3, pc) * (1 - smooth(0.7, 1.0, pc))
        val frost = smooth(0.84, P_FROSTED, pc)
        val n = g.rows.size
        val rows = DoubleArray(n) { i ->
            if (frost >= 1) {
                smooth(P_FROSTED + (0.028 * i) / max(1, n - 1), 0.985 + (0.015 * i) / max(1, n - 1), pc)
            } else 0.0
        }
        return GlassPourMenuGlass(
            beadX = 0.0, beadY = beadY, beadR = beadR, boxX = 0.0, boxY = boxY, boxW = boxW, boxH = boxH, boxR = boxR,
            k = 16 * smooth(0.0, 0.12, pc), thick = thick, hot = hot, frost = frost, open = smooth(0.15, 0.95, pc),
            glyphK = smooth(0.3, 0.9, pc), rows = rows, panelBottom = bottom, rim = mix(r, 16.0, smooth(0.6, 1.0, pc)),
            neckA = beadY + beadR - 9, neckB = top + 9, neckR = neckR,
        )
    }

    /** One-shot displacement starts at zero, preserving continuity at either threshold crossing. */
    fun impulseAt(t: Double, s: GlassPourMenuSpring): Double {
        if (t <= 0) return 0.0
        val decay = s.damping / (2 * s.mass)
        val wd = sqrt(s.stiffness / s.mass - decay * decay)
        val peakT = atan(wd / decay) / wd
        val peak = exp(-decay * peakT) * sin(wd * peakT)
        return (exp(-decay * t) * sin(wd * t)) / peak
    }

    /** Live hold details fade on/off without changing the finger's progress clock. */
    fun lifeAt(t: Double, p: Double): GlassPourMenuLife {
        val held = smooth(0.55, 0.6, p) * (1 - smooth(0.64, 0.7, p))
        return GlassPourMenuLife(
            sag = 1 - cos((2 * PI * t) / 4.8),
            breath = 0.6 * sin(2 * PI * 1.5 * t) * held,
            bob = sin(2 * PI * 1.5 * t) * held,
        )
    }

    /** The loop at t (s): the pull, the gasp, the pour, the lens tour, the pick, the pour back. */
    fun timeline(tIn: Double): GlassPourMenuFrame {
        val t = ((tIn % LOOP) + LOOP) % LOOP
        val press = springAt(t - T_PRESS, SNAP_SHAPE.response, SNAP_SHAPE.zeta) * (1 - springAt(t - T_RELEASE, SNAP_SHAPE.response, SNAP_SHAPE.zeta))
        var p = P_HOLD * easeInOut(seg(t, T_PULL, T_PULL + T_PULL_DUR))
        if (t >= T_RELEASE) p = P_HOLD + (1 - P_HOLD) * springAt(t - T_RELEASE, OPEN_SHAPE.response, OPEN_SHAPE.zeta)
        if (t >= T_CLOSE) p = 1 - springAt(t - T_CLOSE, CLOSE_SHAPE.response, CLOSE_SHAPE.zeta)
        if (t >= 6.55) p = 0.0
        val rows = GlassPourMenuTheme.DEFAULT.rows
        var lensY = rows[0]
        var from = 0
        for (i in T_LENS_STEPS.indices) {
            val to = T_LENS_ROWS[i]
            lensY += (rows[to] - rows[from]) * springAt(t - T_LENS_STEPS[i], LENS_SHAPE.response, LENS_SHAPE.zeta)
            from = to
        }
        val lensA = easeOut(seg(t, T_LENS_IN, T_LENS_IN + T_LENS_IN_DUR)) * (1 - seg(t, T_CLOSE, T_CLOSE + 0.16))
        val f = seg(t, T_PICK, T_PICK + T_PICK_DUR)
        val flash = if (f <= 0 || f >= 1) 0.0 else sin(PI * sqrt(f))
        return GlassPourMenuFrame(p, press, lensY, lensA, flash)
    }
}
