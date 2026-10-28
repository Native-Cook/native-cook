package com.ngocdevv.nativecook.blocks.animations.prismbeamfolder

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Prism Beam Folder — geometry, the session model and the autoPlay script (a port of
 PrismBeamFolderMotion.swift, same numbers).

 A session is one press of the folder: τ = seconds since the press. The folder squashes (0 – 0.2),
 a thin prism beam shoots up out of it (0.2 – 0.45) and widens into a cone (0.4 – 1.05). While the
 finger is down an item spawns at the top of the beam every 0.26 s (τᵢ = 0.65 + 0.26 i) and floats
 down through it for 2.3 s into the folder; each landing bumps the folder and ticks the badge.
 After the release and the last landing the beam narrows to a line (cs … cs + 0.45), the line falls
 into the folder (cs + 0.4 … cs + 0.65), the front closes, and the badge clears at
 cs + 1.05 … cs + 1.35 (cs = max(release, 1.05, last landing) + 0.05).

 autoPlay: LOOP = 8 s, one session pressed at t = 0.8 and held to τ = 2.8 (nine items):
   0.0 – 0.8 rest · 0.8 – 1.85 press, shoot, widen · 1.45 – 5.83 nine items pour in ·
   5.88 – 6.68 narrow, fall, close · 6.93 – 7.23 badge clears · 7.23 – 8.0 rest (frame 8 = frame 0).
*/
internal object PrismBeamFolderMotion {
    const val LOOP = 8.0
    const val REDUCED_AT = 3.8
    const val AUTO_PRESS = 0.8
    const val AUTO_HOLD = 2.8
    const val IDLE_BEFORE_BACK = 2.0
    const val SPAWN0 = 0.65
    const val GAP = 0.26
    const val FLIGHT = 2.3
    const val WIDEN_END = 1.05

    /** Item kinds: 0 peach, 1 cherries, 2 aubergine, 3 chili. */
    val kinds = intArrayOf(0, 2, 1, 3, 0, 1, 2, 0, 3)
    val lanes = doubleArrayOf(-0.5, 0.42, -0.08, 0.6, -0.62, 0.2, -0.3, 0.5, 0.02)
    val turns = doubleArrayOf(-20.0, 35.0, 10.0, -40.0, 15.0, -10.0, 50.0, -25.0, 20.0)
    val sizes = doubleArrayOf(55.0, 58.0, 53.0, 53.0, 51.0, 53.0, 55.0, 53.0, 51.0)

    fun clamp01(x: Double) = min(1.0, max(0.0, x))
    /** 0 before a, 1 after b (safe when a and b are infinite: a close that has not been scheduled). */
    fun seg(t: Double, a: Double, b: Double) = if (t <= a) 0.0 else if (t >= b) 1.0 else (t - a) / (b - a)
    fun easeOut(x: Double) = 1 - (1 - x).pow(3)
    fun easeIn(x: Double) = x * x * x
    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

    /** Ease-out with a small overshoot (about 2 %). */
    fun easeOutBack(x: Double): Double {
        val c1 = 0.8
        val c3 = c1 + 1
        return 1 + c3 * (x - 1).pow(3) + c1 * (x - 1).pow(2)
    }
}

internal class PrismBeamFolderGeo(val w: Double, val h: Double, val top: Double, val bottom: Double) {
    val cx = w / 2
    val folderCY = (h * 0.64).roundToInt().toDouble()
    /** The foot of the beam, and its full length (to y = -40). */
    val originY = folderCY - 34
    val length = originY + 40
    val itemStartY = top + 110
    val itemEndY = folderCY - 16

    fun halfWidth(y: Double): Double {
        val d = max(0.0, originY - y)
        return HB_FULL + (HT_FULL - HB_FULL) * d / length
    }

    fun onFolder(x: Double, y: Double) =
        abs(x - cx) < 52 + 30 && y > folderCY - 50 - 30 && y < folderCY + 42 + 30

    companion object {
        const val HB_FULL = 36.0
        const val HT_FULL = 232.0
    }
}

internal class PrismBeamFolderItem(
    val kind: Int,
    val x: Double,
    val y: Double,
    val size: Double,
    val scale: Double,
    val angle: Double,
    val alpha: Double,
)

internal class PrismBeamFolderFrame {
    var hb = 0.0
    var ht = 0.0
    var tip = 0.0
    var beamAlpha = 0.0
    var glow = 0.0
    var phase = 0.0
    var folderSX = 1.0
    var folderSY = 1.0
    var open = 0.0
    var rippleR = 0.0
    var rippleA = 0.0
    val items = ArrayList<PrismBeamFolderItem>(12)
    var count = 0
    var badge = 0.0
    var saved = 0.0
    var header = 1.0
    var landed = 0
    var opened = false
    var restAt = Double.POSITIVE_INFINITY
    var closeStart = Double.POSITIVE_INFINITY
}

