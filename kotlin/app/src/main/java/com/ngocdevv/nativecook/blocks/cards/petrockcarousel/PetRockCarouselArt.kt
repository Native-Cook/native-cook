package com.ngocdevv.nativecook.blocks.cards.petrockcarousel

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Rock geometry and paint. Every rock is drawn in its own "rock units": a box of about 200 × 200
// centred on (0, 0), so the faces painted on them share one set of coordinates whatever the
// rock's size.

private const val TWO_PI = (PI * 2).toFloat()

/** A small seeded random generator (mulberry32, bit for bit as in the React Native version). */
internal class PetRockRandom(seed: Int) {
    private var t = seed

    fun next(): Float {
        t += 0x6d2b79f5
        var r = (t xor (t ushr 15)) * (1 or t)
        r = (r + (r xor (r ushr 7)) * (61 or r)) xor r
        return ((r xor (r ushr 14)).toLong() and 0xffffffffL).toDouble().div(4294967296.0).toFloat()
    }
}

internal object PetRockShapes {
    // Samples around the outline, and how far the outline leans to a faceted polygon (1) rather
    // than a round blob (0): river stones are mostly round with a few flat, chipped faces.
    private const val SAMPLES = 120
    private const val FACETED = 0.55f
    // Corners of the underlying polygon.
    private const val CORNERS_MIN = 7
    private const val CORNERS_SPREAD = 4f
    // Low harmonics that make the blob lumpy, and a fine ripple that roughens the edge.
    private val Harmonics = floatArrayOf(2f, 3f, 4f, 5f)
    private const val LUMP = 0.05f
    private const val RIPPLE = 0.006f
    // Rocks sit on a flatter base: below this height (rock units) the outline is pressed up by half.
    private const val BASE_AT = 66f

