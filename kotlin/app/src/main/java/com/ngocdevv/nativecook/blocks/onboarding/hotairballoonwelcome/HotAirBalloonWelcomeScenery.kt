package com.ngocdevv.nativecook.blocks.onboarding.hotairballoonwelcome

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Hot Air Balloon Welcome — the scenery, as in balloonScenery.ts: screen geometry, and the sky,
// hills and cloud sprites that are baked once (at up to 2×) and then only moved with parallax.
// All drawing is in dp; a bake scales its canvas by its pixel scale first.

/** Mouth to basket bottom (dp). */
internal const val BELOW = 66f

/** Screen geometry. */
internal class BalloonGeo(val w: Float, val h: Float, val ground: Float, ceiling: Float) {
    /** Envelope height, widest radius, mouth radius. */
    val e = min(190f, h * 0.22f)
    private val top = ground - BELOW - e
    /** The screen rise per step and the world scroll. */
    val rise = max(30f, (top - ceiling) / 2f)
    val scroll = 120f
    val r = e * 0.44f
    val rm = e * 0.085f
}

internal class BalloonCloud(val x: Float, val y: Float, val par: Float, val scale: Float, val alpha: Float, val front: Boolean, val amp: Float, val phase: Float, val sprite: Int)

internal val BalloonClouds = listOf(
    BalloonCloud(0.12f, 150f, 0.25f, 0.7f, 0.5f, false, 10f, 0.3f, 1),
    BalloonCloud(0.78f, 215f, 0.32f, 0.85f, 0.6f, false, 12f, 2.1f, 0),
    BalloonCloud(0.02f, 40f, 0.9f, 1.15f, 0.85f, false, 14f, 4.0f, 2),
    BalloonCloud(0.7f, -60f, 1.1f, 1.35f, 0.9f, false, 16f, 1.2f, 0),
    BalloonCloud(0.3f, -150f, 1.25f, 1.5f, 0.72f, true, 18f, 5.1f, 1),
    BalloonCloud(0.92f, -230f, 1.3f, 1.05f, 0.8f, true, 12f, 3.3f, 2),
)

internal val HillTops = floatArrayOf(-150f, -112f, -80f, -36f)
internal val HillPar = floatArrayOf(0.18f, 0.4f, 0.7f, 1.0f)

internal fun balloonHash(n: Double): Float {
    val x = sin(n * 12.9898 + 78.233) * 43758.5453
    return (x - floor(x)).toFloat()
}

private fun argb(r: Int, g: Int, b: Int, a: Float): Int = (((a.coerceIn(0f, 1f) * 255f + 0.5f).toInt()) shl 24) or (r shl 16) or (g shl 8) or b
private fun hex(c: Long): Int = c.toInt()
private fun blur(sigma: Float) = BlurMaskFilter(max(0.01f, (sigma - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)

/** A tile of film grain (white or black by a hashed value), standing in for Skia's overlay of fractal noise. */
private val grainTile: Bitmap by lazy {
    val n = 96
    val px = IntArray(n * n)
    for (y in 0 until n) {
        for (x in 0 until n) {
            var h = x * 374761393 + y * 668265263 + 7771
            h = (h xor (h ushr 13)) * 1274126177
            h = h xor (h ushr 16)
            val v = (h and 0xFFFF) / 65535f
            val a = (abs(v - 0.5f) * 2f * 255f).toInt().coerceIn(0, 255)
            px[y * n + x] = (a shl 24) or (if (v > 0.5f) 0xFFFFFF else 0)
        }
    }
    Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
}

/** The dawn sky, sun, last stars and grain. [pixel] is dp → pixels of the target (for the grain). */
internal fun drawBalloonSky(c: Canvas, w: Float, h: Float, horizon: Float, pixel: Float) {
    val sky = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, horizon + 40f, intArrayOf(hex(0xFF262B5A), hex(0xFF4B3F7E), hex(0xFF9A5F93), hex(0xFFE58A98), hex(0xFFF8C49A)), floatArrayOf(0f, 0.32f, 0.6f, 0.84f, 1f), Shader.TileMode.CLAMP)
    }
    c.drawRect(0f, 0f, w, h, sky)
    // The sun, low on the right, with a wide warm bloom.
    val sx = w * 0.72f
    val sy = horizon - 6f
    val bloom = Paint().apply {
        shader = RadialGradient(sx, sy, w * 0.9f, intArrayOf(argb(255, 214, 160, 0.55f), argb(255, 170, 140, 0.18f), argb(255, 150, 140, 0f)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
    }
    c.drawRect(0f, 0f, w, h, bloom)
    val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(sx, sy, 26f, intArrayOf(hex(0xFFFFF6DE), hex(0xFFFFE2A6), argb(255, 200, 140, 0f)), floatArrayOf(0f, 0.75f, 1f), Shader.TileMode.CLAMP)
    }
    c.drawCircle(sx, sy, 26f, disc)
    // The last stars near the top.
    val star = Paint(Paint.ANTI_ALIAS_FLAG)
    for (i in 0 until 40) {
        val y = balloonHash(i * 3.7) * horizon * 0.42f
        star.color = argb(255, 246, 230, 0.15f + 0.45f * balloonHash(i * 1.3) * (1f - y / (horizon * 0.42f)))
        c.drawCircle(balloonHash(i * 9.1) * w, y, 0.5f + balloonHash(i * 5.3) * 0.8f, star)
    }
    val grain = Paint().apply {
        shader = BitmapShader(grainTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(1f / pixel, 1f / pixel) })
        }
        alpha = (0.06f * 0.6f * 255f).roundToInt()
    }
    c.drawRect(0f, 0f, w, h, grain)
}

