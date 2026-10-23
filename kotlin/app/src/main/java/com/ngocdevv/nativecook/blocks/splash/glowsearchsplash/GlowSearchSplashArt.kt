package com.ngocdevv.nativecook.blocks.splash.glowsearchsplash

import android.graphics.Bitmap
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
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** One card of the flow: its column, its place in the 393-wide design space and its size. */
internal class GlowSearchSplashCard(val column: Int, val row: Int, val x: Double, val width: Double, val height: Double, val baseY: Double) {
    /** Which of a category's ten artworks this card shows: ten cards in a row from top to bottom never repeat one. */
    val artIndex: Int get() = (row * 2 + column) % 10

    companion object {
        const val RADIUS = 20.0
        /** Upward drift in points per second: the left column runs faster than the right (parallax). */
        val SPEEDS = doubleArrayOf(44.0, 30.0)
        /**
         * (x, y, width, height) per row, placed by hand: 100–150 pt wide (25–38 % of the screen), staggered,
         * some bleeding past the screen edge, with 114–138 pt of black between cards of a column.
         */
        private val LEFT = listOf(
            doubleArrayOf(-26.0, 40.0, 150.0, 196.0), doubleArrayOf(30.0, 360.0, 118.0, 150.0), doubleArrayOf(-14.0, 640.0, 144.0, 188.0),
            doubleArrayOf(46.0, 960.0, 100.0, 132.0), doubleArrayOf(-30.0, 1210.0, 150.0, 196.0), doubleArrayOf(24.0, 1530.0, 124.0, 162.0),
        )
        private val RIGHT = listOf(
            doubleArrayOf(262.0, 110.0, 146.0, 186.0), doubleArrayOf(236.0, 420.0, 140.0, 182.0), doubleArrayOf(290.0, 740.0, 118.0, 150.0),
            doubleArrayOf(226.0, 1010.0, 150.0, 196.0), doubleArrayOf(280.0, 1330.0, 104.0, 136.0), doubleArrayOf(244.0, 1580.0, 146.0, 190.0),
        )

        val all: List<GlowSearchSplashCard> = listOf(LEFT, RIGHT).flatMapIndexed { column, rows ->
            rows.mapIndexed { row, item -> GlowSearchSplashCard(column, row, item[0], item[2], item[3], item[1]) }
        }
    }
}

internal enum class GlowSearchSplashKind { ORB, BLOB, FLOWER, POSTER, LANDSCAPE, ARCHES, CHECKER, PILLS, RING }

