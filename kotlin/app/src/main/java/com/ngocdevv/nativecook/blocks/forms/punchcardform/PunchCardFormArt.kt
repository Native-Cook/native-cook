package com.ngocdevv.nativecook.blocks.forms.punchcardform

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.CANVAS_H
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.CARD_H
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.CARD_W
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.CARD_Y
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.COLS
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.DESIGN_W
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.FIELDS
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.MARGIN_TOP
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.SLOT_Y
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.STATION_X
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.colX
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.rowY
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Static art, baked once off the main thread at [scale] px per dp. */
internal class PunchCardArtSet(
    val scale: Float,
    /** The blank card with its shadows, CARD_PAD dp around the card. */
    val card: Bitmap,
    /** The bed (DESIGN_W × CANVAS_H dp, with its shadow). */
    val bed: Bitmap,
    /** The reader across the bottom (DESIGN_W × CANVAS_H dp, transparent above the slot). */
    val reader: Bitmap,
    /** The punch head, HEAD_BOX dp square centred on the station. */
    val head: Bitmap,
    /** A repeating grain tile for the room. */
    val grain: Bitmap,
)

internal object PunchCardFormArt {
    const val CARD_PAD = 18f
    const val HEAD_BOX = 80f

    private fun argb(a: Float, r: Int, g: Int, b: Int) = ((a * 255f).roundToInt().coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b
    private fun hex(c: Long) = c.toInt()
    private fun sigma(s: Float) = max(0.01f, (s - 0.5f) / 0.57735f)

    private fun lin(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, pos: FloatArray? = null) =
        LinearGradient(x0, y0, x1, y1, colors, pos, Shader.TileMode.CLAMP)

    private fun fill(shader: Shader) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }

    private fun blurred(alpha: Float, sigmaDp: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = argb(alpha, 0, 0, 0)
        maskFilter = BlurMaskFilter(sigma(sigmaDp), BlurMaskFilter.Blur.NORMAL)
    }

    private fun mono(size: Float, bold: Boolean, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
        textSize = size
        this.color = color
    }

