package com.ngocdevv.nativecook.blocks.media.boomboxnowplaying

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// One loop drives the whole film (vinylMotion.ts + vinylScene.ts). Every value is a pure function of the loop time t
// (seconds, 0 <= t < LOOP), so t = LOOP gives the frame of t = 0. Only a record and its sleeve, standing upright in the
// air over studio paper (glTF metres, y up, faces toward +z).
//
//   0.00 –  0.80  poster: the sleeve three-quarter from the left, the record peeking out of its open side, 0:00
//   0.80 –  2.30  the record slides out of the sleeve to the right
//   2.20 –  3.80  the sleeve drifts back and to the left; the record comes forward to the middle
//   3.00          play; 3.0 – 4.0 the record spins up and the song starts
//   4.20 –  7.20  the record turns a full 360° about its upright axis
//   7.50 –  8.90  rotary scrub: the record turns 1.5 turns forward, the song jumps to about 1:40 with it
//   9.20 – 10.40  the volume goes up to full and back
//  10.60          forward +15 s
//  11.30          pause: the record runs down by 12.5; 11.8 – 12.3 back to 0:00
//  12.50 – 14.00  the sleeve comes back; the record moves beside it
//  13.90 – 15.10  the record slides back in; 15.1 – 15.8 rest = the poster

/** The film's period in seconds (vinylMotion.ts LOOP). */
const val LOOP = 15.8

internal object BnpScene {
    val KEY_DIRECTION = floatArrayOf(0.55f, -0.6f, -0.75f)
    val RIM_DIRECTION = floatArrayOf(-0.5f, -0.4f, 0.8f)
    const val KEY_LUX = 60_000f
    const val RIM_LUX = 28_000f
    const val IBL_INTENSITY = 32_000f
    const val EXPOSURE_STOPS = 0.25f
    const val CAMERA_NEAR = 0.02
    const val CAMERA_FAR = 20.0
    val TO_KEY: DoubleArray = run {
        val l = sqrt(0.55 * 0.55 + 0.6 * 0.6 + 0.75 * 0.75)
        doubleArrayOf(-0.55 / l, 0.6 / l, 0.75 / l)
    }
    const val SHEEN_GLOW = 0.9f
    const val PAPER_TOP = 0xFFF4F2EE.toInt()
    const val PAPER_BOTTOM = 0xFFE4DFD8.toInt()
}

internal object BnpMotion {
    const val TRACK_SECONDS = 228.0
    const val IDLE = 2.0
    const val VOLUME = 0.7

    const val SLIDE_OUT_A = 0.8
    const val SLIDE_OUT_B = 2.3
    const val AWAY_Z_A = 2.2
    const val AWAY_Z_B = 3.5
    const val AWAY_X_A = 2.45
    const val AWAY_X_B = 3.8
    const val CENTRE_A = 2.35
    const val CENTRE_B = 3.8
    const val PLAY = 3.0
    const val SPIN_A = 3.0
    const val SPIN_B = 4.0
    const val TURN_A = 4.2
    const val TURN_B = 7.2
    const val SCRUB_A = 7.5
    const val SCRUB_B = 8.9
    const val VOL_UP_A = 9.2
    const val VOL_UP_B = 9.7
    const val VOL_DOWN_A = 9.9
    const val VOL_DOWN_B = 10.4
    const val FORWARD = 10.6
    const val PAUSE = 11.3
    const val STOP_A = 11.3
    const val STOP_B = 12.5
    const val BACK_A = 11.8
    const val BACK_B = 12.3
    const val HOME_Z_A = 12.6
    const val HOME_Z_B = 13.9
    const val HOME_X_A = 12.5
    const val HOME_X_B = 13.7
    const val UNCENTRE_A = 12.5
    const val UNCENTRE_B = 14.0
    const val SLIDE_IN_A = 13.9
    const val SLIDE_IN_B = 15.1

    const val CENTRED_FROM = 3.8
    const val CENTRED_TO = 12.5
    const val CLOSE_T = 12.5
    const val STILL_T = 3.9

    const val R_VINYL = 0.1505
    const val PEEK_X = 0.085
    const val OUT_X = 0.338
    val AWAY = doubleArrayOf(-0.205, 0.004, -0.37, 0.42)
    val MIDDLE = doubleArrayOf(0.0, 0.0, 0.05)
    const val FLOOR_Y = -0.205

