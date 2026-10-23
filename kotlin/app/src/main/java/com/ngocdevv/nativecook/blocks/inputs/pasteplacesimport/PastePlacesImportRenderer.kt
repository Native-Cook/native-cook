package com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.ColorInt
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportMotion.Detect
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportMotion.Fan
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportMotion.Label
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportMotion.NoteIn
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportMotion.Press
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportMotion.Presses
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportTheme.Chip
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportTheme.Colors
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportTheme.MotionColors
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportTheme.Note
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportTheme.Type
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin

internal class PastePlacesImportRenderer(private val assets: PastePlacesImportAssets) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val rect = RectF()
    private val clipPath = Path()
    private var cachedLayout: Layout? = null

    /** What the host knows besides the controller: the text area's own text and the button's fill for it. */
    class Frame(val draftCount: Int, val draftFill: Float)

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        topInset: Float,
        controller: PastePlacesImportController,
        frame: Frame,
    ) {
        val layout = layout(width, height, topInset)
        val time = controller.time
        if (controller.reducedOpacity < 0.9999) {
            canvas.saveLayerAlpha(0f, 0f, width, height, (controller.reducedOpacity * 255.0).roundToInt())
        } else {
            canvas.save()
        }
        drawPage(canvas, layout)
        // The live text area, replaced by the paper panels from the first fold to the end of the reset.
        if (!PastePlacesImportMotion.panelsAt(time)) {
            val scripted = if (controller.scriptedPresses) PastePlacesImportMotion.pressAt(time, Presses.paste) else 0.0
            drawCardFace(canvas, layout, time, max(controller.pastePress, scripted), frame.draftCount > 0)
        }
        layout.paper.draw(canvas, time)
        drawIcons(canvas, layout, time)
        drawHeadlineAndHints(canvas, layout, time)
        drawCounter(canvas, layout, time, frame.draftCount)
        drawButton(canvas, layout, time, controller, frame.draftFill)
        drawClose(canvas, layout, time, controller)
        drawChips(canvas, layout, time)
        canvas.restore()
    }

    private fun drawPage(canvas: Canvas, layout: Layout) {
        paint.resetFill(Color.WHITE, 1f)
        paint.shader = layout.pageShader
        paint.alpha = 255
        canvas.drawRect(0f, 0f, layout.width, layout.height, paint)
        paint.shader = null
        drawGlow(canvas, layout, Colors.glowBand, layout.bandShader)
        drawGlow(canvas, layout, Colors.glowCorner, layout.cornerShader)
    }

    private fun drawGlow(canvas: Canvas, layout: Layout, glow: PastePlacesImportTheme.Glow, shader: RadialGradient) {
        val centerX = layout.x(glow.cx)
        val centerY = layout.y(glow.cy)
        val radiusX = layout.px(glow.rx)
        val radiusY = layout.px(glow.ry)
        paint.resetFill(Color.WHITE, 1f)
        paint.shader = shader
        paint.alpha = 255
        canvas.save()
        canvas.scale(1f, radiusY / radiusX, centerX, centerY)
        canvas.drawCircle(centerX, centerY, radiusX, paint)
        canvas.restore()
        paint.shader = null
    }

    private fun drawIcons(canvas: Canvas, layout: Layout, time: Double) {
        val fan = PastePlacesImportMotion.fanAt(time)
        drawIcon(canvas, layout, assets.doc, PastePlacesImportTheme.icons.doc, Fan.doc, fan)
        drawIcon(canvas, layout, assets.chat, PastePlacesImportTheme.icons.chat, Fan.chat, fan)
        drawIcon(canvas, layout, assets.notes, PastePlacesImportTheme.icons.notes, Fan.notes, fan)
    }

    private fun drawIcon(
        canvas: Canvas,
        layout: Layout,
        bitmap: Bitmap,
        bounds: PastePlacesImportTheme.RectToken,
        motion: PastePlacesImportMotion.IconFan,
        fan: Double,
    ) {
        val left = layout.x(bounds.x)
        val top = layout.y(bounds.y)
        val width = layout.px(bounds.w)
        val height = layout.px(bounds.h)
        val centerX = left + width / 2f
        val centerY = top + height / 2f
        canvas.save()
        canvas.translate((motion.x * layout.k * fan).toFloat(), (motion.y * layout.k * fan).toFloat())
        canvas.rotate((motion.rotation * fan).toFloat(), centerX, centerY)
        val scale = PastePlacesImportMotion.lerp(1.0, motion.scale, fan).toFloat()
        canvas.scale(scale, scale, centerX, centerY)
        paint.resetFill(Color.WHITE, 1f)
        paint.isFilterBitmap = true
        rect.set(left, top, left + width, top + height)
        canvas.drawBitmap(bitmap, null, rect, paint)
        canvas.restore()
    }

    private fun drawHeadlineAndHints(canvas: Canvas, layout: Layout, time: Double) {
        drawTextBlock(canvas, layout, Type.h1)
        drawPin(canvas, layout, 1f, 0f)
        Type.hint.lines.forEach { line ->
            drawWords(canvas, layout, Type.hint, line, 1f, 0f)
        }
        drawTextBlock(canvas, layout, Type.tip)
        paint.resetFill(Type.tip.color, 1f)
        val dash = Type.tipDash
        canvas.drawRect(layout.x(dash.x), layout.y(dash.y), layout.x(dash.x + dash.w), layout.y(dash.y + dash.h), paint)
        val underline = PastePlacesImportTheme.tipUnderline
        val underlineProgress = PastePlacesImportMotion.tipLineAt(time, CITY_PASS).toFloat()
        if (underlineProgress > 0f) {
            paint.resetFill(MotionColors.detect, 1f)
            rect.set(
                layout.x(underline.x),
                layout.y(underline.y),
                layout.x(underline.x) + layout.px(underline.w) * underlineProgress,
                layout.y(underline.y + underline.h),
            )
            canvas.drawRoundRect(rect, layout.px(underline.h), layout.px(underline.h), paint)
        }
    }

    /** The text area as paper: the card, the scan line, the placeholder, the note and the paste control. */
    fun drawCardFace(canvas: Canvas, layout: Layout, time: Double, pasteAmount: Double, hasDraft: Boolean) {
        val card = PastePlacesImportTheme.Box.card
        val left = layout.x(card.x)
        val top = layout.y(card.y)
        val right = layout.x(card.x + card.w)
        val bottom = layout.y(card.y + card.h)
        val radius = layout.px(PastePlacesImportTheme.Radius.card)
        rect.set(left, top, right, bottom)
        paint.resetFill(Colors.white, 1f)
        canvas.drawRoundRect(rect, radius, radius, paint)
        // The hairline sits inside the card's edge.
        val hairline = layout.px(PastePlacesImportTheme.HAIRLINE)
        paint.resetStroke(Colors.cardBorder, 1f, hairline)
        rect.set(left + hairline / 2f, top + hairline / 2f, right - hairline / 2f, bottom - hairline / 2f)
        canvas.drawRoundRect(rect, radius - hairline / 2f, radius - hairline / 2f, paint)

        rect.set(left, top, right, bottom)
        drawScan(canvas, layout, time, rect, radius)
        val placeholder = if (hasDraft) 0f else PastePlacesImportMotion.placeholderAt(time).toFloat()
        Type.placeholder.lines.forEach { line ->
            drawWords(canvas, layout, Type.placeholder, line, placeholder, 0f)
        }
        drawNote(canvas, layout, time)
        drawPaste(canvas, layout, pasteAmount)
    }

    private fun drawScan(canvas: Canvas, layout: Layout, time: Double, card: RectF, radius: Float) {
        val scan = PastePlacesImportMotion.scanAt(time)
        if (scan.opacity <= 0.0) return
        val scanY = layout.y(PastePlacesImportMotion.lerp(Note.scanTop.toDouble(), Note.scanBottom.toDouble(), scan.progress).toFloat())
        val trailTop = scanY - layout.px(Note.scanTrail)
        val trailBottom = scanY + layout.px(Note.scanStroke)
        canvas.save()
        clipRoundRect(canvas, card, radius)
        paint.resetFill(Color.WHITE, 1f)
        paint.shader = LinearGradient(
            0f,
            trailTop,
            0f,
            trailBottom,
            Color.TRANSPARENT,
            withAlpha(MotionColors.detect, (0.12 * scan.opacity).toFloat()),
            Shader.TileMode.CLAMP,
        )
        paint.alpha = 255
        canvas.drawRect(card.left, trailTop, card.right, trailBottom, paint)
        paint.shader = null
        paint.resetFill(MotionColors.detect, (0.7 * scan.opacity).toFloat())
        canvas.drawRect(card.left, scanY, card.right, scanY + layout.px(Note.scanStroke), paint)
        canvas.restore()
    }

    private fun drawNote(canvas: Canvas, layout: Layout, time: Double) {
        configureText(assets.textTypeface, Note.size * layout.k, 0f, MotionColors.noteInk, 1f)
        PastePlacesImportTheme.NOTE.forEachIndexed { index, line ->
            val progress = PastePlacesImportMotion.noteLineAt(time, index)
            if (progress <= 0.0) return@forEachIndexed
            val shift = ((1.0 - progress) * NoteIn.rise * layout.k).toFloat()
            val baseline = layout.y(Note.firstBase + index * Note.pitch) + shift
            val lineBox = layout.lineBox(Note.size, Note.firstBase + index * Note.pitch)
            val lineTop = lineBox.top + shift
            val preWidth = text.measureText(line.pre)
            val wordWidth = text.measureText(line.place)
            val wordLeft = layout.x(Note.left) + preWidth
            val mark = if (line.city) {
                PastePlacesImportMotion.cityLineAt(time, PASSES[index])
            } else {
                PastePlacesImportMotion.highlightAt(time, PASSES[index])
            }
            if (mark > 0.0) {
                if (line.city) {
                    paint.resetFill(MotionColors.detect, progress.toFloat())
                    val underlineTop = lineTop + lineBox.baseIn + layout.px(Note.underline.gap)
                    rect.set(wordLeft, underlineTop, wordLeft + wordWidth * mark.toFloat(), underlineTop + layout.px(Note.underline.stroke))
                    canvas.drawRoundRect(rect, layout.px(Note.underline.stroke), layout.px(Note.underline.stroke), paint)
                } else {
                    paint.resetFill(MotionColors.marker, progress.toFloat())
                    val markerLeft = wordLeft - layout.px(Note.marker.over)
                    val markerWidth = wordWidth + layout.px(Note.marker.over * 2f)
                    val markerTop = lineTop + lineBox.height * Note.marker.top
                    val markerHeight = lineBox.height * (Note.marker.bottom - Note.marker.top)
                    rect.set(markerLeft, markerTop, markerLeft + markerWidth * mark.toFloat(), markerTop + markerHeight)
                    canvas.drawRoundRect(rect, layout.px(Note.marker.radius), layout.px(Note.marker.radius), paint)
                }
            }

            configureText(assets.textTypeface, Note.size * layout.k, 0f, MotionColors.noteInk, progress.toFloat())
            canvas.drawText(line.pre, layout.x(Note.left), baseline, text)
            val wordOpacity = if (line.city) 1.0 else PastePlacesImportMotion.wordAt(time, PASSES[index])
            text.alpha = (255.0 * progress * wordOpacity).roundToInt().coerceIn(0, 255)
            canvas.drawText(line.place, wordLeft, baseline, text)
            text.alpha = (255.0 * progress).roundToInt().coerceIn(0, 255)
            canvas.drawText(line.post, wordLeft + wordWidth, baseline, text)
        }
    }

    private fun drawPaste(canvas: Canvas, layout: Layout, amount: Double) {
        val scale = PastePlacesImportMotion.lerp(1.0, Press.scale, amount).toFloat()
        val centerX = layout.x(603f)
        val centerY = layout.y(2031f)
        canvas.save()
        canvas.scale(scale, scale, centerX, centerY)
        val paste = Type.paste
        configureText(assets.systemExtraBold, paste.size * layout.k, paste.letterSpacing * layout.k, paste.color, 1f)
        canvas.drawText(paste.text, layout.x(requireNotNull(paste.left)), layout.y(paste.baseline), text)
        drawClipboard(canvas, layout)
        canvas.restore()
    }

    private fun drawClipboard(canvas: Canvas, layout: Layout) {
        val clip = PastePlacesImportTheme.Box.clip
        val stroke = layout.px(3.8f)
        paint.resetStroke(Colors.glyph, 1f, stroke)
        rect.set(
            layout.x(clip.x + 1.9f),
            layout.y(clip.y + 5.9f),
            layout.x(clip.x + clip.w - 1.9f),
            layout.y(clip.y + clip.h - 1.9f),
        )
        canvas.drawRoundRect(rect, layout.px(5f), layout.px(5f), paint)
        paint.resetFill(Colors.white, 1f)
        rect.set(
            layout.x(clip.x + 8.9f),
            layout.y(clip.y + 1.9f),
            layout.x(clip.x + clip.w - 8.9f),
            layout.y(clip.y + 10.6f),
        )
        canvas.drawRoundRect(rect, layout.px(3f), layout.px(3f), paint)
        paint.resetStroke(Colors.glyph, 1f, stroke)
        canvas.drawRoundRect(rect, layout.px(3f), layout.px(3f), paint)
    }

    private fun drawCounter(canvas: Canvas, layout: Layout, time: Double, draftCount: Int) {
        val counter = Type.counter
        val line = counter.lines.single()
        val number = line.words.first()
        if (draftCount > 0) {
            // The user's own text: the counter shows its real length.
            val label = "${grouped(draftCount)}/${grouped(PastePlacesImportTheme.Input.max)} characters"
            configureText(assets.textTypeface, counter.size * layout.k, number.letterSpacing * layout.k, counter.color, 1f)
            canvas.drawText(label, layout.x(number.x), layout.y(line.baseline), text)
            return
        }
        val master = PastePlacesImportMotion.countAt(time)
        if (master <= 0.0) {
            drawWords(canvas, layout, counter, line, 1f, 0f)
            return
        }

        val lineBox = layout.lineBox(counter.size, line.baseline)
        val fontSize = counter.size * layout.k
        val tracking = number.letterSpacing * layout.k
        val columnWidth = PastePlacesImportTheme.digitAdvance.text * fontSize + tracking
        val zeroWidth = (1139f / 2000f) * fontSize + tracking
        val targetDigits = PastePlacesImportTheme.NOTE_TEXT.length.toString().map { it.digitToInt() }
        val span = if (time >= PastePlacesImportMotion.countOut.start) PastePlacesImportMotion.countOut.duration else PastePlacesImportMotion.countIn.duration
        val stagger = if (time >= PastePlacesImportMotion.countOut.start) PastePlacesImportMotion.countOut.stagger else PastePlacesImportMotion.countIn.stagger
        val baseX = layout.x(number.x)
        configureText(assets.textTypeface, fontSize, tracking, counter.color, 1f)
        targetDigits.forEachIndexed { index, target ->
            val progress = PastePlacesImportMotion.slotAt(master, index, targetDigits.size, span, stagger)
            var leftShift = 0f
            repeat(index) { before ->
                val beforeProgress = PastePlacesImportMotion.slotAt(master, before, targetDigits.size, span, stagger)
                leftShift += (PastePlacesImportMotion.clamp01(beforeProgress * 3.0) * columnWidth).toFloat()
            }
            val growth = if (index == targetDigits.lastIndex) 1.0 else PastePlacesImportMotion.clamp01(progress * 3.0)
            val slotLeft = baseX - (columnWidth - zeroWidth) / 2f + leftShift
            canvas.save()
            canvas.clipRect(slotLeft, lineBox.top, slotLeft + columnWidth, lineBox.top + lineBox.height)
            val steps = target + 10 * index
            repeat(steps + 1) { digitIndex ->
                text.textAlign = Paint.Align.CENTER
                text.alpha = (growth * 255.0).roundToInt().coerceIn(0, 255)
                val baseline = layout.y(line.baseline) + ((digitIndex - progress * steps) * lineBox.height).toFloat()
                canvas.drawText((digitIndex % 10).toString(), slotLeft + columnWidth / 2f, baseline, text)
            }
            canvas.restore()
            text.textAlign = Paint.Align.LEFT
        }

        var numberShift = 0f
        repeat(targetDigits.size - 1) { index ->
            val progress = PastePlacesImportMotion.slotAt(master, index, targetDigits.size, span, stagger)
            numberShift += (PastePlacesImportMotion.clamp01(progress * 3.0) * columnWidth).toFloat()
        }
        configureText(assets.textTypeface, fontSize, tracking, counter.color, 1f)
        canvas.drawText(number.text.drop(1), baseX + zeroWidth + numberShift, layout.y(line.baseline), text)
        line.words.drop(1).forEach { word ->
            configureText(assets.textTypeface, fontSize, word.letterSpacing * layout.k, counter.color, 1f)
            canvas.drawText(word.text, layout.x(word.x) + numberShift, layout.y(line.baseline), text)
        }
    }

    /** "1,234": the counter's digits while the user types. */
    private fun grouped(value: Int): String {
        val digits = value.toString()
        val builder = StringBuilder()
        digits.forEachIndexed { index, char ->
            if (index > 0 && (digits.length - index) % 3 == 0) builder.append(',')
            builder.append(char)
        }
        return builder.toString()
    }

    private fun drawButton(
        canvas: Canvas,
        layout: Layout,
        time: Double,
        controller: PastePlacesImportController,
        draftFill: Float,
    ) {
        val button = PastePlacesImportTheme.Box.button
        val left = layout.x(button.x)
        val top = layout.y(button.y)
        val right = layout.x(button.x + button.w)
        val bottom = layout.y(button.y + button.h)
        val centerX = (left + right) / 2f
        val centerY = (top + bottom) / 2f
        val scripted = if (controller.scriptedPresses) PastePlacesImportMotion.pressAt(time, Presses.start) else 0.0
        val amount = max(controller.startPress, scripted)
        val scale = PastePlacesImportMotion.lerp(1.0, Press.scale, amount).toFloat()
        canvas.save()
        canvas.scale(scale, scale, centerX, centerY)

        val radius = layout.px(PastePlacesImportTheme.Radius.button)
        rect.set(left, top, right, bottom)
        paint.resetFill(Colors.buttonFill, 1f)
        canvas.drawRoundRect(rect, radius, radius, paint)
        canvas.save()
        clipRoundRect(canvas, rect, radius)
        val fill = max(PastePlacesImportMotion.fillAt(time).toFloat(), draftFill)
        paint.resetFill(MotionColors.buttonSolid, 1f)
        canvas.drawRect(left + (fill - 1f) * (right - left), top, left + fill * (right - left), bottom, paint)
        canvas.restore()

        drawButtonLabels(canvas, layout, time, centerX)
        canvas.restore()
    }

    private fun drawButtonLabels(canvas: Canvas, layout: Layout, time: Double, centerX: Float) {
        val start = Type.start
        val lineBox = layout.lineBox(start.size, start.baseline)
        val label = PastePlacesImportMotion.labelAt(time)
        val startOpacity = (1.0 - label.oldLabel).toFloat()
        val startShift = (-Label.shift * layout.k * label.oldLabel).toFloat()
        configureText(assets.extraTypeface, start.size * layout.k, start.letterSpacing * layout.k, start.color, startOpacity)
        text.textAlign = Paint.Align.CENTER
        canvas.drawText(start.text, centerX, layout.y(start.baseline) + startShift, text)
        text.textAlign = Paint.Align.LEFT

        val foundOpacity = label.newLabel.toFloat()
        if (foundOpacity <= 0f) return
        val foundShift = (Label.shift * layout.k * (1.0 - label.newLabel)).toFloat()
        configureText(assets.extraTypeface, start.size * layout.k, start.letterSpacing * layout.k, start.color, foundOpacity)
        val columnWidth = PastePlacesImportTheme.digitAdvance.extra * start.size * layout.k + start.letterSpacing * layout.k
        val preWidth = text.measureText(PastePlacesImportTheme.found.pre)
        val postWidth = text.measureText(PastePlacesImportTheme.found.post)
        val pluralWidth = text.measureText(PastePlacesImportTheme.found.plural)
        val totalWidth = preWidth + columnWidth + postWidth + pluralWidth
        var cursor = centerX - totalWidth / 2f
        val baseline = layout.y(start.baseline) + foundShift
        canvas.drawText(PastePlacesImportTheme.found.pre, cursor, baseline, text)
        cursor += preWidth

        val foundCount = max(1.0, PastePlacesImportMotion.foundAt(time, PLACE_PASSES.size))
        canvas.save()
        canvas.clipRect(cursor, lineBox.top + foundShift, cursor + columnWidth, lineBox.top + lineBox.height + foundShift)
        text.textAlign = Paint.Align.CENTER
        repeat(PLACE_PASSES.size) { index ->
            val digitBaseline = baseline + ((index - (foundCount - 1.0)) * lineBox.height).toFloat()
            canvas.drawText((index + 1).toString(), cursor + columnWidth / 2f, digitBaseline, text)
        }
        canvas.restore()
        text.textAlign = Paint.Align.LEFT
        cursor += columnWidth
        canvas.drawText(PastePlacesImportTheme.found.post, cursor, baseline, text)
        cursor += postWidth
        text.alpha = (255.0 * foundOpacity * PastePlacesImportMotion.clamp01(foundCount - 1.0)).roundToInt().coerceIn(0, 255)
        canvas.drawText(PastePlacesImportTheme.found.plural, cursor, baseline, text)
    }

    private fun drawClose(
        canvas: Canvas,
        layout: Layout,
        time: Double,
        controller: PastePlacesImportController,
    ) {
        val scripted = if (controller.scriptedPresses) PastePlacesImportMotion.pressAt(time, Presses.close) else 0.0
        val amount = max(controller.closePress, scripted)
        val scale = PastePlacesImportMotion.lerp(1.0, Press.closeScale, amount).toFloat()
        val opacity = PastePlacesImportMotion.lerp(1.0, Press.closeDim, amount).toFloat()
        val centerX = layout.x(1098f)
        val centerY = layout.y(224.5f)
        canvas.save()
        canvas.scale(scale, scale, centerX, centerY)
        paint.resetStroke(Colors.ink, opacity, layout.px(PastePlacesImportTheme.Box.close.stroke))
        paint.strokeCap = Paint.Cap.ROUND
        val close = PastePlacesImportTheme.Box.close
        canvas.drawLine(layout.x(close.x1), layout.y(close.y1), layout.x(close.x2), layout.y(close.y2), paint)
        canvas.drawLine(layout.x(close.x2), layout.y(close.y1), layout.x(close.x1), layout.y(close.y2), paint)
        canvas.restore()
    }

    /**
     * A place chip: starts as its word (label over the word, same size), hovers off the paper while it folds, flies on
     * an arc to its place on the map and drops onto it as a pin (gravity, bounces, squash). Transforms are about the
     * chip's centre, computed so the right point of the chip lands on the right point of the screen.
     */
    private fun drawChips(canvas: Canvas, layout: Layout, time: Double) {
        val card = PastePlacesImportTheme.Box.card
        layout.chips.forEachIndexed { index, chip ->
            val pass = PLACE_PASSES[index]
            val lift = pass + Detect.lift
            val pop = PastePlacesImportMotion.popAt(time, index)
            if (time < lift || pop >= 1.0) return@forEachIndexed
            val word = layout.wordBoxes[index + 1]
            val spot = PastePlacesImportTheme.SPOTS[index]
            val spotX = layout.x(card.x) + layout.px(spot.x)
            val spotY = layout.y(card.y) + layout.px(spot.y)
            val k = layout.k.toDouble()
            val startScale = (Note.size / Chip.size).toDouble()
            val mapScale = Chip.mapScale.toDouble()
            val cx = chip.width / 2.0
            val cy = chip.height / 2.0
            // Start: the label's left / middle on the word's, at the word's size, hovering.
            val hover = PastePlacesImportMotion.hoverAt(time, pass)
            val sA = startScale * (1.0 + PastePlacesImportMotion.Hover.grow * hover)
            val ax = word.left - sA * (chip.labelLeft - cx)
            val ay = word.centerY - PastePlacesImportMotion.Hover.rise * k * hover
            // End: the pin's foot right above the dot, at map scale.
            val qy = spotY - (PastePlacesImportTheme.Pin.dotR + PastePlacesImportTheme.Pin.gap) * k
            val bx = spotX - mapScale * (chip.pinCenterX - cx)
            val by = qy - mapScale * (chip.height - cy)
            val f = PastePlacesImportMotion.flyAt(time, index)
            val fc = PastePlacesImportMotion.clamp01(f)
            val arc = sin(PI * fc)
            val drop = PastePlacesImportMotion.dropAt(time, index)
            val s = PastePlacesImportMotion.lerp(sA, mapScale, fc) *
                (1.0 + PastePlacesImportMotion.Fly.grow * arc) * (1.0 - PastePlacesImportMotion.Pop.shrink * pop)
            val sx = s * (1.0 + PastePlacesImportMotion.Drop.squashX * drop.imp)
            val sy = s * (1.0 - PastePlacesImportMotion.Drop.squashY * drop.imp)
            val x = PastePlacesImportMotion.lerp(ax, bx, f)
            // The drop lowers the chip by its height above the map; the squash keeps its foot on the map.
            val y = PastePlacesImportMotion.lerp(ay, by - PastePlacesImportMotion.Drop.height * k * drop.h, f) -
                PastePlacesImportMotion.Fly.arc * k * arc + (s - sy) * cy - PastePlacesImportMotion.Pop.rise * k * pop
            val tilt = (if (index % 2 == 1) 1.0 else -1.0) * PastePlacesImportMotion.Fly.tilt * arc
            val pill = PastePlacesImportMotion.chipPillAt(time, pass)
            val air = maxOf(0.45 * hover * (1.0 - fc), sin(PI * fc), 0.6 * drop.h * fc)
            val opacity = (1.0 - pop).toFloat()

            canvas.save()
            canvas.translate(x.toFloat(), y.toFloat())
            canvas.rotate(tilt.toFloat())
            canvas.scale(sx.toFloat(), sy.toFloat())
            canvas.translate(-cx.toFloat(), -cy.toFloat())
            drawChipPill(canvas, layout, chip, (opacity * pill).toFloat(), air.toFloat())
            drawChipContent(canvas, layout, chip, (opacity * pill).toFloat(), opacity)
            canvas.restore()
        }
    }

    private fun drawChipPill(canvas: Canvas, layout: Layout, chip: ChipBox, opacity: Float, air: Float) {
        if (opacity <= 0f) return
        val radius = layout.px(Chip.radius)
        val blur = (2f + 9f * air) * layout.k
        val offsetY = (1f + 9f * air) * layout.k
        val shadowAlpha = (0.05f + 0.15f * air) * opacity
        rect.set(0f, 0f, chip.width, chip.height)
        paint.resetFill(MotionColors.chipFill, opacity)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            paint.setShadowLayer(blur, 0f, offsetY, withAlpha(MotionColors.chipShadow, shadowAlpha))
        } else {
            drawLegacyChipShadow(canvas, chip, radius, blur, offsetY, shadowAlpha)
            paint.resetFill(MotionColors.chipFill, opacity)
        }
        rect.set(0f, 0f, chip.width, chip.height)
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.clearShadowLayer()
        // The border sits inside the pill, as in React Native.
        val hairline = layout.px(PastePlacesImportTheme.HAIRLINE)
        paint.resetStroke(MotionColors.chipBorder, opacity, hairline)
        rect.set(hairline / 2f, hairline / 2f, chip.width - hairline / 2f, chip.height - hairline / 2f)
        canvas.drawRoundRect(rect, radius - hairline / 2f, radius - hairline / 2f, paint)
    }

    /** API 26–27 do not accelerate shape shadows; concentric shells keep the same animated extent. */
    private fun drawLegacyChipShadow(canvas: Canvas, chip: ChipBox, radius: Float, blur: Float, offsetY: Float, alpha: Float) {
        val shellOpacity = alpha / LEGACY_SHADOW_SHELLS
        repeat(LEGACY_SHADOW_SHELLS) { index ->
            val expansion = blur * (LEGACY_SHADOW_SHELLS - index).toFloat() / LEGACY_SHADOW_SHELLS
            paint.resetFill(MotionColors.chipShadow, shellOpacity)
            rect.set(-expansion, offsetY - expansion, chip.width + expansion, chip.height + offsetY + expansion)
            canvas.drawRoundRect(rect, radius + expansion, radius + expansion, paint)
        }
    }

    private fun drawChipContent(canvas: Canvas, layout: Layout, chip: ChipBox, pinOpacity: Float, labelOpacity: Float) {
        configureText(assets.system, Chip.pinSize * layout.k, 0f, Colors.ink, pinOpacity)
        val pinMetrics = text.fontMetrics
        val pinBaseline = chip.height / 2f - (pinMetrics.ascent + pinMetrics.descent) / 2f
        canvas.drawText(Type.pin.text, chip.pinLeft, pinBaseline, text)
        configureText(assets.textTypeface, Chip.size * layout.k, 0f, Colors.ink, labelOpacity)
        canvas.drawText(chip.text, chip.labelLeft, chip.labelBaseline, text)
    }

    private fun drawTextBlock(canvas: Canvas, layout: Layout, block: PastePlacesImportTheme.TextBlock) {
        block.lines.forEach { line ->
            drawWords(canvas, layout, block, line, 1f, 0f)
        }
    }

    private fun drawWords(
        canvas: Canvas,
        layout: Layout,
        block: PastePlacesImportTheme.TextBlock,
        line: PastePlacesImportTheme.WordLine,
        opacity: Float,
        translateY: Float,
    ) {
        if (opacity <= 0f) return
        val typeface = if (block.font == PastePlacesImportTheme.Font.extra) assets.extraTypeface else assets.textTypeface
        line.words.forEach { word ->
            configureText(typeface, block.size * layout.k, word.letterSpacing * layout.k, block.color, opacity)
            canvas.drawText(word.text, layout.x(word.x), layout.y(line.baseline) + translateY, text)
        }
    }

    private fun drawPin(canvas: Canvas, layout: Layout, opacity: Float, translateY: Float) {
        if (opacity <= 0f) return
        val pin = Type.pin
        configureText(assets.system, pin.size * layout.k, 0f, Colors.ink, opacity)
        val baseline = layout.y(pin.top) - text.fontMetrics.ascent + translateY
        canvas.drawText(pin.text, layout.x(pin.left), baseline, text)
    }

    private fun configureText(
        typeface: Typeface,
        size: Float,
        tracking: Float,
        @ColorInt color: Int,
        opacity: Float,
    ) {
        text.reset()
        text.isAntiAlias = true
        text.isSubpixelText = true
        text.typeface = typeface
        text.textSize = size
        text.letterSpacing = if (size == 0f) 0f else tracking / size
        text.color = color
        text.alpha = (opacity.coerceIn(0f, 1f) * 255f).roundToInt()
        text.textAlign = Paint.Align.LEFT
    }

    private fun layout(width: Float, height: Float, topInset: Float): Layout {
        val cached = cachedLayout
        if (cached != null && cached.width == width && cached.height == height && cached.topInset == topInset) return cached
        val next = buildLayout(width, height, topInset)
        cachedLayout = next
        return next
    }

    private fun buildLayout(width: Float, height: Float, topInset: Float): Layout {
        val unit = width / PastePlacesImportTheme.REF_W
        val fontScale = width / PastePlacesImportTheme.REF_PT
        val y0 = topInset - PastePlacesImportTheme.REF_INSET * unit
        val referenceHeight = (height - y0) / unit
        val pageColors = Colors.pageStops.map { it.second }.toIntArray()
        val pagePositions = Colors.pageStops.map { (position, _) ->
            (position / referenceHeight).coerceIn(0f, 1f)
        }.toFloatArray()
        val pageShader = LinearGradient(0f, y0, 0f, height, pageColors, pagePositions, Shader.TileMode.CLAMP)
        val bandShader = radialShader(unit, y0, Colors.glowBand)
        val cornerShader = radialShader(unit, y0, Colors.glowCorner)
        val layout = Layout(
            width,
            height,
            topInset,
            unit,
            fontScale,
            y0,
            pageShader,
            bandShader,
            cornerShader,
            emptyList(),
            emptyList(),
        )
        configureText(assets.textTypeface, Note.size * fontScale, 0f, MotionColors.noteInk, 1f)
        val wordBoxes = PastePlacesImportTheme.NOTE.mapIndexed { index, line ->
            val lineBox = layout.lineBox(Note.size, Note.firstBase + index * Note.pitch)
            val left = layout.x(Note.left) + text.measureText(line.pre)
            WordBox(left, lineBox.top, text.measureText(line.place), lineBox.height)
        }

        configureText(assets.textTypeface, Chip.size * fontScale, 0f, Colors.ink, 1f)
        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = assets.system
            textSize = Chip.pinSize * fontScale
        }
        val pinWidth = pinPaint.measureText(Type.pin.text)
        val labelHeight = round(Chip.size * fontScale * 1.4f)
        val chipBoxes = PLACE_PASSES.indices.map { index ->
            val place = PastePlacesImportTheme.NOTE[index + 1].place
            val labelLeft = layout.px(Chip.padLeft) + pinWidth + layout.px(Chip.pinGap)
            val height = layout.px(Chip.height)
            val baseIn = (labelHeight - (PastePlacesImportTheme.ASCENT + PastePlacesImportTheme.DESCENT) * Chip.size * fontScale) / 2f +
                PastePlacesImportTheme.ASCENT * Chip.size * fontScale
            ChipBox(
                text = place,
                width = labelLeft + text.measureText(place) + layout.px(Chip.padRight),
                height = height,
                pinLeft = layout.px(Chip.padLeft),
                pinCenterX = layout.px(Chip.padLeft) + pinWidth / 2f,
                labelLeft = labelLeft,
                labelBaseline = (height - labelHeight) / 2f + baseIn,
            )
        }
        val sized = layout.copy(wordBoxes = wordBoxes, chips = chipBoxes)
        sized.paper = PastePlacesImportPaper(sized) { canvas, time ->
            drawCardFace(canvas, sized, time, 0.0, false)
        }
        return sized
    }

    private fun radialShader(unit: Float, y0: Float, glow: PastePlacesImportTheme.Glow): RadialGradient {
        val centerColor = withAlpha(glow.color, glow.opacity)
        return RadialGradient(
            glow.cx * unit,
            glow.cy * unit + y0,
            glow.rx * unit,
            centerColor,
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
    }

    private fun Paint.resetFill(@ColorInt color: Int, opacity: Float) {
        reset()
        isAntiAlias = true
        isFilterBitmap = true
        style = Paint.Style.FILL
        this.color = color
        alpha = (opacity.coerceIn(0f, 1f) * Color.alpha(color)).roundToInt()
        shader = null
        maskFilter = null
    }

    private fun Paint.resetStroke(@ColorInt color: Int, opacity: Float, width: Float) {
        resetFill(color, opacity)
        style = Paint.Style.STROKE
        strokeWidth = width
    }

    private fun clipRoundRect(canvas: Canvas, bounds: RectF, radius: Float) {
        clipPath.reset()
        clipPath.addRoundRect(bounds, radius, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)
    }

    @ColorInt
    private fun withAlpha(@ColorInt color: Int, opacity: Float): Int {
        return Color.argb((opacity.coerceIn(0f, 1f) * 255f).roundToInt(), Color.red(color), Color.green(color), Color.blue(color))
    }

    companion object {
        private const val LEGACY_SHADOW_SHELLS = 8
        private val PASSES = PastePlacesImportTheme.NOTE.indices.map { index ->
            val lineMid = Note.firstBase + index * Note.pitch - 0.3 * Note.size * 3.0
            PastePlacesImportMotion.passAt((lineMid - Note.scanTop) / (Note.scanBottom - Note.scanTop))
        }
        private val CITY_PASS = PASSES.first()
        private val PLACE_PASSES = PASSES.drop(1)
    }
}