/** A code-drawn artwork: `kind` picks the drawing, `colors` its palette (0xRRGGBB). */
internal class GlowSearchSplashArtSpec(
    val kind: GlowSearchSplashKind,
    val colors: IntArray,
    val words: List<String> = emptyList(),
    val caption: String = "",
    val seed: Double = 0.0,
) {
    companion object {
        private fun spec(kind: GlowSearchSplashKind, vararg colors: Int, words: List<String> = emptyList(), caption: String = "", seed: Double = 0.0) =
            GlowSearchSplashArtSpec(kind, colors, words, caption, seed)

        /** Four categories of ten artworks, in the order the placeholder names them: anything, illustration, typography, 3D shapes. */
        val categories: List<List<GlowSearchSplashArtSpec>> =
            listOf(
                listOf(
                    spec(GlowSearchSplashKind.BLOB, 0x1E3BFF, 0x6F8DFF, 0xFFD1EE, 0xFF5FC8, 0xB01682, seed = 0.7),
                    spec(GlowSearchSplashKind.FLOWER, 0xF3EFEA, 0xFF8AD0, 0xFF2EA6, 0xB0005E, 0xFFF3A0, 0xFFD23F, 0xE09400, seed = 0.2),
                    spec(GlowSearchSplashKind.LANDSCAPE, 0xFF4FD8, 0xFFC86B, 0xC8F56A, 0xFFF7D6, 0x7BD88F, 0x3FAE6A, 0x1F7A4D, seed = 0.30),
                    spec(GlowSearchSplashKind.POSTER, 0xFF4A1C, 0xFFE9D6, words = listOf("Yes."), caption = "Edition 01"),
                    spec(GlowSearchSplashKind.ORB, 0x7CF0C8, 0x22A893, 0xFFE2B8, 0xFF8A3D, 0xB33F00, seed = 0.8),
                    spec(GlowSearchSplashKind.ARCHES, 0xFFE3C2, 0xFF4A1C, 0xFF8A3D, 0xFFC23D, 0x3DB2FF, 0x2346FF),
                    spec(GlowSearchSplashKind.PILLS, 0x8E7BFF, 0x4B2AE8, 0xFFF6B0, 0xFFD23F, 0xD69A00, 0xFFC4E6, 0xFF5FC8, 0xB01682, 0xFFFFFF, 0xE6E6F0, 0x9A9AB8),
                    spec(GlowSearchSplashKind.CHECKER, 0xFF8A3D, 0xFFB067, 0xFFFBD6, 0xFFE14D),
                    spec(GlowSearchSplashKind.RING, 0x2346FF, 0x111A66, 0xFFF3C4, 0xFFC23D, 0xC27A00),
                    spec(GlowSearchSplashKind.LANDSCAPE, 0x1BC7FF, 0xFFB6E1, 0xFFE9A8, 0xFFFFFF, 0xFF8FB8, 0xE0457B, 0x8E1F4F, seed = 0.35),
                ),
                listOf(
                    spec(GlowSearchSplashKind.LANDSCAPE, 0x3A1C71, 0xD76D77, 0xFFAF7B, 0xFFE8C8, 0x9B4F96, 0x5E2D79, 0x2E1450, seed = 0.45),
                    spec(GlowSearchSplashKind.FLOWER, 0x3B5BFF, 0xFFFFFF, 0xE8ECFF, 0xA9B6FF, 0xFFD9A0, 0xFF8A3D, 0xC2410C, seed = 0.5),
                    spec(GlowSearchSplashKind.ARCHES, 0x2B2D6E, 0xFFB3C7, 0xFFD6A5, 0xFDFFB6, 0xCAFFBF, 0x9BF6FF),
                    spec(GlowSearchSplashKind.LANDSCAPE, 0x5CCBFF, 0xB7F0FF, 0xFFF4C2, 0xFFFFFF, 0x7EE08A, 0x2FB36B, 0x147A4A, seed = 0.25),
                    spec(GlowSearchSplashKind.FLOWER, 0xFFD6E8, 0xFFC27A, 0xFF7A00, 0xC24A00, 0xC6A8FF, 0x6B2BFF, 0x3A0CA3, seed = 0.0),
                    spec(GlowSearchSplashKind.CHECKER, 0x1BB55C, 0x139447, 0xFFC4F0, 0xFF4FD8),
                    spec(GlowSearchSplashKind.LANDSCAPE, 0x0B1340, 0x24308A, 0x5E4BC9, 0xFFF6C8, 0x2A2F7A, 0x1A1D52, 0x0D0F33, seed = 0.22),
                    spec(GlowSearchSplashKind.ARCHES, 0xFF5B3A, 0xFFE14D, 0xFFFFFF, 0x2346FF, 0x7C3AED, 0xFFB3C7),
                    spec(GlowSearchSplashKind.FLOWER, 0x1E1B3A, 0xFFE38A, 0xFFC23D, 0xC27A00, 0xFFB3C7, 0xFF4F8B, 0xA3164A, seed = 0.3),
                    spec(GlowSearchSplashKind.CHECKER, 0x2346FF, 0x1B37CC, 0xFFFBD6, 0xFFC23D),
                ),
                listOf(
                    spec(GlowSearchSplashKind.POSTER, 0xFF2D2D, 0xFFF1E0, words = listOf("BOLD"), caption = "Weight 900"),
                    spec(GlowSearchSplashKind.POSTER, 0x22C55E, 0x0B3D1E, words = listOf("Aa"), caption = "Specimen"),
                    spec(GlowSearchSplashKind.POSTER, 0x2346FF, 0xFFE14D, words = listOf("GO!"), caption = "Vol. 03"),
                    spec(GlowSearchSplashKind.POSTER, 0xFF8A00, 0x1A1A1A, words = listOf("47"), caption = "No. 47"),
                    spec(GlowSearchSplashKind.POSTER, 0xF5F0E6, 0xE63B2E, words = listOf("Big", "Idea"), caption = "Display"),
                    spec(GlowSearchSplashKind.POSTER, 0xFF5FC8, 0x2A0A3C, words = listOf("ZINE"), caption = "Issue 12"),
                    spec(GlowSearchSplashKind.POSTER, 0xFFE14D, 0x1A1A1A, words = listOf("Type", "Set"), caption = "Grotesk"),
                    spec(GlowSearchSplashKind.POSTER, 0x7C3AED, 0xF5F0E6, words = listOf("Néo"), caption = "Studio"),
                    spec(GlowSearchSplashKind.POSTER, 0x00C2A8, 0x062A26, words = listOf("Hello"), caption = "Sans Serif"),
                    spec(GlowSearchSplashKind.POSTER, 0xFFB3C7, 0xB0005E, words = listOf("Wow"), caption = "Headline"),
                ),
                listOf(
                    spec(GlowSearchSplashKind.ORB, 0xFF7AC6, 0xFFB8DD, 0xC9E4FF, 0x3D7BFF, 0x1238A8, seed = 0.2),
                    spec(GlowSearchSplashKind.RING, 0x1C1C3A, 0x3A2B8F, 0xFFF0C2, 0xFFB547, 0xB8620B),
                    spec(GlowSearchSplashKind.BLOB, 0xF2F2F5, 0xD5D5E0, 0xF3C8FF, 0xB15CFF, 0x5B1AA8, seed = 2.1),
                    spec(GlowSearchSplashKind.PILLS, 0x22D3EE, 0x0284C7, 0xFFFFFF, 0xF0F0F5, 0xA0A0B8, 0xFFE1A8, 0xFF8A3D, 0xB33F00, 0xC4F5D0, 0x34D399, 0x047857),
                    spec(GlowSearchSplashKind.RING, 0xFFD84D, 0xFF9F1C, 0xFFD1EE, 0xFF4FB3, 0xA3006B),
                    spec(GlowSearchSplashKind.ORB, 0x4B1FD1, 0x7B5CFF, 0xF4FFC2, 0xB8F02A, 0x5A8A00, seed = 0.9),
                    spec(GlowSearchSplashKind.BLOB, 0x0F172A, 0x1E293B, 0xC2FFF2, 0x2EE6C5, 0x0B7A6A, seed = 4.0),
                    spec(GlowSearchSplashKind.PILLS, 0xFF5B3A, 0xFF9575, 0xFFF6B0, 0xFFD23F, 0xD69A00, 0xC9E4FF, 0x3D7BFF, 0x1238A8, 0xFFFFFF, 0xEDEDF5, 0x9A9AB8),
                    spec(GlowSearchSplashKind.ORB, 0xFFE14D, 0xFFB800, 0xFFD1EE, 0xFF4FB3, 0xA3006B, seed = 0.7),
                    spec(GlowSearchSplashKind.BLOB, 0xFF6A3D, 0xFF9E7A, 0xFFF6B0, 0xFFD23F, 0xC28E00, seed = 5.3),
                ),
            )
    }
}

