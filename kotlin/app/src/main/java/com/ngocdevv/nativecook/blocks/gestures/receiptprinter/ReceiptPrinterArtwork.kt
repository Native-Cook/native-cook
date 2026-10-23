package com.ngocdevv.nativecook.blocks.gestures.receiptprinter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.ngocdevv.nativecook.R
import androidx.core.content.res.ResourcesCompat
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Draws the RN receipt-printer art in its measured 402 pt source coordinate space. */
internal class ReceiptPrinterArtwork(context: Context) {
    private companion object {
        const val SOURCE_WIDTH = Tokens.SOURCE.width
        const val PAPER_RASTER_SCALE = Tokens.PAPER.rasterScale
    }

    private val regular = requireNotNull(ResourcesCompat.getFont(context, R.font.receiptprinter_mono_regular))
    private val medium = requireNotNull(ResourcesCompat.getFont(context, R.font.receiptprinter_mono_medium))
    private val bold = requireNotNull(ResourcesCompat.getFont(context, R.font.receiptprinter_mono_bold))
    private val heavy = requireNotNull(ResourcesCompat.getFont(context, R.font.receiptprinter_mono_extrabold))
    private val logo = checkNotNull(BitmapFactory.decodeResource(context.resources, R.drawable.receiptprinter_logo))
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        pathEffect = DashPathEffect(floatArrayOf(3f, 2.4f), 0f)
    }
    private val dotted = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        pathEffect = DashPathEffect(floatArrayOf(1.5f, 2.5f), 0f)
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val paperGrain = createPaperGrain()
    private val paperBitmap = recordPaper()
    private val menuShadow = receiptPrinterShadow(
        Tokens.GRID.menu.w,
        Tokens.GRID.menu.h,
        Tokens.GRID.menu.radius,
        Tokens.GRID.menuShadowOffsetY,
        Tokens.GRID.menuShadowRadius,
    )
    private val paperShadow = receiptPrinterShadow(
        Tokens.SLOT.paperX1 - Tokens.SLOT.paperX0,
        Tokens.RECEIPT.length - Tokens.RECEIPT.edge.depth,
        0f,
        Tokens.PAPER.shadowOffsetY,
        Tokens.PAPER.shadowRadius,
    )
    private val cardShadow = receiptPrinterShadow(Tokens.GRID.cardW, Tokens.GRID.cardH, Tokens.GRID.radius, 6f, 14f)
    private val buttonShadow = receiptPrinterShadow(
        Tokens.BAR.w,
        Tokens.BAR.h,
        Tokens.BAR.h / 2f,
        Tokens.BAR.shadowOffsetY,
        Tokens.BAR.shadowRadius,
    )
    private val ledGlow = receiptPrinterLedGlow()

    fun draw(canvas: Canvas, frame: ReceiptPrinterFrame) {
        canvas.drawColor(Tokens.COLOR.bg)
        drawGrid(canvas, frame.gridOpacity, frame.pressedIndex, frame.cardLift, frame.holdIndex, frame.holdScale, frame.viewportWidth)
        drawMenu(canvas, frame.menuOpacity, frame.menuScale, frame.menuX, frame.menuY, frame.menuOriginX, frame.menuPressedRow, frame.viewportWidth)
        drawMorph(canvas, frame.morphProgress, frame.morphBack, frame.morphFrom, frame.islandTop, frame.islandCenterX)
        drawPaper(canvas, frame.paperVisible, frame.paperScroll, frame.paperRotation, frame.paperLift, frame.paperAlpha, frame.cutOffset, frame.paperTop)
        drawPaperOverlay(canvas, frame.islandProgress, frame.paperVisible, frame.paperTop, frame.developOpacity)
        drawIsland(canvas, frame.islandProgress, frame.islandTop, frame.islandCenterX, frame.ledOpacity, frame.ledGlowOpacity)
        drawIndicator(canvas, frame.indicatorOpacity, frame.indicatorY, frame.indicatorHeight, frame.viewportWidth)
        drawBar(canvas, frame.barOpacity, frame.barRise, frame.buttonPressedIndex, frame.buttonPressScale, frame.viewportWidth)
    }

    fun drawGrid(
        canvas: Canvas,
        opacity: Float,
        pressedIndex: Int,
        lift: Float,
        holdIndex: Int,
        holdScale: Float,
        viewportWidth: Float = SOURCE_WIDTH,
    ) {
        if (opacity <= 0f) return
        val layer = canvas.saveLayerAlpha(0f, 0f, viewportWidth, canvas.height.toFloat(), alpha(opacity))

        drawCapText(canvas, Tokens.COPY.title, Tokens.GRID.gutter, Tokens.GRID.titleCap, Tokens.TYPE.title, heavy, Tokens.COLOR.ink)
        drawCapText(canvas, Tokens.COPY.subtitle, Tokens.GRID.gutter, Tokens.GRID.subtitleCap, Tokens.TYPE.subtitle, regular, Tokens.COLOR.inkSoft)
        drawInfoIcon(canvas, Tokens.GRID.info.cx, Tokens.GRID.info.cy, Tokens.GRID.info.r)
        Tokens.PILLS.forEachIndexed { index, label ->
            val x = cardX(index)
            fill.color = Tokens.COLOR.card
            canvas.drawRoundRect(x, Tokens.GRID.pillTop, x + Tokens.GRID.cardW, Tokens.GRID.pillTop + Tokens.GRID.pillH, Tokens.GRID.radius, Tokens.GRID.radius, fill)
            drawTextCenteredInBox(canvas, label, x + Tokens.GRID.cardW / 2f, Tokens.GRID.pillTop, Tokens.GRID.pillH, Tokens.TYPE.pill, heavy, Tokens.COLOR.ink)
        }
        Tokens.CARDS.forEachIndexed { index, card ->
            val x = cardX(index % 3)
            val y = Tokens.GRID.rowTop + index / 3 * Tokens.GRID.rowPitch
            val saved = canvas.save()
            val scale = cardScale(index, pressedIndex, lift, holdIndex, holdScale)
            canvas.scale(scale, scale, x + Tokens.GRID.cardW / 2f, y + Tokens.GRID.cardH / 2f)
            drawCard(canvas, x, y, card, if (index == pressedIndex) lift else 0f)
            canvas.restoreToCount(saved)
        }
        canvas.restoreToCount(layer)
    }

    fun drawMenu(canvas: Canvas, opacity: Float, scale: Float, x: Float, y: Float, originX: Float, pressedRow: Int, viewportWidth: Float = SOURCE_WIDTH) {
        if (opacity <= 0f) return
        val menu = Tokens.GRID.menu
        val layer = canvas.saveLayerAlpha(0f, 0f, viewportWidth, canvas.height.toFloat(), alpha(opacity))
        val saved = canvas.save()
        canvas.scale(scale, scale, x + originX, y)
        drawShadow(canvas, menuShadow, x, y, Tokens.GRID.menuShadowOpacity)
        fill.color = Tokens.COLOR.menu
        canvas.drawRoundRect(x, y, x + menu.w, y + menu.h, menu.radius, menu.radius, fill)
        listOf(Tokens.COPY.long, Tokens.COPY.short).forEachIndexed { row, label ->
            val rowTop = y + Tokens.GRID.menuPaddingVertical + row * menu.rowH
            if (pressedRow == row) {
                fill.color = alphaColor(Tokens.COLOR.shadow, Tokens.GRID.menuPressedOpacity)
                canvas.drawRect(x, rowTop, x + menu.w, rowTop + menu.rowH, fill)
            }
            drawMenuIcon(canvas, x + menu.iconX, rowTop + menu.rowH / 2f, row)
            drawTextCenteredInBox(canvas, label, x + menu.textX, rowTop, menu.rowH, Tokens.TYPE.menu, regular, Tokens.COLOR.ink, alignLeft = true)
            if (row == 0) {
                fill.color = alphaColor(Tokens.COLOR.shadow, Tokens.GRID.menuRuleOpacity)
                canvas.drawRect(
                    x,
                    rowTop + menu.rowH - Tokens.GRID.menuRuleHeight,
                    x + menu.w,
                    rowTop + menu.rowH,
                    fill,
                )
            }
        }
        canvas.restoreToCount(saved)
        canvas.restoreToCount(layer)
    }

    fun drawPaper(
        canvas: Canvas,
        visible: Float,
        scroll: Float,
        rotation: Float,
        lift: Float,
        paperAlpha: Float,
        cutOffset: Float,
        paperTop: Float,
    ) {
        if (visible <= 0.01f) return
        val slot = Tokens.SLOT
        val paper = Tokens.RECEIPT
        val maxVisible = visible.coerceIn(0f, paper.length + paper.edge.depth)
        val layer = canvas.saveLayerAlpha(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), alpha(paperAlpha))
        val saved = canvas.save()
        canvas.clipRect(0f, paperTop, canvas.width.toFloat(), canvas.height.toFloat())
        if (maxVisible > 1f) {
            val shadowTop = paperTop + min(maxVisible, paper.length) - paper.length - scroll
            val shadowOpacity = Tokens.PAPER.shadowOpacity +
                (Tokens.PAPER.liftedShadowOpacity - Tokens.PAPER.shadowOpacity) * lift
            drawShadow(canvas, paperShadow, slot.paperX0, shadowTop, shadowOpacity)
            fill.color = Tokens.COLOR.paper
            canvas.drawRect(slot.paperX0, shadowTop, slot.paperX1, shadowTop + paper.length - paper.edge.depth, fill)
        }
        canvas.clipRect(slot.paperX0, paperTop, slot.paperX1, paperTop + maxVisible)
        canvas.translate(slot.paperX0, paperTop - scroll + cutOffset)
        canvas.rotate(rotation, (slot.paperX1 - slot.paperX0) / 2f, (paper.length + paper.edge.depth) / 2f)
        canvas.drawBitmap(paperBitmap, null, RectF(0f, 0f, slot.paperX1 - slot.paperX0, paper.length + paper.edge.depth), bitmapPaint)
        canvas.restoreToCount(saved)
        canvas.restoreToCount(layer)
    }

    fun drawPaperOverlay(canvas: Canvas, progress: Float, visible: Float, paperTop: Float, developOpacity: Float) {
        val opacity = progress.coerceIn(0f, 1f)
        if (opacity <= 0f) return
        val slot = Tokens.SLOT
        val shadeTop = paperTop + Tokens.ISLAND.tuck
        if (developOpacity > 0f) {
            fill.color = 0xFFFFFFFF.toInt()
            fill.shader = LinearGradient(slot.paperX0, shadeTop, slot.paperX0, shadeTop + Tokens.UPGRADE.developH, alphaColor(Tokens.COLOR.develop, developOpacity * Tokens.UPGRADE.developOpacity * opacity), 0x00FFFFFF, Shader.TileMode.CLAMP)
            canvas.drawRect(slot.paperX0, shadeTop, slot.paperX1, shadeTop + Tokens.UPGRADE.developH, fill)
            fill.shader = null
        }
        if (visible <= 1f) return
        fill.color = 0xFFFFFFFF.toInt()
        fill.shader = LinearGradient(
            slot.paperX0,
            shadeTop,
            slot.paperX0,
            shadeTop + slot.shadowH,
            alphaColor(Tokens.COLOR.shadow, slot.shadeOpacity * opacity),
            0x00000000,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(slot.paperX0, shadeTop, slot.paperX1, shadeTop + slot.shadowH, fill)
        fill.shader = null
    }

    fun drawIsland(
        canvas: Canvas,
        progress: Float,
        top: Float,
        centerX: Float,
        ledOpacity: Float,
        ledGlowOpacity: Float,
    ) {
        // Clamp only the closing undershoot: the opening spring keeps its soft overshoot.
        val open = max(0f, progress)
        val width = Tokens.ISLAND.w + (Tokens.ISLAND.openW - Tokens.ISLAND.w) * open
        val height = Tokens.ISLAND.h + (Tokens.ISLAND.openH - Tokens.ISLAND.h) * open
        fill.color = Tokens.ISLAND.color
        canvas.drawRoundRect(
            centerX - width / 2f,
            top,
            centerX + width / 2f,
            top + height,
            height / 2f,
            height / 2f,
            fill,
        )
        val ledReveal = ((open - 0.6f) / 0.4f).coerceIn(0f, 1f)
        if (ledReveal <= 0f) return
        val ledX = centerX + width / 2f - Tokens.ISLAND.ledInset
        val ledY = top + height / 2f
        drawLedGlow(canvas, ledX, ledY, ledGlowOpacity * ledReveal)
        fill.color = alphaColor(Tokens.COLOR.led, ledOpacity * ledReveal)
        canvas.drawCircle(ledX, ledY, Tokens.SLOT.led.r, fill)
    }

    fun drawIndicator(canvas: Canvas, opacity: Float, y: Float, height: Float, viewportWidth: Float) {
        if (opacity <= 0f || height <= 0f) return
        fill.color = alphaColor(Tokens.COLOR.indicator, opacity)
        val right = viewportWidth - Tokens.INDICATOR.right
        canvas.drawRoundRect(
            right - Tokens.INDICATOR.width,
            y,
            right,
            y + height,
            Tokens.INDICATOR.radius,
            Tokens.INDICATOR.radius,
            fill,
        )
    }

    fun drawBar(canvas: Canvas, opacity: Float, rise: Float, pressedIndex: Int, pressedScale: Float, viewportWidth: Float = SOURCE_WIDTH) {
        if (opacity <= 0f) return
        val bar = Tokens.BAR
        val labels = listOf(Tokens.COPY.save, Tokens.COPY.tear, Tokens.COPY.sign)
        val layer = canvas.saveLayerAlpha(0f, 0f, viewportWidth, canvas.height.toFloat(), alpha(opacity))
        val saved = canvas.save()
        canvas.translate(0f, rise)
        labels.forEachIndexed { index, label ->
            val x = bar.x + index * (bar.w + bar.gap)
            val pressed = index == pressedIndex
            val scale = if (pressed) pressedScale else 1f
            val button = canvas.save()
            canvas.scale(scale, scale, x + bar.w / 2f, bar.top + bar.h / 2f)
            if (index == 0) {
                fill.color = Tokens.COLOR.buttonDark
            } else {
                drawShadow(canvas, buttonShadow, x, bar.top, bar.shadowOpacity)
                fill.color = Tokens.COLOR.buttonLight
            }
            canvas.drawRoundRect(x, bar.top, x + bar.w, bar.top + bar.h, bar.h / 2f, bar.h / 2f, fill)
            drawBarIcon(canvas, x + bar.w / 2f, bar.top + bar.iconCap + Tokens.BAR.icon / 2f, index, if (index == 0) Tokens.COLOR.tagInk else Tokens.COLOR.buttonInk)
            drawCapTextCentered(canvas, label, x + bar.w / 2f, bar.top + bar.labelCap, Tokens.TYPE.button, bold, if (index == 0) Tokens.COLOR.tagInk else Tokens.COLOR.buttonInk)
            canvas.restoreToCount(button)
        }
        canvas.restoreToCount(saved)
        canvas.restoreToCount(layer)
    }

    fun drawMorph(canvas: Canvas, progress: Float, back: Boolean, from: Int, islandTop: Float, islandCenterX: Float) {
        if (progress <= 0f || progress >= 1f || from < 0) return
        val grid = Tokens.GRID
        val x0 = cardX(from % 3)
        val y0 = grid.rowTop + from / 3 * grid.rowPitch
        val centerX = x0 + grid.cardW / 2f + (islandCenterX - (x0 + grid.cardW / 2f)) * progress
        val targetY = islandTop + Tokens.ISLAND.openH / 2f
        val centerY = y0 + grid.cardH / 2f + (targetY - (y0 + grid.cardH / 2f)) * progress
        val opacity = if (back) min(min(1f, (1f - progress) / 0.15f), progress / 0.25f) else min(1f, (1f - progress) / 0.3f)
        fill.color = if (back) {
            mixColor(Tokens.COLOR.card, Tokens.ISLAND.color, (progress - 0.85f) / 0.15f, opacity)
        } else {
            mixColor(Tokens.COLOR.cardPressed, Tokens.ISLAND.color, (progress - 0.55f) / 0.4f, opacity)
        }
        val width = grid.cardW + (Tokens.ISLAND.openW - grid.cardW) * progress
        val height = grid.cardH + (Tokens.ISLAND.openH - grid.cardH) * progress
        val radius = grid.radius + (Tokens.ISLAND.openH / 2f - grid.radius) * progress
        canvas.drawRoundRect(
            centerX - width / 2f,
            centerY - height / 2f,
            centerX + width / 2f,
            centerY + height / 2f,
            radius,
            radius,
            fill,
        )
    }

    private fun drawCard(canvas: Canvas, x: Float, y: Float, card: Tokens.RunCard, lift: Float) {
        val grid = Tokens.GRID
        if (lift > 0f) {
            drawShadow(canvas, cardShadow, x, y, 0.12f * lift)
        }
        fill.color = if (lift > 0.01f) Tokens.COLOR.cardPressed else Tokens.COLOR.card
        canvas.drawRoundRect(x, y, x + grid.cardW, y + grid.cardH, grid.radius, grid.radius, fill)
        drawCapText(canvas, card.date[0], x + grid.dateInset, y + grid.dateCap, Tokens.TYPE.cardDate, medium, Tokens.COLOR.date)
        drawCapTextRight(canvas, card.date[1], x + grid.cardW - grid.dateInset, y + grid.dateCap, Tokens.TYPE.cardDate, medium, Tokens.COLOR.date)
        drawTreadmill(canvas, x + (grid.cardW - grid.iconW) / 2f, y + grid.iconTop)
        drawCapTextCentered(canvas, card.km, x + grid.cardW / 2f, y + grid.cardH - grid.kmCapFromBottom, Tokens.TYPE.cardKm, heavy, Tokens.COLOR.ink)
    }

    private fun drawTreadmill(canvas: Canvas, x: Float, y: Float) {
        // Exact RN SVG treadmill geometry, drawn without adding an asset dependency.
        val saved = canvas.save()
        canvas.translate(x, y)
        fill.color = Tokens.COLOR.icon
        canvas.drawOval(6f, 30.6f, 10.2f, 32.6f, fill)
        treadmillBar(canvas, 2.8f, 28.4f, 42.6f, 25.6f, 5.4f)
        treadmillBar(canvas, 34.6f, 21.8f, 43.4f, 21.2f, 5f)
        treadmillBar(canvas, 30.6f, 30.4f, 28.2f, 9.8f, 4f)
        treadmillBar(canvas, 7.6f, 9.8f, 39.2f, 7.4f, 4.2f)
        val console = Path().apply {
            moveTo(33.8f, 0.6f)
            lineTo(38.6f, 6.2f)
            lineTo(30.4f, 6.8f)
            close()
        }
        fill.color = Tokens.COLOR.paper
        canvas.drawPath(console, fill)
        stroke.color = Tokens.COLOR.icon
        stroke.strokeWidth = 1.3f
        stroke.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(console, stroke)
        canvas.restoreToCount(saved)
    }

    private fun treadmillBar(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, width: Float) {
        stroke.color = Tokens.COLOR.icon
        stroke.strokeWidth = width + 1.3f
        stroke.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(x1, y1, x2, y2, stroke)
        stroke.color = Tokens.COLOR.paper
        stroke.strokeWidth = max(0.1f, width - 1.3f)
        canvas.drawLine(x1, y1, x2, y2, stroke)
        stroke.strokeCap = Paint.Cap.BUTT
    }

    private fun drawInfoIcon(canvas: Canvas, x: Float, y: Float, radius: Float) {
        stroke.color = Tokens.COLOR.ink
        stroke.strokeWidth = 1.8f
        canvas.drawCircle(x, y, radius - 1.2f, stroke)
        fill.color = Tokens.COLOR.ink
        canvas.drawCircle(x, y - 5f, 1.35f, fill)
        canvas.drawRoundRect(x - 1.25f, y - 1.5f, x + 1.25f, y + 6.5f, 1.25f, 1.25f, fill)
    }

    private fun drawMenuIcon(canvas: Canvas, x: Float, y: Float, row: Int) {
        // Android has no matching system symbols; these are compact vector approximations of doc/text-align-left.
        stroke.color = Tokens.COLOR.ink
        stroke.strokeWidth = 1.4f
        stroke.strokeJoin = Paint.Join.ROUND
        if (row == 0) {
            canvas.drawRoundRect(x - 6f, y - 7f, x + 5f, y + 7f, 1.5f, 1.5f, stroke)
            canvas.drawLine(x + 1f, y - 7f, x + 5f, y - 3f, stroke)
            canvas.drawLine(x + 1f, y - 7f, x + 1f, y - 3f, stroke)
            canvas.drawLine(x + 1f, y - 3f, x + 5f, y - 3f, stroke)
            canvas.drawLine(x - 3f, y, x + 3f, y, stroke)
            canvas.drawLine(x - 3f, y + 3f, x + 3f, y + 3f, stroke)
        } else {
            repeat(4) { line ->
                val yy = y - 5f + line * 3.5f
                canvas.drawLine(x - 6f, yy, x + if (line == 3) 1f else 6f, yy, stroke)
            }
        }
    }

    private fun drawBarIcon(canvas: Canvas, x: Float, y: Float, index: Int, color: Int) {
        // Android Canvas vectors stand in for SF Symbols: tray-and-arrow-down, list rectangle, signature.
        stroke.color = color
        stroke.strokeWidth = 1.8f
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeJoin = Paint.Join.ROUND
        when (index) {
            0 -> {
                canvas.drawRoundRect(x - 7f, y + 1f, x + 7f, y + 6f, 1.5f, 1.5f, stroke)
                canvas.drawLine(x, y - 8f, x, y + 2f, stroke)
                canvas.drawLine(x - 3f, y - 1f, x, y + 2f, stroke)
                canvas.drawLine(x + 3f, y - 1f, x, y + 2f, stroke)
            }
            1 -> {
                canvas.drawRoundRect(x - 8f, y - 7f, x + 8f, y + 7f, 2f, 2f, stroke)
                repeat(3) { line ->
                    val yy = y - 3.5f + line * 3.5f
                    canvas.drawCircle(x - 4.5f, yy, 0.7f, stroke)
                    canvas.drawLine(x - 1.5f, yy, x + 5f, yy, stroke)
                }
            }
            else -> {
                val signature = Path().apply {
                    moveTo(x - 8f, y + 4f)
                    cubicTo(x - 4f, y - 8f, x - 2f, y + 5f, x + 1f, y)
                    cubicTo(x + 3f, y - 4f, x + 4f, y + 5f, x + 8f, y - 3f)
                }
                canvas.drawPath(signature, stroke)
                canvas.drawLine(x - 8f, y + 7f, x + 8f, y + 7f, stroke)
            }
        }
        stroke.strokeCap = Paint.Cap.BUTT
    }

    /** Software-rasterized once because hardware Canvas ignores MaskFilter on supported Android versions. */
    private fun drawShadow(canvas: Canvas, cached: ReceiptPrinterShadow, x: Float, y: Float, opacity: Float) {
        bitmapPaint.alpha = alpha(opacity)
        val destination = RectF(x - cached.inset, y - cached.inset, x + cached.width + cached.inset, y + cached.height + cached.offsetY + cached.inset)
        canvas.drawBitmap(cached.bitmap, null, destination, bitmapPaint)
        bitmapPaint.alpha = 255
    }

    private fun drawLedGlow(canvas: Canvas, x: Float, y: Float, opacity: Float) {
        bitmapPaint.alpha = alpha(opacity)
        val inset = ledGlow.inset
        canvas.drawBitmap(ledGlow.bitmap, null, RectF(x - Tokens.SLOT.led.glow - inset, y - Tokens.SLOT.led.glow - inset, x + Tokens.SLOT.led.glow + inset, y + Tokens.SLOT.led.glow + inset), bitmapPaint)
        bitmapPaint.alpha = 255
    }

    private fun receiptPrinterShadow(width: Float, height: Float, radius: Float, offsetY: Float, blur: Float): ReceiptPrinterShadow {
        val inset = ceil(blur * 3f)
        val bitmap = Bitmap.createBitmap(ceil(width + inset * 2f).toInt(), ceil(height + offsetY + inset * 2f).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF000000.toInt()
            maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(inset, inset + offsetY, inset + width, inset + offsetY + height, radius, radius, paint)
        return ReceiptPrinterShadow(bitmap, inset, width, height, offsetY)
    }

    private fun receiptPrinterLedGlow(): ReceiptPrinterLedGlow {
        val radius = Tokens.SLOT.led.glow
        val inset = 18f
        val bitmap = Bitmap.createBitmap(ceil((radius + inset) * 2f).toInt(), ceil((radius + inset) * 2f).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Tokens.COLOR.led
            setShadowLayer(6f, 0f, 0f, alphaColor(Tokens.COLOR.led, 0.9f))
        }
        canvas.drawCircle(radius + inset, radius + inset, radius, paint)
        return ReceiptPrinterLedGlow(bitmap, inset)
    }

    private fun recordPaper(): Bitmap {
        val receipt = Tokens.RECEIPT
        val width = (Tokens.SLOT.paperX1 - Tokens.SLOT.paperX0).roundToInt()
        val height = ceil(receipt.length + receipt.edge.depth).toInt()
        val bitmap = Bitmap.createBitmap((width * PAPER_RASTER_SCALE).roundToInt(), (height * PAPER_RASTER_SCALE).roundToInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(PAPER_RASTER_SCALE, PAPER_RASTER_SCALE)
        drawTornPaper(canvas, width.toFloat(), receipt.length, receipt.edge)
        drawReceiptLines(canvas)
        drawChart(canvas)
        canvas.drawBitmap(paperGrain, 0f, 0f, bitmapPaint)
        drawPaperCurl(canvas, width.toFloat(), receipt.length - receipt.edge.depth)
        drawReceiptLogo(canvas, width.toFloat())
        drawFastTag(canvas)
        receipt.rows.forEach { row -> drawReceiptRow(canvas, row) }
        receipt.paragraph.forEachIndexed { index, line ->
            drawReceiptRow(canvas, Tokens.ReceiptRow(receipt.paragraphCap + index * receipt.paragraphPitch, line, "left", receipt.left, Tokens.TYPE.small, color = Tokens.COLOR.inkSoft))
        }
        return bitmap
    }

    private fun drawTornPaper(canvas: Canvas, width: Float, length: Float, edge: Tokens.Edge) {
        val scallops = (width / edge.period).roundToInt()
        val period = width / scallops
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(width, 0f)
            lineTo(width, length - edge.depth)
            for (index in scallops - 1 downTo 0) {
                val x0 = (index + 1) * period
                val x1 = index * period
                quadTo(x0 - period / 2f, length + edge.depth * 0.6f, x1, length - edge.depth)
            }
            close()
        }
        fill.color = Tokens.COLOR.paper
        canvas.drawPath(path, fill)
    }

    private fun drawPaperCurl(canvas: Canvas, width: Float, length: Float) {
        fill.color = 0xFFFFFFFF.toInt()
        fill.shader = LinearGradient(0f, 0f, width, 0f, intArrayOf(Tokens.COLOR.paperShade, 0x00000000, 0x00000000, Tokens.COLOR.paperShade), floatArrayOf(0f, 0.06f, 0.94f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width, length, fill)
        fill.shader = null
    }

    private fun drawReceiptLines(canvas: Canvas) {
        val receipt = Tokens.RECEIPT
        dashed.color = Tokens.COLOR.dash
        dashed.strokeWidth = 1f
        receipt.dashes.forEach { y ->
            val localY = y - Tokens.SLOT.paperTop + 0.5f
            canvas.drawLine(localX(receipt.left), localY, localX(receipt.right), localY, dashed)
        }
        fill.color = Tokens.COLOR.zone
        receipt.zone.ys.forEach { y -> canvas.drawCircle(localX(receipt.zone.x), localY(y), receipt.zone.r, fill) }
    }

    private fun drawChart(canvas: Canvas) {
        val receipt = Tokens.RECEIPT
        val chart = receipt.chart
        val left = localX(receipt.left)
        val right = localX(receipt.right)
        dotted.color = Tokens.COLOR.gridFaint
        dotted.strokeWidth = 0.6f
        for (index in 0 until (chart.columns - 1) * chart.dotted) {
            val x = left + (right - left) * (index + 0.5f) / ((chart.columns - 1) * chart.dotted)
            canvas.drawLine(x, localY(chart.top), x, localY(chart.base), dotted)
        }
        repeat(3) { index ->
            val y = localY(chart.top) + (localY(chart.base) - localY(chart.top)) * (index + 1) / 4f
            canvas.drawLine(left, y, right, y, dotted)
        }
        stroke.color = Tokens.COLOR.grid
        stroke.strokeWidth = 0.8f
        repeat(chart.columns) { index ->
            val x = left + (right - left) * index / (chart.columns - 1)
            canvas.drawLine(x, localY(chart.top), x, localY(chart.bottom), stroke)
        }
        stroke.color = Tokens.COLOR.axis
        stroke.strokeWidth = chart.baseW
        canvas.drawLine(left, localY(chart.base), right, localY(chart.base), stroke)
    }

    private fun drawReceiptLogo(canvas: Canvas, paperWidth: Float) {
        val logo = Tokens.RECEIPT.logo
        val destination = RectF((paperWidth - logo.w) / 2f, localY(logo.top), (paperWidth + logo.w) / 2f, localY(logo.top) + logo.h)
        canvas.drawBitmap(this.logo, null, destination, fill)
    }

    private fun drawFastTag(canvas: Canvas) {
        val fast = Tokens.RECEIPT.fast
        fill.color = Tokens.COLOR.tag
        canvas.drawRoundRect(localX(fast.x), localY(fast.top), localX(fast.x) + fast.w, localY(fast.top) + fast.h, fast.radius, fast.radius, fill)
        drawTextCenteredInBox(canvas, "FAST", localX(fast.x) + fast.w / 2f, localY(fast.top), fast.h, fast.size, bold, Tokens.COLOR.tagInk)
    }

    private fun drawReceiptRow(canvas: Canvas, row: Tokens.ReceiptRow) {
        val typeface = if (row.bold) bold else regular
        val color = row.color ?: Tokens.COLOR.ink
        when (row.align) {
            "center" -> drawCapTextCentered(canvas, row.text, (Tokens.SLOT.paperX1 - Tokens.SLOT.paperX0) / 2f, localY(row.cap), row.size, typeface, color)
            "right" -> drawCapTextRight(canvas, row.text, localX(row.x ?: Tokens.RECEIPT.right), localY(row.cap), row.size, typeface, color)
            else -> drawCapText(canvas, row.text, localX(row.x ?: Tokens.RECEIPT.left), localY(row.cap), row.size, typeface, color)
        }
    }

    private fun drawCapText(canvas: Canvas, value: String, x: Float, cap: Float, size: Float, typeface: Typeface, color: Int) {
        configureText(size, typeface, color)
        canvas.drawText(value, x, cap + capBaseline(), text)
    }

    private fun drawCapTextCentered(canvas: Canvas, value: String, centerX: Float, cap: Float, size: Float, typeface: Typeface, color: Int) {
        configureText(size, typeface, color)
        canvas.drawText(value, centerX - text.measureText(value) / 2f, cap + capBaseline(), text)
    }

    private fun drawCapTextRight(canvas: Canvas, value: String, right: Float, cap: Float, size: Float, typeface: Typeface, color: Int) {
        configureText(size, typeface, color)
        canvas.drawText(value, right - text.measureText(value), cap + capBaseline(), text)
    }

    private fun drawTextCenteredInBox(
        canvas: Canvas,
        value: String,
        x: Float,
        top: Float,
        height: Float,
        size: Float,
        typeface: Typeface,
        color: Int,
        alignLeft: Boolean = false,
    ) {
        configureText(size, typeface, color)
        val bounds = Rect()
        text.getTextBounds(value, 0, value.length, bounds)
        val baseline = top + (height - bounds.height()) / 2f - bounds.top
        val drawX = if (alignLeft) x else x - text.measureText(value) / 2f
        canvas.drawText(value, drawX, baseline, text)
    }

    private fun configureText(size: Float, typeface: Typeface, color: Int) {
        text.typeface = typeface
        text.textSize = size
        text.color = color
    }

    private fun capBaseline(): Float {
        // JetBrains Mono caps are .73 em high; cap coordinates in the RN source are exact cap tops.
        return text.textSize * 0.73f
    }

    private fun cardScale(index: Int, pressedIndex: Int, lift: Float, holdIndex: Int, holdScale: Float): Float {
        val liftScale = if (index == pressedIndex) 1f + (Tokens.PRESS.cardLift - 1f) * lift else 1f
        val hold = if (index == holdIndex) holdScale else 1f
        return liftScale * hold
    }

    private fun cardX(column: Int) = Tokens.GRID.gutter + column * (Tokens.GRID.cardW + Tokens.GRID.gap)

    private fun localX(screenX: Float) = screenX - Tokens.SLOT.paperX0

    private fun localY(screenY: Float) = screenY - Tokens.SLOT.paperTop

    private fun alpha(value: Float) = (value.coerceIn(0f, 1f) * 255f).roundToInt()

    private fun alphaColor(color: Int, opacity: Float): Int {
        val sourceAlpha = (color ushr 24 and 0xFF) / 255f
        return (color and 0x00FFFFFF) or (alpha(sourceAlpha * opacity) shl 24)
    }

    private fun mixColor(start: Int, end: Int, progress: Float, opacity: Float): Int {
        val t = progress.coerceIn(0f, 1f)
        val red = gammaMix(start shr 16 and 0xFF, end shr 16 and 0xFF, t)
        val green = gammaMix(start shr 8 and 0xFF, end shr 8 and 0xFF, t)
        val blue = gammaMix(start and 0xFF, end and 0xFF, t)
        return (alpha(opacity) shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun gammaMix(start: Int, end: Int, progress: Float): Int {
        val gamma = 2.2
        val mixed = (start / 255.0).pow(gamma).let { startLinear ->
            val endLinear = (end / 255.0).pow(gamma)
            startLinear + (endLinear - startLinear) * progress
        }
        return (mixed.pow(1.0 / gamma) * 255.0).roundToInt()
    }

    private fun createPaperGrain(): Bitmap {
        val width = (Tokens.SLOT.paperX1 - Tokens.SLOT.paperX0).roundToInt()
        val height = (Tokens.RECEIPT.length - Tokens.RECEIPT.edge.depth).roundToInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // Android Canvas lacks Skia FractalNoise. This fixed two-octave 0.9-frequency grain is the documented platform difference.
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (fractalNoise(x * Tokens.PAPER.grainFrequency, y * Tokens.PAPER.grainFrequency) >
                    Tokens.PAPER.grainThreshold
                ) {
                    bitmap.setPixel(x, y, Tokens.COLOR.paperShade)
                }
            }
        }
        return bitmap
    }

    private fun fractalNoise(x: Float, y: Float): Float {
        val first = noise(x, y)
        val second = noise(x * 2f, y * 2f)
        return (first + second * 0.5f) / 1.5f
    }

    private fun noise(x: Float, y: Float): Float {
        val value = sin((x * 12.9898f + y * 78.233f).toDouble()) * 43758.5453
        return (value - kotlin.math.floor(value)).toFloat()
    }

    private data class ReceiptPrinterShadow(
        val bitmap: Bitmap,
        val inset: Float,
        val width: Float,
        val height: Float,
        val offsetY: Float,
    )

    private data class ReceiptPrinterLedGlow(
        val bitmap: Bitmap,
        val inset: Float,
    )
}
