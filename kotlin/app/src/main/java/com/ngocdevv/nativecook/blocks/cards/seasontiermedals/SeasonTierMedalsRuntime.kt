package com.ngocdevv.nativecook.blocks.cards.seasontiermedals

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

@RequiresApi(33)
internal class SeasonTierMedalsRuntime {
    private val shaders = Array(3) { RuntimeShader(SeasonTierMedalsShader.source) }
    fun shader(i: Int, x: Double, y: Double, radius: Double, yaw: Double, sweep: Double, glow: Double): RuntimeShader {
        val shader = shaders[i]
        shader.setFloatUniform("c", x.toFloat(), y.toFloat())
        shader.setFloatUniform("R", radius.toFloat())
        shader.setFloatUniform("yaw", yaw.toFloat())
        shader.setFloatUniform("pitch", 0.1f)
        shader.setFloatUniform("roll", 0f)
        shader.setFloatUniform("chev", (i + 1).toFloat())
        val metal = SeasonTierMedalsTheme.metals[i]
        shader.setFloatUniform("metal", metal[0], metal[1], metal[2])
        val deep = SeasonTierMedalsTheme.deeps[i]
        shader.setFloatUniform("deep", deep[0], deep[1], deep[2])
        shader.setFloatUniform("glassy", if (i == 1) 1f else 0f)
        shader.setFloatUniform("sweep", sweep.toFloat())
        shader.setFloatUniform("glow", glow.toFloat())
        return shader
    }
}
