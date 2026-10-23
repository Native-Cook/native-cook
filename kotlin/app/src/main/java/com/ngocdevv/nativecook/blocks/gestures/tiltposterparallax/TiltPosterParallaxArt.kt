package com.ngocdevv.nativecook.blocks.gestures.tiltposterparallax

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

internal class TiltPosterParallaxArt(
    val layers: List<ImageBitmap>,
    val backdrop: ImageBitmap,
)

private val TiltPosterParallaxOverscan = listOf(1.24, 1.20, 1.18, 1.14, 1.12)

private const val TiltPosterParallaxGrainShader = """
uniform float uScale;
uniform float uAmount;
float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
half4 main(float2 xy) {
    float g = hash(floor(xy * uScale));
    float a = abs(g - 0.5) * uAmount;
    return half4(half3(step(0.5, g)), a);
}
"""

internal fun makeTiltPosterParallaxArt(
    cardWidth: Float,
    cardHeight: Float,
    density: Float,
): TiltPosterParallaxArt {
    val bitmaps = TiltPosterParallaxOverscan.indices.map { index ->
        val width = max(1, (cardWidth * TiltPosterParallaxOverscan[index] * density).roundToInt())
        val height = max(1, (cardHeight * TiltPosterParallaxOverscan[index] * density).roundToInt())
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.scale(density, density)
            drawTiltPosterParallaxLayer(canvas, index, cardWidth, cardHeight, density)
        }
    }
    return TiltPosterParallaxArt(
        layers = bitmaps.map(Bitmap::asImageBitmap),
        backdrop = makeTiltPosterParallaxBackdrop(bitmaps, cardWidth, cardHeight, density).asImageBitmap(),
    )
}

private fun drawTiltPosterParallaxLayer(
    canvas: Canvas,
    index: Int,
    cardWidth: Float,
    cardHeight: Float,
    density: Float,
) {
    val width = cardWidth * TiltPosterParallaxOverscan[index].toFloat()
    val height = cardHeight * TiltPosterParallaxOverscan[index].toFloat()
    val offsetX = (width - cardWidth) / 2f
    val offsetY = (height - cardHeight) / 2f
    fun y(share: Float) = offsetY + cardHeight * share

    when (index) {
        0 -> {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    0f,
                    height,
                    intArrayOf(
                        Color.rgb(20, 26, 48), Color.rgb(46, 43, 74), Color.rgb(122, 78, 94),
                        Color.rgb(217, 140, 98), Color.rgb(242, 183, 126),
                    ),
                    floatArrayOf(0f, y(0.30f) / height, y(0.50f) / height, y(0.64f) / height, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, width, height, paint)
            drawTiltPosterParallaxGrain(canvas, width, height, density, 0.05f, null)
        }

        1 -> {
            val sunX = offsetX + cardWidth * 0.50f
            val sunY = y(0.60f)
            val radius = cardWidth * 0.17f
            val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    sunX,
                    sunY,
                    radius * 3.2f,
                    intArrayOf(0x73FFD6A0, 0x1FFFAA6E, 0x00FFAA6E),
                    floatArrayOf(0f, 0.4f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, width, height, halo)
            val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, sunY - radius, 0f, sunY + radius,
                    Color.rgb(255, 241, 214), Color.rgb(247, 201, 143), Shader.TileMode.CLAMP,
                )
            }
            canvas.drawCircle(sunX, sunY, radius, disc)
        }

        2 -> {
            val path = tiltPosterParallaxRidge(width, height, y(0.64f), cardHeight * 0.05f, 1.3f)
            fillTiltPosterParallaxRidge(canvas, path, 0xFF9C6E78.toInt(), 0xFF7A5A6C.toInt(), y(0.58f), y(0.80f))
            drawTiltPosterParallaxGrain(canvas, width, height, density, 0.04f, path)
        }

        3 -> {
            val path = tiltPosterParallaxRidge(width, height, y(0.73f), cardHeight * 0.06f, 4.1f)
            fillTiltPosterParallaxRidge(canvas, path, 0xFF53405A.toInt(), 0xFF3A2E44.toInt(), y(0.66f), y(0.90f))
            drawTiltPosterParallaxGrain(canvas, width, height, density, 0.04f, path)
        }

        else -> {
            val path = tiltPosterParallaxRidge(width, height, y(0.84f), cardHeight * 0.045f, 2.6f)
            fillTiltPosterParallaxRidge(canvas, path, 0xFF211A28.toInt(), 0xFF120E17.toInt(), y(0.80f), y(1f))
            drawTiltPosterParallaxGrain(canvas, width, height, density, 0.035f, path)
            val figureX = offsetX + cardWidth * 0.62f
            val ground = tiltPosterParallaxRidgeY(y(0.84f), cardHeight * 0.045f, 2.6f, figureX / width) + 1f
            val scale = cardHeight / 250f
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF120E17.toInt() }
            canvas.drawCircle(figureX + 0.3f * scale, ground - 22.5f * scale, 1.9f * scale, ink)
            val body = Path().apply {
                moveTo(figureX - 1.4f * scale, ground - 20f * scale)
                lineTo(figureX + 1.8f * scale, ground - 20f * scale)
                lineTo(figureX + 2.6f * scale, ground - 8.5f * scale)
                lineTo(figureX - 4.2f * scale, ground - 7.5f * scale)
                close()
                moveTo(figureX - 0.9f * scale, ground - 8f * scale)
                lineTo(figureX - 3.2f * scale, ground + 0.5f * scale)
                lineTo(figureX - 2.2f * scale, ground + 0.5f * scale)
                lineTo(figureX + 0.1f * scale, ground - 8f * scale)
                close()
                moveTo(figureX + 0.7f * scale, ground - 8f * scale)
                lineTo(figureX + 2.9f * scale, ground + 0.5f * scale)
                lineTo(figureX + 3.9f * scale, ground + 0.5f * scale)
                lineTo(figureX + 1.7f * scale, ground - 8f * scale)
                close()
            }
            canvas.drawPath(body, ink)
        }
    }
}

