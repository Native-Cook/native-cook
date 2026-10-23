package com.ngocdevv.nativecook.blocks.gestures.foilpacktear

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.min

@Composable
fun FoilPackTearDemo() {
    FoilPackTear(autoPlay = true)
}

/** A self-contained 402 × 874 stage. Real input takes over and the demo resumes after 3.5 s. */
@Composable
fun FoilPackTear(autoPlay: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = LocalActivity.current?.window
    val engine = remember { initialEngine() }
    val assets = remember(context) { TearAssets(context) }
    val renderer = remember(assets) { if (Build.VERSION.SDK_INT >= 33) TearRenderer(assets) else null }
    val fallback = remember(assets) { if (Build.VERSION.SDK_INT < 33) TearFallback(assets) else null }
    val caption = remember(assets) { TearCaption(assets.font) }
    val frame = remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(engine.sim.phase) }
    val clock = remember { doubleArrayOf(0.0, 0.0, 0.0) }

    fun haptics() {
        engine.sim.events.forEach { event ->
            val feedback = when (event) {
                HAPTIC.tooth -> HapticFeedbackConstants.CLOCK_TICK
                HAPTIC.face -> HapticFeedbackConstants.CONFIRM
                HAPTIC.open -> HapticFeedbackConstants.LONG_PRESS
                else -> HapticFeedbackConstants.CONTEXT_CLICK
            }
            view.performHapticFeedback(feedback)
        }
        engine.sim.events.clear()
    }

    fun act(input: TearInput) {
        actEngine(engine, input)
        haptics()
        phase = engine.sim.phase
        frame.intValue++
    }

    DisposableEffect(context, window, view) {
        val resolver = context.contentResolver
        fun readMotion() {
            engine.sim.reduced = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
        readMotion()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { readMotion() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val bars = WindowInsetsCompat.Type.systemBars()
        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(view)
        val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        val navVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        controller?.hide(bars)
        onDispose {
            resolver.unregisterContentObserver(observer)
            if (statusVisible) controller?.show(WindowInsetsCompat.Type.statusBars())
            if (navVisible) controller?.show(WindowInsetsCompat.Type.navigationBars())
        }
    }

    LaunchedEffect(engine, autoPlay) {
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = min((now - previous) / 1e9, 0.1)
            previous = now
            stepEngine(engine, dt, autoPlay)
            clock[0] += dt
            clock[1] = if (engine.sim.reduced) 0.0 else (clock[1] + dt) % 13
            clock[2] = dt
            haptics()
            if (phase != engine.sim.phase) phase = engine.sim.phase
            frame.intValue++
        }
    }

    val label = when (phase) {
        "sealed" -> "Sealed foil pack"
        "tearing" -> "Partially torn foil pack"
        "opened" -> "Opening foil pack"
        "peek" -> "Card peeking out of the pack"
        "revealing" -> "Revealing Solstice Koi"
        "revealed" -> "Solstice Koi, legendary card"
        else -> "Collecting Solstice Koi"
    }
    val action = when (phase) {
        "sealed", "tearing" -> "Tear it open"
        "peek" -> "Pull it out"
        "revealed" -> "Collect it"
        else -> "Wait for the animation to finish"
    }
    Canvas(modifier.fillMaxSize().background(Color(0xFF010207))
        .semantics {
            role = Role.Button
            contentDescription = label
            stateDescription = phase
            if (phase in listOf("opened", "revealing", "collecting")) disabled()
            onClick(action) {
                act(TearInput("activate"))
                true
            }
        }
        .pointerInput(engine) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val scale = min(size.width / 402.0, size.height / 874.0)
                val originX = (size.width - 402 * scale) / 2
                val originY = (size.height - 874 * scale) / 2
                val velocity = VelocityTracker()
                velocity.addPosition(down.uptimeMillis, down.position)
                act(TearInput("down", (down.position.x - originX) / scale, (down.position.y - originY) / scale))
                down.consume()
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id }
                        if (pointer == null) {
                            act(TearInput("up", cancelled = true))
                            break
                        }
                        velocity.addPosition(pointer.uptimeMillis, pointer.position)
                        val speed = velocity.calculateVelocity()
                        if (!pointer.pressed) {
                            // Android can deliver the last coordinate with ACTION_UP.
                            act(TearInput("move", (pointer.position.x - originX) / scale,
                                (pointer.position.y - originY) / scale, speed.x / scale, speed.y / scale))
                            act(TearInput("up", vx = speed.x / scale, vy = speed.y / scale))
                            pointer.consume()
                            break
                        }
                        act(TearInput("move", (pointer.position.x - originX) / scale,
                            (pointer.position.y - originY) / scale, speed.x / scale, speed.y / scale))
                        pointer.consume()
                    }
                } finally {
                    if (engine.demo.realDown) act(TearInput("up", cancelled = true))
                }
            }
        }) {
        frame.intValue
        drawIntoCanvas {
            val canvas = it.nativeCanvas
            val fading = clock[0] < 0.22
            if (fading) canvas.saveLayerAlpha(null, (clamp(clock[0] / 0.22) * 255).toInt())
            if (Build.VERSION.SDK_INT >= 33) renderer?.draw(canvas, engine.sim.pose, clock[1], size.width, size.height, engine.sim.reduced)
            fallback?.draw(canvas, engine.sim.pose, size.width, size.height)
            caption.draw(canvas, engine.sim.phase, clock[0], size.width, size.height, engine.sim.reduced)
            if (fading) canvas.restore()
        }
    }
}
