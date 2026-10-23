package com.ngocdevv.nativecook.blocks.progress.stitchweavepreview

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

internal data class StitchWeavePreviewPoint(val x: Double, val y: Double)

/** Immutable formation, fiber, arc-length and motif data, matching the frozen Swift atlas. */
internal class StitchWeavePreviewResources private constructor() {
    val atlas: Bitmap
    val fallbackAtlas: Bitmap
    val grain: Bitmap
    val shadow: Bitmap
    private val arcs = DoubleArray(65 * 129)
    val completion = FloatArray(48) { StitchWeavePreviewMath.canonicalTime(StitchWeavePreviewMath.clamp((it + 1.0 - 3) / 45)).toFloat() }
    val delays = FloatArray(52) { StitchWeavePreviewMath.delay(it).toFloat() }
    val motifs = Array(48) { FloatArray(52 * 2) }
    private val m = StitchWeavePreviewMath

    init {
        val pixels = IntArray(512 * 256) { Color.argb(255, 0, 128, 128) }
        val appearance = IntArray(512 * 256)
        repeat(16) { frame ->
            val f = frame / 15.0; val front = frontCurve(f); val back = backCurve(f)
            repeat(40) { y -> repeat(32) { x ->
                val p = StitchWeavePreviewPoint((x + .5) / 3 - 2.2, (y + .5) / 3 - 2.4)
                val a = nearest(p, front); val b = nearest(p, back)
                val radius = .98
                val side = (p.x - a.point.x) * -a.tangent.y + (p.y - a.point.y) * a.tangent.x
                val fibers = .5 + .5 * cos(side * 14.3 + .25 * sin(a.along * .9))
                val fringe = .10 * m.clamp((radius + .38 - a.distance) / .38) * (.45 + .55 * m.hash(x * 37 + y * 103))
                val core = m.clamp((radius + .17 - a.distance) / .34)
                val aa = core + fringe * (1 - core)
                val ba = m.clamp((radius + .19 - b.distance) / .38) * .96
                val coverage = (aa + ba * (1 - aa)) * m.smooth(f * 6)
                val near = if (aa > .02) a else b
                val nx = ((p.x - near.point.x) / radius).coerceIn(-1.0, 1.0)
                val ny = ((p.y - near.point.y) / radius).coerceIn(-1.0, 1.0)
                val index = ((frame / 8) * 40 + y) * 512 + (frame % 8) * 32 + x
                pixels[index] = Color.argb(255, byte(coverage), byte(nx * .5 + .5), byte(ny * .5 + .5))
                val micro = .5 + .5 * sin(side * 20 + a.along * 1.2)
                pixels[index + 256] = Color.argb(255, byte(fibers), byte(fringe), byte(micro))
                // Below 33, the same centerline formation frames retain their grain and bundle ridge.
                val nz = sqrt(max(.02, 1 - nx * nx - ny * ny))
                val light = (.84 + .16 * nz) * (.965 + .050 * fibers + .020 * micro)
                appearance[index] = Color.argb(byte(coverage), byte(light), byte(light), byte(light))
            } }
        }
        repeat(65) { amplitude ->
            val a = amplitude / 64.0 * 17
            val samples = DoubleArray(129)
            fun integrand(u: Double): Double { val dz = 2 * PI * a * cos(2 * PI * u + .6); return sqrt(322.4 * 322.4 - dz * dz) }
            for (column in 1..128) {
                val l = (column - 1) / 128.0; val r = column / 128.0; val mid = (l + r) / 2
                samples[column] = samples[column - 1] + (r - l) / 6 * (integrand(l) + 4 * integrand(mid) + integrand(r))
            }
            val center = samples[128] / 2
            repeat(129) { column ->
                val baseline = (column / 128.0 - .5) * 322.4
                val normalized = m.clamp((samples[column] - center - baseline) / 16 + .5)
                val packed = floor(normalized * 65535 + .5).toInt()
                pixels[(128 + amplitude) * 512 + column] = Color.argb(255, packed shr 8, packed and 255, 0)
                arcs[amplitude * 129 + column] = baseline + (packed / 65535.0 - .5) * 16
            }
        }
        repeat(48) { row -> repeat(52) { column ->
            val point = StitchWeavePreviewPoint((column + .5) * 6.2, (47.5 - row) * 6.8)
            fun segment(u: Double, v: Double, x: Double, y: Double) = nearest(point, listOf(StitchWeavePreviewPoint(u * 322.4, v * 326.4), StitchWeavePreviewPoint(x * 322.4, y * 326.4))).distance
            var sprig = segment(.5, .28, .5, .73); var ripple = Double.MAX_VALUE
            repeat(3) { i ->
                var v = .40 + i * .12
                sprig = min(sprig, min(segment(.5, v, .33, v - .085), segment(.5, v, .67, v - .085)))
                v = .30 + i * .16
                ripple = min(ripple, min(segment(.24, v, .50, v + .1), segment(.50, v + .1, .76, v)))
            }
            val r = byte(1 - m.smooth((sprig - 6.8) / .9)); val g = byte(1 - m.smooth((ripple - 6.8) / .9))
            pixels[(208 + row) * 512 + column] = Color.argb(255, r, g, 0)
            motifs[row][column * 2] = r / 255f; motifs[row][column * 2 + 1] = g / 255f
        } }
        // 24-bit static data avoids divergent uniform-array lookups on older Skia backends.
        fun pack24(x: Int, value: Double) {
            val n = floor(m.clamp(value) * 16777215 + .5).toInt()
            pixels[200 * 512 + x] = Color.argb(255, n shr 16, (n shr 8) and 255, n and 255)
        }
        repeat(48) { pack24(it, completion[it] / 1.9) }
        repeat(52) { pack24(64 + it, (delays[it] + .004) / .0155) }
        atlas = bitmap(pixels, 512, 256); fallbackAtlas = bitmap(appearance, 512, 256)
        grain = bitmap(IntArray(128 * 128) { i -> val n = byte(m.hash(i + 321)); Color.rgb(n, n, n) }, 128, 128)
        shadow = bitmap(IntArray(256 * 96) { i ->
            val dx = (i % 256 - 127.5) / 73; val dy = (i / 256 - 47.5) / 18
            Color.argb(byte(exp(-.5 * (dx * dx + dy * dy))), 0, 0, 0)
        }, 256, 96)
    }

