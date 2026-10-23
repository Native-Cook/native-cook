// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.console

import android.graphics.Bitmap
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Skia's `SkShaders::MakeFractalNoise` (SkPerlinNoiseShaderImpl.h PaintingData
 * + SkRasterPipeline `perlin_noise` stage), no stitching. Deterministic for a
 * seed, so the housing grain is the same texture RN Skia draws for
 * `<FractalNoise freqX={0.65} freqY={0.65} octaves={1} seed={7} />`.
 */
class FractalNoise(private val freqX: Float, private val freqY: Float, private val octaves: Int, seed: Float) {
    private val blockSize = 256
    private var seedState = 0
    private val lattice = IntArray(blockSize)
    private val noise = Array(4) { Array(blockSize) { IntArray(2) } }

    private fun random(): Int {
        val kRandAmplitude = 16807
        val kRandQ = 127773
        val kRandR = 2836
        val kRandMaximum = 2147483647
        var result = kRandAmplitude * (seedState % kRandQ) - kRandR * (seedState / kRandQ)
        if (result <= 0) result += kRandMaximum
        seedState = result
        return result
    }

    init {
        val kRandMaximum = 2147483647
        seedState = seed.toInt()
        if (seedState <= 0) seedState = -(seedState % (kRandMaximum - 1)) + 1
        if (seedState > kRandMaximum - 1) seedState = kRandMaximum - 1
        for (channel in 0 until 4) {
            for (i in 0 until blockSize) {
                lattice[i] = i
                noise[channel][i][0] = random() % (2 * blockSize)
                noise[channel][i][1] = random() % (2 * blockSize)
            }
        }
        for (i in blockSize - 1 downTo 1) {
            val k = lattice[i]
            val j = random() % blockSize
            lattice[i] = lattice[j]
            lattice[j] = k
        }
        val copy = Array(4) { c -> Array(blockSize) { i -> noise[c][i].copyOf() } }
        for (i in 0 until blockSize) for (c in 0 until 4) for (j in 0 until 2) noise[c][i][j] = copy[c][lattice[i]][j]
        val kHalfMax16bits = 32767.5f
        val inv = 1f / blockSize
        for (c in 0 until 4) for (i in 0 until blockSize) {
            var gx = (noise[c][i][0] - blockSize) * inv
            var gy = (noise[c][i][1] - blockSize) * inv
            val len = sqrt(gx * gx + gy * gy)
            if (len > 0f) { gx /= len; gy /= len }
            noise[c][i][0] = ((gx + 1) * kHalfMax16bits).roundToInt()
            noise[c][i][1] = ((gy + 1) * kHalfMax16bits).roundToInt()
        }
    }

    private fun vec(sample: IntArray, x: Float, y: Float): Float {
        val vx = sample[0] * (2f / 65535f) - 1f
        val vy = sample[1] * (2f / 65535f) - 1f
        return vx * x + vy * y
    }

    /** Premultiplied RGBA (0…1) at local point (x, y) (pixel center already applied by caller). */
    fun sample(x: Float, y: Float, out: FloatArray) {
        var nx = (x + 0.5f) * freqX
        var ny = (y + 0.5f) * freqY
        var r = 0f; var g = 0f; var b = 0f; var a = 0f
        var ratio = 1f
        for (octave in 0 until octaves) {
            val floorX = floor(nx)
            val floorY = floor(ny)
            val ceilX = floorX + 1f
            val ceilY = floorY + 1f
            val fx = nx - floorX
            val fy = ny - floorY
            val latticeX = lattice[floorX.roundToInt() and 0xFF].toFloat()
            val latticeY = lattice[ceilX.roundToInt() and 0xFF].toFloat()
            val b00 = (latticeX + floorY).roundToInt() and 0xFF
            val b10 = (latticeY + floorY).roundToInt() and 0xFF
            val b01 = (latticeX + ceilY).roundToInt() and 0xFF
            val b11 = (latticeY + ceilY).roundToInt() and 0xFF
            val sx = fx * fx * (3f - 2f * fx)
            val sy = fy * fy * (3f - 2f * fy)
            for (c in 0 until 4) {
                val n = noise[c]
                var u = vec(n[b00], fx, fy)
                var v = vec(n[b10], fx - 1f, fy)
                val aa = u + (v - u) * sx
                u = vec(n[b01], fx, fy - 1f)
                v = vec(n[b11], fx - 1f, fy - 1f)
                val bb = u + (v - u) * sx
                val value = aa + (bb - aa) * sy
                when (c) {
                    0 -> r += value * ratio
                    1 -> g += value * ratio
                    2 -> b += value * ratio
                    else -> a += value * ratio
                }
            }
            nx *= 2f; ny *= 2f; ratio *= 0.5f
        }
        r = r * 0.5f + 0.5f; g = g * 0.5f + 0.5f; b = b * 0.5f + 0.5f; a = a * 0.5f + 0.5f
        a = a.coerceIn(0f, 1f)
        out[0] = r.coerceIn(0f, 1f) * a
        out[1] = g.coerceIn(0f, 1f) * a
        out[2] = b.coerceIn(0f, 1f) * a
        out[3] = a
    }

    /**
     * Rasterizes the shader for a local rect at `pxPerUnit` device pixels per
     * local unit, sampling each device pixel center as Skia does.
     */
    fun bitmap(widthUnits: Float, heightUnits: Float, pxPerUnit: Float): Bitmap {
        val w = (widthUnits * pxPerUnit).roundToInt().coerceAtLeast(1)
        val h = (heightUnits * pxPerUnit).roundToInt().coerceAtLeast(1)
        val pixels = IntArray(w * h)
        val px = FloatArray(4)
        for (j in 0 until h) for (i in 0 until w) {
            // Local coordinate of the device pixel center.
            sample((i + 0.5f) / pxPerUnit, (j + 0.5f) / pxPerUnit, px)
            val a = px[3]
            fun ch(v: Float) = if (a <= 0f) 0 else ((v / a).coerceIn(0f, 1f) * 255f).roundToInt()
            pixels[j * w + i] = ((a * 255f).roundToInt() shl 24) or (ch(px[0]) shl 16) or (ch(px[1]) shl 8) or ch(px[2])
        }
        // setPixels takes unpremultiplied colors and premultiplies them.
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}