    /**
     * A pebble outline: a faceted polygon blended with a lumpy blob and smoothed, so it reads as a
     * stone with a few flat faces rather than a circle. [aspect] squashes it vertically.
     */
    fun rock(seed: Int, aspect: Float): Path {
        val rand = PetRockRandom(seed)
        val corners = CORNERS_MIN + floor(rand.next() * CORNERS_SPREAD).toInt()
        val px = FloatArray(corners)
        val py = FloatArray(corners)
        for (k in 0 until corners) {
            val angle = ((k + (rand.next() - 0.5f) * 0.5f) / corners) * TWO_PI
            val radius = 0.86f + rand.next() * 0.16f
            px[k] = cos(angle) * radius
            py[k] = sin(angle) * radius
        }
        val order = (0 until corners)
            .map { k -> val a = atan2(py[k], px[k]); Triple(if (a < 0) a + TWO_PI else a, px[k], py[k]) }
            .sortedBy { it.first }
        val lumps = Harmonics.map { n -> Triple(n, (rand.next() * LUMP) / sqrt(n * 0.5f), rand.next() * TWO_PI) }
        val ripplePhase = rand.next() * TWO_PI

        val radii = FloatArray(SAMPLES)
        for (j in 0 until SAMPLES) {
            val theta = j.toFloat() / SAMPLES * TWO_PI
            // The polygon edge this ray crosses: r·d = P1 + u·(P2 − P1)  ⇒  r = (P1 × E) / (d × E).
            var k = order.indexOfFirst { it.first > theta } - 1
            if (k < 0) k = order.size - 1
            val p1 = order[k]
            val p2 = order[(k + 1) % order.size]
            val ex = p2.second - p1.second
            val ey = p2.third - p1.third
            val dx = cos(theta)
            val dy = sin(theta)
            val denominator = dx * ey - dy * ex
            val line = if (kotlin.math.abs(denominator) < 1e-6f) 1f else (p1.second * ey - p1.third * ex) / denominator
            var round = 0.95f
            for (l in lumps) round += l.second * sin(l.first * theta + l.third)
            round += RIPPLE * sin(23 * theta + ripplePhase)
            radii[j] = FACETED * min(1.1f, max(0.7f, line)) + (1 - FACETED) * round
        }
        // A short running average rounds the polygon's corners off, like water would.
        val path = Path()
        for (j in 0 until SAMPLES) {
            var sum = 0f
            for (o in -3..3) sum += radii[(j + o + SAMPLES) % SAMPLES]
            val r = sum / 7f
            val theta = j.toFloat() / SAMPLES * TWO_PI
            val x = cos(theta) * r * 100f
            var y = sin(theta) * r * 100f * aspect
            val base = BASE_AT * aspect
            if (y > base) y = base + (y - base) * 0.5f
            if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    /** Tiny dark and light flecks scattered over the stone, as two paths. */
    fun speckles(seed: Int): Pair<Path, Path> {
        val rand = PetRockRandom(seed * 7 + 3)
        val dark = Path()
        val light = Path()
        for (k in 0 until 70) {
            val angle = rand.next() * TWO_PI
            val distance = sqrt(rand.next()) * 94f
            val radius = 0.5f + rand.next() * rand.next() * 2.2f
            (if (k % 3 == 0) light else dark).addCircle(cos(angle) * distance, sin(angle) * distance, radius, Path.Direction.CW)
        }
        return dark to light
    }

    /** The absolute SVG path commands the faces use (M, L, C, Q, Z) as a path. */
    fun svg(d: String): Path {
        val path = Path()
        var command = 'M'
        val numbers = ArrayList<Float>()
        fun flush() {
            val n = numbers
            var i = 0
            when (command) {
                'M' -> if (n.size >= 2) {
                    path.moveTo(n[0], n[1])
                    i = 2
                    while (i + 1 < n.size) { path.lineTo(n[i], n[i + 1]); i += 2 }
                }
                'L' -> while (i + 1 < n.size) { path.lineTo(n[i], n[i + 1]); i += 2 }
                'C' -> while (i + 5 < n.size) { path.cubicTo(n[i], n[i + 1], n[i + 2], n[i + 3], n[i + 4], n[i + 5]); i += 6 }
                'Q' -> while (i + 3 < n.size) { path.quadTo(n[i], n[i + 1], n[i + 2], n[i + 3]); i += 4 }
                'Z' -> path.close()
            }
            numbers.clear()
        }
        for (token in d.split(' ')) {
            val value = token.toFloatOrNull()
            if (value != null) {
                numbers.add(value)
            } else if (token.isNotEmpty()) {
                flush()
                command = token[0]
                if (command == 'Z') {
                    flush()
                    command = ' '
                }
            }
        }
        flush()
        return path
    }
}

/**
 * The faces painted on the rocks, in rock units. Each face has two parts: the paint, which is
 * clipped to the stone and takes its grain and shading, and the stickers (googly eyes), which sit
 * on top of it with their own little shadow.
 */
internal object PetRockFaces {
    const val COUNT = 6
    /** How each face reads in the caption, in the same order as the faces. */
    val Moods = listOf("all eyes", "in a mood", "smitten", "freshly tagged", "giddy", "half asleep")

    class Eye(val x: Float, val y: Float, val r: Float)

    // Googly eyes of each face, if it has any.
    val Eyes = listOf(
        listOf(Eye(-30f, -30f, 25f), Eye(22f, -28f, 22f)),
        emptyList(),
        emptyList(),
        listOf(Eye(-22f, -46f, 15f), Eye(12f, -48f, 14f)),
        listOf(Eye(-27f, -40f, 19f), Eye(23f, -41f, 18f)),
        emptyList(),
    )

    // A pupil is this share of its eye, and travels at most this far from the centre.
    const val PUPIL = 0.55f
    const val PUPIL_TRAVEL = 0.38f

    private const val INK = 0xFF17171A.toInt()
    private val Heart = PetRockShapes.svg(
        "M 0 64 C -40 36 -84 8 -84 -24 C -84 -52 -60 -70 -37 -70 C -19 -70 -6 -59 0 -47 C 6 -59 19 -70 37 -70 C 60 -70 84 -52 84 -24 C 84 8 40 36 0 64 Z",
    )
    private val GiddyMouth = PetRockShapes.svg("M -48 0 C -30 -6 30 -6 48 0 C 48 50 -48 50 -48 0 Z")
    private val GiddyThroat = PetRockShapes.svg("M -37 7 C -22 3 22 3 37 7 C 35 38 -35 38 -37 7 Z")
    private val GiddyTongue = PetRockShapes.svg("M -21 34 C -18 21 18 21 21 34 C 13 41 -13 41 -21 34 Z")

    // A heavy condensed sans, a heavy sans and a heavy sans, as the RN version picks away from iOS.
    private fun heavy(family: String): Typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.create(family, Typeface.NORMAL), 800, false)
    } else {
        Typeface.create(family, Typeface.BOLD)
    }
    private val LoveFont = heavy("sans-serif-condensed")
    private val TagFont = heavy("sans-serif")

