package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Size
import kotlin.math.roundToInt

// Portrait shaders and their cached side-row blur levels belong to one card instance.
internal class ReferralPortraits(
    context: Context,
    private val density: Float,
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Side portraits are desaturated to 62%; the focused portrait keeps its full color.
    private val dull =
        ColorMatrixColorFilter(
            floatArrayOf(
                0.700788f,
                0.271776f,
                0.027436f,
                0f,
                0f,
                0.080788f,
                0.891776f,
                0.027436f,
                0f,
                0f,
                0.080788f,
                0.271776f,
                0.647436f,
                0f,
                0f,
                0f,
                0f,
                0f,
                1f,
                0f,
            ),
        )
    private val images =
        Array(ReferralPalette.friendCount) { i ->
            context.assets.open("referralrewardcard/${ReferralFriends.ids[i]}.png").use {
                BitmapFactory.decodeStream(it)
            }
        }
    private val shaders =
        Array(ReferralPalette.friendCount) { i ->
            BitmapShader(images[i], Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
    private val matrices = Array(ReferralPalette.friendCount) { Matrix() }

    // The edge fade hides portraits by distance 3.5, where softness reaches 3.75 dp.
    // Include the ramp's 1.1 dp shoulder and interpolate cached sigmas between these stops.
    private val sideImages =
        Array(ReferralPalette.friendCount) { i ->
            // Sixteen dp of padding on each side contain the largest cached blur.
            val side = ((Size.avatar + 32) * density).roundToInt()
            val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.scale(density, density)
            canvas.translate(Size.avatar / 2f + 16, Size.avatar / 2f + 16)
            portrait(canvas, i, Size.avatar / 2f, true)
            ReferralBlurImages(bitmap, floatArrayOf(0f, 0.55f, 1.1f, 2f, 3f, 3.75f), density)
        }

    fun portrait(
        canvas: Canvas,
        index: Int,
        radius: Float,
        side: Boolean,
    ) {
        matrices[index].setScale(radius * 2 / images[index].width, radius * 2 / images[index].height)
        matrices[index].postTranslate(-radius, -radius)
        shaders[index].setLocalMatrix(matrices[index])
        paint.shader = shaders[index]
        paint.colorFilter = if (side) dull else null
        paint.alpha = 255
        canvas.drawCircle(0f, 0f, radius, paint)
        paint.shader = null
        paint.colorFilter = null
    }

    fun side(
        canvas: Canvas,
        index: Int,
        softness: Float,
    ) {
        sideImages[index].draw(canvas, softness)
    }
}
