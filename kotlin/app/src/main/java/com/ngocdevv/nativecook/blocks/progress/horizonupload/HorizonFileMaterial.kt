// Original image-lighting shader with a point-space bitmap child, compiled once per layer.
package com.ngocdevv.nativecook.blocks.progress.horizonupload

import android.graphics.*
import androidx.annotation.RequiresApi

@RequiresApi(33)
internal class HorizonFileMaterial(bitmap: Bitmap, private val sheet: Boolean) {
    private val shader = RuntimeShader(horizonFileAgsl)
    private val uniforms = HorizonShaderUniforms()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = this@HorizonFileMaterial.shader }
    private val width = if (sheet) 106f else 98f
    private val height = if (sheet) 128f else 68f
    init {
        val child = BitmapShader(bitmap, Shader.TileMode.DECAL, Shader.TileMode.DECAL)
        val matrix = Matrix().apply { setScale(width / bitmap.width, height / bitmap.height) }
        child.setLocalMatrix(matrix)
        child.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        shader.setInputShader("fAsset", child)
        uniforms.set(shader, "fOpaque", 0f)
        uniforms.set(shader, "fSheet", if (sheet) 1f else 0f)
    }
    fun draw(canvas: Canvas, state: HorizonState, alpha: Float = 1f) {
        uniforms.set(shader, "fPower", state.power.value)
        uniforms.set(shader, "fPulse", if (sheet) state.pulse else 0f)
        uniforms.set(shader, "fGlint", if (sheet && !state.reduced) HorizonMath.part(state.crest.value, .45f, .62f) else 0f)
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        canvas.drawRect(0f, 0f, width, height, paint)
    }
}
