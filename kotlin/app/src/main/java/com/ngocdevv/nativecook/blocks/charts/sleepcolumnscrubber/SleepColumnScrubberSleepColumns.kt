package com.ngocdevv.nativecook.blocks.charts.sleepcolumnscrubber

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.roundToInt

private val GridColor = Color.White.copy(alpha = 0.16f)
private val BandColor = Color.White.copy(alpha = 0.05f)
private val HatchColor = Color.White.copy(alpha = 0.07f)
private val AverageColor = Color.White.copy(alpha = 0.85f)
private val RingColor = Color.White.copy(alpha = 0.6f)

private const val HATCH_STEP = 7f
private const val RING_WIDTH = 2f
private const val RING_GAP = 2f
// The scrubbed column widens by this share; the others keep this much of their light.
private const val LIFT = 0.12f
private const val DIMMED = 0.7f

/**
 * The chart's frame, in dp.
 *
 * @param top Where the time axis starts on the canvas, [bottom] where it ends.
 * @param from The first minute (top) of the axis, [to] the last (bottom).
 * @param first Centre of the first column, [step] the step to the next.
 */
internal data class ChartFrame(
    val width: Float,
    val top: Float,
    val bottom: Float,
    val from: Float,
    val to: Float,
    val first: Float,
    val step: Float,
    val column: Float,
    val plotWidth: Float,
) {
    fun y(minutes: Double) = top + ((minutes.toFloat() - from) / (to - from)) * (bottom - top)
}

/**
 * The time bands behind the columns.
 *
 * @param ideal Shaded band of the ideal time to fall asleep, in minutes.
 * @param wake Hatched band of the usual wake-up time, in minutes.
 */
internal class Bands(
    val ideal: Pair<Double, Double>,
    val wake: Pair<Double, Double>,
    val grid: List<Double>,
    val averageAsleep: Double,
    val averageAwake: Double,
)

/** The chart's drawing: bands, grid, average lines and one column of sleep stages per night. */
internal class SleepColumns(frame: ChartFrame, nights: List<SleepNight>, bands: Bands) {
    private class Column(val x: Float, val top: Float, val bottom: Float, val stages: List<Pair<Color, Path>>)

    private val columns = nights.mapIndexed { index, night ->
        val paths = LinkedHashMap<SleepStage, Path>()
        val x = frame.first + frame.step * index
        var at = night.asleep
        for (run in stagesOf(night, index)) {
            val y0 = frame.y(at)
            val y1 = frame.y(at + run.minutes)
            // A hair of overlap so no seam shows between runs.
            paths.getOrPut(run.stage) { Path() }.addRect(Rect(x - frame.column, y0, x + frame.column, y1 + 0.5f))
            at += run.minutes
        }
        Column(x, frame.y(night.asleep), frame.y(night.awake), SleepStage.entries.mapNotNull { stage -> paths[stage]?.let { stage.color to it } })
    }
    private val width = frame.width
    private val column = frame.column
    private val gridLines = bands.grid.map { frame.y(it).roundToInt() + 0.5f }
    private val hatchTop = frame.y(bands.wake.first)
    private val hatchBottom = frame.y(bands.wake.second)
    private val idealTop = frame.y(bands.ideal.first)
    private val idealBottom = frame.y(bands.ideal.second)
    private val asleep = frame.y(bands.averageAsleep).roundToInt() + 0.5f
    private val awake = frame.y(bands.averageAwake).roundToInt() + 0.5f
    private val plotWidth = frame.plotWidth
    private val gridDash = PathEffect.dashPathEffect(floatArrayOf(1.5f, 5f))
    private val awakeDash = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
    private val layerPaint = Paint()

    /** Draws in dp; [on] is each column's share of the lift and [press] is 1 while a finger is down. */
    fun DrawScope.draw(on: (Int) -> Float, press: Float, still: Boolean) {
        drawRect(BandColor, Offset(0f, idealTop), Size(width, idealBottom - idealTop))
        clipRect(0f, hatchTop, width, hatchBottom) {
            drawRect(BandColor, Offset(0f, hatchTop), Size(width, hatchBottom - hatchTop))
            val h = hatchBottom - hatchTop
            var x = -h
            while (x < width) {
                drawLine(HatchColor, Offset(x, hatchBottom), Offset(x + h, hatchTop), strokeWidth = 2f)
                x += HATCH_STEP
            }
        }
        for (y in gridLines) drawLine(GridColor, Offset(0f, y), Offset(width, y), strokeWidth = 1f, pathEffect = gridDash)

        columns.forEachIndexed { index, c ->
            val lit = on(index)
            // One night's column: its stage runs clipped to a capsule, widened and ringed while scrubbed.
            withTransform({ scale(1 + (if (still) 0f else LIFT) * lit * press, 1f, pivot = Offset(c.x, (c.top + c.bottom) / 2)) }) {
                val alpha = 1 - (1 - DIMMED) * press * (1 - lit)
                val shape = Path().apply {
                    addRoundRect(RoundRect(c.x - column / 2, c.top, c.x + column / 2, c.bottom, CornerRadius(column / 2)))
                }
                // One layer per column, so the runs' overlap does not show when the column is dimmed.
                drawIntoCanvas { it.saveLayer(Rect(c.x - column, c.top, c.x + column, c.bottom + 1f), layerPaint.apply { this.alpha = alpha }) }
                clipPath(shape) {
                    for ((color, path) in c.stages) drawPath(path, color)
                }
                drawIntoCanvas { it.restore() }
                val ring = lit * press
                if (ring > 0f) {
                    drawRoundRect(
                        RingColor,
                        topLeft = Offset(c.x - column / 2 - RING_GAP, c.top - RING_GAP),
                        size = Size(column + RING_GAP * 2, c.bottom - c.top + RING_GAP * 2),
                        cornerRadius = CornerRadius(column / 2 + RING_GAP),
                        style = Stroke(RING_WIDTH),
                        alpha = ring * alpha,
                    )
                }
            }
        }

        drawLine(AverageColor, Offset(0f, asleep), Offset(plotWidth, asleep), strokeWidth = 1.5f)
        drawLine(AverageColor, Offset(0f, awake), Offset(plotWidth, awake), strokeWidth = 1.5f, pathEffect = awakeDash)
    }
}
