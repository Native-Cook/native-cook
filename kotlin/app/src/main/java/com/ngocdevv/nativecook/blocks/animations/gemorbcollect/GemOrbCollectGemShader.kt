package com.ngocdevv.nativecook.blocks.animations.gemorbcollect

import android.annotation.TargetApi
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max

/** API 33+ convex-cut ray tracer; the existing Canvas path serves API 26–32. */
internal class GemOrbCollectGemShader {
    private val gpu = if (Build.VERSION.SDK_INT >= 33) Gpu() else null

    fun draw(
        canvas: Canvas,
        paint: Paint,
        centerX: Float,
        centerY: Float,
        radius: Float,
        yaw: Float,
        pitch: Float,
        stretch: Float,
        charge: Float,
        flare: Float,
        time: Float,
        lights: FloatArray,
        miniature: Boolean,
    ): Boolean {
        val renderer = gpu ?: return false
        renderer.draw(canvas, paint, centerX, centerY, radius, yaw, pitch, stretch, charge, flare, time, lights, miniature)
        return true
    }

    @TargetApi(33)
    private class Gpu {
        private val cut = GemOrbCollectCut()
        private val shader = RuntimeShader(GemOrbCollectOpticalSource.source)
        private var previousTime = -1f
        private var previousYaw = 0f
        private var previousPitch = 0f
        private var opticalSpeed = 0f

        fun draw(
            canvas: Canvas,
            paint: Paint,
            centerX: Float,
            centerY: Float,
            radius: Float,
            yaw: Float,
            pitch: Float,
            stretch: Float,
            charge: Float,
            flare: Float,
            time: Float,
            lights: FloatArray,
            miniature: Boolean,
        ) {
            if (!miniature) {
                val dt = time - previousTime
                if (previousTime >= 0f && dt > 0f && dt < 0.15f) {
                    val velocity = hypot(yaw - previousYaw, pitch - previousPitch) / dt
                    opticalSpeed += (velocity - opticalSpeed) * (1f - exp(-dt * 12f))
                } else if (dt < 0f) {
                    opticalSpeed = 0f
                }
                previousTime = time
                previousYaw = yaw
                previousPitch = pitch
            }
            val extra = if (miniature) 0f else max(flare, ((opticalSpeed - 0.8f) / 3f).coerceIn(0f, 1f))
            val rows = cut.attitude(yaw, pitch)
            shader.setFloatUniform("uCentre", centerX, centerY)
            shader.setFloatUniform("uRadius", radius.coerceAtLeast(0.01f))
            shader.setFloatUniform("uPixelRatio", 1f)
            shader.setFloatUniform("uCamera", 7f)
            shader.setFloatUniform("uRow0", rows[0])
            shader.setFloatUniform("uRow1", rows[1])
            shader.setFloatUniform("uRow2", rows[2])
            shader.setFloatUniform("uStretch", if (miniature) 1f else stretch.coerceIn(0.5f, 1.5f))
            shader.setFloatUniform("uCharge", charge.coerceIn(0f, 1f))
            shader.setFloatUniform("uFlare", flare.coerceIn(0f, 1f))
            shader.setFloatUniform("uLights", lights)
            shader.setFloatUniform("uTime", time)
            shader.setFloatUniform("uOpacity", 1f)
            shader.setFloatUniform("uBounces", 2f + 3f * extra)
            shader.setFloatUniform("uOutsideLights", if ((3 until lights.size step 4).any { lights[it] > 0f }) 1f else 0f)
            shader.setFloatUniform("uInsideLights", if ((3 until lights.size step 4).any { lights[it] < 0f }) 1f else 0f)
            shader.setFloatUniform("uPlanes", cut.planes)
            shader.setFloatUniform("uOutline", cut.outline(rows))
            paint.shader = shader
            val extent = radius * 2.25f
            canvas.drawRect(centerX - extent, centerY - extent, centerX + extent, centerY + extent, paint)
            paint.shader = null
        }
    }
}
