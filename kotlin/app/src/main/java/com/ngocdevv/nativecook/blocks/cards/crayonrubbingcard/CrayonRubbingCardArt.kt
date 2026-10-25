package com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.CELL
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.GX
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.GY
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.PAPER_H
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.PAPER_W
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** A wax stick: the shader's colour (0–1 sRGB) and the crayon's body tones. */
internal class CrayonRubbingWax(val name: String, val r: Float, val g: Float, val b: Float, val tones: IntArray)

internal val CrayonRubbingWaxes = listOf(
    CrayonRubbingWax("INDIGO", 0.17f, 0.19f, 0.36f, intArrayOf(0xFF5A6296.toInt(), 0xFF2C3160.toInt(), 0xFF181B3A.toInt())),
    CrayonRubbingWax("OXBLOOD", 0.4f, 0.11f, 0.12f, intArrayOf(0xFF9A4A48.toInt(), 0xFF651D20.toInt(), 0xFF3E0F12.toInt())),
    CrayonRubbingWax("GRAPHITE", 0.15f, 0.15f, 0.16f, intArrayOf(0xFF6E6E72.toInt(), 0xFF38383C.toInt(), 0xFF1D1D20.toInt())),
)

/** Static art, baked off the main thread. */
internal class CrayonRubbingArtSet(
    /** The plaque's hidden relief as a height image (white = raised), RELIEF_BAKE px per dp over the paper. */
    val relief: Bitmap,
    /** How much raised relief each grid cell holds (0 … 1), and their sum, for the reveal %. */
    val weights: FloatArray,
    val total: Float,
    /** The three crayons (no shadow), each covering CRAYON_LEFT … in x and ±CRAYON_HALF in y around the tip. */
    val crayons: List<Bitmap>,
    /** The crayon's shadow at blur 3 (pressing) and 6 (lifted), SHADOW_MARGIN dp around a 92 × 18 rounded rect. */
    val shadowNear: Bitmap,
    val shadowFar: Bitmap,
    /** A repeating felt grain tile for the table. */
    val felt: Bitmap,
    val scale: Float,
)

internal object CrayonRubbingCardArt {
    const val RELIEF_BAKE = 2f
    const val CRAYON_LEFT = -4f
    const val CRAYON_RIGHT = 100f
    const val CRAYON_HALF = 12f
    const val SHADOW_MARGIN = 16f

    private fun argb(a: Float, r: Int, g: Int, b: Int) = ((a * 255f).roundToInt().coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b
    private fun sigma(s: Float) = max(0.01f, (s - 0.5f) / 0.57735f)

    // The relief's shapes are drawn with a soft mask (Skia's blurred layer, sigma 1.6 dp) so their
    // edges slope, which the shader turns into the paper's bend and the wax's falloff.
    private val reliefBlur = BlurMaskFilter(sigma(1.6f), BlurMaskFilter.Blur.NORMAL)

    private fun white(alpha: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = argb(alpha, 255, 255, 255)
        maskFilter = reliefBlur
    }

    private fun stroke(width: Float, alpha: Float = 1f) = white(alpha).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun centredText(c: Canvas, text: String, x: Float, y: Float, size: Float, track: Float, alpha: Float) {
        val p = white(alpha).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textSize = size
        }
        val widths = FloatArray(text.length)
        p.getTextWidths(text, widths)
        var total = 0f
        for (w in widths) total += w + track
        total -= track
        var at = x - total / 2f
        for (i in text.indices) {
            c.drawText(text[i].toString(), at, y, p)
            at += widths[i] + track
        }
    }

