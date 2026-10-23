package com.ngocdevv.nativecook.blocks.gestures.foilpacktear

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RuntimeShader
import android.graphics.Shader

@TargetApi(33)
internal class TearMaterial(source: String, additive: Boolean = false) {
    val shader = RuntimeShader(source)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = this@TearMaterial.shader
        if (additive) xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }

    fun uniform(name: String, vararg values: Double) {
        shader.setFloatUniform(name, FloatArray(values.size) { values[it].toFloat() })
    }

    fun image(name: String, bitmap: Bitmap, raw: Boolean = false, decal: Boolean = false) {
        val tile = if (decal) Shader.TileMode.DECAL else Shader.TileMode.CLAMP
        val child = BitmapShader(bitmap, tile, tile).apply { setFilterMode(BitmapShader.FILTER_MODE_LINEAR) }
        // Raw data sampling bypasses colour-space conversion and premultiplication.
        if (raw) shader.setInputBuffer(name, child) else shader.setInputShader(name, child)
    }
}
