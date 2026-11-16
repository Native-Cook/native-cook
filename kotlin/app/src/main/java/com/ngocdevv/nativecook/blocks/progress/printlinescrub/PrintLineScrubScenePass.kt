package com.ngocdevv.nativecook.blocks.progress.printlinescrub

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi

/** What the scene compositor reads each frame (RN `sceneUniforms` / `glassUniforms`), in dp. */
internal class PrintLineScrubSceneState(
    val figure: FloatArray, // x, y, w, h
    val spans: FloatArray, // 12 numbers
    val ox: Float,
    val oy: Float,
    val u: Float,
    val camCos: Float,
    val camSin: Float,
    val yaw: Float,
    val h: Float,
    val band: Float,
    val heat: Float,
    val bead: Float,
    val tint: Float,
    val px: Float,
    val glass: Boolean,
    val lens: FloatArray, // cx, cy, half w, half h
    val lensR: Float,
    val lensK: Float,
    val refr: Float,
)

/** One instance of the scene compositor (API 33+). Distinct instances keep each recorded pass's uniforms until GPU submission. */
@RequiresApi(33)
internal class PrintLineScrubSceneShader(solid: Bitmap, ghost: Bitmap) {
    private val shader = RuntimeShader(PrintLineScrubSceneSource)
    private val paint = Paint()

    init {
        // the two registered renders, read at their own pixels; nothing outside them (decal)
        val a = BitmapShader(solid, Shader.TileMode.DECAL, Shader.TileMode.DECAL)
        a.filterMode = BitmapShader.FILTER_MODE_LINEAR
        val b = BitmapShader(ghost, Shader.TileMode.DECAL, Shader.TileMode.DECAL)
        b.filterMode = BitmapShader.FILTER_MODE_LINEAR
        shader.setInputShader("uSolid", a)
        shader.setInputShader("uGhost", b)
    }

    fun draw(c: Canvas, left: Float, top: Float, right: Float, bottom: Float, s: PrintLineScrubSceneState) {
        shader.setFloatUniform("uO", s.ox, s.oy)
        shader.setFloatUniform("uU", s.u)
        shader.setFloatUniform("uCam", s.camCos, s.camSin)
        shader.setFloatUniform("uFigure", s.figure[0], s.figure[1], s.figure[2], s.figure[3])
        shader.setFloatUniform("uImage", PrintLineScrubSilhouette.FIGURE_W.toFloat(), PrintLineScrubSilhouette.FIGURE_H.toFloat())
        shader.setFloatUniform("uSpanA", s.spans[0], s.spans[1], s.spans[2], s.spans[3])
        shader.setFloatUniform("uSpanB", s.spans[4], s.spans[5], s.spans[6], s.spans[7])
        shader.setFloatUniform("uSpanC", s.spans[8], s.spans[9], s.spans[10], s.spans[11])
        shader.setFloatUniform("uYaw", s.yaw)
        shader.setFloatUniform("uH", s.h)
        shader.setFloatUniform("uBand", s.band)
        shader.setFloatUniform("uHeat", s.heat)
        shader.setFloatUniform("uBead", s.bead)
        shader.setFloatUniform("uTint", s.tint)
        shader.setFloatUniform("uPx", s.px)
        shader.setFloatUniform("uP3", 0f)
        shader.setFloatUniform("uGlass", if (s.glass) 1f else 0f)
        shader.setFloatUniform("uLens", s.lens[0], s.lens[1], s.lens[2], s.lens[3])
        shader.setFloatUniform("uLensR", s.lensR)
        shader.setFloatUniform("uLensK", s.lensK)
        shader.setFloatUniform("uRefr", s.refr)
        paint.shader = shader
        c.drawRect(left, top, right, bottom, paint)
    }
}

/** The progress bar made of matter (API 33+). */
@RequiresApi(33)
internal class PrintLineScrubBarShader {
    private val shader = RuntimeShader(PrintLineScrubBarSource)
    private val paint = Paint()

    fun draw(
        c: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        originX: Float,
        originY: Float,
        s: Float,
        sizeW: Float,
        sizeH: Float,
        fill: Float,
        over: Float,
        tail: Float,
        flash: Float,
    ) {
        shader.setFloatUniform("uOrigin", originX, originY)
        shader.setFloatUniform("uS", s)
        shader.setFloatUniform("uSize", sizeW, sizeH)
        shader.setFloatUniform("uFill", fill)
        shader.setFloatUniform("uOver", over)
        shader.setFloatUniform("uTail", tail)
        shader.setFloatUniform("uFlash", flash)
        shader.setFloatUniform("uP3", 0f)
        paint.shader = shader
        c.drawRect(left, top, right, bottom, paint)
    }
}