    private fun heron(c: Canvas) {
        val body = Path().apply {
            moveTo(146f, 168f)
            cubicTo(170f, 160f, 204f, 172f, 226f, 206f)
            cubicTo(232f, 216f, 236f, 226f, 244f, 236f)
            cubicTo(226f, 232f, 214f, 230f, 204f, 236f)
            cubicTo(186f, 246f, 160f, 240f, 148f, 222f)
            cubicTo(138f, 206f, 136f, 182f, 146f, 168f)
            close()
        }
        c.drawPath(body, white(0.95f))
        val neck = Path().apply {
            moveTo(150f, 176f)
            cubicTo(126f, 160f, 152f, 132f, 140f, 116f)
            cubicTo(132f, 104f, 118f, 100f, 120f, 90f)
        }
        c.drawPath(neck, stroke(11f, 0.95f))
        c.drawCircle(122f, 88f, 8.5f, white(0.97f))
        val beak = Path().apply { moveTo(116f, 84f); lineTo(70f, 96f); lineTo(116f, 93f); close() }
        c.drawPath(beak, white(1f))
        val crest = Path().apply { moveTo(126f, 82f); cubicTo(140f, 76f, 150f, 78f, 160f, 74f) }
        c.drawPath(crest, stroke(2.4f, 0.9f))
        val wing = Path().apply {
            moveTo(162f, 178f)
            cubicTo(188f, 176f, 214f, 196f, 236f, 230f)
            cubicTo(210f, 222f, 182f, 222f, 164f, 208f)
            cubicTo(154f, 198f, 154f, 186f, 162f, 178f)
            close()
        }
        c.drawPath(wing, white(1f))
        for (i in 0 until 3) {
            val cut = Path().apply {
                moveTo(172f + i * 14, 190f + i * 6)
                cubicTo(186f + i * 14, 198f + i * 6, 200f + i * 14, 210f + i * 6, 212f + i * 10, 222f)
            }
            c.drawPath(cut, stroke(1.6f, 1f).apply { color = 0xFF8A8A8A.toInt() })
        }
        c.drawCircle(124f, 86f, 1.8f, white().apply { color = 0xFF9A9A9A.toInt() })
        val legs = Path().apply {
            moveTo(184f, 238f); lineTo(180f, 272f); lineTo(184f, 304f)
            moveTo(198f, 236f); lineTo(204f, 270f); lineTo(200f, 304f)
            moveTo(172f, 306f); lineTo(186f, 304f); lineTo(196f, 308f)
            moveTo(192f, 306f); lineTo(202f, 304f); lineTo(214f, 308f)
        }
        c.drawPath(legs, stroke(3.2f, 0.92f))
    }

    private fun reeds(c: Canvas) {
        val stems = arrayOf(
            floatArrayOf(246f, 304f, 150f), floatArrayOf(258f, 304f, 132f), floatArrayOf(270f, 306f, 170f),
            floatArrayOf(284f, 306f, 196f), floatArrayOf(54f, 306f, 188f), floatArrayOf(66f, 306f, 214f),
        )
        for ((x, y0, top) in stems) {
            val bend = (x - 160f) * 0.08f
            c.drawPath(Path().apply { moveTo(x, y0); quadTo(x + bend, (y0 + top) / 2f, x + bend * 1.6f, top) }, stroke(2.2f, 0.85f))
            if (top < 180f) c.drawOval(RectF(x + bend * 1.6f - 3.2f, top - 20f, x + bend * 1.6f + 3.2f, top + 2f), white(0.9f))
            val dir = if (x > 160f) 1f else -1f
            c.drawPath(
                Path().apply {
                    moveTo(x + bend * 0.5f, y0 - 40f)
                    quadTo(x + bend * 0.5f + 18f * dir, y0 - 70f, x + bend * 0.5f + 30f * dir, y0 - 96f)
                },
                stroke(2.6f, 0.8f),
            )
        }
    }

