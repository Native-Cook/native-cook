@file:androidx.annotation.RequiresApi(33)

package com.ngocdevv.nativecook.blocks.animations.emberchargewidget

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.Shader
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.E
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.drawing
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.emberHeartTone

/** A smoked vessel: floor contact stays fixed while pool, rims and crescent pulse. */
internal class EmberHeart {
    private val brush = EmberPaint()
    private val paint get() = brush.value
    private val crescent = Path().apply {
        addArc(
            drawing.lobeX - drawing.crescentRadius, drawing.lobeY - drawing.crescentRadius,
            drawing.lobeX + drawing.crescentRadius, drawing.lobeY + drawing.crescentRadius,
            drawing.crescentStart, drawing.crescentSweep,
        )
    }
    private val body = LinearGradient(
        E.heart.cx, drawing.bodyTop, E.heart.cx, drawing.bodyBottom,
        intArrayOf(emberHeartTone.bodyTop, emberHeartTone.bodyMiddle, emberHeartTone.bodyTip),
        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
    )
    private val depth = LinearGradient(
        E.heart.cx, drawing.bodyTop, E.heart.cx, drawing.depthBottom,
        intArrayOf(emberHeartTone.depthTop, emberHeartTone.depthClear), null, Shader.TileMode.CLAMP,
    )
    private val rim = LinearGradient(
        E.heart.cx, drawing.rimTop, E.heart.cx, drawing.bodyBottom,
        intArrayOf(emberHeartTone.rimEmberClear, emberHeartTone.rimEmber), null, Shader.TileMode.CLAMP,
    )
    private val key = LinearGradient(
        drawing.keyX, drawing.keyY, drawing.keyEndX, drawing.keyEndY,
        intArrayOf(emberHeartTone.rimKey, emberHeartTone.rimKeyClear), floatArrayOf(0f, 0.18f), Shader.TileMode.CLAMP,
    )
    private val blur = BlurMaskFilter(drawing.heartBlur, BlurMaskFilter.Blur.NORMAL)

    fun draw(canvas: Canvas, motion: EmberMotion) {
        // Opacities and gradient stops below retain the reference vessel material recipe.
        val beatEnvelope = if (motion.reduced) 0f else ((EmberMotion.beat(motion.phase) - 1) / 0.12f).coerceIn(0f, 1f)
        val poolBeat = if (motion.quality == EmberQuality.Low) 0f else beatEnvelope
        canvas.save()
        canvas.translate(E.heart.x, E.heart.y)
        canvas.clipRect(
            -drawing.heartClipInset, -drawing.heartClipInset,
            drawing.heartClipSize - drawing.heartClipInset, drawing.heartClipSize - drawing.heartClipInset,
        )
        // Contact filters remain on the floor, outside the pulse transform.
        if (motion.quality != EmberQuality.Low) {
            brush.reset(emberHeartTone.contactShadow, 0.18f)
            paint.maskFilter = blur
            canvas.save()
            canvas.translate(0f, drawing.contactDrop)
            canvas.drawPath(EmberPaths.heart, paint)
            canvas.restore()
            brush.reset(emberHeartTone.contactCaustic, 0.16f + 0.22f * beatEnvelope)
            paint.maskFilter = blur
            canvas.drawOval(
                E.heart.cx - drawing.causticRx, drawing.causticY - drawing.causticRy,
                E.heart.cx + drawing.causticRx, drawing.causticY + drawing.causticRy, paint,
            )
        }
        canvas.scale(1 + 0.12f * beatEnvelope, 1 + 0.12f * beatEnvelope, E.heart.cx, E.heart.cy)
        brush.reset(alpha = 0.86f, shader = body)
        canvas.drawPath(EmberPaths.heart, paint)
        // Clipping full strokes inside the path leaves half-width inner walls.
        canvas.clipPath(EmberPaths.heart)
        val pool = LinearGradient(
            E.heart.cx, drawing.poolRest - drawing.poolHeat * motion.glow - drawing.poolBeat * poolBeat,
            E.heart.cx, drawing.bodyBottom,
            intArrayOf(emberHeartTone.poolSurface, emberHeartTone.poolMiddle, emberHeartTone.poolTip),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        brush.reset(alpha = 0.78f + 0.22f * poolBeat, shader = pool)
        canvas.drawPath(EmberPaths.heart, paint)
        brush.reset(shader = depth, stroke = drawing.depthStroke)
        canvas.drawPath(EmberPaths.heart, paint)
        brush.reset(alpha = 0.55f + 0.35f * beatEnvelope, shader = rim, stroke = drawing.rimStroke)
        canvas.drawPath(EmberPaths.heart, paint)
        canvas.save()
        canvas.clipRect(0f, 0f, E.heart.cx, drawing.rimTop)
        brush.reset(alpha = 0.3f, shader = key, stroke = drawing.rimStroke)
        canvas.drawPath(EmberPaths.heart, paint)
        canvas.restore()
        brush.reset(emberHeartTone.specularCrescent, 0.5f, stroke = drawing.crescentStroke)
        canvas.drawPath(crescent, paint)
        canvas.restore()
    }
}
