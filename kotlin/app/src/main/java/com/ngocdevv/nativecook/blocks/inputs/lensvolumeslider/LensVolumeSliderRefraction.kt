package com.ngocdevv.nativecook.blocks.inputs.lensvolumeslider

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build

/**
 * The lens cap samples a raster of the glyph and numeral through an AGSL ball lens on API 33+.
 * Older devices retain the same inverted, magnified Canvas treatment rather than silently dropping it.
 */
internal class LensVolumeSliderRefraction {
    private var source: Bitmap? = null
    private var sourceCanvas: Canvas? = null
    private var runtime: RuntimeShader? = null
    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    val usesAgsl: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        centerX: Float,
        centerY: Float,
        radius: Float,
        drawSource: (Canvas) -> Unit,
        drawFallback: (Canvas) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            drawFallback(canvas)
            return
        }
        val scratch = ensureSource(width, height)
        val scratchCanvas = sourceCanvas ?: return
        scratchCanvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        drawSource(scratchCanvas)
        val effect = runtime ?: return
        effect.setFloatUniform("uCenter", centerX, centerY)
        effect.setFloatUniform("uRadius", radius)
        canvas.drawCircle(centerX, centerY, radius, shaderPaint)
    }

    private fun ensureSource(width: Int, height: Int): Bitmap {
        val old = source
        if (old != null && old.width == width && old.height == height) return old
        old?.recycle()
        return Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888).also { bitmap ->
            source = bitmap
            sourceCanvas = Canvas(bitmap)
            runtime = RuntimeShader(AGSL).apply {
                setInputShader("content", BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
            }
            shaderPaint.shader = runtime
        }
    }

    private companion object {
        // `content` is the already-drawn value/glyph layer; the circular cap reverses its centre and adds a subtle blue rim.
        const val AGSL = """
            uniform shader content;
            uniform float2 uCenter;
            uniform float uRadius;
            half4 main(float2 p) {
              float2 q = (p - uCenter) / uRadius;
              float r = length(q);
              float edge = smoothstep(0.64, 1.0, r);
              float gain = mix(1.8, 0.85, min(r, 1.0) * min(r, 1.0));
              float2 sampleAt = uCenter - q * uRadius / gain;
              half4 base = content.eval(sampleAt);
              float blue = smoothstep(0.76, 1.0, r) * base.a * 0.22;
              return half4(base.r * (1.0 - blue), base.g, min(1.0, base.b + blue), base.a);
            }
        """
    }
}
