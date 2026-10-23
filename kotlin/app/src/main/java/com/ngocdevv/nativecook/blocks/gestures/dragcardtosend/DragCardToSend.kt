package com.ngocdevv.nativecook.blocks.gestures.dragcardtosend

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.isActive
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun DragCardToSend(autoPlay: Boolean = false) {
    val density = LocalDensity.current.density
    val reducedMotion = rememberReducedMotion()
    val haptics = LocalHapticFeedback.current
    val engine = remember(autoPlay, reducedMotion, haptics) {
        DragCardToSendEngine(
            autoPlay = autoPlay,
            reducedMotion = reducedMotion,
            onHaptic = { kind ->
                val type = when (kind) {
                    DragCardToSendHaptic.Select -> HapticFeedbackType.TextHandleMove
                    else -> HapticFeedbackType.LongPress
                }
                haptics.performHapticFeedback(type)
            },
        )
    }
    val resources = LocalContext.current.resources
    val images = remember(resources) {
        DragCardToSendTokens.people.associate { person ->
            person.id to ImageBitmap.imageResource(resources, person.asset)
        }
    }
    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    LaunchedEffect(engine) {
        while (isActive) {
            withFrameNanos { frameNanos ->
                engine.frame(frameNanos)
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DragCardToSendTokens.Colors.page),
    ) {
        val stageWidth = maxWidth.value.toDouble()
        val stageHeight = maxHeight.value.toDouble()
        LaunchedEffect(engine, stageWidth, stageHeight) {
            engine.setSize(stageWidth, stageHeight)
        }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "AERO card. Pull down to choose who to send money to."
                }
                .pointerInput(engine, density) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        val start = Offset(down.position.x / density, down.position.y / density)
                        engine.pointerDown(start)
                        var latest = down.position
                        var pressed: Boolean
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.first()
                            latest = change.position
                            tracker.addPosition(change.uptimeMillis, change.position)
                            pressed = change.pressed
                            if (pressed) {
                                val velocity = tracker.calculateVelocity()
                                engine.pointerMove(
                                    position = Offset(latest.x / density, latest.y / density),
                                    velocityX = (velocity.x / density).toDouble(),
                                )
                                change.consume()
                            }
                        } while (pressed)
                        val velocity = tracker.calculateVelocity()
                        engine.pointerUp(
                            position = Offset(latest.x / density, latest.y / density),
                            velocity = Offset(velocity.x / density, velocity.y / density),
                        )
                    }
                },
        ) {
            scale(density, density, pivot = Offset.Zero) {
                drawDragCardToSend(engine, images, textPaint)
            }
        }
    }
}

private fun DrawScope.drawDragCardToSend(
    engine: DragCardToSendEngine,
    images: Map<String, ImageBitmap>,
    paint: Paint,
) {
    val homeOpen = if (engine.reducedMotion) {
        engine.veil
    } else {
        clamp01((engine.g - 1.0) / 0.6)
    }
    val homeAlpha = 1.0 - (1.0 - DragCardToSendTokens.Fade.homeDim) * homeOpen
    drawHome(engine, paint, homeAlpha.toFloat())
    drawDragLabel(engine, paint)
    drawIsland(engine)
    drawDropArrow(engine)
    drawSheet(engine, images, paint)
    drawPill(engine, images, paint)
    drawWalletCard(engine, paint)
}