    const val SEEK_PER_RAD = 10.0
    const val SCRUB_TURNS = 1.5
    private const val SCRUB_ANGLE = SCRUB_TURNS * 2 * PI
    const val SKIP_S = 15.0
    private const val SKIP_TIME = 0.4
    private const val TURNS = 6
    private const val SPIN_TIME = (SPIN_B - SPIN_A) / 2 + (STOP_A - SPIN_B) + (STOP_B - STOP_A) / 2
    /** Record speed while playing (rad/s): 32.5 rpm. */
    const val OMEGA = (TURNS * 2 * PI - SCRUB_ANGLE) / SPIN_TIME

    fun clamp01(x: Double): Double = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    fun seg(t: Double, a: Double, b: Double): Double = clamp01((t - a) / (b - a))
    fun lerp(a: Double, b: Double, k: Double): Double = a + (b - a) * k
    fun smooth(x: Double): Double {
        val u = clamp01(x)
        return u * u * (3 - 2 * u)
    }

    private fun smoothInt(u: Double): Double {
        val v = clamp01(u)
        return v * v * v - v * v * v * v / 2
    }

    fun easeInOutSine(x: Double): Double = -(cos(PI * clamp01(x)) - 1) / 2
    fun easeInOutCubic(x: Double): Double {
        val u = clamp01(x)
        return if (u < 0.5) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2
    }

    fun fract(x: Double): Double = x - floor(x)

    /** A CSS cubic-bezier(x1, y1, x2, y2) at x. */
    fun bezier(x1: Double, y1: Double, x2: Double, y2: Double, x: Double): Double {
        fun bx(u: Double) = 3 * (1 - u) * (1 - u) * u * x1 + 3 * (1 - u) * u * u * x2 + u * u * u
        fun by(u: Double) = 3 * (1 - u) * (1 - u) * u * y1 + 3 * (1 - u) * u * u * y2 + u * u * u
        val xx = clamp01(x)
        var lo = 0.0
        var hi = 1.0
        repeat(30) {
            val m = (lo + hi) / 2
            if (bx(m) < xx) lo = m else hi = m
        }
        return by((lo + hi) / 2)
    }

    fun ease(x: Double) = bezier(0.65, 0.0, 0.35, 1.0, x)
    fun inOutQuad(x: Double): Double = if (x < 0.5) 2 * x * x else 1 - (-2 * x + 2).pow(2) / 2
    fun outQuad(x: Double): Double = 1 - (1 - x) * (1 - x)

    fun speedAt(t: Double): Double = smooth(seg(t, SPIN_A, SPIN_B)) * (1 - smooth(seg(t, STOP_A, STOP_B)))

    private fun motorAt(t: Double): Double {
        val up = (SPIN_B - SPIN_A) * smoothInt(seg(t, SPIN_A, SPIN_B))
        val hold = max(0.0, min(t, STOP_A) - SPIN_B)
        val d = STOP_B - STOP_A
        val u = seg(t, STOP_A, STOP_B)
        return up + hold + d * (u - smoothInt(u))
    }

    private fun scrubAt(t: Double): Double = easeInOutCubic(seg(t, SCRUB_A, SCRUB_B))

    fun platterAt(t: Double): Double = OMEGA * motorAt(t) + SCRUB_ANGLE * scrubAt(t)

    fun songAt(t: Double): Double {
        val played = motorAt(t)
        val scrub = SCRUB_ANGLE * SEEK_PER_RAD * scrubAt(t)
        val skip = SKIP_S * easeInOutSine(seg(t, FORWARD, FORWARD + SKIP_TIME))
        return (played + scrub + skip) * (1 - easeInOutSine(seg(t, BACK_A, BACK_B)))
    }

    fun playingAt(t: Double): Boolean = t >= PLAY && t < PAUSE

    fun volumeAt(t: Double): Double {
        val up = easeInOutSine(seg(t, VOL_UP_A, VOL_UP_B))
        val down = easeInOutSine(seg(t, VOL_DOWN_A, VOL_DOWN_B))
        return VOLUME + (1 - VOLUME) * up * (1 - down)
    }

