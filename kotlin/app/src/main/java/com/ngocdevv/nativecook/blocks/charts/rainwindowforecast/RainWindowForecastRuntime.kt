package com.ngocdevv.nativecook.blocks.charts.rainwindowforecast

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import kotlin.math.max

@RequiresApi(33)
internal class RainWindowForecastRuntime(sharp: Bitmap, blur: Bitmap) {
    private val shader = RuntimeShader(RainWindowForecastShader.SOURCE)
    private val sharpShader = BitmapShader(sharp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val blurShader = BitmapShader(blur, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val sizes = listOf(sharp.width to sharp.height, blur.width to blur.height)
    private val wipe = FloatArray(72)
    private val matrix = Matrix()
    fun update(w: Float, h: Float, t: Double, frame: RainWindowForecastMotion.Frame): Shader {
        listOf(sharpShader, blurShader).forEachIndexed { i, image ->
            val (iw, ih) = sizes[i]
            val scale = max(w / iw, h / ih)
            matrix.setScale(scale, scale)
            matrix.postTranslate((w - iw * scale) / 2, (h - ih * scale) / 2)
            image.setLocalMatrix(matrix)
        }
        shader.setInputShader("uSharp", sharpShader)
        shader.setInputShader("uBlur", blurShader)
        shader.setFloatUniform("uRes", w, h)
        shader.setFloatUniform("uT", t.toFloat())
        shader.setFloatUniform("uLoop", 10f)
        shader.setFloatUniform("uWipeR", 46f)
        shader.setFloatUniform("uPx", 1f)
        frame.wipe.forEachIndexed { i, p ->
            wipe[i * 3] = p.x.toFloat()
            wipe[i * 3 + 1] = p.y.toFloat()
            wipe[i * 3 + 2] = p.age.toFloat()
        }
        shader.setFloatUniform("uWipe", wipe)
        return shader
    }
}