private fun DrawScope.drawHome(engine: DragCardToSendEngine, paint: Paint, alpha: Float) {
    val layout = DragCardToSendTokens.Layout
    val spread = if (engine.reducedMotion) {
        0.0
    } else {
        layout.headerSpread * smooth(engine.g / 0.66)
    }
    val buttonY = layout.headerCenter - layout.headerButton / 2.0
    val leftX = layout.side - spread
    val rightX = engine.width - layout.side - layout.headerButton + spread
    drawCircle(
        color = DragCardToSendTokens.Colors.headerButton.copy(alpha = alpha),
        radius = (layout.headerButton / 2.0).toFloat(),
        center = Offset((leftX + layout.headerButton / 2.0).toFloat(), layout.headerCenter.toFloat()),
    )
    repeat(9) { index ->
        val column = index % 3
        val row = index / 3
        drawRoundRect(
            color = DragCardToSendTokens.Colors.ink.copy(alpha = alpha),
            topLeft = Offset((leftX + 14.0 + column * 5.3).toFloat(), (buttonY + 14.0 + row * 5.3).toFloat()),
            size = androidx.compose.ui.geometry.Size(3.3f, 3.3f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.0f, 1.0f),
        )
    }
    drawCircle(
        color = DragCardToSendTokens.Colors.headerButton.copy(alpha = alpha),
        radius = (layout.headerButton / 2.0).toFloat(),
        center = Offset((rightX + layout.headerButton / 2.0).toFloat(), layout.headerCenter.toFloat()),
    )
    drawPlus(
        center = Offset((rightX + layout.headerButton / 2.0).toFloat(), layout.headerCenter.toFloat()),
        color = DragCardToSendTokens.Colors.ink.copy(alpha = alpha),
        size = 14.0f,
    )

    val listY = if (engine.phase == DragCardToSendPhase.Pulling) max(0.0, engine.cardY) else 0.0
    val listScale = 1.0 - DragCardToSendTokens.Pull.listSink * clamp01(listY / 263.0)
    withTransform({
        translate(0.0f, listY.toFloat())
        scale(listScale.toFloat(), listScale.toFloat(), Offset((engine.width / 2.0).toFloat(), layout.listTitleTop.toFloat()))
    }) {
        drawText("Last", layout.side + 2.0, layout.listTitleTop + 21.0, DragCardToSendTokens.Type.title, DragCardToSendTokens.Colors.ink.copy(alpha = alpha), paint)
        drawText("Transactions", layout.side + 2.0, layout.listTitleTop + 47.0, DragCardToSendTokens.Type.title, DragCardToSendTokens.Colors.ink.copy(alpha = alpha), paint, bold = true)
        val titleButtonX = engine.width - layout.side - 40.0
        drawCircle(
            color = DragCardToSendTokens.Colors.headerButton.copy(alpha = alpha),
            radius = 20.0f,
            center = Offset((titleButtonX + 20.0).toFloat(), (layout.listTitleTop + 27.0).toFloat()),
        )
        drawArrow(
            center = Offset((titleButtonX + 20.0).toFloat(), (layout.listTitleTop + 27.0).toFloat()),
            kind = ArrowKind.Open,
            color = DragCardToSendTokens.Colors.ink.copy(alpha = alpha),
            size = 18.0,
        )
        val rows = if (engine.balanceProgress > 0.001) {
            listOf(
                DragCardToSendTokens.Transaction(
                    id = "sent",
                    name = DragCardToSendTokens.people[2].name,
                    date = "Just now",
                    amount = -DragCardToSendTokens.amounts[DragCardToSendTokens.defaultAmountIndex] * 100,
                    kind = DragCardToSendTokens.Kind.Out,
                ),
            ) + DragCardToSendTokens.transactions
        } else {
            DragCardToSendTokens.transactions
        }
        rows.take(5).forEachIndexed { index, transaction ->
            val rowY = layout.rowTop + index * (layout.rowHeight + layout.rowGap)
            drawTransactionRow(transaction, rowY, engine.width, paint, alpha)
        }
    }
}

private fun DrawScope.drawTransactionRow(
    transaction: DragCardToSendTokens.Transaction,
    y: Double,
    width: Double,
    paint: Paint,
    alpha: Float,
) {
    val layout = DragCardToSendTokens.Layout
    val rowWidth = width - layout.side * 2.0
    drawRoundRect(
        color = DragCardToSendTokens.Colors.rowFill.copy(alpha = alpha),
        topLeft = Offset(layout.side.toFloat(), y.toFloat()),
        size = androidx.compose.ui.geometry.Size(rowWidth.toFloat(), layout.rowHeight.toFloat()),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius((layout.rowHeight / 2.0).toFloat()),
    )
    val iconCenter = Offset((layout.side + 10.0 + layout.rowIcon / 2.0).toFloat(), (y + layout.rowHeight / 2.0).toFloat())
    drawCircle(
        color = DragCardToSendTokens.Colors.rowIcon.copy(alpha = alpha),
        radius = (layout.rowIcon / 2.0).toFloat(),
        center = iconCenter,
    )
    drawArrow(
        center = iconCenter,
        kind = if (transaction.kind == DragCardToSendTokens.Kind.Out) ArrowKind.RowOut else ArrowKind.RowIn,
        color = DragCardToSendTokens.Colors.ink.copy(alpha = alpha),
        size = 22.0,
    )
    val textX = layout.side + 10.0 + layout.rowIcon + 18.0
    drawText(transaction.name, textX, y + 26.0, DragCardToSendTokens.Type.rowName - 1.0, DragCardToSendTokens.Colors.ink.copy(alpha = alpha), paint, bold = true)
    drawText(transaction.date, textX, y + 44.0, DragCardToSendTokens.Type.rowMeta, DragCardToSendTokens.Colors.mute.copy(alpha = alpha), paint)
    val amountColor = if (transaction.amount > 0) DragCardToSendTokens.Colors.positive else DragCardToSendTokens.Colors.ink
    val amount = if (transaction.amount > 0) "+${formatMoney(transaction.amount)}" else formatMoney(transaction.amount)
    drawText(amount, width - layout.side - 10.0, y + 36.0, 13.0, amountColor.copy(alpha = alpha), paint, bold = true, align = Paint.Align.RIGHT)
}

private fun DrawScope.drawDragLabel(engine: DragCardToSendEngine, paint: Paint) {
    val alpha = if (engine.phase == DragCardToSendPhase.Pulling) {
        clamp01(engine.cardY / 60.0) * (1.0 - clamp01((engine.g - 0.95) / 0.2))
    } else {
        0.0
    }
    if (alpha <= 0.0) {
        return
    }
    drawText(
        text = "Drag to send",
        x = engine.width / 2.0,
        y = DragCardToSendTokens.Layout.dragLabelTop + DragCardToSendTokens.Type.hint,
        size = DragCardToSendTokens.Type.hint,
        color = DragCardToSendTokens.Colors.mute.copy(alpha = alpha.toFloat()),
        paint = paint,
        align = Paint.Align.CENTER,
    )
}

