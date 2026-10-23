package com.ngocdevv.nativecook.blocks.gestures.giftslotdrop

import android.graphics.Bitmap
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class GiftSlotDropAssets(
    val cards: Map<String, Bitmap>,
    val marks: Map<String, Bitmap>,
    val offer: Bitmap,
    val giftBox: Bitmap,
)

private data class GiftSlotDropCardPose(
    val x: Float,
    val y: Float,
    val scale: Float,
    val turn: Float,
    val opacity: Float,
    val lean: Float,
    val roll: Float,
)

internal class GiftSlotDropRenderer(
    private val assets: GiftSlotDropAssets,
) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val boldText = Paint(text).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private val camera = Camera()
    private val matrix = Matrix()
    private val cardRect = RectF(
        -Tokens.LAYOUT.cardW / 2f,
        -Tokens.LAYOUT.cardH / 2f,
        Tokens.LAYOUT.cardW / 2f,
        Tokens.LAYOUT.cardH / 2f,
    )
    private val offerRect = RectF(
        -Tokens.LAYOUT.offerW / 2f,
        -Tokens.LAYOUT.offerH / 2f,
        Tokens.LAYOUT.offerW / 2f,
        Tokens.LAYOUT.offerH / 2f,
    )

    fun draw(canvas: Canvas, simulation: GiftSlotDropSimulation, width: Float, height: Float, reducedMotion: Boolean) {
        drawStage(canvas, simulation, width, height, reducedMotion)
        drawPocket(canvas, simulation, width, reducedMotion)
        drawChevrons(canvas, simulation, width, reducedMotion)
        drawPlaceholder(canvas, simulation, width)
        val save = canvas.save()
        canvas.clipRect(0f, 0f, width, Tokens.LAYOUT.slotY)
        Tokens.GIFTS.indices.forEach { index ->
            drawCarouselCard(canvas, simulation, width, index, reducedMotion)
        }
        drawOffer(canvas, simulation, width, reducedMotion)
        canvas.restoreToCount(save)
        drawConfetti(canvas, simulation)
    }

    private fun drawStage(
        canvas: Canvas,
        simulation: GiftSlotDropSimulation,
        width: Float,
        height: Float,
        reducedMotion: Boolean,
    ) {
        fill.alpha = 255
        fill.shader = RadialGradient(
            width / 2f,
            height * 0.08f,
            height * 0.85f,
            intArrayOf(colour(Tokens.COLORS.stage), colour(Tokens.COLORS.stageEdge)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width, height, fill)
        fill.shader = null

        val warm = Tokens.COLORS.stageWarm * giftSlotDropClamp(simulation.pullY / Tokens.DRAG.full)
        fill.color = colour(Tokens.COLORS.stageWarmInk)
        fill.alpha = alpha(warm)
        canvas.drawRect(0f, 0f, width, Tokens.LAYOUT.floorY, fill)

        val glow = interpolatedGiftColour(simulation.offset)
        fill.alpha = 255
        fill.shader = RadialGradient(
            width / 2f,
            Tokens.DRAW.stageGlowTop + Tokens.DRAW.stageGlowHeight / 2f,
            Tokens.DRAW.stageGlowWidth / 2f,
            intArrayOf(withAlpha(glow, Tokens.COLORS.hueTint), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawOval(
            width / 2f - Tokens.DRAW.stageGlowWidth / 2f,
            Tokens.DRAW.stageGlowTop,
            width / 2f + Tokens.DRAW.stageGlowWidth / 2f,
            Tokens.DRAW.stageGlowTop + Tokens.DRAW.stageGlowHeight,
            fill,
        )

        fill.alpha = 255
        fill.shader = LinearGradient(
            0f,
            Tokens.LAYOUT.floorY,
            0f,
            height,
            colour(Tokens.COLORS.floor),
            colour(Tokens.COLORS.floorLow),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, Tokens.LAYOUT.floorY, width, height, fill)
        fill.shader = null
        fill.color = withAlpha(Color.rgb(255, 240, 190), 0.08f)
        fill.alpha = 255
        canvas.drawRect(0f, Tokens.LAYOUT.floorY, width, Tokens.LAYOUT.floorY + 1f, fill)
    }

    private fun drawPocket(canvas: Canvas, simulation: GiftSlotDropSimulation, width: Float, reducedMotion: Boolean) {
        val centre = width / 2f
        val pocket = RectF(
            centre - Tokens.LAYOUT.pocketW / 2f,
            Tokens.LAYOUT.pocketTop,
            centre + Tokens.LAYOUT.pocketW / 2f,
            Tokens.LAYOUT.floorY + Tokens.LAYOUT.pocketDepth,
        )
        fill.shader = LinearGradient(
            0f,
            Tokens.LAYOUT.pocketTop,
            0f,
            Tokens.LAYOUT.floorY,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, colour(Tokens.COLORS.pocketLow)),
            floatArrayOf(0f, 0.35f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(pocket, Tokens.DRAW.pocketRadius, Tokens.DRAW.pocketRadius, fill)
        fill.shader = null

        val glow = slotGlow(simulation, reducedMotion)
        val scaleX = 0.9f + 0.12f * min(1.2f, glow)
        val save = canvas.save()
        canvas.translate(centre, Tokens.LAYOUT.slotY)
        canvas.scale(scaleX, 1f)
        val halo = RectF(
            -Tokens.LAYOUT.slotW / 2f - Tokens.DRAW.slotHaloInsetX,
            -Tokens.LAYOUT.slotH / 2f - Tokens.DRAW.slotHaloInsetY + Tokens.LAYOUT.glowDrop,
            Tokens.LAYOUT.slotW / 2f + Tokens.DRAW.slotHaloInsetX,
            Tokens.LAYOUT.slotH / 2f - Tokens.DRAW.slotHaloInsetY + Tokens.LAYOUT.glowDrop + Tokens.DRAW.slotHaloExtraH,
        )
        fill.shader = RadialGradient(
            0f,
            Tokens.LAYOUT.glowDrop,
            Tokens.LAYOUT.slotW * 0.7f,
            intArrayOf(withAlpha(colour(Tokens.COLORS.yellowDeep), Tokens.DRAW.slotHaloAlpha * min(1f, glow)), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawOval(halo, fill)

        val clip = RectF(
            -Tokens.LAYOUT.pocketW / 2f,
            Tokens.LAYOUT.pocketTop - Tokens.LAYOUT.slotY,
            Tokens.LAYOUT.pocketW / 2f,
            Tokens.LAYOUT.floorY + Tokens.LAYOUT.pocketDepth - Tokens.LAYOUT.slotY,
        )
        canvas.clipRect(clip)
        fill.shader = RadialGradient(
            0f,
            16f,
            Tokens.LAYOUT.pocketW * 0.45f,
            intArrayOf(
                withAlpha(colour(Tokens.COLORS.glowCore), Tokens.DRAW.slotGlowAlpha * min(1f, glow)),
                withAlpha(colour(Tokens.COLORS.yellowDeep), Tokens.DRAW.slotGlowAlpha * min(1f, glow)),
                Color.TRANSPARENT,
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawOval(
            -Tokens.LAYOUT.pocketW * 0.6f,
            Tokens.LAYOUT.glowTop,
            Tokens.LAYOUT.pocketW * 0.6f,
            Tokens.LAYOUT.glowTop + Tokens.LAYOUT.glowH,
            fill,
        )
        fill.shader = null
        canvas.restoreToCount(save)
    }

    private fun drawChevrons(canvas: Canvas, simulation: GiftSlotDropSimulation, width: Float, reducedMotion: Boolean) {
        if (simulation.releasedAt >= 0f) {
            return
        }
        val base = Tokens.LAYOUT.chevronY + if (reducedMotion) 0f else {
            Tokens.chevronBobPt * sin(simulation.clockMs / Tokens.chevronBobMs * PI.toFloat() * 2f)
        }
        val progress = giftSlotDropClamp(simulation.pullY / Tokens.DRAG.full)
        val halfHeight = Tokens.LAYOUT.cardH *
            (1f - (1f - Tokens.DRAG.minScale) * progress) *
            cos(progress * Tokens.DRAG.leanDeg * PI.toFloat() / 180f) / 2f
        val top = Tokens.LAYOUT.cardY + simulation.pullY - halfHeight
        val bottom = Tokens.LAYOUT.cardY + simulation.pullY + halfHeight
        var y = base
        if (bottom > base - Tokens.chevronGap) {
            if (top < base - Tokens.chevronGap) {
                return
            }
            y = top - Tokens.chevronGap
        }
        val path = Path().apply {
            moveTo(width / 2f - Tokens.DRAW.chevronHalfW, y + Tokens.DRAW.chevronTop)
            lineTo(width / 2f, y + Tokens.DRAW.chevronMid)
            lineTo(width / 2f + Tokens.DRAW.chevronHalfW, y + Tokens.DRAW.chevronTop)
            moveTo(width / 2f - Tokens.DRAW.chevronHalfW, y + Tokens.DRAW.chevronSecondTop)
            lineTo(width / 2f, y + Tokens.DRAW.chevronBottom)
            lineTo(width / 2f + Tokens.DRAW.chevronHalfW, y + Tokens.DRAW.chevronSecondTop)
        }
        stroke.color = colour(Tokens.COLORS.mute)
        stroke.strokeWidth = Tokens.DRAW.chevronStroke
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(path, stroke)
    }

    private fun drawPlaceholder(canvas: Canvas, simulation: GiftSlotDropSimulation, width: Float) {
        val opacity = if (simulation.releasedAt >= 0f) {
            if (simulation.phase == GiftSlotDropPhase.Inserting || simulation.phase == GiftSlotDropPhase.Activating) 1f else 0f
        } else {
            giftSlotDropClamp(simulation.pullY / 40f)
        }
        if (opacity <= 0f) {
            return
        }
        stroke.color = colour(Tokens.COLORS.ink)
        stroke.alpha = alpha(0.42f * opacity)
        stroke.strokeWidth = Tokens.DRAW.placeholderStroke
        val inset = Tokens.DRAW.frameOuterInset
        val rect = RectF(
            width / 2f - Tokens.LAYOUT.cardW / 2f - inset,
            Tokens.LAYOUT.cardY - Tokens.LAYOUT.cardH / 2f - inset,
            width / 2f + Tokens.LAYOUT.cardW / 2f + inset,
            Tokens.LAYOUT.cardY + Tokens.LAYOUT.cardH / 2f + inset,
        )
        canvas.drawRoundRect(rect, Tokens.LAYOUT.cardR + 6f, Tokens.LAYOUT.cardR + 6f, stroke)
        stroke.alpha = 255
    }

    private fun drawCarouselCard(
        canvas: Canvas,
        simulation: GiftSlotDropSimulation,
        width: Float,
        index: Int,
        reducedMotion: Boolean,
    ) {
        val pose = cardPose(simulation, width, index, reducedMotion)
        if (pose.opacity <= 0f) {
            return
        }
        val save = canvas.save()
        canvas.translate(pose.x, pose.y)
        canvas.rotate(pose.roll)
        canvas.scale(pose.scale, pose.scale)
        camera.save()
        camera.rotateX(pose.lean)
        camera.rotateY(pose.turn)
        camera.getMatrix(matrix)
        camera.restore()
        canvas.concat(matrix)
        val frameAlpha = if (simulation.releasedAt >= 0f ||
            simulation.phase !in setOf(GiftSlotDropPhase.Choosing, GiftSlotDropPhase.Pulling, GiftSlotDropPhase.Swiping)
        ) {
            0f
        } else {
            giftSlotDropClamp(1f - abs(index - simulation.offset) * 2f) *
                (1f - giftSlotDropClamp(simulation.pullY / Tokens.DRAW.frameFadePull))
        }
        drawSelectionFrame(canvas, frameAlpha * pose.opacity)
        drawCardArt(canvas, Tokens.GIFTS[index], assets.cards.getValue(Tokens.GIFTS[index].id), pose.opacity)
        if (!reducedMotion) {
            drawSpecular(canvas, simulation, index, pose.opacity)
        }
        if (abs(index - simulation.offset) < 0.5f) {
            drawPocketLightOnCard(canvas, simulation, pose.opacity)
        }
        canvas.restoreToCount(save)
    }

    private fun cardPose(
        simulation: GiftSlotDropSimulation,
        width: Float,
        index: Int,
        reducedMotion: Boolean,
    ): GiftSlotDropCardPose {
        val relative = index - simulation.offset
        val selected = index == simulation.selected
        var x = width / 2f + relative * Tokens.LAYOUT.spacing
        var y = Tokens.LAYOUT.cardY
        var scale = 1f - (1f - Tokens.LAYOUT.sideScale) * min(1f, abs(relative))
        val turn = (-relative).coerceIn(-1f, 1f) * Tokens.LAYOUT.turnDeg
        var opacity = 1f
        var lean = 0f
        var roll = 0f
        if (selected && simulation.phase == GiftSlotDropPhase.Choosing && simulation.releasedAt < 0f && !reducedMotion) {
            y += Tokens.ambientFloatPt * sin(2f * PI.toFloat() * simulation.clockMs / Tokens.ambientFloatMs)
        }
        if (selected && simulation.phase in setOf(GiftSlotDropPhase.Pulling, GiftSlotDropPhase.Choosing, GiftSlotDropPhase.Swiping) &&
            simulation.releasedAt < 0f
        ) {
            x += simulation.pullX
            y += simulation.pullY
            scale *= (1f - (1f - Tokens.DRAG.minScale) * giftSlotDropClamp(simulation.pullY / Tokens.DRAG.full)) * simulation.lift
            lean = giftSlotDropClamp(simulation.pullY / Tokens.DRAG.full) * Tokens.DRAG.leanDeg
            roll = if (reducedMotion) 0f else {
                (simulation.pullX * Tokens.ambientRollPerPt).coerceIn(-Tokens.ambientRollMax, Tokens.ambientRollMax)
            }
        }
        if (simulation.releasedAt >= 0f) {
            val elapsed = simulation.elapsedSinceRelease()
            if (selected) {
                val from = simulation.releasedFrom
                if (reducedMotion) {
                    x = width / 2f + from.x
                    y = Tokens.LAYOUT.cardY + from.y
                    scale = from.scale
                    opacity = 1f - giftSlotDropClamp(elapsed / Tokens.reducedMs)
                } else {
                    val centre = 1f - (1f - giftSlotDropClamp(elapsed / Tokens.insertCentreMs)).pow(3)
                    lean = Tokens.DRAG.leanDeg
                    val tall = Tokens.LAYOUT.cardH * Tokens.DRAG.minScale * cos(Tokens.DRAG.leanDeg * PI.toFloat() / 180f)
                    val above = Tokens.LAYOUT.slotY - tall / 2f
                    x = width / 2f + from.x * (1f - centre)
                    y = Tokens.LAYOUT.cardY + from.y + (above - Tokens.LAYOUT.cardY - from.y) * centre
                    scale = from.scale + (Tokens.DRAG.minScale - from.scale) * centre
                    val sink = giftSlotDropClamp((elapsed - Tokens.insertCentreMs) / Tokens.insertSinkMs)
                    y += tall * 1.1f * giftSlotDropEase(sink)
                    if (elapsed > Tokens.insertCentreMs + Tokens.insertSinkMs) {
                        opacity = 0f
                    }
                }
            } else {
                opacity = 1f - giftSlotDropClamp(elapsed / 250f)
            }
            if (simulation.closeAt >= 0f) {
                val delay = if (reducedMotion) 0f else Tokens.closeMs.toFloat()
                val duration = if (reducedMotion) Tokens.reducedMs.toFloat() else Tokens.closeReturnMs.toFloat()
                val returning = giftSlotDropClamp((simulation.clockMs - simulation.closeAt - delay) / duration)
                x = width / 2f + relative * Tokens.LAYOUT.spacing
                y = Tokens.LAYOUT.cardY
                scale = (1f - (1f - Tokens.LAYOUT.sideScale) * min(1f, abs(relative))) * (0.94f + 0.06f * returning)
                opacity = returning
            }
        }
        return GiftSlotDropCardPose(x, y, scale, turn, opacity, lean, roll)
    }

    private fun drawSelectionFrame(canvas: Canvas, opacity: Float) {
        if (opacity <= 0f) {
            return
        }
        stroke.color = colour(Tokens.COLORS.yellow)
        stroke.alpha = alpha(Tokens.DRAW.frameOuterAlpha * opacity)
        stroke.strokeWidth = Tokens.DRAW.frameOuterStroke
        val outer = expanded(cardRect, Tokens.DRAW.frameOuterInset)
        canvas.drawRoundRect(outer, Tokens.LAYOUT.cardR + 6f, Tokens.LAYOUT.cardR + 6f, stroke)
        stroke.alpha = alpha(opacity)
        stroke.strokeWidth = Tokens.DRAW.frameInnerStroke
        val inner = expanded(cardRect, Tokens.DRAW.frameInnerInset)
        canvas.drawRoundRect(inner, Tokens.LAYOUT.cardR + 5f, Tokens.LAYOUT.cardR + 5f, stroke)
        stroke.alpha = 255
    }

    private fun drawCardArt(canvas: Canvas, gift: Tokens.Gift, bitmap: Bitmap, opacity: Float) {
        val save = canvas.save()
        val clip = Path().apply { addRoundRect(cardRect, Tokens.LAYOUT.cardR, Tokens.LAYOUT.cardR, Path.Direction.CW) }
        canvas.clipPath(clip)
        fill.alpha = alpha(opacity)
        canvas.drawBitmap(bitmap, null, cardRect, fill)
        fill.color = colour(Tokens.COLORS.cardShadeInk)
        fill.alpha = alpha(Tokens.COLORS.cardShade[0] * opacity)
        canvas.drawRect(cardRect, fill)
        fill.color = colour(gift.glow)
        fill.alpha = alpha(Tokens.COLORS.cardShade[1] * opacity)
        canvas.drawRect(cardRect, fill)
        canvas.restoreToCount(save)
        drawWordmark(canvas, gift, -Tokens.LAYOUT.cardH / 2f + Tokens.DRAW.cardWordmarkY, 1f, opacity)
        boldText.color = colour(gift.ink)
        boldText.alpha = alpha(opacity)
        boldText.textSize = Tokens.TYPE.cardHeadline
        canvas.drawText(gift.headline, 0f, -Tokens.LAYOUT.cardH / 2f + Tokens.DRAW.cardHeadlineY, boldText)
        boldText.textSize = Tokens.TYPE.cardLine
        canvas.drawText(gift.line, 0f, -Tokens.LAYOUT.cardH / 2f + Tokens.DRAW.cardLineY, boldText)
        canvas.drawText(gift.cap, 0f, -Tokens.LAYOUT.cardH / 2f + Tokens.DRAW.cardCapY, boldText)
    }

    private fun drawWordmark(canvas: Canvas, gift: Tokens.Gift, baseline: Float, scale: Float, opacity: Float) {
        boldText.textSize = Tokens.TYPE.cardMark * scale
        boldText.color = colour(gift.ink)
        boldText.alpha = alpha(opacity)
        val mark = assets.marks[gift.id]
        val wordWidth = boldText.measureText(gift.wordmark)
        val markSize = 16f * scale
        if (gift.id == "parcels") {
            canvas.drawText(gift.wordmark, 0f, baseline, boldText)
            if (mark != null) {
                val rect = RectF(-wordWidth * 0.45f, baseline + 1f, wordWidth * 0.45f, baseline + 1f + markSize * 0.9f)
                fill.alpha = alpha(opacity)
                canvas.drawBitmap(mark, null, rect, fill)
            }
            return
        }
        val gap = if (mark == null) 0f else 5f * scale
        val total = wordWidth + if (mark == null) 0f else markSize + gap
        if (mark != null) {
            val left = -total / 2f
            val rect = RectF(left, baseline - markSize + 3f * scale, left + markSize, baseline + 3f * scale)
            fill.alpha = alpha(opacity)
            canvas.drawBitmap(mark, null, rect, fill)
        }
        boldText.textAlign = Paint.Align.LEFT
        canvas.drawText(gift.wordmark, -total / 2f + if (mark == null) 0f else markSize + gap, baseline, boldText)
        boldText.textAlign = Paint.Align.CENTER
    }

    private fun drawSpecular(canvas: Canvas, simulation: GiftSlotDropSimulation, index: Int, opacity: Float) {
        val save = canvas.save()
        val clip = Path().apply { addRoundRect(cardRect, Tokens.LAYOUT.cardR, Tokens.LAYOUT.cardR, Path.Direction.CW) }
        canvas.clipPath(clip)
        val drift = 0.25f * sin(2f * PI.toFloat() * simulation.clockMs / Tokens.ambientBandMs + index)
        val position = ((index - simulation.offset) * -0.9f + simulation.pullX / 120f + drift) * Tokens.LAYOUT.cardW
        fill.shader = LinearGradient(
            position - Tokens.LAYOUT.cardW * 0.25f,
            -Tokens.LAYOUT.cardH / 2f,
            position + Tokens.LAYOUT.cardW * 0.15f,
            Tokens.LAYOUT.cardH / 2f,
            intArrayOf(Color.TRANSPARENT, withAlpha(Color.WHITE, Tokens.ambientBand * opacity), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(cardRect, fill)
        fill.shader = null
        canvas.restoreToCount(save)
    }

    private fun drawPocketLightOnCard(canvas: Canvas, simulation: GiftSlotDropSimulation, opacity: Float) {
        val light = giftSlotDropClamp((simulation.pullY - 50f) / 100f)
        if (light <= 0f) {
            return
        }
        val save = canvas.save()
        val clip = Path().apply { addRoundRect(cardRect, Tokens.LAYOUT.cardR, Tokens.LAYOUT.cardR, Path.Direction.CW) }
        canvas.clipPath(clip)
        fill.color = Color.WHITE
        fill.alpha = alpha(light * Tokens.COLORS.cardLift * opacity)
        canvas.drawRect(cardRect, fill)
        fill.shader = LinearGradient(
            0f,
            -Tokens.LAYOUT.cardH * 0.6f,
            0f,
            Tokens.LAYOUT.cardH / 2f,
            Color.TRANSPARENT,
            withAlpha(Color.rgb(255, 224, 60), light * opacity),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(cardRect, fill)
        fill.shader = null
        canvas.restoreToCount(save)
    }

    private fun drawOffer(canvas: Canvas, simulation: GiftSlotDropSimulation, width: Float, reducedMotion: Boolean) {
        if (simulation.releasedAt < 0f) {
            return
        }
        val milestones = giftSlotDropMilestones(reducedMotion)
        val elapsed = simulation.elapsedSinceRelease() - milestones.printStart
        if (elapsed < 0f) {
            return
        }
        var y: Float
        var scale: Float
        var opacity = 1f
        var roll: Float
        if (reducedMotion) {
            y = Tokens.LAYOUT.offerY
            scale = 1f
            opacity = giftSlotDropClamp(elapsed / Tokens.reducedMs)
            roll = 1f
        } else {
            var fed = 0f
            repeat(Tokens.printFeeds) { feed ->
                val at = feed * (Tokens.printFeedMs + Tokens.printFeedGap)
                fed += Tokens.printFeedShare * giftSlotDropBackOut((elapsed - at) / Tokens.printFeedMs)
            }
            val feedEnd = Tokens.printFeeds * (Tokens.printFeedMs + Tokens.printFeedGap)
            val fedY = Tokens.LAYOUT.slotY - fed * Tokens.LAYOUT.offerH * Tokens.printStartScale +
                Tokens.LAYOUT.offerH * Tokens.printStartScale / 2f
            val rise = giftSlotDropSpring((elapsed - feedEnd) / 1000f, 170f, 20f)
            y = fedY + (Tokens.LAYOUT.offerY - fedY) * rise
            scale = Tokens.printStartScale + (1f - Tokens.printStartScale) * rise
            val progress = giftSlotDropClamp((elapsed - feedEnd + 100f) / Tokens.printRollMs)
            roll = 1f - (1f - progress).pow(3)
        }
        if (simulation.closeAt >= 0f) {
            val close = giftSlotDropClamp(
                (simulation.clockMs - simulation.closeAt) / if (reducedMotion) Tokens.reducedMs else Tokens.closeMs,
            )
            opacity *= 1f - close
            if (!reducedMotion) {
                scale *= 1f - Tokens.LAYOUT.closeScale * close * close
                y -= Tokens.LAYOUT.closeRise * close * close
            }
        }
        val save = canvas.save()
        canvas.translate(width / 2f, y)
        canvas.scale(scale, scale)
        drawOfferArt(canvas, Tokens.GIFTS[simulation.selected], roll, opacity)
        canvas.restoreToCount(save)
    }

    private fun drawOfferArt(canvas: Canvas, gift: Tokens.Gift, roll: Float, opacity: Float) {
        val save = canvas.save()
        val clip = Path().apply { addRoundRect(offerRect, Tokens.LAYOUT.offerRadius, Tokens.LAYOUT.offerRadius, Path.Direction.CW) }
        canvas.clipPath(clip)
        fill.alpha = alpha(opacity)
        val bitmap = if (gift.id == "parcels") assets.offer else assets.cards.getValue(gift.id)
        val matrix = ColorMatrix().apply { setSaturation(0.75f) }
        fill.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(bitmap, null, offerRect, fill)
        fill.colorFilter = null
        fill.color = Color.rgb(214, 200, 140)
        fill.alpha = alpha(0.28f * opacity)
        canvas.drawRect(offerRect, fill)
        canvas.restoreToCount(save)

        val offerGift = if (gift.id == "parcels") gift.copy(ink = "#FFFFFF") else gift
        drawWordmark(
            canvas,
            offerGift,
            -Tokens.LAYOUT.offerH / 2f + Tokens.DRAW.offerWordmarkY,
            Tokens.DRAW.offerWordmarkScale,
            opacity,
        )
        val ink = colour(offerGift.ink)
        boldText.color = ink
        boldText.alpha = alpha(opacity)
        boldText.textSize = Tokens.TYPE.offerHeadline
        val percent = (gift.headline.removeSuffix("%").toInt() * roll).roundToInt()
        canvas.drawText("$percent%", 0f, Tokens.DRAW.offerPercentY, boldText)
        text.color = ink
        text.alpha = alpha(opacity)
        text.textSize = Tokens.TYPE.offerLine
        canvas.drawText(gift.line, 0f, Tokens.DRAW.offerLineY, text)
        text.textSize = Tokens.TYPE.offerSmall
        val words = gift.use.split(' ')
        val half = (words.size + 1) / 2
        canvas.drawText(words.take(half).joinToString(" "), 0f, Tokens.DRAW.offerUseFirstY, text)
        canvas.drawText(words.drop(half).joinToString(" "), 0f, Tokens.DRAW.offerUseSecondY, text)
        canvas.drawText(gift.expires, 0f, Tokens.LAYOUT.offerH / 2f - Tokens.DRAW.offerExpiryInset, text)
    }

    private fun drawConfetti(canvas: Canvas, simulation: GiftSlotDropSimulation) {
        val colours = intArrayOf(
            colour(Tokens.COLORS.yellow),
            Color.WHITE,
            colour(Tokens.GIFTS[simulation.selected].glow),
        )
        simulation.confetti.forEach { piece ->
            if (piece.life <= 0f) {
                return@forEach
            }
            val fade = min(1f, piece.life / 0.4f)
            val halfWidth = Tokens.DRAW.confettiHalfW * fade
            val halfHeight = Tokens.DRAW.confettiHalfH * fade * abs(cos(piece.angle * 1.7f)) + Tokens.DRAW.confettiMinHalfH
            val cosine = cos(piece.angle)
            val sine = sin(piece.angle)
            val path = Path().apply {
                moveTo(piece.x + cosine * halfWidth - sine * halfHeight, piece.y + sine * halfWidth + cosine * halfHeight)
                lineTo(piece.x - cosine * halfWidth - sine * halfHeight, piece.y - sine * halfWidth + cosine * halfHeight)
                lineTo(piece.x - cosine * halfWidth + sine * halfHeight, piece.y - sine * halfWidth - cosine * halfHeight)
                lineTo(piece.x + cosine * halfWidth + sine * halfHeight, piece.y + sine * halfWidth - cosine * halfHeight)
                close()
            }
            fill.color = colours[piece.colour.coerceIn(0, colours.lastIndex)]
            fill.alpha = 255
            canvas.drawPath(path, fill)
        }
    }

    private fun slotGlow(simulation: GiftSlotDropSimulation, reducedMotion: Boolean): Float {
        if (simulation.releasedAt < 0f) {
            val breath = if (reducedMotion) 0f else {
                0.08f * sin(2f * PI.toFloat() * simulation.clockMs / Tokens.ambientSlotBreathMs)
            }
            return 0.5f + 0.5f * giftSlotDropClamp(simulation.pullY / Tokens.DRAG.commit) + breath
        }
        val elapsed = simulation.elapsedSinceRelease()
        val milestones = giftSlotDropMilestones(reducedMotion)
        if (elapsed < milestones.sunk) {
            return 1f
        }
        if (elapsed < milestones.sunk + Tokens.insertFlashMs) {
            return 1f + 0.6f * (1f - (elapsed - milestones.sunk) / Tokens.insertFlashMs)
        }
        if (elapsed < milestones.printStart) {
            return 0.85f + 0.25f * sin((elapsed - milestones.sunk) / Tokens.activateDotMs * PI.toFloat())
        }
        if (simulation.closeAt >= 0f) {
            return 0.5f * giftSlotDropClamp((simulation.clockMs - simulation.closeAt) / Tokens.closeReturnMs)
        }
        return 1f - 0.9f * giftSlotDropClamp((elapsed - milestones.printed) / 600f)
    }
}

private fun colour(value: String): Int {
    return Color.parseColor(value)
}

private fun withAlpha(value: Int, opacity: Float): Int {
    return Color.argb(alpha(opacity), Color.red(value), Color.green(value), Color.blue(value))
}

private fun alpha(opacity: Float): Int {
    return (opacity.coerceIn(0f, 1f) * 255f).roundToInt()
}

private fun interpolatedGiftColour(offset: Float): Int {
    val position = offset.coerceIn(0f, Tokens.GIFTS.lastIndex.toFloat())
    val lower = position.toInt()
    val upper = min(Tokens.GIFTS.lastIndex, lower + 1)
    val progress = position - lower
    val start = colour(Tokens.GIFTS[lower].glow)
    val end = colour(Tokens.GIFTS[upper].glow)
    return Color.rgb(
        (Color.red(start) + (Color.red(end) - Color.red(start)) * progress).roundToInt(),
        (Color.green(start) + (Color.green(end) - Color.green(start)) * progress).roundToInt(),
        (Color.blue(start) + (Color.blue(end) - Color.blue(start)) * progress).roundToInt(),
    )
}

private fun expanded(rect: RectF, amount: Float): RectF {
    return RectF(rect.left - amount, rect.top - amount, rect.right + amount, rect.bottom + amount)
}
