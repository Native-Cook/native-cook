// Three right-aligned digit slots roll over 220ms with 0.22em travel and up to 4px blur.
package com.ngocdevv.nativecook.blocks.progress.horizonupload

import android.graphics.*
import androidx.core.graphics.withClip
import kotlin.math.*

internal class HorizonDigits(private val g: HorizonGeometry) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textSize = 26 * g.typeScale; color = 0xFFF4F7FF.toInt()
        fontFeatureSettings = "tnum"; textAlign = Paint.Align.CENTER
    }
    private val digits = Array(10) { it.toString() }
    private val digitWidth = paint.measureText("0")
    private val suffix = paint.measureText(" %")
    private val targets = intArrayOf(-1, -1, 0)
    private val weights = Array(3) { FloatArray(10) }
    private val from = Array(3) { FloatArray(10) }
    private val started = DoubleArray(3) { -220.0 }
    private val direction = IntArray(3) { 1 }
    private val blur = Array(17) { if (it == 0) null else BlurMaskFilter(it / 4f / g.density, BlurMaskFilter.Blur.NORMAL) }
    private var lastPercent = 0
    init { weights[2][0] = 1f; from[2][0] = 1f }
    fun draw(canvas: Canvas, state: HorizonState) {
        val value = state.percent
        canvas.withClip(g.cardWidth - 36 - 78 * g.typeScale, 158 + g.figureTop, g.cardWidth - 36, 158 + g.figureTop + g.figureClip) {
            val baseline = 158 + g.baseline
            val right = g.cardWidth - 36
            paint.alpha = (state.figure.value * 255).toInt(); paint.maskFilter = null; paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(" %", right, baseline, paint); paint.textAlign = Paint.Align.CENTER
            for (slot in 0..2) {
                val divisor = if (slot == 0) 100 else if (slot == 1) 10 else 1
                val target = if (slot < 2 && value < divisor) -1 else value / divisor % 10
                val t = ((state.now - started[slot]) / 220).toFloat().coerceIn(0f, 1f)
                val ease = 1 - (1 - t).pow(3)
                for (digit in 0..9) weights[slot][digit] = from[slot][digit] + ((if (targets[slot] == digit) 1f else 0f) - from[slot][digit]) * ease
                if (target != targets[slot]) {
                    weights[slot].copyInto(from[slot]); targets[slot] = target; started[slot] = state.now
                    direction[slot] = if (value >= lastPercent) 1 else -1
                }
                val x = right - suffix - digitWidth * (2.5f - slot)
                for (digit in 0..9) {
                    val alpha = if (state.reduced) { if (target == digit) 1f else 0f } else weights[slot][digit]
                    if (alpha < .002f) continue
                    val arriving = target == digit
                    val drift = if (state.reduced) 0f else (1 - alpha) * paint.textSize * .22f * direction[slot] * if (arriving) 1 else -1
                    paint.alpha = (alpha * state.figure.value * 255).toInt().coerceIn(0, 255)
                    paint.maskFilter = if (state.reduced) null else blur[((1 - alpha) * 16).roundToInt().coerceIn(0, 16)]
                    canvas.drawText(digits[digit], x, baseline + drift, paint)
                }
            }
            lastPercent = value; paint.maskFilter = null
        }
    }
}
