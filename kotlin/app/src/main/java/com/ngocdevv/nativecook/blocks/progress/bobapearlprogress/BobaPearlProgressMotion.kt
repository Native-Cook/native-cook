package com.ngocdevv.nativecook.blocks.progress.bobapearlprogress

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Boba Pearl Progress — layout, the pearls' packing and the order's timeline (a port of
 bobaMotion.ts). A cup's content is a pure function of its content time `tc` (pearls dropped, tea
 level, foam, seal, straw); the loop adds the shake and the cup swap. Every frame is a function of
 the loop time t.

 autoPlay timeline (LOOP = 10 s):
   0.0 – 1.4   rest: an empty clear cup, "Brewing" at 0 %.
   1.4 – 2.8   40 tapioca pearls drop in, bottom row first, three scoops; each falls under gravity
               and bounces once into its packed place.
   2.6 – 5.0   milk tea pours in (ease-in-out cubic), surface waving; brown-sugar streaks climb.
   5.0 – 6.4   "Shaking": the cup rocks ±4° about its base, the tea sloshes 70 ms behind, pearls
               jiggle; foam rises on top.
   6.4 – 7.0   "Sealing": the film drops onto the rim (ease-out) and is pressed (squash 8 %).
   7.05 – 7.45 the straw drops in and punches the film.
   7.0 – 8.2   "Ready" at 100 %.
   8.2 – 9.0   the cup slides out to the left; 8.85 – 9.6 an empty cup slides in from the right
               with a little overshoot; the label returns to "Brewing", 0 %.
   9.6 – 10.0  rest.
 */

/** The autoPlay loop in seconds (the manifest's recording.loop). */
const val LOOP = 10f
private const val G = 2200f
private const val PIF = PI.toFloat()

