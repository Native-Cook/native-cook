package com.ngocdevv.nativecook.blocks.gestures.envelopegiftreveal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val EW = EnvelopeLayout.envW
private const val EH = EnvelopeLayout.envH
private const val CW = EnvelopeLayout.cardW
private const val CH = EnvelopeLayout.cardH

/** Where the amount sits on the card, from its centre (EnvelopeScene.tsx AMOUNT_Y). */
private const val AMOUNT_Y = CH / 2f - 22f

/** Petals in the card's palette: magenta, pink, blush, lime, white, yellow. */
private val CONFETTI_COLOURS = intArrayOf(
    0xFFE0359A.toInt(), 0xFFF06BB5.toInt(), 0xFFFFC1DF.toInt(), 0xFFC9F23A.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFE45C.toInt(),
)

private const val SEAL_TEXT = "PT"
private const val FLAP_PERSPECTIVE = 900f
private const val TIP_PERSPECTIVE = 700f
private const val DEG_TO_RAD = (PI / 180.0).toFloat()
private const val RAD_TO_DEG = (180.0 / PI).toFloat()

/** Where the gift card is for one frame (EnvelopeScene.tsx cardPose). */
private class CardPose {
    var y = 0f
    var scale = 1f
    var rotation = 0f
    var front = false
}

/**
 * Drawn layer (EnvelopeScene.tsx): the page colour, the envelope (back, folding flap, card inside,
 * front pocket, seal), the gift card on its way out and in place, and the confetti. Everything is
 * drawn in dp: the caller scales the canvas by the display density.
 */
