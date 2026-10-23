package com.ngocdevv.nativecook.blocks.gestures.foilpacktear

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import kotlin.math.min

internal class TearCaption(font: Typeface) {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = font
        textAlign = Paint.Align.CENTER
        textSize = 11f
        letterSpacing = 1.4f / 11f
        color = Color.rgb(196, 214, 255)
    }
    private var kind = ""
    private var age = 0.0
    private var lastClock = 0.0
    private var departing = ""
    private var departingAlpha = 0.0

    fun draw(canvas: Canvas, phase: String, clock: Double, width: Float, height: Float, reduced: Boolean) {
        val next = when (phase) {
            "sealed", "tearing" -> "DRAG ACROSS THE TOP TO TEAR"
            "peek" -> "PULL THE CARD OUT"
            "revealed" -> "TAP THE CARD TO COLLECT"
            else -> ""
        }
        if (next != kind) {
            departing = kind
            departingAlpha = ease((age - 0.16) / 0.24)
            kind = next
            age = 0.0
        }
        age += (clock - lastClock).coerceAtLeast(0.0)
        lastClock = clock
        val scale = min(width / 402f, height / 874f)
        canvas.save()
        canvas.translate((width - scale * 402) / 2, (height - scale * 874) / 2)
        canvas.scale(scale, scale)
        val appear = ease((age - 0.16) / 0.24)
        val leave = ease(age / 0.16)
        val baseline = 806f - (paint.ascent() + paint.descent()) / 2
        if (departing.isNotEmpty() && leave < 1) {
            paint.alpha = (143 * departingAlpha * (1 - leave)).toInt()
            canvas.drawText(departing, 201f, baseline - (if (reduced) 0f else (6 * leave).toFloat()), paint)
        }
        paint.alpha = (143 * appear).toInt()
        canvas.drawText(kind, 201f, baseline + (if (reduced) 0f else (6 * (1 - appear)).toFloat()), paint)
        canvas.restore()
    }
}