private fun DrawScope.drawIsland(engine: DragCardToSendEngine) {
    val g = if (engine.reducedMotion) 0.0 else engine.g
    val pill = pillAt(g, engine.width)
    drawRoundRect(
        color = DragCardToSendTokens.Colors.island,
        topLeft = Offset(pill.left.toFloat(), pill.top.toFloat()),
        size = androidx.compose.ui.geometry.Size(pill.width.toFloat(), pill.height.toFloat()),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius((pill.height / 2.0).toFloat()),
    )
    if (g > 0.001) {
        val body = bodyAt(g, engine.width, engine.height)
        val glass = clamp01((g - 0.05) / 0.3) * (1.0 - clamp01((g - 1.1) / 0.5))
        val opacity = 1.0 - (1.0 - DragCardToSendTokens.Pull.dropOpacity) * glass
        drawRoundRect(
            color = DragCardToSendTokens.Colors.island.copy(alpha = opacity.toFloat()),
            topLeft = Offset(body.left.toFloat(), body.top.toFloat()),
            size = androidx.compose.ui.geometry.Size(body.width.toFloat(), body.height.toFloat()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(body.radiusX.toFloat(), body.radiusY.toFloat()),
        )
        drawIslandJoin(pill, body, opacity.toFloat())
    }
    if (engine.reducedMotion && engine.veil > 0.0) {
        val sheet = bodyAt(2.0, engine.width, engine.height)
        drawRoundRect(
            color = DragCardToSendTokens.Colors.island.copy(alpha = engine.veil.toFloat()),
            topLeft = Offset(sheet.left.toFloat(), sheet.top.toFloat()),
            size = androidx.compose.ui.geometry.Size(sheet.width.toFloat(), sheet.height.toFloat()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(sheet.radiusX.toFloat(), sheet.radiusY.toFloat()),
        )
    }
}

private fun DrawScope.drawIslandJoin(pill: ShapeBox, body: ShapeBox, alpha: Float) {
    val pillBottom = pill.top + pill.height
    val gap = body.top - pillBottom
    if (gap >= DragCardToSendTokens.Drop.neckBreak) {
        return
    }
    val center = pill.left + pill.width / 2.0
    val progress = if (gap <= 0.0) 1.0 else 1.0 - gap / DragCardToSendTokens.Drop.neckBreak
    val topHalf = min(44.0, pill.width * 0.28) * sqrt(max(0.0, progress))
    val bottomHalf = min(64.0, body.width * 0.3) * sqrt(max(0.0, progress))
    val waist = min(topHalf, bottomHalf) * progress * 0.7
    val top = pillBottom - 4.0
    val bottom = body.top + 6.0
    val middle = (top + bottom) / 2.0
    val path = Path().apply {
        moveTo((center - topHalf).toFloat(), top.toFloat())
        lineTo((center + topHalf).toFloat(), top.toFloat())
        quadraticBezierTo((center + waist).toFloat(), middle.toFloat(), (center + bottomHalf).toFloat(), bottom.toFloat())
        lineTo((center - bottomHalf).toFloat(), bottom.toFloat())
        quadraticBezierTo((center - waist).toFloat(), middle.toFloat(), (center - topHalf).toFloat(), top.toFloat())
        close()
    }
    drawPath(path, DragCardToSendTokens.Colors.island.copy(alpha = alpha))
}

private fun DrawScope.drawDropArrow(engine: DragCardToSendEngine) {
    if (engine.reducedMotion) {
        return
    }
    val alpha = DragCardToSendTokens.Pull.arrow * clamp01(engine.g / 0.4) * (1.0 - clamp01((engine.g - 0.9) / 0.2))
    if (alpha <= 0.0) {
        return
    }
    val body = bodyAt(min(1.0, engine.g), engine.width, engine.height)
    drawArrow(
        center = Offset((engine.width / 2.0).toFloat(), (body.top + body.height * 0.42).toFloat()),
        kind = ArrowKind.Out,
        color = DragCardToSendTokens.Colors.sheetInk.copy(alpha = alpha.toFloat()),
        size = DragCardToSendTokens.Space.xxl,
    )
}

private fun DrawScope.drawSheet(
    engine: DragCardToSendEngine,
    images: Map<String, ImageBitmap>,
    paint: Paint,
) {
    val visibleG = if (engine.reducedMotion) 2.0 else engine.g
    val opacity = if (engine.reducedMotion) {
        engine.veil
    } else {
        clamp01((visibleG - DragCardToSendTokens.Fade.contentFrom) /
            (DragCardToSendTokens.Fade.contentTo - DragCardToSendTokens.Fade.contentFrom))
    }
    if (opacity <= 0.0) {
        return
    }
    val body = bodyAt(visibleG, engine.width, engine.height)
    val inset = 6.0 * (1.0 - smooth((visibleG - 1.0) * 2.0))
    val clipBox = ShapeBox(
        left = body.left + inset,
        top = body.top + inset,
        width = max(0.0, body.width - inset * 2.0),
        height = max(0.0, body.height - inset * 2.0),
        radiusX = max(0.0, min(body.radiusX, body.radiusY) - inset),
        radiusY = max(0.0, min(body.radiusX, body.radiusY) - inset),
    )
    val clip = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(
                    left = clipBox.left.toFloat(),
                    top = clipBox.top.toFloat(),
                    right = (clipBox.left + clipBox.width).toFloat(),
                    bottom = (clipBox.top + clipBox.height).toFloat(),
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    min(clipBox.radiusX, clipBox.width / 2.0).toFloat(),
                    min(clipBox.radiusY, clipBox.height / 2.0).toFloat(),
                ),
            ),
        )
    }
    val sheetWidth = engine.width - DragCardToSendTokens.Sheet.inset * 2.0
    val transition = clamp01((visibleG - 1.0) / 0.6)
    val scale = mix(min(1.0, body.width / sheetWidth), 1.0, transition)
    val dropLeft = engine.width / 2.0 - sheetWidth * scale / 2.0
    val dropTop = body.top + 12.0 - (DragCardToSendTokens.Sheet.favTop - 8.0) * scale
    val left = mix(dropLeft, DragCardToSendTokens.Sheet.inset, transition)
    val top = mix(dropTop, DragCardToSendTokens.Sheet.top, transition)
    clipPath(clip) {
        withTransform({
            translate(left.toFloat(), top.toFloat())
            scale(scale.toFloat(), scale.toFloat(), Offset.Zero)
        }) {
            drawSheetContent(engine, images, paint, opacity.toFloat(), sheetWidth)
        }
    }
}

