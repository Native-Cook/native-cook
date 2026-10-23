package com.ngocdevv.nativecook.blocks.splash.cosmicdriftsplash

import android.graphics.Bitmap
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Everything the Cosmic Drift splash draws that is not tied to a frame: the glossy sphere
// sprites (shaded pixel by pixel once, then blurred into depth-of-field levels), the seeded
// scene (spheres, dust streaks, stars) and the Orrery mark's geometry. Same numbers as the
// Swift version (CosmicDriftSplashArt.swift).

internal object CosmicDriftSplashArt {
    /** Sphere diameter inside a sprite, in pixels, and the sprite's side (2.1 diameters). */
    const val DIAMETER = 168
    const val SIDE = 352
    const val SPRITE_SPAN = SIDE.toDouble() / DIAMETER
    /** Blur of each level, as a fraction of the sphere's diameter (Gaussian sigma). */
    val blurLevels = doubleArrayOf(0.0, 0.02, 0.04, 0.07, 0.11, 0.17)

    class V3(val x: Double, val y: Double, val z: Double) {
        operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
        operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
        operator fun times(k: Double) = V3(x * k, y * k, z * k)
        fun dot(o: V3) = x * o.x + y * o.y + z * o.z
        fun normalized(): V3 { val l = sqrt(dot(this)); return V3(x / l, y / l, z / l) }
    }

    class Palette(
        val base: V3, val deep: V3, val band: V3, val rim: V3,
        val bandFrom: Double, val bandStrength: Double, val rimStrength: Double,
    ) {
        /** What a trail of this sphere is drawn with while the field recedes. */
        val trail: Int = android.graphics.Color.rgb((base.x * 255).roundToInt(), (base.y * 255).roundToInt(), (base.z * 255).roundToInt())
    }

    fun rgb(hex: Int) = V3(((hex shr 16) and 255) / 255.0, ((hex shr 8) and 255) / 255.0, (hex and 255) / 255.0)

    /** Ember with a teal cap, cream, teal with a cream band, dusty blue, ochre with a teal cap, charcoal. */
    val palettes = listOf(
        Palette(rgb(0xD5532F), rgb(0x2A0A07), rgb(0x4DBDB5), rgb(0x6FD8CE), 0.64, 1.0, 0.55),
        Palette(rgb(0xE8DEC6), rgb(0x2B2720), rgb(0xE8DEC6), rgb(0xF3C48E), 2.0, 0.0, 0.35),
        Palette(rgb(0x2F8E89), rgb(0x051817), rgb(0xE6D9BC), rgb(0xA6E8DA), 0.72, 0.95, 0.4),
        Palette(rgb(0x5D7894), rgb(0x0C131C), rgb(0x5D7894), rgb(0xF2A46C), 2.0, 0.0, 0.38),
        Palette(rgb(0xCB972F), rgb(0x281805), rgb(0x3FA19C), rgb(0x7FD9D1), 0.68, 1.0, 0.45),
        Palette(rgb(0x5E6364), rgb(0x0A0B0C), rgb(0x5E6364), rgb(0xAACCCE), 2.0, 0.0, 0.42),
    )

    fun fract(v: Double) = v - floor(v)
    fun hash(x: Double, y: Double) = fract(sin(x * 127.1 + y * 311.7) * 43758.5453)
    fun hash(i: Int, k: Int) = hash(i * 1.618 + 0.37, k * 2.71 + 0.11)
    fun smoothstep(a: Double, b: Double, x: Double): Double {
        val u = ((x - a) / (b - a)).coerceIn(0.0, 1.0); return u * u * (3 - 2 * u)
    }
    fun noise(x: Double, y: Double): Double {
        val ix = floor(x); val iy = floor(y); val fx = x - ix; val fy = y - iy
        val ux = fx * fx * (3 - 2 * fx); val uy = fy * fy * (3 - 2 * fy)
        val a = hash(ix, iy); val b = hash(ix + 1, iy); val c = hash(ix, iy + 1); val d = hash(ix + 1, iy + 1)
        return (a + (b - a) * ux) * (1 - uy) + (c + (d - c) * ux) * uy
    }

