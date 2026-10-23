package com.ngocdevv.nativecook.blocks.lists.widgetstamppicker

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.roundToInt

private val Background = 0xFFFFFCFF.toInt()
private val Ink = 0xFF161C33.toInt()
private val BackBackground = 0xFFF1F5FB.toInt()
private val SkipBackground = 0xFFFFE8FF.toInt()
private val SkipInk = 0xFFE0129E.toInt()
private val CaptionInk = 0xFF4E4B50.toInt()
private val HintInk = 0xFFDCDFE7.toInt()
private val DoorInk = 0xFFE3E8F0.toInt()
private val DoorEdge = 0xFFCBD3DF.toInt()
private val MascotOrange = 0xFFFFA51A.toInt()
private val MascotGreen = 0xFF26B36B.toInt()
private val MascotPink = 0xFFFF3FC4.toInt()

private const val SideMargin = 17f
private const val BackSize = 38f
private const val NavCenterY = 4.5f
private const val SkipWidth = 74f
private const val SkipHeight = 39f
private const val MascotLeft = 167f
private const val MascotTop = 89f
private const val TitleTop = 189.5f
private const val TitleSize = 31.9f
private const val TitleLineHeight = 37.5f
private const val TitleStretch = 1.125f
private const val StampCenterY = 478.5f
private const val StampWidth = 228f
private const val StampHeight = 287f
private const val HintCenterY = 667.8f
private const val HintWidth = 50f
private const val HintHeight = 28f
private const val CaptionTop = 704f
private const val CaptionSize = 18f
private const val CaptionLineHeight = 24f
private const val StampNotchRadius = 7.8f
private const val StampNotchPitch = 19.2f
private const val StampNotchesAcross = 11
private const val StampNotchesDown = 15
private const val PanelLeft = 33f
private const val PanelTop = 30f
private const val PanelWidth = 162f
private const val PanelHeight = 183f
private const val PanelRadius = 12f
private const val PanelBorder = 2f
private const val LabelCenterY = 243f
private const val LabelSize = 27.5f
private const val LabelStretch = 1.09f

private data class WidgetStampTone(
    val tint: Int,
    val glow: Int,
    val border: Int,
    val label: Int,
    val shade: Int,
)

private data class WidgetStampVisual(
    val id: String,
    val label: String,
    val bitmap: Bitmap,
    val tone: WidgetStampTone,
)

data class WidgetStampSelection(val index: Int, val id: String, val label: String)

private class WidgetStampFonts(context: Context) {
    val display: Typeface = ResourcesCompat.getFont(context, R.font.widgetstamppicker_titan_one_regular)
        ?: Typeface.create("sans-serif", Typeface.BOLD)
    val text: Typeface = ResourcesCompat.getFont(context, R.font.widgetstamppicker_inter_regular)
        ?: Typeface.create("sans-serif", Typeface.NORMAL)
}