    fun turnAt(t: Double): Double = 2 * PI * easeInOutCubic(seg(t, TURN_A, TURN_B))

    /** out = [record x, y, z, yaw, sleeve x, y, z, yaw]. */
    fun poseAt(t: Double, out: DoubleArray) {
        val awayZ = easeInOutSine(seg(t, AWAY_Z_A, AWAY_Z_B)) * (1 - easeInOutSine(seg(t, HOME_Z_A, HOME_Z_B)))
        val awayX = easeInOutSine(seg(t, AWAY_X_A, AWAY_X_B)) * (1 - easeInOutSine(seg(t, HOME_X_A, HOME_X_B)))
        val sx = AWAY[0] * awayX
        val sy = AWAY[1] * awayX
        val sz = AWAY[2] * awayZ
        val syaw = AWAY[3] * awayX
        val rel = lerp(PEEK_X, OUT_X, easeInOutCubic(seg(t, SLIDE_OUT_A, SLIDE_OUT_B)) * (1 - easeInOutCubic(seg(t, SLIDE_IN_A, SLIDE_IN_B))))
        val ix = sx + cos(syaw) * rel
        val iz = sz - sin(syaw) * rel
        val c = easeInOutCubic(seg(t, CENTRE_A, CENTRE_B)) * (1 - easeInOutCubic(seg(t, UNCENTRE_A, UNCENTRE_B)))
        val bob = 0.005 * sin(4 * PI * t / LOOP) * c
        out[0] = lerp(ix, MIDDLE[0], c)
        out[1] = lerp(sy, MIDDLE[1], c) + bob
        out[2] = lerp(iz, MIDDLE[2], c) + 0.03 * sin(PI * c)
        out[3] = lerp(syaw, 0.0, c) + turnAt(t)
        out[4] = sx
        out[5] = sy
        out[6] = sz
        out[7] = syaw
    }

    /** Keyframes: t, azimuth (deg), elevation (deg), distance, target x, y, z. */
    private val KEYS = arrayOf(
        doubleArrayOf(0.0, -17.0, 7.0, 0.93, 0.06, -0.012, 0.0),
        doubleArrayOf(0.8, -17.0, 7.0, 0.93, 0.06, -0.012, 0.0),
        doubleArrayOf(2.3, -9.0, 6.0, 1.14, 0.165, -0.01, 0.0),
        doubleArrayOf(3.8, -4.0, 5.0, 0.82, -0.03, 0.0, 0.02),
        doubleArrayOf(5.8, 6.0, 4.0, 0.75, -0.02, 0.0, 0.03),
        doubleArrayOf(7.5, 11.0, 9.0, 0.68, 0.0, 0.0, 0.05),
        doubleArrayOf(8.9, 9.0, 8.0, 0.7, 0.0, 0.0, 0.05),
        doubleArrayOf(10.6, 1.0, 6.0, 0.78, -0.02, 0.0, 0.03),
        doubleArrayOf(12.5, -5.0, 6.0, 0.86, 0.0, 0.0, 0.01),
        doubleArrayOf(13.9, -11.0, 6.0, 1.14, 0.165, -0.01, 0.0),
        doubleArrayOf(15.1, -17.0, 7.0, 0.93, 0.06, -0.012, 0.0),
        doubleArrayOf(15.8, -17.0, 7.0, 0.93, 0.06, -0.012, 0.0),
    )

    private fun keyAt(t: Double, c: Int): Double {
        var i = 0
        while (i < KEYS.size - 2 && t >= KEYS[i + 1][0]) i++
        val k0 = KEYS[max(0, i - 1)]
        val k1 = KEYS[i]
        val k2 = KEYS[i + 1]
        val k3 = KEYS[min(KEYS.size - 1, i + 2)]
        val x = easeInOutSine((t - k1[0]) / (k2[0] - k1[0]))
        val p0 = k0[c]
        val p1 = k1[c]
        val p2 = k2[c]
        val p3 = k3[c]
        var m1 = (p2 - p0) / 2
        var m2 = (p3 - p1) / 2
        if ((p1 - p0) * (p2 - p1) <= 0) m1 = 0.0
        if ((p2 - p1) * (p3 - p2) <= 0) m2 = 0.0
        val x2 = x * x
        val x3 = x2 * x
        return (2 * x3 - 3 * x2 + 1) * p1 + (x3 - 2 * x2 + x) * m1 + (-2 * x3 + 3 * x2) * p2 + (x3 - x2) * m2
    }

