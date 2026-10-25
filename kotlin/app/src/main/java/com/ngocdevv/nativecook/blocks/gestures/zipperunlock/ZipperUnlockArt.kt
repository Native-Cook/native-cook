package com.ngocdevv.nativecook.blocks.gestures.zipperunlock

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Zipper Unlock — the metal and leather, pre-rendered once into bitmaps around their origin (the
 slider, its pull tab and the tab's soft shadow, the bottom stop, the leather patch), and the
 teeth, rebuilt as three batched paths per frame. A port of zipperArt.ts.
*/

private fun hex(v: String) = android.graphics.Color.parseColor(v)
private fun rgba(r: Int, g: Int, b: Int, a: Float) = android.graphics.Color.argb((a * 255f).roundToInt(), r, g, b)

private val BRASS = intArrayOf(hex("#F6E2A6"), hex("#D3A453"), hex("#8C6326"), hex("#E2BE72"))

/** A bitmap of art drawn around an origin: it covers [left, top] – [left + w, top + h] dp. */
internal class ZipSprite(val bitmap: Bitmap, val left: Float, val top: Float, val w: Float, val h: Float) {
    val rect = RectF(left, top, left + w, top + h)
}

private inline fun sprite(scale: Float, left: Float, top: Float, w: Float, h: Float, draw: (Canvas) -> Unit): ZipSprite {
    val bmp = Bitmap.createBitmap(ceil(w * scale).toInt(), ceil(h * scale).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.scale(scale, scale)
    canvas.translate(-left, -top)
    draw(canvas)
    return ZipSprite(bmp, left, top, w, h)
}

private fun brass(canvas: Canvas, path: Path, x0: Float, y0: Float, x1: Float, y1: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = LinearGradient(x0, y0, x1, y1, BRASS, floatArrayOf(0f, 0.38f, 0.72f, 1f), Shader.TileMode.CLAMP)
    canvas.drawPath(path, p)
}

private fun edgeLines(canvas: Canvas, path: Path) {
    val s = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
        color = rgba(70, 44, 12, 0.65f)
    }
    canvas.drawPath(path, s)
}

private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float) = Path().apply { addRoundRect(RectF(x, y, x + w, y + h), r, r, Path.Direction.CW) }

private fun blur(sigma: Float) = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)

/** The slider, centred on (0, 0): 32 dp wide at the top, 21 at the bottom, 36 tall. */
internal fun zipSlider(scale: Float) = sprite(scale, -40f, -40f, 80f, 90f) { c ->
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    shadow.color = rgba(5, 10, 24, 0.55f)
    shadow.maskFilter = blur(5f)
    c.drawRoundRect(-15f, -14f, 17f, 24f, 9f, 9f, shadow)
    shadow.maskFilter = blur(1.5f)
    shadow.color = rgba(5, 10, 24, 0.6f)
    c.drawRoundRect(-15.5f, -17f, 15.5f, 18f, 8f, 8f, shadow)

    val body = Path().apply {
        moveTo(-16f, -12f)
        cubicTo(-16f, -17f, -13f, -18f, -9f, -18f)
        lineTo(9f, -18f)
        cubicTo(13f, -18f, 16f, -17f, 16f, -12f)
        lineTo(10.5f, 13f)
        cubicTo(9.5f, 17f, 7f, 18f, 4f, 18f)
        lineTo(-4f, 18f)
        cubicTo(-7f, 18f, -9.5f, 17f, -10.5f, 13f)
        close()
    }
    brass(c, body, -16f, -18f, 14f, 18f)
    edgeLines(c, body)
    // Raised centre ridge and the bridge the tab hangs from.
    val ridge = rrect(-3.2f, -15f, 6.4f, 24f, 3f)
    brass(c, ridge, 3f, -15f, -3f, 9f)
    edgeLines(c, ridge)
    val bridge = rrect(-5.5f, 8f, 11f, 7f, 3.5f)
    brass(c, bridge, -5f, 8f, 5f, 15f)
    edgeLines(c, bridge)
    // Top edge highlight.
    val hi = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        strokeCap = Paint.Cap.ROUND
        color = rgba(255, 248, 222, 0.85f)
    }
    c.drawLine(-11f, -16.6f, 11f, -16.6f, hi)
}

