package com.ngocdevv.nativecook.blocks.pickers.tinyworldpicker

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

@RequiresApi(33)
internal class TinyWorldPickerRuntime {
    private val shader=RuntimeShader(TinyWorldPickerShader.source)
    fun shader(w: Float, cy: Float, s: Float, density: Float, f: TinyWorldPickerMotion.Frame): RuntimeShader {
        shader.setFloatUniform("c",w/2,cy); shader.setFloatUniform("S",s)
        shader.setFloatUniform("yaw",f.yaw.toFloat()); shader.setFloatUniform("wa",f.cur.toFloat()); shader.setFloatUniform("wb",f.nxt.toFloat())
        shader.setFloatUniform("m",f.m.coerceAtMost(1.03).toFloat()); shader.setFloatUniform("sq",f.sq.toFloat()); shader.setFloatUniform("glow",f.glow.toFloat())
        shader.setFloatUniform("px",1/(s*density)); shader.setFloatUniform("tm",f.tm.toFloat())
        return shader
    }
}
