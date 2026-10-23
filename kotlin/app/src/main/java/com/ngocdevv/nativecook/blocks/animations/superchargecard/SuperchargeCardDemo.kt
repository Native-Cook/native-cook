package com.ngocdevv.nativecook.blocks.animations.superchargecard

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun SuperchargeCardDemo() {
    SuperchargeCard(autoPlay = true)
}

/** Hold to charge; release at full power, drag to draw filaments, tap to drain. */
@Composable
fun SuperchargeCard(autoPlay: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val simulation = remember { SuperchargeSimulation(autoPlay) }
    val renderer = remember(context) { SuperchargeRenderer(context) }
    val frame = remember { mutableLongStateOf(0L) }
    var reduced by remember { mutableStateOf(false) }
    var valueText by remember { mutableStateOf("Empty") }
    val lastReport = remember { doubleArrayOf(-1.0) }

    fun haptics() {
        val events = simulation.state.events
        val feedback =
            when {
                events and (32 or 64) != 0 -> HapticFeedbackConstants.LONG_PRESS
                events and 8 != 0 -> HapticFeedbackConstants.CONFIRM
                events and 4 != 0 -> HapticFeedbackConstants.CONTEXT_CLICK
                events and 2 != 0 -> HapticFeedbackConstants.VIRTUAL_KEY
                events and 16 != 0 -> HapticFeedbackConstants.CLOCK_TICK
                events and 1 != 0 -> HapticFeedbackConstants.CLOCK_TICK
                else -> null
            }
        if (feedback != null) view.performHapticFeedback(feedback)
        simulation.state.events = 0
    }
    fun input(action: SuperchargeSimulation.Action, x: Double = 0.0, y: Double = 0.0) {
        simulation.act(action, x, y)
        haptics()
        frame.longValue++
    }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        fun update() {
            reduced =
                Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) ==
                    0f
            simulation.reduced = reduced
        }
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = update()
            }
        update()
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        val activity = findActivity(context)
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            resolver.unregisterContentObserver(observer)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(autoPlay) {
        simulation.autoPlay = autoPlay
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            simulation.step((now - previous) / 1e9)
            previous = now
            haptics()
            frame.longValue++
            val state = simulation.state
            if (state.clock < lastReport[0] || state.clock - lastReport[0] >= .1) {
                val percent = (state.energy * 100).roundToInt()
                valueText =
                    when (percent) {
                        0 -> "Empty"
                        100 -> "Fully charged"
                        else -> "$percent percent"
                    }
                lastReport[0] = state.clock
            }
        }
    }
    Canvas(
        modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Supercharge card. Press and hold to charge, then release."
                role = Role.Button
                stateDescription = valueText
                onClick(label = "Charge or drain") {
                    input(SuperchargeSimulation.Action.Activate)
                    true
                }
            }
            .pointerInput(simulation, density) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val k = min(density, min(size.width / 402f, size.height / 874f))
                    val left = (size.width - 402 * k) / 2
                    val top = (size.height - 874 * k) / 2
                    input(
                        SuperchargeSimulation.Action.Down,
                        ((down.position.x - left) / k).toDouble(),
                        ((down.position.y - top) / k).toDouble(),
                    )
                    down.consume()
                    try {
                        var held = true
                        while (held) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id }
                            if (pointer == null) {
                                input(SuperchargeSimulation.Action.Cancel)
                                held = false
                            } else if (!pointer.pressed) {
                                input(SuperchargeSimulation.Action.Up)
                                pointer.consume()
                                held = false
                            } else {
                                input(
                                    SuperchargeSimulation.Action.Move,
                                    ((pointer.position.x - left) / k).toDouble(),
                                    ((pointer.position.y - top) / k).toDouble(),
                                )
                                pointer.consume()
                            }
                        }
                    } finally {
                        if (simulation.state.real) input(SuperchargeSimulation.Action.Cancel)
                    }
                }
            }
    ) {
        frame.longValue
        renderer.draw(
            drawContext.canvas.nativeCanvas,
            size.width,
            size.height,
            density,
            simulation.state,
            reduced,
        )
    }
}

private fun findActivity(context: Context): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
