package com.ngocdevv.nativecook.blocks.gestures.glasslockkey

import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.min

/** A full-screen Compose port of the approved React Native brass-vault block. */
@Composable
fun GlassLockKey(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    var reducedMotion by remember { mutableStateOf(glassLockKeyReducedMotion(context)) }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reducedMotion = glassLockKeyReducedMotion(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    val serif = remember(context) {
        FontFamily(Font(GlassLockKeyTokens.Assets.instrumentSerif, context.assets))
    }
    val regular = remember(context) {
        FontFamily(Font(GlassLockKeyTokens.Assets.interRegular, context.assets))
    }
    val medium = remember(context) {
        FontFamily(Font(GlassLockKeyTokens.Assets.interMedium, context.assets))
    }
    val semibold = remember(context) {
        FontFamily(Font(GlassLockKeyTokens.Assets.interSemibold, context.assets))
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(GlassLockKeyTokens.Colors.bgTop)),
    ) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val geometry = remember(width, height) { glassLockKeyGeometry(width, height) }
        val simulation = remember(geometry, autoPlay) {
            GlassLockKeySimulation(geometry, reducedMotion, autoPlay)
        }
        val revision = remember(simulation) { mutableIntStateOf(0) }
        var open by remember(simulation) { mutableStateOf(false) }
        var wrong by remember(simulation) { mutableStateOf(false) }
        val safeTop = WindowInsets.safeDrawing.getTop(density) / density.density
        val safeBottom = WindowInsets.safeDrawing.getBottom(density) / density.density

        LaunchedEffect(simulation, autoPlay, reducedMotion) {
            simulation.reduced = reducedMotion
            var previous = withFrameNanos { it }
            while (isActive) {
                val frame = withFrameNanos { it }
                val elapsed = max(0.0, (frame - previous) / 1_000_000_000.0)
                previous = frame
                simulation.advance(elapsed, autoPlay)
                simulation.drainEvents().forEach { glassLockKeyHaptic(view, it) }
                if (open != simulation.isOpen) open = simulation.isOpen
                if (wrong != simulation.wrongHint) wrong = simulation.wrongHint
                revision.intValue++
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(simulation, density) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        simulation.down(
                            (down.position.x / density.density).toDouble(),
                            (down.position.y / density.density).toDouble(),
                        )
                        var released = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (event.changes.count { it.pressed } > 1) break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                simulation.move(
                                    (change.position.x / density.density).toDouble(),
                                    (change.position.y / density.density).toDouble(),
                                )
                                change.consume()
                                if (!change.pressed) {
                                    val velocity = tracker.calculateVelocity()
                                    simulation.up(
                                        (velocity.x / density.density).toDouble(),
                                        (velocity.y / density.density).toDouble(),
                                    )
                                    released = true
                                    break
                                }
                            }
                        } finally {
                            if (!released) simulation.up(0.0, 0.0)
                            simulation.drainEvents().forEach { glassLockKeyHaptic(view, it) }
                            revision.intValue++
                        }
                    }
                },
        ) {
            GlassLockKeyScene(
                simulation = simulation,
                revision = revision,
                modifier = Modifier.fillMaxSize(),
            )
            GlassLockKeyAccessibilityKey(
                simulation = simulation,
                revision = revision,
                open = open,
            )
        }

        GlassLockKeyHeader(
            simulation = simulation,
            revision = revision,
            reducedMotion = reducedMotion,
            safeTop = safeTop,
            serif = serif,
            medium = medium,
            open = open,
        )
        GlassLockKeyHint(
            simulation = simulation,
            revision = revision,
            safeBottom = safeBottom,
            regular = regular,
            wrong = wrong,
        )
        GlassLockKeyVaultSheet(
            simulation = simulation,
            revision = revision,
            reducedMotion = reducedMotion,
            stageHeight = height,
            safeBottom = safeBottom,
            enabled = open,
            regular = regular,
            medium = medium,
            semibold = semibold,
        )
    }
}

@Composable
private fun GlassLockKeyAccessibilityKey(
    simulation: GlassLockKeySimulation,
    revision: MutableIntState,
    open: Boolean,
) {
    val geometry = simulation.geometry
    Box(
        modifier = Modifier
            .offset(
                x = (geometry.restX - 44.0).dp,
                y = geometry.restY.dp,
            )
            .size(
                width = 88.dp,
                height = ((GlassLockKeyTokens.Key.blade + GlassLockKeyTokens.Key.ring) * geometry.scale).dp,
            )
            .semantics {
                role = Role.Button
                contentDescription = if (open) GlassLockKeyTokens.Copy.lock else GlassLockKeyTokens.Copy.unlock
                onClick(GlassLockKeyTokens.Copy.keyHint) {
                    simulation.command(
                        if (open) GlassLockKeySimulation.Action.lock else GlassLockKeySimulation.Action.unlock,
                    )
                    revision.intValue++
                    true
                }
            },
    )
}