private fun DrawScope.drawSheetContent(
    engine: DragCardToSendEngine,
    images: Map<String, ImageBitmap>,
    paint: Paint,
    alpha: Float,
    sheetWidth: Double,
) {
    val sheet = DragCardToSendTokens.Sheet
    drawText("Send", sheet.pad, sheet.titleTop + 42.0, sheet.titleSize, DragCardToSendTokens.Colors.sheetMute.copy(alpha = alpha), paint, bold = true)
    drawText("Money To", sheet.pad, sheet.titleTop + 92.0, sheet.titleSize, DragCardToSendTokens.Colors.sheetInk.copy(alpha = alpha), paint, bold = true)
    val closeCenter = Offset((sheetWidth - sheet.closeRight - sheet.close / 2.0).toFloat(), (sheet.titleTop + sheet.close / 2.0).toFloat())
    drawLine(DragCardToSendTokens.Colors.sheetInk.copy(alpha = alpha), closeCenter - Offset(5.0f, 5.0f), closeCenter + Offset(5.0f, 5.0f), 1.8f, StrokeCap.Round)
    drawLine(DragCardToSendTokens.Colors.sheetInk.copy(alpha = alpha), closeCenter + Offset(5.0f, -5.0f), closeCenter + Offset(-5.0f, 5.0f), 1.8f, StrokeCap.Round)

    val innerWidth = sheetWidth - (sheet.favPad * 2.0)
    DragCardToSendTokens.favourites.forEachIndexed { index, person ->
        val centerX = sheet.favPad + innerWidth * (index + 0.5) / 4.0 + sheet.favNudge[index]
        val centerY = sheet.favTop + sheet.favSize / 2.0
        val hovered = if (engine.hoverIndex == index) engine.hoverAmount else 0.0
        val targetScale = 1.0 + (DragCardToSendTokens.Magnet.targetScale - 1.0) * hovered
        scale(targetScale.toFloat(), targetScale.toFloat(), Offset(centerX.toFloat(), centerY.toFloat())) {
            drawAvatar(images.getValue(person.id), centerX, centerY, sheet.favSize, alpha)
        }
        drawText(person.name, centerX, sheet.favTop + sheet.favSize + 25.0, DragCardToSendTokens.Type.favName, DragCardToSendTokens.Colors.sheetInk.copy(alpha = alpha), paint, align = Paint.Align.CENTER)
    }
    val addX = sheet.favPad + innerWidth * 3.5 / 4.0 + sheet.favNudge[3]
    val addY = sheet.favTop + sheet.favSize / 2.0
    drawCircle(
        color = Color.Transparent,
        radius = (sheet.favSize / 2.0).toFloat(),
        center = Offset(addX.toFloat(), addY.toFloat()),
        style = Stroke(1.5f),
    )
    drawCircle(
        color = DragCardToSendTokens.Colors.sheetInk.copy(alpha = alpha * 0.7f),
        radius = (sheet.favSize / 2.0).toFloat(),
        center = Offset(addX.toFloat(), addY.toFloat()),
        style = Stroke(1.5f),
    )
    drawPlus(Offset(addX.toFloat(), addY.toFloat()), DragCardToSendTokens.Colors.sheetInk.copy(alpha = alpha), 14.0f)
    drawText("Add New", addX, sheet.favTop + sheet.favSize + 25.0, DragCardToSendTokens.Type.favName, DragCardToSendTokens.Colors.sheetInk.copy(alpha = alpha), paint, align = Paint.Align.CENTER)

    drawText("Your", sheet.pad, sheet.sectionTop + 21.0, DragCardToSendTokens.Type.sheetSection, DragCardToSendTokens.Colors.sheetMute.copy(alpha = alpha), paint, bold = true)
    drawText("Contacts", sheet.pad, sheet.sectionTop + 47.0, DragCardToSendTokens.Type.sheetSection, DragCardToSendTokens.Colors.sheetInk.copy(alpha = alpha), paint, bold = true)
    DragCardToSendTokens.contacts.forEachIndexed { index, person ->
        val targetIndex = DragCardToSendTokens.favourites.size + index
        val rowTop = sheet.rowsTop + index * sheet.rowHeight
        val centerY = rowTop + sheet.rowHeight / 2.0
        val hovered = if (engine.hoverIndex == targetIndex) engine.hoverAmount else 0.0
        if (hovered > 0.0) {
            drawRoundRect(
                color = DragCardToSendTokens.Colors.sheetHover.copy(alpha = (alpha * clamp01(hovered).toFloat())),
                topLeft = Offset(DragCardToSendTokens.Space.lg.toFloat(), (rowTop + 6.0).toFloat()),
                size = androidx.compose.ui.geometry.Size((sheetWidth - DragCardToSendTokens.Space.lg * 2.0).toFloat(), (sheet.rowHeight - 12.0).toFloat()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius((sheet.rowHeight / 2.0).toFloat()),
            )
        }
        val avatarX = sheet.rowAvatarX
        val targetScale = 1.0 + (DragCardToSendTokens.Magnet.targetScale - 1.0) * hovered
        scale(targetScale.toFloat(), targetScale.toFloat(), Offset(avatarX.toFloat(), centerY.toFloat())) {
            drawAvatar(images.getValue(person.id), avatarX, centerY, sheet.contactSize, alpha)
        }
        drawText(person.name, avatarX + sheet.contactSize / 2.0 + 14.0, centerY + 5.0, DragCardToSendTokens.Type.contact - 1.0, DragCardToSendTokens.Colors.sheetInk.copy(alpha = alpha), paint, bold = true)
    }
    val nextAlpha = alpha * clamp01((engine.g - 1.3) / 0.4).toFloat()
    if (nextAlpha > 0.0f) {
        val nextLeft = (sheetWidth - sheet.nextWidth) / 2.0
        drawRoundRect(
            color = DragCardToSendTokens.Colors.next.copy(alpha = nextAlpha),
            topLeft = Offset(nextLeft.toFloat(), sheet.nextTop.toFloat()),
            size = androidx.compose.ui.geometry.Size(sheet.nextWidth.toFloat(), sheet.nextHeight.toFloat()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius((sheet.nextHeight / 2.0).toFloat()),
        )
        drawText("Next", sheetWidth / 2.0, sheet.nextTop + 33.0, 15.0, DragCardToSendTokens.Colors.sheetInk.copy(alpha = nextAlpha), paint, bold = true, align = Paint.Align.CENTER)
    }
    drawSendPayoff(engine, paint, alpha)
}

private fun DrawScope.drawSendPayoff(engine: DragCardToSendEngine, paint: Paint, alpha: Float) {
    val index = engine.sendIndex
    if (index < 0) {
        return
    }
    val global = engine.targets()[index]
    val center = Offset(
        x = (global.x.toDouble() - DragCardToSendTokens.Sheet.inset).toFloat(),
        y = (global.y.toDouble() - DragCardToSendTokens.Sheet.top).toFloat(),
    )
    val avatarSize = if (index < DragCardToSendTokens.favourites.size) DragCardToSendTokens.Sheet.favSize else DragCardToSendTokens.Sheet.contactSize
    val ringRadius = avatarSize / 2.0 * DragCardToSendTokens.Magnet.targetScale + 6.0
    if (!engine.reducedMotion && engine.ring > 0.0 && engine.ring < 1.0) {
        repeat(DragCardToSendTokens.Ambient.sparks) { spark ->
            val angle = spark.toDouble() / DragCardToSendTokens.Ambient.sparks * PI * 2.0
            val distance = ringRadius + DragCardToSendTokens.Ambient.sparkReach * sqrt(engine.ring)
            drawCircle(
                color = DragCardToSendTokens.Colors.ring.copy(alpha = (alpha * (1.0 - engine.ring)).toFloat()),
                radius = (3.0 * (1.0 - 0.6 * engine.ring)).toFloat(),
                center = Offset(
                    x = (center.x.toDouble() + cos(angle) * distance).toFloat(),
                    y = (center.y.toDouble() + sin(angle) * distance).toFloat(),
                ),
            )
        }
    }
    if (engine.ring > 0.0) {
        drawArc(
            color = DragCardToSendTokens.Colors.ring.copy(alpha = alpha),
            startAngle = -90.0f,
            sweepAngle = (360.0 * engine.ring).toFloat(),
            useCenter = false,
            topLeft = center - Offset(ringRadius.toFloat(), ringRadius.toFloat()),
            size = androidx.compose.ui.geometry.Size((ringRadius * 2.0).toFloat(), (ringRadius * 2.0).toFloat()),
            style = Stroke(width = 3.0f, cap = StrokeCap.Round),
        )
    }
    if (engine.check > 0.0) {
        val badge = center + Offset((avatarSize * 0.36).toFloat(), (avatarSize * 0.36).toFloat())
        scale(engine.check.toFloat(), engine.check.toFloat(), badge) {
            drawCircle(DragCardToSendTokens.Colors.success.copy(alpha = alpha), 11.0f, badge)
            drawCheck(badge, DragCardToSendTokens.Colors.island.copy(alpha = alpha), 10.0f)
        }
    }
}

private fun DrawScope.drawPill(engine: DragCardToSendEngine, images: Map<String, ImageBitmap>, paint: Paint) {
    val pill = pillAt(engine.g, engine.width)
    val contentAlpha = 1.0 - clamp01((engine.g - 0.9) / 0.2)
    if (contentAlpha <= 0.0) {
        return
    }
    val offset = (pill.width - DragCardToSendTokens.Layout.pillWidth) / 2.0
    val left = pill.left + offset
    val centerY = DragCardToSendTokens.Layout.pillTop + DragCardToSendTokens.Layout.pillHeight / 2.0
    val restAlpha = contentAlpha * (1.0 - engine.sent)
    if (restAlpha > 0.0) {
        drawText("Send", left + 16.0, centerY + 5.0, DragCardToSendTokens.Type.pill, Color.White.copy(alpha = (restAlpha * 0.72).toFloat()), paint, bold = true)
        drawText("to", left + 52.0, centerY + 5.0, DragCardToSendTokens.Type.pill, Color.White.copy(alpha = restAlpha.toFloat()), paint, bold = true)
        var avatarX = left + DragCardToSendTokens.Layout.pillWidth - 10.0 - DragCardToSendTokens.Layout.avatarSmall / 2.0
        DragCardToSendTokens.favourites.reversed().forEach { person ->
            drawCircle(DragCardToSendTokens.Colors.island, (DragCardToSendTokens.Layout.avatarSmall / 2.0 + 2.0).toFloat(), Offset(avatarX.toFloat(), centerY.toFloat()))
            drawAvatar(images.getValue(person.id), avatarX, centerY, DragCardToSendTokens.Layout.avatarSmall, restAlpha.toFloat())
            avatarX -= DragCardToSendTokens.Layout.avatarSmall - DragCardToSendTokens.Layout.avatarOverlap
        }
    }
    val doneAlpha = contentAlpha * engine.sent
    if (doneAlpha > 0.0) {
        val tick = Offset((left + 25.0).toFloat(), centerY.toFloat())
        drawCircle(DragCardToSendTokens.Colors.success.copy(alpha = doneAlpha.toFloat()), 9.0f, tick)
        drawCheck(tick, DragCardToSendTokens.Colors.island.copy(alpha = doneAlpha.toFloat()), 10.0f)
        drawText("$120", left + 40.0, centerY + 5.0, DragCardToSendTokens.Type.pill, Color.White.copy(alpha = doneAlpha.toFloat()), paint, bold = true)
        val undoLeft = left + DragCardToSendTokens.Layout.pillWidth - 73.0
        drawRoundRect(
            color = Color(0xFF26272C).copy(alpha = doneAlpha.toFloat()),
            topLeft = Offset(undoLeft.toFloat(), (centerY - 15.0).toFloat()),
            size = androidx.compose.ui.geometry.Size(63.0f, 30.0f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(15.0f),
        )
        drawText("Undo", undoLeft + 31.5, centerY + 5.0, 14.0, Color.White.copy(alpha = doneAlpha.toFloat()), paint, bold = true, align = Paint.Align.CENTER)
        drawRoundRect(
            color = Color.White.copy(alpha = (doneAlpha * 0.5).toFloat()),
            topLeft = Offset((left + 24.0).toFloat(), (DragCardToSendTokens.Layout.pillTop + DragCardToSendTokens.Layout.pillHeight - 6.0).toFloat()),
            size = androidx.compose.ui.geometry.Size(((DragCardToSendTokens.Layout.pillWidth - 48.0) * engine.undo).toFloat(), 2.0f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.0f),
        )
    }
}

private fun DrawScope.drawWalletCard(engine: DragCardToSendEngine, paint: Paint) {
    if (engine.cardOpacity <= 0.001) {
        return
    }
    val layout = DragCardToSendTokens.Layout
    val width = engine.cardWidth
    val scale = engine.cardScale * engine.press * (1.0 - DragCardToSendTokens.Pull.shrink * engine.tilt)
    val center = Offset((engine.width / 2.0 + engine.cardX).toFloat(), (engine.homeCenterY + engine.cardY).toFloat())
    withTransform({
        translate((center.x.toDouble() - width / 2.0).toFloat(), (center.y.toDouble() - layout.cardHeight / 2.0).toFloat())
        scale(scale.toFloat(), scale.toFloat(), Offset((width / 2.0).toFloat(), (layout.cardHeight / 2.0).toFloat()))
        rotate(engine.sway.toFloat(), Offset((width / 2.0).toFloat(), (layout.cardHeight / 2.0).toFloat()))
    }) {
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(DragCardToSendTokens.Colors.cardHigh, DragCardToSendTokens.Colors.card, DragCardToSendTokens.Colors.card),
                start = Offset.Zero,
                end = Offset(width.toFloat(), layout.cardHeight.toFloat()),
            ),
            topLeft = Offset.Zero,
            size = androidx.compose.ui.geometry.Size(width.toFloat(), layout.cardHeight.toFloat()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(layout.cardRadius.toFloat()),
            alpha = engine.cardOpacity.toFloat(),
        )
        val deepAlpha = DragCardToSendTokens.Pull.deep * clamp01(engine.cardY / 263.0) * engine.cardOpacity
        if (deepAlpha > 0.0) {
            drawRoundRect(
                color = DragCardToSendTokens.Colors.cardDeep.copy(alpha = deepAlpha.toFloat()),
                topLeft = Offset.Zero,
                size = androidx.compose.ui.geometry.Size(width.toFloat(), layout.cardHeight.toFloat()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(layout.cardRadius.toFloat()),
            )
        }
        drawText("AERO", layout.cardPad, 37.0, DragCardToSendTokens.Type.brand, DragCardToSendTokens.Colors.cardInk.copy(alpha = engine.cardOpacity.toFloat()), paint, bold = true, italic = true)
        drawText(formatMoney(engine.displayedBalance), width - layout.cardPad, 40.0, DragCardToSendTokens.Type.balance, DragCardToSendTokens.Colors.cardInk.copy(alpha = engine.cardOpacity.toFloat()), paint, bold = true, align = Paint.Align.RIGHT)
        drawRoundRect(
            color = DragCardToSendTokens.Colors.chip.copy(alpha = engine.cardOpacity.toFloat()),
            topLeft = Offset(layout.cardPad.toFloat(), 58.0f),
            size = androidx.compose.ui.geometry.Size(30.0f, 22.0f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.0f),
        )
        drawText("VALID THRU", width - layout.cardPad, 73.0, 9.0, DragCardToSendTokens.Colors.cardFaint.copy(alpha = engine.cardOpacity.toFloat()), paint, bold = true, align = Paint.Align.RIGHT)
        drawText("••••  ••••  ••••  1234", layout.cardPad, 101.0, DragCardToSendTokens.Type.cardSmall + 1.0, DragCardToSendTokens.Colors.cardInk.copy(alpha = engine.cardOpacity.toFloat()), paint, bold = true)
        drawText("12/35", width - layout.cardPad, 101.0, DragCardToSendTokens.Type.cardSmall + 1.0, DragCardToSendTokens.Colors.cardInk.copy(alpha = engine.cardOpacity.toFloat()), paint, bold = true, align = Paint.Align.RIGHT)
        drawText("JULES MORROW", layout.cardPad, 122.0, 10.0, DragCardToSendTokens.Colors.cardFaint.copy(alpha = engine.cardOpacity.toFloat()), paint, bold = true)
        val actions = listOf(ArrowKind.Out to "Send to", ArrowKind.In to "Receive", ArrowKind.Add to "Add")
        actions.forEachIndexed { index, action ->
            val x = 44.0 + index * (width - 88.0) / 2.0
            val y = layout.cardHeight - 24.5 - 33.5
            drawCircle(DragCardToSendTokens.Colors.cardButton.copy(alpha = engine.cardOpacity.toFloat()), (layout.actionSize / 2.0).toFloat(), Offset(x.toFloat(), y.toFloat()))
            drawArrow(Offset(x.toFloat(), y.toFloat()), action.first, DragCardToSendTokens.Colors.cardInk.copy(alpha = engine.cardOpacity.toFloat()), 18.0)
            drawText(action.second, x, y + 38.0, DragCardToSendTokens.Type.cardLabel, DragCardToSendTokens.Colors.cardInk.copy(alpha = engine.cardOpacity.toFloat()), paint, bold = true, align = Paint.Align.CENTER)
        }
        val sheen = sin(2.0 * PI * engine.clockMs / DragCardToSendTokens.Ambient.sheenMs) * 0.35 + engine.tilt * 0.9 + engine.sway / 20.0
        rotate(18.0f, Offset((width / 2.0).toFloat(), (layout.cardHeight / 2.0).toFloat())) {
            translate((sheen * width).toFloat(), 0.0f) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.12f), Color.Transparent),
                    ),
                    topLeft = Offset((width * 0.2).toFloat(), -60.0f),
                    size = androidx.compose.ui.geometry.Size((width * 0.45).toFloat(), (layout.cardHeight + 120.0).toFloat()),
                    alpha = engine.cardOpacity.toFloat(),
                )
            }
        }
    }
}

