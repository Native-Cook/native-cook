package com.ngocdevv.nativecook.blocks.gestures.stickerboard

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The bin: a small steel pedal bin drawn in vector at the bottom of the board. Its units are dp of
 * a 402 dp wide screen, measured from the middle of its base, up being negative.
 */

// Body: radius at the rim and at the foot, height, and how flat its round top and foot look.
internal const val BIN_TOP_R = 28f
internal const val BIN_FOOT_R = 24.5f
internal const val BIN_BODY = 64f
internal const val BIN_TILT = 0.28f
private val BIN_SIDE = sqrt(1f - BIN_TILT * BIN_TILT)
// The lid: a little wider than the rim, its thickness, and how far it opens on its hinge (degrees).
private const val LID_R = 29.6f
private const val LID_THICK = 4.5f
private const val LID_OPEN = 100f
private const val FOOT = 6f
// The base stands this far above the bottom safe area; tucked away it sits this far below the screen.
internal const val BIN_FLOOR = 18f
private const val BIN_TUCK = 120f
// Where a sticker hangs over the bin as it is pulled in, and where a paper ball goes in.
internal const val BIN_HOVER = 72f
internal const val BIN_MOUTH_DROP = 4f
// The counter plate on the front, and the roll of its digits.
private const val PLATE_W = 28f
private const val PLATE_H = 16f
private const val PLATE_Y = -BIN_BODY * 0.46f
internal const val ROLL_TIME = 0.34f

