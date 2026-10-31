package com.ngocdevv.nativecook.blocks.buttons.glasssplitbutton

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Blurred artwork is baked once, with the same rendering on API26 and later. */
internal class GlassSplitButtonArt {
    private class Sprite(val bitmap: Bitmap, val half: Float)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(21, 23, 28)
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
    }
    private val clip = Path()
    private val bounds = RectF()
    private val destination = RectF()
    private val ring = RectF()
    private var gradientWidth = -1f
    private var gradientHeight = -1f
    private val background = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wash = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(107, 236, 240, 246) }
    private val sigmaBehindGlass = sqrt(34f * 34 + 18f * 18)
    private val colors = intArrayOf(0xFFFFA97A.toInt(), 0xFF9D8CFF.toInt(), 0xFF6FD8C8.toInt())
    private val radii = floatArrayOf(84f, 78f, 62f)
    private val blobs = Array(3) { sprite(radii[it], 34f, colors[it]) }
    // Gaussian convolution: the existing sigma34 blur followed by the sigma18 backdrop.
    private val frostedBlobs = Array(3) { sprite(radii[it], sigmaBehindGlass, colors[it]) }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // Skia's shadowOnly filter multiplies the source rounded rect's .01 alpha.
        color = Color.argb((255 * 0.28 * 0.01).roundToInt(), 40, 48, 70)
        maskFilter = BlurMaskFilter(sigmaToRadius(24f), BlurMaskFilter.Blur.NORMAL)
    }
    private val oldAndroidShadows = LruCache<Long, Bitmap>(96)
    private fun sigmaToRadius(sigma: Float) = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f)
    private fun sprite(radius: Float, sigma: Float, color: Int): Sprite {
        // One pixel/dp is ample for this low-frequency field; never rasterize it per frame.
        val half = ceil(radius + 4 * sigma)
        val bitmap = Bitmap.createBitmap((half * 2).toInt(), (half * 2).toInt(), Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            alpha = (255 * 0.9).roundToInt()
            maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        }
        Canvas(bitmap).drawCircle(half, half, radius, paint)
        return Sprite(bitmap, half)
    }
    private fun drawBlob(canvas: Canvas, sprite: Sprite, x: Float, y: Float) {
        destination.set(x - sprite.half, y - sprite.half, x + sprite.half, y + sprite.half)
        canvas.drawBitmap(sprite.bitmap, null, destination, bitmapPaint)
    }
    private fun shadow(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        if (Build.VERSION.SDK_INT >= 28) {
            canvas.drawRoundRect(x, y + 18, x + w, y + h + 18, h / 2, h / 2, shadowPaint)
        } else {
            // API26–27 hardware ignores mask filters; cache the low-frequency shadow at 2dp steps.
            val qw = (w / 2).roundToInt() * 2
            val qh = (h / 2).roundToInt() * 2
            val key = (qw.toLong() shl 32) or qh.toLong()
            val bitmap = oldAndroidShadows.get(key) ?: Bitmap.createBitmap(
                qw + 192, qh + 192, Bitmap.Config.ARGB_8888,
            ).also {
                Canvas(it).drawRoundRect(96f, 96f, 96f + qw, 96f + qh, qh / 2f, qh / 2f, shadowPaint)
                oldAndroidShadows.put(key, it)
            }
            destination.set(x - 96, y + 18 - 96, x + w + 96, y + h + 18 + 96)
            canvas.drawBitmap(bitmap, null, destination, bitmapPaint)
        }
    }
    fun draw(canvas: Canvas, width: Float, height: Float, clock: Double, frame: GlassSplitButtonFrame) {
        val cx = width / 2
        val cy = height / 2
        if (gradientWidth != width || gradientHeight != height) {
            gradientWidth = width
            gradientHeight = height
            background.shader = LinearGradient(0f, 0f, width * 0.4f, height,
                intArrayOf(0xFFE9ECF1.toInt(), 0xFFB7BFCC.toInt(), 0xFF7D879A.toInt()), null, Shader.TileMode.CLAMP)
            rim.shader = LinearGradient(0f, cy - 66, 0f, cy + 66,
                intArrayOf(Color.argb(242, 255, 255, 255), Color.argb(64, 255, 255, 255), Color.argb(230, 255, 210, 170)),
                null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width, height, background)
        val angle = clock / LOOP * PI * 2
        val orangeX = cx - 90 + 40 * sin(angle).toFloat()
        val purpleX = cx + 100 + 36 * cos(angle).toFloat()
        val mintY = cy - 110 + 30 * sin(angle * 2).toFloat()
        drawBlob(canvas, blobs[0], orangeX, cy - 40)
        drawBlob(canvas, blobs[1], purpleX, cy + 10)
        drawBlob(canvas, blobs[2], cx, mintY)
        val w = frame.width * frame.press
        val h = frame.height * frame.press
        bounds.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        shadow(canvas, bounds.left, bounds.top, w, h)
        clip.rewind()
        clip.addRoundRect(bounds, h / 2, h / 2, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawRect(0f, 0f, width, height, background)
        drawBlob(canvas, frostedBlobs[0], orangeX, cy - 40)
        drawBlob(canvas, frostedBlobs[1], purpleX, cy + 10)
        drawBlob(canvas, frostedBlobs[2], cx, mintY)
        canvas.drawRect(bounds, wash)
        canvas.restore()
        canvas.drawRoundRect(bounds, h / 2, h / 2, rim)
        icon.alpha = 255
        if (frame.icons > 0) {
            for (sign in intArrayOf(-1, 1)) {
                val x = cx + sign * frame.width * 0.31f
                ring.set(x - 16, cy - 16, x + 16, cy + 16)
                if (frame.icons >= 1f) canvas.drawOval(ring, icon)
                else canvas.drawArc(ring, 0f, frame.icons * 360, false, icon)
            }
        }
        // Only the face is pressed. Icon geometry remains unchanged, exactly as in RN.
        icon.alpha = ((0.28f + 0.72f * frame.icons) * 255).roundToInt()
        canvas.drawLine(cx, cy - 15, cx, cy + 15, icon)
        canvas.drawLine(cx - 15, cy, cx + 15, cy, icon)
    }
}

/** Static, shaped text layouts; only the labels' opacity and vertical offset animate. */
internal class GlassSplitButtonText(
    private val width: Float,
    private val height: Float,
    private val safeTop: Float,
    private val safeBottom: Float,
    private val density: Float,
    brand: String,
    title: String,
) {
    private class Label(val layout: StaticLayout, val leading: Float, val opacity: Float, val height: Float)
    private val sans = Typeface.create("sans-serif", Typeface.NORMAL)
    private fun face(weight: Int) = if (Build.VERSION.SDK_INT >= 28) Typeface.create(sans, weight, false)
        else Typeface.create(if (weight >= 800) "sans-serif-black" else "sans-serif-medium", Typeface.NORMAL)
    private fun label(
        text: String, size: Float, weight: Int = 400, maxWidth: Float = width - 48,
        lineHeight: Float? = null, spacing: Float = 0f, alpha: Float = 1f,
    ): Label {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = if (weight == 400) sans else face(weight)
            // TextPaint has its own density property; sizes use the screen's density.
            textSize = size * this@GlassSplitButtonText.density
            letterSpacing = spacing / size
        }
        val leading = lineHeight?.let { it * density - (paint.fontMetrics.descent - paint.fontMetrics.ascent) } ?: 0f
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, (maxWidth * density).roundToInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
        if (lineHeight != null) builder.setLineSpacing(leading, 1f)
        val layout = builder.build()
        return Label(layout, leading / density / 2, alpha,
            if (lineHeight != null) layout.lineCount * lineHeight else layout.height / density)
    }
    private val brandLabel = label(brand, 18f, 800)
    private val meta = label("12 notes today", 14f, 600, alpha = 0.55f)
    private val titleLabel = label(title, 48f, 900, lineHeight = 50f, spacing = -1.8f)
    private val sub = label("A photo, a thought or a voice memo — one tap away.", 17f,
        maxWidth = 290f, lineHeight = 23f, alpha = 0.65f)
    private val labels = arrayOf("Photo", "Note", "Voice").map { label(it, 14f, 700, maxWidth = 64f) }
    private val footer = label("Last capture · 9:12 · “Pick up film rolls”", 14f, 600, alpha = 0.85f)
    private fun naturalWidth(label: Label) = (0 until label.layout.lineCount).maxOf { label.layout.getLineWidth(it) } / density
    private fun draw(canvas: Canvas, label: Label, x: Float, y: Float, ink: Int = 0xFF15171C.toInt(), opacity: Float = 1f) {
        if (opacity <= 0) return
        label.layout.paint.color = ink
        label.layout.paint.alpha = (255 * label.opacity * opacity).roundToInt().coerceIn(0, 255)
        canvas.save()
        canvas.translate(x, y + label.leading)
        canvas.scale(1 / density, 1 / density)
        label.layout.draw(canvas)
        canvas.restore()
    }
    fun draw(canvas: Canvas, icons: Float) {
        val head = safeTop + 8
        draw(canvas, brandLabel, 24f, head + (34 - brandLabel.height) / 2)
        draw(canvas, meta, width - 24 - naturalWidth(meta), head + (34 - meta.height) / 2)
        val titleY = head + 34 + 16
        draw(canvas, titleLabel, 24f, titleY)
        draw(canvas, sub, 24f, titleY + titleLabel.height + 12)
        val labelsY = height / 2 + 46 + 22 + 8 * (1 - icons)
        // RN space-between distributes 64dp labels inside a 272dp row padded by 22dp.
        labels.forEachIndexed { i, label ->
            val x = width / 2 - 136 + 22 + i * 82 + (64 - naturalWidth(label)) / 2
            draw(canvas, label, x, labelsY, opacity = icons)
        }
        draw(canvas, footer, (width - naturalWidth(footer)) / 2,
            height - safeBottom - 18 - footer.height, Color.WHITE)
    }
}
