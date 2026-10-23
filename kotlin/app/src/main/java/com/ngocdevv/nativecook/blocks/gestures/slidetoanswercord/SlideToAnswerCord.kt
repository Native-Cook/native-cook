package com.ngocdevv.nativecook.blocks.gestures.slidetoanswercord

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.isActive

@Composable
fun SlideToAnswerCord(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val reducedMotion = rememberReducedMotion()
    val demoEnabled = autoPlay && !reducedMotion
    val view = LocalView.current
    val activity = LocalActivity.current
    val scene = remember { SlideToAnswerCordScene() }
    val safeBottom = WindowInsets.safeDrawing.getBottom(density) / density.density
    var widthDp by remember { mutableStateOf(402.0) }
    var heightDp by remember { mutableStateOf(874.0) }
    val geometry = remember(widthDp, heightDp) {
        SlideToAnswerCordSimulation.makeGeometry(widthDp, heightDp)
    }
    val sim = remember(geometry, reducedMotion, demoEnabled) {
        SlideToAnswerCordSimulation.initialSim(geometry, reducedMotion, demoEnabled)
    }
    var redraw by remember { mutableLongStateOf(0L) }
    var phase by remember(sim) { mutableIntStateOf(sim.phase) }
    var seconds by remember(sim) { mutableIntStateOf(0) }
    var muted by remember { mutableStateOf(false) }
    val declineInteraction = remember { MutableInteractionSource() }
    val acceptInteraction = remember { MutableInteractionSource() }
    val declinePressed by declineInteraction.collectIsPressedAsState()
    val acceptPressed by acceptInteraction.collectIsPressedAsState()
    val pressEasing = remember { CubicBezierEasing(0.23f, 1f, 0.32f, 1f) }
    val declineScale by animateFloatAsState(
        targetValue = if (declinePressed && !reducedMotion) Tokens.PRESS.scale.toFloat() else 1f,
        animationSpec = tween(
            durationMillis = if (declinePressed) Tokens.PRESS.inMs else Tokens.PRESS.outMs,
            easing = pressEasing,
        ),
        label = "decline-press",
    )
    val acceptScale by animateFloatAsState(
        targetValue = if (acceptPressed && !reducedMotion) Tokens.PRESS.scale.toFloat() else 1f,
        animationSpec = tween(
            durationMillis = if (acceptPressed) Tokens.PRESS.inMs else Tokens.PRESS.outMs,
            easing = pressEasing,
        ),
        label = "accept-press",
    )

    fun flushHaptics() {
        sim.events.forEach { event ->
            val feedback = when (event) {
                Tokens.HAPTIC.answer -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.CONTEXT_CLICK
                }
                Tokens.HAPTIC.decline -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.REJECT
                } else {
                    HapticFeedbackConstants.LONG_PRESS
                }
                Tokens.HAPTIC.tick, Tokens.HAPTIC.grab -> HapticFeedbackConstants.CLOCK_TICK
                else -> HapticFeedbackConstants.CONTEXT_CLICK
            }
            view.performHapticFeedback(feedback)
        }
        sim.events.clear()
    }

    fun command(action: SlideToAnswerCordSimulation.Input.Action) {
        SlideToAnswerCordSimulation.input(sim, geometry, SlideToAnswerCordSimulation.Input.Command(action))
        flushHaptics()
        phase = sim.phase
        redraw++
    }

    DisposableEffect(activity, view) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previousSystemUi = view.systemUiVisibility
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose { view.systemUiVisibility = previousSystemUi }
    }

    LaunchedEffect(sim, geometry, demoEnabled, reducedMotion) {
        if (demoEnabled) SlideToAnswerCordSimulation.preroll(sim, geometry)
        redraw++
        var previous = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = min((now - previous) / 1e9, 0.1)
            previous = now
            sim.reduced = reducedMotion
            SlideToAnswerCordSimulation.stepSim(sim, geometry, dt, demoEnabled)
            flushHaptics()
            if (phase != sim.phase) phase = sim.phase
            val currentSecond = floor(sim.callT).toInt()
            if (seconds != currentSecond) seconds = currentSecond
            redraw = now
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Tokens.COLORS.page)
            .onSizeChanged { size ->
                widthDp = size.width / density.density.toDouble()
                heightDp = size.height / density.density.toDouble()
            },
    ) {
        val buttonsY = heightDp - safeBottom - Tokens.LAYOUT.buttonsBottom -
            Tokens.LAYOUT.labelSize - Tokens.LAYOUT.labelGap - Tokens.LAYOUT.button / 2
        val connected = phase == SlideToAnswerCordSimulation.PHASE.connected
        val ringing = phase == SlideToAnswerCordSimulation.PHASE.ringing
        val status = when (phase) {
            SlideToAnswerCordSimulation.PHASE.connected -> "%02d:%02d".format(seconds / 60, seconds % 60)
            SlideToAnswerCordSimulation.PHASE.declined -> Tokens.COPY.declined
            SlideToAnswerCordSimulation.PHASE.ended -> Tokens.COPY.ended
            else -> Tokens.COPY.ringing
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(sim, geometry, density) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val scale = density.density.toDouble()
                        val point = SlideToAnswerCordSimulation.handsetPoint(
                            sim,
                            geometry,
                            Tokens.HANDSET.comX,
                            Tokens.HANDSET.comY,
                        )
                        val dx = down.position.x / scale - point.x
                        val dy = down.position.y / scale - point.y
                        if (hypot(dx, dy) >= Tokens.HANDSET.hit * geometry.s / 2) {
                            return@awaitEachGesture
                        }
                        tracker.resetTracking()
                        tracker.addPointerInputChange(down)
                        SlideToAnswerCordSimulation.input(
                            sim,
                            geometry,
                            SlideToAnswerCordSimulation.Input.Down(
                                down.position.x / scale,
                                down.position.y / scale,
                            ),
                        )
                        flushHaptics()
                        down.consume()
                        var released = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } > 1) {
                                    SlideToAnswerCordSimulation.input(
                                        sim,
                                        geometry,
                                        SlideToAnswerCordSimulation.Input.Up(0.0, 0.0),
                                    )
                                    flushHaptics()
                                    released = true
                                    break
                                }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPointerInputChange(change)
                                val velocity = tracker.calculateVelocity()
                                if (!change.pressed) {
                                    SlideToAnswerCordSimulation.input(
                                        sim,
                                        geometry,
                                        SlideToAnswerCordSimulation.Input.Move(
                                            change.position.x / scale,
                                            change.position.y / scale,
                                            velocity.x / scale,
                                            velocity.y / scale,
                                        ),
                                    )
                                    SlideToAnswerCordSimulation.input(
                                        sim,
                                        geometry,
                                        SlideToAnswerCordSimulation.Input.Up(
                                            velocity.x / scale,
                                            velocity.y / scale,
                                        ),
                                    )
                                    flushHaptics()
                                    released = true
                                    change.consume()
                                    break
                                }
                                SlideToAnswerCordSimulation.input(
                                    sim,
                                    geometry,
                                    SlideToAnswerCordSimulation.Input.Move(
                                        change.position.x / scale,
                                        change.position.y / scale,
                                        velocity.x / scale,
                                        velocity.y / scale,
                                    ),
                                )
                                change.consume()
                            }
                        } finally {
                            if (!released && sim.grab) {
                                SlideToAnswerCordSimulation.input(
                                    sim,
                                    geometry,
                                    SlideToAnswerCordSimulation.Input.Up(0.0, 0.0),
                                )
                                flushHaptics()
                            }
                        }
                    }
                },
        ) {
            @Suppress("UNUSED_VARIABLE") val frame = redraw
            drawIntoCanvas { composeCanvas ->
                composeCanvas.nativeCanvas.save()
                composeCanvas.nativeCanvas.scale(density.density, density.density)
                scene.draw(
                    composeCanvas.nativeCanvas,
                    sim,
                    geometry,
                    pixelScale = density.density,
                )
                composeCanvas.nativeCanvas.restore()
            }
        }

        Box(
            Modifier
                .size(150.dp, 210.dp)
                .offset {
                    IntOffset(
                        ((widthDp / 2 - 75) * density.density).roundToInt(),
                        ((geometry.restY - 26) * density.density).roundToInt(),
                    )
                }
                .semantics {
                    contentDescription = Tokens.COPY.a11yHandset
                    role = Role.Button
                    stateDescription = Tokens.COPY.a11yHint
                    customActions = listOf(
                        CustomAccessibilityAction(Tokens.COPY.accept) {
                            command(SlideToAnswerCordSimulation.Input.Action.answer)
                            true
                        },
                        CustomAccessibilityAction(Tokens.COPY.decline) {
                            command(
                                if (connected) {
                                    SlideToAnswerCordSimulation.Input.Action.end
                                } else {
                                    SlideToAnswerCordSimulation.Input.Action.decline
                                },
                            )
                            true
                        },
                    )
                },
        )

        SlideToAnswerCordCopy(
            status = status,
            widthDp = widthDp,
            heightDp = heightDp,
            buttonsY = buttonsY,
            sim = sim,
            frame = { redraw },
        )
        SlideToAnswerCordControls(
            widthDp = widthDp,
            buttonsY = buttonsY,
            muted = muted,
            enabled = connected,
            sim = sim,
            frame = { redraw },
            onMute = { muted = !muted },
        )
        SlideToAnswerCordCallButton(
            label = if (connected) Tokens.COPY.end else Tokens.COPY.decline,
            description = if (connected) "End call" else "Decline call",
            icon = Icons.Filled.CallEnd,
            color = Tokens.COLORS.decline,
            left = widthDp / 2 - Tokens.LAYOUT.buttonGap - Tokens.LAYOUT.button / 2,
            top = buttonsY - Tokens.LAYOUT.button / 2,
            enabled = ringing || connected,
            interactionSource = declineInteraction,
            pressScale = declineScale,
            sim = sim,
            frame = { redraw },
            translateX = { Tokens.LAYOUT.buttonGap * sim.wConn },
            opacity = { if (ringing || connected) 1.0 else 0.45 },
        ) {
            command(
                if (connected) {
                    SlideToAnswerCordSimulation.Input.Action.end
                } else {
                    SlideToAnswerCordSimulation.Input.Action.decline
                },
            )
        }
        SlideToAnswerCordCallButton(
            label = Tokens.COPY.accept,
            description = "Accept call",
            icon = Icons.Filled.Call,
            color = Tokens.COLORS.accept,
            left = widthDp / 2 + Tokens.LAYOUT.buttonGap - Tokens.LAYOUT.button / 2,
            top = buttonsY - Tokens.LAYOUT.button / 2,
            enabled = ringing,
            interactionSource = acceptInteraction,
            pressScale = acceptScale,
            sim = sim,
            frame = { redraw },
            translateX = { 0.0 },
            opacity = { 1.0 - sim.wConn },
            showPulse = true,
        ) {
            command(SlideToAnswerCordSimulation.Input.Action.answer)
        }
    }
}