/** Static resources and native paints shared by the one Canvas renderer. */
private class WidgetStampPickerPainter(resources: Resources, fonts: WidgetStampFonts) {
    private val bodyPath = stampPath()
    private val panelClip = Path().apply {
        addRoundRect(RectF(PanelLeft, PanelTop, PanelLeft + PanelWidth, PanelTop + PanelHeight), PanelRadius, PanelRadius, Path.Direction.CW)
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fonts = fonts

    val stamps: List<WidgetStampVisual> = listOf(
        WidgetStampVisual(
            id = "organisation",
            label = "Organisation",
            bitmap = BitmapFactory.decodeResource(resources, R.drawable.widgetstamppicker_art_organisation),
            tone = WidgetStampTone(0xFFEEF9F4.toInt(), 0xFFA9DCC6.toInt(), 0xFF2FB47C.toInt(), 0xFF006539.toInt(), 0xFF1E8A5E.toInt()),
        ),
        WidgetStampVisual(
            id = "mixed",
            label = "Mixed",
            bitmap = BitmapFactory.decodeResource(resources, R.drawable.widgetstamppicker_art_mixed),
            tone = WidgetStampTone(0xFFFEECE9.toInt(), 0xFFEDB9B4.toInt(), 0xFFF8544D.toInt(), 0xFF490000.toInt(), 0xFF4A1820.toInt()),
        ),
        WidgetStampVisual(
            id = "games",
            label = "Games",
            bitmap = BitmapFactory.decodeResource(resources, R.drawable.widgetstamppicker_art_games),
            tone = WidgetStampTone(0xFFE8F0FE.toInt(), 0xFFB3C9F5.toInt(), 0xFF304FD9.toInt(), 0xFF0F35B5.toInt(), 0xFF3A5596.toInt()),
        ),
        WidgetStampVisual(
            id = "daily",
            label = "Daily",
            bitmap = BitmapFactory.decodeResource(resources, R.drawable.widgetstamppicker_art_daily),
            tone = WidgetStampTone(0xFFFEF9E8.toInt(), 0xFFF5D9A2.toInt(), 0xFFE58A00.toInt(), 0xFF8F4E00.toInt(), 0xFF9A6A20.toInt()),
        ),
    )

    fun drawScene(canvas: Canvas, motion: WidgetStampPickerMotion, width: Float, top: Float) {
        canvas.drawColor(Background)
        drawWheel(canvas, motion, width / 2f, top + StampCenterY)
        drawNavigation(canvas, width, top)
        drawMascot(canvas, width / 2f - 201f + MascotLeft, top + MascotTop, motion)
        drawTitle(canvas, width / 2f, top + TitleTop)
        drawHint(canvas, width / 2f - HintWidth / 2f, top + HintCenterY - HintHeight / 2f)
        drawCaption(canvas, width / 2f, top + CaptionTop)
    }

    private fun drawWheel(canvas: Canvas, motion: WidgetStampPickerMotion, centerX: Float, centerY: Float) {
        val progress = (motion.reducedFadeElapsedMs / WIDGET_STAMP_FADE_MS).toFloat().coerceIn(0f, 1f)
        val alpha = if (progress < 0.5f) 1f - progress * 2f else (progress - 0.5f) * 2f
        if (alpha >= 0.999f) {
            drawWheelAt(canvas, centerX, centerY, motion.position, 1f)
            return
        }
        val layer = canvas.saveLayerAlpha(null, (alpha * 255f).roundToInt().coerceIn(0, 255))
        drawWheelAt(canvas, centerX, centerY, motion.position, 1f)
        canvas.restoreToCount(layer)
    }

    private fun drawWheelAt(canvas: Canvas, centerX: Float, centerY: Float, position: Double, alpha: Float) {
        val order = (0 until stamps.size).sortedByDescending { abs(it.toDouble() - position) }
        for (index in order) {
            val signed = index.toDouble() - position
            val distance = abs(signed)
            if (distance > WIDGET_STAMP_HIDE_BEYOND) continue
            val rise = widgetInterpolate(distance, WidgetWheelTable, WidgetWheelRise).toFloat()
            val tilt = widgetInterpolate(distance, WidgetWheelTable, WidgetWheelTilt).toFloat()
            val stampScale = widgetInterpolate(distance, WidgetWheelTable, WidgetWheelScale).toFloat()
            val side = if (signed < 0.0) -1f else 1f
            canvas.save()
            canvas.translate(centerX + (signed * WIDGET_STAMP_STEP_X).toFloat(), centerY - rise)
            canvas.rotate(-side * tilt)
            canvas.scale(stampScale, stampScale)
            canvas.translate(-StampWidth / 2f, -StampHeight / 2f)
            drawStamp(canvas, stamps[index], alpha)
            canvas.restore()
        }
    }

    private fun drawStamp(canvas: Canvas, stamp: WidgetStampVisual, alpha: Float) {
        val tone = stamp.tone
        drawBodyShadow(canvas, tone.tint, tone.glow, tone.shade, alpha)
        bodyPaint.style = Paint.Style.FILL
        bodyPaint.color = tone.tint
        bodyPaint.alpha = (255f * alpha).roundToInt().coerceIn(0, 255)
        canvas.drawPath(bodyPath, bodyPaint)

        canvas.save()
        canvas.clipPath(panelClip)
        bitmapPaint.alpha = (255f * alpha).roundToInt().coerceIn(0, 255)
        canvas.drawBitmap(
            stamp.bitmap,
            centeredCoverSource(stamp.bitmap, PanelWidth, PanelHeight),
            RectF(PanelLeft, PanelTop, PanelLeft + PanelWidth, PanelTop + PanelHeight),
            bitmapPaint,
        )
        canvas.restore()

        panelPaint.style = Paint.Style.STROKE
        panelPaint.strokeWidth = PanelBorder
        panelPaint.color = tone.border
        panelPaint.alpha = (255f * alpha).roundToInt().coerceIn(0, 255)
        canvas.drawRoundRect(PanelLeft, PanelTop, PanelLeft + PanelWidth, PanelTop + PanelHeight, PanelRadius, PanelRadius, panelPaint)
        drawText(canvas, stamp.label, StampWidth / 2f, LabelCenterY, LabelSize, tone.label, fonts.display, centered = true, anchorTop = false, scaleX = LabelStretch, alpha = alpha)
    }

    private fun drawBodyShadow(canvas: Canvas, tint: Int, glow: Int, shade: Int, alpha: Float) {
        bodyPaint.style = Paint.Style.FILL
        bodyPaint.color = tint
        bodyPaint.alpha = (255f * alpha).roundToInt().coerceIn(0, 255)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            bodyPaint.setShadowLayer(7f, 0f, 0f, withAlpha(AndroidColor.WHITE, alpha))
            canvas.drawPath(bodyPath, bodyPaint)
            bodyPaint.setShadowLayer(7f, 0f, 5f, withAlpha(glow, alpha))
            canvas.drawPath(bodyPath, bodyPaint)
            bodyPaint.setShadowLayer(4f, 0f, 3f, withAlpha(shade, alpha * 0.3f))
            canvas.drawPath(bodyPath, bodyPaint)
            bodyPaint.clearShadowLayer()
        } else {
            // API 26–27 do not blur hardware path shadows; retain the layered depth cue.
            bodyPaint.color = glow
            bodyPaint.alpha = (255f * alpha * 0.18f).roundToInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(0f, 5f)
            canvas.drawPath(bodyPath, bodyPaint)
            canvas.restore()
            bodyPaint.color = shade
            bodyPaint.alpha = (255f * alpha * 0.12f).roundToInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(0f, 3f)
            canvas.drawPath(bodyPath, bodyPaint)
            canvas.restore()
        }
    }

