package com.ngocdevv.nativecook.blocks.gestures.glasslockkey

import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Wifi
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.min

/** A full-screen Compose port of the accepted React Native Glass Lock Key upgrade. */
@Composable
fun GlassLockKey(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    var reducedMotion by remember {
        mutableStateOf(glassLockKeyReducedMotion(context))
    }

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
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(GlassLockKeyTokens.VIEW.backgroundArgb)),
    ) {
        val stageWidth = maxWidth.value.toDouble()
        val stageHeight = maxHeight.value.toDouble()
        val safeTop = WindowInsets.safeDrawing.getTop(density).toFloat() / density.density
        val safeBottom = WindowInsets.safeDrawing.getBottom(density).toFloat() / density.density
        val simulation = remember(stageWidth, autoPlay) {
            GlassLockKeySimulation(stageWidth, reducedMotion, autoPlay)
        }
        val revision = remember(simulation) {
            mutableIntStateOf(0)
        }
        var interactiveMode by remember(simulation) {
            mutableStateOf(GlassLockKeySimulation.IDLE)
        }

        LaunchedEffect(simulation, autoPlay, reducedMotion) {
            var previousFrame = withFrameNanos { it }
            while (isActive) {
                val frame = withFrameNanos { it }
                val elapsed = max(0.0, (frame - previousFrame) / 1_000_000_000.0)
                val frameDt = min(elapsed, GlassLockKeyTokens.SIM.maxFrameDelta)
                previousFrame = frame
                simulation.reduced = reducedMotion
                simulation.advance(frameDt, autoPlay)
                simulation.drainEvents().forEach { event ->
                    glassLockKeyHaptic(view, event)
                }
                if (interactiveMode != simulation.mode) {
                    interactiveMode = simulation.mode
                }
                revision.intValue++
            }
        }

        val inputModifier = Modifier
            .matchParentSize()
            .pointerInput(simulation, density) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    simulation.down(
                        (down.position.x / density.density).toDouble(),
                        (down.position.y / density.density).toDouble(),
                    )
                    var finished = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: break
                            if (event.changes.count { it.pressed } > 1) {
                                break
                            }
                            tracker.addPosition(change.uptimeMillis, change.position)
                            simulation.move(
                                (change.position.x / density.density).toDouble(),
                                (change.position.y / density.density).toDouble(),
                            )
                            if (!change.pressed) {
                                val velocity = tracker.calculateVelocity()
                                simulation.up(
                                    (velocity.x / density.density).toDouble(),
                                    (velocity.y / density.density).toDouble(),
                                )
                                finished = true
                                change.consume()
                                break
                            }
                            change.consume()
                        }
                    } finally {
                        if (!finished) {
                            simulation.up(0.0, 0.0)
                        }
                        simulation.drainEvents().forEach { event ->
                            glassLockKeyHaptic(view, event)
                        }
                        revision.intValue++
                    }
                }
            }

        Box(inputModifier) {
            GlassLockKeyScene(
                simulation = simulation,
                revision = revision,
                modifier = Modifier.matchParentSize(),
            )

            GlassLockKeyAccessibilityKey(
                simulation = simulation,
                revision = revision,
                stageWidth = stageWidth,
                mode = interactiveMode,
            )
        }

        GlassLockKeyHeader(
            simulation = simulation,
            revision = revision,
            safeTop = safeTop,
        )

        GlassLockKeyHint(
            simulation = simulation,
            revision = revision,
            safeBottom = safeBottom,
        )

        GlassLockKeyVaultSheet(
            simulation = simulation,
            revision = revision,
            reducedMotion = reducedMotion,
            stageHeight = stageHeight,
            safeBottom = safeBottom,
            enabled = interactiveMode == GlassLockKeySimulation.OPEN,
        )
    }
}

