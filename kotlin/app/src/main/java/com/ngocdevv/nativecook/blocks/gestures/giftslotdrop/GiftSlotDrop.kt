package com.ngocdevv.nativecook.blocks.gestures.giftslotdrop

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.isActive

const val LOOP: Long = GiftSlotDropTokens.LOOP_MS

@Composable
fun GiftSlotDrop(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    onShop: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val reducedMotion = rememberReducedMotion()
    val assets = remember(context) { GiftSlotDropAssets(context) }
    val renderer = remember(assets) { GiftSlotDropRenderer(assets) }
    val simulation = remember {
        GiftSlotDropSimulation { event ->
            val feedback = when (event) {
                GiftSlotDropHaptic.Select -> HapticFeedbackConstants.CLOCK_TICK
                GiftSlotDropHaptic.Armed -> HapticFeedbackConstants.CONTEXT_CLICK
                GiftSlotDropHaptic.Insert -> HapticFeedbackConstants.LONG_PRESS
                GiftSlotDropHaptic.Success -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.CONTEXT_CLICK
                }
                GiftSlotDropHaptic.Light -> HapticFeedbackConstants.VIRTUAL_KEY
            }
            view.performHapticFeedback(feedback)
        }
    }
    var drawTick by remember { mutableLongStateOf(0L) }
    var phase by remember { mutableStateOf(GiftSlotDropPhase.Choosing) }
    var selected by remember { mutableIntStateOf(GiftSlotDropTokens.START_INDEX) }

    LaunchedEffect(autoPlay, reducedMotion) {
        simulation.start(autoPlay && !reducedMotion)
    }

    LaunchedEffect(simulation, autoPlay, reducedMotion) {
        var previous = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            val seconds = (now - previous).coerceAtLeast(0L) / 1_000_000_000f
            previous = now
            simulation.step(seconds, autoPlay, reducedMotion)
            if (phase != simulation.phase) phase = simulation.phase
            if (selected != simulation.selected) selected = simulation.selected
            drawTick += 1L
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(android.graphics.Color.parseColor(GiftSlotDropTokens.Color.page))),
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val top = WindowInsets.statusBars.getTop(density) / density.density
        val bottom = WindowInsets.navigationBars.getBottom(density) / density.density
        val layout = remember(width, height, top, bottom) { GiftSlotDropLayout(width, height, top, bottom) }
        simulation.layout = layout
        val gift = GiftSlotDropTokens.gifts[selected]
        val choosing = phase == GiftSlotDropPhase.Choosing || phase == GiftSlotDropPhase.Pulling || phase == GiftSlotDropPhase.Swiping
        val offer = phase == GiftSlotDropPhase.Offer

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = if (offer) {
                        "Your gift is ready. ${gift.shop}, code ${gift.code.toList().joinToString(" ")}."
                    } else {
                        "Choose your welcome gift. ${gift.shop}, ${gift.kind}: ${gift.offer} ${gift.line}, ${gift.cap}. Gift ${selected + 1} of ${GiftSlotDropTokens.gifts.size}."
                    }
                    if (choosing) {
                        role = Role.Button
                        customActions = listOf(
                            CustomAccessibilityAction("Next gift") {
                                simulation.takeOver()
                                simulation.stepGift(1)
                                simulation.letGo()
                                true
                            },
                            CustomAccessibilityAction("Previous gift") {
                                simulation.takeOver()
                                simulation.stepGift(-1)
                                simulation.letGo()
                                true
                            },
                            CustomAccessibilityAction("Activate gift") {
                                simulation.takeOver()
                                simulation.activate()
                                simulation.letGo()
                                true
                            },
                        )
                    }
                }
                .pointerInput(simulation, density.density) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        simulation.takeOver()
                        simulation.down(down.position.x / density.density, down.position.y / density.density, false)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!change.pressed) {
                                val velocity = tracker.calculateVelocity()
                                simulation.up(velocity.x / density.density, velocity.y / density.density)
                                simulation.letGo()
                                break
                            }
                            simulation.move(change.position.x / density.density, change.position.y / density.density)
                            change.consume()
                        }
                    }
                },
        ) {
            drawTick
            val save = drawContext.canvas.nativeCanvas.save()
            drawContext.canvas.nativeCanvas.scale(density.density, density.density)
            renderer.draw(drawContext.canvas.nativeCanvas, simulation, reducedMotion, density.density)
            drawContext.canvas.nativeCanvas.restoreToCount(save)
        }

        Box(
            modifier = Modifier
                .offset(x = GiftSlotDropTokens.Layout.edge.dp, y = layout.top.dp)
                .size(44.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = if (offer) "Done" else "Back"
                }
                .clickable(enabled = offer, indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    simulation.takeOver()
                    simulation.close()
                    simulation.letGo()
                },
        )

        if (offer) {
            GiftSlotDropActionTarget(
                layout = layout,
                label = gift.action,
                code = gift.code,
                onPressed = simulation::setButtonPressed,
                onClick = {
                    if (onShop != null) {
                        onShop(gift.id)
                    } else {
                        simulation.takeOver()
                        simulation.close()
                        simulation.letGo()
                    }
                },
            )
        }
    }
}

@Composable
private fun GiftSlotDropActionTarget(
    layout: GiftSlotDropLayout,
    label: String,
    code: String,
    onPressed: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed) { onPressed(pressed) }
    Box(
        modifier = Modifier
            .offset(x = GiftSlotDropTokens.Layout.edge.dp, y = layout.buttonY.dp)
            .size(
                width = (layout.width - 2f * GiftSlotDropTokens.Layout.edge).dp,
                height = GiftSlotDropTokens.Layout.buttonH.dp,
            )
            .semantics {
                role = Role.Button
                contentDescription = "$label. Your code is ${code.toList().joinToString(" ")}."
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    )
}
