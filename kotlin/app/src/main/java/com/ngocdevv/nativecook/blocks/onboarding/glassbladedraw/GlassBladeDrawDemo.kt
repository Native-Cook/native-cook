package com.ngocdevv.nativecook.blocks.onboarding.glassbladedraw

import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.*

@Composable
fun GlassBladeDrawDemo() {
    GlassBladeDraw(autoPlay = true)
}

/** A reusable screen; autoplay is opt-in and every touch pauses it until the fan rests. */
@Composable
fun GlassBladeDraw(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = LocalActivity.current?.window
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current.density
    val bottomInset = WindowInsets.navigationBars.getBottom(LocalDensity.current) / density
    val topInset = WindowInsets.displayCutout.getTop(LocalDensity.current) / density
    var reduced by remember {
        mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f)
    }
    var running by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(context, lifecycle, window) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        val lifecycleObserver = LifecycleEventObserver { _, _ -> running = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(lifecycleObserver)
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            lifecycle.removeObserver(lifecycleObserver)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    val effectiveReduced = reduced || Build.VERSION.SDK_INT < 33
    val renderer = remember(context) { BladeRenderer(context) }
    val motion = remember { BladeMotion(autoPlay, effectiveReduced) { landing ->
        view.performHapticFeedback(if (landing) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CLOCK_TICK)
    } }
    SideEffect {
        motion.autoPlay = autoPlay
        motion.reduced = effectiveReduced
    }
    var revision by remember { mutableIntStateOf(0) }
    var hero by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(motion, running) {
        if (!running) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            motion.advance((now - last) / 1_000_000_000.0)
            last = now
            hero = BladeModel.cardIndex(motion.frame.pos)
            busy = motion.drawing
            revision++
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val stageScale = maxWidth.value / 402.0
        val width = 402.0
        val height = maxHeight.value / stageScale
        val renderDensity = (density * stageScale).toFloat()
        // Preserve the reference's optical safe margins on phones with shorter Android cutouts.
        val layout = remember(width, height, bottomInset) { BladeLayout(width, height, max(bottomInset / stageScale, 34 * min(width / 402, height / 874))) }
        val headerInset = max(topInset / stageScale, 62 * layout.scale)
        Canvas(Modifier.fillMaxSize()) {
            revision // Animation invalidates drawing, not composition.
            renderer.draw(drawContext.canvas.nativeCanvas, layout, motion, renderDensity, headerInset)
        }
        Box(Modifier.fillMaxWidth().offset(y = ((headerInset + 12) * stageScale).dp).height((194 * stageScale).dp).semantics(mergeDescendants = true) {
            heading()
            contentDescription = "Vane. Nine blades. One lights. Nobody loses. Every stake comes back. Only the prize moves."
        })
        val slot = 78 * layout.scale
        Box(Modifier.offset(y = ((layout.ctaY - 8 - 300 * layout.scale) * stageScale).dp)
            .fillMaxWidth().height((300 * layout.scale * stageScale).dp)
            .semantics {
                contentDescription = "Blades"
                stateDescription = "${BladeModel.values[hero]}, ${BladeModel.hues[hero % 3]}"
                customActions = listOf(
                    CustomAccessibilityAction("Next blade") { motion.accessibilityStep(1) },
                    CustomAccessibilityAction("Previous blade") { motion.accessibilityStep(-1) },
                )
            }.pointerInput(motion, slot, width, renderDensity) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!motion.startDrag()) return@awaitEachGesture
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var dragged = false
                    var lastX = down.position.x
                    var released = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            lastX = change.position.x
                            val translation = change.position - down.position
                            if (abs(translation.x / renderDensity) > 3 || abs(translation.y / renderDensity) > 3) dragged = true
                            if (dragged) motion.drag(translation.x / renderDensity.toDouble(), slot)
                            change.consume()
                            if (!change.pressed) {
                                released = true
                                break
                            }
                        }
                    } finally {
                        motion.endFan(lastX / renderDensity.toDouble(), width, slot, dragged,
                            tracker.calculateVelocity().x / renderDensity.toDouble(), !released)
                    }
                }
            })
        Box(Modifier.offset(x = (layout.ctaX * stageScale).dp, y = (layout.ctaY * stageScale).dp)
            .width((layout.ctaW * stageScale).dp).height((layout.ctaH * stageScale).dp)
            .semantics {
                contentDescription = "Draw a blade"
                role = Role.Button
                stateDescription = if (busy) "Drawing" else "Ready"
                onClick { motion.accessibilityDraw() }
            }.pointerInput(motion) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!motion.press()) return@awaitEachGesture
                    var success = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (change.position.x < 0 || change.position.x > size.width || change.position.y < 0 || change.position.y > size.height) break
                            if (!change.pressed) {
                                success = true
                                break
                            }
                        }
                    } finally {
                        if (success) motion.draw() else motion.cancelPress()
                    }
                }
            })
    }
}
