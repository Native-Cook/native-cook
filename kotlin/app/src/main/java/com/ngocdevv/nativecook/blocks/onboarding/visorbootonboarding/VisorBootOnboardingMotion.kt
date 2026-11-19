package com.ngocdevv.nativecook.blocks.onboarding.visorbootonboarding

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** The loop of the film, in seconds (React Native's LOOP, Swift's `static let loop`). */
const val LOOP = 18.0

/** A point or direction in glTF space (y up, the helmet faces +z). */
internal data class VisorBootV3(val x: Double, val y: Double, val z: Double)

/** A camera: where the eye is and what it looks at. */
internal data class VisorBootShot(val eye: VisorBootV3, val target: VisorBootV3)

/** Everything one frame needs: the film's time, the float page, the blend and the finger's orbit. */
internal data class VisorBootState(
    val t: Double,
    val page: Double,
    val m: Double,
    val yaw: Double,
    val pitch: Double,
    /** Running time for the periodic motion (radar, compass, voice); the film's own t while it plays. */
    val run: Double,
) {
    val lift: Double get() = VisorBootMotion.blend(VisorBootMotion.filmRise(t), VisorBootMotion.pageRise(page), m)
}

/** Camera basis for the overlay: forward, right (x, z), up, focal length in points. */
internal class VisorBootBasis(
    val fx: Double, val fy: Double, val fz: Double,
    val rx: Double, val rz: Double,
    val ux: Double, val uy: Double, val uz: Double,
    val focal: Double,
)

/**
 * The scene's fixed numbers and the film, ported from React Native's visorScene.ts and visorMotion.ts. Every film
 * value is a pure function of the loop time t (0 <= t < LOOP), so t = LOOP gives the same frame as t = 0. Under a
 * finger the film is replaced by the onboarding's pages: a float page p (0 intro .. 4 Get started); every channel has
 * a film form f(t) and a page form g(p), and the screen shows lerp(f(t), g(p), m).
 *
 *  0.0 –  1.5  poster: the helmet three-quarter on its plinth, "Meet Halo"
 *  1.5 –  5.0  step 1: the camera swings to the front; the HUD boots on the visor
 *  5.0 –  9.0  step 2: the camera orbits to the side; the panel lamps ramp and pulse; the radar sweeps the ear sensor
 *  9.0 – 13.0  step 3: a high angle; voice rings at the mouthpiece, a waveform and a transcript
 * 13.0 – 15.2  Get Started grows out of the pager's pill; the helmet rises under a light sweep
 * 15.2 – 17.0  back to the poster
 * 17.0 – 18.0  rest; last frame = first frame
 */
internal object VisorBootMotion {
    // ---- scene (visorScene.ts)
    const val PLINTH_R = 1.55
    const val HELMET_Y = 1.6787
    const val RISE = 0.14

    const val HFOV_DEG = 36.0
    const val NEAR = 0.2
    const val FAR = 60.0

    private fun v(x: Double, y: Double, z: Double) = VisorBootV3(x, y, z)
    private fun shot(e: VisorBootV3, t: VisorBootV3) = VisorBootShot(e, t)

    val poster = shot(v(-1.95, 2.58, 5.0), v(0.0, 0.44, 0.05))
    val front = shot(v(0.0, 2.03, 5.37), v(0.0, 0.7, 0.71))
    val frontDrift = shot(v(0.42, 2.06, 5.36), v(0.04, 0.7, 0.71))
    val side = shot(v(-5.38, 2.05, -0.06), v(0.0, 0.48, -0.01))
    val sideDrift = shot(v(-5.35, 2.17, 0.55), v(-0.02, 0.48, 0.02))
    val top = shot(v(1.29, 5.47, 3.02), v(0.15, 0.24, 0.34))
    val topDrift = shot(v(0.98, 5.46, 3.04), v(0.11, 0.26, 0.32))
    val riseShot = shot(v(-1.28, 1.86, 5.23), v(0.01, 0.48, -0.02))
    val riseDrift = shot(v(-1.52, 2.0, 5.17), v(0.02, 0.51, -0.02))

    val pageShots = listOf(poster, frontDrift, sideDrift, topDrift, riseDrift)
    const val PAGES = 5

