package com.ngocdevv.nativecook.blocks.onboarding.paperplanesteps

import android.graphics.Path
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 Paper Plane Steps — the autoPlay script and the per-frame mesh (a port of planeMotion.ts).

 LOOP = 10 s:
   0.0 – 1.6   rest on sheet 1.
   1.6 – 3.4   sheet 1 folds into a plane: four folds of 0.45 s (ease-in-out), each flap turning
               about its crease in 3D with perspective, its shading following its angle.
   3.25 – 3.5  the plane tucks its wings (width × 0.62) and lifts (scale 1 → 0.86).
   3.4 – 4.6   it flies off along a curve (a small dip, then up and out at the top right), banking
               into the turn, shrinking with distance, a dashed trail behind it.
   4.4 – 5.2   sheet 2 slides up from below (ease-out back, 3 % overshoot, 4° → 0° tilt).
   5.2 – 6.4   hold.
   6.4 – 7.6   sheet 2 folds (four folds of 0.3 s); 7.45 – 7.7 tuck; 7.6 – 8.6 flight.
   8.6 – 9.4   sheet 1 slides back up, so the loop rests where it began; 9.4 – 10 rest.

 State, as flat numbers: [sheet, fold 0..4, tuck 0..1, flight 0..1 (−1 none), incoming sheet,
 incoming progress 0..1 (−1 none), dot position].
*/

internal const val LOOP = 10f
private const val PERSPECTIVE = 800f
internal const val PLANE_IDLE_BEFORE_RETURN = 2f
internal const val PLANE_STEPS = 3

internal fun pClamp(v: Float, lo: Float, hi: Float) = min(hi, max(lo, v))

internal fun pSeg(t: Float, a: Float, b: Float) = pClamp((t - a) / (b - a), 0f, 1f)

internal fun pEaseInOut(x: Float): Float {
    val c = pClamp(x, 0f, 1f)
    return if (c < 0.5f) 4 * c * c * c else 1 - (-2 * c + 2).pow(3) / 2
}

internal fun pEaseOutBack(x: Float): Float {
    val c = pClamp(x, 0f, 1f)
    val s = 0.9f
    return 1 + (s + 1) * (c - 1).pow(3) + s * (c - 1).pow(2)
}

private fun folds(t: Float, start: Float, each: Float) = pClamp((t - start) / each, 0f, 4f)

