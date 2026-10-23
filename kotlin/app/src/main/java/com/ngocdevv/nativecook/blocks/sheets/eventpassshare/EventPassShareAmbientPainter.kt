package com.ngocdevv.nativecook.blocks.sheets.eventpassshare

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build

/**
 * Draws the ambient page and frosted sheet on a native [Canvas]. Coordinates are pixels; [scale]
 * maps reference points to pixels and [referenceY] is the reference y coordinate at canvas y = 0.
 */
internal class EventPassShareAmbientPainter {
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stageRect = RectF()
    private val api33 = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        EventPassShareApi33AmbientPainter()
    } else {
        null
    }
    val isDynamic: Boolean
        get() = api33 != null

    fun drawPage(
        canvas: Canvas,
        width: Float,
        height: Float,
        scale: Float,
        referenceY: Float,
        sheetOffset: Float,
        ambient: EventPassShareAmbientFrame,
    ) {
        val modern = api33
        if (modern != null) {
            modern.drawPage(canvas, width, height, scale, referenceY, sheetOffset, ambient)
        } else {
            drawPageFallback(canvas, width, height)
        }
    }

    fun drawSheet(
        canvas: Canvas,
        width: Float,
        height: Float,
        scale: Float,
        referenceY: Float,
        sheetOffset: Float,
        ambient: EventPassShareAmbientFrame,
        cardSpill: FloatArray,
    ) {
        require(cardSpill.size >= 3)
        val modern = api33
        if (modern != null) {
            modern.drawSheet(canvas, width, height, scale, referenceY, sheetOffset, ambient, cardSpill)
        } else {
            drawSheetFallback(canvas, width, height, scale, referenceY)
        }
    }

    /** The RN RuntimeEffect fallback: #646464 → #4E4E4E. */
    private fun drawPageFallback(canvas: Canvas, width: Float, height: Float) {
        fallbackPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height,
            EventPassShareAmbientFallback.pageTop,
            EventPassShareAmbientFallback.pageBottom,
            Shader.TileMode.CLAMP,
        )
        fallbackPaint.alpha = 255
        canvas.drawRect(0f, 0f, width, height, fallbackPaint)
        fallbackPaint.shader = null
        fallbackPaint.alpha = 255
    }

    /** The RN RuntimeEffect fallback: charcoal sheet with a #656565 → #909090 stage. */
    private fun drawSheetFallback(
        canvas: Canvas,
        width: Float,
        height: Float,
        scale: Float,
        referenceY: Float,
    ) {
        fallbackPaint.shader = null
        fallbackPaint.color = EventPassShareAmbientFallback.charcoal
        fallbackPaint.alpha = 255
        canvas.drawRect(0f, 0f, width, height, fallbackPaint)

        val stage = EventPassShareTheme.stage
        stageRect.set(
            stage.left * scale,
            (stage.top - referenceY) * scale,
            (stage.left + stage.width) * scale,
            (stage.top + stage.height - referenceY) * scale,
        )
        fallbackPaint.shader = LinearGradient(
            stageRect.left,
            stageRect.top,
            stageRect.left,
            stageRect.bottom,
            EventPassShareAmbientFallback.stageTop,
            EventPassShareAmbientFallback.stageBottom,
            Shader.TileMode.CLAMP,
        )
        fallbackPaint.alpha = 255
        canvas.drawRoundRect(stageRect, stage.radius * scale, stage.radius * scale, fallbackPaint)
        fallbackPaint.shader = null
        fallbackPaint.alpha = 255
    }
}

/** Canonical static values used when RuntimeShader is unavailable (API 26-32). */
internal object EventPassShareAmbientFallback {
    const val charcoal = 0xFF222122.toInt()
    const val pageTop = 0xFF646464.toInt()
    const val pageBottom = 0xFF4E4E4E.toInt()
    const val stageTop = 0xFF656565.toInt()
    const val stageBottom = 0xFF909090.toInt()
}
