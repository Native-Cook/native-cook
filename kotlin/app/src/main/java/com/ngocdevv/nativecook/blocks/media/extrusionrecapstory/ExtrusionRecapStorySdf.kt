package com.ngocdevv.nativecook.blocks.media.extrusionrecapstory

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.PathParser
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The glyph distance fields (`ers-distance.ts`, `ers-atlas.ts`): each page's glyph is rasterised to a 256-square coverage mask, two
 * separable squared Euclidean transforms give the far field and, within three texels of the outline, the distance is exact (to the
 * flattened path), so the silhouette has no stair-steps. Stored as 16 bits: the high byte as grey in the top tile row, the low byte as
 * grey below it. Computed once per glyph and kept across mounts.
 */
internal object ExtrusionRecapStorySdf {
    const val SIZE = 256
    const val PAD = 16
    /** Distance range, in texels, encoded either side of the outline. */
    const val RANGE = 96
    private const val BAND = 3
    private const val INF = 1e20

    private val fields = HashMap<String, IntArray>()

    private fun lineTransform(f: DoubleArray, out: DoubleArray, n: Int, v: IntArray, z: DoubleArray) {
        var k = 0
        v[0] = 0
        z[0] = -INF
        z[1] = INF
        for (q in 1 until n) {
            var d = (f[q] + q * q - (f[v[k]] + v[k] * v[k])) / (2.0 * q - 2.0 * v[k])
            while (d <= z[k] && k > 0) {
                k--
                d = (f[q] + q * q - (f[v[k]] + v[k] * v[k])) / (2.0 * q - 2.0 * v[k])
            }
            k++
            v[k] = q
            z[k] = d
            z[k + 1] = INF
        }
        k = 0
        for (q in 0 until n) {
            while (z[k + 1] < q) k++
            out[q] = (q - v[k]).toDouble() * (q - v[k]) + f[v[k]]
        }
    }

    private fun transform(grid: DoubleArray) {
        val n = SIZE
        val f = DoubleArray(n)
        val out = DoubleArray(n)
        val v = IntArray(n)
        val z = DoubleArray(n + 1)
        for (y in 0 until n) {
            for (x in 0 until n) f[x] = grid[y * n + x]
            lineTransform(f, out, n, v, z)
            for (x in 0 until n) grid[y * n + x] = out[x]
        }
        for (x in 0 until n) {
            for (y in 0 until n) f[y] = grid[y * n + x]
            lineTransform(f, out, n, v, z)
            for (y in 0 until n) grid[y * n + x] = out[y]
        }
    }

    /** Unsigned distance from each texel centre to the nearest segment, up to BAND (else infinity). */
    private fun exactBand(segments: FloatArray): FloatArray {
        val n = SIZE
        val out = FloatArray(n * n) { Float.POSITIVE_INFINITY }
        var s = 0
        while (s < segments.size) {
            val ax = segments[s]; val ay = segments[s + 1]; val bx = segments[s + 2]; val by = segments[s + 3]
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val x0 = max(0, floor(min(ax, bx) - BAND - 0.5f).toInt())
            val x1 = min(n - 1, ceil(max(ax, bx) + BAND - 0.5f).toInt())
            val y0 = max(0, floor(min(ay, by) - BAND - 0.5f).toInt())
            val y1 = min(n - 1, ceil(max(ay, by) + BAND - 0.5f).toInt())
            for (y in y0..y1) for (x in x0..x1) {
                val px = x + 0.5f - ax
                val py = y + 0.5f - ay
                val t = if (len2 > 0) max(0f, min(1f, (px * dx + py * dy) / len2)) else 0f
                val ex = px - t * dx
                val ey = py - t * dy
                val d = sqrt(ex * ex + ey * ey)
                val i = y * n + x
                if (d < out[i]) out[i] = d
            }
            s += 4
        }
        return out
    }

