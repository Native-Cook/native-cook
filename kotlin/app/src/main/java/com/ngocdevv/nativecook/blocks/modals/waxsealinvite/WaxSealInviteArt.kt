package com.ngocdevv.nativecook.blocks.modals.waxsealinvite

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Static art, made once per layout off the main thread: the envelope's layers baked into bitmaps
 (back with its liner and shadows, the pocket front, the flap's outside and its lined inside), the
 card, the seal's monogram mask, and the pieces' outlines. Per frame only transforms and uniforms
 change; the cracks are rebuilt from fixed points.
 */

internal const val WAX_SEAL_PAD = 10f
/** Room round the envelope's back bitmap for its two shadows. */
internal const val WAX_BACK_BLEED = 48f

internal class WaxRect(val x: Float, val y: Float, val w: Float, val h: Float)

internal class WaxLayout(
    val width: Float,
    val height: Float,
    val headerTop: Float,
    val env: WaxRect,
    val flap: Float,
    val sealX: Float,
    val sealY: Float,
    val sealR: Float,
    val cardW: Float,
    val cardH: Float,
    val cardX: Float,
    val insideTop: Float,
    val clearTop: Float,
    val finalTop: Float,
    val chipsTop: Float,
    val hintTop: Float,
)

internal fun waxLayout(width: Float, height: Float, top: Float, bottom: Float): WaxLayout {
    val w = min(width - 32f, 380f)
    val h = (w * 0.7f).roundToInt().toFloat()
    val cardH = h - 14f
    val hintTop = height - max(bottom, 16f) - 34f
    // The envelope sits low, with room above it for the card to rise clear and the detail chips
    // and the hint below it.
    val y = max(top + 74f + cardH, min(height * 0.5f, hintTop - 92f - h)).roundToInt().toFloat()
    val flap = (h * 0.6f).roundToInt().toFloat()
    val x = (width - w) / 2f
    val cw = w - 26f
    return WaxLayout(
        width, height, top + 22f, WaxRect(x, y, w, h), flap,
        width / 2f, y + flap - 6f, min(42f, w * 0.115f).roundToInt().toFloat(),
        cw, cardH, (width - cw) / 2f, y + 8f, y - cardH - 10f, min(y + 6f - cardH / 2f, y - flap + 8f),
        y + h + 28f, max(hintTop, y + h + 68f),
    )
}

private fun rgb(c: FloatArray) = android.graphics.Color.rgb((c[0] * 255).roundToInt(), (c[1] * 255).roundToInt(), (c[2] * 255).roundToInt())

private val CREAM = floatArrayOf(0.93f, 0.886f, 0.81f)
private val CREAM_FRONT = floatArrayOf(0.953f, 0.914f, 0.845f)
private val CREAM_FLAP = floatArrayOf(0.972f, 0.94f, 0.88f)
private val LINER = floatArrayOf(0.43f, 0.09f, 0.11f)

/** A paper fill: the paper shader on Android 13+, its flat tint below. */
private fun paperPaint(tint: FloatArray, liner: Boolean): Paint {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = rgb(tint)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching {
            RuntimeShader(WAX_PAPER_AGSL).apply {
                setFloatUniform("tint", tint[0], tint[1], tint[2])
                setFloatUniform("liner", if (liner) 1f else 0f)
            }
        }.getOrNull()?.let { p.shader = it }
    }
    return p
}

/** The flap's outline from the envelope's top edge (local coordinates, hinge at y = 0). */
internal fun waxFlapPath(w: Float, fh: Float, x: Float = 0f, y: Float = 0f) = Path().apply {
    moveTo(x, y)
    lineTo(x + w, y)
    lineTo(x + w / 2f + 16f, y + fh - 8f)
    quadTo(x + w / 2f, y + fh + 2f, x + w / 2f - 16f, y + fh - 8f)
    close()
}

private fun frontPath(w: Float, h: Float) = Path().apply {
    moveTo(0f, 2f)
    lineTo(w / 2f, h * 0.5f)
    lineTo(w, 2f)
    lineTo(w, h - 10f)
    quadTo(w, h, w - 10f, h)
    lineTo(10f, h)
    quadTo(0f, h, 0f, h - 10f)
    close()
}

// Android takes blur radii; Skia (and the RN code) thinks in sigma.
internal fun waxSigma(sigma: Float) = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f)

