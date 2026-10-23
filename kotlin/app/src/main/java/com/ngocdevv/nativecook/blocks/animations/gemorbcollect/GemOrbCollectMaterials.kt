package com.ngocdevv.nativecook.blocks.animations.gemorbcollect

import android.annotation.TargetApi
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build

/** Source SkSL atmosphere and cut-glass controls translated to AGSL on API 33+. */
internal class GemOrbCollectMaterials {
    private val gpu = if (Build.VERSION.SDK_INT >= 33) Gpu() else null

    fun backdrop(canvas: Canvas, paint: Paint, layout: GemOrbCollectLayout, frame: GemOrbCollectSimulation.Frame): Boolean {
        val renderer = gpu ?: return false
        renderer.backdrop(canvas, paint, layout, frame)
        return true
    }

    fun pill(
        canvas: Canvas,
        paint: Paint,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        capsule: Boolean,
        press: Float,
        glow: Float,
        sheen: Float,
        opacity: Float,
        scale: Float,
        time: Float,
        unit: Float,
    ): Boolean {
        val renderer = gpu ?: return false
        renderer.pill(canvas, paint, left, top, width, height, capsule, press, glow, sheen, opacity, scale, time, unit)
        return true
    }

    @TargetApi(33)
    private class Gpu {
        private val backdrop = RuntimeShader(GemOrbCollectBackdropSource.source)
        private val pill = RuntimeShader(GemOrbCollectPillSource.source)

        fun backdrop(canvas: Canvas, paint: Paint, layout: GemOrbCollectLayout, frame: GemOrbCollectSimulation.Frame) {
            backdrop.setFloatUniform("uSize", layout.width, layout.height)
            backdrop.setFloatUniform("uGem", layout.gemX, layout.gemY)
            backdrop.setFloatUniform("uRadius", layout.gemRadius)
            backdrop.setFloatUniform("uYaw", frame.yaw)
            backdrop.setFloatUniform("uPitch", frame.pitch)
            backdrop.setFloatUniform("uCharge", frame.charge)
            backdrop.setFloatUniform("uFlare", frame.flare)
            backdrop.setFloatUniform("uHush", frame.hush)
            backdrop.setFloatUniform("uShock", frame.shock)
            backdrop.setFloatUniform("uBeams", frame.beams)
            backdrop.setFloatUniform("uPhase", frame.clock * 6.2831853f / 12f)
            backdrop.setFloatUniform("uPixelRatio", 1f)
            backdrop.setFloatUniform("uTop", 23f / 255f, 23f / 255f, 94f / 255f)
            backdrop.setFloatUniform("uMid", 10f / 255f, 10f / 255f, 48f / 255f)
            backdrop.setFloatUniform("uBloom", 42f / 255f, 44f / 255f, 224f / 255f)
            backdrop.setFloatUniform("uDust", 123f / 255f, 92f / 255f, 1f)
            paint.shader = backdrop
            canvas.drawRect(0f, 0f, layout.width, layout.height, paint)
            paint.shader = null
        }

        fun pill(
            canvas: Canvas,
            paint: Paint,
            left: Float,
            top: Float,
            width: Float,
            height: Float,
            capsule: Boolean,
            press: Float,
            glow: Float,
            sheen: Float,
            opacity: Float,
            scale: Float,
            time: Float,
            unit: Float,
        ) {
            pill.setFloatUniform("uCentre", left + width / 2f, top + height / 2f)
            pill.setFloatUniform("uSize", width, height)
            pill.setFloatUniform("uTone", if (capsule) 1f else 0f)
            pill.setFloatUniform("uPress", press)
            pill.setFloatUniform("uGlow", glow)
            pill.setFloatUniform("uSheen", sheen)
            pill.setFloatUniform("uOpacity", opacity)
            pill.setFloatUniform("uScale", scale)
            pill.setFloatUniform("uTime", time)
            pill.setFloatUniform("uPixelWidth", 1f)
            paint.shader = pill
            val padding = 40f * unit
            canvas.drawRect(left - padding, top - padding, left + width + padding, top + height + padding, paint)
            paint.shader = null
        }
    }
}