    private fun drawNavigation(canvas: Canvas, width: Float, top: Float) {
        bodyPaint.style = Paint.Style.FILL
        bodyPaint.color = BackBackground
        bodyPaint.alpha = 255
        canvas.drawCircle(SideMargin + BackSize / 2f, top + NavCenterY, BackSize / 2f, bodyPaint)
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = 2.2f
        linePaint.strokeCap = Paint.Cap.ROUND
        linePaint.strokeJoin = Paint.Join.ROUND
        linePaint.color = Ink
        linePaint.alpha = 255
        val chevron = Path().apply {
            moveTo(SideMargin + 26f, top + NavCenterY - 5.1f)
            lineTo(SideMargin + 19.5f, top + NavCenterY)
            lineTo(SideMargin + 26f, top + NavCenterY + 5.1f)
        }
        canvas.drawPath(chevron, linePaint)

        bodyPaint.color = SkipBackground
        canvas.drawRoundRect(width - SideMargin - SkipWidth, top + NavCenterY - SkipHeight / 2f, width - SideMargin, top + NavCenterY + SkipHeight / 2f, SkipHeight / 2f, SkipHeight / 2f, bodyPaint)
        drawText(canvas, "Skip", width - SideMargin - SkipWidth / 2f, top + NavCenterY, 17f, SkipInk, fonts.display, centered = true, anchorTop = false, scaleX = 1f, alpha = 1f)
    }

    private fun drawMascot(canvas: Canvas, left: Float, top: Float, motion: WidgetStampPickerMotion) {
        val eyes = widgetEyesAt(motion.autoTimeMs)
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = 1.2f
        linePaint.strokeCap = Paint.Cap.ROUND
        linePaint.color = 0xFFEEF0F4.toInt()
        linePaint.alpha = 255
        canvas.drawLine(left, top + 74.5f, left + 18f, top + 74.5f, linePaint)

        drawBlob(canvas, left + 52f, top + 14f, MascotOrange, eyes[0], tail = false)
        drawBlob(canvas, left + 50f, top + 38f, MascotGreen, eyes[1], tail = true)
        drawBlob(canvas, left + 51f, top + 62f, MascotPink, eyes[2], tail = false)

        val door = Path().apply {
            moveTo(left + 14f, top + 9f)
            quadTo(left + 14f, top + 4f, left + 19f, top + 3.6f)
            lineTo(left + 39.5f, top + 1.6f)
            lineTo(left + 41f, top + 1.6f)
            lineTo(left + 41f, top + 76f)
            lineTo(left + 39.5f, top + 76f)
            lineTo(left + 17f, top + 73.6f)
            quadTo(left + 14f, top + 73.3f, left + 14f, top + 70f)
            close()
        }
        bodyPaint.style = Paint.Style.FILL
        bodyPaint.color = DoorInk
        bodyPaint.alpha = 255
        canvas.drawPath(door, bodyPaint)
        bodyPaint.color = DoorEdge
        canvas.drawRect(left + 39.5f, top + 1f, left + 41.5f, top + 76f, bodyPaint)
        bodyPaint.color = AndroidColor.WHITE
        canvas.drawCircle(left + 36.2f, top + 40f, 1.1f, bodyPaint)
    }