private fun tiltPosterParallaxRidgeY(base: Float, amplitude: Float, seed: Float, unit: Float): Float {
    return base - amplitude * (
        0.55f * sin(unit * 5.1f + seed) +
            0.30f * sin(unit * 11.7f + seed * 2.3f) +
            0.15f * sin(unit * 23.9f + seed * 0.7f)
        )
}

private fun tiltPosterParallaxRidge(
    width: Float,
    height: Float,
    base: Float,
    amplitude: Float,
    seed: Float,
): Path {
    return Path().apply {
        moveTo(0f, height)
        repeat(49) { index ->
            val unit = index / 48f
            lineTo(width * unit, tiltPosterParallaxRidgeY(base, amplitude, seed, unit))
        }
        lineTo(width, height)
        close()
    }
}

private fun fillTiltPosterParallaxRidge(
    canvas: Canvas,
    path: Path,
    top: Int,
    bottom: Int,
    startY: Float,
    endY: Float,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, startY, 0f, endY, top, bottom, Shader.TileMode.CLAMP)
    }
    canvas.drawPath(path, paint)
}

private fun drawTiltPosterParallaxGrain(
    canvas: Canvas,
    width: Float,
    height: Float,
    density: Float,
    amount: Float,
    clip: Path?,
) {
    canvas.save()
    if (clip != null) {
        canvas.clipPath(clip)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        drawTiltPosterParallaxShaderGrain(canvas, width, height, density, amount)
    } else {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 0.7f }
        var state = 0x6D2B79F5
        repeat(max(180, (width * height / 28f).roundToInt())) {
            state = state * 1_664_525 + 1_013_904_223
            val x = ((state ushr 8) and 0xFFFF) / 65535f * width
            state = state * 1_664_525 + 1_013_904_223
            val y = ((state ushr 8) and 0xFFFF) / 65535f * height
            paint.color = if ((state and 1) == 0) Color.WHITE else Color.BLACK
            paint.alpha = (amount * 64f).roundToInt().coerceIn(1, 12)
            canvas.drawPoint(x, y, paint)
        }
    }
    canvas.restore()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun drawTiltPosterParallaxShaderGrain(
    canvas: Canvas,
    width: Float,
    height: Float,
    density: Float,
    amount: Float,
) {
    val shader = RuntimeShader(TiltPosterParallaxGrainShader).apply {
        setFloatUniform("uScale", 0.8f * density)
        setFloatUniform("uAmount", amount)
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.shader = shader
        @Suppress("DEPRECATION")
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    canvas.drawRect(0f, 0f, width, height, paint)
}

private fun makeTiltPosterParallaxBackdrop(
    layers: List<Bitmap>,
    cardWidth: Float,
    cardHeight: Float,
    density: Float,
): Bitmap {
    val scale = density / 4f
    val width = max(1, (cardWidth * scale).roundToInt())
    val height = max(1, (cardHeight * scale).roundToInt())
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    layers.forEachIndexed { index, layer ->
        val layerWidth = cardWidth * TiltPosterParallaxOverscan[index].toFloat() * scale
        val layerHeight = cardHeight * TiltPosterParallaxOverscan[index].toFloat() * scale
        canvas.drawBitmap(
            layer,
            null,
            RectF((width - layerWidth) / 2f, (height - layerHeight) / 2f, (width + layerWidth) / 2f, (height + layerHeight) / 2f),
            paint,
        )
    }
    repeat(2) { tiltPosterParallaxBoxBlur(bitmap, 5) }
    return bitmap
}

private fun tiltPosterParallaxBoxBlur(bitmap: Bitmap, radius: Int) {
    val width = bitmap.width
    val height = bitmap.height
    val source = IntArray(width * height)
    val horizontal = IntArray(source.size)
    val output = IntArray(source.size)
    bitmap.getPixels(source, 0, width, 0, 0, width, height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            var count = 0
            for (sampleX in max(0, x - radius)..minOf(width - 1, x + radius)) {
                val color = source[y * width + sampleX]
                a += Color.alpha(color)
                r += Color.red(color)
                g += Color.green(color)
                b += Color.blue(color)
                count++
            }
            horizontal[y * width + x] = Color.argb(a / count, r / count, g / count, b / count)
        }
    }
    for (y in 0 until height) {
        for (x in 0 until width) {
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            var count = 0
            for (sampleY in max(0, y - radius)..minOf(height - 1, y + radius)) {
                val color = horizontal[sampleY * width + x]
                a += Color.alpha(color)
                r += Color.red(color)
                g += Color.green(color)
                b += Color.blue(color)
                count++
            }
            output[y * width + x] = Color.argb(a / count, r / count, g / count, b / count)
        }
    }
    bitmap.setPixels(output, 0, width, 0, 0, width, height)
}
