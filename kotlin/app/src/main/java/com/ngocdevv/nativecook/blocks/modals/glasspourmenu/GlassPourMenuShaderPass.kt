package com.ngocdevv.nativecook.blocks.modals.glasspourmenu

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

/** What the glass shader reads each frame (RN `uniforms()`), in dp from O. */
internal class GlassPourMenuGlassState(
    val g: GlassPourMenuGlass,
    val lens: DoubleArray, // bar centre y, half height, alpha, flash
    val spec: Double,
    val satellite: DoubleArray,
)

/** The page (three blurs of one analytic flower) as child shaders of the glass (API 33+). */
@RequiresApi(33)
internal class GlassPourMenuShaderPass {
    // sharp, frost (10), soft (3.5)
    private val petals = Array(3) { RuntimeShader(GlassPourMenuPetalsSource) }
    // the live glass, and the opened endpoint under Reduce Motion
    private val glass = Array(2) { RuntimeShader(GlassPourMenuGlassSource) }
    private val paint = Paint()

    init {
        for (gl in glass) {
            gl.setInputShader("page", petals[0])
            gl.setInputShader("frost", petals[1])
            gl.setInputShader("soft", petals[2])
        }
    }

    private fun page(ox: Float, oy: Float, twist: Float) {
        val blurs = floatArrayOf(0f, GLASS_POUR_MENU_FROST_BLUR.toFloat(), GLASS_POUR_MENU_SOFT_BLUR.toFloat())
        for (i in 0 until 3) {
            petals[i].setFloatUniform("uO", ox, oy)
            petals[i].setFloatUniform("uS", 1f)
            petals[i].setFloatUniform("uBlur", blurs[i])
            petals[i].setFloatUniform("uTwist", twist)
        }
    }

    /** `idx` 0 the live glass, 1 the opened endpoint (Reduce Motion); `alpha` 0..1 fades the whole screen draw. */
    fun draw(
        c: Canvas,
        w: Float,
        h: Float,
        idx: Int,
        s: GlassPourMenuGlassState,
        ox: Double,
        oy: Double,
        dir: Int,
        optics: FloatArray,
        tint: FloatArray,
        twist: Double,
        alpha: Float,
    ) {
        page(ox.toFloat(), oy.toFloat(), twist.toFloat())
        val sh = glass[idx]
        val g = s.g
        sh.setFloatUniform("uO", ox.toFloat(), oy.toFloat())
        sh.setFloatUniform("uS", 1f)
        sh.setFloatUniform("uBead", g.beadX.toFloat(), g.beadY.toFloat(), g.beadR.toFloat())
        sh.setFloatUniform("uBox", g.boxX.toFloat(), g.boxY.toFloat(), g.boxW.toFloat(), g.boxH.toFloat())
        sh.setFloatUniform("uBoxR", g.boxR.toFloat())
        sh.setFloatUniform("uK", g.k.toFloat())
        sh.setFloatUniform("uThick", g.thick.toFloat())
        sh.setFloatUniform("uHot", g.hot.toFloat())
        sh.setFloatUniform("uFrost", g.frost.toFloat())
        sh.setFloatUniform("uOpen", g.open.toFloat())
        sh.setFloatUniform("uPress", 0f)
        sh.setFloatUniform("uLens", s.lens[0].toFloat(), s.lens[1].toFloat(), s.lens[2].toFloat(), s.lens[3].toFloat())
        sh.setFloatUniform("uSpec", s.spec.toFloat())
        sh.setFloatUniform("uRim", g.rim.toFloat())
        sh.setFloatUniform("uP3", 0f)
        sh.setFloatUniform("uNeck", g.neckA.toFloat(), g.neckB.toFloat(), g.neckR.toFloat())
        sh.setFloatUniform("uSatellite", s.satellite[0].toFloat(), s.satellite[1].toFloat(), s.satellite[2].toFloat())
        sh.setFloatUniform("uDwell", ((s.lens[1] - 18) / 2).toFloat())
        sh.setFloatUniform("uDir", dir.toFloat())
        sh.setFloatUniform("uOptics", optics[0], optics[1], optics[2], optics[3])
        sh.setFloatUniform("uTint", tint[0], tint[1], tint[2])
        paint.shader = sh
        paint.alpha = (255 * alpha).toInt()
        c.drawRect(0f, 0f, w, h, paint)
    }
}
