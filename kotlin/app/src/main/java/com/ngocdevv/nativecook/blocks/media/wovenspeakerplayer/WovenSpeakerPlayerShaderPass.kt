package com.ngocdevv.nativecook.blocks.media.wovenspeakerplayer

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

/** The dome's weave pass (API 33+): RN `uniforms` per frame. */
@RequiresApi(33)
internal class WovenSpeakerPlayerShaderPass {
    private val shader = RuntimeShader(WovenSpeakerPlayerWeaveSource)
    private val paint = Paint()

    fun draw(c: Canvas, cx: Float, cy: Float, s: Float, density: Float, look: WovenSpeakerPlayerLook, duration: Double, accent: FloatArray, fibre: Double, strands: Int) {
        val head = -Math.PI / 2 + (look.pos / duration) * 2 * Math.PI
        shader.setFloatUniform("uC", cx, cy)
        shader.setFloatUniform("uS", s)
        shader.setFloatUniform("uPx", density)
        shader.setFloatUniform("uAng", look.psiO.toFloat(), look.psiI.toFloat(), look.omO.toFloat(), look.omI.toFloat())
        shader.setFloatUniform("uShear", look.shear.toFloat(), look.finger.toFloat(), look.turn.toFloat(), look.turn.toFloat())
        shader.setFloatUniform("uRingA", look.r0.toFloat(), look.a0.toFloat(), look.r1.toFloat(), look.a1.toFloat())
        shader.setFloatUniform("uRingB", look.r2.toFloat(), look.a2.toFloat(), look.r3.toFloat(), look.a3.toFloat())
        shader.setFloatUniform("uDent", look.dentX.toFloat(), look.dentY.toFloat(), look.dent.toFloat(), 0f)
        shader.setFloatUniform("uLamp", head.toFloat(), look.reach.toFloat(), look.lamp.toFloat(), 0f)
        shader.setFloatUniform("uAccent", accent[0], accent[1], accent[2])
        shader.setFloatUniform("uFibre", fibre.toFloat(), look.pullAt.toFloat(), look.pull.toFloat(), 1f)
        shader.setFloatUniform("uP3", 0f)
        shader.setFloatUniform("uN", strands.toFloat())
        paint.shader = shader
        val half = 173f * s
        c.drawRect(cx - half, cy - half, cx + half, cy + half, paint)
    }
}
