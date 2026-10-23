package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Size
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Tone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// Vignette and shine sit inside the lens; the ring, progress arc and ticks sit above it.
internal class ReferralLens(
    private val density: Float,
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val line =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val clip = Path()
    private val ticks = Path()
    private val rect = RectF()
    private val vignette =
        RadialGradient(
            0f,
            0f,
            Size.lens.toFloat(),
            intArrayOf(0, 0, 0x4D000000),
            floatArrayOf(0f, 0.74f, 1f),
            Shader.TileMode.CLAMP,
        )
    private val shineBitmap = makeShine()
    private val shineBounds = RectF(-40f, -40f, 40f, 40f)

    private fun makeShine(): Bitmap {
        val side = (80 * density).roundToInt()
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(density, density)
        canvas.translate(40f, 40f)
        val shinePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x4DFFFFFF
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
                strokeCap = Paint.Cap.ROUND
                maskFilter = BlurMaskFilter(0.8f, BlurMaskFilter.Blur.NORMAL)
            }
        canvas.drawArc(-33.5f, -33.5f, 33.5f, 33.5f, -152f, 44f, false, shinePaint)
        return bitmap
    }

    fun clipLens(
        canvas: Canvas,
        state: ReferralState,
        width: Float,
    ) {
        val lensWidth = Size.lens * 2 + (width - (Size.cardPad * 2 + Size.ring * 2)) * state.peek.value.toFloat()
        clip.rewind()
        clip.addRoundRect(
            width / 2 - lensWidth / 2,
            75f,
            width / 2 + lensWidth / 2,
            149f,
            Size.lens.toFloat(),
            Size.lens.toFloat(),
            Path.Direction.CW,
        )
        canvas.clipPath(clip)
    }

    fun draw(
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
        val rowWidth = Size.ring * 2 + (content - Size.ring * 2) * peek
        drawSurface(canvas, state, width, centerX, centerY, closed, entered)
        drawWaves(canvas, state, centerX, centerY)
        drawRing(canvas, state, centerX, centerY, rowWidth, closed, entered, loose)
        drawTicks(canvas, state, centerX, centerY, closed, entered, loose)
        val ripple = state.ripple.value
        stroke(
            canvas,
            centerX,
            centerY - Size.pointsRise,
            (12 + 20 * ReferralMath.quart(ripple)).toFloat(),
            1.25f,
            (0.45 * (1 - ripple).pow(1.6)).toFloat(),
        )
    }

    private fun drawSurface(
        canvas: Canvas,
        state: ReferralState,
        width: Float,
        centerX: Float,
        centerY: Float,
        closed: Float,
        entered: Float,
    ) {
        canvas.save()
        val lensScale = ((1 + 0.05 * state.pop.value) * (0.9 + 0.1 * entered)).toFloat()
        canvas.scale(lensScale, lensScale, centerX, centerY)
        clipLens(canvas, state, width)
        canvas.save()
        canvas.translate(centerX, centerY)
        paint.shader = vignette
        paint.alpha = (closed * 255).toInt()
        canvas.drawCircle(0f, 0f, Size.lens.toFloat(), paint)
        paint.shader = null
        paint.alpha = 255
        paint.color = Tone.ink
        paint.alpha = (closed * 255).toInt()
        canvas.drawBitmap(shineBitmap, null, shineBounds, paint)
        paint.alpha = 255
        canvas.restore()
        canvas.restore()
    }

    private fun drawWaves(
        canvas: Canvas,
        state: ReferralState,
        centerX: Float,
        centerY: Float,
    ) {
        val wave = state.wave.value.toFloat()
        stroke(
            canvas,
            centerX,
            centerY,
            Size.ring + 40 * wave,
            0.5f + 1.5f * (1 - wave),
            (0.5 * (1 - wave).toDouble().pow(1.5)).toFloat(),
        )
        val crest = state.crest.value.toFloat()
        stroke(
            canvas,
            centerX,
            centerY,
            Size.ring + 58 * crest,
            0.5f + 2.5f * (1 - crest),
            (0.7 * (1 - crest).toDouble().pow(1.4)).toFloat(),
        )
    }

    private fun drawRing(
        canvas: Canvas,
        state: ReferralState,
        centerX: Float,
        centerY: Float,
        rowWidth: Float,
        closed: Float,
        entered: Float,
        loose: Float,
    ) {
        canvas.save()
        val ringScale = (Size.ring + (2.5f * loose - 1.2f * state.lock.value.toFloat()) * closed) / Size.ring
        canvas.scale(ringScale, ringScale, centerX, centerY)
        line.color = Tone.ink
        line.alpha = (Tone.ringTrackAlpha * entered * (1 - 0.45 * loose) * 255).toInt()
        line.strokeWidth = Size.ringStroke
        canvas.drawRoundRect(
            centerX - rowWidth / 2,
            centerY - Size.ring,
            centerX + rowWidth / 2,
            centerY + Size.ring,
            Size.ring.toFloat(),
            Size.ring.toFloat(),
            line,
        )
        stroke(canvas, centerX, centerY, Size.ring.toFloat(), 2.5f, state.flash.value.toFloat() * closed)
        // A nearly complete sweep avoids the seam Android draws for an exact 360-degree arc.
        if (state.arcEnd.value - state.arcStart.value >= 0.004) {
            line.color = Tone.ink
            line.alpha = (255 * entered * (1 - 0.3 * loose) * closed).toInt()
            line.strokeWidth = (Size.arcStroke + 1.25 * state.flash.value).toFloat()
            rect.set(centerX - Size.ring, centerY - Size.ring, centerX + Size.ring, centerY + Size.ring)
            val arcStart = ReferralMath.clamp(state.arcStart.value)
            val arcEnd = ReferralMath.clamp(state.arcEnd.value)
            canvas.drawArc(
                rect,
                (-90 + 359.99 * arcStart).toFloat(),
                (359.99 * (arcEnd - arcStart)).toFloat(),
                false,
                line,
            )
        }
        canvas.restore()
    }

    private fun drawTicks(
        canvas: Canvas,
        state: ReferralState,
        centerX: Float,
        centerY: Float,
        closed: Float,
        entered: Float,
        loose: Float,
    ) {
        val kick = state.kick.value
        val lock = state.lock.value
        val start = Size.ring + (Size.tickFrom - Size.ring + 6 * kick + 2 * loose) * closed
        val length = max((Size.tickLen * (1 - 0.5 * loose) + 9 * kick + 4 * lock) * entered * closed, 0.0)
        ticks.rewind()
        for (i in 0..3) {
            val angle = PI / 4 + i * PI / 2
            val x = cos(angle)
            val y = sin(angle)
            ticks.moveTo(centerX + (x * start).toFloat(), centerY + (y * start).toFloat())
            ticks.lineTo(
                centerX + (x * (start + length)).toFloat(),
                centerY + (y * (start + length)).toFloat(),
            )
        }
        line.color = Tone.ink
        line.alpha =
            (
                255 *
                    closed *
                    entered *
                    min(0.38 - 0.16 * loose + 0.5 * max(kick, 0.0) + 0.2 * lock, 1.0)
            ).toInt()
                .coerceIn(0, 255)
        line.strokeWidth = 1.25f
        canvas.drawPath(ticks, line)
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
}
