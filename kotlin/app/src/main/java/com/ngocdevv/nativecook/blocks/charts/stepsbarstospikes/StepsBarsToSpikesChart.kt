package com.ngocdevv.nativecook.blocks.charts.stepsbarstospikes

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.min
import kotlin.math.pow

/** The bars of the blue field, drawn in one canvas: they sink and rise again on every switch. */
internal object StepsBarsToSpikesChart {
    // A bar fades from its top into the blue; the bar that is now, or the one under the finger, is solid at the top.
    private val BAR = arrayOf(0f to Color(1f, 1f, 1f, 0.42f), 1f to Color(1f, 1f, 1f, 0.03f))
    private val LIT = arrayOf(0f to Color(1f, 1f, 1f, 1f), 0.3f to Color(0.78f, 0.76f, 1f, 0.75f), 1f to Color(0.6f, 0.58f, 1f, 0.08f))

    // Bars are this share of their column, between these widths; a day of a month is a hairline.
    private const val BAR_SHARE = 0.62f
    private const val BAR_MAX = 30f
    private const val SPIKE = 3f
    private const val RADIUS = 7f

    // Motion, in seconds from a switch: the old bars sink together, then the new ones rise one after another.
    const val SINK_FOR = 0.3
    const val RISE_FOR = 0.45
    private const val RISE_GAP_BARS = 0.04
    private const val RISE_GAP_SPIKES = 0.012
    const val SWITCH_FOR = SINK_FOR + RISE_FOR + 0.4

    class Box(val left: Float, val width: Float, val base: Float, val height: Float)

    private class Bar(val x: Float, val width: Float, val height: Float, val delay: Double)

    /** Where each bar of a view stands: its centre, width and full height. */
    private fun barsOf(view: StepsBarsToSpikesView, box: Box): List<Bar> {
        val step = box.width / view.values.size
        val spiky = view.values.size > 7
        val width = if (spiky) SPIKE else min(BAR_MAX, step * BAR_SHARE)
        return view.values.mapIndexed { i, value ->
            Bar(
                x = box.left + step * (i + 0.5f),
                width = width,
                height = (min(value, view.max) / view.max).toFloat() * box.height,
                delay = SINK_FOR + i * (if (spiky) RISE_GAP_SPIKES else RISE_GAP_BARS),
            )
        }
    }

    private fun easeIn(t: Double): Double {
        val x = t.coerceIn(0.0, 1.0)
        return x * x * x
    }

    private fun easeOut(t: Double): Double {
        val x = t.coerceIn(0.0, 1.0)
        return 1 - (1 - x).pow(3)
    }

    private fun DrawScope.drawBars(bars: List<Bar>, box: Box, lit: Int, grown: (Int) -> Double) {
        for (i in bars.indices) {
            val bar = bars[i]
            val h = bar.height * grown(i).toFloat()
            if (h < 0.5f) continue
            val top = box.base - h
            val brush = Brush.verticalGradient(colorStops = if (i == lit) LIT else BAR, startY = top, endY = box.base)
            val r = min(bar.width / 2, RADIUS)
            drawRoundRect(brush, Offset(bar.x - bar.width / 2, top), Size(bar.width, h), CornerRadius(r, r))
        }
    }

    /** Draws, in points, the views switched [from] and [to], [since] seconds after the switch, lighting [picked] (or -1). */
    fun DrawScope.drawChart(box: Box, views: List<StepsBarsToSpikesView>, from: Int, to: Int, since: Double, picked: Int) {
        if (from != to && since < SINK_FOR) {
            val left = 1 - easeIn(since / SINK_FOR)
            drawBars(barsOf(views[from], box), box, views[from].current) { left }
        }
        val rising = barsOf(views[to], box)
        drawBars(rising, box, if (picked >= 0) picked else views[to].current) { i ->
            if (from == to) 1.0 else easeOut((since - rising[i].delay) / RISE_FOR)
        }
    }
}
