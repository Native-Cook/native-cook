package com.ngocdevv.nativecook.blocks.charts.tidegaugechart

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Static layers of the chart card (a port of tideGaugeArt.ts): the card itself with its shadows and
 lit edge, the backdrop of the plot (night bands before sunrise and after sunset, grid, axis
 labels, sun markers, the high and low tags), drawn once into the card's layer; and the buoy,
 pre-rendered into a bitmap around its waterline so a per-frame transform moves it.
*/

internal const val TIDE_CARD_RADIUS = 24f

private fun c(hex: String) = android.graphics.Color.parseColor(hex)
private fun rgba(r: Int, g: Int, b: Int, a: Float) = android.graphics.Color.argb((a * 255f).roundToInt(), r, g, b)

private val INK = c("#E6F0F3")
private val INK_SOFT = rgba(200, 222, 230, 0.62f)
private val INK_FAINT = rgba(200, 222, 230, 0.16f)
private val NIGHT = rgba(3, 10, 18, 0.32f)
private val SUN = c("#F6C46A")

private fun font(size: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = size
    typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SANS_SERIF, 600, false)
    } else {
        Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
}

private fun sunMark(canvas: Canvas, x: Float, y: Float, rising: Boolean) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SUN }
    // Half a sun on a horizon line, with five short rays and an arrow for up or down.
    val half = Path().apply {
        addArc(RectF(x - 6, y - 6, x + 6, y + 6), 180f, 180f)
        close()
    }
    canvas.drawPath(half, paint)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f
        strokeCap = Paint.Cap.ROUND
        color = SUN
    }
    for (i in 0 until 5) {
        val a = PI + PI * (i + 0.5) / 5
        canvas.drawLine(x + cos(a).toFloat() * 8.5f, y + sin(a).toFloat() * 8.5f, x + cos(a).toFloat() * 11, y + sin(a).toFloat() * 11, stroke)
    }
    stroke.color = rgba(246, 196, 106, 0.55f)
    canvas.drawLine(x - 12, y + 0.5f, x + 12, y + 0.5f, stroke)
    val dir = if (rising) -1 else 1
    val arrow = Path().apply {
        moveTo(x - 2.5f, y + 5 - dir * 1.5f)
        lineTo(x, y + 5 + dir * 1.5f)
        lineTo(x + 2.5f, y + 5 - dir * 1.5f)
    }
    stroke.color = SUN
    stroke.strokeWidth = 1.2f
    canvas.drawPath(arrow, stroke)
}

