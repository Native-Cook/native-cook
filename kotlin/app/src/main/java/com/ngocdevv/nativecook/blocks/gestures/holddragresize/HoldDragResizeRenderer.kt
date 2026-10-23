package com.ngocdevv.nativecook.blocks.gestures.holddragresize

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.PathParser
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class HoldDragResizeRenderer(
    private val assets: HoldDragResizeAssets,
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val handPath = requireNotNull(PathParser.createPathFromPathData(HAND_PATH))
    private val colors = mapOf(
        Tokens.Color.background to Color.parseColor(Tokens.Color.background),
        Tokens.Color.chrome to Color.parseColor(Tokens.Color.chrome),
        Tokens.Color.chromeHi to Color.parseColor(Tokens.Color.chromeHi),
        Tokens.Color.ink to Color.parseColor(Tokens.Color.ink),
        Tokens.Color.mute to Color.parseColor(Tokens.Color.mute),
        Tokens.Color.divider to Color.parseColor(Tokens.Color.divider),
        Tokens.Color.toolDim to Color.parseColor(Tokens.Color.toolDim),
        Tokens.Color.faint to Color.parseColor(Tokens.Color.faint),
        Tokens.Color.blue to Color.parseColor(Tokens.Color.blue),
        Tokens.Color.scrim to Color.parseColor(Tokens.Color.scrim),
        Tokens.Color.touch to Color.parseColor(Tokens.Color.touch),
        Tokens.Color.captionShadow to Color.parseColor(Tokens.Color.captionShadow),
        Tokens.Color.finger to Color.parseColor(Tokens.Color.finger),
        Tokens.Color.fingerEdge to Color.parseColor(Tokens.Color.fingerEdge),
        Tokens.Color.sticker to Color.parseColor(Tokens.Color.sticker),
        Tokens.Color.stickerRing to Color.parseColor(Tokens.Color.stickerRing),
        Tokens.Color.overlayButton to Color.parseColor(Tokens.Color.overlayButton),
        Tokens.Color.badge to Color.parseColor(Tokens.Color.badge),
        Tokens.Color.badgeDot to Color.parseColor(Tokens.Color.badgeDot),
        Tokens.Color.badgeBorder to Color.parseColor(Tokens.Color.badgeBorder),
    )

    fun draw(
        canvas: Canvas,
        simulation: HoldDragResizeSimulation,
        screenWidth: Double,
        screenHeight: Double,
        safeTop: Double,
        safeBottom: Double,
        pixelScale: Float,
    ) {
        canvas.drawColor(color(Tokens.Color.background))
        drawTimeline(canvas, simulation, screenWidth, screenHeight, safeBottom)
        drawPreview(canvas, simulation, screenWidth, pixelScale)
        drawChrome(canvas, simulation, screenWidth, screenHeight, safeTop, safeBottom)
        drawCoach(canvas, simulation, screenWidth, screenHeight)
        drawGhost(canvas, simulation)
    }

    private fun drawTimeline(
        canvas: Canvas,
        simulation: HoldDragResizeSimulation,
        screenWidth: Double,
        screenHeight: Double,
        safeBottom: Double,
    ) {
        val shown = simulation.shown
        val opacity = 1.0 - min(HoldDragResizeGeometry.fullness(shown) * 1.6, 1.0)
        if (opacity <= 0.0) {
            return
        }
        val groupTop = Tokens.frameTops[0] + Tokens.sizes[0] + HoldDragResizeGeometry.belowShift(simulation.lag)
        val squeezed = HoldDragResizeGeometry.squeeze(shown)
        val controlsTop = groupTop + Tokens.Layout.controlsGap - Tokens.Layout.control / 2.0 + Tokens.Squeeze.controls * squeezed
        paint.alpha = alpha(opacity)
        val playX = Tokens.Layout.gutter + Tokens.Layout.control / 2.0
        val playY = controlsTop + Tokens.Layout.control / 2.0
        withControlTransform(canvas, simulation, Tokens.Control.timelinePlay, playX, playY) {
            val controlOpacity = opacity * simulation.controlOpacity(Tokens.Control.timelinePlay)
            drawCircleButton(canvas, playX, playY, Tokens.Layout.control, Tokens.Color.chromeHi, controlOpacity)
            drawPause(canvas, playX, playY, simulation.playing, controlOpacity)
        }
        val currentSecond = floor(simulation.clock).toInt()
        drawCentredText(
            canvas,
            "0:${currentSecond.toString().padStart(2, '0')}  /  0:12",
            screenWidth / 2.0,
            controlsTop + 21.0,
            Tokens.Type.timeSize,
            Tokens.Type.timeWeight,
            Tokens.Color.mute,
            opacity,
        )
        val redoX = screenWidth - Tokens.Layout.gutter - Tokens.Layout.control / 2.0
        val undoX = redoX - Tokens.Layout.control - Tokens.Layout.pairGap
        drawCircleButton(canvas, undoX, controlsTop + Tokens.Layout.control / 2.0, Tokens.Layout.control, Tokens.Color.chromeHi)
        drawCircleButton(canvas, redoX, controlsTop + Tokens.Layout.control / 2.0, Tokens.Layout.control, Tokens.Color.chromeHi)
        drawUndo(canvas, undoX, controlsTop + Tokens.Layout.control / 2.0, false, opacity)
        drawUndo(canvas, redoX, controlsTop + Tokens.Layout.control / 2.0, true, opacity * 0.55)

        val dividerTop = groupTop + Tokens.Layout.controlsGap - Tokens.Layout.control / 2.0 + Tokens.Layout.control + Tokens.Layout.dividerGap - Tokens.Layout.control / 2.0 + Tokens.Squeeze.ruler * squeezed
        paint.color = color(Tokens.Color.divider)
        paint.alpha = alpha(opacity)
        canvas.drawRect(0f, dividerTop.toFloat(), screenWidth.toFloat(), (dividerTop + 1.0).toFloat(), paint)

        val laneTop = dividerTop + 1.0 + Tokens.Layout.rulerGap - Tokens.Layout.dividerGap - Tokens.Layout.ruler / 2.0 + (Tokens.Squeeze.below - Tokens.Squeeze.ruler) * squeezed
        canvas.save()
        canvas.clipRect(0f, laneTop.toFloat(), screenWidth.toFloat(), screenHeight.toFloat())
        drawRulerAndStrip(canvas, simulation, screenWidth, laneTop, opacity, squeezed)
        drawTracks(canvas, screenWidth, laneTop, opacity)
        canvas.restore()

        val hintY = screenHeight - safeBottom - Tokens.Layout.hintBottom
        paint.color = color(Tokens.Color.background)
        paint.alpha = alpha(opacity)
        canvas.drawRect(0f, (hintY - 14.0).toFloat(), screenWidth.toFloat(), (hintY + 8.0).toFloat(), paint)
        drawCentredText(canvas, Tokens.Copy.hint, screenWidth / 2.0, hintY, Tokens.Type.hintSize, Tokens.Type.hintWeight, Tokens.Color.mute, opacity)
    }

    private fun drawRulerAndStrip(
        canvas: Canvas,
        simulation: HoldDragResizeSimulation,
        screenWidth: Double,
        laneTop: Double,
        opacity: Double,
        squeezed: Double,
    ) {
        val centre = screenWidth / 2.0
        val firstX = centre - Tokens.Timeline.period * Tokens.Timeline.repeat - simulation.clock * Tokens.Timing.stripSpeed
        val marks = ceil(Tokens.Timeline.period * Tokens.Timeline.repeat * 2.0 / Tokens.Layout.rulerPitch).toInt()
        val rulerY = laneTop + (Tokens.Squeeze.ruler - Tokens.Squeeze.below) * squeezed
        repeat(marks) { index ->
            val x = firstX + Tokens.Timeline.rulerOffset + index * Tokens.Layout.rulerPitch
            if (index % 2 == 0) {
                paint.color = color(Tokens.Color.mute)
                paint.alpha = alpha(opacity)
                canvas.drawCircle(x.toFloat(), (rulerY + Tokens.Layout.ruler / 2.0).toFloat(), 1.5f, paint)
            } else {
                val label = if (index % 4 == 1) "10s" else "30s"
                drawCentredText(canvas, label, x, rulerY + 9.0, Tokens.Type.rulerSize, Tokens.Type.rulerWeight, Tokens.Color.mute, opacity)
            }
        }
        val stripTop = laneTop + Tokens.Layout.ruler + Tokens.Layout.stripGap
        val thumbCount = assets.clips.size * Tokens.Timeline.thumbsPerClip * Tokens.Timeline.repeat * 2
        repeat(thumbCount) { index ->
            val x = firstX + index * Tokens.Layout.thumb
            if (x + Tokens.Layout.thumb >= 0.0 && x <= screenWidth) {
                val image = assets.clips[(index / Tokens.Timeline.thumbsPerClip) % assets.clips.size]
                drawBitmapCrop(canvas, image, RectF(x.toFloat(), stripTop.toFloat(), (x + Tokens.Layout.thumb + 0.5).toFloat(), (stripTop + Tokens.Layout.strip).toFloat()), opacity)
            }
        }
    }

    private fun drawTracks(
        canvas: Canvas,
        screenWidth: Double,
        laneTop: Double,
        opacity: Double,
    ) {
        val stripTop = laneTop + Tokens.Layout.ruler + Tokens.Layout.stripGap
        val firstTrackTop = stripTop + Tokens.Layout.strip + Tokens.Layout.trackFirstGap
        val secondTrackTop = firstTrackTop + Tokens.Layout.track + Tokens.Layout.trackGap
        paint.color = color(Tokens.Color.chrome)
        paint.alpha = alpha(opacity)
        canvas.drawRect(0f, firstTrackTop.toFloat(), screenWidth.toFloat(), (firstTrackTop + Tokens.Layout.track).toFloat(), paint)
        canvas.drawRect(0f, secondTrackTop.toFloat(), screenWidth.toFloat(), (secondTrackTop + Tokens.Layout.track).toFloat(), paint)
        drawText(canvas, "♪", Tokens.Layout.trackIndent, firstTrackTop + 22.0, Tokens.Type.trackSize, 500, Tokens.Color.mute, opacity)
        drawText(canvas, Tokens.Copy.audioTrack, Tokens.Layout.trackIndent + 20.0, firstTrackTop + 22.0, Tokens.Type.trackSize, Tokens.Type.trackWeight, Tokens.Color.mute, opacity)
        drawText(canvas, "Aa", Tokens.Layout.trackIndent, secondTrackTop + 22.0, Tokens.Type.trackSize, 700, Tokens.Color.mute, opacity)
        drawText(canvas, Tokens.Copy.textTrack, Tokens.Layout.trackIndent + 25.0, secondTrackTop + 22.0, Tokens.Type.trackSize, Tokens.Type.trackWeight, Tokens.Color.mute, opacity)

        val trimTop = stripTop + (Tokens.Layout.strip - Tokens.Layout.trimHandleHeight) / 2.0
        paint.color = color(Tokens.Color.ink)
        paint.alpha = alpha(opacity)
        canvas.drawRoundRect(
            RectF(
                (Tokens.Layout.trimHandleX - Tokens.Layout.trimHandleWidth / 2.0).toFloat(),
                trimTop.toFloat(),
                (Tokens.Layout.trimHandleX + Tokens.Layout.trimHandleWidth / 2.0).toFloat(),
                (trimTop + Tokens.Layout.trimHandleHeight).toFloat(),
            ),
            3f,
            3f,
            paint,
        )
        val stickerY = stripTop + Tokens.Layout.strip / 2.0
        paint.color = color(Tokens.Color.stickerRing)
        canvas.drawCircle(Tokens.Layout.stickerX.toFloat(), stickerY.toFloat(), (Tokens.Layout.stickerSize / 2.0).toFloat(), paint)
        paint.color = color(Tokens.Color.sticker)
        canvas.drawCircle(Tokens.Layout.stickerX.toFloat(), stickerY.toFloat(), (Tokens.Layout.stickerSize / 2.0 - 2.0).toFloat(), paint)
        drawCentredText(canvas, "☺", Tokens.Layout.stickerX, stickerY + 5.0, 14.0, 600, Tokens.Color.stickerInk, opacity)
        paint.color = color(Tokens.Color.playhead)
        canvas.drawRoundRect(
            RectF(
                (screenWidth / 2.0 - 1.0).toFloat(),
                laneTop.toFloat(),
                (screenWidth / 2.0 + 1.0).toFloat(),
                (secondTrackTop + Tokens.Layout.track + 6.0).toFloat(),
            ),
            1f,
            1f,
            paint,
        )
    }

    private fun drawPreview(
        canvas: Canvas,
        simulation: HoldDragResizeSimulation,
        screenWidth: Double,
        pixelScale: Float,
    ) {
        val frame = HoldDragResizeGeometry.preview(simulation.shown, screenWidth)
        val scale = 1.0 - (1.0 - Tokens.Upgrade.press) * simulation.press * (1.0 - simulation.held) - (1.0 - Tokens.Resize.lift) * simulation.held
        val centreX = frame.left + frame.width / 2.0
        val centreY = frame.top + frame.height / 2.0
        canvas.save()
        canvas.scale(scale.toFloat(), scale.toFloat(), centreX.toFloat(), centreY.toFloat())
        if (simulation.held > 0.001) {
            paint.color = color(Tokens.Color.background)
            paint.alpha = alpha(Tokens.Upgrade.liftShadowOpacity * simulation.held)
            paint.setShadowLayer((Tokens.Upgrade.liftShadowRadius * pixelScale).toFloat(), 0f, (Tokens.Upgrade.liftShadowY * simulation.held * pixelScale).toFloat(), color(Tokens.Color.background))
            canvas.drawRoundRect(frame.rect(), frame.radius.toFloat(), frame.radius.toFloat(), paint)
            paint.clearShadowLayer()
        }
        canvas.save()
        canvas.clipPath(roundRectPath(frame.rect(), frame.radius.toFloat()))
        paint.color = color(Tokens.Color.chrome)
        paint.alpha = 255
        canvas.drawRect(frame.rect(), paint)
        intArrayOf(1, 2, 3, 0).forEach { index ->
            val layerOpacity = clipOpacity(index, simulation.clock)
            if (layerOpacity > 0.0) {
                val clipSeconds = Tokens.Timing.clip / 1000.0
                val fadeSeconds = Tokens.Timing.crossfade / 1000.0
                val start = index * clipSeconds - fadeSeconds
                val loopSeconds = Tokens.LOOP_MS / 1000.0
                val local = ((simulation.clock - start) % loopSeconds + loopSeconds) % loopSeconds
                val push = if (simulation.reducedMotion) 0.0 else Tokens.Timing.kenBurns * min(local / (clipSeconds + fadeSeconds), 1.0)
                val destination = scaledRect(frame.rect(), 1.0 + push)
                drawBitmapCrop(canvas, assets.clips[index], destination, layerOpacity)
            }
        }
        drawCaption(canvas, simulation, frame)
        if (simulation.held > 0.001) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = color(Tokens.Color.rim)
            paint.alpha = alpha(Tokens.Upgrade.rim * simulation.held)
            canvas.drawRoundRect(frame.rect(), frame.radius.toFloat(), frame.radius.toFloat(), paint)
            paint.style = Paint.Style.FILL
        }
        drawBadge(canvas, simulation, frame)
        canvas.restore()
        canvas.restore()
    }

    private fun drawCaption(
        canvas: Canvas,
        simulation: HoldDragResizeSimulation,
        frame: HoldDragResizeFrame,
    ) {
        val index = floor(simulation.clock / (Tokens.Timing.clip / 1000.0)).toInt() % Tokens.Copy.captions.size
        val caption = Tokens.Copy.captions[index]
        if (caption.isEmpty()) {
            return
        }
        val scale = simulation.shown / Tokens.sizes[2]
        paint.setShadowLayer(6f, 0f, 1f, color(Tokens.Color.captionShadow))
        drawCentredText(
            canvas,
            caption,
            frame.left + frame.width / 2.0,
            frame.top + frame.height * 0.62 + Tokens.Type.captionSize * scale,
            Tokens.Type.captionSize * scale,
            Tokens.Type.captionWeight,
            Tokens.Color.caption,
            1.0,
        )
        paint.clearShadowLayer()
    }

    private fun drawBadge(
        canvas: Canvas,
        simulation: HoldDragResizeSimulation,
        frame: HoldDragResizeFrame,
    ) {
        val opacity = simulation.held.coerceIn(0.0, 1.0)
        if (opacity <= 0.01) {
            return
        }
        val label = Tokens.Copy.sizeNames[simulation.near]
        setTextPaint(Tokens.Type.timeSize, 600, Tokens.Color.ink, opacity)
        val labelWidth = paint.measureText(label)
        val dotsWidth = Tokens.Badge.dot * 3.0 + Tokens.Space.xs * 2.0
        val width = Tokens.Badge.padX * 2.0 + dotsWidth + Tokens.Badge.gap + labelWidth
        val scale = Tokens.Badge.initialScale + (1.0 - Tokens.Badge.initialScale) * opacity
        val centreX = frame.left + frame.width / 2.0
        val bottom = frame.top + frame.height - Tokens.Badge.bottom + Tokens.Upgrade.badgeRise * (1.0 - opacity)
        val rect = RectF(
            (centreX - width / 2.0).toFloat(),
            (bottom - Tokens.Badge.height).toFloat(),
            (centreX + width / 2.0).toFloat(),
            bottom.toFloat(),
        )
        canvas.save()
        canvas.scale(scale.toFloat(), scale.toFloat(), centreX.toFloat(), (rect.centerY()).toFloat())
        paint.style = Paint.Style.FILL
        paint.color = color(Tokens.Color.badge)
        paint.alpha = alpha(opacity)
        canvas.drawRoundRect(rect, (Tokens.Badge.height / 2.0).toFloat(), (Tokens.Badge.height / 2.0).toFloat(), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = color(Tokens.Color.badgeBorder)
        canvas.drawRoundRect(rect, (Tokens.Badge.height / 2.0).toFloat(), (Tokens.Badge.height / 2.0).toFloat(), paint)
        paint.style = Paint.Style.FILL
        var dotX = rect.left + Tokens.Badge.padX.toFloat() + (Tokens.Badge.dot / 2.0).toFloat()
        repeat(3) { index ->
            paint.color = color(if (index == simulation.near) Tokens.Color.ink else Tokens.Color.badgeDot)
            canvas.drawCircle(dotX, rect.centerY(), (Tokens.Badge.dot / 2.0).toFloat(), paint)
            dotX += (Tokens.Badge.dot + Tokens.Space.xs).toFloat()
        }
        drawText(
            canvas,
            label,
            rect.left + Tokens.Badge.padX + dotsWidth + Tokens.Badge.gap,
            rect.centerY() + Tokens.Type.timeSize * 0.36,
            Tokens.Type.timeSize,
            600,
            Tokens.Color.ink,
            opacity,
        )
        canvas.restore()
    }

    private fun drawChrome(
        canvas: Canvas,
        simulation: HoldDragResizeSimulation,
        screenWidth: Double,
        screenHeight: Double,
        safeTop: Double,
        safeBottom: Double,
    ) {
        val shown = simulation.shown
        val fullness = HoldDragResizeGeometry.fullness(shown)
        val editorOpacity = 1.0 - min(fullness * 1.6, 1.0)
        val barY = safeTop + Tokens.Layout.barY
        if (editorOpacity > 0.0) {
            val leftX = Tokens.Layout.gutter + Tokens.Layout.barButton / 2.0
            val rightX = screenWidth - Tokens.Layout.gutter - Tokens.Layout.barButton / 2.0
            withControlTransform(canvas, simulation, Tokens.Control.topLeft, leftX, barY) {
                val controlOpacity = editorOpacity * simulation.controlOpacity(Tokens.Control.topLeft)
                drawCircleButton(canvas, leftX, barY, Tokens.Layout.barButton, Tokens.Color.chromeHi, controlOpacity)
                drawChevron(canvas, leftX, barY, true, controlOpacity)
            }
            withControlTransform(canvas, simulation, Tokens.Control.topRight, rightX, barY) {
                val controlOpacity = editorOpacity * simulation.controlOpacity(Tokens.Control.topRight)
                drawCircleButton(canvas, rightX, barY, Tokens.Layout.barButton, Tokens.Color.blue, controlOpacity)
                drawArrow(canvas, rightX, barY, controlOpacity)
            }
            drawToolRow(canvas, simulation, shown, screenWidth, screenHeight, safeBottom, editorOpacity)
        }
        val overlayOpacity = ((fullness - 0.85) / 0.15).coerceIn(0.0, 1.0)
        if (overlayOpacity > 0.0) {
            val leftX = Tokens.Layout.gutter + Tokens.Layout.barButton / 2.0
            withControlTransform(canvas, simulation, Tokens.Control.topLeft, leftX, barY) {
                val controlOpacity = overlayOpacity * simulation.controlOpacity(Tokens.Control.topLeft)
                drawCircleButton(canvas, leftX, barY, Tokens.Layout.barButton, Tokens.Color.overlayButton, controlOpacity)
                drawClose(canvas, leftX, barY, controlOpacity)
            }
            drawFullScreenControls(canvas, simulation, fullness, screenWidth, screenHeight, safeBottom, overlayOpacity)
        }
    }

    private fun drawToolRow(
        canvas: Canvas,
        simulation: HoldDragResizeSimulation,
        shown: Double,
        screenWidth: Double,
        screenHeight: Double,
        safeBottom: Double,
        opacity: Double,
    ) {
        val top = screenHeight - safeBottom - Tokens.Layout.tools + Tokens.Squeeze.tools * HoldDragResizeGeometry.squeeze(shown)
        paint.color = color(Tokens.Color.background)
        paint.alpha = alpha(opacity)
        canvas.drawRect(0f, top.toFloat(), screenWidth.toFloat(), screenHeight.toFloat(), paint)
        Tokens.Copy.tools.forEachIndexed { index, label ->
            val left = Tokens.Layout.toolInset + index * (Tokens.Layout.tool + Tokens.Layout.toolGap)
            val iconTop = top + Tokens.Layout.toolTop
            val dim = Tokens.Copy.toolDim[index]
            val control = Tokens.Control.firstTool + index
            val centreX = left + Tokens.Layout.tool / 2.0
            val centreY = iconTop + Tokens.Layout.toolH / 2.0
            withControlTransform(canvas, simulation, control, centreX, centreY) {
                val controlOpacity = opacity * simulation.controlOpacity(control)
                paint.color = color(Tokens.Color.chrome)
                paint.alpha = alpha(controlOpacity)
                canvas.drawRoundRect(
                    RectF(left.toFloat(), iconTop.toFloat(), (left + Tokens.Layout.tool).toFloat(), (iconTop + Tokens.Layout.toolH).toFloat()),
                    Tokens.Layout.toolRadius.toFloat(),
                    Tokens.Layout.toolRadius.toFloat(),
                    paint,
                )
                val iconColor = if (dim) Tokens.Color.toolDim else Tokens.Color.ink
                drawCentredText(canvas, toolGlyph(index), centreX, iconTop + 27.0, if (index == 0) Tokens.Type.toolAaSize else 18.0, 700, iconColor, controlOpacity)
                drawCentredText(canvas, label, centreX, iconTop + Tokens.Layout.toolH + 14.0, Tokens.Type.toolSize, Tokens.Type.toolWeight, iconColor, controlOpacity)
            }
        }
    }

    private fun drawFullScreenControls(
        canvas: Canvas,
        simulation: HoldDragResizeSimulation,
        fullness: Double,
        screenWidth: Double,
        screenHeight: Double,
        safeBottom: Double,
        opacity: Double,
    ) {
        val iconY = screenHeight - safeBottom - Tokens.Layout.fullIconsBottom - Tokens.Layout.fullIcon / 2.0
        Tokens.Copy.fullTools.forEachIndexed { index, label ->
            val progress = ((fullness - 0.85 - Tokens.Upgrade.stagger * index) / 0.1).coerceIn(0.0, 1.0)
            val x = Tokens.Layout.gutter + Tokens.Layout.fullIcon / 2.0 + index * (screenWidth - Tokens.Layout.gutter * 2.0 - Tokens.Layout.fullIcon) / (Tokens.Copy.fullTools.size - 1)
            val y = iconY + Tokens.Layout.fullIconRise * (1.0 - progress)
            drawCircleButton(canvas, x, y, Tokens.Layout.fullIcon, Tokens.Color.overlayButton, opacity * progress)
            drawCentredText(canvas, fullGlyph(index), x, y + 5.5, 17.0, 600, Tokens.Color.ink, opacity * progress)
        }
        val pillTop = screenHeight - safeBottom - Tokens.Layout.pillsBottom - Tokens.Layout.pillHeight
        val editWidth = 112.0
        val nextWidth = 91.0
        withControlTransform(canvas, simulation, Tokens.Control.editVideo, Tokens.Layout.gutter + editWidth / 2.0, pillTop + Tokens.Layout.pillHeight / 2.0) {
            drawPill(canvas, Tokens.Layout.gutter, pillTop, editWidth, Tokens.Copy.editVideo, Tokens.Color.overlayButton, opacity * simulation.controlOpacity(Tokens.Control.editVideo), false)
        }
        withControlTransform(canvas, simulation, Tokens.Control.next, screenWidth - Tokens.Layout.gutter - nextWidth / 2.0, pillTop + Tokens.Layout.pillHeight / 2.0) {
            drawPill(canvas, screenWidth - Tokens.Layout.gutter - nextWidth, pillTop, nextWidth, Tokens.Copy.next, Tokens.Color.blue, opacity * simulation.controlOpacity(Tokens.Control.next), true)
        }
    }

    private fun drawCoach(
        canvas: Canvas,
        simulation: HoldDragResizeSimulation,
        screenWidth: Double,
        screenHeight: Double,
    ) {
        if (simulation.coach <= 0.001) {
            return
        }
        paint.color = color(Tokens.Color.scrim)
        paint.alpha = alpha(simulation.coach)
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), paint)
        val handY = Tokens.Coach.handY + Tokens.Demo.coachTravel * simulation.coachProgress
        paint.color = color(Tokens.Color.touch)
        canvas.drawCircle(
            (Tokens.Coach.handX + Tokens.Coach.touchX).toFloat(),
            (handY + Tokens.Coach.touchY).toFloat(),
            (Tokens.Coach.touchSize / 2.0).toFloat(),
            paint,
        )
        canvas.save()
        canvas.translate(Tokens.Coach.handX.toFloat(), handY.toFloat())
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = Tokens.Coach.handStroke.toFloat()
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = color(Tokens.Color.ink)
        paint.alpha = alpha(simulation.coach)
        canvas.drawPath(handPath, paint)
        paint.style = Paint.Style.FILL
        canvas.restore()
        drawCentredMultilineText(
            canvas,
            Tokens.Copy.coach,
            screenWidth / 2.0,
            Tokens.Coach.textTop,
            Tokens.Type.coachSize,
            Tokens.Type.coachLine,
            Tokens.Type.coachWeight,
            Tokens.Color.ink,
            simulation.coach,
        )
        val button = RectF(
            Tokens.Coach.buttonX.toFloat(),
            Tokens.Coach.buttonY.toFloat(),
            (Tokens.Coach.buttonX + Tokens.Coach.buttonWidth).toFloat(),
            (Tokens.Coach.buttonY + Tokens.Coach.buttonHeight).toFloat(),
        )
        withControlTransform(canvas, simulation, Tokens.Control.gotIt, button.centerX().toDouble(), button.centerY().toDouble()) {
            val opacity = simulation.coach * simulation.controlOpacity(Tokens.Control.gotIt)
            paint.color = color(Tokens.Color.ink)
            paint.alpha = alpha(opacity)
            canvas.drawRoundRect(button, (Tokens.Coach.buttonHeight / 2.0).toFloat(), (Tokens.Coach.buttonHeight / 2.0).toFloat(), paint)
            drawCentredText(canvas, Tokens.Copy.gotIt, button.centerX().toDouble(), button.centerY() + 4.5, Tokens.Type.coachButtonSize, Tokens.Type.coachButtonWeight, Tokens.Color.background, opacity)
        }
    }

    private fun drawGhost(
        canvas: Canvas,
        simulation: HoldDragResizeSimulation,
    ) {
        if (simulation.ghostOpacity <= 0.001) {
            return
        }
        val radius = Tokens.Layout.finger / 2.0 * (1.0 - 0.18 * simulation.ghostPress)
        paint.color = color(Tokens.Color.finger)
        paint.alpha = alpha(simulation.ghostOpacity)
        canvas.drawCircle(simulation.ghostX.toFloat(), simulation.ghostY.toFloat(), radius.toFloat(), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = color(Tokens.Color.fingerEdge)
        canvas.drawCircle(simulation.ghostX.toFloat(), simulation.ghostY.toFloat(), radius.toFloat(), paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawBitmapCrop(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: RectF,
        opacity: Double,
    ) {
        val sourceAspect = bitmap.width.toDouble() / bitmap.height
        val destinationAspect = destination.width().toDouble() / destination.height()
        val source = if (sourceAspect > destinationAspect) {
            val sourceWidth = bitmap.height * destinationAspect
            val left = (bitmap.width - sourceWidth) / 2.0
            Rect(left.toInt(), 0, (left + sourceWidth).toInt(), bitmap.height)
        } else {
            val sourceHeight = bitmap.width / destinationAspect
            val top = (bitmap.height - sourceHeight) / 2.0
            Rect(0, top.toInt(), bitmap.width, (top + sourceHeight).toInt())
        }
        paint.alpha = alpha(opacity)
        canvas.drawBitmap(bitmap, source, destination, paint)
    }

    private fun clipOpacity(index: Int, clock: Double): Double {
        val clip = Tokens.Timing.clip / 1000.0
        val fade = Tokens.Timing.crossfade / 1000.0
        val loop = Tokens.LOOP_MS / 1000.0
        val start = index * clip - fade
        if (index == 0) {
            return if (clock < clip) {
                1.0 - ((clock - (clip - fade)) / fade).coerceIn(0.0, 1.0)
            } else {
                ((clock - (loop - fade)) / fade).coerceIn(0.0, 1.0)
            }
        }
        if (index == 1) {
            return if (clock >= start && clock < (index + 1) * clip) 1.0 else 0.0
        }
        return if (clock >= start && clock < (index + 1) * clip) {
            ((clock - start) / fade).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
    }

    private fun drawCircleButton(
        canvas: Canvas,
        x: Double,
        y: Double,
        size: Double,
        token: String,
        opacity: Double = 1.0,
    ) {
        paint.color = color(token)
        paint.alpha = alpha(opacity)
        canvas.drawCircle(x.toFloat(), y.toFloat(), (size / 2.0).toFloat(), paint)
    }

    private fun drawPause(canvas: Canvas, x: Double, y: Double, playing: Boolean, opacity: Double) {
        paint.color = color(Tokens.Color.ink)
        paint.alpha = alpha(opacity)
        if (playing) {
            canvas.drawRoundRect(RectF((x - 4.0).toFloat(), (y - 6.0).toFloat(), (x - 1.0).toFloat(), (y + 6.0).toFloat()), 1f, 1f, paint)
            canvas.drawRoundRect(RectF((x + 1.0).toFloat(), (y - 6.0).toFloat(), (x + 4.0).toFloat(), (y + 6.0).toFloat()), 1f, 1f, paint)
        } else {
            val path = Path()
            path.moveTo((x - 4.0).toFloat(), (y - 6.0).toFloat())
            path.lineTo((x + 6.0).toFloat(), y.toFloat())
            path.lineTo((x - 4.0).toFloat(), (y + 6.0).toFloat())
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    private fun drawChevron(canvas: Canvas, x: Double, y: Double, up: Boolean, opacity: Double) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = color(Tokens.Color.ink)
        paint.alpha = alpha(opacity)
        val direction = if (up) -1.0 else 1.0
        val path = Path()
        path.moveTo((x - 5.0).toFloat(), (y + 2.0 * direction).toFloat())
        path.lineTo(x.toFloat(), (y - 3.0 * direction).toFloat())
        path.lineTo((x + 5.0).toFloat(), (y + 2.0 * direction).toFloat())
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawArrow(canvas: Canvas, x: Double, y: Double, opacity: Double) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = color(Tokens.Color.ink)
        paint.alpha = alpha(opacity)
        canvas.drawLine((x - 6.0).toFloat(), y.toFloat(), (x + 5.0).toFloat(), y.toFloat(), paint)
        canvas.drawLine((x + 1.0).toFloat(), (y - 4.0).toFloat(), (x + 5.0).toFloat(), y.toFloat(), paint)
        canvas.drawLine((x + 1.0).toFloat(), (y + 4.0).toFloat(), (x + 5.0).toFloat(), y.toFloat(), paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawClose(canvas: Canvas, x: Double, y: Double, opacity: Double) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = color(Tokens.Color.ink)
        paint.alpha = alpha(opacity)
        canvas.drawLine((x - 5.0).toFloat(), (y - 5.0).toFloat(), (x + 5.0).toFloat(), (y + 5.0).toFloat(), paint)
        canvas.drawLine((x + 5.0).toFloat(), (y - 5.0).toFloat(), (x - 5.0).toFloat(), (y + 5.0).toFloat(), paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawUndo(canvas: Canvas, x: Double, y: Double, mirrored: Boolean, opacity: Double) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.7f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = color(if (mirrored) Tokens.Color.faint else Tokens.Color.ink)
        paint.alpha = alpha(opacity)
        val direction = if (mirrored) -1.0 else 1.0
        canvas.drawArc(RectF((x - 6.0).toFloat(), (y - 5.0).toFloat(), (x + 6.0).toFloat(), (y + 7.0).toFloat()), 205f, 250f * direction.toFloat(), false, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawPill(
        canvas: Canvas,
        left: Double,
        top: Double,
        width: Double,
        label: String,
        token: String,
        opacity: Double,
        arrow: Boolean,
    ) {
        paint.color = color(token)
        paint.alpha = alpha(opacity)
        val rect = RectF(left.toFloat(), top.toFloat(), (left + width).toFloat(), (top + Tokens.Layout.pillHeight).toFloat())
        canvas.drawRoundRect(rect, (Tokens.Layout.pillHeight / 2.0).toFloat(), (Tokens.Layout.pillHeight / 2.0).toFloat(), paint)
        val textX = if (arrow) left + width / 2.0 - 7.0 else left + width / 2.0
        drawCentredText(canvas, label, textX, top + 28.0, Tokens.Type.pillSize, Tokens.Type.pillWeight, Tokens.Color.ink, opacity)
        if (arrow) {
            drawArrow(canvas, left + width - 19.0, top + Tokens.Layout.pillHeight / 2.0, opacity)
        }
    }

    private inline fun withControlTransform(
        canvas: Canvas,
        simulation: HoldDragResizeSimulation,
        control: Int,
        centreX: Double,
        centreY: Double,
        draw: () -> Unit,
    ) {
        canvas.save()
        val scale = simulation.controlScale(control).toFloat()
        canvas.scale(scale, scale, centreX.toFloat(), centreY.toFloat())
        draw()
        canvas.restore()
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Double,
        baseline: Double,
        size: Double,
        weight: Int,
        token: String,
        opacity: Double,
    ) {
        setTextPaint(size, weight, token, opacity)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(text, x.toFloat(), baseline.toFloat(), paint)
    }

    private fun drawCentredText(
        canvas: Canvas,
        text: String,
        x: Double,
        baseline: Double,
        size: Double,
        weight: Int,
        token: String,
        opacity: Double,
    ) {
        setTextPaint(size, weight, token, opacity)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, x.toFloat(), baseline.toFloat(), paint)
    }

    private fun drawCentredMultilineText(
        canvas: Canvas,
        text: String,
        x: Double,
        top: Double,
        size: Double,
        lineHeight: Double,
        weight: Int,
        token: String,
        opacity: Double,
    ) {
        text.lines().forEachIndexed { index, line ->
            drawCentredText(canvas, line, x, top + size + index * lineHeight, size, weight, token, opacity)
        }
    }

    private fun setTextPaint(size: Double, weight: Int, token: String, opacity: Double) {
        paint.style = Paint.Style.FILL
        paint.textSize = size.toFloat()
        paint.typeface = typeface(weight)
        paint.color = color(token)
        paint.alpha = alpha(opacity)
    }

    private fun typeface(weight: Int): Typeface {
        return when {
            weight >= 700 -> Typeface.create("sans-serif", Typeface.BOLD)
            weight >= 600 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            weight >= 500 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            else -> Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    private fun HoldDragResizeFrame.rect(): RectF {
        return RectF(left.toFloat(), top.toFloat(), (left + width).toFloat(), (top + height).toFloat())
    }

    private fun scaledRect(rect: RectF, scale: Double): RectF {
        val dx = rect.width() * (scale - 1.0) / 2.0
        val dy = rect.height() * (scale - 1.0) / 2.0
        return RectF((rect.left - dx).toFloat(), (rect.top - dy).toFloat(), (rect.right + dx).toFloat(), (rect.bottom + dy).toFloat())
    }

    private fun roundRectPath(rect: RectF, radius: Float): Path {
        return Path().apply {
            addRoundRect(rect, radius, radius, Path.Direction.CW)
        }
    }

    private fun toolGlyph(index: Int): String {
        return arrayOf("Aa", "●", "♫", "▶", "▣", "✂", "●")[index]
    }

    private fun fullGlyph(index: Int): String {
        return arrayOf("▣", "T", "☺", "♫", "▱", "≡")[index]
    }

    private fun color(token: String): Int {
        return colors[token] ?: Color.parseColor(token)
    }

    private fun alpha(value: Double): Int {
        return (value.coerceIn(0.0, 1.0) * 255.0).toInt()
    }

    private companion object {
        const val HAND_PATH = "M14 40V9a5 5 0 0 1 10 0v22a4.5 4.5 0 0 1 9 0v3a4.5 4.5 0 0 1 9 0v4a4 4 0 0 1 0.5 0v14c0 10-7 17-17 17h-4c-8 0-12-4-16-10L3 47c-2-3 0-7 4-6 3 0 5 2 7 5"
    }
}