@Composable
private fun GlassLockKeyHeader(
    simulation: GlassLockKeySimulation,
    revision: State<Int>,
    reducedMotion: Boolean,
    safeTop: Float,
    serif: FontFamily,
    medium: FontFamily,
    open: Boolean,
) {
    Column(
        modifier = Modifier
            .offset(x = GlassLockKeyTokens.Type.side.dp, y = (safeTop + GlassLockKeyTokens.Type.top).dp)
            .fillMaxWidth()
            .padding(end = (GlassLockKeyTokens.Type.side * 2.0).dp),
    ) {
        Text(
            text = GlassLockKeyTokens.Copy.eyebrow,
            color = Color(GlassLockKeyTokens.Colors.mute),
            fontFamily = medium,
            fontSize = GlassLockKeyTokens.Type.eyebrowSize.sp,
            lineHeight = 16.sp,
            letterSpacing = GlassLockKeyTokens.Type.eyebrowTracking.sp,
        )
        Box(
            modifier = Modifier
                .padding(top = GlassLockKeyTokens.Type.gap.dp)
                .height(GlassLockKeyTokens.Type.titleLine.dp)
                .fillMaxWidth()
                .semantics {
                    heading()
                    contentDescription = if (open) {
                        GlassLockKeyTokens.Copy.unlocked
                    } else {
                        GlassLockKeyTokens.Copy.locked
                    }
                },
        ) {
            Text(
                text = GlassLockKeyTokens.Copy.locked,
                color = Color(GlassLockKeyTokens.Colors.ink),
                fontFamily = serif,
                fontSize = GlassLockKeyTokens.Type.titleSize.sp,
                lineHeight = GlassLockKeyTokens.Type.titleLine.sp,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.graphicsLayer {
                    revision.value
                    val progress = ((simulation.shackle - 0.1) / 0.3).coerceIn(0.0, 1.0)
                    alpha = (1.0 - progress).toFloat()
                    translationY = if (reducedMotion) 0f else (-6.0 * progress * density).toFloat()
                },
            )
            Text(
                text = GlassLockKeyTokens.Copy.unlocked,
                color = Color(GlassLockKeyTokens.Colors.ink),
                fontFamily = serif,
                fontSize = GlassLockKeyTokens.Type.titleSize.sp,
                lineHeight = GlassLockKeyTokens.Type.titleLine.sp,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.graphicsLayer {
                    revision.value
                    val progress = ((simulation.shackle - 0.5) / 0.3).coerceIn(0.0, 1.0)
                    alpha = progress.toFloat()
                    translationY = if (reducedMotion) 0f else (6.0 * (1.0 - progress) * density).toFloat()
                },
            )
        }
    }
}

@Composable
private fun GlassLockKeyHint(
    simulation: GlassLockKeySimulation,
    revision: State<Int>,
    safeBottom: Float,
    regular: FontFamily,
    wrong: Boolean,
) {
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = GlassLockKeyTokens.Type.side.dp,
                end = GlassLockKeyTokens.Type.side.dp,
                bottom = (safeBottom + GlassLockKeyTokens.Type.bottom).dp,
            )
            .graphicsLayer {
                revision.value
                alpha = (1.0 - (simulation.sheet * 3.0).coerceIn(0.0, 1.0)).toFloat()
            },
    ) {
        Text(
            text = if (wrong) GlassLockKeyTokens.Copy.wrongWay else GlassLockKeyTokens.Copy.hint,
            color = Color(if (wrong) GlassLockKeyTokens.Colors.error else GlassLockKeyTokens.Colors.mute),
            fontFamily = regular,
            fontSize = GlassLockKeyTokens.Type.hintSize.sp,
            lineHeight = GlassLockKeyTokens.Type.hintLine.sp,
        )
    }
}