/** The tab's outline, hanging from its pivot at (0, 0) down +y, with the ring hole cut out. */
private fun tabPath(): Path {
    val L = ZIP_TAB_LEN
    return Path().apply {
        moveTo(-7f, -4f)
        cubicTo(-7f, -7f, 7f, -7f, 7f, -4f)
        lineTo(10f, L - 12)
        cubicTo(10f, L - 4, 6f, L, 0f, L)
        cubicTo(-6f, L, -10f, L - 4, -10f, L - 12)
        close()
        addRoundRect(RectF(-4.5f, L - 15, 4.5f, L - 7), 3.5f, 3.5f, Path.Direction.CW)
        addRoundRect(RectF(-3f, -2.5f, 3f, 1.5f), 2f, 2f, Path.Direction.CW)
        fillType = Path.FillType.EVEN_ODD
    }
}

/** The pull tab with its split ring, pivot at (0, 0). */
internal fun zipTab(scale: Float) = sprite(scale, -30f, -12f, 60f, ZIP_TAB_LEN + 40) { c ->
    val L = ZIP_TAB_LEN
    val tab = tabPath()
    brass(c, tab, -10f, 0f, 12f, L)
    edgeLines(c, tab)
    // A pressed border on the tab's face.
    val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = rgba(120, 80, 24, 0.55f)
    }
    val face = Path().apply {
        moveTo(-4.5f, 3f)
        lineTo(-7f, L - 18)
        moveTo(4.5f, 3f)
        lineTo(7f, L - 18)
    }
    c.drawPath(face, inner)
    inner.color = rgba(255, 245, 210, 0.6f)
    c.drawLine(-6f, -3.5f, 6f, -3.5f, inner)
    // The ring through the hole.
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.2f
        shader = LinearGradient(-10f, L - 4, 10f, L + 18, BRASS, floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
    }
    c.drawCircle(0f, L + 4, 9.5f, ring)
    ring.shader = null
    ring.strokeWidth = 0.8f
    ring.color = rgba(255, 248, 222, 0.7f)
    c.drawArc(RectF(-9.5f, L - 5.5f, 9.5f, L + 13.5f), 200f, 80f, false, ring)
}

/** The tab's soft shadow (its outline blurred 4 dp), pivot at (0, 0); drawn with an alpha per frame. */
internal fun zipTabShadow(scale: Float) = sprite(scale, -30f, -22f, 60f, ZIP_TAB_LEN + 50) { c ->
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = hex("#040816")
    p.maskFilter = blur(4f)
    c.drawPath(tabPath(), p)
}

/** The bottom stop: a small box, centred on (0, 0). */
internal fun zipStop(scale: Float) = sprite(scale, -20f, -20f, 40f, 40f) { c ->
    val sh = Paint(Paint.ANTI_ALIAS_FLAG)
    sh.color = rgba(5, 10, 24, 0.5f)
    sh.maskFilter = blur(2f)
    c.drawRoundRect(-8f, -5f, 9f, 8f, 3f, 3f, sh)
    val box = rrect(-8.5f, -6f, 17f, 12f, 2.5f)
    brass(c, box, -8f, -6f, 8f, 6f)
    edgeLines(c, box)
}

private fun typefaceOf(weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.SANS_SERIF, weight, false) else Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

private fun spaced(c: Canvas, text: String, paint: Paint, x: Float, y: Float, spacing: Float) {
    val widths = FloatArray(text.length)
    paint.getTextWidths(text, widths)
    val total = widths.sum() + spacing * (text.length - 1)
    var cx = x - total / 2
    for (i in text.indices) {
        c.drawText(text, i, i + 1, cx, y, paint)
        cx += widths[i] + spacing
    }
}

