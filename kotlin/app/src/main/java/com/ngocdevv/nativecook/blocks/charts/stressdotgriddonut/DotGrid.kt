package com.ngocdevv.nativecook.blocks.charts.stressdotgriddonut

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private val LabelColor = Color(0xFF6C6C70)
private val RowLine = Color(0.17f, 0.17f, 0.19f)
private val NowRing = Color(0xFF8E8E93)

// Layout, in dp.
private const val HEADER = 24f
private const val ROW = 28f

// The row labels' column: day names are short, week ranges are not.
private const val DAY_LABELS = 40f
private const val WEEK_LABELS = 92f

// Dot radii: a day's period, a day of the four weeks, and a cell with no data.
private const val DOT_DAY = 4f
private const val DOT_WEEK = 5f
private const val DOT_EMPTY = 2f
private const val NOW_RADIUS = 6f
private const val GLOW_BLUR = 3f
private const val GLOW_ALPHA = 0.45f

// Motion, in seconds of the morph: each dot moves for DOT_FOR, starting up to STAGGER later the
// farther it is from the top left.
internal const val DOT_FOR = 0.6f
internal const val STAGGER = 0.3f
internal const val MORPH_FOR = DOT_FOR + STAGGER
private const val LABELS_OVER = 0.4f

private val LabelStyle = stressText(11f, 14f, FontWeight.Medium, LabelColor)

internal fun gridHeight(period: Period) = HEADER + period.rows.size * ROW

/** Smooth start and stop over 0…1. */
private fun ease(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
}

private class Geometry(period: Period, width: Float, private val labels: Float) {
    private val pitch = (width - labels) / period.columns.size
    fun x(column: Int) = labels + pitch * (column + 0.5f)
    fun y(row: Int) = HEADER + ROW * (row + 0.5f)
}

/** A dot's start and end: place, radius and level, and how long after the morph starts it moves. */
private class Dot(
    val x0: Float, val y0: Float, val r0: Float, val from: Color,
    val x1: Float, val y1: Float, val r1: Float, val to: Color,
    val delay: Float,
)

private class GridPlan(val dots: List<Dot>, val dayLines: List<FloatArray>, val weekLines: List<FloatArray>, val now: FloatArray)

/**
 * The stress heat map. Every two-hour dot of the week has a place in the four-week grid: the day's
 * row becomes that weekday's column, and its hours split into the four week rows, so three dots
 * meet in each cell of the four weeks and the grid can morph either way dot for dot.
 *
 * @param morph 0 shows the days, 1 the weeks; between, every dot is on its way.
 * @param opacity The grid's opacity; dips when the view jumps under reduced motion.
 */
