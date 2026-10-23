package com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Every value on screen is a pure function of three effect clocks (seconds); in autoPlay they are a
 * pure function of the loop time, so t = LOOP renders exactly like t = 0.
 *
 *   0.00-0.50  rest
 *   0.50-0.77  "invite them" presses in to 0.97 and is released
 *   0.77-1.73  the button folds itself into a paper plane
 *   1.73-3.63  the dart launches on a curve with one loop, contrail, its wake rocks the cup
 *   2.97-3.97  a new sheet unfolds into the button
 *   4.10-4.37  "check contacts" presses; the black button fills
 *   4.37-4.80  the four avatars melt into one liquid blob
 *   4.72-5.80  five contact droplets fly in from the screen edges
 *   5.90-6.85  the blob splits back into the four avatars
 *   6.90-7.80  rest
 */
internal object FindFriendsInviteMotion {
    const val LOOP = 7.8
    const val RESUME_AFTER = 2.0

    val easeOut = doubleArrayOf(0.23, 1.0, 0.32, 1.0)
    val easeInOut = doubleArrayOf(0.77, 0.0, 0.175, 1.0)
    val easeFold = doubleArrayOf(0.6, 0.0, 0.3, 1.0)
    val easeHang = doubleArrayOf(0.3, 0.7, 0.7, 0.3)

    object SpringUi {
        const val omega = 15.7
        const val zeta = 0.8
    }

    object Invite {
        const val pressIn = 0.12
        const val release = 0.27
        const val pressScale = 0.97
    }

    /** Times are seconds after the release. Angles in degrees. */
    object Fold {
        const val labelOut = 0.1
        const val labelScale = 0.96
        const val radiusDur = 0.16
        const val paperRadius = 3.0
        const val skiaOn = 0.1
        const val rnOff = 0.15
        val book = doubleArrayOf(0.16, 0.5)
        val flapTop = doubleArrayOf(0.5, 0.7)
        val flapBot = doubleArrayOf(0.54, 0.74)
        const val flapAngle = 90.0
        const val flapFade = 20.0
        val keelClose = doubleArrayOf(0.72, 0.88)
        const val keelAngle = 64.0
        const val wingAngle = 24.0
        const val wingOmega = 15.0
        const val wingZeta = 0.42
        const val launch = 0.96
    }

    object Flight {
        const val duration = 1.9
        const val scale = 0.46
        const val scaleDur = 0.42
        const val yawDur = 0.32
        const val bankIn = 0.25
        const val lift = 60.0
        const val camera = 1000.0
        const val bankGain = 30.0
        const val cameraAtX = 312.0
        const val cameraAtY = 222.0
        const val bankMax = 0.62
        val points = arrayOf(
            doubleArrayOf(281.0, 341.2), doubleArrayOf(325.0, 326.0), doubleArrayOf(360.0, 292.0),
            doubleArrayOf(368.0, 246.0), doubleArrayOf(350.0, 200.0), doubleArrayOf(314.0, 176.0),
            doubleArrayOf(277.0, 190.0), doubleArrayOf(264.0, 226.0), doubleArrayOf(282.0, 256.0),
            doubleArrayOf(320.0, 262.0), doubleArrayOf(360.0, 238.0), doubleArrayOf(398.0, 186.0),
            doubleArrayOf(445.0, 105.0), doubleArrayOf(505.0, 15.0), doubleArrayOf(560.0, -60.0),
        )
        const val samplesPerSegment = 24
    }

    object Reform {
        const val appear = 2.2
        const val appearDur = 0.2
        const val appearScale = 0.96
        const val unfold = 2.38
        const val omega = 11.0
        const val zeta = 0.86
        const val rnOn = 2.92
        const val skiaOff = 2.98
        val radiusBack = doubleArrayOf(2.92, 3.16)
        val labelIn = doubleArrayOf(2.96, 3.2)
    }

