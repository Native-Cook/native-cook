package com.ngocdevv.nativecook.blocks.gestures.slidetoanswercord

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.floor
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
    val view = LocalView.current
    val activity = LocalActivity.current
    val assets = rememberSlideToAnswerCordAssets()
    val scene = remember(assets) { SlideToAnswerCordScene(assets) }
    val insets = WindowInsets.safeDrawing
    val safeBottom = insets.getBottom(density) / density.density
    var widthDp by remember { mutableStateOf(402.0) }
    var heightDp by remember { mutableStateOf(874.0) }
    val geometry = remember(widthDp, heightDp) { SlideToAnswerCordSimulation.makeGeometry(widthDp, heightDp) }
    val sim = remember(geometry, reducedMotion, autoPlay) {
        SlideToAnswerCordSimulation.initialSim(geometry, reducedMotion, autoPlay)
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

    fun haptics() {
        sim.events.forEach { event ->
            val feedback = when (event) {
                Tokens.HAPTIC.answer -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
                Tokens.HAPTIC.decline -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
                Tokens.HAPTIC.tick, Tokens.HAPTIC.grab -> HapticFeedbackConstants.CLOCK_TICK
                else -> HapticFeedbackConstants.CONTEXT_CLICK
            }
            view.performHapticFeedback(feedback)
        }
        sim.events.clear()
    }

    fun command(action: SlideToAnswerCordSimulation.Input.Action) {
        SlideToAnswerCordSimulation.input(sim, geometry, SlideToAnswerCordSimulation.Input.Command(action))
        haptics()
        phase = sim.phase
        redraw++
    }

    DisposableEffect(activity, view) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previousSystemUi = view.systemUiVisibility
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            view.systemUiVisibility = previousSystemUi
        }
    }

    LaunchedEffect(sim, geometry, autoPlay, reducedMotion) {
        if (autoPlay) SlideToAnswerCordSimulation.preroll(sim, geometry)
        if (Tokens.REPLICA_HOLD >= 0.0) {
            SlideToAnswerCordSimulation.stepSim(sim, geometry, Tokens.REPLICA_HOLD, autoPlay, uncapped = true)
        }
        redraw++
        var previous = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = min((now - previous) / 1e9, 0.1)
            previous = now
            sim.reduced = reducedMotion
            if (Tokens.REPLICA_HOLD < 0.0) {
                SlideToAnswerCordSimulation.stepSim(sim, geometry, dt, autoPlay)
            }
            haptics()
            if (phase != sim.phase) phase = sim.phase
            val currentSecond = floor(sim.callT).toInt()
            if (seconds != currentSecond) seconds = currentSecond
            redraw = now
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Tokens.COLORS.skyTop)
            .onSizeChanged { size ->
                widthDp = size.width / density.density.toDouble()
                heightDp = size.height / density.density.toDouble()
            },
    ) {
        val buttonsY = heightDp - safeBottom - Tokens.LAYOUT.buttonsBottom - Tokens.LAYOUT.labelSize - Tokens.LAYOUT.labelGap - Tokens.LAYOUT.button / 2
        val half = Tokens.LAYOUT.button / 2
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
                        val p = SlideToAnswerCordSimulation.handsetPoint(sim, geometry, Tokens.HANDSET.comX, Tokens.HANDSET.comY)
                        val dx = down.position.x / scale - p.x
                        val dy = down.position.y / scale - p.y
                        if (kotlin.math.hypot(dx, dy) >= Tokens.HANDSET.hit * geometry.s / 2) return@awaitEachGesture
                        tracker.resetTracking()
                        tracker.addPointerInputChange(down)
                        SlideToAnswerCordSimulation.input(sim, geometry, SlideToAnswerCordSimulation.Input.Down(down.position.x / scale, down.position.y / scale))
                        haptics()
                        down.consume()
                        var released = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } > 1) {
                                    SlideToAnswerCordSimulation.input(sim, geometry, SlideToAnswerCordSimulation.Input.Up(0.0, 0.0))
                                    haptics()
                                    released = true
                                    break
                                }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPointerInputChange(change)
                                val velocity = tracker.calculateVelocity()
                                if (!change.pressed) {
                                    SlideToAnswerCordSimulation.input(sim, geometry, SlideToAnswerCordSimulation.Input.Move(change.position.x / scale, change.position.y / scale, velocity.x / scale, velocity.y / scale))
                                    SlideToAnswerCordSimulation.input(sim, geometry, SlideToAnswerCordSimulation.Input.Up(velocity.x / scale, velocity.y / scale))
                                    haptics()
                                    released = true
                                    change.consume()
                                    break
                                }
                                SlideToAnswerCordSimulation.input(sim, geometry, SlideToAnswerCordSimulation.Input.Move(change.position.x / scale, change.position.y / scale, velocity.x / scale, velocity.y / scale))
                                change.consume()
                            }
                        } finally {
                            if (!released && sim.grab) {
                                SlideToAnswerCordSimulation.input(sim, geometry, SlideToAnswerCordSimulation.Input.Up(0.0, 0.0))
                                haptics()
                            }
                        }
                    }
                },
        ) {
            // Reading redraw here invalidates only this Canvas, never the composition hosting the controls.
            @Suppress("UNUSED_VARIABLE") val frame = redraw
            drawIntoCanvas { composeCanvas ->
                composeCanvas.nativeCanvas.save()
                composeCanvas.nativeCanvas.scale(density.density, density.density)
                scene.draw(
                    composeCanvas.nativeCanvas,
                    sim,
                    geometry,
                    buttonsY,
                    declineScale,
                    acceptScale,
                    pixelScale = density.density,
                )
                composeCanvas.nativeCanvas.restore()
            }
        }

        Box(
            Modifier
                .size(150.dp, 170.dp)
                .offset { IntOffset(((widthDp / 2 - 75) * density.density).roundToInt(), ((geometry.restY - 10) * density.density).roundToInt()) }
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
                            command(if (connected) SlideToAnswerCordSimulation.Input.Action.end else SlideToAnswerCordSimulation.Input.Action.decline)
                            true
                        },
                    )
                },
        )

        if (!Tokens.REPLICA) {
            SlideToAnswerCordCopy(phase, seconds, heightDp)
            SlideToAnswerCordButton(
                label = if (connected) Tokens.COPY.end else Tokens.COPY.decline,
                description = if (connected) "End call" else "Decline call",
                centerX = if (connected) widthDp / 2 else widthDp / 2 - Tokens.LAYOUT.buttonGap * geometry.s,
                centerY = buttonsY,
                enabled = ringing || connected,
                interactionSource = declineInteraction,
            ) { command(if (connected) SlideToAnswerCordSimulation.Input.Action.end else SlideToAnswerCordSimulation.Input.Action.decline) }
            if (!connected) {
                SlideToAnswerCordButton(
                    Tokens.COPY.accept,
                    "Accept call",
                    widthDp / 2 + Tokens.LAYOUT.buttonGap * geometry.s,
                    buttonsY,
                    ringing,
                    acceptInteraction,
                ) {
                    command(SlideToAnswerCordSimulation.Input.Action.answer)
                }
            }
            SlideToAnswerCordPill(
                widthDp,
                buttonsY,
                muted,
                sim,
                connected,
                reducedMotion,
                frame = { redraw },
            ) {
                muted = !muted
            }
        }
    }
}