    val hudQuad = listOf(v(-0.9, 0.886, 0.933), v(0.9, 0.886, 0.933), v(0.9, 0.274, 1.307), v(-0.9, 0.274, 1.307))
    val hudNormal = v(0.0, 0.52, 0.85)
    const val HUD_W = 260.0
    const val HUD_H = 108.0

    val portCenter = v(-0.98, 0.0, -0.68)
    val portAxisA = v(0.0, 1.0, 0.0)
    val portAxisB = v(0.0, 0.0, -1.0)
    const val RADAR_R = 0.8

    val mouth = v(0.0, -0.836, 1.26)
    val mouthAxisA = v(1.0, 0.0, 0.0)
    val mouthAxisB = v(0.0, 0.92, -0.4)

    // light (Filament units, as in React Native)
    const val IBL_INTENSITY = 24_000f
    val keyDirection = floatArrayOf(0.55f, -0.6f, -0.55f)
    const val KEY_LUX = 52_000f
    val rimDirection = floatArrayOf(0.5f, -0.25f, 0.85f)
    const val RIM_LUX = 60_000f
    const val SWEEP_LUX = 90_000f
    const val SWEEP_FROM_DEG = -70.0
    const val SWEEP_TO_DEG = 70.0
    const val LAMP_STRENGTH = 1.5

    // colours (sRGB)
    const val INK = 0xFFEEF0F2
    const val MUTE = 0xFF99A1AB
    const val AMBER = 0xFFFFC27A
    const val BG_EDGE = 0xFF090B0E

    // ---- phases
    const val IDLE = 2.0
    const val RETURN_DURATION = 1.4
    const val TAKEOVER = 0.45

    private val copyWindows = arrayOf(
        doubleArrayOf(15.75, 1.55), doubleArrayOf(1.95, 5.15), doubleArrayOf(5.55, 9.15),
        doubleArrayOf(9.55, 13.05), doubleArrayOf(13.5, 15.45),
    )

    // ---- helpers
    fun clamp01(x: Double) = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun lerp(a: Double, b: Double, k: Double) = a + (b - a) * k
    fun easeInOutCubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun easeOutCubic(x: Double) = 1 - (1 - x).pow(3)
    fun easeInCubic(x: Double) = x * x * x
    fun easeInOutSine(x: Double) = -(cos(PI * x) - 1) / 2
    fun easeOutBack3(x: Double): Double {
        val c1 = 0.72
        val c3 = c1 + 1
        return 1 + c3 * (x - 1).pow(3) + c1 * (x - 1).pow(2)
    }
    fun bump(t: Double, a: Double, b: Double): Double {
        val k = sin(PI * seg(t, a, b))
        return k * k
    }
    fun pageWeight(p: Double, i: Int) = clamp01(1 - abs(p - i))
    fun blend(film: Double, page: Double, m: Double) = lerp(film, page, clamp01(m))

    /** Reanimated's Easing.bezier(0.65, 0, 0.35, 1). */
    fun easeInOutTouch(x: Double): Double = bezier(0.65, 0.0, 0.35, 1.0, x)
    private fun bezier(x1: Double, y1: Double, x2: Double, y2: Double, x: Double): Double {
        if (x <= 0) return 0.0
        if (x >= 1) return 1.0
        fun bx(s: Double) = 3 * (1 - s) * (1 - s) * s * x1 + 3 * (1 - s) * s * s * x2 + s * s * s
        fun by(s: Double) = 3 * (1 - s) * (1 - s) * s * y1 + 3 * (1 - s) * s * s * y2 + s * s * s
        var lo = 0.0
        var hi = 1.0
        repeat(30) {
            val mid = (lo + hi) / 2
            if (bx(mid) < x) lo = mid else hi = mid
        }
        return by((lo + hi) / 2)
    }

    // ---- copy, pager, button

    /** Copy block i in the film: (opacity, dy). Enters over 0.45 s rising 12 pt, leaves over 0.3 s lifting 6 pt. */
    fun filmCopyOpacity(i: Int, t: Double): Double {
        val w = copyWindows[i]
        val inK = easeOutCubic(seg(t, w[0], w[0] + 0.45))
        val outK = easeInCubic(seg(t, w[1], w[1] + 0.3))
        if (i == 0) return if (t < 8) 1 - outK else inK
        return inK * (1 - outK)
    }

