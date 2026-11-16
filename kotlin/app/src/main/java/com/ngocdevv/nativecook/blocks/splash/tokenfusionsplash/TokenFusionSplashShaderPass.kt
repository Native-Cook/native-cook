package com.ngocdevv.nativecook.blocks.splash.tokenfusionsplash

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import com.ngocdevv.nativecook.blocks.splash.tokenfusionsplash.TokenFusionSplashTheme as Theme

/** The live signed-distance shader (API 33+): RN's `uniformsFor`, drawn over the whole screen. */
@RequiresApi(33)
internal class TokenFusionSplashShaderPass(atlas: Bitmap) {
    private val shader = RuntimeShader(TokenFusionSplashShaderSource)
    private val paint = Paint()
    private val bodies = FloatArray(24)
    private val lagBodies = FloatArray(24)
    private val colors = FloatArray(18)
    private val dyes = FloatArray(6)
    private val leans = FloatArray(6)

    init {
        val sampler = BitmapShader(atlas, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        sampler.filterMode = BitmapShader.FILTER_MODE_LINEAR
        shader.setInputShader("glyphs", sampler)
        for (i in 0 until 6) {
            val c = Theme.tokenColors.getOrNull(i) ?: floatArrayOf(0f, 0f, 0f)
            colors[i * 3] = c[0]
            colors[i * 3 + 1] = c[1]
            colors[i * 3 + 2] = c[2]
        }
        shader.setFloatUniform("colors", colors)
    }

    fun draw(c: Canvas, w: Float, h: Float, scene: TokenFusionScene, lag: TokenFusionScene, layout: Theme.Layout) {
        for (i in 0 until 6) {
            val co = scene.coins.getOrNull(i)
            val old = lag.coins.getOrNull(i) ?: co
            bodies[i * 4] = (co?.x ?: 0.0).toFloat()
            bodies[i * 4 + 1] = (co?.y ?: 0.0).toFloat()
            bodies[i * 4 + 2] = (co?.r ?: 0.0).toFloat()
            bodies[i * 4 + 3] = (co?.reach ?: 0.0).toFloat()
            lagBodies[i * 4] = (old?.x ?: 0.0).toFloat()
            lagBodies[i * 4 + 1] = (old?.y ?: 0.0).toFloat()
            lagBodies[i * 4 + 2] = (old?.r ?: 0.0).toFloat()
            lagBodies[i * 4 + 3] = (old?.reach ?: 0.0).toFloat()
            dyes[i] = (co?.dye ?: 0.0).toFloat()
            leans[i] = (co?.lean ?: 0.0).toFloat()
        }
        shader.setFloatUniform("viewport", w, h)
        shader.setFloatUniform("mapping", layout.scale.toFloat(), layout.offsetX.toFloat(), layout.offsetY.toFloat())
        shader.setFloatUniform("core", scene.x.toFloat(), scene.y.toFloat(), scene.r.toFloat(), scene.sphere.toFloat())
        shader.setFloatUniform("bodies", bodies)
        shader.setFloatUniform("lagBodies", lagBodies)
        shader.setFloatUniform("dyes", dyes)
        shader.setFloatUniform("leans", leans)
        shader.setFloatUniform("meta", scene.stretch.toFloat(), scene.axis.toFloat(), scene.flood.toFloat(), Theme.GRAIN.toFloat())
        shader.setFloatUniform("logo", scene.mark.toFloat(), scene.markSize.toFloat(), scene.cut.toFloat(), scene.ringScale.toFloat())
        shader.setFloatUniform("baseColor", Theme.core[0], Theme.core[1], Theme.core[2])
        shader.setFloatUniform("shadeColor", Theme.shade[0], Theme.shade[1], Theme.shade[2])
        shader.setFloatUniform("hotColor", Theme.hot[0], Theme.hot[1], Theme.hot[2])
        shader.setFloatUniform("pageColor", Theme.page[0], Theme.page[1], Theme.page[2])
        shader.setFloatUniform("ringColor", Theme.ring[0], Theme.ring[1], Theme.ring[2])
        shader.setFloatUniform("inkColor", Theme.ink[0], Theme.ink[1], Theme.ink[2])
        shader.setFloatUniform("count", scene.coins.size.toFloat())
        shader.setFloatUniform("outputP3", 0f)
        paint.shader = shader
        c.drawRect(0f, 0f, w, h, paint)
    }
}