internal fun bobaClamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
internal fun bobaSeg(t: Float, a: Float, b: Float) = bobaClamp01((t - a) / (b - a))
internal fun bobaEaseOutCubic(x: Float): Float { val u = 1 - x; return 1 - u * u * u }
internal fun bobaEaseInOutCubic(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
/** Ease-out with a small overshoot (about 4 %). */
internal fun bobaEaseOutBack(x: Float): Float {
    val c1 = 1.1f
    val c3 = c1 + 1
    val u = x - 1
    return 1 + c3 * u * u * u + c1 * u * u
}

private fun bobaHash(i: Int): Float {
    val s = sin(i * 12.9898 + 4.1) * 43758.5453
    return (s - floor(s)).toFloat()
}

/** Layout in dp. Cup: rim y, base y, half widths at rim and base, rim and base ellipse radii. */
internal class BobaGeo(val w: Float, val h: Float, top: Float, bottom: Float) {
    val u = min(w / 393f, (h - top - bottom) / 759f)
    val cx = w / 2
    val top = top + 104 + 86 * u
    val cupH = 300 * u
    val base = this.top + cupH
    val rimW = 96 * u
    val baseW = 70 * u
    val rimRy = 13 * u
    val baseRy = 9 * u
    val maxLevel = cupH * 0.8f
    val pearlR = 8.4f * u
    val percentY = this.top + cupH + 36 * u
    val stepsY = this.top + cupH + 104 * u
    val buttonY = h - bottom - 16 - 52
}

/** Half width of the cup's inside at height y above the base. */
internal fun bobaHalfWidth(g: BobaGeo, y: Float) = g.baseW + ((g.rimW - g.baseW) * y) / g.cupH - 3 * g.u

/** A pearl's resting place (cup frame: x from the axis, y up from the base), size, layer and drop. */
internal class BobaPearl(val x: Float, val y: Float, val r: Float, val back: Boolean) {
    var drop = 0f
    var fromX = 0f
}

/**
 * Packs the pearls in rows from the base up: a back layer (smaller, higher, darker) and a front
 * layer, with seeded jitter. Then orders the drops bottom first, in three scoops.
 */
internal fun bobaPackPearls(g: BobaGeo): List<BobaPearl> {
    val out = ArrayList<BobaPearl>()
    for (back in intArrayOf(1, 0)) {
        val r = g.pearlR * (if (back == 1) 0.86f else 1f)
        val want = if (back == 1) 18 else 22
        var row = 0
        var placed = 0
        while (placed < want && row < 8) {
            val y = r + row * r * 1.72f + (if (back == 1) 3 * g.u else 0f)
            val half = bobaHalfWidth(g, y) - r
            val count = max(1, floor((half * 2) / (r * 2.02f)).toInt() + 1)
            val step = if (count > 1) (half * 2) / (count - 1) else 0f
            val shift = (if (row % 2 == 1) step * 0.5f else 0f) + (if (back == 1) step * 0.25f else 0f)
            var i = 0
            while (i < count && placed < want) {
                val k = out.size
                var x = -half + i * step + shift
                if (x > half) x -= step
                x += (bobaHash(k) - 0.5f) * r * 0.35f
                out.add(BobaPearl(x, y + (bobaHash(k + 50) - 0.5f) * r * 0.25f, r * (0.94f + bobaHash(k + 90) * 0.12f), back == 1))
                placed += 1
                i += 1
            }
            row += 1
        }
    }
    val order = out.indices.sortedBy { out[it].y }
    val third = ceil(out.size / 3f).toInt()
    order.forEachIndexed { rank, idx ->
        val scoop = (rank * 3) / out.size
        out[idx].drop = 1.4f + scoop * 0.42f + (rank % third) * 0.022f
        out[idx].fromX = (bobaHash(idx + 7) - 0.5f) * 34 * g.u
    }
    return out
}

/** Height above the base (into out[1]; −1: not dropped yet) and x (out[0]) of pearl p at content time tc. */
internal fun bobaPearlAt(g: BobaGeo, p: BobaPearl, tc: Float, out: FloatArray) {
    if (tc < p.drop) {
        out[0] = p.fromX
        out[1] = -1f
        return
    }
    val startY = g.cupH + 46 * g.u
    val fall = startY - p.y
    val tFall = sqrt((2 * fall) / G)
    val tau = tc - p.drop
    if (tau < tFall) {
        val k = tau / tFall
        out[0] = p.fromX + (p.x - p.fromX) * bobaEaseOutCubic(k)
        out[1] = startY - 0.5f * G * tau * tau
        return
    }
    val after = tau - tFall
    val bounce = 9 * g.u * exp(-after / 0.09f) * abs(sin(after * 24))
    out[0] = p.x
    out[1] = p.y + bounce
}

internal fun bobaLevel(g: BobaGeo, tc: Float) = g.maxLevel * bobaEaseInOutCubic(bobaSeg(tc, 2.6f, 5.0f))
/** The pour: top of the stream reached (0 … 1) and tail fallen (0 … 1). */
internal fun bobaPourIn(tc: Float) = bobaSeg(tc, 2.5f, 2.68f)
internal fun bobaPourOut(tc: Float) = bobaSeg(tc, 4.82f, 5.02f)
internal fun bobaFoam(tc: Float) = bobaEaseOutCubic(bobaSeg(tc, 5.3f, 6.3f))
/** The seal: drop (0 above … 1 on the rim) and squash (0 … 1 … 0). */
internal fun bobaSealDrop(tc: Float) = bobaEaseOutCubic(bobaSeg(tc, 6.4f, 6.75f))
internal fun bobaSealSquash(tc: Float) = sin(PIF * bobaSeg(tc, 6.72f, 7.0f))
/** The straw: how far it has come down (0 … 1) and the film's pop ring (0 … 1). */
internal fun bobaStrawCome(tc: Float) = bobaEaseOutBack(bobaSeg(tc, 7.05f, 7.45f))
internal fun bobaStrawPop(tc: Float) = bobaSeg(tc, 7.38f, 7.75f)

internal fun bobaPercent(tc: Float) =
    18 * bobaSeg(tc, 1.4f, 2.8f) + 42 * bobaSeg(tc, 2.8f, 5.0f) + 22 * bobaSeg(tc, 5.0f, 6.4f) +
        13 * bobaSeg(tc, 6.4f, 7.0f) + 5 * bobaSeg(tc, 7.0f, 7.2f)

internal val BOBA_STEPS = arrayOf("Brewing", "Shaking", "Sealing", "Ready")
internal val BOBA_STATUS = arrayOf("Brewing your tea", "Shaking it up", "Sealing your cup", "Ready for pickup")
internal val BOBA_ETA = arrayOf("About 2 min", "About 1 min", "Almost there", "Counter 2")

internal fun bobaStepOf(tc: Float) = if (tc < 5.0f) 0 else if (tc < 6.4f) 1 else if (tc < 7.0f) 2 else 3

/** Cup rotation (radians) of a shake that started `s` seconds ago (1.4 s long). */
internal fun bobaShakeRot(s: Float): Float {
    if (s <= 0 || s >= 1.4f) return 0f
    val env = sin((PIF * s) / 1.4f).pow(0.6f)
    return ((4 * PIF) / 180) * sin((2 * PIF * 4 * s) / 1.4f) * env
}

internal fun bobaShakeEnv(s: Float): Float {
    if (s <= 0 || s >= 1.4f) return 0f
    return sin((PIF * s) / 1.4f)
}

/** One cup to draw. Surface tilt in the cup's frame, pearl roll (dp), shake envelope and phase. */
internal class BobaCup {
    var dx = 0f
    var rot = 0f
    var tc = 0f
    var tilt = 0f
    var roll = 0f
    var shake = 0f
    var phase = 0f

    fun set(dx: Float, rot: Float, tc: Float, tilt: Float, roll: Float, shake: Float, phase: Float) {
        this.dx = dx
        this.rot = rot
        this.tc = tc
        this.tilt = tilt
        this.roll = roll
        this.shake = shake
        this.phase = phase
    }
}

/** What one frame shows; reused, written by the frame loop and read by the draw. */
internal class BobaScene {
    val cups = arrayOf(BobaCup(), BobaCup())
    var count = 0
    var percent = 0f
    /** Step shown, the previous one and the cross-fade between them. */
    var step = 0
    var prevStep = 0
    var stepFade = 1f
    /** Progress value cross-fade during the swap: value before, value after, fade. */
    var pctFrom = 0f
    var pctTo = 0f
    var pctFade = 1f
    /** The ambient time for waves and the stream. */
    var clock = 0f
}

private val CHANGES = floatArrayOf(5.0f, 6.4f, 7.0f, 8.9f)

internal fun bobaLoopScene(g: BobaGeo, t: Float, ambient: Float, sc: BobaScene) {
    sc.count = 0
    val s = t - 5.0f
    val rot = bobaShakeRot(s)
    val lagRot = bobaShakeRot(s - 0.07f)
    if (t < 9.0f) {
        val tc = min(t, 8.2f)
        sc.cups[sc.count++].set(-g.w * 0.8f * bobaEaseInOutCubic(bobaSeg(t, 8.2f, 9.0f)), rot, tc, -rot + 1.5f * lagRot, 0f, bobaShakeEnv(s), s)
    }
    if (t > 8.85f) sc.cups[sc.count++].set(g.w * 0.8f * (1 - bobaEaseOutBack(bobaSeg(t, 8.85f, 9.6f))), 0f, 0f, 0f, 0f, 0f, 0f)
    val swapped = t >= 8.9f
    val tcLabel = if (swapped) 0f else min(t, 8.2f)
    var last = -10f
    for (c in CHANGES) if (t >= c) last = c
    sc.step = bobaStepOf(tcLabel)
    sc.prevStep = when (last) {
        8.9f -> 3
        5.0f -> 0
        6.4f -> 1
        7.0f -> 2
        else -> 0
    }
    sc.percent = bobaPercent(tcLabel)
    sc.stepFade = if (last < 0) 1f else bobaClamp01((t - last) / 0.3f)
    sc.pctFrom = 100f
    sc.pctTo = 0f
    sc.pctFade = bobaEaseInOutCubic(bobaSeg(t, 8.8f, 9.1f))
    sc.clock = ambient
}
