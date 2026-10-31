package com.ngocdevv.nativecook.blocks.cards.tapridepass

import android.content.Context
import android.graphics.*
import android.os.Build
import com.ngocdevv.nativecook.blocks.cards.tapridepass.TapRidePassMotion as Motion
import kotlin.math.*

/**
 * Cached artwork and ordered Canvas rendering for Tap Ride Pass. Geometry and font sizes use dp;
 * the Compose host scales the canvas to physical pixels. Static card/ticket faces are rasterized at
 * 3×, while transforms and lighting change each frame.
 */
internal class TapRidePassArt(private val context: Context) {
    private fun load(name: String) =
        context.assets.open("tapridepass/tapridepass_$name").use {
            requireNotNull(BitmapFactory.decodeStream(it))
        }

    private val backdrop = load("backdrop.jpg")
    private val validator = load("validator.webp")
    private val card = load("card.webp")
    private val coil = load("card-coil.webp")
    private val faces =
        listOf("Archivo-ExtraBold", "Archivo-SemiBold", "Archivo-Medium", "JetBrainsMono-Medium")
            .associateWith {
                Typeface.createFromAsset(context.assets, "tapridepass/tapridepass_$it.ttf")
            }
    private val paints = mutableMapOf<String, Paint>()
    private val workingPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val matrix = Matrix()
    private val sourceCorners = FloatArray(8)
    private val projectedCorners = FloatArray(8)
    private var cachedGeometry: TapRidePassGeometry? = null
    private var cardFace: Bitmap? = null
    private var ticketFace: Bitmap? = null
    private var cachedBalance = -1.0
    private var ticketRollY = 0.0
    private val paperRuntime = if (Build.VERSION.SDK_INT >= 33) TapRidePassPaperRuntime() else null
    private lateinit var canvas: Canvas

