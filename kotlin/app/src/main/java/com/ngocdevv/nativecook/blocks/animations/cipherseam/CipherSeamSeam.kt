package com.ngocdevv.nativecook.blocks.animations.cipherseam

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * `Seam`: a hairline of white inside two violet glows, tapering at both ends, over the card's lane.
 * Idle it is a thin lens of light; while the card crosses (5.0 to 8.1 s) it flares into a white band
 * about 12 dp wide with a violet edge, whose right side breaks into particles over the token
 * matrix, and sparks peel off the boundary into the token side. It draws over the planes; nothing in
 * it takes touch. Reduce Motion ([reduced]) holds the seam at a steady 35% flare.
 */
@Composable
internal fun CipherSeamSeamLayer(
    clock: CipherSeamClock,
    width: Float,
    height: Float,
    cardTop: Float,
    cardHeight: Float,
    reduced: Boolean,
) {
    val painter = remember(width, height, cardTop, cardHeight) { CipherSeamSeamPainter(width, height, cardTop, cardHeight) }
    ComposeCanvas(Modifier.size(width.dp, height.dp)) {
        val t = clock.time
        scale(density, density, pivot = Offset.Zero) {
            drawIntoCanvas { painter.draw(it.nativeCanvas, t, reduced) }
        }
    }
}

/**
 * The paints and the frame maths of the seam, in the dp of the hero (the canvas is scaled by the
 * screen density before it is called).
 *
 * `Blur` of Skia is a blur of the finished shape; here it is a mask filter, which is the same for a
 * single colour, and for the three gradient rectangles the gradient is baked blurred
 * ([CipherSeamBlurRamp]). A mask filter needs API 28 on a hardware canvas, so below it the two wide
 * glows are left out and the small blurs are drawn sharp.
 */
private class CipherSeamSeamPainter(
    private val width: Float,
    height: Float,
    private val cardTop: Float,
    private val cardHeight: Float,
) {
    private val centerX = width / 2f
    private val bounds = RectF(0f, 0f, width, height)
    private val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    // `tall` and `top`: the glows are 1.2 card heights tall, centred on the card.
    private val tall = cardHeight * 1.2f
    private val top = cardTop + cardHeight / 2f - tall / 2f

    private val wideGlow = CipherSeamBlurRamp(
        intArrayOf(0x007A4DFF, 0xFF7A4DFF.toInt(), 0x007A4DFF),
        from = top,
        length = tall,
        sigma = 18f,
        opacity = 0.55f,
    ).also { soften(it.paint, 18f) }
    private val bloom = CipherSeamBlurRamp(
        intArrayOf(0x009A6BFF, 0xFFA884FF.toInt(), 0x009A6BFF),
        from = top + tall * 0.08f,
        length = tall * 0.84f,
        sigma = 5f,
        opacity = 1f,
    ).also { soften(it.paint, 5f) }
    private val hairline = CipherSeamBlurRamp(
        intArrayOf(0x00FFFFFF, Color.WHITE, 0x00FFFFFF),
        from = top + tall * 0.14f,
        length = tall * 0.72f,
        sigma = 0.7f,
        opacity = 1f,
    ).also { soften(it.paint, 0.7f) }
    private val coreGlow = solid(0xFF7B4BFF.toInt(), 5f)
    private val core = solid(Color.WHITE, 1.4f)
    private val edge = solid(Color.WHITE, 0f)
    private val sparks = solid(Color.WHITE, 0f)

    fun draw(canvas: Canvas, t: Double, reduced: Boolean) {
        val flare = CipherSeamTimeline.flare(t, reduced).toFloat()
        val glow = CipherSeamTimeline.glow(t, reduced).toFloat()

        // Group 1, at the glow's opacity: the wide violet glow, the lilac bloom, the white hairline.
        val glowLayer = canvas.saveLayerAlpha(bounds, (glow * 255f).roundToInt().coerceIn(0, 255))
        if (blurs) {
            canvas.drawRect(centerX - 22f, wideGlow.top, centerX + 22f, wideGlow.bottom, wideGlow.paint)
            val bloomX = centerX - 5f - flare * 4f
            val bloomWidth = 10f + flare * 4f
            canvas.drawRect(bloomX, bloom.top, bloomX + bloomWidth, bloom.bottom, bloom.paint)
        }
        canvas.drawRect(centerX - 0.8f, hairline.top, centerX + 0.8f, hairline.bottom, hairline.paint)
        canvas.restoreToCount(glowLayer)

        // Group 2, at the flare: the band, its violet edge and the broken-up right side.
        if (flare > 0f) {
            val bandLayer = canvas.saveLayerAlpha(bounds, (flare * 255f).roundToInt().coerceIn(0, 255))
            val coreTop = cardTop - cardHeight * 0.02f * flare
            val coreHeight = cardHeight * (1f + 0.04f * flare)
            val coreWidth = 1.6f + flare * 10.4f
            val coreX = centerX - 1f - flare * 1f
            canvas.drawRect(centerX - 9f, coreTop, centerX + 3f, coreTop + coreHeight, coreGlow)
            canvas.drawRect(coreX, coreTop, coreX + coreWidth, coreTop + coreHeight, core)
            drawEdge(canvas, t, flare)
            canvas.restoreToCount(bandLayer)
        }

        drawSparks(canvas, t, flare)
    }

    /**
     * `edge`: 60 rows down the card, each a white bar whose length jitters 24 times a second, and
     * now and then a single speck a little further out.
     */
    private fun drawEdge(canvas: Canvas, t: Double, flare: Float) {
        if (flare <= 0.01f) return
        val frame = floor(t * 24).toInt()
        val rowHeight = cardHeight / 60f
        for (row in 0 until 60) {
            val jitter = CipherSeamTimeline.hash(row * 31 + frame * 7)
            val reach = CipherSeamTimeline.hash(row * 17 + frame * 13 + 5)
            val y = cardTop + row * rowHeight
            val x = centerX + 11f * flare - 1f
            canvas.drawRect(x, y, x + ((1 + jitter * 7) * flare).toFloat(), y + rowHeight + 0.4f, edge)
            if (reach > 0.4) {
                val speckX = (centerX + 11f * flare + 4f + reach * reach * 16).toFloat()
                canvas.drawRect(speckX, y, speckX + 1.3f, y + 1.3f, edge)
            }
        }
    }

    /** `sparks`: 320 points peeling off the boundary into the token side, shrinking as they age. */
    private fun drawSparks(canvas: Canvas, t: Double, flare: Float) {
        if (flare <= 0.01f) return
        for (i in 0 until CipherSeamParticles.SPARK_COUNT) {
            val age = (t * CipherSeamParticles.sparkSpeed[i] + CipherSeamParticles.sparkOffset[i]) % 1.0
            val x = centerX + 10f * flare + age * age * (10 + CipherSeamParticles.sparkReach[i] * 40)
            val y = cardTop + 2f + CipherSeamParticles.sparkY[i] * (cardHeight - 4f) + sin(age * 7 + i) * 1.2
            val radius = CipherSeamParticles.sparkRadius[i] * (1 - age * 0.7) * flare
            canvas.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), sparks)
        }
    }

    /** A white or violet fill, its edges softened by [sigma] (Skia's `Blur`), when the device can. */
    private fun solid(color: Int, sigma: Float): Paint {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        if (sigma > 0f) soften(paint, sigma)
        return paint
    }

    private fun soften(paint: Paint, sigma: Float) {
        if (blurs) paint.maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
    }
}
