package com.ngocdevv.nativecook.blocks.cards.foldedmapcard

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 Folded Map Card — the fold geometry and the autoPlay script (a port of foldedMapMotion.ts).

 The map is three panels side by side, folded as an accordion. Panel 0 lies flat; panel 1 turns
 about its left edge by φ1 (0 flat → FOLD_MAX folded back over panel 0, toward the viewer);
 panel 2 turns about panel 1's far edge by −φ2, so folded it lies flat on top, face up. Points are
 placed in 3D (x across, z toward the viewer is negative) and projected with a perspective of
 PERSPECTIVE dp about the card's centre. Each panel is a plane, so its image is mapped with one
 exact perspective matrix (the React Native version uses six textured strips).

 LOOP = 8 s:
   0.0 – 1.4   rest: folded, the cover info beside it.
   1.4 – 3.2   unfold: panel 1 opens (ease-out back, 3 % overshoot), panel 2 follows 80 ms later.
   3.2 – 4.4   the route draws on, the summit pin drops (4.0 – 4.4) and the profile rises (3.4 – 4.2).
   4.4 – 6.0   hold.
   6.0 – 7.6   the route, pin and profile go (6.0 – 6.3), then refold (6.1 – 7.6).
   7.6 – 8.0   rest.
*/

internal const val LOOP = 8f
internal const val FOLDED_MAP_PERSPECTIVE = 900f
/** Folded angle: a little short of flat so the stacked panels keep their paper thickness. */
internal const val FOLDED_MAP_FOLD_MAX = (PI - 0.05).toFloat()
internal const val FOLDED_MAP_IDLE_BEFORE_RETURN = 2f
internal const val FOLDED_MAP_RETURN_FOR = 0.8f

internal class FoldedMapCardGeometry(val x: Float, val y: Float, val w: Float, val h: Float, val pw: Float)

internal fun fmClamp(v: Float, lo: Float, hi: Float) = min(hi, max(lo, v))

private fun seg(t: Float, a: Float, b: Float) = fmClamp((t - a) / (b - a), 0f, 1f)

internal fun fmEaseInOut(x: Float): Float {
    val c = fmClamp(x, 0f, 1f)
    return if (c < 0.5f) 4 * c * c * c else 1 - (-2 * c + 2).pow(3) / 2
}

internal fun fmEaseOut(x: Float): Float = 1 - (1 - fmClamp(x, 0f, 1f)).pow(3)

/** Ease-out with a 3 % overshoot (back curve, s = 0.9). */
private fun easeOutBack(x: Float): Float {
    val c = fmClamp(x, 0f, 1f)
    val s = 0.9f
    return 1 + (s + 1) * (c - 1).pow(3) + s * (c - 1).pow(2)
}

/** The loop into [out]: panel 1 unfold, panel 2 unfold, route drawn, pin, profile rise. */
internal fun foldedMapScript(t: Float, out: FloatArray) {
    val close1 = fmEaseInOut(seg(t, 6.16f, 7.6f))
    val close2 = fmEaseInOut(seg(t, 6.1f, 7.54f))
    out[0] = if (t < 6f) (if (t < 1.4f) 0f else easeOutBack(seg(t, 1.4f, 3.1f))) else 1 - close1
    out[1] = if (t < 6f) (if (t < 1.48f) 0f else easeOutBack(seg(t, 1.48f, 3.2f))) else 1 - close2
    val gone = 1 - fmEaseOut(seg(t, 6.0f, 6.3f))
    out[2] = fmEaseInOut(seg(t, 3.2f, 4.2f)) * gone
    out[3] = seg(t, 4.0f, 4.4f) * gone
    out[4] = seg(t, 3.4f, 4.2f) * gone
}

/** The pin's drop: from 26 dp above, landing with a small bounce. */
internal fun foldedMapPinDrop(p: Float): Float {
    if (p <= 0f) return -1f
    val x = fmClamp(p, 0f, 1f)
    if (x < 0.55f) {
        val f = x / 0.55f
        return 26 * (1 - f * f)
    }
    val b = (x - 0.55f) / 0.45f
    return 5 * sin(PI.toFloat() * b) * (1 - b)
}

/** Panels' frames for an unfold pair into [out]: [origin x, origin z, direction angle] × 3. */
internal fun foldedMapPanels(card: FoldedMapCardGeometry, u1: Float, u2: Float, out: FloatArray) {
    val f1 = FOLDED_MAP_FOLD_MAX * (1 - u1)
    val f2 = FOLDED_MAP_FOLD_MAX * (1 - u2)
    val psi1 = f1
    val psi2 = f1 - f2
    val ox1 = card.pw
    out[0] = 0f; out[1] = 0f; out[2] = 0f
    out[3] = ox1; out[4] = 0f; out[5] = psi1
    out[6] = ox1 + card.pw * cos(psi1); out[7] = -card.pw * sin(psi1); out[8] = psi2
}

/** A point on the flat map (x across, y down) through the folds, projected, into [out] (x, y). */
internal fun foldedMapProject(card: FoldedMapCardGeometry, frames: FloatArray, mx: Float, my: Float, out: FloatArray, at: Int) {
    val k = fmClamp(floor(mx / card.pw), 0f, 2f).toInt()
    val lx = mx - k * card.pw
    val X = frames[k * 3] + lx * cos(frames[k * 3 + 2])
    val Z = frames[k * 3 + 1] - lx * sin(frames[k * 3 + 2])
    val cx = card.w / 2
    val cy = card.h / 2
    val s = FOLDED_MAP_PERSPECTIVE / (FOLDED_MAP_PERSPECTIVE + Z)
    out[at] = card.x + cx + (X - cx) * s
    out[at + 1] = card.y + cy + (my - cy) * s
}

/** Brightness of a panel facing along psi (front or back), flat front = 1. */
internal fun foldedMapShade(psi: Float, front: Boolean): Float {
    var nx = -sin(psi)
    var nz = -cos(psi)
    if (!front) {
        nx = -nx
        nz = -nz
    }
    // Toward the light: upper left, in front.
    val d = max(0f, nx * -0.5f + nz * -0.866f)
    return fmClamp((0.55f + 0.45f * d) / (0.55f + 0.45f * 0.866f), 0.35f, 1.08f)
}

/** The tint laid over a panel for a brightness b (1 = untouched), as ARGB: a warm shadow below 1, light above. */
internal fun foldedMapTint(b: Float): Int {
    return if (b >= 1f) {
        argb(fmClamp(b - 1, 0f, 0.3f), 255, 250, 235)
    } else {
        argb(fmClamp(1 - b, 0f, 0.75f), 26, 18, 8)
    }
}

private fun argb(a: Float, r: Int, g: Int, b: Int): Int =
    ((a * 255f + 0.5f).toInt().coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b

/** How dark a crease gets as it closes: up to 26 %. */
internal fun foldedMapCrease(fold: Float) = 0.26f * abs(sin(fold / 2))
