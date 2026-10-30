package com.ngocdevv.nativecook.blocks.modals.spilledcoffeeerror

import android.graphics.*
import android.os.Build
import kotlin.math.floor
import kotlin.math.sin

/** Premultiplied cool grain. Native shaders output sRGB without the Skia iOS P3 correction. */
internal class SpilledCoffeeErrorShaders(private val density: Float) {
    private val paint = Paint().apply {
        shader = if (Build.VERSION.SDK_INT >= 33) RuntimeShader("""
            float h(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
            half4 main(float2 p) {
                float g = h(floor(p * 0.9));
                return half4(0.18, 0.2, 0.3, 1.0) * (g * 0.05);
            }
        """.trimIndent()) else {
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(512 * 512) { index ->
                val x = floor((index % 512) * 0.9)
                val y = floor((index / 512) * 0.9)
                val hash = sin(x * 12.9898 + y * 78.233) * 43758.5453
                val alpha = ((hash - floor(hash)) * 0.05 * 255).toInt()
                (alpha shl 24) or (46 shl 16) or (51 shl 8) or 77
            }
            bitmap.setPixels(pixels, 0, 512, 0, 0, 512, 512)
            BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }
    init { paint.shader?.setLocalMatrix(Matrix().apply { setScale(density, density) }) }
    fun draw(canvas: Canvas, width: Float, height: Float) = canvas.drawRect(0f, 0f, width, height, paint)
}
