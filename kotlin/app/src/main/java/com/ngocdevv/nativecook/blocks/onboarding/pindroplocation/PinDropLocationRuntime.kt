package com.ngocdevv.nativecook.blocks.onboarding.pindroplocation

import android.graphics.Shader
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

@RequiresApi(33)
internal class PinDropLocationRuntime {
    private val shader = RuntimeShader(PinDropLocationShader.SOURCE)

    fun shader(layout: PinDropLocationLayout, frame: PinDropLocationMotion.Frame): Shader {
        shader.setFloatUniform("uView", layout.w.toFloat(), layout.h.toFloat())
        shader.setFloatUniform("uTime", frame.time.toFloat())
        shader.setFloatUniform("uDisc", layout.discX.toFloat(), layout.discY.toFloat(), layout.discR.toFloat(), frame.bend.toFloat())
        shader.setFloatUniform("uSpin", frame.lon.toFloat(), frame.lat.toFloat(), frame.zoom.toFloat(), frame.places.toFloat())
        // RN's frame buffer reserves a hover slot but writes zero in the current WebGPU ticker.
        shader.setFloatUniform("uPin", frame.fall.toFloat(), 0f, frame.since.toFloat(), frame.lights.toFloat())
        return shader
    }
}