    /**
     * One sphere shaded per pixel: soft key light from the upper left, a tight specular spot and a
     * broad sheen, a coloured rim light on the upper right, a tilted colour cap, grain and glints.
     * Premultiplied RGBA bytes (0..255 as ints), SIDE × SIDE.
     */
    fun shade(p: Palette, index: Int): IntArray {
        val out = IntArray(SIDE * SIDE * 4)
        val r = DIAMETER / 2.0; val c = SIDE / 2.0
        val light = V3(-0.55, -0.68, 0.48).normalized()
        val half = (light + V3(0.0, 0.0, 1.0)).normalized()
        val axis = V3(0.45, -0.75, 0.48).normalized()
        val rimX = 0.95; val rimY = -0.31
        val lo = (c - r - 2).toInt(); val hi = (c + r + 2).toInt()
        val seed = index.toDouble()
        for (py in lo until hi) for (px in lo until hi) {
            val x = (px + 0.5 - c) / r; val y = (py + 0.5 - c) / r
            val d2 = x * x + y * y
            val alpha = (r * (1 - sqrt(d2)) + 0.5).coerceIn(0.0, 1.0)
            if (alpha <= 0) continue
            val z = sqrt(max(0.0, 1 - d2))
            val len0 = max(1.0, sqrt(d2))
            val n = V3(x / len0, y / len0, z / len0)
            val diff = smoothstep(-0.28, 1.0, n.dot(light))
            val cap = smoothstep(p.bandFrom - 0.12, p.bandFrom + 0.12, n.dot(axis)) * p.bandStrength
            val grain = 0.6 * noise(x * 5 + seed * 7, y * 5) + 0.4 * noise(x * 11, y * 11 + seed * 3)
            val albedo = (p.base + (p.band - p.base) * cap) * (0.9 + 0.12 * grain)
            var col = p.deep + (albedo - p.deep) * diff.pow(0.9)
            col = col * (0.82 + 0.36 * diff)
            val nh = max(0.0, n.dot(half))
            col = col + V3(1.0, 0.97, 0.92) * (nh.pow(46) * 0.65 + nh.pow(9) * 0.11)
            val edge = 1 - z
            val len = max(1e-4, sqrt(x * x + y * y))
            val toward = (x * rimX + y * rimY) / len
            col = col + p.rim * (edge.pow(2.6) * smoothstep(-0.15, 0.9, toward) * p.rimStrength)
            col = col + albedo * (edge.pow(2) * smoothstep(0.2, 1.0, y) * 0.12)
            col = col * ((1 - 0.28 * edge.pow(4) * (1 - diff)) * 0.86)
            if (hash(px * 1.37 + seed * 19, py * 0.91) > 0.9972) {
                val g = 0.55 * (0.3 + 0.7 * diff); col = col + V3(g, g, g)
            }
            val i = (py * SIDE + px) * 4
            out[i] = (col.x.coerceIn(0.0, 1.0) * alpha * 255).toInt()
            out[i + 1] = (col.y.coerceIn(0.0, 1.0) * alpha * 255).toInt()
            out[i + 2] = (col.z.coerceIn(0.0, 1.0) * alpha * 255).toInt()
            out[i + 3] = (alpha * 255).toInt()
        }
        return out
    }

    /** Three box blurs (each horizontal then vertical, edges extended) approximate a Gaussian. */
    fun blur(pixels: IntArray, sigma: Double): IntArray {
        if (sigma < 0.5) return pixels
        val radius = sigma.roundToInt()
        var a = pixels.copyOf()
        var b = IntArray(pixels.size)
        repeat(3) {
            boxPass(a, b, radius, horizontal = true)
            boxPass(b, a, radius, horizontal = false)
        }
        return a
    }

    private fun boxPass(src: IntArray, dst: IntArray, radius: Int, horizontal: Boolean) {
        val width = 2 * radius + 1
        for (line in 0 until SIDE) for (ch in 0 until 4) {
            fun at(k: Int): Int {
                val kk = k.coerceIn(0, SIDE - 1)
                return if (horizontal) src[(line * SIDE + kk) * 4 + ch] else src[(kk * SIDE + line) * 4 + ch]
            }
            var sum = 0
            for (k in -radius..radius) sum += at(k)
            for (k in 0 until SIDE) {
                val v = (sum + width / 2) / width
                if (horizontal) dst[(line * SIDE + k) * 4 + ch] = v else dst[(k * SIDE + line) * 4 + ch] = v
                sum += at(k + radius + 1) - at(k - radius)
            }
        }
    }

    /** ARGB_8888 bitmaps hold premultiplied RGBA in memory, so the bytes go in as they are. */
    fun bitmap(pixels: IntArray): Bitmap {
        val bytes = ByteArray(pixels.size) { pixels[it].coerceIn(0, 255).toByte() }
        val bmp = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
        return bmp
    }

    /** Built once per process, like the Swift version's shared sprites. */
    @Volatile var cachedSprites: List<List<Bitmap>>? = null
        private set

