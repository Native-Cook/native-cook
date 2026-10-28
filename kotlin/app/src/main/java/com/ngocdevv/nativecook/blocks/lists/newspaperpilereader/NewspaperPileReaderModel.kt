package com.ngocdevv.nativecook.blocks.lists.newspaperpilereader

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One invented paper of the morning pile (a port of NewspaperPileReaderModel.swift). */
internal class NprPaper(
    val masthead: String,
    /** 0 tabloid, 1 caps broadsheet, 2 blackletter with photo right, 3 blackletter with photo left,
     *  4 sans masthead between red rules, 5 serif masthead with a wide photo. */
    val style: Int,
    val headline: String,
    val deck: String,
    val kicker: String,
    val title: String,
    val summary: String,
    val tone: Long,
)

/** Where one paper is drawn this frame: centre, scale (1 = open size 340 × 228), rotation in degrees, lift. */
internal class NprSheet(
    var x: Double = 0.0,
    var y: Double = 0.0,
    var scale: Double = 1.0,
    var rot: Double = 0.0,
    var lift: Double = 0.0,
)

internal class NprFrame {
    val sheets = ArrayList<NprSheet>(6)
    /** The paper drawn a second time on top of the pile (the lifted one), and that copy's opacity. */
    var raised = -1
    var raisedAlpha = 0.0
    /** Reduced motion crossfades the whole pile. */
    var papersAlpha = 1.0
    /** The paper the caption is about, and the opacity / rise of its four rows (kicker, title, summary, dashes). */
    var caption = 1
    val rows = DoubleArray(4)
    val rise = DoubleArray(4)
    var page = 1.0
    /** 0 = pile mode in the toggle, 1 = reader mode. */
    var toggle = 0.0
}

/** Everything that drives a frame, as times and values: a pure input to [NprModel.frame]. */
internal class NprState {
    var openAt: Double? = null
    var closeAt: Double? = null
    var sel = 1
    var closeSel = 1
    var pageFrom = 1.0
    var pageTo = 1.0
    var pageAt = -1e9
    var pageLive: Double? = null
    var dragLive: Double? = null
    var dragFrom = 0.0
    var dragAt = -1e9
    var closeDrag = 0.0
    var riffleOn = false
    var riffleDx = 0.0
    var riffleY = 0.0
    var riffleVx = 0.0
    var riffleFromDx = DoubleArray(6)
    var riffleFromRot = DoubleArray(6)
    var riffleFromLift = DoubleArray(6)
    var riffleAt = -1e9
    var autoRiffle: Double? = null
    var reduced = false
}

internal class NprGeo(val w: Double, val h: Double, val top: Double, val bottom: Double) {
    val cx = w / 2
    val pileTop = top + 96
    val barY = h - max(bottom, 16.0) - 30
    val gap = (barY - 4 - pileTop - 98) / 5
    val openY = top + 222
    val paperBottom = openY + 114
    val captionTop = paperBottom + 30
    val dashY = min(captionTop + 200, barY - 70)

    fun pileX(i: Int) = cx + NprModel.JX[i]
    fun pileY(i: Int) = pileTop + 98 + i * gap + NprModel.JY[i]
}

internal object NprModel {
    const val LOOP = 9.0

    const val PAPER_W = 340.0
    const val PAPER_H = 228.0
    const val PILE_SCALE = 0.86
    const val PAGE_STRIDE = 170 + 14 + 0.9 * 170.0
    val JX = doubleArrayOf(-5.0, 7.0, -8.0, 4.0, -3.0, 6.0)
    val JY = doubleArrayOf(0.0, 4.0, -3.0, 5.0, -2.0, 0.0)
    val PILE_ROT = doubleArrayOf(-2.2, 1.6, -3.0, 2.4, -1.4, 2.0)

    // Loop beats (s).
    const val RIFFLE_AT = 1.0
    const val OPEN_AT = 2.6
    const val CLOSE_AT = 6.0
    const val IDLE_BEFORE_BACK = 2.0

