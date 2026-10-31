package com.ngocdevv.nativecook.blocks.cards.marketbagcheckout

import android.content.Context
import android.graphics.*
import com.ngocdevv.nativecook.blocks.cards.marketbagcheckout.MarketBagCheckoutMotion as Motion
import kotlin.math.*

/** Layout dimensions and insets are dp; bag and row positions share this coordinate space. */
internal data class MarketBagCheckoutGeometry(
    val width: Double,
    val height: Double,
    val top: Double,
    val bottom: Double,
) {
    val cardY
        get() = top + 108

    val cardWidth
        get() = width - 32

    val cardHeight = 288.0
    val buttonY
        get() = height - bottom - 72

    val hookY
        get() = cardY + cardHeight + 26

    val rimY
        get() = hookY + 56

    val bagLength
        get() = max(120.0, min(160.0, buttonY - rimY - 68))

    val bagX
        get() = width / 2

    fun rowY(itemIndex: Int) = cardY + 35 + 46 * itemIndex
}

/**
 * Decodes artwork once and caches the photographic backdrop, frosted panel and type per dp layout.
 */
internal class MarketBagCheckoutArt(private val context: Context) {
    private fun load(name: String) =
        context.assets.open("marketbagcheckout/marketbagcheckout_$name").use {
            requireNotNull(BitmapFactory.decodeStream(it))
        }

    private val counter = load("counter.jpg")
    private val prices = Motion.items.map { "$%.2f".format(java.util.Locale.US, it.price) }
    private val itemDrawOrder = intArrayOf(3, 1, 0, 2, 4)
    private val buttonClip = Path()
    private var buttonRect = RectF()
    private val images = Motion.items.map { load("${it.key}.webp") }
    private val fonts =
        listOf("Archivo-ExtraBold", "Archivo-SemiBold", "Archivo-Medium", "JetBrainsMono-Medium")
            .associateWith {
                Typeface.createFromAsset(
                    context.assets,
                    "marketbagcheckout/marketbagcheckout_$it.ttf",
                )
            }
    private val textPaints = mutableMapOf<String, Paint>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val outline = Path()
    private val net = Path()
    private val knots = Path()
    private val handles = Path()
    private val hem = Path()
    private val check = Path()
    private val knotX = DoubleArray(100)
    private val knotY = DoubleArray(100)
    private var cachedPage: Bitmap? = null
    private var cachedGeometry: MarketBagCheckoutGeometry? = null
    private var cachedDensity = 0f