/**
 * The card images, drawn once off the main thread: a sharp bitmap per card and category, plus a tiny copy of the
 * first category's, which drawn large reads as a blur while the cards bloom in.
 */
internal class GlowSearchSplashArt private constructor(val sharp: List<List<Bitmap>>, val soft: List<Bitmap>) {
    companion object {
        fun make(scale: Float): GlowSearchSplashArt {
            val cards = GlowSearchSplashCard.all
            val sharp = GlowSearchSplashArtSpec.categories.map { specs -> cards.map { render(specs[it.artIndex], it, scale) } }
            val soft = cards.map { render(GlowSearchSplashArtSpec.categories[0][it.artIndex], it, 0.09f) }
            return GlowSearchSplashArt(sharp, soft)
        }

        private fun render(spec: GlowSearchSplashArtSpec, card: GlowSearchSplashCard, scale: Float): Bitmap {
            val w = card.width.toFloat()
            val h = card.height.toFloat()
            val bitmap = Bitmap.createBitmap(max(1, ceil(w * scale).toInt()), max(1, ceil(h * scale).toInt()), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.scale(bitmap.width / w, bitmap.height / h)
            val radius = GlowSearchSplashCard.RADIUS.toFloat()
            // Paint the artwork in a layer, then keep only the rounded card (anti-aliased, unlike clipPath).
            canvas.saveLayer(0f, 0f, w, h, null)
            GlowSearchSplashPainter(canvas, w, h).paint(spec)
            val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
            canvas.drawRoundRect(RectF(0f, 0f, w, h), radius, radius, mask)
            canvas.restore()
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = GlowSearchSplashPainter.argb(0xFFFFFF, 0.08f) }
            canvas.drawRoundRect(RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f), radius - 0.5f, radius - 0.5f, border)
            return bitmap
        }
    }
}