    private fun reliefShapes(c: Canvas) {
        val w = PAPER_W
        val h = PAPER_H
        c.drawRoundRect(RectF(16f, 16f, w - 16f, h - 16f), 6f, 6f, stroke(4f, 0.95f))
        c.drawRoundRect(RectF(25f, 25f, w - 25f, h - 25f), 3f, 3f, stroke(1.6f, 0.85f))
        for ((x, y) in listOf(25f to 25f, w - 25f to 25f, 25f to h - 25f, w - 25f to h - 25f)) {
            c.drawCircle(x, y, 7f, white(1f))
            for (k in 0 until 8) {
                val a = k * PI.toFloat() / 4f
                c.drawCircle(x + cos(a) * 10.5f, y + sin(a) * 10.5f, 2.4f, white(0.9f))
            }
        }
        c.drawCircle(206f, 128f, 46f, stroke(3f, 0.6f))
        for (k in 0 until 14) {
            val a = PI.toFloat() + k * PI.toFloat() / 13f
            val r0 = 54f
            val r1 = 70f + (k % 2) * 10f
            c.drawLine(206f + cos(a) * r0, 128f + sin(a) * r0, 206f + cos(a) * r1, 128f + sin(a) * r1, stroke(2f, 0.5f))
        }
        reeds(c)
        heron(c)
        for (i in 0 until 4) {
            val y = 314f + i * 9f
            val path = Path()
            val x0 = 44f + i * 10f
            val x1 = w - 44f - i * 10f
            var x = x0
            while (x <= x1) {
                val yy = y + sin(x / 22f * PI.toFloat() * 2f + i) * 1.8f
                if (x == x0) path.moveTo(x, yy) else path.lineTo(x, yy)
                x += 3f
            }
            c.drawPath(path, stroke(2f, 0.7f - i * 0.1f))
        }
        centredText(c, "THE RIVER KEEPS", w / 2f, 372f, 19f, 2.4f, 1f)
        centredText(c, "ITS OWN TIME", w / 2f, 394f, 19f, 2.4f, 1f)
    }

    private fun bakeRelief(): Bitmap {
        val bmp = Bitmap.createBitmap(ceil(PAPER_W * RELIEF_BAKE).toInt(), ceil(PAPER_H * RELIEF_BAKE).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF000000.toInt())
        c.scale(RELIEF_BAKE, RELIEF_BAKE)
        reliefShapes(c)
        return bmp
    }

    private suspend fun weights(relief: Bitmap): Pair<FloatArray, Float> {
        val w = relief.width
        val h = relief.height
        val pixels = IntArray(w * h)
        relief.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = FloatArray(GX * GY)
        val step = (CELL * RELIEF_BAKE).toInt()
        for (gy in 0 until GY) {
            currentCoroutineContext().ensureActive()
            for (gx in 0 until GX) {
                var raised = 0
                var n = 0
                var y = gy * step
                while (y < min(h, (gy + 1) * step)) {
                    var x = gx * step
                    while (x < min(w, (gx + 1) * step)) {
                        val v = (pixels[y * w + x] shr 16) and 0xFF
                        if (v / 255f > 0.35f) raised++
                        n++
                        x += 2
                    }
                    y += 2
                }
                out[gy * GX + gx] = if (n > 0) raised.toFloat() / n else 0f
            }
        }
        return out to out.sum()
    }

