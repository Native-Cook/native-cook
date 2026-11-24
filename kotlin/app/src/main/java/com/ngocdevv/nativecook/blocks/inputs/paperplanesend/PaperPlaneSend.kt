package com.ngocdevv.nativecook.blocks.inputs.paperplanesend

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The film's period (s), as in planeMotion.ts. */
const val LOOP = PlaneMotion.LOOP

private const val GUTTER = 16f

/** The composer: the field's height and radius, the send button's size, the gap between them, the row's bottom margin. */
private const val FIELD_H = 40f
private const val FIELD_R = 20f
private const val SEND = 36f
private const val SEND_GAP = 8f
private const val ROW_BOTTOM = 10f

/** The send button's touch target grows by this much on every side. */
private const val SEND_SLOP = 10f

/** The field's text inset (the 2D paper's words start at the same place). */
private const val TEXT_X = 14f
private const val TEXT_LINE = 21f

/** The thread rests this far above the composer; the sheet hangs this far above it. */
private const val THREAD_GAP = 16f
private const val SHEET_GAP = 44f

/** The sheet's width on screen, and its corner radius as flat paper. */
private const val SHEET_PT_MAX = 200f
private const val PAPER_R = 1.5f
private const val DOT = 44f

/** A one-line bubble's height, the gap between bubbles and the receipt line's height. */
private const val BUBBLE_H = 38f
private const val BUBBLE_GAP = 6f
private const val RECEIPT_H = 20f

/** How far the thread moves up for the new message. */
private const val SHIFT = BUBBLE_H + BUBBLE_GAP + RECEIPT_H

/** A finger moving this far is a drag; a press longer than this is not a tap. */
private const val DRAG_SLOP = 8f
private const val TAP_MS = 400L

/** A CALayer shadow radius as a BlurMaskFilter radius. */
private const val SHADOW_BLUR = 1.6f

private fun rgb(hex: Int, a: Float = 1f): Color = Color(hex or (0xFF shl 24)).copy(alpha = a)

