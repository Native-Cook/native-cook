@file:androidx.annotation.RequiresApi(33)

package com.ngocdevv.nativecook.blocks.animations.emberchargewidget

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.E
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The sharp tube and its two bounded blooms share the same moving fill. */
internal class EmberBar(private val density: Float) {
    private val tube = RuntimeShader(EmberTubeShader)
    private val bloom = RuntimeShader(EmberTubeShader)
    private val brush = EmberPaint()
    private val paint get() = brush.value
    private val broad = RenderNode("Ember broad tube bloom")
    private val tight = RenderNode("Ember tight tube bloom")
    private val cardClip = Path().apply { addRoundRect(0f, 0f, E.size, E.size, E.radius, E.radius, Path.Direction.CW) }

    init {
        listOf(tube, bloom).forEach { it.setFloatUniform("density", density) }
        for ((node, radius) in listOf(broad to E.bar.bloom, tight to E.bar.core)) {
            node.setPosition(
                0, 0, ((E.bar.w + 2 * E.bar.pad) * density).toInt(),
                ((E.bar.h + 2 * E.bar.pad) * density).toInt(),
            )
            node.setRenderEffect(
                RenderEffect.createBlurEffect(radius * density, radius * density, Shader.TileMode.DECAL),
            )
        }
    }

    fun draw(canvas: Canvas, motion: EmberMotion) {
        // Fuse and ring are temporal envelopes; opacity coefficients are the RN bloom recipe.
        val age = motion.ignition * M.ignite
        val lit = !motion.reduced && age > 0
        val fuse = if (lit) min(1f, age / M.bar.fuseIn) * exp(-max(0f, age - M.bar.fuseIn) / M.bar.fuseOut) else 0f
        for (shader in listOf(tube, bloom)) {
            shader.setFloatUniform("width", max(E.bar.h / 2, motion.progress.coerceIn(0f, 1f) * E.bar.w))
            shader.setFloatUniform("heat", if (motion.reduced) 0f else motion.glow)
            shader.setFloatUniform("drift", if (motion.reduced) 0f else motion.drift)
            shader.setFloatUniform(
                "bend",
                if (motion.reduced) 0f else (motion.meniscus + 3 * motion.slosh) * min(1f, (1 - motion.progress) * 12),
            )
            shader.setFloatUniform(
                "press", if (motion.reduced) 0f else (motion.pressure + 0.12f * motion.charge) * (1 - fuse),
            )
            shader.setFloatUniform("fuse", fuse)
            shader.setFloatUniform(
                "ring", if (lit) exp(-M.bar.ringDecay * age) * sin(M.bar.ringFrequency * age) else 0f,
            )
            shader.setFloatUniform("aimX", if (motion.reduced) 0f else motion.aimX)
            shader.setFloatUniform("bloomOnly", if (shader === bloom) 1f else 0f)
        }
        canvas.save()
        canvas.clipPath(cardClip)
        canvas.translate(E.bar.x, E.bar.y)
        canvas.scale(1 / density, 1 / density)
        if (motion.quality != EmberQuality.Low) {
            bloom(canvas, broad, 0.65f + if (motion.reduced) 0f else 0.12f * motion.glow + 0.25f * fuse)
            if (motion.quality == EmberQuality.High) bloom(
                canvas, tight, 0.22f + if (motion.reduced) 0f else 0.18f * fuse,
            )
        }
        brush.reset(shader = tube)
        canvas.drawRect(
            -E.bar.pad * density, -E.bar.pad * density, (E.bar.w + E.bar.pad) * density,
            (E.bar.h + E.bar.pad) * density, paint,
        )
        canvas.restore()
    }

    private fun bloom(canvas: Canvas, node: RenderNode, alpha: Float) {
        val recording = node.beginRecording()
        recording.translate(E.bar.pad * density, E.bar.pad * density)
        brush.reset(shader = bloom)
        recording.drawRect(
            -E.bar.pad * density, -E.bar.pad * density, (E.bar.w + E.bar.pad) * density,
            (E.bar.h + E.bar.pad) * density, paint,
        )
        node.endRecording()
        node.alpha = alpha.coerceIn(0f, 1f)
        canvas.save()
        canvas.translate(-E.bar.pad * density, -E.bar.pad * density)
        canvas.drawRenderNode(node)
        canvas.restore()
    }
}