    private fun drawBlob(canvas: Canvas, cx: Float, cy: Float, color: Int, closed: Boolean, tail: Boolean) {
        val puffs = arrayOf(
            floatArrayOf(0f, -6.5f, 7f),
            floatArrayOf(6.5f, -2f, 6.5f),
            floatArrayOf(5f, 6f, 6.5f),
            floatArrayOf(-4f, 6.5f, 6.5f),
            floatArrayOf(-6f, -2f, 6.5f),
        )
        bodyPaint.style = Paint.Style.FILL
        bodyPaint.color = color
        bodyPaint.alpha = 255
        puffs.forEach { puff -> canvas.drawCircle(cx + puff[0], cy + puff[1], puff[2], bodyPaint) }
        canvas.drawCircle(cx, cy, 7.5f, bodyPaint)
        if (tail) {
            val tailPath = Path().apply {
                moveTo(cx - 6f, cy + 4f)
                lineTo(cx - 9f, cy + 11f)
                lineTo(cx - 1f, cy + 7f)
                close()
            }
            canvas.drawPath(tailPath, bodyPaint)
        }
        bodyPaint.color = AndroidColor.WHITE
        if (closed) {
            canvas.drawRoundRect(cx - 3.4f, cy + 1.5f, cx, cy + 2.9f, 0.7f, 0.7f, bodyPaint)
            canvas.drawRoundRect(cx + 2.3f, cy + 1.5f, cx + 5.7f, cy + 2.9f, 0.7f, 0.7f, bodyPaint)
        } else {
            canvas.drawOval(cx - 3.35f, cy - 0.15f, cx - 0.05f, cy + 4.55f, bodyPaint)
            canvas.drawOval(cx + 2.35f, cy - 0.15f, cx + 5.65f, cy + 4.55f, bodyPaint)
        }
    }

    private fun drawTitle(canvas: Canvas, centerX: Float, top: Float) {
        drawText(canvas, "Choose your", centerX, top, TitleSize, Ink, fonts.display, centered = true, anchorTop = true, scaleX = TitleStretch, alpha = 1f)
        drawText(canvas, "starting widgets", centerX, top + TitleLineHeight, TitleSize, Ink, fonts.display, centered = true, anchorTop = true, scaleX = TitleStretch, alpha = 1f)
    }

    private fun drawHint(canvas: Canvas, left: Float, top: Float) {
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = 2.4f
        linePaint.strokeCap = Paint.Cap.ROUND
        linePaint.strokeJoin = Paint.Join.ROUND
        linePaint.color = HintInk
        linePaint.alpha = 255
        val track = Path().apply {
            moveTo(left + 2.5f, top + 4.6f)
            quadTo(left + 25f, top + 22f, left + 47.5f, top + 4.6f)
        }
        canvas.drawPath(track, linePaint)
        val arrows = Path().apply {
            moveTo(left + 1.2f, top + 1.4f)
            lineTo(left + 2.5f, top + 4.6f)
            lineTo(left + 6f, top + 4.1f)
            moveTo(left + 48.8f, top + 1.4f)
            lineTo(left + 47.5f, top + 4.6f)
            lineTo(left + 44f, top + 4.1f)
        }
        canvas.drawPath(arrows, linePaint)
        canvas.save()
        canvas.rotate(-22f, left + 28f, top + 21f)
        bodyPaint.style = Paint.Style.FILL
        bodyPaint.color = HintInk
        canvas.drawRoundRect(left + 23.4f, top + 12.2f, left + 27f, top + 21.2f, 1.8f, 1.8f, bodyPaint)
        canvas.drawRoundRect(left + 22.6f, top + 18f, left + 34.2f, top + 26.6f, 3.4f, 3.4f, bodyPaint)
        canvas.drawRoundRect(left + 20.4f, top + 19.6f, left + 24.4f, top + 24.6f, 2f, 2f, bodyPaint)
        canvas.restore()
    }