    private fun color(hex: Int, alpha: Double = 1.0) =
        ((alpha.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24) or hex

    private fun paintFor(hex: Int, alpha: Double = 1.0, width: Double = 0.0): Paint {
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.color = color(hex, alpha)
        paint.style = if (width > 0) Paint.Style.STROKE else Paint.Style.FILL
        paint.strokeWidth = width.toFloat()
        return paint
    }

    private fun rect(x: Double, y: Double, width: Double, height: Double) =
        RectF(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat())

    private fun circle(canvas: Canvas, x: Double, y: Double, radius: Double, circlePaint: Paint) {
        canvas.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), circlePaint)
    }

    private fun labelPaint(key: String, face: String, size: Double, hex: Int) =
        textPaints.getOrPut(key) {
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                typeface = fonts.getValue(face)
                textSize = size.toFloat()
                color = color(hex)
            }
        }

    /** Draws dp-sized text centered vertically at y; align is 0 left, 0.5 center or 1 right. */
    private fun label(
        canvas: Canvas,
        text: String,
        x: Double,
        y: Double,
        face: String,
        size: Double,
        hex: Int,
        align: Double = 0.0,
        tracking: Double = 0.0,
    ) {
        val textPaint = labelPaint("$face$size$hex", face, size, hex)
        val width = textPaint.measureText(text) + tracking * (text.length - 1)
        var textX = x - width * align
        val baseline = y - (textPaint.ascent() + textPaint.descent()) / 2
        if (tracking == 0.0) canvas.drawText(text, textX.toFloat(), baseline.toFloat(), textPaint)
        else
            for (character in text) {
                val glyph = character.toString()
                canvas.drawText(glyph, textX.toFloat(), baseline.toFloat(), textPaint)
                textX += textPaint.measureText(glyph) + tracking
            }
    }

    /** Centers source pixels inside a dp rectangle using contain, or cover when requested. */
    private fun bitmap(
        canvas: Canvas,
        image: Bitmap,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        cover: Boolean = false,
    ) {
        val scale =
            if (cover) max(width / image.width, height / image.height)
            else min(width / image.width, height / image.height)
        val drawWidth = image.width * scale
        val drawHeight = image.height * scale
        canvas.drawBitmap(
            image,
            null,
            rect(x + (width - drawWidth) / 2, y + (height - drawHeight) / 2, drawWidth, drawHeight),
            paintFor(0xffffff),
        )
    }

    /** Caches a density-sized bitmap in pixels while drawing its contents in dp. */
    private fun backdrop(layout: MarketBagCheckoutGeometry, density: Float): Bitmap {
        if (layout == cachedGeometry && cachedDensity == density) return cachedPage!!
        cachedGeometry = layout
        cachedDensity = density
        buttonRect = rect(16.0, layout.buttonY, layout.cardWidth, 56.0)
        buttonClip.rewind()
        buttonClip.addRoundRect(buttonRect, 28f, 28f, Path.Direction.CW)
        val pixelWidth = (layout.width * density).roundToInt()
        val pixelHeight = (layout.height * density).roundToInt()
        val base = Bitmap.createBitmap(pixelWidth, pixelHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(base)
        canvas.scale(density, density)
        bitmap(canvas, counter, 0.0, 0.0, layout.width, layout.height, true)
        paintFor(0xffffff).shader =
            LinearGradient(
                0f,
                0f,
                0f,
                (layout.top + 120).toFloat(),
                intArrayOf(color(0xFAF6EE, 0.85), color(0xFAF6EE, 0.0)),
                null,
                Shader.TileMode.CLAMP,
            )
        canvas.drawRect(rect(0.0, 0.0, layout.width, layout.top + 120), paint)
        val blur = blur(base, layout.width.roundToInt(), layout.height.roundToInt())
        canvas.save()
        canvas.clipPath(
            Path().apply {
                addRoundRect(
                    rect(16.0, layout.cardY, layout.cardWidth, layout.cardHeight),
                    24f,
                    24f,
                    Path.Direction.CW,
                )
            }
        )
        canvas.drawBitmap(
            blur,
            null,
            rect(0.0, 0.0, layout.width, layout.height),
            paintFor(0xffffff),
        )
        canvas.drawRect(
            rect(16.0, layout.cardY, layout.cardWidth, layout.cardHeight),
            paintFor(0xFFFDF8, 0.66),
        )
        canvas.restore()
        blur.recycle()
        canvas.drawRoundRect(
            rect(16.5, layout.cardY + 0.5, layout.cardWidth - 1, layout.cardHeight - 1),
            23.5f,
            23.5f,
            paintFor(0xffffff, 0.75, 1.0),
        )
        for (i in 0..4) circle(canvas, 49.0, layout.rowY(i), 20.0, paintFor(0x1F2A22, 0.06))
        label(
            canvas,
            "CORNER GREENGROCER · PICKUP",
            24.0,
            layout.top + 19,
            "JetBrainsMono-Medium",
            11.0,
            0x6C7568,
            tracking = 1.6,
        )
        label(
            canvas,
            "Your basket",
            24.0,
            layout.top + 54,
            "Archivo-ExtraBold",
            36.0,
            0x1F2A22,
            tracking = -0.7,
        )
        val totalY = layout.cardY + 242
        canvas.drawRect(
            rect(16.0, totalY, layout.cardWidth, 1.0 / density),
            paintFor(0x1F2A22, 0.16),
        )
        label(canvas, "Total", 34.0, totalY + 23, "Archivo-SemiBold", 15.0, 0x6C7568)
        label(
            canvas,
            "$11.40",
            layout.width - 34,
            totalY + 23,
            "Archivo-ExtraBold",
            20.0,
            0x1F2A22,
            1.0,
        )
        paintFor(0x281E14, 0.25).maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        circle(canvas, layout.bagX + 3, layout.hookY + 4, 9.0, paint)
        paintFor(0xffffff).shader =
            RadialGradient(
                (layout.bagX - 3).toFloat(),
                (layout.hookY - 3).toFloat(),
                10f,
                intArrayOf(color(0xF6DFA2), color(0xC79A45), color(0x7C5A22)),
                null,
                Shader.TileMode.CLAMP,
            )
        circle(canvas, layout.bagX, layout.hookY, 8.0, paint)
        cachedPage?.recycle()
        cachedPage = base
        return base
    }

    /** Three box passes approximate sigma 16 with clamped edges, only when the layout changes. */
    private fun blur(source: Bitmap, width: Int, height: Int): Bitmap {
        val bitmap =
            Bitmap.createScaledBitmap(source, width, height, true)
                .copy(Bitmap.Config.ARGB_8888, true)
        var pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var output = IntArray(pixels.size)
        for (pass in 0..2) for (horizontal in listOf(true, false)) {
            val length = if (horizontal) width else height
            val lines = if (horizontal) height else width
            val radius = 15
            val sampleCount = radius * 2 + 1
            fun pixelIndex(line: Int, position: Int) =
                if (horizontal) line * width + position.coerceIn(0, width - 1)
                else position.coerceIn(0, height - 1) * width + line
            for (line in 0 until lines) {
                var redSum = 0
                var greenSum = 0
                var blueSum = 0
                for (offset in -radius..radius) {
                    val pixel = pixels[pixelIndex(line, offset)]
                    redSum += pixel shr 16 and 255
                    greenSum += pixel shr 8 and 255
                    blueSum += pixel and 255
                }
                for (position in 0 until length) {
                    output[pixelIndex(line, position)] =
                        (255 shl 24) or
                            ((redSum / sampleCount) shl 16) or
                            ((greenSum / sampleCount) shl 8) or
                            (blueSum / sampleCount)
                    val outgoing = pixels[pixelIndex(line, position - radius)]
                    val incoming = pixels[pixelIndex(line, position + radius + 1)]
                    redSum += (incoming shr 16 and 255) - (outgoing shr 16 and 255)
                    greenSum += (incoming shr 8 and 255) - (outgoing shr 8 and 255)
                    blueSum += (incoming and 255) - (outgoing and 255)
                }
            }
            val swap = pixels
            pixels = output
            output = swap
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Draws one pose in dp, then scales by display density. Keeps the authored layer order:
     * backdrop, bag shadow and handles, packed items, net, flying items, rows, then pay button.
     */
    fun draw(
        canvas: Canvas,
        layout: MarketBagCheckoutGeometry,
        density: Float,
        pose: Motion.Pose,
        holding: Boolean,
    ) {
        val background = backdrop(layout, density)
        canvas.save()
        canvas.scale(density, density)
        canvas.drawBitmap(
            background,
            null,
            rect(0.0, 0.0, layout.width, layout.height),
            paintFor(0xffffff),
        )
        val bagStretch = Motion.bagStretch(pose)
        val cinch =
            if (pose.paid >= 0)
                Motion.easeInOut(Motion.seg(pose.paid, 0.0, 0.35)) * (1 - pose.reset)
            else 0.0
        val hopProgress = if (pose.paid >= 0) Motion.seg(pose.paid, 0.3, 0.85) else 0.0
        val hop = sin(PI * hopProgress) * 16 * (1 - pose.reset)
        val bagLength = layout.bagLength + 34 * bagStretch
        val rim = 58 - 34 * cinch
        val belly = 52 + 38 * min(1.0, bagStretch * 1.3)
        val top = layout.rimY - hop
        val fill = pose.land.count { it >= 0 } * 0.2
        outline.rewind()
        for (i in 0..24) {
            val v = i / 24.0
            val x = (layout.bagX - Motion.halfWidth(v, rim, belly)).toFloat()
            val y = (top + v * bagLength).toFloat()
            if (i == 0) outline.moveTo(x, y) else outline.lineTo(x, y)
        }
        for (i in 24 downTo 0) {
            val v = i / 24.0
            outline.lineTo(
                (layout.bagX + Motion.halfWidth(v, rim, belly)).toFloat(),
                (top + v * bagLength).toFloat(),
            )
        }
        outline.close()
        for (row in 0..9) {
            val depth = row / 9.0
            val halfWidth = Motion.halfWidth(depth, rim, belly)
            val rowOffset = if (row % 2 == 0) 0.0 else 0.5
            for (column in 0..9) {
                val horizontalPosition = min(1.0, (column + rowOffset) / 9)
                val angle = (horizontalPosition - 0.5) * PI
                val index = row * 10 + column
                knotX[index] = layout.bagX + sin(angle) * halfWidth
                knotY[index] = top + depth * bagLength + cos(angle) * 3 * depth * (0.5 + fill)
            }
        }
        net.rewind()
        knots.rewind()
        for (row in 0..8) for (column in 0..9) {
            val knotIndex = row * 10 + column
            val evenRow = row % 2 == 0
            val leftColumn = if (evenRow) column - 1 else column
            val rightColumn = if (evenRow) column else column + 1
            fun connectKnot(nextKnotIndex: Int) {
                net.moveTo(knotX[knotIndex].toFloat(), knotY[knotIndex].toFloat())
                net.lineTo(knotX[nextKnotIndex].toFloat(), knotY[nextKnotIndex].toFloat())
            }
            if (leftColumn >= 0) connectKnot((row + 1) * 10 + leftColumn)
            if (rightColumn <= 9) connectKnot((row + 1) * 10 + rightColumn)
            if (row > 0 && row < 9)
                knots.addCircle(
                    knotX[knotIndex].toFloat(),
                    knotY[knotIndex].toFloat(),
                    1.5f,
                    Path.Direction.CW,
                )
        }
        hem.rewind()
        hem.moveTo((layout.bagX - rim).toFloat(), top.toFloat())
        hem.quadTo(
            layout.bagX.toFloat(),
            (top + 6 + 4 * cinch).toFloat(),
            (layout.bagX + rim).toFloat(),
            top.toFloat(),
        )
        handles.rewind()
        handles.moveTo((layout.bagX - rim + 4).toFloat(), top.toFloat())
        handles.cubicTo(
            (layout.bagX - rim - 10).toFloat(),
            (top - 30).toFloat(),
            (layout.bagX - 18).toFloat(),
            (layout.hookY - 8 - hop * 0.2).toFloat(),
            (layout.bagX - 2).toFloat(),
            layout.hookY.toFloat(),
        )
        handles.moveTo((layout.bagX + rim - 4).toFloat(), top.toFloat())
        handles.cubicTo(
            (layout.bagX + rim + 10).toFloat(),
            (top - 30).toFloat(),
            (layout.bagX + 18).toFloat(),
            (layout.hookY - 8 - hop * 0.2).toFloat(),
            (layout.bagX + 2).toFloat(),
            layout.hookY.toFloat(),
        )
        canvas.save()
        canvas.translate(10f, (12 + hop * 0.3).toFloat())
        paintFor(0x3C321E, 0.16).maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(outline, paint)
        canvas.restore()
        canvas.drawPath(
            handles,
            paintFor(0xB9A988, width = 4.0).apply { strokeCap = Paint.Cap.ROUND },
        )
        canvas.drawPath(
            handles,
            paintFor(0xEDE3CC, width = 2.6).apply { strokeCap = Paint.Cap.ROUND },
        )
        fun drawItem(itemIndex: Int) {
            val item = Motion.items[itemIndex]
            val flight = pose.fly[itemIndex]
            val bagSlotX = layout.bagX + item.slotX * belly * 1.5
            val bagSlotY = top + item.slotY * bagLength + item.size * 0.22
            val rowY = layout.rowY(itemIndex)
            val apex = min(rowY, bagSlotY) - 70
            val x = 49 + (bagSlotX - 49) * flight
            val y =
                (1 - flight) * (1 - flight) * rowY +
                    2 * (1 - flight) * flight * apex +
                    flight * flight * bagSlotY
            val scale = (38 + (item.size - 38) * min(1.0, flight * 1.4)) / item.size
            val rotation = item.rotation * flight + sin(PI * flight) * 0.6
            canvas.save()
            canvas.translate(x.toFloat(), y.toFloat())
            canvas.rotate((rotation * 180 / PI).toFloat())
            canvas.scale(scale.toFloat(), scale.toFloat())
            bitmap(canvas, images[itemIndex], -item.size / 2, -item.size / 2, item.size, item.size)
            canvas.restore()
        }
        for (i in itemDrawOrder) if (pose.fly[i] > 0.86) drawItem(i)
        canvas.drawPath(outline, paintFor(0xF2EAD8, 0.10))
        canvas.save()
        canvas.translate(1.2f, 1.6f)
        canvas.drawPath(net, paintFor(0x3C2D19, 0.35, 1.6))
        canvas.restore()
        canvas.drawPath(net, paintFor(0xF2EAD8, width = 1.5))
        canvas.drawPath(knots, paintFor(0xFBF5E6))
        canvas.drawPath(hem, paintFor(0xB9A988, width = 5.0).apply { strokeCap = Paint.Cap.ROUND })
        canvas.drawPath(hem, paintFor(0xEDE3CC, width = 3.6).apply { strokeCap = Paint.Cap.ROUND })
        canvas.drawRoundRect(
            rect(layout.bagX + 6, top + 10 + 10 * cinch, 9.0, 14.0),
            4f,
            4f,
            paintFor(0x9C6B3C),
        )
        for (i in 0..4) if (pose.fly[i] <= 0.86) drawItem(i)
        for (itemIndex in 0..4) {
            val y = layout.rowY(itemIndex)
            val item = Motion.items[itemIndex]
            val alpha = 1 - 0.55 * Motion.smooth(0.0, 0.6, pose.fly[itemIndex])
            canvas.saveLayerAlpha(null, (alpha * 255).roundToInt())
            label(canvas, item.name, 82.0, y - 7, "Archivo-SemiBold", 15.0, 0x1F2A22)
            label(canvas, item.meta, 82.0, y + 9, "Archivo-Medium", 12.0, 0x6C7568)
            label(
                canvas,
                prices[itemIndex],
                layout.width - 34,
                y,
                "JetBrainsMono-Medium",
                14.0,
                0x1F2A22,
                1.0,
            )
            canvas.restore()
            val checkProgress = Motion.smooth(0.7, 1.0, pose.fly[itemIndex])
            canvas.saveLayerAlpha(null, (checkProgress * 255).roundToInt())
            canvas.translate(69f, (layout.cardY + 27 + itemIndex * 46).toFloat())
            canvas.scale(
                (0.6 + 0.4 * checkProgress).toFloat(),
                (0.6 + 0.4 * checkProgress).toFloat(),
            )
            circle(canvas, 0.0, 0.0, 9.0, paintFor(0x3F7A57))
            check.rewind()
            check.moveTo(-3.5f, 0.3f)
            check.lineTo(-1f, 2.6f)
            check.lineTo(3.6f, -2.4f)
            canvas.drawPath(
                check,
                paintFor(0xffffff, width = 1.8).apply {
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                },
            )
            canvas.restore()
        }
        canvas.save()
        if (holding)
            canvas.scale(0.97f, 0.97f, layout.bagX.toFloat(), (layout.buttonY + 28).toFloat())
        val button = buttonRect
        canvas.drawRoundRect(button, 28f, 28f, paintFor(0x1F3A2C))
        canvas.clipPath(buttonClip)
        canvas.drawRect(
            rect(16.0, layout.buttonY, layout.cardWidth * max(0.0001, pose.hold), 56.0),
            paintFor(0x3F7A57),
        )
        val payLabelProgress =
            if (pose.paid >= 0) Motion.smooth(0.15, 0.4, pose.paid) * (1 - pose.reset) else 0.0
        canvas.saveLayerAlpha(null, ((1 - payLabelProgress) * 255).roundToInt())
        label(
            canvas,
            "Hold to pay $11.40",
            layout.bagX,
            layout.buttonY + 28 - 8 * payLabelProgress,
            "Archivo-SemiBold",
            17.0,
            0xF2EAD8,
            0.5,
        )
        canvas.restore()
        val paidLabelProgress =
            if (pose.paid >= 0) Motion.smooth(0.25, 0.55, pose.paid) * (1 - pose.reset) else 0.0
        canvas.saveLayerAlpha(null, (paidLabelProgress * 255).roundToInt())
        val text = "Paid · ready at the counter"
        val paidTextPaint =
            labelPaint("Archivo-SemiBold17.0${0xF2EAD8}", "Archivo-SemiBold", 17.0, 0xF2EAD8)
        val start = layout.bagX - (26 + paidTextPaint.measureText(text)) / 2
        val paidTextY = layout.buttonY + 28 + 8 * (1 - paidLabelProgress)
        check.rewind()
        check.moveTo((start + 4).toFloat(), (paidTextY + 0.4).toFloat())
        check.lineTo((start + 7.6).toFloat(), (paidTextY + 3.8).toFloat())
        check.lineTo((start + 14.2).toFloat(), (paidTextY - 3.4).toFloat())
        canvas.drawPath(
            check,
            paintFor(0xF2EAD8, width = 2.4).apply {
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            },
        )
        label(canvas, text, start + 26, paidTextY, "Archivo-SemiBold", 17.0, 0xF2EAD8)
        canvas.restore()
        canvas.restore()
        canvas.restore()
    }
}