    val PAPERS = listOf(
        NprPaper(
            "Evening Lantern", 0, "SIGNING OFF", "Keeper of Gull Point, 41 years",
            "EVENING LANTERN · 4 MIN READ", "Lighthouse keeper signs off after 41 years",
            "Ada Morrow climbed the 212 steps of Gull Point every night since 1985. On Saturday the light goes automatic.",
            0xF3F1EA,
        ),
        NprPaper(
            "THE QUAYSIDE JOURNAL.", 1, "Port Cities Bet on Electric Ferries", "Spring Fares Fall 12%",
            "THE QUAYSIDE JOURNAL · 6 MIN READ", "Port cities bet on electric ferries",
            "Three harbors retire their diesel fleets by 2029. Fares fall 12% this spring as the first quiet crossings begin.",
            0xEFECE4,
        ),
        NprPaper(
            "The Larkmoor Herald", 2, "Night Market Returns to the Old Docks", "",
            "THE LARKMOOR HERALD · 3 MIN READ", "Night market returns to the old docks",
            "Forty stalls, a string of lights and a brass band: the Friday market is back after six years, with late ferries home.",
            0xF2EFE6,
        ),
        NprPaper(
            "The Fernhill Post", 3, "Rail Line Reopens After Storm Repairs", "",
            "THE FERNHILL POST · 5 MIN READ", "Rail line reopens after storm repairs",
            "Trains run again between Fernhill and Copper Bay after eleven weeks of work on the washed-out embankment.",
            0xEEEDE8,
        ),
        NprPaper(
            "Saltday", 4, "Tides of Change on the North Shore", "",
            "SALTDAY · 7 MIN READ", "Tides of change on the north shore",
            "A new seawall, a wider beach and a boardwalk that floats: how one coastal town plans for the next fifty winters.",
            0xF1EEE5,
        ),
        NprPaper(
            "Orrin Bay Gazette", 5, "Gardens Bloom a Month Early", "",
            "ORRIN BAY GAZETTE · 2 MIN READ", "Gardens bloom a month early",
            "A warm March has the magnolias out ahead of the festival, so the botanic garden opens its gates this weekend.",
            0xEDEAE1,
        ),
    )

    // Curves.

    fun clamp01(x: Double) = min(max(x, 0.0), 1.0)
    fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
    /** 0 before a, 1 after b. */
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun easeOutCubic(x: Double) = 1 - (1 - x).pow(3)
    fun easeInCubic(x: Double) = x * x * x
    fun easeInOutCubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    /** A damped spring as a curve: ≈ 3.5 % overshoot at x ≈ 0.42, exactly 1 from x = 1. */
    fun spr(x: Double) = if (x >= 1) 1.0 else 1 - exp(-8 * x) * cos(7.5 * x)
    /** A riffle pulse: out, back, a small overshoot, 0 at both ends. */
    fun rif(x: Double) = if (x <= 0 || x >= 1) 0.0 else sin(1.25 * PI * x) * sqrt(1 - x) / 0.8
    fun hash(n: Double): Double {
        val v = sin(n * 12.9898) * 43758.5453
        return v - floor(v)
    }

    /** Swift's `rounded()`: half away from zero. */
    fun roundHalfAway(x: Double): Double = if (x >= 0) floor(x + 0.5) else -floor(-x + 0.5)

    // Frame.

    fun autoState(): NprState = NprState().apply {
        autoRiffle = RIFFLE_AT
        openAt = OPEN_AT
        closeAt = CLOSE_AT
    }

    fun page(s: NprState, t: Double): Double {
        s.pageLive?.let { return it }
        return lerp(s.pageFrom, s.pageTo, spr(seg(t, s.pageAt, s.pageAt + 0.5)))
    }

    fun openSpring(s: NprState, t: Double, i: Int): Double {
        val a = s.openAt ?: return 0.0
        if (s.reduced) return if (t >= a + 0.2) 1.0 else 0.0
        val lead = i == s.sel
        val d = if (lead) 0.0 else 0.06 + 0.045 * abs(i - s.sel)
        val start = a + 0.1 + d
        return spr(seg(t, start, start + if (lead) 0.75 else 0.6))
    }

    fun closeSpring(s: NprState, t: Double, i: Int): Double {
        val b = s.closeAt ?: return 0.0
        if (s.reduced) return if (t >= b + 0.2) 1.0 else 0.0
        val lead = i == s.closeSel
        val start = b + if (lead) 0.05 else 0.15 + 0.045 * abs(i - s.closeSel)
        return spr(seg(t, start, start + if (lead) 0.75 else 0.65))
    }

    /** 0 = in the pile, 1 = in the reader (may overshoot either way). */
    fun morph(s: NprState, t: Double, i: Int) = openSpring(s, t, i) * (1 - closeSpring(s, t, i))

    fun dragOffset(s: NprState, t: Double): Double {
        s.dragLive?.let { return it }
        if (s.closeAt != null) return s.closeDrag * (1 - clamp01(closeSpring(s, t, s.closeSel)))
        return s.dragFrom * (1 - spr(seg(t, s.dragAt, s.dragAt + 0.45)))
    }

    fun riffleShift(dx: Double) = dx * 0.55 / (1 + abs(dx) / 240)

    /** The finger's part of the riffle: papers near the finger follow it most. Returns (dx, rot, lift). */
    fun fingerRiffle(s: NprState, i: Int, g: NprGeo): DoubleArray {
        val w = exp(-((g.pileY(i) - s.riffleY) / 110).pow(2))
        val shift = riffleShift(s.riffleDx)
        return doubleArrayOf(
            shift * w,
            (shift * 0.1 + min(max(s.riffleVx * 0.0015, -4.0), 4.0)) * w,
            0.35 * min(1.0, abs(shift) / 22) * w,
        )
    }