@Composable
private fun SlideToAnswerCordCopy(
    status: String,
    widthDp: Double,
    heightDp: Double,
    buttonsY: Double,
    sim: SlideToAnswerCordSimulation.Sim,
    frame: () -> Long,
) {
    val density = LocalDensity.current
    val copyTop = heightDp * Tokens.LAYOUT.copyTopRatio
    val copyHeight = Tokens.LAYOUT.nameLine + Tokens.LAYOUT.metaGap + Tokens.LAYOUT.metaLine +
        Tokens.LAYOUT.gap + Tokens.LAYOUT.statusLine
    val copyWidth = widthDp - Tokens.LAYOUT.side * 2
    val pillTop = buttonsY + Tokens.GLASS.pillY - Tokens.GLASS.pillH / 2
    val lift = max(0.0, copyTop + copyHeight - (pillTop - Tokens.LAYOUT.copyToPill))

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(copyWidth.dp)
            .offset {
                IntOffset(
                    (Tokens.LAYOUT.side * density.density).roundToInt(),
                    (copyTop * density.density).roundToInt(),
                )
            }
            .graphicsLayer {
                frame()
                translationY = (-lift * sim.wConn * density.density).toFloat()
            },
    ) {
        Text(
            text = Tokens.COPY.name,
            color = Tokens.COLORS.ink,
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontSize = Tokens.LAYOUT.nameSize.sp,
                lineHeight = Tokens.LAYOUT.nameLine.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.2.sp,
            ),
        )
        Text(
            text = Tokens.COPY.meta,
            color = Tokens.COLORS.mute,
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontSize = Tokens.LAYOUT.metaSize.sp,
                lineHeight = Tokens.LAYOUT.metaLine.sp,
                fontWeight = FontWeight.Normal,
            ),
            modifier = Modifier.offset(y = Tokens.LAYOUT.metaGap.dp),
        )
        AnimatedContent(
            targetState = status,
            transitionSpec = {
                fadeIn(tween(200, delayMillis = 120)) togetherWith fadeOut(tween(120))
            },
            label = "call-status",
            modifier = Modifier.offset(y = (Tokens.LAYOUT.metaGap + Tokens.LAYOUT.gap).dp),
        ) { value ->
            Text(
                text = value,
                color = Tokens.COLORS.faint,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontSize = Tokens.LAYOUT.statusSize.sp,
                    lineHeight = Tokens.LAYOUT.statusLine.sp,
                    fontWeight = FontWeight.Normal,
                    fontFeatureSettings = "tnum",
                ),
                modifier = Modifier.requiredWidth(copyWidth.dp),
            )
        }
    }
}

