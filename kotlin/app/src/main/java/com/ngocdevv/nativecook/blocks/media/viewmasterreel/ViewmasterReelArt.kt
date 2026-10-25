package com.ngocdevv.nativecook.blocks.media.viewmasterreel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 Viewmaster Reel — layout, the reel's slides, and the static art drawn once into bitmaps off the
 main thread (a port of viewmasterArt.ts): the room (slate gradient, grain, vignette, the reel's
 shadow, the red bakelite bezel), the cardboard reel itself (cream card, printed ring, seven film
 chips, rim notches, centre hole), the lens overlay of the window, the gate and the lever.
 */

internal class ViewmasterSlide(val photo: Int, val title: String, val place: String, val crop: IntArray)

/** Seven slides from six photos (800 × 600 px); the last is a closer crop of the river. */
internal val viewmasterSlides = listOf(
    ViewmasterSlide(0, "Vestrahorn at dusk", "Stokksnes · 64.25° N 14.97° W", intArrayOf(0, 0, 800, 600)),
    ViewmasterSlide(1, "Seljalandsfoss", "South Coast · 63.62° N 19.99° W", intArrayOf(0, 0, 800, 600)),
    ViewmasterSlide(2, "Kirkjufell, first light", "Snæfellsnes · 64.94° N 23.31° W", intArrayOf(0, 0, 800, 600)),
    ViewmasterSlide(3, "Braided rivers", "Fjallabak · 63.98° N 19.06° W", intArrayOf(0, 0, 800, 600)),
    ViewmasterSlide(4, "Route 1 after midnight", "Eastfjords · 65.26° N 14.39° W", intArrayOf(0, 0, 800, 600)),
    ViewmasterSlide(5, "Behind the falls", "Gljúfrabúi trail · 63.62° N 19.98° W", intArrayOf(0, 0, 800, 600)),
    ViewmasterSlide(3, "The golden ridge", "Landmannalaugar · 63.99° N 19.06° W", intArrayOf(240, 40, 480, 360)),
)

private val viewmasterPhotoNames = listOf("vestrahorn", "falls", "kirkjufell", "river", "road", "cave")

internal const val VIEWMASTER_HEADER_H = 92f
internal const val VIEWMASTER_CAPTION_H = 60f
internal const val VIEWMASTER_LEVER_REST = -110f
internal const val VIEWMASTER_LEVER_PULLED = -176f

internal class ViewmasterReelLayout(val W: Float, val H: Float, top: Float) {
    val bezel: RectF
    val win: RectF
    val captionY: Float
    val cx: Float
    val cy: Float
    val r: Float
    val px: Float
    val py: Float
    val len: Float

    init {
        val bx = 20f
        val bw = W - 40f
        val bh = (bw * 0.75f).roundToInt().toFloat()
        val by = top + 12f + VIEWMASTER_HEADER_H
        val pad = 12f
        captionY = by + bh + 12f
        r = min(170f, W * 0.43f)
        cy = max(captionY + VIEWMASTER_CAPTION_H + 18f + r, H - r - 10f)
        cx = W / 2f
        bezel = RectF(bx, by, bx + bw, by + bh)
        win = RectF(bx + pad, by + pad, bx + bw - pad, by + bh - pad)
        px = W - 18f
        py = cy - r * 0.26f
        len = min(86f, r * 0.52f)
    }
}

internal fun viewmasterArgb(r: Int, g: Int, b: Int, a: Float = 1f) =
    ((a * 255f + 0.5f).toInt().coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b

internal fun viewmasterHex(hex: Long) = (0xFF000000 or hex).toInt()

private fun blur(sigma: Float) = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)

/*
 Grain "multiply": PorterDuff.Mode.MULTIPLY on Android also multiplies alpha (Sa·Da), so a 10 % grain
 left the layer nearly transparent instead of darkening it as Skia's Multiply does. Multiplying by a
 grey v at opacity a is D·(1 − a(1 − v)): a black tile of alpha 1 − v drawn SRC_ATOP at a does exactly
 that and keeps the layer's alpha.
 */