/** One hold of the finger, in session seconds; `end` is infinite while it is down. */
internal class PrismBeamFolderHold(val start: Double, var end: Double)

internal object PrismBeamFolderSession {
    private typealias M = PrismBeamFolderMotion

    private fun held(holds: List<PrismBeamFolderHold>, s: Double) = holds.any { s >= it.start && s <= it.end }

    fun closeStart(holds: List<PrismBeamFolderHold>): Double {
        val last = holds.lastOrNull() ?: return Double.POSITIVE_INFINITY
        if (last.end.isInfinite()) return Double.POSITIVE_INFINITY
        var lastLand = Double.NEGATIVE_INFINITY
        var i = 0
        while (true) {
            val s = M.SPAWN0 + M.GAP * i
            if (s > last.end) break
            if (held(holds, s)) lastLand = s + M.FLIGHT
            i++
        }
        return max(max(last.end, M.WIDEN_END), lastLand) + 0.05
    }

    /** The frame at τ seconds into a session. Pure: the same inputs give the same frame. */
    fun frame(tau: Double, holds: List<PrismBeamFolderHold>, base: Int, phase: Double, g: PrismBeamFolderGeo): PrismBeamFolderFrame {
        val f = PrismBeamFolderFrame()
        f.phase = phase
        if (tau < 0 || holds.isEmpty()) return f
        val cs = closeStart(holds)
        f.closeStart = cs
        f.restAt = cs + 1.35

        val shoot = M.easeOut(M.seg(tau, 0.2, 0.45))
        val widen = M.easeOutBack(M.seg(tau, 0.4, M.WIDEN_END))
        val narrow = M.easeInOut(M.seg(tau, cs, cs + 0.45))
        val retract = M.easeIn(M.seg(tau, cs + 0.4, cs + 0.65))
        f.hb = (4 + (PrismBeamFolderGeo.HB_FULL - 4) * widen) * (1 - 0.94 * narrow)
        f.ht = (12 + (PrismBeamFolderGeo.HT_FULL - 12) * widen) * (1 - 0.985 * narrow)
        f.tip = g.length * shoot * (1 - retract)
        f.beamAlpha = M.seg(tau, 0.2, 0.3) * (1 - M.seg(tau, cs + 0.5, cs + 0.65))
        f.glow = min(1.0, max(0.0, widen)) * (1 - narrow)
        f.header = 1 - 0.55 * f.glow
        f.open = M.easeOut(M.seg(tau, 0.2, 0.5)) * (1 - M.easeInOut(M.seg(tau, cs + 0.45, cs + 0.8)))
        f.opened = tau >= 0.2

        val hs = holds.lastOrNull { it.start <= tau }?.start ?: 0.0
        val x = tau - hs
        var a = 0.0
        if (x < 0.2) {
            a = M.easeOut(M.seg(x, 0.0, 0.2))
        } else if (x < 1.2) {
            val k = x - 0.2
            a = exp(-6 * k) * cos(14 * k)
        }
        if (x < 0.5) {
            val r = x / 0.5
            f.rippleR = 18 + 28 * M.easeOut(r)
            f.rippleA = 0.5 * (1 - r)
        }

        var landed = 0
        var firstLand = -1.0
        var lastLand = -1.0
        var bump = 0.0
        var i = 0
        while (true) {
            val s = M.SPAWN0 + M.GAP * i
            if (s > tau) break
            val index = i
            i++
            if (!held(holds, s)) continue
            val land = s + M.FLIGHT
            if (tau >= land) {
                landed++
                if (firstLand < 0) firstLand = land
                lastLand = land
                val k = tau - land
                if (k < 0.6) bump += 0.03 * exp(-9 * k) * cos(16 * k)
                continue
            }
            f.items.add(item(index, (tau - s) / M.FLIGHT, g))
        }
        f.landed = landed
        f.folderSX = 1 + 0.04 * a + 0.5 * bump
        f.folderSY = 1 - 0.07 * a - bump

        f.count = base + landed
        val clear = M.easeIn(M.seg(tau, cs + 1.05, cs + 1.35))
        var pop = 0.0
        var shown = 0.0
        if (base > 0) {
            pop = 1.0
            shown = 1.0
        } else if (firstLand >= 0) {
            val k = tau - firstLand
            pop = 1 - exp(-8 * k) * cos(12 * k)
            shown = M.easeOut(M.seg(tau, firstLand, firstLand + 0.25))
        }
        var pulse = 0.0
        if (lastLand >= 0 && (firstLand != lastLand || base > 0)) {
            val k = tau - lastLand
            if (k < 0.6) pulse = 0.25 * exp(-9 * k) * sin(14 * k)
        }
        f.badge = if (f.count > 0) max(0.0, (pop + pulse) * (1 - clear)) else 0.0
        f.saved = shown * (1 - M.easeOut(M.seg(tau, cs + 1.05, cs + 1.3)))
        return f
    }

