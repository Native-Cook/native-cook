package com.ngocdevv.nativecook.blocks.gestures.holddragresize

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

internal class HoldDragResizeBackdrop private constructor(
    val sharp: Bitmap,
    val frosted: Bitmap,
    val frostScale: Float,
    val width: Float,
    val height: Float,
) {
    fun recycle() {
        sharp.recycle()
        frosted.recycle()
    }

    companion object {
        fun create(layout: HoldDragResizeLayout, density: Double): HoldDragResizeBackdrop {
            val sharpScale = density.coerceAtLeast(1.0)
            val width = max(1, ceil(layout.width * sharpScale).toInt())
            val height = max(1, ceil(layout.height * sharpScale).toInt())
            val sharp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val sharpCanvas = Canvas(sharp)
            val save = sharpCanvas.save()
            sharpCanvas.scale(sharpScale.toFloat(), sharpScale.toFloat())
            drawPage(sharpCanvas, layout)
            sharpCanvas.restoreToCount(save)
            drawGrain(sharpCanvas, width, height)

            val frostScale = (sharpScale / 3.0).coerceAtLeast(0.5)
            val frostWidth = max(1, ceil(layout.width * frostScale).toInt())
            val frostHeight = max(1, ceil(layout.height * frostScale).toInt())
            val reduced = Bitmap.createBitmap(frostWidth, frostHeight, Bitmap.Config.ARGB_8888)
            Canvas(reduced).drawBitmap(
                sharp,
                null,
                RectF(0f, 0f, frostWidth.toFloat(), frostHeight.toFloat()),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
            val blurRadius = max(1, (24 * frostScale).toInt())
            var frosted = boxBlur(reduced, blurRadius)
            repeat(2) {
                val next = boxBlur(frosted, blurRadius)
                frosted.recycle()
                frosted = next
            }
            reduced.recycle()
            return HoldDragResizeBackdrop(
                sharp,
                frosted,
                frostScale.toFloat(),
                layout.width.toFloat(),
                layout.height.toFloat(),
            )
        }

        private fun drawPage(canvas: Canvas, layout: HoldDragResizeLayout) {
            val width = layout.width.toFloat()
            val height = layout.height.toFloat()
            val whole = RectF(0f, 0f, width, height)
            canvas.drawRect(
                whole,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f,
                        0f,
                        0f,
                        height,
                        intArrayOf(Color.rgb(8, 11, 19), HoldDragResizeTokens.Color.Page, Color.rgb(10, 10, 14)),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                },
            )
            canvas.drawRect(
                whole,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f,
                        height * 0.38f,
                        0f,
                        height,
                        intArrayOf(0x00FF7832, 0x1FE66830, 0x4DFF8C3C),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                },
            )
            canvas.drawRect(
                whole,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        width * 0.92f,
                        height * 0.98f,
                        width * 1.15f,
                        intArrayOf(0x6BFFAA50, 0x2EEC642E, 0x00000000),
                        floatArrayOf(0f, 0.4f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                },
            )
            canvas.drawRect(
                whole,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        width * 0.08f,
                        height * 0.2f,
                        width * 0.9f,
                        intArrayOf(0x245878C8, 0x00000000),
                        null,
                        Shader.TileMode.CLAMP,
                    )
                },
            )

            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x11FFFFFF }
            for (hour in HoldDragResizeTokens.Day.Start / 60..HoldDragResizeTokens.Day.End / 60) {
                val y = layout.yOf(hour * 60.0).toFloat()
                canvas.drawRect(HoldDragResizeTokens.Layout.RailX.toFloat(), y - 0.25f, layout.right.toFloat(), y + 0.25f, line)
            }
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
            var y = layout.yOf(HoldDragResizeTokens.Day.Start.toDouble()) - 6
            while (y <= layout.yOf(HoldDragResizeTokens.Day.End.toDouble()) + 6) {
                canvas.drawCircle(HoldDragResizeTokens.Layout.RailX.toFloat(), y.toFloat(), 0.9f, dot)
                y += 6
            }
            val now = layout.yOf(HoldDragResizeTokens.Day.Now.toDouble()).toFloat()
            val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HoldDragResizeTokens.Color.Now }
            canvas.drawRect(HoldDragResizeTokens.Layout.RailX.toFloat(), now - 0.75f, layout.right.toFloat(), now + 0.75f, red)
            canvas.drawCircle(HoldDragResizeTokens.Layout.RailX.toFloat(), now, 4f, red)
        }

        private fun drawGrain(canvas: Canvas, width: Int, height: Int) {
            val grain = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    var hash = x * 374761393 + y * 668265263
                    hash = (hash xor (hash ushr 13)) * 1274126177
                    val value = ((hash xor (hash ushr 16)) and 15) - 7
                    pixels[y * width + x] = if (value >= 0) Color.argb(value, 255, 255, 255) else Color.argb(-value, 0, 0, 0)
                }
            }
            grain.setPixels(pixels, 0, width, 0, 0, width, height)
            canvas.drawBitmap(grain, 0f, 0f, Paint())
            grain.recycle()
        }

        private fun boxBlur(source: Bitmap, radius: Int): Bitmap {
            val width = source.width
            val height = source.height
            val input = IntArray(width * height)
            val horizontal = IntArray(input.size)
            val output = IntArray(input.size)
            source.getPixels(input, 0, width, 0, 0, width, height)
            val window = radius * 2 + 1
            for (y in 0 until height) {
                var red = 0
                var green = 0
                var blue = 0
                for (offset in -radius..radius) {
                    val color = input[y * width + offset.coerceIn(0, width - 1)]
                    red += Color.red(color)
                    green += Color.green(color)
                    blue += Color.blue(color)
                }
                for (x in 0 until width) {
                    horizontal[y * width + x] = Color.rgb(red / window, green / window, blue / window)
                    val remove = input[y * width + (x - radius).coerceIn(0, width - 1)]
                    val add = input[y * width + (x + radius + 1).coerceIn(0, width - 1)]
                    red += Color.red(add) - Color.red(remove)
                    green += Color.green(add) - Color.green(remove)
                    blue += Color.blue(add) - Color.blue(remove)
                }
            }
            for (x in 0 until width) {
                var red = 0
                var green = 0
                var blue = 0
                for (offset in -radius..radius) {
                    val color = horizontal[offset.coerceIn(0, height - 1) * width + x]
                    red += Color.red(color)
                    green += Color.green(color)
                    blue += Color.blue(color)
                }
                for (y in 0 until height) {
                    output[y * width + x] = Color.rgb(red / window, green / window, blue / window)
                    val remove = horizontal[(y - radius).coerceIn(0, height - 1) * width + x]
                    val add = horizontal[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                    red += Color.red(add) - Color.red(remove)
                    green += Color.green(add) - Color.green(remove)
                    blue += Color.blue(add) - Color.blue(remove)
                }
            }
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                it.setPixels(output, 0, width, 0, 0, width, height)
            }
        }
    }
}