/**
 * Draws once into a bitmap of `width` × `height` dp at `scale`: through a picture into a hardware
 * bitmap on API 28+ (so runtime shaders and blurs render exactly as on screen), into a plain
 * bitmap below that.
 */
private fun snapshot(width: Float, height: Float, scale: Float, draw: (Canvas) -> Unit): Bitmap? {
    val w = ceil(width * scale).toInt()
    val h = ceil(height * scale).toInt()
    if (w <= 0 || h <= 0 || w > 8192 || h > 8192) return null
    val drawScaled: (Canvas) -> Unit = { c ->
        c.scale(scale, scale)
        draw(c)
    }
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val picture = Picture()
            drawScaled(picture.beginRecording(w, h))
            picture.endRecording()
            Bitmap.createBitmap(picture, w, h, Bitmap.Config.HARDWARE)
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { drawScaled(Canvas(it)) }
        }
    } catch (_: RuntimeException) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }
}

internal class WaxEnvelopeArt(val back: Bitmap?, val front: Bitmap?, val flapOut: Bitmap?, val flapIn: Bitmap?)

/**
 * The envelope's layers at `scale` px per dp. The back is WAX_BACK_BLEED larger on every side and
 * carries the envelope's two shadows; the others are sized to the envelope (flaps to the flap).
 */
internal fun waxBakeEnvelope(l: WaxLayout, scale: Float): WaxEnvelopeArt {
    val w = l.env.w
    val h = l.env.h
    val flap = l.flap
    val b = WAX_BACK_BLEED
    val back = snapshot(w + b * 2f, h + b * 2f, scale) { c ->
        c.translate(b, b)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
        shadow.color = android.graphics.Color.argb(140, 8, 2, 0)
        shadow.maskFilter = BlurMaskFilter(waxSigma(22f), BlurMaskFilter.Blur.NORMAL)
        c.drawRoundRect(8f, 18f, w - 8f, 18f + h, 12f, 12f, shadow)
        shadow.color = android.graphics.Color.argb(115, 8, 2, 0)
        shadow.maskFilter = BlurMaskFilter(waxSigma(2.5f), BlurMaskFilter.Blur.NORMAL)
        c.drawRoundRect(1f, 2f, w - 1f, 2f + h, 10f, 10f, shadow)
        c.drawRoundRect(0f, 0f, w, h, 10f, 10f, paperPaint(CREAM, false))
        c.drawRect(5f, 4f, w - 5f, 4f + h * 0.66f, paperPaint(LINER, true))
        // Shade at the top inside, where the flap's hinge hides the light.
        val shade = Paint().apply {
            this.shader = LinearGradient(0f, 0f, 0f, h * 0.4f, android.graphics.Color.argb(140, 20, 4, 6), android.graphics.Color.argb(0, 20, 4, 6), Shader.TileMode.CLAMP)
        }
        c.drawRect(5f, 4f, w - 5f, 4f + h * 0.4f, shade)
    }
    val front = snapshot(w, h, scale) { c ->
        c.drawPath(frontPath(w, h), paperPaint(CREAM_FRONT, false))
        // The bottom flap's edges: a fold casting a hair of shadow, with light below it.
        val fold = Path().apply {
            moveTo(6f, h - 3f)
            quadTo(w * 0.3f, h * 0.7f, w / 2f, h * 0.64f)
            quadTo(w * 0.7f, h * 0.7f, w - 6f, h - 3f)
        }
        val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
            color = android.graphics.Color.argb(36, 90, 60, 30)
            maskFilter = BlurMaskFilter(waxSigma(1.4f), BlurMaskFilter.Blur.NORMAL)
        }
        c.save()
        c.translate(0f, -1.2f)
        c.drawPath(fold, dark)
        c.restore()
        val light = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            color = android.graphics.Color.argb(191, 255, 252, 244)
        }
        c.drawPath(fold, light)
        // The V's edge: a lit paper edge over the dark inside.
        val lip = Path().apply {
            moveTo(0f, 2f)
            lineTo(w / 2f, h * 0.5f)
            lineTo(w, 2f)
        }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = android.graphics.Color.argb(230, 255, 250, 240)
        }
        c.drawPath(lip, edge)
    }
    val flapOut = snapshot(w, flap + 4f, scale) { c ->
        val path = waxFlapPath(w, flap)
        c.drawPath(path, paperPaint(CREAM_FLAP, false))
        // Lighter toward the tip (it bulges a little), a lit edge along its lower sides.
        val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = LinearGradient(
                0f, 0f, 0f, flap,
                intArrayOf(android.graphics.Color.argb(31, 120, 90, 50), android.graphics.Color.argb(5, 120, 90, 50), android.graphics.Color.argb(31, 255, 255, 250)),
                floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP,
            )
        }
        c.drawPath(path, sheen)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = android.graphics.Color.argb(230, 255, 253, 246)
        }
        c.drawPath(path, edge)
    }
    val flapIn = snapshot(w, flap + 4f, scale) { c ->
        c.drawPath(waxFlapPath(w, flap), paperPaint(CREAM, false))
        c.save()
        c.translate(w * 0.025f, 4f)
        c.scale(0.95f, 0.9f)
        c.drawPath(waxFlapPath(w, flap), paperPaint(LINER, true))
        c.restore()
    }
    return WaxEnvelopeArt(back, front, flapOut, flapIn)
}

