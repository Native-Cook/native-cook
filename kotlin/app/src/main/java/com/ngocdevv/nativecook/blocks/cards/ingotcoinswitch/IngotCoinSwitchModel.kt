package com.ngocdevv.nativecook.blocks.cards.ingotcoinswitch

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Ingot Coin Switch — the timeline and the 3D ring (a port of IngotCoinSwitchModel.swift).

 Ten pieces stand on a ring of radius 120 around a vertical axis and turn with it (piece i and i + 5
 share a style, so half a turn brings the ring back to itself). Each piece is a convex body made of
 four rings of K outline points: its outline goes from a rounded rectangle (a superellipse, the ingot)
 to a circle (the coin) with the morph m, its rings from a tapered ingot (small top face, big base) to
 a thin coin with a chamfer. The body is turned by its own yaw (a quarter turn with the morph, so a
 coin stands radially like a page of a book), leaned outward from its foot (the crown of the pin) and
 placed on the ring; then it is projected with perspective and shaded face by face.
*/

internal class IcsV3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(b: IcsV3) = IcsV3(x + b.x, y + b.y, z + b.z)
    operator fun minus(b: IcsV3) = IcsV3(x - b.x, y - b.y, z - b.z)
    operator fun times(k: Double) = IcsV3(x * k, y * k, z * k)
    fun dot(b: IcsV3) = x * b.x + y * b.y + z * b.z
    fun cross(b: IcsV3) = IcsV3(y * b.z - z * b.y, z * b.x - x * b.z, x * b.y - y * b.x)
    val length get() = sqrt(x * x + y * y + z * z)
    val normalized: IcsV3 get() { val l = length; return if (l > 1e-9) this * (1 / l) else this }
}

/** An RGB colour, 0…1 per channel. */
internal class IcsRgb(val r: Double, val g: Double, val b: Double) {
    operator fun times(k: Double) = IcsRgb(r * k, g * k, b * k)
    operator fun plus(o: IcsRgb) = IcsRgb(r + o.r, g + o.g, b + o.b)
    fun mix(o: IcsRgb, t: Double) = IcsRgb(r + (o.r - r) * t, g + (o.g - g) * t, b + (o.b - b) * t)
    val clamped get() = IcsRgb(r.coerceIn(0.0, 1.0), g.coerceIn(0.0, 1.0), b.coerceIn(0.0, 1.0))

    companion object {
        fun hex(v: Long) = IcsRgb(((v shr 16) and 0xFF) / 255.0, ((v shr 8) and 0xFF) / 255.0, (v and 0xFF) / 255.0)
    }
}

/** One frame of the screen, whatever drives it (the loop, the finger or the way back to rest). */
internal class IcsFrame {
    /** The ring's angle, radians. */
    var phi = 0.0
    /** The morph of each piece: 0 ingot, 1 coin. */
    var m = DoubleArray(IcsModel.COUNT)
    /** The switch's dark capsule: 0 on "Commodities", 1 on "Stocks" (a spring, it overshoots). */
    var sel = 0.0
    /** The copy crossfades from one mode (0 commodities, 1 stocks) to the other, `copyU` s after the tap. */
    var copyFrom = 0
    var copyTo = 0
    var copyU = 99.0
    var reduced = false
}

/** A strip of a piece's side, ready to fill: xy pairs on the screen. */
internal class IcsQuad(val points: FloatArray, val color: IcsRgb)

/** A flat face with its sliding highlight and its mark. */
internal class IcsCap(
    val points: FloatArray,
    /** Colours of the gradient across the face, at [IcsModel.CAP_STOPS]. */
    val colors: List<IcsRgb>,
    val fromX: Double, val fromY: Double, val toX: Double, val toY: Double,
    /** Top and bottom of the face, for the sheen. */
    val topX: Double, val topY: Double, val bottomX: Double, val bottomY: Double,
    /** Maps the mark's image (centred on 0, y down, in the face's units) onto the screen: a, b, c, d, tx, ty. */
    val transform: DoubleArray,
    val ingotAlpha: Double,
    val coinAlpha: Double,
)

internal class IcsPiece(
    val index: Int,
    val depth: Double,
    val opacity: Double,
    val silhouette: FloatArray,
    val silhouetteColor: IcsRgb,
    val quads: List<IcsQuad>,
    val caps: List<IcsCap>,
)

