package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Size
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Tone
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sign

/** One rail draw pass. Paths, paints, image shaders and offscreen nodes belong to this instance. */
internal class ReferralRailPainter(
    context: Context,
    private val density: Float,
) {
    private val portraits = ReferralPortraits(context, density)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val line =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val clear =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
    private val mask =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }

    private val clip = Path()
    private val check = Path()
    private var lastWidth = 0f
    private val dim = doubleArrayOf(0.74, 0.74, 0.52, 0.3, 0.3)
    private val soft = doubleArrayOf(0.0, 0.0, 1.1, 3.0, 4.5)
    private val lens = ReferralLens(density)
    private val glowBitmap = makeGlow()
    private val glowBounds = RectF(-100f, -100f, 100f, 100f)

    fun clipRow(
        canvas: Canvas,
        state: ReferralState,
        width: Float,
    ) {
        val rowWidth = Size.ring * 2 + (width - (Size.cardPad * 2 + Size.ring * 2)) * state.peek.value.toFloat()
        clip.rewind()
        clip.addRoundRect(
            width / 2 - rowWidth / 2,
            70f,
            width / 2 + rowWidth / 2,
            154f,
            Size.ring.toFloat(),
            Size.ring.toFloat(),
            Path.Direction.CW,
        )
        canvas.clipOutPath(clip)
    }

    private fun makeGlow(): Bitmap {
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        glowPaint.color = Tone.ink
        glowPaint.maskFilter = BlurMaskFilter(36f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(200f, 200f, 68f, glowPaint)
        return bitmap
    }

    fun background(
        canvas: Canvas,
        state: ReferralState,
        width: Float,
    ) {
        val centerX = width / 2
        val centerY = Size.cy.toFloat()
        val content = width - Size.cardPad * 2
        val peek = state.peek.value.toFloat()
        val closed = (1 - peek).coerceIn(0f, 1f)
        val entered = state.railArrival.toFloat()
        val loose =
            ReferralMath
                .smooth(
                    abs(state.scroll.value - ReferralMath.nearest(state.scroll.value)) * 2,
                ).toFloat()
        val tint = ReferralFriends.blend(ReferralFriends.colors, state.scroll.value)
        if (lastWidth != width) {
            lastWidth = width
            mask.shader =
                LinearGradient(
                    0f,
                    0f,
                    width,
                    0f,
                    intArrayOf(0, Color.BLACK, Color.BLACK, 0),
                    floatArrayOf(0f, 0.3f, 0.7f, 1f),
                    Shader.TileMode.CLAMP,
                )
        }
        canvas.save()
        canvas.translate(centerX, centerY)
        paint.color = tint
        paint.alpha =
            (255 * entered * (0.2 + 0.55 * state.glow.value) * (1 - 0.5 * loose))
                .toInt()
                .coerceIn(0, 255)
        canvas.drawBitmap(glowBitmap, null, glowBounds, paint)
        paint.colorFilter = null
        paint.alpha = 255
        canvas.restore()
        val rowWidth = Size.ring * 2 + (content - Size.ring * 2) * peek
        paint.color = Tone.pane
        paint.alpha = (255 * Tone.paneAlpha * (1 - closed)).toInt()
        canvas.drawRoundRect(
            centerX - rowWidth / 2,
            centerY - Size.ring,
            centerX + rowWidth / 2,
            centerY + Size.ring,
            Size.ring.toFloat(),
            Size.ring.toFloat(),
            paint,
        )
        paint.alpha = 255
    }

    fun outside(
        canvas: Canvas,
        state: ReferralState,
        width: Float,
    ) {
        val centerX = width / 2
        val centerY = Size.cy.toFloat()
        val content = width - Size.cardPad * 2
        val peek = state.peek.value.toFloat()
        val closed = (1 - peek).coerceIn(0f, 1f)
        val entered = state.railArrival.toFloat()
        val row = canvas.saveLayer(0f, 0f, width, Size.railHeight.toFloat(), null)
        for (i in 0..6) {
            val offset = ReferralMath.offset(i, state.scroll.value)
            val distance = abs(offset)
            val alpha =
                max(
                    entered *
                        ReferralMath.ramp(distance, dim) *
                        (1 - ReferralMath.smooth((distance - 3) * 2)),
                    if (state.warm > 0) 0.012 else 0.0,
                ) * closed
            if (alpha <= 0) continue
            val softness = if (state.reduced) 0.0 else ReferralMath.ramp(distance, soft)
            val x =
                centerX +
                    (
                        sign(offset) *
                            (
                                (distance * Size.pitch + Size.tuck * ReferralMath.smooth(distance)) *
                                    (1 + 0.3 * (1 - entered)) + (content / 2 - 36) * peek
                            )
                    ).toFloat()
            canvas.save()
            canvas.translate(x, centerY)
            val layer =
                canvas.saveLayerAlpha(-64f, -64f, 64f, 64f, (alpha * 255).toInt().coerceIn(0, 255))
            portraits.side(canvas, i, softness.toFloat())
            val crisp = max(1 - softness / 1.1, 0.0).toFloat()
            stroke(canvas, 0f, 0f, 25.5f, 1f, 0.1f * crisp)
            badge(
                canvas,
                15f,
                15f,
                0.62f,
                ReferralMath.clamp(state.badges[i].value).toFloat() * crisp,
                1f,
            )
            canvas.restoreToCount(layer)
            canvas.restore()
        }
        canvas.drawRect(0f, 0f, width, Size.railHeight.toFloat(), mask)
        canvas.restoreToCount(row)
    }

    fun lens(
        canvas: Canvas,
        state: ReferralState,
        width: Float,
    ) {
        val centerX = width / 2
        val centerY = Size.cy.toFloat()
        val content = width - Size.cardPad * 2
        val peek = state.peek.value.toFloat()
        val lensWidth = Size.lens * 2 + (content - Size.ring * 2) * peek
        for (i in 0..6) {
            val offset = ReferralMath.offset(i, state.scroll.value)
            val alpha = if (i == state.focus) 1f else 1 - (peek / 0.33f).coerceIn(0f, 1f)
            val x = centerX + (offset * Size.lensPitch - (content / 2 - Size.ring) * peek).toFloat()
            if (alpha <= 0 || x + 60 < centerX - lensWidth / 2 || x - 60 > centerX + lensWidth / 2) {
                continue
            }
            canvas.save()
            canvas.translate(x, centerY)
            val layer = canvas.saveLayerAlpha(-64f, -64f, 64f, 64f, (255 * alpha).toInt())
            portraits.portrait(canvas, i, Size.focus / 2f, false)
            stroke(canvas, 0f, 0f, 35.5f, 1f, 0.1f)
            val mark = state.badges[i].value.toFloat()
            badge(
                canvas,
                19f,
                19f,
                0.6f + 0.4f * mark,
                (mark * 6).coerceIn(0f, 1f),
                ((mark - 0.3f) / 0.6f).coerceIn(0f, 1f),
            )
            canvas.restoreToCount(layer)
            canvas.restore()
        }
    }

    private fun stroke(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        width: Float,
        alpha: Float,
    ) {
        if (alpha <= 0) return
        line.color = Tone.ink
        line.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        line.strokeWidth = width
        canvas.drawCircle(x, y, radius, line)
    }

    private fun badge(
        canvas: Canvas,
        x: Float,
        y: Float,
        scale: Float,
        alpha: Float,
        trim: Float,
    ) {
        if (alpha <= 0) return
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(scale, scale)
        // DST_OUT cuts the portrait away around the check, letting glass show through its gap.
        clear.alpha = (255 * alpha).toInt()
        canvas.drawCircle(0f, 0f, 10.5f, clear)
        clear.alpha = 255
        paint.color = Tone.ink
        paint.alpha = (alpha * 255).toInt()
        canvas.drawCircle(0f, 0f, 8f, paint)
        paint.alpha = 255
        check.rewind()
        check.moveTo(-3.3f, 0.2f)
        val first = hypot(2.3f, 2.3f)
        val second = hypot(4.4f, -4.7f)
        val length = trim * (first + second)
        if (length < first) {
            check.lineTo(-3.3f + 2.3f * length / first, 0.2f + 2.3f * length / first)
        } else {
            check.lineTo(-1f, 2.5f)
            val progress = (length - first) / second
            check.lineTo(-1 + 4.4f * progress, 2.5f - 4.7f * progress)
        }
        line.color = Tone.night
        line.alpha = (alpha * 255).toInt()
        line.strokeWidth = 2f
        canvas.drawPath(check, line)
        canvas.restore()
    }

    fun clipLens(
        canvas: Canvas,
        state: ReferralState,
        width: Float,
    ) = lens.clipLens(canvas, state, width)

    fun foreground(
        canvas: Canvas,
        state: ReferralState,
        width: Float,
    ) = lens.draw(canvas, state, width)
}