private fun ridgePath(w: Float, top: Float, amp: Float, seed: Double, bottom: Float, n: Int): Path {
    val b = Path()
    b.moveTo(-12f, bottom)
    var px = -12f
    var py = top + amp * balloonHash(seed)
    b.lineTo(px, py)
    for (i in 1..n) {
        val x = -12f + (w + 24f) * i / n
        val y = top + amp * (0.5f + 0.5f * sin(i * 1.7 + seed).toFloat() * balloonHash(seed + i)) + amp * 0.3f * balloonHash(seed + i * 2.3)
        b.quadTo((px + x) / 2f, (py + y) / 2f - amp * 0.25f, x, y)
        px = x
        py = y
    }
    b.lineTo(w + 12f, bottom)
    b.close()
    return b
}

private val HillColors = arrayOf(
    intArrayOf(hex(0xFFC291A4), hex(0xFFA57D9B), hex(0xFF8A6D90)),
    intArrayOf(hex(0xFF93708F), hex(0xFF735C82), hex(0xFF5A4C72)),
    intArrayOf(hex(0xFF4D5672), hex(0xFF3D4A62), hex(0xFF2C3850)),
    intArrayOf(hex(0xFF33463F), hex(0xFF283A34), hex(0xFF1C2A26)),
)
private val HillAmps = floatArrayOf(46f, 34f, 26f, 14f)

/**
 * One layer of hills: a ridge filled with a three-stop gradient, a rim of dawn light on top, trees
 * on the nearer ones. Drawn with its ridge at y = 60 of the layer.
 */
internal fun drawBalloonHill(c: Canvas, w: Float, h: Float, i: Int) {
    val amp = HillAmps[i]
    val path = ridgePath(w, 60f, amp, 3.0 + i * 11, h, 6 + i * 2)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 40f, 0f, 60f + 220f, HillColors[i], floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
    }
    c.drawPath(path, fill)
    // Dawn light along the ridge, warmer to the right where the sun is.
    c.save()
    c.clipPath(path)
    val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f + i
        shader = LinearGradient(0f, 0f, w, 0f, intArrayOf(argb(255, 200, 170, 0.05f), argb(255, 200, 160, 0.22f), argb(255, 214, 160, 0.45f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        maskFilter = blur(2f)
    }
    c.drawPath(path, rim)
    if (i >= 2) {
        // Tree clusters and hedgerows.
        val tree = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (i == 2) argb(30, 40, 58, 0.75f) else argb(16, 28, 24, 0.85f) }
        for (k in 0 until if (i == 2) 26 else 18) {
            val x = balloonHash(k * 7.1 + i) * w
            val y = 60f + amp * 0.9f + balloonHash(k * 3.3 + i) * 40f
            val r = (if (i == 2) 4f else 6f) + balloonHash(k * 1.7) * 5f
            c.drawCircle(x, y, r, tree)
            c.drawCircle(x + r * 0.8f, y + 1f, r * 0.8f, tree)
        }
        val field = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = argb(255, 220, 190, 0.06f)
        }
        for (k in 0 until 6) {
            val b = Path()
            val y = 60f + amp + 30f + k * 18f
            b.moveTo(0f, y)
            b.quadTo(w / 2f, y + 10f * sin(k.toFloat()), w, y - 6f)
            c.drawPath(b, field)
        }
    }
    c.restore()
}