internal object IcsModel {
    /** One loop of the self-playing demo: rest, switch to coins, hold, switch back, rest. */
    const val LOOP = 10.0
    const val COUNT = 10
    const val STYLES = 5
    const val RING_R = 120.0
    /** The ring's size on the screen. */
    const val VIEW_SCALE = 0.9
    const val CAM_D = 900.0
    const val FOCAL = 900.0
    /** Outline points per ring. */
    const val K = 48

    /** The loop's taps on the switch, and the morph that follows each. */
    const val SWITCH_ON = 2.4
    const val SWITCH_OFF = 6.4
    const val MORPH_LAG = 0.1
    const val MORPH_DUR = 1.1
    /** The morph runs as a wave from the front piece to the sides. */
    const val WAVE_DELAY = 0.32
    const val IDLE_BEFORE_BACK = 2.0
    /** The ring's slow turn: half a turn per loop. */
    const val OMEGA = PI / LOOP
    /** Rotating the ring by this angle brings it back to itself. */
    const val HOME = PI

    val INGOT_HEX = longArrayOf(0xD8B25A, 0xD9967F, 0xB8BCC7, 0x50525C, 0xC7A47E)
    val COIN_HEX = longArrayOf(0xA9A3B2, 0xE4B9AE, 0xECDCD6, 0x86A1C9, 0xC78E86)

    val METALS = listOf("Gold", "Copper", "Silver", "Platinum", "Palladium")
    val METAL_PRICES = listOf("$78.42 /g", "$9.36 /kg", "$1.04 /g", "$33.90 /g", "$35.12 /g")
    val METAL_MOVES = listOf("+1.2%", "+0.8%", "+2.1%", "+0.4%", "+1.7%")
    val TICKERS = listOf("HLDN", "NWG", "ORF", "VLA", "QRY")
    val MARKS = listOf("H", "N", "O", "V", "Q")
    val STOCK_PRICES = listOf("$42.18", "$118.60", "$27.35", "$64.02", "$211.40")
    val STOCK_MOVES = listOf("+2.4%", "+0.9%", "+1.6%", "+3.1%", "+0.6%")

    /** Ingot: half width and height of the outline; coin: radius. */
    const val INGOT_A = 40.0
    const val INGOT_B = 108.0
    const val COIN_R = 78.0
    /** The four rings of a body, (z, scale x, scale y), for the ingot and the coin. */
    val INGOT_RINGS = arrayOf(doubleArrayOf(22.0, 0.80, 0.91), doubleArrayOf(-17.0, 1.0, 1.0), doubleArrayOf(-21.0, 1.0, 1.0), doubleArrayOf(-22.0, 0.97, 0.98))
    val COIN_RINGS = arrayOf(doubleArrayOf(7.5, 0.94, 0.94), doubleArrayOf(5.0, 1.0, 1.0), doubleArrayOf(-5.0, 1.0, 1.0), doubleArrayOf(-7.5, 0.94, 0.94))

    /** Locations of the colours across a face. */
    val CAP_STOPS = doubleArrayOf(0.0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1.0)

    // Curves