private fun serif(style: Int, size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = Typeface.create(Typeface.SERIF, style)
    textSize = size
    this.color = color
}

/** Text centred at x, with extra tracking between the letters. */
private fun centred(c: Canvas, text: String, x: Float, y: Float, paint: Paint, tracking: Float = 0f) {
    if (tracking == 0f) {
        c.drawText(text, x - paint.measureText(text) / 2f, y, paint)
        return
    }
    val ws = FloatArray(text.length)
    paint.getTextWidths(text, ws)
    val total = ws.sum() + tracking * (ws.size - 1)
    var cx = x - total / 2f
    for (i in text.indices) {
        c.drawText(text, i, i + 1, cx, y, paint)
        cx += ws[i] + tracking
    }
}

/** Room round the card's bitmap for its two shadows. */
internal const val WAX_CARD_BLEED = 44f

/**
 * The invitation card, WAX_CARD_BLEED larger on every side: with its two shadows (`shadow`, drawn
 * once it is in front of the envelope) or without. The RSVP buttons are composables.
 */
internal fun waxBakeCard(w: Float, h: Float, scale: Float, shadow: Boolean): Bitmap? =
    snapshot(w + WAX_CARD_BLEED * 2f, h + WAX_CARD_BLEED * 2f, scale) { c ->
        c.translate(WAX_CARD_BLEED, WAX_CARD_BLEED)
        if (shadow) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = android.graphics.Color.argb(102, 30, 8, 4)
            p.maskFilter = BlurMaskFilter(waxSigma(16f), BlurMaskFilter.Blur.NORMAL)
            c.drawRoundRect(6f, 18f, w - 6f, 18f + h - 4f, 10f, 10f, p)
            p.color = android.graphics.Color.argb(77, 30, 8, 4)
            p.maskFilter = BlurMaskFilter(waxSigma(2f), BlurMaskFilter.Blur.NORMAL)
            c.drawRoundRect(0.5f, 1.5f, w - 0.5f, 1.5f + h, 8f, 8f, p)
        }
        waxDrawCard(c, w, h)
    }

private fun waxDrawCard(c: Canvas, w: Float, h: Float) {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(0xFFFDF8EF.toInt(), 0xFFF8F0E2.toInt(), 0xFFF3E8D6.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
    }
    c.drawRoundRect(0f, 0f, w, h, 8f, 8f, fill)
    val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
        color = 0xFFB48F58.toInt()
    }
    c.drawRoundRect(8f, 8f, w - 8f, h - 8f, 4f, 4f, gold)
    val ink = 0xFF4A2A22.toInt()
    val kicker = serif(Typeface.BOLD, 8.5f, ink)
    val title = serif(Typeface.BOLD_ITALIC, min(31f, w * 0.095f), 0xFF7C1A20.toInt())
    val body = serif(Typeface.NORMAL, 12.5f, ink)
    val strong = serif(Typeface.BOLD, 12.5f, ink)
    val mid = w / 2f
    centred(c, "TOGETHER WITH THEIR FAMILIES", mid, 30f, kicker, 1.6f)
    centred(c, "Lena & Theo", mid, 66f, title)
    centred(c, "invite you to celebrate their wedding", mid, 87f, body)
    // A small ornament: two lines and a diamond.
    gold.strokeWidth = 0.9f
    c.drawLine(mid - 46f, 101f, mid - 9f, 101f, gold)
    c.drawLine(mid + 9f, 101f, mid + 46f, 101f, gold)
    val diamond = Path().apply {
        moveTo(mid, 97f)
        lineTo(mid + 4f, 101f)
        lineTo(mid, 105f)
        lineTo(mid - 4f, 101f)
        close()
    }
    c.drawPath(diamond, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB48F58.toInt() })
    centred(c, "Saturday, 14 June 2027 · 4 pm", mid, 124f, strong)
    centred(c, "Villa Amara · Lake Orta", mid, 142f, body)
}

