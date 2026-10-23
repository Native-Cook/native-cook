package com.ngocdevv.nativecook.blocks.modals.pitstopreview

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt

/** Cached paints and the once-baked light trail used by the hero draw loop. */
internal class PitStopReviewHeroPaints(
    private val unit: Float,
) {
    val world = PitStopReviewWorldPainter()
    val car = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix(PitStopReviewTheme.carGrade))
    }
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val smokeShader = PitStopReviewSmokePainter()
    val trail = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    val smoke = Paint(Paint.ANTI_ALIAS_FLAG)
    val housing = Paint(Paint.ANTI_ALIAS_FLAG)
    val housingStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.6f * unit
    }
    val lampOff = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(43, 12, 11) }
    val lamp = Paint(Paint.ANTI_ALIAS_FLAG)
    val discCore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        shader = RadialGradient(
            0f,
            0f,
            7.5f * unit,
            intArrayOf(Color.rgb(255, 226, 168), Color.rgb(255, 106, 26), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    val discBloom = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        shader = RadialGradient(
            0f,
            0f,
            17f * unit,
            intArrayOf(Color.argb(204, 226, 52, 12), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    val streak = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
    }
    val trailBitmap: Bitmap = bakeTrail()

    private fun bakeTrail(): Bitmap {
        val width = (300f * unit).roundToInt().coerceAtLeast(1)
        val height = (200f * unit).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(unit, unit)
        canvas.translate(120f, 0f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        PitStopReviewTheme.Hero.trailBlooms.forEachIndexed { index, bloom ->
            paint.shader = null
            paint.alpha = 255
            paint.color = PitStopReviewTheme.Hero.trailBloomColors[index].toArgb()
            paint.alpha = (255f * bloom[5]).roundToInt()
            paint.maskFilter = BlurMaskFilter(bloom[4], BlurMaskFilter.Blur.NORMAL)
            canvas.drawOval(
                bloom[0],
                bloom[1],
                bloom[0] + bloom[2],
                bloom[1] + bloom[3],
                paint,
            )
        }
        paint.alpha = 255
        paint.maskFilter = BlurMaskFilter(
            PitStopReviewTheme.Hero.trailCoreBlur,
            BlurMaskFilter.Blur.NORMAL,
        )
        paint.shader = LinearGradient(
            PitStopReviewTheme.Hero.trailCoreX,
            PitStopReviewTheme.Hero.trailCoreY,
            PitStopReviewTheme.Hero.trailCoreX + PitStopReviewTheme.Hero.trailCoreWidth,
            PitStopReviewTheme.Hero.trailCoreY,
            intArrayOf(
                Color.WHITE,
                Color.rgb(255, 255, 250),
                Color.rgb(255, 236, 170),
                Color.argb(140, 255, 128, 30),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.45f, 0.68f, 0.86f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawOval(
            PitStopReviewTheme.Hero.trailCoreX,
            PitStopReviewTheme.Hero.trailCoreY,
            PitStopReviewTheme.Hero.trailCoreX + PitStopReviewTheme.Hero.trailCoreWidth,
            PitStopReviewTheme.Hero.trailCoreY + PitStopReviewTheme.Hero.trailCoreHeight,
            paint,
        )
        paint.maskFilter = null
        repeat(PitStopReviewTheme.Hero.trailStreaks) { index ->
            val y = PitStopReviewTheme.Hero.trailStreakTop +
                (((index * 37) % 100) / 100f) * PitStopReviewTheme.Hero.trailStreakSpan
            val start = -45f - (index * 13) % 37
            val end = 55f + (index * 29) % 70
            paint.alpha = 255
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = if (index % 3 == 0) 1.6f else 0.8f
            paint.shader = LinearGradient(
                start,
                y,
                end,
                y,
                intArrayOf(
                    if (index % 3 == 0) Color.WHITE else Color.argb(128, 255, 255, 255),
                    Color.argb(153, 255, 210, 90),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawLine(start, y, end, y, paint)
        }
        return bitmap
    }
}
