@file:androidx.annotation.RequiresApi(33)

package com.ngocdevv.nativecook.blocks.animations.emberchargewidget

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.E
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.drawing
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.emberTone

/** The header disc and original pictograms on the dark face. */
internal class EmberSurface {
    private val brush = EmberPaint()
    private val paint get() = brush.value
    private val disc = RadialGradient(
        E.disc.cx, E.disc.cy - drawing.discLightOffset, E.disc.d / 2 + drawing.discLightOffset,
        intArrayOf(emberTone.discTop, emberTone.discBottom), null, Shader.TileMode.CLAMP,
    )

    fun draw(canvas: Canvas) {
        brush.reset(shader = disc)
        canvas.drawCircle(E.disc.cx, E.disc.cy, E.disc.d / 2, paint)
        glyph(
            canvas, EmberPaths.walkFill, EmberPaths.walkStroke, E.disc.cx - drawing.walkOffset,
            E.disc.cy - drawing.walkOffset, drawing.walkSize, emberTone.ink,
        )
        glyph(
            canvas, EmberPaths.routeFill, EmberPaths.routeStroke, E.metrics.x[0], E.metrics.glyphTop, E.metrics.glyph,
            emberTone.sub,
        )
        glyph(
            canvas, EmberPaths.clockFill, EmberPaths.clockStroke, E.metrics.x[1], E.metrics.glyphTop, E.metrics.glyph,
            emberTone.sub,
        )
        glyph(
            canvas, EmberPaths.boltFill, EmberPaths.boltStroke, E.metrics.x[2], E.metrics.glyphTop, E.metrics.glyph,
            emberTone.sub,
        )
    }

    private fun glyph(
        canvas: Canvas, fill: List<Path>, stroke: List<Path>, x: Float, y: Float, size: Float, color: Int,
    ) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(size / drawing.glyphGrid, size / drawing.glyphGrid)
        brush.reset(color)
        fill.forEach { canvas.drawPath(it, paint) }
        brush.reset(color, stroke = drawing.glyphStroke)
        stroke.forEach { canvas.drawPath(it, paint) }
        canvas.restore()
    }
}