    fun cameraAt(t: Double, out: DoubleArray) {
        val az = keyAt(t, 1) * PI / 180
        val el = keyAt(t, 2) * PI / 180
        val dist = keyAt(t, 3)
        val tx = keyAt(t, 4)
        val ty = keyAt(t, 5)
        val tz = keyAt(t, 6)
        val ce = cos(el)
        out[0] = tx + sin(az) * ce * dist
        out[1] = ty + sin(el) * dist
        out[2] = tz + cos(az) * ce * dist
        out[3] = tx
        out[4] = ty
        out[5] = tz
    }

    fun nearestTurn(a: Double): Double = (a / (2 * PI)).roundToLong() * 2 * PI

    /** out = [record x, y, z, yaw, sleeve x, y, z, yaw, eye x, y, z, target x, y, z]. */
    fun sceneAt(s: Double, r: Double, out: DoubleArray) {
        val p = DoubleArray(8)
        val c = DoubleArray(6)
        poseAt(s, p)
        cameraAt(s, c)
        if (r > 0) {
            val q = DoubleArray(8)
            val d = DoubleArray(6)
            poseAt(CLOSE_T, q)
            cameraAt(CLOSE_T, d)
            q[3] = nearestTurn(p[3])
            for (i in 0 until 8) p[i] = lerp(p[i], q[i], r)
            for (i in 0 until 6) c[i] = lerp(c[i], d[i], r)
        }
        for (i in 0 until 8) out[i] = p[i]
        for (i in 0 until 6) out[8 + i] = c[i]
    }

    const val HFOV_PHONE = 34.0

    fun focalFor(w: Double, h: Double): Double {
        val t = tan(HFOV_PHONE * PI / 360)
        return min(w / 2 / t, h * 0.73 / 2 / t)
    }

    fun basis(c: DoubleArray, out: DoubleArray) {
        var fx = c[3] - c[0]
        var fy = c[4] - c[1]
        var fz = c[5] - c[2]
        val fl = sqrt(fx * fx + fy * fy + fz * fz).let { if (it == 0.0) 1.0 else it }
        fx /= fl; fy /= fl; fz /= fl
        var rx = -fz
        var rz = fx
        val rl = hypot(rx, rz).let { if (it == 0.0) 1.0 else it }
        rx /= rl; rz /= rl
        out[0] = fx; out[1] = fy; out[2] = fz
        out[3] = rx; out[4] = rz
        out[5] = -rz * fy
        out[6] = rz * fx - rx * fz
        out[7] = rx * fy
    }

    fun project(c: DoubleArray, b: DoubleArray, w: Double, h: Double, x: Double, y: Double, z: Double, out: DoubleArray): Boolean {
        val px = x - c[0]
        val py = y - c[1]
        val pz = z - c[2]
        val d = px * b[0] + py * b[1] + pz * b[2]
        if (d < 0.02) return false
        val f = focalFor(w, h)
        out[0] = w / 2 + (px * b[3] + pz * b[4]) / d * f
        out[1] = h / 2 - (px * b[5] + py * b[6] + pz * b[7]) / d * f
        out[2] = f / d
        return true
    }

    /** The groove highlight's angle in the record's own plane (the half vector between the eye and the key). */
    fun sheenAngle(scene: DoubleArray): Double {
        var vx = scene[8] - scene[0]
        var vy = scene[9] - scene[1]
        var vz = scene[10] - scene[2]
        val vl = sqrt(vx * vx + vy * vy + vz * vz).let { if (it == 0.0) 1.0 else it }
        vx = vx / vl + BnpScene.TO_KEY[0]
        vy = vy / vl + BnpScene.TO_KEY[1]
        vz = vz / vl + BnpScene.TO_KEY[2]
        val a = scene[3]
        val lx = cos(a) * vx - sin(a) * vz
        return atan2(vy, lx)
    }

