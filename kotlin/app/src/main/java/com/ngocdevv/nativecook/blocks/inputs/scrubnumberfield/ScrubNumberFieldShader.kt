package com.ngocdevv.nativecook.blocks.inputs.scrubnumberfield

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

// The photo's adjustments: one AGSL pass over the image, the same maths as the Skia shader.
//
//   warmth   -1..1   white balance: red up and blue down (or the reverse), luminance kept
//   exposure in stops: linear light times 2^(1.35 e - 0.75). The frame is treated as shot 0.75 of a
//                    stop under and the control is a third stronger than a true stop, so the change
//                    reads in a small silent video; then a soft shoulder so highlights roll off
//   vignette 0..1    darkens towards the corners
//
// Colour: the bitmap is sRGB and so is the canvas, so the shader only decodes the transfer curve,
// works in linear light and encodes again. Coordinates are device pixels.
private const val PHOTO_AGSL = """
uniform shader image;
uniform float2 size;
uniform float exposure;
uniform float warmth;
uniform float vignette;

float3 toLinear(float3 c) {
  float3 lo = c / 12.92;
  float3 hi = pow((c + 0.055) / 1.055, float3(2.4));
  return mix(lo, hi, step(float3(0.04045), c));
}

float3 toEncoded(float3 lin) {
  float3 lo = lin * 12.92;
  float3 hi = 1.055 * pow(lin, float3(1.0 / 2.4)) - 0.055;
  return mix(lo, hi, step(float3(0.0031308), lin));
}

// Linear up to 0.7, then an exponential shoulder that reaches 1 only at infinity.
float3 shoulder(float3 x) {
  float3 over = max(x - 0.7, 0.0);
  return min(x, float3(0.7)) + 0.3 * (1.0 - exp(-over / 0.3));
}

float ign(float2 p) { return fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715)))); }

half4 main(float2 p) {
  float3 lin = toLinear(clamp(image.eval(p).rgb, 0.0, 1.0));

  float3 wb = float3(1.0 + 0.50 * warmth, 1.0 + 0.06 * warmth, 1.0 - 0.60 * warmth);
  wb /= dot(wb, float3(0.2126, 0.7152, 0.0722));
  lin *= wb;

  lin *= exp2(1.35 * exposure - 0.75);

  float2 q = p / size - 0.5;
  float d = length(q) * 1.41421;                       // 0 centre, 1 corners
  lin *= 1.0 - 0.95 * vignette * smoothstep(0.18, 0.92, d);

  float3 rgb = toEncoded(shoulder(max(lin, 0.0)));
  rgb += (ign(p) + ign(p + float2(41.0, 17.0)) - 1.0) / 255.0;   // triangular dither
  return half4(half3(clamp(rgb, 0.0, 1.0)), 1.0);
}
"""

internal const val EXPOSURE_STOPS_PER_STEP = 0.05f
internal const val WARMTH_PER_STEP = 0.01f
internal const val VIGNETTE_PER_STEP = 0.01f

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class ScrubPhotoRuntime(image: BitmapShader) {
    val shader = RuntimeShader(PHOTO_AGSL)

    init {
        image.filterMode = BitmapShader.FILTER_MODE_LINEAR
    }

    fun update(image: BitmapShader, w: Float, h: Float, exposure: Float, warmth: Float, vignette: Float, imageChanged: Boolean) {
        if (imageChanged) shader.setInputShader("image", image)
        shader.setFloatUniform("size", w, h)
        shader.setFloatUniform("exposure", exposure)
        shader.setFloatUniform("warmth", warmth)
        shader.setFloatUniform("vignette", vignette)
    }
}

/**
 * Draws the photo, cover-fitted into a rounded rectangle, through the grade. From API 33 that is the
 * AGSL shader; below it a colour matrix (white balance and exposure as gains on the encoded values)
 * and a radial gradient for the vignette, without the highlight shoulder.
 */
internal class ScrubPhotoPainter(private val bitmap: Bitmap) {
    private val image = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val runtime: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ScrubPhotoRuntime(image) else null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private var fittedW = -1f
    private var fittedH = -1f

    // the fallback's parts
    private val grade = ColorMatrix()
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private var shadeFor = Float.NaN

    fun draw(canvas: Canvas, w: Float, h: Float, radius: Float, exposure: Float, warmth: Float, vignette: Float) {
        val refit = w != fittedW || h != fittedH
        if (refit) {
            val s = max(w / bitmap.width, h / bitmap.height)
            matrix.setScale(s, s)
            matrix.postTranslate((w - bitmap.width * s) / 2f, (h - bitmap.height * s) / 2f)
            image.setLocalMatrix(matrix)
            fittedW = w
            fittedH = h
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && runtime != null) {
            val pass = runtime as ScrubPhotoRuntime
            pass.update(image, w, h, exposure, warmth, vignette, refit)
            paint.alpha = 255
            paint.shader = null // set again, so the paint picks the new uniforms up
            paint.shader = pass.shader
            canvas.drawRoundRect(0f, 0f, w, h, radius, radius, paint)
            return
        }

        // API 26-32: gains in linear light, applied to the encoded values as gain^(1/2.2).
        var r = 1f + 0.50f * warmth
        var g = 1f + 0.06f * warmth
        var b = 1f - 0.60f * warmth
        val norm = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val gain = 2f.pow(1.35f * exposure - 0.75f) / norm
        r = (r * gain).coerceAtLeast(0f).pow(1 / 2.2f)
        g = (g * gain).coerceAtLeast(0f).pow(1 / 2.2f)
        b = (b * gain).coerceAtLeast(0f).pow(1 / 2.2f)
        grade.setScale(r, g, b, 1f)
        paint.alpha = 255
        paint.shader = image
        paint.colorFilter = ColorMatrixColorFilter(grade)
        canvas.drawRoundRect(0f, 0f, w, h, radius, radius, paint)

        if (vignette <= 0f) return
        if (vignette != shadeFor) {
            // black over the photo, in a unit square: alpha = 1 - (1 - 0.95 v smoothstep(0.18, 0.92, d))^(1/2.2)
            val stops = floatArrayOf(0f, 0.18f, 0.30f, 0.42f, 0.54f, 0.66f, 0.78f, 0.92f, 1f)
            val colors = IntArray(stops.size) { i ->
                val x = ((stops[i] - 0.18f) / (0.92f - 0.18f)).coerceIn(0f, 1f)
                val keep = 1f - 0.95f * vignette * (x * x * (3 - 2 * x))
                Color.argb(((1f - keep.pow(1 / 2.2f)) * 255f).roundToInt().coerceIn(0, 255), 0, 0, 0)
            }
            shade.shader = RadialGradient(0.5f, 0.5f, 0.70711f, colors, stops, Shader.TileMode.CLAMP)
            shadeFor = vignette
        }
        clip.rewind()
        clip.addRoundRect(0f, 0f, w, h, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        canvas.scale(w, h)
        canvas.drawRect(0f, 0f, 1f, 1f, shade)
        canvas.restore()
    }
}
