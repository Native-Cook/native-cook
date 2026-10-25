package com.ngocdevv.nativecook.blocks.cards.filmnegativecard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/*
 Film Negative Card — layout and the static art (a port of filmNegativeArt.ts). The desk with the
 light table, and the soft shadows the strip throws, are drawn once into bitmaps off the main
 thread; per frame only the strip's shader runs.

 Layout (dp = pt), from the screen size and safe area: a header block, then the strip resting on
 the desk, the light table under it, and a footer row. The strip is 35 mm film: frame height =
 strip height × 24 / 35, frame width = 1.5 × frame height, perforation pitch = frame width / 7.58,
 holes 2.8 × 2 mm. On 393 × 852 the strip is 361 × 250.
 */

internal const val FILM_HEADER_H = 112f
internal const val FILM_FOOTER_H = 56f

internal class FilmNegativeLayout(val W: Float, val H: Float, top: Float, bottom: Float) {
    val stripX = 16f
    val stripW: Float
    val stripH: Float
    val restY: Float
    val onY: Float
    /** The table and the lit panel inside its bezel (screen space). */
    val table: RectF
    val panel: RectF
    /** Frame 7 in strip space. */
    val frame: RectF
    val gap: Float
    val perfPitch: Float
    val perfW: Float
    val perfH: Float
    val perfPhase: Float
    val perfTop: Float
    val perfBottom: Float
    val footerY: Float

    init {
        val a = top + 12f + FILM_HEADER_H
        footerY = H - bottom - 16f - FILM_FOOTER_H
        val b = footerY - 16f
        stripW = W - 32f
        stripH = min(250f, min(stripW * 0.7f, (b - a - 36f - 22f) / 2f))
        val tableH = stripH + 36f
        val tableY = b - tableH
        restY = a + max(0f, (tableY - 14f - a - stripH) * 0.5f)
        onY = tableY + 18f
        table = RectF(10f, tableY, W - 10f, tableY + tableH)
        panel = RectF(20f, tableY + 10f, W - 20f, tableY + tableH - 10f)
        val fh = stripH * 24f / 35f
        val fw = fh * 1.5f
        val mm = fw / 36f
        gap = 2f * mm
        val fx = (stripW - fw) / 2f
        val fy = (stripH - fh) / 2f
        frame = RectF(fx, fy, fx + fw, fy + fh)
        perfPitch = 4.75f * mm
        perfH = 2f * mm
        perfW = 2.8f * mm
        perfPhase = fx + perfPitch * 0.5f
        perfTop = fy * 0.36f + perfH * 0.5f + 2f
        perfBottom = stripH - (fy * 0.36f + perfH * 0.5f + 2f)
    }

    /** Where the strip rests: partly over the top of the light table. */
    val homeY get() = restY + FilmNegativeMotion.REST_ON * (onY - restY)
}

/** A blurred black rounded rect (or circle when r = min side / 2), padded by 3 σ, at `scale` px per dp. */
internal class FilmNegativeSoft(val bitmap: Bitmap, val pad: Float, val w: Float, val h: Float)

internal fun filmNegativeSoft(w: Float, h: Float, r: Float, sigma: Float, scale: Float): FilmNegativeSoft {
    val pad = sigma * 3f
    val bw = ceil((w + pad * 2) * scale).toInt().coerceAtLeast(1)
    val bh = ceil((h + pad * 2) * scale).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(scale, scale)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
    }
    c.drawRoundRect(pad, pad, pad + w, pad + h, r, r, p)
    return FilmNegativeSoft(bmp, pad, w, h)
}

