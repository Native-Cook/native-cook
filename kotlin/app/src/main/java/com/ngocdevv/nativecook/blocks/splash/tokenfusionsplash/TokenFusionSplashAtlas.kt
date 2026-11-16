package com.ngocdevv.nativecook.blocks.splash.tokenfusionsplash

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.PathParser
import com.ngocdevv.nativecook.blocks.splash.tokenfusionsplash.TokenFusionSplashTheme as Theme
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The mount-time signed-distance atlas of the glyphs (RN `tfs-atlas.ts` / `tfs-sdf.ts`): slot 0 the core's mark, then one per token,
 * each a 128 px cell holding the exact Euclidean distance transform (Felzenszwalb, two separable lower-envelope passes) of the path,
 * 0.5 on the edge, +-16 px of spread. CPU rasterisation: no GPU readback.
 */
internal object TokenFusionSplashAtlas {
    private val cache = HashMap<String, ByteArray>()

    /** The exact Euclidean distance transform (squared distances) of `mask` (alpha 0..255) to the nearest inside / outside pixel. */
    private fun distanceTransform(mask: IntArray, width: Int, height: Int, inside: Boolean): DoubleArray {
        val grid = DoubleArray(width * height)
        val len = max(width, height)
        val tmp = DoubleArray(len)
        val v = IntArray(len)
        val z = DoubleArray(len + 1)
        val dst = DoubleArray(len)
        fun pass(length: Int) {
            var k = 0
            v[0] = 0
            z[0] = -1e20
            z[1] = 1e20
            for (q in 1 until length) {
                var s = (tmp[q] + q.toDouble() * q - (tmp[v[k]] + v[k].toDouble() * v[k])) / (2.0 * q - 2.0 * v[k])
                while (s <= z[k] && k > 0) {
                    k--
                    s = (tmp[q] + q.toDouble() * q - (tmp[v[k]] + v[k].toDouble() * v[k])) / (2.0 * q - 2.0 * v[k])
                }
                k++
                v[k] = q
                z[k] = s
                z[k + 1] = 1e20
            }
            k = 0
            for (q in 0 until length) {
                while (z[k + 1] < q) k++
                val d = (q - v[k]).toDouble()
                dst[q] = d * d + tmp[v[k]]
            }
        }
        for (i in grid.indices) grid[i] = if ((mask[i] > 127) == inside) 0.0 else 1e10
        for (x in 0 until width) {
            for (y in 0 until height) tmp[y] = grid[y * width + x]
            pass(height)
            for (y in 0 until height) grid[y * width + x] = dst[y]
        }
        for (y in 0 until height) {
            for (x in 0 until width) tmp[x] = grid[y * width + x]
            pass(width)
            for (x in 0 until width) grid[y * width + x] = dst[x]
        }
        return grid
    }

    private fun sdfValues(alpha: IntArray, width: Int, height: Int, spread: Int): ByteArray {
        val toInside = distanceTransform(alpha, width, height, true)
        val toOutside = distanceTransform(alpha, width, height, false)
        val out = ByteArray(width * height)
        for (i in alpha.indices) {
            val distance = if (alpha[i] > 127) -sqrt(toOutside[i]) else sqrt(toInside[i])
            val value = (0.5 + distance / (2.0 * spread)).coerceIn(0.0, 1.0)
            out[i] = (value * 255).roundToInt().toByte()
        }
        return out
    }

    private fun rasterise(pathData: String): ByteArray? {
        val cell = Theme.ATLAS_CELL
        val path = try {
            PathParser.createPathFromPathData(pathData)
        } catch (error: RuntimeException) {
            return null
        }
        val bmp = Bitmap.createBitmap(cell, cell, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)
        val b = RectF()
        path.computeBounds(b, true)
        val scale = (cell - Theme.ATLAS_PADDING * 2) / max(max(b.width(), b.height()), 1f)
        canvas.translate(cell / 2f, cell / 2f)
        canvas.scale(scale, scale)
        canvas.translate(-b.left - b.width() / 2, -b.top - b.height() / 2)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawPath(path, paint)
        val px = IntArray(cell * cell)
        bmp.getPixels(px, 0, cell, 0, 0, cell, cell)
        bmp.recycle()
        val alpha = IntArray(cell * cell) { px[it] ushr 24 }
        return sdfValues(alpha, cell, cell, Theme.ATLAS_SPREAD)
    }

    /** The atlas: `cells` cells of `cell` px side by side, opaque; unused slots stay white (far outside). */
    fun make(paths: List<String?>): Bitmap {
        val cell = Theme.ATLAS_CELL
        val cells = Theme.ATLAS_CELLS
        val w = cell * cells
        val pixels = IntArray(w * cell) { Color.WHITE }
        for (slot in 0 until minOf(paths.size, cells)) {
            val input = paths[slot] ?: continue
            val sdf = synchronized(cache) {
                cache.getOrPut(input) { rasterise(input) ?: return@getOrPut ByteArray(0) }
            }
            if (sdf.isEmpty()) continue
            for (y in 0 until cell) {
                for (x in 0 until cell) {
                    val v = sdf[y * cell + x].toInt() and 255
                    pixels[y * w + slot * cell + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
                }
            }
        }
        return Bitmap.createBitmap(pixels, w, cell, Bitmap.Config.ARGB_8888)
    }
}
