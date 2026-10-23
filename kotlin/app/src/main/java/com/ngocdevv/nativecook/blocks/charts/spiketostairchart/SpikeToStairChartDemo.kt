package com.ngocdevv.nativecook.blocks.charts.spiketostairchart

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.max
import kotlin.math.min

@Composable
fun SpikeToStairChartDemo() {
    SpikeToStairChart(autoPlay = true)
}

/** A self-contained chart. With autoplay off, Motivation draws once and waits for touch. */
@Composable
fun SpikeToStairChart(autoPlay: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = LocalActivity.current
    val reduced = rememberChartReducedMotion()
    val renderer = remember(context) { ChartRenderer(context) }
    val motion =
        remember(autoPlay, reduced, view) {
            ChartMotion(autoPlay, reduced) { kind ->
                view.performHapticFeedback(
                    when (kind) {
                        0 -> HapticFeedbackConstants.CLOCK_TICK
                        1 -> HapticFeedbackConstants.CONTEXT_CLICK
                        else -> HapticFeedbackConstants.LONG_PRESS
                    }
                )
            }
        }
    val frame = remember { mutableIntStateOf(0) }
    DisposableEffect(activity, view) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    LaunchedEffect(motion) {
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            motion.step((now - previous) / 1e9)
            motion.clearPress()
            frame.intValue++
            previous = now
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val k = min(width / 402, height / 874)
        val ox = (width - 402 * k) / 2
        val oy = (height - 874 * k) / 2
        Canvas(Modifier.fillMaxSize()) {
            frame.intValue
            drawIntoCanvas { renderer.draw(it.nativeCanvas, size.width, size.height, motion) }
        }
        fun target(x: Float, y: Float, w: Float, h: Float) =
            Modifier.offset(
                    with(density) { (ox + x * k).toDp() },
                    with(density) { (oy + y * k).toDp() },
                )
                .size(with(density) { (w * k).toDp() }, with(density) { (h * k).toDp() })
        Box(
            target(40f, 180f, 322f, 380f)
                .semantics {
                    contentDescription = "Chart scrubber"
                    stateDescription = motion.accessibilityValue
                    progressBarRangeInfo = ProgressBarRangeInfo(motion.scrubU.toFloat(), 0f..1f)
                    setProgress {
                        motion.setProgress(it)
                        true
                    }
                    customActions =
                        listOf(
                            CustomAccessibilityAction("Increment") {
                                motion.adjust(1)
                                true
                            },
                            CustomAccessibilityAction("Decrement") {
                                motion.adjust(-1)
                                true
                            },
                        )
                }
                .pointerInput(motion, k) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        motion.startScrub(40 + down.position.x / k.toDouble())
                        down.consume()
                        try {
                            var held = true
                            while (held) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                motion.moveScrub(40 + change.position.x / k.toDouble())
                                held = change.pressed
                                change.consume()
                            }
                        } finally {
                            motion.endScrub(tracker.calculateVelocity().x / k.toDouble())
                        }
                    }
                }
        )
        val switchHeight = max(44f, with(density) { 44.dp.toPx() } / k)
        for (d in 0..1) {
            Box(
                target(133f + 68 * d, 792f - switchHeight / 2, 68f, switchHeight)
                    .semantics {
                        role = Role.Button
                        contentDescription = if (d == 1) "Show discipline" else "Show motivation"
                        selected = motion.selected == d
                        onClick {
                            motion.press(d, true)
                            motion.press(d, false)
                            motion.go(d)
                            true
                        }
                    }
                    .pointerInput(motion, d) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            motion.press(d, true)
                            var accepted = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change =
                                        event.changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    if (!change.pressed) {
                                        accepted =
                                            change.position.x in 0f..size.width.toFloat() &&
                                                change.position.y in 0f..size.height.toFloat()
                                        break
                                    }
                                }
                            } finally {
                                motion.press(d, false)
                            }
                            if (accepted) motion.go(d)
                        }
                    }
            )
        }
    }
}

@Composable
private fun rememberChartReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() =
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    reduced = read()
                }
            }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}
