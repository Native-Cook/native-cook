package com.ngocdevv.nativecook.blocks.gestures.dragcardtosend

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.isActive

const val LOOP = 8.5

@Composable
fun DragCardToSend(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val reducedMotion = rememberReducedMotion()
    val haptics = LocalHapticFeedback.current
    val assets = remember(context) { DragCardToSendAssets(context) }
    val renderer = remember(assets) { DragCardToSendRenderer(assets) }
    val engine = remember(autoPlay, reducedMotion, haptics) {
        DragCardToSendEngine(autoPlay, reducedMotion) { kind ->
            val feedback = when (kind) {
                DragCardToSendEngine.Haptic.Selection -> HapticFeedbackType.TextHandleMove
                DragCardToSendEngine.Haptic.Light,
                DragCardToSendEngine.Haptic.Success,
                -> HapticFeedbackType.LongPress
            }
            haptics.performHapticFeedback(feedback)
        }
    }

    LaunchedEffect(engine) {
        while (isActive) withFrameNanos(engine::frame)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(DragCardToSendTokens.Colors.Page)),
    ) {
        val widthDp = maxWidth.value
        val heightDp = maxHeight.value
        engine.setSize(widthDp)
        val background = remember(renderer, widthDp, heightDp, density) {
            renderer.renderStaticBackground(widthDp, heightDp, density).asImageBitmap()
        }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Cove card. Pull down to set an amount, slide sideways to choose a person, and let go to send."
                }
                .pointerInput(engine, density) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val velocity = VelocityTracker()
                        velocity.addPosition(down.uptimeMillis, down.position)
                        engine.pointerDown(Offset(down.position.x / density, down.position.y / density))
                        var latest = down.position
                        var pressed: Boolean
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.first()
                            latest = change.position
                            velocity.addPosition(change.uptimeMillis, latest)
                            pressed = change.pressed
                            if (pressed) {
                                engine.pointerMove(Offset(latest.x / density, latest.y / density))
                                change.consume()
                            }
                        } while (pressed)
                        val finalVelocity = velocity.calculateVelocity()
                        engine.pointerUp(
                            position = Offset(latest.x / density, latest.y / density),
                            velocityY = finalVelocity.y / density,
                        )
                    }
                },
        ) {
            drawImage(background)
            scale(density, density, pivot = Offset.Zero) {
                renderer.draw(drawContext.canvas.nativeCanvas, engine)
            }
        }
    }
}