    fun clamp01(x: Double) = min(max(x, 0.0), 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
    fun easeOut(x: Double): Double { val u = 1 - x; return 1 - u * u * u }
    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun smooth(a: Double, b: Double, x: Double): Double {
        val t = clamp01((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }

    /** An angle wrapped into −π…π. */
    fun wrap(a: Double): Double {
        var x = a % (2 * PI)
        if (x > PI) x -= 2 * PI
        if (x < -PI) x += 2 * PI
        return x
    }

    /** A damped spring from x0 (velocity v0) to `target`, tau seconds in. */
    fun spring(tau: Double, x0: Double, v0: Double, target: Double, response: Double, damping: Double): Double {
        if (tau <= 0) return x0
        val z = min(damping, 0.999)
        val w0 = 2 * PI / response
        val wd = w0 * sqrt(1 - z * z)
        val a = x0 - target
        val b = (v0 + z * w0 * a) / wd
        return target + exp(-z * w0 * tau) * (a * cos(wd * tau) + b * sin(wd * tau))
    }

    const val SEL_RESPONSE = 0.42
    const val SEL_DAMPING = 0.78

    fun theta(i: Int, phi: Double) = phi + 2 * PI * i / COUNT

    /** The delay of piece i in the morph wave, from where it stands on the ring. */
    fun delay(i: Int, phi: Double) = WAVE_DELAY * abs(wrap(theta(i, phi))) / PI

    /** The morph of a piece from m0 to `target`, `u` s after its wave started. */
    fun morph(u: Double, delay: Double, m0: Double, target: Double, duration: Double = MORPH_DUR) =
        m0 + (target - m0) * easeInOut(seg(u, delay, delay + duration))

    // autoPlay

    fun autoFrame(t: Double): IcsFrame {
        val f = IcsFrame()
        f.phi = OMEGA * t
        val on = SWITCH_ON + MORPH_LAG
        val off = SWITCH_OFF + MORPH_LAG
        for (i in 0 until COUNT) {
            val up = morph(t - on, delay(i, OMEGA * on), 0.0, 1.0)
            val down = morph(t - off, delay(i, OMEGA * off), 0.0, 1.0)
            f.m[i] = up - down
        }
        if (t < SWITCH_ON) {
            f.sel = 0.0
        } else if (t < SWITCH_OFF) {
            f.sel = spring(t - SWITCH_ON, 0.0, 0.0, 1.0, SEL_RESPONSE, SEL_DAMPING)
            f.copyFrom = 0; f.copyTo = 1; f.copyU = t - SWITCH_ON
        } else {
            val x0 = spring(SWITCH_OFF - SWITCH_ON, 0.0, 0.0, 1.0, SEL_RESPONSE, SEL_DAMPING)
            f.sel = spring(t - SWITCH_OFF, x0, 0.0, 0.0, SEL_RESPONSE, SEL_DAMPING)
            f.copyFrom = 1; f.copyTo = 0; f.copyU = t - SWITCH_OFF
        }
        return f
    }

    /** Opacity of the outgoing and incoming copy, and the rise of the incoming one. */
    fun copyFade(u: Double): DoubleArray {
        val o = 1 - easeOut(seg(u, 0.0, 0.15))
        val e = easeOut(seg(u, 0.12, 0.35))
        return doubleArrayOf(o, e, 6 * (1 - e))
    }

    /** The piece nearest the front, and how much its ticker label shows (it fades at each change). */
    fun frontIndex(f: IcsFrame): Int {
        val x = -f.phi / (2 * PI / COUNT)
        var i = x.roundToInt() % COUNT
        if (i < 0) i += COUNT
        return i
    }

    fun frontAlpha(f: IcsFrame): Double {
        val x = -f.phi / (2 * PI / COUNT)
        val y = x + 0.5
        val frac = y - kotlin.math.floor(y)
        val edge = clamp01(min(frac, 1 - frac) / 0.07)
        val swap = clamp01(abs(f.m[frontIndex(f)] - 0.5) * 5)
        return if (f.reduced) swap else min(edge, swap)
    }

    // Material

    private val keyL = IcsV3(-0.45, 0.6, 0.66).normalized
    private val rimL = IcsV3(0.85, 0.15, 0.5).normalized
    private val keyH = (keyL + IcsV3(0.0, 0.0, 1.0)).normalized
    private val rimH = (rimL + IcsV3(0.0, 0.0, 1.0)).normalized
    private val white = IcsRgb(1.0, 1.0, 1.0)

    /** Metal (or frosted coin metal, `frost` 1) under a key light and a rim light. */
    fun shade(base: IcsRgb, n: IcsV3, frost: Double): IcsRgb {
        val nv = max(0.0, n.z)
        val deep = IcsRgb(base.r * base.r, base.g * base.g, base.b * base.b) * 0.62
        var c = deep.mix(base, smooth(0.0, 0.95, nv))
        c = c * (0.6 + 0.42 * max(0.0, n.dot(keyL)))
        val spec = 0.95 * max(0.0, n.dot(keyH)).pow(lerp(36.0, 22.0, frost)) + 0.4 * max(0.0, n.dot(rimH)).pow(10)
        val hi = base.mix(white, lerp(0.62, 0.5, frost))
        c = c + hi * (spec * lerp(1.0, 0.75, frost))
        return c.clamped
    }

    /** The colours across a face: its flat shade with a highlight band at `p` (0…1 across the face). */
    fun capColors(base: IcsRgb, n: IcsV3, p: Double, frost: Double): List<IcsRgb> {
        val flat = shade(base, n, frost)
        val hi = flat.mix(white, lerp(0.6, 0.4, frost))
        return CAP_STOPS.map { x ->
            val edge = 0.86 + 0.14 * (1 - (2 * x - 1).pow(2)) - 0.06 * x
            val band = exp(-((x - p) / 0.17).pow(2))
            // Polished metal also mirrors the dark room: a darker band on each side of the highlight.
            val dark = exp(-((x - p - 0.42) / 0.16).pow(2)) + exp(-((x - p + 0.42) / 0.16).pow(2))
            (flat * (edge * (1 - 0.3 * dark))).mix(hi, band * 0.9).clamped
        }
    }

    // Geometry

    /** The convex hull of xy points (monotone chain). */
    fun hull(xs: DoubleArray, ys: DoubleArray): FloatArray {
        val idx = (xs.indices).sortedWith(compareBy({ xs[it] }, { ys[it] }))
        if (idx.size < 3) return FloatArray(0)
        fun cross(o: Int, a: Int, b: Int) = (xs[a] - xs[o]) * (ys[b] - ys[o]) - (ys[a] - ys[o]) * (xs[b] - xs[o])
        val lower = ArrayList<Int>()
        for (q in idx) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], q) <= 0) lower.removeAt(lower.size - 1)
            lower.add(q)
        }
        val upper = ArrayList<Int>()
        for (q in idx.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], q) <= 0) upper.removeAt(upper.size - 1)
            upper.add(q)
        }
        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)
        val all = lower + upper
        val out = FloatArray(all.size * 2)
        for ((n, q) in all.withIndex()) { out[2 * n] = xs[q].toFloat(); out[2 * n + 1] = ys[q].toFloat() }
        return out
    }

    private fun outline(j: Int, m: Double): DoubleArray {
        val a = 2 * PI * j / K
        val c = cos(a)
        val s = sin(a)
        val e = 2.0 / 6.0
        val ix = INGOT_A * (if (c < 0) -1.0 else 1.0) * abs(c).pow(e)
        val iy = INGOT_B * (if (s < 0) -1.0 else 1.0) * abs(s).pow(e)
        return doubleArrayOf(lerp(ix, COIN_R * c, m), lerp(iy, COIN_R * s, m))
    }

    private val outlines0 = Array(K) { outline(it, 0.0) }
    private val outlines1 = Array(K) { outline(it, 1.0) }

    /** The pieces of the ring, sorted far to near, projected around (cx, cy). */
    fun pieces(f: IcsFrame, cx: Double, cy: Double): List<IcsPiece> =
        (0 until COUNT).map { piece(it, f, cx, cy) }.sortedBy { it.depth }

    private fun piece(i: Int, f: IcsFrame, cx: Double, cy: Double): IcsPiece {
        val m = f.m[i]
        val th = theta(i, f.phi)
        val style = i % STYLES
        val yaw = PI / 2 * m
        val lean = 0.2 * (1 - 0.75 * m)
        val bump = if (f.reduced) 0.0 else sin(PI * m)
        val pop = 1 + 0.05 * bump
        val half = lerp(INGOT_B, COIN_R, m)
        val cyaw = cos(yaw); val syaw = sin(yaw)
        val cl = cos(lean); val sl = sin(lean)
        val ct = cos(th); val st = sin(th)
        val cam = IcsV3(0.0, 0.0, CAM_D)

        fun world(x0: Double, y0: Double, z0: Double): IcsV3 {
            var x = x0 * pop
            var y = y0 * pop
            var z = z0 * pop
            val x1 = x * cyaw + z * syaw
            z = -x * syaw + z * cyaw
            x = x1
            val yy = y + half
            y = -half + yy * cl - z * sl
            z = yy * sl + z * cl
            val x2 = x * ct + z * st
            z = -x * st + z * ct
            return IcsV3(x2 + RING_R * st, y, z + RING_R * ct)
        }
        fun projX(p: IcsV3) = cx + p.x * VIEW_SCALE * FOCAL / (CAM_D - p.z)
        fun projY(p: IcsV3) = cy - p.y * VIEW_SCALE * FOCAL / (CAM_D - p.z)

        val rings = Array(4) { r ->
            val a = INGOT_RINGS[r]
            val b = COIN_RINGS[r]
            doubleArrayOf(lerp(a[0], b[0], m), lerp(a[1], b[1], m), lerp(a[2], b[2], m))
        }
        val pts3 = Array(4) { r ->
            Array(K) { j ->
                val ox = lerp(outlines0[j][0], outlines1[j][0], m)
                val oy = lerp(outlines0[j][1], outlines1[j][1], m)
                world(ox * rings[r][1], oy * rings[r][2], rings[r][0])
            }
        }
        val px = Array(4) { r -> DoubleArray(K) { j -> projX(pts3[r][j]) } }
        val py = Array(4) { r -> DoubleArray(K) { j -> projY(pts3[r][j]) } }

        val center = world(0.0, 0.0, 0.0)
        val frost = smooth(0.25, 0.75, m)
        val base = IcsRgb.hex(INGOT_HEX[style]).mix(IcsRgb.hex(COIN_HEX[style]), frost)
        // A coin's edge is brighter, plain metal, whatever its face's tint.
        val rim = base.mix(IcsRgb.hex(0xD9D8DC), 0.4 * frost)
        // Pieces at the back of the ring sit in the ring's shade.
        val shadeK = lerp(0.72, 1.0, smooth(-RING_R, RING_R * 0.4, center.z))

        // Side quads facing the camera; neighbours of (almost) the same colour merge into one strip.
        val quads = ArrayList<IcsQuad>()
        var sr = 0.0; var sg = 0.0; var sb = 0.0
        var seen = 0
        for (r in 0 until 3) {
            var j0 = -1
            var j1 = -1
            var runColor = IcsRgb(0.0, 0.0, 0.0)
            fun flush() {
                if (j0 < 0) return
                val n = (j1 - j0 + 2) * 2
                val pts = FloatArray(n * 2)
                var w = 0
                for (j in j0..(j1 + 1)) { pts[w++] = px[r][j % K].toFloat(); pts[w++] = py[r][j % K].toFloat() }
                for (j in (j1 + 1) downTo j0) { pts[w++] = px[r + 1][j % K].toFloat(); pts[w++] = py[r + 1][j % K].toFloat() }
                quads.add(IcsQuad(pts, runColor))
                j0 = -1
            }
            for (j in 0 until K) {
                val jn = (j + 1) % K
                val a = pts3[r][j]; val b = pts3[r][jn]; val c = pts3[r + 1][jn]; val d = pts3[r + 1][j]
                var n = (b - a).cross(d - a)
                if (n.length < 1e-6) n = (c - b).cross(d - b)
                val mid = (a + b + c + d) * 0.25
                if (n.length < 1e-6) { flush(); continue }
                if (n.dot(mid - center) < 0) n = n * -1.0
                n = n.normalized
                if (n.dot(cam - mid) <= 0) { flush(); continue }
                val col = shade(rim, n, frost) * shadeK
                sr += col.r; sg += col.g; sb += col.b
                seen += 1
                if (j0 >= 0) {
                    val dc = max(abs(col.r - runColor.r), max(abs(col.g - runColor.g), abs(col.b - runColor.b)))
                    if (dc > 0.012) flush()
                }
                if (j0 < 0) { runColor = col; j0 = j }
                j1 = j
            }
            flush()
        }

        // The silhouette, filled first so no background shows through the seams between faces.
        val allX = DoubleArray(4 * K) { px[it / K][it % K] }
        val allY = DoubleArray(4 * K) { py[it / K][it % K] }
        val silhouette = hull(allX, allY)
        val silhouetteColor = if (seen > 0) IcsRgb(sr / seen, sg / seen, sb / seen) else base * shadeK

        val caps = ArrayList<IcsCap>()
        val axisZ = (world(0.0, 0.0, 1.0) - world(0.0, 0.0, 0.0)).normalized
        for ((r, sign) in listOf(0 to 1.0, 3 to -1.0)) {
            val zc = rings[r][0]
            val cc = world(0.0, 0.0, zc)
            val n = axisZ * sign
            val facing = n.dot((cam - cc).normalized)
            if (facing <= 0) continue
            val hw = lerp(INGOT_A, COIN_R, m) * rings[r][1]
            val hh = half * rings[r][2]
            // Across the face, left to right as seen from its front.
            val left = world(-hw * sign, 0.0, zc)
            val right = world(hw * sign, 0.0, zc)
            val p = 0.5 - 0.85 * sin(atan2(n.x, n.z))
            val ox = projX(cc); val oy = projY(cc)
            val ax = world(40 * sign * rings[r][1], 0.0, zc)
            val ay = world(0.0, 40 * rings[r][2], zc)
            val ux = (projX(ax) - ox) / 40; val uy = (projY(ax) - oy) / 40
            val vx = (projX(ay) - ox) / 40; val vy = (projY(ay) - oy) / 40
            val show = smooth(0.08, 0.4, facing)
            val pts = FloatArray(K * 2)
            for (j in 0 until K) { pts[2 * j] = px[r][j].toFloat(); pts[2 * j + 1] = py[r][j].toFloat() }
            val top = world(0.0, hh, zc)
            val bottom = world(0.0, -hh, zc)
            caps.add(
                IcsCap(
                    points = pts,
                    colors = capColors(base, n, p, frost).map { it * shadeK },
                    fromX = projX(left), fromY = projY(left), toX = projX(right), toY = projY(right),
                    topX = projX(top), topY = projY(top), bottomX = projX(bottom), bottomY = projY(bottom),
                    transform = doubleArrayOf(ux, uy, -vx, -vy, ox, oy),
                    ingotAlpha = if (r == 0) show * smooth(0.5, 0.2, m) else 0.0,
                    coinAlpha = show * smooth(0.5, 0.8, m),
                ),
            )
        }

        val opacity = if (f.reduced) 1.0 else 1 - 0.45 * bump
        return IcsPiece(i, center.z, opacity, silhouette, silhouetteColor, quads, caps)
    }
}

