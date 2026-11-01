package com.ngocdevv.nativecook.blocks.sheets.dronedroptracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Cached Android Canvas renderer for the map, drone, and order sheet. */
internal class DroneDropTrackerArt(context: Context) {
    private val regular = Typeface.createFromAsset(
        context.assets,
        "dronedroptracker/dronedroptracker_Inter-Regular.ttf",
    )
    private val semiBold = Typeface.createFromAsset(
        context.assets,
        "dronedroptracker/dronedroptracker_Inter-SemiBold.ttf",
    )
    private val bold = Typeface.createFromAsset(
        context.assets,
        "dronedroptracker/dronedroptracker_Inter-Bold.ttf",
    )
    private val ionicons = Typeface.createFromAsset(
        context.assets,
        "dronedroptracker/dronedroptracker_Ionicons.ttf",
    )

    private var staticTown: Bitmap? = null
    private var staticTownW = -1
    private var staticTownH = -1

    private fun color(value: String, alpha: Float = 1f): Int {
        val base = Color.parseColor(value)
        return Color.argb(
            (Color.alpha(base) * alpha).roundToInt().coerceIn(0, 255),
            Color.red(base),
            Color.green(base),
            Color.blue(base),
        )
    }

    private fun fill(value: String, alpha: Float = 1f): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color(value, alpha)
    }

    private fun stroke(value: String, width: Float, alpha: Float = 1f): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color(value, alpha)
    }

    private fun text(
        typeface: Typeface,
        size: Float,
        value: String,
        align: Paint.Align = Paint.Align.LEFT,
        alpha: Float = 1f,
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        this.typeface = typeface
        textSize = size
        textAlign = align
        this.color = color(value, alpha)
    }

    private fun roundRect(canvas: Canvas, left: Double, top: Double, right: Double, bottom: Double, radius: Double, paint: Paint) {
        canvas.drawRoundRect(
            left.toFloat(),
            top.toFloat(),
            right.toFloat(),
            bottom.toFloat(),
            radius.toFloat(),
            radius.toFloat(),
            paint,
        )
    }

    private fun centeredText(canvas: Canvas, value: String, x: Double, centerY: Double, paint: Paint) {
        val metrics = paint.fontMetrics
        val baseline = centerY - (metrics.ascent + metrics.descent) * 0.5
        canvas.drawText(value, x.toFloat(), baseline.toFloat(), paint)
    }

    private fun rightText(canvas: Canvas, value: String, right: Double, centerY: Double, paint: Paint) {
        paint.textAlign = Paint.Align.RIGHT
        centeredText(canvas, value, right, centerY, paint)
    }

    private fun icon(canvas: Canvas, code: Int, x: Double, y: Double, size: Double, value: String) {
        val paint = text(ionicons, size.toFloat(), value, Paint.Align.CENTER)
        val metrics = paint.fontMetrics
        val baseline = y - (metrics.ascent + metrics.descent) * 0.5
        canvas.drawText(code.toChar().toString(), x.toFloat(), baseline.toFloat(), paint)
    }

    private fun iconCode(name: IconName): Int = when (name) {
        IconName.ChevronBack -> 61993
        IconName.Help -> 62329
        IconName.PaperPlane -> 62605
        IconName.Checkmark -> 61981
        IconName.BagCheckOutline -> 61788
        IconName.PaperPlaneOutline -> 62606
        IconName.DownloadOutline -> 62138
        IconName.ChatbubbleOutline -> 61973
        IconName.LocationOutline -> 62405
    }

    private enum class IconName {
        ChevronBack,
        Help,
        PaperPlane,
        Checkmark,
        BagCheckOutline,
        PaperPlaneOutline,
        DownloadOutline,
        ChatbubbleOutline,
        LocationOutline,
    }

    private fun house(canvas: Canvas, x: Double, y: Double, width: Double, roof: String, wall: String) {
        roundRect(canvas, x - width / 2.0, y - width * 0.15, x + width / 2.0, y + width * 0.40, 4.0, fill(wall))
        val roofPath = Path().apply {
            moveTo((x - width * 0.60).toFloat(), (y - width * 0.10).toFloat())
            lineTo(x.toFloat(), (y - width * 0.62).toFloat())
            lineTo((x + width * 0.60).toFloat(), (y - width * 0.10).toFloat())
            close()
        }
        canvas.drawPath(roofPath, fill(roof))
        canvas.drawRect(
            (x - width * 0.10).toFloat(),
            (y + width * 0.12).toFloat(),
            (x + width * 0.10).toFloat(),
            (y + width * 0.40).toFloat(),
            fill("#7A5A44"),
        )
    }

    private fun ensureTown(width: Double, height: Double) {
        val bitmapWidth = max(1, width.roundToInt())
        val bitmapHeight = max(1, height.roundToInt())
        if (staticTown != null && bitmapWidth == staticTownW && bitmapHeight == staticTownH) return
        staticTownW = bitmapWidth
        staticTownH = bitmapHeight
        staticTown = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888).also {
            it.density = Bitmap.DENSITY_NONE
            drawStaticTown(Canvas(it), width, height)
        }
    }

    /** Town blocks, roads, trees, houses, shop, and home are unchanged during the loop. */
    private fun drawStaticTown(canvas: Canvas, width: Double, height: Double) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill("#BFE6C9"))
        for (i in 0 until 4) {
            for (j in 0 until 5) {
                roundRect(
                    canvas,
                    (0.02 + i * 0.25) * width,
                    (0.02 + j * 0.22) * height,
                    (0.02 + i * 0.25 + 0.21) * width,
                    (0.02 + j * 0.22 + 0.18) * height,
                    14.0,
                    fill("#D3F0D9"),
                )
            }
        }
        val roads = stroke("#FFFFFF", 16f)
        for (i in 0 until 4) {
            val y = (0.20 + i * 0.22) * height
            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), roads)
        }
        for (i in 0 until 4) {
            val x = (0.245 * i + 0.005) * width
            canvas.drawLine(x.toFloat(), -20f, x.toFloat(), (height + 20).toFloat(), roads)
        }
        for (i in 0 until 18) {
            val tx = (((i * 0.137) % 1.0) * 0.96 + 0.02) * width
            val ty = (((i * 0.311) % 1.0) * 0.94 + 0.03) * height
            canvas.drawCircle((tx + 2).toFloat(), (ty + 3).toFloat(), 9f, fill("#2E6B4A", 0.18f))
            canvas.drawCircle(tx.toFloat(), ty.toFloat(), 9f, fill(if (i % 3 == 0) "#46A65A" else "#5DBB6B"))
        }
        val roofs = arrayOf("#FF8A65", "#7E9CFF", "#FFC857", "#B98BFF")
        for (i in 0 until 9) {
            house(
                canvas,
                (0.10 + ((i * 0.29) % 1.0) * 0.80) * width,
                (0.10 + ((i * 0.43) % 1.0) * 0.80) * height,
                26.0,
                roofs[i % roofs.size],
                "#FFF6EA",
            )
        }
        val shopX = DroneDropTrackerMotion.route.first().x * width
        val shopY = DroneDropTrackerMotion.route.first().y * height
        roundRect(canvas, shopX - 26, shopY - 22, shopX + 26, shopY + 14, 8.0, fill("#FFFFFF"))
        roundRect(canvas, shopX - 26, shopY - 30, shopX + 26, shopY - 18, 6.0, fill("#FF6B5A"))
        val home = DroneDropTrackerMotion.route.last()
        val homeX = home.x * width
        val homeY = home.y * height
        house(canvas, homeX, homeY, 40.0, "#2F9E62", "#FFFFFF")
        canvas.drawOval(
            homeX.toFloat() - 22f,
            homeY.toFloat() + 22f,
            homeX.toFloat() + 22f,
            homeY.toFloat() + 34f,
            fill("#2F9E62", 0.20f),
        )
    }

    fun drawScene(
        canvas: Canvas,
        widthPx: Float,
        heightPx: Float,
        density: Float,
        statusInsetPx: Float,
        bottomInsetPx: Float,
        frame: DroneDropTrackerMotion.Frame,
        reducedMotion: Boolean,
    ) {
        val width = widthPx.toDouble() / density
        val height = heightPx.toDouble() / density
        val topInset = statusInsetPx.toDouble() / density
        val bottomInset = bottomInsetPx.toDouble() / density
        val sheetMin = 150.0 + bottomInset
        val sheetMax = (height * 0.55).roundToInt().toDouble()
        val mapTop = topInset + 56.0
        val mapHeight = height - topInset - 56.0 - sheetMin + 30.0
        val focusY = topInset + 56.0 + (height - sheetMax - topInset - 56.0) * 0.42

        canvas.save()
        canvas.scale(density, density)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill("#BFE6C9"))
        drawMap(canvas, width, mapTop, mapHeight, focusY, frame, reducedMotion)
        drawHeader(canvas, width, topInset)
        drawSheet(canvas, width, height, bottomInset, sheetMin, sheetMax, frame)
        canvas.restore()
    }

    private fun drawMap(
        canvas: Canvas,
        width: Double,
        top: Double,
        mapHeight: Double,
        focusY: Double,
        frame: DroneDropTrackerMotion.Frame,
        reducedMotion: Boolean,
    ) {
        val position = if (frame.back > 0.0) {
            DroneDropTrackerMotion.routeAt(1.0 - frame.back)
        } else {
            DroneDropTrackerMotion.routeAt(frame.f)
        }
        val px = position.x * width
        val py = top + position.y * mapHeight
        val zoom = if (reducedMotion) 1.0 else 1.0 + 0.5 * frame.sheet
        canvas.save()
        val fx = DroneDropTrackerMotion.lerp(width / 2.0, px, frame.sheet)
        val fy = DroneDropTrackerMotion.lerp(top + mapHeight / 2.0, py, frame.sheet)
        canvas.translate(
            DroneDropTrackerMotion.lerp(0.0, width / 2.0 - fx, frame.sheet).toFloat(),
            DroneDropTrackerMotion.lerp(0.0, focusY - fy, frame.sheet).toFloat(),
        )
        canvas.translate(fx.toFloat(), fy.toFloat())
        canvas.scale(zoom.toFloat(), zoom.toFloat())
        canvas.translate(-fx.toFloat(), -fy.toFloat())

        canvas.drawRect(
            -width.toFloat(),
            (top - mapHeight).toFloat(),
            (width * 2.0).toFloat(),
            (top + mapHeight * 2.0).toFloat(),
            fill("#BFE6C9"),
        )
        ensureTown(width, mapHeight)
        staticTown?.let { town ->
            canvas.drawBitmap(town, 0f, top.toFloat(), fill("#FFFFFF"))
        }

        val routePath = Path().apply {
            DroneDropTrackerMotion.route.forEachIndexed { index, point ->
                val x = point.x * width
                val y = top + point.y * mapHeight
                if (index == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
            }
        }
        canvas.drawPath(
            routePath,
            stroke("#2F3A4A", 3f, 0.50f).apply {
                pathEffect = DashPathEffect(floatArrayOf(2f, 9f), 0f)
            },
        )
        if (frame.back == 0.0 && frame.f > 0.0) {
            val measure = PathMeasure(routePath, false)
            val done = Path()
            measure.getSegment(0f, measure.length * frame.f.toFloat(), done, true)
            canvas.drawPath(done, stroke("#2F3A4A", 4f, 0.90f))
        }
        if (frame.carried < 1.0 && frame.back > 0.0 && frame.back < 1.0) {
            val home = DroneDropTrackerMotion.route.last()
            val hx = home.x * width
            val hy = top + home.y * mapHeight
            roundRect(canvas, hx - 9, hy + 18, hx + 9, hy + 32, 3.0, fill("#D9A066"))
        }

        val hover = if (reducedMotion) 0.0 else sin(frame.t * (14 * PI / 9)) * 1.5
        val droneY = py - 34.0 + hover
        canvas.drawOval(
            (px - 12).toFloat(),
            (py).toFloat(),
            (px + 32).toFloat(),
            (py + 14).toFloat(),
            fill("#1E3A2A", 0.16f),
        )
        val lineLength = 10.0 + 44.0 * frame.drop
        val sway = if (reducedMotion) 0.0 else {
            sin(frame.t * (8 * PI / 9)) * 4.0 * (1.0 - frame.drop) * if (frame.f > 0.0 && frame.f < 1.0) 1.0 else 0.3
        }
        if (frame.carried > 0.0) {
            canvas.drawLine(
                px.toFloat(),
                (droneY + 8.0).toFloat(),
                (px + sway).toFloat(),
                (droneY + 8.0 + lineLength).toFloat(),
                stroke("#2F3A4A", 1.4f, (0.8 * frame.carried).toFloat()),
            )
            canvas.save()
            canvas.translate((px + sway).toFloat(), (droneY + 8.0 + lineLength).toFloat())
            roundRect(canvas, -10.0, 0.0, 10.0, 16.0, 3.0, fill("#D9A066", frame.carried.toFloat()))
            canvas.drawRect(-10f, 6f, 10f, 9f, fill("#FF6B5A", frame.carried.toFloat()))
            canvas.restore()
        }
        roundRect(canvas, px - 13, droneY - 7, px + 13, droneY + 9, 7.0, fill("#F4F6FA"))
        roundRect(canvas, px - 13, droneY - 7, px + 13, droneY + 9, 7.0, stroke("#C9D1DC", 1.2f))
        canvas.drawCircle(px.toFloat(), (droneY + 1).toFloat(), 4f, fill("#3D7BFF"))
        for (index in 0 until 4) {
            val armX = px + if (index % 2 == 1) 22.0 else -22.0
            val armY = droneY + if (index < 2) -9.0 else 9.0
            canvas.drawLine(px.toFloat(), droneY.toFloat(), armX.toFloat(), armY.toFloat(), stroke("#C9D1DC", 3f))
            canvas.drawOval(
                (armX - 13).toFloat(),
                (armY - 4).toFloat(),
                (armX + 13).toFloat(),
                (armY + 4).toFloat(),
                fill("#2F3A4A", if (reducedMotion) 0.50f else 0.18f),
            )
            val angle = frame.t * (114 * PI / 9) + index
            canvas.drawLine(
                (armX - cos(angle) * 12.0).toFloat(),
                (armY - sin(angle) * 3.5).toFloat(),
                (armX + cos(angle) * 12.0).toFloat(),
                (armY + sin(angle) * 3.5).toFloat(),
                stroke("#2F3A4A", 2f, 0.60f),
            )
        }
        canvas.restore()
    }

    private fun drawHeader(canvas: Canvas, width: Double, topInset: Double) {
        val y = topInset + 4.0
        roundRect(canvas, 16.0, y, 60.0, y + 44, 22.0, fill("#FFFFFF"))
        icon(canvas, iconCode(IconName.ChevronBack), 38.0, y + 22, 22.0, "#1F2A37")
        roundRect(canvas, width - 60.0, y, width - 16.0, y + 44, 22.0, fill("#FFFFFF"))
        icon(canvas, iconCode(IconName.Help), width - 38, y + 22, 20.0, "#1F2A37")
        val titlePaint = text(semiBold, 16f, "#1F2A37", Paint.Align.CENTER)
        val titleWidth = titlePaint.measureText("Order #4821") + 28f
        roundRect(canvas, width / 2.0 - titleWidth / 2.0, y + 4, width / 2.0 + titleWidth / 2.0, y + 40, 18.0, fill("#FFFFFF"))
        centeredText(canvas, "Order #4821", width / 2.0, y + 22.0, titlePaint)
    }

    private fun drawSheet(
        canvas: Canvas,
        width: Double,
        height: Double,
        bottomInset: Double,
        sheetMin: Double,
        sheetMax: Double,
        frame: DroneDropTrackerMotion.Frame,
    ) {
        val sheetTop = height - sheetMax + (1.0 - frame.sheet) * (sheetMax - sheetMin)
        roundRect(canvas, 0.0, sheetTop - 8.0, width, height + 16.0, 30.0, fill("#1F2A37", 0.12f))
        roundRect(canvas, 0.0, sheetTop, width, height + 16.0, 28.0, fill("#FFFFFF"))
        val grabberY = sheetTop + 10.0
        roundRect(canvas, width / 2.0 - 20, grabberY, width / 2.0 + 20, grabberY + 5, 3.0, fill("#E1E6EE"))
        val rowTop = sheetTop + 29.0
        roundRect(canvas, 20.0, rowTop, 64.0, rowTop + 44.0, 14.0, fill("#2F9E62"))
        val delivered = frame.drop > 0.98
        icon(
            canvas,
            iconCode(if (delivered) IconName.Checkmark else IconName.PaperPlane),
            42.0,
            rowTop + 22,
            20.0,
            "#FFFFFF",
        )
        val eta = if (delivered) {
            "Delivered"
        } else if (frame.f >= 1.0) {
            "Lowering your order"
        } else {
            "Arriving in ${max(1, kotlin.math.ceil((1.0 - frame.f) * 6.0).toInt())} min"
        }
        centeredText(canvas, eta, 76.0, rowTop + 14.0, text(bold, 20f, "#1F2A37"))
        centeredText(canvas, "Drone K-7 · 2 bags from Greenleaf Market", 76.0, rowTop + 36.0, text(regular, 13f, "#6B7686"))
        val trackTop = rowTop + 58.0
        val trackWidth = width - 40.0
        roundRect(canvas, 20.0, trackTop, width - 20.0, trackTop + 6.0, 3.0, fill("#EDF1F6"))
        val drainK = ((frame.back - 0.55) / 0.45).coerceIn(0.0, 1.0)
        val fillScale = if (frame.back > 0.0) 1.0 - 0.85 * drainK * drainK * (3 - 2 * drainK) else 0.15 + 0.75 * frame.f + 0.1 * frame.drop
        roundRect(canvas, 20.0, trackTop, 20.0 + trackWidth * fillScale.coerceIn(0.0, 1.0), trackTop + 6.0, 3.0, fill("#2F9E62"))

        val detailOpacity = DroneDropTrackerMotion.clamp01((frame.sheet - 0.35) / 0.5)
        if (detailOpacity <= 0.001) return
        val detailOffset = (1.0 - detailOpacity) * 14.0
        val detailStart = sheetTop + 111.0 + detailOffset
        val steps = listOf(
            Triple(IconName.BagCheckOutline, "Order packed", "6:42 PM"),
            Triple(IconName.PaperPlaneOutline, "In the air", "6:44 PM"),
            Triple(IconName.DownloadOutline, "Lowered to your doorstep", if (delivered) "6:51 PM" else "about 6:51 PM"),
        )
        steps.forEachIndexed { index, step ->
            val centerY = detailStart + 25.0 + index * 50.0
            val on = index < 2 || delivered
            roundRect(canvas, 20.0, centerY - 17.0, 54.0, centerY + 17.0, 17.0, fill(if (on) "#2F9E62" else "#EDF1F6", detailOpacity.toFloat()))
            icon(canvas, iconCode(step.first), 37.0, centerY, 18.0, if (on) "#FFFFFF" else "#8A94A6")
            centeredText(canvas, step.second, 66.0, centerY, text(semiBold, 15f, "#1F2A37", alpha = detailOpacity.toFloat()))
            rightText(canvas, step.third, width - 20.0, centerY, text(regular, 13f, "#8A94A6", alpha = detailOpacity.toFloat()))
        }
        val actionTop = detailStart + 164.0
        val actionWidth = (width - 52.0) / 2.0
        roundRect(canvas, 20.0, actionTop, 20.0 + actionWidth, actionTop + 50.0, 25.0, fill("#EDF1F6", detailOpacity.toFloat()))
        icon(canvas, iconCode(IconName.ChatbubbleOutline), 20 + actionWidth / 2.0 - 30, actionTop + 25, 18.0, "#1F2A37")
        centeredText(canvas, "Message", 20 + actionWidth / 2.0 + 10, actionTop + 25, text(semiBold, 15f, "#1F2A37", Paint.Align.CENTER, detailOpacity.toFloat()))
        val darkLeft = 32 + actionWidth
        roundRect(canvas, darkLeft, actionTop, darkLeft + actionWidth, actionTop + 50.0, 25.0, fill("#1F2A37", detailOpacity.toFloat()))
        icon(canvas, iconCode(IconName.LocationOutline), darkLeft + actionWidth / 2.0 - 30, actionTop + 25, 18.0, "#FFFFFF")
        centeredText(canvas, "Drop spot", darkLeft + actionWidth / 2.0 + 10, actionTop + 25, text(semiBold, 15f, "#FFFFFF", Paint.Align.CENTER, detailOpacity.toFloat()))
    }
}