@Composable
private fun SlideToAnswerCordControls(
    widthDp: Double,
    buttonsY: Double,
    muted: Boolean,
    enabled: Boolean,
    sim: SlideToAnswerCordSimulation.Sim,
    frame: () -> Long,
    onMute: () -> Unit,
) {
    val density = LocalDensity.current
    val controls = listOf(
        Triple(Tokens.COPY.controls[0], Icons.Filled.MicOff, onMute),
        Triple(Tokens.COPY.controls[1], Icons.Filled.Dialpad, {}),
        Triple(Tokens.COPY.controls[2], Icons.Filled.VolumeUp, {}),
    )
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .width((widthDp - Tokens.LAYOUT.side * 2).dp)
            .offset {
                IntOffset(
                    (Tokens.LAYOUT.side * density.density).roundToInt(),
                    ((buttonsY + Tokens.GLASS.pillY - Tokens.GLASS.pillH / 2) * density.density).roundToInt(),
                )
            }
            .graphicsLayer {
                frame()
                alpha = ((sim.wConn - 0.5) / 0.5).coerceIn(0.0, 1.0).toFloat()
                translationY = (12.0 * (1.0 - sim.wConn) * density.density).toFloat()
            },
    ) {
        controls.forEachIndexed { index, (control, icon, action) ->
            val selected = index == 0 && muted
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(80.dp)
                    .semantics {
                        contentDescription = control.label
                        role = Role.Button
                        if (selected) stateDescription = "On"
                    },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(Tokens.GLASS.controlH.dp)
                        .background(
                            if (selected) Tokens.COLORS.controlOn else Tokens.COLORS.glass,
                            CircleShape,
                        )
                        .border(1.dp, Tokens.COLORS.glassEdge, CircleShape)
                        .clickable(
                            enabled = enabled,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = action,
                        ),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) Tokens.COLORS.controlOnInk else Tokens.COLORS.glyph,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = control.label,
                    color = Tokens.COLORS.mute,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = Modifier.offset(y = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SlideToAnswerCordCallButton(
    label: String,
    description: String,
    icon: ImageVector,
    color: Color,
    left: Double,
    top: Double,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    pressScale: Float,
    sim: SlideToAnswerCordSimulation.Sim,
    frame: () -> Long,
    translateX: () -> Double,
    opacity: () -> Double,
    showPulse: Boolean = false,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .requiredWidth(120.dp)
            .offset {
                IntOffset(
                    ((left - (120.0 - Tokens.LAYOUT.button) / 2) * density.density).roundToInt(),
                    (top * density.density).roundToInt(),
                )
            }
            .graphicsLayer {
                frame()
                translationX = (translateX() * density.density).toFloat()
                alpha = opacity().coerceIn(0.0, 1.0).toFloat()
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(Tokens.LAYOUT.button.dp)
                .scale(pressScale),
        ) {
            if (showPulse) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            frame()
                            val pulse = SlideToAnswerCordSimulation.ringState(sim).pulse
                            alpha = if (pulse < 0.0) 0f else (0.45 * (1.0 - pulse)).toFloat()
                            val haloScale = if (pulse < 0.0) 1.0 else 1.0 + 0.5 * pulse
                            scaleX = haloScale.toFloat()
                            scaleY = haloScale.toFloat()
                        }
                        .background(Tokens.COLORS.accept, CircleShape),
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .matchParentSize()
                    .background(color, CircleShape)
                    .clickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = description,
                        onClick = onClick,
                    )
                    .semantics { contentDescription = description },
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Tokens.COLORS.glyph,
                    modifier = Modifier.size(Tokens.LAYOUT.glyph.dp),
                )
            }
        }
        Text(
            text = label,
            color = Tokens.COLORS.ink,
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontSize = Tokens.LAYOUT.labelSize.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = Modifier.offset(y = Tokens.LAYOUT.labelGap.dp),
        )
    }
}
