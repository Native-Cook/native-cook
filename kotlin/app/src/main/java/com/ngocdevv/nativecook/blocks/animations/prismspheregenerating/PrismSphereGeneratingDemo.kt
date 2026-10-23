package com.ngocdevv.nativecook.blocks.animations.prismspheregenerating

import android.app.Activity
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.min

@Composable
fun PrismSphereGeneratingDemo() {
    PrismSphereGenerating(autoPlay = true)
}

/** Native procedural waiting screen. Touch interrupts autoplay for 3.5 seconds. */
@Composable
fun PrismSphereGenerating(autoPlay: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val motion = remember { PrismMotion() }
    val renderer = remember { PrismRenderer() }
    val frame = remember { mutableDoubleStateOf(0.0) }
    var captionIndex by remember { mutableIntStateOf(0) }
    var reduced by remember {
        mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f)
    }
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.systemBarsBehavior
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (previous != null) controller.systemBarsBehavior = previous
        }
    }
    SideEffect {
        motion.autoPlay = autoPlay
        motion.reduced = reduced || android.os.Build.VERSION.SDK_INT < 33
    }
    LaunchedEffect(motion) {
        val epoch = withFrameNanos { it }
        var settingsReadAt = -1.0
        while (isActive) {
            withFrameNanos { now ->
                val elapsed = (now - epoch) / 1e9
                // Some Android providers do not deliver animation-scale URI notifications.
                // Sample the cached setting twice a second from the same animation clock.
                if (elapsed - settingsReadAt >= 0.5) {
                    reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
                    settingsReadAt = elapsed
                }
                motion.reduced = reduced || android.os.Build.VERSION.SDK_INT < 33
                motion.frame(elapsed)
                frame.doubleValue = elapsed
                captionIndex = (motion.clock / 5).toInt() % 2
            }
        }
    }
    fun pulse() { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val k = min(maxWidth.value / 402f, maxHeight.value / 874f)
        val left = (maxWidth.value - 402 * k) / 2
        val top = (maxHeight.value - 874 * k) / 2
        Canvas(Modifier.fillMaxSize()) {
            renderer.draw(drawContext.canvas.nativeCanvas, size.width, size.height, motion, frame.doubleValue)
        }
        Box(Modifier.offset(left.dp, (top + 196 * k).dp).size((402 * k).dp, (88 * k).dp)
            .semantics {
                heading()
                contentDescription = "We are creating your digital art."
            })
        Box(Modifier.offset(left.dp, (top + 660 * k).dp).size((402 * k).dp, (24 * k).dp)
            .semantics {
                contentDescription = if (captionIndex == 0) "Please stand by…" else "Shaping the light…"
                liveRegion = LiveRegionMode.Polite
            })
        Box(Modifier.offset((left + 65 * k).dp, (top + 336 * k).dp).size((272 * k).dp)
            .semantics {
                role = Role.Button
                contentDescription = "Light sphere"
                stateDescription = if (reduced || android.os.Build.VERSION.SDK_INT < 33) {
                    "Still sphere. Tap to glow."
                } else {
                    "Animated sphere. Hold to charge; drag to turn."
                }
                onClick("Send a pulse of light through it") {
                    motion.activate()
                    pulse()
                    true
                }
            }
            .pointerInput(k, density, reduced) {
                val unit = k * density
                try {
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        val origin = first.position
                        val centre = Offset(136 * unit, 136 * unit)
                        if ((origin - centre).getDistance() > 136 * unit) return@awaitEachGesture
                        val tracker = VelocityTracker()
                        tracker.addPosition(first.uptimeMillis, origin)
                        motion.begin(((origin.x - centre.x) / unit).toDouble(), ((origin.y - centre.y) / unit).toDouble())
                        first.consume()
                        var finished = false
                        while (!finished) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == first.id }
                            if (change == null || event.changes.count { it.pressed } > 1) {
                                motion.end(false, 0.0, 0.0)
                                finished = true
                            } else {
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (!change.pressed) {
                                    val velocity = tracker.calculateVelocity()
                                    if (motion.end(true, (velocity.x / unit).toDouble(), (velocity.y / unit).toDouble())) pulse()
                                    finished = true
                                } else {
                                    motion.move(((change.position.x - centre.x) / unit).toDouble(), ((change.position.y - centre.y) / unit).toDouble(),
                                        ((change.position.x - origin.x) / unit).toDouble(), ((change.position.y - origin.y) / unit).toDouble())
                                }
                                change.consume()
                            }
                        }
                    }
                } finally {
                    motion.end(false, 0.0, 0.0)
                }
            })
    }
}
