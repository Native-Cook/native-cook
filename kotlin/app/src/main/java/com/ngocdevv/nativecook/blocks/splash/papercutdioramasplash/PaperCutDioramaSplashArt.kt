package com.ngocdevv.nativecook.blocks.splash.papercutdioramasplash

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/*
 Paper Cut Diorama Splash — layout and the six layers of cut paper, each baked once into its own
 bitmap off the main thread (with the soft shadow it throws on the layer behind), plus the bare
 paper backing and the light bloom of the cut-out logo. A port of dioramaArt.ts.

 Back to front: a dusk sky (deep blue → violet → peach, a paper moon, a few paper stars), far
 lavender hills, a row of teal pines, green meadow with a winding river, a dark grass edge with
 blades, and the cream front frame with an arched window, the logo (a little arched door) and the
 wordmark "Fable" cut right through it. Layers are 30 dp wider than the screen on each side so
 tilting never shows an edge, and run 40 dp past the bottom.
 */

private const val PAD = 30f

internal class PaperCutLayout(val W: Float, val H: Float, top: Float) {
    val x0 = 26f
    val x1 = W - 26f
    val r = (x1 - x0) / 2f
    val winTop = top + 34f
    val winBottom = max(winTop + r + 120f, H * 0.67f)
    val ew = 50f
    val eh = 68f
    val ex = W / 2f - ew / 2f
    val ey = winBottom + 26f
    val wordY = ey + eh + 60f
    val tagY = ey + eh + 92f
}

internal class PaperCutLayerArt(val bitmap: Bitmap, val x: Float, val y: Float, val w: Float, val h: Float)

internal class PaperCutArt(val layers: List<PaperCutLayerArt>, val bloom: PaperCutLayerArt, val backing: Bitmap)

internal fun paperCutArgb(r: Int, g: Int, b: Int, a: Float = 1f) =
    ((a * 255f + 0.5f).toInt().coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b

internal fun paperCutHex(hex: Long) = (0xFF000000 or hex).toInt()

private fun hash(n: Int): Float {
    val v = sin(n * 91.345f + 7.13f) * 43758.5453f
    return v - floor(v)
}

private class Surface(val bitmap: Bitmap, val c: Canvas, val x: Float, val y: Float, val w: Float, val h: Float)

private fun surfaceFor(L: PaperCutLayout, y: Float, scale: Float): Surface {
    val w = L.W + PAD * 2f
    val h = L.H + 40f - y
    val bmp = Bitmap.createBitmap(ceil(w * scale).toInt(), ceil(h * scale).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(scale, scale)
    c.translate(PAD, -y)
    return Surface(bmp, c, -PAD, y, w, h)
}

private fun Surface.finish() = PaperCutLayerArt(bitmap, x, y, w, h)

/*
 Grain "multiply": PorterDuff.Mode.MULTIPLY on Android also multiplies alpha (Sa·Da), so a 10 % grain
 left the layer nearly transparent instead of darkening it as Skia's Multiply does. Multiplying by a
 grey v at opacity a is D·(1 − a(1 − v)): a black tile of alpha 1 − v drawn SRC_ATOP at a does exactly
 that and keeps the layer's alpha.
 */
private fun noise(seed: Int): Bitmap {
    val n = 160
    val rnd = Random(seed)
    val px = IntArray(n * n) {
        val v = (rnd.nextFloat() * 255f).toInt()
        (255 - v) shl 24
    }
    return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
}

private fun grain(seed: Int, alpha: Float) = Paint(Paint.FILTER_BITMAP_FLAG).apply {
    shader = BitmapShader(noise(seed), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    this.alpha = (alpha * 255f).toInt()
    xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
}

private fun blur(sigma: Float) = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)

/** Fill a cut-paper shape: its shadow on the layer behind, the paper, grain, a lit top edge. */
private fun paper(c: Canvas, path: Path, fill: Paint, L: PaperCutLayout, seed: Int, shadow: Float = 0.32f, lit: Boolean = true) {
    val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paperCutArgb(24, 16, 30, shadow); maskFilter = blur(7f) }
    c.save(); c.translate(0f, -3f); c.drawPath(path, sh); c.restore()
    c.drawPath(path, fill)
    c.save()
    c.clipPath(path)
    c.drawRect(-PAD, 0f, L.W + PAD, L.H + 40f, grain(seed, 0.1f))
    if (lit) c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f; color = paperCutArgb(255, 248, 236, 0.22f) })
    c.restore()
}

