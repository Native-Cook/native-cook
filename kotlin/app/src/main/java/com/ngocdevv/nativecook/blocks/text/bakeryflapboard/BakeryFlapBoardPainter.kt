package com.ngocdevv.nativecook.blocks.text.bakeryflapboard

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

// Colours: walnut case, charcoal flaps, cream letters, amber prices, brass fittings.
internal const val BACKGROUND = 0xFF1A0F09.toInt()
private const val FLAP_TOP = 0xFF2C2723.toInt()
private const val FLAP_BOTTOM = 0xFF24201C.toInt()
private const val SPLIT = 0xFF0B0908.toInt()
private const val LETTER = 0xFFF2E8D2.toInt()
private const val PRICE = 0xFFF3B85E.toInt()
internal const val BRASS = 0xFFC9A25E.toInt()
private const val BRASS_DARK = 0xFF6E4F24.toInt()
private const val BLACK = 0xFF000000.toInt()

// A falling half turns faster as it goes (a share FALL_LINEAR of its turn is linear, the rest grows
// with the square). The last flap of a cell lands with a bounce of BOUNCE_ANGLE rad. CAMERA is the
// distance of the eye, in flap heights.
private const val FALL_LINEAR = 0.35f
private const val BOUNCE_ANGLE = 0.3f
private const val CAMERA = 3.4f
// Shade on a flap turned edge-on, and on the half it covers.
private const val FLAP_SHADE = 0.55f
private const val CAST_SHADE = 0.4f

private val HALF_PI = (PI / 2).toFloat()

/**
 * An outline hanging from a baseline at y = 0, and its ink box measured the way the React Native
 * version's Skia measures it (the outline rounded out to whole dp, plus one on every side).
 */
internal class FlapGlyph(val path: Path, val x: Float, val w: Float, val y: Float)

internal fun flapGlyph(ch: Char, paint: Paint): FlapGlyph {
    val path = Path()
    paint.getTextPath(ch.toString(), 0, 1, 0f, 0f, path)
    val ink = RectF()
    path.computeBounds(ink, true)
    if (ink.isEmpty) return FlapGlyph(path, 0f, 0f, 0f)
    val left = floor(ink.left) - 1f
    val right = ceil(ink.right) + 1f
    return FlapGlyph(path, left, right - left, floor(ink.top) - 1f)
}

private fun condensed(weight: Int): Typeface {
    val base = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(base, weight, false)
    else Typeface.create("sans-serif-condensed", if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
}

internal class BakeryFlapBoardPaints {
    val flapFont = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = condensed(700); textSize = 25f }
    val labelFont = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = condensed(600); textSize = 10.5f }
    /** The drum's characters on the flap face; the space borrows H's box but is never drawn. */
    val glyphs = DRUM.map { flapGlyph(if (it == ' ') 'H' else it, flapFont) }
    val capHeight = -flapGlyph('H', flapFont).y
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val shape = Path()
    val letter = Path()
    val matrix = Matrix()
    val values = FloatArray(9)
    val rect = RectF()
    val cell = FlapCell()
}

private fun Paint.tone(color: Int, alpha: Float = 1f): Paint {
    shader = null
    maskFilter = null
    this.color = color
    // roundToInt throws on NaN, so a stray NaN shade draws nothing instead.
    this.alpha = if (alpha.isNaN()) 0 else (alpha * 255f).roundToInt().coerceIn(0, 255)
    return this
}

/** One half of a flap's face, with its top or bottom corners rounded. */
private fun halfShape(path: Path, x: Float, y: Float, w: Float, h: Float, top: Boolean) {
    val r = 3f
    val mid = y + h / 2f
    path.rewind()
    if (top) {
        path.moveTo(x, mid)
        path.lineTo(x, y + r)
        path.quadTo(x, y, x + r, y)
        path.lineTo(x + w - r, y)
        path.quadTo(x + w, y, x + w, y + r)
        path.lineTo(x + w, mid)
    } else {
        path.moveTo(x, mid)
        path.lineTo(x + w, mid)
        path.lineTo(x + w, y + h - r)
        path.quadTo(x + w, y + h, x + w - r, y + h)
        path.lineTo(x + r, y + h)
        path.quadTo(x, y + h, x, y + h - r)
    }
    path.close()
}

/**
 * Sets `m` to turn a half flap about the hinge line `axis` by `angle` (0 flat, ±π/2 edge on), seen
 * from `camera` away: the edge swinging towards the viewer grows.
 */
private fun hinge(p: BakeryFlapBoardPaints, cx: Float, axis: Float, angle: Float, camera: Float): Matrix {
    val k = sin(angle) / camera
    val c = cos(angle)
    val v = p.values
    v[0] = 1f; v[1] = cx * k; v[2] = -cx * k * axis
    v[3] = 0f; v[4] = c + axis * k; v[5] = axis * (1 - c) - k * axis * axis
    v[6] = 0f; v[7] = k; v[8] = 1 - k * axis
    p.matrix.setValues(v)
    return p.matrix
}

