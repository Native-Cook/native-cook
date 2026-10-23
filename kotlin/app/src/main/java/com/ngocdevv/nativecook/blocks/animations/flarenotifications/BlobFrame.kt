package com.ngocdevv.nativecook.blocks.animations.flarenotifications

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * One frame of a pill's flame (flare-blob-frame.ts): interpolates the fitted
 * blob keys at time t (cyclic Catmull-Rom, the same curve the fit used) into
 * the shader's uniform arrays. Math in Double as in JS; the arrays are reused
 * every frame.
 */
class BlobFrame(private val blobs: FlareBlobs, private val keys: DoubleArray) {
    private val n = blobs.count
    private val k = (blobs.period / blobs.dt).roundToInt()

    /** Per blob: u, v, radius a, radius b. */
    val geo = FloatArray(n * 4)
    /** Per blob: OKLab L, a, b, alpha. */
    val col = FloatArray(n * 4)
    /** Per blob: cos, sin of its angle. */
    val rot = FloatArray(n * 2)

    fun update(t: Double) {
        var x = (t / blobs.dt) % k
        if (x < 0) x += k
        val i1 = floor(x).toInt()
        val f = x - i1
        val f2 = f * f
        val f3 = f2 * f
        val k0 = ((i1 - 1 + k) % k) * n * 9
        val k1 = (i1 % k) * n * 9
        val k2 = ((i1 + 1) % k) * n * 9
        val k3 = ((i1 + 2) % k) * n * 9
        fun at(b: Int, c: Int): Double {
            val p0 = keys[k0 + b * 9 + c]
            val p1 = keys[k1 + b * 9 + c]
            val p2 = keys[k2 + b * 9 + c]
            val p3 = keys[k3 + b * 9 + c]
            return 0.5 * (2 * p1 + (p2 - p0) * f + (2 * p0 - 5 * p1 + 4 * p2 - p3) * f2 + (3 * p1 - p0 - 3 * p2 + p3) * f3)
        }
        for (b in 0 until n) {
            geo[b * 4] = at(b, 0).toFloat()
            geo[b * 4 + 1] = at(b, 1).toFloat()
            geo[b * 4 + 2] = (blobs.minA + exp(at(b, 2))).toFloat()
            geo[b * 4 + 3] = (blobs.minB + exp(at(b, 3))).toFloat()
            val ang = at(b, 4)
            rot[b * 2] = cos(ang).toFloat()
            rot[b * 2 + 1] = sin(ang).toFloat()
            col[b * 4] = at(b, 6).toFloat()
            col[b * 4 + 1] = at(b, 7).toFloat()
            col[b * 4 + 2] = at(b, 8).toFloat()
            col[b * 4 + 3] = (1 / (1 + exp(-at(b, 5)))).toFloat()
        }
    }
}
