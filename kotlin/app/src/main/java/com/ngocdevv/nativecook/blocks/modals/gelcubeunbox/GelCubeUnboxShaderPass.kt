package com.ngocdevv.nativecook.blocks.modals.gelcubeunbox

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

/** The per-frame uniforms of the gel shader (RN's `uniforms` derived value). */
internal class GelCubeUnboxUniforms {
    val frame = FloatArray(4) // originX, originY, scale, controls (bit0 close, bit1 default sheet copy)
    val shape = FloatArray(4) // cubeScale, glassFill, glassStroke, sheetRadius
    val state = FloatArray(4) // progress, coreBrightness, fanProgress, causticBrightness
    val optics = FloatArray(4) // lidRadians, flash, P3Output (0), grain
}

/** The one material pass (API 33+): no child shaders. */
@RequiresApi(33)
internal class GelCubeUnboxShaderPass {
    private val shader = RuntimeShader(GelCubeUnboxSource)
    private val paint = Paint()

    fun draw(c: Canvas, w: Float, h: Float, u: GelCubeUnboxUniforms, pal: GelCubeUnboxTheme.Palette) {
        shader.setFloatUniform("uFrame", u.frame[0], u.frame[1], u.frame[2], u.frame[3])
        shader.setFloatUniform("uShape", u.shape[0], u.shape[1], u.shape[2], u.shape[3])
        shader.setFloatUniform("uState", u.state[0], u.state[1], u.state[2], u.state[3])
        shader.setFloatUniform("uOptics", u.optics[0], u.optics[1], u.optics[2], u.optics[3])
        shader.setFloatUniform("uTint", pal.tint[0], pal.tint[1], pal.tint[2])
        shader.setFloatUniform("uPage", pal.page[0], pal.page[1], pal.page[2])
        shader.setFloatUniform("uFloor", pal.floor[0], pal.floor[1], pal.floor[2])
        shader.setFloatUniform("uEdge", pal.edge[0], pal.edge[1], pal.edge[2])
        shader.setFloatUniform("uCore", pal.core[0], pal.core[1], pal.core[2])
        shader.setFloatUniform("uFan", pal.fan[0], pal.fan[1], pal.fan[2])
        shader.setFloatUniform("uUnder", pal.underside[0], pal.underside[1], pal.underside[2])
        paint.shader = shader
        c.drawRect(0f, 0f, w, h, paint)
    }
}