@Composable
private fun SlideToAnswerCordCopy(phase: Int, seconds: Int, height: Double) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(
                start = Tokens.LAYOUT.side.dp,
                top = (height * Tokens.LAYOUT.copyTopRatio).dp,
                end = Tokens.LAYOUT.side.dp,
            ),
        ) {
            androidx.compose.material3.Text(Tokens.COPY.name, color = Tokens.COLORS.ink, fontSize = Tokens.LAYOUT.nameSize.sp, lineHeight = Tokens.LAYOUT.nameLine.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.4).sp)
            androidx.compose.material3.Text(Tokens.COPY.meta, color = Tokens.COLORS.ink.copy(alpha = .5f), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            AnimatedContent(
                targetState = phase,
                transitionSpec = {
                    (fadeIn(tween(200)) togetherWith fadeOut(tween(120))).using(null)
                },
                contentAlignment = Alignment.Center,
                label = "call-status",
            ) { visiblePhase ->
                val visibleStatus = when (visiblePhase) {
                    SlideToAnswerCordSimulation.PHASE.connected -> "%02d:%02d".format(seconds / 60, seconds % 60)
                    SlideToAnswerCordSimulation.PHASE.declined -> Tokens.COPY.declined
                    SlideToAnswerCordSimulation.PHASE.ended -> Tokens.COPY.ended
                    else -> Tokens.COPY.ringing
                }
                androidx.compose.material3.Text(visibleStatus, color = Tokens.COLORS.mute, fontSize = Tokens.LAYOUT.statusSize.sp, lineHeight = Tokens.LAYOUT.statusLine.sp, style = TextStyle(fontFeatureSettings = "tnum"), modifier = Modifier.padding(top = Tokens.LAYOUT.gap.dp))
            }
        }
    }
}