/** The leather patch, 136 × 80 dp, centred on (0, 0). */
internal fun zipPatch(scale: Float): ZipSprite {
    val W = 136f
    val H = 80f
    return sprite(scale, -W, -H, W * 2, H * 2) { c ->
        val rect = RectF(-W / 2, -H / 2, W / 2, H / 2)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Two shadows: tight contact and soft ambient.
        p.color = rgba(4, 8, 20, 0.45f)
        p.maskFilter = blur(9f)
        c.drawRoundRect(-W / 2 + 2, -H / 2 + 8, W / 2 + 2, H / 2 + 8, 10f, 10f, p)
        p.maskFilter = blur(1.4f)
        p.color = rgba(4, 8, 20, 0.5f)
        c.drawRoundRect(-W / 2, -H / 2 + 1.5f, W / 2, H / 2 + 1.5f, 10f, 10f, p)
        p.maskFilter = null
        p.color = android.graphics.Color.BLACK
        p.shader = LinearGradient(-W / 2, -H / 2, W / 2, H / 2, intArrayOf(hex("#C48D5E"), hex("#9C6438"), hex("#7A4826"), hex("#93603A")), floatArrayOf(0f, 0.4f, 0.8f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, 10f, 10f, p)
        p.shader = null
        // Leather pores: seeded specks, darker and lighter.
        c.save()
        c.clipPath(Path().apply { addRoundRect(rect, 10f, 10f, Path.Direction.CW) })
        var seed = 7L
        fun rnd(): Float {
            seed = (seed * 16807L) % 2147483647L
            return (seed.toDouble() / 2147483647.0).toFloat()
        }
        repeat(220) {
            val x = -W / 2 + rnd() * W
            val y = -H / 2 + rnd() * H
            p.color = if (rnd() > 0.5f) rgba(60, 30, 12, 0.18f) else rgba(255, 220, 180, 0.10f)
            c.drawCircle(x, y, 0.5f + rnd() * 0.9f, p)
        }
        // Worn, lighter rim.
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = rgba(255, 214, 170, 0.10f)
            maskFilter = blur(1f)
        }
        c.drawRoundRect(rect, 10f, 10f, rim)
        c.restore()
        // Stitched border.
        val st = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
            strokeCap = Paint.Cap.ROUND
            color = hex("#E9C98E")
            pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
        }
        c.drawRoundRect(-W / 2 + 6, -H / 2 + 6, W / 2 - 6, H / 2 - 6, 6f, 6f, st)
        // Debossed lettering: a light lower edge under a dark fill.
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f; typeface = typefaceOf(800) }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; typeface = typefaceOf(700) }
        val light = rgba(255, 222, 184, 0.35f)
        val dark = hex("#5A3216")
        title.color = light
        spaced(c, "POCKET", title, 0f, 7.8f, 3f)
        title.color = dark
        spaced(c, "POCKET", title, 0f, 6.6f, 3f)
        small.color = light
        spaced(c, "NOTES FOR LATER", small, 0f, 25f, 1.6f)
        small.color = dark
        spaced(c, "NOTES FOR LATER", small, 0f, 24f, 1.6f)
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rgba(70, 36, 14, 0.6f) }
        c.drawRect(-30f, 12.5f, 30f, 13.5f, rule)
    }
}

/** Appends a rotated rectangle (centre, half sizes, angle) to a path. */
private fun quad(b: androidx.compose.ui.graphics.Path, x: Float, y: Float, hw: Float, hh: Float, ca: Float, sa: Float) {
    b.moveTo(x - hw * ca + hh * sa, y - hw * sa - hh * ca)
    b.lineTo(x + hw * ca + hh * sa, y + hw * sa - hh * ca)
    b.lineTo(x + hw * ca - hh * sa, y + hw * sa + hh * ca)
    b.lineTo(x - hw * ca - hh * sa, y - hw * sa + hh * ca)
    b.close()
}

/**
 * Every tooth of both tapes for this frame into [shadow], [body] and [light] (rewound first). Each
 * tooth sits across its tape's edge (cx ∓ gap), turned square to the edge; closed teeth interlock
 * across the centre.
 */
internal fun buildZipTeeth(
    z: ZipGeometry,
    ys: Float,
    o: Float,
    shadow: androidx.compose.ui.graphics.Path,
    body: androidx.compose.ui.graphics.Path,
    light: androidx.compose.ui.graphics.Path,
) {
    shadow.rewind()
    body.rewind()
    light.rewind()
    val hw = ZIP_TOOTH_W / 2
    val hh = ZIP_TOOTH_H / 2
    for (s in intArrayOf(-1, 1)) {
        var y = z.yTop + 2 + if (s > 0) ZIP_TOOTH_PITCH / 2 else 0f
        while (y < z.yEnd - 6) {
            val g = gapAt(z, y, ys, o)
            val x = z.cx + s * g - s * 0.3f
            if (x > -12 && x < z.width + 12) {
                val slope = (gapAt(z, y + 1.5f, ys, o) - gapAt(z, y - 1.5f, ys, o)) / 3
                val a = -atan(s * slope)
                val ca = cos(a)
                val sa = sin(a)
                quad(shadow, x + 0.6f, y + 1.4f, hw, hh, ca, sa)
                quad(body, x, y, hw, hh, ca, sa)
                // The lit upper face of the scoop.
                quad(light, x + sa * hh * 0.45f, y - ca * hh * 0.45f, hw - 1, hh * 0.32f, ca, sa)
            }
            y += ZIP_TOOTH_PITCH
        }
    }
}
