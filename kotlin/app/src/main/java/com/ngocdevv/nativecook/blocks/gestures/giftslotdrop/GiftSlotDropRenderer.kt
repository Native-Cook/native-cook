package com.ngocdevv.nativecook.blocks.gestures.giftslotdrop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal class GiftSlotDropAssets(context: Context) {
    val regular: Typeface = Typeface.createFromAsset(context.assets, "giftslotdrop/giftslotdrop_Inter-Regular.ttf")
    val medium: Typeface = Typeface.createFromAsset(context.assets, "giftslotdrop/giftslotdrop_Inter-Medium.ttf")
    val semibold: Typeface = Typeface.createFromAsset(context.assets, "giftslotdrop/giftslotdrop_Inter-SemiBold.ttf")
    val mono: Typeface = Typeface.createFromAsset(context.assets, "giftslotdrop/giftslotdrop_JetBrainsMono-Medium.ttf")
}

internal class GiftSlotDropRenderer(private val assets: GiftSlotDropAssets) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letters = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val camera = Camera()
    private val matrix = Matrix()
    private val paths = GiftSlotDropMark.entries.associateWith(::makeMarkPath)
    private var background: Bitmap? = null
    private var frosted: Bitmap? = null
    private var backdropWidth = 0
    private var backdropHeight = 0
    private var backdropDensity = 0f
    private var runtimeShader: RuntimeShader? = null

    fun draw(canvas: Canvas, simulation: GiftSlotDropSimulation, reducedMotion: Boolean, density: Float) {
        val layout = simulation.layout
        ensureBackdrop(layout, density)
        background?.let { canvas.drawBitmap(it, null, RectF(0f, 0f, layout.width, layout.height), bitmapPaint) }
        drawScene(canvas, simulation, reducedMotion, density)
    }

    private fun ensureBackdrop(layout: GiftSlotDropLayout, density: Float) {
        val width = max(1, (layout.width * density).roundToInt())
        val height = max(1, (layout.height * density).roundToInt())
        if (width == backdropWidth && height == backdropHeight && density == backdropDensity) return
        backdropWidth = width
        backdropHeight = height
        backdropDensity = density

        background?.recycle()
        frosted?.recycle()
        background = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val target = Canvas(bitmap)
            target.scale(density, density)
            drawStaticPage(target, layout)
        }

        val frostWidth = max(1, width / 3)
        val frostHeight = max(1, height / 3)
        frosted = Bitmap.createBitmap(frostWidth, frostHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            val target = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            target.drawBitmap(requireNotNull(background), null, Rect(0, 0, frostWidth, frostHeight), paint)
            blurBitmap(bitmap, max(1, (8f * density).roundToInt()))
        }
        runtimeShader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(GLASS_SHADER) else null
    }

    private fun drawStaticPage(canvas: Canvas, layout: GiftSlotDropLayout) {
        reset(fill)
        fill.shader = LinearGradient(
            0f,
            0f,
            0f,
            layout.height,
            intArrayOf(color("#100D0A"), color("#0C0A08"), color("#120E0A")),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, layout.width, layout.height, fill)
        radial(
            canvas,
            layout.centerX,
            layout.slotY + 10f,
            layout.width * 0.95f,
            layout.width * 0.78f,
            intArrayOf(color("#57FFAA50"), color("#24E66E32"), color("#0D5A2814"), Color.TRANSPARENT),
            floatArrayOf(0f, 0.35f, 0.7f, 1f),
            RectF(0f, 0f, layout.width, layout.height),
        )
        radial(
            canvas,
            layout.width * 0.15f,
            layout.top,
            layout.width * 0.8f,
            layout.width * 0.6f,
            intArrayOf(color("#14788CBE"), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            RectF(0f, 0f, layout.width, layout.height),
        )
        // Deterministic static grain, rendered only when the canvas size changes.
        var seed = 0x13579BDF
        repeat(1_400) {
            seed = seed * 1_103_515_245 + 12_345
            val x = ((seed ushr 8) and 0xFFFF) / 65535f * layout.width
            seed = seed * 1_103_515_245 + 12_345
            val y = ((seed ushr 8) and 0xFFFF) / 65535f * layout.height
            val light = if (seed and 1 == 0) 8 else 5
            canvas.drawRect(x, y, x + 1.2f, y + 1.2f, solid(Color.argb(light, 255, 255, 255)))
        }
    }

    private fun drawScene(canvas: Canvas, state: GiftSlotDropSimulation, reduced: Boolean, density: Float) {
        val layout = state.layout
        val milestones = giftSlotDropMilestones(reduced)
        val since = state.elapsedSinceRelease()
        val closing = if (state.closeAt >= 0f) {
            giftSlotDropClamp((state.clockMs - state.closeAt) / if (reduced) GiftSlotDropTokens.REDUCED_MS else GiftSlotDropTokens.Close.out)
        } else {
            0f
        }
        val backIn = if (state.closeAt >= 0f) {
            giftSlotDropClamp(
                (state.clockMs - state.closeAt - if (reduced) 0f else GiftSlotDropTokens.Close.out) /
                    if (reduced) GiftSlotDropTokens.REDUCED_MS else GiftSlotDropTokens.Close.back,
            )
        } else {
            0f
        }
        val phase = state.phase
        val inSlot = phase == GiftSlotDropPhase.Inserting || phase == GiftSlotDropPhase.Reading ||
            phase == GiftSlotDropPhase.Offer || phase == GiftSlotDropPhase.Closing
        val offerAlpha = when (phase) {
            GiftSlotDropPhase.Offer -> 1f
            GiftSlotDropPhase.Closing -> 1f - closing
            else -> 0f
        }
        val carouselAlpha = when (phase) {
            GiftSlotDropPhase.Inserting -> if (reduced) 0f else 1f - giftSlotDropClamp(since / 300f)
            GiftSlotDropPhase.Reading, GiftSlotDropPhase.Offer -> 0f
            GiftSlotDropPhase.Closing -> if (reduced) backIn else giftSlotDropEaseOut(backIn)
            else -> 1f
        }
        val shelfOut = when (phase) {
            GiftSlotDropPhase.Offer -> giftSlotDropClamp((since - milestones.risen) / 260f)
            GiftSlotDropPhase.Closing -> 1f - backIn
            else -> 0f
        }

        drawHeader(canvas, layout, offerAlpha, density)
        drawTitle(canvas, state, reduced)

        val center = state.selected
        val closingDrop = if (reduced) 0f else 12f * (1f - carouselAlpha)
        for (pass in 2 downTo 0) {
            GiftSlotDropTokens.gifts.forEachIndexed { index, gift ->
                val relative = index - state.offset
                val distance = abs(relative)
                if (distance > 1.7f || min(distance, 2f).roundToInt() != pass) return@forEachIndexed
                if (index == center && (phase == GiftSlotDropPhase.Pulling || inSlot) && phase != GiftSlotDropPhase.Closing) return@forEachIndexed
                val amount = min(distance, 1f)
                val x = layout.centerX + relative * layout.spacing
                val scale = (1f - (1f - GiftSlotDropTokens.Layout.sideScale) * amount) * if (index == center) state.lift else 1f
                val turn = if (reduced) 0f else -relative.coerceIn(-1f, 1f) * GiftSlotDropTokens.Layout.sideTurn
                val alpha = if (index == center && phase == GiftSlotDropPhase.Closing) carouselAlpha else if (index == center) 1f else carouselAlpha
                val y = layout.cardY + if (index == center && phase == GiftSlotDropPhase.Closing) closingDrop else 0f
                if (amount < 0.5f) drawCardShadow(canvas, x, y, GiftSlotDropTokens.Layout.cardW * scale, GiftSlotDropTokens.Layout.cardH * scale, alpha * (1f - amount * 2f))
                drawCard(canvas, gift, x, y, scale, turn, 0f, alpha, false, 0.5f * amount)
            }
        }

        if (phase == GiftSlotDropPhase.Choosing || phase == GiftSlotDropPhase.Swiping) {
            text(canvas, "Drag a gift into the slot", layout.centerX, layout.hintY, FontRole.Hint, GiftSlotDropTokens.Color.mute, 1f, Align.Center)
        }

        val dotsAlpha = carouselAlpha * if (phase == GiftSlotDropPhase.Inserting) 0f else 1f
        var total = 0f
        GiftSlotDropTokens.gifts.indices.forEach { index ->
            total += 6f + 12f * giftSlotDropClamp(1f - abs(index - state.offset)) + if (index > 0) 6f else 0f
        }
        var dotX = layout.centerX - total / 2f
        GiftSlotDropTokens.gifts.indices.forEach { index ->
            val selected = giftSlotDropClamp(1f - abs(index - state.offset))
            val width = 6f + 12f * selected
            canvas.drawRoundRect(RectF(dotX, layout.dotsY - 3f, dotX + width, layout.dotsY + 3f), 3f, 3f, solid(GiftSlotDropTokens.Color.ink, (0.22f + 0.68f * selected) * dotsAlpha))
            dotX += width + 6f
        }
        text(canvas, "One welcome gift per member  ·  valid 30 days", layout.centerX, layout.printY, FontRole.Cap, GiftSlotDropTokens.Color.faint, dotsAlpha, Align.Center)

        val glow = when (phase) {
            GiftSlotDropPhase.Pulling -> giftSlotDropClamp(state.pullY / GiftSlotDropTokens.Drag.commit) * 0.6f + if (state.armed) 0.25f else 0f
            GiftSlotDropPhase.Inserting -> 0.85f
            GiftSlotDropPhase.Reading -> if (reduced) 0.8f else 0.8f + 0.2f * sin(((since - milestones.sunk) / GiftSlotDropTokens.Sequence.read) * PI.toFloat() * 2f - PI.toFloat() / 2f)
            GiftSlotDropPhase.Offer -> 1f - giftSlotDropClamp((since - milestones.read) / 600f) * 0.6f
            else -> 0f
        }
        val shelfAlpha = 1f - shelfOut
        val shelfDeltaY = if (reduced) 0f else 16f * shelfOut
        drawShelf(canvas, layout, glow, shelfAlpha, shelfDeltaY, density)

        val gift = GiftSlotDropTokens.gifts[center]
        if (phase == GiftSlotDropPhase.Pulling || phase == GiftSlotDropPhase.Inserting) {
            var pullY = state.pullY
            var pullX = state.pullX
            if (phase == GiftSlotDropPhase.Inserting) {
                val progress = if (reduced) 1f else giftSlotDropEaseInOut(giftSlotDropClamp(since / GiftSlotDropTokens.Sequence.sink))
                val target = layout.slotY - layout.cardY + GiftSlotDropTokens.Layout.cardH * 0.7f
                pullY = state.releasedFrom.y + (target - state.releasedFrom.y) * progress
                pullX = state.releasedFrom.x * (1f - progress)
            }
            val progress = giftSlotDropClamp(pullY / GiftSlotDropTokens.Drag.full)
            val scale = (1f - (1f - GiftSlotDropTokens.Drag.minScale) * progress) * state.lift
            val lean = if (reduced) 0f else GiftSlotDropTokens.Drag.leanDeg * progress
            val save = canvas.save()
            canvas.clipRect(0f, 0f, layout.width, layout.slotY)
            drawCardShadow(canvas, layout.centerX + pullX, layout.cardY + pullY, GiftSlotDropTokens.Layout.cardW * scale, GiftSlotDropTokens.Layout.cardH * scale, 1f - progress)
            drawCard(canvas, gift, layout.centerX + pullX, layout.cardY + pullY, scale, 0f, lean, 1f)
            canvas.restoreToCount(save)
        }
        drawLip(canvas, layout, shelfAlpha, shelfDeltaY, density)

        if (phase == GiftSlotDropPhase.Offer || (phase == GiftSlotDropPhase.Closing && closing < 1f)) {
            val riseProgress = if (reduced) 1f else giftSlotDropClamp((since - milestones.read) / GiftSlotDropTokens.Sequence.rise)
            val rise = if (reduced) 1f else giftSlotDropSettle(since - milestones.read, GiftSlotDropTokens.Sequence.rise)
            val giftScale = GiftSlotDropTokens.Layout.giftW / GiftSlotDropTokens.Layout.cardW
            val startY = layout.slotY + GiftSlotDropTokens.Layout.giftH * 0.55f
            val giftY = startY + (layout.giftY - startY) * rise
            val flip = if (reduced) 1f else giftSlotDropEaseInOut(
                giftSlotDropClamp((since - milestones.risen - GiftSlotDropTokens.Sequence.pause) / GiftSlotDropTokens.Sequence.flip),
            )
            val backSide = flip > 0.5f
            val alpha = if (reduced) {
                if (phase == GiftSlotDropPhase.Closing) 1f - closing else giftSlotDropClamp((since - milestones.read) / GiftSlotDropTokens.REDUCED_MS)
            } else {
                1f - closing
            }
            val scale = giftScale * (0.9f + 0.1f * giftSlotDropEaseOut(riseProgress)) *
                if (phase == GiftSlotDropPhase.Closing && !reduced) 1f - 0.04f * closing else 1f
            val save = canvas.save()
            if (riseProgress < 1f) canvas.clipRect(0f, 0f, layout.width, layout.slotY + shelfDeltaY)
            val closeOffset = if (reduced) 0f else 10f * closing
            drawCardShadow(canvas, layout.centerX, giftY + closeOffset, GiftSlotDropTokens.Layout.giftW, GiftSlotDropTokens.Layout.giftH, alpha * giftSlotDropClamp(riseProgress * 2f - 1f))
            drawCard(canvas, gift, layout.centerX, giftY + closeOffset, scale, if (backSide) flip * 180f - 180f else flip * 180f, 0f, alpha, backSide)
            canvas.restoreToCount(save)
            if (riseProgress < 1f) drawLip(canvas, layout, shelfAlpha, shelfDeltaY, density)

            fun shown(index: Int): Float {
                val progress = if (reduced) {
                    giftSlotDropClamp((since - milestones.turned) / GiftSlotDropTokens.REDUCED_MS)
                } else {
                    giftSlotDropEaseOut(
                        giftSlotDropClamp(
                            (since - milestones.turned - index * GiftSlotDropTokens.Sequence.stagger) /
                                GiftSlotDropTokens.Sequence.detailsIn,
                        ),
                    )
                }
                return progress * (1f - closing)
            }

            repeat(2) { index ->
                val visible = shown(index)
                val offsetY = if (reduced) 0f else 8f * (1f - visible)
                text(canvas, "0${index + 1}", GiftSlotDropTokens.Layout.edge + 24f, layout.stepsY + index * 32f + offsetY, FontRole.StepNum, GiftSlotDropTokens.Color.amberInk, visible)
                text(canvas, gift.steps[index], GiftSlotDropTokens.Layout.edge + 56f, layout.stepsY + index * 32f + offsetY, FontRole.Step, GiftSlotDropTokens.Color.ink, visible)
            }
            val buttonAlpha = shown(2)
            if (buttonAlpha > 0.004f) {
                val buttonWidth = layout.width - 2f * GiftSlotDropTokens.Layout.edge
                val buttonY = layout.buttonY + if (reduced) 0f else 10f * (1f - buttonAlpha)
                val buttonScale = state.buttonScale
                val saveButton = canvas.save()
                canvas.scale(buttonScale, buttonScale, layout.centerX, buttonY + GiftSlotDropTokens.Layout.buttonH / 2f)
                canvas.drawRoundRect(
                    RectF(GiftSlotDropTokens.Layout.edge, buttonY, GiftSlotDropTokens.Layout.edge + buttonWidth, buttonY + GiftSlotDropTokens.Layout.buttonH),
                    GiftSlotDropTokens.Layout.buttonH / 2f,
                    GiftSlotDropTokens.Layout.buttonH / 2f,
                    solid(GiftSlotDropTokens.Color.button, buttonAlpha),
                )
                text(canvas, gift.action, layout.centerX, buttonY + GiftSlotDropTokens.Layout.buttonH / 2f + 6f, FontRole.Button, GiftSlotDropTokens.Color.buttonInk, buttonAlpha, Align.Center)
                canvas.restoreToCount(saveButton)
            }
        }

        if (state.ghostOpacity > 0.01f) {
            canvas.drawCircle(state.ghostX, state.ghostY, 20f, solid(Color.WHITE, 0.14f * state.ghostOpacity))
            canvas.drawCircle(state.ghostX, state.ghostY, 19.5f, stroke(Color.WHITE, 1f, 0.4f * state.ghostOpacity))
        }
    }

    private fun drawHeader(canvas: Canvas, layout: GiftSlotDropLayout, offerAlpha: Float, density: Float) {
        val y = layout.top
        glass(canvas, layout, GiftSlotDropTokens.Layout.edge, y, 44f, 44f, 22f, floatArrayOf(0.95f, 0.9f, 0.84f, 0.06f), 0f, 1f, density)
        val centerX = GiftSlotDropTokens.Layout.edge + 22f
        val centerY = y + 22f
        val back = Path().apply {
            moveTo(centerX + 3f, centerY - 7f)
            lineTo(centerX - 4f, centerY)
            lineTo(centerX + 3f, centerY + 7f)
        }
        canvas.drawPath(back, stroke(GiftSlotDropTokens.Color.ink, 2f, 1f - offerAlpha))
        val close = Path().apply {
            moveTo(centerX - 6f, centerY - 6f)
            lineTo(centerX + 6f, centerY + 6f)
            moveTo(centerX + 6f, centerY - 6f)
            lineTo(centerX - 6f, centerY + 6f)
        }
        canvas.drawPath(close, stroke(GiftSlotDropTokens.Color.ink, 2f, offerAlpha))
        val termsWidth = measure("Terms", FontRole.Nav) + 32f
        glass(canvas, layout, layout.width - GiftSlotDropTokens.Layout.edge - termsWidth, y + 4f, termsWidth, 36f, 18f, floatArrayOf(0.95f, 0.9f, 0.84f, 0.06f), 0f, 1f, density)
        text(canvas, "Terms", layout.width - GiftSlotDropTokens.Layout.edge - termsWidth / 2f, y + 27f, FontRole.Nav, GiftSlotDropTokens.Color.ink, 1f, Align.Center)
        text(canvas, "ORRA PAY  ·  NEW MEMBER", layout.centerX, y + GiftSlotDropTokens.Layout.eyebrowY, FontRole.Eyebrow, GiftSlotDropTokens.Color.faint, 1f, Align.Center, 1.4f)
    }

    private fun drawTitle(canvas: Canvas, state: GiftSlotDropSimulation, reduced: Boolean) {
        val titles = arrayOf(
            arrayOf("Choose your", "welcome gift"),
            arrayOf("Let go to", "activate it"),
            arrayOf("Activating", "your gift…"),
            arrayOf("Your gift", "is ready"),
        )
        val current = when (state.phase) {
            GiftSlotDropPhase.Inserting, GiftSlotDropPhase.Reading -> 2
            GiftSlotDropPhase.Offer -> 3
            else -> if (state.armed) 1 else 0
        }
        val elapsed = if (reduced) 1_000_000f else state.clockMs - state.titleAt
        val out = giftSlotDropClamp(elapsed / GiftSlotDropTokens.Title.out)
        val inside = giftSlotDropEaseOut(giftSlotDropClamp((elapsed - GiftSlotDropTokens.Title.out) / GiftSlotDropTokens.Title.inside))
        val baseline = state.layout.top + GiftSlotDropTokens.Layout.titleY
        fun draw(index: Int, alpha: Float, deltaY: Float) {
            val shade = if (index == 1) GiftSlotDropTokens.Color.amberInk else GiftSlotDropTokens.Color.ink
            text(canvas, titles[index][0], state.layout.centerX, baseline + deltaY, FontRole.Title, shade, alpha, Align.Center)
            text(canvas, titles[index][1], state.layout.centerX, baseline + GiftSlotDropTokens.Layout.titleLine + deltaY, FontRole.Title, shade, alpha, Align.Center)
        }
        if (out < 1f && state.titleWas != current) draw(state.titleWas, 1f - out, -GiftSlotDropTokens.Title.rise * out)
        draw(current, if (out < 1f && state.titleWas != current) 0f else inside, GiftSlotDropTokens.Title.rise * (1f - inside))
    }

    private fun drawShelf(canvas: Canvas, layout: GiftSlotDropLayout, glow: Float, alpha: Float, deltaY: Float, density: Float) {
        if (alpha <= 0.004f) return
        val slotY = layout.slotY + deltaY
        glass(
            canvas,
            layout,
            layout.centerX - GiftSlotDropTokens.Layout.shelfW / 2f,
            slotY - GiftSlotDropTokens.Layout.shelfH / 2f,
            GiftSlotDropTokens.Layout.shelfW,
            GiftSlotDropTokens.Layout.shelfH,
            GiftSlotDropTokens.Layout.shelfH / 2f,
            floatArrayOf(1f, 0.86f, 0.68f, 0.07f),
            0.4f,
            alpha,
            density,
        )
        radial(
            canvas,
            layout.centerX,
            slotY,
            GiftSlotDropTokens.Layout.slotW * 0.7f,
            GiftSlotDropTokens.Layout.slotW * 0.224f,
            intArrayOf(withAlpha(color(GiftSlotDropTokens.Color.amber), (0.22f + 0.4f * glow) * alpha), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            RectF(layout.centerX - GiftSlotDropTokens.Layout.slotW, slotY - 60f, layout.centerX + GiftSlotDropTokens.Layout.slotW, slotY + 60f),
            plus = true,
        )
        val halfWidth = GiftSlotDropTokens.Layout.slotW / 2f
        val halfHeight = GiftSlotDropTokens.Layout.slotH / 2f
        reset(fill)
        fill.shader = LinearGradient(
            0f,
            slotY - halfHeight,
            0f,
            slotY + halfHeight,
            intArrayOf(color(GiftSlotDropTokens.Color.slotDeep), color(GiftSlotDropTokens.Color.slotDeep), withAlpha(color(GiftSlotDropTokens.Color.amber), (0.35f + 0.65f * glow) * alpha)),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(RectF(layout.centerX - halfWidth, slotY - halfHeight, layout.centerX + halfWidth, slotY + halfHeight), halfHeight, halfHeight, fill)
    }

    private fun drawLip(canvas: Canvas, layout: GiftSlotDropLayout, alpha: Float, deltaY: Float, density: Float) {
        if (alpha <= 0.004f) return
        val slotY = layout.slotY + deltaY
        val width = GiftSlotDropTokens.Layout.slotW
        val height = GiftSlotDropTokens.Layout.slotH
        glass(canvas, layout, layout.centerX - width / 2f - 6f, slotY, width + 12f, height / 2f + 3f, 2f, floatArrayOf(1f, 0.86f, 0.68f, 0.07f), 0f, alpha, density)
        reset(fill)
        fill.shader = LinearGradient(
            layout.centerX - width / 2f,
            0f,
            layout.centerX + width / 2f,
            0f,
            intArrayOf(Color.TRANSPARENT, withAlpha(color(GiftSlotDropTokens.Color.lip), 0.9f * alpha), withAlpha(color(GiftSlotDropTokens.Color.lip), 0.9f * alpha), Color.TRANSPARENT),
            floatArrayOf(0f, 0.2f, 0.8f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(layout.centerX - width / 2f, slotY - 0.5f, layout.centerX + width / 2f, slotY + 0.7f, fill)
    }

    private fun drawCard(
        canvas: Canvas,
        gift: GiftSlotDropTokens.Gift,
        x: Float,
        y: Float,
        scale: Float,
        turnY: Float,
        leanX: Float,
        alpha: Float,
        backSide: Boolean = false,
        dim: Float = 0f,
    ) {
        if (alpha <= 0.004f) return
        val save = canvas.save()
        camera.save()
        camera.setLocation(0f, 0f, -12.5f)
        camera.rotateX(leanX)
        camera.rotateY(turnY)
        matrix.reset()
        camera.getMatrix(matrix)
        camera.restore()
        matrix.preScale(scale, scale)
        matrix.postTranslate(x, y)
        canvas.concat(matrix)
        if (backSide) drawCardBack(canvas, gift, alpha) else drawCardFront(canvas, gift, alpha)
        if (dim > 0.004f) {
            val width = GiftSlotDropTokens.Layout.cardW
            val height = GiftSlotDropTokens.Layout.cardH
            canvas.drawRoundRect(RectF(-width / 2f, -height / 2f, width / 2f, height / 2f), GiftSlotDropTokens.Layout.cardR, GiftSlotDropTokens.Layout.cardR, solid(GiftSlotDropTokens.Color.page, dim * alpha))
        }
        canvas.restoreToCount(save)
    }

    private fun drawCardFront(canvas: Canvas, gift: GiftSlotDropTokens.Gift, alpha: Float) {
        val width = GiftSlotDropTokens.Layout.cardW
        val height = GiftSlotDropTokens.Layout.cardH
        val left = -width / 2f
        val top = -height / 2f
        val card = RectF(left, top, left + width, top + height)
        reset(fill)
        fill.alpha = alphaByte(alpha)
        fill.shader = LinearGradient(left, top, left + width, top + height, color(gift.background), color(gift.backgroundEnd), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(card, GiftSlotDropTokens.Layout.cardR, GiftSlotDropTokens.Layout.cardR, fill)
        val save = canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(card, GiftSlotDropTokens.Layout.cardR, GiftSlotDropTokens.Layout.cardR, Path.Direction.CW) })
        drawMark(canvas, gift.mark, left + width - 128f, top + height - 124f, 168f, gift.ink, 0.07f * alpha, 2.2f)
        reset(fill)
        fill.shader = LinearGradient(left, top, left + width * 0.7f, top + height * 0.55f, withAlpha(Color.WHITE, 0.09f * alpha), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(card, fill)
        canvas.restoreToCount(save)
        canvas.drawRoundRect(RectF(left + 0.5f, top + 0.5f, left + width - 0.5f, top + height - 0.5f), GiftSlotDropTokens.Layout.cardR - 0.5f, GiftSlotDropTokens.Layout.cardR - 0.5f, stroke(Color.WHITE, 1f, 0.16f * alpha))

        val contentLeft = left + 20f
        drawMark(canvas, gift.mark, contentLeft, top + 22f, 16f, gift.ink, alpha)
        text(canvas, gift.shop, contentLeft + 24f, top + 36f, FontRole.Mark, gift.ink, alpha)
        val tagWidth = measure("GIFT", FontRole.Tag, 1.2f) + 14f
        val tag = RectF(left + width - 20f - tagWidth, top + 22f, left + width - 20f, top + 40f)
        canvas.drawRoundRect(tag, 9f, 9f, stroke(gift.ink, 1f, 0.4f * alpha))
        text(canvas, "GIFT", tag.centerX(), top + 35f, FontRole.Tag, gift.ink, 0.85f * alpha, Align.Center, 1.2f)
        text(canvas, gift.offer, contentLeft - 2f, top + 150f, FontRole.Offer, gift.ink, alpha)
        text(canvas, gift.line, contentLeft, top + 176f, FontRole.OfferLine, gift.ink, alpha)
        text(canvas, gift.cap, contentLeft, top + 196f, FontRole.Cap, gift.sub, alpha)
        canvas.drawRect(contentLeft, top + height - 46f, left + width - 20f, top + height - 45f, solid(gift.ink, 0.16f * alpha))
        text(canvas, "WELCOME GIFT", contentLeft, top + height - 22f, FontRole.Tag, gift.sub, alpha, tracking = 1.2f)
        text(canvas, "ORRA PAY", left + width - 20f, top + height - 22f, FontRole.Tag, gift.sub, alpha, Align.Right, 1.2f)
    }

    private fun drawCardBack(canvas: Canvas, gift: GiftSlotDropTokens.Gift, alpha: Float) {
        val width = GiftSlotDropTokens.Layout.cardW
        val height = GiftSlotDropTokens.Layout.cardH
        val left = -width / 2f
        val top = -height / 2f
        val card = RectF(left, top, left + width, top + height)
        reset(fill)
        fill.alpha = alphaByte(alpha)
        fill.shader = LinearGradient(left, top, left, top + height, color(gift.backgroundEnd), color(gift.background), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(card, GiftSlotDropTokens.Layout.cardR, GiftSlotDropTokens.Layout.cardR, fill)
        canvas.drawRoundRect(RectF(left + 0.5f, top + 0.5f, left + width - 0.5f, top + height - 0.5f), GiftSlotDropTokens.Layout.cardR - 0.5f, GiftSlotDropTokens.Layout.cardR - 0.5f, stroke(Color.WHITE, 1f, 0.16f * alpha))
        val contentLeft = left + 20f
        drawMark(canvas, gift.mark, contentLeft, top + 22f, 16f, gift.ink, alpha)
        text(canvas, gift.shop, contentLeft + 24f, top + 36f, FontRole.Mark, gift.ink, alpha)
        val checkX = left + width - 29f
        val checkY = top + 31f
        canvas.drawCircle(checkX, checkY, 9f, stroke(gift.ink, 1.2f, 0.5f * alpha))
        val check = Path().apply {
            moveTo(checkX - 4f, checkY)
            lineTo(checkX - 1.2f, checkY + 3f)
            lineTo(checkX + 4.2f, checkY - 3f)
        }
        canvas.drawPath(check, stroke(gift.ink, 1.6f, alpha))
        text(canvas, "YOUR CODE", contentLeft, top + 104f, FontRole.CodeLabel, gift.sub, alpha, tracking = 1.4f)
        val well = RectF(contentLeft, top + 116f, left + width - 20f, top + 168f)
        canvas.drawRoundRect(well, 12f, 12f, solid(Color.BLACK, 0.18f * alpha))
        canvas.drawRoundRect(well, 12f, 12f, stroke(gift.ink, 1f, 0.14f * alpha))
        val room = width - 68f
        val codeSize = GiftSlotDropTokens.Type.code * min(1f, room / measure(gift.code, FontRole.Code))
        text(canvas, gift.code, 0f, top + 150f, FontRole.Code, gift.ink, alpha, Align.Center, overrideSize = codeSize)
        val offer = "${gift.offer} ${gift.line}"
        val offerSize = GiftSlotDropTokens.Type.offerLine * min(1f, (width - 40f) / measure(offer, FontRole.OfferLine))
        text(canvas, offer, contentLeft, top + 202f, FontRole.OfferLine, gift.ink, alpha, overrideSize = offerSize)
        text(canvas, gift.cap, contentLeft, top + 222f, FontRole.Cap, gift.sub, alpha)
        canvas.drawRect(contentLeft, top + height - 46f, left + width - 20f, top + height - 45f, solid(gift.ink, 0.16f * alpha))
        text(canvas, "VALID TO 27 NOV", contentLeft, top + height - 22f, FontRole.Tag, gift.sub, alpha, tracking = 1.2f)
        text(canvas, "ACTIVE", left + width - 20f, top + height - 22f, FontRole.Tag, gift.ink, alpha, Align.Right, 1.2f)
    }

    private fun drawCardShadow(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, alpha: Float) {
        if (alpha <= 0.01f) return
        val rect = RectF(x - width / 2f + 10f, y - height / 2f + 28f, x + width / 2f - 10f, y + height / 2f + 18f)
        reset(fill)
        fill.shader = RadialGradient(rect.centerX(), rect.centerY(), max(rect.width(), rect.height()) * 0.62f, intArrayOf(withAlpha(Color.BLACK, 0.35f * alpha), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, 28f, 28f, fill)
    }

    private fun drawMark(canvas: Canvas, mark: GiftSlotDropMark, x: Float, y: Float, size: Float, shade: String, alpha: Float, width: Float = 1.6f) {
        val save = canvas.save()
        canvas.translate(x, y)
        canvas.scale(size / 16f, size / 16f)
        canvas.drawPath(requireNotNull(paths[mark]), stroke(shade, width * 16f / size, alpha))
        canvas.restoreToCount(save)
    }

    private fun glass(
        canvas: Canvas,
        layout: GiftSlotDropLayout,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        tint: FloatArray,
        thick: Float,
        alpha: Float,
        density: Float,
    ) {
        if (alpha <= 0.004f) return
        val frost = frosted ?: return
        val rect = RectF(x, y, x + width, y + height)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val shader = runtimeShader ?: return
            val child = BitmapShader(frost, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            child.setLocalMatrix(Matrix().apply { setScale(3f / density, 3f / density) })
            shader.setInputShader("bg", child)
            shader.setFloatUniform("uRect", x, y, width, height)
            shader.setFloatUniform("uRadius", radius)
            shader.setFloatUniform("uTint", tint[0], tint[1], tint[2], tint[3])
            shader.setFloatUniform("uThick", thick)
            shader.setFloatUniform("uGlow", 0f, 0f, 0f)
            shader.setFloatUniform("uAlpha", alpha)
            reset(fill)
            fill.shader = shader
            canvas.drawRect(RectF(x - 1f, y - 1f, x + width + 1f, y + height + 1f), fill)
            return
        }
        val save = canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
        canvas.drawBitmap(frost, null, RectF(0f, 0f, layout.width, layout.height), bitmapPaint)
        val tintColor = Color.rgb((tint[0] * 255f).roundToInt(), (tint[1] * 255f).roundToInt(), (tint[2] * 255f).roundToInt())
        canvas.drawRect(rect, solid(tintColor, tint[3] * 0.85f * alpha))
        canvas.restoreToCount(save)
        canvas.drawRoundRect(rect, radius, radius, stroke(Color.WHITE, 1f, (0.18f + 0.1f * thick) * alpha))
    }

    private fun radial(
        canvas: Canvas,
        x: Float,
        y: Float,
        radiusX: Float,
        radiusY: Float,
        colors: IntArray,
        stops: FloatArray,
        destination: RectF,
        plus: Boolean = false,
    ) {
        reset(fill)
        val shader = RadialGradient(x, y, radiusX, colors, stops, Shader.TileMode.CLAMP)
        shader.setLocalMatrix(Matrix().apply {
            setScale(1f, radiusY / radiusX, x, y)
        })
        fill.shader = shader
        if (plus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) fill.blendMode = BlendMode.PLUS
        canvas.drawRect(destination, fill)
        fill.blendMode = null
    }

    private fun text(
        canvas: Canvas,
        value: String,
        x: Float,
        baseline: Float,
        role: FontRole,
        shade: String,
        alpha: Float = 1f,
        align: Align = Align.Left,
        tracking: Float = 0f,
        overrideSize: Float? = null,
    ) {
        if (alpha <= 0.004f || value.isEmpty()) return
        configureText(role, shade, alpha, overrideSize)
        val width = measureConfigured(value, tracking)
        var left = when (align) {
            Align.Left -> x
            Align.Center -> x - width / 2f
            Align.Right -> x - width
        }
        if (tracking == 0f) {
            canvas.drawText(value, left, baseline, letters)
            return
        }
        value.forEach { character ->
            val glyph = character.toString()
            canvas.drawText(glyph, left, baseline, letters)
            left += letters.measureText(glyph) + tracking
        }
    }

    private fun measure(value: String, role: FontRole, tracking: Float = 0f): Float {
        configureText(role, GiftSlotDropTokens.Color.ink, 1f)
        return measureConfigured(value, tracking)
    }

    private fun measureConfigured(value: String, tracking: Float): Float =
        letters.measureText(value) + max(0, value.length - 1) * tracking

    private fun configureText(role: FontRole, shade: String, alpha: Float, overrideSize: Float? = null) {
        letters.reset()
        letters.isAntiAlias = true
        letters.isSubpixelText = true
        letters.color = color(shade)
        letters.alpha = alphaByte(alpha)
        letters.textSize = overrideSize ?: role.size
        letters.typeface = when (role.face) {
            Face.Regular -> assets.regular
            Face.Medium -> assets.medium
            Face.Semibold -> assets.semibold
            Face.Mono -> assets.mono
        }
    }

    private fun solid(shade: String, alpha: Float = 1f): Paint = solid(color(shade), alpha)

    private fun solid(shade: Int, alpha: Float = 1f): Paint {
        reset(fill)
        fill.color = shade
        fill.alpha = alphaByte(alpha * (Color.alpha(shade) / 255f))
        return fill
    }

    private fun stroke(shade: String, width: Float, alpha: Float = 1f): Paint = stroke(color(shade), width, alpha)

    private fun stroke(shade: Int, width: Float, alpha: Float = 1f): Paint {
        reset(line)
        line.style = Paint.Style.STROKE
        line.strokeWidth = width
        line.strokeCap = Paint.Cap.ROUND
        line.strokeJoin = Paint.Join.ROUND
        line.color = shade
        line.alpha = alphaByte(alpha * (Color.alpha(shade) / 255f))
        return line
    }

    private fun reset(paint: Paint) {
        paint.reset()
        paint.isAntiAlias = true
        paint.alpha = 255
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
    }

    /** Two-pass box blur on the one-third-size page. It runs only when size/density changes. */
    private fun blurBitmap(bitmap: Bitmap, radius: Int) {
        val width = bitmap.width
        val height = bitmap.height
        if (width == 0 || height == 0) return
        val source = IntArray(width * height)
        val horizontal = IntArray(source.size)
        val output = IntArray(source.size)
        bitmap.getPixels(source, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            var red = 0
            var green = 0
            var blue = 0
            for (sample in -radius..radius) {
                val value = source[y * width + sample.coerceIn(0, width - 1)]
                red += Color.red(value)
                green += Color.green(value)
                blue += Color.blue(value)
            }
            val count = radius * 2 + 1
            for (x in 0 until width) {
                horizontal[y * width + x] = Color.rgb(red / count, green / count, blue / count)
                val leaving = source[y * width + (x - radius).coerceIn(0, width - 1)]
                val entering = source[y * width + (x + radius + 1).coerceIn(0, width - 1)]
                red += Color.red(entering) - Color.red(leaving)
                green += Color.green(entering) - Color.green(leaving)
                blue += Color.blue(entering) - Color.blue(leaving)
            }
        }

        for (x in 0 until width) {
            var red = 0
            var green = 0
            var blue = 0
            for (sample in -radius..radius) {
                val value = horizontal[sample.coerceIn(0, height - 1) * width + x]
                red += Color.red(value)
                green += Color.green(value)
                blue += Color.blue(value)
            }
            val count = radius * 2 + 1
            for (y in 0 until height) {
                output[y * width + x] = Color.rgb(red / count, green / count, blue / count)
                val leaving = horizontal[(y - radius).coerceIn(0, height - 1) * width + x]
                val entering = horizontal[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                red += Color.red(entering) - Color.red(leaving)
                green += Color.green(entering) - Color.green(leaving)
                blue += Color.blue(entering) - Color.blue(leaving)
            }
        }
        bitmap.setPixels(output, 0, width, 0, 0, width, height)
    }

    private enum class Align { Left, Center, Right }
    private enum class Face { Regular, Medium, Semibold, Mono }
    private enum class FontRole(val size: Float, val face: Face) {
        Eyebrow(GiftSlotDropTokens.Type.eyebrow, Face.Mono),
        Title(GiftSlotDropTokens.Type.title, Face.Semibold),
        Nav(GiftSlotDropTokens.Type.nav, Face.Medium),
        Hint(GiftSlotDropTokens.Type.hint, Face.Medium),
        Mark(GiftSlotDropTokens.Type.mark, Face.Semibold),
        Offer(GiftSlotDropTokens.Type.offer, Face.Semibold),
        OfferLine(GiftSlotDropTokens.Type.offerLine, Face.Medium),
        Cap(GiftSlotDropTokens.Type.cap, Face.Regular),
        Tag(GiftSlotDropTokens.Type.tag, Face.Mono),
        CodeLabel(GiftSlotDropTokens.Type.codeLabel, Face.Mono),
        Code(GiftSlotDropTokens.Type.code, Face.Mono),
        Step(GiftSlotDropTokens.Type.step, Face.Regular),
        StepNum(GiftSlotDropTokens.Type.stepNum, Face.Mono),
        Button(GiftSlotDropTokens.Type.button, Face.Semibold),
    }
}

private fun makeMarkPath(mark: GiftSlotDropMark): Path = Path().apply {
    when (mark) {
        GiftSlotDropMark.Bowl -> {
            moveTo(1f, 6.5f); lineTo(15f, 6.5f)
            cubicTo(15f, 10.9f, 11.9f, 14f, 8f, 14f)
            cubicTo(4.1f, 14f, 1f, 10.9f, 1f, 6.5f)
            close()
            moveTo(3.2f, 4.2f); lineTo(12.8f, 4.2f)
        }
        GiftSlotDropMark.Rails -> {
            moveTo(4f, 15f); lineTo(7.5f, 1f)
            moveTo(8.5f, 15f); lineTo(12f, 1f)
            moveTo(3.9f, 11.5f); lineTo(10.9f, 11.5f)
            moveTo(4.8f, 8f); lineTo(11.8f, 8f)
            moveTo(5.7f, 4.5f); lineTo(12.7f, 4.5f)
        }
        GiftSlotDropMark.Leaf -> {
            moveTo(8f, 1f)
            cubicTo(13.2f, 4.2f, 14f, 10.2f, 8f, 15f)
            cubicTo(2f, 10.2f, 2.8f, 4.2f, 8f, 1f)
            close()
            moveTo(8f, 4f); lineTo(8f, 15f)
        }
        GiftSlotDropMark.Bean -> {
            moveTo(8f, 1.2f)
            cubicTo(12.2f, 1.2f, 14.4f, 4.6f, 14.4f, 8f)
            cubicTo(14.4f, 11.4f, 12.2f, 14.8f, 8f, 14.8f)
            cubicTo(3.8f, 14.8f, 1.6f, 11.4f, 1.6f, 8f)
            cubicTo(1.6f, 4.6f, 3.8f, 1.2f, 8f, 1.2f)
            close()
            moveTo(9.6f, 2.4f)
            cubicTo(6.2f, 5.2f, 9.8f, 10.8f, 6.4f, 13.6f)
        }
    }
}

private fun color(value: String): Int = Color.parseColor(value)

private fun alphaByte(alpha: Float): Int = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()

private fun withAlpha(value: Int, alpha: Float): Int = Color.argb(alphaByte(alpha), Color.red(value), Color.green(value), Color.blue(value))

private const val GLASS_SHADER = """
uniform shader bg;
uniform float4 uRect;
uniform float uRadius;
uniform float4 uTint;
uniform float uThick;
uniform float3 uGlow;
uniform float uAlpha;

float sdBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 xy) {
  float2 b = uRect.zw * 0.5;
  float2 p = xy - (uRect.xy + b);
  float r = min(uRadius, min(b.x, b.y));
  float d = sdBox(p, b, r);
  float cov = 1.0 - smoothstep(-0.5, 0.5, d);
  if (cov <= 0.0) return half4(0.0);
  float e = 0.5;
  float2 n = float2(sdBox(p + float2(e, 0.0), b, r) - sdBox(p - float2(e, 0.0), b, r),
                    sdBox(p + float2(0.0, e), b, r) - sdBox(p - float2(0.0, e), b, r));
  n /= max(length(n), 0.0001);
  float bevel = min(12.0, min(b.x, b.y));
  float t = clamp(-d / bevel, 0.0, 1.0);
  float lens = (1.0 - t) * (1.0 - t);
  float bend = 7.0 + 7.0 * uThick;
  float2 s = xy - n * lens * bend - p * 0.025;
  float spread = lens * (1.2 + uThick);
  float3 col = float3(bg.eval(s - n * spread).r, bg.eval(s).g, bg.eval(s + n * spread).b);
  col = col * 0.8 + 0.03;
  col = mix(col, uTint.rgb, uTint.a * 0.55) + uTint.rgb * uTint.a * 0.32;
  float rim = 1.0 - smoothstep(0.0, 1.2, -d);
  float band = (1.0 - smoothstep(0.0, 7.0, -d)) * lens;
  float up = pow(max(dot(n, float2(-0.34, -0.94)), 0.0), 1.4);
  float down = pow(max(dot(n, float2(0.3, 0.95)), 0.0), 2.0);
  float lit = 0.12 + 0.6 * up + 0.24 * down;
  col += rim * lit * (0.9 + 0.3 * uThick);
  col += band * lit * 0.16 * (1.0 + uThick);
  col += mix(uTint.rgb, float3(1.0), 0.5) * rim * uTint.a * 0.6;
  col += (1.0 - smoothstep(-b.y, -b.y + min(28.0, b.y), p.y)) * 0.03;
  col *= 1.0 - 0.1 * smoothstep(1.6, 5.0, -d) * (1.0 - smoothstep(5.0, 10.0, -d));
  float2 finger = xy - uGlow.xy;
  col += uGlow.z * 0.16 * exp(-dot(finger, finger) / (2.0 * 46.0 * 46.0));
  float alpha = cov * uAlpha;
  return half4(half3(col * alpha), half(alpha));
}
"""
