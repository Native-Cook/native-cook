package com.ngocdevv.nativecook.blocks.buttons.foilstampbutton

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 Static art, made once per layout off the main thread: the paper background (a shader baked into a
 bitmap) and the seal's mask (the monogram drawn crisp in red and blurred in green). Per frame the
 screen only changes uniforms and transforms.
 */

/** Margin (dp) round the seal inside its mask, so the blur and the dip have room. */
internal const val FOIL_MASK_PAD = 14f

internal class FoilRect(val x: Float, val y: Float, val w: Float, val h: Float)

internal class FoilLayout(
    val width: Float,
    val height: Float,
    val headerTop: Float,
    val card: FoilRect,
    val sealX: Float,
    val sealY: Float,
    val sealR: Float,
    val rowsTop: Float,
    val buttonX: Float,
    val buttonY: Float,
    val buttonR: Float,
    val hintTop: Float,
)

private const val HEADER = 84f
private const val ROWS = 124f
private const val BUTTON_R = 52f
private const val BUTTON_BLOCK = BUTTON_R * 2 + 30

internal fun foilLayout(width: Float, height: Float, top: Float, bottom: Float): FoilLayout {
    val headerTop = top + 14f
    val bottomEdge = height - max(bottom, 12f) - 10f
    val room = bottomEdge - (headerTop + HEADER)
    // Gaps: card, rows and the stamp share the room; the card takes what is left, within limits.
    val gap = if (room > 560f) 22f else 14f
    val cardH = max(196f, min(330f, room - ROWS - BUTTON_BLOCK - gap * 3))
    val spare = max(0f, room - cardH - ROWS - BUTTON_BLOCK - gap * 3)
    val cardY = headerTop + HEADER + gap + spare * 0.35f
    val cardW = min(width - 48f, 400f)
    val card = FoilRect((width - cardW) / 2f, cardY, cardW, cardH)
    val r = min(70f, cardH * 0.28f)
    val rowsTop = cardY + cardH + gap + spare * 0.2f
    val buttonY = rowsTop + ROWS + gap + spare * 0.3f + BUTTON_R
    return FoilLayout(
        width, height, headerTop, card,
        width / 2f, cardY + cardH * 0.47f, r,
        rowsTop, width / 2f, buttonY, BUTTON_R, buttonY + BUTTON_R + 12f,
    )
}

/**
 * Draws once into a bitmap `w` × `h` px: through a picture into a hardware bitmap on API 28+ (so
 * runtime shaders render exactly as on screen), into a plain bitmap below that.
 */
private fun foilBake(w: Int, h: Int, hardware: Boolean, draw: (Canvas) -> Unit): Bitmap? {
    if (w <= 0 || h <= 0 || w > 8192 || h > 8192) return null
    return try {
        if (hardware && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val picture = Picture()
            draw(picture.beginRecording(w, h))
            picture.endRecording()
            Bitmap.createBitmap(picture, w, h, Bitmap.Config.HARDWARE)
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }
        }
    } catch (_: RuntimeException) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }
}

/** The table and the card, baked into one bitmap at `scale` pixels per dp. */
internal fun foilBakePaper(l: FoilLayout, scale: Float): Bitmap? {
    val w = ceil(l.width * scale).toInt()
    val h = ceil(l.height * scale).toInt()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = runCatching { RuntimeShader(FOIL_PAPER_AGSL) }.getOrNull()
        if (shader != null) {
            shader.setFloatUniform("size", l.width, l.height)
            shader.setFloatUniform("card", l.card.x, l.card.y, l.card.w, l.card.h)
            shader.setFloatUniform("radius", 22f)
            val baked = foilBake(w, h, true) { c ->
                c.scale(scale, scale)
                c.drawRect(0f, 0f, l.width, l.height, Paint().apply { this.shader = shader })
            }
            if (baked != null) return baked
        }
    }
    return foilBake(w, h, false) { c ->
        c.scale(scale, scale)
        foilDrawPaperFallback(c, l)
    }
}

/** Below Android 13: the same table and card from gradients and blurred shapes. */
private fun foilDrawPaperFallback(c: Canvas, l: FoilLayout) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = LinearGradient(
        0f, 0f, 0f, l.height,
        intArrayOf(0xFFECE4D7.toInt(), 0xFFE6DBCA.toInt(), 0xFFD8CBB8.toInt()),
        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
    )
    c.drawRect(0f, 0f, l.width, l.height, p)
    p.shader = null
    val card = l.card
    // Ambient and contact shadows.
    p.color = 0x29000000
    p.maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
    c.drawRoundRect(card.x + 6f, card.y + 14f + 6f, card.x + card.w - 6f, card.y + card.h + 14f - 6f, 22f, 22f, p)
    p.color = 0x38000000
    p.maskFilter = BlurMaskFilter(2.5f, BlurMaskFilter.Blur.NORMAL)
    c.drawRoundRect(card.x, card.y + 1.5f, card.x + card.w, card.y + card.h + 1.5f, 22f, 22f, p)
    p.maskFilter = null
    p.color = 0xFFF3ECE0.toInt()
    p.shader = LinearGradient(
        card.x, card.y, card.x + card.w, card.y,
        0xFFF1EADD.toInt(), 0xFFF5EFE4.toInt(), Shader.TileMode.CLAMP,
    )
    c.drawRoundRect(card.x, card.y, card.x + card.w, card.y + card.h, 22f, 22f, p)
    p.shader = null
    p.style = Paint.Style.STROKE
    p.strokeWidth = 1f
    p.color = 0x26FFFFFF
    c.drawRoundRect(card.x + 0.5f, card.y + 0.5f, card.x + card.w - 0.5f, card.y + card.h - 0.5f, 22f, 22f, p)
    p.style = Paint.Style.FILL
    // Vignette.
    p.shader = RadialGradient(
        l.width / 2f, l.height / 2f, max(l.width, l.height) * 0.75f,
        intArrayOf(0x00000000, 0x00000000, 0x1E1A1008),
        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
    )
    c.drawRect(0f, 0f, l.width, l.height, p)
}