/** Turns the clock and the finger into frames: the loop, or the touch state and its way back to rest. */
internal class IcsDriver {
    private var autoStart: Double? = null
    var manual = false
        private set
    var dragging = false
        private set
    var lastTouch = -1e9
        private set

    // The ring: an anchor angle, the release speed (decays) and the slow turn.
    private var phiAnchor = 0.0
    private var anchorAt = 0.0
    private var release = 0.0
    private var dragPhi0 = 0.0

    // The morph towards `target` (0 ingots, 1 coins).
    private var target = 0
    private var switchAt = -1e9
    private var m0 = DoubleArray(IcsModel.COUNT)
    private var delays = DoubleArray(IcsModel.COUNT)

    // The switch's capsule and the copy.
    private var selAt = -1e9
    private var sel0 = 0.0
    private var selV0 = 0.0
    private var copyFrom = 0
    private var copyAt = -1e9

    // The way home after the last touch.
    private var returnAt: Double? = null
    private var retPhi0 = 0.0
    private var retV0 = 0.0
    private var retPhi1 = 0.0

    /** True while something still moves after a touch (for pausing the frame loop without autoPlay). */
    fun settled(now: Double) = !dragging && now - lastTouch > 3

    fun frame(now: Double, auto: Boolean, reduced: Boolean): IcsFrame {
        if (auto && manual && !dragging) {
            if (returnAt == null && now >= lastTouch + IcsModel.IDLE_BEFORE_BACK) goHome(now)
            val r = returnAt
            if (r != null && now >= r + RETURN_DUR) {
                manual = false
                returnAt = null
                autoStart = now
            }
        }
        val f: IcsFrame
        if (auto && !manual) {
            val start = autoStart ?: now.also { autoStart = it }
            f = IcsModel.autoFrame((now - start) % IcsModel.LOOP)
        } else {
            f = manualFrame(now, auto, reduced)
        }
        f.reduced = reduced
        if (reduced) f.phi = 0.0
        return f
    }

