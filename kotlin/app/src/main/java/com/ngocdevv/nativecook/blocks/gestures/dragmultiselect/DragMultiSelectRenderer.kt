package com.ngocdevv.nativecook.blocks.gestures.dragmultiselect

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Picture
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.roundToInt

internal class DragMultiSelectRenderer(private val fonts: DragMultiSelectAssets) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { style = Paint.Style.FILL }
    private var mainPicture: Picture? = null
    private var rowsPicture: Picture? = null
    private var pictureWidth = -1
    private var pictureHeight = -1
    private var pictureTop = -1f

    fun draw(canvas: Canvas, engine: DragMultiSelectEngine) {
        ensurePictures(engine)
        mainPicture?.let { canvas.drawPicture(it) }
        drawRoute(canvas, engine)
        drawHeaderControl(canvas, engine)
        drawList(canvas, engine)
        drawFootFade(canvas, engine)
        drawPanel(canvas, engine)
        if (engine.ghostAlpha > .001f) drawGhost(canvas, engine)
        if (engine.passAmount > .001f) drawPass(canvas, engine)
    }

    private fun ensurePictures(engine: DragMultiSelectEngine) {
        val w = engine.width.roundToInt()
        val h = engine.height.roundToInt()
        if (w == pictureWidth && h == pictureHeight && engine.safeTop == pictureTop) return
        pictureWidth = w
        pictureHeight = h
        pictureTop = engine.safeTop
        mainPicture = Picture().also { picture ->
            val c = picture.beginRecording(w, h)
            drawStaticMain(c, engine)
            picture.endRecording()
        }
        val rowsHeight = (DragMultiSelectEpisodes.size * DragMultiSelectTokens.Layout.row).roundToInt()
        rowsPicture = Picture().also { picture ->
            val c = picture.beginRecording(w, rowsHeight)
            drawStaticRows(c, engine.width)
            picture.endRecording()
        }
    }

    private fun drawStaticMain(canvas: Canvas, e: DragMultiSelectEngine) {
        canvas.drawColor(DragMultiSelectTokens.Colors.page)
        val l = DragMultiSelectTokens.Layout
        // Navigation.
        stroke.color = DragMultiSelectTokens.Colors.ink
        stroke.strokeWidth = 2f
        val back = Path().apply {
            moveTo(l.edge + 15f, e.safeTop + 12f)
            lineTo(l.edge + 8f, e.safeTop + 22f)
            lineTo(l.edge + 15f, e.safeTop + 32f)
        }
        canvas.drawPath(back, stroke)

        // Flight card.
        fill.color = DragMultiSelectTokens.Colors.card
        canvas.drawRoundRect(l.edge, e.cardTop, e.width - l.edge, e.cardTop + l.cardHeight, l.cardRadius, l.cardRadius, fill)
        stroke.color = DragMultiSelectTokens.Colors.line
        stroke.strokeWidth = 1f
        canvas.drawRoundRect(l.edge + .5f, e.cardTop + .5f, e.width - l.edge - .5f, e.cardTop + l.cardHeight - .5f, l.cardRadius, l.cardRadius, stroke)

        drawText(canvas, "UA 1514", l.edge + l.cardPad, e.cardTop + 27f, DragMultiSelectTokens.Type.cardMeta, DragMultiSelectTokens.Colors.ink, fonts.interMedium)
        drawText(canvas, "  ·  Thu 23 Oct  ·  5h 25m", l.edge + l.cardPad + 46f, e.cardTop + 27f, DragMultiSelectTokens.Type.cardMeta, DragMultiSelectTokens.Colors.mute, fonts.interRegular)
        drawText(canvas, "SFO", l.edge + l.cardPad, e.cardTop + 72f, DragMultiSelectTokens.Type.code, DragMultiSelectTokens.Colors.ink, fonts.interLight)
        drawText(canvas, "JFK", e.width - l.edge - l.cardPad, e.cardTop + 72f, DragMultiSelectTokens.Type.code, DragMultiSelectTokens.Colors.ink, fonts.interLight, Paint.Align.RIGHT)
        drawText(canvas, "San Francisco", l.edge + l.cardPad, e.cardTop + 95f, DragMultiSelectTokens.Type.city, DragMultiSelectTokens.Colors.mute, fonts.interRegular)
        drawText(canvas, "New York", e.width - l.edge - l.cardPad, e.cardTop + 95f, DragMultiSelectTokens.Type.city, DragMultiSelectTokens.Colors.mute, fonts.interRegular, Paint.Align.RIGHT)
        drawText(canvas, "9:05", l.edge + l.cardPad, e.cardTop + 132f, DragMultiSelectTokens.Type.time, DragMultiSelectTokens.Colors.ink, fonts.interMedium)
        drawText(canvas, " AM", l.edge + l.cardPad + 39f, e.cardTop + 132f, DragMultiSelectTokens.Type.cardMeta, DragMultiSelectTokens.Colors.mute, fonts.interRegular)
        drawText(canvas, "5:30", e.width - l.edge - l.cardPad - 23f, e.cardTop + 132f, DragMultiSelectTokens.Type.time, DragMultiSelectTokens.Colors.ink, fonts.interMedium, Paint.Align.RIGHT)
        drawText(canvas, " PM", e.width - l.edge - l.cardPad, e.cardTop + 132f, DragMultiSelectTokens.Type.cardMeta, DragMultiSelectTokens.Colors.mute, fonts.interRegular, Paint.Align.RIGHT)

        val route = routePath(e)
        stroke.color = DragMultiSelectTokens.Colors.dot
        stroke.strokeWidth = 1.5f
        stroke.pathEffect = DashPathEffect(floatArrayOf(1f, 5f), 0f)
        canvas.drawPath(route, stroke)
        stroke.pathEffect = null
        fill.color = DragMultiSelectTokens.Colors.ink
        canvas.drawCircle(l.edge + DragMultiSelectTokens.Route.x0, e.cardTop + DragMultiSelectTokens.Route.y, 3.5f, fill)
        fill.color = DragMultiSelectTokens.Colors.card
        canvas.drawCircle(l.edge + DragMultiSelectTokens.Route.x1, e.cardTop + DragMultiSelectTokens.Route.y, 3.5f, fill)
        stroke.color = DragMultiSelectTokens.Colors.ink
        stroke.strokeWidth = 1.5f
        canvas.drawCircle(l.edge + DragMultiSelectTokens.Route.x1, e.cardTop + DragMultiSelectTokens.Route.y, 3.5f, stroke)

        drawText(canvas, "IN-FLIGHT LISTENING", l.edge, e.sectionTop + 13f, DragMultiSelectTokens.Type.section, DragMultiSelectTokens.Colors.mute, fonts.interMedium)
        drawText(canvas, "Second Draft · 7 new", e.width - l.edge, e.sectionTop + 13f, DragMultiSelectTokens.Type.sectionMeta, DragMultiSelectTokens.Colors.mute, fonts.interRegular, Paint.Align.RIGHT)

        // Bottom figures card is static; its changing values are not.
        fill.color = DragMultiSelectTokens.Colors.card
        canvas.drawRoundRect(l.panelSide, e.panelTop, e.width - l.panelSide, e.panelTop + l.cells, 18f, 18f, fill)
        stroke.color = DragMultiSelectTokens.Colors.line
        stroke.strokeWidth = 1f
        canvas.drawRoundRect(l.panelSide + .5f, e.panelTop + .5f, e.width - l.panelSide - .5f, e.panelTop + l.cells - .5f, 18f, 18f, stroke)
        val divider = e.width / 2f
        canvas.drawLine(divider, e.panelTop, divider, e.panelTop + l.cells, stroke)
    }

    private fun drawStaticRows(canvas: Canvas, width: Float) {
        val l = DragMultiSelectTokens.Layout
        DragMultiSelectEpisodes.forEachIndexed { i, episode ->
            val top = i * l.row
            var dotY = top + 4f
            fill.color = DragMultiSelectTokens.Colors.dot
            while (dotY <= top + l.row - 5f) {
                canvas.drawCircle(l.railX + 1f, dotY + 1f, 1f, fill)
                dotY += 6f
            }
            drawText(canvas, episode.title, l.textX, top + 29f, DragMultiSelectTokens.Type.title, DragMultiSelectTokens.Colors.ink, fonts.interLight)
            drawText(canvas, "#${episode.number} · ${formatMinutes(episode.minutes)} · ${episode.guest}", l.textX, top + 49f, DragMultiSelectTokens.Type.meta, DragMultiSelectTokens.Colors.mute, fonts.interRegular)
        }
        @Suppress("UNUSED_VARIABLE") val keepWidth = width
    }

    private fun drawRoute(canvas: Canvas, e: DragMultiSelectEngine) {
        val route = routePath(e)
        val measure = PathMeasure(route, false)
        val segment = Path()
        measure.getSegment(0f, measure.length * e.coverage(), segment, true)
        stroke.shader = LinearGradient(
            DragMultiSelectTokens.Layout.edge + DragMultiSelectTokens.Route.x0,
            0f,
            DragMultiSelectTokens.Layout.edge + DragMultiSelectTokens.Route.x1,
            0f,
            DragMultiSelectTokens.Colors.orange,
            DragMultiSelectTokens.Colors.violet,
            Shader.TileMode.CLAMP,
        )
        stroke.strokeWidth = 2.5f
        canvas.drawPath(segment, stroke)
        stroke.shader = null

        val p = e.planeProgress().coerceIn(0f, 1f)
        val point = routePoint(e, p)
        fill.color = DragMultiSelectTokens.Colors.card
        canvas.drawCircle(point.first, point.second, 11f, fill)
        drawPlane(canvas, point.first, point.second, p, DragMultiSelectTokens.Colors.ink)
    }

    private fun drawHeaderControl(canvas: Canvas, e: DragMultiSelectEngine) {
        if (e.phase == DragMultiSelectPhase.Downloading || e.phase == DragMultiSelectPhase.Done) return
        drawText(
            canvas,
            if (e.selecting) "Cancel" else "Select",
            e.width - DragMultiSelectTokens.Layout.edge,
            e.safeTop + 28f,
            DragMultiSelectTokens.Type.nav,
            DragMultiSelectTokens.Colors.violet,
            fonts.interMedium,
            Paint.Align.RIGHT,
        )
    }

    private fun drawList(canvas: Canvas, e: DragMultiSelectEngine) {
        val save = canvas.save()
        canvas.clipRect(0f, e.listTop, e.width, e.panelTop)
        canvas.translate(0f, e.listTop - e.scroll)
        rowsPicture?.let { canvas.drawPicture(it) }

        val l = DragMultiSelectTokens.Layout
        var accumulated = 0
        var colorIndex = 0
        var landsAfter = -1
        for (i in DragMultiSelectEpisodes.indices) {
            val on = e.selected[i] || e.downloaded[i]
            val start = accumulated
            val segmentColor = if (colorIndex % 2 == 0) DragMultiSelectTokens.Colors.orange else DragMultiSelectTokens.Colors.violet
            if (on) {
                accumulated += DragMultiSelectEpisodes[i].minutes
                colorIndex++
                if (landsAfter < 0 && accumulated >= DragMultiSelectEngine.FLIGHT_MINUTES) landsAfter = i
            }
            val share = e.share(i)
            val top = i * l.row
            if (share > .002f) {
                fill.color = DragMultiSelectTokens.withAlpha(segmentColor, if (e.reducedMotion) share else 1f)
                val railHeight = l.row - 8f
                canvas.drawRoundRect(l.railX, top + 4f, l.railX + l.rail, top + 4f + railHeight * share, l.rail / 2f, l.rail / 2f, fill)
            }
            if (on) {
                val timeAlpha = ((share - .4f) / .4f).coerceIn(0f, 1f)
                val pair = clock(9 * 60 + 5 + start)
                drawText(canvas, pair.first, l.numberX, top + 24f, DragMultiSelectTokens.Type.rowTime, DragMultiSelectTokens.withAlpha(DragMultiSelectTokens.Colors.ink, timeAlpha), fonts.interMedium)
                drawText(canvas, pair.second, l.numberX, top + 38f, DragMultiSelectTokens.Type.rowAp, DragMultiSelectTokens.withAlpha(DragMultiSelectTokens.Colors.mute, timeAlpha), fonts.interRegular)
            } else {
                drawText(canvas, "—", l.numberX, top + 24f, DragMultiSelectTokens.Type.rowTime, DragMultiSelectTokens.Colors.dim, fonts.interMedium)
            }
            drawEpisodeGlyph(canvas, e, i, top)
        }

        if (landsAfter >= 0) {
            val y = (landsAfter + 1) * l.row
            stroke.color = DragMultiSelectTokens.withAlpha(DragMultiSelectTokens.Colors.green, .45f)
            stroke.strokeWidth = .75f
            canvas.drawLine(l.railX + 7f, y, e.width - l.edge, y, stroke)
            fill.color = DragMultiSelectTokens.Colors.page
            canvas.drawCircle(l.railX + 1f, y, 5f, fill)
            stroke.color = DragMultiSelectTokens.Colors.green
            stroke.strokeWidth = 2f
            canvas.drawCircle(l.railX + 1f, y, 5f, stroke)
            fill.color = DragMultiSelectTokens.Colors.page
            canvas.drawRect(e.width - 128f, y - 9f, e.width - l.edge, y + 8f, fill)
            drawText(canvas, "Lands JFK 5:30 PM", e.width - l.edge, y + 5f, DragMultiSelectTokens.Type.land, DragMultiSelectTokens.Colors.green, fonts.interMedium, Paint.Align.RIGHT)
        }
        canvas.restoreToCount(save)
    }

    private fun drawEpisodeGlyph(canvas: Canvas, e: DragMultiSelectEngine, row: Int, top: Float) {
        val l = DragMultiSelectTokens.Layout
        val cx = e.width - l.trailRight - l.glyph / 2f
        val cy = top + l.row / 2f
        val mark = e.marks[row]
        val p = e.progress[row]
        if (mark > .5f) {
            fill.color = DragMultiSelectTokens.Colors.greenSoft
            canvas.drawCircle(cx, cy, 11f, fill)
            stroke.color = DragMultiSelectTokens.Colors.green
            stroke.strokeWidth = 2.2f
            val check = Path().apply {
                moveTo(cx - 5f, cy)
                lineTo(cx - 1f, cy + 4f)
                lineTo(cx + 6f, cy - 5f)
            }
            canvas.drawPath(check, stroke)
        } else if (p > .01f) {
            stroke.color = DragMultiSelectTokens.Colors.ring
            stroke.strokeWidth = 2f
            canvas.drawCircle(cx, cy, 9f, stroke)
            stroke.color = DragMultiSelectTokens.Colors.violet
            canvas.drawArc(RectF(cx - 9f, cy - 9f, cx + 9f, cy + 9f), -90f, 360f * p, false, stroke)
        } else {
            val tint = blend(DragMultiSelectTokens.Colors.glyph, DragMultiSelectTokens.Colors.violet, ((e.shown[row] - .45f) / .4f).coerceIn(0f, 1f))
            stroke.color = tint
            stroke.strokeWidth = 1.6f
            canvas.drawLine(cx, cy - 7f, cx, cy + 3f, stroke)
            canvas.drawLine(cx - 4f, cy - 1f, cx, cy + 3f, stroke)
            canvas.drawLine(cx + 4f, cy - 1f, cx, cy + 3f, stroke)
            canvas.drawLine(cx - 6f, cy + 6f, cx + 6f, cy + 6f, stroke)
        }
    }

    private fun drawFootFade(canvas: Canvas, e: DragMultiSelectEngine) {
        fill.shader = LinearGradient(
            0f,
            e.panelTop - 24f,
            0f,
            e.panelTop + 24f,
            intArrayOf(0x000B0B0C, DragMultiSelectTokens.Colors.page, DragMultiSelectTokens.Colors.page),
            floatArrayOf(0f, .5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, e.panelTop - 24f, e.width, e.height, fill)
        fill.shader = null
    }

    private fun drawPanel(canvas: Canvas, e: DragMultiSelectEngine) {
        val l = DragMultiSelectTokens.Layout
        val minutes = e.offlineMinutes
        val finished = e.phase == DragMultiSelectPhase.Done || e.phase == DragMultiSelectPhase.Ready
        val enabled = e.phase == DragMultiSelectPhase.Selecting && e.selectedCount > 0
        val gap = DragMultiSelectEngine.FLIGHT_MINUTES - minutes
        val leftValue = shortMinutes(minutes)
        val leftLabel = if (finished) "Offline" else "Planned"
        val rightValue: String
        val rightLabel: String
        var rightColor = DragMultiSelectTokens.Colors.ink
        when {
            finished -> {
                rightValue = e.downloadedCount.toString()
                rightLabel = "Episodes"
            }
            e.phase == DragMultiSelectPhase.Idle || e.selectedCount == 0 -> {
                rightValue = shortMinutes(DragMultiSelectEngine.FLIGHT_MINUTES)
                rightLabel = "Flight time"
            }
            gap > 0 -> {
                rightValue = shortMinutes(gap)
                rightLabel = "Still to fill"
            }
            else -> {
                rightValue = "+${shortMinutes(-gap)}"
                rightLabel = "Spare on landing"
                rightColor = DragMultiSelectTokens.Colors.green
            }
        }
        val half = e.width / 2f
        drawText(canvas, leftValue, l.panelSide + 16f, e.panelTop + 39f, DragMultiSelectTokens.Type.figure, DragMultiSelectTokens.Colors.ink, fonts.interLight)
        drawText(canvas, leftLabel, l.panelSide + 16f, e.panelTop + 57f, DragMultiSelectTokens.Type.figureLabel, DragMultiSelectTokens.Colors.mute, fonts.interRegular)
        drawText(canvas, rightValue, half + 16f, e.panelTop + 39f, DragMultiSelectTokens.Type.figure, rightColor, fonts.interLight)
        drawText(canvas, rightLabel, half + 16f, e.panelTop + 57f, DragMultiSelectTokens.Type.figureLabel, DragMultiSelectTokens.Colors.mute, fonts.interRegular)

        val buttonTop = e.panelTop + l.cells + l.cellsGap
        fill.color = when {
            enabled -> DragMultiSelectTokens.Colors.violet
            e.phase == DragMultiSelectPhase.Downloading -> DragMultiSelectTokens.Colors.violetTrack
            else -> DragMultiSelectTokens.Colors.buttonOff
        }
        canvas.drawRoundRect(l.panelSide, buttonTop, e.width - l.panelSide, buttonTop + l.button, l.buttonRadius, l.buttonRadius, fill)
        if (e.phase == DragMultiSelectPhase.Downloading) {
            val rows = e.selected.indices.filter { e.selected[it] }
            val p = if (rows.isEmpty()) 0f else rows.sumOf { e.progress[it].toDouble() }.toFloat() / rows.size
            val right = l.panelSide + (e.width - l.panelSide * 2f) * p
            val save = canvas.save()
            val clip = Path().apply {
                addRoundRect(
                    RectF(l.panelSide, buttonTop, e.width - l.panelSide, buttonTop + l.button),
                    l.buttonRadius,
                    l.buttonRadius,
                    Path.Direction.CW,
                )
            }
            canvas.clipPath(clip)
            fill.color = DragMultiSelectTokens.Colors.violet
            canvas.drawRect(l.panelSide, buttonTop, right, buttonTop + l.button, fill)
            canvas.restoreToCount(save)
        }
        val label = when {
            finished -> "Ready for takeoff"
            e.phase == DragMultiSelectPhase.Downloading -> "Downloading ${e.selectedCount} episode${if (e.selectedCount == 1) "" else "s"}"
            enabled -> "Download ${e.selectedCount} · ${formatSize(e.selectedMinutes)}"
            else -> "Hold an episode, then drag"
        }
        val ink = when {
            enabled -> DragMultiSelectTokens.Colors.violetInk
            e.phase == DragMultiSelectPhase.Downloading -> DragMultiSelectTokens.Colors.ink
            finished -> DragMultiSelectTokens.Colors.ink
            else -> DragMultiSelectTokens.Colors.mute
        }
        val baseline = buttonTop + 32f
        if (finished) {
            val labelWidth = measure(label, DragMultiSelectTokens.Type.button, fonts.interSemibold)
            val start = e.width / 2f - labelWidth / 2f - 13f
            stroke.color = DragMultiSelectTokens.Colors.green
            stroke.strokeWidth = 2.2f
            val check = Path().apply {
                moveTo(start, baseline - 7f)
                lineTo(start + 4f, baseline - 3f)
                lineTo(start + 11f, baseline - 12f)
            }
            canvas.drawPath(check, stroke)
            drawText(canvas, label, start + 18f, baseline, DragMultiSelectTokens.Type.button, ink, fonts.interSemibold)
        } else {
            drawText(canvas, label, e.width / 2f, baseline, DragMultiSelectTokens.Type.button, ink, fonts.interSemibold, Paint.Align.CENTER)
        }
    }

    private fun drawGhost(canvas: Canvas, e: DragMultiSelectEngine) {
        val radius = DragMultiSelectTokens.Layout.finger / 2f * (1f - .12f * e.ghostPressed)
        fill.color = DragMultiSelectTokens.withAlpha(DragMultiSelectTokens.Colors.finger, e.ghostAlpha)
        canvas.drawCircle(e.ghostX, e.ghostY, radius, fill)
        stroke.color = DragMultiSelectTokens.withAlpha(DragMultiSelectTokens.Colors.fingerEdge, e.ghostAlpha)
        stroke.strokeWidth = 1f
        canvas.drawCircle(e.ghostX, e.ghostY, radius, stroke)
    }

    private fun drawPass(canvas: Canvas, e: DragMultiSelectEngine) {
        val a = e.passAmount.coerceIn(0f, 1f)
        fill.color = DragMultiSelectTokens.withAlpha(DragMultiSelectTokens.Colors.scrim, a)
        canvas.drawRect(0f, 0f, e.width, e.height, fill)
        val side = 28f
        val cardWidth = e.width - side * 2f
        val cardHeight = 560f
        val top = e.safeTop + 52f
        val travel = (1f - a) * DragMultiSelectTokens.passFrom
        val scale = DragMultiSelectTokens.passScale + (1f - DragMultiSelectTokens.passScale) * a
        val save = canvas.save()
        canvas.translate(e.width / 2f, top + cardHeight / 2f + travel)
        canvas.scale(scale, scale)
        canvas.translate(-e.width / 2f, -(top + cardHeight / 2f))

        fill.color = DragMultiSelectTokens.Colors.pass
        canvas.drawRoundRect(side, top, side + cardWidth, top + cardHeight, 28f, 28f, fill)
        stroke.color = DragMultiSelectTokens.Colors.passEdge
        stroke.strokeWidth = 1f
        canvas.drawRoundRect(side + .5f, top + .5f, side + cardWidth - .5f, top + cardHeight - .5f, 28f, 28f, stroke)

        val keys = arrayOf("FLIGHT" to "UA 1514", "SEAT" to "23A", "BOARDING" to "8:25 AM", "DATE" to "23 Oct")
        val column = (cardWidth - 44f) / 4f
        keys.forEachIndexed { i, pair ->
            val x = side + 22f + i * column
            drawText(canvas, pair.first, x, top + 34f, DragMultiSelectTokens.Type.passKey, DragMultiSelectTokens.Colors.passMute, fonts.interMedium)
            drawText(canvas, pair.second, x, top + 57f, DragMultiSelectTokens.Type.passValue, DragMultiSelectTokens.Colors.ink, fonts.interMedium)
        }
        drawText(canvas, "SFO", e.width / 2f, top + 177f, DragMultiSelectTokens.Type.passCode, DragMultiSelectTokens.Colors.ink, fonts.dotoBlack, Paint.Align.CENTER)
        drawText(canvas, "San Francisco International", e.width / 2f, top + 205f, DragMultiSelectTokens.Type.passCity, DragMultiSelectTokens.Colors.passMute, fonts.interRegular, Paint.Align.CENTER)
        drawText(canvas, "JFK", e.width / 2f, top + 316f, DragMultiSelectTokens.Type.passCode, DragMultiSelectTokens.Colors.ink, fonts.dotoBlack, Paint.Align.CENTER)
        drawText(canvas, "John F. Kennedy International", e.width / 2f, top + 344f, DragMultiSelectTokens.Type.passCity, DragMultiSelectTokens.Colors.passMute, fonts.interRegular, Paint.Align.CENTER)

        val perfY = top + 392f
        fill.color = 0x52FFFFFF
        for (i in 0 until 34) canvas.drawCircle(side + 16f + i * ((cardWidth - 32f) / 33f), perfY, 1f, fill)
        fill.color = DragMultiSelectTokens.Colors.page
        canvas.drawCircle(side, perfY, 11f, fill)
        canvas.drawCircle(side + cardWidth, perfY, 11f, fill)

        fill.shader = RadialGradient(
            e.width / 2f,
            top + cardHeight + 30f,
            230f,
            intArrayOf(DragMultiSelectTokens.Colors.orange, DragMultiSelectTokens.Colors.violet, 0x00A57BFF),
            floatArrayOf(0f, .5f, 1f),
            Shader.TileMode.CLAMP,
        )
        val glow = Path().apply {
            addRoundRect(RectF(side, top + 390f, side + cardWidth, top + cardHeight), floatArrayOf(0f, 0f, 0f, 0f, 28f, 28f, 28f, 28f), Path.Direction.CW)
        }
        canvas.drawPath(glow, fill)
        fill.shader = null

        drawText(canvas, "SECOND DRAFT", side + 22f, top + 466f, DragMultiSelectTokens.Type.passShow, DragMultiSelectTokens.Colors.ink, fonts.interSemibold)
        drawText(canvas, "${e.downloadedCount} episodes · ${shortMinutes(e.offlineMinutes)} offline", side + 22f, top + 491f, DragMultiSelectTokens.Type.passNote, DragMultiSelectTokens.Colors.passText, fonts.interRegular)
        drawText(canvas, "${formatSize(e.offlineMinutes)} on this phone", side + 22f, top + 508f, DragMultiSelectTokens.Type.passNote, DragMultiSelectTokens.Colors.passText, fonts.interRegular)
        val chipText = "Ready for takeoff"
        val chipWidth = measure(chipText, DragMultiSelectTokens.Type.chip, fonts.interSemibold) + 24f
        fill.color = DragMultiSelectTokens.Colors.chip
        canvas.drawRoundRect(side + cardWidth - 22f - chipWidth, top + 468f, side + cardWidth - 22f, top + 496f, 14f, 14f, fill)
        drawText(canvas, chipText, side + cardWidth - 22f - chipWidth / 2f, top + 487f, DragMultiSelectTokens.Type.chip, DragMultiSelectTokens.Colors.chipInk, fonts.interSemibold, Paint.Align.CENTER)
        canvas.restoreToCount(save)
    }

    private fun routePath(e: DragMultiSelectEngine): Path {
        val edge = DragMultiSelectTokens.Layout.edge
        val r = DragMultiSelectTokens.Route
        val x0 = edge + r.x0
        val x1 = edge + r.x1
        val y = e.cardTop + r.y
        return Path().apply {
            moveTo(x0, y)
            quadTo((x0 + x1) / 2f, y - 2f * r.lift, x1, y)
        }
    }

    private fun routePoint(e: DragMultiSelectEngine, t: Float): Pair<Float, Float> {
        val edge = DragMultiSelectTokens.Layout.edge
        val r = DragMultiSelectTokens.Route
        val x0 = edge + r.x0
        val x1 = edge + r.x1
        val y = e.cardTop + r.y
        val cx = (x0 + x1) / 2f
        val cy = y - 2f * r.lift
        val v = 1f - t
        return (v * v * x0 + 2f * v * t * cx + t * t * x1) to (v * v * y + 2f * v * t * cy + t * t * y)
    }

    private fun drawPlane(canvas: Canvas, x: Float, y: Float, t: Float, color: Int) {
        val angle = (-32f + 64f * t) * PI.toFloat() / 180f
        val save = canvas.save()
        canvas.rotate(angle * 180f / PI.toFloat(), x, y)
        fill.color = color
        val p = Path().apply {
            moveTo(x + 9f, y)
            lineTo(x - 2f, y - 2f)
            lineTo(x - 6f, y - 8f)
            lineTo(x - 8f, y - 8f)
            lineTo(x - 6f, y - 2f)
            lineTo(x - 10f, y - 3f)
            lineTo(x - 12f, y)
            lineTo(x - 10f, y + 3f)
            lineTo(x - 6f, y + 2f)
            lineTo(x - 8f, y + 8f)
            lineTo(x - 6f, y + 8f)
            lineTo(x - 2f, y + 2f)
            close()
        }
        canvas.drawPath(p, fill)
        canvas.restoreToCount(save)
    }

    private fun drawText(
        canvas: Canvas,
        value: String,
        x: Float,
        baseline: Float,
        size: Float,
        color: Int,
        typeface: Typeface,
        align: Paint.Align = Paint.Align.LEFT,
    ) {
        text.shader = null
        text.typeface = typeface
        text.textSize = size
        text.color = color
        text.alpha = 255
        text.textAlign = align
        canvas.drawText(value, x, baseline, text)
    }

    private fun measure(value: String, size: Float, typeface: Typeface): Float {
        text.typeface = typeface
        text.textSize = size
        return text.measureText(value)
    }

    private fun clock(minute: Int): Pair<String, String> {
        val h24 = (minute / 60) % 24
        val minutePart = minute % 60
        val rawHour = h24 % 12
        val hour = if (rawHour == 0) 12 else rawHour
        return "$hour:${minutePart.toString().padStart(2, '0')}" to if (h24 >= 12) "PM" else "AM"
    }

    private fun shortMinutes(minute: Int): String {
        if (minute < 60) return "${minute}m"
        val h = minute / 60
        val m = minute % 60
        return if (m == 0) "${h}h" else "${h}h ${m}m"
    }

    private fun formatMinutes(minute: Int): String {
        if (minute < 60) return "$minute min"
        val h = minute / 60
        val m = minute % 60
        return if (m == 0) "$h hr" else "$h hr $m min"
    }

    private fun formatSize(minute: Int): String {
        val megabytes = (minute * .94f).roundToInt()
        return if (megabytes >= 1_000) "%.1f GB".format(megabytes / 1_000f) else "$megabytes MB"
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        fun channel(color: Int, shift: Int) = color ushr shift and 0xFF
        val alpha = (channel(a, 24) + (channel(b, 24) - channel(a, 24)) * u).roundToInt()
        val red = (channel(a, 16) + (channel(b, 16) - channel(a, 16)) * u).roundToInt()
        val green = (channel(a, 8) + (channel(b, 8) - channel(a, 8)) * u).roundToInt()
        val blue = (channel(a, 0) + (channel(b, 0) - channel(a, 0)) * u).roundToInt()
        return alpha shl 24 or (red shl 16) or (green shl 8) or blue
    }
}
