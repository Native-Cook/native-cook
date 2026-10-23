package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws the React Native scene in stage coordinates. The aluminium deck, cassette shell, labels, frames, and glass
 * are rasterized once; a frame only draws tape, wheels, meters, keys, hint, and the scripted touch indicator.
 */
internal class CassetteDeckRenderer(
    private val layout: CassetteDeckLayout,
    private val assets: CassetteDeckAssets,
    density: Float,
) {
    private val bitmapScale = min(density.toDouble(), 3.0) * layout.scale
    private val bitmapWidth = ceil(CassetteDeckTheme.STAGE_WIDTH * bitmapScale).toInt()
    private val bitmapHeight = ceil(CassetteDeckTheme.STAGE_HEIGHT * bitmapScale).toInt()
    private val backdrop = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
    private val foreground = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bitmapSource = Rect(0, 0, bitmapWidth, bitmapHeight)
    private val bitmapTarget = RectF(0f, 0f, CassetteDeckTheme.STAGE_WIDTH.f, CassetteDeckTheme.STAGE_HEIGHT.f)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val path = Path()
    private val rect = RectF()
    private var roundRadius = 0f

    init {
        Canvas(backdrop).run {
            scale(bitmapScale.f, bitmapScale.f)
            drawPlate(this)
            drawWell(this)
            drawCassette(this)
            drawReadout(this)
            drawKeySlots(this)
        }
        Canvas(foreground).run {
            scale(bitmapScale.f, bitmapScale.f)
            drawGlass(this)
        }
        backdrop.prepareToDraw()
        foreground.prepareToDraw()
    }

    fun draw(canvas: Canvas, model: CassetteDeckModel) {
        canvas.drawColor(CassetteDeckTheme.plate)
        val saved = canvas.save()
        canvas.translate(layout.originX.f, layout.originY.f)
        canvas.scale(layout.scale.f, layout.scale.f)
        canvas.drawBitmap(backdrop, bitmapSource, bitmapTarget, bitmapPaint)
        drawTape(canvas, model)
        canvas.drawBitmap(foreground, bitmapSource, bitmapTarget, bitmapPaint)
        drawCounter(canvas, model)
        drawMeters(canvas, model)
        drawKeys(canvas, model)
        drawHint(canvas, model)
        if (model.ghostOpacity > 0.01) {
            val radius = CassetteDeckTheme.FINGER_SIZE / 2.0
            canvas.drawCircle(model.ghostX.f, model.ghostY.f, radius.f, solid(CassetteDeckTheme.finger, model.ghostOpacity))
            canvas.drawCircle(
                model.ghostX.f,
                model.ghostY.f,
                (radius - 0.5).f,
                stroked(CassetteDeckTheme.fingerEdge, 1.0, model.ghostOpacity),
            )
        }
        canvas.restoreToCount(saved)
    }

    fun recycle() {
        backdrop.recycle()
        foreground.recycle()
    }

    private fun drawPlate(canvas: Canvas) {
        brushed(canvas, -200.0, -200.0, CassetteDeckTheme.STAGE_WIDTH + 400.0, CassetteDeckTheme.STAGE_HEIGHT + 600.0, 1.0)
        engraved(canvas, CassetteDeckTheme.brand, CassetteDeckTheme.EDGE, CassetteDeckTheme.BRAND_BASELINE, assets.semibold, 22.0, 5.0)
        engraved(
            canvas,
            CassetteDeckTheme.model,
            CassetteDeckTheme.EDGE,
            CassetteDeckTheme.MODEL_BASELINE,
            assets.medium,
            10.0,
            1.6,
            color = CassetteDeckTheme.engraveMuted,
        )
        val power = CassetteDeckTheme.power
        canvas.drawCircle(power.x.f, power.y.f, 9f, radial(power.x, power.y, 9.0, intArrayOf(0x59FF8A2A, 0x00FF8A2A)))
        canvas.drawCircle(power.x.f, power.y.f, 4f, radial(power.x - 1.0, power.y - 1.0, 5.0, intArrayOf(0xFFFFE2B8.toInt(), CassetteDeckTheme.led, 0xFFB4520E.toInt()), floatArrayOf(0f, 0.5f, 1f)))
        canvas.drawCircle(power.x.f, power.y.f, 5f, stroked(0xFF5A5F66.toInt(), 1.0, 0.6))
        engraved(canvas, "POWER", power.x, power.y + 22.0, assets.medium, 9.0, 1.4, TextAlign.Center, CassetteDeckTheme.engraveMuted)
    }

    private fun drawWell(canvas: Canvas) {
        val well = CassetteDeckTheme.well
        canvas.drawRoundRect(
            roundRect(well.x - 2.0, well.y - 2.0, well.width + 4.0, well.height + 4.0, well.radius + 2.0),
            linear(0.0, well.y, 0.0, well.y + well.height, intArrayOf(0xFF7D8288.toInt(), 0xFFF4F5F6.toInt())),
        )
        canvas.drawRoundRect(roundRect(well), solid(CassetteDeckTheme.wellColor))
        val saved = canvas.save()
        clipRoundRect(canvas, well)
        canvas.drawRect(well.x.f, well.y.f, (well.x + well.width).f, (well.y + 30.0).f, blurred(0xFF000000.toInt(), 0.7, 10.0))
        canvas.restoreToCount(saved)
    }

    private fun drawCassette(canvas: Canvas) {
        val cassette = CassetteDeckTheme.cassette
        canvas.drawRoundRect(
            roundRect(cassette.x + 2.0, cassette.y + 6.0, cassette.width, cassette.height, cassette.radius),
            blurred(0xFF000000.toInt(), 0.6, 6.0),
        )
        canvas.drawRoundRect(
            roundRect(cassette),
            linear(0.0, cassette.y, 0.0, cassette.y + cassette.height, intArrayOf(CassetteDeckTheme.shellLight, CassetteDeckTheme.shell, 0xFF1E1F22.toInt()), floatArrayOf(0f, 0.4f, 1f)),
        )
        canvas.drawRoundRect(
            roundRect(cassette.x + 0.5, cassette.y + 0.5, cassette.width - 1.0, cassette.height - 1.0, cassette.radius),
            stroked(Color.WHITE, 1.0, 0.12),
        )

        val bottomY = cassette.y + cassette.height - 42.0
        path.reset()
        path.moveTo((cassette.x + 62.0).f, bottomY.f)
        path.lineTo((cassette.x + cassette.width - 62.0).f, bottomY.f)
        path.lineTo((cassette.x + cassette.width - 40.0).f, (cassette.y + cassette.height).f)
        path.lineTo((cassette.x + 40.0).f, (cassette.y + cassette.height).f)
        path.close()
        canvas.drawPath(path, linear(0.0, bottomY, 0.0, cassette.y + cassette.height, intArrayOf(0xFF36373B.toInt(), 0xFF232427.toInt())))
        canvas.drawLine((cassette.x + 62.0).f, (bottomY + 0.5).f, (cassette.x + cassette.width - 62.0).f, (bottomY + 0.5).f, stroked(Color.WHITE, 1.0, 0.14))
        val holes = arrayOf(
            cassette.x + 86.0 to 22.0,
            cassette.x + 128.0 to 18.0,
            cassette.x + cassette.width / 2.0 - 15.0 to 30.0,
            cassette.x + cassette.width - 146.0 to 18.0,
            cassette.x + cassette.width - 108.0 to 22.0,
        )
        for ((holeX, holeWidth) in holes) {
            canvas.drawRoundRect(roundRect(holeX, bottomY + 14.0, holeWidth, 20.0, 3.0), solid(0xFF0B0B0C.toInt()))
            canvas.drawRect(holeX.f, (bottomY + 26.0).f, (holeX + holeWidth).f, (bottomY + 29.0).f, solid(CassetteDeckTheme.tape))
            canvas.drawRect(holeX.f, (bottomY + 26.0).f, (holeX + holeWidth).f, (bottomY + 26.8).f, solid(CassetteDeckTheme.tapeLight, 0.8))
        }
        val screws = arrayOf(
            cassette.x + 12.0 to cassette.y + 12.0,
            cassette.x + cassette.width - 12.0 to cassette.y + 12.0,
            cassette.x + 12.0 to cassette.y + cassette.height - 12.0,
            cassette.x + cassette.width - 12.0 to cassette.y + cassette.height - 12.0,
            cassette.x + cassette.width / 2.0 to cassette.y + cassette.height - 10.0,
        )
        for ((x, y) in screws) screw(canvas, x, y, 3.4, hash(x + y) * 2.0)

        val label = CassetteDeckTheme.labelRect
        canvas.drawRoundRect(
            roundRect(label),
            linear(label.x, label.y, label.x + label.width, label.y + label.height, intArrayOf(0xFFF6EEDC.toInt(), CassetteDeckTheme.label, 0xFFE9DEC5.toInt()), floatArrayOf(0f, 0.5f, 1f)),
        )
        for (index in 0 until 3) {
            val y = label.y + 18.0 + index * 12.0
            canvas.drawRect((label.x + 44.0).f, y.f, (label.x + label.width - 14.0).f, (y + 0.6).f, solid(CassetteDeckTheme.labelLine))
        }
        canvas.drawRect(label.x.f, (label.y + label.height - 22.0).f, (label.x + label.width).f, (label.y + label.height).f, solid(CassetteDeckTheme.labelBand))
        canvas.drawRect(label.x.f, (label.y + label.height - 22.0).f, (label.x + label.width).f, (label.y + label.height - 21.0).f, solid(Color.WHITE, 0.35))
        canvas.drawCircle((label.x + 22.0).f, (label.y + 30.0).f, 13f, stroked(CassetteDeckTheme.pen, 1.4, 0.85))
        drawText(canvas, CassetteDeckTheme.side, label.x + 22.0, label.y + 39.0, assets.handwriting, 27.0, CassetteDeckTheme.pen, 0.9, TextAlign.Center)
        val saved = canvas.save()
        canvas.rotate(-1.6f, (label.x + 50.0).f, (label.y + 40.0).f)
        drawText(canvas, CassetteDeckTheme.title, label.x + 46.0, label.y + 34.0, assets.handwriting, 27.0, CassetteDeckTheme.pen, 0.92)
        drawText(canvas, CassetteDeckTheme.subtitle, label.x + 52.0, label.y + 54.0, assets.handwriting, 18.0, CassetteDeckTheme.pen, 0.8)
        canvas.restoreToCount(saved)

        val window = CassetteDeckTheme.window
        canvas.drawRoundRect(roundRect(window.x - 3.0, window.y - 3.0, window.width + 6.0, window.height + 6.0, window.radius + 3.0), solid(0xFF1A1B1D.toInt()))
        canvas.drawRoundRect(roundRect(window), solid(0xFF0D0D0E.toInt()))
    }

    private fun drawReadout(canvas: Canvas) {
        val y = CassetteDeckTheme.READOUT_Y
        val height = CassetteDeckTheme.READOUT_HEIGHT
        val counterX = CassetteDeckTheme.COUNTER_X
        engraved(canvas, CassetteDeckTheme.counterLabel, counterX, y - 10.0, assets.medium, 9.0, 1.4, color = CassetteDeckTheme.engraveMuted)
        canvas.drawRoundRect(roundRect(counterX - 1.5, y - 1.5, 103.0, height + 3.0, 9.0), linear(0.0, y, 0.0, y + height, intArrayOf(0xFF80858B.toInt(), 0xFFF2F3F4.toInt())))
        canvas.drawRoundRect(roundRect(counterX, y, 100.0, height, 8.0), solid(CassetteDeckTheme.counter))
        engraved(canvas, CassetteDeckTheme.memo, counterX + 116.0, y + 52.0, assets.medium, 9.0, 1.4, color = CassetteDeckTheme.engraveMuted)

        val marks = arrayOf("-20", "-10", "-5", "0", "+3")
        val segments = intArrayOf(0, 4, 7, 9, 11)
        for (index in marks.indices) {
            val x = CassetteDeckTheme.METERS_X + 22.0 + segments[index] * ((CassetteDeckTheme.METERS_WIDTH - 22.0) / CassetteDeckModel.METER_SEGMENTS) + 6.0
            drawText(canvas, marks[index], x, y - 10.0, assets.medium, 9.0, CassetteDeckTheme.engraveMuted, align = TextAlign.Center)
        }
        val metersX = CassetteDeckTheme.METERS_X
        val metersWidth = CassetteDeckTheme.METERS_WIDTH
        canvas.drawRoundRect(roundRect(metersX - 1.5, y - 1.5, metersWidth + 3.0, height + 3.0, 9.0), linear(0.0, y, 0.0, y + height, intArrayOf(0xFF80858B.toInt(), 0xFFF2F3F4.toInt())))
        canvas.drawRoundRect(roundRect(metersX, y, metersWidth, height, 8.0), solid(CassetteDeckTheme.counter))
        drawText(canvas, "L", metersX + 10.0, y + 26.0, assets.medium, 9.0, 0xFF9AA0A6.toInt())
        drawText(canvas, "R", metersX + 10.0, y + 50.0, assets.medium, 9.0, 0xFF9AA0A6.toInt())
    }

    private fun drawKeySlots(canvas: Canvas) {
        val y = CassetteDeckTheme.KEYS_Y
        val height = CassetteDeckTheme.KEYS_HEIGHT
        canvas.drawRoundRect(roundRect(CassetteDeckTheme.EDGE - 4.0, y - 4.0, CassetteDeckTheme.STAGE_WIDTH - 2.0 * CassetteDeckTheme.EDGE + 8.0, height + 8.0, 12.0), solid(0xFF16171A.toInt()))
        canvas.drawRoundRect(roundRect(CassetteDeckTheme.EDGE - 6.0, y - 6.0, CassetteDeckTheme.STAGE_WIDTH - 2.0 * CassetteDeckTheme.EDGE + 12.0, height + 12.0, 14.0), stroked(Color.WHITE, 1.0, 0.5))
        val names = arrayOf("REW", "PLAY", "FF", "STOP", "PAUSE")
        for (index in names.indices) {
            engraved(canvas, names[index], CassetteDeckTheme.keyX(index) + CassetteDeckTheme.KEY_WIDTH / 2.0, y - 14.0, assets.medium, 9.0, 1.4, TextAlign.Center, CassetteDeckTheme.engraveMuted)
        }
    }

    private fun drawGlass(canvas: Canvas) {
        val window = CassetteDeckTheme.window
        canvas.drawRoundRect(roundRect(window.x + 0.5, window.y + 0.5, window.width - 1.0, window.height - 1.0, window.radius), stroked(Color.WHITE, 1.0, 0.28))
        for (index in 0..10) {
            val x = window.x + 40.0 + index * ((window.width - 80.0) / 10.0)
            canvas.drawLine(x.f, (window.y + 3.0).f, x.f, (window.y + if (index % 5 == 0) 9.0 else 6.0).f, stroked(Color.WHITE, 0.7, 0.35))
        }
        val door = CassetteDeckTheme.well
        val saved = canvas.save()
        clipRoundRect(canvas, door)
        canvas.drawRect(door.x.f, door.y.f, (door.x + door.width).f, (door.y + door.height).f, solid(0xFF2A2018.toInt(), 0.16))
        drawReflection(canvas, door.x + 150.0, 70.0, 0.07)
        drawReflection(canvas, door.x + 250.0, 16.0, 0.08)
        canvas.drawRect((door.x + 10.0).f, (door.y + 1.0).f, (door.x + door.width - 10.0).f, (door.y + 2.0).f, solid(Color.WHITE, 0.35))
        canvas.restoreToCount(saved)
        canvas.drawRoundRect(roundRect(door.x + 0.5, door.y + 0.5, door.width - 1.0, door.height - 1.0, door.radius), stroked(Color.WHITE, 1.0, 0.16))
    }

    private fun drawReflection(canvas: Canvas, startX: Double, width: Double, alpha: Double) {
        val door = CassetteDeckTheme.well
        path.reset()
        path.moveTo(startX.f, (door.y - 10.0).f)
        path.lineTo((startX + width).f, (door.y - 10.0).f)
        path.lineTo((startX + width - 150.0).f, (door.y + door.height + 10.0).f)
        path.lineTo((startX - 150.0).f, (door.y + door.height + 10.0).f)
        path.close()
        canvas.drawPath(path, blurred(Color.WHITE, alpha, 6.0))
    }

    private fun drawTape(canvas: Canvas, model: CassetteDeckModel) {
        val window = CassetteDeckTheme.window
        val saved = canvas.save()
        clipRoundRect(canvas, window)
        val angles = CassetteDeckModel.reelAngles(model.tapePosition)
        val left = CassetteDeckTheme.reels[0]
        val right = CassetteDeckTheme.reels[1]
        drawReel(canvas, left.x, left.y, CassetteDeckModel.packLeft(model.tapePosition), angles.first)
        drawReel(canvas, right.x, right.y, CassetteDeckModel.packRight(model.tapePosition), angles.second)
        canvas.restoreToCount(saved)
    }

    private fun drawReel(canvas: Canvas, x: Double, y: Double, pack: Double, angle: Double) {
        canvas.drawCircle(x.f, y.f, pack.f, radial(x, y, pack, intArrayOf(CassetteDeckTheme.tape, CassetteDeckTheme.tape, 0xFF2A170E.toInt()), floatArrayOf(0f, 0.85f, 1f)))
        var ring = CassetteDeckTheme.HUB_RADIUS + 4.0
        while (ring < pack - 1.0) {
            canvas.drawCircle(x.f, y.f, ring.f, stroked(CassetteDeckTheme.tapeLight, 0.5, 0.18))
            ring += 3.2
        }
        canvas.drawCircle(x.f, y.f, (pack - 0.6).f, stroked(CassetteDeckTheme.tapeLight, 1.0, 0.5))
        val hub = CassetteDeckTheme.HUB_RADIUS
        canvas.drawCircle(x.f, y.f, hub.f, radial(x - 3.0, y - 4.0, hub * 1.6, intArrayOf(Color.WHITE, CassetteDeckTheme.hub, 0xFFBDB8AE.toInt()), floatArrayOf(0f, 0.5f, 1f)))
        canvas.drawCircle(x.f, y.f, (hub * 0.55).f, solid(0xFF0D0D0E.toInt()))
        repeat(6) { index ->
            val toothAngle = angle + index * PI / 3.0
            path.reset()
            path.moveTo((x + cos(toothAngle - 0.16) * hub * 0.55).f, (y + sin(toothAngle - 0.16) * hub * 0.55).f)
            path.lineTo((x + cos(toothAngle) * hub * 0.3).f, (y + sin(toothAngle) * hub * 0.3).f)
            path.lineTo((x + cos(toothAngle + 0.16) * hub * 0.55).f, (y + sin(toothAngle + 0.16) * hub * 0.55).f)
            path.close()
            canvas.drawPath(path, solid(CassetteDeckTheme.hub))
        }
    }

    private fun drawCounter(canvas: Canvas, model: CassetteDeckModel) {
        val y = CassetteDeckTheme.READOUT_Y
        val height = CassetteDeckTheme.READOUT_HEIGHT
        val x0 = CassetteDeckTheme.COUNTER_X + 8.0
        val wheelWidth = 28.0
        var value = CassetteDeckModel.counterAt(model.tapePosition)
        value = ((value % 1000.0) + 1000.0) % 1000.0
        val saved = canvas.save()
        clipRoundRect(canvas, CassetteDeckRect(CassetteDeckTheme.COUNTER_X, y, 100.0, height, 8.0))
        repeat(3) { index ->
            val place = 10.0.powInt(2 - index)
            val raw = value / place
            val digit = floor(raw).toInt() % 10
            val below = value % place / place
            val roll = if (index == 2) raw - floor(raw) else if (below > 0.9) (below - 0.9) * 10.0 else 0.0
            val centerX = x0 + index * wheelWidth + wheelWidth / 2.0 - 2.0
            val centerY = y + height / 2.0 + 9.0
            val digitStep = 34.0
            canvas.drawRoundRect(roundRect(centerX - 12.0, y + 8.0, 24.0, height - 16.0, 3.0), solid(0xFF1B1C1E.toInt()))
            val wheelSaved = canvas.save()
            canvas.clipRect((centerX - 12.0).f, (y + 8.0).f, (centerX + 12.0).f, (y + height - 8.0).f)
            drawText(canvas, digit.toString(), centerX, centerY - roll * digitStep, assets.semibold, 26.0, CassetteDeckTheme.digit, align = TextAlign.Center)
            drawText(canvas, ((digit + 1) % 10).toString(), centerX, centerY + (1.0 - roll) * digitStep, assets.semibold, 26.0, CassetteDeckTheme.digit, align = TextAlign.Center)
            canvas.restoreToCount(wheelSaved)
        }
        canvas.drawRect(
            CassetteDeckTheme.COUNTER_X.f,
            y.f,
            (CassetteDeckTheme.COUNTER_X + 100.0).f,
            (y + height).f,
            linear(0.0, y, 0.0, y + height, intArrayOf(0xD9000000.toInt(), 0x00000000, 0x00000000, 0xD9000000.toInt()), floatArrayOf(0f, 0.3f, 0.7f, 1f)),
        )
        canvas.restoreToCount(saved)
        val lampX = CassetteDeckTheme.COUNTER_X + 122.0
        val lampY = y + 30.0
        val colors = if (model.memory) intArrayOf(0xFFFFE2B8.toInt(), CassetteDeckTheme.led, 0xFFB4520E.toInt()) else intArrayOf(0xFF5A4334.toInt(), CassetteDeckTheme.ledOff, 0xFF21170F.toInt())
        canvas.drawCircle(lampX.f, lampY.f, 4f, radial(lampX - 1.0, lampY - 1.0, 5.0, colors, floatArrayOf(0f, 0.5f, 1f)))
        if (model.memory) canvas.drawCircle(lampX.f, lampY.f, 10f, radial(lampX, lampY, 10.0, intArrayOf(0x66FF8A2A, 0x00FF8A2A)))
    }

    private fun drawMeters(canvas: Canvas, model: CassetteDeckModel) {
        val x0 = CassetteDeckTheme.METERS_X + 22.0
        val segmentWidth = (CassetteDeckTheme.METERS_WIDTH - 22.0 - 10.0) / CassetteDeckModel.METER_SEGMENTS
        repeat(2) { row ->
            val level = if (row == 0) model.leftLevel else model.rightLevel
            val y = CassetteDeckTheme.READOUT_Y + if (row == 0) 16.0 else 40.0
            repeat(CassetteDeckModel.METER_SEGMENTS) { index ->
                val on = level * CassetteDeckModel.METER_SEGMENTS > index + 0.3
                val color = when {
                    index >= CassetteDeckModel.METER_SEGMENTS - 1 -> CassetteDeckTheme.meterPeak
                    index >= CassetteDeckModel.METER_SEGMENTS - 3 -> CassetteDeckTheme.meterHot
                    else -> CassetteDeckTheme.meterOn
                }
                val x = x0 + index * segmentWidth
                if (on) {
                    val glow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        blurred(color, 0.35, 3.0)
                    } else {
                        solid(color, 0.22)
                    }
                    canvas.drawRoundRect(roundRect(x - 2.0, y - 2.0, segmentWidth + 1.0, 16.0, 4.0), glow)
                }
                canvas.drawRoundRect(roundRect(x, y, segmentWidth - 3.0, 12.0, 2.0), solid(if (on) color else CassetteDeckTheme.meterOff))
            }
        }
    }

    private fun drawKeys(canvas: Canvas, model: CassetteDeckModel) {
        repeat(CassetteDeckModel.KEY_COUNT) { index ->
            val depth = model.keyDepth[index]
            val x = CassetteDeckTheme.keyX(index)
            val sink = 12.0 * depth
            val frontHeight = CassetteDeckTheme.KEY_TOP * (1.0 - 0.75 * depth)
            val top = CassetteDeckTheme.KEYS_Y + sink
            val faceHeight = CassetteDeckTheme.KEYS_HEIGHT - CassetteDeckTheme.KEY_TOP - 4.0
            canvas.drawRoundRect(
                roundRect(x + 2.0, top + 6.0, CassetteDeckTheme.KEY_WIDTH - 4.0, faceHeight + frontHeight, 8.0),
                blurred(0xFF000000.toInt(), 0.55 - 0.3 * depth, 5.0),
            )
            canvas.drawRoundRect(
                roundRect(x, top + faceHeight - 8.0, CassetteDeckTheme.KEY_WIDTH, frontHeight + 8.0, 8.0),
                linear(0.0, top + faceHeight, 0.0, top + faceHeight + frontHeight, intArrayOf(CassetteDeckTheme.keyFront, CassetteDeckTheme.keyDeep)),
            )
            canvas.drawRoundRect(
                roundRect(x, top, CassetteDeckTheme.KEY_WIDTH, faceHeight, 8.0),
                linear(0.0, top, 0.0, top + faceHeight, intArrayOf(CassetteDeckTheme.keyTop, 0xFFCDD1D5.toInt())),
            )
            canvas.drawRoundRect(roundRect(x, top, CassetteDeckTheme.KEY_WIDTH, faceHeight, 8.0), solid(Color.BLACK, 0.12 * depth))
            repeat(14) { line ->
                val y = top + 6.0 + line * ((faceHeight - 12.0) / 14.0)
                canvas.drawRect((x + 6.0).f, y.f, (x + CassetteDeckTheme.KEY_WIDTH - 6.0).f, (y + 0.4).f, solid(Color.WHITE, 0.25))
            }
            canvas.drawRoundRect(roundRect(x + 0.5, top + 0.5, CassetteDeckTheme.KEY_WIDTH - 1.0, faceHeight - 1.0, 8.0), stroked(Color.WHITE, 1.0, 0.8))
            drawKeySymbol(canvas, index, x + CassetteDeckTheme.KEY_WIDTH / 2.0, top + faceHeight / 2.0)
        }
    }

    private fun drawKeySymbol(canvas: Canvas, index: Int, centerX: Double, centerY: Double) {
        val color = if (index == CassetteDeckModel.KEY_PLAY) CassetteDeckTheme.playMark else CassetteDeckTheme.engrave
        when (index) {
            CassetteDeckModel.KEY_PLAY -> triangle(canvas, centerX - 5.0, centerY, 1.0, color)
            CassetteDeckModel.KEY_FAST_FORWARD -> {
                triangle(canvas, centerX - 10.0, centerY, 1.0, color)
                triangle(canvas, centerX, centerY, 1.0, color)
            }
            CassetteDeckModel.KEY_REWIND -> {
                triangle(canvas, centerX + 10.0, centerY, -1.0, color)
                triangle(canvas, centerX, centerY, -1.0, color)
            }
            CassetteDeckModel.KEY_STOP -> canvas.drawRect((centerX - 6.5).f, (centerY - 6.5).f, (centerX + 6.5).f, (centerY + 6.5).f, solid(color))
            else -> {
                canvas.drawRect((centerX - 7.0).f, (centerY - 7.0).f, (centerX - 2.5).f, (centerY + 7.0).f, solid(color))
                canvas.drawRect((centerX + 2.5).f, (centerY - 7.0).f, (centerX + 7.0).f, (centerY + 7.0).f, solid(color))
            }
        }
    }

    private fun triangle(canvas: Canvas, x: Double, y: Double, direction: Double, color: Int) {
        path.reset()
        path.moveTo(x.f, (y - 7.0).f)
        path.lineTo((x + 10.0 * direction).f, y.f)
        path.lineTo(x.f, (y + 7.0).f)
        path.close()
        canvas.drawPath(path, solid(color))
    }

    private fun drawHint(canvas: Canvas, model: CassetteDeckModel) {
        val hint = when (model.mode) {
            CassetteDeckMode.Play -> CassetteDeckTheme.hints[1]
            CassetteDeckMode.FastForward -> CassetteDeckTheme.hints[2]
            CassetteDeckMode.Rewind -> if (model.memory) CassetteDeckTheme.hints[3] else "Rewinding"
            CassetteDeckMode.Pause -> CassetteDeckTheme.hints[4]
            CassetteDeckMode.Stop -> if (model.tapePosition > CassetteDeckModel.TAPE_START + 0.001) "Stopped" else CassetteDeckTheme.hints[0]
        }
        drawText(canvas, hint, CassetteDeckTheme.STAGE_WIDTH / 2.0, CassetteDeckTheme.HINT_BASELINE, assets.medium, 14.0, CassetteDeckTheme.engrave, 0.9, TextAlign.Center)
    }

    private fun brushed(canvas: Canvas, x: Double, y: Double, width: Double, height: Double, seed: Double) {
        canvas.drawRect(x.f, y.f, (x + width).f, (y + height).f, linear(0.0, y, 0.0, y + height, intArrayOf(CassetteDeckTheme.plateLight, CassetteDeckTheme.plate, CassetteDeckTheme.plateDeep), floatArrayOf(0f, 0.45f, 1f)))
        repeat((height * 3.2).toInt()) { index ->
            val yy = y + hash(index + seed) * height
            val x0 = x + hash(index * 3.0 + seed) * width * 0.6 - 20.0
            val length = 60.0 + hash(index * 7.0 + seed) * width
            val light = hash(index * 11.0 + seed) > 0.5
            val alpha = 0.05 + hash(index * 13.0 + seed) * if (light) 0.12 else 0.06
            canvas.drawRect(x0.f, yy.f, (x0 + length).f, (yy + 0.35).f, solid(if (light) Color.WHITE else 0xFF5A5F66.toInt(), alpha))
        }
    }

    private fun screw(canvas: Canvas, x: Double, y: Double, radius: Double, angle: Double) {
        canvas.drawCircle(x.f, y.f, radius.f, radial(x - radius * 0.3, y - radius * 0.3, radius * 1.6, intArrayOf(0xFF8C9096.toInt(), 0xFF54585E.toInt(), 0xFF2E3135.toInt()), floatArrayOf(0f, 0.6f, 1f)))
        val screwStroke = stroked(0xFF1B1D20.toInt(), 1.1)
        canvas.drawLine((x - cos(angle) * radius * 0.7).f, (y - sin(angle) * radius * 0.7).f, (x + cos(angle) * radius * 0.7).f, (y + sin(angle) * radius * 0.7).f, screwStroke)
        canvas.drawLine((x - sin(angle) * radius * 0.7).f, (y + cos(angle) * radius * 0.7).f, (x + sin(angle) * radius * 0.7).f, (y - cos(angle) * radius * 0.7).f, screwStroke)
    }

    private fun drawText(
        canvas: Canvas,
        value: String,
        x: Double,
        baseline: Double,
        typeface: android.graphics.Typeface,
        size: Double,
        color: Int,
        alpha: Double = 1.0,
        align: TextAlign = TextAlign.Left,
    ) {
        if (value.isEmpty() || alpha <= 0.004) return
        textPaint.shader = null
        textPaint.maskFilter = null
        textPaint.style = Paint.Style.FILL
        textPaint.typeface = typeface
        textPaint.textSize = size.f
        textPaint.color = color
        textPaint.alpha = (Color.alpha(color) * alpha).roundToInt().coerceIn(0, 255)
        val width = textPaint.measureText(value).toDouble()
        val left = when (align) {
            TextAlign.Left -> x
            TextAlign.Center -> x - width / 2.0
            TextAlign.Right -> x - width
        }
        canvas.drawText(value, left.f, baseline.f, textPaint)
    }

    private fun engraved(
        canvas: Canvas,
        value: String,
        x: Double,
        baseline: Double,
        typeface: android.graphics.Typeface,
        size: Double,
        tracking: Double,
        align: TextAlign = TextAlign.Left,
        color: Int = CassetteDeckTheme.engrave,
    ) {
        textPaint.typeface = typeface
        textPaint.textSize = size.f
        var width = 0.0
        value.forEachIndexed { index, character ->
            width += textPaint.measureText(character.toString())
            if (index < value.lastIndex) width += tracking
        }
        var cursor = when (align) {
            TextAlign.Left -> x
            TextAlign.Center -> x - width / 2.0
            TextAlign.Right -> x - width
        }
        value.forEachIndexed { index, character ->
            val glyph = character.toString()
            drawText(canvas, glyph, cursor, baseline + 0.7, typeface, size, Color.WHITE, 0.7)
            drawText(canvas, glyph, cursor, baseline, typeface, size, color)
            cursor += textPaint.measureText(glyph) + if (index < value.lastIndex) tracking else 0.0
        }
    }

    private fun solid(color: Int, alpha: Double = 1.0): Paint {
        fill.style = Paint.Style.FILL
        fill.shader = null
        fill.maskFilter = null
        fill.color = color
        fill.alpha = (Color.alpha(color) * max(0.0, alpha)).roundToInt().coerceIn(0, 255)
        return fill
    }

    private fun stroked(color: Int, width: Double, alpha: Double = 1.0): Paint {
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = width.f
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.shader = null
        stroke.maskFilter = null
        stroke.color = color
        stroke.alpha = (Color.alpha(color) * max(0.0, alpha)).roundToInt().coerceIn(0, 255)
        return stroke
    }

    private fun blurred(color: Int, alpha: Double, sigma: Double): Paint {
        solid(color, alpha)
        fill.maskFilter = BlurMaskFilter(sigma.f, BlurMaskFilter.Blur.NORMAL)
        return fill
    }

    private fun linear(
        x0: Double,
        y0: Double,
        x1: Double,
        y1: Double,
        colors: IntArray,
        positions: FloatArray? = null,
    ): Paint {
        fill.style = Paint.Style.FILL
        fill.maskFilter = null
        fill.alpha = 255
        fill.shader = LinearGradient(x0.f, y0.f, x1.f, y1.f, colors, positions, Shader.TileMode.CLAMP)
        return fill
    }

    private fun radial(
        x: Double,
        y: Double,
        radius: Double,
        colors: IntArray,
        positions: FloatArray? = null,
    ): Paint {
        fill.style = Paint.Style.FILL
        fill.maskFilter = null
        fill.alpha = 255
        fill.shader = RadialGradient(x.f, y.f, radius.f, colors, positions, Shader.TileMode.CLAMP)
        return fill
    }

    private fun roundRect(rectangle: CassetteDeckRect): RectF {
        return roundRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height, rectangle.radius)
    }

    private fun roundRect(x: Double, y: Double, width: Double, height: Double, radius: Double): RectF {
        roundRadius = radius.f
        rect.set(x.f, y.f, (x + width).f, (y + height).f)
        return rect
    }

    private fun Canvas.drawRoundRect(bounds: RectF, paint: Paint) {
        drawRoundRect(bounds, roundRadius, roundRadius, paint)
    }

    private fun clipRoundRect(canvas: Canvas, rectangle: CassetteDeckRect) {
        path.reset()
        path.addRoundRect(roundRect(rectangle), rectangle.radius.f, rectangle.radius.f, Path.Direction.CW)
        canvas.clipPath(path)
    }

    private fun hash(value: Double): Double {
        val hashed = sin(value * 127.1 + 311.7) * 43758.5453
        return hashed - floor(hashed)
    }

    private enum class TextAlign { Left, Center, Right }
}

private fun Double.powInt(power: Int): Double {
    var result = 1.0
    repeat(power) { result *= this }
    return result
}

private val Double.f: Float get() = toFloat()