internal class EnvelopeGiftRevealPainter(
    context: Context,
    private val width: Float,
    private val height: Float,
    private val reduced: Boolean,
    private val layout: () -> EnvelopeScreenLayout,
    private val roll: () -> Float,
) {
    private val density = context.resources.displayMetrics.density

    // Android's "sans-serif" is the Skia family the React Native block asks for outside iOS.
    private val boldFace = Typeface.create("sans-serif", Typeface.BOLD)
    private val art: Bitmap = coverBitmap(
        requireNotNull(
            BitmapFactory.decodeResource(
                context.resources,
                R.drawable.envelopegiftreveal_gift_card_art,
                BitmapFactory.Options().apply { inScaled = false },
            ),
        ) { "envelopegiftreveal_gift_card_art did not decode" },
        (CW * density).roundToInt(),
        (CH * density).roundToInt(),
    )

    // Paths -------------------------------------------------------------------------------------
    private val flapPath = Path().apply {
        val tip = EH * EnvelopeLayout.flapTip
        moveTo(0f, 0f)
        lineTo(EW, 0f)
        lineTo(EW / 2f + 22f, tip - 10f)
        quadTo(EW / 2f, tip + 4f, EW / 2f - 22f, tip - 10f)
        close()
    }

    // Side panels run from the top corners down to the pocket's top corners (source frame 010).
    private val leftPath = Path().apply {
        moveTo(0f, 0f)
        lineTo(EW * 0.25f, EH * 0.38f)
        lineTo(EW * 0.25f, EH)
        lineTo(0f, EH)
        close()
    }
    private val rightPath = Path().apply {
        moveTo(EW, 0f)
        lineTo(EW * 0.75f, EH * 0.38f)
        lineTo(EW * 0.75f, EH)
        lineTo(EW, EH)
        close()
    }

    // The front pocket: a trapezoid whose flat top sits at the apex (source frames 001 and 010).
    private val bottomPath = Path().apply {
        val top = EnvelopeOpen.pocketApex * EH
        moveTo(0f, EH)
        lineTo(EW * 0.25f, top)
        lineTo(EW * 0.75f, top)
        lineTo(EW, EH)
        close()
    }
    private val envelopeClip = Path().apply { addRoundRect(0f, 0f, EW, EH, 10f, 10f, Path.Direction.CW) }
    private val cardClip = Path().apply {
        addRoundRect(-CW / 2f, -CH / 2f, CW / 2f, CH / 2f, EnvelopeLayout.cardR, EnvelopeLayout.cardR, Path.Direction.CW)
    }
    private val clipScratch = Path()
    private val cardRect = RectF(-CW / 2f, -CH / 2f, CW / 2f, CH / 2f)
    private val petal = Path()

    // Paints ------------------------------------------------------------------------------------
    private val pagePaint = Paint()
    private val glowPaint = Paint()
    private val envelopeShadow = EnvelopeSoftRoundRect(EnvelopeColors.shadow.toArgb(), 26f)
    private val envelopeBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = linear(0f, 0f, 0f, EH, EnvelopeColors.sideHigh.toArgb(), EnvelopeColors.bottomLow.toArgb())
    }
    private val flapBackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = linear(0f, 0f, 0f, EH * EnvelopeLayout.flapTip, EnvelopeColors.limeInside.toArgb(), EnvelopeColors.limeLow.toArgb())
    }
    private val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = linear(0f, EH, EW * 0.3f, EH * 0.4f, EnvelopeColors.sideLow.toArgb(), EnvelopeColors.sideHigh.toArgb())
    }
    private val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = EnvelopeColors.sideRight.toArgb() }
    private val bottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = linear(0f, EH, 0f, 0f, EnvelopeColors.bottomLow.toArgb(), EnvelopeColors.bottomHigh.toArgb())
    }

    // Key light from the top left catches the pocket's upper-left area, as in the source.
    private val bottomLight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            EW * 0.28f, EH * 0.5f, EW * 0.42f, 0x8CF6FF96.toInt(), 0x00F6FF96, Shader.TileMode.CLAMP,
        )
    }

    // Soft creases where the paper folds, not drawn outlines.
    private val creasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = EnvelopeColors.crease.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(1.2f), BlurMaskFilter.Blur.NORMAL)
    }
    private val flapShadow = softPaint(0x2E506400, 5f)
    private val flapPaper = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = linear(0f, 0f, EW, EH * EnvelopeLayout.flapTip, EnvelopeColors.flapHigh.toArgb(), EnvelopeColors.flapLow.toArgb())
    }
    private val flapLight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            EW * 0.24f, EH * 0.14f, EW * 0.38f, 0x99F6FF8C.toInt(), 0x00F6FF8C, Shader.TileMode.CLAMP,
        )
    }
    private val sealShadow = EnvelopeSoftRoundRect(0x403C5000, 4f)
    private val sealDisc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = EnvelopeColors.ink.toArgb() }
    private val sealText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = EnvelopeColors.sealMark.toArgb()
        textSize = 22f
        typeface = boldFace
    }
    private val cardShadow = EnvelopeSoftRoundRect(0x59781E3C, 16f)
    private val cardPink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = EnvelopeColors.cardPink.toArgb() }
    private val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val cardSheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            -CW / 2f, -CH / 2f, CW / 2f, CH / 2f,
            intArrayOf(0x2EFFFFFF, 0x00FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP,
        )
    }

    // A band of light crosses the glossy art: on arrival, then it rests where device tilt puts it.
    private val cardBand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            -CW * 0.2f, -CH / 2f, CW * 0.15f, CH / 2f,
            intArrayOf(0x00FFFFFF, 0xF2FFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            blendMode = BlendMode.SOFT_LIGHT
        } else {
            // No soft-light below API 29: a faint screen reads as the same glint.
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            alpha = 90
        }
    }
    private val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = EnvelopeColors.white.toArgb()
        textSize = 30f
        typeface = boldFace
    }
    private val petalPaints = Array(CONFETTI_COLOURS.size) { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CONFETTI_COLOURS[it] } }
    private val perspective = Matrix()
    private val perspectiveValues = FloatArray(9)
    private val pose = CardPose()

    /** One frame, in dp. */
    fun draw(canvas: Canvas, engine: EnvelopeGiftRevealEngine) {
        val f = engine.frame
        val l = layout()
        drawPage(canvas, f)
        val sweep = sweepOf(f)
        val amount = amountOf(f)
        poseCard(f, l)
        drawEnvelope(canvas, f, l, sweep, amount)
        if (pose.front) {
            canvas.save()
            canvas.translate(width / 2f, pose.y)
            canvas.rotate(pose.rotation)
            canvas.scale(pose.scale, pose.scale)
            drawCard(canvas, sweep, amount)
            canvas.restore()
        }
        drawConfetti(canvas, engine.confetti)
    }

    private fun drawPage(canvas: Canvas, f: EnvelopeFrame) {
        val mix = envelopeClamp01(f.page * (1f - f.reset))
        val lime = EnvelopeColors.lime.toArgb()
        val white = EnvelopeColors.white.toArgb()
        pagePaint.color = 0xFF000000.toInt() or
            (mixChannel(lime shr 16 and 0xFF, white shr 16 and 0xFF, mix) shl 16) or
            (mixChannel(lime shr 8 and 0xFF, white shr 8 and 0xFF, mix) shl 8) or
            mixChannel(lime and 0xFF, white and 0xFF, mix)
        canvas.drawRect(0f, 0f, width, height, pagePaint)

        // A slow highlight drifts across the lime page, kept very faint so the rest colour matches the source.
        val glowOpacity = if (reduced) 0f else EnvelopeAmbient.glowAmount * (1f - f.page)
        if (glowOpacity <= 0f) return
        val t = f.now / EnvelopeAmbient.glowMs
        val cx = width * (0.5 + 0.35 * sin(2.0 * PI * t))
        val cy = height * (0.35 + 0.12 * cos(2.0 * PI * t * 0.7))
        glowPaint.shader = RadialGradient(cx.toFloat(), cy.toFloat(), width * 0.8f, 0x52FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        glowPaint.alpha = (glowOpacity * 255f).roundToInt()
        canvas.drawRect(0f, 0f, width, height, glowPaint)
    }

    /** The light band's position across the card: resting from tilt and drift, sweeping on arrival. */
    private fun sweepOf(f: EnvelopeFrame): Float {
        val drift = if (reduced) 0f else 0.12f * sin(2.0 * PI * (f.now % 6000.0) / 6000.0).toFloat()
        val resting = 0.5f + 0.8f * (if (reduced) 0f else roll().coerceIn(-0.35f, 0.35f)) + drift
        if (f.after < 0f || reduced) return resting
        val u = envelopeClamp01((f.after - EnvelopeReveal.flyMs * 0.7f) / EnvelopeReveal.sweepMs)
        return if (u < 1f) -0.4f + 1.8f * u + (resting - 1.4f) * u * u else resting
    }

    /** The price counting up on arrival. */
    private fun amountOf(f: EnvelopeFrame): String {
        if (f.after < 0f || reduced) return EnvelopeGift.amount
        val u = envelopeClamp01((f.after - EnvelopeReveal.flyMs * 0.8f) / EnvelopeReveal.countMs)
        val eased = 1f - (1f - u).pow(3)
        return "$" + (EnvelopeGift.value * eased).roundToInt()
    }

    private fun poseCard(f: EnvelopeFrame, l: EnvelopeScreenLayout) {
        val resetting = f.reset > 0f
        val out = if (resetting) 0f else envelopeClamp01((f.p - EnvelopeOpen.flapShare) / (1f - EnvelopeOpen.flapShare))
        val settle = EnvelopeOpen.settlePt * envelopeClamp01(f.p / EnvelopeOpen.flapShare) * (if (resetting) 0f else 1f)
        val insideY = l.envTop + settle + 33f + (EnvelopeLayout.cardH * EnvelopeLayout.cardInScale) / 2f - out * EnvelopeOpen.cardRise
        val finalY = l.cardTop + CH / 2f
        val k = f.fly
        // A small extra lift mid-flight so the card arcs instead of sliding.
        val arc = -36f * sin(PI.toFloat() * k)
        pose.y = insideY + (finalY - insideY) * k + arc
        pose.scale = EnvelopeLayout.cardInScale + (1f - EnvelopeLayout.cardInScale) * envelopeEaseInOut(k)
        pose.rotation = f.tilt0 * (1f - k)
        pose.front = f.after >= 0f && !resetting
    }

    private fun drawEnvelope(canvas: Canvas, f: EnvelopeFrame, l: EnvelopeScreenLayout, sweep: Float, amount: String) {
        val resetting = f.reset > 0f
        val opacity = if (resetting) f.reset else 1f - f.drop
        if (opacity <= 0f) return
        val dropped = if (resetting) 0f else f.drop
        // As the flap opens the envelope settles down a little (source frames 001 to 010).
        val settle = if (resetting) 0f else EnvelopeOpen.settlePt * envelopeClamp01(f.p / EnvelopeOpen.flapShare)
        // Ambient: the sealed envelope floats and settles while it is being opened.
        val still = 1f - envelopeClamp01(f.p / 0.05f)
        val float = if (reduced) {
            0f
        } else {
            EnvelopeAmbient.floatPt * sin(2.0 * PI * (f.now % EnvelopeAmbient.floatMs) / EnvelopeAmbient.floatMs).toFloat() * still
        }
        val scale = if (resetting) 0.96f + 0.04f * f.reset else 1f
        val angle = if (resetting) 0f else envelopeClamp01(f.p / EnvelopeOpen.flapShare) * 180f + f.breath

        canvas.save()
        canvas.translate((width - EW) / 2f, l.envTop + settle + dropped * dropped * 460f + float)
        canvas.scale(scale, scale)
        val layered = opacity < 0.999f
        if (layered) canvas.saveLayerAlpha(-140f, -340f, EW + 140f, EH + 200f, (opacity * 255f).roundToInt())

        envelopeShadow.draw(canvas, 14f, 30f, EW - 28f, EH - 10f, 14f)
        canvas.drawRoundRect(0f, 0f, EW, EH, 10f, 10f, envelopeBody)

        val flapBack = if (angle >= 90f) 1f - envelopeClamp01((angle - 90f) / 25f) else 0f
        if (flapBack > 0f) {
            // The layer opens before the perspective, so its bounds stay a plain rectangle.
            if (flapBack < 0.999f) {
                canvas.saveLayerAlpha(-40f, -300f, EW + 40f, 60f, (flapBack * 255f).roundToInt())
            } else {
                canvas.save()
            }
            canvas.concat(flapMatrix(angle))
            canvas.drawPath(flapPath, flapBackPaint)
            canvas.restore()
        }

        if (!pose.front) {
            canvas.save()
            canvas.translate(EW / 2f, pose.y - l.envTop)
            canvas.rotate(pose.rotation)
            canvas.scale(pose.scale, pose.scale)
            drawCard(canvas, sweep, amount)
            canvas.restore()
        }

        canvas.save()
        canvas.clipPath(envelopeClip)
        canvas.drawPath(leftPath, leftPaint)
        canvas.drawPath(rightPath, rightPaint)
        canvas.drawPath(bottomPath, bottomPaint)
        canvas.drawPath(bottomPath, bottomLight)
        canvas.drawPath(bottomPath, creasePaint)
        canvas.restore()

        if (angle < 90f) drawFlapFront(canvas, angle, f)

        if (layered) canvas.restore()
        canvas.restore()
    }

    /** The flap seen from the front: paper in two parts so the lower part can curl, and the seal. */
    private fun drawFlapFront(canvas: Canvas, angle: Float, f: EnvelopeFrame) {
        val fold = EH * EnvelopeLayout.flapTip * 0.55f
        canvas.save()
        canvas.concat(flapMatrix(angle))

        canvas.save()
        canvas.translate(0f, 4f)
        canvas.drawPath(flapPath, flapShadow)
        canvas.restore()

        canvas.save()
        clipRectPath(canvas, 0f, 0f, EW, fold)
        canvas.drawPath(flapPath, flapPaper)
        canvas.drawPath(flapPath, flapLight)
        canvas.restore()

        // The flap is paper: its lower part lags the fold, so it curls as it lifts.
        val lag = if (reduced) 0f else EnvelopeOpen.curlDeg * sin(PI.toFloat() * min(180f, angle) / 180f)
        canvas.save()
        canvas.translate(0f, fold)
        canvas.concat(rotateXMatrix(lag * DEG_TO_RAD, TIP_PERSPECTIVE))
        canvas.translate(0f, -fold)
        canvas.save()
        clipRectPath(canvas, 0f, fold, EW, fold + EH)
        canvas.drawPath(flapPath, flapPaper)
        canvas.drawPath(flapPath, flapLight)
        canvas.restore()
        // The seal cracks only when the finger lifts the flap (not on the idle breath), so the rest state stays the source's.
        val lifted = if (f.reset > 0f) 0f else envelopeClamp01(f.p / EnvelopeOpen.flapShare) * 180f
        drawSeal(canvas, envelopeClamp01(lifted / (if (reduced) 30f else EnvelopeOpen.sealSplitDeg)))
        canvas.restore()

        canvas.restore()
    }

    /** The wax seal: as the flap starts to lift it swells, then cracks into two halves along the flap's line. */
    private fun drawSeal(canvas: Canvas, split: Float) {
        val halves = 1f - envelopeClamp01((split - 0.55f) / 0.45f)
        if (halves <= 0f) return
        val r = EnvelopeLayout.sealD / 2f
        canvas.save()
        canvas.translate(EW / 2f, EH * EnvelopeLayout.sealY)
        sealShadow.draw(canvas, -r, 3f - r, r * 2f, r * 2f, r, halves)

        val pop = 1f + 0.12f * sin(PI.toFloat() * envelopeClamp01(split * 2.5f))
        canvas.scale(pop, pop)
        val layered = halves < 0.999f
        if (layered) canvas.saveLayerAlpha(-r * 2f, -r * 2f, r * 2f, r * 2f, (halves * 255f).roundToInt())

        canvas.save()
        canvas.translate(-14f * split, 10f * split * split)
        canvas.rotate(-0.35f * split * RAD_TO_DEG)
        canvas.clipRect(-r - 2f, -r - 2f, 0.5f, r + 2f)
        drawSealFace(canvas, r)
        canvas.restore()

        canvas.save()
        canvas.translate(14f * split, 12f * split * split)
        canvas.rotate(0.4f * split * RAD_TO_DEG)
        canvas.clipRect(0f, -r - 2f, r + 2f, r + 2f)
        drawSealFace(canvas, r)
        canvas.restore()

        if (layered) canvas.restore()
        canvas.restore()
    }

    private fun drawSealFace(canvas: Canvas, r: Float) {
        canvas.drawCircle(0f, 0f, r, sealDisc)
        canvas.drawText(SEAL_TEXT, -sealText.measureText(SEAL_TEXT) / 2f - 1f, 8f, sealText)
    }

    /** The gift card, centred on the canvas origin. */
    private fun drawCard(canvas: Canvas, sweep: Float, amount: String) {
        cardShadow.draw(canvas, -CW / 2f + 10f, -CH / 2f + 18f, CW - 20f, CH - 8f, EnvelopeLayout.cardR)
        canvas.save()
        canvas.clipPath(cardClip)
        canvas.drawRect(-CW / 2f, -CH / 2f, CW / 2f, CH / 2f, cardPink)
        canvas.drawBitmap(art, null, cardRect, artPaint)
        canvas.drawRect(-CW / 2f, -CH / 2f, CW / 2f, CH / 2f, cardSheen)
        canvas.save()
        canvas.translate((sweep - 0.5f) * CW * 1.6f, 0f)
        canvas.drawRect(-CW * 0.7f, -CH / 2f, CW * 0.7f, CH / 2f, cardBand)
        canvas.restore()
        canvas.restore()
        canvas.drawText(amount, -CW / 2f + 20f, AMOUNT_Y, amountPaint)
    }

    private fun drawConfetti(canvas: Canvas, c: DoubleArray) {
        for (which in CONFETTI_COLOURS.indices) {
            petal.rewind()
            var o = 0
            while (o < c.size) {
                if (c[o + 6] > 0.0 && c[o + 7].toInt() == which) addPetal(c, o)
                o += 8
            }
            canvas.drawPath(petal, petalPaints[which])
        }
    }

    /** A lens shape whose width follows its spin, so each petal flutters as it falls. */
    private fun addPetal(c: DoubleArray, o: Int) {
        val a = c[o + 4]
        val life = min(1.0, c[o + 6] / 0.5)
        val hw = (7.0 * life).toFloat()
        val hh = (3.6 * life * (0.25 + 0.75 * abs(cos(a * 1.3)))).toFloat()
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        val x = c[o].toFloat()
        val y = c[o + 1].toFloat()
        petal.moveTo(x - ca * hw, y - sa * hw)
        petal.quadTo(x - sa * hh * 2f, y + ca * hh * 2f, x + ca * hw, y + sa * hw)
        petal.quadTo(x + sa * hh * 2f, y - ca * hh * 2f, x - ca * hw, y - sa * hw)
        petal.close()
    }

    // Helpers -----------------------------------------------------------------------------------

    /** Perspective 900, then a rotation about the top edge by -angle (EnvelopeScene.tsx flapTransform). */
    private fun flapMatrix(angleDeg: Float): Matrix = rotateXMatrix(-angleDeg * DEG_TO_RAD, FLAP_PERSPECTIVE)

    /**
     * [{ perspective: p }, { rotateX: theta }] for a flat shape: y' = y cos(theta), z' = y sin(theta),
     * and the perspective divide w = 1 - z' / p. That is one projective 3x3.
     */
    private fun rotateXMatrix(theta: Float, p: Float): Matrix {
        perspectiveValues[0] = 1f
        perspectiveValues[1] = 0f
        perspectiveValues[2] = 0f
        perspectiveValues[3] = 0f
        perspectiveValues[4] = cos(theta)
        perspectiveValues[5] = 0f
        perspectiveValues[6] = 0f
        perspectiveValues[7] = -sin(theta) / p
        perspectiveValues[8] = 1f
        perspective.setValues(perspectiveValues)
        return perspective
    }

    private fun clipRectPath(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        clipScratch.rewind()
        clipScratch.addRect(left, top, right, bottom, Path.Direction.CW)
        canvas.clipPath(clipScratch)
    }

    private fun softPaint(argb: Int, sigma: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = argb
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
    }

    private fun linear(x0: Float, y0: Float, x1: Float, y1: Float, from: Int, to: Int): Shader =
        LinearGradient(x0, y0, x1, y1, from, to, Shader.TileMode.CLAMP)

    /** Reanimated interpolateColor's default RGB interpolation converts through gamma 2.2. */
    private fun mixChannel(a: Int, b: Int, t: Float): Int {
        val gamma = 2.2
        val from = (a / 255.0).pow(gamma)
        val to = (b / 255.0).pow(gamma)
        return ((from + (to - from) * t).pow(1.0 / gamma) * 255.0).roundToInt().coerceIn(0, 255)
    }

    /** Crops [source] to the target's aspect (fit: cover) and scales it to the pixel size it is drawn at. */
    private fun coverBitmap(source: Bitmap, widthPx: Int, heightPx: Int): Bitmap {
        val scale = max(widthPx / source.width.toFloat(), heightPx / source.height.toFloat())
        val cropW = min(source.width, (widthPx / scale).roundToInt())
        val cropH = min(source.height, (heightPx / scale).roundToInt())
        val cropX = (source.width - cropW) / 2
        val cropY = (source.height - cropH) / 2
        var current = Bitmap.createBitmap(source, cropX, cropY, cropW, cropH)
        // Halve while it is more than twice too big, so the last bilinear step stays sharp and smooth.
        while (current.width > widthPx * 2 && current.height > heightPx * 2) {
            current = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
        }
        return Bitmap.createScaledBitmap(current, widthPx, heightPx, true)
    }
}
