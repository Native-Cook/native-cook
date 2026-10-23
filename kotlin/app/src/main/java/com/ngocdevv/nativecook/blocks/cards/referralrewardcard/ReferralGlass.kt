package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Size
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Tone
import kotlin.math.max

/** Same measured body for AGSL and the white collapse overlay. No per-frame uniform arrays. */
internal class ReferralGlass {
    private val light = if (Build.VERSION.SDK_INT >= 33) ReferralLight() else null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shader = if (Build.VERSION.SDK_INT >= 33) createShader() else null
    private val pill = FloatArray(4)
    private val look = FloatArray(3)
    private var lastWidth = 0f
    private var lastHeight = 0f
    private var lastLens = 0f
    private var lastDensity = 0f
    private val scalar = FloatArray(1)
    private val pair = FloatArray(2)
    private var bodyWidth = 0f

    private var bodyHeight = 0f

    private var bodyRadius = 0f

    private var visible = false

    fun draw(
        canvas: Canvas,
        state: ReferralState,
        width: Float,
        height: Float,
        buttonWidth: Float,
        buttonHeight: Float,
        density: Float,
        opaque: Boolean,
    ) {
        val shape = if (state.reduced) 0f else state.shape.value.toFloat()
        val press = state.press.value.toFloat()
        val scale = 1 - 0.04f * press * (1 - shape)
        bodyWidth = (buttonWidth + (Size.buttonDot - buttonWidth) * shape) * scale
        bodyHeight = (buttonHeight + (Size.buttonDot - buttonHeight) * shape) * scale
        bodyRadius =
            (
                max(Size.buttonHeight / 2f, buttonHeight / 2) +
                    (Size.buttonDot / 2 - max(Size.buttonHeight / 2f, buttonHeight / 2)) * shape
            ) *
            scale
        val t = state.comet.value * (state.path?.total ?: 1.0)
        visible =
            state.reduced ||
            !(shape >= 1 || (!state.fromCapsule && state.comet.value < 1 && t >= 0 && t < 120))
        val buttonY =
            height - Size.cardPad - buttonHeight / 2 +
                (if (state.reduced) 0f else (8 * (1 - state.part(2))).toFloat())
        val glassShader = shader
        if (glassShader != null && Build.VERSION.SDK_INT >= 33) {
            if (
                width != lastWidth ||
                height != lastHeight ||
                state.railTop.toFloat() != lastLens ||
                density != lastDensity
            ) {
                lastWidth = width
                lastHeight = height
                lastLens = state.railTop.toFloat()
                lastDensity = density
                uniform(glassShader, "origin", Size.glassBleed.toFloat(), Size.glassBleed.toFloat())
                uniform(glassShader, "card", width, height)
                uniform(glassShader, "radius", Size.cardRadius.toFloat())
                uniform(glassShader, "bleed", Size.glassBleed.toFloat())
                uniform(glassShader, "scale", density)
            }
            light!!.update(glassShader, state, width, height, lastLens + Size.cy)
            uniform(glassShader, "phase", state.phase.value.toFloat())
            uniform(glassShader, "light", state.railArrival.toFloat())
            uniform(glassShader, "flare", state.flash.value.toFloat())
            uniform(glassShader, "opaque", if (opaque) 1f else 0f)
            pill[0] = width / 2
            pill[1] = buttonY
            pill[2] = bodyWidth / 2
            pill[3] = bodyHeight / 2
            look[0] = bodyRadius
            look[1] = if (visible) state.part(2).toFloat() else 0f
            look[2] = press
            ReferralShaderUniforms.set(glassShader, "pill", pill)
            ReferralShaderUniforms.set(glassShader, "pillLook", look)
            paint.shader = glassShader
            canvas.save()
            canvas.translate(-Size.glassBleed.toFloat(), -Size.glassBleed.toFloat())
            canvas.drawRect(0f, 0f, width + Size.glassBleed * 2, height + Size.glassBleed * 2, paint)
            canvas.restore()
            paint.shader = null
        } else {
            drawOpaque(canvas, state, width, height, buttonY, press, density)
        }
    }

    private fun drawOpaque(
        canvas: Canvas,
        state: ReferralState,
        width: Float,
        height: Float,
        buttonY: Float,
        press: Float,
        density: Float,
    ) {
        paint.color = Tone.night
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(0f, 0f, width, height, Size.cardRadius.toFloat(), Size.cardRadius.toFloat(), paint)
        paint.color = 0x1FFFFFFF
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(0.5f, 0.5f, width - 0.5f, height - 0.5f, 31.5f, 31.5f, paint)
        if (visible) {
            paint.style = Paint.Style.FILL
            val shade = (28 + 10 * press).toInt()
            paint.color = android.graphics.Color.rgb(shade, shade, shade + 2)
            paint.alpha = (255 * state.part(2)).toInt()
            canvas.drawRoundRect(
                width / 2 - bodyWidth / 2,
                buttonY - bodyHeight / 2,
                width / 2 + bodyWidth / 2,
                buttonY + bodyHeight / 2,
                bodyRadius,
                bodyRadius,
                paint,
            )
            paint.color = 0x1AFFFFFF
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1 / density
            val inset = 0.5f / density
            canvas.drawRoundRect(
                width / 2 - bodyWidth / 2 + inset,
                buttonY - bodyHeight / 2 + inset,
                width / 2 + bodyWidth / 2 - inset,
                buttonY + bodyHeight / 2 - inset,
                max(0f, bodyRadius - inset),
                max(0f, bodyRadius - inset),
                paint,
            )
        }
        paint.style = Paint.Style.FILL
    }

    @RequiresApi(33)
    private fun uniform(
        shader: RuntimeShader,
        name: String,
        x: Float,
    ) {
        scalar[0] = x
        ReferralShaderUniforms.set(shader, name, scalar)
    }

    @RequiresApi(33)
    private fun uniform(
        shader: RuntimeShader,
        name: String,
        x: Float,
        y: Float,
    ) {
        pair[0] = x
        pair[1] = y
        ReferralShaderUniforms.set(shader, name, pair)
    }

    @RequiresApi(33)
    private fun createShader(): RuntimeShader? =
        try {
            RuntimeShader(referralAgsl)
        } catch (_: IllegalArgumentException) {
            null
        }
}
