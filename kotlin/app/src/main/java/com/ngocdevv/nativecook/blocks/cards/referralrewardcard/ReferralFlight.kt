package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Size
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Tone
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Trail
import kotlin.math.pow
import kotlin.math.roundToInt

// Both comets share the canvas over the card. Their waiting dot lives here too, so the
// launch never hands a visible head from one renderer to another.
internal class ReferralFlight(
    private val density: Float,
) {
    private val comet = ReferralComet()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    // A separate blur pass gives the white ribbon a colored halo without softening its core.
    private val glow =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL)
        }
    private val glowNode =
        if (Build.VERSION.SDK_INT >= 31) RenderNode("Referral comet glow") else null
    private val glowEffect =
        if (Build.VERSION.SDK_INT >= 31) {
            RenderEffect.createBlurEffect(5 * density, 5 * density, Shader.TileMode.DECAL)
        } else {
            null
        }

    fun draw(
        canvas: Canvas,
        state: ReferralState,
        width: Float,
        height: Float,
        markX: Float,
        markY: Float,
    ) {
        if (state.reduced) return
        val route = state.path
        val t = state.comet.value
        comet.path.rewind()
        if (route != null && t < Trail.end) {
            if (t < 0) {
                if ((if (state.fromCapsule) state.capsuleShape.value else state.shape.value) >= 1) {
                    comet.path.addCircle(
                        route.originX.toFloat(),
                        route.originY.toFloat(),
                        6f,
                        Path.Direction.CW,
                    )
                }
            } else {
                comet.ribbon(t, 6.0, route, 0.0, 0.0, 0.0, 0.0)
            }
            drawTrail(canvas, state, width, height)
        }
        val flight = state.flight.value
        if (flight > 0 && flight < Trail.end) {
            comet.path.rewind()
            comet.ribbon(
                flight,
                0.0,
                null,
                width / 2.0,
                state.railTop + (Size.cy - Size.pointsRise),
                markX.toDouble(),
                markY.toDouble(),
            )
            drawTrail(canvas, state, width, height)
            val landed = ((flight - 1) / 0.2).coerceIn(0.0, 1.0)
            if (landed > 0 && landed < 1) {
                line.color = Tone.ink
                line.alpha = (255 * 0.55 * (1 - landed).pow(1.4)).toInt()
                line.strokeWidth = 1.25f
                canvas.drawCircle(
                    markX,
                    markY,
                    (15 + 13 * ReferralMath.quart(landed)).toFloat(),
                    line,
                )
            }
        }
    }

    private fun drawTrail(
        canvas: Canvas,
        state: ReferralState,
        width: Float,
        height: Float,
    ) {
        val tint = ReferralFriends.blend(ReferralFriends.colors, state.scroll.value)
        if (Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated) {
            val node = glowNode!!
            node.setPosition(0, 0, (width * density).roundToInt(), (height * density).roundToInt())
            node.setRenderEffect(glowEffect)
            val recordingCanvas = node.beginRecording()
            recordingCanvas.scale(density, density)
            paint.color = tint
            paint.alpha = 255
            recordingCanvas.drawPath(comet.path, paint)
            node.endRecording()
            canvas.save()
            canvas.scale(1 / density, 1 / density)
            canvas.drawRenderNode(node)
            canvas.restore()
        } else {
            glow.color = tint
            canvas.drawPath(comet.path, glow)
        }
        paint.color = Tone.ink
        paint.alpha = 255
        canvas.drawPath(comet.path, paint)
    }
}