/** Canvas drawing of the artworks, in card coordinates (0…w, 0…h, y down). */
internal class GlowSearchSplashPainter(private val c: Canvas, private val w: Float, private val h: Float) {
    private val m = min(w, h)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    companion object {
        fun argb(hex: Int, alpha: Float = 1f): Int = ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (hex and 0xFFFFFF)
        /** The colour with its channels multiplied by `factor` (a darker shade). */
        fun shade(hex: Int, factor: Double): Int {
            val r = (((hex shr 16) and 255) * factor).toInt()
            val g = (((hex shr 8) and 255) * factor).toInt()
            val b = ((hex and 255) * factor).toInt()
            return (r shl 16) or (g shl 8) or b
        }
    }

    private fun fresh(): Paint { paint.reset(); paint.isAntiAlias = true; paint.isDither = true; paint.color = android.graphics.Color.WHITE; return paint }
    private fun linear(path: Path, colors: IntArray, stops: FloatArray?, x0: Float, y0: Float, x1: Float, y1: Float) {
        val p = fresh(); p.shader = LinearGradient(x0, y0, x1, y1, colors, stops, Shader.TileMode.CLAMP); c.drawPath(path, p)
    }
    private fun radial(path: Path, colors: IntArray, stops: FloatArray?, x: Float, y: Float, r: Float) {
        val p = fresh(); p.shader = RadialGradient(x, y, max(r, 0.01f), colors, stops, Shader.TileMode.CLAMP); c.drawPath(path, p)
    }
    private fun fill(path: Path, color: Int) { val p = fresh(); p.color = color; c.drawPath(path, p) }
    private fun circle(x: Float, y: Float, r: Float) = Path().apply { addCircle(x, y, r, Path.Direction.CW) }
    private fun oval(x: Float, y: Float, rx: Float, ry: Float) = Path().apply { addOval(RectF(x - rx, y - ry, x + rx, y + ry), Path.Direction.CW) }
    private fun rect(x: Float, y: Float, rw: Float, rh: Float) = Path().apply { addRect(x, y, x + rw, y + rh, Path.Direction.CW) }
    private fun roundRect(x: Float, y: Float, rw: Float, rh: Float, r: Float) = Path().apply { addRoundRect(RectF(x, y, x + rw, y + rh), r, r, Path.Direction.CW) }
    private val all get() = rect(0f, 0f, w, h)
    private fun col(hex: Int, alpha: Float = 1f) = argb(hex, alpha)
    private fun shadow(x: Float, y: Float, rx: Float, ry: Float, alpha: Float) {
        radial(oval(x, y, rx, ry), intArrayOf(argb(0, alpha), argb(0, 0f)), null, x, y, rx)
    }
    private fun white(x: Float, y: Float, r: Float, alpha: Float) {
        radial(circle(x, y, r), intArrayOf(argb(0xFFFFFF, alpha), argb(0xFFFFFF, 0f)), null, x, y, r)
    }
    /** A glossy sphere lit from the upper left. */
    private fun sphere(x: Float, y: Float, r: Float, hi: Int, base: Int, deep: Int) {
        radial(circle(x, y, r), intArrayOf(col(hi), col(base), col(deep)), floatArrayOf(0f, 0.5f, 1f), x - 0.35f * r, y - 0.4f * r, 1.45f * r)
        white(x - 0.36f * r, y - 0.42f * r, 0.26f * r, 0.85f)
    }