    private fun omega(auto: Boolean) = if (auto) IcsModel.OMEGA else 0.0

    private fun phi(now: Double, auto: Boolean): Double {
        val r = returnAt
        if (r != null) {
            // Cubic Hermite from the current angle and speed to home, arriving at the loop's speed.
            val t = RETURN_DUR
            val u = IcsModel.clamp01((now - r) / t)
            val u2 = u * u
            val u3 = u2 * u
            return (2 * u3 - 3 * u2 + 1) * retPhi0 + (u3 - 2 * u2 + u) * t * retV0 +
                (-2 * u3 + 3 * u2) * retPhi1 + (u3 - u2) * t * IcsModel.OMEGA
        }
        if (dragging) return phiAnchor
        val d = max(0.0, now - anchorAt)
        return phiAnchor + release * DECAY * (1 - exp(-d / DECAY)) + omega(auto) * d
    }

    private fun phiVelocity(now: Double, auto: Boolean): Double {
        if (dragging) return 0.0
        val d = max(0.0, now - anchorAt)
        return release * exp(-d / DECAY) + omega(auto)
    }

    private fun manualFrame(now: Double, auto: Boolean, reduced: Boolean): IcsFrame {
        val f = IcsFrame()
        f.phi = phi(now, auto)
        val dur = if (reduced) 0.3 else IcsModel.MORPH_DUR
        for (i in 0 until IcsModel.COUNT) {
            f.m[i] = IcsModel.morph(now - switchAt, if (reduced) 0.0 else delays[i], m0[i], target.toDouble(), dur)
        }
        f.sel = IcsModel.spring(now - selAt, sel0, selV0, target.toDouble(), IcsModel.SEL_RESPONSE, IcsModel.SEL_DAMPING)
        f.copyFrom = copyFrom
        f.copyTo = target
        f.copyU = now - copyAt
        return f
    }

