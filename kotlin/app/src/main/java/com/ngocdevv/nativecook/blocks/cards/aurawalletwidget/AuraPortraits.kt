package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader

// Decode once. A circular bitmap shader crops the enlarged face without
// allocating paths or image effects while a friend follows its drop.
internal class AuraPortraits(context: Context) {
    private val images = Array(6) { index ->
        context.assets.open("aurawalletwidget/${AuraPeople.ids[index]}.png").use {
            checkNotNull(BitmapFactory.decodeStream(it))
        }
    }
    private val shaders = Array(6) { BitmapShader(images[it], Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    private val matrices = Array(6) { Matrix() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun draw(canvas: Canvas, index: Int, radius: Float, zoom: Boolean) {
        val diameter = radius * 2
        val enlarged = diameter * if (zoom) AuraPalette.faceZoom.toFloat() else 1f
        val left = -enlarged / 2
        val top = left + if (zoom) enlarged * AuraPalette.faceLift.toFloat() else 0f
        matrices[index].setScale(enlarged / images[index].width, enlarged / images[index].height)
        matrices[index].postTranslate(left, top)
        shaders[index].setLocalMatrix(matrices[index])
        paint.shader = shaders[index]
        canvas.drawCircle(0f, 0f, radius, paint)
    }
}
