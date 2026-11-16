package com.ngocdevv.nativecook.blocks.cards.cardvacuumseal

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import com.ngocdevv.nativecook.blocks.cards.cardvacuumseal.CardVacuumSealMotion as M
import com.ngocdevv.nativecook.blocks.cards.cardvacuumseal.CardVacuumSealTheme as Theme

/** The film's geometry in the canvas, in design units (RN `FilmGeom`). */
internal class CardVacuumSealFilmGeom(
    val s: Double, // dp per design unit (card width / 354)
    val cx: Double, // card centre in the canvas
    val cy: Double,
    val hw: Double, // card half size
    val hh: Double,
    val radius: Double,
    val lip: Double,
    val ear: Double,
    val mapK: Double, // snapshot px per design unit
)

/** The film shader (API 33+): the card's snapshot and the baked crease map as child shaders, RN's `filmUniforms` per frame. */
@RequiresApi(33)
internal class CardVacuumSealShaderPass(card: Bitmap, crease: Bitmap?) {
    private val shader = RuntimeShader(CardVacuumSealFilmSource)
    private val paint = Paint()
    private val creaseOn = crease != null

    init {
        val a = BitmapShader(card, Shader.TileMode.DECAL, Shader.TileMode.DECAL)
        a.filterMode = BitmapShader.FILTER_MODE_LINEAR
        val b = BitmapShader(crease ?: card, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        b.filterMode = BitmapShader.FILTER_MODE_LINEAR
        shader.setInputShader("uMap", a)
        shader.setInputShader("uCrease", b)
    }

    fun draw(c: Canvas, w: Float, h: Float, g: CardVacuumSealFilmGeom, e: CardVacuumSealEngine, tint: FloatArray) {
        val l = e.look
        val azimuth = Math.toRadians(Theme.LIGHT_AZ)
        shader.setFloatUniform("uCreaseOn", if (creaseOn) 1f else 0f)
        shader.setFloatUniform("uS", g.s.toFloat())
        shader.setFloatUniform("uC", g.cx.toFloat(), g.cy.toFloat())
        shader.setFloatUniform("uCB", g.hw.toFloat(), g.hh.toFloat())
        shader.setFloatUniform("uCR", g.radius.toFloat())
        shader.setFloatUniform("uLip", g.lip.toFloat())
        shader.setFloatUniform("uEar", g.ear.toFloat())
        shader.setFloatUniform("uP", l.p.toFloat())
        shader.setFloatUniform("uPuff", l.puff.toFloat())
        shader.setFloatUniform("uSettle", l.settle.toFloat())
        shader.setFloatUniform("uGlint", l.glint.toFloat())
        shader.setFloatUniform("uSweep", l.sweep.toFloat())
        shader.setFloatUniform("uSweepK", l.sweepK.toFloat())
        shader.setFloatUniform("uAz", (azimuth + M.lightDrift(e.ambient)).toFloat())
        shader.setFloatUniform("uDimple", (e.dimpleX + g.cx - g.hw).toFloat(), (e.dimpleY + g.cy - g.hh).toFloat(), e.dimpleK.value.toFloat())
        shader.setFloatUniform("uFinger", (e.fingerX + g.cx - g.hw).toFloat(), (e.fingerY + g.cy - g.hh).toFloat(), e.fingerK.value.toFloat())
        shader.setFloatUniform("uStretch", e.stretch.value.toFloat())
        shader.setFloatUniform("uShimmer", M.shimmerAt(e.ambient).toFloat())
        shader.setFloatUniform("uFade", e.fade.value.toFloat())
        shader.setFloatUniform("uMapK", g.mapK.toFloat())
        shader.setFloatUniform("uSeed", e.seed.toFloat())
        shader.setFloatUniform("uAmp", e.amp.toFloat())
        shader.setFloatUniform("uExtra", e.extra.toFloat())
        shader.setFloatUniform("uTint", tint[0], tint[1], tint[2])
        paint.shader = shader
        c.drawRect(0f, 0f, w, h, paint)
    }
}