/** One half of a flap (top or bottom) with its half of character `ch`, shaded by `shade`. */
private fun half(canvas: Canvas, p: BakeryFlapBoardPaints, x: Float, y: Float, w: Float, h: Float, top: Boolean, ch: Int, color: Int, shade: Float, baseline: Float) {
    val mid = y + h / 2f
    halfShape(p.shape, x, y, w, h, top)
    canvas.drawPath(p.shape, p.fill.tone(if (top) FLAP_TOP else FLAP_BOTTOM))
    if (DRUM[ch] != ' ') {
        val glyph = p.glyphs[ch]
        canvas.save()
        if (top) canvas.clipRect(x, y, x + w, mid) else canvas.clipRect(x, mid, x + w, y + h)
        p.letter.set(glyph.path)
        p.letter.offset(x + (w - glyph.w) / 2f - glyph.x, y + baseline)
        canvas.drawPath(p.letter, p.fill.tone(color))
        canvas.restore()
    }
    if (shade > 0.003f) canvas.drawPath(p.shape, p.fill.tone(BLACK, shade))
}

/** A whole cell: settled, flipping (two static halves and one turning), or bouncing. */
private fun cell(canvas: Canvas, p: BakeryFlapBoardPaints, x: Float, y: Float, w: Float, h: Float, cell: FlapCell, color: Int, baseline: Float, reducedMotion: Boolean) {
    val cx = x + w / 2f
    val axis = y + h / 2f
    if (cell.p < 0f && cell.bounce < 0f) {
        if (DRUM[cell.cur] != ' ') {
            val glyph = p.glyphs[cell.cur]
            p.letter.set(glyph.path)
            p.letter.offset(x + (w - glyph.w) / 2f - glyph.x, y + baseline)
            canvas.drawPath(p.letter, p.fill.tone(color))
        }
        return
    }
    val camera = CAMERA * h
    if (cell.p < 0f) {
        // The last flap has landed and lifts once off its stop.
        val q = cell.bounce
        val lift = BOUNCE_ANGLE * sin(PI.toFloat() * q) * (1 - q)
        half(canvas, p, x, y, w, h, true, cell.cur, color, 0f, baseline)
        half(canvas, p, x, y, w, h, false, cell.cur, color, CAST_SHADE * (lift / BOUNCE_ANGLE) * 0.6f, baseline)
        canvas.save()
        canvas.concat(hinge(p, cx, axis, -lift, camera))
        half(canvas, p, x, y, w, h, false, cell.cur, color, lift * 0.5f, baseline)
        canvas.restore()
        return
    }
    if (reducedMotion) {
        // Without the turn the letters just change, half by half.
        val ch = if (cell.p < 0.5f) cell.cur else cell.next
        half(canvas, p, x, y, w, h, true, ch, color, 0f, baseline)
        half(canvas, p, x, y, w, h, false, ch, color, 0f, baseline)
        return
    }
    val t = cell.p
    val angle = PI.toFloat() * (FALL_LINEAR * t + (1 - FALL_LINEAR) * t * t)
    val firstHalf = angle < HALF_PI
    // Behind the falling flap: the next letter's top, darkened while the flap still hangs over it.
    half(canvas, p, x, y, w, h, true, cell.next, color, if (firstHalf) CAST_SHADE * sin(angle) else 0f, baseline)
    // Below: the current letter's bottom, shaded as the flap comes down onto it.
    half(canvas, p, x, y, w, h, false, cell.cur, color, if (firstHalf) 0f else CAST_SHADE * (angle / HALF_PI - 1), baseline)
    canvas.save()
    if (firstHalf) {
        canvas.concat(hinge(p, cx, axis, angle, camera))
        half(canvas, p, x, y, w, h, true, cell.cur, color, FLAP_SHADE * (angle / HALF_PI), baseline)
    } else {
        canvas.concat(hinge(p, cx, axis, angle - PI.toFloat(), camera))
        half(canvas, p, x, y, w, h, false, cell.next, color, FLAP_SHADE * (2 - angle / HALF_PI), baseline)
    }
    canvas.restore()
}

/** Every flap of every line this frame. */
internal fun drawFlaps(canvas: Canvas, p: BakeryFlapBoardPaints, m: BakeryFlapBoardMotion, layout: BoardLayout) {
    val baseline = layout.cellH / 2f + p.capHeight / 2f
    val rows = m.rows
    for (r in rows.indices) {
        val y = layout.rows[r]
        for (i in 0 until COLUMNS) {
            // Prices are set in amber, the rest in cream.
            val color = if (r >= TIME_ROWS && i >= COLUMNS - 4) PRICE else LETTER
            cell(canvas, p, layout.cellX(i), y, layout.cellW, layout.cellH, cellAt(rows[r], i, m.elapsed, p.cell), color, baseline, m.reducedMotion)
        }
    }
}