private fun argb(hex: Long, a: Float = 1f) = ((a.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or (hex and 0xFFFFFF).toInt()

/** Where the bin stands on the screen and how it is turned and scaled, from its springs. */
internal class BinFrame {
    var x = 0f
    var y = 0f
    var rot = 0f
    var sx = 1f
    var sy = 1f
    var alpha = 1f

    fun set(b: BinState, k: Float, width: Float, height: Float, bottom: Float, reduced: Boolean) {
        val baseY = height - bottom - BIN_FLOOR * k
        x = width / 2f
        if (reduced) {
            y = baseY
            rot = 0f
            sx = k
            sy = k
            alpha = clamp01(b.show)
            return
        }
        val g = max(0.2f, b.grow)
        y = baseY + (1f - b.show) * (bottom + BIN_TUCK * k)
        rot = b.lean + b.wobble
        sx = k * g * (1f + 0.5f * b.squash)
        sy = k * g * (1f - b.squash)
        alpha = 1f
    }

    /** A point in the bin's own units on the screen. */
    fun point(lx: Float, ly: Float, out: FloatArray) {
        val a = rot * PI.toFloat() / 180f
        val px = lx * sx
        val py = ly * sy
        out[0] = x + px * cos(a) - py * sin(a)
        out[1] = y + px * sin(a) + py * cos(a)
    }
}

private fun oval(r: Float, cy: Float) = RectF(-r, cy - r * BIN_TILT, r, cy + r * BIN_TILT)

private fun gradient(x0: Float, y0: Float, x1: Float, y1: Float, colors: LongArray, stops: FloatArray, alpha: FloatArray? = null) =
    LinearGradient(x0, y0, x1, y1, IntArray(colors.size) { argb(colors[it], alpha?.get(it) ?: 1f) }, stops, Shader.TileMode.CLAMP)

/** The fixed parts of the drawing and every paint it needs, made once. */
internal class BinArt {
    private val h = BIN_BODY
    private val rt = BIN_TOP_R
    private val rb = BIN_FOOT_R
    private val rf = rb + (rt - rb) * FOOT / h

    val body = Path().apply {
        moveTo(-rt, -h)
        lineTo(-rb, 0f)
        arcTo(oval(rb, 0f), 180f, -180f, false)
        lineTo(rt, -h)
        arcTo(oval(rt, -h), 0f, -180f, false)
        close()
    }

    // The dark plastic foot ring: between the foot and a line FOOT above it.
    val footRing = Path().apply {
        moveTo(-rf, -FOOT)
        arcTo(oval(rf, -FOOT), 180f, -180f, false)
        lineTo(rb, 0f)
        arcTo(oval(rb, 0f), 0f, 180f, false)
        close()
    }
    val footEdge = Path().apply { addArc(oval(rf, -FOOT), 180f, -180f) }

    // A bead just under the rim, and the rim itself.
    val bead = Path().apply { addArc(oval(rt - 0.3f, -h + 3.2f), 180f, -180f) }
    val rim = oval(rt + 0.9f, -h)
    val mouth = oval(rt - 2f, -h)

    // A ball going in is seen above the rim and through the mouth, never through the front.
    val inside = Path().apply {
        addRect(-400f, -1000f, 400f, -h, Path.Direction.CW)
        addOval(oval(rt - 2f, -h), Path.Direction.CW)
    }

    val plate = RectF(-PLATE_W / 2f, PLATE_Y - PLATE_H / 2f, PLATE_W / 2f, PLATE_Y + PLATE_H / 2f)
    val platePath = Path().apply { addRoundRect(plate, 4f, 4f, Path.Direction.CW) }

    val steel = gradient(
        -rt, 0f, rt, 0f,
        longArrayOf(0x585C61, 0x8A8F94, 0xC8CCD0, 0xF5F7F8, 0xDDE0E3, 0xAAB0B5, 0x7E8388, 0x63676C, 0x4B4F54),
        floatArrayOf(0f, 0.08f, 0.21f, 0.29f, 0.34f, 0.55f, 0.77f, 0.91f, 1f),
    )
    val dusk = gradient(0f, -h, 0f, 0f, longArrayOf(0x141619, 0x141619, 0x141619), floatArrayOf(0f, 0.55f, 1f), floatArrayOf(0f, 0f, 0.24f))
    val cavity = gradient(0f, -h - (rt - 2f) * BIN_TILT, 0f, -h + (rt - 2f) * BIN_TILT, longArrayOf(0x4F5358, 0x1F2124, 0x0F1011), floatArrayOf(0f, 0.45f, 1f))
    val lidTop = gradient(-LID_R, -LID_R, LID_R, LID_R, longArrayOf(0xFBFCFC, 0xDADEE1, 0xAEB3B8, 0x868B90), floatArrayOf(0f, 0.34f, 0.72f, 1f))
    val lidBand = gradient(-LID_R, 0f, LID_R, 0f, longArrayOf(0x4C5055, 0x9EA3A8, 0xD9DCDF, 0x7C8186, 0x43474B), floatArrayOf(0f, 0.2f, 0.3f, 0.7f, 1f))
    val underside = gradient(-LID_R, 0f, LID_R, 0f, longArrayOf(0x3C4044, 0x6A6F74, 0x55595E, 0x3A3D41), floatArrayOf(0f, 0.3f, 0.65f, 1f))

    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    val layer = Paint()
    val digits = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.SANS_SERIF, 700, false) else Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = 11.5f
        color = argb(0xF3EEE4)
    }
    val rect = RectF()
    val bounds = android.graphics.Rect()
    val soft = Paint(Paint.ANTI_ALIAS_FLAG)
    val tight = Paint(Paint.ANTI_ALIAS_FLAG)
    var blurK = -1f

    /** A string's width as Skia's `measureText` gives it: its ink bounds plus a point either side. */
    fun measure(text: String): Float {
        digits.getTextBounds(text, 0, text.length, bounds)
        return if (bounds.isEmpty) 0f else bounds.width() + 2f
    }
}

private fun fillWith(p: Paint, color: Int): Paint {
    p.shader = null
    p.color = color
    return p
}

private fun strokeWith(p: Paint, color: Int, width: Float): Paint {
    p.shader = null
    p.color = color
    p.strokeWidth = width
    return p
}

/**
 * The lid, hinged at the back of the rim. Turned back by θ, a point of its plane `y` from its
 * middle lands at `y·(t·cosθ − s·sinθ)` on the screen, so it is drawn as a circle squeezed by that
 * factor; past about 17° the factor turns negative and its underside faces us.
 */