/** The card, its shadows, its lit edge and the plot's backdrop, in card-local dp. */
internal fun drawTideCard(canvas: Canvas, width: Float, height: Float, plot: TidePlot) {
    val r = TIDE_CARD_RADIUS
    // Ambient and contact shadows (mask-filter blur needs API 28 on a hardware canvas).
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
        shadow.color = rgba(1, 8, 14, 0.55f)
        shadow.maskFilter = BlurMaskFilter(sigmaToRadius(22f), BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(4f, 18f, width - 4, 18 + height - 10, r, r, shadow)
        shadow.color = rgba(1, 8, 14, 0.5f)
        shadow.maskFilter = BlurMaskFilter(sigmaToRadius(3f), BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(1f, 2f, width - 1, 2 + height, r, r, shadow)
    }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    fill.shader = LinearGradient(0f, 0f, width * 0.3f, height, intArrayOf(c("#123447"), c("#0C2636"), c("#091C2A")), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    canvas.drawRoundRect(0f, 0f, width, height, r, r, fill)
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        shader = LinearGradient(
            0f, 0f, 0f, height,
            intArrayOf(rgba(190, 230, 245, 0.16f), rgba(190, 230, 245, 0.04f), rgba(0, 0, 0, 0.25f)),
            floatArrayOf(0f, 0.3f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRoundRect(0.5f, 0.5f, width - 0.5f, height - 0.5f, r - 0.5f, r - 0.5f, edge)

    val small = font(11f)
    val tiny = font(10f)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Night before sunrise and after sunset.
    paint.color = NIGHT
    canvas.drawRect(plot.x, plot.y, hourToX(TIDE_SUNRISE, plot), plot.y + plot.h, paint)
    canvas.drawRect(hourToX(TIDE_SUNSET, plot), plot.y, plot.x + plot.w, plot.y + plot.h, paint)

    // Level lines every metre, labelled at the right edge.
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = INK_FAINT
    }
    tiny.color = INK_SOFT
    small.color = INK_SOFT
    for (m in 0..floor(TIDE_H_MAX).toInt()) {
        if (m < TIDE_H_MIN) continue
        val y = levelToY(m.toFloat(), plot)
        canvas.drawLine(plot.x, y, plot.x + plot.w, y, line)
        val text = "$m m"
        canvas.drawText(text, plot.x + plot.w - tiny.measureText(text) - 4, y - 4, tiny)
    }

    // Hour ticks every 3 h, labels every 6 h.
    line.color = rgba(200, 222, 230, 0.10f)
    for (h in 0..TIDE_DAY.toInt() step 3) {
        val x = hourToX(h.toFloat(), plot)
        canvas.drawLine(x, plot.y, x, plot.y + plot.h, line)
        if (h % 6 == 0) {
            val text = "${if (h < 10) "0" else ""}$h:00"
            val w = small.measureText(text)
            val tx = min(max(x - w / 2, plot.x), plot.x + plot.w - w)
            canvas.drawText(text, tx, plot.y + plot.h + 18, small)
        }
    }

    // Sunrise and sunset under the axis.
    sunMark(canvas, hourToX(TIDE_SUNRISE, plot), plot.y + plot.h + 32, true)
    sunMark(canvas, hourToX(TIDE_SUNSET, plot), plot.y + plot.h + 32, false)
    canvas.drawText("06:12", hourToX(TIDE_SUNRISE, plot) + 15, plot.y + plot.h + 36, tiny)
    canvas.drawText("18:47", hourToX(TIDE_SUNSET, plot) - 15 - tiny.measureText("18:47"), plot.y + plot.h + 36, tiny)

    // "Now" marker above the plot.
    val nx = hourToX(TIDE_NOW, plot)
    val tag = "NOW 10:40"
    val tw = tiny.measureText(tag)
    paint.color = rgba(230, 240, 243, 0.10f)
    canvas.drawRoundRect(nx - tw / 2 - 7, plot.y - 24, nx + tw / 2 + 7, plot.y - 8, 8f, 8f, paint)
    tiny.color = INK
    canvas.drawText(tag, nx - tw / 2, plot.y - 12.5f, tiny)
    line.color = rgba(230, 240, 243, 0.28f)
    line.pathEffect = DashPathEffect(floatArrayOf(2f, 3f), 0f)
    canvas.drawLine(nx, plot.y - 8, nx, plot.y + plot.h, line)
    line.pathEffect = null

    // High and low tags just above the curve's turning points.
    tiny.color = INK_SOFT
    val tags = listOf(
        TIDE_HIGH_AT - TIDE_PERIOD to true,
        TIDE_HIGH_AT - TIDE_PERIOD / 2 to false,
        TIDE_HIGH_AT to true,
        TIDE_HIGH_AT + TIDE_PERIOD / 2 to false,
    )
    for ((hour, high) in tags) {
        if (hour < 0.5f || hour > TIDE_DAY - 0.5f) continue
        val x = hourToX(hour, plot)
        val y = levelToY(if (high) 2.4f else 0.3f, plot) - 14
        val text = if (high) "H 2.4" else "L 0.3"
        canvas.drawText(text, x - tiny.measureText(text) / 2, y, tiny)
    }
}

/** The buoy bitmap covers (-30, -50) – (30, 20) dp around its waterline. */
internal const val TIDE_BUOY_LEFT = -30f
internal const val TIDE_BUOY_TOP = -50f
internal const val TIDE_BUOY_W = 60f
internal const val TIDE_BUOY_H = 70f

/** The buoy at the origin: waterline at y = 0, mast up, light at (0, -31), at [scale] px per dp. */
internal fun buildTideBuoy(scale: Float): Bitmap {
    val bmp = Bitmap.createBitmap(ceil(TIDE_BUOY_W * scale).toInt(), ceil(TIDE_BUOY_H * scale).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.scale(scale, scale)
    canvas.translate(-TIDE_BUOY_LEFT, -TIDE_BUOY_TOP)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)

    // Shadow ring on the water.
    p.color = rgba(4, 24, 40, 0.45f)
    p.maskFilter = BlurMaskFilter(sigmaToRadius(2.5f), BlurMaskFilter.Blur.NORMAL)
    canvas.drawOval(-17f, -2f, 17f, 5f, p)
    p.maskFilter = null

    // Hull: a rounded float, red with a white band, lit from the upper left.
    val hull = Path().apply {
        moveTo(-13f, 1f)
        cubicTo(-13f, -9f, -8f, -13f, 0f, -13f)
        cubicTo(8f, -13f, 13f, -9f, 13f, 1f)
        cubicTo(8f, 4f, -8f, 4f, -13f, 1f)
        close()
    }
    p.color = android.graphics.Color.BLACK
    p.shader = LinearGradient(-13f, -13f, 13f, 3f, intArrayOf(c("#FF8A5C"), c("#E5462B"), c("#8E2117")), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    canvas.drawPath(hull, p)
    p.shader = null
    canvas.save()
    canvas.clipPath(hull)
    p.color = rgba(244, 240, 232, 0.92f)
    canvas.drawRect(-14f, -8f, 14f, -4.5f, p)
    canvas.restore()
    // Specular on the hull.
    p.color = rgba(255, 236, 226, 0.55f)
    p.maskFilter = BlurMaskFilter(sigmaToRadius(1.2f), BlurMaskFilter.Blur.NORMAL)
    canvas.drawOval(-9f, -11.5f, -3f, -8f, p)
    p.maskFilter = null

    // Mast, cage and lamp housing.
    p.color = c("#2B3540")
    canvas.drawRect(-1.2f, -27f, 1.2f, -13f, p)
    val cage = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = c("#3A4652")
    }
    canvas.drawLine(-6f, -13f, -1f, -27f, cage)
    canvas.drawLine(6f, -13f, 1f, -27f, cage)
    p.color = c("#E9E2D2")
    canvas.drawRoundRect(-3.5f, -34f, 3.5f, -27f, 2f, 2f, p)
    p.color = c("#FFD27A")
    canvas.drawCircle(0f, -30.5f, 2.2f, p)
    return bmp
}
