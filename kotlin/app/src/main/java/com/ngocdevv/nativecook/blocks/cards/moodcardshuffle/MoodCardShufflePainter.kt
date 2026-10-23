package com.ngocdevv.nativecook.blocks.cards.moodcardshuffle

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** Every photo (800 px wide) and its blurred backdrop, as they arrive, and the engine's clock then. */
internal class MoodCardShufflePhotos {
    val cards = HashMap<String, Bitmap>()
    val blurs = HashMap<String, Bitmap>()
    val at = HashMap<String, Double>()
}

private fun sans(weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SANS_SERIF, weight, false)
    } else {
        Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

private fun textPaint(weight: Int, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
    typeface = sans(weight)
    textSize = size
}

private fun argb(r: Float, g: Float, b: Float, a: Float): Int =
    ((a.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or
        ((r * 255f).roundToInt() shl 16) or
        ((g * 255f).roundToInt() shl 8) or
        (b * 255f).roundToInt()

/** Draws the backdrop and the deck in dp, like the RN Skia picture. */
internal class MoodCardShufflePainter {
    // sans-serif, as the RN version sets it on Android: semibold (600) and medium (500), never lighter.
    private val region = textPaint(600, 9.5f)
    private val name = textPaint(600, 21f)
    private val blurb = textPaint(500, 12.5f)
    private val chip = textPaint(600, 11f)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val gradient = Paint()
    private val matrix = Matrix()
    private val values = FloatArray(9)
    private val rect = RectF()
    private val src = Rect()
    private val clip = Path()
    private val bookmark = Path().apply {
        moveTo(-4.5f, -6f)
        lineTo(4.5f, -6f)
        lineTo(4.5f, 6f)
        lineTo(0f, 2.6f)
        lineTo(-4.5f, 6f)
        close()
    }

    private var groundH = -1f
    private var ground: Shader? = null
    private var shade: Shader? = null
    private var veilH = -1f
    private var veil: Shader? = null
    private var shadowKey = ""
    private var shadow: Bitmap? = null

    /** A place as the canvas draws it, its blurb set for a card `cardWidth` dp wide. */
    fun face(place: MoodCardShufflePlace, cardWidth: Double): Face {
        val upper = place.region.uppercase()
        // Letter by letter (UTF-16 units, as RN indexes the string), each on its glyph advance plus
        // REGION_TRACKING; spaces keep their own advance.
        val letters = ArrayList<Pair<String, Float>>(upper.length)
        val advance = FloatArray(1)
        var at = 0f
        for (ch in upper) {
            val s = ch.toString()
            letters.add(s to at)
            region.getTextWidths(s, advance)
            at += (if (advance[0].isFinite()) advance[0] else 0f) + REGION_TRACKING
        }
        return Face(
            name = place.name,
            region = upper,
            photo = place.photo,
            lines = if (cardWidth > 0) wrapTwo(place.blurb, (cardWidth - 32).toFloat()) else emptyList(),
            regionLetters = letters,
            chipWidth = measure(chip, "Curated") + 20f,
        )
    }

    private val bounds = Rect()

    /**
     * A string's width as the RN version measures the chip and the blurb lines: Skia's
     * `measureText(…).width` is the width of the glyphs' ink bounds rounded out to whole units and
     * padded for antialiasing (fitted on the RN rendering: ink rounded out + 1.75).
     */
    private fun measure(paint: Paint, text: String): Float {
        if (text.isEmpty()) return 0f
        paint.getTextBounds(text, 0, text.length, bounds)
        if (bounds.isEmpty) return 0f
        return bounds.width() + 1.75f
    }

    /** Breaks `text` into at most two lines of `width` dp, the second ending in "…" if it overflows. */
    private fun wrapTwo(text: String, width: Float): List<String> {
        val words = text.split(" ")
        val lines = ArrayList<String>(2)
        var line = ""
        for (word in words) {
            val next = if (line.isNotEmpty()) "$line $word" else word
            if (measure(blurb, next) <= width || line.isEmpty()) {
                line = next
                continue
            }
            if (lines.size == 1) {
                // Second line full: end it with an ellipsis that fits.
                var cut = "$line…"
                while (cut.length > 1 && measure(blurb, cut) > width) cut = "${cut.dropLast(2).trimEnd()}…"
                lines.add(cut)
                return lines
            }
            lines.add(line)
            line = word
        }
        if (line.isNotEmpty()) lines.add(line)
        return lines.take(2)
    }

    /** The whole stage, in dp: backdrop, veil, then the deck back to front. */
    fun drawStage(c: Canvas, engine: MoodCardShuffleEngine, photos: MoodCardShufflePhotos, width: Float, height: Float, density: Float) {
        if (width <= 0f || height <= 0f || !width.isFinite() || !height.isFinite()) return
        fill.shader = null
        fill.color = 0xFF2A2820.toInt()
        c.drawRect(0f, 0f, width, height, fill)
        for ((id, alpha) in engine.backdrops) {
            val blur = photos.blurs[id] ?: continue
            drawCover(c, blur, width, height, alpha.toFloat())
        }
        // A warm veil: darker at the top for the title, lighter around the deck.
        if (veilH != height || veil == null) {
            veilH = height
            veil = LinearGradient(
                0f, 0f, 0f, height,
                intArrayOf(argb(0.07f, 0.066f, 0.047f, 0.46f), argb(0.07f, 0.066f, 0.047f, 0.1f), argb(0.07f, 0.066f, 0.047f, 0.4f)),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        gradient.shader = veil
        gradient.alpha = 255
        c.drawRect(0f, 0f, width, height, gradient)

        val w = cardSizeFor(engine.stageWidth).first.toFloat()
        val h = cardSizeFor(engine.stageWidth).second.toFloat()
        if (w <= 0f || h <= 0f) return
        val shadowBitmap = shadowFor(w, h, density)
        val now = engine.elapsed
        for (item in engine.items) {
            if (item == null) continue
            cardMatrix(item.pose, engine.cx, engine.cy, values)
            if (values.any { !it.isFinite() }) continue
            matrix.setValues(values)
            c.save()
            c.concat(matrix)
            val layered = item.alpha < 0.996
            if (layered) {
                rect.set(-w / 2 - 40, -h / 2 - 40, w / 2 + 40, h / 2 + 40)
                c.saveLayerAlpha(rect, (item.alpha * 255).roundToInt().coerceIn(0, 255))
            }
            drawCard(c, item, w, h, now, photos, if (item.shadow) shadowBitmap else null)
            if (layered) c.restore()
            c.restore()
        }
    }

    /** A blurred photo stretched over the stage, cover-fit, at `alpha`. */
    private fun drawCover(c: Canvas, bitmap: Bitmap, width: Float, height: Float, alpha: Float) {
        val iw = bitmap.width.toFloat()
        val ih = bitmap.height.toFloat()
        if (iw <= 0 || ih <= 0 || alpha <= 0.002f) return
        val k = max(width / iw, height / ih)
        val sw = width / k
        val sh = height / k
        src.set(((iw - sw) / 2).roundToInt(), ((ih - sh) / 2).roundToInt(), ((iw + sw) / 2).roundToInt(), ((ih + sh) / 2).roundToInt())
        rect.set(0f, 0f, width, height)
        image.alpha = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
        c.drawBitmap(bitmap, src, rect, image)
    }

    /** One card, drawn in its own coordinates (w × h, centred). */
    private fun drawCard(c: Canvas, item: Item, w: Float, h: Float, now: Double, photos: MoodCardShufflePhotos, shadowBitmap: Bitmap?) {
        val p = item.pose
        val dim = p.dim.toFloat()
        if (shadowBitmap != null) {
            val pad = SHADOW_BLUR * 3
            rect.set(-w / 2 + 10 - pad, -h / 2 + SHADOW_DROP - pad, w / 2 - 10 + pad, h / 2 + 10 + pad)
            image.alpha = (SHADOW_ALPHA * (1 - dim * 0.6f) * 255).roundToInt().coerceIn(0, 255)
            c.drawBitmap(shadowBitmap, null, rect, image)
        }
        c.save()
        clip.reset()
        clip.addRoundRect(-w / 2, -h / 2, w / 2, h / 2, CARD_RADIUS, CARD_RADIUS, Path.Direction.CW)
        c.clipPath(clip)
        // Until the photo arrives the card is a warm grey with a soft fall of light.
        if (groundH != h || ground == null || shade == null) {
            groundH = h
            ground = LinearGradient(0f, -h / 2, 0f, h / 2, 0xFF5A5848.toInt(), 0xFF34332A.toInt(), Shader.TileMode.CLAMP)
            shade = LinearGradient(
                0f, -h / 2, 0f, h / 2,
                intArrayOf(argb(0.03f, 0.03f, 0.02f, 0f), argb(0.03f, 0.03f, 0.02f, 0.2f), argb(0.03f, 0.03f, 0.02f, 0.8f)),
                floatArrayOf(0.32f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        gradient.shader = ground
        gradient.alpha = 255
        c.drawRect(-w / 2, -h / 2, w / 2, h / 2, gradient)
        val bitmap = photos.cards[item.face.photo]
        val at = photos.at[item.face.photo]
        if (bitmap != null && at != null && bitmap.width > 0 && bitmap.height > 0) {
            // Cover: crop the photo to the card's shape.
            val iw = bitmap.width.toFloat()
            val ih = bitmap.height.toFloat()
            val k = max(w / iw, h / ih)
            val sw = w / k
            val sh = h / k
            src.set(((iw - sw) / 2).roundToInt(), ((ih - sh) / 2).roundToInt(), ((iw + sw) / 2).roundToInt(), ((ih + sh) / 2).roundToInt())
            rect.set(-w / 2, -h / 2, w / 2, h / 2)
            image.alpha = (clamp01((now - at) / IMAGE_FADE) * 255).roundToInt()
            c.drawBitmap(bitmap, src, rect, image)
        }
        if (p.text > 0.01) {
            // The shade under the text, then the chip, the bookmark and the words, all fading together.
            val t = p.text.toFloat()
            gradient.shader = shade
            gradient.alpha = (t * 255).roundToInt().coerceIn(0, 255)
            c.drawRect(-w / 2, -h / 2, w / 2, h / 2, gradient)

            val left = -w / 2 + 16
            val top = -h / 2 + 14
            val f = item.face
            fill.shader = null
            fill.color = argb(1f, 1f, 1f, 0.94f * t)
            c.drawRoundRect(left - 2, top, left - 2 + f.chipWidth, top + 22, 11f, 11f, fill)
            chip.color = argb(0.08f, 0.08f, 0.06f, t)
            c.drawText("Curated", left + 8, top + 15, chip)

            // The bookmark: a round glass button, white when the place is saved.
            c.save()
            c.translate(w / 2 - 14 - 15, top + 11 + 4)
            val pop = item.pop.toFloat()
            c.scale(pop, pop)
            fill.color = if (item.saved) argb(1f, 1f, 1f, t) else argb(0.08f, 0.08f, 0.06f, 0.32f * t)
            c.drawCircle(0f, 0f, 15f, fill)
            if (item.saved) {
                fill.color = argb(0.08f, 0.08f, 0.06f, t)
                c.drawPath(bookmark, fill)
            } else {
                stroke.strokeWidth = 1f
                stroke.strokeJoin = Paint.Join.MITER
                stroke.color = argb(1f, 1f, 1f, 0.4f * t)
                c.drawCircle(0f, 0f, 14.5f, stroke)
                stroke.strokeWidth = 1.6f
                stroke.strokeJoin = Paint.Join.ROUND
                stroke.color = argb(1f, 1f, 1f, t)
                c.drawPath(bookmark, stroke)
            }
            c.restore()

            val lines = f.lines
            val bottom = h / 2 - 17
            blurb.color = argb(1f, 1f, 1f, 0.78f * t)
            for ((i, line) in lines.withIndex()) c.drawText(line, left, bottom - (lines.size - 1 - i) * 17, blurb)
            val nameY = bottom - max(0, lines.size - 1) * 17 - 22
            name.color = argb(1f, 1f, 1f, t)
            c.drawText(f.name, left, nameY, name)
            region.color = argb(1f, 1f, 1f, 0.72f * t)
            for ((letter, x) in f.regionLetters) {
                if (letter != " ") c.drawText(letter, left + x, nameY - 24, region)
            }
        }
        if (dim > 0.004f) {
            fill.shader = null
            fill.color = argb(0.04f, 0.04f, 0.03f, dim)
            c.drawRect(-w / 2, -h / 2, w / 2, h / 2, fill)
        }
        c.restore()
        // A hairline of light along the rim.
        stroke.strokeWidth = 1f
        stroke.strokeJoin = Paint.Join.MITER
        stroke.color = argb(1f, 1f, 1f, 0.14f * max(0f, 1 - dim))
        rect.set(-w / 2, -h / 2, w / 2, h / 2)
        c.drawRoundRect(rect, CARD_RADIUS, CARD_RADIUS, stroke)
    }

    /** The cards' shadow, made once: a rounded rect blurred (sigma SHADOW_BLUR dp) in a software bitmap. */
    private fun shadowFor(w: Float, h: Float, density: Float): Bitmap? {
        val key = "$w|$h|$density"
        if (key == shadowKey) return shadow
        shadowKey = key
        shadow = runCatching {
            val pad = SHADOW_BLUR * 3
            val bw = ((w - 20 + pad * 2) * density).roundToInt()
            val bh = ((h - 6 + pad * 2) * density).roundToInt()
            if (bw <= 0 || bh <= 0) return@runCatching null
            val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF000000.toInt()
                maskFilter = BlurMaskFilter(sigmaToRadius(SHADOW_BLUR * density), BlurMaskFilter.Blur.NORMAL)
            }
            val r = CARD_RADIUS * density
            canvas.drawRoundRect(pad * density, pad * density, bw - pad * density, bh - pad * density, r, r, paint)
            bitmap
        }.getOrNull()
        return shadow
    }
}

/**
 * The blurred backdrop for a photo: cover-fit into BACKDROP_W × BACKDROP_H px and muted towards grey,
 * blurred (sigma BACKDROP_BLUR px, edges clamped), then scaled up four times with a Mitchell cubic,
 * standing in for the RN version's cubic sampling. Runs off the main thread.
 */
internal fun moodCardShuffleBackdrop(source: Bitmap): Bitmap? {
    val iw = source.width.toFloat()
    val ih = source.height.toFloat()
    if (iw <= 0 || ih <= 0) return null
    val w = BACKDROP_W
    val h = BACKDROP_H
    val small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val k = max(w / iw, h / ih)
    val sw = w / k
    val sh = h / k
    val s = BACKDROP_SATURATION
    val r = (1 - s) * 0.2126f
    val g = (1 - s) * 0.7152f
    val b = (1 - s) * 0.0722f
    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix(floatArrayOf(r + s, g, b, 0f, 0f, r, g + s, b, 0f, 0f, r, g, b + s, 0f, 0f, 0f, 0f, 0f, 1f, 0f)),
        )
    }
    Canvas(small).drawBitmap(
        source,
        Rect(((iw - sw) / 2).roundToInt(), ((ih - sh) / 2).roundToInt(), ((iw + sw) / 2).roundToInt(), ((ih + sh) / 2).roundToInt()),
        Rect(0, 0, w, h),
        paint,
    )
    val pixels = IntArray(w * h)
    small.getPixels(pixels, 0, w, 0, 0, w, h)
    small.recycle()
    val red = FloatArray(w * h) { ((pixels[it] shr 16) and 0xFF).toFloat() }
    val green = FloatArray(w * h) { ((pixels[it] shr 8) and 0xFF).toFloat() }
    val blue = FloatArray(w * h) { (pixels[it] and 0xFF).toFloat() }
    for (channel in arrayOf(red, green, blue)) gaussian(channel, w, h, BACKDROP_BLUR)

    // Up four times along rows, then along columns.
    val scale = 4
    val ow = w * scale
    val oh = h * scale
    val wide = Array(3) { FloatArray(ow * h) }
    val tall = Array(3) { FloatArray(ow * oh) }
    val channels = arrayOf(red, green, blue)
    for (ch in 0 until 3) {
        cubicRows(channels[ch], w, h, wide[ch], ow)
        cubicColumns(wide[ch], ow, h, tall[ch], oh)
    }
    val out = IntArray(ow * oh)
    for (i in out.indices) {
        val rr = tall[0][i].roundToInt().coerceIn(0, 255)
        val gg = tall[1][i].roundToInt().coerceIn(0, 255)
        val bb = tall[2][i].roundToInt().coerceIn(0, 255)
        out[i] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
    }
    return Bitmap.createBitmap(out, ow, oh, Bitmap.Config.ARGB_8888)
}

/** A separable Gaussian of `sigma` px, edges clamped. */
private fun gaussian(channel: FloatArray, w: Int, h: Int, sigma: Double) {
    val radius = (sigma * 3).toInt().coerceAtLeast(1)
    val kernel = FloatArray(radius * 2 + 1) { exp(-((it - radius) * (it - radius)) / (2 * sigma * sigma)).toFloat() }
    val sum = kernel.sum()
    for (i in kernel.indices) kernel[i] /= sum
    val line = FloatArray(max(w, h))
    for (y in 0 until h) {
        for (x in 0 until w) line[x] = channel[y * w + x]
        for (x in 0 until w) {
            var acc = 0f
            for (j in kernel.indices) acc += kernel[j] * line[(x + j - radius).coerceIn(0, w - 1)]
            channel[y * w + x] = acc
        }
    }
    for (x in 0 until w) {
        for (y in 0 until h) line[y] = channel[y * w + x]
        for (y in 0 until h) {
            var acc = 0f
            for (j in kernel.indices) acc += kernel[j] * line[(y + j - radius).coerceIn(0, h - 1)]
            channel[y * w + x] = acc
        }
    }
}

/** Mitchell–Netravali cubic (B = C = 1/3), as Skia's cubic sampling uses. */
private fun mitchell(x: Float): Float {
    val t = kotlin.math.abs(x)
    val b = 1f / 3f
    val c = 1f / 3f
    return when {
        t < 1 -> ((12 - 9 * b - 6 * c) * t * t * t + (-18 + 12 * b + 6 * c) * t * t + (6 - 2 * b)) / 6
        t < 2 -> ((-b - 6 * c) * t * t * t + (6 * b + 30 * c) * t * t + (-12 * b - 48 * c) * t + (8 * b + 24 * c)) / 6
        else -> 0f
    }
}

private fun cubicRows(src: FloatArray, w: Int, h: Int, dst: FloatArray, ow: Int) {
    val ratio = w.toFloat() / ow
    for (x in 0 until ow) {
        val sx = (x + 0.5f) * ratio - 0.5f
        val x0 = floor(sx).toInt()
        val f = sx - x0
        val k0 = mitchell(f + 1)
        val k1 = mitchell(f)
        val k2 = mitchell(1 - f)
        val k3 = mitchell(2 - f)
        val i0 = (x0 - 1).coerceIn(0, w - 1)
        val i1 = x0.coerceIn(0, w - 1)
        val i2 = (x0 + 1).coerceIn(0, w - 1)
        val i3 = (x0 + 2).coerceIn(0, w - 1)
        for (y in 0 until h) {
            val row = y * w
            dst[y * ow + x] = k0 * src[row + i0] + k1 * src[row + i1] + k2 * src[row + i2] + k3 * src[row + i3]
        }
    }
}

private fun cubicColumns(src: FloatArray, w: Int, h: Int, dst: FloatArray, oh: Int) {
    val ratio = h.toFloat() / oh
    for (y in 0 until oh) {
        val sy = (y + 0.5f) * ratio - 0.5f
        val y0 = floor(sy).toInt()
        val f = sy - y0
        val k0 = mitchell(f + 1)
        val k1 = mitchell(f)
        val k2 = mitchell(1 - f)
        val k3 = mitchell(2 - f)
        val r0 = (y0 - 1).coerceIn(0, h - 1) * w
        val r1 = y0.coerceIn(0, h - 1) * w
        val r2 = (y0 + 1).coerceIn(0, h - 1) * w
        val r3 = (y0 + 2).coerceIn(0, h - 1) * w
        val out = y * w
        for (x in 0 until w) dst[out + x] = k0 * src[r0 + x] + k1 * src[r1 + x] + k2 * src[r2 + x] + k3 * src[r3 + x]
    }
}