    fun item(i: Int, p: Double, g: PrismBeamFolderGeo): PrismBeamFolderItem {
        val j = i % M.kinds.size
        val e = 0.55 * p + 0.45 * p * p * p
        val y = g.itemStartY + (g.itemEndY - g.itemStartY) * e
        val wob = 5 * sin(2 * PI * (1.1 * p + 0.37 * i))
        val x = g.cx + M.lanes[j] * g.halfWidth(y) * 0.88 * (1 - 0.5 * p * p * p) + wob
        val scale = (0.6 + 0.4 * M.easeOut(M.seg(p, 0.0, 0.12))) * (1 - 0.5 * M.seg(p, 0.8, 1.0))
        val spin = if (i % 2 == 0) 90.0 else -90.0
        val angle = M.turns[j] + 18 * sin(2 * PI * (0.6 * p + 0.21 * i)) + spin * p * p * p
        return PrismBeamFolderItem(M.kinds[j], x, y, M.sizes[j], scale, angle, M.seg(p, 0.0, 0.1))
    }

    fun script(t: Double, g: PrismBeamFolderGeo): PrismBeamFolderFrame =
        frame(t - M.AUTO_PRESS, listOf(PrismBeamFolderHold(0.0, M.AUTO_HOLD)), 0, t, g)
}

/** Clock state behind the frames (seconds): the autoPlay clock and the session a finger started. */
internal class PrismBeamFolderSim {
    private typealias M = PrismBeamFolderMotion
    private typealias S = PrismBeamFolderSession

    private var epoch = Double.NaN
    private var start = Double.NaN
    private val holds = ArrayList<PrismBeamFolderHold>()
    private var base = 0
    private var holding = false
    private var pending = false
    private var released = Double.NaN
    private var lastLanded = 0
    private var wasOpen = false
    var onFeel: (() -> Unit)? = null

    val inSession get() = !start.isNaN()
    val touching get() = holding

    fun frame(now: Double, g: PrismBeamFolderGeo, auto: Boolean): PrismBeamFolderFrame {
        if (epoch.isNaN()) epoch = now
        if (start.isNaN()) {
            val t = if (auto) (now - epoch) % M.LOOP else 0.0
            return S.script(t, g)
        }
        var tau = now - start
        var f = S.frame(tau, holds, base, now - epoch, g)
        if (pending && tau >= f.closeStart + 0.65) {
            pending = false
            begin(now, 0)
            tau = 0.0
            f = S.frame(0.0, holds, base, now - epoch, g)
        }
        if (f.opened && !wasOpen) onFeel?.invoke()
        if (f.landed > lastLanded) onFeel?.invoke()
        wasOpen = f.opened
        lastLanded = f.landed
        if (!holding && !pending && !released.isNaN() && now - released >= M.IDLE_BEFORE_BACK && tau >= f.restAt) {
            start = Double.NaN
            epoch = now
            return S.script(0.0, g)
        }
        return f
    }

    private fun begin(now: Double, base: Int) {
        start = now
        holds.clear()
        holds.add(PrismBeamFolderHold(0.0, Double.POSITIVE_INFINITY))
        this.base = base
        lastLanded = 0
        wasOpen = false
    }

    fun press(now: Double, g: PrismBeamFolderGeo, auto: Boolean) {
        holding = true
        if (start.isNaN()) {
            val t = if (auto && !epoch.isNaN()) (now - epoch) % M.LOOP else -1.0
            val tau = t - M.AUTO_PRESS
            val restAt = S.closeStart(listOf(PrismBeamFolderHold(0.0, M.AUTO_HOLD))) + 1.35
            if (tau >= 0 && tau < restAt) {
                // The running loop becomes the session: its virtual finger lifts now.
                start = now - tau
                holds.clear()
                holds.add(PrismBeamFolderHold(0.0, min(M.AUTO_HOLD, tau)))
                base = 0
                val f = S.frame(tau, holds, 0, 0.0, g)
                lastLanded = f.landed
                wasOpen = f.opened
            } else {
                begin(now, 0)
                return
            }
        }
        val tau = now - start
        val cs = S.closeStart(holds)
        if (tau < cs) {
            holds.add(PrismBeamFolderHold(tau, Double.POSITIVE_INFINITY))
        } else if (tau < cs + 0.65) {
            pending = true
        } else {
            val f = S.frame(tau, holds, base, 0.0, g)
            begin(now, if (tau < cs + 1.05) f.count else 0)
        }
    }

    fun release(now: Double) {
        holding = false
        pending = false
        released = now
        val last = holds.lastOrNull() ?: return
        if (!start.isNaN() && last.end.isInfinite()) last.end = now - start
    }
}
