package com.ngocdevv.nativecook.blocks.cards.cityeventhero

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Software glow buffer keeps BlurMaskFilter working on hardware Canvas, including API 26. */
internal class CityEventHeroSparkGlowPainter {
    private val bitmap = Bitmap.createBitmap(640, 625, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val composite = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private var tip = Offset.Zero
    private var unit = 1f

    fun begin(origin: Offset, referenceUnit: Float) {
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        tip = origin
        unit = referenceUnit
        canvas.save()
        canvas.scale(.5f, .5f)
        canvas.translate(640f, 260f)
    }

    fun line(start: Offset, end: Offset, color: Color, width: Float) {
        stroke.color = color.toArgb()
        stroke.strokeWidth = width / unit
        canvas.drawLine((start.x - tip.x) / unit, (start.y - tip.y) / unit, (end.x - tip.x) / unit, (end.y - tip.y) / unit, stroke)
    }

    fun finish(destination: Canvas) {
        canvas.restore()
        destination.drawBitmap(bitmap, null, RectF(tip.x - 640f * unit, tip.y - 260f * unit, tip.x + 640f * unit, tip.y + 990f * unit), composite)
    }
}