private fun DrawScope.drawAvatar(image: ImageBitmap, centerX: Double, centerY: Double, size: Double, alpha: Float) {
    val left = centerX - size / 2.0
    val top = centerY - size / 2.0
    val path = Path().apply {
        addOval(Rect(left.toFloat(), top.toFloat(), (left + size).toFloat(), (top + size).toFloat()))
    }
    clipPath(path) {
        drawImage(
            image = image,
            dstOffset = IntOffset(left.toInt(), top.toInt()),
            dstSize = IntSize(size.toInt(), size.toInt()),
            alpha = alpha,
        )
    }
}

private enum class ArrowKind {
    Out,
    In,
    Add,
    Open,
    RowOut,
    RowIn,
}

private fun DrawScope.drawArrow(center: Offset, kind: ArrowKind, color: Color, size: Double) {
    val unit = (size / 20.0).toFloat()
    val origin = center - Offset((size / 2.0).toFloat(), (size / 2.0).toFloat())
    fun point(x: Double, y: Double): Offset {
        return origin + Offset((x * unit).toFloat(), (y * unit).toFloat())
    }
    when (kind) {
        ArrowKind.Add -> drawPlus(center, color, (size * 0.7).toFloat())
        ArrowKind.Open -> {
            drawLine(color, point(5.0, 15.0), point(15.0, 5.0), 1.8f, StrokeCap.Round)
            drawLine(color, point(8.0, 5.0), point(15.0, 5.0), 1.8f, StrokeCap.Round)
            drawLine(color, point(15.0, 5.0), point(15.0, 12.0), 1.8f, StrokeCap.Round)
        }
        else -> {
            val outgoing = kind == ArrowKind.Out || kind == ArrowKind.RowOut
            val row = kind == ArrowKind.RowOut || kind == ArrowKind.RowIn
            val start = if (outgoing) point(14.0, if (row) 12.0 else 15.0) else point(14.0, if (row) 3.0 else 4.0)
            val end = if (outgoing) point(5.0, if (row) 3.0 else 5.0) else point(5.0, if (row) 12.0 else 13.0)
            drawLine(color, start, end, 1.8f, StrokeCap.Round)
            val arrowY = if (outgoing) if (row) 3.0 else 5.0 else if (row) 12.0 else 13.0
            val otherY = if (outgoing) if (row) 9.5 else 12.0 else if (row) 5.5 else 6.0
            drawLine(color, point(5.0, otherY), point(5.0, arrowY), 1.8f, StrokeCap.Round)
            drawLine(color, point(5.0, arrowY), point(11.5, arrowY), 1.8f, StrokeCap.Round)
            if (row) {
                drawLine(color, point(4.0, 16.5), point(16.0, 16.5), 1.8f, StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.drawPlus(center: Offset, color: Color, size: Float) {
    drawLine(color, center - Offset(size / 2.0f, 0.0f), center + Offset(size / 2.0f, 0.0f), 1.8f, StrokeCap.Round)
    drawLine(color, center - Offset(0.0f, size / 2.0f), center + Offset(0.0f, size / 2.0f), 1.8f, StrokeCap.Round)
}

private fun DrawScope.drawCheck(center: Offset, color: Color, size: Float) {
    val left = center.x - size / 2.0f
    val top = center.y - size / 2.0f
    val path = Path().apply {
        moveTo(left + size * 0.12f, top + size * 0.52f)
        lineTo(left + size * 0.4f, top + size * 0.78f)
        lineTo(left + size * 0.9f, top + size * 0.2f)
    }
    drawPath(path, color, style = Stroke(2.0f, cap = StrokeCap.Round))
}

private fun DrawScope.drawText(
    text: String,
    x: Double,
    y: Double,
    size: Double,
    color: Color,
    paint: Paint,
    bold: Boolean = false,
    italic: Boolean = false,
    align: Paint.Align = Paint.Align.LEFT,
) {
    paint.textSize = size.toFloat()
    paint.color = color.toArgb()
    paint.textAlign = align
    paint.typeface = Typeface.create(
        "sans-serif",
        when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        },
    )
    drawContext.canvas.nativeCanvas.drawText(text, x.toFloat(), y.toFloat(), paint)
}

private data class ShapeBox(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
    val radiusX: Double,
    val radiusY: Double,
)

private fun pillAt(g: Double, width: Double): ShapeBox {
    val transition = smooth((g - 1.0) * 1.6)
    val narrow = if (g < 1.0) {
        mix(DragCardToSendTokens.Layout.pillWidth, DragCardToSendTokens.Drop.width, smooth(g))
    } else {
        DragCardToSendTokens.Drop.width
    }
    val pillWidth = mix(narrow, DragCardToSendTokens.Layout.islandWidth, transition)
    val height = mix(DragCardToSendTokens.Layout.pillHeight, DragCardToSendTokens.Layout.islandHeight, transition)
    val top = mix(DragCardToSendTokens.Layout.pillTop, DragCardToSendTokens.Layout.islandTop, transition)
    return ShapeBox((width - pillWidth) / 2.0, top, pillWidth, height, height / 2.0, height / 2.0)
}

private fun bodyAt(g: Double, width: Double, height: Double): ShapeBox {
    if (g <= 1.0) {
        val clamped = clamp01(g)
        val eased = 1.0 - (1.0 - clamped) * (1.0 - clamped)
        val bodyWidth = mix(120.0, DragCardToSendTokens.Drop.width, sin(clamped * PI / 2.0))
        val bodyHeight = mix(40.0, DragCardToSendTokens.Drop.height, clamped)
        return ShapeBox(
            left = (width - bodyWidth) / 2.0,
            top = mix(8.0, DragCardToSendTokens.Drop.top, eased),
            width = bodyWidth,
            height = bodyHeight,
            radiusX = bodyWidth / 2.0,
            radiusY = bodyHeight / 2.0,
        )
    }
    val transition = min(g - 1.0, 1.08)
    val sheetWidth = width - DragCardToSendTokens.Sheet.inset * 2.0
    val sheetHeight = height - DragCardToSendTokens.Sheet.top - DragCardToSendTokens.Sheet.bottomGap
    val bodyWidth = mix(DragCardToSendTokens.Drop.width, sheetWidth, transition)
    val bodyHeight = mix(DragCardToSendTokens.Drop.height, sheetHeight, transition)
    val top = mix(DragCardToSendTokens.Drop.top, DragCardToSendTokens.Sheet.top, clamp01(transition))
    val radiusX = max(DragCardToSendTokens.Sheet.radius, mix(DragCardToSendTokens.Drop.width / 2.0, DragCardToSendTokens.Sheet.radius, clamp01(transition)))
    val radiusY = max(DragCardToSendTokens.Sheet.radius, mix(DragCardToSendTokens.Drop.height / 2.0, DragCardToSendTokens.Sheet.radius, clamp01(transition)))
    return ShapeBox((width - bodyWidth) / 2.0, top, bodyWidth, bodyHeight, radiusX, radiusY)
}

private fun formatMoney(cents: Int): String {
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    val sign = if (cents < 0) "−" else ""
    return "${sign}\$${formatter.format(abs(cents) / 100.0)}"
}
