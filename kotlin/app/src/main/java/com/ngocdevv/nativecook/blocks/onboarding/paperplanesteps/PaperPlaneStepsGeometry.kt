package com.ngocdevv.nativecook.blocks.onboarding.paperplanesteps

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.tan

/*
 Paper Plane Steps — the folds, worked out once per sheet size (a port of planeGeometry.ts).

 The sheet (0..SW, 0..SH) is folded four times, each a flat fold along a line:
   1. the top-left corner down to the centre line (line from (SW/2, 0) to (0, SW/2));
   2. the top-right corner, mirrored;
   3. the new left edge onto the centre line (line from the nose at 22.5° to the vertical);
   4. the same on the right.
 For each fold every face of the paper so far is clipped by the line (Sutherland–Hodgman, carrying
 its texture point along): the part on the fold's far side stays, the part on the near side is the
 flap that turns over. After the fold the flap is reflected across the line, shows its other side,
 and lands on top of everything with its layers in reverse order (a stack turned over).
*/

internal class PlaneFace(val pts: FloatArray, val uv: FloatArray, val back: Int, val layer: Int)

internal class PlaneFold(val ax: Float, val ay: Float, val bx: Float, val by: Float, val statics: List<PlaneFace>, val movers: List<PlaneFace>)

internal class PlanePlan(val sw: Float, val sh: Float, val folds: List<PlaneFold>, val final: List<PlaneFace>)

private fun sideOf(ax: Float, ay: Float, bx: Float, by: Float, x: Float, y: Float) = (bx - ax) * (y - ay) - (by - ay) * (x - ax)

/** Keeps the part of a face where sign · side ≥ 0, or null when nothing (or a sliver) is left. */
private fun clip(f: PlaneFace, ax: Float, ay: Float, bx: Float, by: Float, sign: Float): PlaneFace? {
    val pts = ArrayList<Float>()
    val uv = ArrayList<Float>()
    val n = f.pts.size / 2
    for (i in 0 until n) {
        val j = (i + 1) % n
        val px = f.pts[i * 2]
        val py = f.pts[i * 2 + 1]
        val qx = f.pts[j * 2]
        val qy = f.pts[j * 2 + 1]
        val sp = sign * sideOf(ax, ay, bx, by, px, py)
        val sq = sign * sideOf(ax, ay, bx, by, qx, qy)
        if (sp >= 0f) {
            pts += px; pts += py
            uv += f.uv[i * 2]; uv += f.uv[i * 2 + 1]
        }
        if ((sp >= 0f) != (sq >= 0f)) {
            val t = sp / (sp - sq)
            pts += px + (qx - px) * t; pts += py + (qy - py) * t
            uv += f.uv[i * 2] + (f.uv[j * 2] - f.uv[i * 2]) * t
            uv += f.uv[i * 2 + 1] + (f.uv[j * 2 + 1] - f.uv[i * 2 + 1]) * t
        }
    }
    if (pts.size < 6) return null
    // Drop slivers thinner than half a point.
    var area = 0f
    val m = pts.size / 2
    for (i in 0 until m) {
        val j = (i + 1) % m
        area += pts[i * 2] * pts[j * 2 + 1] - pts[j * 2] * pts[i * 2 + 1]
    }
    if (abs(area) < 1f) return null
    return PlaneFace(pts.toFloatArray(), uv.toFloatArray(), f.back, f.layer)
}

private fun reflect(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float, out: FloatArray, at: Int) {
    val dx = bx - ax
    val dy = by - ay
    val l = hypot(dx, dy)
    val ux = dx / l
    val uy = dy / l
    val d = (x - ax) * ux + (y - ay) * uy
    val fx = ax + ux * d
    val fy = ay + uy * d
    out[at] = 2 * fx - x
    out[at + 1] = 2 * fy - y
}

internal fun makePlanePlan(sw: Float, sh: Float): PlanePlan {
    val t = tan(22.5 * PI / 180).toFloat()
    // ax, ay, bx, by, and a point on the flap's side
    val lines = listOf(
        floatArrayOf(sw / 2, 0f, 0f, sw / 2, 1f, 1f),
        floatArrayOf(sw / 2, 0f, sw, sw / 2, sw - 1, 1f),
        floatArrayOf(sw / 2, 0f, 0f, sw / 2 / t, 2f, sw * 0.6f),
        floatArrayOf(sw / 2, 0f, sw, sw / 2 / t, sw - 2, sw * 0.6f),
    )
    var faces = listOf(PlaneFace(floatArrayOf(0f, 0f, sw, 0f, sw, sh, 0f, sh), floatArrayOf(0f, 0f, sw, 0f, sw, sh, 0f, sh), 0, 0))
    val folds = ArrayList<PlaneFold>()
    for (l in lines) {
        val (ax, ay, bx, by) = l
        val flap = sign(sideOf(ax, ay, bx, by, l[4], l[5]))
        val statics = ArrayList<PlaneFace>()
        val movers = ArrayList<PlaneFace>()
        for (f in faces) {
            clip(f, ax, ay, bx, by, -flap)?.let { statics += it }
            clip(f, ax, ay, bx, by, flap)?.let { movers += it }
        }
        var top = 0
        for (f in statics) top = max(top, f.layer)
        var moverTop = 0
        for (f in movers) moverTop = max(moverTop, f.layer)
        val moved = movers.map { f ->
            val pts = FloatArray(f.pts.size)
            for (i in 0 until f.pts.size step 2) reflect(f.pts[i], f.pts[i + 1], ax, ay, bx, by, pts, i)
            PlaneFace(pts, f.uv.copyOf(), 1 - f.back, top + 1 + (moverTop - f.layer))
        }
        folds += PlaneFold(ax, ay, bx, by, statics.sortedBy { it.layer }, movers.sortedBy { it.layer })
        faces = (statics + moved).sortedBy { it.layer }
    }
    return PlanePlan(sw, sh, folds, faces)
}
