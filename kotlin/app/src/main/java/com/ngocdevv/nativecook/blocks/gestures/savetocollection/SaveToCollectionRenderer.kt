package com.ngocdevv.nativecook.blocks.gestures.savetocollection

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

class SaveToCollectionRenderer(
    private val assets: Map<Int, Bitmap>,
) {
    private val tokens = SaveToCollectionTokens
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tokens.ink.toArgb()
        textAlign = Paint.Align.CENTER
        textSize = tokens.countFontSize
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private val bookmarkPath = makeBookmarkPath()
    private val streamDrops = saveStreamDrops()

    fun draw(canvas: Canvas, engine: SaveToCollectionEngine) {
        drawCards(canvas, engine)
        drawStream(canvas, engine)
        drawBookmark(canvas, engine)
        drawFlights(canvas, engine)
        drawDraggedCard(canvas, engine)
        drawFinger(canvas, engine)
    }

    private fun drawCards(canvas: Canvas, engine: SaveToCollectionEngine) {
        val visible = engine.visibleIndices()
        visible.forEachIndexed { order, index ->
            val card = tokens.cards[index]
            val rect = engine.cardRect(index)
            val deal = engine.dealProgress(order)
            val phase = index * tokens.cardPhaseStep
            val amplitude = tokens.saveFloatAmp * (1f + card.blur / tokens.cardBlurDepthDivisor)
            val floatY = if (engine.reducedMotion) {
                0f
            } else {
                amplitude * sin((2f * PI.toFloat() * engine.clock.toFloat()) / tokens.saveFloatPeriod + phase)
            }
            val alpha = deal * if (engine.dragCard == index) 1f - tokens.cardDim else 1f
            val pressed = if (engine.pressTarget == index) 1f - 0.03f * engine.pressAmount else 1f
            val scale = if (engine.reducedMotion) 1f else (tokens.cardDealStartScale + (1f - tokens.cardDealStartScale) * deal) * pressed
            val centreX = rect.x + rect.width / 2f
            val centreY = rect.y + rect.height / 2f
            canvas.save()
            canvas.scale(scale, scale, centreX, centreY)
            val drop = if (engine.reducedMotion) 0f else (1f - deal) * tokens.timingDealDrop
            val pad = tokens.softPad * engine.layoutScale
            imagePaint.alpha = (255f * alpha).toInt().coerceIn(0, 255)
            drawBitmapFill(
                canvas = canvas,
                bitmap = requireAsset(card.soft),
                destination = RectF(
                    rect.x - pad,
                    rect.y - pad + drop + floatY,
                    rect.x + rect.width + pad,
                    rect.y + rect.height + pad + drop + floatY,
                ),
                paint = imagePaint,
            )
            canvas.restore()
        }
    }

    private fun drawStream(canvas: Canvas, engine: SaveToCollectionEngine) {
        if (engine.reducedMotion || engine.rushStart < 0.0) {
            return
        }
        val global = clamp01(((engine.clock - engine.rushStart) / (tokens.streamTotal / 1000.0)).toFloat())
        if (global <= 0f || global >= 1f) {
            return
        }
        val rise = engine.riseProgress()
        streamDrops.forEach { drop ->
            val progress = clamp01((global - drop.start) / drop.duration)
            if (progress <= 0f || progress >= 1f) {
                return@forEach
            }
            val eased = cubicInOut(progress)
            val speed = cubicSpeed(progress) / 3f
            val x = engine.bookmarkX + drop.x * engine.layoutScale * (1f - eased * eased * eased)
            val targetY = mix(engine.bookmarkY, engine.bookmarkRiseY, rise)
            val startY = drop.y0 * engine.layoutScale
            val y = mix(startY, targetY, eased)
            val scale = (drop.width / tokens.streamCardWidth) * engine.layoutScale * (1f - (1f - tokens.streamEndScale) * eased * eased * eased)
            val scaleY = 1f + tokens.streamStretch * speed
            val alphaIn = min(progress * tokens.streamAlphaIn, 1f)
            val alphaOut = 1f - max(progress - tokens.streamAlphaOutStart, 0f) / (1f - tokens.streamAlphaOutStart)
            imagePaint.alpha = (255f * alphaIn * alphaOut).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(scale, scale * scaleY)
            val halfWidth = tokens.streamWidth / 2f
            val halfHeight = tokens.streamHeight / 2f
            drawBitmapFill(
                canvas,
                requireAsset(tokens.streaks[drop.art]),
                RectF(-halfWidth, -halfHeight, halfWidth, halfHeight),
                imagePaint,
            )
            canvas.restore()
        }
    }

    private fun drawBookmark(canvas: Canvas, engine: SaveToCollectionEngine) {
        val rise = engine.riseProgress()
        val bounce = engine.bounceProgress()
        val open = engine.dragOpen
        val translateY = (engine.bookmarkRiseY - engine.bookmarkY) * rise
        val centreX = engine.bookmarkX
        val centreY = engine.bookmarkY + translateY
        val width = tokens.bookmarkWidth
        val height = tokens.bookmarkHeight
        val haloAlpha = min(open + max(bounce, 0f), 1f)
        if (haloAlpha > 0f) {
            fillPaint.color = tokens.glow.toArgb()
            fillPaint.alpha = (255f * haloAlpha).toInt()
            canvas.drawCircle(centreX, centreY, tokens.haloSize / 2f * (tokens.ringScaleStart + tokens.haloScaleTravel * open), fillPaint)
        }
        val ring = engine.ringProgress()
        if (ring < 1f) {
            strokePaint.color = tokens.ink.toArgb()
            strokePaint.alpha = (255f * tokens.ringOpacity * (1f - ring)).toInt().coerceIn(0, 255)
            strokePaint.strokeWidth = tokens.ringWidth
            canvas.drawCircle(centreX, centreY, tokens.ringSize / 2f * (tokens.ringScaleStart + tokens.ringScaleTravel * ring), strokePaint)
        }
        val pressed = if (engine.pressTarget == -2) 1f - tokens.bookmarkPressScale * engine.pressAmount else 1f
        val glyphScale = (if (engine.reducedMotion) 1f else 1f + tokens.bookmarkOpenScale * open + tokens.bookmarkBounceScale * bounce) *
            (1f + tokens.bookmarkRiseScale * rise) * pressed
        fillPaint.color = tokens.ink.toArgb()
        fillPaint.alpha = 255
        canvas.save()
        canvas.translate(centreX - width / 2f, centreY - height / 2f)
        canvas.scale(glyphScale, glyphScale, width / 2f, height / 2f)
        canvas.drawPath(bookmarkPath, fillPaint)
        canvas.restore()
        if (!tokens.replicaOnly) {
            drawCountPill(canvas, engine, rise, translateY)
        }
    }

    private fun drawCountPill(
        canvas: Canvas,
        engine: SaveToCollectionEngine,
        rise: Float,
        translateY: Float,
    ) {
        val alpha = max(rise * tokens.countRiseThreshold - 1f, 0f)
        if (alpha <= 0f) {
            return
        }
        val label = "${engine.pileCount} saved"
        textPaint.textSize = tokens.countFontSize
        val pillWidth = textPaint.measureText(label) + tokens.spaceL * 2f
        val pillHeight = tokens.spaceXxl
        val baseTop = engine.bookmarkY - tokens.bookmarkHeight / 2f + tokens.bookmarkHeight + tokens.spaceL
        val top = baseTop + translateY + (1f - rise) * tokens.countRestDrop
        val left = engine.bookmarkX - pillWidth / 2f
        fillPaint.color = tokens.ink.copy(alpha = tokens.pillBackgroundAlpha).toArgb()
        fillPaint.alpha = (255f * alpha).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(RectF(left, top, left + pillWidth, top + pillHeight), tokens.spaceL, tokens.spaceL, fillPaint)
        fillPaint.color = tokens.glassEdge.toArgb()
        fillPaint.alpha = (255f * alpha).toInt().coerceIn(0, 255)
        canvas.drawRect(left + tokens.spaceM, top, left + pillWidth - tokens.spaceM, top + tokens.pillEdgeHeight, fillPaint)
        textPaint.alpha = (255f * alpha).toInt().coerceIn(0, 255)
        val baseline = top + pillHeight / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(label, engine.bookmarkX, baseline, textPaint)
    }

    private fun drawFlights(canvas: Canvas, engine: SaveToCollectionEngine) {
        engine.flights.forEach { flight ->
            val progress = engine.flightProgress(flight)
            for (lag in tokens.saveTrail downTo 1) {
                drawFlightLayer(canvas, engine, flight, progress, lag, smear = false)
            }
            drawFlightLayer(canvas, engine, flight, progress, 0, smear = false)
            drawFlightLayer(canvas, engine, flight, progress, 0, smear = true)
        }
    }

    private fun drawFlightLayer(
        canvas: Canvas,
        engine: SaveToCollectionEngine,
        flight: SaveFlight,
        progress: Float,
        lag: Int,
        smear: Boolean,
    ) {
        val raw = max(progress - lag * tokens.saveTrailStep, 0f)
        val eased = cubicInOut(raw)
        val centre = curvePoint(
            flight.from.x + flight.from.width / 2f,
            flight.from.y + flight.from.height / 2f,
            engine.bookmarkX,
            engine.bookmarkY,
            tokens.saveArc,
            eased,
        )
        val ahead = curvePoint(
            flight.from.x + flight.from.width / 2f,
            flight.from.y + flight.from.height / 2f,
            engine.bookmarkX,
            engine.bookmarkY,
            tokens.saveArc,
            min(eased + tokens.flightLookAhead, 1f),
        )
        val angle = atan2(ahead.second - centre.second, ahead.first - centre.first) * 180f / PI.toFloat() - 90f
        val alpha = if (lag == 0) {
            1f - max(progress - tokens.flightLeadFadeStart, 0f) / tokens.flightLeadFadeSpan
        } else {
            (tokens.flightGhostOpacity / lag.toFloat().pow(tokens.flightGhostExponent)) * min(progress * tokens.flightGhostFadeIn, 1f) * (1f - progress)
        }
        val speed = cubicSpeed(raw) / 3f
        val stretch = 1f + tokens.saveStreak * speed * if (lag == 0) {
            if (smear) tokens.flightSmearStretch else tokens.flightLeadStretch
        } else {
            1f + lag * tokens.flightGhostStretchStep
        }
        val layerAlpha = if (smear) min(speed * tokens.flightSmearOpacity, 1f) * alpha else alpha
        if (layerAlpha <= 0f) {
            return
        }
        val scale = mix(1f, tokens.saveEndScale, eased)
        val radius = mix(tokens.cardRadius, min(flight.from.width, flight.from.height) / 2f, eased * eased)
        imagePaint.alpha = (255f * layerAlpha).toInt().coerceIn(0, 255)
        canvas.save()
        canvas.translate(centre.first, centre.second)
        canvas.rotate(angle)
        canvas.scale(scale, scale * stretch)
        canvas.rotate(-angle)
        drawBitmapCoverRounded(
            canvas = canvas,
            bitmap = requireAsset(tokens.cards[flight.card].art),
            destination = RectF(
                -flight.from.width / 2f,
                -flight.from.height / 2f,
                flight.from.width / 2f,
                flight.from.height / 2f,
            ),
            radius = radius,
            paint = imagePaint,
        )
        canvas.restore()
    }

    private fun drawDraggedCard(canvas: Canvas, engine: SaveToCollectionEngine) {
        if (engine.dragCard < 0) {
            return
        }
        val rect = engine.cardRect(engine.dragCard)
        val card = tokens.cards[engine.dragCard]
        val bitmap = requireAsset(card.art)
        val softBitmap = requireAsset(card.soft)
        val softPad = tokens.softPad * engine.layoutScale
        imagePaint.alpha = (255f * tokens.glowUnderOpacity).toInt()
        canvas.save()
        canvas.translate(engine.dragX, engine.dragY + tokens.glowUnderDrop)
        canvas.rotate(engine.dragTilt)
        canvas.scale(engine.dragScale * 1.05f, engine.dragScale * 1.05f)
        drawBitmapFill(
            canvas,
            softBitmap,
            RectF(
                -rect.width / 2f - softPad,
                -rect.height / 2f - softPad,
                rect.width / 2f + softPad,
                rect.height / 2f + softPad,
            ),
            imagePaint,
        )
        canvas.restore()
        imagePaint.alpha = 255
        canvas.save()
        canvas.translate(engine.dragX, engine.dragY)
        canvas.rotate(engine.dragTilt)
        canvas.scale(engine.dragScale, engine.dragScale)
        drawBitmapCoverRounded(
            canvas,
            bitmap,
            RectF(-rect.width / 2f, -rect.height / 2f, rect.width / 2f, rect.height / 2f),
            tokens.cardRadius,
            imagePaint,
        )
        canvas.restore()
    }

    private fun drawFinger(canvas: Canvas, engine: SaveToCollectionEngine) {
        if (engine.ghostAlpha <= 0f) {
            return
        }
        val radius = tokens.fingerSize / 2f
        val scale = 1f - tokens.fingerPressScale * engine.ghostPress
        fillPaint.color = tokens.finger.toArgb()
        fillPaint.alpha = (255f * engine.ghostAlpha).toInt().coerceIn(0, 255)
        strokePaint.color = tokens.fingerEdge.toArgb()
        strokePaint.alpha = (255f * engine.ghostAlpha).toInt().coerceIn(0, 255)
        strokePaint.strokeWidth = 1f
        canvas.save()
        canvas.scale(scale, scale, engine.ghostX, engine.ghostY)
        canvas.drawCircle(engine.ghostX, engine.ghostY, radius, fillPaint)
        canvas.drawCircle(engine.ghostX, engine.ghostY, radius - 0.5f, strokePaint)
        canvas.restore()
    }

    private fun drawBitmapFill(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: RectF,
        paint: Paint,
    ) {
        canvas.drawBitmap(bitmap, null, destination, paint)
    }

    private fun drawBitmapCoverRounded(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: RectF,
        radius: Float,
        paint: Paint,
    ) {
        val sourceRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val destinationRatio = destination.width() / destination.height()
        val source = if (sourceRatio > destinationRatio) {
            val wantedWidth = bitmap.height * destinationRatio
            val left = (bitmap.width - wantedWidth) / 2f
            Rect(left.toInt(), 0, (left + wantedWidth).toInt(), bitmap.height)
        } else {
            val wantedHeight = bitmap.width / destinationRatio
            val top = (bitmap.height - wantedHeight) / 2f
            Rect(0, top.toInt(), bitmap.width, (top + wantedHeight).toInt())
        }
        canvas.save()
        val clip = Path().apply {
            addRoundRect(destination, radius, radius, Path.Direction.CW)
        }
        canvas.clipPath(clip)
        canvas.drawBitmap(bitmap, source, destination, paint)
        canvas.restore()
    }

    private fun curvePoint(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        arc: Float,
        progress: Float,
    ): Pair<Float, Float> {
        val middleX = (startX + endX) / 2f
        val middleY = (startY + endY) / 2f
        val deltaX = endX - startX
        val deltaY = endY - startY
        val side = if (startX < endX) -1f else 1f
        val controlX = middleX + side * abs(deltaY) * arc
        val controlY = middleY - abs(deltaX) * arc * 0.5f
        val inverse = 1f - progress
        val x = inverse * inverse * startX + 2f * inverse * progress * controlX + progress * progress * endX
        val y = inverse * inverse * startY + 2f * inverse * progress * controlY + progress * progress * endY
        return Pair(x, y)
    }

    private fun makeBookmarkPath(): Path {
        val width = tokens.bookmarkWidth
        val height = tokens.bookmarkHeight
        val radius = width * tokens.bookmarkCornerFraction
        val notch = height * tokens.bookmarkNotchFraction
        return Path().apply {
            moveTo(radius, 0f)
            lineTo(width - radius, 0f)
            quadTo(width, 0f, width, radius)
            lineTo(width, height - tokens.bookmarkBottomInset)
            quadTo(width, height, width - tokens.bookmarkShoulder, height - tokens.bookmarkShoulderInset)
            lineTo(width / 2f, height - notch)
            lineTo(tokens.bookmarkShoulder, height - tokens.bookmarkShoulderInset)
            quadTo(0f, height, 0f, height - tokens.bookmarkBottomInset)
            lineTo(0f, radius)
            quadTo(0f, 0f, radius, 0f)
            close()
        }
    }

    private fun requireAsset(resource: Int): Bitmap {
        return requireNotNull(assets[resource]) {
            "Missing SaveToCollection drawable $resource"
        }
    }
}