@Composable
private fun GlassLockKeyVaultSheet(
    simulation: GlassLockKeySimulation,
    revision: MutableIntState,
    reducedMotion: Boolean,
    stageHeight: Double,
    safeBottom: Float,
    enabled: Boolean,
    regular: FontFamily,
    medium: FontFamily,
    semibold: FontFamily,
) {
    val density = LocalDensity.current
    val height = stageHeight * GlassLockKeyTokens.Sheet.heightRatio
    val shape = RoundedCornerShape(
        topStart = GlassLockKeyTokens.Sheet.radius.dp,
        topEnd = GlassLockKeyTokens.Sheet.radius.dp,
    )
    Column(
        modifier = Modifier
            .offset(y = (stageHeight - height).dp)
            .fillMaxWidth()
            .height(height.dp)
            .graphicsLayer {
                revision.value
                val progress = simulation.sheet.coerceIn(0.0, 1.0)
                alpha = if (reducedMotion) progress.toFloat() else 1f
                translationY = if (reducedMotion) {
                    0f
                } else {
                    ((1.0 - progress) * (height + 40.0) * density.density).toFloat()
                }
            }
            .clip(shape)
            .background(Color(GlassLockKeyTokens.Colors.sheet))
            .border(
                width = (1f / density.density).dp,
                color = Color(GlassLockKeyTokens.Colors.sheetEdge),
                shape = shape,
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        event.changes.filterNot { it.isConsumed }.forEach { it.consume() }
                    }
                }
            }
            .padding(
                start = GlassLockKeyTokens.Sheet.pad.dp,
                end = GlassLockKeyTokens.Sheet.pad.dp,
                top = 8.dp,
                bottom = (safeBottom + GlassLockKeyTokens.Sheet.pad).dp,
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.16f)),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = GlassLockKeyTokens.Copy.head,
                color = Color(GlassLockKeyTokens.Colors.ink),
                fontFamily = semibold,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = GlassLockKeyTokens.Copy.meta,
                color = Color(GlassLockKeyTokens.Colors.mute),
                fontFamily = regular,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
        Column(
            modifier = Modifier
                .padding(top = 8.dp)
                .weight(1f),
        ) {
            GlassLockKeyTokens.Copy.rows.forEachIndexed { index, row ->
                GlassLockKeyVaultRow(
                    row = row,
                    index = index,
                    simulation = simulation,
                    revision = revision,
                    reducedMotion = reducedMotion,
                    regular = regular,
                    medium = medium,
                    last = index == GlassLockKeyTokens.Copy.rows.lastIndex,
                )
            }
        }
        GlassLockKeyButton(
            enabled = enabled,
            semibold = semibold,
            onClick = {
                simulation.command(GlassLockKeySimulation.Action.lock)
                revision.intValue++
            },
        )
    }
}

@Composable
private fun GlassLockKeyVaultRow(
    row: GlassLockKeyTokens.Copy.Row,
    index: Int,
    simulation: GlassLockKeySimulation,
    revision: State<Int>,
    reducedMotion: Boolean,
    regular: FontFamily,
    medium: FontFamily,
    last: Boolean,
) {
    val density = LocalDensity.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(GlassLockKeyTokens.Sheet.row.dp)
            .graphicsLayer {
                revision.value
                val progress = (
                    (simulation.sheet - 0.35 - index * GlassLockKeyTokens.SheetMotion.stagger * 2.0) / 0.45
                    ).coerceIn(0.0, 1.0)
                alpha = if (reducedMotion) simulation.sheet.toFloat() else progress.toFloat()
                translationY = if (reducedMotion) 0f else ((1.0 - progress) * 16.0 * density.density).toFloat()
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(GlassLockKeyTokens.Sheet.icon.dp)
                .clip(RoundedCornerShape(GlassLockKeyTokens.Sheet.iconRadius.dp))
                .background(Color(GlassLockKeyTokens.Colors.rowIcon)),
        ) {
            GlassLockKeyRowIcon(row.icon)
        }
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        ) {
            Text(
                text = row.title,
                color = Color(GlassLockKeyTokens.Colors.ink),
                fontFamily = medium,
                fontSize = 16.sp,
                lineHeight = 21.sp,
            )
            Text(
                text = row.subtitle,
                color = Color(GlassLockKeyTokens.Colors.mute),
                fontFamily = regular,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Canvas(
            modifier = Modifier
                .padding(end = 4.dp)
                .size(8.dp)
                .graphicsLayer { rotationZ = 45f },
        ) {
            val stroke = 1.5.dp.toPx()
            drawLine(
                color = Color(GlassLockKeyTokens.Colors.faint),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = stroke,
            )
            drawLine(
                color = Color(GlassLockKeyTokens.Colors.faint),
                start = Offset(size.width, 0f),
                end = Offset(size.width, size.height),
                strokeWidth = stroke,
            )
        }
    }
    if (!last) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((1f / density.density).dp)
                .background(Color(GlassLockKeyTokens.Colors.sheetLine)),
        )
    }
}

