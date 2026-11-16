package com.ngocdevv.nativecook.blocks.onboarding.objectsignindissolve

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import com.ngocdevv.nativecook.blocks.onboarding.objectsignindissolve.ObjectSigninDissolveMotion as M
import com.ngocdevv.nativecook.blocks.onboarding.objectsignindissolve.ObjectSigninDissolveTheme as Theme
import kotlin.math.PI

/** The pear's runtime shader (API 33+): the per-frame uniforms of RN's `PearLayer`, drawn into the pear's 224 x 396 pt rect. */
@RequiresApi(33)
internal class ObjectSigninDissolveShaderPass {
    private val shader = RuntimeShader(ObjectSigninDissolveShaderSource)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * `c` is already scaled to dp. `pearCy`: the pear's centre y; `s`: the layout scale; `screenH`: the screen's height in dp;
     * `wipeR`: the disc's radius for the shader (0 in Reduce Motion); `light`: the Reduce Motion cross-fade 0..1; `aa`: dp per pixel.
     */
    fun draw(
        c: Canvas,
        w: Float,
        pearCy: Float,
        s: Float,
        screenH: Float,
        k: ObjectSigninDissolveLook,
        clock: Double,
        wipeR: Float,
        light: Float,
        aa: Float,
    ) {
        val cx = w / 2
        shader.setFloatUniform("uC", cx, pearCy)
        shader.setFloatUniform("uAa", aa)
        shader.setFloatUniform("uP3", 0f)
        shader.setFloatUniform("uYaw", (k.yaw * PI / 180).toFloat())
        shader.setFloatUniform("uScale", (k.scale * s).toFloat())
        shader.setFloatUniform("uKey", k.key.toFloat())
        shader.setFloatUniform("uPlane", (M.planeY(k.c) * s).toFloat())
        shader.setFloatUniform("uRoll", k.roll.toFloat())
        shader.setFloatUniform("uPitchP", k.pitchP.toFloat())
        shader.setFloatUniform("uSeam", k.seam.toFloat())
        shader.setFloatUniform("uTime", ((clock * 8 * s) % (5 * s)).toFloat())
        shader.setFloatUniform("uWipe", M.WIPE_CX.toFloat(), screenH + M.WIPE_DROP.toFloat(), wipeR)
        shader.setFloatUniform("uLight", light)
        shader.setFloatUniform("uShell", Theme.shell[0], Theme.shell[1], Theme.shell[2])
        shader.setFloatUniform("uSeamC", Theme.seam[0], Theme.seam[1], Theme.seam[2])
        shader.setFloatUniform("uSeamCore", Theme.seamCore[0], Theme.seamCore[1], Theme.seamCore[2])
        shader.setFloatUniform("uLine", 5 * s, 1.2f * s)
        paint.shader = shader
        c.drawRect(cx - 112 * s, pearCy - 218 * s, cx + 112 * s, pearCy + 178 * s, paint)
    }
}
