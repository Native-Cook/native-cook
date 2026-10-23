package com.ngocdevv.nativecook.blocks.gestures.pullcordtheme

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt

/**
 * The whole canvas of PullCordTheme.tsx: the dark room, the light room cut to a circle growing from the bulb,
 * the reveal's warm edge and the flash. Drawn on an android.graphics.Canvas that is already scaled to dp.
 */
internal class PullCordThemeScene(private val g: PullCordThemeGeometry) {
    private val w = g.w.toFloat()
    private val h = g.h.toFloat()
    private val room = PullCordThemeRoom(g)
    private val paint = Paint()
    private val matrix = Matrix()
    private val dstIn = PorterDuffXfermode(PorterDuff.Mode.DST_IN)

    // A hard but anti-aliased edge for the reveal circle, as a unit gradient scaled to the reveal radius.
    private val revealShader = RadialGradient(
        0f, 0f, 1f, intArrayOf(Color.White.toArgb(), Color.White.toArgb(), Color(1f, 1f, 1f, 0f).toArgb()),
        floatArrayOf(0f, 0.994f, 1f), Shader.TileMode.CLAMP,
    )

    // The flash: white to transparent white over the screen's width.
    private val flashShader = RadialGradient(
        0f, 0f, w, Color.White.toArgb(), Color(1f, 1f, 1f, 0f).toArgb(), Shader.TileMode.CLAMP,
    )

    fun draw(canvas: Canvas, f: PullCordThemeFrame, assets: PullCordThemeAssets?) {
        room.draw(canvas, light = false, f = f, assets = assets)

        // The light room, cut to a circle growing from the bulb: a radial gradient used as a dstIn mask on a layer.
        if (f.fade > 0.0) {
            canvas.saveLayerAlpha(0f, 0f, w, h, alphaOf(f.fade.toFloat()))
            room.draw(canvas, light = true, f = f, assets = assets)
            val radius = f.revealRadius.toFloat()
            matrix.setScale(radius, radius)
            matrix.postTranslate(f.cx.toFloat(), f.cy.toFloat())
            revealShader.setLocalMatrix(matrix)
            paint.reset()
            paint.shader = revealShader
            paint.xfermode = dstIn
            canvas.drawRect(0f, 0f, w, h, paint)
            canvas.restore()
        }

        // The reveal's warm soft edge, strongest mid-way.
        val edgeOpacity = if (f.reduced) 0.0 else 4 * f.frac * (1 - f.frac)
        if (edgeOpacity > 0.0) {
            room.drawEdge(canvas, f.cx.toFloat(), f.cy.toFloat(), f.revealRadius.toFloat(), edgeOpacity.toFloat())
        }

        // Payoff: a brief soft white flash from the bulb on commit to light.
        val hero = PullCordThemeMotion.Hero
        val flashOpacity = if (f.themeNow == 1 && f.commit < hero.flash) hero.flashPeak * (1 - f.commit / hero.flash) else 0.0
        if (flashOpacity > 0.0) {
            matrix.setTranslate(f.cx.toFloat(), f.cy.toFloat())
            flashShader.setLocalMatrix(matrix)
            paint.reset()
            paint.shader = flashShader
            paint.alpha = alphaOf(flashOpacity.toFloat())
            canvas.drawRect(0f, 0f, w, h, paint)
        }
    }

    private fun alphaOf(opacity: Float) = (opacity.coerceIn(0f, 1f) * 255f).roundToInt()
}
