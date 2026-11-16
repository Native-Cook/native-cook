package com.ngocdevv.nativecook.blocks.progress.breathvolumelungs

import android.graphics.Bitmap
import com.ngocdevv.nativecook.blocks.progress.breathvolumelungs.BreathVolumeLungsMotion as M
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Port of bvl-fields.ts (lungs preset only) and the bitmap packing of bvl-mask.ts' gridImage. */
internal class BreathVolumeLungsFieldGrid(val cols: Int, val rows: Int, val width: Int, val bytes: ByteArray) {
    /** RGBA data atlas as an opaque sRGB bitmap. */
    fun toBitmap(): Bitmap {
        val h = rows + 1
        val px = IntArray(width * h)
        for (i in px.indices) {
            val o = i * 4
            val r = bytes[o].toInt() and 255
            val g = bytes[o + 1].toInt() and 255
            val b = bytes[o + 2].toInt() and 255
            val a = bytes[o + 3].toInt() and 255
            px[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, width, 0, 0, width, h)
        return bmp
    }
}

internal object BreathVolumeLungsFields {
    private const val ROWS = 40
    private const val TABLE = 64
    private const val SOFT = 0.22
    private val RADII = BreathVolumeLungsRest.RADII

    fun restRadius(x: Double, y: Double): Double {
        val r = Math.floor(y + 0.5).toInt() - 7
        val c = Math.floor(x + 0.5).toInt()
        return RADII.getOrNull(r)?.getOrNull(c) ?: 0.0
    }

    private const val TR_FROM = 14
    private const val TR_TO = 17
    private const val TR_ROWS = 4
    private const val OUTLET_LEFT = 11
    private const val OUTLET_RIGHT = 20
    private const val OUTLET_ROW = 17

    private fun inTrachea(col: Int, row: Int) = col in TR_FROM..TR_TO && row < TR_ROWS

    fun lungExhaleDensity(x: Double, y: Double): Double {
        val r = restRadius(x, y)
        if (r <= 0.12) return 0.0
        if (r < 0.5) return 0.26
        val col = Math.floor(x + 0.5).toInt()
        val row = Math.floor(y + 0.5).toInt() - 7
        if (inTrachea(col, row)) return 0.62 + 0.36 * (1 - row / TR_ROWS.toDouble())
        val left = col <= 15
        val ax = (if (left) OUTLET_LEFT else OUTLET_RIGHT).toDouble()
        val ay = OUTLET_ROW.toDouble()
        fun inLobe(u: Double, v: Double): Boolean {
            val c = Math.floor(u + 0.5).toInt()
            if (inTrachea(c, Math.floor(v + 0.5).toInt() - 7)) return false
            return (if (left) c <= 15 else c >= 16) && restRadius(u, v) >= 0.5
        }
        var g = 1.0
        for (k in 1..50) {
            val sc = k / 50.0
            if (inLobe(ax + (x - ax) / sc, ay + (y - ay) / sc)) { g = sc; break }
        }
        return 0.3 + 0.68 * (1 - g.pow(1.6))
    }

    /** Two-pass chamfer distance transform (not normalised). */
    private fun distanceField(mask: DoubleArray, cols: Int, rows: Int): DoubleArray {
        val d = DoubleArray(mask.size) { if (mask[it] > 0.1) 1000.0 else 0.0 }
        val sq2 = Math.sqrt(2.0)
        for (y in 0 until rows) for (x in 0 until cols) {
            val i = y * cols + x
            if (x > 0) d[i] = min(d[i], d[i - 1] + 1)
            if (y > 0) d[i] = min(d[i], d[i - cols] + 1)
            if (x > 0 && y > 0) d[i] = min(d[i], d[i - cols - 1] + sq2)
        }
        for (y in rows - 1 downTo 0) for (x in cols - 1 downTo 0) {
            val i = y * cols + x
            if (x < cols - 1) d[i] = min(d[i], d[i + 1] + 1)
            if (y < rows - 1) d[i] = min(d[i], d[i + cols] + 1)
            if (x < cols - 1 && y < rows - 1) d[i] = min(d[i], d[i + cols + 1] + sq2)
        }
        return d
    }

    private fun q255(v: Double) = (v * 255).roundToInt() / 255.0

