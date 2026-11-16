package com.ngocdevv.nativecook.blocks.gestures.flipfocusbadge

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import com.ngocdevv.nativecook.blocks.gestures.flipfocusbadge.FlipFocusBadgeGeometry as G
import com.ngocdevv.nativecook.blocks.gestures.flipfocusbadge.FlipFocusBadgeTheme as Theme

/** The badge's ray-marched pass (API 33+): RN `NumeralBadge`'s uniforms, per frame, drawn in a `w` x `h` dp rect. */
@RequiresApi(33)
internal class FlipFocusBadgeBadgePass(atlas: Bitmap) {
    private val shader = RuntimeShader(FlipFocusBadgeBadgeSource)
    private val paint = Paint()
    private val state = G.BadgeState()
    private val u = G.Uniforms()

    init {
        // the glyph SDF atlas as the child: 1120 x 1080 px, one pixel per unit, clamped, linearly sampled
        val child = BitmapShader(atlas, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        child.filterMode = BitmapShader.FILTER_MODE_LINEAR
        shader.setInputShader("uAtlas", child)
        shader.setFloatUniform("uTint", Theme.rgbOf(Theme.numeral))
        shader.setFloatUniform("uEdge", Theme.rgbOf(Theme.edge))
        shader.setFloatUniform("uPlate", Theme.rgbOf(Theme.plate))
    }

    fun draw(c: Canvas, w: Float, h: Float, k: Float, density: Float, look: FlipFocusBadgeLook, reduce: Boolean) {
        G.uniforms(state.fromLook(look), u)
        shader.setFloatUniform("uC", (Theme.CENTRE_X * k).toFloat(), 150f * k)
        shader.setFloatUniform("uS", k)
        shader.setFloatUniform("uPx", 1f / (k * density))
        shader.setFloatUniform("uBx", u.uBx); shader.setFloatUniform("uBy", u.uBy); shader.setFloatUniform("uBz", u.uBz)
        shader.setFloatUniform("uRx", u.uRx); shader.setFloatUniform("uRy", u.uRy); shader.setFloatUniform("uRz", u.uRz)
        shader.setFloatUniform("uRc", u.uRc); shader.setFloatUniform("uSpH", u.uSpH); shader.setFloatUniform("uSpS", u.uSpS)
        shader.setFloatUniform("uFront", u.uFront); shader.setFloatUniform("uBack", u.uBack); shader.setFloatUniform("uBackX", u.uBackX)
        shader.setFloatUniform("uGlint", u.uGlint); shader.setFloatUniform("uMat", u.uMat)
        shader.setFloatUniform("uPop", u.uPop); shader.setFloatUniform("uNum", u.uNum)
        // p3 = 0 on Android, film grain seed, reduce motion, the "hr" unit
        shader.setFloatUniform("uAux", 0f, 0f, if (reduce) 1f else 0f, 1f)
        paint.shader = shader
        c.drawRect(0f, 0f, w, h, paint)
    }
}

/** The page's film (API 33+): the near-black ground with the pin's slow ramp, a static grain and the badge's contact shadow. */
@RequiresApi(33)
internal class FlipFocusBadgeBackdropPass {
    private val shader = RuntimeShader(FlipFocusBadgeBackdropSource)
    private val paint = Paint()

    init {
        shader.setFloatUniform("uGround", Theme.rgbOf(Theme.pageTop)[0], Theme.rgbOf(Theme.pageMid)[0], Theme.rgbOf(Theme.pageRight)[0])
    }

    fun draw(c: Canvas, w: Float, h: Float, shadowX: Float, shadowY: Float, shadowHalfW: Float, shadowHalfH: Float, amt: Float, ramp0: Float, ramp1: Float) {
        shader.setFloatUniform("uSize", w, h)
        shader.setFloatUniform("uShadow", shadowX, shadowY, shadowHalfW, shadowHalfH)
        shader.setFloatUniform("uAmt", amt)
        shader.setFloatUniform("uYramp0", ramp0)
        shader.setFloatUniform("uYramp1", ramp1)
        shader.setFloatUniform("uAux", 0f, 0f, 0f, 0f)
        paint.shader = shader
        c.drawRect(0f, 0f, w, h, paint)
    }
}
