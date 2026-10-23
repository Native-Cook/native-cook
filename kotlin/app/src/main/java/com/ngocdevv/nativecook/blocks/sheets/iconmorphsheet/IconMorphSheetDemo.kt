package com.ngocdevv.nativecook.blocks.sheets.iconmorphsheet

import android.app.Activity
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun IconMorphSheetDemo() {
    IconMorphSheet(autoPlay = true)
}

/** A reusable interactive sheet; the gallery alone opts into the scripted loop. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconMorphSheet(autoPlay: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val motion = remember(autoPlay) { IconMorphMotion(autoPlay) }
    val painter = remember { IconMorphPainter() }
    val redraw = remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var step by remember { mutableIntStateOf(0) }
    var opened by remember { mutableStateOf(true) }
    var label by remember { mutableIntStateOf(1) }
    val topInset = WindowInsets.statusBarsIgnoringVisibility.getTop(density) / density.density
    val bottomInset = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density) / density.density
    DisposableEffect(view, lifecycle) {
        val controller = (context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    SideEffect {
        motion.reduced = reduced
        motion.onHaptic = { medium -> view.performHapticFeedback(if (medium) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CONTEXT_CLICK) }
    }
    LaunchedEffect(motion, active) {
        if (!active) return@LaunchedEffect
        var last = withFrameNanos { it }
        var readyDelay = 0.0
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1e9
            last = now
            readyDelay += dt
            if (readyDelay < 0.3) continue
            motion.tick(dt)
            redraw.intValue++
            step = motion.frame.position.roundToInt().coerceIn(0, 3)
            opened = motion.frame.open > 0.001
            label = if (motion.frame.labelOpen < 0.5) 0 else if (motion.frame.position < 2.5) 1 else 2
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // A resized emulator may retain its original density. Keep a readable phone canvas
        // below 360 dp instead of letting that density mismatch select the compact design.
        val designScale = if (maxWidth.value < 360) maxWidth.value / 402f else 1f
        val drawingDensity = density.density * designScale
        val l = remember(maxWidth, maxHeight, topInset, bottomInset) {
            val height = maxHeight.value / designScale
            IconMorphLayout((maxWidth.value / designScale).toDouble(), height.toDouble(),
                max((topInset / designScale).toDouble(), if (height < 780) 24.0 else 62.0),
                max((bottomInset / designScale).toDouble(), 34.0))
        }
        SideEffect { motion.layout = l }
        Canvas(Modifier.fillMaxSize().pointerInput(motion, l, reduced) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                motion.ambientDown()
                val start = down.position / drawingDensity
                val f = motion.frame
                val sheetTop = l.height - l.sheetHeight(f.position) + l.travel(f, reduced)
                val target = when {
                    start.y >= l.buttonTop && start.y <= l.buttonTop + 56 && start.x >= 16 && start.x <= l.width - 16 -> 3
                    f.open > 0.001 && start.x >= l.width - 54 && start.y >= sheetTop + 6 && start.y <= sheetTop + 50 -> 0
                    start.x <= 56 && abs(start.y - l.headerY) <= 22 -> 1
                    start.x >= l.width - 56 && abs(start.y - l.headerY) <= 22 -> 2
                    else -> 4
                }
                when (target) {
                    3 -> motion.buttonDown()
                    0, 1, 2 -> motion.puckDown(target)
                    else -> motion.panBegin(start.x.toDouble(), start.y.toDouble())
                }
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                var inside = true
                var ended = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (event.changes.count { it.pressed } > 1) break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val delta = (change.position - down.position) / drawingDensity
                        if (target == 4) motion.panChange(delta.x.toDouble(), delta.y.toDouble())
                        else if (inside && delta.getDistance() > (if (target == 3) 28 else 16)) {
                            inside = false
                            if (target == 3) motion.buttonUp(false) else motion.puckUp(false, target)
                        }
                        if (change.isConsumed) break
                        change.consume()
                        if (!change.pressed) {
                            ended = true
                            break
                        }
                    }
                } finally {
                    val velocity = tracker.calculateVelocity()
                    when (target) {
                        3 -> motion.buttonUp(inside && ended)
                        0, 1, 2 -> motion.puckUp(inside && ended, target)
                        else -> motion.panFinalize((velocity.x / drawingDensity).toDouble(), (velocity.y / drawingDensity).toDouble(), !ended)
                    }
                    motion.ambientUp()
                }
            }
        }) {
            redraw.intValue
            painter.draw(drawContext.canvas.nativeCanvas, l, motion.frame, motion.times, drawingDensity, reduced, autoPlay)
        }
        // Separate semantics stay stable while the canvas reads its animation in the draw phase.
        if (opened) {
            Box(Modifier.offset(0.dp, (l.copyBottom - 72).times(designScale).dp).size(maxWidth, (72 * designScale).dp).semantics {
                heading()
                contentDescription = "${IconMorphPainter.titles[step]}. ${IconMorphPainter.subtitles[step]}"
                stateDescription = "Step ${step + 1} of 4"
            })
            Box(Modifier.offset((l.width / 2 - 90).times(designScale).dp, (l.stageTop + l.stageHeight / 2 - 90).times(designScale).dp).size((180 * designScale).dp).semantics {
                contentDescription = IconMorphPainter.images[step]
                role = Role.Image
                onClick("Squash the clay icon") { motion.activateIcon(); true }
                customActions = listOf(
                    CustomAccessibilityAction("Next tip") { motion.buttonDown(); motion.buttonUp(true); true },
                    CustomAccessibilityAction("Previous tip") {
                        motion.ambientDown()
                        motion.panBegin(l.width / 2, l.stageTop + l.stageHeight / 2)
                        motion.panChange(140.0, 0.0)
                        motion.panFinalize(600.0, 0.0)
                        motion.ambientUp()
                        true
                    },
                )
            })
            Box(Modifier.offset((l.width - 54).times(designScale).dp, (l.height - l.sheetHeight(step.toDouble()) + 6).times(designScale).dp).size((44 * designScale).dp).semantics {
                role = Role.Button
                contentDescription = "Close tips"
                onClick { motion.commitSheet(false); true }
            })
        } else {
            Box(Modifier.offset((l.width - 56).times(designScale).dp, (l.headerY - 22).times(designScale).dp).size((44 * designScale).dp).semantics {
                role = Role.Button
                contentDescription = "Show tips"
                onClick { motion.commitSheet(true); true }
            })
            Box(Modifier.offset((12 * designScale).dp, (l.headerY - 22).times(designScale).dp).size((44 * designScale).dp).semantics {
                role = Role.Button
                contentDescription = "Back"
                onClick { motion.puckDown(1); motion.puckUp(true, 1); true }
            })
            Box(Modifier.offset((60 * designScale).dp, (l.titleY * designScale).dp).size((l.width - 120).times(designScale).dp, (108 * designScale).dp).semantics {
                contentDescription = "To Mina Park. $24.00. Send it now? From noku balance, $182.40 available."
            })
        }
        Box(Modifier.offset((16 * designScale).dp, (l.buttonTop * designScale).dp).size((l.width - 32).times(designScale).dp, (56 * designScale).dp).semantics {
            role = Role.Button
            contentDescription = IconMorphPainter.labels[label]
            onClick { motion.buttonDown(); motion.buttonUp(true); true }
        })
    }
}
