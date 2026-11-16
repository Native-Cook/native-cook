package com.ngocdevv.nativecook.blocks.cards.reededglassreveal

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import com.ngocdevv.nativecook.blocks.cards.reededglassreveal.ReededGlassRevealTheme as Theme

/** A bitmap as a child shader that fills the portrait's rect in dp (RN `ImageShader fit="fill" rect`). */
@RequiresApi(33)
internal fun reededGlassRevealImageShader(bmp: Bitmap, l: Theme.Layout): BitmapShader {
    val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    shader.filterMode = BitmapShader.FILTER_MODE_LINEAR
    val m = Matrix()
    m.setScale((l.imgW / bmp.width).toFloat(), (l.imgH / bmp.height).toFloat())
    m.postTranslate(l.imgX.toFloat(), l.imgY.toFloat())
    shader.setLocalMatrix(m)
    return shader
}

/** The reeded glass pass (API 33+): the sharp and the defocused portrait as children, the flutes' state as uniforms. */
@RequiresApi(33)
internal class ReededGlassRevealGlassPass(portrait: Bitmap, soft: Bitmap, l: Theme.Layout) {
    private val shader = RuntimeShader(ReededGlassRevealGlassSource)
    private val paint = Paint()
    private val edge = FloatArray(Theme.RIBS + 1) { l.edges[it].toFloat() }
    private val seed = FloatArray(Theme.RIBS) { Theme.RIB_SEEDS[it].toFloat() }
    private val depth = FloatArray(Theme.RIBS)
    private val img = floatArrayOf(l.imgX.toFloat(), l.imgY.toFloat(), l.imgW.toFloat(), l.imgH.toFloat())
    private val s = l.s.toFloat()
    private val magX = l.magX.toFloat()
    private val magY = l.magY.toFloat()

    init {
        shader.setInputShader("portrait", reededGlassRevealImageShader(portrait, l))
        shader.setInputShader("portraitSoft", reededGlassRevealImageShader(soft, l))
    }

    /** RN `glassUniforms`; `strength` is the `depth` prop (1), `glints` the glint strength. */
    fun draw(c: Canvas, x: Float, y: Float, w: Float, h: Float, look: ReededGlassRevealLook, reduce: Boolean, glints: Double, strength: Double = 1.0) {
        for (i in 0 until Theme.RIBS) depth[i] = look.depths[i].toFloat()
        shader.setFloatUniform("uImg", img)
        shader.setFloatUniform("uEdge", edge)
        shader.setFloatUniform("uSeed", seed)
        shader.setFloatUniform("uDepth", depth)
        shader.setFloatUniform("uMag", look.mean.toFloat())
        shader.setFloatUniform("uMagAmt", (Theme.GLASS_MAG * (1 + look.breath)).toFloat())
        shader.setFloatUniform("uMagC", magX, magY)
        shader.setFloatUniform("uS", s)
        shader.setFloatUniform("uFlatMix", if (reduce) ReededGlassRevealMotion.clamp01(1 - look.mean).toFloat() else -1f)
        shader.setFloatUniform("uKappa", (Theme.GLASS_KAPPA * strength).toFloat())
        shader.setFloatUniform("uTau", Theme.GLASS_TAU.toFloat())
        shader.setFloatUniform("uSlice", (Theme.GLASS_SLICE * strength).toFloat())
        shader.setFloatUniform("uSpread", (Theme.GLASS_SPREAD * strength).toFloat())
        shader.setFloatUniform("uSag", Theme.GLASS_SAG.toFloat())
        shader.setFloatUniform("uBlur", Theme.GLASS_BLUR.toFloat())
        shader.setFloatUniform("uEdgeK", Theme.GLASS_EDGE_K.toFloat())
        shader.setFloatUniform("uChroma", Theme.GLASS_CHROMA.toFloat())
        shader.setFloatUniform("uGlintW", Theme.GLASS_GLINT_W.toFloat())
        shader.setFloatUniform("uSeam", Theme.GLASS_SEAM.toFloat())
        shader.setFloatUniform("uLight", look.light.toFloat())
        shader.setFloatUniform("uSpec", (if (reduce) 0.6 * glints else glints).toFloat())
        shader.setFloatUniform("uRun", (if (reduce) -1.0 else look.run).toFloat())
        shader.setFloatUniform("uRunRib", Theme.RUN_RIBS[0].toFloat(), Theme.RUN_RIBS[1].toFloat(), Theme.RUN_RIBS[2].toFloat())
        paint.shader = shader
        c.drawRect(x, y, x + w, y + h, paint)
    }
}

/** The capsule's glass pass (API 33+): its ends refract the hazed portrait. */
@RequiresApi(33)
internal class ReededGlassRevealCapsulePass(haze: Bitmap, l: Theme.Layout) {
    private val shader = RuntimeShader(ReededGlassRevealCapsuleSource)
    private val paint = Paint()
    private val l0 = l

    init {
        shader.setInputShader("portrait", reededGlassRevealImageShader(haze, l))
    }

    private fun rgb(c: Int) = floatArrayOf(android.graphics.Color.red(c) / 255f, android.graphics.Color.green(c) / 255f, android.graphics.Color.blue(c) / 255f)

    fun draw(c: Canvas, cap: FloatArray, look: ReededGlassRevealLook, endLeft: Double, endRight: Double) {
        val l = l0
        val s = l.s.toFloat()
        shader.setFloatUniform("uImg", l.imgX.toFloat(), l.imgY.toFloat(), l.imgW.toFloat(), l.imgH.toFloat())
        shader.setFloatUniform("uRect", cap[0], cap[1], cap[2], cap[3])
        shader.setFloatUniform("uR", (Theme.CAPSULE_R * l.s).toFloat())
        shader.setFloatUniform("uBtn", l.x(Theme.SWAP_CX).toFloat(), l.yb(Theme.SWAP_CY).toFloat())
        shader.setFloatUniform("uBtnR", (Theme.SWAP_D / 2 * l.s).toFloat())
        shader.setFloatUniform("uThumb", l.x(Theme.THUMB.x).toFloat(), l.yb(Theme.THUMB.y).toFloat(), (Theme.THUMB.w * l.s).toFloat(), (Theme.THUMB.h * l.s).toFloat())
        val top = rgb(Theme.capsuleTop)
        val low = rgb(Theme.capsuleLow)
        val end = rgb(Theme.capsuleEnd)
        shader.setFloatUniform("uTop", top[0], top[1], top[2])
        shader.setFloatUniform("uLow", low[0], low[1], low[2])
        shader.setFloatUniform("uEnd", end[0], end[1], end[2])
        shader.setFloatUniform("uS", s)
        shader.setFloatUniform("uFront", look.lag.toFloat())
        shader.setFloatUniform("uEnds", endLeft.toFloat(), endRight.toFloat())
        paint.shader = shader
        c.drawRect(cap[0] - 2, cap[1] - 2, cap[0] + cap[2] + 2, cap[1] + cap[3] + 2, paint)
    }
}