/** The monogram, centred on (0, 0): a rope border, a beaded ring, a thin ring, "E" & "V". */
private fun foilDrawMonogram(c: Canvas, paint: Paint, r: Float) {
    val letters = Paint(paint).apply {
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textSize = r * 0.6f
    }
    val amp = Paint(paint).apply {
        typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        textSize = r * 0.34f
    }
    val stroke = Paint(paint).apply { style = Paint.Style.STROKE; strokeWidth = r * 0.045f }
    c.drawCircle(0f, 0f, r * 0.94f, stroke)
    stroke.strokeWidth = r * 0.016f
    c.drawCircle(0f, 0f, r * 0.7f, stroke)
    // Beads between the two rings.
    val beads = 36
    for (i in 0 until beads) {
        val a = i.toFloat() / beads * PI.toFloat() * 2f
        c.drawCircle(cos(a) * r * 0.82f, sin(a) * r * 0.82f, r * 0.028f, paint)
    }
    // Two small four-point stars above and below the letters.
    for (y in floatArrayOf(-r * 0.5f, r * 0.52f)) {
        val s = r * 0.07f
        val star = Path()
        star.moveTo(0f, y - s)
        star.quadTo(s * 0.18f, y - s * 0.18f, s, y)
        star.quadTo(s * 0.18f, y + s * 0.18f, 0f, y + s)
        star.quadTo(-s * 0.18f, y + s * 0.18f, -s, y)
        star.quadTo(-s * 0.18f, y - s * 0.18f, 0f, y - s)
        star.close()
        c.drawPath(star, paint)
    }
    val ew = letters.measureText("E")
    val vw = letters.measureText("V")
    val aw = amp.measureText("&")
    val base = r * 0.2f
    val gap = r * 0.02f
    val total = ew + aw + vw + gap * 2
    var x = -total / 2f
    c.drawText("E", x, base, letters)
    x += ew + gap
    c.drawText("&", x, base - r * 0.04f, amp)
    x += aw + gap
    c.drawText("V", x, base, letters)
}

/** The seal's mask: red = crisp monogram, green = blurred (the height map). Square, R + pad. */
internal fun foilBuildSealMask(r: Float, scale: Float): Bitmap? {
    val side = ceil((r + FOIL_MASK_PAD) * 2 * scale).toInt()
    return foilBake(side, side, false) { c ->
        c.drawColor(0xFF000000.toInt())
        c.save()
        c.scale(scale, scale)
        c.translate(r + FOIL_MASK_PAD, r + FOIL_MASK_PAD)
        val crisp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF0000.toInt() }
        foilDrawMonogram(c, crisp, r)
        // The blurred copy is added on top, so it fills green without touching red.
        val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF00FF00.toInt()
            maskFilter = BlurMaskFilter(foilSigmaToRadius(1.5f), BlurMaskFilter.Blur.NORMAL)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        }
        foilDrawMonogram(c, soft, r)
        c.restore()
    }
}

/** Below Android 13: the crisp monogram alone, white on clear, coloured by the paint at draw time. */
internal fun foilBuildSealAlpha(r: Float, scale: Float): Bitmap? {
    val side = ceil((r + FOIL_MASK_PAD) * 2 * scale).toInt()
    return foilBake(side, side, false) { c ->
        c.scale(scale, scale)
        c.translate(r + FOIL_MASK_PAD, r + FOIL_MASK_PAD)
        foilDrawMonogram(c, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }, r)
    }?.extractAlpha()
}

// Android takes blur radii; Skia (and the RN code) thinks in sigma.
internal fun foilSigmaToRadius(sigma: Float) = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f)

/** A check mark centred on (cx, cy), about 22 dp wide, drawn as one stroke left to right. */
internal fun foilCheckPath(cx: Float, cy: Float) = Path().apply {
    moveTo(cx - 10f, cy + 0.5f)
    cubicTo(cx - 7f, cy + 3f, cx - 5f, cy + 5.5f, cx - 3.5f, cy + 7.5f)
    cubicTo(cx + 1f, cy + 0.5f, cx + 5.5f, cy - 5f, cx + 11f, cy - 9f)
}