private val Puffs = arrayOf(
    arrayOf(floatArrayOf(40f, 44f, 22f), floatArrayOf(66f, 34f, 28f), floatArrayOf(96f, 40f, 24f), floatArrayOf(120f, 48f, 18f), floatArrayOf(24f, 52f, 14f)),
    arrayOf(floatArrayOf(30f, 46f, 18f), floatArrayOf(56f, 38f, 24f), floatArrayOf(86f, 34f, 26f), floatArrayOf(114f, 44f, 20f), floatArrayOf(138f, 52f, 12f)),
    arrayOf(floatArrayOf(36f, 48f, 16f), floatArrayOf(60f, 40f, 22f), floatArrayOf(84f, 44f, 18f), floatArrayOf(104f, 50f, 12f)),
)

/** A cloud sprite (160 × 70 dp). [pixel] is dp → pixels (Skia's blur here does not follow the scale). */
internal fun drawBalloonCloud(c: Canvas, k: Int, pixel: Float) {
    val shape = Path()
    for (p in Puffs[k]) shape.addCircle(p[0], p[1], p[2], Path.Direction.CW)
    shape.addRoundRect(RectF(18f, 44f, 146f, 60f), 8f, 8f, Path.Direction.CW)
    // The union of the puffs, so overlapping circles do not cancel out.
    shape.fillType = Path.FillType.WINDING
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 10f, 0f, 62f, intArrayOf(hex(0xFFFFF1E4), hex(0xFFF7C9C2), hex(0xFFB898B8)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        maskFilter = blur(max(0.6f, 3.5f / pixel))
    }
    c.drawPath(shape, body)
    val lit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        shader = RadialGradient(120f, 24f, 70f, intArrayOf(argb(255, 226, 180, 0.6f), argb(255, 226, 180, 0f)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
    }
    c.save()
    c.clipPath(shape)
    c.drawRect(0f, 0f, 160f, 70f, lit)
    c.restore()
}

internal class BalloonLayer(val image: Bitmap, val y: Float, val height: Float, val par: Float)
internal class BalloonArt(val sky: Bitmap, val hills: List<BalloonLayer>, val clouds: List<Bitmap>)

private fun bake(w: Float, h: Float, scale: Float, draw: (Canvas) -> Unit): Bitmap {
    val bmp = Bitmap.createBitmap(max(1, ceil(w * scale).toInt()), max(1, ceil(h * scale).toInt()), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(scale, scale)
    draw(c)
    return bmp
}

/** The y and height of hill layer i in the screen (dp). */
internal fun balloonHillY(g: BalloonGeo, i: Int) = g.ground + HillTops[i] - 60f
internal fun balloonHillHeight(g: BalloonGeo, i: Int) = g.h - balloonHillY(g, i) + g.scroll * 2f * HillPar[i] + 40f

/** Bakes everything static at up to 2×, off the main thread; checks for cancellation between layers. */
internal suspend fun bakeBalloonArt(g: BalloonGeo, scale: Float): BalloonArt {
    val s = min(2f, scale)
    val horizon = g.ground + HillTops[0] + 60f
    val sky = bake(g.w, g.h, s) { drawBalloonSky(it, g.w, g.h, horizon, s) }
    val hills = ArrayList<BalloonLayer>()
    for (i in 0 until 4) {
        currentCoroutineContext().ensureActive()
        val y = balloonHillY(g, i)
        val height = balloonHillHeight(g, i)
        hills.add(BalloonLayer(bake(g.w, height, s) { drawBalloonHill(it, g.w, height, i) }, y, height, HillPar[i]))
    }
    val clouds = ArrayList<Bitmap>()
    for (k in 0 until 3) {
        currentCoroutineContext().ensureActive()
        clouds.add(bake(160f, 70f, s) { drawBalloonCloud(it, k, s) })
    }
    return BalloonArt(sky, hills, clouds)
}
