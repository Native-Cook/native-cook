package com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardFrame
import kotlin.math.*

/** 12 × 12 textured meshes, preserving yaw depth, directional squash, press and tail bending. */
internal class PinataSmashRewardMesh {
    class PinataSmashRewardGrid(u0: Double, v0: Double, u1: Double, v1: Double) {
        val uv = FloatArray(13 * 13 * 2)
        val art = DoubleArray(uv.size)
        val positions = FloatArray(uv.size)
        val colors = IntArray(13 * 13)
        val indices = ShortArray(12 * 12 * 6)
        init {
            val du = (u1 - u0) / 12; val dv = (v1 - v0) / 12
            for (j in 0..12) for (i in 0..12) { val p = (j * 13 + i) * 2; art[p] = u0 + i * du; art[p + 1] = v0 + j * dv; uv[p] = art[p].toFloat(); uv[p + 1] = art[p + 1].toFloat() }
            for (j in 0 until 12) for (i in 0 until 12) { val a = j * 13 + i; val p = (j * 12 + i) * 6; val ids = intArrayOf(a, a + 1, a + 14, a, a + 14, a + 13); for (q in 0..5) indices[p + q] = ids[q].toShort() }
        }
    }
    class PinataSmashRewardPlaced { var x = 0.0; var y = 0.0; var shade = 1.0 }
    private class PinataSmashRewardScratch {
        var bitmap: Bitmap? = null
        var canvas = Canvas()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        fun sized(w: Int, h: Int): Bitmap {
            val old = bitmap
            if (old != null && old.width >= w && old.height >= h) { old.eraseColor(Color.TRANSPARENT); return old }
            val b = Bitmap.createBitmap(max(w, old?.width ?: 0), max(h, old?.height ?: 0), Bitmap.Config.ARGB_8888)
            bitmap = b; canvas = Canvas(b); return b
        }
    }
    private val scratch = mutableListOf<PinataSmashRewardScratch>()
    private var cursor = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val placed = PinataSmashRewardPlaced()
    fun beginFrame() { cursor = 0 }
    fun place(res: PinataSmashRewardResources, f: PinataSmashRewardFrame, u: Double, v: Double, out: PinataSmashRewardPlaced) {
        val x = res.ox + u * res.k; val y = res.oy + v * res.k
        var dx = x - res.cx; var dy = y - res.cy
        val nx = dx / res.rx; val ny = dy / res.ry; val dd = 1 - nx * nx - ny * ny
        val depth = if (dd > 0) 26 * sqrt(dd) else 0.0
        out.shade = 1 - 0.22 * sin(f.yaw) * nx.coerceIn(-1.0, 1.0)
        dx = dx * cos(f.yaw) + depth * sin(f.yaw)
        if (f.sq != 0.0) {
            val c = cos(f.sqAng); val s = sin(f.sqAng); val a = dx * c + dy * s; val b = -dx * s + dy * c
            val a2 = a * (1 - f.sq); val b2 = b * (1 + f.sq * 0.5); dx = a2 * c - b2 * s; dy = a2 * s + b2 * c
        }
        dx *= 1 - 0.03 * f.press; dy *= 1 - 0.03 * f.press
        val rx = res.cx + dx - (res.ox + res.attach.x * res.k); val ry = res.cy + dy - (res.oy + res.attach.y * res.k)
        out.x = f.ax + rx * cos(f.roll) - ry * sin(f.roll); out.y = f.ay + rx * sin(f.roll) + ry * cos(f.roll)
    }
    fun vertices(res: PinataSmashRewardResources, f: PinataSmashRewardFrame, grid: PinataSmashRewardGrid, tail: Boolean) {
        for (i in grid.colors.indices) {
            var u = grid.art[i * 2]; var v = grid.art[i * 2 + 1]
            if (tail && f.tail != 0.0) {
                val dx = u - res.tailRoot.x; val dy = v - res.tailRoot.y
                val a = f.tail * min(1.0, hypot(dx, dy) / 300); val c = cos(a); val s = sin(a)
                u = res.tailRoot.x + dx * c - dy * s; v = res.tailRoot.y + dx * s + dy * c
            }
            place(res, f, u, v, placed)
            grid.positions[i * 2] = placed.x.toFloat(); grid.positions[i * 2 + 1] = placed.y.toFloat()
            val g = (placed.shade.coerceIn(0.6, 1.0) * 255).roundToInt()
            grid.colors[i] = Color.rgb(g, g, g)
        }
    }
    fun draw(canvas: Canvas, grid: PinataSmashRewardGrid, shader: Shader, alpha: Double, density: Float) {
        if (alpha <= 0.002) return
        paint.shader = shader; paint.alpha = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt()
        if (Build.VERSION.SDK_INT >= 29 || !canvas.isHardwareAccelerated) {
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES, grid.positions.size, grid.positions, 0, grid.uv, 0, grid.colors, 0, grid.indices, 0, grid.indices.size, paint)
        } else {
            // Android 8/9 ignore hardware drawVertices; use the same raster twin as Booster Pack.
            val xs = grid.positions.indices.filter { it % 2 == 0 }; val ys = grid.positions.indices.filter { it % 2 == 1 }
            val left = floor(xs.minOf { grid.positions[it] } - 1); val top = floor(ys.minOf { grid.positions[it] } - 1)
            val right = ceil(xs.maxOf { grid.positions[it] } + 1); val bottom = ceil(ys.maxOf { grid.positions[it] } + 1)
            val w = ceil((right - left) * density).toInt(); val h = ceil((bottom - top) * density).toInt()
            if (w <= 0 || h <= 0 || w > 4096 || h > 4096) return
            if (cursor == scratch.size) scratch += PinataSmashRewardScratch()
            val s = scratch[cursor++]; val bitmap = s.sized(w, h); val alpha255 = paint.alpha
            paint.alpha = 255
            s.canvas.save(); s.canvas.scale(density, density); s.canvas.translate(-left, -top)
            s.canvas.drawVertices(Canvas.VertexMode.TRIANGLES, grid.positions.size, grid.positions, 0, grid.uv, 0, grid.colors, 0, grid.indices, 0, grid.indices.size, paint)
            s.canvas.restore(); paint.alpha = alpha255; s.paint.alpha = alpha255
            canvas.drawBitmap(bitmap, null, RectF(left, top, left + bitmap.width / density, top + bitmap.height / density), s.paint)
        }
    }
}
