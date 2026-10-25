package com.ngocdevv.nativecook.blocks.pickers.paintmixerpicker

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Paint Mixer Picker — layout, the paint model and the autoPlay script (a port of
 paintMixerMotion.ts).

 Paint is mixed subtractively, as a weighted average of absorbance (−ln of each sRGB channel): a
 base of white with weight 1 and each drop with weight K × amount. The bowl's shader uses the same
 weights per point (each drop is a blob that a swirl winds into a spiral and stirring spreads), and
 `paintMixColor` uses them with every blob spread evenly: the colour the paint will be once mixed.

 autoPlay timeline (LOOP = 9 s):
   0.0 – 1.4   rest: a bowl of white paint, five pots around it.
   1.4 – 1.6   the yellow pot lifts (scale 1 → 1.06, shadow grows).
   1.5 – 2.0   a yellow drop flies to the bowl (ease-in-out, arcing towards the camera), lands at
               2.0: ripple, splash, the paint swirls (1.6π, ease-out cubic over 1.4 s).
   2.6 – 2.8   the teal pot lifts; 2.75 – 3.25 its drop flies; lands at 3.25, swirl 1.2π over 1.15 s.
   3.9 – 5.4   a stirring stick comes in, circles twice (4.0 – 5.2, ease-in-out sine), leaves;
               the paint winds 3.2π and mixes to green.
   5.3 – 6.6   hold: "Morning Fern" and its code; the wall card behind is that colour.
   6.6 – 8.3   white paint washes back in from the centre in a reverse spiral; the drops fade.
   8.3 – 9.0   rest.
 */

/** The autoPlay loop in seconds (the manifest's recording.loop). */
const val LOOP = 9f
/** Weight of one full drop against the white base. */
internal const val PAINT_K = 1.2f
private const val PIF = PI.toFloat()
/** Swirl (radians of winding) at which the paint is about 63 % mixed. */
private const val MIX_TURN = 3.4f * PIF

internal class PaintPot(val name: String, val color: Int) {
    val rgb = floatArrayOf(((color shr 16) and 0xFF) / 255f, ((color shr 8) and 0xFF) / 255f, (color and 0xFF) / 255f)
}

internal val PAINT_POTS = arrayOf(
    PaintPot("Teal", 0xFF24879A.toInt()),
    PaintPot("Magenta", 0xFFB8336A.toInt()),
    PaintPot("Yellow", 0xFFE6B23C.toInt()),
    PaintPot("White", 0xFFF7F4EE.toInt()),
    PaintPot("Umber", 0xFF6B4A33.toInt()),
)
internal const val PAINT_BASE = 0xFFF2EEE6.toInt()
/** Pot angles around the bowl (degrees, 0 = right, y down). */
private val POT_ANGLES = floatArrayOf(200f, 152f, 90f, 28f, -20f)
internal const val PAINT_YELLOW = 2
internal const val PAINT_TEAL = 0

private fun rgbOf(color: Int) = floatArrayOf(((color shr 16) and 0xFF) / 255f, ((color shr 8) and 0xFF) / 255f, (color and 0xFF) / 255f)

internal fun paintAbsorb(rgb: FloatArray) = floatArrayOf(-ln(max(rgb[0], 0.02f)), -ln(max(rgb[1], 0.02f)), -ln(max(rgb[2], 0.02f)))

internal val PAINT_BASE_RGB = rgbOf(PAINT_BASE)
internal val PAINT_BASE_A = paintAbsorb(PAINT_BASE_RGB)
internal val PAINT_POT_A = Array(PAINT_POTS.size) { paintAbsorb(PAINT_POTS[it].rgb) }

/** Names for colours the bowl can make; the nearest one is shown. */
private val NAMES = arrayOf(
    "Chalk White" to 0xFFF2EEE6.toInt(),
    "Lemon Sorbet" to 0xFFEED9A2.toInt(),
    "Saffron" to 0xFFE3B04A.toInt(),
    "Pale Lagoon" to 0xFFA9CDD2.toInt(),
    "Deep Teal" to 0xFF3E8B95.toInt(),
    "Sea Glass" to 0xFF94C3B5.toInt(),
    "Morning Fern" to 0xFF7AAE7C.toInt(),
    "Moss" to 0xFF6F8A55.toInt(),
    "Olive Grove" to 0xFF8E8A4E.toInt(),
    "Rose Quartz" to 0xFFE5B4C2.toInt(),
    "Raspberry" to 0xFFB54A72.toInt(),
    "Terracotta" to 0xFFC0704F.toInt(),
    "Dusk Plum" to 0xFF6E4A73.toInt(),
    "Driftwood" to 0xFFA8927C.toInt(),
    "Cocoa" to 0xFF7A5A44.toInt(),
)
internal val PAINT_NAMES = Array(NAMES.size) { NAMES[it].first }
private val NAME_RGB = Array(NAMES.size) { rgbOf(NAMES[it].second) }