    private val SHADOW_HALF_W = doubleArrayOf(0.135, 0.165)
    private val SHADOW_HALF_D = doubleArrayOf(0.05, 0.055)
    private val SHADOW_ALPHA = doubleArrayOf(0.3, 0.24)

    /** Shadow k on the floor: out = [centre x, y, radius along the width, radius along the depth, angle, alpha]. */
    fun shadowAt(scene: DoubleArray, k: Int, w: Double, h: Double, out: DoubleArray): Boolean {
        val c = doubleArrayOf(scene[8], scene[9], scene[10], scene[11], scene[12], scene[13])
        val b = DoubleArray(8)
        basis(c, b)
        val x = scene[k * 4]
        val z = scene[k * 4 + 2]
        val yaw = scene[k * 4 + 3]
        val hw = SHADOW_HALF_W[k]
        val hd = SHADOW_HALF_D[k]
        val p0 = DoubleArray(3)
        val p1 = DoubleArray(3)
        val p2 = DoubleArray(3)
        if (!project(c, b, w, h, x, FLOOR_Y, z, p0)) return false
        project(c, b, w, h, x + cos(yaw) * hw, FLOOR_Y, z - sin(yaw) * hw, p1)
        project(c, b, w, h, x + sin(yaw) * hd, FLOOR_Y, z + cos(yaw) * hd, p2)
        val ax = p1[0] - p0[0]
        val ay = p1[1] - p0[1]
        out[0] = p0[0]
        out[1] = p0[1]
        out[2] = max(1.0, hypot(ax, ay))
        out[3] = max(1.0, hypot(p2[0] - p0[0], p2[1] - p0[1]))
        out[4] = atan2(ay, ax)
        val lift = scene[k * 4 + 1] - FLOOR_Y
        out[5] = SHADOW_ALPHA[k] * min(1.2, 0.205 / max(0.12, lift))
        return true
    }

    fun angleDelta(a: Double, b: Double): Double {
        var d = a - b
        while (d > PI) d -= 2 * PI
        while (d < -PI) d += 2 * PI
        return d
    }

    fun barAt(k: Int, song: Double, level: Double): Double {
        val f = fract(song * 2)
        val kick = exp(-f * 5)
        val w = 0.5 + 0.5 * sin(song * (5.3 + k * 2.1) + k * 1.7)
        return level * (0.25 + 0.45 * w + 0.3 * kick * (if (k == 1) 1.0 else 0.6))
    }

    fun clock(s: Double): String {
        val v = max(0, floor(s).toInt())
        val m = v / 60
        val r = v % 60
        return "$m:${if (r < 10) "0" else ""}$r"
    }
}

/** What one frame shows. */
internal class BnpFrame(
    val scene: DoubleArray,
    val platter: Double,
    val song: Double,
    val level: Double,
    val volume: Double,
    val playing: Boolean,
    val auto: Boolean,
)

/** A value easing from one number to another over `dur` seconds from `t0` (Reanimated's withTiming). */
internal class BnpTween(val from: Double, val to: Double, val t0: Double, val dur: Double, val curve: (Double) -> Double) {
    fun at(now: Double): Double = if (dur <= 0) to else from + (to - from) * curve(BnpMotion.clamp01((now - t0) / dur))
    fun done(now: Double): Boolean = now >= t0 + dur

    companion object {
        fun still(v: Double) = BnpTween(v, v, 0.0, 0.0) { it }
    }
}

/**
 * The block's clock and the listener's state (VinylNowPlaying.tsx's frame callback and touch handlers). `frame(now)`
 * advances it once per vsync time (seconds); the Filament stage and the Compose layers read the same frame.
 */
internal class BnpController(autoPlay: Boolean, reduced: Boolean) {
    val film = autoPlay && !reduced
    private val startT = if (film) 0.0 else BnpMotion.STILL_T

    private enum class Mode { AUTO, MANUAL, RETURN }