@Composable
private fun GlassLockKeyAccessibilityKey(
    simulation: GlassLockKeySimulation,
    revision: MutableIntState,
    stageWidth: Double,
    mode: Int,
) {
    val scale = stageWidth / GlassLockKeyTokens.STAGE_WIDTH
    val left = (simulation.restX - GlassLockKeyTokens.VIEW.keyTargetHalfWidth).dp
    val top = (simulation.restY - GlassLockKeyTokens.VIEW.keyTargetTop * scale).dp
    val width = GlassLockKeyTokens.VIEW.keyTargetWidth.dp
    val height = (GlassLockKeyTokens.VIEW.keyTargetHeight * scale).dp
    val canUnlock = mode == GlassLockKeySimulation.IDLE
    val canLock = mode == GlassLockKeySimulation.OPEN
    Box(
        modifier = Modifier
            .offset(x = left, y = top)
            .size(width = width, height = height)
            .semantics {
                role = Role.Button
                contentDescription = if (canLock) GlassLockKeyTokens.COPY.lock else GlassLockKeyTokens.COPY.unlock
                stateDescription = if (canLock) GlassLockKeyTokens.COPY.unlocked else GlassLockKeyTokens.COPY.locked
                if (!canUnlock && !canLock) {
                    disabled()
                }
                onClick(GlassLockKeyTokens.COPY.keyHint) {
                    when {
                        canUnlock -> {
                            simulation.command("unlock")
                            revision.intValue++
                            true
                        }
                        canLock -> {
                            simulation.command("lock")
                            revision.intValue++
                            true
                        }
                        else -> false
                    }
                }
                customActions = when {
                    canUnlock -> listOf(CustomAccessibilityAction("Increment") {
                        simulation.command("unlock")
                        revision.intValue++
                        true
                    })
                    canLock -> listOf(CustomAccessibilityAction("Decrement") {
                        simulation.command("lock")
                        revision.intValue++
                        true
                    })
                    else -> emptyList()
                }
            },
    )
}

@Composable
private fun GlassLockKeyHeader(
    simulation: GlassLockKeySimulation,
    revision: State<Int>,
    safeTop: Float,
) {
    Column(
        modifier = Modifier
            .offset(x = GlassLockKeyTokens.SPACE.xl.dp, y = (safeTop + GlassLockKeyTokens.SPACE.xl).dp)
            .fillMaxWidth()
            .padding(end = (GlassLockKeyTokens.SPACE.xl * 2.0).dp)
            .graphicsLayer {
                revision.value
                alpha = (1.0 - simulation.sheet * GlassLockKeyTokens.VIEW.headerSheetFade)
                    .toFloat()
                    .coerceIn(0f, 1f)
            },
    ) {
        Text(
            text = GlassLockKeyTokens.COPY.eyebrow,
            color = Color(GlassLockKeyTokens.VIEW.secondary50Argb),
            fontSize = GlassLockKeyTokens.TYPE.eyebrowSize.sp,
            letterSpacing = GlassLockKeyTokens.TYPE.eyebrowTracking.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(
            modifier = Modifier
                .padding(top = GlassLockKeyTokens.SPACE.sm.dp)
                .height(GlassLockKeyTokens.VIEW.titleBoxHeight.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(0.dp)),
        ) {
            Text(
                text = GlassLockKeyTokens.COPY.locked,
                color = Color(GlassLockKeyTokens.VIEW.inkArgb),
                fontSize = GlassLockKeyTokens.VIEW.titleSize.sp,
                lineHeight = GlassLockKeyTokens.VIEW.titleLineHeight.sp,
                letterSpacing = GlassLockKeyTokens.VIEW.titleTracking.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer {
                    revision.value
                    val shackle = simulation.shackle.coerceIn(0.0, 1.0)
                    alpha = (
                        1.0 - ((shackle - GlassLockKeyTokens.VIEW.titleTransitionStart) /
                            GlassLockKeyTokens.VIEW.titleTransitionSpan).coerceIn(0.0, 1.0)
                        ).toFloat()
                    translationY = (-GlassLockKeyTokens.VIEW.titleTravel * shackle).toFloat() * density
                },
            )
            Text(
                text = GlassLockKeyTokens.COPY.unlocked,
                color = Color(GlassLockKeyTokens.VIEW.inkArgb),
                fontSize = GlassLockKeyTokens.VIEW.titleSize.sp,
                lineHeight = GlassLockKeyTokens.VIEW.titleLineHeight.sp,
                letterSpacing = GlassLockKeyTokens.VIEW.titleTracking.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer {
                    revision.value
                    val shown = (
                        (simulation.shackle - GlassLockKeyTokens.VIEW.titleTransitionStart) /
                            GlassLockKeyTokens.VIEW.titleTransitionSpan
                        ).coerceIn(0.0, 1.0)
                    alpha = shown.toFloat()
                    translationY = (
                        GlassLockKeyTokens.VIEW.titleTravel *
                            (1.0 - simulation.shackle.coerceIn(0.0, 1.0))
                        ).toFloat() * density
                },
            )
        }
        Text(
            text = GlassLockKeyTokens.COPY.meta,
            color = Color(GlassLockKeyTokens.VIEW.secondary50Argb),
            fontSize = GlassLockKeyTokens.VIEW.metaSize.sp,
            modifier = Modifier.padding(top = GlassLockKeyTokens.SPACE.xs.dp),
        )
    }
}

@Composable
private fun GlassLockKeyHint(
    simulation: GlassLockKeySimulation,
    revision: State<Int>,
    safeBottom: Float,
) {
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = GlassLockKeyTokens.SPACE.xl.dp,
                end = GlassLockKeyTokens.SPACE.xl.dp,
                bottom = (safeBottom + GlassLockKeyTokens.SPACE.xxl).dp,
            )
            .graphicsLayer {
                revision.value
                alpha = (
                    1.0 - (simulation.sheet * GlassLockKeyTokens.VIEW.hintSheetFade).coerceIn(0.0, 1.0)
                    ).toFloat()
            },
    ) {
        Text(
            text = GlassLockKeyTokens.COPY.hint,
            color = Color(GlassLockKeyTokens.VIEW.secondary55Argb),
            fontSize = GlassLockKeyTokens.VIEW.hintSize.sp,
        )
    }
}