    /**
     * Paints a face's paint layer onto a software canvas, every stroke softened by [extra] more
     * sigma (rock units): blurring each piece adds up to blurring the whole layer, which is how the
     * morph's blur levels are drawn ahead of time.
     */
    class Painter(private val canvas: Canvas, private val extra: Float) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        private fun prepare(color: Int, alpha: Float, sigma: Float, stroke: Float?) {
            paint.reset()
            paint.isAntiAlias = true
            paint.color = color
            paint.alpha = (android.graphics.Color.alpha(color) * alpha).roundToInt()
            val s = sqrt(sigma * sigma + extra * extra)
            paint.maskFilter = if (s > 0.35f) BlurMaskFilter(sigmaToRadius(s), BlurMaskFilter.Blur.NORMAL) else null
            if (stroke != null) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = stroke
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
            } else {
                paint.style = Paint.Style.FILL
            }
        }

        fun path(path: Path, color: Int, alpha: Float = 1f, sigma: Float = 0f, stroke: Float? = null) {
            prepare(color, alpha, sigma, stroke)
            canvas.drawPath(path, paint)
        }

        fun oval(l: Float, t: Float, w: Float, h: Float, color: Int, alpha: Float = 1f, sigma: Float = 0f, stroke: Float? = null) {
            prepare(color, alpha, sigma, stroke)
            canvas.drawOval(l, t, l + w, t + h, paint)
        }

        fun text(text: String, x: Float, y: Float, font: Typeface, size: Float, color: Int, alpha: Float = 1f, sigma: Float = 0f, stroke: Float? = null) {
            prepare(color, alpha, sigma, stroke)
            if (stroke != null) paint.strokeCap = Paint.Cap.BUTT
            paint.typeface = font
            paint.textSize = size
            canvas.drawText(text, x, y, paint)
        }

        fun measure(text: String, font: Typeface, size: Float): Float {
            paint.reset()
            paint.typeface = font
            paint.textSize = size
            return paint.measureText(text)
        }

        fun save(block: Canvas.() -> Unit) {
            canvas.save()
            canvas.block()
        }

        fun restore() = canvas.restore()

        /** Paint softened by a halo of overspray, like a spray can held a little too far away. */
        fun spray(path: Path, color: Int, width: Float? = null) {
            path(path, color, 0.35f, 5f, width?.plus(7f))
            path(path, color, 1f, 0.7f, width)
        }

        fun spray(d: String, color: Int, width: Float? = null) = spray(PetRockShapes.svg(d), color, width)

        /** Text drawn like a spray-painted letter: a dark outline under a coloured fill. */
        fun lettering(text: String, x: Float, y: Float, font: Typeface, size: Float, fill: Int, outline: Int, width: Float) {
            text(text, x + 2.5f, y + 3f, font, size, outline, 0.5f, 3f, width)
            text(text, x, y, font, size, outline, stroke = width)
            text(text, x, y, font, size, fill)
        }
    }

    /** The painted part of a face. */
    fun paint(face: Int, p: Painter) {
        when (face) {
            0 -> {
                // Googly: a blue swoosh hugging the top of the stone and a black tag scrawled below.
                p.spray("M -82 -18 C -78 -64 -28 -88 16 -84 C 54 -80 80 -58 86 -14", 0xFF3A8ED8.toInt(), 7f)
                p.spray("M -70 -6 C -66 -48 -24 -70 14 -68", 0xFF3A8ED8.toInt(), 3f)
                p.spray(
                    "M -56 28 C -62 4 -22 -6 -16 18 C -10 42 -46 52 -40 30 C -34 10 2 2 10 24 C 18 44 -6 54 0 34 C 6 10 40 6 46 28 C 50 48 24 56 30 38 C 34 22 60 22 64 40",
                    INK,
                    4.5f,
                )
                p.spray("M -52 54 C -20 64 22 66 58 54", INK, 3.5f)
            }
            1 -> {
                // Grumpy: a yellow disc with brows pulled down and a frown.
                val yellow = 0xFFF1C42E.toInt()
                val brown = 0xFF2A1F14.toInt()
                p.oval(-70f, -66f, 140f, 140f, yellow, 0.35f, 6f)
                p.oval(-64f, -60f, 128f, 128f, yellow, sigma = 0.8f)
                p.spray("M -46 -28 L -12 -12", brown, 8f)
                p.spray("M 46 -30 L 12 -12", brown, 8f)
                p.oval(-28f, -8f, 12f, 16f, brown)
                p.oval(16f, -8f, 12f, 16f, brown)
                p.spray("M -27 38 Q 0 14 27 38", brown, 7f)
            }
            2 -> {
                // Smitten: a red heart with LOVE lettered across it.
                p.spray(Heart, 0xFFD8343B.toInt())
                p.path(Heart, 0xFF9D1C24.toInt(), 0.6f, 2f, 3f)
                val width = p.measure("LOVE", LoveFont, 46f)
                p.save { rotate(-0.08f * 180f / PI.toFloat()) }
                p.text("LOVE", -width / 2f, 8f, LoveFont, 46f, INK)
                p.restore()
                p.spray("M -40 24 Q 0 34 44 20", INK, 4f)
            }
            3 -> {
                // Tagged: bubble letters in teal with a dark outline.
                p.save { rotate(-0.12f * 180f / PI.toFloat()) }
                val width = p.measure("blub", TagFont, 58f)
                p.lettering("blub", -width / 2f, 24f, TagFont, 58f, 0xFF47B8C6.toInt(), 0xFF0F2F3B.toInt(), 9f)
                p.spray("M -58 40 C -30 50 30 48 60 34", 0xFF0F2F3B.toInt(), 3f)
                p.restore()
            }
            4 -> {
                // Giddy: a wide purple grin.
                p.spray(GiddyMouth, 0xFF8B3F99.toInt())
                p.path(GiddyThroat, 0xFF3A1346.toInt())
                p.path(GiddyTongue, 0xFFE37B9C.toInt())
                p.spray("M -60 -6 Q -54 -14 -46 -10", 0xFF2D8C5C.toInt(), 5f)
                p.spray("M 60 -6 Q 54 -14 46 -10", 0xFF2D8C5C.toInt(), 5f)
            }
            else -> {
                // Sleepy: closed eyes, rosy cheeks, a small "o" and a few z's.
                p.spray("M -44 -14 Q -27 2 -10 -14", INK, 5f)
                p.spray("M 10 -14 Q 27 2 44 -14", INK, 5f)
                p.spray("M -40 -8 L -45 -1 M -27 -5 L -28 3 M -15 -8 L -11 -1", INK, 2.5f)
                p.spray("M 15 -8 L 11 -1 M 27 -5 L 28 3 M 40 -8 L 45 -1", INK, 2.5f)
                p.oval(-52f, 4f, 24f, 24f, 0xFFEC8A8A.toInt(), 0.75f, 6f)
                p.oval(28f, 4f, 24f, 24f, 0xFFEC8A8A.toInt(), 0.75f, 6f)
                p.oval(-7f, 26f, 14f, 17f, INK, stroke = 4f)
                val cream = 0xFFF4F1EA.toInt()
                p.lettering("z", 30f, -42f, TagFont, 26f, cream, INK, 4f)
                p.save {
                    translate(52f, -56f)
                    scale(0.72f, 0.72f)
                }
                p.lettering("z", 0f, 0f, TagFont, 26f, cream, INK, 4f)
                p.restore()
            }
        }
    }

    /**
     * The still part of a face's googly eyes: shadow, white dome and rim, softened by [extra]
     * sigma. The pupils and the glint are drawn over it every frame.
     */
    fun domes(face: Int, canvas: Canvas, extra: Float) {
        val p = Painter(canvas, extra)
        val dome = Paint(Paint.ANTI_ALIAS_FLAG)
        for (eye in Eyes[face]) {
            val x = eye.x
            val y = eye.y
            val r = eye.r
            p.oval(x + 1 - r, y + 3 - r, r * 2, r * 2, android.graphics.Color.argb(115, 30, 22, 14), sigma = 3.5f)
            dome.shader = RadialGradient(
                x - r * 0.3f, y - r * 0.35f, r * 1.35f,
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFF3F1EC.toInt(), 0xFFC7C3BB.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
            dome.maskFilter = if (extra > 0.35f) BlurMaskFilter(sigmaToRadius(extra), BlurMaskFilter.Blur.NORMAL) else null
            canvas.drawCircle(x, y, r, dome)
            p.oval(x - r + 0.5f, y - r + 0.5f, (r - 0.5f) * 2, (r - 0.5f) * 2, android.graphics.Color.argb(56, 0, 0, 0), stroke = 1f)
        }
    }
}