    fun filmCopyDy(i: Int, t: Double): Double {
        val w = copyWindows[i]
        val inK = easeOutCubic(seg(t, w[0], w[0] + 0.45))
        val outK = easeInCubic(seg(t, w[1], w[1] + 0.3))
        if (i == 0) return if (t < 8) -6 * outK else 12 * (1 - inK)
        return if (outK > 0) -6 * outK else 12 * (1 - inK)
    }

    fun pageCopyOpacity(i: Int, p: Double) = clamp01(1 - abs(i - p) * 2)
    fun pageCopyDy(i: Int, p: Double) = max(-6.0, min(12.0, (i - p) * 24))

    fun filmDot(i: Int, t: Double): Double {
        val o = filmCopyOpacity(i, t)
        if (i < 3) return o
        return max(o, filmCopyOpacity(4, t))
    }

    fun pageDot(i: Int, p: Double) = if (i < 3) pageWeight(p, i) else clamp01(1 - abs(min(p, 3.0) - 3))

    /** Get started: 0 = the pager's pill, 1 = the full button. Grows 13.2 – 14.1 (3 % overshoot), folds 15.3 – 16.1. */
    fun filmCta(t: Double) = if (t < 15) easeOutBack3(seg(t, 13.2, 14.1)) else 1 - easeInOutCubic(seg(t, 15.3, 16.1))
    fun pageCta(p: Double) = clamp01(p - 3)
    fun filmCtaAnchor(t: Double) = if (t < 15) 3.0 else 0.0

    // ---- the helmet and its light
    fun filmRise(t: Double) = RISE * (easeInOutCubic(seg(t, 13.0, 14.2)) - easeInOutCubic(seg(t, 15.3, 16.9)))
    fun pageRise(p: Double) = RISE * clamp01(p - 3)

    /** Panel lamp level (rest 0.15): ramps up as the side comes round, one strong pulse, settles, dims away. */
    fun filmLamps(t: Double): Double {
        val up = easeInOutCubic(seg(t, 5.9, 6.8))
        val down = easeInOutCubic(seg(t, 8.6, 9.6))
        val pulse = 2.6 * bump(t, 6.9, 7.9)
        val cta = 0.9 * bump(t, 13.4, 15.4)
        return 0.15 + (1.35 * up + pulse) * (1 - down) + cta
    }
    fun pageLamps(p: Double) = 0.15 + 1.35 * pageWeight(p, 2) + 0.6 * clamp01(p - 3)

    /** The Get started light sweep: level 0..1 and azimuth 0..1 across the front. */
    fun filmSweepLevel(t: Double) = bump(t, 13.5, 15.1)
    fun filmSweepAzimuth(t: Double) = easeInOutSine(seg(t, 13.5, 15.1))

    // ---- 2D modules
    fun filmHud(t: Double) = easeOutCubic(seg(t, 2.45, 2.85)) * (1 - easeInCubic(seg(t, 5.0, 5.35)))
    fun filmBoot(t: Double) = seg(t, 2.5, 4.4)
    fun filmRadar(t: Double) = easeOutCubic(seg(t, 6.3, 6.9)) * (1 - easeInCubic(seg(t, 8.75, 9.15)))
    fun filmVoice(t: Double) = easeOutCubic(seg(t, 10.1, 10.6)) * (1 - easeInCubic(seg(t, 12.7, 13.05)))
    fun filmTranscript(t: Double) = seg(t, 10.9, 12.1)
    fun radarAngle(s: Double) = 2 * PI * s / 2.1
    fun heading(s: Double) = 24 + 7 * sin(2 * PI * s / 9) + 2.5 * sin(2 * PI * s / 3.6)

    fun voiceBar(i: Int, n: Int, s: Double): Double {
        val x = i.toDouble() / (n - 1)
        val env = sin(PI * x)
        val h = abs(sin(i * 12.9898) * 43758.5453) % 1.0
        val v = 0.5 +
            0.22 * sin(2 * PI * (s * 1.7 + x * 1.3)) +
            0.16 * sin(2 * PI * (s * 3.1 - x * 2.2) + h * 6) +
            0.12 * sin(2 * PI * (s * 0.6 + h))
        val syll = 0.55 + 0.45 * max(0.0, sin(2 * PI * s * 1.25))
        return clamp01(env * v * syll * (0.75 + 0.5 * h))
    }