    fun make(pitch: Double = 402.0 / 32): BreathVolumeLungsFieldGrid {
        val cols = max(16, min(69, (402.0 / max(402.0 / 69, pitch)).roundToInt()))
        val rows = (ROWS * cols / 32.0).roundToInt()
        val width = max(cols * 2, TABLE)
        val n = cols * rows
        val body = DoubleArray(n)
        val radius = DoubleArray(n)
        val pile = DoubleArray(n)
        for (i in 0 until n) {
            val x = (i % cols).toDouble() / cols * 32
            val y = (i / cols).toDouble() / cols * 32
            val r = restRadius(x, y)
            radius[i] = r
            body[i] = if (r > 0.12) lungExhaleDensity(x, y) else 0.0
            pile[i] = if (y >= 29 && y <= 36) M.clamp(1 - ((x - 15.5) / 15).pow(2) - ((36 - y) / 11).pow(1.4)) else 0.0
        }
        val bytes = ByteArray(width * (rows + 1) * 4)
        val quantBody = DoubleArray(n) { q255(body[it]) }
        val quantPile = DoubleArray(n) { q255(pile[it]) }
        val haloDistance = distanceField(DoubleArray(n) { if (radius[it] > 0.12) 0.0 else 1.0 }, cols, rows)
        val ranked = (0 until n).filter { radius[it] > 0.7 }.sortedByDescending { quantBody[it] }.take(12)
        val lastAir = HashMap<Int, Double>()
        ranked.forEachIndexed { rank, i -> lastAir[i] = (rank + 1) / 12.0 }
        val sparse = DoubleArray(n) { i ->
            val x = (((i % cols) * 32.0) / cols).roundToInt()
            val row = i / cols
            when {
                row == (29.0 * cols / 32).roundToInt() -> if (x in 14..17) doubleArrayOf(0.55, 1.0, 1.0, 0.65)[x - 14] else 0.0
                row == (33.0 * cols / 32).roundToInt() -> if (x in 14..17) doubleArrayOf(0.3, 0.8, 0.88, 0.4)[x - 14] else 0.0
                row == (36.0 * cols / 32).roundToInt() -> if (x in 12..19) doubleArrayOf(0.4, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.5)[x - 12] else 0.0
                else -> 0.0
            }
        }
        val bodyWeights = DoubleArray(256)
        val pileNormal = DoubleArray(256)
        val sparseDensity = ArrayList<Double>()
        val sparseArea = ArrayList<Double>()
        for (i in 0 until n) {
            val rr = q255(radius[i])
            bodyWeights[(quantBody[i] * 255).roundToInt()] += rr * rr
            pileNormal[(quantPile[i] * 255).roundToInt()]++
            if (sparse[i] != 0.0) { sparseDensity.add(quantPile[i]); sparseArea.add(sparse[i] * sparse[i]) }
        }
        val bodyBins = (0 until 256).filter { bodyWeights[it] > 0 }
        val pileBins = (0 until 256).filter { it > 0 && pileNormal[it] > 0 }
        fun bodyInk(th: Double): Double {
            var sum = 0.0
            for (i in bodyBins) { val rr = M.smooth((i / 255.0 - th) / SOFT); sum += rr * rr * bodyWeights[i] }
            return sum
        }
        fun pileInk(th: Double): Double {
            var sum = 0.0
            for (i in pileBins) { val rr = M.smooth((i / 255.0 - th) / SOFT); sum += rr * rr * pileNormal[i] }
            for (j in sparseDensity.indices) {
                val rr = M.smooth((sparseDensity[j] - th) / SOFT)
                sum += max(0.0, sparseArea[j] - rr * rr)
            }
            return sum
        }
        val total = bodyInk(0.04)
        val basePile = sparse.sumOf { it * it }
        for (k in 0 until TABLE) {
            val p = (k.toDouble() / (TABLE - 1)).pow(1 / 0.8)
            val target = basePile + total - bodyInk(M.thresholdAt(p))
            var lo = -0.22
            var hi = 1.0
            for (iter in 0 until 18) {
                val m = (lo + hi) / 2
                if (pileInk(m) > target) lo = m else hi = m
            }
            val th = (lo + hi) / 2
            val code = (((th + 0.22) / 1.22) * 65535).roundToInt()
            val offset = (rows * width + k) * 4
            bytes[offset] = (code shr 8).toByte()
            bytes[offset + 1] = (code and 255).toByte()
            bytes[offset + 3] = 255.toByte()
        }
        for (i in 0 until n) {
            val off = ((i / cols) * width + (i % cols)) * 4
            bytes[off] = (quantBody[i] * 255).roundToInt().toByte()
            bytes[off + 1] = (radius[i] * 255).roundToInt().toByte()
            bytes[off + 2] = (quantPile[i] * 255).roundToInt().toByte()
            bytes[off + 3] = 255.toByte()
            val go = off + cols * 4
            val hd = haloDistance[i]
            bytes[go] = (if (hd > 0 && hd <= 4) ((0.08 + 0.2 * (1 - hd / 4)) * 255).roundToInt() else 0).toByte()
            bytes[go + 1] = ((lastAir[i] ?: 0.0) * 255).roundToInt().toByte()
            bytes[go + 2] = (sparse[i] * 255).roundToInt().toByte()
            bytes[go + 3] = 255.toByte()
        }
        return BreathVolumeLungsFieldGrid(cols, rows, width, bytes)
    }
}
