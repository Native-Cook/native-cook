package com.ngocdevv.nativecook.blocks.gestures.holddragresize

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

internal class HoldDragResizeRenderer(
    private val assets: HoldDragResizeAssets,
    private val backdrop: HoldDragResizeBackdrop,
) {
    private enum class Face { Regular, Medium, Semibold, Mono, MonoBold }

    private data class TextStyle(val face: Face, val size: Float)

    private data class BlockBox(
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
        val scale: Double,
        val centreX: Double,
        val centreY: Double,
    )

    private val styles = mapOf(
        "title" to TextStyle(Face.Semibold, HoldDragResizeTokens.Type.Title),
        "date" to TextStyle(Face.Regular, HoldDragResizeTokens.Type.Date),
        "chip" to TextStyle(Face.Medium, HoldDragResizeTokens.Type.ChipLabel),
        "total" to TextStyle(Face.Semibold, HoldDragResizeTokens.Type.Total),
        "totalUnit" to TextStyle(Face.Medium, HoldDragResizeTokens.Type.TotalUnit),
        "weekday" to TextStyle(Face.Medium, HoldDragResizeTokens.Type.Weekday),
        "weekdate" to TextStyle(Face.Semibold, HoldDragResizeTokens.Type.WeekDate),
        "hour" to TextStyle(Face.Mono, HoldDragResizeTokens.Type.Hour),
        "pill" to TextStyle(Face.MonoBold, HoldDragResizeTokens.Type.Pill),
        "delta" to TextStyle(Face.Semibold, HoldDragResizeTokens.Type.Delta),
        "event" to TextStyle(Face.Medium, HoldDragResizeTokens.Type.Event),
        "eventSub" to TextStyle(Face.Mono, HoldDragResizeTokens.Type.EventSub),
        "eventNote" to TextStyle(Face.Regular, HoldDragResizeTokens.Type.EventNote),
        "eventNow" to TextStyle(Face.MonoBold, HoldDragResizeTokens.Type.EventSub),
        "bar" to TextStyle(Face.Medium, HoldDragResizeTokens.Type.Bar),
        "barMute" to TextStyle(Face.Regular, HoldDragResizeTokens.Type.Bar),
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val glass = HoldDragResizeGlass(backdrop)
    private val shadow = HoldDragResizeShadow()
    private val digitWidth: Float = (0..9).maxOf { measure("total", it.toString()) }

    fun draw(canvas: Canvas, model: HoldDragResizeModel) {
        glass.beginFrame()
        shadow.beginFrame()
        canvas.drawBitmap(
            backdrop.sharp,
            null,
            RectF(0f, 0f, model.layout.width.toFloat(), model.layout.height.toFloat()),
            bitmapPaint(),
        )
        drawHeader(canvas, model)
        drawWeek(canvas, model.layout)
        val edgeY = edgeY(model)
        drawLabels(canvas, model, edgeY)

        val live = model.settledLayout()
        val held = if (model.shown >= 0 && model.lift > 0.001) model.shown else -1
        drawShadows(canvas, model, only = -1, skip = held)
        drawCapsules(canvas, model)
        HoldDragResizePlan.indices.forEach { index ->
            if (index != held) drawBlock(canvas, model, index, lifted = false, live)
        }
        if (held >= 0) {
            drawShadows(canvas, model, only = held, skip = -1)
            drawBlock(canvas, model, held, lifted = true, live)
            val time = if (model.edge == 1) live.first[held] + live.second[held] else live.first[held]
            val box = boxOf(model, held, lifted = true)
            val edge = if (model.edge == 1) box.centreY + box.height * box.scale / 2 else box.centreY - box.height * box.scale / 2
            timePill(
                canvas,
                edge.coerceIn(model.layout.gridTop, model.layout.yOf(HoldDragResizeTokens.Day.End.toDouble())),
                holdDragResizeClock(time),
                HoldDragResizeTokens.Color.Focus,
                HoldDragResizeTokens.Color.OnAccent,
                pillShow(model),
            )
        }
        drawBar(canvas, model)
        if (model.ghostOpacity > 0.01) {
            val radius = HoldDragResizeTokens.Layout.Finger / 2 * (1 - 0.12 * model.ghostPress)
            canvas.drawCircle(model.ghostX.toFloat(), model.ghostY.toFloat(), radius.toFloat(), fill(HoldDragResizeTokens.Color.Finger, model.ghostOpacity))
            canvas.drawCircle(model.ghostX.toFloat(), model.ghostY.toFloat(), (radius - 0.5).toFloat(), stroke(HoldDragResizeTokens.Color.FingerEdge, 1f, model.ghostOpacity))
        }
    }

    private fun drawHeader(canvas: Canvas, model: HoldDragResizeModel) {
        val layout = model.layout
        text(canvas, HoldDragResizeDayTitle, HoldDragResizeTokens.Layout.Edge, layout.top + HoldDragResizeTokens.Layout.Line1, "title", HoldDragResizeTokens.Color.Ink)
        text(canvas, "$HoldDragResizeDayDate · ${HoldDragResizePlan.size} plans", HoldDragResizeTokens.Layout.Edge, layout.top + HoldDragResizeTokens.Layout.Line2, "date", HoldDragResizeTokens.Color.Mute)
        val x = layout.right - HoldDragResizeTokens.Layout.ChipW
        val y = layout.top + HoldDragResizeTokens.Layout.ChipY
        glass.draw(canvas, x, y, HoldDragResizeTokens.Layout.ChipW, HoldDragResizeTokens.Layout.ChipH, HoldDragResizeTokens.Layout.ChipH / 2, HoldDragResizeTokens.Tint.Chrome)
        val pad = 18.0
        canvas.drawCircle((x + pad + 3).toFloat(), (y + 17).toFloat(), 3f, fill(HoldDragResizeTokens.Color.Focus))
        text(canvas, "Focus today", x + pad + 11, y + 21, "chip", HoldDragResizeTokens.Color.Mute)
        drawRoll(canvas, model.focus, x + HoldDragResizeTokens.Layout.ChipW - pad, y + 39, HoldDragResizeTokens.Color.FocusInk, model.reducedMotion)
    }

    private fun drawWeek(canvas: Canvas, layout: HoldDragResizeLayout) {
        val x = HoldDragResizeTokens.Layout.Edge
        val width = layout.right - x
        val y = layout.top + HoldDragResizeTokens.Layout.WeekY
        glass.draw(canvas, x, y, width, HoldDragResizeTokens.Layout.WeekH, HoldDragResizeTokens.Layout.WeekRadius, HoldDragResizeTokens.Tint.Chrome)
        val step = (width - 8) / 7
        HoldDragResizeWeek.forEachIndexed { index, day ->
            val centre = x + 4 + step * (index + 0.5)
            val today = index == HoldDragResizeToday
            if (today) {
                glass.draw(
                    canvas,
                    centre - HoldDragResizeTokens.Layout.WeekCell / 2,
                    y + 4,
                    HoldDragResizeTokens.Layout.WeekCell,
                    HoldDragResizeTokens.Layout.WeekH - 8,
                    (HoldDragResizeTokens.Layout.WeekH - 8) / 2,
                    HoldDragResizeTokens.Tint.Selected,
                    thick = 1.0,
                )
            }
            text(canvas, day.day, centre, y + 21, "weekday", if (today) HoldDragResizeTokens.Color.Ink else HoldDragResizeTokens.Color.Faint, align = 1)
            text(
                canvas,
                day.date.toString(),
                centre,
                y + 40,
                "weekdate",
                if (today || index > HoldDragResizeToday) HoldDragResizeTokens.Color.Ink else HoldDragResizeTokens.Color.Mute,
                if (today || index > HoldDragResizeToday) 1.0 else 0.9,
                align = 1,
            )
            day.dots.forEachIndexed { dot, kind ->
                canvas.drawCircle(
                    (centre + (dot - (day.dots.size - 1) / 2.0) * 6).toFloat(),
                    (y + 48).toFloat(),
                    1.75f,
                    fill(kindColor(kind), if (today) 1.0 else 0.7),
                )
            }
        }
    }

    private fun drawLabels(canvas: Canvas, model: HoldDragResizeModel, edgeY: Double) {
        val layout = model.layout
        val nowY = layout.yOf(HoldDragResizeTokens.Day.Now.toDouble())
        for (hour in HoldDragResizeTokens.Day.Start / 60..HoldDragResizeTokens.Day.End / 60) {
            val y = layout.yOf(hour * 60.0)
            var alpha = 1.0
            if (edgeY >= 0) alpha *= 1 - labelMakesWay(model, abs(y - edgeY))
            alpha *= smooth(12.0, 20.0, abs(y - nowY))
            text(canvas, holdDragResizeClock(hour * 60.0), HoldDragResizeTokens.Layout.LabelRight, y + 4, "hour", HoldDragResizeTokens.Color.Faint, alpha, align = 2)
        }
        var nowAlpha = 1.0
        if (edgeY >= 0) nowAlpha *= 1 - labelMakesWay(model, abs(nowY - edgeY))
        timePill(canvas, nowY, holdDragResizeClock(HoldDragResizeTokens.Day.Now.toDouble()), HoldDragResizeTokens.Color.Now, HoldDragResizeTokens.Color.Ink, nowAlpha)
    }

    private fun drawShadows(canvas: Canvas, model: HoldDragResizeModel, only: Int, skip: Int) {
        HoldDragResizePlan.indices.forEach { index ->
            if (index == skip || (only >= 0 && index != only)) return@forEach
            val box = boxOf(model, index, lifted = index == only)
            val width = box.width * box.scale
            val height = box.height * box.scale
            val rect = RectF(
                (box.centreX - width / 2).toFloat(),
                (box.centreY - height / 2).toFloat(),
                (box.centreX + width / 2).toFloat(),
                (box.centreY + height / 2).toFloat(),
            )
            val elevation = model.elevation[index] * if (model.kind[index] == HoldDragResizeKind.Meeting) 0.6 else 1.0
            shadow.draw(canvas, rect, elevation, 2.2, min(HoldDragResizeTokens.Layout.Radius, height / 2) * box.scale)
        }
    }

    private fun drawCapsules(canvas: Canvas, model: HoldDragResizeModel) {
        HoldDragResizePlan.indices.forEach { index ->
            val box = boxOf(model, index, lifted = index == model.shown)
            val height = box.height * box.scale
            val width = HoldDragResizeTokens.Layout.Capsule
            val y = box.centreY - height / 2
            val color = if (model.kind[index] == HoldDragResizeKind.Meeting) 0xFF6E727B.toInt() else kindColor(model.kind[index])
            canvas.drawRoundRect(
                RectF(
                    (HoldDragResizeTokens.Layout.RailX - width / 2).toFloat(),
                    y.toFloat(),
                    (HoldDragResizeTokens.Layout.RailX + width / 2).toFloat(),
                    (y + height).toFloat(),
                ),
                (width / 2).toFloat(),
                (width / 2).toFloat(),
                fill(color),
            )
        }
    }

    private fun drawBlock(
        canvas: Canvas,
        model: HoldDragResizeModel,
        index: Int,
        lifted: Boolean,
        live: Pair<DoubleArray, DoubleArray>,
    ) {
        val box = boxOf(model, index, lifted)
        val item = HoldDragResizePlan[index]
        val kind = model.kind[index]
        val save = canvas.save()
        if (box.scale != 1.0) {
            canvas.scale(box.scale.toFloat(), box.scale.toFloat(), box.centreX.toFloat(), box.centreY.toFloat())
        }
        val radius = min(HoldDragResizeTokens.Layout.Radius, box.height / 2)
        val rect = RectF(box.x.toFloat(), box.y.toFloat(), (box.x + box.width).toFloat(), (box.y + box.height).toFloat())
        val cardPress = if (index == model.pressedIndex) model.press else 0.0
        val up = if (index == model.shown) model.lift else 0.0
        val light = if (model.reducedMotion) 0.0 else max(cardPress, 0.75 * up)
        val base = when (kind) {
            HoldDragResizeKind.Focus -> HoldDragResizeTokens.Tint.Focus
            HoldDragResizeKind.Task -> HoldDragResizeTokens.Tint.Task
            else -> HoldDragResizeTokens.Tint.Meeting
        }
        val tint = if (kind == HoldDragResizeKind.Meeting) {
            floatArrayOf(
                (base[0] * (1 - 0.45 * model.lock[index])).toFloat(),
                (base[1] * (1 - 0.45 * model.lock[index])).toFloat(),
                (base[2] * (1 - 0.40 * model.lock[index])).toFloat(),
                (base[3] + 0.08 * model.lock[index]).toFloat(),
            )
        } else {
            floatArrayOf(base[0], base[1], base[2], (base[3] + 0.06 * max(cardPress, up)).toFloat())
        }
        glass.draw(canvas, box.x, box.y, box.width, box.height, radius, tint, up, light, model.fingerX, model.fingerY)
        if (lifted) drawDelta(canvas, model, box, rect)

        val clip = canvas.save()
        canvas.holdDragResizeClipRoundRect(rect, radius.toFloat())
        val start = if (kind == HoldDragResizeKind.Meeting) model.start[index] else live.first[index]
        val end = if (kind == HoldDragResizeKind.Meeting) model.end[index] else live.first[index] + live.second[index]
        val subColor = if (kind == HoldDragResizeKind.Focus) HoldDragResizeTokens.Color.FocusInk else HoldDragResizeTokens.Color.Mute
        var x = box.x + HoldDragResizeTokens.Layout.PadX
        val right = box.x + box.width - HoldDragResizeTokens.Layout.PadX
        val twoLines = box.height >= 44
        val titleY = if (twoLines) box.y + HoldDragResizeTokens.Layout.PadY + 14 else box.y + box.height / 2 + 5
        if (kind == HoldDragResizeKind.Task) {
            canvas.drawCircle(
                (x + HoldDragResizeTokens.Layout.Check / 2).toFloat(),
                (titleY - 5).toFloat(),
                (HoldDragResizeTokens.Layout.Check / 2 - 0.75).toFloat(),
                stroke(HoldDragResizeTokens.Color.Task, 1.25f, 0.85),
            )
            x += HoldDragResizeTokens.Layout.Check + 8
        }
        val lockWidth = if (kind == HoldDragResizeKind.Meeting) 16 * smooth(0.0, 1.0, model.lock[index]) else 0.0
        if (twoLines) {
            text(canvas, fit("event", item.title, right - x - lockWidth), x, titleY, "event", HoldDragResizeTokens.Color.Ink)
            val range = if (kind == HoldDragResizeKind.Focus) {
                "${holdDragResizeClock(start)}–${holdDragResizeClock(end)} · ${holdDragResizeLength(end - start)}"
            } else {
                "${holdDragResizeClock(start)}–${holdDragResizeClock(end)}"
            }
            text(canvas, range, x, titleY + 18, "eventSub", subColor)
            if (kind != HoldDragResizeKind.Focus && item.note.isNotEmpty()) {
                val noteX = x + measure("eventSub", range)
                text(canvas, fit("eventNote", " · ${item.note}", right - noteX), noteX, titleY + 18, "eventNote", subColor)
            }
        } else {
            val side = if (kind == HoldDragResizeKind.Meeting) item.note else holdDragResizeClock(start)
            val sideStyle = if (kind == HoldDragResizeKind.Meeting) "eventNote" else "eventSub"
            val pushed = kind == HoldDragResizeKind.Task && index != model.active && start > model.start[index] + 0.5
            var sideWidth = measure(if (pushed) "eventNow" else sideStyle, side).toDouble()
            text(canvas, side, right - lockWidth, titleY, if (pushed) "eventNow" else sideStyle, if (pushed) HoldDragResizeTokens.Color.Ink else subColor, align = 2)
            val wasAlpha = if (kind == HoldDragResizeKind.Task) model.push[index] else 0.0
            if (wasAlpha > 0.004) {
                val old = holdDragResizeClock(model.was[index])
                val oldWidth = measure("eventSub", old)
                val oldX = right - sideWidth - 8 - oldWidth
                text(canvas, old, oldX, titleY, "eventSub", HoldDragResizeTokens.Color.Faint, wasAlpha)
                canvas.drawRect(
                    (oldX - 1).toFloat(),
                    (titleY - 4).toFloat(),
                    (oldX + oldWidth + 1).toFloat(),
                    (titleY - 3).toFloat(),
                    fill(HoldDragResizeTokens.Color.Faint, wasAlpha),
                )
                if (pushed) sideWidth += 8 + oldWidth
            }
            text(canvas, fit("event", item.title, right - lockWidth - sideWidth - 12 - x), x, titleY, "event", HoldDragResizeTokens.Color.Ink)
        }
        canvas.restoreToCount(clip)
        if (kind == HoldDragResizeKind.Meeting) drawLock(canvas, right - 4.5, titleY - 5, model.lock[index], model.reducedMotion)

        if (lifted && model.lift > 0.01) {
            val gripY = if (model.edge == 1) {
                box.y + box.height - HoldDragResizeTokens.Layout.GripInset - HoldDragResizeTokens.Layout.GripH
            } else {
                box.y + HoldDragResizeTokens.Layout.GripInset
            }
            val grip = RectF(
                (box.centreX - HoldDragResizeTokens.Layout.GripW / 2).toFloat(),
                gripY.toFloat(),
                (box.centreX + HoldDragResizeTokens.Layout.GripW / 2).toFloat(),
                (gripY + HoldDragResizeTokens.Layout.GripH).toFloat(),
            )
            canvas.drawRoundRect(grip, (HoldDragResizeTokens.Layout.GripH / 2).toFloat(), (HoldDragResizeTokens.Layout.GripH / 2).toFloat(), fill(HoldDragResizeTokens.Color.Ink, 0.7 * pillShow(model)))
        }
        canvas.restoreToCount(save)
    }

    private fun drawDelta(canvas: Canvas, model: HoldDragResizeModel, box: BlockBox, rect: RectF) {
        val alpha = pillShow(model)
        if (alpha < 0.01 || model.shown < 0) return
        val liftOffset = box.y - (model.layout.yOf(model.top[model.shown]) + HoldDragResizeTokens.Layout.CardInset)
        val grab = model.layout.yOf(model.originalEdge) + liftOffset
        val edge = model.layout.yOf(if (model.edge == 1) model.top[model.shown] + model.length[model.shown] else model.top[model.shown]) + liftOffset
        val growth = if (model.edge == 1) edge - grab else grab - edge
        if (growth > 1) {
            val low = min(grab, edge)
            val high = max(grab, edge)
            val from = if (model.edge == 1) low else high
            val to = if (model.edge == 1) high else low
            val save = canvas.save()
            canvas.holdDragResizeClipRoundRect(
                rect,
                min(HoldDragResizeTokens.Layout.Radius, box.height / 2).toFloat(),
            )
            canvas.drawRect(
                box.x.toFloat(),
                low.toFloat(),
                (box.x + box.width).toFloat(),
                high.toFloat(),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f,
                        from.toFloat(),
                        0f,
                        to.toFloat(),
                        intArrayOf(0x00FFB547, Color.argb((0.2 * alpha * 255).toInt(), 255, 181, 71)),
                        null,
                        Shader.TileMode.CLAMP,
                    )
                },
            )
            canvas.restoreToCount(save)
        }
        if (model.delta > 0.004 && box.height >= 40) {
            val minutes = model.deltaLast
            val label = "${if (minutes >= 0) "+" else "−"}${holdDragResizeLength(abs(minutes))}"
            val y = if (model.edge == 1) {
                box.y + box.height - HoldDragResizeTokens.Layout.GripInset - 1
            } else {
                box.y + HoldDragResizeTokens.Layout.GripInset + 9
            }
            text(canvas, label, box.x + box.width - HoldDragResizeTokens.Layout.PadX, y, "delta", HoldDragResizeTokens.Color.FocusInk, alpha * model.delta, align = 2)
        }
    }

    private fun drawBar(canvas: Canvas, model: HoldDragResizeModel) {
        val layout = model.layout
        val y = layout.barTop
        val plusX = layout.right - HoldDragResizeTokens.Layout.BarPlus
        val width = plusX - 8 - HoldDragResizeTokens.Layout.Edge
        glass.draw(canvas, HoldDragResizeTokens.Layout.Edge, y, width, HoldDragResizeTokens.Layout.BarH, HoldDragResizeTokens.Layout.BarH / 2, HoldDragResizeTokens.Tint.Chrome)
        glass.draw(canvas, plusX, y, HoldDragResizeTokens.Layout.BarPlus, HoldDragResizeTokens.Layout.BarH, HoldDragResizeTokens.Layout.BarH / 2, HoldDragResizeTokens.Tint.Chrome)
        val baseline = y + HoldDragResizeTokens.Layout.BarH / 2 + 5
        val rollRight = HoldDragResizeTokens.Layout.Edge + 20 + 64
        drawRoll(canvas, model.open, rollRight, baseline + 1, HoldDragResizeTokens.Color.Ink, model.reducedMotion)
        text(canvas, " open until 18:00", rollRight, baseline, "barMute", HoldDragResizeTokens.Color.Mute)
        val centreX = plusX + HoldDragResizeTokens.Layout.BarPlus / 2
        val centreY = y + HoldDragResizeTokens.Layout.BarH / 2
        val pen = stroke(HoldDragResizeTokens.Color.Ink, 2f)
        canvas.drawLine((centreX - 8).toFloat(), centreY.toFloat(), (centreX + 8).toFloat(), centreY.toFloat(), pen)
        canvas.drawLine(centreX.toFloat(), (centreY - 8).toFloat(), centreX.toFloat(), (centreY + 8).toFloat(), pen)
    }

    private fun drawRoll(canvas: Canvas, value: Double, right: Double, baseline: Double, color: Int, reduced: Boolean) {
        val current = max(value, 0.0)
        val from = floor((current + 0.01) / HoldDragResizeTokens.Resize.Quarter) * HoldDragResizeTokens.Resize.Quarter
        val fraction = ((current - from) / HoldDragResizeTokens.Resize.Quarter).coerceIn(0.0, 1.0)
        val to = from + HoldDragResizeTokens.Resize.Quarter
        val roll = if (reduced) 0.0 else 16.0
        fun parts(minutes: Double): Pair<String, String> {
            val rounded = round(minutes).toInt()
            return (rounded / 60).toString() to "%02d".format(rounded % 60)
        }
        val (hoursA, minutesA) = parts(from)
        val (hoursB, minutesB) = parts(to)
        fun cell(old: String, new: String, x: Double, width: Double, anchor: Double = 0.5) {
            fun at(value: String) = x + (width - measure("total", value)) * anchor
            if (fraction < 0.002 || old == new) {
                text(canvas, old, at(old), baseline, "total", color)
                return
            }
            val save = canvas.save()
            canvas.clipRect((x - 2).toFloat(), (baseline - 15).toFloat(), (x + width + 2).toFloat(), (baseline + 4).toFloat())
            text(canvas, old, at(old), baseline - roll * fraction, "total", color, 1 - fraction)
            text(canvas, new, at(new), baseline + roll * (1 - fraction), "total", color, fraction)
            canvas.restoreToCount(save)
        }
        val tight = 1.5
        val loose = 5.0
        var x = right - measure("totalUnit", "m")
        text(canvas, "m", x, baseline, "totalUnit", color)
        x -= tight + 2 * digitWidth
        cell(minutesA[0].toString(), minutesB[0].toString(), x, digitWidth.toDouble())
        cell(minutesA[1].toString(), minutesB[1].toString(), x + digitWidth, digitWidth.toDouble())
        x -= loose + measure("totalUnit", "h")
        text(canvas, "h", x, baseline, "totalUnit", color)
        val cells = max(hoursA.length, hoursB.length)
        val paddedA = hoursA.padStart(cells, ' ')
        val paddedB = hoursB.padStart(cells, ' ')
        for (index in cells - 1 downTo 0) {
            val old = paddedA[index].toString().trim()
            val new = paddedB[index].toString().trim()
            val width = max(measure("total", old), measure("total", new)).toDouble()
            x -= (if (index == cells - 1) tight else 0.0) + width
            cell(old, new, x, width, 1.0)
        }
    }

    private fun drawLock(canvas: Canvas, x: Double, y: Double, alpha: Double, reduced: Boolean) {
        if (alpha <= 0.01) return
        val scale = if (reduced) 1.0 else 0.85 + 0.15 * alpha
        val path = Path().apply {
            moveTo((x - 2.5 * scale).toFloat(), (y - 0.5 * scale).toFloat())
            lineTo((x - 2.5 * scale).toFloat(), (y - 2.5 * scale).toFloat())
            cubicTo(
                (x - 2.5 * scale).toFloat(),
                (y - 6 * scale).toFloat(),
                (x + 2.5 * scale).toFloat(),
                (y - 6 * scale).toFloat(),
                (x + 2.5 * scale).toFloat(),
                (y - 2.5 * scale).toFloat(),
            )
            lineTo((x + 2.5 * scale).toFloat(), (y - 0.5 * scale).toFloat())
        }
        canvas.drawPath(path, stroke(HoldDragResizeTokens.Color.Ink, 1.25f, 0.7 * alpha))
        canvas.drawRoundRect(
            RectF(
                (x - 4.5 * scale).toFloat(),
                (y - scale).toFloat(),
                (x + 4.5 * scale).toFloat(),
                (y + 6 * scale).toFloat(),
            ),
            1.75f,
            1.75f,
            fill(HoldDragResizeTokens.Color.Ink, 0.7 * alpha),
        )
    }

    private fun timePill(canvas: Canvas, centreY: Double, label: String, fill: Int, ink: Int, alpha: Double) {
        if (alpha <= 0.004) return
        val height = HoldDragResizeTokens.Layout.PillH
        val width = measure("pill", label) + 2 * HoldDragResizeTokens.Layout.PillPad
        val x = HoldDragResizeTokens.Layout.LabelRight + HoldDragResizeTokens.Layout.PillPad - width
        canvas.drawRoundRect(
            RectF(x.toFloat(), (centreY - height / 2).toFloat(), (x + width).toFloat(), (centreY + height / 2).toFloat()),
            (height / 2).toFloat(),
            (height / 2).toFloat(),
            fill(fill, alpha),
        )
        text(canvas, label, HoldDragResizeTokens.Layout.LabelRight, centreY + 4, "pill", ink, alpha, align = 2)
    }

    private fun edgeY(model: HoldDragResizeModel): Double {
        val index = model.shown
        if (index < 0 || model.lift <= 0.001) return -1.0
        return model.layout.yOf(if (model.edge == 1) model.top[index] + model.length[index] else model.top[index])
    }

    private fun boxOf(model: HoldDragResizeModel, index: Int, lifted: Boolean): BlockBox {
        val planned = model.end[index] - model.start[index]
        val squash = if (planned > 0) max(0.0, 1 - model.length[index] / planned) else 0.0
        val bulge = if (model.kind[index] == HoldDragResizeKind.Meeting || index == model.active) {
            0.0
        } else {
            min(HoldDragResizeTokens.Resize.BulgeMax, HoldDragResizeTokens.Resize.SquashBulge * squash)
        }
        val up = if (lifted && !model.reducedMotion) model.lift else 0.0
        val give = if (model.kind[index] == HoldDragResizeKind.Meeting && !model.reducedMotion) 1.5 * model.lock[index] else 0.0
        val y = model.layout.yOf(model.top[index]) + HoldDragResizeTokens.Layout.CardInset - HoldDragResizeTokens.Lift.Rise * up + give
        val height = max(model.length[index] * model.layout.ppm - 2 * HoldDragResizeTokens.Layout.CardInset - give, 4.0)
        val x = model.layout.cardX - bulge
        val width = model.layout.cardW + 2 * bulge
        val scale = 1 + HoldDragResizeTokens.Lift.Scale * up
        return BlockBox(x, y, width, height, scale, x + width / 2, y + height / 2)
    }

    private fun labelMakesWay(model: HoldDragResizeModel, distance: Double): Double =
        smooth(0.2, 0.5, model.lift) * (1 - smooth(22.0, 30.0, distance))

    private fun pillShow(model: HoldDragResizeModel): Double = smooth(0.5, 1.0, model.lift)

    private fun kindColor(kind: Int): Int = when (kind) {
        HoldDragResizeKind.Focus -> HoldDragResizeTokens.Color.Focus
        HoldDragResizeKind.Task -> HoldDragResizeTokens.Color.Task
        else -> HoldDragResizeTokens.Color.Meeting
    }

    private fun fit(style: String, value: String, width: Double): String {
        if (measure(style, value) <= width) return value
        var end = value.length
        while (end > 1 && measure(style, "${value.take(end).trimEnd()}…") > width) end--
        return "${value.take(end).trimEnd()}…"
    }

    private fun text(
        canvas: Canvas,
        value: String,
        x: Double,
        baseline: Double,
        style: String,
        color: Int,
        alpha: Double = 1.0,
        align: Int = 0,
    ) {
        if (alpha <= 0.004 || value.isEmpty()) return
        configureTextPaint(style, color, alpha)
        val left = when (align) {
            1 -> x - paint.measureText(value) / 2
            2 -> x - paint.measureText(value)
            else -> x
        }
        canvas.drawText(value, left.toFloat(), baseline.toFloat(), paint)
    }

    private fun measure(style: String, value: String): Float {
        configureTextPaint(style, Color.WHITE, 1.0)
        return paint.measureText(value)
    }

    private fun configureTextPaint(style: String, color: Int, alpha: Double) {
        val token = requireNotNull(styles[style])
        paint.reset()
        paint.flags = Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG
        paint.typeface = typeface(token.face)
        paint.textSize = token.size
        paint.color = color
        paint.alpha = (Color.alpha(color) * alpha).toInt().coerceIn(0, 255)
    }

    private fun typeface(face: Face): Typeface = when (face) {
        Face.Regular -> assets.regular
        Face.Medium -> assets.medium
        Face.Semibold -> assets.semibold
        Face.Mono -> assets.mono
        Face.MonoBold -> assets.monoBold
    }

    private fun fill(color: Int, alpha: Double = 1.0): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.alpha = (Color.alpha(color) * alpha).toInt().coerceIn(0, 255)
        style = Paint.Style.FILL
    }

    private fun stroke(color: Int, width: Float, alpha: Double = 1.0): Paint = fill(color, alpha).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
    }

    private fun bitmapPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun smooth(low: Double, high: Double, value: Double): Double {
        val progress = ((value - low) / (high - low)).coerceIn(0.0, 1.0)
        return progress * progress * (3 - 2 * progress)
    }
}
