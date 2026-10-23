package com.ngocdevv.nativecook.blocks.charts.growthodometertimeline

import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.floor
import java.util.Locale

@Composable
fun GrowthOdometerTimelineDemo() {
    GrowthOdometerTimeline(autoPlay = true)
}

/** An interactive instrument by default; the gallery opts into its twelve-second film. */
@Composable
fun GrowthOdometerTimeline(autoPlay: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = LocalActivity.current?.window
    val density = LocalDensity.current.density
    var reduced by remember {
        mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f)
    }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    DisposableEffect(window, view) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val behavior = controller?.systemBarsBehavior
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (behavior != null) controller.systemBarsBehavior = behavior
        }
    }
    val wheel = remember(autoPlay, reduced) { GrowthSimulation(autoPlay, reduced || Build.VERSION.SDK_INT < 33) }
    val renderer = remember { GrowthRenderer() }
    val revision = remember { mutableIntStateOf(0) }
    var announced by remember(wheel) { mutableIntStateOf(0) }
    LaunchedEffect(wheel) {
        var last = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            if (view.isShown && view.hasWindowFocus()) {
                wheel.advance((now - last) / 1e9)
                val events = wheel.events
                if (events and 4 != 0) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                else if (events and 2 != 0) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                else if (events and 1 != 0) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                if (events and 8 != 0) announced = wheel.announced
                revision.intValue++
            }
            last = now
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Preserve the reference composition even on emulators with an overridden display density.
        val pointToDp = maxWidth.value / 402f
        val pointToPx = pointToDp * density
        val width = 402f
        val height = maxHeight.value / pointToDp
        val cy = floor(height / 8 + .5f) * 4
        val railX = floor(width * .318f + .5f)
        val knobX = railX + 34
        val description = "${GrowthSimulation.fullMonths[announced]}, ${String.format(Locale.US, "%,d", GrowthSimulation.values[announced])} followers"
        Canvas(Modifier.fillMaxSize().pointerInput(wheel, pointToPx, width, height) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                val initial = down.position
                val knob = abs(initial.x / pointToPx - knobX) <= 28 && abs(initial.y / pointToPx - cy) <= 28
                wheel.touch()
                wheel.pressed = knob
                var maximumDistance = 0f
                var dragStarted = false
                var released = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val delta = change.position - initial
                        maximumDistance = maxOf(maximumDistance, delta.getDistance() / pointToPx)
                        if (abs(delta.y) / pointToPx > 6) dragStarted = true
                        if (dragStarted && change.pressed) {
                            wheel.drag((delta.y / pointToPx).toDouble(), (tracker.calculateVelocity().y / pointToPx).toDouble())
                            change.consume()
                        }
                        if (!change.pressed) {
                            if (knob && maximumDistance <= 8 && change.uptimeMillis - down.uptimeMillis <= 280) {
                                wheel.nextMonth(fromGrab = true)
                                change.consume()
                            }
                            wheel.release((tracker.calculateVelocity().y / pointToPx).toDouble())
                            released = true
                            break
                        }
                        if (event.changes.count { it.pressed } > 1 || change.isConsumed && !dragStarted) break
                    }
                } finally {
                    wheel.pressed = false
                    if (!released) wheel.release(0.0)
                }
            }
        }) {
            revision.intValue
            drawIntoCanvas { target ->
                val native = target.nativeCanvas
                native.save()
                native.scale(pointToPx, pointToPx)
                renderer.draw(native, size.width / pointToPx, size.height / pointToPx, pointToPx, wheel)
                native.restore()
            }
        }
        Box(Modifier.width((railX + 4).times(pointToDp).dp).fillMaxHeight().semantics {
            contentDescription = "Month"
            stateDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo(announced.toFloat(), 0f..12f, 11)
            setProgress { value ->
                wheel.nextMonth(0, false)
                wheel.goTo(floor(value.toDouble() + .5))
                true
            }
            customActions = listOf(
                CustomAccessibilityAction("Next month") { wheel.nextMonth(1, false); true },
                CustomAccessibilityAction("Previous month") { wheel.nextMonth(-1, false); true },
            )
        })
        Box(Modifier.offset((knobX - 28).times(pointToDp).dp, (cy - 28).times(pointToDp).dp).size((56 * pointToDp).dp).semantics {
            role = Role.Button
            contentDescription = "Next month"
            stateDescription = description
            onClick(label = "Next month, or rewind from the last month") {
                wheel.nextMonth()
                true
            }
        })
        Box(Modifier.offset((railX + 80).times(pointToDp).dp, (cy - 66).times(pointToDp).dp).size(((width - railX - 80) * pointToDp).dp, (120 * pointToDp).dp).semantics {
            contentDescription = "Nova Lin. $description"
        })
    }
}
