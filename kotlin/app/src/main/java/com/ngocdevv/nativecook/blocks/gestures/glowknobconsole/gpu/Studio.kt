// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.gpu

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** gpu/studio.ts SoftBox: a plane `size` at `at`, facing the origin. */
class SoftBox(val size: DoubleArray, val at: DoubleArray, val power: Double, val tint: DoubleArray = doubleArrayOf(1.0, 1.0, 0.97))

class StudioOptions(
    /** Lower hemisphere (the floor bounce), linear RGB. */
    val floor: DoubleArray,
    /** Upper hemisphere, linear RGB. */
    val sky: DoubleArray,
    /** Where the floor fades into the sky, as [from, to] of the up vector. */
    val horizon: DoubleArray = doubleArrayOf(-0.8, -0.3),
    val boxes: List<SoftBox>,
)

/**
 * The procedural studio of `gpu/studio.ts` (a two-tone dome of radius 20 and
 * emissive soft boxes that `lookAt` the origin, seen from the origin) as an
 * HDR equirectangular image in Filament's equirect convention
 * (u = atan2(x, z)/2π + ½, v = ½ − asin(y)/π), softened by the same σ the
 * three.js PMREM generator applies before prefiltering (fromScene sigma
 * 0.035 rad). Also projects it onto 3 bands of spherical harmonics in the
 * pre-convolved, pre-scaled form Filament's IndirectLight expects.
 */
class StudioEnvironment(val width: Int, val height: Int, val rgb: FloatArray, val sh: FloatArray)

private class Plane(val c: DoubleArray, val n: DoubleArray, val ax: DoubleArray, val ay: DoubleArray, val hw: Double, val hh: Double, val color: DoubleArray)

private fun norm(v: DoubleArray): DoubleArray {
    val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
    return doubleArrayOf(v[0] / l, v[1] / l, v[2] / l)
}

private fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
    a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0],
)

private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

private fun smoothstep(e0: Double, e1: Double, x: Double): Double {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t)
}

fun makeStudio(options: StudioOptions, width: Int = 1024, height: Int = 512, sigma: Double = 0.035): StudioEnvironment {
    val planes = options.boxes.map { b ->
        // Object3D.lookAt(0,0,0) for a non-camera: +z points at the target.
        val z = norm(doubleArrayOf(-b.at[0], -b.at[1], -b.at[2]))
        val x = norm(cross(doubleArrayOf(0.0, 1.0, 0.0), z))
        val y = cross(z, x)
        Plane(b.at, z, x, y, b.size[0] / 2, b.size[1] / 2, doubleArrayOf(b.power * b.tint[0], b.power * b.tint[1], b.power * b.tint[2]))
    }
    fun radiance(d: DoubleArray, out: DoubleArray) {
        var best = 20.0 // dome radius
        var hit: Plane? = null
        for (p in planes) {
            val denom = dot(d, p.n)
            if (denom == 0.0) continue
            val t = dot(p.c, p.n) / denom
            if (t <= 0 || t >= best) continue
            val q = doubleArrayOf(d[0] * t - p.c[0], d[1] * t - p.c[1], d[2] * t - p.c[2])
            if (kotlin.math.abs(dot(q, p.ax)) <= p.hw && kotlin.math.abs(dot(q, p.ay)) <= p.hh) {
                best = t; hit = p
            }
        }
        if (hit != null) {
            out[0] = hit.color[0]; out[1] = hit.color[1]; out[2] = hit.color[2]
        } else {
            val k = smoothstep(options.horizon[0], options.horizon[1], d[1])
            for (i in 0..2) out[i] = options.floor[i] + (options.sky[i] - options.floor[i]) * k
        }
    }
    // Rasterize with 3×3 supersampling.
    val img = FloatArray(width * height * 3)
    val d = DoubleArray(3)
    val c = DoubleArray(3)
    val ss = 3
    for (j in 0 until height) for (i in 0 until width) {
        var r = 0.0; var g = 0.0; var b = 0.0
        for (sj in 0 until ss) for (si in 0 until ss) {
            val u = (i + (si + 0.5) / ss) / width
            val v = (j + (sj + 0.5) / ss) / height
            val lon = (u * 2 - 1) * PI
            val lat = (1 - 2 * v) * PI / 2
            d[0] = cos(lat) * sin(lon); d[1] = sin(lat); d[2] = cos(lat) * cos(lon)
            radiance(d, c)
            r += c[0]; g += c[1]; b += c[2]
        }
        val o = (j * width + i) * 3
        img[o] = (r / (ss * ss)).toFloat(); img[o + 1] = (g / (ss * ss)).toFloat(); img[o + 2] = (b / (ss * ss)).toFloat()
    }
    val blurred = blurEquirect(img, width, height, sigma)
    return StudioEnvironment(width, height, blurred, projectSH(blurred, width, height))
}

