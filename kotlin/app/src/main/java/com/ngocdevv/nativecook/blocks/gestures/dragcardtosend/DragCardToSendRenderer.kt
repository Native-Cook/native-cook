package com.ngocdevv.nativecook.blocks.gestures.dragcardtosend

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

internal class DragCardToSendRenderer(
    private val assets: DragCardToSendAssets,
) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private val portraitShaders = assets.portraits.mapValues { BitmapShader(it.value, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    private val headerShader = BitmapShader(assets.header, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val cardShader: Shader? = if (Build.VERSION.SDK_INT >= 33) createShader(CARD_SHADER) else null
    private val liquidShader: Shader? = if (Build.VERSION.SDK_INT >= 33) createShader(LIQUID_SHADER) else null

    data class LiquidLook(
        val island: FloatArray,
        val bulb: FloatArray,
        val drop: FloatArray,
        val direction: FloatArray,
        val neck: FloatArray,
        val lip: Float,
        val tail: Float,
        val shadow: Float,
        val labelX: Float,
        val labelY: Float,
        val labelScale: Float,
        val labelOpacity: Float,
        val bottom: Float,
    )

    fun renderStaticBackground(widthDp: Float, heightDp: Float, density: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(
            max(1, (widthDp * density).roundToInt()),
            max(1, (heightDp * density).roundToInt()),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(DragCardToSendTokens.Colors.Page)
        canvas.scale(density, density)
        val layout = dragCardToSendLayout(widthDp)
        drawText(canvas, "Wallet", DragCardToSendTokens.Layout.Edge, DragCardToSendTokens.Layout.HeaderTop, DragCardToSendTokens.Type.LargeTitle, 700, DragCardToSendTokens.Colors.Ink)
        drawAvatar(canvas, headerShader, widthDp - DragCardToSendTokens.Layout.Edge - DragCardToSendTokens.Layout.HeaderAvatar, DragCardToSendTokens.Layout.HeaderTop + 2f, DragCardToSendTokens.Layout.HeaderAvatar)
        drawText(canvas, "Send to", DragCardToSendTokens.Layout.Edge, layout.sectionTop, DragCardToSendTokens.Type.Section, 600, DragCardToSendTokens.Colors.Ink)
        drawText(canvas, "Activity", DragCardToSendTokens.Layout.Edge, layout.activityTop, DragCardToSendTokens.Type.Section, 600, DragCardToSendTokens.Colors.Ink)
        drawText(canvas, "See all", widthDp - DragCardToSendTokens.Layout.Edge, layout.activityTop + 2f, DragCardToSendTokens.Type.Hint, 500, DragCardToSendTokens.Colors.Accent, Paint.Align.RIGHT)
        return bitmap
    }

    fun draw(canvas: Canvas, engine: DragCardToSendEngine) {
        drawHint(canvas, engine)
        drawActivity(canvas, engine)
        drawCard(canvas, engine)
        val look = liquidLook(engine)
        drawLiquid(canvas, look, engine.layout.width)
        drawPeople(canvas, engine)
        drawDropAmount(canvas, engine, look)
        drawReceipt(canvas, engine)
    }

    private fun drawHint(canvas: Canvas, engine: DragCardToSendEngine) {
        val color = if ((engine.phase == DragCardToSendEngine.Phase.Pulling && engine.step >= 0) || engine.phase == DragCardToSendEngine.Phase.Falling) {
            DragCardToSendTokens.Colors.Ink
        } else {
            DragCardToSendTokens.Colors.Mute
        }
        drawText(
            canvas,
            engine.hint,
            engine.layout.width - DragCardToSendTokens.Layout.Edge,
            engine.layout.sectionTop + 2f,
            DragCardToSendTokens.Type.Hint,
            400,
            color,
            Paint.Align.RIGHT,
        )
    }

    private fun drawActivity(canvas: Canvas, engine: DragCardToSendEngine) {
        val rowHeight = DragCardToSendTokens.Layout.RowHeight
        val top = engine.layout.rowTop
        val sent = if (engine.lastPerson >= 0) {
            val person = DragCardToSendTokens.People[engine.lastPerson]
            DragCardToSendTokens.Transaction("sent", person.fullName, "Just now", -engine.lastCents, person = engine.lastPerson)
        } else {
            null
        }
        val elapsed = engine.transactionElapsedMs
        if (elapsed >= 0f && sent != null) {
            val rows = DragCardToSendTokens.RowsMotion
            val exit = cubicBezier(clamp01(elapsed / rows.ExitMs), EASE_OUT)
            val move = cubicBezier(clamp01((elapsed - rows.ExitMs) / rows.MoveMs), EASE_IN_OUT)
            val enter = cubicBezier(clamp01((elapsed - rows.ExitMs - rows.EnterAfterMs) / rows.EnterMs), EASE_OUT)
            val enterScale = mix(rows.EnterScale, 1f, enter)
            if (engine.transactionReturning) {
                drawTransaction(canvas, sent, top, engine.layout.width, 1f - exit, 1f)
                DragCardToSendTokens.Transactions.take(3).forEachIndexed { index, transaction ->
                    drawTransaction(canvas, transaction, top + rowHeight * (index + 1f - move), engine.layout.width, 1f, 1f)
                }
                drawTransaction(canvas, DragCardToSendTokens.Transactions[3], top + rowHeight * 3f, engine.layout.width, enter, enterScale)
            } else {
                DragCardToSendTokens.Transactions.forEachIndexed { index, transaction ->
                    val alpha = if (index == DragCardToSendTokens.Transactions.lastIndex) 1f - exit else 1f
                    drawTransaction(canvas, transaction, top + rowHeight * (index + move), engine.layout.width, alpha, 1f)
                }
                drawTransaction(canvas, sent, top, engine.layout.width, enter, enterScale)
            }
            return
        }
        if (engine.transactionProgress >= 0.999f && sent != null) {
            drawTransaction(canvas, sent, top, engine.layout.width, 1f, 1f)
            DragCardToSendTokens.Transactions.take(3).forEachIndexed { index, transaction ->
                drawTransaction(canvas, transaction, top + rowHeight * (index + 1), engine.layout.width, 1f, 1f)
            }
        } else {
            DragCardToSendTokens.Transactions.forEachIndexed { index, transaction ->
                drawTransaction(canvas, transaction, top + rowHeight * index, engine.layout.width, 1f, 1f)
            }
        }
    }

    private fun drawTransaction(
        canvas: Canvas,
        row: DragCardToSendTokens.Transaction,
        top: Float,
        width: Float,
        alpha: Float,
        scale: Float,
    ) {
        if (alpha <= 0.001f) return
        val centerX = width / 2f
        val centerY = top + DragCardToSendTokens.Layout.RowHeight / 2f
        canvas.save()
        canvas.scale(scale, scale, centerX, centerY)
        if (row.person != null) {
            drawAvatar(canvas, portraitShaders.getValue(DragCardToSendTokens.People[row.person].id), DragCardToSendTokens.Layout.Edge, top + 8f, DragCardToSendTokens.Layout.RowIcon, alpha)
        } else {
            drawMerchant(canvas, row.merchant ?: DragCardToSendTokens.Merchant.Coffee, DragCardToSendTokens.Layout.Edge, top + 8f, alpha)
        }
        val textX = DragCardToSendTokens.Layout.Edge + DragCardToSendTokens.Layout.RowIcon + 12f
        drawText(canvas, row.name, textX, top + 8f, DragCardToSendTokens.Type.Row, 600, withAlpha(DragCardToSendTokens.Colors.Ink, alpha))
        drawText(canvas, row.meta, textX, top + 30f, DragCardToSendTokens.Type.Meta, 400, withAlpha(DragCardToSendTokens.Colors.Mute, alpha))
        val positive = row.cents > 0
        val amount = (if (positive) "+" else "") + formatMoney(row.cents)
        drawText(
            canvas,
            amount,
            width - DragCardToSendTokens.Layout.Edge,
            top + 18f,
            DragCardToSendTokens.Type.Row,
            600,
            withAlpha(if (positive) DragCardToSendTokens.Colors.Accent else DragCardToSendTokens.Colors.Ink, alpha),
            Paint.Align.RIGHT,
            tabular = true,
        )
        canvas.restore()
    }

    private fun drawMerchant(canvas: Canvas, merchant: DragCardToSendTokens.Merchant, left: Float, top: Float, alpha: Float) {
        val tile: Int
        val ink: Int
        when (merchant) {
            DragCardToSendTokens.Merchant.Coffee -> {
                tile = DragCardToSendTokens.Colors.CoffeeTile
                ink = DragCardToSendTokens.Colors.CoffeeInk
            }
            DragCardToSendTokens.Merchant.Grocer -> {
                tile = DragCardToSendTokens.Colors.GrocerTile
                ink = DragCardToSendTokens.Colors.GrocerInk
            }
            DragCardToSendTokens.Merchant.Transit -> {
                tile = DragCardToSendTokens.Colors.TransitTile
                ink = DragCardToSendTokens.Colors.TransitInk
            }
        }
        fill.resetForFill(withAlpha(tile, alpha))
        canvas.drawRoundRect(left, top, left + 40f, top + 40f, 12f, 12f, fill)
        fill.color = withAlpha(ink, alpha)
        stroke.resetForStroke(withAlpha(ink, alpha), 1.7f)
        when (merchant) {
            DragCardToSendTokens.Merchant.Coffee -> {
                canvas.save()
                canvas.rotate(-32f, left + 20f, top + 20f)
                canvas.drawOval(left + 11.4f, top + 9.5f, left + 28.6f, top + 30.5f, fill)
                stroke.color = withAlpha(tile, alpha)
                val path = Path().apply {
                    moveTo(left + 20.6f, top + 10.4f)
                    cubicTo(left + 16.4f, top + 15.6f, left + 23.6f, top + 24.4f, left + 19.4f, top + 29.6f)
                }
                canvas.drawPath(path, stroke)
                canvas.restore()
            }
            DragCardToSendTokens.Merchant.Grocer -> {
                val path = Path().apply {
                    moveTo(left + 11.5f, top + 28.5f)
                    cubicTo(left + 10.5f, top + 18.5f, left + 16.5f, top + 10.5f, left + 29f, top + 11f)
                    cubicTo(left + 29.5f, top + 23.5f, left + 21.5f, top + 29.5f, left + 11.5f, top + 28.5f)
                    close()
                }
                canvas.drawPath(path, fill)
                stroke.color = withAlpha(tile, alpha)
                canvas.drawLine(left + 11.5f, top + 28.5f, left + 24.5f, top + 15.5f, stroke)
            }
            DragCardToSendTokens.Merchant.Transit -> {
                canvas.drawRoundRect(left + 12.5f, top + 11.5f, left + 27.5f, top + 27f, 4f, 4f, fill)
                val window = Paint(fill).apply { color = withAlpha(tile, alpha) }
                canvas.drawRoundRect(left + 14.8f, top + 13.8f, left + 25.2f, top + 19.4f, 1.6f, 1.6f, window)
                canvas.drawCircle(left + 16.3f, top + 23f, 1.25f, window)
                canvas.drawCircle(left + 23.7f, top + 23f, 1.25f, window)
            }
        }
    }

    private fun drawCard(canvas: Canvas, engine: DragCardToSendEngine) {
        val layout = engine.layout
        val left = DragCardToSendTokens.Layout.Edge
        val top = layout.cardTop + engine.cardY
        val scale = if (engine.reducedMotion) 1f else 1f - (1f - DragCardToSendTokens.Send.PressScale) * engine.press
        val centerX = left + layout.cardWidth / 2f
        val centerY = top + layout.cardHeight / 2f
        canvas.save()
        canvas.scale(scale, scale, centerX, centerY)
        val lift = clamp01(engine.cardY / DragCardToSendTokens.Pull.Tether)
        fill.resetForFill(withAlpha(DragCardToSendTokens.Colors.CardShadow, 0.2f + 0.06f * lift))
        fill.maskFilter = BlurMaskFilter(16f + 10f * lift, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(left + 12f, top + 14f + 12f * lift, left + layout.cardWidth - 12f, top + layout.cardHeight + 14f + 12f * lift, DragCardToSendTokens.Layout.CardRadius, DragCardToSendTokens.Layout.CardRadius, fill)
        fill.maskFilter = null
        fill.color = withAlpha(DragCardToSendTokens.Colors.CardShadow, 0.16f - 0.06f * lift)
        fill.maskFilter = BlurMaskFilter(4f + 2f * lift, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(left + 4f, top + 3f + 2f * lift, left + layout.cardWidth - 4f, top + layout.cardHeight + 3f + 2f * lift, DragCardToSendTokens.Layout.CardRadius, DragCardToSendTokens.Layout.CardRadius, fill)
        fill.maskFilter = null

        val shader = cardShader
        if (shader != null && Build.VERSION.SDK_INT >= 33 && updateCardShader(shader, left, top, layout.cardWidth, layout.cardHeight, engine.cardY, engine.press)) {
            fill.resetForFill(Color.WHITE)
            fill.shader = shader
            canvas.drawRoundRect(left, top, left + layout.cardWidth, top + layout.cardHeight, DragCardToSendTokens.Layout.CardRadius, DragCardToSendTokens.Layout.CardRadius, fill)
            fill.shader = null
        } else {
            drawCardFallback(canvas, left, top, layout.cardWidth, layout.cardHeight, engine.cardY, engine.press)
        }
        drawCardFace(canvas, engine, left, top)
        canvas.restore()
    }

    private fun drawCardFallback(canvas: Canvas, left: Float, top: Float, width: Float, height: Float, shift: Float, press: Float) {
        fill.resetForFill(Color.WHITE)
        fill.shader = LinearGradient(left, top, left + width, top + height, intArrayOf(0xFF223FC7.toInt(), 0xFF101E69.toInt(), 0xFF050A2F.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(left, top, left + width, top + height, DragCardToSendTokens.Layout.CardRadius, DragCardToSendTokens.Layout.CardRadius, fill)
        fill.shader = null
        val sheenX = left + width * (0.18f - shift / max(1f, height) * 0.5f)
        fill.shader = LinearGradient(sheenX, top, sheenX + width * 0.55f, top + height, intArrayOf(Color.TRANSPARENT, withAlpha(Color.WHITE, 0.12f * (1f - press * 0.35f)), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(left, top, left + width, top + height, DragCardToSendTokens.Layout.CardRadius, DragCardToSendTokens.Layout.CardRadius, fill)
        fill.shader = null
        stroke.resetForStroke(withAlpha(0xFF8EA4FF.toInt(), 0.11f), 0.55f)
        for (i in 0 until 18) {
            val radius = 52f + i * 7f
            canvas.drawArc(left + width - radius * 1.55f, top + height - radius * 1.4f, left + width + radius * 0.45f, top + height + radius * 0.6f, 195f, 125f, false, stroke)
        }
        drawChip(canvas, left + DragCardToSendTokens.Layout.ChipX, top + DragCardToSendTokens.Layout.ChipY)
    }

    private fun drawChip(canvas: Canvas, left: Float, top: Float) {
        fill.resetForFill(Color.WHITE)
        fill.shader = LinearGradient(left, top, left + 46f, top + 35f, 0xFFE0C392.toInt(), 0xFF76603F.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(left, top, left + 46f, top + 35f, 6f, 6f, fill)
        fill.shader = null
        stroke.resetForStroke(withAlpha(0xFF49391F.toInt(), 0.68f), 0.7f)
        canvas.drawLine(left + 13f, top + 2f, left + 13f, top + 33f, stroke)
        canvas.drawLine(left + 33f, top + 2f, left + 33f, top + 33f, stroke)
        canvas.drawLine(left, top + 12f, left + 13f, top + 12f, stroke)
        canvas.drawLine(left, top + 23f, left + 13f, top + 23f, stroke)
        canvas.drawLine(left + 33f, top + 12f, left + 46f, top + 12f, stroke)
        canvas.drawLine(left + 33f, top + 23f, left + 46f, top + 23f, stroke)
        canvas.drawRoundRect(left + 17f, top + 8f, left + 29f, top + 27f, 3.2f, 3.2f, stroke)
    }

    private fun drawCardFace(canvas: Canvas, engine: DragCardToSendEngine, left: Float, top: Float) {
        val pad = DragCardToSendTokens.Layout.CardPad
        stroke.resetForStroke(Color.WHITE, 2.5f)
        stroke.strokeCap = Paint.Cap.ROUND
        val logoTop = top + pad + 1f
        canvas.drawArc(left + pad, logoTop, left + pad + 12f, logoTop + 12f, 40f, 280f, false, stroke)
        canvas.drawCircle(left + pad + 22f, logoTop + 6f, 6f, stroke)
        canvas.drawLine(left + pad + 32f, logoTop, left + pad + 38f, logoTop + 12f, stroke)
        canvas.drawLine(left + pad + 38f, logoTop + 12f, left + pad + 44f, logoTop, stroke)
        val e = Path().apply {
            moveTo(left + pad + 48f, logoTop + 6f)
            lineTo(left + pad + 60f, logoTop + 6f)
            arcTo(left + pad + 48f, logoTop, left + pad + 60f, logoTop + 12f, -90f, 275f, false)
        }
        canvas.drawPath(e, stroke)
        stroke.strokeWidth = 1.8f
        repeat(3) { i ->
            val r = 6f + i * 5f
            canvas.drawArc(left + engine.layout.cardWidth - pad - 17f - r, top + pad - 4f + 12f - r, left + engine.layout.cardWidth - pad - 17f + r, top + pad - 4f + 12f + r, -38f, 76f, false, stroke)
        }
        val amountTop = top + engine.layout.cardHeight - 55f
        drawText(canvas, "Balance", left + pad, amountTop - 18f, DragCardToSendTokens.Type.CardLabel, 500, withAlpha(Color.WHITE, 0.66f))
        drawBalance(canvas, engine, left + pad, amountTop)
        drawText(canvas, "•••• ${DragCardToSendTokens.CardLast4}", left + engine.layout.cardWidth - pad, amountTop + 14f, DragCardToSendTokens.Type.Pan, 600, withAlpha(Color.WHITE, 0.66f), Paint.Align.RIGHT, tabular = true)
    }

    private fun drawBalance(canvas: Canvas, engine: DragCardToSendEngine, left: Float, top: Float) {
        val start = formatMoney(DragCardToSendTokens.StartBalance)
        if (engine.lastCents <= 0 || engine.transactionProgress <= 0.001f) {
            drawText(canvas, start, left, top, DragCardToSendTokens.Type.Balance, 700, Color.WHITE, tabular = true)
            return
        }
        val paid = formatMoney(DragCardToSendTokens.StartBalance - engine.lastCents)
        if (engine.transactionProgress >= 0.999f) {
            drawText(canvas, paid, left, top, DragCardToSendTokens.Type.Balance, 700, Color.WHITE, tabular = true)
            return
        }
        val oldText = if (engine.transactionReturning) paid else start
        val newText = if (engine.transactionReturning) start else paid
        val progress = if (engine.transactionReturning) 1f - engine.transactionProgress else engine.transactionProgress
        prepareText(DragCardToSendTokens.Type.Balance, 700, Color.WHITE, Paint.Align.LEFT, true)
        var x = left
        var digitIndex = 0
        oldText.indices.forEach { index ->
            val oldChar = oldText[index].toString()
            val newChar = newText.getOrElse(index) { oldText[index] }.toString()
            val width = text.measureText(oldChar)
            if (oldChar == newChar) {
                drawText(canvas, oldChar, x, top, DragCardToSendTokens.Type.Balance, 700, Color.WHITE, tabular = true)
            } else {
                val delayed = clamp01((progress * DragCardToSendTokens.Send.RollMs - digitIndex * 40f) / (DragCardToSendTokens.Send.RollMs - digitIndex * 40f).coerceAtLeast(80f))
                val k = cubicBezier(delayed, EASE_OUT)
                drawText(canvas, oldChar, x, top - 10.8f * k, DragCardToSendTokens.Type.Balance, 700, withAlpha(Color.WHITE, 1f - k), tabular = true)
                drawText(canvas, newChar, x, top + 10.8f * (1f - k), DragCardToSendTokens.Type.Balance, 700, withAlpha(Color.WHITE, k), tabular = true)
                digitIndex += 1
            }
            x += width
        }
    }

    private fun drawLiquid(canvas: Canvas, look: LiquidLook, width: Float) {
        val shader = liquidShader
        if (shader != null && Build.VERSION.SDK_INT >= 33 && updateLiquidShader(shader, look)) {
            fill.resetForFill(Color.WHITE)
            fill.shader = shader
            canvas.drawRect(0f, 0f, width, look.bottom, fill)
            fill.shader = null
            return
        }
        drawLiquidFallback(canvas, look)
    }

    private fun drawLiquidFallback(canvas: Canvas, look: LiquidLook) {
        val island = look.island
        val bulb = look.bulb
        val drop = look.drop
        if (look.shadow > 0f) {
            fill.resetForFill(withAlpha(Color.BLACK, 0.14f * look.shadow))
            fill.maskFilter = BlurMaskFilter(if (drop[2] > 0f) 12f else 7f, BlurMaskFilter.Blur.NORMAL)
            if (bulb[2] > 0f) canvas.drawCircle(bulb[0] + 1.5f, bulb[1] + 5f, bulb[2], fill)
            if (drop[2] > 0f) canvas.drawCircle(drop[0] + 3f, drop[1] + 12f, drop[2], fill)
            fill.maskFilter = null
        }
        fill.resetForFill(Color.BLACK)
        canvas.drawRoundRect(island[0] - island[2], island[1] - island[3], island[0] + island[2], island[1] + island[3], island[3], island[3], fill)
        if (look.neck[4] > 0f) {
            stroke.resetForStroke(Color.BLACK, look.neck[4] * 2f)
            stroke.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(look.neck[0], look.neck[1], look.neck[2], look.neck[3], stroke)
        }
        if (look.lip > 0f) canvas.drawCircle(island[0], island[1] + island[3] + look.lip * 0.2f, look.lip, fill)
        if (bulb[2] > 0f) {
            canvas.drawCircle(bulb[0], bulb[1], bulb[2], fill)
            drawLiquidHighlight(canvas, bulb[0], bulb[1], bulb[2])
        }
        if (drop[2] > 0f) {
            canvas.save()
            val angle = Math.toDegrees(kotlin.math.atan2(look.direction[1].toDouble(), look.direction[0].toDouble())).toFloat()
            canvas.rotate(angle - 90f, drop[0], drop[1])
            canvas.scale(1f, drop[3], drop[0], drop[1])
            canvas.drawCircle(drop[0], drop[1], drop[2], fill)
            drawLiquidHighlight(canvas, drop[0], drop[1], drop[2])
            canvas.restore()
        }
    }

    private fun drawLiquidHighlight(canvas: Canvas, x: Float, y: Float, radius: Float) {
        fill.resetForFill(Color.WHITE)
        fill.shader = RadialGradient(x - radius * 0.36f, y - radius * 0.38f, max(2f, radius * 0.75f), intArrayOf(withAlpha(Color.WHITE, 0.38f), withAlpha(0xFF8490A8.toInt(), 0.12f), Color.TRANSPARENT), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, radius, fill)
        fill.shader = null
    }

    private fun drawPeople(canvas: Canvas, engine: DragCardToSendEngine) {
        val layout = engine.layout
        val diameter = (DragCardToSendTokens.Layout.Avatar / 2f + DragCardToSendTokens.Layout.RingGap + DragCardToSendTokens.Layout.RingStroke) * 2f
        val rippleScale = if (engine.reducedMotion) 1f else 1f + DragCardToSendTokens.Send.RippleScale * engine.ripple
        val ringScale = (if (engine.reducedMotion) 1f else 1f - 0.08f * (1f - engine.ringOn)) * rippleScale
        stroke.resetForStroke(withAlpha(DragCardToSendTokens.Colors.Accent, engine.ringOn * (1f - engine.ripple)), DragCardToSendTokens.Layout.RingStroke)
        canvas.drawCircle(engine.ringX, layout.avatarY, diameter / 2f * ringScale, stroke)
        DragCardToSendTokens.People.forEachIndexed { index, person ->
            val catch = if (engine.targetPerson == index) engine.catchAmount else 0f
            canvas.save()
            canvas.scale(1f + 0.6f * DragCardToSendTokens.Send.CatchSquash * catch, 1f - DragCardToSendTokens.Send.CatchSquash * catch, layout.avatarX[index], layout.avatarY)
            drawAvatar(canvas, portraitShaders.getValue(person.id), layout.avatarX[index] - 26f, layout.avatarTop, DragCardToSendTokens.Layout.Avatar)
            canvas.restore()
            val near = max(0f, 1f - abs(engine.ringX - layout.avatarX[index]) / layout.pitch)
            val lit = engine.ringOn * (1f - engine.ripple) * near
            val color = blend(DragCardToSendTokens.Colors.Mute, DragCardToSendTokens.Colors.Ink, lit)
            drawText(canvas, person.name, layout.avatarX[index], layout.avatarTop + DragCardToSendTokens.Layout.Avatar + DragCardToSendTokens.Layout.NameGap, DragCardToSendTokens.Type.Name, 500, color, Paint.Align.CENTER)
        }
    }

    private fun drawDropAmount(canvas: Canvas, engine: DragCardToSendEngine, look: LiquidLook) {
        if (engine.step < 0 || look.labelOpacity <= 0.001f) return
        canvas.save()
        canvas.scale(look.labelScale, look.labelScale, look.labelX, look.labelY)
        drawText(canvas, "\$${DragCardToSendTokens.Amounts[engine.step]}", look.labelX, look.labelY - DragCardToSendTokens.Type.DropAmount * 0.55f, DragCardToSendTokens.Type.DropAmount, 700, withAlpha(Color.WHITE, look.labelOpacity), Paint.Align.CENTER, tabular = true)
        canvas.restore()
    }

    private fun drawReceipt(canvas: Canvas, engine: DragCardToSendEngine) {
        if (engine.receipt <= DragCardToSendTokens.Send.ReceiptContentFrom || engine.lastPerson < 0) return
        val token = DragCardToSendTokens.Receipt
        val from = if (engine.reducedMotion) DragCardToSendTokens.Send.ReceiptContentFromReduced else DragCardToSendTokens.Send.ReceiptContentFrom
        val alpha = clamp01((engine.receipt - from) / (1f - from))
        val islandHalf = DragCardToSendTokens.Island.Width / 2f
        val fullHalf = engine.layout.cardWidth / 2f
        val width = mix(islandHalf, fullHalf, engine.receipt)
        val lag = DragCardToSendTokens.Send.ReceiptContentLag * clamp01((1f - engine.receipt) / (1f - from))
        val scale = if (engine.reducedMotion) 1f else 1f - (1f - (width / fullHalf) * (1f - lag))
        val centerX = engine.layout.width / 2f
        val centerY = engine.layout.receiptCenterY
        canvas.save()
        canvas.scale(scale, scale, centerX, centerY)
        val person = DragCardToSendTokens.People[engine.lastPerson]
        val left = DragCardToSendTokens.Layout.Edge + token.Pad
        val top = token.Top + token.Pad
        drawAvatar(canvas, portraitShaders.getValue(person.id), left, top, token.Avatar, alpha)
        fill.resetForFill(withAlpha(DragCardToSendTokens.Colors.Accent, alpha))
        canvas.drawCircle(left + token.Avatar - 3f, top + token.Avatar - 3f, token.Badge / 2f, fill)
        stroke.resetForStroke(withAlpha(Color.WHITE, alpha), 2.6f)
        stroke.strokeCap = Paint.Cap.ROUND
        val check = Path().apply {
            moveTo(left + token.Avatar - 7.8f, top + token.Avatar - 3.6f)
            lineTo(left + token.Avatar - 4.4f, top + token.Avatar - 0.4f)
            lineTo(left + token.Avatar + 2f, top + token.Avatar - 7.2f)
        }
        canvas.drawPath(check, stroke)
        val textX = left + token.Avatar + token.Gap
        drawText(canvas, "To ${person.name}", textX, top + 2f, DragCardToSendTokens.Type.ReceiptMeta, 500, withAlpha(Color.WHITE, 0.64f * alpha))
        drawText(canvas, formatMoney(engine.lastCents, engine.lastCents % 100 != 0), textX, top + 18f, DragCardToSendTokens.Type.ReceiptAmount, 700, withAlpha(Color.WHITE, alpha), tabular = true)
        val undoLeft = engine.layout.width - DragCardToSendTokens.Layout.Edge - token.Pad - token.UndoWidth
        val undoTop = token.Top + (token.Height - token.UndoHeight) / 2f
        fill.resetForFill(withAlpha(Color.WHITE, 0.16f * alpha))
        canvas.drawRoundRect(undoLeft, undoTop, undoLeft + token.UndoWidth, undoTop + token.UndoHeight, token.UndoHeight / 2f, token.UndoHeight / 2f, fill)
        val ringX = undoLeft + 16f
        val ringY = undoTop + token.UndoHeight / 2f
        stroke.resetForStroke(withAlpha(Color.WHITE, 0.22f * alpha), token.RingStroke)
        canvas.drawCircle(ringX, ringY, (token.Ring - token.RingStroke) / 2f, stroke)
        stroke.color = withAlpha(Color.WHITE, alpha)
        stroke.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(ringX - 7f, ringY - 7f, ringX + 7f, ringY + 7f, -90f, 360f * engine.undoProgress, false, stroke)
        drawText(canvas, "Undo", undoLeft + 31f, undoTop + 8f, DragCardToSendTokens.Type.Undo, 600, withAlpha(Color.WHITE, alpha))
        canvas.restore()
    }

    private fun liquidLook(engine: DragCardToSendEngine): LiquidLook {
        val layout = engine.layout
        val k = max(0f, engine.receipt)
        val island = floatArrayOf(
            layout.islandCenterX,
            mix(layout.islandCenterY, layout.receiptCenterY, k),
            mix(DragCardToSendTokens.Island.Width / 2f, layout.cardWidth / 2f, k),
            mix(DragCardToSendTokens.Island.Height / 2f, DragCardToSendTokens.Receipt.Height / 2f, k),
        )
        val lipY = layout.islandCenterY + DragCardToSendTokens.Island.Height / 2f
        var bulb = floatArrayOf(0f, 0f, 0f)
        var drop = floatArrayOf(0f, 0f, 0f, 1f)
        var direction = floatArrayOf(0f, 1f)
        var neck = floatArrayOf(0f, 0f, 0f, 0f, 0f)
        var lip = 0f
        var tail = 0f
        var shadow = 0f
        var labelX = layout.islandCenterX
        var labelY = lipY
        var labelScale = 0.6f
        var labelOpacity = 0f
        var bottom = island[1] + island[3]
        if (engine.flightProgress < 0f) {
            if (engine.dropRadius > 0.5f) {
                val y = lipY + DragCardToSendTokens.Drop.Hang * engine.dropRadius
                bulb = floatArrayOf(layout.islandCenterX, y, engine.dropRadius)
                shadow = clamp01(engine.dropRadius / 20f)
                labelX = layout.islandCenterX
                labelY = y + 0.2f * engine.dropRadius
                labelScale = labelScale(engine.dropRadius)
                labelOpacity = clamp01((engine.dropRadius - 12f) / 6f)
                bottom = y + engine.dropRadius
            }
        } else {
            val f = engine.flight
            val t = clamp01(engine.flightProgress)
            val u = cubicBezier(clamp01(t / DragCardToSendTokens.Flight.Travel), EASE_IN_OUT)
            val point = pathAt(f, u)
            val u2 = cubicBezier(clamp01((t + 0.01f) / DragCardToSendTokens.Flight.Travel), EASE_IN_OUT)
            val next = pathAt(f, u2)
            val vx = next.first - point.first
            val vy = next.second - point.second
            val speedLength = hypot(vx.toDouble(), vy.toDouble()).toFloat()
            val total = hypot((f.toX - f.fromX).toDouble(), (f.toY - f.fromY).toDouble()).toFloat()
            val speed = clamp01(speedLength / max(1f, total * 0.01f / DragCardToSendTokens.Flight.Travel * 2.2f))
            if (speedLength > 0.0001f) direction = floatArrayOf(vx / speedLength, vy / speedLength)
            val distance = hypot((f.toX - point.first).toDouble(), (f.toY - point.second).toDouble()).toFloat()
            val meet = DragCardToSendTokens.Layout.Avatar / 2f + f.radius * 0.6f
            val n = clamp01((meet - distance) / (meet - DragCardToSendTokens.Layout.Avatar * 0.15f))
            val near = n * n * (3f - 2f * n)
            val absorb = max(near, cubicBezier(clamp01((t - DragCardToSendTokens.Flight.AbsorbFrom) / (1f - DragCardToSendTokens.Flight.AbsorbFrom)), EASE_OUT))
            val radius = f.radius * (1f - 0.74f * absorb)
            drop = floatArrayOf(point.first, point.second, radius, 1f + DragCardToSendTokens.Flight.Stretch * speed * (1f - absorb))
            val travelled = hypot((point.first - f.fromX).toDouble(), (point.second - f.fromY).toDouble()).toFloat()
            val snapDistance = (1f - DragCardToSendTokens.Drop.Hang) * f.radius + DragCardToSendTokens.Drop.Blend * 1.6f
            val snap = clamp01(travelled / snapDistance)
            if (snap < 1f) {
                val thin = (1f - snap).pow(2)
                neck = floatArrayOf(layout.islandCenterX, lipY - 2f, point.first - direction[0] * radius * 0.5f, point.second - direction[1] * radius * 0.5f, max(0.6f, f.radius * 0.42f * thin))
            } else {
                val timeAfterSnap = max(0f, (t - snapTime(f)) * DragCardToSendTokens.Flight.DurationMs)
                val wave = 2f * PI.toFloat() * DragCardToSendTokens.Flight.RecoilHz * timeAfterSnap / 1_000f
                lip = max(0f, 9f * exp(-timeAfterSnap / 150f) * cos(wave))
                tail = exp(-timeAfterSnap / 90f)
            }
            shadow = 1f - absorb
            labelX = point.first
            labelY = point.second + 0.2f * f.radius
            labelScale = labelScale(f.radius)
            labelOpacity = 1f - clamp01(travelled / snapDistance)
            bottom = max(bottom, point.second + radius * drop[3])
        }
        return LiquidLook(island, bulb, drop, direction, neck, lip, tail, shadow, labelX, labelY, labelScale, labelOpacity, bottom + 24f)
    }

    private fun pathAt(flight: DragCardToSendEngine.Flight, progress: Float): Pair<Float, Float> {
        val controlX = mix(flight.fromX, flight.toX, 0.12f)
        val controlY = mix(flight.fromY, flight.toY, 0.55f)
        val a = (1f - progress).pow(2)
        val b = 2f * (1f - progress) * progress
        val c = progress * progress
        return (a * flight.fromX + b * controlX + c * flight.toX) to (a * flight.fromY + b * controlY + c * flight.toY)
    }

    private fun snapTime(flight: DragCardToSendEngine.Flight): Float {
        val needed = (1f - DragCardToSendTokens.Drop.Hang) * flight.radius + DragCardToSendTokens.Drop.Blend * 1.6f
        var lo = 0f
        var hi = 1f
        repeat(16) {
            val middle = (lo + hi) / 2f
            val progress = cubicBezier(clamp01(middle / DragCardToSendTokens.Flight.Travel), EASE_IN_OUT)
            val point = pathAt(flight, progress)
            if (hypot((point.first - flight.fromX).toDouble(), (point.second - flight.fromY).toDouble()) < needed) lo = middle else hi = middle
        }
        return hi
    }

    private fun labelScale(radius: Float): Float {
        return 0.4f + 0.52f * clamp01(radius / radiusFor(DragCardToSendTokens.Amounts.last()))
    }

    private fun drawAvatar(canvas: Canvas, shader: BitmapShader, left: Float, top: Float, size: Float, alpha: Float = 1f) {
        val bitmapWidth = if (shader === headerShader) assets.header.width else 168
        val bitmapHeight = if (shader === headerShader) assets.header.height else 168
        matrix.reset()
        matrix.setScale(size / bitmapWidth, size / bitmapHeight)
        matrix.postTranslate(left, top)
        shader.setLocalMatrix(matrix)
        image.shader = shader
        image.alpha = (255f * alpha).roundToInt().coerceIn(0, 255)
        canvas.drawCircle(left + size / 2f, top + size / 2f, size / 2f, image)
        image.shader = null
        image.alpha = 255
        stroke.resetForStroke(withAlpha(DragCardToSendTokens.Colors.Ink, 0.08f * alpha), 0.5f)
        canvas.drawCircle(left + size / 2f, top + size / 2f, size / 2f - 0.25f, stroke)
    }

    private fun drawText(
        canvas: Canvas,
        value: String,
        x: Float,
        top: Float,
        size: Float,
        weight: Int,
        color: Int,
        align: Paint.Align = Paint.Align.LEFT,
        tabular: Boolean = false,
    ) {
        prepareText(size, weight, color, align, tabular)
        canvas.drawText(value, x, top - text.fontMetrics.ascent, text)
    }

    private fun prepareText(size: Float, weight: Int, color: Int, align: Paint.Align, tabular: Boolean) {
        text.reset()
        text.isAntiAlias = true
        text.isSubpixelText = true
        text.textSize = size
        text.color = color
        text.textAlign = align
        text.typeface = if (Build.VERSION.SDK_INT >= 28) {
            Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), weight, false)
        } else {
            Typeface.create("sans-serif", if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
        }
        text.fontFeatureSettings = if (tabular) "tnum" else null
    }

    @RequiresApi(33)
    private fun createShader(source: String): Shader? = runCatching { RuntimeShader(source) }.getOrNull()

    @RequiresApi(33)
    private fun updateCardShader(shader: Shader, left: Float, top: Float, width: Float, height: Float, shift: Float, press: Float): Boolean {
        val runtime = shader as? RuntimeShader ?: return false
        runtime.setFloatUniform("uOrigin", left, top)
        runtime.setFloatUniform("uSize", width, height)
        runtime.setFloatUniform("uShift", shift)
        runtime.setFloatUniform("uPress", press)
        return true
    }

    @RequiresApi(33)
    private fun updateLiquidShader(shader: Shader, look: LiquidLook): Boolean {
        val runtime = shader as? RuntimeShader ?: return false
        runtime.setFloatUniform("uIsland", look.island)
        runtime.setFloatUniform("uBulb", look.bulb)
        runtime.setFloatUniform("uDrop", look.drop)
        runtime.setFloatUniform("uDir", look.direction)
        runtime.setFloatUniform("uNeck", look.neck[0], look.neck[1], look.neck[2], look.neck[3])
        runtime.setFloatUniform("uNeckR", look.neck[4])
        runtime.setFloatUniform("uLip", look.lip)
        runtime.setFloatUniform("uTail", look.tail)
        runtime.setFloatUniform("uShadow", look.shadow)
        return true
    }

    private fun Paint.resetForFill(color: Int) {
        reset()
        isAntiAlias = true
        style = Paint.Style.FILL
        this.color = color
    }

    private fun Paint.resetForStroke(color: Int, width: Float) {
        reset()
        isAntiAlias = true
        style = Paint.Style.STROKE
        this.color = color
        strokeWidth = width
    }

    private fun withAlpha(color: Int, alpha: Float): Int = Color.argb((Color.alpha(color) * clamp01(alpha)).roundToInt(), Color.red(color), Color.green(color), Color.blue(color))

    private fun blend(from: Int, to: Int, progress: Float): Int {
        val p = clamp01(progress)
        return Color.rgb(
            mix(Color.red(from).toFloat(), Color.red(to).toFloat(), p).roundToInt(),
            mix(Color.green(from).toFloat(), Color.green(to).toFloat(), p).roundToInt(),
            mix(Color.blue(from).toFloat(), Color.blue(to).toFloat(), p).roundToInt(),
        )
    }

    private fun formatMoney(cents: Int, withCents: Boolean = true): String {
        val sign = if (cents < 0) "−" else ""
        val absolute = abs(cents)
        val dollars = absolute / 100
        val grouped = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(dollars)
        return if (withCents) "${sign}\$${grouped}.${(absolute % 100).toString().padStart(2, '0')}" else "${sign}\$${grouped}"
    }

    private companion object {
        const val CARD_SHADER = """
            uniform float2 uOrigin;
            uniform float2 uSize;
            uniform float uShift;
            uniform float uPress;
            float hash12(float2 p) {
                float3 p3 = fract(float3(p.xyx) * 0.1031);
                p3 += dot(p3, p3.yzx + 33.33);
                return fract((p3.x + p3.y) * p3.z);
            }
            float noise(float2 p) {
                float2 i = floor(p); float2 f = fract(p); f = f*f*(3.0-2.0*f);
                return mix(mix(hash12(i),hash12(i+float2(1,0)),f.x),mix(hash12(i+float2(0,1)),hash12(i+float2(1,1)),f.x),f.y);
            }
            float groove(float x, float w) { return 1.0-smoothstep(w,w+0.7,abs(x)); }
            half4 main(float2 frag) {
                float2 p = frag-uOrigin;
                float2 uv = p/uSize;
                float3 deep = float3(0.0035,0.006,0.042);
                float3 cobalt = float3(0.012,0.024,0.16);
                float3 c = mix(cobalt,deep,smoothstep(0.0,1.0,uv.x*0.5+uv.y*0.7));
                float grain = noise(p*1.9)*0.5+noise(float2(p.x*0.06,p.y*2.4))*0.5-0.5;
                c *= 1.0+grain*0.06;
                float s=uShift/uSize.y;
                float2 d=(uv-float2(0.08,-0.16-s*1.4))*float2(0.85,1.25);
                float lobe=exp(-dot(d,d)*3.6)*(1.0-uPress*0.35);
                c += float3(0.1,0.17,0.75)*lobe*0.26;
                float bandPos=dot(uv,normalize(float2(1.0,0.62)))-(0.5-s*0.9);
                float window=smoothstep(-0.18,-0.03,bandPos)*(1.0-smoothstep(0.03,0.24,bandPos));
                c += float3(0.12,0.17,0.5)*window*(0.1+grain*0.2)*(1.0-uPress*0.35);
                float2 cc=float2(uSize.x*1.06,uSize.y*1.18);
                float rr=length(p-cc)+(noise(p*0.011+3.7)-0.5)*16.0;
                float line=groove((fract(rr/7.0)-0.5)*7.0,0.2);
                float zone=smoothstep(320.0,200.0,rr)*smoothstep(96.0,150.0,rr);
                c += float3(0.42,0.5,0.95)*line*zone*(0.035+window*0.32+lobe*0.15);
                float2 chipOrigin=float2(24.0,80.0); float2 chipSize=float2(46.0,35.0);
                float2 q=(p-chipOrigin)/chipSize;
                if (q.x>=0.0 && q.x<=1.0 && q.y>=0.0 && q.y<=1.0) {
                    float3 gold=mix(float3(0.74,0.6,0.4),float3(0.34,0.25,0.14),q.y*0.8+q.x*0.3);
                    float channels=max(groove(abs((p.x-chipOrigin.x)-23.0)-10.0,0.35),groove(abs((p.y-chipOrigin.y)-17.5)-6.0,0.35));
                    c=mix(gold,gold*0.42,channels*0.75);
                }
                float3 outColor=pow(max(c,0.0),float3(0.4545));
                outColor += (hash12(p*3.1)-0.5)*0.012;
                return half4(half3(outColor),1.0);
            }
        """

        const val LIQUID_SHADER = """
            uniform float4 uIsland;
            uniform float3 uBulb;
            uniform float4 uDrop;
            uniform float2 uDir;
            uniform float4 uNeck;
            uniform float uNeckR;
            uniform float uLip;
            uniform float uTail;
            uniform float uShadow;
            float sdBox(float2 p,float2 b,float r){float2 q=abs(p)-b+r;return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;}
            float smin(float a,float b,float k){float h=clamp(0.5+0.5*(b-a)/k,0.0,1.0);return mix(b,a,h)-k*h*(1.0-h);}
            float segDist(float2 p,float2 a,float2 b){float2 ab=b-a;float t=clamp(dot(p-a,ab)/max(dot(ab,ab),0.0001),0.0,1.0);return length(p-a-ab*t);}
            float dDrop(float2 p){
                if(uDrop.z<=0.01)return 10000.0;
                float2 q=p-uDrop.xy;float2 n=float2(-uDir.y,uDir.x);float s=uDrop.w;
                float2 e=float2(dot(q,uDir)/s,dot(q,n)*sqrt(s));float d=(length(e)-uDrop.z)/sqrt(s);
                if(uTail>0.01)d=smin(d,length(p-(uDrop.xy-uDir*uDrop.z*1.05))-uDrop.z*0.2*uTail,9.0);
                return d;
            }
            float liquid(float2 p){
                float d=sdBox(p-uIsland.xy,uIsland.zw,uIsland.w);
                if(uBulb.z>0.01)d=smin(d,length(p-uBulb.xy)-uBulb.z,15.0);
                if(uNeckR>0.01)d=smin(d,segDist(p,uNeck.xy,uNeck.zw)-uNeckR,10.5);
                if(uLip>0.01)d=smin(d,length(p-float2(uIsland.x,uIsland.y+uIsland.w+uLip*0.2))-uLip,8.0);
                return smin(d,dDrop(p),15.0);
            }
            float caster(float2 p){float d=10000.0;if(uBulb.z>0.01)d=length(p-uBulb.xy)-uBulb.z;return min(d,dDrop(p));}
            half4 main(float2 p){
                float d=liquid(p);float lip=uIsland.y+uIsland.w;float air=uDrop.z>0.01?1.0:0.0;
                float2 off=mix(float2(1.5,5.0),float2(3.0,12.0),air);float blur=mix(7.0,12.0,air);
                float sh=uShadow*mix(0.16,0.13,air)*(1.0-smoothstep(-blur,blur,caster(p-off)))*smoothstep(lip,lip+10.0,p.y);
                float cov=clamp(0.5-d/0.45,0.0,1.0);if(cov<=0.0)return half4(0,0,0,half(sh));
                float e=0.6;float2 g=float2(liquid(p+float2(e,0))-liquid(p-float2(e,0)),liquid(p+float2(0,e))-liquid(p-float2(0,e)));
                g/=max(length(g),0.0001);float bevel=max(8.0,0.9*max(uBulb.z,uDrop.z));float edge=1.0-clamp(-d/bevel,0.0,1.0);
                float3 n=normalize(float3(g*edge,sqrt(max(0.0,1.0-edge*edge))+0.0001));float3 r=reflect(float3(0,0,-1),n);
                float below=clamp(r.y*0.8+0.35,0.0,1.0);float3 env=float3(0.78,0.8,0.84)*mix(0.12,0.8,below*below)*clamp(1.0-r.z,0.0,1.0);
                float lobe=dot(r,normalize(float3(-0.55,-0.55,0.63)));float soft=smoothstep(0.82,0.97,lobe);float core=pow(max(lobe,0.0),260.0);
                float fresnel=0.035+0.965*pow(1.0-n.z,5.0);float3 c=float3(0.003,0.0035,0.005)*(1.0-fresnel)+env*fresnel+float3(1)* (soft*0.34+core*1.1);
                c=mix(float3(0),c,smoothstep(lip-1.0,lip+9.0,p.y));c=pow(max(c,0.0),float3(0.4545));
                return half4(half3(c*cov),half(cov+sh*(1.0-cov)));
            }
        """
    }
}