    private fun centred(c: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        c.drawText(text, x - paint.measureText(text) / 2f, y, paint)
    }

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777215f
    }

    /**
     * A wrapping tile of fractal value noise (integer hashes), [cx] × [cy] cells for its first
     * octave. [signed]: white where lighter than mid-grey, black where darker (an overlay that does
     * not need a blend mode); otherwise black with alpha (1 − v), a darkening grain.
     */
    private suspend fun tile(w: Int, h: Int, cx: Int, cy: Int, seed: Int, strength: Float, signed: Boolean): Bitmap {
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            currentCoroutineContext().ensureActive()
            for (x in 0 until w) {
                var v = 0f
                var amp = 0.5f
                var total = 0f
                var px = cx
                var py = cy
                for (o in 0 until 3) {
                    val fx = x * px / w.toFloat()
                    val fy = y * py / h.toFloat()
                    val ix = floor(fx).toInt()
                    val iy = floor(fy).toInt()
                    val ux = fx - ix
                    val uy = fy - iy
                    val sx = ux * ux * (3 - 2 * ux)
                    val sy = uy * uy * (3 - 2 * uy)
                    val a = hash(ix % px, iy % py, seed + o)
                    val b = hash((ix + 1) % px, iy % py, seed + o)
                    val c = hash(ix % px, (iy + 1) % py, seed + o)
                    val d = hash((ix + 1) % px, (iy + 1) % py, seed + o)
                    val top = a + (b - a) * sx
                    val bottom = c + (d - c) * sx
                    v += amp * (top + (bottom - top) * sy)
                    total += amp
                    amp *= 0.5f
                    px *= 2
                    py *= 2
                }
                v /= total
                pixels[y * w + x] = if (signed) {
                    val dv = (v - 0.5f) * 2f
                    val a = (abs(dv) * strength * 255f).roundToInt().coerceIn(0, 255)
                    (a shl 24) or (if (dv > 0) 0xFFFFFF else 0)
                } else {
                    ((1f - v) * strength * 255f).roundToInt().coerceIn(0, 255) shl 24
                }
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun tiled(bmp: Bitmap, s: Float) = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(android.graphics.Matrix().apply { setScale(1f / s, 1f / s) })
        }
    }

    private fun cardOutline(): Path {
        val cut = 16f
        return Path().apply {
            moveTo(cut, 0f)
            lineTo(CARD_W - 4f, 0f)
            quadTo(CARD_W, 0f, CARD_W, 4f)
            lineTo(CARD_W, CARD_H - 4f)
            quadTo(CARD_W, CARD_H, CARD_W - 4f, CARD_H)
            lineTo(4f, CARD_H)
            quadTo(0f, CARD_H, 0f, CARD_H - 4f)
            lineTo(0f, cut * 0.9f)
            close()
        }
    }

    /** The blank card: manila stock with a cut corner, printed digits, field rules and labels, grain. */
    private suspend fun bakeCard(s: Float): Bitmap {
        val w = CARD_W + CARD_PAD * 2
        val h = CARD_H + CARD_PAD * 2
        val bmp = Bitmap.createBitmap(ceil(w * s).toInt(), ceil(h * s).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(s, s)
        c.translate(CARD_PAD, CARD_PAD)
        val outline = cardOutline()
        c.save(); c.translate(0f, 6f); c.drawPath(outline, blurred(0.4f, 9f)); c.restore()
        c.save(); c.translate(0f, 1f); c.drawPath(outline, blurred(0.45f, 1.5f)); c.restore()

        c.save()
        c.clipPath(outline)
        c.drawRect(0f, 0f, CARD_W, CARD_H, fill(lin(0f, 0f, CARD_W * 0.3f, CARD_H, intArrayOf(hex(0xFFEADCB6), hex(0xFFE2D0A4), hex(0xFFD5BF8F)), floatArrayOf(0f, 0.55f, 1f))))
        // Paper grain (a darkening tile, not a multiply blend) and long faint fibres.
        val grain = tile(128, 128, (128 * 0.9f / s).roundToInt(), (128 * 0.9f / s).roundToInt(), 5, 0.1f, false)
        c.drawRect(0f, 0f, CARD_W, CARD_H, tiled(grain, s))
        val fibre = tile(512, 64, max(1, (512 * 0.02f / s).roundToInt()), (64 * 0.6f / s).roundToInt(), 9, 0.12f, true)
        c.drawRect(0f, 0f, CARD_W, CARD_H, tiled(fibre, s))
        currentCoroutineContext().ensureActive()
        c.drawRect(0f, CARD_H - 10f, CARD_W, CARD_H, Paint().apply { color = argb(0.14f, 0xB4, 0x55, 0x3F) })
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(0.55f, 0x9A, 0x44, 0x33); strokeWidth = 0.7f }
        val label = mono(7f, true, argb(0.85f, 0x8A, 0x3B, 0x2C))
        for (f in FIELDS) {
            for (col in intArrayOf(f.first, f.first + f.size)) {
                val x = colX(col.toFloat()) - 4.2f
                c.drawLine(x, MARGIN_TOP - 4f, x, CARD_H - 10f, rule)
            }
            centred(c, f.label, (colX(f.first.toFloat()) + colX((f.first + f.size - 1).toFloat())) / 2f, CARD_H - 2.6f, label)
        }
        centred(c, "ANALYTICAL SOCIETY · MEMBER CARD · FORM 29-A", colX(57f), CARD_H - 2.6f, mono(7f, true, argb(0.7f, 0x8A, 0x3B, 0x2C)))
        val digit = mono(6.2f, false, argb(0.62f, 0x5C, 0x3A, 0x2A))
        val small = mono(4.6f, false, argb(0.5f, 0x5C, 0x3A, 0x2A))
        for (col in 1..COLS) {
            val x = colX(col.toFloat())
            for (d in 0..9) centred(c, d.toString(), x, rowY(d + 2) + 2.2f, digit)
            centred(c, col.toString(), x, rowY(2) + 9.6f, small)
            centred(c, col.toString(), x, rowY(11) + 9.6f, small)
        }
        c.drawLine(16f, 0.6f, CARD_W, 0.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(0.5f, 255, 255, 255) })
        c.restore()
        c.drawPath(outline, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            color = argb(0.6f, 0x8C, 0x74, 0x49)
        })
        return bmp
    }

    /** The bed the card slides on: dark machine enamel with brushed grain and two steel guides. */
    private suspend fun bakeBed(s: Float): Bitmap {
        val bmp = Bitmap.createBitmap(ceil(DESIGN_W * s).toInt(), ceil(CANVAS_H * s).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(s, s)
        val rect = RectF(6f, 6f, DESIGN_W - 6f, 6f + SLOT_Y + 4f)
        val bed = Path().apply { addRoundRect(rect, 18f, 18f, Path.Direction.CW) }
        c.drawPath(bed, blurred(0.55f, 10f))
        c.drawPath(bed, fill(lin(0f, 6f, 0f, SLOT_Y, intArrayOf(hex(0xFF3A423C), hex(0xFF2C332E), hex(0xFF232925)), floatArrayOf(0f, 0.5f, 1f))))
        c.save()
        c.clipPath(bed)
        val brush = tile(512, 64, max(1, (512 * 0.003f / s).roundToInt()), (64 * 0.5f / s).roundToInt(), 12, 0.3f, true)
        c.drawRect(0f, 0f, DESIGN_W, CANVAS_H, tiled(brush, s))
        c.drawRect(
            0f, 0f, DESIGN_W, CANVAS_H,
            fill(RadialGradient(STATION_X + 40f, CARD_Y + CARD_H * 0.45f, 260f, argb(0.16f, 255, 214, 150), argb(0f, 255, 214, 150), Shader.TileMode.CLAMP)),
        )
        c.restore()
        c.drawPath(bed, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = argb(0.18f, 0x9F, 0xB0, 0xA4)
        })
        for (y in floatArrayOf(CARD_Y - 9f, CARD_Y + CARD_H + 2f)) {
            c.drawRoundRect(
                RectF(14f, y, DESIGN_W - 14f, y + 7f), 3.5f, 3.5f,
                fill(lin(0f, y, 0f, y + 7f, intArrayOf(hex(0xFFC9D0CC), hex(0xFF7E8884), hex(0xFF4A524E)))),
            )
        }
        return bmp
    }

    /** The reader across the bottom: a slot, an enamel front with a vent, a plate and four lamp bezels. */
    private suspend fun bakeReader(s: Float): Bitmap {
        val bmp = Bitmap.createBitmap(ceil(DESIGN_W * s).toInt(), ceil(CANVAS_H * s).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(s, s)
        val top = SLOT_Y
        val h = CANVAS_H - top - 4f
        val body = RectF(4f, top, DESIGN_W - 4f, top + h)
        c.drawRoundRect(RectF(4f, top - 4f, DESIGN_W - 4f, top - 4f + h), 16f, 16f, blurred(0.5f, 8f))
        c.drawRoundRect(body, 16f, 16f, fill(lin(0f, top, 0f, top + h, intArrayOf(hex(0xFF58625B), hex(0xFF434C46), hex(0xFF2F3632)), floatArrayOf(0f, 0.35f, 1f))))
        c.save()
        c.clipPath(Path().apply { addRoundRect(body, 16f, 16f, Path.Direction.CW) })
        val brush = tile(512, 64, max(1, (512 * 0.003f / s).roundToInt()), (64 * 0.6f / s).roundToInt(), 31, 0.32f, true)
        c.drawRect(0f, top, DESIGN_W, top + h, tiled(brush, s))
        c.drawRect(4f, top, DESIGN_W - 4f, top + 10f, fill(lin(0f, top, 0f, top + 12f, intArrayOf(hex(0xFF050605), hex(0xFF141815), hex(0xFF2A302C)))))
        c.drawRect(4f, top + 10f, DESIGN_W - 4f, top + 11f, Paint().apply { color = argb(0.35f, 0xD7, 0xE0, 0xDA) })
        val vent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(0.85f, 0x14, 0x18, 0x15) }
        for (i in 0 until 6) c.drawRoundRect(RectF(DESIGN_W - 120f + i * 16f, top + 34f, DESIGN_W - 112f + i * 16f, top + 74f), 4f, 4f, vent)
        c.restore()
        c.drawRoundRect(
            RectF(24f, top + 30f, 172f, top + 60f), 4f, 4f,
            fill(lin(24f, top + 30f, 170f, top + 70f, intArrayOf(hex(0xFFE9CF8A), hex(0xFFB98E45), hex(0xFF7E5A24), hex(0xFFC9A35C)), floatArrayOf(0f, 0.4f, 0.75f, 1f))),
        )
        centred(c, "CARD READER · MODEL 2", 98f, top + 48f, mono(7f, true, argb(0.9f, 0x3A, 0x28, 0x10)))
        for (i in 0 until 4) {
            val x = 36f + i * 30f
            c.drawCircle(
                x, top + 80f, 7.5f,
                fill(RadialGradient(x - 1f, top + 79f, 9f, intArrayOf(hex(0xFFD6DDD8), hex(0xFF6D7671), hex(0xFF262B28)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)),
            )
            c.drawCircle(x, top + 80f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hex(0xFF101311) })
        }
        return bmp
    }

    /** The punch head above the station: a steel die block with a red column pointer, centred on (0, 0). */
    private fun bakeHead(s: Float): Bitmap {
        val bmp = Bitmap.createBitmap(ceil(HEAD_BOX * s).toInt(), ceil(HEAD_BOX * s).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(s, s)
        c.translate(HEAD_BOX / 2f, HEAD_BOX / 2f)
        c.drawRoundRect(RectF(-17f, -22f, 17f, 2f), 5f, 5f, blurred(0.55f, 4f))
        c.drawRoundRect(
            RectF(-17f, -26f, 17f, -2f), 5f, 5f,
            fill(lin(-17f, 0f, 17f, 0f, intArrayOf(hex(0xFF5E6763), hex(0xFFD5DCD8), hex(0xFF9AA39F), hex(0xFF4C5450)), floatArrayOf(0f, 0.35f, 0.65f, 1f))),
        )
        c.drawLine(-13f, -25.4f, 13f, -25.4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(0.5f, 255, 255, 255) })
        c.drawPath(Path().apply { moveTo(-4f, -4f); lineTo(4f, -4f); lineTo(0f, 1f); close() }, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hex(0xFFD8483A) })
        return bmp
    }

    suspend fun bake(density: Float): PunchCardArtSet {
        val s = min(2f, density)
        val card = bakeCard(s)
        currentCoroutineContext().ensureActive()
        val bed = bakeBed(s)
        val reader = bakeReader(s)
        val head = bakeHead(s)
        val grain = tile(160, 160, 64, 64, 3, 0.1f, true)
        return PunchCardArtSet(s, card, bed, reader, head, grain)
    }
}