@Composable
private fun GlassLockKeyVaultSheet(
    simulation: GlassLockKeySimulation,
    revision: State<Int>,
    reducedMotion: Boolean,
    stageHeight: Double,
    safeBottom: Float,
    enabled: Boolean,
) {
    val density = LocalDensity.current
    val sheetHeight = stageHeight * GlassLockKeyTokens.SHEET.heightRatio
    val surfaceInput = if (enabled) {
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                var inGesture = false
                var childClaimed = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val startsGesture = event.changes.any { change ->
                        change.pressed && !change.previousPressed
                    }
                    if (!inGesture && startsGesture) {
                        inGesture = true
                        // Final pass sees a child clickable's Main-pass down consumption.
                        childClaimed = event.changes.any { change -> change.isConsumed }
                    }
                    if (!childClaimed) {
                        event.changes.forEach { change ->
                            change.consume()
                        }
                    }
                    if (event.changes.none { change -> change.pressed }) {
                        inGesture = false
                        childClaimed = false
                    }
                }
            }
        }
    } else {
        Modifier
    }
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(sheetHeight.dp)
                .graphicsLayer {
                    revision.value
                    val progress = simulation.sheet.coerceIn(0.0, 1.0)
                    alpha = if (reducedMotion) progress.toFloat() else 1f
                    translationY = if (reducedMotion) {
                        0f
                    } else {
                        ((1.0 - progress) * (sheetHeight + GlassLockKeyTokens.VIEW.sheetOffscreenExtra))
                            .toFloat() * density.density
                    }
                }
                .padding(horizontal = GlassLockKeyTokens.SPACE.sm.dp),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(y = (-GlassLockKeyTokens.SPACE.sm).dp)
                    .clip(RoundedCornerShape(GlassLockKeyTokens.SPACE.xxl.dp))
                    .then(surfaceInput),
            ) {
                // Compose blur cannot sample sibling Canvas content; this is the static light material layer only.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(GlassLockKeyTokens.VIEW.androidSheetBlurRadius.dp)
                        .background(
                            Color(GlassLockKeyTokens.VIEW.whiteArgb).copy(
                                alpha = GlassLockKeyTokens.VIEW.sheetTintAlpha.toFloat(),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(GlassLockKeyTokens.VIEW.sheetEdgeHeight.dp)
                        .background(
                            Color(GlassLockKeyTokens.VIEW.whiteArgb).copy(
                                alpha = GlassLockKeyTokens.VIEW.sheetEdgeAlpha.toFloat(),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier.padding(
                        start = GlassLockKeyTokens.SPACE.lg.dp,
                        end = GlassLockKeyTokens.SPACE.lg.dp,
                        top = GlassLockKeyTokens.SPACE.xl.dp,
                        bottom = (safeBottom + GlassLockKeyTokens.SPACE.lg).dp,
                    ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = GlassLockKeyTokens.SPACE.xs.dp,
                                end = GlassLockKeyTokens.SPACE.xs.dp,
                                bottom = GlassLockKeyTokens.SPACE.md.dp,
                            ),
                    ) {
                        Text(
                            text = GlassLockKeyTokens.COPY.head,
                            color = Color(GlassLockKeyTokens.VIEW.inkArgb),
                            fontSize = GlassLockKeyTokens.VIEW.sheetTitleSize.sp,
                            letterSpacing = GlassLockKeyTokens.VIEW.sheetTitleTracking.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.alignByBaseline(),
                        )
                        Text(
                            text = GlassLockKeyTokens.COPY.sheetMeta,
                            color = Color(GlassLockKeyTokens.VIEW.secondary50Argb),
                            fontSize = GlassLockKeyTokens.VIEW.sheetMetaSize.sp,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape((GlassLockKeyTokens.SPACE.xxl - GlassLockKeyTokens.SPACE.lg).dp))
                            .background(
                                Color(GlassLockKeyTokens.VIEW.whiteArgb).copy(
                                    alpha = GlassLockKeyTokens.VIEW.groupAlpha.toFloat(),
                                ),
                            ),
                    ) {
                        GlassLockKeyTokens.COPY.rows.forEachIndexed { index, row ->
                            GlassLockKeyVaultRow(
                                index = index,
                                simulation = simulation,
                                revision = revision,
                                reducedMotion = reducedMotion,
                                title = row.title,
                                subtitle = row.sub,
                                symbol = row.symbol,
                                hasHairline = index < GlassLockKeyTokens.COPY.rows.lastIndex,
                            )
                        }
                    }
                    GlassLockKeyPressScale(
                        enabled = enabled,
                        reducedMotion = reducedMotion,
                        onClick = { simulation.command("lock") },
                        modifier = Modifier
                            .padding(top = GlassLockKeyTokens.SPACE.lg.dp)
                            .fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = Color(GlassLockKeyTokens.VIEW.whiteArgb),
                                modifier = Modifier.size(GlassLockKeyTokens.VIEW.buttonIconSize.dp),
                            )
                            Text(
                                text = GlassLockKeyTokens.COPY.lock,
                                color = Color(GlassLockKeyTokens.VIEW.whiteArgb),
                                fontSize = GlassLockKeyTokens.VIEW.buttonTextSize.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = GlassLockKeyTokens.SPACE.sm.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassLockKeyVaultRow(
    index: Int,
    simulation: GlassLockKeySimulation,
    revision: State<Int>,
    reducedMotion: Boolean,
    title: String,
    subtitle: String,
    symbol: String,
    hasHairline: Boolean,
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(GlassLockKeyTokens.VIEW.rowHeight.dp)
            .graphicsLayer {
                revision.value
                val raw = (
                    simulation.sheet - GlassLockKeyTokens.VIEW.rowStart -
                        index * GlassLockKeyTokens.GM.rows.stagger
                    ) /
                    GlassLockKeyTokens.GM.rows.span
                val progress = raw.coerceIn(0.0, 1.0)
                val eased = 1.0 - (1.0 - progress) * (1.0 - progress) * (1.0 - progress)
                alpha = eased.toFloat()
                translationY = if (reducedMotion) 0f else {
                    (GlassLockKeyTokens.GM.rows.rise * (1.0 - eased)).toFloat() * density.density
                }
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = GlassLockKeyTokens.SPACE.lg.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(GlassLockKeyTokens.VIEW.rowIconSize.dp)
                    .clip(RoundedCornerShape(GlassLockKeyTokens.SPACE.sm.dp))
                    .background(Color(GlassLockKeyTokens.VIEW.rowIconBgArgb)),
            ) {
                val image = when (symbol) {
                    "number" -> Icons.Filled.Numbers
                    "doc.text" -> Icons.Filled.Description
                    else -> Icons.Filled.Wifi
                }
                Icon(
                    imageVector = image,
                    contentDescription = null,
                    tint = Color(GlassLockKeyTokens.VIEW.rowIconInkArgb),
                    modifier = Modifier.size(GlassLockKeyTokens.VIEW.rowIconInkSize.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = GlassLockKeyTokens.SPACE.md.dp),
            ) {
                Text(
                    text = title,
                    color = Color(GlassLockKeyTokens.VIEW.inkArgb),
                    fontSize = GlassLockKeyTokens.VIEW.rowTitleSize.sp,
                    letterSpacing = GlassLockKeyTokens.VIEW.rowTitleTracking.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    color = Color(GlassLockKeyTokens.VIEW.secondary50Argb),
                    fontSize = GlassLockKeyTokens.VIEW.rowSubSize.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = GlassLockKeyTokens.VIEW.rowSubGap.dp),
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Color(GlassLockKeyTokens.VIEW.secondary30Argb),
                modifier = Modifier.size(GlassLockKeyTokens.VIEW.chevronSize.dp),
            )
        }
        if (hasHairline) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .height((1f / density.density).dp)
                    .fillMaxWidth()
                    .padding(start = GlassLockKeyTokens.VIEW.hairlineInset.dp)
                    .background(Color(GlassLockKeyTokens.VIEW.divider12Argb)),
            )
        }
    }
}

@Composable
private fun GlassLockKeyPressScale(
    enabled: Boolean,
    reducedMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) GlassLockKeyTokens.VIEW.pressScale.toFloat() else 1f,
        animationSpec = tween(
            durationMillis = if (pressed && !reducedMotion) {
                GlassLockKeyTokens.VIEW.pressInMs
            } else {
                GlassLockKeyTokens.VIEW.pressOutMs
            },
            easing = CubicBezierEasing(
                GlassLockKeyTokens.VIEW.pressBezierX1.toFloat(),
                GlassLockKeyTokens.VIEW.pressBezierY1.toFloat(),
                GlassLockKeyTokens.VIEW.pressBezierX2.toFloat(),
                GlassLockKeyTokens.VIEW.pressBezierY2.toFloat(),
            ),
        ),
        label = "glass-lock-key-press",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(GlassLockKeyTokens.VIEW.buttonHeight.dp)
            .clip(RoundedCornerShape(GlassLockKeyTokens.VIEW.buttonRadius.dp))
            .background(Color(GlassLockKeyTokens.VIEW.inkArgb))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = GlassLockKeyTokens.COPY.lock
            },
    ) {
        content()
    }
}

private fun glassLockKeyReducedMotion(context: android.content.Context): Boolean {
    return Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
}

private fun glassLockKeyHaptic(view: android.view.View, event: Int) {
    val feedback = when (event) {
        GlassLockKeyTokens.G_HAPTIC.grab.toInt() -> HapticFeedbackConstants.CLOCK_TICK
        GlassLockKeyTokens.G_HAPTIC.seat.toInt() -> HapticFeedbackConstants.VIRTUAL_KEY
        GlassLockKeyTokens.G_HAPTIC.detent.toInt() -> HapticFeedbackConstants.CLOCK_TICK
        GlassLockKeyTokens.G_HAPTIC.open.toInt() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
        }
        GlassLockKeyTokens.G_HAPTIC.wrong.toInt() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        }
        else -> return
    }
    view.performHapticFeedback(feedback)
}