    object Contrail {
        const val samples = 36
        const val length = 230.0
        const val headGap = 0.85
        const val widthHead = 1.4
        const val widthTail = 11.0
        const val wobble = 3.2
        const val opacityHead = 0.38
        const val fadeIn = 0.15
        const val fadeOut = 0.45
        const val blur = 1.6
        const val segments = 6
    }

    object Light {
        val dir = doubleArrayOf(-0.35, -0.55, 0.76)
        const val dark = 0.7
        const val bright = 0.5
        val shadowDir = doubleArrayOf(0.16, 0.28)
        const val shadowOpacity = 0.13
        const val shadowBlur = 2.5
        const val shadowBlurPerPt = 0.05
    }

    const val INVITE_END = Invite.release + Fold.launch + Flight.duration + Contrail.fadeOut + 0.05

    object Scan {
        const val pressIn = 0.12
        const val release = 0.27
        const val fillStart = 0.3
        const val fill = 1.45
        const val fadeDur = 0.25
        const val end = 2.8
        const val pressScale = 0.97
        const val fillOpacity = 0.18
    }

    class Drop(val side: Double, val dy: Double, val r: Double, val color: Int, val start: Double)

    object Goo {
        const val skiaOn = 0.27
        const val rnOff = 0.31
        const val sinkStart = 0.3
        const val sinkStagger = 0.04
        const val sinkDur = 0.24
        const val sinkScale = 0.5
        val gooIn = doubleArrayOf(0.32, 0.7)
        const val pullStart = 0.36
        const val pullAmount = 0.7
        const val pullOmega = 9.0
        const val pullZeta = 0.55
        const val pullShrink = 0.1
        const val wobbleAmp = 3.4
        const val wobbleFreq = 2.6
        val wobbleOn = doubleArrayOf(0.55, 0.85)
        val wobbleOff = doubleArrayOf(1.7, 1.9)
        val drops = listOf(
            Drop(-1.0, -22.0, 9.0, 0xFFD59A, 0.62),
            Drop(1.0, 18.0, 11.0, 0xA9D8FF, 0.74),
            Drop(-1.0, 26.0, 8.0, 0xADEEA9, 0.86),
            Drop(1.0, -28.0, 7.0, 0xF298B9, 0.98),
            Drop(-1.0, 4.0, 10.0, 0xCCC0FB, 1.1),
        )
        /** Head per droplet (avatar source keys) and whether it is mirrored. */
        val dropHeads = listOf("h1", "h3", "h2", "h5", "h1")
        val dropMirror = listOf(true, true, true, true, false)
        val dropFade = doubleArrayOf(0.62, 0.86)
        const val dropDur = 0.56
        const val dropArc = 24.0
        const val dropAbsorb = 0.8
        const val dropOffscreen = 24.0
        const val kickAmp = 0.07
        const val kickPeriod = 0.3
        const val kickDecay = 0.16
        const val kickFadeFrom = 0.45
        const val kickEnd = 0.6
        const val splitStart = 1.8
        const val splitOmega = 11.0
        const val splitZeta = 0.5
        val gooOut = doubleArrayOf(1.86, 2.2)
        const val faceStart = 1.92
        const val faceStagger = 0.05
        const val faceDur = 0.2
        const val faceFrom = 0.6
        const val faceOmega = 14.0
        const val faceZeta = 0.6
        const val rnOn = 2.68
        const val skiaOff = 2.74
        const val bandPad = 60.0
        val rank = intArrayOf(2, 0, 1, 3)
    }

    object Cup {
        const val end = 1.6
        const val amp = -7.0
        const val shiftX = -2.5
        const val shiftLag = 0.05
        const val period = 0.55
        const val decay = 0.35
        const val fadeFrom = 1.25
        const val tapAmp = 5.0
        const val tapRearm = 0.45
        const val centreX = 200.0
        const val centreY = 209.0
    }

    object Reduced {
        const val planeDip = 0.35
        const val inviteEnd = 0.4
        const val scanEnd = 0.4
    }

    /** GPU warm-up: a pose of the paper and of the contacts at alpha 1/255 for the first frames. */
    object Warm {
        const val frames = 3
        const val alpha = 1.0 / 255.0
        const val inv = 0.27 + 2.5
        const val scan = 1.0
    }