/** React Native's type: a line box of `line`, the glyphs centred in it, no font padding. */
private fun type(size: Int, line: Float, color: Color, weight: FontWeight = FontWeight.Normal) =
    TextStyle(
        color = color,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

/**
 * A chat composer told as a 9-second film. A message types itself into the field, the send button is pressed, and the
 * message leaves the field as a sheet of paper that folds into a dart (sixteen rigid panels hinged on the real
 * creases), flies up over the thread, banks, and shrinks into the place where the sent bubble pops in with "Delivered".
 *
 * Ported from the React Native block (react-native-filament + Skia): the same plane.glb and studio light in Filament
 * (PaperPlaneSendScene), the same film (PlaneMotion). The glTF's baked animation is not used: each hinge is set from
 * the film's time. Touch: tap send, or drag it up and let go to throw; a harder flick flies a bigger, faster arc.
 * Reduced motion: the still of the delivered message.
 */
@Composable
fun PaperPlaneSend(autoPlay: Boolean = true, modifier: Modifier = Modifier) {
    BlockSystemBars(darkIcons = false)
    val reduced = rememberLiveReducedMotion()
    val controller = remember(autoPlay, reduced) { PaperPlaneSendController(autoPlay, reduced) }
    val view = LocalView.current

    // One clock for the Compose layers: Choreographer's frame time, read only in draw and layer lambdas.
    var frameNanos by remember { mutableLongStateOf(System.nanoTime()) }
    var typed by remember(controller) { mutableIntStateOf(PlaneMotion.typedCount(controller.t)) }
    val clock = remember(controller) {
        {
            controller.frame(frameNanos / 1e6)
            controller.t
        }
    }
    LaunchedEffect(controller) {
        var sends = controller.sendTaps
        var lands = controller.landTaps
        while (true) {
            withFrameNanos { nanos ->
                controller.frame(nanos / 1e6)
                frameNanos = nanos
                typed = PlaneMotion.typedCount(controller.t)
                if (controller.sendTaps != sends) {
                    sends = controller.sendTaps
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                if (controller.landTaps != lands) {
                    lands = controller.landTaps
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(rgb(PlaneMotion.BG_TOP), rgb(PlaneMotion.BG)))),
    ) {
        val density = LocalDensity.current
        val hairline = with(density) { 1f.toDp() }
        val insets = WindowInsets.safeDrawing
        val top = with(density) { insets.getTop(this).toDp().value }
        val bottom = with(density) { insets.getBottom(this).toDp().value }
        val w = maxWidth.value
        val h = maxHeight.value

        // the composer, the sheet above it, and the place the new message takes in the thread
        val fieldY = h - bottom - ROW_BOTTOM - FIELD_H
        val fieldW = w - GUTTER * 2 - SEND - SEND_GAP
        val sendX = w - GUTTER - SEND
        val sendY = fieldY + (FIELD_H - SEND) / 2
        val sheetPt = min(SHEET_PT_MAX, w * 0.5f)
        val sheetH = sheetPt * (PlaneMotion.SHEET_W / PlaneMotion.SHEET_L).toFloat()
        val sheetCx = w / 2
        val sheetCy = fieldY - SHEET_GAP - sheetH / 2
        val threadBottom = fieldY - THREAD_GAP
        val slotCy = threadBottom - RECEIPT_H - BUBBLE_H / 2
        var measuredSlot by remember { mutableFloatStateOf(-1f) }
        val slotW = if (measuredSlot > 0) measuredSlot else w * 0.66f
        SideEffect {
            controller.setGeometry(
                w.toDouble(), h.toDouble(), sheetCx.toDouble(), sheetCy.toDouble(),
                (w - GUTTER - slotW / 2).toDouble(), slotCy.toDouble(), sheetPt.toDouble(),
            )
        }

        // The header.
        Row(
            modifier = Modifier
                .offset(x = GUTTER.dp, y = (top + 6).dp)
                .width((w - GUTTER * 2).dp)
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFFF2B36B), Color(0xFFE0764A), Color(0xFFB9483C))),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(PlaneMotion.CONTACT_INITIALS, style = type(14, 18f, Color(0xFFFFF7EE), FontWeight.Bold))
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(PlaneMotion.CONTACT, style = type(16, 20f, rgb(PlaneMotion.INK), FontWeight.SemiBold))
                Text("Active now", style = type(13, 17f, rgb(PlaneMotion.MUTE)))
            }
            Text(PlaneMotion.APP, style = type(13, 17f, rgb(PlaneMotion.INK, 0.8f), FontWeight.SemiBold))
        }
        Box(
            Modifier
                .offset(y = (top + 56).dp)
                .fillMaxWidth()
                .height(hairline)
                .background(Color.White.copy(alpha = 0.08f)),
        )

        // The conversation: three bubbles that move up as one.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = GUTTER.dp, end = GUTTER.dp, bottom = (h - threadBottom).dp)
                .fillMaxWidth()
                .graphicsLayer { translationY = -SHIFT * density.density * PlaneMotion.shiftAt(clock()).toFloat() },
            verticalArrangement = Arrangement.spacedBy(BUBBLE_GAP.dp),
        ) {
            val maxBubble = (w - GUTTER * 2) * 0.82f
            for ((out, text) in PlaneMotion.THREAD) {
                Bubble(text, out, hairline, Modifier.align(if (out) Alignment.End else Alignment.Start).widthIn(max = maxBubble.dp))
            }
        }

        // The new message's bubble, which pops in where the plane lands, and its receipt.
        Bubble(
            PlaneMotion.MESSAGE,
            out = true,
            hairline = hairline,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = GUTTER.dp, bottom = (h - threadBottom + RECEIPT_H).dp)
                .widthIn(max = (w * 0.82f).dp)
                .onSizeChanged { measuredSlot = it.width / density.density }
                .graphicsLayer {
                    val tt = clock()
                    val s = PlaneMotion.bubbleScale(tt).toFloat()
                    alpha = PlaneMotion.bubbleAlpha(tt).toFloat()
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(1f, 1f)
                },
        )
        Text(
            PlaneMotion.DELIVERED,
            style = type(12, RECEIPT_H, rgb(PlaneMotion.MUTE), FontWeight.Medium),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = (GUTTER + 4).dp, bottom = (h - threadBottom).dp)
                .height(RECEIPT_H.dp)
                .graphicsLayer { alpha = PlaneMotion.deliveredAt(clock()).toFloat() },
        )

        // The composer: the message types itself into the value (never focused, so no keyboard).
        Box(
            modifier = Modifier
                .offset(x = GUTTER.dp, y = fieldY.dp)
                .size(fieldW.dp, FIELD_H.dp)
                .background(rgb(PlaneMotion.FIELD), RoundedCornerShape(FIELD_R.dp))
                .border(hairline, Color.White.copy(alpha = 0.12f), RoundedCornerShape(FIELD_R.dp))
                .padding(horizontal = TEXT_X.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (typed == 0) Text("Message", style = type(16, TEXT_LINE, rgb(PlaneMotion.MUTE)))
            BasicTextField(
                value = PlaneMotion.MESSAGE.take(typed),
                onValueChange = {},
                enabled = false,
                readOnly = true,
                singleLine = true,
                textStyle = type(16, TEXT_LINE, rgb(PlaneMotion.INK)),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // The send button: a tap sends, a drag up throws. The touch target stays put while the button follows the finger.
        Box(
            modifier = Modifier
                .offset(x = (sendX - SEND_SLOP).dp, y = (sendY - SEND_SLOP).dp)
                .size((SEND + SEND_SLOP * 2).dp)
                .semantics {
                    contentDescription = "Send. Sends the message as a paper plane; drag up to throw it"
                    role = Role.Button
                }
                .pointerInput(controller) {
                    val d = this.density
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        controller.touchDown()
                        var dragging = false
                        var released = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val delta = change.position - down.position
                            if (!dragging && hypot(delta.x, delta.y) >= DRAG_SLOP * d) dragging = true
                            if (dragging) controller.dragged((delta.y / d).toDouble())
                            change.consume()
                            if (!change.pressed) {
                                released = true
                                if (dragging) {
                                    controller.dragEnded((delta.y / d).toDouble(), (tracker.calculateVelocity().y / d).toDouble())
                                } else if (change.uptimeMillis - down.uptimeMillis <= TAP_MS) {
                                    controller.tapped()
                                }
                                break
                            }
                        }
                        // a cancelled drag is no throw: the button only springs back
                        if (dragging && !released) controller.dragEnded(0.0, 0.0)
                        controller.touchUp()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val ghost = remember { DoubleArray(2) }
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val tt = clock()
                        PlaneMotion.ghostAt(tt, ghost)
                        val s = 1 - 0.1f * max(controller.pressed, ghost[1]).toFloat()
                        translationY = controller.pull.toFloat() * density.density
                        scaleX = s
                        scaleY = s
                    }
                    .size(SEND.dp)
                    .cssBoxShadow(Color.Black.copy(alpha = 0.35f), offsetY = 3.dp, blur = 12.dp, radius = (SEND / 2).dp)
                    .background(Color(0xFF363B46), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = PlaneMotion.sendLitAt(clock()).toFloat() }
                        .background(rgb(PlaneMotion.BUBBLE_OUT), CircleShape)
                        .border(hairline, Color.White.copy(alpha = 0.28f), CircleShape),
                )
                Canvas(Modifier.size(17.dp)) {
                    val sw = 2.3.dp.toPx()
                    val cx = size.width / 2
                    val inset = sw / 2
                    val arrow = Path().apply {
                        moveTo(cx, size.height - inset)
                        lineTo(cx, inset)
                        moveTo(size.width * 0.16f, size.height * 0.42f)
                        lineTo(cx, inset)
                        lineTo(size.width * 0.84f, size.height * 0.42f)
                    }
                    drawPath(arrow, Color.White, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }

        // Under the 3D layer: the folding paper's shadow on the chat, and the message as flat paper until the sheet
        // takes over.
        val measurer = rememberTextMeasurer()
        val words = remember(measurer, density) {
            measurer.measure(
                PlaneMotion.MESSAGE,
                style = type(16, TEXT_LINE, rgb(PlaneMotion.PAPER_INK)),
                overflow = TextOverflow.Clip,
                softWrap = false,
                maxLines = 1,
                constraints = Constraints(),
            )
        }
        val shadowBrush = remember {
            Brush.radialGradient(
                0f to Color.Black.copy(alpha = 0.95f),
                0.45f to Color.Black.copy(alpha = 0.6f),
                0.78f to Color.Black.copy(alpha = 0.18f),
                1f to Color.Black.copy(alpha = 0f),
                center = Offset.Zero,
                radius = 1f,
            )
        }
        val shadow = remember { DoubleArray(5) }
        val paperShadow = remember { PaperShadow() }
        Canvas(Modifier.fillMaxSize()) {
            val tt = clock()
            val d = density.density

            PlaneMotion.shadowAt(tt, controller.geo, shadow)
            if (shadow[4] > 0.002) {
                translate(shadow[0].toFloat() * d, shadow[1].toFloat() * d) {
                    scale(shadow[2].toFloat() * d, shadow[3].toFloat() * d, pivot = Offset.Zero) {
                        drawCircle(shadowBrush, radius = 1f, center = Offset.Zero, alpha = shadow[4].toFloat())
                    }
                }
            }

            val a = PlaneMotion.paperAlpha(tt).toFloat()
            if (a > 0.002f) {
                // from the field's rectangle to the sheet's
                val k = PlaneMotion.liftAt(tt).toFloat()
                val pw = fieldW + (sheetPt - fieldW) * k
                val ph = FIELD_H + (sheetH - FIELD_H) * k
                val cx = GUTTER + fieldW / 2 + (sheetCx - GUTTER - fieldW / 2) * k
                val cy = fieldY + FIELD_H / 2 + (sheetCy - fieldY - FIELD_H / 2) * k
                val left = (cx - pw / 2) * d
                val topPx = (cy - ph / 2) * d
                val widthPx = pw * d
                val heightPx = ph * d
                val radius = (FIELD_R + (PAPER_R - FIELD_R) * k) * d
                drawIntoCanvas {
                    paperShadow.draw(
                        it.nativeCanvas, left + 4 * d, topPx + 12 * d, widthPx, heightPx, radius,
                        blur = (4 + 14 * k) * SHADOW_BLUR * d,
                        alpha = (0.1f + 0.3f * k) * a,
                    )
                }
                drawRoundRect(
                    rgb(PlaneMotion.PAPER),
                    topLeft = Offset(left, topPx),
                    size = Size(widthPx, heightPx),
                    cornerRadius = CornerRadius(radius),
                    alpha = a,
                )
                val wordsA = a * PlaneMotion.wordsAlpha(tt).toFloat()
                if (wordsA > 0.002f) {
                    clipRect(left, topPx, left + widthPx, topPx + heightPx) {
                        drawText(words, topLeft = Offset(left + TEXT_X * d, topPx + (FIELD_H - TEXT_LINE) / 2 * d), alpha = wordsA)
                    }
                }
            }
        }

        // The sheet in 3D, see-through over the whole chat.
        PaperPlaneSendStage(controller, Modifier.fillMaxSize())

        // The film's finger on the send button.
        val dotGhost = remember { DoubleArray(2) }
        Canvas(Modifier.fillMaxSize()) {
            PlaneMotion.ghostAt(clock(), dotGhost)
            val a = (dotGhost[0] * 0.9).toFloat()
            if (a > 0.002f) {
                val d = density.density
                val c = Offset((sendX + SEND / 2) * d, (sendY + SEND / 2) * d)
                val r = DOT / 2 * (1 - 0.16f * dotGhost[1].toFloat()) * d
                drawCircle(Color.White, radius = r, center = c, alpha = 0.3f * a)
                drawCircle(Color.White, radius = r - 0.75f * d, center = c, alpha = 0.66f * a, style = Stroke(1.5f * d))
            }
        }
    }
}

@Composable
private fun Bubble(text: String, out: Boolean, hairline: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val shape = if (out) {
        RoundedCornerShape(topStart = 19.dp, topEnd = 19.dp, bottomEnd = 6.dp, bottomStart = 19.dp)
    } else {
        RoundedCornerShape(topStart = 19.dp, topEnd = 19.dp, bottomEnd = 19.dp, bottomStart = 6.dp)
    }
    val base = modifier
        .height(BUBBLE_H.dp)
        .background(rgb(if (out) PlaneMotion.BUBBLE_OUT else PlaneMotion.BUBBLE_IN), shape)
    Box(
        modifier = (if (out) base else base.border(hairline, Color.White.copy(alpha = 0.06f), shape)).padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = type(16, TEXT_LINE, rgb(PlaneMotion.INK)), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The 2D paper's soft shadow. The blur is kept in whole pixels so its filter is rebuilt only when it really changes. */
private class PaperShadow {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var blurPx = -1

    fun draw(canvas: android.graphics.Canvas, x: Float, y: Float, w: Float, h: Float, radius: Float, blur: Float, alpha: Float) {
        val px = max(1, blur.roundToInt())
        if (px != blurPx) {
            blurPx = px
            paint.maskFilter = BlurMaskFilter(px.toFloat(), BlurMaskFilter.Blur.NORMAL)
        }
        paint.color = android.graphics.Color.argb((alpha * 255).roundToInt().coerceIn(0, 255), 0, 0, 0)
        rect.set(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, radius, radius, paint)
    }
}