    /** One crayon (no shadow): the wax stick with a worn tip and a sheen, the paper wrapper with its name. */
    private fun bakeCrayon(s: Float, wax: CrayonRubbingWax): Bitmap {
        val bmp = Bitmap.createBitmap(
            ceil((CRAYON_RIGHT - CRAYON_LEFT) * s).toInt(), ceil(CRAYON_HALF * 2 * s).toInt(), Bitmap.Config.ARGB_8888,
        )
        val c = Canvas(bmp)
        c.scale(s, s)
        c.translate(-CRAYON_LEFT, CRAYON_HALF)
        val tones = wax.tones
        c.drawRoundRect(
            RectF(8f, -9f, 96f, 9f), 4f, 4f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, -9f, 0f, 9f, tones, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            },
        )
        c.drawPath(
            Path().apply { moveTo(10f, -9f); lineTo(1f, -3f); quadTo(-1f, 0f, 1f, 3f); lineTo(10f, 9f); close() },
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tones[1] },
        )
        c.drawPath(
            Path().apply { moveTo(10f, -9f); lineTo(1f, -3f); lineTo(3f, -3f); lineTo(11f, -7f); close() },
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(0.22f, 255, 255, 255) },
        )
        c.drawRect(
            32f, -9.6f, 88f, 9.6f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, -9.6f, 0f, 9.6f, intArrayOf(0xFFFBF4E2.toInt(), 0xFFEADCBE.toInt(), 0xFFCDBB98.toInt()),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
                )
            },
        )
        val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tones[1] }
        c.drawRect(32f, -6.6f, 88f, -5f, band)
        c.drawRect(32f, 5f, 88f, 6.6f, band)
        c.drawText(
            wax.name, 39f, 2.4f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tones[2]
                typeface = Typeface.DEFAULT_BOLD
                textSize = 6.5f
            },
        )
        c.drawRect(12f, -5.5f, 94f, -4.3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(0.3f, 255, 255, 255) })
        return bmp
    }

    private fun bakeShadow(s: Float, blur: Float): Bitmap {
        val m = SHADOW_MARGIN
        val bmp = Bitmap.createBitmap(ceil((92f + m * 2) * s).toInt(), ceil((18f + m * 2) * s).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(s, s)
        c.drawRoundRect(
            RectF(m, m, m + 92f, m + 18f), 5f, 5f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0A0806.toInt()
                maskFilter = BlurMaskFilter(sigma(blur), BlurMaskFilter.Blur.NORMAL)
            },
        )
        return bmp
    }

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777215f
    }

    /** Periodic value-noise grain as a signed overlay (white lighter, black darker), a tile that wraps. */
    private suspend fun feltTile(size: Int, cells: Int, strength: Float): Bitmap {
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            currentCoroutineContext().ensureActive()
            for (x in 0 until size) {
                var v = 0f
                var amp = 0.5f
                var total = 0f
                var p = cells
                for (o in 0 until 3) {
                    val fx = x * p / size.toFloat()
                    val fy = y * p / size.toFloat()
                    val ix = floor(fx).toInt()
                    val iy = floor(fy).toInt()
                    val ux = fx - ix
                    val uy = fy - iy
                    val sx = ux * ux * (3 - 2 * ux)
                    val sy = uy * uy * (3 - 2 * uy)
                    val x0 = ix % p
                    val x1 = (ix + 1) % p
                    val y0 = iy % p
                    val y1 = (iy + 1) % p
                    val a = hash(x0, y0, 8 + o)
                    val b = hash(x1, y0, 8 + o)
                    val cc = hash(x0, y1, 8 + o)
                    val d = hash(x1, y1, 8 + o)
                    val top = a + (b - a) * sx
                    val bottom = cc + (d - cc) * sx
                    v += amp * (top + (bottom - top) * sy)
                    total += amp
                    amp *= 0.5f
                    p *= 2
                }
                val dv = (v / total - 0.5f) * 2f
                val alpha = (abs(dv) * strength * 255f).roundToInt().coerceIn(0, 255)
                pixels[y * size + x] = (alpha shl 24) or (if (dv > 0) 0xFFFFFF else 0)
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    suspend fun bake(density: Float): CrayonRubbingArtSet {
        val s = min(2f, density)
        val relief = bakeRelief()
        currentCoroutineContext().ensureActive()
        val (weights, total) = weights(relief)
        val crayons = CrayonRubbingWaxes.map { bakeCrayon(s, it) }
        val near = bakeShadow(s, 3f)
        val far = bakeShadow(s, 6f)
        val felt = feltTile(160, 56, 0.14f)
        return CrayonRubbingArtSet(relief, weights, total, crayons, near, far, felt, s)
    }
}