private fun grainTile(seed: Int, darken: Boolean = false): Bitmap {
    val n = 160
    val rnd = Random(seed)
    val px = IntArray(n * n) {
        val v = (rnd.nextFloat() * 255f).toInt()
        if (darken) (255 - v) shl 24 else (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
}

private fun grainPaint(seed: Int, alpha: Float, mode: PorterDuff.Mode) = Paint(Paint.FILTER_BITMAP_FLAG).apply {
    val multiply = mode == PorterDuff.Mode.MULTIPLY
    shader = BitmapShader(grainTile(seed, darken = multiply), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    this.alpha = (alpha * 255f).toInt()
    xfermode = PorterDuffXfermode(if (multiply) PorterDuff.Mode.SRC_ATOP else mode)
}

private fun canvasFor(w: Float, h: Float, scale: Float): Pair<Bitmap, Canvas> {
    val b = Bitmap.createBitmap(ceil(w * scale).toInt().coerceAtLeast(1), ceil(h * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    c.scale(scale, scale)
    return b to c
}

internal class ViewmasterReelArt(
    val photos: List<Bitmap>,
    val room: Bitmap,
    val reel: Bitmap,
    /** Vignette, glass streak and the dark inner edge of the window, in window space. */
    val lens: Bitmap,
    /** The non-turning light on the reel and the gate bracket at 12 o'clock. */
    val over: Bitmap,
    /** The lever in its own space (pivot at LEVER_PAD, LEVER_PAD), turned per frame. */
    val lever: Bitmap,
)

internal const val VIEWMASTER_LEVER_PAD = 32f

internal fun viewmasterBake(context: Context, L: ViewmasterReelLayout, scale: Float): ViewmasterReelArt {
    val photos = viewmasterPhotoNames.map { n ->
        context.assets.open("viewmasterreel/viewmasterreel_$n.jpg").use { s ->
            BitmapFactory.decodeStream(s, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })!!
        }
    }
    return ViewmasterReelArt(photos, bakeRoom(L, scale), bakeReel(L.r, photos, scale), bakeLens(L, scale), bakeOver(L, scale), bakeLever(L, scale))
}

/** Room background, the reel's shadow and the viewer bezel. */
private fun bakeRoom(L: ViewmasterReelLayout, scale: Float): Bitmap {
    val (bmp, c) = canvasFor(L.W, L.H, scale)
    val W = L.W
    val H = L.H
    val B = L.bezel
    val Wn = L.win
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = LinearGradient(0f, 0f, W * 0.3f, H, intArrayOf(viewmasterHex(0x1A2731), viewmasterHex(0x121C23), viewmasterHex(0x0D1418)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, W, H, p)
    p.shader = RadialGradient(W / 2f, B.centerY(), W * 0.9f, intArrayOf(viewmasterArgb(120, 150, 170, 0.16f), viewmasterArgb(80, 110, 130, 0.05f), 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, W, H, p)
    p.shader = null

    // Reel shadow on the table: soft ambient + tight contact.
    c.drawCircle(L.cx + 2f, L.cy + 14f, L.r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = viewmasterArgb(0, 0, 0, 0.5f); maskFilter = blur(18f) })
    c.drawCircle(L.cx, L.cy + 2f, L.r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = viewmasterArgb(0, 0, 0, 0.55f); maskFilter = blur(2.5f) })

    // Viewer bezel: red bakelite, two shadows, a lit top edge, a black recess around the window.
    c.drawRoundRect(B.left, B.top + 16f, B.right, B.bottom + 16f, 30f, 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = viewmasterArgb(0, 0, 0, 0.5f); maskFilter = blur(20f) })
    c.drawRoundRect(B.left, B.top + 2f, B.right, B.bottom + 2f, 30f, 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = viewmasterArgb(0, 0, 0, 0.6f); maskFilter = blur(2f) })
    p.shader = LinearGradient(B.left, B.top, B.left + B.width() * 0.4f, B.bottom, intArrayOf(viewmasterHex(0xD24A3C), viewmasterHex(0xA9322A), viewmasterHex(0x7E2019)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    c.drawRoundRect(B, 30f, 30f, p)
    p.shader = LinearGradient(0f, B.top, 0f, B.bottom, intArrayOf(viewmasterArgb(255, 230, 220, 0.28f), viewmasterArgb(255, 230, 220, 0f), viewmasterArgb(0, 0, 0, 0.18f)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
    c.drawRoundRect(B, 30f, 30f, p)
    p.shader = null
    c.drawRoundRect(B.left + 0.5f, B.top + 0.5f, B.right - 0.5f, B.bottom - 0.5f, 29.5f, 29.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = viewmasterArgb(255, 215, 200, 0.35f) })
    c.drawRoundRect(Wn.left - 3f, Wn.top - 3f, Wn.right + 3f, Wn.bottom + 3f, 21f, 21f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = viewmasterHex(0x0B0A0A) })
    c.drawRoundRect(Wn.left - 3.6f, Wn.top - 3.6f, Wn.right + 3.6f, Wn.bottom + 3.6f, 21.6f, 21.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f; color = viewmasterArgb(255, 190, 170, 0.22f) })
    // Two screw heads, for scale.
    val screw = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(0f, 0f, 4f, intArrayOf(viewmasterHex(0xF0E4DA), viewmasterHex(0x8E7F76)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP) }
    for (sx in floatArrayOf(B.centerX() - 34f, B.centerX() + 34f)) {
        c.save(); c.translate(sx, B.top + 6f); c.drawCircle(0f, 0f, 2.6f, screw); c.restore()
    }

    c.drawRect(0f, 0f, W, H, grainPaint(3, 0.05f, PorterDuff.Mode.OVERLAY))
    p.shader = RadialGradient(W / 2f, H * 0.45f, H * 0.75f, intArrayOf(0, viewmasterArgb(0, 0, 0, 0.15f), viewmasterArgb(0, 0, 0, 0.55f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, W, H, p)
    return bmp
}

/** The reel, centred in a square of side 2r, slot 0 at 12 o'clock and the rest clockwise. */
private fun bakeReel(r: Float, photos: List<Bitmap>, scale: Float): Bitmap {
    val (bmp, c) = canvasFor(r * 2f, r * 2f, scale)
    c.translate(r, r)
    val card = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(-r * 0.3f, -r * 0.35f, r * 1.5f, intArrayOf(viewmasterHex(0xF6EEDD), viewmasterHex(0xECE1CA), viewmasterHex(0xDCCDB1)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }
    c.drawCircle(0f, 0f, r, card)
    // Paper grain on the card only.
    c.save()
    c.clipPath(Path().apply { addCircle(0f, 0f, r, Path.Direction.CW) })
    c.drawRect(-r, -r, r, r, grainPaint(11, 0.09f, PorterDuff.Mode.MULTIPLY))
    c.restore()
    // Printed rings.
    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = viewmasterArgb(122, 32, 30, 0.85f); strokeWidth = 1.4f }
    c.drawCircle(0f, 0f, r * 0.93f, ink)
    ink.strokeWidth = 0.8f
    c.drawCircle(0f, 0f, r * 0.81f, ink)
    c.drawCircle(0f, 0f, r * 0.23f, ink)

    val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; textSize = max(7f, r * 0.06f); color = viewmasterArgb(122, 32, 30, 0.9f) }
    val phrase = "ICELAND · RING ROAD · JUNE 2025 · REEL 01 · "
    val phraseW = tp.measureText(phrase)
    val tr = r * 0.85f
    // As many copies of the phrase as fit with a little tracking, spread evenly round the ring.
    val copies = max(1, floor((2f * PI.toFloat() * tr) / (phraseW * 1.18f)).toInt())
    val text = phrase.repeat(copies)
    val widths = FloatArray(text.length)
    tp.getTextWidths(text, widths)
    val total = widths.sum()
    val spread = (2f * PI.toFloat() * tr) / total
    var acc = 0f
    for (i in text.indices) {
        val w = widths[i] * spread
        val a = (acc + w / 2f) / tr - PI.toFloat() / 2f
        c.save()
        c.translate(cos(a) * tr, sin(a) * tr)
        c.rotate(Math.toDegrees(a.toDouble()).toFloat() + 90f)
        c.drawText(text, i, i + 1, -widths[i] / 2f, tp.textSize * 0.36f, tp)
        c.restore()
        acc += w
    }

    // Seven film chips, image top towards the rim, with a number on the inner side.
    val cw = r * 0.3f
    val ch = cw * 0.75f
    val rc = r * 0.56f
    val chipShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = viewmasterArgb(60, 40, 20, 0.35f); maskFilter = blur(1.2f) }
    val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = viewmasterHex(0x2A2421) }
    val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(-cw / 2f, -ch / 2f, cw / 2f, ch / 2f, intArrayOf(viewmasterArgb(255, 255, 255, 0.28f), viewmasterArgb(255, 255, 255, 0f), viewmasterArgb(255, 255, 255, 0.08f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }
    val np = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; textSize = max(8f, r * 0.065f); color = viewmasterArgb(122, 32, 30, 0.95f) }
    val img = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val n = viewmasterSlides.size
    for (k in 0 until n) {
        c.save()
        c.rotate(k * 360f / n)
        c.translate(0f, -rc)
        c.drawRoundRect(-cw / 2f - 2f, -ch / 2f - 1f, cw / 2f + 2f, ch / 2f + 3f, 2f, 2f, chipShadow)
        c.drawRoundRect(-cw / 2f - 2f, -ch / 2f - 2f, cw / 2f + 2f, ch / 2f + 2f, 2f, 2f, frame)
        val sl = viewmasterSlides[k]
        val cr = sl.crop
        c.drawBitmap(photos[sl.photo], Rect(cr[0], cr[1], cr[0] + cr[2], cr[1] + cr[3]), RectF(-cw / 2f, -ch / 2f, cw / 2f, ch / 2f), img)
        c.drawRect(-cw / 2f, -ch / 2f, cw / 2f, ch / 2f, shine)
        val label = "${k + 1}"
        c.drawText(label, -np.measureText(label) / 2f, ch / 2f + r * 0.13f, np)
        c.restore()
    }
    // Notches in the rim between the chips (cut through the card).
    val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    for (k in 0 until n) {
        val a = ((k + 0.5f) * 2f * PI.toFloat()) / n - PI.toFloat() / 2f
        c.drawCircle(cos(a) * r, sin(a) * r, r * 0.035f, cut)
    }
    // Centre hole with a pressed rim.
    c.drawCircle(0f, 0f, r * 0.085f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = viewmasterArgb(110, 80, 50, 0.35f) })
    c.drawCircle(0f, 0f, r * 0.065f, cut)
    return bmp
}

/** Lens over the window: darker edges, a faint glass streak, a dark blurred inner edge. */
private fun bakeLens(L: ViewmasterReelLayout, scale: Float): Bitmap {
    val Wn = L.win
    val w = Wn.width()
    val h = Wn.height()
    val (bmp, c) = canvasFor(w, h, scale)
    c.clipPath(Path().apply { addRoundRect(0f, 0f, w, h, 18f, 18f, Path.Direction.CW) })
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = RadialGradient(w / 2f, h / 2f, w * 0.72f, intArrayOf(0, viewmasterArgb(0, 0, 0, 0.08f), viewmasterArgb(0, 0, 0, 0.55f)), floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w, h, p)
    p.shader = LinearGradient(0f, 0f, w, h, intArrayOf(viewmasterArgb(255, 255, 255, 0f), viewmasterArgb(255, 255, 255, 0.10f), viewmasterArgb(255, 255, 255, 0f)), floatArrayOf(0.25f, 0.36f, 0.48f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w, h, p)
    c.drawRoundRect(0f, 0f, w, h, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 10f; color = viewmasterArgb(0, 0, 0, 0.55f); maskFilter = blur(6f) })
    return bmp
}

/** Light that does not turn with the reel, and the gate at 12 o'clock (screen space, transparent elsewhere). */
private fun bakeOver(L: ViewmasterReelLayout, scale: Float): Bitmap {
    val (bmp, c) = canvasFor(L.W, L.H, scale)
    val r = L.r
    c.drawCircle(L.cx, L.cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(L.cx - r * 0.4f, L.cy - r * 0.5f, r * 1.6f, intArrayOf(viewmasterArgb(255, 252, 240, 0.22f), viewmasterArgb(255, 252, 240, 0f), viewmasterArgb(40, 20, 0, 0.16f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    })
    val chipW = r * 0.3f
    val chipH = chipW * 0.75f
    val gateY = L.cy - r * 0.56f
    val x0 = L.cx - chipW / 2f
    val y0 = gateY - chipH / 2f
    c.drawRoundRect(x0 - 7f, y0 - 7f, x0 + chipW + 7f, y0 + chipH + 7f, 5f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = viewmasterArgb(0, 0, 0, 0.35f); maskFilter = blur(4f) })
    c.drawRoundRect(x0 - 6f, y0 - 6f, x0 + chipW + 6f, y0 + chipH + 6f, 5f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = viewmasterHex(0x2B2321) })
    c.drawRoundRect(x0 - 3.5f, y0 - 3.5f, x0 + chipW + 3.5f, y0 + chipH + 3.5f, 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.8f; color = viewmasterArgb(255, 220, 200, 0.35f) })
    return bmp
}

/** The lever: red bakelite arm, its shadow and the knob, with the pivot at (PAD, PAD + 0) of the bitmap's row centre. */
private fun bakeLever(L: ViewmasterReelLayout, scale: Float): Bitmap {
    val pad = VIEWMASTER_LEVER_PAD
    val len = L.len
    val (bmp, c) = canvasFor(len + pad * 2f, pad * 2f, scale)
    c.translate(pad, pad)
    c.drawRoundRect(2f, -2f, 2f + len, 12f, 7f, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = viewmasterArgb(0, 0, 0, 0.45f); maskFilter = blur(5f) })
    c.drawRoundRect(0f, -7f, len, 7f, 7f, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, -7f, 0f, 7f, intArrayOf(viewmasterHex(0xE26A5A), viewmasterHex(0xB3362C), viewmasterHex(0x7E2019)), null, Shader.TileMode.CLAMP)
    })
    c.drawCircle(len, 0f, 15f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(len - 5f, -6f, 18f, intArrayOf(viewmasterHex(0xF08A7A), viewmasterHex(0xC23E33), viewmasterHex(0x7A1E18)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    })
    return bmp
}
