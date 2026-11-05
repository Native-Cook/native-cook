package com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding

import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding.AuroraTeammatesOnboardingMotion.GL
import com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding.AuroraTeammatesOnboardingTheme.Layout

@RequiresApi(33)
internal class AuroraTeammatesOnboardingRuntime {
    private val aurora=RuntimeShader(AuroraTeammatesOnboardingShaders.AURORA)
    private val glass=RuntimeShader(AuroraTeammatesOnboardingShaders.GLASS)
    fun background(l:Layout,sc:DoubleArray):Shader {
        aurora.setFloatUniform("uSize",l.w.toFloat(),l.h.toFloat());aurora.setFloatUniform("uCore",sc[GL.coreX].toFloat(),sc[GL.coreY].toFloat())
        aurora.setFloatUniform("uPhase",sc[GL.phase].toFloat());aurora.setFloatUniform("uW",sc[GL.w0].toFloat(),sc[GL.w1].toFloat(),sc[GL.w2].toFloat())
        aurora.setFloatUniform("uFlare",sc[GL.flare].toFloat());aurora.setFloatUniform("uTilt",(sc[GL.tiltX]*12).toFloat(),(sc[GL.tiltY]*8.4).toFloat());return aurora
    }
    fun panel(sc:DoubleArray,o:Int,r:Double):Shader {
        glass.setInputShader("aurora",aurora);glass.setFloatUniform("origin",sc[o].toFloat(),sc[o+1].toFloat());glass.setFloatUniform("halfSize",sc[o+6].toFloat(),sc[o+7].toFloat())
        glass.setFloatUniform("radius",r.toFloat());glass.setFloatUniform("dispersion",sc[o+10].toFloat());glass.setFloatUniform("lightAngle",(sc[GL.lightAngle]*Math.PI/180).toFloat());glass.setFloatUniform("intensity",sc[GL.lightIntensity].toFloat());return glass
    }
}
