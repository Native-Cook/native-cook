package com.ngocdevv.nativecook.blocks.onboarding.metacardunifyfan

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.min

@Composable
fun MetacardUnifyFanDemo() {
    MetacardUnifyFan(autoPlay = true)
}

/** Eight cards becoming one. A touch takes over until 3.5 seconds after release. */
@Composable
fun MetacardUnifyFan(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = LocalActivity.current?.window
    var reduced by remember {
        mutableStateOf(
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        )
    }
    DisposableEffect(context, window) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    reduced =
                        Settings.Global.getFloat(
                            context.contentResolver,
                            Settings.Global.ANIMATOR_DURATION_SCALE,
                            1f,
                        ) == 0f
                }
            }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(view)
        val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        val navigationVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            if (statusVisible) controller?.show(WindowInsetsCompat.Type.statusBars())
            if (navigationVisible) controller?.show(WindowInsetsCompat.Type.navigationBars())
        }
    }
    val still = reduced || Build.VERSION.SDK_INT < 33
    val renderer = remember(context) { MetacardRenderer(context) }
    val motion = remember {
        MetacardMotion(autoPlay, still) { heavy ->
            view.performHapticFeedback(
                if (heavy) HapticFeedbackConstants.LONG_PRESS
                else HapticFeedbackConstants.CLOCK_TICK
            )
        }
    }
    val frame = remember { mutableIntStateOf(0) }
    var hero by remember { mutableStateOf(false) }
    LaunchedEffect(autoPlay, still) {
        motion.autoPlay = autoPlay
        motion.reduced = still
        while (true) {
            withFrameNanos { stamp ->
                motion.tick(stamp / 1e9)
                frame.intValue++
                hero = motion.u >= .5
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val k = min(maxWidth.value / 402, maxHeight.value / 874)
        val x = (maxWidth.value - 402 * k) / 2
        val y = (maxHeight.value - 874 * k) / 2
        Canvas(
            Modifier.fillMaxSize().pointerInput(motion, still) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    val scale = min(size.width / 402f, size.height / 874f)
                    val offset =
                        Offset((size.width - 402 * scale) / 2, (size.height - 874 * scale) / 2)
                    fun design(point: Offset) = (point - offset) / scale
                    val origin = design(first.position)
                    val tracker = VelocityTracker()
                    tracker.addPosition(first.uptimeMillis, first.position)
                    motion.touchDown(origin.x.toDouble(), origin.y.toDouble())
                    first.consume()
                    var maxTravel = 0f
                    var cancelled = true
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == first.id } ?: break
                            val point = design(change.position)
                            maxTravel =
                                maxOf(maxTravel, (change.position - first.position).getDistance())
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val velocity = tracker.calculateVelocity()
                            if (maxTravel > 3 * scale)
                                motion.drag(
                                    point.x.toDouble(),
                                    point.y.toDouble(),
                                    velocity.x / scale.toDouble(),
                                    velocity.y / scale.toDouble(),
                                )
                            change.consume()
                            if (!change.pressed) {
                                cancelled = false
                                motion.release(
                                    maxTravel <= 8 * scale &&
                                        change.uptimeMillis - first.uptimeMillis < 500
                                )
                                break
                            }
                        }
                    } finally {
                        if (cancelled) motion.release(false, true)
                    }
                }
            }
        ) {
            frame.intValue
            renderer.draw(drawContext.canvas.nativeCanvas, size.width, size.height, motion)
        }
        Box(
            Modifier.offset((x + 30 * k).dp, (y + (if (hero) 305 else 595) * k).dp)
                .size((342 * k).dp, (75 * k).dp)
                .semantics {
                    heading()
                    contentDescription =
                        if (hero) "Meet the first Metacard" else "Unify the limit of all your cards"
                }
        )
        Box(
            Modifier.offset(
                    (x + (if (hero) 40 else 135) * k).dp,
                    (y + (if (hero) 398 else 290) * k).dp,
                )
                .size(((if (hero) 322 else 132) * k).dp, ((if (hero) 245 else 132) * k).dp)
                .semantics {
                    role = Role.Button
                    contentDescription = if (hero) "Split Metacard" else "Unify cards"
                    stateDescription = if (hero) "One Metacard" else "Eight cards"
                    onClick(
                        if (hero) "Fans the eight cards out again"
                        else "Folds the eight cards into one Metacard"
                    ) {
                        motion.activate()
                        true
                    }
                }
        )
    }
}