    private var mode = if (film) Mode.AUTO else Mode.MANUAL
    var ready = false
    private var loopStart = -1.0
    private var resumeAt = 0.0
    private var lastActivity = -1.0
    private var lastNow = Double.NaN
    private var t = startT
    private var stage = startT
    private var ret = 0.0
    private var platter = BnpMotion.platterAt(startT)
    private var platterBase = 0.0
    private var song = if (film) 0.0 else BnpMotion.songAt(BnpMotion.STILL_T)
    private var speed = 0.0
    private var volume = BnpMotion.VOLUME
    var playing = false
        private set
    private var speedA: BnpTween? = null
    private var songA: BnpTween? = null
    private var volA: BnpTween? = null
    private var stageA: BnpTween? = null
    private var retA: BnpTween? = null
    private var returnToClose = false
    var dragging = false
    var scrubbing = false
    private var seeking = false
    private var cache: BnpFrame? = null
    private var cacheTime = Double.NaN
    private var now = 0.0

    fun frame(at: Double): BnpFrame {
        if (at == cacheTime) cache?.let { return it }
        step(at)
        val sc = DoubleArray(14)
        BnpMotion.sceneAt(stage, ret, sc)
        val f = BnpFrame(sc, platter, song, speed, volume, playing, mode == Mode.AUTO)
        cache = f
        cacheTime = at
        return f
    }

    private fun step(time: Double) {
        // a touch may have brought the state up to a moment after this vsync's time: never run backwards
        val at = if (lastNow.isNaN()) time else max(time, lastNow)
        val dt = if (lastNow.isNaN()) 0.0 else (at - lastNow).coerceIn(0.0, 0.05)
        lastNow = at
        now = at
        if (mode == Mode.AUTO) {
            if (!film) {
                mode = Mode.MANUAL
                return
            }
            if (!ready) {
                t = 0.0
            } else {
                if (loopStart < 0) {
                    loopStart = at - resumeAt
                    resumeAt = 0.0
                }
                t = ((at - loopStart) % LOOP + LOOP) % LOOP
            }
            stage = t
            ret = 0.0
            song = BnpMotion.songAt(t)
            platter = platterBase + BnpMotion.platterAt(t)
            speed = BnpMotion.speedAt(t)
            volume = BnpMotion.volumeAt(t)
            playing = BnpMotion.playingAt(t)
            return
        }
        speedA?.let { speed = it.at(at); if (it.done(at)) speedA = null }
        volA?.let { volume = it.at(at); if (it.done(at)) volA = null }
        songA?.let { song = it.at(at); if (it.done(at)) { songA = null; seeking = false } }
        stageA?.let { stage = it.at(at) }
        retA?.let { ret = it.at(at) }
        if (!dragging) platter += BnpMotion.OMEGA * speed * dt
        if (!dragging && !seeking && !scrubbing && songA == null && speed > 0) song = min(BnpMotion.TRACK_SECONDS, song + speed * dt)
        if (mode == Mode.RETURN) {
            if (returnToClose) {
                val r = retA
                if (r != null && r.done(at)) {
                    retA = null
                    stage = BnpMotion.CLOSE_T
                    ret = 0.0
                    platterBase = platter - BnpMotion.platterAt(BnpMotion.CLOSE_T)
                    resumeAt = BnpMotion.CLOSE_T
                    loopStart = -1.0
                    mode = Mode.AUTO
                }
            } else {
                val s = stageA
                if (s != null && s.done(at)) {
                    stageA = null
                    stage = 0.0
                    platterBase = platter
                    resumeAt = 0.0
                    loopStart = -1.0
                    mode = Mode.AUTO
                }
            }
            return
        }
        stageA?.let { if (it.done(at)) stageA = null }
        val busy = dragging || scrubbing || seeking
        if (lastActivity < 0 || busy) {
            lastActivity = at
            return
        }
        if (!film || at - lastActivity < BnpMotion.IDLE) return
        // 2 s after the last touch the record stops and the scene finds its way back into the film: from the middle it
        // eases to the close (stopped, 0:00) and the film carries on putting it away; nearer the sleeve it runs the
        // film's own slide back to the poster
        mode = Mode.RETURN
        playing = false
        speedA = BnpTween(speed, 0.0, at, SPIN_DOWN, BnpMotion::outQuad)
        songA = BnpTween(song, 0.0, at, RETURN, BnpMotion::ease)
        volA = BnpTween(volume, BnpMotion.VOLUME, at, RETURN, BnpMotion::inOutQuad)
        val s0 = stage
        if (s0 >= BnpMotion.CENTRED_FROM && s0 <= BnpMotion.CENTRED_TO) {
            returnToClose = true
            retA = BnpTween(0.0, 1.0, at, RETURN, BnpMotion::ease)
        } else {
            returnToClose = false
            val target = if (s0 > BnpMotion.CENTRED_TO) LOOP else 0.0
            stageA = BnpTween(s0, target, at, max(SPIN_DOWN, kotlin.math.abs(target - s0) * 0.8), BnpMotion::ease)
        }
    }

