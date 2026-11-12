package com.ngocdevv.nativecook.blocks.cards.glasspaymorph

import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.util.Log
import kotlin.math.abs

/** Same pass order as RN: page/glass, printed face, front lights, pulse, chrome. */
internal class GlassPayMorphRenderer(assets: AssetManager) {
    private val face = GlassPayMorphFace(assets)
    private val fallback by lazy { GlassPayMorphFallback() }
    private val passes = if (Build.VERSION.SDK_INT >= 33) {
        try { arrayOf(GlassPayMorphShaderPass(), GlassPayMorphShaderPass()) }
        catch (error: IllegalArgumentException) {
            Log.w("GlassPayMorph", "Runtime shader unavailable; using Canvas fallback", error)
            null
        }
    } else null
    private val layerPaint = Paint()
    private val matrix = Matrix()

    fun draw(c: Canvas, width: Float, height: Float, topInset: Float, scene: GlassPayMorphMotion.Scene) {
        drawScene(c, width, height, topInset, scene, 0)
    }

    fun crossfade(c: Canvas, width: Float, height: Float, topInset: Float, k: Double) {
        drawScene(c, width, height, topInset, GlassPayMorphMotion.sceneAt(0.0), 0)
        if (k > 0.001) {
            layerPaint.alpha = (255 * k.coerceIn(0.0, 1.0)).toInt()
            c.saveLayer(0f, 0f, width, height, layerPaint)
            drawScene(c, width, height, topInset, GlassPayMorphMotion.sceneAt(GlassPayMorphMotion.PAID_HOLD), 1)
            c.restore()
        }
    }

    private fun drawScene(c: Canvas, width: Float, height: Float, topInset: Float, s: GlassPayMorphMotion.Scene, pass: Int) {
        val scale = width / 402
        val dy = (height - 874 * scale) / 2
        val top = (topInset - dy) / scale
        c.save()
        c.translate(0f, dy)
        c.scale(scale, scale)
        if (Build.VERSION.SDK_INT >= 33 && passes != null) {
            passes[pass].background(c, s, 1 / scale, -dy / scale, height / scale)
        } else {
            fallback.background(c, s, -dy / scale, height / scale)
        }
        if (s.face > 0.003 && abs(s.turn) < 90) {
            c.save()
            matrix.setValues(GlassPayMorphMotion.cardMatrix(s).map { it.toFloat() }.toFloatArray())
            c.concat(matrix)
            c.scale((s.w / 282).toFloat(), (s.h / 168).toFloat())
            face.draw(c, s, scale)
            c.restore()
        }
        if (Build.VERSION.SDK_INT >= 33 && passes != null) passes[pass].overlay(c, s, 1 / scale)
        else fallback.overlay(c, s)
        face.drawPulse(c, s)
        face.drawChrome(c, s, top)
        c.restore()
    }
}