internal fun planeScript(t: Float, out: FloatArray) {
    val dot = pEaseInOut(pSeg(t, 4.5f, 4.9f)) - pEaseInOut(pSeg(t, 8.6f, 9.0f))
    fun set(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {
        out[0] = a; out[1] = b; out[2] = c; out[3] = d; out[4] = e; out[5] = f; out[6] = dot
    }
    when {
        t < 4.4f -> set(0f, folds(t, 1.6f, 0.45f), pEaseInOut(pSeg(t, 3.25f, 3.5f)), if (t < 3.4f) -1f else pSeg(t, 3.4f, 4.6f), 1f, -1f)
        t < 5.2f -> set(0f, 4f, 1f, pSeg(t, 3.4f, 4.6f), 1f, pSeg(t, 4.4f, 5.2f))
        t < 8.6f -> set(1f, folds(t, 6.4f, 0.3f), pEaseInOut(pSeg(t, 7.45f, 7.7f)), if (t < 7.6f) -1f else pSeg(t, 7.6f, 8.6f), 0f, -1f)
        t < 9.4f -> set(1f, 4f, 1f, 1f, 0f, pSeg(t, 8.6f, 9.4f))
        else -> set(0f, 0f, 0f, -1f, 1f, -1f)
    }
}

/** The flight path from the sheet's centre (sx, sy) into [out]: x, y, heading (rad, 0 = nose up), scale, bank. */
internal fun planeFlight(f: Float, sx: Float, sy: Float, w: Float, out: FloatArray) {
    val cf = pClamp(f, 0f, 1f)
    val u = pEaseInOut(cf * 0.92f + 0.08f * cf * cf)
    val p0x = sx
    val p0y = sy
    val p1x = sx - 70
    val p1y = sy + 80
    val p2x = sx + w * 0.6f
    val p2y = sy - 120
    val p3x = w + 140
    val p3y = -220f
    val a = 1 - u
    out[0] = a * a * a * p0x + 3 * a * a * u * p1x + 3 * a * u * u * p2x + u * u * u * p3x
    out[1] = a * a * a * p0y + 3 * a * a * u * p1y + 3 * a * u * u * p2y + u * u * u * p3y
    val dx = 3 * a * a * (p1x - p0x) + 6 * a * u * (p2x - p1x) + 3 * u * u * (p3x - p2x)
    val dy = 3 * a * a * (p1y - p0y) + 6 * a * u * (p2y - p1y) + 3 * u * u * (p3y - p2y)
    // Nose along the path once it moves; it starts pointing up the screen.
    val heading = if (u < 0.001f) 0f else atan2(dy, dx) + PI.toFloat() / 2
    out[2] = heading * pClamp(u * 6, 0f, 1f)
    out[3] = 0.86f - 0.62f * u
    out[4] = 0.55f * sin(PI.toFloat() * u)
}

/** Where the sheet sits on screen this frame. */
internal class PlaneXf {
    var ox = 0f
    var oy = 0f
    var cx = 0f
    var cy = 0f
    var sx = 1f
    var sy = 1f
    var rot = 0f
    var scale = 1f
    var bank = 0f

    fun set(ox: Float, oy: Float, cx: Float, cy: Float, sx: Float, rot: Float, scale: Float, bank: Float) {
        this.ox = ox; this.oy = oy; this.cx = cx; this.cy = cy; this.sx = sx; this.sy = 1f
        this.rot = rot; this.scale = scale; this.bank = bank
    }
}

/** A growable float buffer, reused every frame. */
internal class PlaneBuf {
    var data = FloatArray(256)
    var size = 0

    fun clear() {
        size = 0
    }

    fun add(a: Float, b: Float) {
        if (size + 2 > data.size) data = data.copyOf(data.size * 2)
        data[size] = a
        data[size + 1] = b
        size += 2
    }
}

/**
 * The paper this frame: triangles with texture points (in atlas dp; the image is drawn as is) for
 * the faces lying flat and the flap turning over, plus outlines for the overlays and the shadow.
 */
internal class PlaneMesh {
    val sv = PlaneBuf()
    val st = PlaneBuf()
    val mv = PlaneBuf()
    val mt = PlaneBuf()
    /** The flap's outline and every face's, on screen. */
    val mpoly = Path()
    val all = Path()
    /** Outlines flattened onto the table, for the shadow. */
    val sh = Path()
    /** The flap's brightness (1 = untouched). */
    var flap = 1f
    var empty = true

    fun clear() {
        sv.clear(); st.clear(); mv.clear(); mt.clear()
        mpoly.rewind(); all.rewind(); sh.rewind()
        flap = 1f
        empty = true
    }
}

private val scratch = FloatArray(2)

/** A sheet point (with height z toward the viewer, negative) on the screen, into [out]. */
internal fun planeScreenOf(xf: PlaneXf, x: Float, y: Float, z: Float, out: FloatArray) {
    val lx = (x - xf.cx) * xf.sx
    val ly = (y - xf.cy) * xf.sy
    val s = PERSPECTIVE / (PERSPECTIVE + z)
    val px = lx * s * xf.scale
    val py = ly * s * xf.scale
    val ca = cos(xf.rot)
    val sa = sin(xf.rot)
    out[0] = xf.ox + px * ca - py * sa
    out[1] = xf.oy + px * sa + py * ca
}

private val faceX = FloatArray(32)
private val faceY = FloatArray(32)

private fun addFace(o: PlaneMesh, xf: PlaneXf, f: PlaneFace, sw: Float, step: Int, moving: Boolean, line: FloatArray?, theta: Float, flipped: Boolean) {
    val n = f.pts.size / 2
    o.empty = false
    for (i in 0 until n) {
        var x = f.pts[i * 2]
        var y = f.pts[i * 2 + 1]
        var z = 0f
        if (line != null) {
            // Turn the point about the crease by theta: its offset across the line shrinks by
            // cos θ and rises toward the viewer by sin θ.
            val ux = line[2] - line[0]
            val uy = line[3] - line[1]
            val l = hypot(ux, uy)
            val dx = ux / l
            val dy = uy / l
            val along = (x - line[0]) * dx + (y - line[1]) * dy
            val fx = line[0] + dx * along
            val fy = line[1] + dy * along
            val px = x - fx
            val py = y - fy
            val c = cos(theta)
            x = fx + px * c
            y = fy + py * c
            z = -hypot(px, py) * sin(theta)
        }
        planeScreenOf(xf, x, y, z, scratch)
        faceX[i] = scratch[0]
        faceY[i] = scratch[1]
        if (i == 0) o.all.moveTo(scratch[0], scratch[1]) else o.all.lineTo(scratch[0], scratch[1])
        if (moving) {
            if (i == 0) o.mpoly.moveTo(scratch[0], scratch[1]) else o.mpoly.lineTo(scratch[0], scratch[1])
        }
        // The shadow falls on the table: the outline flattened, the lift kept as an offset.
        planeScreenOf(xf, x, y, 0f, scratch)
        val shx = scratch[0] - z * 0.05f
        val shy = scratch[1] - z * 0.09f
        if (i == 0) o.sh.moveTo(shx, shy) else o.sh.lineTo(shx, shy)
    }
    o.all.close()
    if (moving) o.mpoly.close()
    o.sh.close()
    val back = (f.back == 1) != flipped
    val vs = if (moving) o.mv else o.sv
    val ts = if (moving) o.mt else o.st
    val ox = if (back) 3 * sw else step * sw
    for (i in 1 until n - 1) {
        for (k in intArrayOf(0, i, i + 1)) {
            vs.add(faceX[k], faceY[k])
            ts.add(ox + f.uv[k * 2], f.uv[k * 2 + 1])
        }
    }
}

/**
 * The paper this frame into [o]: static faces, then the turning flap (its layers reversed once it
 * passes upright). [fold] is 0..4. The flap's brightness follows its angle, with a glint as it
 * catches the light on the way over.
 */
internal fun planePaperMesh(o: PlaneMesh, plan: PlanePlan, final: List<PlaneFace>, fold: Float, step: Int, xf: PlaneXf) {
    o.clear()
    if (fold >= 4f) {
        for (f in final) addFace(o, xf, f, plan.sw, step, false, null, 0f, false)
        return
    }
    val k = floor(fold).toInt()
    val fd = plan.folds[k]
    val theta = PI.toFloat() * pEaseInOut(fold - k)
    for (f in fd.statics) addFace(o, xf, f, plan.sw, step, false, null, 0f, false)
    val flipped = theta > PI.toFloat() / 2
    val gq = (theta - 1.05f) / 0.28f
    val glint = 0.12f * exp(-gq * gq)
    o.flap = (if (flipped) 0.8f - 0.14f * cos(theta) else 0.8f + 0.2f * cos(theta)) + glint
    val line = floatArrayOf(fd.ax, fd.ay, fd.bx, fd.by)
    val n = fd.movers.size
    for (i in 0 until n) {
        val f = fd.movers[if (flipped) n - 1 - i else i]
        addFace(o, xf, f, plan.sw, step, true, line, theta, flipped)
    }
}

/** The tint laid over a face for a brightness b (1 = untouched), as ARGB: cool shadow below 1, light above. */
internal fun planeTint(b: Float): Int {
    val (a, r, g, bl) = if (b >= 1f) listOf(pClamp(b - 1, 0f, 0.3f), 255f, 250f, 236f) else listOf(pClamp(1 - b, 0f, 0.7f), 22f, 20f, 38f)
    return android.graphics.Color.argb((a * 255).toInt(), r.toInt(), g.toInt(), bl.toInt())
}