@Composable
private fun GlassLockKeyRowIcon(icon: GlassLockKeyTokens.Copy.Icon) {
    val color = Color(GlassLockKeyTokens.Colors.rowIconInk)
    Canvas(Modifier.size(20.dp)) {
        val unit = size.width / 20f
        when (icon) {
            GlassLockKeyTokens.Copy.Icon.codes -> {
                for (row in 0..2) for (column in 0..2) {
                    drawRoundRect(
                        color = color.copy(alpha = if ((row + column) % 2 == 0) 1f else 0.55f),
                        topLeft = Offset((3f + column * 5.2f) * unit, (3f + row * 5.2f) * unit),
                        size = androidx.compose.ui.geometry.Size(3.6f * unit, 3.6f * unit),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(unit, unit),
                    )
                }
            }
            GlassLockKeyTokens.Copy.Icon.document -> {
                val path = Path().apply {
                    moveTo(5f * unit, 2.5f * unit)
                    lineTo(11.5f * unit, 2.5f * unit)
                    lineTo(15f * unit, 6f * unit)
                    lineTo(15f * unit, 17.5f * unit)
                    lineTo(5f * unit, 17.5f * unit)
                    close()
                }
                drawPath(path, color, style = Stroke(1.5f * unit))
                drawLine(color, Offset(8f * unit, 10f * unit), Offset(12.5f * unit, 10f * unit), 1.5f * unit)
                drawLine(color, Offset(8f * unit, 13f * unit), Offset(12.5f * unit, 13f * unit), 1.5f * unit)
            }
            GlassLockKeyTokens.Copy.Icon.wifi -> {
                val outer = Path().apply {
                    moveTo(3f * unit, 8f * unit)
                    quadraticBezierTo(10f * unit, 1.5f * unit, 17f * unit, 8f * unit)
                }
                val middle = Path().apply {
                    moveTo(5.5f * unit, 10.8f * unit)
                    quadraticBezierTo(10f * unit, 6.6f * unit, 14.5f * unit, 10.8f * unit)
                }
                val inner = Path().apply {
                    moveTo(8f * unit, 13.5f * unit)
                    quadraticBezierTo(10f * unit, 11.7f * unit, 12f * unit, 13.5f * unit)
                }
                val style = Stroke(1.6f * unit, cap = StrokeCap.Round)
                drawPath(outer, color, style = style)
                drawPath(middle, color, style = style)
                drawPath(inner, color, style = style)
                drawCircle(color, 1.3f * unit, Offset(10f * unit, 16f * unit))
            }
        }
    }
}

@Composable
private fun GlassLockKeyButton(
    enabled: Boolean,
    semibold: FontFamily,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = snap(),
        label = "glass-lock-key-button",
    )
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(GlassLockKeyTokens.Sheet.button.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape((GlassLockKeyTokens.Sheet.button / 2.0).dp))
            .background(Color(GlassLockKeyTokens.Colors.button))
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = GlassLockKeyTokens.Copy.lock
            },
    ) {
        Canvas(Modifier.size(16.dp)) {
            val unit = size.width / 16f
            drawRoundRect(
                color = Color(GlassLockKeyTokens.Colors.buttonInk),
                topLeft = Offset(2.5f * unit, 7f * unit),
                size = androidx.compose.ui.geometry.Size(11f * unit, 7.5f * unit),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * unit, 2f * unit),
            )
            val path = Path().apply {
                moveTo(5f * unit, 7f * unit)
                lineTo(5f * unit, 5f * unit)
                cubicTo(5f * unit, 1f * unit, 11f * unit, 1f * unit, 11f * unit, 5f * unit)
                lineTo(11f * unit, 7f * unit)
            }
            drawPath(
                path,
                Color(GlassLockKeyTokens.Colors.buttonInk),
                style = Stroke(1.6f * unit, cap = StrokeCap.Round),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = GlassLockKeyTokens.Copy.lock,
            color = Color(GlassLockKeyTokens.Colors.buttonInk),
            fontFamily = semibold,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 20.sp,
        )
    }
}

private fun glassLockKeyReducedMotion(context: android.content.Context): Boolean =
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f

private fun glassLockKeyHaptic(view: android.view.View, event: Int) {
    val feedback = when (event) {
        GlassLockKeyTokens.Haptic.grab -> HapticFeedbackConstants.CLOCK_TICK
        GlassLockKeyTokens.Haptic.seat -> HapticFeedbackConstants.VIRTUAL_KEY
        GlassLockKeyTokens.Haptic.detent -> HapticFeedbackConstants.CLOCK_TICK
        GlassLockKeyTokens.Haptic.open -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        GlassLockKeyTokens.Haptic.wrong -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        else -> return
    }
    view.performHapticFeedback(feedback)
}