private fun kernel(sigmaPx: Double): FloatArray {
    val r = max(1, (sigmaPx * 3).roundToInt())
    val k = FloatArray(r * 2 + 1) { exp(-((it - r) * (it - r)) / (2 * sigmaPx * sigmaPx)).toFloat() }
    val sum = k.sum()
    for (i in k.indices) k[i] /= sum
    return k
}

/** Separable Gaussian of σ radians on the sphere (horizontal σ widens by 1/cos(lat), wrapping). */
private fun blurEquirect(src: FloatArray, w: Int, h: Int, sigma: Double): FloatArray {
    val tmp = FloatArray(src.size)
    for (j in 0 until h) {
        val lat = (1 - 2 * (j + 0.5) / h) * PI / 2
        val sigmaPx = min(w / 8.0, sigma / (2 * PI / w) / max(cos(lat), 1e-3))
        val k = kernel(sigmaPx)
        val r = k.size / 2
        for (i in 0 until w) {
            var a = 0f; var b = 0f; var cc = 0f
            for (t in k.indices) {
                val x = ((i + t - r) % w + w) % w
                val o = (j * w + x) * 3
                a += src[o] * k[t]; b += src[o + 1] * k[t]; cc += src[o + 2] * k[t]
            }
            val o = (j * w + i) * 3
            tmp[o] = a; tmp[o + 1] = b; tmp[o + 2] = cc
        }
    }
    val out = FloatArray(src.size)
    val k = kernel(sigma / (PI / h))
    val r = k.size / 2
    for (j in 0 until h) for (i in 0 until w) {
        var a = 0f; var b = 0f; var cc = 0f
        for (t in k.indices) {
            val y = (j + t - r).coerceIn(0, h - 1)
            val o = (y * w + i) * 3
            a += tmp[o] * k[t]; b += tmp[o + 1] * k[t]; cc += tmp[o + 2] * k[t]
        }
        val o = (j * w + i) * 3
        out[o] = a; out[o + 1] = b; out[o + 2] = cc
    }
    return out
}

/**
 * 3-band SH in Filament's IndirectLight convention: irradiance(n)/π =
 * Σ sh[i]·P_i(n) with P = {1, y, z, x, yx, yz, 3z²−1, zx, x²−y²}; i.e. the
 * radiance projected on the unnormalized polynomials, convolved with the
 * Lambert kernel (π, 2π/3, π/4), times K²(l,m), divided by π (as cmgen does).
 */
private fun projectSH(img: FloatArray, w: Int, h: Int): FloatArray {
    val acc = DoubleArray(27)
    for (j in 0 until h) {
        val lat = (1 - 2 * (j + 0.5) / h) * PI / 2
        val dOmega = (2 * PI / w) * (PI / h) * cos(lat)
        for (i in 0 until w) {
            val lon = ((i + 0.5) / w * 2 - 1) * PI
            val x = cos(lat) * sin(lon); val y = sin(lat); val z = cos(lat) * cos(lon)
            val p = doubleArrayOf(1.0, y, z, x, y * x, y * z, 3 * z * z - 1, z * x, x * x - y * y)
            val o = (j * w + i) * 3
            for (k in 0 until 9) for (ch in 0..2) acc[k * 3 + ch] += img[o + ch] * p[k] * dOmega
        }
    }
    val k2 = doubleArrayOf(
        1 / (4 * PI),
        3 / (4 * PI), 3 / (4 * PI), 3 / (4 * PI),
        15 / (4 * PI), 15 / (4 * PI), 5 / (16 * PI), 15 / (4 * PI), 15 / (16 * PI),
    )
    val band = doubleArrayOf(PI, 2 * PI / 3, 2 * PI / 3, 2 * PI / 3, PI / 4, PI / 4, PI / 4, PI / 4, PI / 4)
    return FloatArray(27) { (acc[it] * k2[it / 3] * band[it / 3] / PI).toFloat() }
}