/**
 * Everything about a rock that never moves relative to it, drawn once into bitmaps on a
 * background thread so a frame only places images: the ground shadow, the shading over the stone
 * (flecks, light, rim), each face's paint and eye domes at a few blur levels for the morph, a grain
 * texture and the stone photos.
 */
internal class PetRockArt(count: Int) {
    class RockLayers(val path: Path, val shadow: Bitmap, val shade: Bitmap)

    @Volatile var rocks: Array<RockLayers?> = arrayOfNulls(count)
    @Volatile var photos: Array<Bitmap?> = arrayOfNulls(count)
    // Each face's paint and eye domes at the blur levels in [LEVELS].
    @Volatile var paint: Array<Array<Bitmap?>> = Array(PetRockFaces.COUNT) { arrayOfNulls(LEVELS.size) }
    @Volatile var domes: Array<Array<Bitmap?>> = Array(PetRockFaces.COUNT) { arrayOfNulls(LEVELS.size) }
    @Volatile var grain: Bitmap? = null

    companion object {
        /** The box every cached rock layer covers, in rock units. */
        val Box = RectF(-120f, -120f, 120f, 120f)
        val ShadowBox = RectF(-125f, 20f, 125f, 160f)
        /** The grain texture: a square this many rock units wide that each rock samples a part of. */
        const val GRAIN_SIDE = 360f
        /** Blur levels (sigma, rock units) the morph's crossfade steps through. */
        val LEVELS = floatArrayOf(0f, 3f, 6f, 9f)

        private fun render(box: RectF, scale: Float, draw: (Canvas) -> Unit): Bitmap {
            val bitmap = Bitmap.createBitmap(
                max(1, (box.width() * scale).roundToInt()),
                max(1, (box.height() * scale).roundToInt()),
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(bitmap)
            canvas.scale(scale, scale)
            canvas.translate(-box.left, -box.top)
            draw(canvas)
            return bitmap
        }

        /** The stone photo in grey with a little more contrast, so laid over the tone it only adds relief. */
        fun stonePhoto(source: Bitmap): Bitmap {
            val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val row = floatArrayOf(0.2f, 0.62f, 0.06f, 0f, 0.06f * 255f)
            val matrix = ColorMatrix(row + row + row + floatArrayOf(0f, 0f, 0f, 1f, 0f))
            Canvas(out).drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) })
            return out
        }
    }

    /** Draws every layer, [pixels] pixels per rock unit: the rocks and their first faces, then the rest. */
    fun prepare(rocks: List<PetRockSpec>, pixels: Float) {
        for ((i, rock) in rocks.withIndex()) {
            val path = PetRockShapes.rock(rock.seed, rock.aspect)
            val speckles = PetRockShapes.speckles(rock.seed)
            val shade = render(Box, pixels * 0.8f) { shade(it, path, speckles) }
            val shadow = render(ShadowBox, pixels * 0.5f) { groundShadow(it, rock.aspect) }
            this.rocks = this.rocks.copyOf().also { it[i] = RockLayers(path, shadow, shade) }
            renderFace(rock.face, 0, pixels)
        }
        grain = grainTexture(2.6f)
        for (face in 0 until PetRockFaces.COUNT) renderFace(face, 0, pixels)
        for (level in 1 until LEVELS.size) {
            for (face in 0 until PetRockFaces.COUNT) renderFace(face, level, pixels)
        }
    }

    private fun renderFace(face: Int, level: Int, pixels: Float) {
        if (paint[face][level] != null) return
        val extra = LEVELS[level]
        // Blurred levels hold no fine detail, so they are drawn at half the resolution.
        val scale = if (level == 0) pixels else pixels * 0.5f
        val bitmap = render(Box, scale) { PetRockFaces.paint(face, PetRockFaces.Painter(it, extra)) }
        val eyes = if (PetRockFaces.Eyes[face].isEmpty()) null else render(Box, scale) { PetRockFaces.domes(face, it, extra) }
        paint = paint.copyOf().also { all -> all[face] = all[face].copyOf().also { it[level] = bitmap } }
        domes = domes.copyOf().also { all -> all[face] = all[face].copyOf().also { it[level] = eyes } }
    }

    /** Shadow on the ground: a wide soft one and a tight dark one where the stone touches. */
    private fun groundShadow(canvas: Canvas, aspect: Float) {
        val p = PetRockFaces.Painter(canvas, 0f)
        p.oval(-78f, 78f * aspect, 156f, 30f, android.graphics.Color.argb(77, 70, 56, 40), sigma = 12f)
        p.oval(-52f, 84f * aspect, 104f, 14f, android.graphics.Color.argb(89, 50, 40, 28), sigma = 5f)
    }

    /** Flecks, light from the top left and the edge falling away into shadow, clipped to the stone. */
    private fun shade(canvas: Canvas, path: Path, speckles: Pair<Path, Path>) {
        canvas.save()
        canvas.clipPath(path)
        val p = PetRockFaces.Painter(canvas, 0f)
        p.path(speckles.first, android.graphics.Color.argb(97, 35, 28, 22))
        p.path(speckles.second, android.graphics.Color.argb(82, 255, 255, 255))
        val light = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                -36f, -48f, 165f,
                intArrayOf(
                    android.graphics.Color.argb(87, 255, 255, 255),
                    android.graphics.Color.argb(13, 255, 255, 255),
                    android.graphics.Color.argb(51, 20, 14, 8),
                    android.graphics.Color.argb(140, 20, 14, 8),
                ),
                floatArrayOf(0f, 0.38f, 0.7f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(Box, light)
        p.path(path, android.graphics.Color.argb(87, 40, 30, 20), sigma = 9f, stroke = 22f)
        // A catch of light along the upper left edge.
        canvas.translate(4f, 5f)
        p.path(path, android.graphics.Color.argb(56, 255, 255, 255), sigma = 4f, stroke = 5f)
        canvas.restore()
    }

    /**
     * Two octave sums of gradient noise in grey, a coarse mottling and a fine grain, added into one
     * texture that is laid over the stone and its paint in overlay.
     */
    private fun grainTexture(pixelsPerUnit: Float): Bitmap {
        val side = (GRAIN_SIDE * pixelsPerUnit).toInt()
        val noise = PetRockNoise(11)
        val pixels = IntArray(side * side)
        for (py in 0 until side) {
            for (px in 0 until side) {
                val x = px / pixelsPerUnit
                val y = py / pixelsPerUnit
                val coarse = noise.fractal(x * 0.035f, y * 0.035f, 3)
                val fine = noise.fractal(x * 0.45f + 37.1f, y * 0.45f + 11.7f, 2)
                // Skia's fractal noise is (sum + 1) / 2 per channel; three channels averaged to grey
                // spread about 0.58 as far.
                val g = (0.5f + 0.5f * 0.58f * (coarse + fine)).coerceIn(0f, 1f)
                val v = (g * 255f).toInt()
                pixels[py * side + px] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
        return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
    }
}

/** Classic gradient (Perlin) noise with a seeded permutation, summed over octaves. */
internal class PetRockNoise(seed: Int) {
    private val perm = IntArray(512)

    init {
        val rand = PetRockRandom(seed)
        val p = IntArray(256) { it }
        for (i in 255 downTo 1) {
            val j = (rand.next() * (i + 1)).toInt().coerceAtMost(i)
            val t = p[i]
            p[i] = p[j]
            p[j] = t
        }
        for (i in 0 until 512) perm[i] = p[i and 255]
    }

    private fun grad(hash: Int, x: Float, y: Float): Float = when (hash and 7) {
        0 -> x + y
        1 -> -x + y
        2 -> x - y
        3 -> -x - y
        4 -> x
        5 -> -x
        6 -> y
        else -> -y
    }

    private fun noise(x: Float, y: Float): Float {
        val fx = floor(x)
        val fy = floor(y)
        val xi = fx.toInt() and 255
        val yi = fy.toInt() and 255
        val xf = x - fx
        val yf = y - fy
        val u = xf * xf * xf * (xf * (xf * 6 - 15) + 10)
        val v = yf * yf * yf * (yf * (yf * 6 - 15) + 10)
        val aa = perm[perm[xi] + yi]
        val ab = perm[perm[xi] + yi + 1]
        val ba = perm[perm[xi + 1] + yi]
        val bb = perm[perm[xi + 1] + yi + 1]
        val x1 = grad(aa, xf, yf) + (grad(ba, xf - 1, yf) - grad(aa, xf, yf)) * u
        val x2 = grad(ab, xf, yf - 1) + (grad(bb, xf - 1, yf - 1) - grad(ab, xf, yf - 1)) * u
        return (x1 + (x2 - x1) * v) * 0.7f
    }

    fun fractal(x: Float, y: Float, octaves: Int): Float {
        var sum = 0f
        var amp = 1f
        var f = 1f
        repeat(octaves) {
            sum += noise(x * f, y * f) * amp
            amp *= 0.5f
            f *= 2f
        }
        return sum
    }
}