@Composable
internal fun DotGrid(width: Float, days: Period, weeks: Period, morph: () -> Float, opacity: () -> Float, modifier: Modifier = Modifier) {
    val layout = remember(days, weeks, width) {
        val from = Geometry(days, width, DAY_LABELS)
        val to = Geometry(weeks, width, WEEK_LABELS)
        val reach = hypot(width, gridHeight(days))
        val dots = ArrayList<Dot>()
        for (r in days.rows.indices) {
            for (c in days.columns.indices) {
                val level = days.levelAt(r, c)
                val row = min(weeks.rows.size - 1, c * weeks.rows.size / days.columns.size)
                val column = min(weeks.columns.size - 1, r * weeks.columns.size / days.rows.size)
                val target = weeks.levelAt(row, column)
                val x = from.x(c)
                val y = from.y(r)
                dots += Dot(
                    x, y, if (level > 0) DOT_DAY else DOT_EMPTY, LevelColors.ByLevel[level],
                    to.x(column), to.y(row), if (target > 0) DOT_WEEK else DOT_EMPTY, LevelColors.ByLevel[target],
                    hypot(x, y) / reach * STAGGER,
                )
            }
        }
        fun lines(period: Period, at: Geometry) = period.rows.indices.map { floatArrayOf(at.x(0), at.x(period.columns.size - 1), at.y(it)) }
        GridPlan(
            dots, lines(days, from), lines(weeks, to),
            floatArrayOf(from.x(days.now.second), from.y(days.now.first), to.x(weeks.now.second), to.y(weeks.now.first)),
        )
    }
    val line = remember { Paint().apply { strokeWidth = 1f } }
    val dot = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    // Hardware canvases honour mask filters from API 28; below it the dots have no glow.
    val glow = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(sigmaToRadius(GLOW_BLUR), BlurMaskFilter.Blur.NORMAL) }
        } else {
            null
        }
    }
    val ring = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = NowRing.toArgb() } }
    val dayHeight = gridHeight(days)
    val weekHeight = gridHeight(weeks)

    Box(
        modifier
            .clipToBounds()
            .layout { measurable, constraints ->
                val height = ((dayHeight + (weekHeight - dayHeight) * ease(morph())) * density).roundToInt()
                val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, (max(dayHeight, weekHeight) * density).roundToInt()))
                layout(constraints.maxWidth, height) { placeable.place(0, 0) }
            }
            .graphicsLayer { alpha = opacity() },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val m = morph()
            val whole = ease(m)
            scale(density, density, pivot = Offset.Zero) {
                drawIntoCanvas { c ->
                    val canvas = c.nativeCanvas
                    line.color = RowLine.copy(alpha = 1 - whole).toArgb()
                    for (l in layout.dayLines) canvas.drawLine(l[0], l[2], l[1], l[2], line)
                    line.color = RowLine.copy(alpha = whole).toArgb()
                    for (l in layout.weekLines) canvas.drawLine(l[0], l[2], l[1], l[2], line)

                    for (d in layout.dots) {
                        val k = ease((m * MORPH_FOR - d.delay) / DOT_FOR)
                        val x = d.x0 + (d.x1 - d.x0) * k
                        val y = d.y0 + (d.y1 - d.y0) * k
                        val radius = d.r0 + (d.r1 - d.r0) * k
                        // A straight mix of the two colours, as the Skia version adds the channels.
                        val red = d.from.red + (d.to.red - d.from.red) * k
                        val green = d.from.green + (d.to.green - d.from.green) * k
                        val blue = d.from.blue + (d.to.blue - d.from.blue) * k
                        // Only dots with data glow; an empty one fades its glow out as it fills.
                        val lit = (if (d.r0 > DOT_EMPTY) 1 - k else 0f) + (if (d.r1 > DOT_EMPTY) k else 0f)
                        if (glow != null && lit > 0) {
                            glow.color = Color(red, green, blue, GLOW_ALPHA * lit).toArgb()
                            canvas.drawCircle(x, y, radius + 1, glow)
                        }
                        dot.color = Color(red, green, blue).toArgb()
                        canvas.drawCircle(x, y, radius, dot)
                    }

                    val now = layout.now
                    canvas.drawCircle(now[0] + (now[2] - now[0]) * whole, now[1] + (now[3] - now[1]) * whole, NOW_RADIUS, ring)
                }
            }
        }
        // The labels hand over without overlapping: the old ones leave in the first part of the morph, the new arrive in the last.
        Labels(days, width, DAY_LABELS, Modifier.graphicsLayer { alpha = 1 - (morph() / LABELS_OVER).coerceIn(0f, 1f) })
        Labels(weeks, width, WEEK_LABELS, Modifier.graphicsLayer { alpha = ((morph() - 1 + LABELS_OVER) / LABELS_OVER).coerceIn(0f, 1f) })
    }
}

@Composable
private fun Labels(period: Period, width: Float, labelWidth: Float, modifier: Modifier) {
    val at = Geometry(period, width, labelWidth)
    Box(modifier.fillMaxSize()) {
        period.columns.forEachIndexed { c, label ->
            Text(
                label,
                style = LabelStyle.copy(textAlign = TextAlign.Center),
                maxLines = 1,
                modifier = Modifier.offset((at.x(c) - 20).dp, 2.dp).width(40.dp).height(14.dp),
            )
        }
        period.rows.forEachIndexed { r, label ->
            Text(label, style = LabelStyle, maxLines = 1, softWrap = false, modifier = Modifier.offset(0.dp, (at.y(r) - 7).dp).height(14.dp))
        }
    }
}