    /** Any touch takes the record over where the film is; the film's state carries on as the listener's own. */
    private fun takeOver() {
        val at = now
        when (mode) {
            Mode.RETURN -> {
                // cancelAnimation: everything stays where the return had it
                retA = null; stageA = null; speedA = null; songA = null; volA = null; seeking = false
            }
            Mode.AUTO -> {
                if (BnpMotion.playingAt(t)) {
                    speedA = BnpTween(speed, 1.0, at, 1 - BnpMotion.speedAt(t) + 0.001, BnpMotion::ease)
                } else if (speed > 0) {
                    speedA = BnpTween(speed, 0.0, at, SPIN_DOWN, BnpMotion::outQuad)
                }
            }
            Mode.MANUAL -> Unit
        }
        mode = Mode.MANUAL
        lastActivity = -1.0
    }

    private fun bringOut() {
        val s0 = stage
        if (s0 < BnpMotion.CENTRED_FROM) {
            stageA = BnpTween(s0, BnpMotion.STILL_T, now, max(0.5, (BnpMotion.STILL_T - s0) * 0.7), BnpMotion::ease)
        } else if (s0 > BnpMotion.CENTRED_TO) {
            stageA = BnpTween(s0, BnpMotion.CENTRED_TO - 0.1, now, max(0.5, (s0 - BnpMotion.CENTRED_TO) * 0.7), BnpMotion::ease)
        }
    }

    fun toggle(at: Double) {
        sync(at)
        takeOver()
        if (playing) {
            playing = false
            speedA = BnpTween(speed, 0.0, now, SPIN_DOWN, BnpMotion::outQuad)
        } else {
            playing = true
            bringOut()
            speedA = BnpTween(speed, 1.0, now, 1.0, BnpMotion::inOutQuad)
        }
    }

    private fun seek(at: Double, to: Double) {
        sync(at)
        takeOver()
        seeking = true
        songA = BnpTween(song, to, now, 0.5, BnpMotion::ease)
    }

    fun back(at: Double) = seek(at, 0.0)
    fun forward(at: Double) {
        sync(at)
        seek(at, min(BnpMotion.TRACK_SECONDS - 1, song + BnpMotion.SKIP_S))
    }

    fun scrub(at: Double, f: Double) {
        sync(at)
        takeOver()
        songA = null
        seeking = false
        song = f * BnpMotion.TRACK_SECONDS
        cacheTime = Double.NaN
    }

    fun scrubState(active: Boolean) {
        scrubbing = active
        if (!active) lastActivity = -1.0
    }

    fun setVolume(at: Double, v: Double) {
        sync(at)
        takeOver()
        volA = null
        volume = v
        lastActivity = -1.0
        cacheTime = Double.NaN
    }

    fun dragBegan(at: Double) {
        sync(at)
        takeOver()
        dragging = true
        songA = null
        seeking = false
    }

    fun turn(d: Double) {
        platter += d
        song = (song + d * BnpMotion.SEEK_PER_RAD).coerceIn(0.0, BnpMotion.TRACK_SECONDS)
        cacheTime = Double.NaN
    }

    fun dragEnded() {
        dragging = false
        lastActivity = -1.0
    }

    /** The scene as the stage shows it now (for the rotary scrub's centre). */
    fun sceneNow(): DoubleArray = cache?.scene ?: DoubleArray(14).also { BnpMotion.sceneAt(stage, ret, it) }

    /** Brings the state up to a touch's time before changing it. */
    private fun sync(at: Double) {
        if (at > now) frame(at)
        cacheTime = Double.NaN
    }

    private companion object {
        const val SPIN_DOWN = 1.2
        const val RETURN = 1.4
    }
}