    /** Takes over from the loop at its current frame. */
    private fun capture(now: Double, auto: Boolean, reduced: Boolean) {
        if (manual) return
        val f = frame(now, auto, reduced)
        manual = true
        phiAnchor = f.phi % IcsModel.HOME
        anchorAt = now
        release = 0.0
        target = f.copyTo
        m0 = f.m.copyOf()
        switchAt = now
        delays = DoubleArray(IcsModel.COUNT)
        sel0 = f.sel
        selV0 = 0.0
        selAt = now
        copyFrom = f.copyFrom
        copyAt = now - f.copyU
    }

    /** Stops the way home where it is, keeping the angle. */
    private fun cancelReturn(now: Double, auto: Boolean) {
        if (returnAt == null) return
        val p = phi(now, auto)
        returnAt = null
        phiAnchor = p
        anchorAt = now
        release = 0.0
    }

    private fun goHome(now: Double) {
        retPhi0 = phi(now, true)
        retV0 = phiVelocity(now, true)
        val guess = retPhi0 + IcsModel.OMEGA * RETURN_DUR + (retV0 - IcsModel.OMEGA) * DECAY
        retPhi1 = Math.round(guess / IcsModel.HOME) * IcsModel.HOME
        if (target != 0) switchTo(0, now, true, false)
        returnAt = now
    }