/** The monogram's mask (green = blurred height), centred in a square of side 2 (R + SEAL_PAD). */
internal fun waxBuildSealMask(r: Float, scale: Float): Bitmap? {
    val side = (r + WAX_SEAL_PAD) * 2f
    val w = ceil(side * scale).toInt()
    if (w <= 0 || w > 4096) return null
    return try {
        Bitmap.createBitmap(w, w, Bitmap.Config.ARGB_8888).also { bmp ->
            val c = Canvas(bmp)
            c.drawColor(0xFF000000.toInt())
            c.scale(scale, scale)
            c.translate(r + WAX_SEAL_PAD, r + WAX_SEAL_PAD)
            val blur = BlurMaskFilter(waxSigma(1.2f), BlurMaskFilter.Blur.NORMAL)
            val letters = serif(Typeface.BOLD, r * 0.64f, 0xFF00FF00.toInt()).apply { maskFilter = blur }
            val amp = serif(Typeface.ITALIC, r * 0.4f, 0xFF00FF00.toInt()).apply { maskFilter = blur }
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF00FF00.toInt()
                style = Paint.Style.STROKE
                strokeWidth = r * 0.035f
                maskFilter = blur
            }
            c.drawCircle(0f, 0f, r * 0.6f, ring)
            val lw = letters.measureText("L")
            val tw = letters.measureText("T")
            val aw = amp.measureText("&")
            val gap = r * 0.02f
            var x = -(lw + aw + tw + gap * 2f) / 2f
            val base = r * 0.2f
            c.drawText("L", x, base, letters)
            x += lw + gap
            c.drawText("&", x, base - r * 0.02f, amp)
            x += aw + gap
            c.drawText("T", x, base, letters)
        }
    } catch (_: OutOfMemoryError) {
        null
    }
}

/** Piece i's outline (seal-centred): from the centre out along crack i, round, back along i + 1. */
internal fun waxWedgePath(cracks: Array<FloatArray>, i: Int, r: Float): Path {
    val a = cracks[i]
    val b = cracks[(i + 1) % WAX_CRACKS]
    val p = Path()
    p.moveTo(a[0], a[1])
    for (j in 1 until WAX_CRACK_POINTS) p.lineTo(a[j * 2], a[j * 2 + 1])
    val a0 = atan2(a[a.size - 1], a[a.size - 2])
    var a1 = atan2(b[b.size - 1], b[b.size - 2])
    if (a1 < a0) a1 += WAX_PI * 2f
    for (k in 1 until 8) {
        val ang = a0 + (a1 - a0) * k / 8f
        p.lineTo(cos(ang) * r * 1.5f, sin(ang) * r * 1.5f)
    }
    for (j in WAX_CRACK_POINTS - 1 downTo 1) p.lineTo(b[j * 2], b[j * 2 + 1])
    p.close()
    return p
}

/** The cracks grown to `amount` (each starts 40 ms after the last), seal-centred, into `out`. */
internal fun waxCrackPath(cracks: Array<FloatArray>, amount: Float, out: Path) {
    out.rewind()
    if (amount <= 0.001f) return
    for (i in 0 until WAX_CRACKS) {
        val own = min(1f, max(0f, (amount - i * 0.05f) / (1f - (WAX_CRACKS - 1) * 0.05f)))
        if (own <= 0f) continue
        // The crack reaches only to the wax's edge (about R).
        val reach = own * (WAX_CRACK_POINTS - 1) * (1f / 1.3f) * 0.9f
        val c = cracks[i]
        out.moveTo(c[0], c[1])
        for (j in 1 until WAX_CRACK_POINTS) {
            val f = min(1f, reach - (j - 1))
            if (f <= 0f) break
            out.lineTo(c[(j - 1) * 2] + (c[j * 2] - c[(j - 1) * 2]) * f, c[(j - 1) * 2 + 1] + (c[j * 2 + 1] - c[(j - 1) * 2 + 1]) * f)
            if (f < 1f) break
        }
    }
}