    /** The loop's stretch of rest (6.9 s to 0.5 s): nothing is drawn or invalidated there. */
    const val REST_FROM = 4.1 + Scan.end
    const val REST_UNTIL = 0.5

    data class Clocks(
        val invite: Double,
        val scan: Double,
        val cup: Double,
        val cupTap: Double,
        val reduced: Boolean,
        val warm: Boolean = false,
    )

    class SampledPath(
        val xs: DoubleArray,
        val ys: DoubleArray,
        val ss: DoubleArray,
        val hs: DoubleArray,
        val bank: DoubleArray,
    ) {
        val length: Double get() = ss.last()
    }

    class PathSample(val x: Double, val y: Double, val h: Double, val b: Double)

    val path = buildPath()

    /** Flight time (s after launch) at which the dart passes closest to the cup: the gust. */
    val gust: Double = run {
        var best = 0.0
        var bestDistance = Double.POSITIVE_INFINITY
        for (index in path.ss.indices) {
            val d = hypot(path.xs[index] - Cup.centreX, path.ys[index] - Cup.centreY)
            if (d < bestDistance) {
                bestDistance = d
                best = path.ss[index] / path.length
            }
        }
        var low = 0.0
        var high = 1.0
        repeat(40) {
            val mid = (low + high) / 2.0
            if (ease(easeHang, mid) < best) low = mid else high = mid
        }
        low * Flight.duration
    }

    /** The plane passes the cup this long after the press of "invite them". */
    val gustAfterPress = Invite.release + Fold.launch + gust

    object Start {
        const val invite = 0.5
        val cup = 0.5 + gustAfterPress
        const val scan = 4.1
    }

    fun clocksAtLoopTime(time: Double): Clocks {
        val loopTime = positiveModulo(time, LOOP)
        return Clocks(
            invite = (loopTime - Start.invite).coerceIn(0.0, INVITE_END),
            scan = (loopTime - Start.scan).coerceIn(0.0, Scan.end),
            cup = (loopTime - Start.cup).coerceIn(0.0, Cup.end),
            cupTap = Cup.end,
            reduced = false,
        )
    }

    fun bezier(x1: Double, y1: Double, x2: Double, y2: Double, x: Double): Double {
        if (x <= 0.0) return 0.0
        if (x >= 1.0) return 1.0
        var low = 0.0
        var high = 1.0
        var u = x
        repeat(24) {
            u = (low + high) / 2.0
            val v = 1.0 - u
            val xu = 3.0 * v * v * u * x1 + 3.0 * v * u * u * x2 + u * u * u
            if (xu < x) low = u else high = u
        }
        val v = 1.0 - u
        return 3.0 * v * v * u * y1 + 3.0 * v * u * u * y2 + u * u * u
    }

    fun ease(curve: DoubleArray, value: Double): Double = bezier(curve[0], curve[1], curve[2], curve[3], value)

    fun clamp01(value: Double): Double = min(1.0, max(0.0, value))

    fun seg(time: Double, start: Double, end: Double): Double = clamp01((time - start) / (end - start))

    /** Underdamped step response 0 -> 1 (closed form of a spring released at rest). */
    fun springStep(time: Double, omega: Double, zeta: Double): Double {
        if (time <= 0.0) return 0.0
        if (zeta * omega * time > 9.0) return 1.0
        val root = sqrt(1.0 - zeta * zeta)
        val e = exp(-zeta * omega * time)
        return 1.0 - e * (cos(omega * root * time) + (zeta / root) * sin(omega * root * time))
    }

    /** Damped sine kick, windowed so it is exactly 0 from [end] on. */
    fun kick(time: Double, period: Double, decay: Double, fadeFrom: Double, end: Double): Double {
        if (time <= 0.0 || time >= end) return 0.0
        return exp(-time / decay) * sin(2.0 * PI * time / period) * (1.0 - seg(time, fadeFrom, end))
    }

