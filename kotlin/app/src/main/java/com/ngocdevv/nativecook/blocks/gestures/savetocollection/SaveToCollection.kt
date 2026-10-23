package com.ngocdevv.nativecook.blocks.gestures.savetocollection

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive

const val LOOP = 10.0

@Composable
fun SaveToCollection(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val reducedMotion = rememberReducedMotion()
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density
    val assets = remember(context) { SaveToCollectionAssets(context) }
    val renderer = remember(assets) { SaveToCollectionRenderer(assets) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(SaveToCollectionTokens.Page)),
    ) {
        val layout = remember(maxWidth.value, maxHeight.value, safeTop, safeBottom) {
            SaveToCollectionLayout.create(maxWidth.value, maxHeight.value, safeTop, safeBottom)
        }
        val engine = remember(layout, autoPlay, reducedMotion, view) {
            SaveToCollectionEngine(
                layout = layout,
                autoPlay = autoPlay && !reducedMotion,
                reducedMotion = reducedMotion,
                onHaptic = { event -> view.performHapticFeedback(event.feedbackConstant()) },
            )
        }
        val page = remember(renderer, layout, density.density) {
            renderer.renderStaticPage(layout, density.density).asImageBitmap()
        }
        val sheetBefore = remember(renderer, layout, density.density) {
            renderer.renderSheet(layout, density.density, fresh = false)
        }
        val sheetFresh = remember(renderer, layout, density.density) {
            renderer.renderSheet(layout, density.density, fresh = true)
        }
        val wake = remember(engine) { Channel<Unit>(Channel.CONFLATED) }
        var drawTick by remember(engine) { mutableLongStateOf(0L) }

        DisposableEffect(wake) {
            onDispose { wake.close() }
        }
        LaunchedEffect(engine, wake) {
            while (isActive) {
                if (!engine.needsFrames) wake.receive()
                withFrameNanos { frameNanos -> engine.frame(frameNanos) }
                drawTick += 1L
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Atlas photo browser. Hold a place and carry it to Saved."
                    customActions = listOf(
                        CustomAccessibilityAction("Save Still water, early") {
                            val handled = engine.accessibilitySaveLake()
                            wake.trySend(Unit)
                            handled
                        },
                        CustomAccessibilityAction("Open Slow coast collection") {
                            val handled = engine.accessibilityOpenCollection()
                            wake.trySend(Unit)
                            handled
                        },
                    )
                }
                .pointerInput(engine, density.density, wake) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        engine.pointerDown(down.position.x / density.density, down.position.y / density.density)
                        wake.trySend(Unit)
                        var finished = false
                        try {
                            while (!finished) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { pointer -> pointer.id == down.id }
                                if (change == null) {
                                    engine.cancelPointer()
                                    finished = true
                                    continue
                                }
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val velocity = tracker.calculateVelocity()
                                if (change.pressed) {
                                    engine.pointerMove(
                                        change.position.x / density.density,
                                        change.position.y / density.density,
                                        velocity.x / density.density,
                                    )
                                    change.consume()
                                } else {
                                    engine.pointerUp(
                                        change.position.x / density.density,
                                        change.position.y / density.density,
                                    )
                                    finished = true
                                }
                                wake.trySend(Unit)
                            }
                        } finally {
                            if (!finished) {
                                engine.cancelPointer()
                                wake.trySend(Unit)
                            }
                        }
                    }
                },
        ) {
            drawImage(page)
            drawTick
            scale(density.density, density.density, pivot = Offset.Zero) {
                renderer.draw(drawContext.canvas.nativeCanvas, engine, sheetBefore, sheetFresh)
            }
        }
    }
}

private fun SaveToCollectionHaptic.feedbackConstant(): Int {
    return when (this) {
        SaveToCollectionHaptic.Lift -> HapticFeedbackConstants.LONG_PRESS
        SaveToCollectionHaptic.Magnet -> HapticFeedbackConstants.VIRTUAL_KEY
        SaveToCollectionHaptic.Select -> HapticFeedbackConstants.CLOCK_TICK
        SaveToCollectionHaptic.Success -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
    }
}