private fun ridge(L: PaperCutLayout, step: Float = 6f, y0: (Float) -> Float): Path {
    val b = Path()
    b.moveTo(-PAD, L.H + 40f)
    var x = -PAD
    while (x <= L.W + PAD) { b.lineTo(x, y0(x)); x += step }
    b.lineTo(L.W + PAD, L.H + 40f)
    b.close()
    return b
}

private fun vgrad(y0: Float, y1: Float, cs: LongArray, pos: FloatArray) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    shader = LinearGradient(0f, y0, 0f, y1, IntArray(cs.size) { paperCutHex(cs[it]) }, pos, Shader.TileMode.CLAMP)
}

private fun archPath(x0: Float, top: Float, w: Float, bottom: Float): Path = Path().apply {
    moveTo(x0, bottom)
    lineTo(x0, top + w / 2f)
    arcTo(RectF(x0, top, x0 + w, top + w), 180f, 180f, false)
    lineTo(x0 + w, bottom)
    close()
}

internal fun paperCutBake(L: PaperCutLayout, scale: Float): PaperCutArt {
    val W = L.W
    val span = L.winBottom - L.winTop
    fun at(f: Float) = L.winTop + span * f
    val layers = ArrayList<PaperCutLayerArt>()

    // 1. Sky with a paper moon and stars.
    val sky = surfaceFor(L, L.winTop - 60f, scale)
    run {
        val c = sky.c
        c.drawRect(-PAD, L.winTop - 60f, W + PAD, L.H + 40f, vgrad(L.winTop, L.winBottom, longArrayOf(0x26315A, 0x5B4F86, 0xB07A9C, 0xF0AE88, 0xF7D3A4), floatArrayOf(0f, 0.32f, 0.55f, 0.78f, 1f)))
        val moon = Path().apply { addCircle(W * 0.67f, at(0.4f), 30f, Path.Direction.CW) }
        paper(c, moon, vgrad(at(0.4f) - 30f, at(0.4f) + 30f, longArrayOf(0xFFF4DC, 0xFBE3BC, 0xF4D2A4), floatArrayOf(0f, 0.6f, 1f)), L, 17, 0.25f)
        val star = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paperCutHex(0xF7E7C6) }
        for (i in 0 until 9) {
            val x = 46f + hash(i) * (W - 92f)
            val y = L.winTop + 30f + hash(i + 9) * span * 0.32f
            val r = 2.2f + hash(i + 19) * 3.2f
            val s = Path().apply {
                moveTo(x, y - r * 2f)
                quadTo(x, y, x + r * 2f, y)
                quadTo(x, y, x, y + r * 2f)
                quadTo(x, y, x - r * 2f, y)
                quadTo(x, y, x, y - r * 2f)
                close()
            }
            paper(c, s, star, L, 17, 0.2f)
        }
    }
    layers.add(sky.finish())

    // 2. Far hills.
    val hills = surfaceFor(L, at(0.5f) - 20f, scale)
    paper(
        hills.c,
        ridge(L) { x -> at(0.6f) + 16f * sin(x / 52f + 1f) + 9f * sin(x / 23f + 0.4f) - 22f * exp(-((x - W * 0.3f) * (x - W * 0.3f)) / 3000f) },
        vgrad(at(0.55f), at(0.8f), longArrayOf(0x9A8BC0, 0x7F74A8, 0x6A6394), floatArrayOf(0f, 0.5f, 1f)),
        L, 17,
    )
    layers.add(hills.finish())

    // 3. A row of pines on a low ridge.
    val trees = surfaceFor(L, at(0.52f), scale)
    run {
        fun base(x: Float) = at(0.71f) + 9f * sin(x / 41f + 2.2f)
        val b = Path()
        b.moveTo(-PAD, L.H + 40f)
        var x = -PAD
        while (x <= W + PAD) { b.lineTo(x, base(x)); x += 4f }
        b.lineTo(W + PAD, L.H + 40f)
        b.close()
        var i = 0
        while (i * 23f - PAD < W + PAD) {
            val tx = -PAD + i * 23f + hash(i + 3) * 8f
            val h = 34f + hash(i + 31) * 52f * (if (tx > W * 0.38f && tx < W * 0.62f) 0.55f else 1f)
            val y = base(tx) + 4f
            val w = 9f + h * 0.2f
            // Three tiers of a paper pine.
            for (k in 0 until 3) {
                val ty = y - h + k * h * 0.28f
                val tw = w * (0.55f + k * 0.28f)
                b.moveTo(tx, ty); b.lineTo(tx + tw, ty + h * 0.42f); b.lineTo(tx - tw, ty + h * 0.42f); b.close()
            }
            b.addRect(tx - 1.5f, y - 6f, tx + 1.5f, y + 2f, Path.Direction.CW)
            i++
        }
        // Overlapping tiers: no lit edge, or every tier's outline would show.
        paper(trees.c, b, vgrad(at(0.55f), at(0.8f), longArrayOf(0x3F6470, 0x34545F, 0x2B4652), floatArrayOf(0f, 0.5f, 1f)), L, 17, 0.32f, lit = false)
    }
    layers.add(trees.finish())

    // 4. Meadow with a winding river.
    val meadowTop = at(0.735f)
    val meadow = surfaceFor(L, meadowTop - 20f, scale)
    run {
        val c = meadow.c
        fun edge(x: Float) = meadowTop + 7f * sin(x / 60f + 2f) + 4f * sin(x / 19f)
        paper(c, ridge(L) { edge(it) }, vgrad(meadowTop, L.winBottom + 60f, longArrayOf(0x7E9C68, 0x678A57, 0x4F7347), floatArrayOf(0f, 0.5f, 1f)), L, 17)
        val vx = W * 0.44f
        val vy = edge(vx) + 2f
        val yb = L.H + 40f
        val river = Path().apply {
            moveTo(vx - 3f, vy)
            cubicTo(vx - 26f, vy + 20f, W * 0.7f, vy + 40f, W * 0.5f, vy + 70f)
            cubicTo(W * 0.3f, vy + 100f, W * 0.18f, vy + 120f, W * 0.08f, yb)
            lineTo(W * 0.62f, yb)
            cubicTo(W * 0.5f, vy + 130f, W * 0.6f, vy + 100f, W * 0.74f, vy + 66f)
            cubicTo(W * 0.86f, vy + 38f, vx + 26f, vy + 18f, vx + 3f, vy)
            close()
        }
        paper(c, river, vgrad(vy, L.winBottom, longArrayOf(0xB4D5D9, 0x86B7C6, 0x5F94AB), floatArrayOf(0f, 0.4f, 1f)), L, 17, 0.22f)
        val ripple = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.3f; color = paperCutArgb(255, 250, 238, 0.55f) }
        c.save()
        c.clipPath(river)
        for (i in 0 until 7) {
            val y = vy + 18f + i * 16f
            val x = W * (0.48f + 0.12f * sin(i * 1.7f) - i * 0.03f)
            c.drawLine(x - 14f, y, x + 14f, y + 1f, ripple)
        }
        c.restore()
    }
    layers.add(meadow.finish())

    // 5. Grass edge with blades.
    val grassTop = at(0.925f)
    val grass = surfaceFor(L, grassTop - 40f, scale)
    run {
        val b = Path()
        b.moveTo(-PAD, L.H + 40f)
        var x = -PAD
        var i = 0
        while (x <= W + PAD) {
            val g = grassTop + 10f * sin(x / 70f + 0.6f)
            val h = 8f + hash(i + 7) * 20f
            val lean = (hash(i + 77) - 0.5f) * 7f
            b.lineTo(x, g); b.lineTo(x + 4.5f + lean, g - h); b.lineTo(x + 9f, g)
            x += 9f
            i++
        }
        b.lineTo(W + PAD, L.H + 40f)
        b.close()
        paper(grass.c, b, vgrad(grassTop - 20f, L.H, longArrayOf(0x3E6545, 0x2F5137, 0x22402B), floatArrayOf(0f, 0.3f, 1f)), L, 17, 0.38f)
    }
    layers.add(grass.finish())

    // 6. The front frame: cream paper with the arched window, the logo door and the wordmark cut out.
    val frame = surfaceFor(L, 0f, scale)
    val word = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); textSize = 46f }
    val tag = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.SANS_SERIF, 600, false) else Typeface.DEFAULT_BOLD
        textSize = 13f
        color = paperCutArgb(120, 96, 70, 0.8f)
    }
    val door = archPath(L.ex, L.ey, L.ew, L.ey + L.eh)
    val windowPath = archPath(L.x0, L.winTop, L.r * 2f, L.winBottom)
    val wordX = W / 2f - word.measureText("Fable") / 2f
    run {
        val c = frame.c
        c.drawRect(-PAD, 0f, W + PAD, L.H + 40f, vgrad(0f, L.H, longArrayOf(0xF6EEDF, 0xF1E6D3, 0xE8DAC2), floatArrayOf(0f, 0.6f, 1f)))
        c.drawRect(-PAD, 0f, W + PAD, L.H + 40f, grain(23, 0.09f))
        val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        c.drawPath(windowPath, cut)
        c.drawPath(door, cut)
        word.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        c.drawText("Fable", wordX, L.wordY, word)
        word.xfermode = null
        // The frame's own shadow falling inwards on the scene, and a lit lip on the cut edge.
        c.save()
        c.clipPath(windowPath)
        c.save()
        c.translate(0f, 4f)
        c.drawPath(windowPath, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 14f; color = paperCutArgb(30, 18, 24, 0.42f); maskFilter = blur(9f) })
        c.restore()
        c.restore()
        val lip = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f; color = paperCutArgb(255, 252, 244, 0.85f) }
        c.drawPath(windowPath, lip)
        c.drawPath(door, lip)
        val tagline = "STORIES FOR SMALL HOURS"
        val tw = tag.measureText(tagline) + tagline.length * 2.2f
        var tx = W / 2f - tw / 2f
        for (ch in tagline) {
            val s = ch.toString()
            c.drawText(s, tx, L.tagY, tag)
            tx += tag.measureText(s) + 2.2f
        }
    }
    layers.add(frame.finish())

    // The light through the cut-outs, blurred, to be added over the frame.
    val glow = surfaceFor(L, 0f, scale)
    run {
        val warm = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paperCutArgb(255, 170, 90, 0.85f); maskFilter = blur(12f) }
        glow.c.drawPath(door, warm)
        word.color = paperCutArgb(255, 170, 90, 0.85f)
        word.maskFilter = blur(12f)
        glow.c.drawText("Fable", wordX, L.wordY, word)
    }
    return PaperCutArt(layers, glow.finish(), bakeBacking(L, scale))
}

/** Blank paper: the backing board the layers rise onto. */
private fun bakeBacking(L: PaperCutLayout, scale: Float): Bitmap {
    val bmp = Bitmap.createBitmap(ceil(L.W * scale).toInt(), ceil(L.H * scale).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(scale, scale)
    c.drawRect(0f, 0f, L.W, L.H, vgrad(0f, L.H, longArrayOf(0xF3EBDD, 0xEDE3D2, 0xE4D7C2), floatArrayOf(0f, 0.6f, 1f)))
    c.drawRect(0f, 0f, L.W, L.H, grain(29, 0.1f))
    c.drawRect(0f, 0f, L.W, L.H, Paint().apply {
        shader = RadialGradient(L.W / 2f, L.H * 0.45f, L.H * 0.7f, intArrayOf(paperCutArgb(80, 50, 20, 0f), paperCutArgb(80, 50, 20, 0f), paperCutArgb(80, 50, 20, 0.12f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    })
    return bmp
}