/** Letters `text` spaced by `track` dp from `x`; returns the width they take. */
private fun spaced(canvas: Canvas, p: BakeryFlapBoardPaints, text: String, x: Float, baseline: Float, track: Float): Float {
    var at = x
    for (ch in text) {
        if (ch == ' ') {
            at += p.labelFont.textSize * 0.32f + track
            continue
        }
        val glyph = flapGlyph(ch, p.labelFont)
        glyph.path.offset(at, baseline)
        canvas.drawPath(glyph.path, p.fill.tone(BRASS))
        at += glyph.w + track
    }
    return at - x - track
}

/** The case, the flap backs and the section labels: everything under the letters that never moves. */
internal fun drawCase(canvas: Canvas, p: BakeryFlapBoardPaints, b: BoardLayout, labels: List<String>) {
    val shadow = p.fill.tone(BLACK, 0.45f)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // Skia's blur sigma 10, as a radius.
        shadow.maskFilter = BlurMaskFilter((10f - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(b.x, b.y + 8f, b.x + b.w, b.y + b.h + 8f, 14f, 14f, shadow)
        shadow.maskFilter = null
    } else {
        // No blurred mask on older hardware canvases: a few soft steps instead.
        for (k in 3 downTo 1) {
            val o = k * 4f
            canvas.drawRoundRect(b.x - o, b.y + 8f - o, b.x + b.w + o, b.y + b.h + 8f + o, 14f + o, 14f + o, p.fill.tone(BLACK, 0.12f))
        }
    }
    canvas.drawRoundRect(b.x, b.y, b.x + b.w, b.y + b.h, 14f, 14f, p.fill.tone(0xFF120D0A.toInt()))
    p.stroke.shader = LinearGradient(
        b.x, b.y, b.x + b.w, b.y + b.h,
        intArrayOf(0xFF7A5A2C.toInt(), 0xFFE8C787.toInt(), 0xFF946F35.toInt(), 0xFFDCB66F.toInt(), 0xFF5F4420.toInt()),
        null, Shader.TileMode.CLAMP,
    )
    p.stroke.strokeWidth = 2.5f
    p.stroke.alpha = 255
    canvas.drawRoundRect(b.x, b.y, b.x + b.w, b.y + b.h, 14f, 14f, p.stroke)
    p.stroke.shader = null
    // Brass screws in the corners.
    for ((sx, sy) in listOf(b.x + 9f to b.y + 9f, b.x + b.w - 9f to b.y + 9f, b.x + 9f to b.y + b.h - 9f, b.x + b.w - 9f to b.y + b.h - 9f)) {
        canvas.drawCircle(sx, sy, 3.2f, p.fill.tone(BRASS_DARK))
        canvas.drawCircle(sx - 0.6f, sy - 0.6f, 2.3f, p.fill.tone(BRASS))
        p.stroke.color = BRASS_DARK
        p.stroke.strokeWidth = 1f / 3f
        canvas.drawLine(sx - 1.8f, sy + 1.2f, sx + 1.2f, sy - 1.8f, p.stroke)
    }

    for ((k, y) in b.labels.withIndex()) {
        if (k >= labels.size) break
        val width = spaced(canvas, p, labels[k], b.left, y, 2.6f)
        val from = b.left + width + 10f
        canvas.drawRect(from, y - 4f, b.x + b.w - PAD, y - 3.2f, p.fill.tone(BRASS, 0.35f))
    }

    for (r in b.rows.indices) {
        for (i in 0 until COLUMNS) {
            val x = b.cellX(i)
            val y = b.rows[r]
            canvas.drawRoundRect(x, y + 1.2f, x + b.cellW, y + 1.2f + b.cellH, 3f, 3f, p.fill.tone(BLACK, 0.6f))
            halfShape(p.shape, x, y, b.cellW, b.cellH, true)
            canvas.drawPath(p.shape, p.fill.tone(FLAP_TOP))
            halfShape(p.shape, x, y, b.cellW, b.cellH, false)
            canvas.drawPath(p.shape, p.fill.tone(FLAP_BOTTOM))
        }
    }
}

/** The split across every flap and its two brass hinge pins, drawn over the letters. */
internal fun drawHinges(canvas: Canvas, p: BakeryFlapBoardPaints, b: BoardLayout) {
    for (r in b.rows.indices) {
        for (i in 0 until COLUMNS) {
            val x = b.cellX(i)
            val mid = b.rows[r] + b.cellH / 2f
            canvas.drawRect(x, mid - 0.7f, x + b.cellW, mid + 0.7f, p.fill.tone(SPLIT))
            canvas.drawRect(x + 1f, b.rows[r] + 0.6f, x + b.cellW - 1f, b.rows[r] + 1.2f, p.fill.tone(0xFFFFFFFF.toInt(), 0.05f))
            canvas.drawCircle(x + 1.6f, mid, 1.2f, p.fill.tone(BRASS_DARK))
            canvas.drawCircle(x + b.cellW - 1.6f, mid, 1.2f, p.fill.tone(BRASS_DARK))
        }
    }
}