private fun drawLid(canvas: Canvas, art: BinArt, lid: Float) {
    val th = max(0f, lid) * LID_OPEN * PI.toFloat() / 180f
    var a = BIN_TILT * cos(th) - BIN_SIDE * sin(th)
    if (abs(a) < 0.004f) a = if (a < 0f) -0.004f else 0.004f
    val yb = -BIN_BODY - BIN_TILT * LID_R + LID_R * a
    val yt = yb - (BIN_TILT * sin(th) + BIN_SIDE * cos(th)) * LID_THICK
    val f = art.fill
    val s = art.stroke
    fun disc(y: Float, shader: Shader) {
        canvas.save()
        canvas.translate(0f, y)
        canvas.scale(1f, a)
        f.shader = shader
        canvas.drawCircle(0f, 0f, LID_R, f)
        canvas.restore()
    }
    fun band() {
        f.shader = art.lidBand
        canvas.drawRect(-LID_R, min(yb, yt), LID_R, max(yb, yt), f)
    }
    if (a >= 0f) {
        // Top face up: the edge band below, then the domed top.
        disc(yb, art.lidBand)
        band()
        disc(yt, art.lidTop)
        f.shader = null
        canvas.save()
        canvas.translate(0f, yt)
        canvas.scale(1f, a)
        canvas.drawCircle(0f, 0f, LID_R, strokeWith(s, argb(0x24262A, 0.55f), 0.7f))
        // An embossed ring, and a sharp highlight along the front edge.
        canvas.drawCircle(0f, 0f, LID_R * 0.72f, strokeWith(s, argb(0x3C4046, 0.32f), 1f))
        art.rect.set(-LID_R * 0.72f, -LID_R * 0.72f + 1.2f, LID_R * 0.72f, LID_R * 0.72f + 1.2f)
        canvas.drawArc(art.rect, 20f, 140f, false, strokeWith(s, argb(0xFFFFFF, 0.7f), 0.9f))
        art.rect.set(-LID_R * 0.9f, -LID_R * 0.9f, LID_R * 0.9f, LID_R * 0.9f)
        canvas.drawArc(art.rect, 205f, 55f, false, strokeWith(s, argb(0xFFFFFF, 0.95f), 1.5f))
        canvas.restore()
    } else {
        // Underside to us: the top face is behind, then the band, then the underside.
        disc(yt, art.lidBand)
        band()
        disc(yb, art.underside)
        f.shader = null
        canvas.save()
        canvas.translate(0f, yb)
        canvas.scale(1f, a)
        canvas.drawCircle(0f, 0f, LID_R, strokeWith(s, argb(0x24262A, 0.55f), 0.7f))
        canvas.drawCircle(0f, 0f, LID_R * 0.82f, strokeWith(s, argb(0x141619, 0.4f), 1.2f))
        canvas.restore()
    }
    f.shader = null
}

/** The number on the plate, each digit centred in a cell of the same width so it never shifts. */
private fun drawNumber(canvas: Canvas, art: BinArt, n: Int, y: Float) {
    val text = (max(0, n) % 100).toString()
    val cell = art.measure("0")
    val x0 = -cell * text.length / 2f
    for (i in text.indices) {
        val ch = text.substring(i, i + 1)
        val w = art.measure(ch)
        canvas.drawText(ch, x0 + cell * i + (cell - w) / 2f, y, art.digits)
    }
}

/**
 * The bin and its contact shadow. `inside` draws whatever is falling in, in the bin's own units,
 * after the lid (which stands behind the mouth when open) and clipped to the mouth.
 */
internal inline fun drawBin(canvas: Canvas, art: BinArt, f: BinFrame, b: BinState, k: Float, inside: () -> Unit) {
    if (b.show < 0.004f && abs(b.showV) < 0.01f && f.alpha >= 1f) return
    if (f.alpha <= 0.004f) return
    val layer = f.alpha < 0.995f
    if (layer) {
        art.layer.alpha = (f.alpha * 255f).roundToInt()
        canvas.saveLayer(null, art.layer)
    }
    drawBinBody(canvas, art, f, b, k)
    canvas.save()
    canvas.clipPath(art.inside)
    inside()
    canvas.restore()
    canvas.restore()
    if (layer) canvas.restore()
}