    fun voiceWaveY(ay: Double, h: Double, limitY: Double) = min(max(ay + 64, h * 0.3), limitY - 64)

    // ---- camera
    fun blendShot(a: VisorBootShot, b: VisorBootShot, k: Double): VisorBootShot {
        val az0 = atan2(a.eye.x, a.eye.z)
        var az1 = atan2(b.eye.x, b.eye.z)
        if (az1 - az0 > PI) az1 -= 2 * PI
        if (az0 - az1 > PI) az1 += 2 * PI
        val r0 = hypot(a.eye.x, a.eye.z)
        val r1 = hypot(b.eye.x, b.eye.z)
        val az = lerp(az0, az1, k)
        val r = lerp(r0, r1, k)
        return VisorBootShot(
            v(sin(az) * r, lerp(a.eye.y, b.eye.y, k), cos(az) * r),
            v(lerp(a.target.x, b.target.x, k), lerp(a.target.y, b.target.y, k), lerp(a.target.z, b.target.z, k)),
        )
    }

    private class Key(val t: Double, val shot: VisorBootShot, val hold: Boolean)

    private val keys = listOf(
        Key(0.0, poster, true),
        Key(1.5, poster, true),
        Key(3.2, front, false),
        Key(5.0, frontDrift, false),
        Key(6.8, side, false),
        Key(8.9, sideDrift, false),
        Key(10.8, top, false),
        Key(12.9, topDrift, false),
        Key(14.4, riseShot, false),
        Key(15.2, riseDrift, false),
        Key(17.0, poster, true),
        Key(LOOP, poster, true),
    )

    /** Cylindrical coordinates (az, r, y, tx, ty, tz) of the keys, azimuth unwrapped along the run. */
    private val cyl: List<DoubleArray> = run {
        var prev = 0.0
        keys.mapIndexed { i, k ->
            val e = k.shot.eye
            val g = k.shot.target
            var az = atan2(e.x, e.z)
            if (i > 0) {
                while (az - prev > PI) az -= 2 * PI
                while (prev - az > PI) az += 2 * PI
            }
            prev = az
            doubleArrayOf(az, hypot(e.x, e.z), e.y, g.x, g.y, g.z)
        }
    }

    /** Monotone (Fritsch–Carlson) tangents per key and channel, zero at held keys. */
    private val tangents: List<DoubleArray> = keys.indices.map { i ->
        DoubleArray(6) { c ->
            if (keys[i].hold || i == 0 || i == keys.size - 1) return@DoubleArray 0.0
            val h0 = keys[i].t - keys[i - 1].t
            val h1 = keys[i + 1].t - keys[i].t
            val d0 = (cyl[i][c] - cyl[i - 1][c]) / h0
            val d1 = (cyl[i + 1][c] - cyl[i][c]) / h1
            if (d0 * d1 <= 0) return@DoubleArray 0.0
            val w1 = 2 * h1 + h0
            val w2 = h1 + 2 * h0
            (w1 + w2) / (w1 / d0 + w2 / d1)
        }
    }

    /** Camera at loop time t: cubic Hermite through the keys. */
    fun filmCamera(t: Double): VisorBootShot {
        val n = keys.size
        var i = 0
        while (i < n - 2 && t >= keys[i + 1].t) i++
        val t0 = keys[i].t
        val h = keys[i + 1].t - t0
        val u = clamp01((t - t0) / h)
        val h00 = 2 * u * u * u - 3 * u * u + 1
        val h10 = u * u * u - 2 * u * u + u
        val h01 = -2 * u * u * u + 3 * u * u
        val h11 = u * u * u - u * u
        val c = DoubleArray(6) { k ->
            h00 * cyl[i][k] + h10 * h * tangents[i][k] + h01 * cyl[i + 1][k] + h11 * h * tangents[i + 1][k]
        }
        return VisorBootShot(v(sin(c[0]) * c[1], c[2], cos(c[0]) * c[1]), v(c[3], c[4], c[5]))
    }

    fun pageCamera(p: Double): VisorBootShot {
        val q = max(0.0, min(pageShots.size - 1.0, p))
        val i = min(pageShots.size - 2, floor(q).toInt())
        return blendShot(pageShots[i], pageShots[i + 1], q - i)
    }

