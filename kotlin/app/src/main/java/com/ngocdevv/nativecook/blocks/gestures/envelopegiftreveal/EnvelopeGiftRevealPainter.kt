package com.ngocdevv.nativecook.blocks.gestures.envelopegiftreveal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val EW = EnvelopeGiftRevealLayout.envW
private const val EH = EnvelopeGiftRevealLayout.envH
private const val CW = EnvelopeGiftRevealLayout.cardW
private const val CH = EnvelopeGiftRevealLayout.cardH
private const val FLAP_PERSPECTIVE = 900f
private const val TIP_PERSPECTIVE = 700f
private const val CARD_PERSPECTIVE = 1_100f
private const val DEG_TO_RAD = (PI / 180.0).toFloat()

private class EnvelopeGiftRevealCardPose {
    var y = 0f
    var scale = 1f
    var rotation = 0f
    var flip = 0f
    var detached = false
}

/** Native Canvas counterpart of EnvelopeScene.tsx. All geometry is expressed in dp. */
internal class EnvelopeGiftRevealPainter(
    context: Context,
    private val width: Float,
    private val height: Float,
    private val reduced: Boolean,
    private val layout: () -> EnvelopeGiftRevealScreenLayout,
) {
    private val textures = EnvelopeGiftRevealTextures(
        widthDp = width,
        heightDp = height,
        density = context.resources.displayMetrics.density,
    )
    private val serif = Typeface.createFromAsset(context.assets, EnvelopeGiftRevealAssets.serif)
    private val hand = Typeface.createFromAsset(context.assets, EnvelopeGiftRevealAssets.hand)
    private val uiBold = Typeface.createFromAsset(context.assets, EnvelopeGiftRevealAssets.uiBold)

    private val flap = Path().apply {
        val tip = EH * EnvelopeGiftRevealLayout.flapTip
        moveTo(0f, 0f)
        lineTo(EW, 0f)
        lineTo(EW / 2f + 26f, tip - 12f)
        quadTo(EW / 2f, tip + 4f, EW / 2f - 26f, tip - 12f)
        close()
    }
    private val left = Path().apply {
        moveTo(0f, 0f)
        lineTo(EW * 0.47f, EH * 0.56f)
        lineTo(0f, EH)
        close()
    }
    private val right = Path().apply {
        moveTo(EW, 0f)
        lineTo(EW * 0.53f, EH * 0.56f)
        lineTo(EW, EH)
        close()
    }
    private val pocket = Path().apply {
        moveTo(0f, EH)
        quadTo(EW * 0.3f, EH * 0.5f, EW / 2f, EH * 0.44f)
        quadTo(EW * 0.7f, EH * 0.5f, EW, EH)
        close()
    }
    private val envelopeClip = Path().apply {
        addRoundRect(0f, 0f, EW, EH, 4f, 4f, Path.Direction.CW)
    }
    private val cardClip = Path().apply {
        addRoundRect(
            -CW / 2f,
            -CH / 2f,
            CW / 2f,
            CH / 2f,
            EnvelopeGiftRevealLayout.cardR,
            EnvelopeGiftRevealLayout.cardR,
            Path.Direction.CW,
        )
    }
    private val wax = Path().apply {
        val radius = EnvelopeGiftRevealLayout.sealD / 2f
        val steps = 72
        for (index in 0..steps) {
            val angle = index.toFloat() / steps * PI.toFloat() * 2f
            val wandering = radius * (
                1f + 0.05f * sin(5f * angle + 0.6f) +
                    0.035f * sin(9f * angle + 2.1f) +
                    0.02f * sin(14f * angle + 4f)
                )
            val x = cos(angle) * wandering
            val y = sin(angle) * wandering
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    private val scratch = Path()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val linenRect = RectF(0f, 0f, width, height)
    private val envelopeRect = RectF(0f, 0f, EW, EH)
    private val cardRect = RectF(-CW / 2f, -CH / 2f, CW / 2f, CH / 2f)

    private val envelopeShadow = EnvelopeGiftRevealSoftRoundRect(EnvelopeGiftRevealColors.shadow.toArgb(), 18f)
    private val cardShadow = EnvelopeGiftRevealSoftRoundRect(EnvelopeGiftRevealColors.shadow.toArgb(), 20f)
    private val insidePaint = fill(EnvelopeGiftRevealColors.paperInside.toArgb())
    private val flapBackPaint = fill(EnvelopeGiftRevealColors.paperLow.toArgb())
    private val linerPaint = fill(EnvelopeGiftRevealColors.letterpress.toArgb())
    private val linerRulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x47D6B878
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
    }
    private val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = linear(0f, 0f, EW * 0.47f, EH * 0.56f, EnvelopeGiftRevealColors.paperHigh.toArgb(), EnvelopeGiftRevealColors.paper.toArgb())
    }
    private val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = linear(EW, 0f, EW * 0.53f, EH * 0.56f, EnvelopeGiftRevealColors.paper.toArgb(), EnvelopeGiftRevealColors.paperLow.toArgb())
    }
    private val pocketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = linear(0f, EH * 0.45f, EW * 0.4f, EH, EnvelopeGiftRevealColors.paperHigh.toArgb(), EnvelopeGiftRevealColors.paper.toArgb())
    }
    private val creasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = EnvelopeGiftRevealColors.crease.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = 2f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(1.6f), BlurMaskFilter.Blur.NORMAL)
        }
    }
    private val sideFoldStroke = stroke(EnvelopeGiftRevealColors.paperShade.toArgb(), 1.2f).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(1.2f), BlurMaskFilter.Blur.NORMAL)
        }
    }
    private val flapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = linear(0f, 0f, EW * 0.6f, EH * EnvelopeGiftRevealLayout.flapTip, EnvelopeGiftRevealColors.paperHigh.toArgb(), EnvelopeGiftRevealColors.paper.toArgb())
    }
    private val flapShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x38603E1E
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(4f), BlurMaskFilter.Blur.NORMAL)
        }
    }
    private val waxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            -EnvelopeGiftRevealLayout.sealD * 0.175f,
            -EnvelopeGiftRevealLayout.sealD * 0.2f,
            EnvelopeGiftRevealLayout.sealD * 0.8f,
            intArrayOf(
                EnvelopeGiftRevealColors.waxHigh.toArgb(),
                EnvelopeGiftRevealColors.wax.toArgb(),
                EnvelopeGiftRevealColors.waxLow.toArgb(),
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    private val waxShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x59280A0A
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(3f), BlurMaskFilter.Blur.NORMAL)
        }
    }
    private val waxDarkStroke = stroke(EnvelopeGiftRevealColors.waxLow.toArgb(), 1.6f)
    private val waxLightStroke = stroke(EnvelopeGiftRevealColors.waxHigh.toArgb(), 0.8f)
    private val waxLetterHigh = textPaint(serif, 30f, EnvelopeGiftRevealColors.waxHigh.toArgb()).apply { alpha = 179 }
    private val waxLetterLow = textPaint(serif, 30f, EnvelopeGiftRevealColors.waxLow.toArgb())
    private val waxHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x52FFEBE1
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(1.5f), BlurMaskFilter.Blur.NORMAL)
        }
    }
    private val cardFill = fill(EnvelopeGiftRevealColors.card.toArgb())
    private val cardEdge = stroke(EnvelopeGiftRevealColors.cardEdge.toArgb(), 1f)
    private val cardRule = stroke(EnvelopeGiftRevealColors.letterpress.toArgb(), 1f).apply { alpha = 179 }
    private val cardRuleInner = stroke(EnvelopeGiftRevealColors.letterpress.toArgb(), 0.5f).apply { alpha = 128 }
    private val shopPaint = textPaint(serif, 32f, EnvelopeGiftRevealColors.letterpress.toArgb())
    private val amountPaint = textPaint(serif, 60f, EnvelopeGiftRevealColors.letterpress.toArgb())
    private val amountHighlightPaint = textPaint(serif, 60f, 0xB3FFFFFF.toInt())
    private val smallPaint = textPaint(uiBold, 9f, EnvelopeGiftRevealColors.cardMute.toArgb())
    private val handPaint = textPaint(hand, 25f, EnvelopeGiftRevealColors.pen.toArgb()).apply { alpha = 235 }
    private val envelopeHandPaint = textPaint(hand, 30f, EnvelopeGiftRevealColors.pen.toArgb()).apply { alpha = 230 }
    private val backAmountPaint = textPaint(serif, 22f, EnvelopeGiftRevealColors.letterpress.toArgb())
    private val matrix = Matrix()
    private val matrixValues = FloatArray(9)
    private val pose = EnvelopeGiftRevealCardPose()

    fun draw(canvas: Canvas, engine: EnvelopeGiftRevealEngine) {
        val frame = engine.frame
        val screen = layout()
        canvas.drawBitmap(textures.linen, null, linenRect, bitmapPaint)
        poseCard(frame, screen)
        drawEnvelope(canvas, frame, screen)
        if (pose.detached) {
            drawDetachedCardShadow(canvas, frame)
            drawDetachedCard(canvas, frame)
        }
    }

    private fun poseCard(frame: EnvelopeGiftRevealFrame, screen: EnvelopeGiftRevealScreenLayout) {
        val resetting = frame.reset > 0f
        val out = if (resetting) 0f else envelopeGiftRevealClamp01(
            (frame.p - EnvelopeGiftRevealOpen.flapShare) / (1f - EnvelopeGiftRevealOpen.flapShare),
        )
        val fold = envelopeGiftRevealClamp01(frame.p / EnvelopeGiftRevealOpen.flapShare)
        val insideY = screen.envTop + 16f * fold * (if (resetting) 0f else 1f) +
            18f + CH * EnvelopeGiftRevealLayout.cardInScale / 2f - out * EnvelopeGiftRevealOpen.cardRise
        val finalY = screen.cardTop + CH / 2f
        val arc = -36f * sin(PI.toFloat() * frame.fly)
        val turn = envelopeGiftRevealEaseOutBack(frame.flipT, EnvelopeGiftRevealReveal.flipBack)
        pose.y = insideY + (finalY - insideY) * frame.fly + arc
        pose.scale = (
            EnvelopeGiftRevealLayout.cardInScale +
                (1f - EnvelopeGiftRevealLayout.cardInScale) * envelopeGiftRevealEaseInOut(frame.fly)
            ) * (1f + EnvelopeGiftRevealReveal.flipLift * sin(PI.toFloat() * frame.flipT))
        pose.rotation = frame.tilt0 * (1f - frame.fly)
        pose.flip = turn * PI.toFloat()
        pose.detached = frame.after >= 0f && !resetting
    }

    private fun drawEnvelope(
        canvas: Canvas,
        frame: EnvelopeGiftRevealFrame,
        screen: EnvelopeGiftRevealScreenLayout,
    ) {
        val resetting = frame.reset > 0f
        val opacity = if (resetting) frame.resetIn else 1f - frame.drop
        if (opacity <= 0f) return
        val fold = envelopeGiftRevealClamp01(frame.p / EnvelopeGiftRevealOpen.flapShare)
        val settle = if (resetting) 0f else 16f * fold
        val still = 1f - envelopeGiftRevealClamp01(frame.p / 0.05f)
        val floating = if (reduced) 0f else {
            EnvelopeGiftRevealAmbient.floatPt *
                sin(2.0 * PI * frame.now / EnvelopeGiftRevealAmbient.floatMs).toFloat() * still
        }
        val scale = if (resetting) 0.96f + 0.04f * frame.resetIn else 1f
        val angle = if (resetting) 0f else fold * 180f + frame.breath
        canvas.save()
        canvas.translate(
            (width - EW) / 2f,
            screen.envTop + settle + frame.drop * frame.drop * 520f + floating,
        )
        canvas.scale(scale, scale)
        val layered = opacity < 0.999f
        if (layered) {
            canvas.saveLayerAlpha(-80f, -320f, EW + 80f, EH + 180f, (opacity * 255f).roundToInt())
        }
        envelopeShadow.draw(canvas, 10f, 22f, EW - 8f, EH - 8f, 6f)
        canvas.drawRoundRect(0f, 0f, EW, EH, 4f, 4f, insidePaint)
        if (angle >= 90f) drawFlapBack(canvas, angle)
        if (!pose.detached) {
            canvas.save()
            canvas.translate(EW / 2f, pose.y - screen.envTop)
            canvas.rotate(pose.rotation)
            canvas.scale(pose.scale, pose.scale)
            drawCardFront(canvas)
            canvas.restore()
        }
        canvas.save()
        canvas.clipPath(envelopeClip)
        canvas.drawPath(left, leftPaint)
        canvas.drawPath(right, rightPaint)
        canvas.drawPath(left, sideFoldStroke)
        canvas.drawPath(pocket, pocketPaint)
        canvas.drawPath(pocket, creasePaint)
        canvas.drawBitmap(textures.envelopeGrain, null, envelopeRect, bitmapPaint)
        val toX = EW - 24f - envelopeHandPaint.measureText(EnvelopeGiftRevealGift.to)
        canvas.drawText(EnvelopeGiftRevealGift.to, toX, EH - 28f, envelopeHandPaint)
        canvas.restore()
        if (angle < 90f) drawFlapFront(canvas, angle, frame)
        if (layered) canvas.restore()
        canvas.restore()
    }

    private fun drawFlapBack(canvas: Canvas, angle: Float) {
        canvas.save()
        canvas.concat(rotateXMatrix(-angle * DEG_TO_RAD, FLAP_PERSPECTIVE))
        canvas.drawPath(flap, flapBackPaint)
        canvas.save()
        canvas.clipPath(flap)
        canvas.translate(6f, 5f)
        canvas.scale(0.96f, 0.96f)
        canvas.drawRect(0f, 0f, EW, EH, linerPaint)
        for (index in 0 until 22) {
            scratch.rewind()
            scratch.moveTo(index * 18f - 120f, 0f)
            scratch.lineTo(index * 18f + 60f, EH)
            canvas.drawPath(scratch, linerRulePaint)
        }
        canvas.restore()
        canvas.restore()
    }

    private fun drawFlapFront(canvas: Canvas, angle: Float, frame: EnvelopeGiftRevealFrame) {
        val fold = EH * EnvelopeGiftRevealLayout.flapTip * 0.55f
        canvas.save()
        canvas.concat(rotateXMatrix(-angle * DEG_TO_RAD, FLAP_PERSPECTIVE))
        canvas.save()
        canvas.translate(0f, 3f)
        canvas.drawPath(flap, flapShadow)
        canvas.restore()
        canvas.save()
        canvas.clipRect(0f, 0f, EW, fold)
        drawFlapPaper(canvas)
        canvas.restore()
        val lag = if (reduced) 0f else {
            EnvelopeGiftRevealOpen.curlDeg * sin(PI.toFloat() * min(180f, angle) / 180f)
        }
        canvas.save()
        canvas.translate(0f, fold)
        canvas.concat(rotateXMatrix(lag * DEG_TO_RAD, TIP_PERSPECTIVE))
        canvas.translate(0f, -fold)
        canvas.save()
        canvas.clipRect(0f, fold, EW, EH)
        drawFlapPaper(canvas)
        canvas.restore()
        val lifted = if (frame.reset > 0f) 0f else {
            envelopeGiftRevealClamp01(frame.p / EnvelopeGiftRevealOpen.flapShare) * 180f
        }
        drawSeal(
            canvas,
            envelopeGiftRevealClamp01(
                lifted / if (reduced) 30f else EnvelopeGiftRevealOpen.sealSplitDeg,
            ),
        )
        canvas.restore()
        canvas.restore()
    }

    private fun drawFlapPaper(canvas: Canvas) {
        canvas.drawPath(flap, flapPaint)
        canvas.save()
        canvas.clipPath(flap)
        canvas.drawBitmap(textures.flapGrain, null, envelopeRect, bitmapPaint)
        canvas.restore()
    }

    private fun drawSeal(canvas: Canvas, split: Float) {
        val halves = 1f - envelopeGiftRevealClamp01((split - 0.55f) / 0.45f)
        if (halves <= 0f) return
        val radius = EnvelopeGiftRevealLayout.sealD / 2f
        canvas.save()
        canvas.translate(EW / 2f, EH * EnvelopeGiftRevealLayout.sealY)
        waxShadow.alpha = (0x59 * halves).roundToInt().coerceIn(0, 255)
        canvas.save()
        canvas.translate(0f, 2.5f)
        canvas.drawPath(wax, waxShadow)
        canvas.restore()
        val pop = 1f + 0.08f * sin(PI.toFloat() * envelopeGiftRevealClamp01(split * 2.5f))
        canvas.scale(pop, pop)
        canvas.saveLayerAlpha(-radius * 2f, -radius * 2f, radius * 2f, radius * 2f, (halves * 255f).roundToInt())
        canvas.save()
        canvas.translate(-12f * split, 10f * split * split)
        canvas.rotate(-17.2f * split)
        canvas.clipRect(-radius - 4f, -radius - 4f, 0.5f, radius + 4f)
        drawSealFace(canvas, radius)
        canvas.restore()
        canvas.save()
        canvas.translate(12f * split, 12f * split * split)
        canvas.rotate(20.6f * split)
        canvas.clipRect(0f, -radius - 4f, radius + 4f, radius + 4f)
        drawSealFace(canvas, radius)
        canvas.restore()
        canvas.restore()
        canvas.restore()
    }

    private fun drawSealFace(canvas: Canvas, radius: Float) {
        canvas.drawPath(wax, waxPaint)
        canvas.drawCircle(0f, 0f, radius * 0.7f, waxDarkStroke)
        canvas.drawCircle(0.6f, 0.8f, radius * 0.7f, waxLightStroke)
        val x = -waxLetterLow.measureText("N") / 2f
        canvas.drawText("N", x + 0.7f, 10.8f, waxLetterHigh)
        canvas.drawText("N", x, 10f, waxLetterLow)
        canvas.drawOval(
            -radius * 0.62f,
            -radius * 0.7f,
            -radius * 0.12f,
            -radius * 0.44f,
            waxHighlight,
        )
    }

    private fun drawDetachedCardShadow(canvas: Canvas, frame: EnvelopeGiftRevealFrame) {
        val lift = sin(PI.toFloat() * envelopeGiftRevealClamp01(frame.flipT))
        val opacity = (1f - frame.resetOut) * (1f - 0.35f * lift)
        if (opacity <= 0f) return
        canvas.save()
        canvas.translate(width / 2f + 4f, pose.y + 14f + 10f * lift)
        canvas.scale(max(0.15f, abs(cos(pose.flip))) * pose.scale, pose.scale)
        cardShadow.draw(
            canvas,
            -CW / 2f + 8f,
            -CH / 2f,
            CW - 16f,
            CH - 4f,
            10f,
            opacity,
        )
        canvas.restore()
    }

    private fun drawDetachedCard(canvas: Canvas, frame: EnvelopeGiftRevealFrame) {
        val opacity = 1f - frame.resetOut
        if (opacity <= 0f) return
        canvas.saveLayerAlpha(0f, 0f, width, height, (opacity * 255f).roundToInt())
        canvas.translate(width / 2f, pose.y)
        canvas.rotate(pose.rotation)
        canvas.scale(pose.scale, pose.scale)
        canvas.concat(rotateYMatrix(pose.flip, CARD_PERSPECTIVE))
        if (cos(pose.flip) >= 0f) {
            drawCardFront(canvas)
        } else {
            canvas.scale(-1f, 1f)
            drawCardBack(canvas)
        }
        canvas.restore()
    }

    private fun drawCardFront(canvas: Canvas) {
        canvas.drawRoundRect(cardRect, EnvelopeGiftRevealLayout.cardR, EnvelopeGiftRevealLayout.cardR, cardFill)
        canvas.save()
        canvas.clipPath(cardClip)
        canvas.drawBitmap(textures.cardFrontGrain, null, cardRect, bitmapPaint)
        canvas.restore()
        canvas.drawRoundRect(-CW / 2f + 12f, -CH / 2f + 12f, CW / 2f - 12f, CH / 2f - 12f, 3f, 3f, cardRule)
        canvas.drawRoundRect(-CW / 2f + 15f, -CH / 2f + 15f, CW / 2f - 15f, CH / 2f - 15f, 2f, 2f, cardRuleInner)
        canvas.drawText(EnvelopeGiftRevealGift.shop, -CW / 2f + 28f, -CH / 2f + 56f, shopPaint)
        canvas.drawText(EnvelopeGiftRevealGift.shopLine, -CW / 2f + 29f, -CH / 2f + 74f, smallPaint)
        canvas.drawText("GIFT CARD", -CW / 2f + 29f, CH / 2f - 30f, smallPaint)
        val amountX = CW / 2f - 28f - amountPaint.measureText(EnvelopeGiftRevealGift.amount)
        canvas.drawText(EnvelopeGiftRevealGift.amount, amountX, CH / 2f - 25.2f, amountHighlightPaint)
        canvas.drawText(EnvelopeGiftRevealGift.amount, amountX, CH / 2f - 26f, amountPaint)
        canvas.drawRoundRect(cardRect, EnvelopeGiftRevealLayout.cardR, EnvelopeGiftRevealLayout.cardR, cardEdge)
    }

    private fun drawCardBack(canvas: Canvas) {
        canvas.drawRoundRect(cardRect, EnvelopeGiftRevealLayout.cardR, EnvelopeGiftRevealLayout.cardR, cardFill)
        canvas.save()
        canvas.clipPath(cardClip)
        canvas.drawBitmap(textures.cardBackGrain, null, cardRect, bitmapPaint)
        canvas.restore()
        EnvelopeGiftRevealGift.note.forEachIndexed { index, line ->
            val x = -CW / 2f + 26f +
                (if (index == EnvelopeGiftRevealGift.note.lastIndex) 150f else 0f) +
                (if (index % 2 == 1) 2f else 0f)
            val y = -CH / 2f + 44f + index * 32f +
                (if (index == EnvelopeGiftRevealGift.note.lastIndex) 4f else 0f)
            canvas.drawText(line, x, y, handPaint)
        }
        val amountX = CW / 2f - 22f - backAmountPaint.measureText(EnvelopeGiftRevealGift.amount)
        canvas.drawText(EnvelopeGiftRevealGift.amount, amountX, -CH / 2f + 34f, backAmountPaint)
        val shop = "FABLE & FERN"
        canvas.drawText(shop, CW / 2f - 22f - smallPaint.measureText(shop), -CH / 2f + 48f, smallPaint)
        canvas.drawRoundRect(cardRect, EnvelopeGiftRevealLayout.cardR, EnvelopeGiftRevealLayout.cardR, cardEdge)
    }

    private fun rotateXMatrix(theta: Float, perspective: Float): Matrix {
        matrixValues[0] = 1f
        matrixValues[1] = 0f
        matrixValues[2] = 0f
        matrixValues[3] = 0f
        matrixValues[4] = cos(theta)
        matrixValues[5] = 0f
        matrixValues[6] = 0f
        matrixValues[7] = -sin(theta) / perspective
        matrixValues[8] = 1f
        matrix.setValues(matrixValues)
        return matrix
    }

    private fun rotateYMatrix(theta: Float, perspective: Float): Matrix {
        matrixValues[0] = cos(theta)
        matrixValues[1] = 0f
        matrixValues[2] = 0f
        matrixValues[3] = 0f
        matrixValues[4] = 1f
        matrixValues[5] = 0f
        matrixValues[6] = sin(theta) / perspective
        matrixValues[7] = 0f
        matrixValues[8] = 1f
        matrix.setValues(matrixValues)
        return matrix
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
    }

    private fun textPaint(typeface: Typeface, size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = size
        this.color = color
    }

    private fun linear(x0: Float, y0: Float, x1: Float, y1: Float, from: Int, to: Int): Shader =
        LinearGradient(x0, y0, x1, y1, from, to, Shader.TileMode.CLAMP)
}