internal fun filmNegativeArgb(r: Int, g: Int, b: Int, a: Float = 1f) =
    ((a * 255f + 0.5f).toInt().coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b

internal fun filmNegativeHex(hex: Long) = (0xFF000000 or hex).toInt()

/** A tile of seeded grey noise, drawn repeated in dp space for paper and film grain. */
internal fun filmNegativeGrain(seed: Int): Bitmap {
    val n = 160
    val rnd = Random(seed)
    val px = IntArray(n * n) {
        val v = (rnd.nextFloat() * 255f).toInt()
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
}

internal fun filmNegativeLoadPhoto(context: Context, name: String): Bitmap =
    context.assets.open(name).use { s ->
        BitmapFactory.decodeStream(s, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })!!
    }

internal class FilmNegativeArt(
    val desk: Bitmap,
    val photo: Bitmap,
    val rollShadow: FilmNegativeSoft,
    val ambient: FilmNegativeSoft,
    val contact: FilmNegativeSoft,
    val ringShadow: FilmNegativeSoft,
)

internal fun filmNegativeBake(context: Context, L: FilmNegativeLayout, scale: Float): FilmNegativeArt {
    val photo = filmNegativeLoadPhoto(context, "filmnegativecard/filmnegativecard_portrait.jpg")
    val ringR = L.frame.height() * 0.3f * 1.05f + 4f
    return FilmNegativeArt(
        desk = filmNegativeBakeDesk(L, scale),
        photo = photo,
        rollShadow = filmNegativeSoft(L.stripW - 8f, L.stripH, 6f, 10f, scale),
        ambient = filmNegativeSoft(L.stripW, L.stripH, 8f, 14f, scale),
        contact = filmNegativeSoft(L.stripW - 4f, L.stripH, 5f, 2.5f, scale),
        ringShadow = filmNegativeSoft(ringR * 2, ringR * 2, ringR, 9f, scale),
    )
}

/** The desk (warm near-black, safelight glow, grain, vignette) and the light table, at `scale` px per dp. */
private fun filmNegativeBakeDesk(L: FilmNegativeLayout, scale: Float): Bitmap {
    val bmp = Bitmap.createBitmap(ceil(L.W * scale).toInt(), ceil(L.H * scale).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(scale, scale)
    val W = L.W
    val H = L.H
    val T = L.table
    val P = L.panel
    val p = Paint(Paint.ANTI_ALIAS_FLAG)

    // Desk: three-stop vertical gradient, a dim red-amber safelight glow at the top right.
    p.shader = LinearGradient(0f, 0f, 0f, H, intArrayOf(filmNegativeHex(0x1C1512), filmNegativeHex(0x16110F), filmNegativeHex(0x110D0C)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, W, H, p)
    p.shader = RadialGradient(W * 0.92f, -20f, W * 0.95f, intArrayOf(filmNegativeArgb(196, 74, 42, 0.22f), filmNegativeArgb(150, 52, 32, 0.08f), 0), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, W, H, p)
    p.shader = null

    // Light spilling out of the table onto the desk.
    val spill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = filmNegativeArgb(255, 214, 150, 0.24f)
        maskFilter = BlurMaskFilter(sigmaToRadius(30f), BlurMaskFilter.Blur.NORMAL)
    }
    c.drawRoundRect(T.left - 6f, T.top - 4f, T.right + 6f, T.bottom + 10f, 22f, 22f, spill)

    // The table: two shadows, an aluminium bezel with a lit top edge.
    val amb = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = filmNegativeArgb(0, 0, 0, 0.45f)
        maskFilter = BlurMaskFilter(sigmaToRadius(16f), BlurMaskFilter.Blur.NORMAL)
    }
    c.drawRoundRect(T.left, T.top + 14f, T.right, T.bottom + 14f, 18f, 18f, amb)
    val contact = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = filmNegativeArgb(0, 0, 0, 0.6f)
        maskFilter = BlurMaskFilter(sigmaToRadius(2f), BlurMaskFilter.Blur.NORMAL)
    }
    c.drawRoundRect(T.left, T.top + 1.5f, T.right, T.bottom + 1.5f, 18f, 18f, contact)
    p.shader = LinearGradient(0f, T.top, 0f, T.bottom, intArrayOf(filmNegativeHex(0x4A4541), filmNegativeHex(0x2B2725), filmNegativeHex(0x38332F)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    c.drawRoundRect(T, 18f, 18f, p)
    val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        shader = LinearGradient(0f, T.top, 0f, T.bottom, intArrayOf(filmNegativeArgb(255, 244, 228, 0.30f), filmNegativeArgb(255, 244, 228, 0.04f), filmNegativeArgb(0, 0, 0, 0.35f)), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
    }
    c.drawRoundRect(T.left + 0.5f, T.top + 0.5f, T.right - 0.5f, T.bottom - 0.5f, 17.5f, 17.5f, rim)

    // The panel: opal glass lit from below, warm in the middle, a touch cooler at the edges.
    p.shader = RadialGradient(P.centerX(), P.centerY(), max(P.width(), P.height()) * 0.62f, intArrayOf(filmNegativeHex(0xFFFBF2), filmNegativeHex(0xFBF1DE), filmNegativeHex(0xEADCC2)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    c.drawRoundRect(P, 9f, 9f, p)
    p.shader = null
    c.save()
    val clip = android.graphics.Path().apply { addRoundRect(P, 9f, 9f, android.graphics.Path.Direction.CW) }
    c.clipPath(clip)
    // A faint printed grid on the glass.
    val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = filmNegativeArgb(150, 120, 80, 0.13f); strokeWidth = 0.6f }
    var x = P.left + 12f
    while (x < P.right) { c.drawLine(x, P.top, x, P.bottom, grid); x += 20f }
    var y = P.top + 10f
    while (y < P.bottom) { c.drawLine(P.left, y, P.right, y, grid); y += 20f }
    // Inner shade along the bezel.
    val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = filmNegativeArgb(120, 90, 50, 0.16f)
        maskFilter = BlurMaskFilter(sigmaToRadius(4f), BlurMaskFilter.Blur.NORMAL)
    }
    c.drawRoundRect(P, 9f, 9f, inner)
    c.restore()

    // Grain over everything, then a vignette.
    val grain = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(filmNegativeGrain(7), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        alpha = (0.05f * 255).toInt()
        xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
    }
    c.drawRect(0f, 0f, W, H, grain)
    p.shader = RadialGradient(W / 2f, H * 0.46f, H * 0.72f, intArrayOf(0, filmNegativeArgb(0, 0, 0, 0.12f), filmNegativeArgb(0, 0, 0, 0.5f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, W, H, p)
    return bmp
}