    private fun switchTo(mode: Int, now: Double, auto: Boolean, reduced: Boolean) {
        val cur = manualFrame(now, auto, reduced)
        val ahead = manualFrame(now + 0.01, auto, reduced)
        m0 = cur.m.copyOf()
        selV0 = (ahead.sel - cur.sel) / 0.01
        sel0 = cur.sel
        selAt = now
        copyFrom = target
        copyAt = now
        target = mode
        switchAt = now + if (reduced) 0.0 else IcsModel.MORPH_LAG
        delays = DoubleArray(IcsModel.COUNT) { IcsModel.delay(it, cur.phi) }
    }

    /** A tap on a pill. Returns true when it changed the mode. */
    fun tap(mode: Int, now: Double, auto: Boolean, reduced: Boolean): Boolean {
        capture(now, auto, reduced)
        cancelReturn(now, auto)
        lastTouch = now
        if (mode == target) return false
        switchTo(mode, now, auto, reduced)
        return true
    }

    fun beginDrag(now: Double, auto: Boolean, reduced: Boolean) {
        capture(now, auto, reduced)
        cancelReturn(now, auto)
        dragPhi0 = phi(now, auto)
        phiAnchor = dragPhi0
        anchorAt = now
        release = 0.0
        dragging = true
        lastTouch = now
    }

    fun drag(dx: Double, now: Double) {
        if (!dragging) return
        phiAnchor = dragPhi0 + dx / POINTS_PER_RADIAN
        anchorAt = now
        lastTouch = now
    }

    fun endDrag(velocity: Double, now: Double) {
        if (!dragging) return
        dragging = false
        anchorAt = now
        release = (velocity / POINTS_PER_RADIAN).coerceIn(-7.0, 7.0)
        lastTouch = now
    }

    companion object {
        const val DECAY = 0.31
        const val RETURN_DUR = 1.4
        const val POINTS_PER_RADIAN = 150.0
    }
}
