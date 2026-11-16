package com.ngocdevv.nativecook.blocks.pickers.beamshapetorch

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import com.ngocdevv.nativecook.blocks.pickers.beamshapetorch.BeamShapeTorchMotion as M

/** The capsule's runtime shader (API 33+): RN's per-frame uniforms, drawn into the capsule's path. */
@RequiresApi(33)
internal class BeamShapeTorchShaderPass {
    private val shader = RuntimeShader(BeamShapeTorchShaderSource)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(
        c: Canvas,
        capsule: Path,
        lensX: Double,
        lensY: Double,
        sS: Double,
        k: BeamShapeTorchLook,
        o: BeamShapeTorchOptics,
        capX: Double,
        capY: Double,
        capW: Double,
        capH: Double,
        capR: Double,
        show: Double,
        reduce: Boolean,
        glass: FloatArray,
        light: FloatArray,
        fringe: Float,
        density: Float,
    ) {
        shader.setFloatUniform("uLens", lensX.toFloat(), lensY.toFloat())
        shader.setFloatUniform("uS", sS.toFloat())
        shader.setFloatUniform("uB", M.clamp01(k.b).toFloat())
        shader.setFloatUniform("uHot", M.clamp01(k.hot).toFloat())
        shader.setFloatUniform("uTheta", o.theta.toFloat())
        shader.setFloatUniform("uReach", o.reach.toFloat())
        shader.setFloatUniform("uGain", o.gain.toFloat())
        shader.setFloatUniform("uWide", M.clamp01(k.w).toFloat())
        shader.setFloatUniform("uCap", capX.toFloat(), capY.toFloat(), capW.toFloat(), capH.toFloat())
        shader.setFloatUniform("uCapR", capR.toFloat())
        shader.setFloatUniform("uShow", show.toFloat())
        shader.setFloatUniform("uPhase", k.phase.toFloat())
        shader.setFloatUniform("uStill", if (reduce) 1f else 0f)
        shader.setFloatUniform("uGlass", glass[0], glass[1], glass[2])
        shader.setFloatUniform("uLight", light[0], light[1], light[2])
        shader.setFloatUniform("uFringe", fringe)
        shader.setFloatUniform("uAfter", k.after.toFloat())
        shader.setFloatUniform("uAfterReach", M.AFTER_REACH.toFloat())
        shader.setFloatUniform("uP3", 0f)
        shader.setFloatUniform("uDen", density)
        paint.shader = shader
        c.drawPath(capsule, paint)
    }
}
