package com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral

import android.database.ContentObserver
import android.os.Handler
import android.os.Build
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

@Composable
fun ChromeZeroNumeralDemo() {
    ChromeZeroNumeral(autoPlay = true)
}

/** Copyable, edge-to-edge onboarding. Touch temporarily takes ownership of the shared frame clock. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChromeZeroNumeral(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = LocalActivity.current?.window
    val density = LocalDensity.current.density
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var reduced by remember {
        mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f)
    }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    DisposableEffect(window,view) {
        val controller = window?.let { WindowCompat.getInsetsController(it,view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    val motion = remember(autoPlay) {
        ZeroMotion(autoPlay,reduced || Build.VERSION.SDK_INT < 33) { kind ->
            view.performHapticFeedback(when (kind) {
                1 -> HapticFeedbackConstants.LONG_PRESS
                2, 3 -> HapticFeedbackConstants.CONFIRM
                else -> HapticFeedbackConstants.CLOCK_TICK
            })
        }
    }
    val renderer = remember(context) { ZeroRenderer(context.assets) }
    val drawTick = remember { mutableLongStateOf(0) }
    LaunchedEffect(motion,reduced,lifecycle) {
        motion.reduced = reduced || Build.VERSION.SDK_INT < 33
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                motion.advance((now-last)/1e9)
                drawTick.longValue = now
                last = now
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Navigation inset remains reserved even while system bars are hidden.
        val bottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(LocalDensity.current) / density
        // Keep the poster's 402-point coordinate space independent of emulator display density.
        val stageScale = maxWidth.value / 402f
        val stageDensity = density * stageScale
        val layout = remember(maxWidth,maxHeight,bottom) { ZeroLayout(402f,maxHeight.value / stageScale,bottom / stageScale) }
        SideEffect {
            motion.centreX = layout.centreX.toDouble()
            motion.centreY = layout.centreY.toDouble()
            motion.unit = layout.unit.toDouble()
        }
        Canvas(Modifier.fillMaxSize()) {
            drawTick.longValue
            val visible = if (reduced) (motion.now / .3).coerceIn(0.0,1.0).toFloat() else 1f
            renderer.draw(drawContext.canvas.nativeCanvas,layout,motion.frame,stageDensity,motion.reduced,visible)
        }
        Box(Modifier.fillMaxWidth().height((layout.heroHeight*stageScale).dp)
            .semantics {
                contentDescription = "Chrome zero percent numeral. Drag to turn. Hold to loosen it into beads."
                role = Role.Image
                stateDescription = "Interactive chrome numeral"
                onClick("Send a pulse") {
                    motion.activateHero()
                    true
                }
            }
            .pointerInput(motion,stageDensity) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    motion.begin((down.position.x/stageDensity).toDouble(),(down.position.y/stageDensity).toDouble())
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis,down.position)
                    var end = down.position
                    var released = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            end = change.position
                            tracker.addPosition(change.uptimeMillis,change.position)
                            if (!change.pressed) {
                                released = true
                                change.consume()
                                break
                            }
                            val translation = change.position-down.position
                            motion.change((end.x/stageDensity).toDouble(),(end.y/stageDensity).toDouble(),
                                (translation.x/stageDensity).toDouble(),(translation.y/stageDensity).toDouble())
                            change.consume()
                        }
                    } finally {
                        motion.release((tracker.calculateVelocity().x/stageDensity).toDouble(),!released,
                            (end.x/stageDensity).toDouble(),(end.y/stageDensity).toDouble())
                    }
                }
            })
        Box(Modifier.offset(y=(layout.pillTop*stageScale).dp).fillMaxWidth().height((28*stageScale).dp).semantics { contentDescription="nought" })
        Box(Modifier.offset(y=(layout.headlineTop*stageScale).dp).fillMaxWidth().height((layout.headlineLine*2*stageScale).dp)
            .semantics {
                heading()
                contentDescription="0% deposit fees. Every time."
            })
        Box(Modifier.offset(y=(layout.bodyTop*stageScale).dp).fillMaxWidth().height((24*layout.typeScale*stageScale).dp)
            .semantics { contentDescription="Zero to get in. Everything to gain." })
        Box(Modifier.offset(x=(24*stageScale).dp,y=(layout.buttonY*stageScale).dp).width((layout.buttonWidth*stageScale).dp).height((layout.buttonHeight*stageScale).dp)
            .semantics {
                role=Role.Button
                contentDescription="Start now"
                onClick {
                    motion.pressButton()
                    motion.releaseButton(true)
                    true
                }
            }
            .pointerInput(motion,stageDensity) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    motion.pressButton()
                    var success = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val inside = change.position.x in 0f..size.width.toFloat() && change.position.y in 0f..size.height.toFloat()
                            if ((change.position-down.position).getDistance()/stageDensity > 24 || !inside) break
                            change.consume()
                            if (!change.pressed) {
                                success = change.uptimeMillis-down.uptimeMillis <= 20000
                                break
                            }
                        }
                    } finally { motion.releaseButton(success) }
                }
            })
    }
}