    /** Turn the eye about the target: yaw round the vertical, pitch changes the elevation (both degrees). */
    fun applyOrbit(c: VisorBootShot, yawDeg: Double, pitchDeg: Double): VisorBootShot {
        if (yawDeg == 0.0 && pitchDeg == 0.0) return c
        val vx = c.eye.x - c.target.x
        val vy = c.eye.y - c.target.y
        val vz = c.eye.z - c.target.z
        val r = sqrt(vx * vx + vy * vy + vz * vz)
        val flat = hypot(vx, vz)
        val az = atan2(vx, vz) + yawDeg * PI / 180
        val el = max(-0.08, min(1.35, atan2(vy, flat) + pitchDeg * PI / 180))
        return VisorBootShot(
            v(c.target.x + sin(az) * cos(el) * r, c.target.y + sin(el) * r, c.target.z + cos(az) * cos(el) * r),
            c.target,
        )
    }

    /** The camera the screen shows: film at t, pages at p, blended by m, then the finger's orbit. */
    fun cameraAt(s: VisorBootState): VisorBootShot {
        var c = filmCamera(s.t)
        val k = clamp01(s.m)
        if (k > 0) c = blendShot(c, pageCamera(s.page), easeInOutSine(k))
        return applyOrbit(c, s.yaw, s.pitch)
    }

    /** The page nearest to the film at t (where a touch picks the onboarding up). */
    fun pageOfFilm(t: Double): Double = when {
        t < 1.8 || t >= 15.6 -> 0.0
        t < 5.2 -> 1.0
        t < 9.2 -> 2.0
        t < 13.1 -> 3.0
        else -> 4.0
    }

    // ---- projection (the stage camera's own maths)
    fun basis(c: VisorBootShot, width: Double): VisorBootBasis {
        var fx = c.target.x - c.eye.x
        var fy = c.target.y - c.eye.y
        var fz = c.target.z - c.eye.z
        val fl = sqrt(fx * fx + fy * fy + fz * fz).takeIf { it > 0 } ?: 1.0
        fx /= fl; fy /= fl; fz /= fl
        var rx = -fz
        var rz = fx
        val rl = hypot(rx, rz).takeIf { it > 0 } ?: 1.0
        rx /= rl; rz /= rl
        return VisorBootBasis(
            fx, fy, fz, rx, rz,
            -rz * fy, rz * fx - rx * fz, rx * fy,
            width / 2 / tan(HFOV_DEG * PI / 360),
        )
    }

    /** Project a world point into `out` (x, y, depth). False behind the camera. */
    fun project(c: VisorBootShot, b: VisorBootBasis, w: Double, h: Double, p: VisorBootV3, out: DoubleArray): Boolean {
        val px = p.x - c.eye.x
        val py = p.y - c.eye.y
        val pz = p.z - c.eye.z
        val z = px * b.fx + py * b.fy + pz * b.fz
        out[2] = z
        if (z < 0.05) return false
        out[0] = w / 2 + (px * b.rx + pz * b.rz) / z * b.focal
        out[1] = h / 2 - (px * b.ux + py * b.uy + pz * b.uz) / z * b.focal
        return true
    }

    /** How squarely the visor faces the camera (0 edge-on or behind .. 1 head-on). */
    fun hudFacing(c: VisorBootShot, helmetY: Double): Double {
        val vx = c.eye.x
        val vy = c.eye.y - (helmetY + 0.58)
        val vz = c.eye.z - 1.1
        val l = sqrt(vx * vx + vy * vy + vz * vz).takeIf { it > 0 } ?: 1.0
        val d = (vx * hudNormal.x + vy * hudNormal.y + vz * hudNormal.z) / l
        return clamp01((d - 0.45) / 0.35)
    }

    fun helmetPoint(p: VisorBootV3, lift: Double) = VisorBootV3(p.x, p.y + HELMET_Y + lift, p.z)

    /** A spring (Reanimated's duration-based form) or exponential decay, for the controller. */
    fun springAt(from: Double, to: Double, v0: Double, t: Double, w: Double, z: Double): Double {
        val x0 = from - to
        if (z < 1) {
            val wd = w * sqrt(1 - z * z)
            return to + exp(-z * w * t) * (x0 * cos(wd * t) + (v0 + z * w * x0) / wd * sin(wd * t))
        }
        return to + exp(-w * t) * (x0 + (v0 + w * x0) * t)
    }
}
