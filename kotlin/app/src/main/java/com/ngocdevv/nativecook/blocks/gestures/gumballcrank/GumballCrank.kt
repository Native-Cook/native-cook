package com.ngocdevv.nativecook.blocks.gestures.gumballcrank

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.isActive

const val LOOP: Long = 10_000L

@Composable
fun GumballCrank(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val reducedMotion = rememberReducedMotion()
    val assets = remember(context) { GumballCrankAssets(context) }
    val renderer = remember(assets) { GumballCrankRenderer(assets) }
    val model = remember { GumballCrankSimulation(reducedMotion) }
    var drawTick by remember { mutableLongStateOf(0L) }

    DisposableEffect(renderer) { onDispose(renderer::close) }
    LaunchedEffect(autoPlay, reducedMotion) { model.start(autoPlay && !reducedMotion, reducedMotion) }
    LaunchedEffect(model, autoPlay, reducedMotion) {
        var previous = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = ((now - previous).coerceAtLeast(0L) / 1_000_000_000.0).coerceAtMost(0.05)
            previous = now
            model.step(dt, autoPlay && !reducedMotion)
            model.drainHaptics().forEach { event ->
                val feedback = when (event) {
                    GumballCrankHaptic.Tick -> HapticFeedbackConstants.CLOCK_TICK
                    GumballCrankHaptic.Strain -> HapticFeedbackConstants.KEYBOARD_TAP
                    GumballCrankHaptic.Drop -> HapticFeedbackConstants.VIRTUAL_KEY
                    GumballCrankHaptic.Open -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
                    GumballCrankHaptic.Collect -> HapticFeedbackConstants.VIRTUAL_KEY
                }
                view.performHapticFeedback(feedback)
            }
            if (model.moving || autoPlay) drawTick++
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color(GumballCrankColor.WallTop))) {
        val px = density.density
        val width = maxWidth.value
        val height = maxHeight.value
        val top = WindowInsets.safeDrawing.getTop(density) / px
        val bottom = WindowInsets.safeDrawing.getBottom(density) / px
        val layout = remember(width, height, top, bottom) { GumballCrankLayout.create(width, height, top, bottom) }

        Canvas(
            modifier = Modifier.fillMaxSize().semantics {
                contentDescription = model.accessibilityDescription
                customActions = listOf(CustomAccessibilityAction(model.accessibilityAction) {
                    model.accessibilityActivate()
                    true
                })
            }.pointerInput(model, layout, px) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    model.pointerDown((down.position.x / px - layout.ox) / layout.scale, (down.position.y / px - layout.oy) / layout.scale)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val x = (change.position.x / px - layout.ox) / layout.scale
                        val y = (change.position.y / px - layout.oy) / layout.scale
                        if (!change.pressed) {
                            model.pointerUp(x, y)
                            break
                        }
                        model.pointerMove(x, y)
                        change.consume()
                    }
                }
            },
        ) {
            drawTick
            val native = drawContext.canvas.nativeCanvas
            val save = native.save()
            native.scale(px, px)
            renderer.draw(native, model, layout, size.width / px, size.height / px)
            native.restoreToCount(save)
        }
    }
}