@Composable
private fun SlideToAnswerCordButton(
    label: String,
    description: String,
    centerX: Double,
    centerY: Double,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current.density
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .size(Tokens.LAYOUT.button.dp)
                .offset { IntOffset(((centerX - Tokens.LAYOUT.button / 2) * density).roundToInt(), ((centerY - Tokens.LAYOUT.button / 2) * density).roundToInt()) }
                .semantics {
                    contentDescription = description
                    role = Role.Button
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
        )
        androidx.compose.material3.Text(
            label,
            color = Tokens.COLORS.label,
            fontSize = Tokens.LAYOUT.labelSize.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(120.dp)
                .offset { IntOffset(((centerX - 60) * density).roundToInt(), ((centerY + Tokens.LAYOUT.button / 2 + Tokens.LAYOUT.labelGap) * density).roundToInt()) },
        )
    }
}

@Composable
private fun SlideToAnswerCordPill(
    width: Double,
    buttonsY: Double,
    muted: Boolean,
    sim: SlideToAnswerCordSimulation.Sim,
    connected: Boolean,
    reducedMotion: Boolean,
    frame: () -> Long,
    onMute: () -> Unit,
) {
    val density = LocalDensity.current.density
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .width((width - 32.0).dp)
                .height(Tokens.GLASS.pillH.dp)
                .offset { IntOffset(16.dp.roundToPx(), ((buttonsY + Tokens.GLASS.pillY - Tokens.GLASS.pillH / 2) * density).roundToInt()) }
                .graphicsLayer {
                    @Suppress("UNUSED_VARIABLE") val frameTick = frame()
                    alpha = ((sim.wConn - .6) / .4).coerceIn(0.0, 1.0).toFloat()
                    scaleX = (.95 + .05 * sim.wConn).toFloat()
                    scaleY = (.95 + .05 * sim.wConn).toFloat()
                },
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tokens.COPY.controls.forEach { control ->
                val interaction = remember(control.name) { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val controlScale by animateFloatAsState(
                    targetValue = if (pressed && !reducedMotion) Tokens.PRESS.scale.toFloat() else 1f,
                    animationSpec = tween(
                        durationMillis = if (pressed) Tokens.PRESS.inMs else Tokens.PRESS.outMs,
                        easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f),
                    ),
                    label = "${control.name}-press",
                )
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .size(64.dp, 52.dp)
                        .background(if (control.name == "mute" && muted) Color.White.copy(alpha = .9f) else Color.Transparent, RoundedCornerShape(26.dp))
                        .graphicsLayer {
                            scaleX = controlScale
                            scaleY = controlScale
                        }
                        .semantics {
                            contentDescription = control.label
                            role = Role.Button
                            stateDescription = if (control.name == "mute") if (muted) "Selected" else "Not selected" else ""
                        }
                        .clickable(interactionSource = interaction, indication = null, enabled = connected) { if (control.name == "mute") onMute() },
                ) {
                    Icon(
                        imageVector = when (control.name) {
                            "mute" -> Icons.Filled.MicOff
                            "keypad" -> Icons.Filled.Dialpad
                            else -> Icons.Filled.VolumeUp
                        },
                        contentDescription = null,
                        tint = if (control.name == "mute" && muted) Color(0xFF16122E) else Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    androidx.compose.material3.Text(
                        control.label,
                        color = if (control.name == "mute" && muted) Color(0xFF16122E) else Color.White.copy(alpha = .75f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = Tokens.SPACE.xs.dp),
                    )
                }
            }
        }
    }
}