    /** [coverage]: 0..255 anti-aliased alpha of the glyph; [exact]: see [exactBand]. Returns the 16-bit field, positive outside. */
    private fun makeDistanceField(coverage: IntArray, exact: FloatArray?): IntArray {
        val count = SIZE * SIZE
        val outer = DoubleArray(count)
        val inner = DoubleArray(count)
        val out = IntArray(count)
        for (i in 0 until count) {
            val a = coverage[i] / 255.0
            if (a >= 1) { outer[i] = 0.0; inner[i] = INF }
            else if (a <= 0) { outer[i] = INF; inner[i] = 0.0 }
            else {
                val d = 0.5 - a
                outer[i] = if (d > 0) d * d else 0.0
                inner[i] = if (d < 0) d * d else 0.0
            }
        }
        transform(outer)
        transform(inner)
        for (i in 0 until count) {
            var distance = sqrt(outer[i]) - sqrt(inner[i])
            val e = if (exact != null) exact[i].toDouble() else Double.POSITIVE_INFINITY
            // exact near the outline; an interior edge of overlapping contours (far field much larger) is ignored
            if (e < BAND && abs(distance) < e + 1.5) {
                val signed = if (coverage[i] >= 128) -e else e
                val w = max(0.0, min(1.0, e - (BAND - 1)))
                distance = signed + (distance - signed) * w * w * (3 - 2 * w)
            }
            out[i] = (max(0.0, min(1.0, 0.5 + distance / (2.0 * RANGE))) * 65535).roundToInt()
        }
        return out
    }

    /** Outline of the (already mapped) path as line segments (x0, y0, x1, y1 ...), curves flattened finely. */
    private fun outlineOf(path: Path, accuracy: Float): FloatArray {
        val pts = path.approximate(accuracy)
        val out = ArrayList<Float>()
        var i = 0
        var startX = 0f
        var startY = 0f
        var prevX = 0f
        var prevY = 0f
        var prevFraction = Float.MAX_VALUE
        var have = false
        while (i + 2 < pts.size) {
            val fraction = pts[i]
            val x = pts[i + 1]
            val y = pts[i + 2]
            if (have && fraction >= prevFraction) {
                out.add(prevX); out.add(prevY); out.add(x); out.add(y)
            } else {
                if (have && (prevX != startX || prevY != startY)) { out.add(prevX); out.add(prevY); out.add(startX); out.add(startY) }
                startX = x; startY = y
            }
            prevX = x; prevY = y; prevFraction = fraction; have = true
            i += 3
        }
        if (have && (prevX != startX || prevY != startY)) { out.add(prevX); out.add(prevY); out.add(startX); out.add(startY) }
        return out.toFloatArray()
    }

    private fun fieldFor(glyph: String): IntArray? {
        synchronized(fields) { fields[glyph]?.let { return it } }
        val path = PathParser.createPathFromPathData(glyph)
        val bounds = RectF()
        path.computeBounds(bounds, true)
        if (bounds.width() <= 0 || bounds.height() <= 0) return null
        val n = SIZE
        val scale = (n - 2 * PAD) / max(bounds.width(), bounds.height())
        val matrix = android.graphics.Matrix().apply {
            postTranslate(-bounds.centerX(), -bounds.centerY())
            postScale(scale, scale)
            postTranslate(n / 2f, n / 2f)
        }
        val mapped = Path()
        path.transform(matrix, mapped)
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)
        canvas.drawPath(mapped, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        val px = IntArray(n * n)
        bmp.getPixels(px, 0, n, 0, 0, n, n)
        bmp.recycle()
        val coverage = IntArray(n * n) { (px[it] ushr 24) and 255 }
        val field = makeDistanceField(coverage, exactBand(outlineOf(mapped, 0.02f)))
        synchronized(fields) {
            if (fields.size > 12) fields.clear()
            fields[glyph] = field
        }
        return field
    }

    /** The atlas for all pages: tile i of the top row holds the high bytes, the same tile one row down the low bytes. */
    fun createAtlas(pages: List<ExtrusionRecapStoryPage>): Bitmap? {
        val n = SIZE
        val w = n * pages.size
        val atlas = IntArray(w * 2 * n)
        for ((i, page) in pages.withIndex()) {
            val field = fieldFor(page.glyph) ?: return null
            for (y in 0 until n) for (x in 0 until n) {
                val v = field[y * n + x]
                val hi = v shr 8
                val lo = v and 255
                atlas[y * w + i * n + x] = (255 shl 24) or (hi shl 16) or (hi shl 8) or hi
                atlas[(y + n) * w + i * n + x] = (255 shl 24) or (lo shl 16) or (lo shl 8) or lo
            }
        }
        val bmp = Bitmap.createBitmap(w, 2 * n, Bitmap.Config.ARGB_8888)
        bmp.setPixels(atlas, 0, w, 0, 0, w, 2 * n)
        return bmp
    }

    /** The glyph's path in design points about its own centre (for the gradient fallback below API 33), and its bounds' longest side. */
    fun outlinePath(glyph: String): Pair<Path, Float> {
        val path = PathParser.createPathFromPathData(glyph)
        val b = RectF()
        path.computeBounds(b, true)
        val p = Path(path)
        p.offset(-b.centerX(), -b.centerY())
        return p to max(b.width(), b.height())
    }

}