    fun triangle(value: Double): Double {
        if (value <= 0.0 || value >= 1.0) return 0.0
        if (value < 0.5) return ease(easeOut, value * 2.0)
        return 1.0 - ease(easeOut, (value - 0.5) * 2.0)
    }

    /** Position (pin pt), unwrapped heading (rad) and bank (rad) at arc distance [distance]. */
    fun pathAt(distance: Double): PathSample {
        val ss = path.ss
        val s = distance.coerceIn(0.0, ss.last())
        var low = 0
        var high = ss.lastIndex
        while (high - low > 1) {
            val mid = (low + high) shr 1
            if (ss[mid] <= s) low = mid else high = mid
        }
        val f = if (ss[high] == ss[low]) 0.0 else (s - ss[low]) / (ss[high] - ss[low])
        return PathSample(
            path.xs[low] + (path.xs[high] - path.xs[low]) * f,
            path.ys[low] + (path.ys[high] - path.ys[low]) * f,
            path.hs[low] + (path.hs[high] - path.hs[low]) * f,
            path.bank[low] + (path.bank[high] - path.bank[low]) * f,
        )
    }

    private fun positiveModulo(value: Double, modulus: Double): Double {
        return ((value % modulus) + modulus) % modulus
    }

    private fun buildPath(): SampledPath {
        val p = Flight.points
        val n = p.size
        fun at(i: Int): DoubleArray = when {
            i < 0 -> doubleArrayOf(2 * p[0][0] - p[1][0], 2 * p[0][1] - p[1][1])
            i >= n -> doubleArrayOf(2 * p[n - 1][0] - p[n - 2][0], 2 * p[n - 1][1] - p[n - 2][1])
            else -> p[i]
        }
        val xs = ArrayList<Double>()
        val ys = ArrayList<Double>()
        for (i in 0 until n - 1) {
            val p0 = at(i - 1)
            val p1 = at(i)
            val p2 = at(i + 1)
            val p3 = at(i + 2)
            for (k in 0 until Flight.samplesPerSegment) {
                val u = k.toDouble() / Flight.samplesPerSegment
                val u2 = u * u
                val u3 = u2 * u
                for (c in 0..1) {
                    val v = 0.5 * (
                        2 * p1[c] + (-p0[c] + p2[c]) * u +
                            (2 * p0[c] - 5 * p1[c] + 4 * p2[c] - p3[c]) * u2 +
                            (-p0[c] + 3 * p1[c] - 3 * p2[c] + p3[c]) * u3
                        )
                    if (c == 0) xs.add(v) else ys.add(v)
                }
            }
        }
        xs.add(p[n - 1][0])
        ys.add(p[n - 1][1])
        val m = xs.size
        val ss = DoubleArray(m)
        for (i in 1 until m) ss[i] = ss[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        val hs = DoubleArray(m)
        for (i in 0 until m) {
            val a = max(0, i - 1)
            val b = min(m - 1, i + 1)
            var h = atan2(ys[b] - ys[a], xs[b] - xs[a])
            if (i > 0) {
                while (h - hs[i - 1] > PI) h -= 2 * PI
                while (h - hs[i - 1] < -PI) h += 2 * PI
            }
            hs[i] = h
        }
        val raw = DoubleArray(m)
        for (i in 0 until m) {
            val a = max(0, i - 2)
            val b = min(m - 1, i + 2)
            val ds = ss[b] - ss[a]
            raw[i] = if (ds > 0) (hs[b] - hs[a]) / ds else 0.0
        }
        val bank = DoubleArray(m)
        val r = 8
        for (i in 0 until m) {
            var acc = 0.0
            var cnt = 0.0
            for (j in max(0, i - r)..min(m - 1, i + r)) {
                acc += raw[j]
                cnt += 1.0
            }
            val k = (acc / cnt) * Flight.bankGain
            bank[i] = max(-Flight.bankMax, min(Flight.bankMax, k))
        }
        return SampledPath(xs.toDoubleArray(), ys.toDoubleArray(), ss, hs, bank)
    }
}