/** Everything of the bin up to the lid, leaving the canvas in the bin's own units. */
internal fun drawBinBody(canvas: Canvas, art: BinArt, f: BinFrame, b: BinState, k: Float) {
    val rb = BIN_FOOT_R
    val t = BIN_TILT
    val h = BIN_BODY
    val gx = f.sx / max(0.0001f, k)
    // The contact shadow stays on the board, under the foot, whatever the bin's lean.
    if (art.blurK != k) {
        art.blurK = k
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            art.soft.maskFilter = android.graphics.BlurMaskFilter(com.ngocdevv.nativecook.shared.sigmaToRadius(5f * k), android.graphics.BlurMaskFilter.Blur.NORMAL)
            art.tight.maskFilter = android.graphics.BlurMaskFilter(com.ngocdevv.nativecook.shared.sigmaToRadius(1.6f * k), android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        art.soft.color = argb(0x2A1505, 0.34f)
        art.tight.color = argb(0x1C0E03, 0.32f)
    }
    art.rect.set(f.x - (rb + 9f) * f.sx, f.y - 3f * k, f.x + (rb + 9f) * f.sx, f.y - 3f * k + (rb * t * 2f + 8f) * k)
    canvas.drawOval(art.rect, art.soft)
    art.rect.set(f.x - (rb + 1.5f) * gx * k, f.y - rb * t * k, f.x + (rb + 1.5f) * gx * k, f.y - rb * t * k + (rb * t * 2f + 1.5f) * k)
    canvas.drawOval(art.rect, art.tight)

    canvas.save()
    canvas.translate(f.x, f.y)
    canvas.rotate(f.rot)
    canvas.scale(f.sx, f.sy)

    // Body: brushed steel, darker towards the foot, a sharp highlight and a faint second one.
    val fill = art.fill
    val stroke = art.stroke
    fill.shader = art.steel
    canvas.drawPath(art.body, fill)
    canvas.save()
    canvas.clipPath(art.body)
    fill.shader = art.dusk
    canvas.drawRect(-BIN_TOP_R, -h - 20f, BIN_TOP_R, 10f, fill)
    fill.shader = null
    canvas.drawLine(-BIN_TOP_R * 0.37f, -h + 5f, -rb * 0.37f, -FOOT - 2f, strokeWith(stroke, argb(0xFFFFFF, 0.92f), 1.4f))
    canvas.drawLine(BIN_TOP_R * 0.6f, -h + 6f, rb * 0.6f, -FOOT - 3f, strokeWith(stroke, argb(0xFFFFFF, 0.28f), 1f))
    canvas.drawPath(art.bead, strokeWith(stroke, argb(0x1E2024, 0.3f), 1.1f))
    canvas.restore()
    // The foot ring and the pedal, pressed down a little as the lid opens.
    canvas.drawPath(art.footRing, fillWith(fill, argb(0x26282B)))
    canvas.drawPath(art.footEdge, strokeWith(stroke, argb(0xFFFFFF, 0.2f), 0.8f))
    canvas.drawPath(art.body, strokeWith(stroke, argb(0x1C1E21, 0.55f), 0.8f))
    val down = 1.6f * clamp01(b.lid)
    art.rect.set(-11f, rb * t - 3f + down, 11f, rb * t + 3f + down)
    canvas.drawRoundRect(art.rect, 2.6f, 2.6f, fillWith(fill, argb(0x1D1F22)))
    art.rect.set(-10.4f, rb * t - 2.6f + down, 10.4f, rb * t - 0.4f + down)
    canvas.drawRoundRect(art.rect, 1.1f, 1.1f, fillWith(fill, argb(0x4B4F54)))

    // The counter plate, its digits rolling up to the new number.
    canvas.drawRoundRect(art.plate, 4f, 4f, fillWith(fill, argb(0x202226)))
    canvas.drawRoundRect(art.plate, 4f, 4f, strokeWith(stroke, argb(0xFFFFFF, 0.16f), 0.6f))
    canvas.save()
    canvas.clipPath(art.platePath)
    val r = clamp01(b.roll)
    val e = if (r >= 1f) 1f else easeOutBack(r)
    val base = PLATE_Y + 4.1f
    if (r < 1f) drawNumber(canvas, art, b.prev, base - e * PLATE_H)
    drawNumber(canvas, art, b.count, base + (1f - e) * PLATE_H)
    canvas.restore()

    // The rim, and the dark mouth inside it.
    canvas.drawOval(art.rim, fillWith(fill, argb(0xE4E7E9)))
    fill.shader = art.cavity
    canvas.drawOval(art.mouth, fill)
    fill.shader = null
    canvas.drawOval(art.rim, strokeWith(stroke, argb(0x3A3D41), 0.9f))
    canvas.drawOval(art.mouth, strokeWith(stroke, argb(0x000000, 0.5f), 0.7f))

    drawLid(canvas, art, b.lid)
    // The hinge, seen once the lid stands up behind the mouth.
    if (b.lid > 0.3f) {
        val y = -h - (BIN_TOP_R + 0.9f) * t - 2.4f
        art.rect.set(-8f, y, 8f, y + 3.6f)
        canvas.drawRoundRect(art.rect, 1.2f, 1.2f, fillWith(fill, argb(0x2C2F33)))
    }
}