    /** Sideways shift, extra rotation and lift of paper i from the riffle (loop wave, finger or release). */
    fun riffle(s: NprState, t: Double, i: Int, g: NprGeo): DoubleArray {
        var dx = 0.0
        var rot = 0.0
        var lift = 0.0
        val a = s.autoRiffle
        if (a != null && !s.reduced) {
            val x = seg(t, a + 0.07 * i, a + 0.07 * i + 0.6)
            val r = rif(x)
            dx += 22 * r
            rot += 3 * r
            lift += 0.35 * max(r, 0.0)
        }
        if (s.riffleOn) {
            val f = fingerRiffle(s, i, g)
            dx += f[0]
            rot += f[1]
            lift += f[2]
        } else {
            val k = 1 - spr(seg(t, s.riffleAt, s.riffleAt + 0.6))
            dx += s.riffleFromDx[i] * k
            rot += s.riffleFromRot[i] * k
            lift += s.riffleFromLift[i] * k
        }
        return doubleArrayOf(dx, rot, lift)
    }

    fun frame(s: NprState, t: Double, g: NprGeo): NprFrame {
        val f = NprFrame()
        val n = PAPERS.size
        val p = page(s, t)
        val rp = min(max(roundHalfAway(p).toInt(), 0), n - 1)
        val lead = if (s.closeAt != null) s.closeSel else s.sel
        val drag = dragOffset(s, t)
        f.page = p
        f.caption = rp
        for (i in 0 until n) {
            val m = morph(s, t, i)
            val mc = clamp01(m)
            val r = riffle(s, t, i, g)
            val di = i - p
            val d = min(1.0, abs(di))
            val near = max(0.0, 1 - abs(di))
            val sgn = if (di > 0) 1.0 else if (di < 0) -1.0 else 0.0
            val ox = g.cx + di * PAGE_STRIDE
            val oy = g.openY + 10 * d + drag * near
            val os = 1 - 0.1 * d - drag / 1500 * near
            val orot = -0.6 * (1 - d) + 3 * sgn * d
            val sh = NprSheet(
                x = lerp(g.pileX(i) + r[0], ox, m),
                y = lerp(g.pileY(i), oy, m),
                scale = lerp(PILE_SCALE, os, m),
                rot = lerp(PILE_ROT[i] + r[1], orot, m),
                lift = mc * near + r[2],
            )
            if (i == lead) {
                sh.scale += 0.04 * sin(PI * mc)
            } else {
                sh.rot += 6 * (if (i > lead) 1 else -1) * sin(PI * mc)
            }
            val a = s.openAt
            if (i == s.sel && a != null && !s.reduced) {
                sh.scale *= 1 - 0.025 * sin(PI * seg(t, a, a + 0.14))
            }
            f.sheets.add(sh)
        }
        val a = s.openAt
        if (a != null) {
            val b = s.closeAt
            if (s.reduced) {
                f.papersAlpha = 1 - sin(PI * seg(t, a, a + 0.4))
                if (b != null) f.papersAlpha *= 1 - sin(PI * seg(t, b, b + 0.4))
            } else {
                f.raised = if (b != null) s.closeSel else rp
                f.raisedAlpha = seg(t, a + 0.1, a + 0.2) * (if (b != null) 1 - seg(t, b, b + 0.12) else 1.0)
            }
            val out = if (b == null) 0.0 else if (s.reduced) seg(t, b, b + 0.3) else easeInCubic(seg(t, b, b + 0.2))
            val pageFade = clamp01(1 - 2 * abs(p - rp))
            val dragFade = 1 - min(1.0, max(0.0, drag) / 120)
            for (k in 0 until 4) {
                val e = if (s.reduced) {
                    seg(t, a + 0.2, a + 0.5)
                } else {
                    val a0 = a + 0.45 + 0.06 * k
                    easeOutCubic(seg(t, a0, a0 + 0.4))
                }
                f.rows[k] = e * (1 - out) * dragFade * (if (k == 3) 1.0 else pageFade)
                f.rise[k] = if (s.reduced) 0.0 else 8 * (1 - e)
            }
        }
        f.toggle = easeInOutCubic(clamp01(morph(s, t, lead)))
        return f
    }

    /** The front-most paper under a point in the pile, or -1. */
    fun hit(f: NprFrame, x: Double, y: Double): Int {
        for (i in f.sheets.indices.reversed()) {
            val sh = f.sheets[i]
            val a = -sh.rot * PI / 180
            val dx = x - sh.x
            val dy = y - sh.y
            val lx = (dx * cos(a) - dy * sin(a)) / sh.scale
            val ly = (dx * sin(a) + dy * cos(a)) / sh.scale
            if (abs(lx) <= PAPER_W / 2 && abs(ly) <= PAPER_H / 2) return i
        }
        return -1
    }
}