    private fun drawCaption(canvas: Canvas, centerX: Float, top: Float) {
        drawText(canvas, "Swipe through and pick the pack that best", centerX, top, CaptionSize, CaptionInk, fonts.text, centered = true, anchorTop = true, scaleX = 1f, alpha = 1f)
        drawText(canvas, "suits you.", centerX, top + CaptionLineHeight, CaptionSize, CaptionInk, fonts.text, centered = true, anchorTop = true, scaleX = 1f, alpha = 1f)
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        typeface: Typeface,
        centered: Boolean,
        anchorTop: Boolean,
        scaleX: Float,
        alpha: Float,
    ) {
        textPaint.typeface = typeface
        textPaint.textSize = size
        textPaint.color = color
        textPaint.alpha = (255f * alpha).roundToInt().coerceIn(0, 255)
        textPaint.textAlign = if (centered) Paint.Align.CENTER else Paint.Align.LEFT
        val metrics = textPaint.fontMetrics
        val baseline = if (anchorTop) y - metrics.ascent else y - (metrics.ascent + metrics.descent) / 2f
        canvas.save()
        if (scaleX != 1f) canvas.scale(scaleX, 1f, x, y)
        canvas.drawText(text, x, baseline, textPaint)
        canvas.restore()
    }

    private fun stampPath(): Path {
        // RN's Canvas is offset by -16 pt inside the box, so its body begins at the box edge.
        val body = Path().apply { addRect(0f, 0f, StampWidth, StampHeight, Path.Direction.CW) }
        val notches = Path()
        fun along(length: Float, count: Int, place: (Float) -> Unit) {
            val start = (length - (count - 1) * StampNotchPitch) / 2f
            repeat(count) { index -> place(start + index * StampNotchPitch) }
        }
        along(StampWidth, StampNotchesAcross) { distance ->
            notches.addCircle(distance, 0f, StampNotchRadius, Path.Direction.CW)
            notches.addCircle(distance, StampHeight, StampNotchRadius, Path.Direction.CW)
        }
        along(StampHeight, StampNotchesDown) { distance ->
            notches.addCircle(0f, distance, StampNotchRadius, Path.Direction.CW)
            notches.addCircle(StampWidth, distance, StampNotchRadius, Path.Direction.CW)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) body.op(notches, Path.Op.DIFFERENCE)
        return body
    }

    private fun centeredCoverSource(bitmap: Bitmap, targetWidth: Float, targetHeight: Float): Rect {
        val sourceWidth = bitmap.width
        val sourceHeight = bitmap.height
        val sourceAspect = sourceWidth.toFloat() / sourceHeight
        val targetAspect = targetWidth / targetHeight
        return if (sourceAspect > targetAspect) {
            val cropWidth = (sourceHeight * targetAspect).roundToInt().coerceAtMost(sourceWidth)
            val left = (sourceWidth - cropWidth) / 2
            Rect(left, 0, left + cropWidth, sourceHeight)
        } else {
            val cropHeight = (sourceWidth / targetAspect).roundToInt().coerceAtMost(sourceHeight)
            val top = (sourceHeight - cropHeight) / 2
            Rect(0, top, sourceWidth, top + cropHeight)
        }
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val scaled = (AndroidColor.alpha(color) / 255f * alpha * 255f).roundToInt().coerceIn(0, 255)
        return AndroidColor.argb(scaled, AndroidColor.red(color), AndroidColor.green(color), AndroidColor.blue(color))
    }
}