    fun paint(spec: GlowSearchSplashArtSpec) {
        val k = spec.colors
        when (spec.kind) {
            GlowSearchSplashKind.ORB -> {
                linear(all, intArrayOf(col(k[0]), col(k[1])), null, 0f, 0f, 0f, h)
                val r = m * 0.32f; val x = w * 0.5f; val y = h * 0.5f
                shadow(x, y + r * 1.04f, r * 1.05f, r * 0.18f, 0.38f)
                sphere(x, y, r, k[2], k[3], k[4])
                if (spec.seed > 0.5) sphere(w * 0.78f, h * 0.2f, m * 0.12f, k[2], k[3], k[4])
            }
            GlowSearchSplashKind.BLOB -> {
                linear(all, intArrayOf(col(k[0]), col(k[1])), null, 0f, 0f, w, h)
                val r = m * 0.36f; val x = w * 0.5f; val y = h * 0.5f
                val path = Path()
                for (i in 0 until 120) {
                    val a = i / 120.0 * 2 * PI
                    val rr = r * (1 + 0.13 * sin(3 * a + spec.seed) + 0.07 * sin(5 * a + 2 * spec.seed) + 0.04 * cos(2 * a))
                    val px = (x + rr * cos(a)).toFloat(); val py = (y + rr * sin(a)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                shadow(x, y + r * 1.12f, r * 0.9f, r * 0.14f, 0.3f)
                radial(path, intArrayOf(col(k[2]), col(k[3]), col(k[4])), floatArrayOf(0f, 0.48f, 1f), x - 0.3f * r, y - 0.38f * r, 1.5f * r)
                white(x - 0.34f * r, y - 0.4f * r, 0.3f * r, 0.7f)
            }
            GlowSearchSplashKind.FLOWER -> {
                fill(all, col(k[0]))
                val x = w * 0.5f; val y = h * 0.5f; val l = m * 0.34f
                white(x, y, m * 0.6f, 0.35f)
                radial(circle(x + l * 0.12f, y + l * 0.18f, l * 1.15f), intArrayOf(argb(0, 0.22f), argb(0, 0f)), null, x + l * 0.12f, y + l * 0.18f, l * 1.15f)
                for (i in 0 until 6) {
                    c.save()
                    c.translate(x, y)
                    c.rotate((spec.seed * 60 + i * 60).toFloat())
                    val petal = Path().apply { addOval(RectF(-0.31f * l, -1.02f * l, 0.31f * l, -0.18f * l), Path.Direction.CW) }
                    radial(petal, intArrayOf(col(k[3]), col(k[2]), col(k[1])), floatArrayOf(0f, 0.45f, 1f), 0f, -0.12f * l, 1.0f * l)
                    c.restore()
                }
                val r = 0.3f * l
                radial(circle(x, y, r), intArrayOf(col(k[4]), col(k[5]), col(k[6])), floatArrayOf(0f, 0.5f, 1f), x - 0.1f * l, y - 0.12f * l, 0.42f * l)
                white(x - 0.32f * r, y - 0.36f * r, 0.3f * r, 0.7f)
            }
            GlowSearchSplashKind.POSTER -> {
                fill(all, col(k[0]))
                fill(circle(w * 0.86f, h * 0.07f + 6, 5f), col(k[1]))
                val caption = fresh().apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); textSize = 10f; color = col(k[1], 0.85f) }
                c.drawText(spec.caption, w * 0.07f, h * 0.07f + 10, caption)
                fill(rect(w * 0.07f, h * 0.07f + 17, w * 0.86f, 1f), col(k[1], 0.5f))
                val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-black", Typeface.NORMAL); textSize = 100f; color = col(k[1]) }
                val widest = spec.words.maxOf { big.measureText(it) }
                val n = spec.words.size.toFloat()
                val size = min(100 * w * 0.86f / widest, h * 0.62f / (n * 0.82f))
                big.textSize = size
                val last = h - w * 0.08f
                spec.words.forEachIndexed { i, word -> c.drawText(word, w * 0.07f, last - (n - 1 - i) * size * 0.84f, big) }
            }
            GlowSearchSplashKind.LANDSCAPE -> {
                linear(all, intArrayOf(col(k[0]), col(k[1]), col(k[2])), null, 0f, 0f, 0f, h)
                val sx = w * 0.64f; val sy = (h * spec.seed).toFloat()
                radial(circle(sx, sy, w * 0.42f), intArrayOf(col(k[3], 0.55f), col(k[3], 0f)), null, sx, sy, w * 0.42f)
                fill(circle(sx, sy, w * 0.13f), col(k[3]))
                for (layer in 0 until 3) {
                    val top = h * (0.56f + 0.12f * layer); val amp = h * 0.035f * (1 - 0.2f * layer)
                    val freq = 1.2 + 0.5 * layer; val phase = layer * 1.7 + spec.seed * 3
                    val path = Path()
                    path.moveTo(0f, h)
                    for (j in 0..40) {
                        val u = j / 40.0
                        path.lineTo((w * u).toFloat(), (top + amp * sin(u * 2 * PI * freq + phase)).toFloat())
                    }
                    path.lineTo(w, h); path.close()
                    val hill = k[4 + layer]
                    linear(path, intArrayOf(col(hill), col(shade(hill, 0.7))), null, 0f, top - amp, 0f, h)
                }
            }
            GlowSearchSplashKind.ARCHES -> {
                fill(all, col(k[0]))
                fill(circle(w * 0.24f, h * 0.2f, m * 0.08f), col(k[1]))
                val r0 = w * 0.62f
                for (i in 0 until 5) fill(circle(w * 0.5f, h * 0.97f, r0 * (1 - i * 0.17f)), col(k[1 + i]))
                fill(circle(w * 0.5f, h * 0.97f, r0 * 0.15f), col(k[0]))
            }
            GlowSearchSplashKind.CHECKER -> {
                fill(all, col(k[0]))
                val s = w / 6
                for (row in 0 until ceil(h / s).toInt()) for (column in 0 until 6) {
                    if ((row + column) % 2 == 0) fill(rect(column * s, row * s, s, s), col(k[1]))
                }
                val x = w * 0.5f; val y = h * 0.46f; val r = w * 0.3f
                radial(circle(x, y, r * 1.6f), intArrayOf(col(k[3], 0.5f), col(k[3], 0f)), null, x, y, r * 1.6f)
                radial(circle(x, y, r), intArrayOf(col(k[2]), col(k[3])), null, x - 0.3f * r, y - 0.35f * r, 1.4f * r)
            }
            GlowSearchSplashKind.PILLS -> {
                linear(all, intArrayOf(col(k[0]), col(k[1])), null, 0f, 0f, 0f, h)
                val l = w * 0.64f; val t = w * 0.22f
                listOf(0.40f to 0.28f, 0.60f to 0.52f, 0.40f to 0.76f).forEachIndexed { i, spot ->
                    c.save()
                    c.translate(w * spot.first, h * spot.second)
                    c.rotate(-35f)
                    fill(roundRect(-l / 2 + t * 0.12f, -t / 2 + t * 0.28f, l, t, t / 2), argb(0, 0.2f))
                    linear(roundRect(-l / 2, -t / 2, l, t, t / 2), intArrayOf(col(k[2 + i * 3]), col(k[3 + i * 3]), col(k[4 + i * 3])), floatArrayOf(0f, 0.45f, 1f), 0f, -t / 2, 0f, t / 2)
                    fill(roundRect(-l / 2 + t * 0.4f, -t * 0.33f, l - t * 0.8f, t * 0.13f, t * 0.065f), argb(0xFFFFFF, 0.6f))
                    c.restore()
                }
            }
            GlowSearchSplashKind.RING -> {
                linear(all, intArrayOf(col(k[0]), col(k[1])), null, 0f, 0f, 0f, h)
                val x = w * 0.5f; val y = h * 0.5f; val ro = m * 0.36f; val ri = m * 0.17f
                shadow(x, y + ro * 1.02f, ro * 0.95f, ro * 0.16f, 0.3f)
                val ring = Path().apply { fillType = Path.FillType.EVEN_ODD; addCircle(x, y, ro, Path.Direction.CW); addCircle(x, y, ri, Path.Direction.CW) }
                linear(ring, intArrayOf(col(k[2]), col(k[3]), col(k[4])), floatArrayOf(0f, 0.5f, 1f), x - ro, y - ro, x + ro, y + ro)
                val inner = fresh().apply { style = Paint.Style.STROKE; strokeWidth = (ro - ri) * 0.25f; color = col(k[4], 0.35f) }
                c.drawCircle(x, y, ri + (ro - ri) * 0.12f, inner)
                val arc = Path(); val mid = (ro + ri) / 2
                for (j in 0..20) {
                    val a = (200 + 55 * j / 20.0) * PI / 180
                    val px = (x + mid * cos(a)).toFloat(); val py = (y + mid * sin(a)).toFloat()
                    if (j == 0) arc.moveTo(px, py) else arc.lineTo(px, py)
                }
                val shine = fresh().apply { style = Paint.Style.STROKE; strokeWidth = (ro - ri) * 0.2f; strokeCap = Paint.Cap.ROUND; color = argb(0xFFFFFF, 0.65f) }
                c.drawPath(arc, shine)
            }
        }
    }
}
