@file:androidx.annotation.RequiresApi(33)

package com.ngocdevv.nativecook.blocks.animations.emberchargewidget

import android.graphics.Canvas
import android.graphics.RuntimeShader
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberMotion.Companion.seg
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.E
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.drawing
import kotlin.math.max

/** The glass pass: dp geometry enters the shader after pixel-to-dp conversion. */
internal class EmberGlass(private val density: Float) {
    private val glass = RuntimeShader(EmberGlassShader)
    private val brush = EmberPaint()
    private val paint get() = brush.value

    init {
        glass.setFloatUniform("density", density)
        glass.setFloatUniform("size", E.size, E.size)
        glass.setFloatUniform("bleed", E.bleed)
        glass.setFloatUniform("radius", E.corner)
    }

    fun draw(canvas: Canvas, motion: EmberMotion) {
        // Fractions below are the reference flash/ripple envelopes, not layout dimensions.
        val ignition = motion.ignition
        val animated = !motion.reduced
        val quality = motion.quality
        glass.setFloatUniform("time", if (animated) motion.time else 0f)
        glass.setFloatUniform("heat", motion.glow)
        glass.setFloatUniform("rise", M.rise.rest + (M.rise.hot - M.rise.rest) * motion.glow)
        glass.setFloatUniform("slosh", if (animated) motion.slosh else 0f)
        glass.setFloatUniform("ripple", if (animated) seg(ignition, 0f, 0.73f) else 0f)
        glass.setFloatUniform("origin", E.bar.x + max(E.bar.h / 2, motion.progress * E.bar.w), E.bar.centerY)
        glass.setFloatUniform(
            "flash",
            if (!animated || ignition <= 0) 0f else if (ignition < 0.055f) ignition / 0.055f else 1 - seg(
                ignition, 0.055f, 0.44f,
            ),
        )
        glass.setFloatUniform(
            "lightLean",
            if (animated && quality == EmberQuality.High) (motion.slosh * 2.4f).coerceIn(
                -0.35f, 0.35f,
            ) + motion.pressure * 0.08f else 0f,
        )
        glass.setFloatUniform(
            "sweep", if (animated && quality == EmberQuality.High) seg(ignition * M.ignite, 0f, 0.5f) else 0f,
        )
        glass.setFloatUniform("aim", if (animated) motion.aimX else 0f, if (animated) motion.aimY else 0f)
        glass.setFloatUniform("touchLight", motion.touchLight)
        glass.setFloatUniform(
            "touchRadius",
            if (animated) drawing.touchRadius + drawing.touchGrowth * motion.touchSpread else drawing.touchRadius,
        )
        glass.setFloatUniform("materialise", if (animated) motion.enter else 1f)
        glass.setFloatUniform(
            "quality",
            when (quality) {
                EmberQuality.Low -> 0f
                EmberQuality.Medium -> 1f
                else -> 2f
            },
        )
        canvas.save()
        canvas.translate(-E.bleed, -E.bleed)
        canvas.scale(1 / density, 1 / density)
        brush.reset(shader = glass)
        canvas.drawRect(0f, 0f, E.canvasSize * density, E.canvasSize * density, paint)
        canvas.restore()
    }
}