@Composable
fun WidgetStampPicker(
    autoPlay: Boolean = false,
    onBack: (() -> Unit)? = null,
    onSkip: ((WidgetStampSelection) -> Unit)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val reducedMotion = rememberReducedMotion()
    val motion = remember(autoPlay) { WidgetStampPickerMotion(autoPlay) }
    val fonts = remember(context) { WidgetStampFonts(context) }
    val painter = remember(context, fonts) { WidgetStampPickerPainter(context.resources, fonts) }
    var driverGeneration by remember { mutableIntStateOf(0) }
    val scriptedAutoPlay = autoPlay && !reducedMotion

    LaunchedEffect(motion, autoPlay, reducedMotion, driverGeneration) {
        motion.configureAutoPlay(autoPlay, reducedMotion)
        if (!motion.needsFrames(autoPlay, reducedMotion)) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (motion.needsFrames(autoPlay, reducedMotion)) {
            val now = withFrameNanos { it }
            val deltaMs = ((now - previous) / 1_000_000.0).coerceAtLeast(0.0)
            if (motion.advance(deltaMs, autoPlay, reducedMotion)) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            previous = now
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(Background))
            .semantics {
                contentDescription = "Widget pack"
                stateDescription = "${painter.stamps[motion.selectedIndex].label}, ${motion.selectedIndex + 1} of ${painter.stamps.size}"
                customActions = listOf(
                    CustomAccessibilityAction("Next widget pack") {
                        motion.beginTouch()
                        motion.tapNeighbour(1, reducedMotion)
                        motion.endTouch(scriptedAutoPlay)
                        driverGeneration += 1
                        true
                    },
                    CustomAccessibilityAction("Previous widget pack") {
                        motion.beginTouch()
                        motion.tapNeighbour(-1, reducedMotion)
                        motion.endTouch(scriptedAutoPlay)
                        driverGeneration += 1
                        true
                    },
                )
            },
    ) {
        val unitPx = density.density
        val safeTop = WindowInsets.safeDrawing.getTop(density) / density.density
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(motion, autoPlay, reducedMotion, unitPx, maxWidth.value) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        motion.beginTouch()
                        driverGeneration += 1
                        try {
                            val startX = down.position.x
                            var lastX = startX
                            var lastTime = down.uptimeMillis
                            var velocityX = 0.0
                            var dragging = false
                            var cancelled = false
                            var released = false
                            while (!released) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.count { it.pressed } > 1) {
                                    cancelled = true
                                    break
                                }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    released = true
                                    if (dragging) {
                                        motion.release(velocityX, reducedMotion)
                                    } else if (!cancelled) {
                                        val tapDx = change.position.x / unitPx - maxWidth.value / 2f
                                        if (abs(tapDx) >= StampWidth / 2f) {
                                            motion.tapNeighbour(if (tapDx > 0f) 1 else -1, reducedMotion)
                                        }
                                    }
                                    continue
                                }
                                val dx = change.position.x - startX
                                val dy = change.position.y - down.position.y
                                if (!dragging && abs(dy) > 24f * unitPx) cancelled = true
                                if (!cancelled && !dragging && abs(dx) >= 8f * unitPx) {
                                    dragging = true
                                    motion.beginDrag()
                                }
                                if (dragging) {
                                    val elapsedMs = (change.uptimeMillis - lastTime).coerceAtLeast(1L)
                                    velocityX = ((change.position.x - lastX) / unitPx) / (elapsedMs / 1_000.0)
                                    motion.updateDrag(((change.position.x - startX) / unitPx).toDouble())
                                    change.consume()
                                }
                                lastX = change.position.x
                                lastTime = change.uptimeMillis
                            }
                        } finally {
                            motion.endTouch(scriptedAutoPlay)
                            driverGeneration += 1
                        }
                    }
                },
        ) {
            val frame = motion.frameSignal
            if (frame >= 0L) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.save()
                    canvas.nativeCanvas.scale(unitPx, unitPx)
                    painter.drawScene(canvas.nativeCanvas, motion, maxWidth.value, safeTop + 18f)
                    canvas.nativeCanvas.restore()
                }
            }
        }
        val navigationTop = safeTop + 18f + NavCenterY
        Box(
            Modifier
                .offset((SideMargin - 4f).dp, (navigationTop - BackSize / 2f - 4f).dp)
                .size((BackSize + 8f).dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Back"
                }
                .clickable(enabled = onBack != null, role = Role.Button) {
                    motion.beginTouch()
                    onBack?.invoke()
                    motion.endTouch(scriptedAutoPlay)
                    driverGeneration += 1
                },
        )
        Box(
            Modifier
                .offset((maxWidth.value - SideMargin - SkipWidth - 4f).dp, (navigationTop - SkipHeight / 2f - 4f).dp)
                .size((SkipWidth + 8f).dp, (SkipHeight + 8f).dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Skip"
                }
                .clickable(enabled = onSkip != null, role = Role.Button) {
                    motion.beginTouch()
                    val item = painter.stamps[motion.selectedIndex]
                    onSkip?.invoke(WidgetStampSelection(motion.selectedIndex, item.id, item.label))
                    motion.endTouch(scriptedAutoPlay)
                    driverGeneration += 1
                },
        )
    }
}