    /**
     * Every palette at every blur level. Slow (seconds on a slow device when done one palette after
     * another), so the palettes are built in parallel off the main thread, and only once.
     */
    suspend fun sprites(): List<List<Bitmap>> = cachedSprites ?: coroutineScope {
        palettes.mapIndexed { index, palette ->
            async(Dispatchers.Default) {
                val sharp = shade(palette, index)
                blurLevels.map { level -> bitmap(blur(sharp, level * DIAMETER)) }
            }
        }.awaitAll()
    }.also { cachedSprites = it }

    // Scene

    class Sphere(val z: Double, val angle: Double, val distance: Double, val radius: Double, val palette: Int)
    class Speck(val x: Double, val y: Double, val z: Double, val size: Double, val rate: Double, val phase: Double)
    class Star(val u: Double, val v: Double, val size: Double, val brightness: Double, val blue: Boolean, val rate: Double, val phase: Double, val twinkles: Boolean)

    private val weights = intArrayOf(0, 0, 1, 2, 2, 3, 4, 5, 0, 1) // ember and teal a little more often

    val spheres = List(56) { i ->
        Sphere(
            z = i * 0.56 + hash(i, 1) * 0.4,
            angle = i * 2.39996 + hash(i, 2) * 0.6,
            distance = 0.75 + 1.5 * hash(i, 3),
            radius = 0.45 + 0.35 * hash(i, 4),
            palette = weights[(hash(i, 5) * 10).toInt() % 10],
        )
    }

    /** Fourteen streaks of dust, 44 glints each, drifting with the spheres. */
    val specks: List<Speck> = (0 until 14).flatMap { j ->
        val zc = j * 2.2 + hash(j, 21) * 1.5
        val ac = hash(j, 22) * 6.283; val dc = 0.7 + 1.2 * hash(j, 23)
        val cx = dc * cos(ac); val cy = dc * sin(ac); val tx = -sin(ac); val ty = cos(ac)
        (0 until 44).map { k ->
            val s = j * 44 + k
            val u = hash(s, 24) * 2 - 1
            val along = u * 1.2; val across = (hash(s, 25) - 0.5) * 0.16 * (1 - abs(u) * 0.5)
            Speck(
                x = cx + tx * along + cos(ac) * across, y = cy + ty * along + sin(ac) * across, z = zc + u * 0.6,
                size = 0.5 + hash(s, 26), rate = (14 + (hash(s, 27) * 20).toInt()).toDouble(), phase = hash(s, 28) * 6.283,
            )
        }
    }

    val stars = List(260) { i ->
        Star(
            u = hash(i, 31), v = hash(i, 32), size = 0.35 + 0.75 * hash(i, 33).pow(3),
            brightness = 0.22 + 0.7 * hash(i, 34).pow(2), blue = hash(i, 35) > 0.82,
            rate = (4 + (hash(i, 36) * 9).toInt()).toDouble(), phase = hash(i, 37) * 6.283, twinkles = hash(i, 38) > 0.62,
        )
    }

    // Mark (in a 100-unit box; the planet sits at its centre)

    const val PLANET_RADIUS = 17.0
    const val STROKE = 7.5
    /** Inner and outer orbit: radius, start and end angle in degrees (y down, clockwise). */
    val orbits = listOf(doubleArrayOf(30.0, 120.0, 330.0), doubleArrayOf(43.5, 160.0, 345.0))
    const val MOON_ANGLE = 8.0
    const val MOON_RADIUS = 5.5
    /** The planet's offset from the mark's visual centre, in mark units. */
    const val VISUAL_OFFSET_X = -1.0
    const val VISUAL_OFFSET_Y = 9.0

    /** An orbit drawn from its middle outwards: progress 0 is nothing, 1 the whole arc. */
    fun orbitPath(orbit: DoubleArray, progress: Double): android.graphics.Path {
        val path = android.graphics.Path()
        if (progress <= 0.001) return path
        val mid = (orbit[1] + orbit[2]) / 2; val halfSweep = (orbit[2] - orbit[1]) / 2 * progress
        val a0 = (mid - halfSweep) * Math.PI / 180; val a1 = (mid + halfSweep) * Math.PI / 180
        val steps = max(2, (48 * progress).toInt())
        for (s in 0..steps) {
            val a = a0 + (a1 - a0) * s / steps
            val x = (orbit[0] * cos(a)).toFloat(); val y = (orbit[0] * sin(a)).toFloat()
            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        return path
    }

}