    fun arc(u: Double, amplitude: Double): Double {
        val clamped = m.clamp(u); val x = clamped * 128; val a = m.clamp(amplitude / 17) * 64
        val xi = min(127, x.toInt()); val ai = min(63, a.toInt()); val xf = x - xi; val af = a - ai
        val first = arcs[ai * 129 + xi] * (1 - xf) + arcs[ai * 129 + xi + 1] * xf
        val next = arcs[(ai + 1) * 129 + xi] * (1 - xf) + arcs[(ai + 1) * 129 + xi + 1] * xf
        // Extrapolate only the analytic plane; the packed correction lookup alone clamps.
        return (u - .5) * 322.4 + first * (1 - af) + next * af - (clamped - .5) * 322.4
    }
    fun projector(pose: StitchWeavePreviewPose) = StitchWeavePreviewProjector(this, pose)
    fun uniforms(pose: StitchWeavePreviewPose, out: FloatArray) {
        val a = m.rotationMatrix(pose.rotation); val b = m.rotationMatrix(pose.lagRotation)
        repeat(9) { out[it] = a[it].toFloat(); out[it + 9] = (b[it] - a[it]).toFloat() }
        if (out.size >= 118) { completion.copyInto(out, 18); delays.copyInto(out, 66) }
    }
    private data class Near(val distance: Double, val point: StitchWeavePreviewPoint, val tangent: StitchWeavePreviewPoint, val along: Double)
    private fun nearest(point: StitchWeavePreviewPoint, path: List<StitchWeavePreviewPoint>): Near {
        var best = Double.MAX_VALUE; var result = StitchWeavePreviewPoint(0.0, 0.0); var tangent = StitchWeavePreviewPoint(0.0, 1.0); var along = 0.0
        for (i in 1 until path.size) {
            val a = path[i - 1]; val b = path[i]; val dx = b.x - a.x; val dy = b.y - a.y
            val t = m.clamp(((point.x - a.x) * dx + (point.y - a.y) * dy) / max(.000001, dx * dx + dy * dy))
            val q = StitchWeavePreviewPoint(a.x + dx * t, a.y + dy * t); val distance = hypot(point.x - q.x, point.y - q.y)
            if (distance < best) { best = distance; result = q; val length = max(.000001, hypot(dx, dy)); tangent = StitchWeavePreviewPoint(dx / length, dy / length); along = i - 1 + t }
        }
        return Near(best, result, tangent, along)
    }
    private fun transform(p: StitchWeavePreviewPoint, f: Double): StitchWeavePreviewPoint {
        val spread = .08 + .92 * m.smooth(f); val top = m.clamp((6.3 - p.y) / 5.8)
        return StitchWeavePreviewPoint(3.1 + (p.x - 3.1) * spread, p.y - 1.8 * (1 - f) * top)
    }
    private fun frontCurve(f: Double): List<StitchWeavePreviewPoint> {
        val segments = arrayOf(doubleArrayOf(.8, 6.3, 1.8, 5.7, 1.2, 3.8, 1.2, 2.0),
            doubleArrayOf(1.2, 2.0, 1.65, .2, 4.35, .2, 4.8, 2.0),
            doubleArrayOf(4.8, 2.0, 4.8, 3.8, 4.4, 5.7, 5.4, 6.3),
            doubleArrayOf(5.4, 6.3, 6.1, 7.1, 6.9, 7.0, 6.9, 6.0))
        return segments.flatMap { a -> (0..12).map { i ->
            val t = i / 12.0; val s = 1 - t
            transform(StitchWeavePreviewPoint(s * s * s * a[0] + 3 * s * s * t * a[2] + 3 * s * t * t * a[4] + t * t * t * a[6],
                s * s * s * a[1] + 3 * s * s * t * a[3] + 3 * s * t * t * a[5] + t * t * t * a[7]), f)
        } }
    }
    private fun backCurve(f: Double) = (0..32).map { i ->
        val t = i / 32.0; val s = 1 - t
        transform(StitchWeavePreviewPoint(s * s * -.7 + 2 * s * t * 3.1 + t * t * 6.9, s * s * 3.7 + 2 * s * t * 8 + t * t * 3.7), f)
    }
    private fun byte(v: Double) = floor(v.coerceIn(0.0, 1.0) * 255 + .5).toInt()
    private fun bitmap(p: IntArray, w: Int, h: Int) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(p, 0, w, 0, 0, w, h) }
    companion object { val shared by lazy { StitchWeavePreviewResources() } }
}