internal fun paintNearestName(rgb: FloatArray): Int {
    var best = 0
    var bestD = 1e9f
    for (i in NAME_RGB.indices) {
        val c = NAME_RGB[i]
        val d = (c[0] - rgb[0]).pow(2) * 0.9f + (c[1] - rgb[1]).pow(2) * 1.2f + (c[2] - rgb[2]).pow(2) * 0.8f
        if (d < bestD) {
            bestD = d
            best = i
        }
    }
    return best
}

internal fun paintClamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
internal fun paintSeg(t: Float, a: Float, b: Float) = paintClamp01((t - a) / (b - a))
internal fun paintEaseOutCubic(x: Float): Float { val u = 1 - x; return 1 - u * u * u }
internal fun paintEaseInOutCubic(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
internal fun paintEaseInOutSine(x: Float) = 0.5f - 0.5f * cos(PIF * x)

/** A drop in the bowl, ready for the shader: position, amount, mixed share, winding, absorbance. */
internal class PaintDrop(val x: Float, val y: Float, val amt: Float, val m: Float, val tw: Float, val a: FloatArray)
/** A drop in the air (h = height towards the camera, 0 … 1) or one held under the finger. */
internal class PaintFlight(val x: Float, val y: Float, val h: Float, val pot: Int)
internal class PaintRipple(val x: Float, val y: Float, val age: Float, val str: Float)
internal class PaintSplash(val x: Float, val y: Float, val age: Float, val pot: Int)

/** Everything one frame needs. */
internal class PaintMix(
    val baseA: FloatArray,
    val baseW: Float,
    val drops: List<PaintDrop>,
    val ripples: List<PaintRipple>,
    val splashes: List<PaintSplash>,
    val flights: List<PaintFlight>,
    /** Wash-back front radius (−1: none) and its reverse twist. */
    val washFront: Float,
    val washTwist: Float,
    /** How much of the drops' paint is left (the wash fades it). */
    val fade: Float,
    /** The stirring stick: x, y, how far in (0 … 1). */
    val stick: FloatArray,
    val lift: FloatArray,
)

/** Layout in dp. */
internal class PaintGeo(val w: Float, val h: Float, val top: Float, bottom: Float) {
    val u = min(w / 393f, (h - top - bottom) / 759f)
    val cardY = top + 112
    val cardH = 150 * u
    /** Outer rim radius and the paint's radius. */
    val rim = 104 * u
    val paint = rim * 0.8f
    val potR = 25 * u
    val potRing = rim + 54 * u
    val cx = w / 2
    val cy = cardY + cardH + 112 * u
    val panelY = cy + potRing + potR + 30 * u
    val buttonY = h - bottom - 16 - 52
}

internal fun paintPotCenter(g: PaintGeo, i: Int, out: FloatArray) {
    val a = POT_ANGLES[i] * PIF / 180
    out[0] = g.cx + cos(a) * g.potRing
    out[1] = g.cy + sin(a) * g.potRing
}

/** The colour the bowl mixes to (sRGB 0 … 1). */
internal fun paintMixColor(m: PaintMix, out: FloatArray = FloatArray(3)): FloatArray {
    var r = m.baseA[0] * m.baseW
    var gg = m.baseA[1] * m.baseW
    var b = m.baseA[2] * m.baseW
    var wsum = m.baseW
    for (d in m.drops) {
        val wgt = PAINT_K * d.amt * m.fade
        r += wgt * d.a[0]
        gg += wgt * d.a[1]
        b += wgt * d.a[2]
        wsum += wgt
    }
    out[0] = exp(-r / wsum)
    out[1] = exp(-gg / wsum)
    out[2] = exp(-b / wsum)
    return out
}

internal fun paintMixedShare(winding: Float) = min(1f, 1.04f * (1 - exp(-max(0f, winding) / MIX_TURN)))

// The script's pieces.
internal const val PAINT_LAND_Y = 2.0f
internal const val PAINT_LAND_T = 3.25f

internal fun paintWindingAt(t: Float) =
    1.6f * PIF * paintEaseOutCubic(paintSeg(t, PAINT_LAND_Y, PAINT_LAND_Y + 1.4f)) +
        1.2f * PIF * paintEaseOutCubic(paintSeg(t, PAINT_LAND_T, PAINT_LAND_T + 1.15f)) +
        3.2f * PIF * paintEaseInOutSine(paintSeg(t, 4.0f, 5.3f))

internal fun paintLanding(g: PaintGeo, which: Int, out: FloatArray) {
    if (which == PAINT_YELLOW) {
        out[0] = g.cx - 20 * g.u
        out[1] = g.cy + 18 * g.u
    } else {
        out[0] = g.cx + 22 * g.u
        out[1] = g.cy - 16 * g.u
    }
}

private val EVENTS = arrayOf(PAINT_YELLOW to PAINT_LAND_Y, PAINT_TEAL to PAINT_LAND_T)

/** The scripted bowl at loop time t (pure). */
internal fun paintAutoMix(g: PaintGeo, t: Float): PaintMix {
    val wind = paintWindingAt(t)
    val back = paintEaseInOutSine(paintSeg(t, 6.6f, 8.4f))
    val twistNow = wind * (1 - back)
    val fade = 1 - paintEaseInOutCubic(paintSeg(t, 6.6f, 8.2f))
    val drops = ArrayList<PaintDrop>(2)
    val ripples = ArrayList<PaintRipple>(3)
    val splashes = ArrayList<PaintSplash>(2)
    val flights = ArrayList<PaintFlight>(1)
    val p = FloatArray(2)
    val l = FloatArray(2)
    for ((pot, at) in EVENTS) {
        if (t >= at) {
            paintLanding(g, pot, l)
            val landWind = paintWindingAt(at)
            drops.add(PaintDrop(l[0], l[1], paintEaseOutCubic(paintSeg(t, at, at + 0.4f)), paintMixedShare(wind - landWind), twistNow - landWind, PAINT_POT_A[pot]))
            if (t < at + 1.6f) ripples.add(PaintRipple(l[0], l[1], t - at, 1f))
            if (t < at + 0.45f) splashes.add(PaintSplash(l[0], l[1], t - at, pot))
        } else if (t >= at - 0.5f) {
            val s = paintSeg(t, at - 0.5f, at)
            paintPotCenter(g, pot, p)
            paintLanding(g, pot, l)
            val e = paintEaseInOutSine(s)
            flights.add(PaintFlight(p[0] + (l[0] - p[0]) * e, p[1] + (l[1] - p[1]) * e, sin(PIF * s), pot))
        }
    }
    val lift = FloatArray(5)
    lift[PAINT_YELLOW] = sin(PIF * paintSeg(t, 1.4f, 1.75f)) * 0.6f + paintEaseOutCubic(paintSeg(t, 1.4f, 1.55f)) * (1 - paintSeg(t, 1.55f, 1.85f)) * 0.4f
    lift[PAINT_TEAL] = sin(PIF * paintSeg(t, 2.6f, 2.95f)) * 0.6f + paintEaseOutCubic(paintSeg(t, 2.6f, 2.75f)) * (1 - paintSeg(t, 2.75f, 3.05f)) * 0.4f
    // The stick: in over 3.85 – 4.1, two turns, out over 5.15 – 5.45.
    val stickA = 4 * PIF * paintEaseInOutSine(paintSeg(t, 4.0f, 5.2f)) - PIF * 0.75f
    val stickIn = paintEaseOutCubic(paintSeg(t, 3.85f, 4.1f)) * (1 - paintEaseInOutCubic(paintSeg(t, 5.15f, 5.45f)))
    val sr = g.paint * 0.46f
    val stick = floatArrayOf(g.cx + cos(stickA) * sr, g.cy + sin(stickA) * sr, stickIn)
    if (stickIn > 0.5f) ripples.add(PaintRipple(stick[0], stick[1], 0.25f, 0.35f * stickIn))
    val front = if (t > 6.6f && t < 8.4f) (g.paint + 46 * g.u) * paintEaseInOutSine(paintSeg(t, 6.6f, 8.2f)) else -1f
    return PaintMix(PAINT_BASE_A, 1f, drops, ripples.take(3), splashes, flights, front, -2.4f * PIF * back, fade, stick, lift)
}

/** The colour shown on the wall card and the label, a quarter second behind the bowl. */
internal fun paintShownColor(g: PaintGeo, t: Float, out: FloatArray = FloatArray(3)) = paintMixColor(paintAutoMix(g, max(0f, t - 0.25f)), out)

internal fun paintToHex(rgb: FloatArray): String {
    val sb = StringBuilder("#")
    for (i in 0 until 3) {
        val v = (paintClamp01(rgb[i]) * 255).roundToInt()
        if (v < 16) sb.append('0')
        sb.append(Integer.toHexString(v).uppercase())
    }
    return sb.toString()
}

/** Light reflectance value (0 … 100), as paint charts quote it. */
internal fun paintLrv(rgb: FloatArray): Int {
    fun lin(c: Float) = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    return (100 * (0.2126f * lin(rgb[0]) + 0.7152f * lin(rgb[1]) + 0.0722f * lin(rgb[2]))).roundToInt()
}

internal fun paintRgbInt(rgb: FloatArray) =
    (0xFF shl 24) or ((paintClamp01(rgb[0]) * 255).roundToInt() shl 16) or ((paintClamp01(rgb[1]) * 255).roundToInt() shl 8) or (paintClamp01(rgb[2]) * 255).roundToInt()