    private fun color(hex: Int, alpha: Double = 1.0) =
        ((alpha.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24) or hex

    /** Resets the shared paint before each use so gradients, alpha and blend modes cannot leak. */
    private fun paint(
        hex: Int,
        alpha: Double = 1.0,
        stroke: Double = 0.0,
        blur: Double = 0.0,
    ): Paint {
        workingPaint.reset()
        workingPaint.isAntiAlias = true
        workingPaint.isFilterBitmap = true
        workingPaint.color = color(hex, alpha)
        if (stroke > 0) {
            workingPaint.style = Paint.Style.STROKE
            workingPaint.strokeWidth = stroke.toFloat()
        }
        if (blur > 0)
            workingPaint.maskFilter = BlurMaskFilter(blur.toFloat(), BlurMaskFilter.Blur.NORMAL)
        return workingPaint
    }

    private fun rect(x: Double, y: Double, width: Double, height: Double) =
        RectF(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat())

    private fun image(
        bitmap: Bitmap,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        paint: Paint = paint(0xffffff),
    ) {
        canvas.drawBitmap(bitmap, null, rect(x, y, width, height), paint)
    }

    private fun circle(x: Double, y: Double, radius: Double, hex: Int, alpha: Double = 1.0) {
        canvas.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), paint(hex, alpha))
    }

    private fun drawLine(
        x: Double,
        y: Double,
        x2: Double,
        y2: Double,
        hex: Int,
        width: Double,
        alpha: Double = 1.0,
    ) {
        canvas.drawLine(
            x.toFloat(),
            y.toFloat(),
            x2.toFloat(),
            y2.toFloat(),
            paint(hex, alpha, width),
        )
    }

    private fun font(face: String, size: Double, tracking: Double = 0.0): Paint {
        val key = "$face/$size/$tracking"
        return paints.getOrPut(key) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = faces[if (face == "Mono") "JetBrainsMono-Medium" else "Archivo-$face"]
                textSize = size.toFloat()
            }
        }
    }

    private fun width(text: String, face: String, size: Double, tracking: Double = 0.0): Double =
        font(face, size).measureText(text).toDouble() + max(0, text.length - 1) * tracking

    /**
     * Draws text at a top-aligned line box or an explicit baseline, preserving manual tracking in
     * dp.
     */
    private fun text(
        text: String,
        x: Double,
        y: Double,
        face: String,
        size: Double,
        hex: Int,
        tracking: Double = 0.0,
        lineHeight: Double? = null,
        right: Boolean = false,
        baseline: Boolean = false,
        alpha: Double = 1.0,
    ) {
        val workingPaint = font(face, size)
        workingPaint.color = color(hex, alpha)
        workingPaint.shader = null
        val fontMetrics = workingPaint.fontMetrics
        val baselineY =
            if (baseline) y
            else
                y - fontMetrics.ascent +
                    ((lineHeight ?: (fontMetrics.descent - fontMetrics.ascent).toDouble()) -
                        (fontMetrics.descent - fontMetrics.ascent)) / 2
        var left = x - if (right) width(text, face, size, tracking) else 0.0
        if (tracking == 0.0)
            canvas.drawText(text, left.toFloat(), baselineY.toFloat(), workingPaint)
        else
            for (character in text) {
                val glyphText = character.toString()
                canvas.drawText(glyphText, left.toFloat(), baselineY.toFloat(), workingPaint)
                left += workingPaint.measureText(glyphText) + tracking
            }
    }

    private fun roundel(x: Double, y: Double, size: Double) {
        circle(x + size / 2, y + size / 2, size / 2, 0xE8452C)
        val fontSize = size * 0.42
        text(
            "R2",
            x + (size - width("R2", "ExtraBold", fontSize)) / 2,
            y,
            "ExtraBold",
            fontSize,
            0xffffff,
            lineHeight = size,
        )
    }

    /** Temporarily redirects drawing into a 3× bitmap, then restores the active frame canvas. */
    private fun bitmap(width: Double, height: Double, draw: () -> Unit): Bitmap {
        val previous = canvas
        val outputBitmap =
            Bitmap.createBitmap(
                ceil(width * 3).toInt(),
                ceil(height * 3).toInt(),
                Bitmap.Config.ARGB_8888,
            )
        canvas = Canvas(outputBitmap)
        canvas.scale(3f, 3f)
        draw()
        canvas = previous
        return outputBitmap
    }

    /** Rebuilds static card and ticket faces only when geometry or balance changes. */
    private fun prepare(layout: TapRidePassGeometry, driver: TapRidePassDriver) {
        if (cachedGeometry == layout && cachedBalance == driver.balance) return
        cachedGeometry = layout
        cachedBalance = driver.balance
        val cardWidth = layout.cardWidth
        val cardHeight = layout.cardHeight
        val ticketWidth = layout.ticketWidth
        cardFace?.recycle()
        ticketFace?.recycle()
        cardFace =
            bitmap(cardWidth, cardHeight) {
                circle(60.0, 62.0, 10.0, 0xE8452C)
                text(
                    "R2",
                    60 - width("R2", "ExtraBold", 8.5) / 2,
                    65.2,
                    "ExtraBold",
                    8.5,
                    0xffffff,
                    baseline = true,
                )
                text("RIVERLINE", 77.0, 66.0, "Mono", 11.0, 0xF3EEE6, baseline = true)
                text(
                    "30 DAYS",
                    cardWidth - 58,
                    66.0,
                    "Mono",
                    11.0,
                    0xC9BFB1,
                    right = true,
                    baseline = true,
                )
                val y = cardHeight * 0.47
                drawLine(104.0, y, cardWidth - 60, y, 0xE8452C, 1.6)
                repeat(6) {
                    circle(104 + (cardWidth - 164) / 5 * it, y, if (it == 2) 4.2 else 2.6, 0xE8452C)
                }
                text("HBG · OML · 4 STOPS", 104.0, y + 20, "Mono", 9.0, 0xC9BFB1, baseline = true)
                text(
                    "Commuter",
                    104.0,
                    cardHeight - 72,
                    "SemiBold",
                    15.0,
                    0xF3EEE6,
                    baseline = true,
                )
                text("0417 2048", 104.0, cardHeight - 55, "Mono", 11.0, 0xC9BFB1, baseline = true)
                text(
                    "BALANCE",
                    cardWidth - 58,
                    cardHeight - 82,
                    "Mono",
                    9.0,
                    0xC9BFB1,
                    right = true,
                    baseline = true,
                )
                text(
                    money(driver.balance),
                    cardWidth - 58,
                    cardHeight - 55,
                    "ExtraBold",
                    24.0,
                    0xF3EEE6,
                    right = true,
                    baseline = true,
                )
            }
        ticketFace =
            bitmap(ticketWidth, 430.0) {
                roundel(22.0, 22.0, 18.0)
                text("SINGLE RIDE", 48.0, 22.0, "Mono", 10.5, 0x7C746A, 1.4, 18.0)
                text(
                    "31 OCT · 08:42",
                    ticketWidth - 22,
                    22.0,
                    "Mono",
                    10.5,
                    0x7C746A,
                    1.4,
                    18.0,
                    true,
                )
                text("HBG", 22.0, 66.0, "ExtraBold", 46.0, 0x17140F, -1.2, 50.0)
                text("OML", ticketWidth - 22, 66.0, "ExtraBold", 46.0, 0x17140F, -1.2, 50.0, true)
                text("Harbor Gate", 22.0, 118.0, "Medium", 13.0, 0x7C746A)
                text("Old Mill", ticketWidth - 22, 118.0, "Medium", 13.0, 0x7C746A, right = true)
                val arrowX = ticketWidth / 2 - 30
                val arrowY = 84.0
                drawLine(arrowX, arrowY + 10, arrowX + 52, arrowY + 10, 0xE8452C, 2.2)
                for (x in listOf(14, 27, 40)) {
                    circle(arrowX + x, arrowY + 10, 2.8, 0xF4F0E8)
                    canvas.drawCircle(
                        (arrowX + x).toFloat(),
                        (arrowY + 10).toFloat(),
                        2.8f,
                        paint(0xE8452C, stroke = 1.8),
                    )
                }
                path.reset()
                path.moveTo((arrowX + 51).toFloat(), (arrowY + 4).toFloat())
                path.lineTo((arrowX + 58).toFloat(), (arrowY + 10).toFloat())
                path.lineTo((arrowX + 51).toFloat(), (arrowY + 16).toFloat())
                canvas.drawPath(path, paint(0xE8452C, stroke = 2.2))
                val gridY = 156.0
                val rowHeight = 72.0
                val labelHeight = 13.0
                ticketRollY = gridY + 12 + labelHeight
                drawLine(22.0, gridY, ticketWidth - 22, gridY, 0, 0.4, 0.18)
                drawLine(22.0, gridY + rowHeight, ticketWidth - 22, gridY + rowHeight, 0, 0.4, 0.18)
                drawLine(
                    ticketWidth / 2,
                    gridY,
                    ticketWidth / 2,
                    gridY + rowHeight + 65,
                    0,
                    0.4,
                    0.18,
                )
                for (i in 0..3) {
                    val x = if (i % 2 == 0) 22.0 else ticketWidth / 2 + 16
                    val y = gridY + if (i < 2) 0.0 else rowHeight
                    text(
                        listOf("FARE", "BALANCE", "STOPS", "GATE")[i],
                        x,
                        y + 12,
                        "Mono",
                        10.0,
                        0x7C746A,
                        1.6,
                    )
                    if (i != 1)
                        text(
                            listOf("$2.75", "", "4 · 11 min", "3")[i],
                            x,
                            y + 12 + labelHeight + 4,
                            if (i == 0) "ExtraBold" else "SemiBold",
                            if (i == 0) 28.0 else 19.0,
                            0x17140F,
                            lineHeight = if (i == 0) 32.0 else 26.0,
                        )
                }
                val y = 338.2
                circle(26.5, y + 10.5, 4.5, 0x1FAF6A)
                text("Gate 3 open", 40.0, y, "SemiBold", 18.0, 0x17140F)
                text("Valid until 10:42", 22.0, y + 28, "Mono", 10.5, 0x7C746A, 0.4)
                val bars =
                    intArrayOf(
                        2,
                        1,
                        1,
                        3,
                        1,
                        2,
                        1,
                        1,
                        2,
                        3,
                        1,
                        1,
                        2,
                        1,
                        3,
                        1,
                        2,
                        2,
                        1,
                        1,
                        3,
                        1,
                        1,
                        2,
                        1,
                        2,
                        3,
                        1,
                        1,
                        2,
                        1,
                        1,
                        2,
                        3,
                        1,
                        2,
                    )
                val barcodeWidth =
                    bars.mapIndexed { i, b -> b + (if (i % 3 == 0) 1.5 else 1.0) }.sum()
                var x = ticketWidth - 22 - barcodeWidth
                bars.forEachIndexed { i, b ->
                    if (i % 2 == 0)
                        canvas.drawRect(rect(x, y + 3, b.toDouble(), 38.0), paint(0x17140F))
                    x += b + if (i % 3 == 0) 1.5 else 1.0
                }
            }
    }

    fun money(amount: Double) = String.format(java.util.Locale.US, "$%.2f", amount)

    private fun oval(layout: TapRidePassGeometry, scale: Double) =
        rect(
            layout.readerX - layout.readerRadiusX * scale,
            layout.readerEllipseY - layout.readerRadiusY * scale,
            layout.readerRadiusX * 2 * scale,
            layout.readerRadiusY * 2 * scale,
        )

    /**
     * Draws in compositing order: station/reader, slotted paper, coil spill, card/shadow, dimmer,
     * raised paper, then labels and controls. The caller supplies a canvas already scaled to dp.
     */
    fun draw(canvas: Canvas, layout: TapRidePassGeometry, driver: TapRidePassDriver) {
        this.canvas = canvas
        prepare(layout, driver)
        val dockProgress =
            Motion.smooth(
                0.0,
                1.0,
                1 -
                    min(
                        1.0,
                        hypot(driver.cardX - layout.readerX, driver.cardY - layout.readerY) /
                            layout.homeDistance,
                    ),
            )
        val cardScale = (1 + (layout.dockScale - 1) * dockProgress) * (1 + 0.035 * driver.lift)
        val x = driver.cardX
        val y =
            driver.cardY +
                Motion.smooth(0.45, 0.95, driver.ticketProgress) * layout.cardHeight * 0.78
        val rotationX = acos(105.0 / 166) * dockProgress
        val rotationZ = driver.tiltDegrees * PI / 180 * (1 - dockProgress)
        val tickPulse = max(0.0, 1 - (driver.now - driver.lastTickTime) / 0.22) * driver.proximity
        val coilAlpha =
            min(
                1.0,
                0.85 * driver.proximity.pow(1.2) +
                    0.35 * tickPulse +
                    if (driver.tapAge > 0) max(0.0, 1 - driver.tapAge / 0.9) else 0.0,
            )
        val flash =
            if (driver.tapAge < 0) 0.0
            else
                Motion.smooth(0.0, 0.05, driver.tapAge) *
                    (1 - Motion.smooth(0.05, 0.6, driver.tapAge))
        canvas.drawColor(color(0x070708))
        val parallaxX = if (driver.reduceMotion) 0.0 else (x - layout.width / 2) * -0.04
        val parallaxY = if (driver.reduceMotion) 0.0 else (y - layout.homeY) * -0.015
        val cover =
            max(layout.width * 1.08 / backdrop.width, layout.height * 1.04 / backdrop.height)
        val backdropWidth = backdrop.width * cover
        val backdropHeight = backdrop.height * cover
        canvas.save()
        canvas.clipRect(
            rect(
                -layout.width * 0.04 + parallaxX,
                -layout.height * 0.02 + parallaxY,
                layout.width * 1.08,
                layout.height * 1.04,
            )
        )
        image(
            backdrop,
            layout.width / 2 - backdropWidth / 2 + parallaxX,
            layout.height / 2 - backdropHeight / 2 + parallaxY,
            backdropWidth,
            backdropHeight,
        )
        canvas.restore()
        val shadePaint = paint(0xffffff)
        shadePaint.shader =
            LinearGradient(
                0f,
                0f,
                0f,
                (layout.safeTop + 170).toFloat(),
                intArrayOf(color(0x060608, 0.92), color(0x060608, 0.55), color(0x060608, 0.0)),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawRect(rect(0.0, 0.0, layout.width, layout.safeTop + 170), shadePaint)
        if (flash > 0) {
            val effectPaint = paint(0xFFD9A8, flash * 0.1)
            effectPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            canvas.drawRect(rect(0.0, 0.0, layout.width, layout.height), effectPaint)
        }
        image(
            validator,
            layout.validatorX,
            layout.validatorY,
            layout.validatorWidth,
            layout.validatorHeight,
        )
        val pad = paint(0xffffff)
        pad.shader =
            RadialGradient(
                layout.readerX.toFloat(),
                (layout.readerEllipseY - layout.readerRadiusY * 0.3).toFloat(),
                layout.readerRadiusX.toFloat(),
                intArrayOf(color(0x16161A, 0.35), color(0x08080A, 0.55)),
                null,
                Shader.TileMode.CLAMP,
            )
        canvas.drawOval(oval(layout, 0.97), pad)
        val successProgress =
            if (driver.tapAge > 0)
                Motion.smooth(0.15, 0.45, driver.tapAge) *
                    (if (driver.mode == 0 && !driver.scripted) 0 else 1)
            else 0.0
        val ringSuccess =
            min(
                successProgress,
                if (driver.scripted) 1 - Motion.smooth(7.05, 7.8, driver.loopTime) else 1.0,
            )
        val ring =
            (((255 + (98 - 255) * ringSuccess).roundToInt()) shl 16) or
                (((178 + (227 - 178) * ringSuccess).roundToInt()) shl 8) or
                (94 + (166 - 94) * ringSuccess).roundToInt()
        val ringAlpha =
            min(
                1.0,
                0.32 +
                    0.1 * sin(2 * PI * driver.breathingTime / 3) +
                    0.45 * driver.proximity +
                    0.3 *
                        max(0.0, 1 - (driver.now - driver.lastTickTime) / 0.25) *
                        driver.proximity +
                    0.5 * Motion.smooth(0.0, 0.3, driver.ticketProgress),
            )
        canvas.drawOval(oval(layout, 1.0), paint(ring, ringAlpha, 6.0, 7.0))
        canvas.drawOval(oval(layout, 1.0), paint(ring, ringAlpha, 1.4))
        if (!driver.reduceMotion) {
            val rippleScale =
                1 +
                    0.55 *
                        Motion.easeOut(Motion.segment(driver.now - driver.lastTickTime, 0.0, 0.6))
            canvas.drawOval(
                oval(layout, rippleScale),
                paint(
                    0xFFB25E,
                    max(0.0, 1 - (driver.now - driver.lastTickTime) / 0.6) * 0.7 * driver.proximity,
                    1.2,
                ),
            )
        }
        if (flash > 0)
            radial(
                layout,
                1.1 + 0.9 * flash,
                intArrayOf(color(0xFFF4DC, flash), color(0xFFB25E, 0.55 * flash), 0),
                floatArrayOf(0f, 0.4f, 1f),
            )
        if (driver.tapAge >= 0 && !driver.reduceMotion)
            canvas.drawOval(
                oval(layout, 1 + 3.2 * Motion.easeOut(Motion.segment(driver.tapAge, 0.0, 0.8))),
                paint(0xFFE2B8, max(0.0, 1 - driver.tapAge / 0.8) * 0.85, 2.0, 2.0),
            )
        val riseProgress =
            if (driver.ticketProgress <= 0.36) 0.0 else (driver.ticketProgress - 0.36) / 0.64
        val exposedHeight =
            if (driver.ticketProgress <= 0.36) layout.printedExposure * driver.ticketProgress / 0.36
            else
                layout.printedExposure +
                    (layout.printedHeight + 6 - layout.printedExposure) *
                        Motion.smooth(0.0, 0.4, riseProgress)
        val paperExpansion = Motion.smooth(0.4, 1.0, riseProgress)
        val paperWidth =
            layout.printedWidth + (layout.ticketWidth - layout.printedWidth) * paperExpansion
        val paperHeight = paperWidth * layout.ticketHeight / layout.ticketWidth
        val paperX = layout.readerX + (layout.homeX - layout.readerX) * paperExpansion
        val printedY = layout.slotY - exposedHeight + layout.printedHeight / 2
        val paperY = printedY + (layout.ticketY - printedY) * paperExpansion
        val inSlot = Motion.smooth(0.0, 0.4, riseProgress) < 1
        if (driver.ticketProgress > 0.0005 && inSlot) {
            canvas.save()
            canvas.clipRect(rect(0.0, 0.0, layout.width, layout.slotY))
            paper(paperX, paperY, paperWidth, paperHeight, layout, paperExpansion, false)
            canvas.restore()
        }
        val spill = coilAlpha * Motion.smooth(0.5, 1.0, dockProgress) * 0.55
        radial(
            layout,
            1.45,
            intArrayOf(color(0xFFAA50, 0.9 * spill), color(0xFF8C32, 0.25 * spill), 0),
            floatArrayOf(0f, 0.5f, 1f),
        )
        /**
         * Projects four card corners through Z rotation, X tilt and a 1100 dp perspective distance.
         */
        fun project(centerX: Double, centerY: Double, scale: Double) {
            val corners =
                arrayOf(
                    -layout.cardWidth / 2 to -layout.cardHeight / 2,
                    layout.cardWidth / 2 to -layout.cardHeight / 2,
                    layout.cardWidth / 2 to layout.cardHeight / 2,
                    -layout.cardWidth / 2 to layout.cardHeight / 2,
                )
            corners.forEachIndexed { i, point ->
                val rotatedX =
                    (point.first * cos(rotationZ) - point.second * sin(rotationZ)) * scale
                val rotatedY =
                    (point.first * sin(rotationZ) + point.second * cos(rotationZ)) * scale
                val z = rotatedY * sin(rotationX)
                val perspectiveDivisor = 1 - z / 1100
                sourceCorners[2 * i] = (point.first + layout.cardWidth / 2).toFloat()
                sourceCorners[2 * i + 1] = (point.second + layout.cardHeight / 2).toFloat()
                projectedCorners[2 * i] = (centerX + rotatedX / perspectiveDivisor).toFloat()
                projectedCorners[2 * i + 1] =
                    (centerY + rotatedY * cos(rotationX) / perspectiveDivisor).toFloat()
            }
            matrix.setPolyToPoly(sourceCorners, 0, projectedCorners, 0, 4)
            canvas.concat(matrix)
        }
        canvas.save()
        project(
            x + 4 * (1 - dockProgress),
            y + (14 + 30 * driver.lift) * (1 - 0.75 * dockProgress),
            cardScale * 0.97,
        )
        canvas.drawRoundRect(
            rect(6.0, 6.0, layout.cardWidth - 12, layout.cardHeight - 8),
            18f,
            18f,
            paint(0, 0.55 - 0.15 * driver.lift, blur = 10 + 14 * driver.lift * (1 - dockProgress)),
        )
        canvas.restore()
        canvas.save()
        project(x, y, cardScale)
        canvas.drawRoundRect(
            rect(3.0, 3.0, layout.cardWidth - 6, layout.cardHeight - 6),
            (layout.cardWidth * 0.034).toFloat(),
            (layout.cardWidth * 0.034).toFloat(),
            paint(0x0C0D12, 0.42),
        )
        image(card, 0.0, 0.0, layout.cardWidth, layout.cardHeight)
        val glow = paint(0xffffff, coilAlpha * 0.9, blur = 5.0)
        glow.colorFilter = PorterDuffColorFilter(color(0xFF9A3C), PorterDuff.Mode.SRC_IN)
        glow.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        image(coil, 0.0, 0.0, layout.cardWidth, layout.cardHeight, glow)
        val sharp = paint(0xffffff, coilAlpha)
        sharp.colorFilter = PorterDuffColorFilter(color(0xFFD9A0), PorterDuff.Mode.SRC_IN)
        sharp.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        image(coil, 0.0, 0.0, layout.cardWidth, layout.cardHeight, sharp)
        cardFace?.let { image(it, 0.0, 0.0, layout.cardWidth, layout.cardHeight) }
        canvas.restore()
        canvas.drawRect(
            rect(0.0, 0.0, layout.width, layout.height),
            paint(0x050506, 0.62 * Motion.smooth(0.4, 0.85, driver.ticketProgress)),
        )
        if (driver.ticketProgress > 0.0005 && !inSlot)
            paper(paperX, paperY, paperWidth, paperHeight, layout, paperExpansion, true)
        roundel(24.0, layout.safeTop + 14, 20.0)
        text("RIVERLINE · NORTHBOUND", 52.0, layout.safeTop + 14, "Mono", 11.0, 0x9A9084, 1.6, 20.0)
        val restHeaderFade = Motion.smooth(0.4, 0.7, driver.ticketProgress)
        val successHeaderFade = Motion.smooth(0.62, 0.92, driver.ticketProgress)
        /**
         * Crossfades the station and successful-read captions with their original vertical offsets.
         */
        fun header(title: String, subtitle: String, parallaxY: Double, alpha: Double) {
            text(
                title,
                24.0,
                layout.safeTop + 46 + parallaxY,
                "ExtraBold",
                38.0,
                0xF3EEE6,
                -0.8,
                42.0,
                alpha = alpha,
            )
            text(
                subtitle,
                24.0,
                layout.safeTop + 92 + parallaxY,
                "Medium",
                15.0,
                0x9A9084,
                lineHeight = 20.0,
                alpha = alpha,
            )
        }
        header(
            "Harbor Gate",
            "Platform 2 · next train in 2 min",
            -6 * restHeaderFade,
            1 - restHeaderFade,
        )
        header(
            "You’re through",
            "Gate 3 open · next train in 2 min",
            6 * (1 - successHeaderFade),
            successHeaderFade,
        )
        if (driver.ticketProgress > 0.0005 && (!inSlot || !driver.reduceMotion)) {
            canvas.save()
            if (inSlot) canvas.clipRect(rect(0.0, 0.0, layout.width, layout.slotY))
            canvas.translate(
                (paperX - paperWidth / 2).toFloat(),
                (paperY - paperHeight / 2).toFloat(),
            )
            canvas.scale(
                (paperWidth / layout.ticketWidth).toFloat(),
                (paperWidth / layout.ticketWidth).toFloat(),
            )
            ticketFace?.let { image(it, 0.0, 0.0, layout.ticketWidth, 430.0) }
            canvas.save()
            canvas.clipRect(
                rect(layout.ticketWidth / 2 + 16, ticketRollY, layout.ticketWidth / 2 - 38, 30.0)
            )
            text(
                money(driver.balance),
                layout.ticketWidth / 2 + 16,
                ticketRollY + 4 - 16 * driver.roll,
                "SemiBold",
                19.0,
                0x17140F,
                lineHeight = 26.0,
                alpha = 1 - driver.roll,
            )
            text(
                money(driver.balanceAfterFare),
                layout.ticketWidth / 2 + 16,
                ticketRollY + 4 + 16 * (1 - driver.roll),
                "SemiBold",
                19.0,
                0x17140F,
                lineHeight = 26.0,
                alpha = driver.roll,
            )
            canvas.restore()
            if (!driver.reduceMotion) {
                val pulseProgress = driver.breathingTime % 1.5 / 1.5
                circle(
                    26.5,
                    348.7,
                    4.5 * (1 + 1.6 * Motion.easeOut(pulseProgress)),
                    0x1FAF6A,
                    0.5 * (1 - pulseProgress),
                )
            }
            canvas.restore()
        }
        val done = Motion.smooth(0.85, 1.0, driver.ticketProgress)
        if (done > 0) {
            val parallaxY = 10 * (1 - done)
            canvas.drawRoundRect(
                rect(layout.width / 2 - 70, layout.ticketY + 237 + parallaxY, 140.0, 48.0),
                24f,
                24f,
                paint(0xffffff, 0.1 * done),
            )
            canvas.drawRoundRect(
                rect(
                    layout.width / 2 - 70 + 0.5,
                    layout.ticketY + 237 + parallaxY + 0.5,
                    139.0,
                    47.0,
                ),
                24f,
                24f,
                paint(0xffffff, 0.16 * done, 1.0),
            )
            text(
                "Done",
                layout.width / 2 - width("Done", "SemiBold", 16.0) / 2,
                layout.ticketY + 237 + parallaxY,
                "SemiBold",
                16.0,
                0xF3EEE6,
                lineHeight = 48.0,
                alpha = done,
            )
        }
    }

    /** Adds a radial light in reader-ellipse space; scale is relative to its horizontal radius. */
    private fun radial(
        layout: TapRidePassGeometry,
        scale: Double,
        colors: IntArray,
        stops: FloatArray,
    ) {
        canvas.save()
        canvas.translate(layout.readerX.toFloat(), layout.readerEllipseY.toFloat())
        canvas.scale(1f, (layout.readerRadiusY / layout.readerRadiusX).toFloat())
        val radius = layout.readerRadiusX * scale
        val effectPaint = paint(0xffffff)
        effectPaint.shader =
            RadialGradient(0f, 0f, radius.toFloat(), colors, stops, Shader.TileMode.CLAMP)
        effectPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        canvas.drawCircle(0f, 0f, radius.toFloat(), effectPaint)
        canvas.restore()
    }

    /**
     * Draws thermal paper, side notches and perforation in dp; expansion controls its raised
     * shadow.
     */
    private fun paper(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        layout: TapRidePassGeometry,
        expansion: Double,
        shadow: Boolean,
    ) {
        canvas.save()
        canvas.translate((x - width / 2).toFloat(), (y - height / 2).toFloat())
        val radius = 18 * width / layout.ticketWidth
        val notchRadius = 12 * width / layout.ticketWidth
        if (shadow)
            canvas.drawRoundRect(
                rect(6.0, 18 * expansion, width - 12, height),
                radius.toFloat(),
                radius.toFloat(),
                paint(0, 0.55 * expansion, blur = 22.0),
            )
        path.reset()
        path.addRoundRect(
            rect(0.0, 0.0, width, height),
            radius.toFloat(),
            radius.toFloat(),
            Path.Direction.CW,
        )
        val cut = Path()
        cut.addCircle(0f, (height * 0.74).toFloat(), notchRadius.toFloat(), Path.Direction.CW)
        cut.addCircle(
            width.toFloat(),
            (height * 0.74).toFloat(),
            notchRadius.toFloat(),
            Path.Direction.CW,
        )
        path.op(cut, Path.Op.DIFFERENCE)
        val paper = paint(0xF4F0E8)
        if (Build.VERSION.SDK_INT >= 33) paper.shader = paperRuntime?.shader(width, height)
        canvas.drawPath(path, paper)
        canvas.save()
        canvas.clipPath(path)
        canvas.drawRect(rect(0.0, 0.0, width, 6 * width / layout.ticketWidth), paint(0xE8452C))
        canvas.restore()
        val perforationPaint = paint(0xC5BCAB, stroke = if (shadow) 1.3 else 1.0)
        perforationPaint.pathEffect =
            DashPathEffect(if (shadow) floatArrayOf(5f, 5f) else floatArrayOf(4f, 4f), 0f)
        canvas.drawLine(
            (22 * width / layout.ticketWidth).toFloat(),
            (height * 0.74).toFloat(),
            (width - 22 * width / layout.ticketWidth).toFloat(),
            (height * 0.74).toFloat(),
            perforationPaint,
        )
        canvas.restore()
    }
}