internal class StitchWeavePreviewProjector(private val resources: StitchWeavePreviewResources, private val pose: StitchWeavePreviewPose) {
    private val a = StitchWeavePreviewMath.rotationMatrix(pose.rotation)
    private val b = StitchWeavePreviewMath.rotationMatrix(pose.lagRotation)
    fun project(u: Double, v: Double): StitchWeavePreviewPoint {
        val amplitude = (8 + 9 * pose.p) * sin(PI * v)
        val x = resources.arc(u, max(0.0, amplitude)); val y = 245.6 + 326.4 * v - 420 + pose.hem
        val lead = 1 - (3 + 45 * pose.p) / 48
        val weight = StitchWeavePreviewMath.smooth(1 - max(0.0, (v - lead) * 326.4) / 256)
        val z = amplitude * sin(2 * PI * u + .6) + pose.lift * weight; val lag = StitchWeavePreviewMath.clamp(1 - v)
        fun row(i: Int): Double { val n = i * 3; return (a[n] + (b[n] - a[n]) * lag) * x + (a[n + 1] + (b[n + 1] - a[n + 1]) * lag) * y + (a[n + 2] + (b[n + 2] - a[n + 2]) * lag) * z }
        val perspective = 950 / (950 - row(2))
        return StitchWeavePreviewPoint(196.5 + row(0) * perspective, 420 + row(1) * perspective)
    }
}
